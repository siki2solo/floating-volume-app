package com.example.floatingvolume

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvPermissionStatus: TextView
    private lateinit var tvAutostartStatus: TextView
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnAutostartPermission: Button
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button

    private lateinit var presetContainer: LinearLayout
    private lateinit var btnNewPreset: Button
    private lateinit var btnResetPresets: Button
    private lateinit var btnExportBackup: Button
    private lateinit var btnImportBackup: Button

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchDarkTheme: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchSwipeScrub: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchSingleTapSlider: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchHideFullscreen: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchHapticTap: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchSnapToEdge: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchDoubleTapMute: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchHoldRepeat: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchAutoBoot: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchLockPosition: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchRotateToast: Switch

    private lateinit var radioGroupStream: RadioGroup
    private lateinit var radioGroupStepSize: RadioGroup
    private lateinit var radioGroupShape: RadioGroup
    private lateinit var radioGroupAutoDim: RadioGroup
    private lateinit var seekBarMaxLimit: SeekBar
    private lateinit var tvMaxLimitValue: TextView
    private lateinit var seekBarWidth: SeekBar
    private lateinit var seekBarHeight: SeekBar
    private lateinit var seekBarTransparency: SeekBar
    private lateinit var seekBarDimOpacity: SeekBar
    private lateinit var tvWidthValue: TextView
    private lateinit var tvHeightValue: TextView
    private lateinit var tvTransparencyValue: TextView
    private lateinit var tvDimOpacityValue: TextView

    private lateinit var viewColorPreview: View
    private lateinit var tvColorHex: TextView
    private lateinit var seekBarRed: SeekBar
    private lateinit var seekBarGreen: SeekBar
    private lateinit var seekBarBlue: SeekBar

    private lateinit var tvCurrentOrientation: TextView
    private lateinit var radioGroupOrientTarget: RadioGroup
    private lateinit var previewScreenFrame: FrameLayout
    private lateinit var previewDot: View
    private lateinit var tvPreviewHint: TextView
    private lateinit var tvPosXLabel: TextView
    private lateinit var tvPosYLabel: TextView
    private lateinit var seekBarPosX: SeekBar
    private lateinit var seekBarPosY: SeekBar
    private lateinit var tvSavedPositions: TextView
    private lateinit var btnTestRotate: Button

    private var updatingColorSliders = false
    private var updatingPositionSliders = false
    private var syncingViews = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionUi()
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    private val autostartPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionUi()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        launchFloatingService()
    }

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            writeBackupToUri(uri)
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            readBackupFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences(FloatingVolumeService.PREFS_NAME, Context.MODE_PRIVATE)
        val useDark = prefs.getBoolean(FloatingVolumeService.KEY_DARK_THEME, true)
        AppCompatDelegate.setDefaultNightMode(
            if (useDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        ensureDefaultPositionsExist()
        ensurePresetsExist()
        loadSavedSettingsIntoViews()
        setupListeners()
        updatePermissionUi()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
        updateCurrentOrientationLabel()
        refreshSavedPositionsText()
        syncPositionSlidersForSelectedTarget()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateCurrentOrientationLabel()
    }

    private fun bindViews() {
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        tvAutostartStatus = findViewById(R.id.tvAutostartStatus)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnAutostartPermission = findViewById(R.id.btnAutostartPermission)
        btnStartService = findViewById(R.id.btnStartService)
        btnStopService = findViewById(R.id.btnStopService)

        presetContainer = findViewById(R.id.presetContainer)
        btnNewPreset = findViewById(R.id.btnNewPreset)
        btnResetPresets = findViewById(R.id.btnResetPresets)
        btnExportBackup = findViewById(R.id.btnExportBackup)
        btnImportBackup = findViewById(R.id.btnImportBackup)

        switchDarkTheme = findViewById(R.id.switchDarkTheme)
        switchSwipeScrub = findViewById(R.id.switchSwipeScrub)
        switchSingleTapSlider = findViewById(R.id.switchSingleTapSlider)
        switchHideFullscreen = findViewById(R.id.switchHideFullscreen)
        switchHapticTap = findViewById(R.id.switchHapticTap)
        switchSnapToEdge = findViewById(R.id.switchSnapToEdge)
        switchDoubleTapMute = findViewById(R.id.switchDoubleTapMute)
        switchHoldRepeat = findViewById(R.id.switchHoldRepeat)
        switchAutoBoot = findViewById(R.id.switchAutoBoot)
        switchLockPosition = findViewById(R.id.switchLockPosition)
        switchRotateToast = findViewById(R.id.switchRotateToast)

        radioGroupStream = findViewById(R.id.radioGroupStream)
        radioGroupStepSize = findViewById(R.id.radioGroupStepSize)
        radioGroupShape = findViewById(R.id.radioGroupShape)
        radioGroupAutoDim = findViewById(R.id.radioGroupAutoDim)
        seekBarMaxLimit = findViewById(R.id.seekBarMaxLimit)
        tvMaxLimitValue = findViewById(R.id.tvMaxLimitValue)
        seekBarWidth = findViewById(R.id.seekBarWidth)
        seekBarHeight = findViewById(R.id.seekBarHeight)
        seekBarTransparency = findViewById(R.id.seekBarTransparency)
        seekBarDimOpacity = findViewById(R.id.seekBarDimOpacity)
        tvWidthValue = findViewById(R.id.tvWidthValue)
        tvHeightValue = findViewById(R.id.tvHeightValue)
        tvTransparencyValue = findViewById(R.id.tvTransparencyValue)
        tvDimOpacityValue = findViewById(R.id.tvDimOpacityValue)

        viewColorPreview = findViewById(R.id.viewColorPreview)
        tvColorHex = findViewById(R.id.tvColorHex)
        seekBarRed = findViewById(R.id.seekBarRed)
        seekBarGreen = findViewById(R.id.seekBarGreen)
        seekBarBlue = findViewById(R.id.seekBarBlue)

        tvCurrentOrientation = findViewById(R.id.tvCurrentOrientation)
        radioGroupOrientTarget = findViewById(R.id.radioGroupOrientTarget)
        previewScreenFrame = findViewById(R.id.previewScreenFrame)
        previewDot = findViewById(R.id.previewDot)
        tvPreviewHint = findViewById(R.id.tvPreviewHint)
        tvPosXLabel = findViewById(R.id.tvPosXLabel)
        tvPosYLabel = findViewById(R.id.tvPosYLabel)
        seekBarPosX = findViewById(R.id.seekBarPosX)
        seekBarPosY = findViewById(R.id.seekBarPosY)
        tvSavedPositions = findViewById(R.id.tvSavedPositions)
        btnTestRotate = findViewById(R.id.btnTestRotate)
    }

    private fun ensureDefaultPositionsExist() {
        val editor = prefs.edit()
        var changed = false
        if (!prefs.contains(FloatingVolumeService.KEY_POS_X_PORTRAIT)) {
            val (maxPX, maxPY) = getMaxBoundsForOrientation(false)
            editor.putInt(FloatingVolumeService.KEY_POS_X_PORTRAIT, (maxPX * 0.88f).toInt())
            editor.putInt(FloatingVolumeService.KEY_POS_Y_PORTRAIT, (maxPY * 0.38f).toInt())
            changed = true
        }
        if (!prefs.contains(FloatingVolumeService.KEY_POS_X_LANDSCAPE)) {
            val (maxLX, maxLY) = getMaxBoundsForOrientation(true)
            editor.putInt(FloatingVolumeService.KEY_POS_X_LANDSCAPE, (maxLX * 0.10f).toInt())
            editor.putInt(FloatingVolumeService.KEY_POS_Y_LANDSCAPE, (maxLY * 0.25f).toInt())
            changed = true
        }
        if (changed) editor.apply()
    }

    private fun buildDefaultPresetsArray(): JSONArray {
        return JSONArray().apply {
            put(buildPresetJson(
                id = "gaming",
                name = "Gaming",
                widthDp = 44,
                heightDp = 96,
                transparencyPct = 55,
                shape = "line",
                colorHex = "#06B6D4",
                audioStream = "music",
                hapticTap = true,
                snapToEdge = true,
                doubleTapMute = false,
                holdToRepeat = true,
                autoDimSec = 5,
                dimOpacityPct = 15,
                volumeStep = 1,
                maxVolumeLimitPct = 100,
                swipeScrub = true,
                popoutMode = "swipe_inward",
                hideInFullscreen = false
            ))
            put(buildPresetJson(
                id = "bedtime",
                name = "Bedtime",
                widthDp = 52,
                heightDp = 112,
                transparencyPct = 28,
                shape = "circle",
                colorHex = "#10B981",
                audioStream = "music",
                hapticTap = true,
                snapToEdge = true,
                doubleTapMute = true,
                holdToRepeat = true,
                autoDimSec = 3,
                dimOpacityPct = 5,
                volumeStep = 1,
                maxVolumeLimitPct = 60,
                swipeScrub = true,
                popoutMode = "swipe_inward",
                hideInFullscreen = false
            ))
            put(buildPresetJson(
                id = "stealth",
                name = "Stealth",
                widthDp = 58,
                heightDp = 124,
                transparencyPct = 0,
                shape = "line",
                colorHex = "#2563EB",
                audioStream = "music",
                hapticTap = true,
                snapToEdge = true,
                doubleTapMute = false,
                holdToRepeat = true,
                autoDimSec = 0,
                dimOpacityPct = 0,
                volumeStep = 1,
                maxVolumeLimitPct = 100,
                swipeScrub = true,
                popoutMode = "swipe_inward",
                hideInFullscreen = true
            ))
        }
    }

    private fun buildPresetJson(
        id: String,
        name: String,
        widthDp: Int,
        heightDp: Int,
        transparencyPct: Int,
        shape: String,
        colorHex: String,
        audioStream: String,
        hapticTap: Boolean,
        snapToEdge: Boolean,
        doubleTapMute: Boolean,
        holdToRepeat: Boolean,
        autoDimSec: Int,
        dimOpacityPct: Int,
        volumeStep: Int,
        maxVolumeLimitPct: Int,
        swipeScrub: Boolean,
        popoutMode: String,
        hideInFullscreen: Boolean
    ): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("widthDp", widthDp)
            put("heightDp", heightDp)
            put("transparencyPct", transparencyPct)
            put("shape", shape)
            put("colorHex", colorHex)
            put("audioStream", audioStream)
            put("hapticTap", hapticTap)
            put("snapToEdge", snapToEdge)
            put("doubleTapMute", doubleTapMute)
            put("holdToRepeat", holdToRepeat)
            put("autoDimSeconds", autoDimSec)
            put("dimOpacityPct", dimOpacityPct)
            put("volumeStep", volumeStep)
            put("maxVolumeLimitPct", maxVolumeLimitPct)
            put("swipeScrub", swipeScrub)
            put("popoutSliderMode", popoutMode)
            put("hideInFullscreen", hideInFullscreen)
        }
    }

    private fun ensurePresetsExist() {
        if (!prefs.contains(KEY_PRESETS_JSON)) {
            prefs.edit().putString(KEY_PRESETS_JSON, buildDefaultPresetsArray().toString()).apply()
        }
    }

    private fun getSavedPresetsArray(): JSONArray {
        val raw = prefs.getString(KEY_PRESETS_JSON, null)
        return try {
            if (raw.isNullOrBlank()) buildDefaultPresetsArray() else JSONArray(raw)
        } catch (_: Exception) {
            buildDefaultPresetsArray()
        }
    }

    private fun savePresetsArray(arr: JSONArray) {
        prefs.edit().putString(KEY_PRESETS_JSON, arr.toString()).apply()
        renderPresetButtons()
    }

    private fun renderPresetButtons() {
        presetContainer.removeAllViews()
        val arr = getSavedPresetsArray()
        val activeId = prefs.getString(KEY_ACTIVE_PRESET_ID, "")
        val marginPx = (6 * resources.displayMetrics.density).toInt()

        for (i in 0 until arr.length()) {
            val presetObj = arr.optJSONObject(i) ?: continue
            val id = presetObj.optString("id", "preset_$i")
            val name = presetObj.optString("name", "Preset")
            val btn = Button(this).apply {
                text = if (id == activeId) "* $name" else name
                textSize = 12f
                isAllCaps = false
                setOnClickListener {
                    applyPresetObject(presetObj)
                }
                setOnLongClickListener {
                    showPresetEditorDialog(i, presetObj)
                    true
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = marginPx
            }
            presetContainer.addView(btn, lp)
        }
    }

    private fun applyPresetObject(obj: JSONObject) {
        val colorStr = obj.optString("colorHex", "#2563EB")
        val parsedColor = try { Color.parseColor(colorStr) } catch (_: Exception) { FloatingVolumeService.DEFAULT_COLOR }

        prefs.edit()
            .putString(KEY_ACTIVE_PRESET_ID, obj.optString("id", ""))
            .putInt(FloatingVolumeService.KEY_WIDTH_DP, obj.optInt("widthDp", FloatingVolumeService.DEFAULT_WIDTH_DP))
            .putInt(FloatingVolumeService.KEY_HEIGHT_DP, obj.optInt("heightDp", FloatingVolumeService.DEFAULT_HEIGHT_DP))
            .putInt(FloatingVolumeService.KEY_TRANSPARENCY_PCT, obj.optInt("transparencyPct", FloatingVolumeService.DEFAULT_TRANSPARENCY_PCT))
            .putString(FloatingVolumeService.KEY_SHAPE, obj.optString("shape", FloatingVolumeService.SHAPE_CIRCLE))
            .putInt(FloatingVolumeService.KEY_OVERLAY_COLOR, parsedColor)
            .putString(FloatingVolumeService.KEY_AUDIO_STREAM, obj.optString("audioStream", FloatingVolumeService.STREAM_MUSIC))
            .putBoolean(FloatingVolumeService.KEY_HAPTIC_TAP, obj.optBoolean("hapticTap", true))
            .putBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, obj.optBoolean("snapToEdge", false))
            .putBoolean(FloatingVolumeService.KEY_DOUBLE_TAP_MUTE, obj.optBoolean("doubleTapMute", false))
            .putBoolean(FloatingVolumeService.KEY_HOLD_TO_REPEAT, obj.optBoolean("holdToRepeat", true))
            .putInt(FloatingVolumeService.KEY_AUTO_DIM_SEC, obj.optInt("autoDimSeconds", 0))
            .putInt(FloatingVolumeService.KEY_DIM_OPACITY_PCT, obj.optInt("dimOpacityPct", 15))
            .putInt(FloatingVolumeService.KEY_VOLUME_STEP, obj.optInt("volumeStep", 1))
            .putInt(FloatingVolumeService.KEY_MAX_VOLUME_LIMIT_PCT, obj.optInt("maxVolumeLimitPct", 100))
            .putBoolean(FloatingVolumeService.KEY_SWIPE_SCRUB, obj.optBoolean("swipeScrub", true))
            .putString(FloatingVolumeService.KEY_POPOUT_MODE, obj.optString("popoutSliderMode", FloatingVolumeService.POPOUT_SWIPE_INWARD))
            .putBoolean(FloatingVolumeService.KEY_HIDE_FULLSCREEN, obj.optBoolean("hideInFullscreen", false))
            .apply()

        loadSavedSettingsIntoViews()
        Toast.makeText(this, "Applied ${obj.optString("name", "Preset")}", Toast.LENGTH_SHORT).show()
    }

    private fun captureCurrentSettingsAsPreset(id: String, name: String): JSONObject {
        val colorInt = prefs.getInt(FloatingVolumeService.KEY_OVERLAY_COLOR, FloatingVolumeService.DEFAULT_COLOR)
        val colorHex = String.format("#%06X", 0xFFFFFF and colorInt)
        return buildPresetJson(
            id = id,
            name = name,
            widthDp = prefs.getInt(FloatingVolumeService.KEY_WIDTH_DP, FloatingVolumeService.DEFAULT_WIDTH_DP),
            heightDp = prefs.getInt(FloatingVolumeService.KEY_HEIGHT_DP, FloatingVolumeService.DEFAULT_HEIGHT_DP),
            transparencyPct = prefs.getInt(FloatingVolumeService.KEY_TRANSPARENCY_PCT, FloatingVolumeService.DEFAULT_TRANSPARENCY_PCT),
            shape = prefs.getString(FloatingVolumeService.KEY_SHAPE, FloatingVolumeService.SHAPE_CIRCLE) ?: FloatingVolumeService.SHAPE_CIRCLE,
            colorHex = colorHex,
            audioStream = prefs.getString(FloatingVolumeService.KEY_AUDIO_STREAM, FloatingVolumeService.DEFAULT_STREAM) ?: FloatingVolumeService.STREAM_MUSIC,
            hapticTap = prefs.getBoolean(FloatingVolumeService.KEY_HAPTIC_TAP, true),
            snapToEdge = prefs.getBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, false),
            doubleTapMute = prefs.getBoolean(FloatingVolumeService.KEY_DOUBLE_TAP_MUTE, false),
            holdToRepeat = prefs.getBoolean(FloatingVolumeService.KEY_HOLD_TO_REPEAT, true),
            autoDimSec = prefs.getInt(FloatingVolumeService.KEY_AUTO_DIM_SEC, 0),
            dimOpacityPct = prefs.getInt(FloatingVolumeService.KEY_DIM_OPACITY_PCT, 15),
            volumeStep = prefs.getInt(FloatingVolumeService.KEY_VOLUME_STEP, 1),
            maxVolumeLimitPct = prefs.getInt(FloatingVolumeService.KEY_MAX_VOLUME_LIMIT_PCT, 100),
            swipeScrub = prefs.getBoolean(FloatingVolumeService.KEY_SWIPE_SCRUB, true),
            popoutMode = prefs.getString(FloatingVolumeService.KEY_POPOUT_MODE, FloatingVolumeService.POPOUT_SWIPE_INWARD) ?: FloatingVolumeService.POPOUT_SWIPE_INWARD,
            hideInFullscreen = prefs.getBoolean(FloatingVolumeService.KEY_HIDE_FULLSCREEN, false)
        )
    }

    private fun showNewPresetDialog() {
        val input = EditText(this).apply { hint = "Custom preset name" }
        AlertDialog.Builder(this)
            .setTitle("Create Custom Preset")
            .setMessage("Saves all current shape, volume step, cap, and gesture settings into a new quick preset.")
            .setView(input)
            .setPositiveButton("Save Preset") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { "Custom" }
                val newId = "custom_${System.currentTimeMillis()}"
                val arr = getSavedPresetsArray()
                arr.put(captureCurrentSettingsAsPreset(newId, name))
                prefs.edit().putString(KEY_ACTIVE_PRESET_ID, newId).apply()
                savePresetsArray(arr)
                Toast.makeText(this, "Saved $name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPresetEditorDialog(index: Int, existing: JSONObject) {
        val currentId = existing.optString("id", "preset_$index")
        val currentName = existing.optString("name", "Preset")
        val input = EditText(this).apply { setText(currentName) }

        val builder = AlertDialog.Builder(this)
            .setTitle("Edit Preset: $currentName")
            .setView(input)
            .setPositiveButton("Save Current & Name") { _, _ ->
                val nextName = input.text.toString().trim().ifEmpty { currentName }
                val arr = getSavedPresetsArray()
                arr.put(index, captureCurrentSettingsAsPreset(currentId, nextName))
                prefs.edit().putString(KEY_ACTIVE_PRESET_ID, currentId).apply()
                savePresetsArray(arr)
                Toast.makeText(this, "Updated $nextName", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)

        val arr = getSavedPresetsArray()
        if (arr.length() > 1) {
            builder.setNeutralButton("Delete") { _, _ ->
                val nextArr = JSONArray()
                for (i in 0 until arr.length()) {
                    if (i != index) nextArr.put(arr.get(i))
                }
                savePresetsArray(nextArr)
                Toast.makeText(this, "Deleted $currentName", Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
    }

    private fun loadSavedSettingsIntoViews() {
        syncingViews = true
        renderPresetButtons()

        switchDarkTheme.isChecked = prefs.getBoolean(FloatingVolumeService.KEY_DARK_THEME, true)
        switchSwipeScrub.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_SWIPE_SCRUB, true)
        val popoutMode = prefs.getString(
            FloatingVolumeService.KEY_POPOUT_MODE,
            "swipe_inward"
        )
        switchSingleTapSlider.isChecked = popoutMode == FloatingVolumeService.POPOUT_SINGLE_TAP
        switchHideFullscreen.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_HIDE_FULLSCREEN, false)
        switchHapticTap.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_HAPTIC_TAP, true)
        switchSnapToEdge.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, true)
        switchDoubleTapMute.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_DOUBLE_TAP_MUTE, false)
        switchHoldRepeat.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_HOLD_TO_REPEAT, true)
        switchAutoBoot.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_AUTO_BOOT, false)
        switchLockPosition.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_POSITION_LOCKED, true)
        switchRotateToast.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_ROTATE_TOAST, true)

        val stream = prefs.getString(
            FloatingVolumeService.KEY_AUDIO_STREAM,
            FloatingVolumeService.DEFAULT_STREAM
        )
        radioGroupStream.check(
            when (stream) {
                FloatingVolumeService.STREAM_RING -> R.id.radioStreamRing
                FloatingVolumeService.STREAM_ALARM -> R.id.radioStreamAlarm
                FloatingVolumeService.STREAM_VOICE -> R.id.radioStreamVoice
                else -> R.id.radioStreamMusic
            }
        )

        val step = prefs.getInt(
            FloatingVolumeService.KEY_VOLUME_STEP,
            FloatingVolumeService.DEFAULT_VOLUME_STEP
        )
        radioGroupStepSize.check(
            when (step) {
                2 -> R.id.radioStep2
                3 -> R.id.radioStep3
                5 -> R.id.radioStep5
                else -> R.id.radioStep1
            }
        )

        val maxLimit = prefs.getInt(
            FloatingVolumeService.KEY_MAX_VOLUME_LIMIT_PCT,
            FloatingVolumeService.DEFAULT_MAX_VOLUME_LIMIT_PCT
        ).coerceIn(30, 100)
        seekBarMaxLimit.progress = maxLimit - 30
        tvMaxLimitValue.text = "$maxLimit%"

        val dimSeconds = prefs.getInt(
            FloatingVolumeService.KEY_AUTO_DIM_SEC,
            FloatingVolumeService.DEFAULT_AUTO_DIM_SEC
        )
        radioGroupAutoDim.check(
            when (dimSeconds) {
                3 -> R.id.radioDim3s
                5 -> R.id.radioDim5s
                10 -> R.id.radioDim10s
                else -> R.id.radioDimOff
            }
        )

        val dimOpacity = prefs.getInt(
            FloatingVolumeService.KEY_DIM_OPACITY_PCT,
            FloatingVolumeService.DEFAULT_DIM_OPACITY_PCT
        ).coerceIn(0, 50)
        seekBarDimOpacity.progress = dimOpacity
        tvDimOpacityValue.text = "$dimOpacity%"

        val widthDp = prefs.getInt(
            FloatingVolumeService.KEY_WIDTH_DP,
            FloatingVolumeService.DEFAULT_WIDTH_DP
        ).coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)

        val heightDp = prefs.getInt(
            FloatingVolumeService.KEY_HEIGHT_DP,
            FloatingVolumeService.DEFAULT_HEIGHT_DP
        ).coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP)

        val transparencyPct = prefs.getInt(
            FloatingVolumeService.KEY_TRANSPARENCY_PCT,
            FloatingVolumeService.DEFAULT_TRANSPARENCY_PCT
        ).coerceIn(MIN_ALPHA_PCT, MAX_ALPHA_PCT)

        val shape = prefs.getString(
            FloatingVolumeService.KEY_SHAPE,
            FloatingVolumeService.SHAPE_CIRCLE
        )

        val colorInt = prefs.getInt(
            FloatingVolumeService.KEY_OVERLAY_COLOR,
            FloatingVolumeService.DEFAULT_COLOR
        )

        seekBarWidth.max = MAX_WIDTH_DP - MIN_WIDTH_DP
        seekBarWidth.progress = widthDp - MIN_WIDTH_DP
        tvWidthValue.text = "$widthDp dp"

        seekBarHeight.max = MAX_HEIGHT_DP - MIN_HEIGHT_DP
        seekBarHeight.progress = heightDp - MIN_HEIGHT_DP
        tvHeightValue.text = "$heightDp dp"

        seekBarTransparency.max = MAX_ALPHA_PCT - MIN_ALPHA_PCT
        seekBarTransparency.progress = transparencyPct - MIN_ALPHA_PCT
        tvTransparencyValue.text = "$transparencyPct%"

        radioGroupShape.check(
            if (shape == FloatingVolumeService.SHAPE_LINE) R.id.radioLine else R.id.radioCircle
        )

        val isNowLandscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        radioGroupOrientTarget.check(
            if (isNowLandscape) R.id.radioTargetLandscape else R.id.radioTargetPortrait
        )

        applyColorToPickerViews(colorInt)
        updateCurrentOrientationLabel()
        refreshSavedPositionsText()
        syncPositionSlidersForSelectedTarget()
        syncingViews = false
    }

    private fun setupListeners() {
        switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_DARK_THEME, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        switchSwipeScrub.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_SWIPE_SCRUB, isChecked).apply()
        }

        switchSingleTapSlider.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            val mode = if (isChecked) FloatingVolumeService.POPOUT_SINGLE_TAP else FloatingVolumeService.POPOUT_SWIPE_INWARD
            prefs.edit().putString(FloatingVolumeService.KEY_POPOUT_MODE, mode).apply()
        }

        switchHideFullscreen.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_HIDE_FULLSCREEN, isChecked).apply()
        }

        switchHapticTap.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_HAPTIC_TAP, isChecked).apply()
        }

        switchSnapToEdge.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, isChecked).apply()
            syncPositionSlidersForSelectedTarget()
            refreshSavedPositionsText()
        }

        btnNewPreset.setOnClickListener {
            showNewPresetDialog()
        }

        btnResetPresets.setOnClickListener {
            savePresetsArray(buildDefaultPresetsArray())
            Toast.makeText(this, "Restored default presets", Toast.LENGTH_SHORT).show()
        }

        btnExportBackup.setOnClickListener {
            exportBackupLauncher.launch("floating-volume-backup.json")
        }

        btnImportBackup.setOnClickListener {
            importBackupLauncher.launch(arrayOf("application/json", "*/*"))
        }

        switchDoubleTapMute.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_DOUBLE_TAP_MUTE, isChecked).apply()
        }

        switchHoldRepeat.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_HOLD_TO_REPEAT, isChecked).apply()
        }

        switchAutoBoot.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_AUTO_BOOT, isChecked).apply()
            if (isChecked && !isBackgroundAutostartGranted()) {
                Toast.makeText(
                    this,
                    "Tip: Grant Background Autostart permission above for reliable reboot launch",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        switchLockPosition.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_POSITION_LOCKED, isChecked).apply()
        }

        switchRotateToast.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_ROTATE_TOAST, isChecked).apply()
        }

        radioGroupStream.setOnCheckedChangeListener { _, checkedId ->
            if (syncingViews) return@setOnCheckedChangeListener
            val stream = when (checkedId) {
                R.id.radioStreamRing -> FloatingVolumeService.STREAM_RING
                R.id.radioStreamAlarm -> FloatingVolumeService.STREAM_ALARM
                R.id.radioStreamVoice -> FloatingVolumeService.STREAM_VOICE
                else -> FloatingVolumeService.STREAM_MUSIC
            }
            prefs.edit().putString(FloatingVolumeService.KEY_AUDIO_STREAM, stream).apply()
        }

        radioGroupStepSize.setOnCheckedChangeListener { _, checkedId ->
            if (syncingViews) return@setOnCheckedChangeListener
            val step = when (checkedId) {
                R.id.radioStep2 -> 2
                R.id.radioStep3 -> 3
                R.id.radioStep5 -> 5
                else -> 1
            }
            prefs.edit().putInt(FloatingVolumeService.KEY_VOLUME_STEP, step).apply()
        }

        seekBarMaxLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pct = 30 + progress
                tvMaxLimitValue.text = "$pct%"
                if (fromUser) {
                    prefs.edit().putInt(FloatingVolumeService.KEY_MAX_VOLUME_LIMIT_PCT, pct).apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        radioGroupAutoDim.setOnCheckedChangeListener { _, checkedId ->
            if (syncingViews) return@setOnCheckedChangeListener
            val seconds = when (checkedId) {
                R.id.radioDim3s -> 3
                R.id.radioDim5s -> 5
                R.id.radioDim10s -> 10
                else -> 0
            }
            prefs.edit().putInt(FloatingVolumeService.KEY_AUTO_DIM_SEC, seconds).apply()
        }

        seekBarDimOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvDimOpacityValue.text = "$progress%"
                if (fromUser) {
                    prefs.edit().putInt(FloatingVolumeService.KEY_DIM_OPACITY_PCT, progress).apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        btnOverlayPermission.setOnClickListener {
            requestOverlayPermissionIfNeeded()
        }

        btnAutostartPermission.setOnClickListener {
            requestBackgroundAutostartPermission()
        }

        btnStartService.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "Please grant Display over other apps permission first",
                    Toast.LENGTH_SHORT
                ).show()
                requestOverlayPermissionIfNeeded()
                return@setOnClickListener
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                launchFloatingService()
            }
        }

        btnStopService.setOnClickListener {
            stopService(Intent(this, FloatingVolumeService::class.java))
        }

        seekBarWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val widthDp = MIN_WIDTH_DP + progress
                tvWidthValue.text = "$widthDp dp"
                if (fromUser) {
                    prefs.edit().putInt(FloatingVolumeService.KEY_WIDTH_DP, widthDp).apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        seekBarHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val heightDp = MIN_HEIGHT_DP + progress
                tvHeightValue.text = "$heightDp dp"
                if (fromUser) {
                    prefs.edit().putInt(FloatingVolumeService.KEY_HEIGHT_DP, heightDp).apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        seekBarTransparency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val alphaPct = MIN_ALPHA_PCT + progress
                tvTransparencyValue.text = "$alphaPct%"
                if (fromUser) {
                    prefs.edit().putInt(FloatingVolumeService.KEY_TRANSPARENCY_PCT, alphaPct).apply()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        radioGroupShape.setOnCheckedChangeListener { _, checkedId ->
            if (syncingViews) return@setOnCheckedChangeListener
            val shape = if (checkedId == R.id.radioLine) {
                FloatingVolumeService.SHAPE_LINE
            } else {
                FloatingVolumeService.SHAPE_CIRCLE
            }
            prefs.edit().putString(FloatingVolumeService.KEY_SHAPE, shape).apply()
            updateOrientationPreviewBox()
        }

        val presetColors = mapOf(
            R.id.btnColorBlue to Color.parseColor("#2563EB"),
            R.id.btnColorEmerald to Color.parseColor("#10B981"),
            R.id.btnColorAmber to Color.parseColor("#F59E0B"),
            R.id.btnColorRose to Color.parseColor("#E11D48"),
            R.id.btnColorCyan to Color.parseColor("#06B6D4"),
            R.id.btnColorWhite to Color.parseColor("#F8FAFC")
        )
        presetColors.forEach { (viewId, colorInt) ->
            findViewById<Button>(viewId).setOnClickListener {
                saveOverlayColor(colorInt)
                applyColorToPickerViews(colorInt)
            }
        }

        val rgbListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (updatingColorSliders || !fromUser) return
                val colorInt = Color.rgb(seekBarRed.progress, seekBarGreen.progress, seekBarBlue.progress)
                saveOverlayColor(colorInt)
                viewColorPreview.setBackgroundColor(colorInt)
                tvColorHex.text = String.format("#%06X", 0xFFFFFF and colorInt)
                updateOrientationPreviewBox()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        seekBarRed.setOnSeekBarChangeListener(rgbListener)
        seekBarGreen.setOnSeekBarChangeListener(rgbListener)
        seekBarBlue.setOnSeekBarChangeListener(rgbListener)

        radioGroupOrientTarget.setOnCheckedChangeListener { _, _ ->
            syncPositionSlidersForSelectedTarget()
        }

        val posSliderListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (updatingPositionSliders || !fromUser) return
                val isLandscapeTarget =
                    radioGroupOrientTarget.checkedRadioButtonId == R.id.radioTargetLandscape
                val (maxX, maxY) = getMaxBoundsForOrientation(isLandscapeTarget)
                val xPct = seekBarPosX.progress
                val yPct = seekBarPosY.progress
                val pxX = ((xPct / 100f) * maxX).toInt()
                val pxY = ((yPct / 100f) * maxY).toInt()

                prefs.edit().apply {
                    if (isLandscapeTarget) {
                        putInt(FloatingVolumeService.KEY_POS_X_LANDSCAPE, pxX)
                        putInt(FloatingVolumeService.KEY_POS_Y_LANDSCAPE, pxY)
                    } else {
                        putInt(FloatingVolumeService.KEY_POS_X_PORTRAIT, pxX)
                        putInt(FloatingVolumeService.KEY_POS_Y_PORTRAIT, pxY)
                    }
                    apply()
                }
                updatePositionLabels(isLandscapeTarget, pxX, pxY, xPct, yPct)
                refreshSavedPositionsText()
                updateOrientationPreviewBox()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        seekBarPosX.setOnSeekBarChangeListener(posSliderListener)
        seekBarPosY.setOnSeekBarChangeListener(posSliderListener)

        btnTestRotate.setOnClickListener {
            val isNowLandscape =
                resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            requestedOrientation = if (isNowLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    private fun writeBackupToUri(uri: Uri) {
        try {
            val colorInt = prefs.getInt(FloatingVolumeService.KEY_OVERLAY_COLOR, FloatingVolumeService.DEFAULT_COLOR)
            val colorHex = String.format("#%06X", 0xFFFFFF and colorInt)
            val settingsObj = JSONObject().apply {
                put("appName", "Floating Volume")
                put("widthDp", prefs.getInt(FloatingVolumeService.KEY_WIDTH_DP, FloatingVolumeService.DEFAULT_WIDTH_DP))
                put("heightDp", prefs.getInt(FloatingVolumeService.KEY_HEIGHT_DP, FloatingVolumeService.DEFAULT_HEIGHT_DP))
                put("transparencyPct", prefs.getInt(FloatingVolumeService.KEY_TRANSPARENCY_PCT, FloatingVolumeService.DEFAULT_TRANSPARENCY_PCT))
                put("shape", prefs.getString(FloatingVolumeService.KEY_SHAPE, FloatingVolumeService.SHAPE_CIRCLE))
                put("colorHex", colorHex)
                put("audioStream", prefs.getString(FloatingVolumeService.KEY_AUDIO_STREAM, FloatingVolumeService.DEFAULT_STREAM))
                put("darkTheme", prefs.getBoolean(FloatingVolumeService.KEY_DARK_THEME, true))
                put("hapticTap", prefs.getBoolean(FloatingVolumeService.KEY_HAPTIC_TAP, true))
                put("snapToEdge", prefs.getBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, false))
                put("doubleTapMute", prefs.getBoolean(FloatingVolumeService.KEY_DOUBLE_TAP_MUTE, false))
                put("holdToRepeat", prefs.getBoolean(FloatingVolumeService.KEY_HOLD_TO_REPEAT, true))
                put("autoStartBoot", prefs.getBoolean(FloatingVolumeService.KEY_AUTO_BOOT, false))
                put("positionLocked", prefs.getBoolean(FloatingVolumeService.KEY_POSITION_LOCKED, false))
                put("autoDimSeconds", prefs.getInt(FloatingVolumeService.KEY_AUTO_DIM_SEC, 0))
                put("dimOpacityPct", prefs.getInt(FloatingVolumeService.KEY_DIM_OPACITY_PCT, 15))
                put("volumeStep", prefs.getInt(FloatingVolumeService.KEY_VOLUME_STEP, 1))
                put("maxVolumeLimitPct", prefs.getInt(FloatingVolumeService.KEY_MAX_VOLUME_LIMIT_PCT, 100))
                put("swipeScrub", prefs.getBoolean(FloatingVolumeService.KEY_SWIPE_SCRUB, true))
                put("popoutSliderMode", prefs.getString(FloatingVolumeService.KEY_POPOUT_MODE, FloatingVolumeService.POPOUT_SWIPE_INWARD))
                put("hideInFullscreen", prefs.getBoolean(FloatingVolumeService.KEY_HIDE_FULLSCREEN, false))
                put("presets", getSavedPresetsArray())
            }
            val root = JSONObject().apply {
                put("schemaVersion", 4)
                put("packageName", packageName)
                put("settings", settingsObj)
            }
            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(root.toString(2).toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "Backup exported successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun readBackupFromUri(uri: Uri) {
        try {
            val jsonText = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
            val root = JSONObject(jsonText)
            val s = root.optJSONObject("settings") ?: root
            val editor = prefs.edit()

            if (s.has("widthDp")) editor.putInt(FloatingVolumeService.KEY_WIDTH_DP, s.optInt("widthDp"))
            if (s.has("heightDp")) editor.putInt(FloatingVolumeService.KEY_HEIGHT_DP, s.optInt("heightDp"))
            if (s.has("transparencyPct")) editor.putInt(FloatingVolumeService.KEY_TRANSPARENCY_PCT, s.optInt("transparencyPct"))
            if (s.has("shape")) editor.putString(FloatingVolumeService.KEY_SHAPE, s.optString("shape"))
            if (s.has("colorHex")) {
                try {
                    editor.putInt(FloatingVolumeService.KEY_OVERLAY_COLOR, Color.parseColor(s.getString("colorHex")))
                } catch (_: Exception) {}
            }
            if (s.has("audioStream")) editor.putString(FloatingVolumeService.KEY_AUDIO_STREAM, s.optString("audioStream"))
            if (s.has("darkTheme")) editor.putBoolean(FloatingVolumeService.KEY_DARK_THEME, s.optBoolean("darkTheme"))
            if (s.has("hapticTap")) editor.putBoolean(FloatingVolumeService.KEY_HAPTIC_TAP, s.optBoolean("hapticTap"))
            if (s.has("snapToEdge")) editor.putBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, s.optBoolean("snapToEdge"))
            if (s.has("doubleTapMute")) editor.putBoolean(FloatingVolumeService.KEY_DOUBLE_TAP_MUTE, s.optBoolean("doubleTapMute"))
            if (s.has("holdToRepeat")) editor.putBoolean(FloatingVolumeService.KEY_HOLD_TO_REPEAT, s.optBoolean("holdToRepeat"))
            if (s.has("autoStartBoot")) editor.putBoolean(FloatingVolumeService.KEY_AUTO_BOOT, s.optBoolean("autoStartBoot"))
            if (s.has("positionLocked")) editor.putBoolean(FloatingVolumeService.KEY_POSITION_LOCKED, s.optBoolean("positionLocked"))
            if (s.has("autoDimSeconds")) editor.putInt(FloatingVolumeService.KEY_AUTO_DIM_SEC, s.optInt("autoDimSeconds"))
            if (s.has("dimOpacityPct")) editor.putInt(FloatingVolumeService.KEY_DIM_OPACITY_PCT, s.optInt("dimOpacityPct"))
            if (s.has("volumeStep")) editor.putInt(FloatingVolumeService.KEY_VOLUME_STEP, s.optInt("volumeStep"))
            if (s.has("maxVolumeLimitPct")) editor.putInt(FloatingVolumeService.KEY_MAX_VOLUME_LIMIT_PCT, s.optInt("maxVolumeLimitPct"))
            if (s.has("swipeScrub")) editor.putBoolean(FloatingVolumeService.KEY_SWIPE_SCRUB, s.optBoolean("swipeScrub"))
            if (s.has("popoutSliderMode")) editor.putString(FloatingVolumeService.KEY_POPOUT_MODE, s.optString("popoutSliderMode"))
            if (s.has("hideInFullscreen")) editor.putBoolean(FloatingVolumeService.KEY_HIDE_FULLSCREEN, s.optBoolean("hideInFullscreen"))
            val presetsArr = s.optJSONArray("presets")
            if (presetsArr != null && presetsArr.length() > 0) {
                editor.putString(KEY_PRESETS_JSON, presetsArr.toString())
            }
            editor.apply()

            loadSavedSettingsIntoViews()
            Toast.makeText(this, "Backup restored", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid backup file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveOverlayColor(colorInt: Int) {
        prefs.edit().putInt(FloatingVolumeService.KEY_OVERLAY_COLOR, colorInt).apply()
        updateOrientationPreviewBox()
    }

    private fun applyColorToPickerViews(colorInt: Int) {
        updatingColorSliders = true
        seekBarRed.progress = Color.red(colorInt)
        seekBarGreen.progress = Color.green(colorInt)
        seekBarBlue.progress = Color.blue(colorInt)
        viewColorPreview.setBackgroundColor(colorInt)
        tvColorHex.text = String.format("#%06X", 0xFFFFFF and colorInt)
        updatingColorSliders = false
        updateOrientationPreviewBox()
    }

    private fun getMaxBoundsForOrientation(isLandscape: Boolean): Pair<Int, Int> {
        val metrics = resources.displayMetrics
        val shortSide = min(metrics.widthPixels, metrics.heightPixels)
        val longSide = max(metrics.widthPixels, metrics.heightPixels)
        val screenW = if (isLandscape) longSide else shortSide
        val screenH = if (isLandscape) shortSide else longSide
        val widthDp = prefs.getInt(
            FloatingVolumeService.KEY_WIDTH_DP,
            FloatingVolumeService.DEFAULT_WIDTH_DP
        )
        val heightDp = prefs.getInt(
            FloatingVolumeService.KEY_HEIGHT_DP,
            FloatingVolumeService.DEFAULT_HEIGHT_DP
        )
        val wPx = (widthDp * metrics.density).toInt().coerceAtLeast(1)
        val hPx = (heightDp * metrics.density).toInt().coerceAtLeast(1)
        return (screenW - wPx).coerceAtLeast(1) to (screenH - hPx).coerceAtLeast(1)
    }

    private fun syncPositionSlidersForSelectedTarget() {
        updatingPositionSliders = true
        val isLandscapeTarget =
            radioGroupOrientTarget.checkedRadioButtonId == R.id.radioTargetLandscape
        val (maxX, maxY) = getMaxBoundsForOrientation(isLandscapeTarget)
        val x = if (isLandscapeTarget) {
            prefs.getInt(FloatingVolumeService.KEY_POS_X_LANDSCAPE, (maxX * 0.10f).toInt())
        } else {
            prefs.getInt(FloatingVolumeService.KEY_POS_X_PORTRAIT, (maxX * 0.88f).toInt())
        }
        val y = if (isLandscapeTarget) {
            prefs.getInt(FloatingVolumeService.KEY_POS_Y_LANDSCAPE, (maxY * 0.25f).toInt())
        } else {
            prefs.getInt(FloatingVolumeService.KEY_POS_Y_PORTRAIT, (maxY * 0.38f).toInt())
        }
        val xPct = ((x.toFloat() / maxX) * 100f).toInt().coerceIn(0, 100)
        val yPct = ((y.toFloat() / maxY) * 100f).toInt().coerceIn(0, 100)
        seekBarPosX.progress = xPct
        seekBarPosY.progress = yPct
        updatePositionLabels(isLandscapeTarget, x, y, xPct, yPct)
        updatingPositionSliders = false
        updateOrientationPreviewBox()
    }

    private fun updatePositionLabels(
        isLandscapeTarget: Boolean,
        pxX: Int,
        pxY: Int,
        xPct: Int,
        yPct: Int
    ) {
        val prefix = if (isLandscapeTarget) "Landscape" else "Portrait"
        tvPreviewHint.text = "$prefix Spot Preview ($xPct%, $yPct%)"
        tvPosXLabel.text = "$prefix Horizontal (X): $pxX px ($xPct%)"
        tvPosYLabel.text = "$prefix Vertical (Y): $pxY px ($yPct%)"
    }

    private fun updateOrientationPreviewBox() {
        val isLandscapeTarget =
            radioGroupOrientTarget.checkedRadioButtonId == R.id.radioTargetLandscape
        val density = resources.displayMetrics.density
        val boxWidthPx = ((if (isLandscapeTarget) 168 else 96) * density).toInt()
        val boxHeightPx = ((if (isLandscapeTarget) 96 else 168) * density).toInt()

        val frameLp = previewScreenFrame.layoutParams
        frameLp.width = boxWidthPx
        frameLp.height = boxHeightPx
        previewScreenFrame.layoutParams = frameLp

        val colorInt = prefs.getInt(
            FloatingVolumeService.KEY_OVERLAY_COLOR,
            FloatingVolumeService.DEFAULT_COLOR
        )
        val shape = prefs.getString(
            FloatingVolumeService.KEY_SHAPE,
            FloatingVolumeService.SHAPE_CIRCLE
        )
        val dotDrawable = GradientDrawable().apply {
            this.shape = if (shape == FloatingVolumeService.SHAPE_LINE) {
                GradientDrawable.RECTANGLE
            } else {
                GradientDrawable.OVAL
            }
            if (shape == FloatingVolumeService.SHAPE_LINE) {
                cornerRadius = 8f * density
            }
            setColor(colorInt)
            setStroke((1.5f * density).toInt().coerceAtLeast(1), Color.WHITE)
        }
        previewDot.background = dotDrawable

        val dotW = (14 * density).toInt()
        val dotH = (22 * density).toInt()
        val maxPreviewX = (boxWidthPx - dotW).coerceAtLeast(0)
        val maxPreviewY = (boxHeightPx - dotH).coerceAtLeast(0)
        previewDot.translationX = (seekBarPosX.progress / 100f) * maxPreviewX
        previewDot.translationY = (seekBarPosY.progress / 100f) * maxPreviewY
    }

    private fun updateCurrentOrientationLabel() {
        val isLandscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val label = if (isLandscape) "Landscape" else "Portrait"
        tvCurrentOrientation.text = "Active screen orientation: $label"
        btnTestRotate.text = if (isLandscape) {
            "Test Orientation Switch (Rotate to Portrait)"
        } else {
            "Test Orientation Switch (Rotate to Landscape)"
        }
    }

    private fun refreshSavedPositionsText() {
        val px = prefs.getInt(FloatingVolumeService.KEY_POS_X_PORTRAIT, 0)
        val py = prefs.getInt(FloatingVolumeService.KEY_POS_Y_PORTRAIT, 0)
        val lx = prefs.getInt(FloatingVolumeService.KEY_POS_X_LANDSCAPE, 0)
        val ly = prefs.getInt(FloatingVolumeService.KEY_POS_Y_LANDSCAPE, 0)
        tvSavedPositions.text = "Portrait: ($px, $py) px  |  Landscape: ($lx, $ly) px"
    }

    private fun requestOverlayPermissionIfNeeded() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isBackgroundAutostartGranted(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    @SuppressLint("BatteryLife")
    private fun requestBackgroundAutostartPermission() {
        if (!isBackgroundAutostartGranted()) {
            try {
                val batteryIntent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
                autostartPermissionLauncher.launch(batteryIntent)
                return
            } catch (_: Exception) {
                // Fall through to OEM autostart or app details
            }
        }

        val oemIntents = listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )

        for (candidate in oemIntents) {
            if (candidate.resolveActivity(packageManager) != null) {
                autostartPermissionLauncher.launch(candidate)
                return
            }
        }

        Toast.makeText(
            this,
            "Unrestricted battery permission is already enabled",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun launchFloatingService() {
        val serviceIntent = Intent(this, FloatingVolumeService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun updatePermissionUi() {
        val overlayOk = Settings.canDrawOverlays(this)
        val autostartOk = isBackgroundAutostartGranted()

        tvPermissionStatus.text = if (overlayOk) {
            "Overlay permission: Granted"
        } else {
            "Overlay permission: Required (Display over other apps)"
        }

        tvAutostartStatus.text = if (autostartOk) {
            "Background autostart: Granted (Unrestricted battery)"
        } else {
            "Background autostart: Recommended for reboot & background"
        }

        btnOverlayPermission.isEnabled = !overlayOk
        btnAutostartPermission.text = if (autostartOk) {
            "Background Autostart Granted (Open OEM / Battery)"
        } else {
            "Grant Background Autostart Permission"
        }
        btnStartService.isEnabled = overlayOk
    }

    companion object {
        private const val KEY_PRESETS_JSON = "profile_presets_json"
        private const val KEY_ACTIVE_PRESET_ID = "active_preset_id"
        private const val MIN_WIDTH_DP = 1
        private const val MAX_WIDTH_DP = 160
        private const val MIN_HEIGHT_DP = 24
        private const val MAX_HEIGHT_DP = 220
        private const val MIN_ALPHA_PCT = 0
        private const val MAX_ALPHA_PCT = 100
    }
}
