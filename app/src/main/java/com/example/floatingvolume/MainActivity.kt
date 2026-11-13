package com.example.floatingvolume

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
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

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchDarkTheme: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchLockPosition: Switch

    private lateinit var radioGroupShape: RadioGroup
    private lateinit var seekBarWidth: SeekBar
    private lateinit var seekBarHeight: SeekBar
    private lateinit var seekBarTransparency: SeekBar
    private lateinit var tvWidthValue: TextView
    private lateinit var tvHeightValue: TextView
    private lateinit var tvTransparencyValue: TextView

    private lateinit var viewColorPreview: View
    private lateinit var tvColorHex: TextView
    private lateinit var seekBarRed: SeekBar
    private lateinit var seekBarGreen: SeekBar
    private lateinit var seekBarBlue: SeekBar

    private lateinit var radioGroupOrientTarget: RadioGroup
    private lateinit var seekBarPosX: SeekBar
    private lateinit var seekBarPosY: SeekBar
    private lateinit var tvSavedPositions: TextView

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
        loadSavedSettingsIntoViews()
        setupListeners()
        updatePermissionUi()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
        refreshSavedPositionsText()
        syncPositionSlidersForSelectedTarget()
    }

    private fun bindViews() {
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnStartService = findViewById(R.id.btnStartService)
        btnStopService = findViewById(R.id.btnStopService)
        switchDarkTheme = findViewById(R.id.switchDarkTheme)
        switchLockPosition = findViewById(R.id.switchLockPosition)

        radioGroupShape = findViewById(R.id.radioGroupShape)
        seekBarWidth = findViewById(R.id.seekBarWidth)
        seekBarHeight = findViewById(R.id.seekBarHeight)
        seekBarTransparency = findViewById(R.id.seekBarTransparency)
        tvWidthValue = findViewById(R.id.tvWidthValue)
        tvHeightValue = findViewById(R.id.tvHeightValue)
        tvTransparencyValue = findViewById(R.id.tvTransparencyValue)

        viewColorPreview = findViewById(R.id.viewColorPreview)
        tvColorHex = findViewById(R.id.tvColorHex)
        seekBarRed = findViewById(R.id.seekBarRed)
        seekBarGreen = findViewById(R.id.seekBarGreen)
        seekBarBlue = findViewById(R.id.seekBarBlue)

        radioGroupOrientTarget = findViewById(R.id.radioGroupOrientTarget)
        seekBarPosX = findViewById(R.id.seekBarPosX)
        seekBarPosY = findViewById(R.id.seekBarPosY)
        tvSavedPositions = findViewById(R.id.tvSavedPositions)
    }

    private fun loadSavedSettingsIntoViews() {
        switchDarkTheme.isChecked = prefs.getBoolean(FloatingVolumeService.KEY_DARK_THEME, true)
        switchLockPosition.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_POSITION_LOCKED, false)

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

        applyColorToPickerViews(colorInt)
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

        switchLockPosition.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(FloatingVolumeService.KEY_POSITION_LOCKED, isChecked).apply()
        }

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
                val pxX = ((seekBarPosX.progress / 100f) * maxX).toInt()
                val pxY = ((seekBarPosY.progress / 100f) * maxY).toInt()

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
                refreshSavedPositionsText()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        seekBarPosX.setOnSeekBarChangeListener(posSliderListener)
        seekBarPosY.setOnSeekBarChangeListener(posSliderListener)
    }

    private fun saveOverlayColor(colorInt: Int) {
        prefs.edit().putInt(FloatingVolumeService.KEY_OVERLAY_COLOR, colorInt).apply()
    }

    private fun applyColorToPickerViews(colorInt: Int) {
        updatingColorSliders = true
        seekBarRed.progress = Color.red(colorInt)
        seekBarGreen.progress = Color.green(colorInt)
        seekBarBlue.progress = Color.blue(colorInt)
        viewColorPreview.setBackgroundColor(colorInt)
        tvColorHex.text = String.format("#%06X", 0xFFFFFF and colorInt)
        updatingColorSliders = false
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
            prefs.getInt(FloatingVolumeService.KEY_POS_X_LANDSCAPE, maxX)
        } else {
            prefs.getInt(FloatingVolumeService.KEY_POS_X_PORTRAIT, maxX)
        }
        val y = if (isLandscapeTarget) {
            prefs.getInt(FloatingVolumeService.KEY_POS_Y_LANDSCAPE, maxY / 3)
        } else {
            prefs.getInt(FloatingVolumeService.KEY_POS_Y_PORTRAIT, maxY / 3)
        }
        seekBarPosX.progress = ((x.toFloat() / maxX) * 100f).toInt().coerceIn(0, 100)
        seekBarPosY.progress = ((y.toFloat() / maxY) * 100f).toInt().coerceIn(0, 100)
        updatingPositionSliders = false
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
