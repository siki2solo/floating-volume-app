package com.example.floatingvolume

import android.Manifest
import android.annotation.SuppressLint
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
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvPermissionStatus: TextView
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button
    private lateinit var btnPresetGaming: Button
    private lateinit var btnPresetBedtime: Button
    private lateinit var btnPresetStealth: Button

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchDarkTheme: Switch

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
    private lateinit var radioGroupShape: RadioGroup
    private lateinit var radioGroupAutoDim: RadioGroup
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

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionUi()
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        launchFloatingService()
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
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnStartService = findViewById(R.id.btnStartService)
        btnStopService = findViewById(R.id.btnStopService)
        btnPresetGaming = findViewById(R.id.btnPresetGaming)
        btnPresetBedtime = findViewById(R.id.btnPresetBedtime)
        btnPresetStealth = findViewById(R.id.btnPresetStealth)

        switchDarkTheme = findViewById(R.id.switchDarkTheme)
        switchHapticTap = findViewById(R.id.switchHapticTap)
        switchSnapToEdge = findViewById(R.id.switchSnapToEdge)
        switchDoubleTapMute = findViewById(R.id.switchDoubleTapMute)
        switchHoldRepeat = findViewById(R.id.switchHoldRepeat)
        switchAutoBoot = findViewById(R.id.switchAutoBoot)
        switchLockPosition = findViewById(R.id.switchLockPosition)
        switchRotateToast = findViewById(R.id.switchRotateToast)

        radioGroupStream = findViewById(R.id.radioGroupStream)
        radioGroupShape = findViewById(R.id.radioGroupShape)
        radioGroupAutoDim = findViewById(R.id.radioGroupAutoDim)
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

    private fun loadSavedSettingsIntoViews() {
        switchDarkTheme.isChecked = prefs.getBoolean(FloatingVolumeService.KEY_DARK_THEME, true)
        switchHapticTap.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_HAPTIC_TAP, true)
        switchSnapToEdge.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, false)
        switchDoubleTapMute.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_DOUBLE_TAP_MUTE, false)
        switchHoldRepeat.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_HOLD_TO_REPEAT, true)
        switchAutoBoot.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_AUTO_BOOT, false)
        switchLockPosition.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_POSITION_LOCKED, false)
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
    }

    private fun setupListeners() {
        switchDarkTheme.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(FloatingVolumeService.KEY_DARK_THEME, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        switchHapticTap.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(FloatingVolumeService.KEY_HAPTIC_TAP, isChecked).apply()
        }

        switchSnapToEdge.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, isChecked).apply()
            syncPositionSlidersForSelectedTarget()
            refreshSavedPositionsText()
        }

        btnPresetGaming.setOnClickListener {
            applyPresetProfile(
                name = "Gaming",
                widthDp = 44,
                heightDp = 96,
                transparencyPct = 55,
                shape = FloatingVolumeService.SHAPE_LINE,
                colorHex = "#06B6D4",
                snapToEdge = true,
                autoDimSec = 5,
                dimOpacity = 15
            )
        }

        btnPresetBedtime.setOnClickListener {
            applyPresetProfile(
                name = "Bedtime",
                widthDp = 52,
                heightDp = 112,
                transparencyPct = 28,
                shape = FloatingVolumeService.SHAPE_CIRCLE,
                colorHex = "#10B981",
                snapToEdge = true,
                autoDimSec = 3,
                dimOpacity = 5
            )
        }

        btnPresetStealth.setOnClickListener {
            applyPresetProfile(
                name = "Stealth",
                widthDp = 58,
                heightDp = 124,
                transparencyPct = 0,
                shape = FloatingVolumeService.SHAPE_LINE,
                colorHex = "#2563EB",
                snapToEdge = true,
                autoDimSec = 0,
                dimOpacity = 0
            )
        }

        switchDoubleTapMute.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(FloatingVolumeService.KEY_DOUBLE_TAP_MUTE, isChecked).apply()
        }

        switchHoldRepeat.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(FloatingVolumeService.KEY_HOLD_TO_REPEAT, isChecked).apply()
        }

        switchAutoBoot.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(FloatingVolumeService.KEY_AUTO_BOOT, isChecked).apply()
        }

        switchLockPosition.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(FloatingVolumeService.KEY_POSITION_LOCKED, isChecked).apply()
        }

        switchRotateToast.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(FloatingVolumeService.KEY_ROTATE_TOAST, isChecked).apply()
        }

        radioGroupStream.setOnCheckedChangeListener { _, checkedId ->
            val stream = when (checkedId) {
                R.id.radioStreamRing -> FloatingVolumeService.STREAM_RING
                R.id.radioStreamAlarm -> FloatingVolumeService.STREAM_ALARM
                R.id.radioStreamVoice -> FloatingVolumeService.STREAM_VOICE
                else -> FloatingVolumeService.STREAM_MUSIC
            }
            prefs.edit().putString(FloatingVolumeService.KEY_AUDIO_STREAM, stream).apply()
        }

        radioGroupAutoDim.setOnCheckedChangeListener { _, checkedId ->
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
                prefs.edit().putInt(FloatingVolumeService.KEY_WIDTH_DP, widthDp).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        seekBarHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val heightDp = MIN_HEIGHT_DP + progress
                tvHeightValue.text = "$heightDp dp"
                prefs.edit().putInt(FloatingVolumeService.KEY_HEIGHT_DP, heightDp).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        seekBarTransparency.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val alphaPct = MIN_ALPHA_PCT + progress
                tvTransparencyValue.text = "$alphaPct%"
                prefs.edit().putInt(FloatingVolumeService.KEY_TRANSPARENCY_PCT, alphaPct).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        radioGroupShape.setOnCheckedChangeListener { _, checkedId ->
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

    private fun applyPresetProfile(
        name: String,
        widthDp: Int,
        heightDp: Int,
        transparencyPct: Int,
        shape: String,
        colorHex: String,
        snapToEdge: Boolean,
        autoDimSec: Int,
        dimOpacity: Int
    ) {
        prefs.edit()
            .putInt(FloatingVolumeService.KEY_WIDTH_DP, widthDp)
            .putInt(FloatingVolumeService.KEY_HEIGHT_DP, heightDp)
            .putInt(FloatingVolumeService.KEY_TRANSPARENCY_PCT, transparencyPct)
            .putString(FloatingVolumeService.KEY_SHAPE, shape)
            .putInt(FloatingVolumeService.KEY_OVERLAY_COLOR, Color.parseColor(colorHex))
            .putString(FloatingVolumeService.KEY_AUDIO_STREAM, FloatingVolumeService.STREAM_MUSIC)
            .putBoolean(FloatingVolumeService.KEY_HAPTIC_TAP, true)
            .putBoolean(FloatingVolumeService.KEY_HOLD_TO_REPEAT, true)
            .putBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, snapToEdge)
            .putInt(FloatingVolumeService.KEY_AUTO_DIM_SEC, autoDimSec)
            .putInt(FloatingVolumeService.KEY_DIM_OPACITY_PCT, dimOpacity)
            .apply()
        loadSavedSettingsIntoViews()
        Toast.makeText(this, "Applied $name preset", Toast.LENGTH_SHORT).show()
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

    private fun launchFloatingService() {
        val serviceIntent = Intent(this, FloatingVolumeService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun updatePermissionUi() {
        val granted = Settings.canDrawOverlays(this)
        tvPermissionStatus.text = if (granted) {
            "Overlay permission: Granted"
        } else {
            "Overlay permission: Required (Display over other apps)"
        }
        btnOverlayPermission.isEnabled = !granted
        btnStartService.isEnabled = granted
    }

    companion object {
        private const val MIN_WIDTH_DP = 1
        private const val MAX_WIDTH_DP = 160
        private const val MIN_HEIGHT_DP = 24
        private const val MAX_HEIGHT_DP = 220
        private const val MIN_ALPHA_PCT = 0
        private const val MAX_ALPHA_PCT = 100
    }
}
