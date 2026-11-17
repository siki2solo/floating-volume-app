package com.example.expenseledger

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.round

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExpenseLedgerApp()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}

private enum class LedgerTab(val title: String) {
    LISTS("Lists"),
    EXPENSES("Expenses"),
    LABELS("Labels")
}

private fun formatAmount(value: Double): String {
    val rounded = round(value * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", rounded)
    }
}

private fun parseColorHex(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFF2563EB)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseLedgerApp() {
    val context = LocalContext.current
    val dbHelper = remember { LedgerDbHelper(context) }

    var snapshot by remember { mutableStateOf(dbHelper.loadSnapshot()) }
    var activeTab by remember { mutableStateOf(LedgerTab.EXPENSES) }
    var isDark by remember { mutableStateOf(false) }

    var showExpenseModal by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseRecord?>(null) }
    var prefillBalance by remember { mutableStateOf("") }
    var prefillQuote by remember { mutableStateOf("") }
    var prefillStatus by remember { mutableStateOf<ExpenseStatus?>(null) }
    var prefillLabelIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var prefillAttachedListIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var prefillCalcHistoryId by remember { mutableStateOf<String?>(null) }
    var prefillCalcFormula by remember { mutableStateOf<String?>(null) }

    var showLabelModal by remember { mutableStateOf(false) }
    var editingLabel by remember { mutableStateOf<LabelRecord?>(null) }

    var showListModal by remember { mutableStateOf(false) }
    var editingList by remember { mutableStateOf<ListNoteRecord?>(null) }
    var prefillListTitle by remember { mutableStateOf("") }
    var prefillListContent by remember { mutableStateOf("") }

    var showCalculatorSheet by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<ExpenseStatus?>(null) }
    var labelFilterId by remember { mutableStateOf<String?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var shareLabelId by remember { mutableStateOf<String?>(null) }

    // v1.2 Controls: Insights Sheet, Recycle Bin, Bulk Select & Quick Templates
    var showInsightsDialog by remember { mutableStateOf(false) }
    var showBinDialog by remember { mutableStateOf(false) }
    var bulkMode by remember { mutableStateOf(false) }
    var selectedExpenseIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showQuickTemplates by remember { mutableStateOf(true) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun persist(updated: LedgerSnapshot) {
        snapshot = updated
        dbHelper.saveSnapshot(updated)
    }

    fun showActionSnackbar(message: String, undoSnapshot: LedgerSnapshot? = null) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (undoSnapshot != null) "Undo" else null,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed && undoSnapshot != null) {
                persist(undoSnapshot)
            }
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(dbHelper.encodeSnapshot(snapshot).toByteArray(Charsets.UTF_8))
                }
                showActionSnackbar("Exported JSON backup")
            } catch (_: Exception) {
                showActionSnackbar("Could not export JSON backup")
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(LedgerMath.generateCsv(snapshot).toByteArray(Charsets.UTF_8))
                }
                showActionSnackbar("Exported CSV spreadsheet")
            } catch (_: Exception) {
                showActionSnackbar("Could not export CSV file")
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val restored = dbHelper.validateBackupJson(raw)
                if (restored != null) {
                    val prev = snapshot
                    persist(restored)
                    selectedExpenseIds = emptySet()
                    bulkMode = false
                    showActionSnackbar("Restored ${restored.expenses.size} expenses from JSON", prev)
                } else {
                    showActionSnackbar("Invalid ledger backup JSON file")
                }
            } catch (_: Exception) {
                showActionSnackbar("Failed to read backup file")
            }
        }
    }

    val paidTotal = remember(snapshot.expenses) {
        snapshot.expenses.filter { it.status == ExpenseStatus.PAID }.sumOf { it.balance }
    }
    val holdTotal = remember(snapshot.expenses) {
        snapshot.expenses.filter { it.status == ExpenseStatus.HOLD }.sumOf { it.balance }
    }
    val needTotal = remember(snapshot.expenses) {
        snapshot.expenses.filter { it.status == ExpenseStatus.NEED }.sumOf { it.balance }
    }
    val grandTotal = paidTotal + holdTotal + needTotal

    val colorScheme = if (isDark) darkColorScheme() else lightColorScheme(
        background = Color(0xFFF8FAFC),
        surface = Color(0xFFFFFFFF),
        primary = Color(0xFF1E40AF)
    )

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Expense Ledger",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showInsightsDialog = true }) {
                                Icon(Icons.Default.BarChart, contentDescription = "Insights and calendar")
                            }
                            IconButton(onClick = { showBinDialog = true }) {
                                BadgedBox(
                                    badge = {
                                        if (snapshot.bin.isNotEmpty()) {
                                            Badge { Text("${snapshot.bin.size}") }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Recycle Bin")
                                }
                            }
                            IconButton(onClick = { isDark = !isDark }) {
                                Icon(
                                    imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle theme"
                                )
                            }
                            FilledTonalButton(
                                onClick = { showCalculatorSheet = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Calc", fontSize = 12.sp)
                            }
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Backup, CSV and share options")
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Insights & Calendar") },
                                        leadingIcon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showInsightsDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Recycle Bin (${snapshot.bin.size})") },
                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showBinDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share Summary") },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            shareLabelId = null
                                            showShareDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export CSV (.csv)") },
                                        leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            exportCsvLauncher.launch("expense-ledger-export.csv")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export Backup (.json)") },
                                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            exportBackupLauncher.launch("expense-ledger-backup.json")
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Restore Backup (.json)") },
                                        leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null) },
                                        onClick = {
                                            showOverflowMenu = false
                                            importBackupLauncher.launch(arrayOf("application/json", "*/*"))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryStatBox("PAID (₹)", paidTotal, Color(0xFF059669), Modifier.weight(1f))
                        SummaryStatBox("HOLD (₹)", holdTotal, Color(0xFFD97706), Modifier.weight(1f))
                        SummaryStatBox("NEED (₹)", needTotal, Color(0xFFDC2626), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Grand Commitment: ₹${formatAmount(grandTotal)} · ${snapshot.expenses.size} entries",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    LedgerTab.entries.forEach { tab ->
                        val icon = when (tab) {
                            LedgerTab.LISTS -> Icons.Default.Checklist
                            LedgerTab.EXPENSES -> Icons.Default.ReceiptLong
                            LedgerTab.LABELS -> Icons.Default.Label
                        }
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = {
                                activeTab = tab
                                if (tab != LedgerTab.EXPENSES) {
                                    bulkMode = false
                                    selectedExpenseIds = emptySet()
                                }
                            },
                            icon = { Icon(icon, contentDescription = tab.title) },
                            label = { Text(tab.title) }
                        )
                    }
                }
            },
            floatingActionButton = {
                if (!(activeTab == LedgerTab.EXPENSES && bulkMode && selectedExpenseIds.isNotEmpty())) {
                    FloatingActionButton(
                        onClick = {
                            when (activeTab) {
                                LedgerTab.EXPENSES -> {
                                    editingExpense = null
                                    prefillBalance = ""
                                    prefillQuote = ""
                                    prefillStatus = null
                                    prefillLabelIds = emptyList()
                                    prefillAttachedListIds = emptyList()
                                    prefillCalcHistoryId = null
                                    prefillCalcFormula = null
                                    showExpenseModal = true
                                }
                                LedgerTab.LABELS -> {
                                    editingLabel = null
                                    showLabelModal = true
                                }
                                LedgerTab.LISTS -> {
                                    editingList = null
                                    prefillListTitle = ""
                                    prefillListContent = ""
                                    showListModal = true
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create new")
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (activeTab) {
                    LedgerTab.EXPENSES -> ExpensesScreen(
                        expenses = snapshot.expenses,
                        labels = snapshot.labels,
                        lists = snapshot.lists,
                        templates = snapshot.templates,
                        searchQuery = searchQuery,
                        statusFilter = statusFilter,
                        labelFilterId = labelFilterId,
                        bulkMode = bulkMode,
                        selectedExpenseIds = selectedExpenseIds,
                        showQuickTemplates = showQuickTemplates,
                        onSearchQueryChange = { searchQuery = it },
                        onStatusFilterChange = { statusFilter = it },
                        onLabelFilterChange = { labelFilterId = it },
                        onClearFilters = {
                            searchQuery = ""
                            statusFilter = null
                            labelFilterId = null
                        },
                        onToggleBulkMode = {
                            bulkMode = !bulkMode
                            if (!bulkMode) selectedExpenseIds = emptySet()
                        },
                        onToggleSelectId = { id ->
                            selectedExpenseIds = if (selectedExpenseIds.contains(id)) selectedExpenseIds - id else selectedExpenseIds + id
                        },
                        onToggleSelectAll = { visibleIds ->
                            val allSelected = visibleIds.isNotEmpty() && visibleIds.all { selectedExpenseIds.contains(it) }
                            selectedExpenseIds = if (allSelected) emptySet() else visibleIds.toSet()
                        },
                        onBulkStatus = { targetStatus ->
                            if (selectedExpenseIds.isNotEmpty()) {
                                val prev = snapshot
                                val now = LedgerMath.nowIso()
                                val count = selectedExpenseIds.size
                                val updated = snapshot.expenses.map { item ->
                                    if (selectedExpenseIds.contains(item.id) && item.status != targetStatus) {
                                        item.copy(
                                            status = targetStatus,
                                            updatedAt = now,
                                            auditLog = item.auditLog + AuditEntry(
                                                action = "Updated",
                                                at = now,
                                                summary = "Bulk status changed to ${targetStatus.label}"
                                            )
                                        )
                                    } else item
                                }
                                persist(snapshot.copy(expenses = updated))
                                bulkMode = false
                                selectedExpenseIds = emptySet()
                                showActionSnackbar("Marked $count expenses ${targetStatus.label}", prev)
                            }
                        },
                        onBulkMoveToBin = {
                            if (selectedExpenseIds.isNotEmpty()) {
                                val prev = snapshot
                                val count = selectedExpenseIds.size
                                val next = dbHelper.moveExpensesBulkToBin(snapshot, selectedExpenseIds)
                                persist(next)
                                bulkMode = false
                                selectedExpenseIds = emptySet()
                                showActionSnackbar("Moved $count expenses to Bin", prev)
                            }
                        },
                        onToggleQuickTemplates = { showQuickTemplates = !showQuickTemplates },
                        onLogTemplate = { tpl ->
                            val prev = snapshot
                            val now = LedgerMath.nowIso()
                            val created = ExpenseRecord(
                                balance = tpl.balance,
                                quote = tpl.title,
                                status = tpl.defaultStatus,
                                labelIds = tpl.labelIds,
                                attachedListIds = tpl.attachedListIds,
                                calcHistoryId = null,
                                calcFormula = null,
                                createdAt = now,
                                updatedAt = now,
                                auditLog = listOf(
                                    AuditEntry(
                                        action = "Created",
                                        at = now,
                                        summary = "1-tap template log: ₹${formatAmount(tpl.balance)} (${tpl.defaultStatus.label})"
                                    )
                                )
                            )
                            val updatedTemplates = snapshot.templates.map { item ->
                                if (item.id == tpl.id) item.copy(lastUsedAt = now) else item
                            }
                            persist(snapshot.copy(expenses = listOf(created) + snapshot.expenses, templates = updatedTemplates))
                            showActionSnackbar("Logged '${tpl.title}' from template", prev)
                        },
                        onMoveTemplateToBin = { tplId ->
                            val target = snapshot.templates.firstOrNull { it.id == tplId }
                            val prev = snapshot
                            persist(dbHelper.moveTemplateToBin(snapshot, tplId))
                            if (target != null) {
                                showActionSnackbar("Moved template '${target.title}' to Bin", prev)
                            }
                        },
                        onCycleStatus = { exp ->
                            val now = LedgerMath.nowIso()
                            val nextStatus = exp.status.next()
                            val updatedExpenses = snapshot.expenses.map { item ->
                                if (item.id == exp.id) {
                                    item.copy(
                                        status = nextStatus,
                                        updatedAt = now,
                                        auditLog = item.auditLog + AuditEntry(
                                            action = "Updated",
                                            at = now,
                                            summary = "Status changed from ${item.status.label} to ${nextStatus.label}"
                                        )
                                    )
                                } else item
                            }
                            persist(snapshot.copy(expenses = updatedExpenses))
                        },
                        onDuplicate = { exp ->
                            val prev = snapshot
                            val now = LedgerMath.nowIso()
                            val copy = ExpenseRecord(
                                balance = exp.balance,
                                quote = "${exp.quote} (Copy)",
                                status = exp.status,
                                labelIds = exp.labelIds,
                                attachedListIds = exp.attachedListIds,
                                calcHistoryId = exp.calcHistoryId,
                                calcFormula = exp.calcFormula,
                                createdAt = now,
                                updatedAt = now,
                                auditLog = listOf(
                                    AuditEntry(
                                        action = "Created",
                                        at = now,
                                        summary = "Duplicated ₹${formatAmount(exp.balance)} (${exp.status.label})"
                                    )
                                )
                            )
                            persist(snapshot.copy(expenses = listOf(copy) + snapshot.expenses))
                            showActionSnackbar("Duplicated '${exp.quote}'", prev)
                        },
                        onEdit = { exp ->
                            editingExpense = exp
                            prefillBalance = formatAmount(exp.balance)
                            prefillQuote = exp.quote
                            prefillStatus = exp.status
                            prefillLabelIds = exp.labelIds
                            prefillAttachedListIds = exp.attachedListIds
                            prefillCalcHistoryId = exp.calcHistoryId
                            prefillCalcFormula = exp.calcFormula
                            showExpenseModal = true
                        },
                        onDelete = { expId ->
                            val target = snapshot.expenses.firstOrNull { it.id == expId }
                            val prev = snapshot
                            persist(dbHelper.moveExpenseToBin(snapshot, expId))
                            if (target != null) {
                                showActionSnackbar("Moved '${target.quote}' to Bin", prev)
                            }
                        }
                    )
                    LedgerTab.LABELS -> LabelsScreen(
                        labels = snapshot.labels,
                        expenses = snapshot.expenses,
                        onEditLabel = { lbl ->
                            editingLabel = lbl
                            showLabelModal = true
                        },
                        onDeleteLabel = { lblId ->
                            val targetName = snapshot.labels.firstOrNull { it.id == lblId }?.name ?: "Label"
                            val prev = snapshot
                            persist(dbHelper.moveLabelToBin(snapshot, lblId))
                            if (labelFilterId == lblId) labelFilterId = null
                            showActionSnackbar("Moved label '$targetName' to Bin", prev)
                        },
                        onAddExpenseForLabel = { lblId ->
                            editingExpense = null
                            prefillBalance = ""
                            prefillQuote = ""
                            prefillStatus = null
                            prefillLabelIds = listOf(lblId)
                            prefillAttachedListIds = emptyList()
                            prefillCalcHistoryId = null
                            prefillCalcFormula = null
                            activeTab = LedgerTab.EXPENSES
                            showExpenseModal = true
                        },
                        onShareLabel = { lblId ->
                            shareLabelId = lblId
                            showShareDialog = true
                        }
                    )
                    LedgerTab.LISTS -> ListsScreen(
                        lists = snapshot.lists,
                        onEditList = { lst ->
                            editingList = lst
                            prefillListTitle = lst.title
                            prefillListContent = lst.content
                            showListModal = true
                        },
                        onDeleteList = { listId ->
                            val targetTitle = snapshot.lists.firstOrNull { it.id == listId }?.title ?: "List"
                            val prev = snapshot
                            persist(dbHelper.moveListToBin(snapshot, listId))
                            showActionSnackbar("Moved list '$targetTitle' to Bin", prev)
                        },
                        onUseInExpense = { lst, detectedSum ->
                            editingExpense = null
                            prefillBalance = if (detectedSum > 0.0) formatAmount(detectedSum) else ""
                            prefillQuote = lst.title
                            prefillStatus = null
                            prefillLabelIds = emptyList()
                            prefillAttachedListIds = listOf(lst.id)
                            prefillCalcHistoryId = null
                            prefillCalcFormula = null
                            activeTab = LedgerTab.EXPENSES
                            showExpenseModal = true
                        }
                    )
                }
            }
        }

        if (showExpenseModal) {
            ExpenseEditorDialog(
                existing = editingExpense,
                initialBalance = prefillBalance,
                initialQuote = prefillQuote,
                initialStatus = prefillStatus,
                initialLabelIds = prefillLabelIds,
                initialAttachedListIds = prefillAttachedListIds,
                calcFormula = prefillCalcFormula,
                labels = snapshot.labels,
                lists = snapshot.lists,
                onDismiss = { showExpenseModal = false },
                onSave = { balance, quote, status, labelIds, attachedListIds, saveAsTemplate, recurrence ->
                    val now = LedgerMath.nowIso()
                    val nextExpenses = if (editingExpense == null) {
                        val entry = ExpenseRecord(
                            balance = balance,
                            quote = quote,
                            status = status,
                            labelIds = labelIds,
                            attachedListIds = attachedListIds,
                            calcHistoryId = prefillCalcHistoryId,
                            calcFormula = prefillCalcFormula,
                            createdAt = now,
                            updatedAt = now,
                            auditLog = listOf(AuditEntry(action = "Created", at = now, summary = "Logged ₹${formatAmount(balance)} (${status.label})"))
                        )
                        listOf(entry) + snapshot.expenses
                    } else {
                        snapshot.expenses.map { item ->
                            if (item.id == editingExpense!!.id) {
                                item.copy(
                                    balance = balance,
                                    quote = quote,
                                    status = status,
                                    labelIds = labelIds,
                                    attachedListIds = attachedListIds,
                                    calcHistoryId = prefillCalcHistoryId,
                                    calcFormula = prefillCalcFormula,
                                    updatedAt = now,
                                    auditLog = item.auditLog + AuditEntry(action = "Updated", at = now, summary = "Updated ₹${formatAmount(balance)} (${status.label})")
                                )
                            } else item
                        }
                    }
                    val nextTemplates = if (saveAsTemplate) {
                        val tpl = ExpenseTemplateRecord(
                            title = quote,
                            balance = balance,
                            defaultStatus = status,
                            labelIds = labelIds,
                            attachedListIds = attachedListIds,
                            recurrence = recurrence,
                            lastUsedAt = now,
                            createdAt = now
                        )
                        listOf(tpl) + snapshot.templates
                    } else {
                        snapshot.templates
                    }
                    persist(snapshot.copy(expenses = nextExpenses, templates = nextTemplates))
                    showExpenseModal = false
                }
            )
        }

        if (showLabelModal) {
            LabelEditorDialog(
                existing = editingLabel,
                onDismiss = { showLabelModal = false },
                onSave = { name, desc, colorHex, monthlyBudget ->
                    val now = LedgerMath.nowIso()
                    val normalizedName = if (name.startsWith("#")) name else "#$name"
                    val nextLabels = if (editingLabel == null) {
                        val newLbl = LabelRecord(
                            name = normalizedName,
                            description = desc,
                            colorHex = colorHex,
                            monthlyBudget = monthlyBudget,
                            createdAt = now,
                            updatedAt = now,
                            auditLog = listOf(AuditEntry(action = "Created", at = now, summary = "Created $normalizedName"))
                        )
                        snapshot.labels + newLbl
                    } else {
                        snapshot.labels.map { l ->
                            if (l.id == editingLabel!!.id) {
                                l.copy(
                                    name = normalizedName,
                                    description = desc,
                                    colorHex = colorHex,
                                    monthlyBudget = monthlyBudget,
                                    updatedAt = now,
                                    auditLog = l.auditLog + AuditEntry(action = "Updated", at = now, summary = "Edited $normalizedName")
                                )
                            } else l
                        }
                    }
                    persist(snapshot.copy(labels = nextLabels))
                    showLabelModal = false
                }
            )
        }

        if (showListModal) {
            ListEditorDialog(
                existing = editingList,
                initialTitle = prefillListTitle,
                initialContent = prefillListContent,
                onDismiss = { showListModal = false },
                onSave = { title, content ->
                    val now = LedgerMath.nowIso()
                    val autoSum = LedgerMath.extractNumbersSum(content)
                    val nextLists = if (editingList == null) {
                        val newList = ListNoteRecord(
                            title = title,
                            content = content,
                            createdAt = now,
                            updatedAt = now,
                            auditLog = listOf(AuditEntry(action = "Created", at = now, summary = "Created list (₹${formatAmount(autoSum)})"))
                        )
                        listOf(newList) + snapshot.lists
                    } else {
                        snapshot.lists.map { lst ->
                            if (lst.id == editingList!!.id) {
                                lst.copy(
                                    title = title,
                                    content = content,
                                    updatedAt = now,
                                    auditLog = lst.auditLog + AuditEntry(action = "Updated", at = now, summary = "Updated list (₹${formatAmount(autoSum)})")
                                )
                            } else lst
                        }
                    }
                    persist(snapshot.copy(lists = nextLists))
                    showListModal = false
                }
            )
        }

        if (showCalculatorSheet) {
            ModalBottomSheet(onDismissRequest = { showCalculatorSheet = false }) {
                CalculatorSheetContent(
                    history = snapshot.calculatorHistory,
                    onRecordHistory = { expr, result ->
                        val now = LedgerMath.nowIso()
                        val entry = CalcHistoryRecord(
                            expression = expr,
                            result = result,
                            createdAt = now,
                            updatedAt = now
                        )
                        persist(snapshot.copy(calculatorHistory = listOf(entry) + snapshot.calculatorHistory))
                    },
                    onSendToExpense = { result, formula, historyId ->
                        showCalculatorSheet = false
                        activeTab = LedgerTab.EXPENSES
                        editingExpense = null
                        prefillBalance = formatAmount(result)
                        prefillQuote = if (formula.isNotBlank()) "Calc: $formula" else "Calc: ${formatAmount(result)}"
                        prefillStatus = null
                        prefillLabelIds = emptyList()
                        prefillAttachedListIds = emptyList()
                        prefillCalcHistoryId = historyId ?: snapshot.calculatorHistory.firstOrNull()?.id
                        prefillCalcFormula = formula.ifBlank { null }
                        showExpenseModal = true
                    },
                    onInsertIntoList = { result, formula ->
                        showCalculatorSheet = false
                        activeTab = LedgerTab.LISTS
                        editingList = null
                        prefillListTitle = "Calculated Breakdown"
                        val prefix = formula.ifBlank { "Item" }
                        prefillListContent = "$prefix -> ₹${formatAmount(result)}"
                        showListModal = true
                    }
                )
            }
        }

        if (showShareDialog) {
            ShareSummaryDialog(
                appName = "Expense Ledger",
                snapshot = snapshot,
                initialLabelId = shareLabelId,
                onDismiss = { showShareDialog = false },
                onExportCsv = {
                    showShareDialog = false
                    exportCsvLauncher.launch("expense-ledger-export.csv")
                },
                onShareText = { summaryText ->
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, summaryText)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Ledger Summary"))
                    showShareDialog = false
                }
            )
        }

        if (showInsightsDialog) {
            InsightsDialog(
                snapshot = snapshot,
                onDismiss = { showInsightsDialog = false }
            )
        }

        if (showBinDialog) {
            RecycleBinDialog(
                snapshot = snapshot,
                onDismiss = { showBinDialog = false },
                onChangeRetention = { nextDays ->
                    val pruned = LedgerMath.pruneExpiredBin(snapshot.bin, nextDays)
                    persist(snapshot.copy(binRetentionDays = nextDays, bin = pruned))
                },
                onRestore = { record ->
                    val restored = dbHelper.restoreFromBin(snapshot, record)
                    persist(restored)
                    showActionSnackbar("Restored '${record.title}'")
                },
                onDeletePermanent = { recordId ->
                    persist(snapshot.copy(bin = snapshot.bin.filterNot { it.id == recordId }))
                    showActionSnackbar("Permanently deleted item")
                },
                onEmptyBin = {
                    val cleared = snapshot.bin.size
                    persist(snapshot.copy(bin = emptyList()))
                    showActionSnackbar("Permanently emptied $cleared items from Bin")
                }
            )
        }
    }
}

@Composable
private fun SummaryStatBox(label: String, amount: Double, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = accent.copy(alpha = 0.08f)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = accent)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "₹${formatAmount(amount)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ExpensesScreen(
    expenses: List<ExpenseRecord>,
    labels: List<LabelRecord>,
    lists: List<ListNoteRecord>,
    templates: List<ExpenseTemplateRecord>,
    searchQuery: String,
    statusFilter: ExpenseStatus?,
    labelFilterId: String?,
    bulkMode: Boolean,
    selectedExpenseIds: Set<String>,
    showQuickTemplates: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onStatusFilterChange: (ExpenseStatus?) -> Unit,
    onLabelFilterChange: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onToggleBulkMode: () -> Unit,
    onToggleSelectId: (String) -> Unit,
    onToggleSelectAll: (List<String>) -> Unit,
    onBulkStatus: (ExpenseStatus) -> Unit,
    onBulkMoveToBin: () -> Unit,
    onToggleQuickTemplates: () -> Unit,
    onLogTemplate: (ExpenseTemplateRecord) -> Unit,
    onMoveTemplateToBin: (String) -> Unit,
    onCycleStatus: (ExpenseRecord) -> Unit,
    onDuplicate: (ExpenseRecord) -> Unit,
    onEdit: (ExpenseRecord) -> Unit,
    onDelete: (String) -> Unit
) {
    val q = searchQuery.trim()
    val filteredExpenses = remember(expenses, labels, lists, q, statusFilter, labelFilterId) {
        expenses.filter { exp ->
            val matchStatus = statusFilter == null || exp.status == statusFilter
            val matchLabel = labelFilterId == null || exp.labelIds.contains(labelFilterId)
            val matchSearch = if (q.isBlank()) {
                true
            } else {
                val labelHit = exp.labelIds.any { id -> labels.firstOrNull { it.id == id }?.name?.contains(q, ignoreCase = true) == true }
                val listHit = exp.attachedListIds.any { id ->
                    val lst = lists.firstOrNull { it.id == id }
                    lst != null && (lst.title.contains(q, ignoreCase = true) || lst.content.contains(q, ignoreCase = true))
                }
                exp.quote.contains(q, ignoreCase = true) || formatAmount(exp.balance).contains(q, ignoreCase = true) || labelHit || listHit
            }
            matchStatus && matchLabel && matchSearch
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (bulkMode && selectedExpenseIds.isNotEmpty()) 88.dp else 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "expenses-filter-bar") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Search quotes, ₹ amounts, lists...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = bulkMode,
                            onClick = onToggleBulkMode,
                            leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            label = { Text(if (bulkMode) "Cancel Select" else "Select", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = statusFilter == null,
                            onClick = { onStatusFilterChange(null) },
                            label = { Text("All (${expenses.size})", fontSize = 11.sp) }
                        )
                        ExpenseStatus.entries.forEach { s ->
                            FilterChip(
                                selected = statusFilter == s,
                                onClick = { onStatusFilterChange(if (statusFilter == s) null else s) },
                                label = { Text(s.label, fontSize = 11.sp) }
                            )
                        }
                        labels.forEach { lbl ->
                            FilterChip(
                                selected = labelFilterId == lbl.id,
                                onClick = { onLabelFilterChange(if (labelFilterId == lbl.id) null else lbl.id) },
                                label = { Text(lbl.name, fontSize = 11.sp) }
                            )
                        }
                    }

                    if (templates.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { onToggleQuickTemplates() },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Quick Templates & Bills (${templates.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = if (showQuickTemplates) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Toggle templates",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                if (showQuickTemplates) {
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        templates.forEach { tpl ->
                                            val isDue = LedgerMath.isTemplateDue(tpl)
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surface,
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (isDue) Color(0xFFD97706) else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                modifier = Modifier.clickable { onLogTemplate(tpl) }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Column {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Text(tpl.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                            if (isDue) {
                                                                Text(
                                                                    text = "Due",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color(0xFFB45309),
                                                                    modifier = Modifier
                                                                        .background(Color(0xFFFEF3C7), RoundedCornerShape(4.dp))
                                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = "₹${formatAmount(tpl.balance)} · ${tpl.recurrence.label}",
                                                            fontSize = 10.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { onMoveTemplateToBin(tpl.id) },
                                                        modifier = Modifier.size(20.dp)
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "Move template to bin", modifier = Modifier.size(12.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (filteredExpenses.isEmpty()) {
                item(key = "expenses-empty-filter") {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("No matching expenses", fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Try clearing your search or status/label filters.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = onClearFilters) {
                                Text("Clear Filters")
                            }
                        }
                    }
                }
            }

            items(filteredExpenses, key = { it.id }) { exp ->
                var expandAudit by remember { mutableStateOf(false) }
                val attachedLists = exp.attachedListIds.mapNotNull { id -> lists.firstOrNull { it.id == id } }
                val isSelected = selectedExpenseIds.contains(exp.id)
                val statusColor = when (exp.status) {
                    ExpenseStatus.PAID -> Color(0xFF059669)
                    ExpenseStatus.HOLD -> Color(0xFFD97706)
                    ExpenseStatus.NEED -> Color(0xFFDC2626)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (bulkMode && isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (bulkMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onToggleSelectId(exp.id) }
                                    )
                                }
                                Text(
                                    text = "₹${formatAmount(exp.balance)}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = exp.status.label.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(statusColor.copy(alpha = 0.14f))
                                        .clickable { onCycleStatus(exp) }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                IconButton(onClick = { onDuplicate(exp) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(15.dp))
                                }
                                IconButton(onClick = { onEdit(exp) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { onDelete(exp.id) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Move to Bin", modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(exp.quote, style = MaterialTheme.typography.bodyMedium)

                        if (exp.labelIds.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                exp.labelIds.forEach { id ->
                                    val lbl = labels.firstOrNull { it.id == id }
                                    if (lbl != null) {
                                        val c = parseColorHex(lbl.colorHex)
                                        Text(
                                            text = lbl.name,
                                            fontSize = 11.sp,
                                            color = c,
                                            modifier = Modifier
                                                .background(c.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (!exp.calcFormula.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Math Tape: ${exp.calcFormula}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        attachedLists.forEach { attachedList ->
                            var expandList by remember(attachedList.id) { mutableStateOf(false) }
                            val listSum = remember(attachedList.content) { LedgerMath.extractNumbersSum(attachedList.content) }
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandList = !expandList },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "2nd Description: ${attachedList.title}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Icon(
                                            imageVector = if (expandList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    AnimatedVisibility(visible = expandList) {
                                        Column(modifier = Modifier.padding(top = 6.dp)) {
                                            Text(
                                                text = attachedList.content,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = "List Sum: ₹${formatAmount(listSum)} · Reference note only",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandAudit = !expandAudit },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Created: ${LedgerMath.formatCompactDate(exp.createdAt)} · Updated: ${LedgerMath.formatCompactDate(exp.updatedAt)}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = if (expandAudit) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle audit log",
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        AnimatedVisibility(visible = expandAudit) {
                            Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                exp.auditLog.forEach { entry ->
                                    Text(
                                        text = "${entry.action}: ${LedgerMath.formatReadableDate(entry.at)} — ${entry.summary}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (bulkMode) {
            val visibleIds = filteredExpenses.map { it.id }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedExpenseIds.size} picked",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { onToggleSelectAll(visibleIds) }) {
                        Text("All/None", fontSize = 11.sp)
                    }
                    FilledTonalButton(
                        enabled = selectedExpenseIds.isNotEmpty(),
                        onClick = { onBulkStatus(ExpenseStatus.PAID) }
                    ) { Text("Paid", fontSize = 11.sp) }
                    FilledTonalButton(
                        enabled = selectedExpenseIds.isNotEmpty(),
                        onClick = { onBulkStatus(ExpenseStatus.HOLD) }
                    ) { Text("Hold", fontSize = 11.sp) }
                    FilledTonalButton(
                        enabled = selectedExpenseIds.isNotEmpty(),
                        onClick = { onBulkStatus(ExpenseStatus.NEED) }
                    ) { Text("Need", fontSize = 11.sp) }
                    Button(
                        enabled = selectedExpenseIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        onClick = onBulkMoveToBin
                    ) { Text("Bin", fontSize = 11.sp) }
                }
            }
        }
    }
}

@Composable
private fun LabelsScreen(
    labels: List<LabelRecord>,
    expenses: List<ExpenseRecord>,
    onEditLabel: (LabelRecord) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onAddExpenseForLabel: (String) -> Unit,
    onShareLabel: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(labels, key = { it.id }) { lbl ->
            var expandAudit by remember { mutableStateOf(false) }
            val linked = expenses.filter { it.labelIds.contains(lbl.id) }
            val paid = linked.filter { it.status == ExpenseStatus.PAID }.sumOf { it.balance }
            val hold = linked.filter { it.status == ExpenseStatus.HOLD }.sumOf { it.balance }
            val need = linked.filter { it.status == ExpenseStatus.NEED }.sumOf { it.balance }
            val spentTotal = paid + hold + need
            val accent = parseColorHex(lbl.colorHex)

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(accent))
                            Text(lbl.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Row {
                            TextButton(onClick = { onAddExpenseForLabel(lbl.id) }) {
                                Text("+ Expense")
                            }
                            IconButton(onClick = { onShareLabel(lbl.id) }) {
                                Icon(Icons.Default.Share, contentDescription = "Share label summary", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onEditLabel(lbl) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit label", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onDeleteLabel(lbl.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Move label to Bin", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    if (lbl.description.isNotBlank()) {
                        Text(lbl.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Paid ₹${formatAmount(paid)} · Hold ₹${formatAmount(hold)} · Need ₹${formatAmount(need)}",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (lbl.monthlyBudget != null && lbl.monthlyBudget > 0.0) {
                        val ratio = (spentTotal / lbl.monthlyBudget).toFloat().coerceIn(0f, 1f)
                        val over = spentTotal > lbl.monthlyBudget
                        val warn = !over && (spentTotal / lbl.monthlyBudget) >= 0.8
                        val barColor = when {
                            over -> Color(0xFFDC2626)
                            warn -> Color(0xFFD97706)
                            else -> Color(0xFF059669)
                        }
                        val statusText = when {
                            over -> "Over by ₹${formatAmount(spentTotal - lbl.monthlyBudget)}"
                            warn -> "Near limit"
                            else -> "Within budget"
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Cap ₹${formatAmount(lbl.monthlyBudget)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            Text(statusText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = barColor)
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                            color = barColor
                        )
                    }
                    if (linked.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        linked.forEach { e ->
                            Text(
                                text = "• ${e.quote} — ₹${formatAmount(e.balance)} (${e.status.label})",
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Audit Log (${lbl.auditLog.size}) — Created ${LedgerMath.formatReadableDate(lbl.createdAt)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { expandAudit = !expandAudit }
                    )
                    AnimatedVisibility(visible = expandAudit) {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            lbl.auditLog.forEach { entry ->
                                Text(
                                    text = "${entry.action}: ${LedgerMath.formatReadableDate(entry.at)}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListsScreen(
    lists: List<ListNoteRecord>,
    onEditList: (ListNoteRecord) -> Unit,
    onDeleteList: (String) -> Unit,
    onUseInExpense: (ListNoteRecord, Double) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(lists, key = { it.id }) { lst ->
            var expandAudit by remember { mutableStateOf(false) }
            val detectedSum = remember(lst.content) { LedgerMath.extractNumbersSum(lst.content) }
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(lst.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(
                            text = "Sum ₹${formatAmount(detectedSum)}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(lst.content, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onUseInExpense(lst, detectedSum) }) {
                            Text("Use in New Expense")
                        }
                        OutlinedButton(onClick = { onEditList(lst) }) {
                            Text("Edit")
                        }
                        IconButton(onClick = { onDeleteList(lst.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Move list to Bin", modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Audit Log (${lst.auditLog.size}) — Created ${LedgerMath.formatReadableDate(lst.createdAt)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { expandAudit = !expandAudit }
                    )
                    AnimatedVisibility(visible = expandAudit) {
                        Column(modifier = Modifier.padding(top = 4.dp)) {
                            lst.auditLog.forEach { entry ->
                                Text(
                                    text = "${entry.action}: ${LedgerMath.formatReadableDate(entry.at)}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseEditorDialog(
    existing: ExpenseRecord?,
    initialBalance: String,
    initialQuote: String,
    initialStatus: ExpenseStatus?,
    initialLabelIds: List<String>,
    initialAttachedListIds: List<String>,
    calcFormula: String?,
    labels: List<LabelRecord>,
    lists: List<ListNoteRecord>,
    onDismiss: () -> Unit,
    onSave: (Double, String, ExpenseStatus, List<String>, List<String>, Boolean, RecurrenceCycle) -> Unit
) {
    var balanceText by remember { mutableStateOf(initialBalance) }
    var quoteText by remember { mutableStateOf(initialQuote) }
    var status by remember { mutableStateOf(existing?.status ?: initialStatus ?: ExpenseStatus.NEED) }
    var selectedLabels by remember { mutableStateOf(initialLabelIds.toSet()) }
    var attachedListIds by remember { mutableStateOf(initialAttachedListIds.toSet()) }
    var saveAsTemplate by remember { mutableStateOf(false) }
    var recurrence by remember { mutableStateOf(RecurrenceCycle.MONTHLY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Expense" else "Edit Expense") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("Balance (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = quoteText,
                    onValueChange = { quoteText = it },
                    label = { Text("Quote / Description") }
                )
                if (!calcFormula.isNullOrBlank()) {
                    Text(
                        text = "Math Tape: $calcFormula",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("Status", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpenseStatus.entries.forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick = { status = s },
                            label = { Text(s.label) }
                        )
                    }
                }
                Text("Labels", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    labels.forEach { lbl ->
                        val picked = selectedLabels.contains(lbl.id)
                        FilterChip(
                            selected = picked,
                            onClick = {
                                selectedLabels = if (picked) selectedLabels - lbl.id else selectedLabels + lbl.id
                            },
                            label = { Text(lbl.name) }
                        )
                    }
                }
                if (lists.isNotEmpty()) {
                    Text("Attach Lists (2nd Description · Optional)", style = MaterialTheme.typography.labelMedium)
                    lists.forEach { lst ->
                        val picked = attachedListIds.contains(lst.id)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                attachedListIds = if (picked) attachedListIds - lst.id else attachedListIds + lst.id
                            }
                        ) {
                            Checkbox(
                                checked = picked,
                                onCheckedChange = { checked ->
                                    attachedListIds = if (checked) attachedListIds + lst.id else attachedListIds - lst.id
                                }
                            )
                            Text(lst.title, fontSize = 13.sp)
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { saveAsTemplate = !saveAsTemplate }
                ) {
                    Checkbox(checked = saveAsTemplate, onCheckedChange = { saveAsTemplate = it })
                    Text("Also save as Quick Template", fontSize = 13.sp)
                }
                if (saveAsTemplate) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RecurrenceCycle.entries.forEach { cycle ->
                            FilterChip(
                                selected = recurrence == cycle,
                                onClick = { recurrence = cycle },
                                label = { Text(cycle.label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = balanceText.toDoubleOrNull() ?: 0.0
                    onSave(
                        amount,
                        quoteText.ifBlank { "Untitled Expense" },
                        status,
                        selectedLabels.toList(),
                        attachedListIds.toList(),
                        saveAsTemplate,
                        recurrence
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun LabelEditorDialog(
    existing: LabelRecord?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Double?) -> Unit
) {
    var name by remember { mutableStateOf(existing?.name ?: "#") }
    var desc by remember { mutableStateOf(existing?.description ?: "") }
    var budgetText by remember { mutableStateOf(existing?.monthlyBudget?.let { formatAmount(it) } ?: "") }
    var colorHex by remember { mutableStateOf(existing?.colorHex ?: "#2563EB") }
    val palette = listOf("#2563EB", "#059669", "#D97706", "#7C3AED", "#DC2626", "#0D9488")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Label" else "Edit Label") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetText = it },
                    label = { Text("Monthly Budget Cap (₹ · Optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(parseColorHex(hex))
                                .border(if (colorHex == hex) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { colorHex = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cap = budgetText.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
                    onSave(name.ifBlank { "#Label" }, desc, colorHex, cap)
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ListEditorDialog(
    existing: ListNoteRecord?,
    initialTitle: String,
    initialContent: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: initialTitle) }
    var content by remember { mutableStateOf(existing?.content ?: initialContent) }
    val previewSum = remember(content) { LedgerMath.extractNumbersSum(content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New List Note" else "Edit List Note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("List Title") }, singleLine = true)
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Items (e.g. Wood -> ₹5)") },
                    minLines = 4
                )
                Text("Detected Auto-Sum: ₹${formatAmount(previewSum)}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title.ifBlank { "Untitled List" }, content) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CalculatorSheetContent(
    history: List<CalcHistoryRecord>,
    onRecordHistory: (String, Double) -> Unit,
    onSendToExpense: (Double, String, String?) -> Unit,
    onInsertIntoList: (Double, String) -> Unit
) {
    var display by remember { mutableStateOf("0") }
    var accumulator by remember { mutableStateOf<Double?>(null) }
    var pendingOp by remember { mutableStateOf<String?>(null) }
    var waitingNext by remember { mutableStateOf(false) }
    var formulaPreview by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }

    fun pressKey(key: String) {
        when (key) {
            "AC" -> {
                display = "0"
                accumulator = null
                pendingOp = null
                waitingNext = false
                formulaPreview = ""
            }
            "C" -> {
                display = "0"
                waitingNext = false
            }
            "00.0" -> {
                display = if (waitingNext || display == "0") "0.0" else if (display.contains(".")) display + "00" else display + "00.0"
                waitingNext = false
            }
            "%" -> {
                val cur = display.toDoubleOrNull() ?: 0.0
                val nextVal = if (accumulator != null && (pendingOp == "+" || pendingOp == "-")) (accumulator!! * cur) / 100.0 else cur / 100.0
                display = formatAmount(nextVal)
            }
            "+", "-", "×", "÷" -> {
                val cur = display.toDoubleOrNull() ?: 0.0
                if (accumulator == null) {
                    accumulator = cur
                } else if (!waitingNext && pendingOp != null) {
                    val res = when (pendingOp) {
                        "+" -> accumulator!! + cur
                        "-" -> accumulator!! - cur
                        "×" -> accumulator!! * cur
                        "÷" -> if (cur == 0.0) 0.0 else accumulator!! / cur
                        else -> cur
                    }
                    accumulator = res
                    display = formatAmount(res)
                }
                pendingOp = key
                waitingNext = true
                formulaPreview = "${formatAmount(accumulator!!)} $key"
            }
            "=" -> {
                val cur = display.toDoubleOrNull() ?: 0.0
                if (accumulator != null && pendingOp != null) {
                    val expr = "${formatAmount(accumulator!!)} $pendingOp ${formatAmount(cur)}"
                    val res = when (pendingOp) {
                        "+" -> accumulator!! + cur
                        "-" -> accumulator!! - cur
                        "×" -> accumulator!! * cur
                        "÷" -> if (cur == 0.0) 0.0 else accumulator!! / cur
                        else -> cur
                    }
                    display = formatAmount(res)
                    formulaPreview = "$expr = $display"
                    onRecordHistory(expr, res)
                    accumulator = null
                    pendingOp = null
                    waitingNext = true
                }
            }
            "." -> {
                if (waitingNext) {
                    display = "0."
                    waitingNext = false
                } else if (!display.contains(".")) {
                    display += "."
                }
            }
            else -> {
                if (waitingNext || display == "0") {
                    display = key
                    waitingNext = false
                } else {
                    display += key
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Quick Calculator", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { showHistory = !showHistory }) {
                Text(if (showHistory) "Keypad" else "History (${history.size})")
            }
        }

        if (showHistory) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                history.forEach { h ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${h.expression} = ₹${formatAmount(h.result)}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Created: ${LedgerMath.formatReadableDate(h.createdAt)} · Updated: ${LedgerMath.formatReadableDate(h.updatedAt)}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onSendToExpense(h.result, "${h.expression} = ${formatAmount(h.result)}", h.id) }) {
                                    Text("Send to Expense", fontSize = 11.sp)
                                }
                                OutlinedButton(onClick = { onInsertIntoList(h.result, h.expression) }) {
                                    Text("Insert into List", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Text(formulaPreview, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "₹$display",
                fontFamily = FontFamily.Monospace,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.End)
            )

            val rows = listOf(
                listOf("AC", "C", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "-"),
                listOf("1", "2", "3", "+"),
                listOf("00.0", "0", ".", "=")
            )
            rows.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { k ->
                        FilledTonalButton(
                            onClick = { pressKey(k) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(k, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val valNum = display.toDoubleOrNull() ?: 0.0
                    val expr = if (formulaPreview.isNotBlank()) formulaPreview else "Calc Item"
                    onInsertIntoList(valNum, expr)
                },
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Text("Insert into List", fontSize = 12.sp)
            }
            Button(
                onClick = {
                    val valNum = display.toDoubleOrNull() ?: 0.0
                    val formula = if (formulaPreview.isNotBlank()) formulaPreview else display
                    onSendToExpense(valNum, formula, null)
                },
                modifier = Modifier.weight(1.2f).height(46.dp)
            ) {
                Text("Send to Expense (₹$display)", fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ShareSummaryDialog(
    appName: String,
    snapshot: LedgerSnapshot,
    initialLabelId: String?,
    onDismiss: () -> Unit,
    onExportCsv: () -> Unit,
    onShareText: (String) -> Unit
) {
    var selectedLabelId by remember(initialLabelId) { mutableStateOf(initialLabelId) }
    var selectedMonthKey by remember { mutableStateOf<String?>(null) }
    val months = remember(snapshot.expenses) { LedgerMath.availableMonths(snapshot.expenses) }
    val summaryText = remember(snapshot, selectedLabelId, selectedMonthKey) {
        LedgerMath.buildShareSummary(snapshot, appName, selectedLabelId, selectedMonthKey)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share & Export") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Scope", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedLabelId == null,
                        onClick = { selectedLabelId = null },
                        label = { Text("All Expenses", fontSize = 11.sp) }
                    )
                    snapshot.labels.forEach { lbl ->
                        FilterChip(
                            selected = selectedLabelId == lbl.id,
                            onClick = { selectedLabelId = lbl.id },
                            label = { Text(lbl.name, fontSize = 11.sp) }
                        )
                    }
                }

                Text("Month", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedMonthKey == null,
                        onClick = { selectedMonthKey = null },
                        label = { Text("All Time", fontSize = 11.sp) }
                    )
                    months.forEach { month ->
                        FilterChip(
                            selected = selectedMonthKey == month,
                            onClick = { selectedMonthKey = month },
                            label = { Text(month, fontSize = 11.sp) }
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = summaryText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export CSV Spreadsheet (.csv)")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onShareText(summaryText) }) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share Text")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun InsightsDialog(
    snapshot: LedgerSnapshot,
    onDismiss: () -> Unit
) {
    val initialMonth = remember(snapshot.expenses) {
        LedgerMath.availableMonths(snapshot.expenses).firstOrNull() ?: LedgerMath.nowIso().take(7)
    }
    var monthKey by remember { mutableStateOf(initialMonth) }
    var activeSection by remember { mutableStateOf(0) } // 0 = Budgets, 1 = Calendar
    var selectedDateKey by remember { mutableStateOf<String?>(null) }

    val monthExpenses = remember(snapshot.expenses, monthKey) {
        snapshot.expenses.filter { it.createdAt.startsWith(monthKey) }
    }
    val monthTotal = remember(monthExpenses) { monthExpenses.sumOf { it.balance } }
    val calendarPair = remember(snapshot.expenses, monthKey) {
        LedgerMath.buildCalendarGrid(snapshot.expenses, monthKey)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insights & Calendar") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        monthKey = LedgerMath.stepMonthKey(monthKey, -1)
                        selectedDateKey = null
                    }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                    }
                    Text(monthKey, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    IconButton(onClick = {
                        monthKey = LedgerMath.stepMonthKey(monthKey, 1)
                        selectedDateKey = null
                    }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = activeSection == 0, onClick = { activeSection = 0 }, label = { Text("Budgets") })
                    FilterChip(selected = activeSection == 1, onClick = { activeSection = 1 }, label = { Text("Calendar") })
                }

                if (activeSection == 0) {
                    Text(
                        text = "Monthly Spend Total: ₹${formatAmount(monthTotal)} (${monthExpenses.size} entries)",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                    snapshot.labels.forEach { lbl ->
                        val spent = monthExpenses.filter { it.labelIds.contains(lbl.id) }.sumOf { it.balance }
                        val cap = lbl.monthlyBudget
                        val ratio = when {
                            cap != null && cap > 0.0 -> (spent / cap).toFloat().coerceIn(0f, 1f)
                            monthTotal > 0.0 -> (spent / monthTotal).toFloat().coerceIn(0f, 1f)
                            else -> 0f
                        }
                        val over = cap != null && spent > cap
                        val warn = cap != null && !over && (spent / cap) >= 0.8
                        val barColor = when {
                            over -> Color(0xFFDC2626)
                            warn -> Color(0xFFD97706)
                            else -> parseColorHex(lbl.colorHex)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(lbl.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (cap != null) "₹${formatAmount(spent)} / ₹${formatAmount(cap)}" else "₹${formatAmount(spent)}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                                color = barColor
                            )
                        }
                    }
                } else {
                    val (firstWeekdayMon0, cells) = calendarPair
                    val totalSlots = firstWeekdayMon0 + cells.size
                    val rowCount = (totalSlots + 6) / 7
                    val weekdays = listOf("M", "T", "W", "T", "F", "S", "S")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        weekdays.forEach { d ->
                            Text(
                                text = d,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    for (r in 0 until rowCount) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (c in 0 until 7) {
                                val slot = r * 7 + c
                                val idx = slot - firstWeekdayMon0
                                if (idx < 0 || idx >= cells.size) {
                                    Spacer(modifier = Modifier.weight(1f).height(38.dp))
                                } else {
                                    val cell = cells[idx]
                                    val picked = selectedDateKey == cell.dateKey
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .clickable { selectedDateKey = if (picked) null else cell.dateKey },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (picked) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else if (cell.total > 0.0) Color(0xFFDBEAFE) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(2.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text("${cell.day}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            if (cell.total > 0.0) {
                                                Text("₹${formatAmount(cell.total)}", fontSize = 8.sp, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val pickedCell = cells.firstOrNull { it.dateKey == selectedDateKey }
                    if (pickedCell != null) {
                        Text(
                            text = "${pickedCell.dateKey}: ₹${formatAmount(pickedCell.total)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (pickedCell.expenses.isEmpty()) {
                            Text("No transactions recorded on this day.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            pickedCell.expenses.forEach { e ->
                                Text(
                                    text = "• ₹${formatAmount(e.balance)} (${e.status.label}) — ${e.quote}",
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun RecycleBinDialog(
    snapshot: LedgerSnapshot,
    onDismiss: () -> Unit,
    onChangeRetention: (Int?) -> Unit,
    onRestore: (BinnedRecord) -> Unit,
    onDeletePermanent: (String) -> Unit,
    onEmptyBin: () -> Unit
) {
    var confirmEmpty by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recycle Bin (${snapshot.bin.size})") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Auto-Purge Retention", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf<Pair<String, Int?>>("30 Days" to 30, "60 Days" to 60, "90 Days" to 90, "Manual Only" to null).forEach { (title, days) ->
                        FilterChip(
                            selected = snapshot.binRetentionDays == days,
                            onClick = { onChangeRetention(days) },
                            label = { Text(title, fontSize = 11.sp) }
                        )
                    }
                }

                if (snapshot.bin.isEmpty()) {
                    Text(
                        text = "Recycle Bin is empty. Deleted items stay here for your selected retention period before auto-purge.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    snapshot.bin.forEach { item ->
                        val daysLeft = LedgerMath.daysRemainingInBin(item.deletedAt, snapshot.binRetentionDays)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    if (item.amount != null) {
                                        Text("₹${formatAmount(item.amount)}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                                Text(item.subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = if (daysLeft == null) "Manual retention" else "Purges in $daysLeft d",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFB45309)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { onRestore(item) }) {
                                        Icon(Icons.Default.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Restore", fontSize = 11.sp)
                                    }
                                    TextButton(onClick = { onDeletePermanent(item.id) }) {
                                        Text("Delete Permanently", fontSize = 11.sp, color = Color(0xFFDC2626))
                                    }
                                }
                            }
                        }
                    }

                    if (!confirmEmpty) {
                        OutlinedButton(
                            onClick = { confirmEmpty = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Empty Bin Now", color = Color(0xFFDC2626))
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    confirmEmpty = false
                                    onEmptyBin()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Confirm Purge")
                            }
                            TextButton(onClick = { confirmEmpty = false }) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
