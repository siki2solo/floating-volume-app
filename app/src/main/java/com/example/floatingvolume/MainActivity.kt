package com.example.floatingvolume

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvPermissionStatus: TextView
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button
    private lateinit var radioGroupShape: RadioGroup
    private lateinit var seekBarWidth: SeekBar
    private lateinit var seekBarHeight: SeekBar
    private lateinit var seekBarTransparency: SeekBar
    private lateinit var tvWidthValue: TextView
    private lateinit var tvHeightValue: TextView
    private lateinit var tvTransparencyValue: TextView

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
        // Start the foreground service regardless; notification visibility is optional on API 33+
        launchFloatingService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(FloatingVolumeService.PREFS_NAME, Context.MODE_PRIVATE)

        bindViews()
        loadSavedSettingsIntoViews()
        setupListeners()
        updatePermissionUi()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
    }

    private fun bindViews() {
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnStartService = findViewById(R.id.btnStartService)
        btnStopService = findViewById(R.id.btnStopService)
        radioGroupShape = findViewById(R.id.radioGroupShape)
        seekBarWidth = findViewById(R.id.seekBarWidth)
        seekBarHeight = findViewById(R.id.seekBarHeight)
        seekBarTransparency = findViewById(R.id.seekBarTransparency)
        tvWidthValue = findViewById(R.id.tvWidthValue)
        tvHeightValue = findViewById(R.id.tvHeightValue)
        tvTransparencyValue = findViewById(R.id.tvTransparencyValue)
    }

    private fun loadSavedSettingsIntoViews() {
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
    }

    private fun setupListeners() {
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
        private const val MIN_WIDTH_DP = 32
        private const val MAX_WIDTH_DP = 160
        private const val MIN_HEIGHT_DP = 64
        private const val MAX_HEIGHT_DP = 220
        private const val MIN_ALPHA_PCT = 15
        private const val MAX_ALPHA_PCT = 100
    }
}
