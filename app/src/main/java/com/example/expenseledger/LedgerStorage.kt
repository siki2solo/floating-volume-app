package com.example.expenseledger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

enum class ExpenseStatus(val label: String) {
    PAID("Paid"),
    HOLD("Hold"),
    NEED("Need");

    fun next(): ExpenseStatus = when (this) {
        PAID -> HOLD
        HOLD -> NEED
        NEED -> PAID
    }

    companion object {
        fun fromString(value: String): ExpenseStatus =
            entries.firstOrNull { it.label.equals(value, ignoreCase = true) } ?: NEED
    }
}

enum class RecurrenceCycle(val label: String) {
    NONE("None"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly");

    companion object {
        fun fromString(value: String): RecurrenceCycle =
            entries.firstOrNull { it.label.equals(value, ignoreCase = true) } ?: NONE
    }
}

enum class BinnedEntityType(val key: String, val displayLabel: String) {
    EXPENSE("expense", "Expense"),
    LABEL("label", "Label"),
    LIST("list", "List"),
    TEMPLATE("template", "Template");

    companion object {
        fun fromKey(value: String): BinnedEntityType =
            entries.firstOrNull { it.key.equals(value, ignoreCase = true) } ?: EXPENSE
    }
}

data class AuditEntry(
    val id: String = UUID.randomUUID().toString(),
    val action: String, // "Created" or "Updated"
    val at: String,
    val summary: String = ""
)

data class LabelRecord(
    val id: String = "lbl-" + UUID.randomUUID().toString().take(8),
    val name: String,
    val description: String,
    val colorHex: String,
    val monthlyBudget: Double? = null,
    val createdAt: String,
    val updatedAt: String = createdAt,
    val auditLog: List<AuditEntry>
)

data class ListNoteRecord(
    val id: String = "list-" + UUID.randomUUID().toString().take(8),
    val title: String,
    val content: String,
    val createdAt: String,
    val updatedAt: String = createdAt,
    val auditLog: List<AuditEntry>
)

data class ExpenseRecord(
    val id: String = "exp-" + UUID.randomUUID().toString().take(8),
    val balance: Double,
    val quote: String,
    val status: ExpenseStatus,
    val labelIds: List<String>,
    val attachedListIds: List<String> = emptyList(),
    val calcHistoryId: String? = null,
    val calcFormula: String? = null,
    val createdAt: String,
    val updatedAt: String = createdAt,
    val auditLog: List<AuditEntry>
)

data class CalcHistoryRecord(
    val id: String = "calc-" + UUID.randomUUID().toString().take(8),
    val expression: String,
    val result: Double,
    val createdAt: String,
    val updatedAt: String
)

data class ExpenseTemplateRecord(
    val id: String = "tpl-" + UUID.randomUUID().toString().take(8),
    val title: String,
    val balance: Double,
    val defaultStatus: ExpenseStatus = ExpenseStatus.NEED,
    val labelIds: List<String> = emptyList(),
    val attachedListIds: List<String> = emptyList(),
    val recurrence: RecurrenceCycle = RecurrenceCycle.NONE,
    val lastUsedAt: String? = null,
    val createdAt: String
)

data class BinnedRecord(
    val id: String = "bin-" + UUID.randomUUID().toString().take(8),
    val entityType: BinnedEntityType,
    val title: String,
    val subtitle: String,
    val amount: Double? = null,
    val deletedAt: String,
    val payloadJson: String
)

data class LedgerSnapshot(
    val expenses: List<ExpenseRecord>,
    val labels: List<LabelRecord>,
    val lists: List<ListNoteRecord>,
    val calculatorHistory: List<CalcHistoryRecord>,
    val templates: List<ExpenseTemplateRecord> = emptyList(),
    val bin: List<BinnedRecord> = emptyList(),
    val binRetentionDays: Int? = 30
)

data class CalendarDayCell(
    val dateKey: String,
    val day: Int,
    val total: Double,
    val expenses: List<ExpenseRecord>
)

object LedgerMath {
    private const val MS_PER_DAY = 86_400_000L

    fun nowIso(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    fun parseIsoMillis(iso: String): Long? {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            parser.parse(iso.take(19))?.time
        } catch (_: Exception) {
            null
        }
    }

    fun formatReadableDate(iso: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val date = parser.parse(iso.take(19)) ?: return iso
            SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(date)
        } catch (_: Exception) {
            iso
        }
    }

    fun formatCompactDate(iso: String): String {
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val date = parser.parse(iso.take(19)) ?: return iso
            SimpleDateFormat("dd-MM-yy HH:mm", Locale.getDefault()).format(date)
        } catch (_: Exception) {
            iso
        }
    }

    fun extractNumbersSum(text: String): Double {
        if (text.isBlank()) return 0.0
        var sum = 0.0
        val regex = Regex("""(?:₹|rs\.?|inr)?\s*(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d+))?""", RegexOption.IGNORE_CASE)
        val bulletPrefix = Regex("""^\s*\d+[.)]\s+""")
        for (rawLine in text.lines()) {
            val cleaned = rawLine.replace(bulletPrefix, "")
            regex.findAll(cleaned).forEach { m ->
                val whole = m.groupValues[1].replace(",", "")
                val dec = if (m.groupValues[2].isNotEmpty()) "." + m.groupValues[2] else ""
                val parsed = (whole + dec).toDoubleOrNull()
                if (parsed != null) sum += parsed
            }
        }
        return kotlin.math.round(sum * 100.0) / 100.0
    }

    fun formatAmt(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (rounded % 1.0 == 0.0) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", rounded)
        }
    }

    fun availableMonths(expenses: List<ExpenseRecord>): List<String> {
        val monthRegex = Regex("""^\d{4}-\d{2}$""")
        return expenses
            .mapNotNull { exp ->
                val prefix = exp.createdAt.take(7)
                if (monthRegex.matches(prefix)) prefix else null
            }
            .distinct()
            .sortedDescending()
    }

    fun stepMonthKey(monthKey: String, delta: Int): String {
        val parts = monthKey.split("-")
        if (parts.size != 2) return monthKey
        val year = parts[0].toIntOrNull() ?: return monthKey
        val month = (parts[1].toIntOrNull() ?: return monthKey) - 1
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(year, month, 1, 0, 0, 0)
        cal.add(Calendar.MONTH, delta)
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        return String.format(Locale.US, "%04d-%02d", y, m)
    }

    fun buildCalendarGrid(expenses: List<ExpenseRecord>, monthKey: String): Pair<Int, List<CalendarDayCell>> {
        val parts = monthKey.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
        val month = (parts.getOrNull(1)?.toIntOrNull() ?: 11) - 1
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(year, month, 1, 0, 0, 0)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val dow = cal.get(Calendar.DAY_OF_WEEK) // Sun=1 .. Sat=7
        val firstWeekdayMon0 = (dow + 5) % 7
        val monthExpenses = expenses.filter { it.createdAt.startsWith(monthKey) }
        val byDate = monthExpenses.groupBy { it.createdAt.take(10) }
        val cells = (1..daysInMonth).map { day ->
            val dateKey = String.format(Locale.US, "%s-%02d", monthKey, day)
            val list = byDate[dateKey].orEmpty()
            CalendarDayCell(dateKey = dateKey, day = day, total = list.sumOf { it.balance }, expenses = list)
        }
        return firstWeekdayMon0 to cells
    }

    fun isTemplateDue(tpl: ExpenseTemplateRecord, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (tpl.recurrence == RecurrenceCycle.NONE) return false
        val last = tpl.lastUsedAt ?: return true
        val lastMs = parseIsoMillis(last) ?: return true
        if (tpl.recurrence == RecurrenceCycle.WEEKLY) {
            return (nowMs - lastMs) >= 7L * MS_PER_DAY
        }
        val fmt = SimpleDateFormat("yyyy-MM", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        val currentMonth = fmt.format(Date(nowMs))
        return last.take(7) < currentMonth
    }

    fun pruneExpiredBin(
        bin: List<BinnedRecord>,
        retentionDays: Int?,
        nowMs: Long = System.currentTimeMillis()
    ): List<BinnedRecord> {
        if (retentionDays == null) return bin
        val maxAge = retentionDays.toLong() * MS_PER_DAY
        return bin.filter { item ->
            val deletedMs = parseIsoMillis(item.deletedAt) ?: return@filter true
            (nowMs - deletedMs) < maxAge
        }
    }

    fun daysRemainingInBin(
        deletedAt: String,
        retentionDays: Int?,
        nowMs: Long = System.currentTimeMillis()
    ): Int? {
        if (retentionDays == null) return null
        val deletedMs = parseIsoMillis(deletedAt) ?: return retentionDays
        val elapsedDays = ((nowMs - deletedMs) / MS_PER_DAY).toInt().coerceAtLeast(0)
        return (retentionDays - elapsedDays).coerceAtLeast(0)
    }

    fun buildShareSummary(
        snapshot: LedgerSnapshot,
        appName: String,
        labelId: String?,
        monthKey: String?
    ): String {
        val targetLabel = labelId?.let { id -> snapshot.labels.firstOrNull { it.id == id } }
        val filtered = snapshot.expenses.filter { exp ->
            val matchLabel = targetLabel == null || exp.labelIds.contains(targetLabel.id)
            val matchMonth = monthKey.isNullOrBlank() || exp.createdAt.startsWith(monthKey)
            matchLabel && matchMonth
        }
        val paid = filtered.filter { it.status == ExpenseStatus.PAID }.sumOf { it.balance }
        val hold = filtered.filter { it.status == ExpenseStatus.HOLD }.sumOf { it.balance }
        val need = filtered.filter { it.status == ExpenseStatus.NEED }.sumOf { it.balance }
        val total = paid + hold + need
        val scopeName = targetLabel?.name ?: "All Expenses"
        val periodName = if (monthKey.isNullOrBlank()) "All Time" else monthKey

        return buildString {
            appendLine("$appName — Ledger Summary")
            appendLine("Scope: $scopeName · Period: $periodName")
            appendLine("Paid: ₹${formatAmt(paid)} | Hold: ₹${formatAmt(hold)} | Need: ₹${formatAmt(need)}")
            appendLine("Grand Commitment: ₹${formatAmt(total)} (${filtered.size} entries)")
            appendLine("---")
            if (filtered.isEmpty()) {
                appendLine("No matching expense entries.")
            } else {
                filtered.forEachIndexed { idx, exp ->
                    val tagNames = exp.labelIds.mapNotNull { id -> snapshot.labels.firstOrNull { it.id == id }?.name }
                    val tagSuffix = if (tagNames.isNotEmpty()) " [" + tagNames.joinToString(", ") + "]" else ""
                    appendLine("${idx + 1}. [${exp.status.label.uppercase()}] ₹${formatAmt(exp.balance)} — ${exp.quote}$tagSuffix")
                }
            }
        }.trimEnd()
    }

    private fun escapeCsv(raw: String): String {
        return if (raw.contains(",") || raw.contains("\"") || raw.contains("\n") || raw.contains("\r")) {
            "\"" + raw.replace("\"", "\"\"") + "\""
        } else {
            raw
        }
    }

    fun generateCsv(snapshot: LedgerSnapshot): String {
        val header = listOf("ID", "Created", "Updated", "Balance (INR)", "Status", "Quote", "Labels", "Attached Lists", "Math Tape")
        return buildString {
            append(header.joinToString(",") { escapeCsv(it) })
            snapshot.expenses.forEach { exp ->
                append("\r\n")
                val lblNames = exp.labelIds.mapNotNull { id -> snapshot.labels.firstOrNull { it.id == id }?.name }.joinToString(" | ")
                val lstNames = exp.attachedListIds.mapNotNull { id -> snapshot.lists.firstOrNull { it.id == id }?.title }.joinToString(" | ")
                val cols = listOf(
                    exp.id,
                    exp.createdAt,
                    exp.updatedAt,
                    formatAmt(exp.balance),
                    exp.status.label,
                    exp.quote,
                    lblNames,
                    lstNames,
                    exp.calcFormula ?: ""
                )
                append(cols.joinToString(",") { escapeCsv(it) })
            }
        }
    }
}

class LedgerDbHelper(context: Context) : SQLiteOpenHelper(context, "expense_ledger.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ledger_store (
                store_key TEXT PRIMARY KEY,
                payload_json TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS ledger_store")
        onCreate(db)
    }

    fun loadSnapshot(): LedgerSnapshot {
        val db = readableDatabase
        val cursor = db.query(
            "ledger_store",
            arrayOf("payload_json"),
            "store_key = ?",
            arrayOf("main_ledger"),
            null,
            null,
            null
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                val raw = c.getString(0)
                val decoded = decodeSnapshot(raw)
                val pruned = LedgerMath.pruneExpiredBin(decoded.bin, decoded.binRetentionDays)
                return if (pruned.size != decoded.bin.size) {
                    val cleaned = decoded.copy(bin = pruned)
                    saveSnapshot(cleaned)
                    cleaned
                } else {
                    decoded
                }
            }
        }
        val seed = createSeedSnapshot()
        saveSnapshot(seed)
        return seed
    }

    fun saveSnapshot(snapshot: LedgerSnapshot) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("store_key", "main_ledger")
            put("payload_json", encodeSnapshot(snapshot))
            put("updated_at", LedgerMath.nowIso())
        }
        db.insertWithOnConflict("ledger_store", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun encodeAuditList(entries: List<AuditEntry>): JSONArray {
        return JSONArray().apply {
            entries.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("action", e.action)
                    put("at", e.at)
                    put("summary", e.summary)
                })
            }
        }
    }

    private fun decodeAuditList(arr: JSONArray?): List<AuditEntry> {
        if (arr == null) return emptyList()
        val list = mutableListOf<AuditEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                AuditEntry(
                    id = obj.optString("id"),
                    action = obj.optString("action", "Created"),
                    at = obj.optString("at"),
                    summary = obj.optString("summary", "")
                )
            )
        }
        return list
    }

    fun encodeLabel(l: LabelRecord): JSONObject = JSONObject().apply {
        put("id", l.id)
        put("name", l.name)
        put("description", l.description)
        put("colorHex", l.colorHex)
        put("monthlyBudget", l.monthlyBudget ?: JSONObject.NULL)
        put("createdAt", l.createdAt)
        put("updatedAt", l.updatedAt)
        put("auditLog", encodeAuditList(l.auditLog))
    }

    fun encodeListNote(n: ListNoteRecord): JSONObject = JSONObject().apply {
        put("id", n.id)
        put("title", n.title)
        put("content", n.content)
        put("createdAt", n.createdAt)
        put("updatedAt", n.updatedAt)
        put("auditLog", encodeAuditList(n.auditLog))
    }

    fun encodeExpense(e: ExpenseRecord): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("balance", e.balance)
        put("quote", e.quote)
        put("status", e.status.label)
        put("labelIds", JSONArray(e.labelIds))
        put("attachedListIds", JSONArray(e.attachedListIds))
        put("calcHistoryId", e.calcHistoryId ?: JSONObject.NULL)
        put("calcFormula", e.calcFormula ?: JSONObject.NULL)
        put("createdAt", e.createdAt)
        put("updatedAt", e.updatedAt)
        put("auditLog", encodeAuditList(e.auditLog))
    }

    fun encodeTemplate(t: ExpenseTemplateRecord): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("title", t.title)
        put("balance", t.balance)
        put("defaultStatus", t.defaultStatus.label)
        put("labelIds", JSONArray(t.labelIds))
        put("attachedListIds", JSONArray(t.attachedListIds))
        put("recurrence", t.recurrence.label)
        put("lastUsedAt", t.lastUsedAt ?: JSONObject.NULL)
        put("createdAt", t.createdAt)
    }

    fun encodeSnapshot(snapshot: LedgerSnapshot): String {
        val root = JSONObject()
        val labelsArr = JSONArray().apply { snapshot.labels.forEach { put(encodeLabel(it)) } }
        val listsArr = JSONArray().apply { snapshot.lists.forEach { put(encodeListNote(it)) } }
        val expArr = JSONArray().apply { snapshot.expenses.forEach { put(encodeExpense(it)) } }
        val calcArr = JSONArray().apply {
            snapshot.calculatorHistory.forEach { c ->
                put(JSONObject().apply {
                    put("id", c.id)
                    put("expression", c.expression)
                    put("result", c.result)
                    put("createdAt", c.createdAt)
                    put("updatedAt", c.updatedAt)
                })
            }
        }
        val tplArr = JSONArray().apply { snapshot.templates.forEach { put(encodeTemplate(it)) } }
        val binArr = JSONArray().apply {
            snapshot.bin.forEach { b ->
                put(JSONObject().apply {
                    put("id", b.id)
                    put("entityType", b.entityType.key)
                    put("title", b.title)
                    put("subtitle", b.subtitle)
                    put("amount", b.amount ?: JSONObject.NULL)
                    put("deletedAt", b.deletedAt)
                    put("payload", JSONObject(b.payloadJson))
                })
            }
        }
        root.put("labels", labelsArr)
        root.put("lists", listsArr)
        root.put("expenses", expArr)
        root.put("calculatorHistory", calcArr)
        root.put("templates", tplArr)
        root.put("bin", binArr)
        root.put("binRetentionDays", snapshot.binRetentionDays ?: JSONObject.NULL)
        return root.toString()
    }

    fun decodeLabel(o: JSONObject): LabelRecord {
        val createdAt = o.optString("createdAt")
        val audit = decodeAuditList(o.optJSONArray("auditLog"))
        val updatedAt = o.optString("updatedAt").ifBlank { audit.lastOrNull()?.at ?: createdAt }
        val rawBudget = if (o.isNull("monthlyBudget")) null else o.optDouble("monthlyBudget", -1.0).takeIf { it > 0.0 }
        return LabelRecord(
            id = o.optString("id"),
            name = o.optString("name"),
            description = o.optString("description"),
            colorHex = o.optString("colorHex", "#2563EB"),
            monthlyBudget = rawBudget,
            createdAt = createdAt,
            updatedAt = updatedAt,
            auditLog = audit
        )
    }

    fun decodeListNote(o: JSONObject): ListNoteRecord {
        val createdAt = o.optString("createdAt")
        val audit = decodeAuditList(o.optJSONArray("auditLog"))
        val updatedAt = o.optString("updatedAt").ifBlank { audit.lastOrNull()?.at ?: createdAt }
        return ListNoteRecord(
            id = o.optString("id"),
            title = o.optString("title"),
            content = o.optString("content"),
            createdAt = createdAt,
            updatedAt = updatedAt,
            auditLog = audit
        )
    }

    fun decodeExpense(o: JSONObject): ExpenseRecord {
        val lblIds = mutableListOf<String>()
        val lArr = o.optJSONArray("labelIds") ?: JSONArray()
        for (j in 0 until lArr.length()) lblIds.add(lArr.optString(j))

        val attachedIds = mutableListOf<String>()
        val aArr = o.optJSONArray("attachedListIds")
        if (aArr != null) {
            for (j in 0 until aArr.length()) {
                val s = aArr.optString(j)
                if (s.isNotBlank()) attachedIds.add(s)
            }
        } else if (!o.isNull("attachedListId")) {
            val legacy = o.optString("attachedListId")
            if (legacy.isNotBlank()) attachedIds.add(legacy)
        }

        val calcHistoryId = if (o.isNull("calcHistoryId")) null else o.optString("calcHistoryId").takeIf { it.isNotBlank() }
        val calcFormula = if (o.isNull("calcFormula")) null else o.optString("calcFormula").takeIf { it.isNotBlank() }
        val createdAt = o.optString("createdAt")
        val audit = decodeAuditList(o.optJSONArray("auditLog"))
        val updatedAt = o.optString("updatedAt").ifBlank { audit.lastOrNull()?.at ?: createdAt }

        return ExpenseRecord(
            id = o.optString("id"),
            balance = o.optDouble("balance", 0.0),
            quote = o.optString("quote"),
            status = ExpenseStatus.fromString(o.optString("status", "Need")),
            labelIds = lblIds,
            attachedListIds = attachedIds,
            calcHistoryId = calcHistoryId,
            calcFormula = calcFormula,
            createdAt = createdAt,
            updatedAt = updatedAt,
            auditLog = audit
        )
    }

    fun decodeTemplate(o: JSONObject): ExpenseTemplateRecord {
        val lblIds = mutableListOf<String>()
        val lArr = o.optJSONArray("labelIds") ?: JSONArray()
        for (j in 0 until lArr.length()) lblIds.add(lArr.optString(j))
        val attIds = mutableListOf<String>()
        val aArr = o.optJSONArray("attachedListIds") ?: JSONArray()
        for (j in 0 until aArr.length()) attIds.add(aArr.optString(j))
        val lastUsed = if (o.isNull("lastUsedAt")) null else o.optString("lastUsedAt").takeIf { it.isNotBlank() }
        return ExpenseTemplateRecord(
            id = o.optString("id"),
            title = o.optString("title"),
            balance = o.optDouble("balance", 0.0),
            defaultStatus = ExpenseStatus.fromString(o.optString("defaultStatus", "Need")),
            labelIds = lblIds,
            attachedListIds = attIds,
            recurrence = RecurrenceCycle.fromString(o.optString("recurrence", "None")),
            lastUsedAt = lastUsed,
            createdAt = o.optString("createdAt")
        )
    }

    fun decodeSnapshot(raw: String): LedgerSnapshot {
        return try {
            val root = JSONObject(raw)
            val labels = mutableListOf<LabelRecord>()
            val labelsArr = root.optJSONArray("labels") ?: JSONArray()
            for (i in 0 until labelsArr.length()) {
                val o = labelsArr.optJSONObject(i) ?: continue
                labels.add(decodeLabel(o))
            }

            val lists = mutableListOf<ListNoteRecord>()
            val listsArr = root.optJSONArray("lists") ?: JSONArray()
            for (i in 0 until listsArr.length()) {
                val o = listsArr.optJSONObject(i) ?: continue
                lists.add(decodeListNote(o))
            }

            val expenses = mutableListOf<ExpenseRecord>()
            val expArr = root.optJSONArray("expenses") ?: JSONArray()
            for (i in 0 until expArr.length()) {
                val o = expArr.optJSONObject(i) ?: continue
                expenses.add(decodeExpense(o))
            }

            val calcHistory = mutableListOf<CalcHistoryRecord>()
            val calcArr = root.optJSONArray("calculatorHistory") ?: JSONArray()
            for (i in 0 until calcArr.length()) {
                val o = calcArr.optJSONObject(i) ?: continue
                calcHistory.add(
                    CalcHistoryRecord(
                        id = o.optString("id"),
                        expression = o.optString("expression"),
                        result = o.optDouble("result", 0.0),
                        createdAt = o.optString("createdAt"),
                        updatedAt = o.optString("updatedAt")
                    )
                )
            }

            val templates = mutableListOf<ExpenseTemplateRecord>()
            val tplArr = root.optJSONArray("templates") ?: JSONArray()
            for (i in 0 until tplArr.length()) {
                val o = tplArr.optJSONObject(i) ?: continue
                templates.add(decodeTemplate(o))
            }

            val retention: Int? = if (!root.has("binRetentionDays")) {
                30
            } else if (root.isNull("binRetentionDays")) {
                null
            } else {
                val v = root.optInt("binRetentionDays", 30)
                if (v == 30 || v == 60 || v == 90) v else 30
            }

            val bin = mutableListOf<BinnedRecord>()
            val binArr = root.optJSONArray("bin") ?: JSONArray()
            for (i in 0 until binArr.length()) {
                val o = binArr.optJSONObject(i) ?: continue
                val payloadObj = o.optJSONObject("payload") ?: continue
                val amt = if (o.isNull("amount")) null else o.optDouble("amount", 0.0)
                bin.add(
                    BinnedRecord(
                        id = o.optString("id"),
                        entityType = BinnedEntityType.fromKey(o.optString("entityType", "expense")),
                        title = o.optString("title", "Deleted item"),
                        subtitle = o.optString("subtitle", ""),
                        amount = amt,
                        deletedAt = o.optString("deletedAt", LedgerMath.nowIso()),
                        payloadJson = payloadObj.toString()
                    )
                )
            }

            LedgerSnapshot(expenses, labels, lists, calcHistory, templates, LedgerMath.pruneExpiredBin(bin, retention), retention)
        } catch (_: Exception) {
            createSeedSnapshot()
        }
    }

    fun validateBackupJson(rawJson: String): LedgerSnapshot? {
        return try {
            val root = JSONObject(rawJson)
            if (root.optJSONArray("expenses") == null ||
                root.optJSONArray("labels") == null ||
                root.optJSONArray("lists") == null
            ) {
                return null
            }
            val parsed = decodeSnapshot(rawJson)
            val validLabelIds = parsed.labels.map { it.id }.toSet()
            val validListIds = parsed.lists.map { it.id }.toSet()
            val normalizedExpenses = parsed.expenses.map { exp ->
                exp.copy(
                    labelIds = exp.labelIds.filter { validLabelIds.contains(it) },
                    attachedListIds = exp.attachedListIds.filter { validListIds.contains(it) }
                )
            }
            parsed.copy(expenses = normalizedExpenses)
        } catch (_: Exception) {
            null
        }
    }

    fun moveExpenseToBin(snapshot: LedgerSnapshot, expId: String): LedgerSnapshot {
        val target = snapshot.expenses.firstOrNull { it.id == expId } ?: return snapshot
        val record = BinnedRecord(
            entityType = BinnedEntityType.EXPENSE,
            title = target.quote,
            subtitle = "${target.status.label} · Created ${LedgerMath.formatCompactDate(target.createdAt)}",
            amount = target.balance,
            deletedAt = LedgerMath.nowIso(),
            payloadJson = encodeExpense(target).toString()
        )
        return snapshot.copy(
            expenses = snapshot.expenses.filterNot { it.id == expId },
            bin = listOf(record) + snapshot.bin
        )
    }

    fun moveExpensesBulkToBin(snapshot: LedgerSnapshot, expIds: Set<String>): LedgerSnapshot {
        if (expIds.isEmpty()) return snapshot
        val now = LedgerMath.nowIso()
        val targets = snapshot.expenses.filter { expIds.contains(it.id) }
        if (targets.isEmpty()) return snapshot
        val binned = targets.map { target ->
            BinnedRecord(
                entityType = BinnedEntityType.EXPENSE,
                title = target.quote,
                subtitle = "${target.status.label} · Created ${LedgerMath.formatCompactDate(target.createdAt)}",
                amount = target.balance,
                deletedAt = now,
                payloadJson = encodeExpense(target).toString()
            )
        }
        return snapshot.copy(
            expenses = snapshot.expenses.filterNot { expIds.contains(it.id) },
            bin = binned + snapshot.bin
        )
    }

    fun moveLabelToBin(snapshot: LedgerSnapshot, labelId: String): LedgerSnapshot {
        val target = snapshot.labels.firstOrNull { it.id == labelId } ?: return snapshot
        val now = LedgerMath.nowIso()
        val record = BinnedRecord(
            entityType = BinnedEntityType.LABEL,
            title = target.name,
            subtitle = target.description.ifBlank { "Relational label" },
            amount = null,
            deletedAt = now,
            payloadJson = encodeLabel(target).toString()
        )
        return snapshot.copy(
            labels = snapshot.labels.filterNot { it.id == labelId },
            expenses = snapshot.expenses.map { exp ->
                if (exp.labelIds.contains(labelId)) {
                    exp.copy(
                        labelIds = exp.labelIds.filterNot { it == labelId },
                        updatedAt = now,
                        auditLog = exp.auditLog + AuditEntry(action = "Updated", at = now, summary = "Unlinked binned label ${target.name}")
                    )
                } else exp
            },
            templates = snapshot.templates.map { tpl ->
                if (tpl.labelIds.contains(labelId)) tpl.copy(labelIds = tpl.labelIds.filterNot { it == labelId }) else tpl
            },
            bin = listOf(record) + snapshot.bin
        )
    }

    fun moveListToBin(snapshot: LedgerSnapshot, listId: String): LedgerSnapshot {
        val target = snapshot.lists.firstOrNull { it.id == listId } ?: return snapshot
        val now = LedgerMath.nowIso()
        val detected = LedgerMath.extractNumbersSum(target.content)
        val record = BinnedRecord(
            entityType = BinnedEntityType.LIST,
            title = target.title,
            subtitle = "Itemized list · Sum ₹${LedgerMath.formatAmt(detected)}",
            amount = if (detected > 0.0) detected else null,
            deletedAt = now,
            payloadJson = encodeListNote(target).toString()
        )
        return snapshot.copy(
            lists = snapshot.lists.filterNot { it.id == listId },
            expenses = snapshot.expenses.map { exp ->
                if (exp.attachedListIds.contains(listId)) exp.copy(attachedListIds = exp.attachedListIds.filterNot { it == listId }, updatedAt = now) else exp
            },
            templates = snapshot.templates.map { tpl ->
                if (tpl.attachedListIds.contains(listId)) tpl.copy(attachedListIds = tpl.attachedListIds.filterNot { it == listId }) else tpl
            },
            bin = listOf(record) + snapshot.bin
        )
    }

    fun moveTemplateToBin(snapshot: LedgerSnapshot, tplId: String): LedgerSnapshot {
        val target = snapshot.templates.firstOrNull { it.id == tplId } ?: return snapshot
        val record = BinnedRecord(
            entityType = BinnedEntityType.TEMPLATE,
            title = target.title,
            subtitle = "Template · ${target.recurrence.label}",
            amount = target.balance,
            deletedAt = LedgerMath.nowIso(),
            payloadJson = encodeTemplate(target).toString()
        )
        return snapshot.copy(
            templates = snapshot.templates.filterNot { it.id == tplId },
            bin = listOf(record) + snapshot.bin
        )
    }

    fun restoreFromBin(snapshot: LedgerSnapshot, record: BinnedRecord): LedgerSnapshot {
        val now = LedgerMath.nowIso()
        val remainingBin = snapshot.bin.filterNot { it.id == record.id }
        val activeLabelIds = snapshot.labels.map { it.id }.toSet()
        val activeListIds = snapshot.lists.map { it.id }.toSet()
        val payload = JSONObject(record.payloadJson)
        return when (record.entityType) {
            BinnedEntityType.EXPENSE -> {
                val raw = decodeExpense(payload)
                val restored = raw.copy(
                    labelIds = raw.labelIds.filter { activeLabelIds.contains(it) },
                    attachedListIds = raw.attachedListIds.filter { activeListIds.contains(it) },
                    updatedAt = now,
                    auditLog = raw.auditLog + AuditEntry(action = "Updated", at = now, summary = "Restored from Recycle Bin")
                )
                snapshot.copy(expenses = listOf(restored) + snapshot.expenses.filterNot { it.id == restored.id }, bin = remainingBin)
            }
            BinnedEntityType.LABEL -> {
                val raw = decodeLabel(payload)
                val restored = raw.copy(updatedAt = now, auditLog = raw.auditLog + AuditEntry(action = "Updated", at = now, summary = "Restored from Recycle Bin"))
                snapshot.copy(labels = snapshot.labels.filterNot { it.id == restored.id } + restored, bin = remainingBin)
            }
            BinnedEntityType.LIST -> {
                val raw = decodeListNote(payload)
                val restored = raw.copy(updatedAt = now, auditLog = raw.auditLog + AuditEntry(action = "Updated", at = now, summary = "Restored from Recycle Bin"))
                snapshot.copy(lists = listOf(restored) + snapshot.lists.filterNot { it.id == restored.id }, bin = remainingBin)
            }
            BinnedEntityType.TEMPLATE -> {
                val raw = decodeTemplate(payload)
                val restored = raw.copy(
                    labelIds = raw.labelIds.filter { activeLabelIds.contains(it) },
                    attachedListIds = raw.attachedListIds.filter { activeListIds.contains(it) }
                )
                snapshot.copy(templates = listOf(restored) + snapshot.templates.filterNot { it.id == restored.id }, bin = remainingBin)
            }
        }
    }

    private fun createSeedSnapshot(): LedgerSnapshot {
        val t1 = "2026-11-10T09:15:00.000Z"
        val t2 = "2026-11-14T08:30:00.000Z"
        val labels = listOf(
            LabelRecord("lbl-dr", "#Dr.", "Clinic consultations & pharmacy bills", "#2563EB", 2000.0, t1, t1, listOf(AuditEntry(action = "Created", at = t1, summary = "Seeded label"))),
            LabelRecord("lbl-school", "#School", "Tuition, craft supplies & fees", "#7C3AED", 1200.0, t1, t1, listOf(AuditEntry(action = "Created", at = t1, summary = "Seeded label"))),
            LabelRecord("lbl-vegetables", "#Vegetables", "Daily fresh market produce", "#059669", 800.0, t1, t1, listOf(AuditEntry(action = "Created", at = t1, summary = "Seeded label")))
        )
        val lists = listOf(
            ListNoteRecord("list-craft", "School Craft & Fees Breakdown", "Wood -> ₹5\nFees -> ₹10\nOthers -> ₹15", t2, t2, listOf(AuditEntry(action = "Created", at = t2, summary = "Created breakdown ₹30")))
        )
        val expenses = listOf(
            ExpenseRecord("exp-1", 850.0, "Pediatric checkup & vitamin prescription", ExpenseStatus.PAID, listOf("lbl-dr", "lbl-school"), emptyList(), null, null, t2, t2, listOf(AuditEntry(action = "Created", at = t2, summary = "Logged ₹850"))),
            ExpenseRecord("exp-2", 30.0, "Term project wood, fees & misc supplies", ExpenseStatus.HOLD, listOf("lbl-school"), listOf("list-craft"), "calc-1", "5 + 10 + 15 = 30", t2, t2, listOf(AuditEntry(action = "Created", at = t2, summary = "Logged ₹30 with attached list"))),
            ExpenseRecord("exp-3", 450.0, "Lab blood panel follow-up deposit", ExpenseStatus.NEED, listOf("lbl-dr"), emptyList(), null, null, t2, t2, listOf(AuditEntry(action = "Created", at = t2, summary = "Logged ₹450")))
        )
        val calcHistory = listOf(
            CalcHistoryRecord("calc-1", "5 + 10 + 15", 30.0, t2, t2)
        )
        val templates = listOf(
            ExpenseTemplateRecord("tpl-veg", "Weekly Produce Basket", 120.0, ExpenseStatus.PAID, listOf("lbl-vegetables"), emptyList(), RecurrenceCycle.WEEKLY, null, t1),
            ExpenseTemplateRecord("tpl-clinic", "Monthly Clinic Checkup", 500.0, ExpenseStatus.NEED, listOf("lbl-dr"), emptyList(), RecurrenceCycle.MONTHLY, null, t1)
        )
        return LedgerSnapshot(expenses, labels, lists, calcHistory, templates, emptyList(), 30)
    }
}
