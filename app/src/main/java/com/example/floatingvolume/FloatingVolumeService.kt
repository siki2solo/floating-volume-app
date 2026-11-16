package com.example.floatingvolume

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothA2dp
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.service.quicksettings.TileService
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class FloatingVolumeService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var displayManager: DisplayManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var powerManager: PowerManager
    private lateinit var prefs: SharedPreferences

    private var floatingView: LinearLayout? = null
    private var handleView: FrameLayout? = null
    private var handleGlyphView: View? = null
    private var innerBadgeView: TextView? = null
    private var sideBadgeView: TextView? = null
    private var fullscreenSentinelView: View? = null
    private var miniSliderView: View? = null
    private var syncMiniSliderCallback: (() -> Unit)? = null
    private var repositionMiniSliderCallback: (() -> Unit)? = null
    private var deviceBannerView: LinearLayout? = null
    private var deviceBannerLayoutParams: WindowManager.LayoutParams? = null
    private var brightnessFloatingView: FrameLayout? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var brightnessLayoutParams: WindowManager.LayoutParams
    private val overlayDrawable = GradientDrawable()
    private val brightnessDrawable = GradientDrawable()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isDragging = false
    private var isScrubbing = false
    private var repeatActive = false
    private var lockedLongPressTriggered = false
    private var repeatUpperHalf = true
    private var isIdleDimmed = false
    private var isFullscreenHidden = false
    private var touchSlop = 0
    private var scrubAccumY = 0f
    private var handleWidthPx = 0
    private var handleHeightPx = 0
    private var safeInsetLeft = 0
    private var safeInsetTop = 0
    private var safeInsetRight = 0
    private var safeInsetBottom = 0
    private var lastAppliedLandscape: Boolean? = null
    private var lastHeadphoneConnected: Boolean? = null
    private val preMuteVolumes = mutableMapOf<Int, Int>()
    private var preAppSavedStream: Int? = null
    private var preAppSavedVolume: Int? = null
    private var preAppRestoreOnExit = false

    private var isBrightnessDragging = false
    private var isBrightnessScrubbing = false
    private var brightnessScrubAccumY = 0f
    private var brightnessInitialX = 0
    private var brightnessInitialY = 0
    private var brightnessInitialTouchX = 0f
    private var brightnessInitialTouchY = 0f
    private var brightnessHandleW = 0
    private var brightnessHandleH = 0

    private var oledStepIndex = 0
    private var oledOffsetPxX = 0
    private var oledOffsetPxY = 0
    private var lastQuietActive = false
    private var lastForegroundPackage = ""
    private var lastFullscreenDetected = false
    private var receiversRegistered = false
    private var writeSettingsPromptedThisSession = false
    private var dndWarningShownThisSession = false

    private var tapCount = 0
    private var tapUpperHalf = true
    private var tapResolveRunnable: Runnable? = null
    private var audioRouteCheckRunnable: Runnable? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val longPressRunnable = Runnable {
        if (isPositionLocked() || isScrubbing) return@Runnable
        isDragging = true
        handleGlyphView?.invalidate()
        floatingView?.apply {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
        }
    }

    private val brightnessLongPressRunnable = Runnable {
        if (isPositionLocked() || isBrightnessScrubbing) return@Runnable
        isBrightnessDragging = true
        brightnessFloatingView?.apply {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
        }
    }

    private val repeatVolumeRunnable = object : Runnable {
        override fun run() {
            if (!repeatActive) return
            val view = floatingView ?: return
            triggerHapticIfEnabled(view)
            adjustVolumeByStep(if (repeatUpperHalf) 1 else -1)
            mainHandler.postDelayed(this, REPEAT_INTERVAL_MS)
        }
    }

    private val startRepeatRunnable = Runnable {
        val view = floatingView ?: return@Runnable
        if (!isPositionLocked()) return@Runnable
        val lockedAction = prefs.getString(KEY_GESTURE_LOCKED_LONG_PRESS, DEFAULT_GESTURE_LOCKED_LONG_PRESS)
            ?: ACTION_ID_NONE
        if (lockedAction != ACTION_ID_NONE) {
            lockedLongPressTriggered = true
            executeGestureAction(lockedAction, view)
            return@Runnable
        }
        if (!prefs.getBoolean(KEY_HOLD_TO_REPEAT, true)) return@Runnable
        repeatActive = true
        triggerHapticIfEnabled(view)
        adjustVolumeByStep(if (repeatUpperHalf) 1 else -1)
        mainHandler.postDelayed(repeatVolumeRunnable, REPEAT_INTERVAL_MS)
    }

    private val autoDimRunnable = Runnable {
        if (prefs.getInt(KEY_AUTO_DIM_SEC, DEFAULT_AUTO_DIM_SEC) <= 0) return@Runnable
        isIdleDimmed = true
        applyAppearanceFromPrefs()
        applyBrightnessAppearance()
    }

    private val dismissSliderRunnable = Runnable {
        dismissMiniSlider()
    }

    private val dismissBannerRunnable = Runnable {
        dismissDeviceBanner()
    }

    private val oledShiftRunnable = object : Runnable {
        override fun run() {
            if (!prefs.getBoolean(KEY_OLED_PIXEL_SHIFT, false)) {
                resetOledShiftToZero()
                return
            }
            if (!isDragging && !isScrubbing && !isBrightnessDragging && !isBrightnessScrubbing) {
                val pattern = arrayOf(
                    0 to 0, 2 to 0, 2 to 2, 0 to 2, -2 to 2, -2 to 0, -2 to -2, 0 to -2, 2 to -2
                )
                oledStepIndex = (oledStepIndex + 1) % pattern.size
                val (dpX, dpY) = pattern[oledStepIndex]
                val nextOffX = if (dpX == 0) 0 else dpToPx(abs(dpX)) * if (dpX > 0) 1 else -1
                val nextOffY = if (dpY == 0) 0 else dpToPx(abs(dpY)) * if (dpY > 0) 1 else -1
                applyOledShiftOffset(nextOffX, nextOffY)
            }
            mainHandler.postDelayed(this, OLED_SHIFT_INTERVAL_MS)
        }
    }

    private val automationRunnable = object : Runnable {
        override fun run() {
            if (powerManager.isInteractive) {
                evaluateQuietHoursAndAppRules()
            }
            mainHandler.postDelayed(this, AUTOMATION_TICK_MS)
        }
    }

    private val pendingAppearanceSyncRunnable = Runnable {
        applyAppearanceFromPrefs()
        applyBrightnessAppearance()
        syncMiniSliderCallback?.invoke()
    }

    private fun scheduleCoalescedAppearanceSync() {
        mainHandler.removeCallbacks(pendingAppearanceSyncRunnable)
        mainHandler.post(pendingAppearanceSyncRunnable)
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            handleOrientationCheck(showNotice = true)
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            scheduleAudioDeviceCheck(forceApplyOnEnable = false)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            scheduleAudioDeviceCheck(forceApplyOnEnable = false)
        }
    }

    private val systemEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                VOLUME_CHANGED_ACTION -> {
                    refreshPercentBadge()
                    handleGlyphView?.invalidate()
                    syncMiniSliderCallback?.invoke()
                    refreshForegroundNotification()
                }
                Intent.ACTION_HEADSET_PLUG,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    scheduleAudioDeviceCheck(forceApplyOnEnable = false)
                }
            }
        }
    }

    private fun safeAddView(view: View, lp: WindowManager.LayoutParams): Boolean {
        return try {
            if (!view.isAttachedToWindow) {
                windowManager.addView(view, lp)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun safeUpdateViewLayout(view: View, lp: WindowManager.LayoutParams) {
        if (!view.isAttachedToWindow) return
        try {
            windowManager.updateViewLayout(view, lp)
        } catch (_: Exception) {
            // Window detached or token invalidated
        }
    }

    private fun safeRemoveView(view: View?) {
        if (view == null || !view.isAttachedToWindow) return
        try {
            windowManager.removeView(view)
        } catch (_: Exception) {
            // View already removed
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        startAsForegroundService()
        if (!Settings.canDrawOverlays(this)) {
            isInstanceRunning = false
            prefs.edit().putBoolean(KEY_IS_SERVICE_RUNNING, false).apply()
            requestTileRefresh()
            stopSelf()
            return
        }

        isInstanceRunning = true
        prefs.edit().putBoolean(KEY_IS_SERVICE_RUNNING, true).apply()
        requestTileRefresh()

        createFullscreenSentinel()
        createFloatingOverlay()
        enforceActiveStreamMaxCap()
        syncBrightnessOverlay()
        syncOledShiftState()
        scheduleAutoDim()

        displayManager.registerDisplayListener(displayListener, mainHandler)
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)
        prefs.registerOnSharedPreferenceChangeListener(this)

        val filter = IntentFilter().apply {
            addAction(VOLUME_CHANGED_ACTION)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(this, systemEventReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiversRegistered = true

        lastHeadphoneConnected = null
        scheduleAudioDeviceCheck(forceApplyOnEnable = true)
        mainHandler.post(automationRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isInstanceRunning || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_VOL_UP -> {
                wakeFromIdleAndScheduleDim()
                adjustVolumeByStep(1)
            }
            ACTION_VOL_DOWN -> {
                wakeFromIdleAndScheduleDim()
                adjustVolumeByStep(-1)
            }
            ACTION_CYCLE_PRESET -> {
                wakeFromIdleAndScheduleDim()
                cycleNextPresetFromNotification()
            }
            ACTION_TOGGLE_MUTE -> {
                wakeFromIdleAndScheduleDim()
                toggleMuteSelectedStream()
                refreshForegroundNotification()
            }
            ACTION_TOGGLE_LOCK -> {
                val nextLocked = !isPositionLocked()
                prefs.edit().putBoolean(KEY_POSITION_LOCKED, nextLocked).apply()
                refreshForegroundNotification()
            }
            else -> {
                enforceActiveStreamMaxCap()
                applyAppearanceFromPrefs()
                syncBrightnessOverlay()
                syncOledShiftState()
                handleOrientationCheck(showNotice = false)
                scheduleAutoDim()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isInstanceRunning = false
        super.onDestroy()
        restorePreAppVolumeIfNeeded()
        mainHandler.removeCallbacks(longPressRunnable)
        mainHandler.removeCallbacks(brightnessLongPressRunnable)
        mainHandler.removeCallbacks(startRepeatRunnable)
        mainHandler.removeCallbacks(repeatVolumeRunnable)
        mainHandler.removeCallbacks(autoDimRunnable)
        mainHandler.removeCallbacks(dismissSliderRunnable)
        mainHandler.removeCallbacks(dismissBannerRunnable)
        mainHandler.removeCallbacks(oledShiftRunnable)
        mainHandler.removeCallbacks(automationRunnable)
        mainHandler.removeCallbacks(pendingAppearanceSyncRunnable)
        tapResolveRunnable?.let { mainHandler.removeCallbacks(it) }
        audioRouteCheckRunnable?.let { mainHandler.removeCallbacks(it) }
        if (receiversRegistered) {
            receiversRegistered = false
            try { displayManager.unregisterDisplayListener(displayListener) } catch (_: Exception) {}
            try { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) } catch (_: Exception) {}
            try { prefs.unregisterOnSharedPreferenceChangeListener(this) } catch (_: Exception) {}
            try { unregisterReceiver(systemEventReceiver) } catch (_: Exception) {}
        }

        prefs.edit().putBoolean(KEY_IS_SERVICE_RUNNING, false).apply()
        requestTileRefresh()

        dismissMiniSlider()
        dismissDeviceBanner()
        removeBrightnessOverlay()
        safeRemoveView(fullscreenSentinelView)
        fullscreenSentinelView = null
        safeRemoveView(floatingView)
        floatingView = null
        handleView = null
        handleGlyphView = null
        innerBadgeView = null
        sideBadgeView = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            KEY_WIDTH_DP, KEY_HEIGHT_DP, KEY_TRANSPARENCY_PCT, KEY_SHAPE, KEY_OVERLAY_COLOR, KEY_DISPLAY_MODE, KEY_HANDLE_ICON_STYLE, KEY_AUDIO_STREAM -> {
                if (key == KEY_AUDIO_STREAM) {
                    enforceActiveStreamMaxCap()
                }
                scheduleCoalescedAppearanceSync()
                if (key == KEY_AUDIO_STREAM) {
                    refreshForegroundNotification()
                }
            }
            KEY_PRESET_APPLY_SEQ -> {
                enforceActiveStreamMaxCap()
                scheduleCoalescedAppearanceSync()
                wakeFromIdleAndScheduleDim()
                applyFullscreenState(lastFullscreenDetected)
                refreshForegroundNotification()
            }
            KEY_PRESETS_JSON -> {
                refreshForegroundNotification()
            }
            KEY_MAX_VOLUME_LIMIT_PCT -> {
                enforceActiveStreamMaxCap()
                refreshPercentBadge()
                handleGlyphView?.invalidate()
                syncMiniSliderCallback?.invoke()
                refreshForegroundNotification()
            }
            KEY_AUTO_DIM_SEC, KEY_DIM_OPACITY_PCT -> {
                wakeFromIdleAndScheduleDim()
            }
            KEY_HIDE_FULLSCREEN -> {
                applyFullscreenState(lastFullscreenDetected)
            }
            KEY_POSITION_LOCKED -> {
                refreshForegroundNotification()
            }
            KEY_SNAP_TO_EDGE -> {
                if (prefs.getBoolean(KEY_SNAP_TO_EDGE, false)) {
                    snapCurrentOverlayToNearestEdge()
                }
            }
            KEY_OLED_PIXEL_SHIFT -> {
                syncOledShiftState()
            }
            KEY_AVOID_SAFE_ZONES -> {
                reapplyOverlayPositionFromSaved()
            }
            KEY_BRIGHTNESS_HANDLE_ENABLED, KEY_BRIGHTNESS_COLOR, KEY_BRIGHTNESS_WIDTH_DP, KEY_BRIGHTNESS_HEIGHT_DP -> {
                syncBrightnessOverlay()
                syncMiniSliderCallback?.invoke()
            }
            KEY_SHOW_BRIGHTNESS_IN_SLIDER -> {
                if (miniSliderView != null) {
                    showMiniSlider()
                }
            }
            KEY_QUIET_HOURS_ENABLED, KEY_QUIET_START, KEY_QUIET_END, KEY_QUIET_PRESET_ID,
            KEY_PER_APP_RULES_ENABLED, KEY_PER_APP_RULES_JSON -> {
                prefs.edit().putBoolean(KEY_MANUAL_OVERRIDE, false).apply()
                lastForegroundPackage = ""
                evaluateQuietHoursAndAppRules()
            }
            KEY_AUDIO_DEVICE_MODE, KEY_HEADPHONE_PRESET_ID, KEY_SPEAKER_PRESET_ID -> {
                val mode = prefs.getString(KEY_AUDIO_DEVICE_MODE, DEFAULT_AUDIO_DEVICE_MODE) ?: DEVICE_MODE_OFF
                if (mode == DEVICE_MODE_AUTO) {
                    scheduleAudioDeviceCheck(forceApplyOnEnable = true)
                }
            }
            KEY_POS_X_PORTRAIT, KEY_POS_Y_PORTRAIT, KEY_POS_X_LANDSCAPE, KEY_POS_Y_LANDSCAPE -> {
                moveOverlayToOrientationPosition(isCurrentlyLandscape(), showNotice = false)
            }
            KEY_BRIGHTNESS_POS_X_PORTRAIT, KEY_BRIGHTNESS_POS_Y_PORTRAIT,
            KEY_BRIGHTNESS_POS_X_LANDSCAPE, KEY_BRIGHTNESS_POS_Y_LANDSCAPE -> {
                if (!isBrightnessDragging) {
                    reapplyOverlayPositionFromSaved()
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            isCurrentlyLandscape()
        if (lastAppliedLandscape != isLandscape) {
            moveOverlayToOrientationPosition(isLandscape, showNotice = true)
        }
    }

    private fun isExternalAudioConnected(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { dev ->
            when (dev.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_HEADSET -> true
                else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                    dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                )
            }
        }
    }

    private fun scheduleAudioDeviceCheck(forceApplyOnEnable: Boolean) {
        audioRouteCheckRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            handleAudioDeviceChange(forceApplyOnEnable)
        }
        audioRouteCheckRunnable = runnable
        mainHandler.postDelayed(runnable, 180L)
    }

    private fun handleAudioDeviceChange(forceApplyOnEnable: Boolean = false) {
        val connected = isExternalAudioConnected()
        val previous = lastHeadphoneConnected
        val routeChanged = previous != null && previous != connected
        lastHeadphoneConnected = connected

        if (routeChanged) {
            prefs.edit().putBoolean(KEY_MANUAL_OVERRIDE, false).apply()
        }

        val mode = prefs.getString(KEY_AUDIO_DEVICE_MODE, DEFAULT_AUDIO_DEVICE_MODE) ?: DEVICE_MODE_OFF
        if (mode == DEVICE_MODE_OFF) return
        if (!routeChanged && !(forceApplyOnEnable && mode == DEVICE_MODE_AUTO)) return

        // Priority: Quiet Hours (#1) > Per-App Rule (#2) > Headphone/Bluetooth (#3)
        if (isQuietHoursCurrentlyActive()) return
        if (prefs.getBoolean(KEY_PER_APP_RULES_ENABLED, false)) {
            val fgPkg = lastForegroundPackage.ifEmpty { queryForegroundAppPackage() ?: "" }
            if (!getMatchedPerAppRulePresetId(fgPkg).isNullOrEmpty()) return
        }
        if (prefs.getBoolean(KEY_MANUAL_OVERRIDE, false) && !routeChanged) return

        val targetPresetId = if (connected) {
            prefs.getString(KEY_HEADPHONE_PRESET_ID, DEFAULT_HEADPHONE_PRESET_ID)
        } else {
            prefs.getString(KEY_SPEAKER_PRESET_ID, DEFAULT_SPEAKER_PRESET_ID)
        } ?: return

        val presetObj = findPresetById(targetPresetId) ?: return
        val presetName = presetObj.optString("name", "Preset")
        val routeLabel = if (connected) "Headphones / Bluetooth" else "Phone Speaker"

        if (mode == DEVICE_MODE_AUTO) {
            val activeId = prefs.getString("active_preset_id", "")
            if (activeId != presetObj.optString("id", "")) {
                applyPresetFromService(presetObj, isManualTap = false)
                if (routeChanged) {
                    Toast.makeText(this, "$routeLabel -> $presetName", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (mode == DEVICE_MODE_TOAST && routeChanged) {
            showDeviceSwitchBanner(routeLabel, presetObj)
        }
    }

    private fun findPresetById(presetId: String, allowFallbackToFirst: Boolean = false): JSONObject? {
        val raw = prefs.getString(KEY_PRESETS_JSON, null) ?: return null
        return try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optString("id") == presetId) return obj
            }
            if (allowFallbackToFirst) arr.optJSONObject(0) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun applyPresetFromService(obj: JSONObject, isManualTap: Boolean = false) {
        val colorStr = obj.optString("colorHex", "#2563EB")
        val parsedColor = try { Color.parseColor(colorStr) } catch (_: Exception) { DEFAULT_COLOR }
        val hapticStrength = obj.optString(
            "hapticStrength",
            if (obj.optBoolean("hapticTap", true)) HAPTIC_MEDIUM else HAPTIC_OFF
        )
        val gestures = obj.optJSONObject("gestureActions")
        val gDouble = gestures?.optString("doubleTap")?.takeIf { it.isNotEmpty() }
            ?: if (obj.optBoolean("doubleTapMute", false)) ACTION_ID_MUTE else ACTION_ID_NONE
        val gTriple = gestures?.optString("tripleTap")?.takeIf { it.isNotEmpty() }
            ?: if (obj.optBoolean("tripleTapPlayPause", false)) ACTION_ID_PLAY_PAUSE else ACTION_ID_NONE
        val gFlick = gestures?.optString("inwardFlick")?.takeIf { it.isNotEmpty() }
            ?: ACTION_ID_MINI_SLIDER
        val gLocked = gestures?.optString("lockedLongPress")?.takeIf { it.isNotEmpty() }
            ?: ACTION_ID_NONE
        val nextSeq = prefs.getInt(KEY_PRESET_APPLY_SEQ, 0) + 1
        val snapEdge = obj.optBoolean("snapToEdge", false)

        prefs.edit()
            .putString("active_preset_id", obj.optString("id", ""))
            .putBoolean(KEY_MANUAL_OVERRIDE, isManualTap)
            .putInt(KEY_PRESET_APPLY_SEQ, nextSeq)
            .putInt(KEY_WIDTH_DP, obj.optInt("widthDp", DEFAULT_WIDTH_DP))
            .putInt(KEY_HEIGHT_DP, obj.optInt("heightDp", DEFAULT_HEIGHT_DP))
            .putInt(KEY_TRANSPARENCY_PCT, obj.optInt("transparencyPct", DEFAULT_TRANSPARENCY_PCT))
            .putString(KEY_SHAPE, obj.optString("shape", SHAPE_CIRCLE))
            .putInt(KEY_OVERLAY_COLOR, parsedColor)
            .putString(KEY_AUDIO_STREAM, obj.optString("audioStream", STREAM_MUSIC))
            .putBoolean(KEY_HAPTIC_TAP, hapticStrength != HAPTIC_OFF)
            .putString(KEY_HAPTIC_STRENGTH, hapticStrength)
            .putBoolean(KEY_SNAP_TO_EDGE, snapEdge)
            .putString(KEY_GESTURE_DOUBLE_TAP, gDouble)
            .putString(KEY_GESTURE_TRIPLE_TAP, gTriple)
            .putString(KEY_GESTURE_INWARD_FLICK, gFlick)
            .putString(KEY_GESTURE_LOCKED_LONG_PRESS, gLocked)
            .putBoolean(KEY_DOUBLE_TAP_MUTE, gDouble != ACTION_ID_NONE)
            .putBoolean(KEY_TRIPLE_TAP_MEDIA, gTriple != ACTION_ID_NONE)
            .putBoolean(KEY_HOLD_TO_REPEAT, obj.optBoolean("holdToRepeat", true))
            .putInt(KEY_AUTO_DIM_SEC, obj.optInt("autoDimSeconds", 0))
            .putInt(KEY_DIM_OPACITY_PCT, obj.optInt("dimOpacityPct", 15))
            .putInt(KEY_VOLUME_STEP, obj.optInt("volumeStep", 1))
            .putInt(KEY_MAX_VOLUME_LIMIT_PCT, obj.optInt("maxVolumeLimitPct", 100))
            .putBoolean(KEY_SWIPE_SCRUB, obj.optBoolean("swipeScrub", true))
            .putString(KEY_POPOUT_MODE, obj.optString("popoutSliderMode", POPOUT_SWIPE_INWARD))
            .putString(KEY_DISPLAY_MODE, obj.optString("overlayDisplayMode", DISPLAY_MINIMAL))
            .putString(KEY_HANDLE_ICON_STYLE, obj.optString("handleIconStyle", DEFAULT_HANDLE_ICON_STYLE))
            .putBoolean(KEY_SMOOTH_FADE, obj.optBoolean("smoothFadeAnimation", true))
            .putBoolean(KEY_HIDE_FULLSCREEN, obj.optBoolean("hideInFullscreen", false))
            .apply()
        enforceActiveStreamMaxCap()
        scheduleCoalescedAppearanceSync()
        refreshForegroundNotification()
    }

    private fun cycleNextPresetFromNotification() {
        val raw = prefs.getString(KEY_PRESETS_JSON, null) ?: return
        val arr = try { JSONArray(raw) } catch (_: Exception) { return }
        if (arr.length() == 0) return

        val curId = prefs.getString("active_preset_id", "") ?: ""
        var curIdx = -1
        for (i in 0 until arr.length()) {
            if (arr.optJSONObject(i)?.optString("id") == curId) {
                curIdx = i
                break
            }
        }
        val nextObj = arr.optJSONObject((curIdx + 1) % arr.length()) ?: return
        val deviceMode = prefs.getString(KEY_AUDIO_DEVICE_MODE, DEFAULT_AUDIO_DEVICE_MODE) ?: DEVICE_MODE_OFF
        val anyRuleActive = isQuietHoursCurrentlyActive() ||
            (prefs.getBoolean(KEY_PER_APP_RULES_ENABLED, false) &&
                !prefs.getString(KEY_ACTIVE_APP_RULE_LABEL, "").isNullOrEmpty()) ||
            (deviceMode == DEVICE_MODE_AUTO && isExternalAudioConnected())
        applyPresetFromService(nextObj, isManualTap = anyRuleActive)
        Toast.makeText(this, "Preset: ${nextObj.optString("name", "Preset")}", Toast.LENGTH_SHORT).show()
    }

    private fun makeServicePill(fillHex: String, strokeHex: String, radiusDp: Float = 8f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(fillHex))
            cornerRadius = radiusDp * resources.displayMetrics.density
            setStroke(dpToPx(1), Color.parseColor(strokeHex))
        }
    }

    private fun showDeviceSwitchBanner(routeLabel: String, presetObj: JSONObject) {
        dismissDeviceBanner()
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val presetName = presetObj.optString("name", "Preset")
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val hPad = dpToPx(12)
            val vPad = dpToPx(10)
            setPadding(hPad, vPad, hPad, vPad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F50F172A"))
                cornerRadius = dpToPx(14).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#38BDF8"))
            }
        }

        val labelTv = TextView(this).apply {
            text = "$routeLabel connected"
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        banner.addView(labelTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val applyBtn = Button(this).apply {
            text = "Apply $presetName"
            textSize = 11f
            isAllCaps = false
            setSingleLine(true)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.WHITE)
            setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))
            background = makeServicePill("#2563EB", "#3B82F6", 8f)
            setOnClickListener {
                applyPresetFromService(presetObj, isManualTap = true)
                dismissDeviceBanner()
            }
        }
        val applyLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = dpToPx(6) }
        banner.addView(applyBtn, applyLp)

        val dismissBtn = Button(this).apply {
            text = "✕"
            textSize = 11f
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            background = makeServicePill("#1E293B", "#334155", 8f)
            setOnClickListener { dismissDeviceBanner() }
        }
        banner.addView(dismissBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val (screenW, _) = getScreenDimensionsPx()
        val avoidSafe = prefs.getBoolean(KEY_AVOID_SAFE_ZONES, DEFAULT_AVOID_SAFE_ZONES)
        val sLeft = if (avoidSafe) safeInsetLeft else 0
        val sRight = if (avoidSafe) safeInsetRight else 0
        val sTop = if (avoidSafe) safeInsetTop else 0
        val bannerW = min(
            dpToPx(300),
            (screenW - sLeft - sRight - dpToPx(24)).coerceAtLeast(dpToPx(180))
        )
        val lp = WindowManager.LayoutParams(
            bannerW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = max(dpToPx(36), sTop + dpToPx(8))
        }

        deviceBannerView = banner
        deviceBannerLayoutParams = lp
        if (safeAddView(banner, lp)) {
            repositionMiniSliderCallback?.invoke()
            mainHandler.postDelayed(dismissBannerRunnable, 6500L)
        } else {
            deviceBannerView = null
            deviceBannerLayoutParams = null
        }
    }

    private fun repositionDeviceBannerForOrientation() {
        val banner = deviceBannerView ?: return
        val lp = deviceBannerLayoutParams ?: return
        if (!banner.isAttachedToWindow) return
        val (screenW, _) = getScreenDimensionsPx()
        val avoidSafe = prefs.getBoolean(KEY_AVOID_SAFE_ZONES, DEFAULT_AVOID_SAFE_ZONES)
        val sLeft = if (avoidSafe) safeInsetLeft else 0
        val sRight = if (avoidSafe) safeInsetRight else 0
        val sTop = if (avoidSafe) safeInsetTop else 0
        lp.width = min(
            dpToPx(300),
            (screenW - sLeft - sRight - dpToPx(24)).coerceAtLeast(dpToPx(180))
        )
        lp.y = max(dpToPx(36), sTop + dpToPx(8))
        safeUpdateViewLayout(banner, lp)
    }

    private fun dismissDeviceBanner() {
        mainHandler.removeCallbacks(dismissBannerRunnable)
        safeRemoveView(deviceBannerView)
        deviceBannerView = null
        deviceBannerLayoutParams = null
        repositionMiniSliderCallback?.invoke()
    }

    private fun requestTileRefresh() {
        try {
            TileService.requestListeningState(
                this,
                ComponentName(this, FloatingVolumeTileService::class.java)
            )
        } catch (_: Exception) {
            // Ignored on restricted OEM builds
        }
    }

    private fun handleOrientationCheck(showNotice: Boolean) {
        val isLandscape = isCurrentlyLandscape()
        if (lastAppliedLandscape != isLandscape) {
            moveOverlayToOrientationPosition(isLandscape, showNotice = showNotice)
        }
    }

    private fun isCurrentlyLandscape(): Boolean {
        val (screenW, screenH) = getScreenDimensionsPx()
        return screenW > screenH
    }

    private fun shouldDockToEdge(): Boolean {
        return prefs.getBoolean(KEY_SNAP_TO_EDGE, false) ||
            prefs.getString(KEY_SHAPE, SHAPE_CIRCLE) == SHAPE_EDGE_TAB
    }

    private fun getDockedEdgeX(rawX: Int, totalW: Int): Int {
        val (screenW, _) = getScreenDimensionsPx()
        val rawMaxX = (screenW - totalW).coerceAtLeast(0)
        val avoidSafe = prefs.getBoolean(KEY_AVOID_SAFE_ZONES, DEFAULT_AVOID_SAFE_ZONES)
        val minX = if (avoidSafe) safeInsetLeft.coerceIn(0, rawMaxX) else 0
        val maxX = if (avoidSafe) (screenW - safeInsetRight - totalW).coerceIn(minX, rawMaxX) else rawMaxX
        return if (rawX + totalW / 2 < screenW / 2) minX else maxX
    }

    private fun resolveBrightnessNonOverlap(
        volX: Int,
        volY: Int,
        brightX: Int,
        brightY: Int
    ): Pair<Int, Int> {
        val (clampedBX, clampedBY) = clampBrightnessCoordinates(brightX, brightY)
        val (screenW, screenH) = getScreenDimensionsPx()
        val bw = brightnessHandleW.takeIf { it > 0 } ?: dpToPx(DEFAULT_BRIGHTNESS_WIDTH_DP)
        val bh = brightnessHandleH.takeIf { it > 0 } ?: dpToPx(DEFAULT_BRIGHTNESS_HEIGHT_DP)
        val avoidSafe = prefs.getBoolean(KEY_AVOID_SAFE_ZONES, DEFAULT_AVOID_SAFE_ZONES)
        val minBX = if (avoidSafe) safeInsetLeft.coerceIn(0, (screenW - bw).coerceAtLeast(0)) else 0
        val maxBX = if (avoidSafe) (screenW - safeInsetRight - bw).coerceIn(minBX, (screenW - bw).coerceAtLeast(0)) else (screenW - bw).coerceAtLeast(0)
        val minBY = if (avoidSafe) safeInsetTop.coerceIn(0, (screenH - bh).coerceAtLeast(0)) else 0
        val maxBY = if (avoidSafe) (screenH - safeInsetBottom - bh).coerceIn(minBY, (screenH - bh).coerceAtLeast(0)) else (screenH - bh).coerceAtLeast(0)

        val volW = currentRootWidthPx()
        val volH = currentHandleHeightPx()
        val pad = dpToPx(6)

        fun overlaps(cx: Int, cy: Int): Boolean {
            return cx < volX + volW + pad &&
                cx + bw + pad > volX &&
                cy < volY + volH + pad &&
                cy + bh + pad > volY
        }

        if (!overlaps(clampedBX, clampedBY)) {
            return clampedBX to clampedBY
        }

        val gap = dpToPx(8)
        val volOnRight = isOverlayOnRightHalf(volX)
        val candidates = listOf(
            clampedBX to (volY + volH + gap).coerceIn(minBY, maxBY),
            clampedBX to (volY - bh - gap).coerceIn(minBY, maxBY),
            (if (volOnRight) minBX else maxBX) to clampedBY,
            (if (volOnRight) (volX - bw - gap) else (volX + volW + gap)).coerceIn(minBX, maxBX) to clampedBY
        )
        return candidates.firstOrNull { (cx, cy) -> !overlaps(cx, cy) } ?: candidates[2]
    }

    private fun moveOverlayToOrientationPosition(isLandscape: Boolean, showNotice: Boolean) {
        val view = floatingView ?: return
        val (savedX, savedY) = loadPositionForOrientation(isLandscape)
        var (clampedX, clampedY) = clampCoordinates(savedX, savedY)
        if (shouldDockToEdge()) {
            clampedX = getDockedEdgeX(clampedX, currentRootWidthPx())
        }

        layoutParams.x = clampedX
        layoutParams.y = clampedY
        lastAppliedLandscape = isLandscape

        applyAppearanceFromPrefs(addIfMissing = false)

        safeUpdateViewLayout(view, layoutParams)

        brightnessFloatingView?.let { bView ->
            if (bView.isAttachedToWindow && !isBrightnessDragging) {
                val (screenW, screenH) = getScreenDimensionsPx()
                val savedBX = if (isLandscape) {
                    prefs.getInt(KEY_BRIGHTNESS_POS_X_LANDSCAPE, (screenW * 0.85f).toInt())
                } else {
                    prefs.getInt(KEY_BRIGHTNESS_POS_X_PORTRAIT, (screenW * 0.10f).toInt())
                }
                val savedBY = if (isLandscape) {
                    prefs.getInt(KEY_BRIGHTNESS_POS_Y_LANDSCAPE, (screenH * 0.30f).toInt())
                } else {
                    prefs.getInt(KEY_BRIGHTNESS_POS_Y_PORTRAIT, (screenH * 0.56f).toInt())
                }
                var (bx, by) = clampBrightnessCoordinates(savedBX + oledOffsetPxX, savedBY + oledOffsetPxY)
                if (prefs.getBoolean(KEY_SNAP_TO_EDGE, false)) {
                    bx = getDockedEdgeX(bx, brightnessHandleW)
                }
                val resolved = resolveBrightnessNonOverlap(layoutParams.x, layoutParams.y, bx, by)
                brightnessLayoutParams.x = resolved.first
                brightnessLayoutParams.y = resolved.second
                safeUpdateViewLayout(bView, brightnessLayoutParams)
            }
        }

        repositionDeviceBannerForOrientation()
        repositionMiniSliderCallback?.invoke()
        refreshForegroundNotification()

        val modeLabel = if (isLandscape) "Landscape" else "Portrait"
        if (showNotice && prefs.getBoolean(KEY_ROTATE_TOAST, true)) {
            Toast.makeText(
                this,
                "$modeLabel detected -> moved to ($clampedX, $clampedY)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun snapCurrentOverlayToNearestEdge() {
        val view = floatingView ?: return
        val snappedX = getDockedEdgeX(layoutParams.x, currentRootWidthPx())

        layoutParams.x = snappedX
        applyAppearanceFromPrefs(addIfMissing = false)
        safeUpdateViewLayout(view, layoutParams)
        savePositionForCurrentOrientation(layoutParams.x, layoutParams.y)
        brightnessFloatingView?.let { bView ->
            if (bView.isAttachedToWindow && !isBrightnessDragging) {
                val resolved = resolveBrightnessNonOverlap(
                    layoutParams.x,
                    layoutParams.y,
                    brightnessLayoutParams.x,
                    brightnessLayoutParams.y
                )
                brightnessLayoutParams.x = resolved.first
                brightnessLayoutParams.y = resolved.second
                safeUpdateViewLayout(bView, brightnessLayoutParams)
            }
        }
        repositionMiniSliderCallback?.invoke()
    }

    private fun isPositionLocked(): Boolean {
        return prefs.getBoolean(KEY_POSITION_LOCKED, false)
    }

    private fun startAsForegroundService() {
        createNotificationChannel()
        val notification = buildForegroundNotification(buildNotificationStatusText())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Floating Volume Control",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating volume overlay active across apps"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotificationStatusText(): String {
        val modeLabel = if (isCurrentlyLandscape()) "Landscape" else "Portrait"
        val lockState = if (isPositionLocked()) "Locked" else "Unlocked"
        val activeId = prefs.getString("active_preset_id", "") ?: ""
        val presetName = if (activeId == "custom" || activeId.isEmpty()) {
            "Custom"
        } else {
            findPresetById(activeId)?.takeIf { it.optString("id") == activeId }?.optString("name", "Custom") ?: "Custom"
        }
        return "$modeLabel · $presetName · $lockState"
    }

    private fun refreshForegroundNotification() {
        notificationManager.notify(
            NOTIFICATION_ID,
            buildForegroundNotification(buildNotificationStatusText())
        )
    }

    private fun buildServiceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val actionIntent = Intent(this, FloatingVolumeService::class.java).apply {
            this.action = action
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, requestCode, actionIntent, piFlags)
        } else {
            PendingIntent.getService(this, requestCode, actionIntent, piFlags)
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stream = getSelectedAudioStream()
        val muted = audioManager.getStreamVolume(stream) == 0 || audioManager.isStreamMute(stream)
        val muteLabel = if (muted) "Unmute" else "Mute"
        val lockLabel = if (isPositionLocked()) "Unlock" else "Lock"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(0, "Preset ↻", buildServiceActionPendingIntent(ACTION_CYCLE_PRESET, 13))
            .addAction(0, muteLabel, buildServiceActionPendingIntent(ACTION_TOGGLE_MUTE, 14))
            .addAction(0, lockLabel, buildServiceActionPendingIntent(ACTION_TOGGLE_LOCK, 12))
            .build()
    }

    private fun createFullscreenSentinel() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val sentinel = View(this).apply {
            alpha = 0f
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sentinel.setOnApplyWindowInsetsListener { _, insets ->
                val statusHidden = !insets.isVisible(WindowInsets.Type.statusBars())
                applyFullscreenState(statusHidden)
                val sysCutout = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.displayCutout() or WindowInsets.Type.systemBars()
                )
                val imeInsets = insets.getInsets(WindowInsets.Type.ime())
                val nextL = kotlin.math.max(sysCutout.left, imeInsets.left)
                val nextT = kotlin.math.max(sysCutout.top, imeInsets.top)
                val nextR = kotlin.math.max(sysCutout.right, imeInsets.right)
                val nextB = kotlin.math.max(sysCutout.bottom, imeInsets.bottom)
                if (nextL != safeInsetLeft || nextT != safeInsetTop || nextR != safeInsetRight || nextB != safeInsetBottom) {
                    safeInsetLeft = nextL
                    safeInsetTop = nextT
                    safeInsetRight = nextR
                    safeInsetBottom = nextB
                    repositionOverlaysForSafeZones()
                }
                insets
            }
        } else {
            @Suppress("DEPRECATION")
            sentinel.setOnApplyWindowInsetsListener { _, insets ->
                val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) insets.displayCutout else null
                val nextL = kotlin.math.max(insets.systemWindowInsetLeft, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) (cutout?.safeInsetLeft ?: 0) else 0)
                val nextT = kotlin.math.max(insets.systemWindowInsetTop, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) (cutout?.safeInsetTop ?: 0) else 0)
                val nextR = kotlin.math.max(insets.systemWindowInsetRight, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) (cutout?.safeInsetRight ?: 0) else 0)
                val nextB = kotlin.math.max(insets.systemWindowInsetBottom, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) (cutout?.safeInsetBottom ?: 0) else 0)
                if (nextL != safeInsetLeft || nextT != safeInsetTop || nextR != safeInsetRight || nextB != safeInsetBottom) {
                    safeInsetLeft = nextL
                    safeInsetTop = nextT
                    safeInsetRight = nextR
                    safeInsetBottom = nextB
                    repositionOverlaysForSafeZones()
                }
                insets
            }
            @Suppress("DEPRECATION")
            sentinel.setOnSystemUiVisibilityChangeListener { visibility ->
                val statusHidden = (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) != 0
                applyFullscreenState(statusHidden)
            }
        }

        fullscreenSentinelView = sentinel
        if (safeAddView(sentinel, lp)) {
            sentinel.requestApplyInsets()
        }
    }

    private fun reapplyOverlayPositionFromSaved() {
        val view = floatingView
        if (view != null && view.isAttachedToWindow && !isDragging) {
            val (savedX, savedY) = loadPositionForOrientation(isCurrentlyLandscape())
            var (cx, cy) = clampCoordinates(savedX + oledOffsetPxX, savedY + oledOffsetPxY)
            if (shouldDockToEdge()) {
                cx = getDockedEdgeX(cx, currentRootWidthPx())
            }
            layoutParams.x = cx
            layoutParams.y = cy
            applyAppearanceFromPrefs(addIfMissing = true)
        }
        val bView = brightnessFloatingView
        if (bView != null && bView.isAttachedToWindow && !isBrightnessDragging) {
            val isLand = isCurrentlyLandscape()
            val (screenW, screenH) = getScreenDimensionsPx()
            val savedBX = if (isLand) {
                prefs.getInt(KEY_BRIGHTNESS_POS_X_LANDSCAPE, (screenW * 0.85f).toInt())
            } else {
                prefs.getInt(KEY_BRIGHTNESS_POS_X_PORTRAIT, (screenW * 0.10f).toInt())
            }
            val savedBY = if (isLand) {
                prefs.getInt(KEY_BRIGHTNESS_POS_Y_LANDSCAPE, (screenH * 0.30f).toInt())
            } else {
                prefs.getInt(KEY_BRIGHTNESS_POS_Y_PORTRAIT, (screenH * 0.56f).toInt())
            }
            var (bx, by) = clampBrightnessCoordinates(savedBX + oledOffsetPxX, savedBY + oledOffsetPxY)
            if (prefs.getBoolean(KEY_SNAP_TO_EDGE, false)) {
                bx = getDockedEdgeX(bx, brightnessHandleW)
            }
            val resolved = resolveBrightnessNonOverlap(layoutParams.x, layoutParams.y, bx, by)
            brightnessLayoutParams.x = resolved.first
            brightnessLayoutParams.y = resolved.second
            safeUpdateViewLayout(bView, brightnessLayoutParams)
        }
    }

    private fun repositionOverlaysForSafeZones() {
        if (prefs.getBoolean(KEY_AVOID_SAFE_ZONES, DEFAULT_AVOID_SAFE_ZONES)) {
            reapplyOverlayPositionFromSaved()
        }
        repositionDeviceBannerForOrientation()
        repositionMiniSliderCallback?.invoke()
    }

    private fun applyFullscreenState(fullscreenDetected: Boolean) {
        lastFullscreenDetected = fullscreenDetected
        val shouldHide = fullscreenDetected && prefs.getBoolean(KEY_HIDE_FULLSCREEN, false)
        if (isFullscreenHidden == shouldHide) return
        isFullscreenHidden = shouldHide
        floatingView?.visibility = if (shouldHide) View.GONE else View.VISIBLE
        brightnessFloatingView?.visibility = if (shouldHide) View.GONE else View.VISIBLE
        if (shouldHide) {
            dismissMiniSlider()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingOverlay() {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val isLandscape = isCurrentlyLandscape()
        lastAppliedLandscape = isLandscape
        val (startX, startY) = loadPositionForOrientation(isLandscape)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }

        val innerBadge = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            textSize = 10f
            setSingleLine(true)
            includeFontPadding = false
            visibility = View.GONE
        }

        val glyphView = object : View(this) {
            private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val glyphPath = Path()
            private val barRect = RectF()

            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val w = width.toFloat()
                val h = height.toFloat()
                if (w <= 0f || h <= 0f) return
                val basePct = prefs.getInt(KEY_TRANSPARENCY_PCT, DEFAULT_TRANSPARENCY_PCT).coerceIn(0, 100)
                val dimPct = prefs.getInt(KEY_DIM_OPACITY_PCT, DEFAULT_DIM_OPACITY_PCT).coerceIn(0, 50)
                val effectivePct = if (isIdleDimmed) min(basePct, dimPct) else basePct
                val contentAlpha = if (effectivePct == 0) 0 else ((effectivePct / 100f) * 235f).toInt().coerceIn(50, 245)
                if (contentAlpha == 0) return

                val cx = w / 2f
                val cy = h / 2f
                val minSide = min(w, h)
                val strokeW = (minSide * 0.085f).coerceIn(dpToPx(1).toFloat(), dpToPx(3).toFloat())
                fillPaint.color = Color.argb(contentAlpha, 255, 255, 255)
                strokePaint.color = Color.argb(contentAlpha, 255, 255, 255)
                strokePaint.strokeWidth = strokeW

                if (isDragging) {
                    val arm = (minSide * 0.24f).coerceIn(dpToPx(5).toFloat(), dpToPx(12).toFloat())
                    canvas.drawLine(cx - arm, cy, cx + arm, cy, strokePaint)
                    canvas.drawLine(cx, cy - arm, cx, cy + arm, strokePaint)
                    return
                }

                val displayMode = prefs.getString(KEY_DISPLAY_MODE, DEFAULT_DISPLAY_MODE) ?: DISPLAY_MINIMAL
                if (displayMode == DISPLAY_SLIDER_FOCUS) {
                    val barW = (w * 0.44f).coerceIn(dpToPx(8).toFloat(), dpToPx(18).toFloat())
                    val dy = dpToPx(5).toFloat()
                    canvas.drawLine(cx - barW / 2f, cy - dy, cx + barW / 2f, cy - dy, strokePaint)
                    canvas.drawLine(cx - barW / 2f, cy, cx + barW / 2f, cy, strokePaint)
                    canvas.drawLine(cx - barW / 2f, cy + dy, cx + barW / 2f, cy + dy, strokePaint)
                    return
                }

                val iconStyle = prefs.getString(KEY_HANDLE_ICON_STYLE, DEFAULT_HANDLE_ICON_STYLE) ?: ICON_STYLE_ARROWS
                when (iconStyle) {
                    ICON_STYLE_NONE -> Unit
                    ICON_STYLE_DOT -> {
                        val r = (minSide * 0.14f).coerceIn(dpToPx(2).toFloat(), dpToPx(5).toFloat())
                        canvas.drawCircle(cx, cy, r, fillPaint)
                    }
                    ICON_STYLE_BARS -> {
                        val s = getSelectedAudioStream()
                        val maxVol = audioManager.getStreamMaxVolume(s).coerceAtLeast(1)
                        val curVol = audioManager.getStreamVolume(s).coerceIn(0, maxVol)
                        val muted = curVol == 0 || audioManager.isStreamMute(s)
                        val volPct = if (muted) 0 else ((curVol.toFloat() / maxVol) * 100f).roundToInt()
                        val barW = (minSide * 0.11f).coerceIn(dpToPx(2).toFloat(), dpToPx(4).toFloat())
                        val gap = barW * 0.9f
                        val heights = floatArrayOf(0.24f, 0.36f, 0.48f)
                        val thresholds = intArrayOf(5, 40, 75)
                        val baseY = cy + (minSide * 0.24f)
                        val startX = cx - (3f * barW + 2f * gap) / 2f
                        for (i in 0..2) {
                            val bh = (minSide * heights[i]).coerceAtLeast(dpToPx(4).toFloat())
                            val x0 = startX + i * (barW + gap)
                            barRect.set(x0, baseY - bh, x0 + barW, baseY)
                            val activeAlpha = if (volPct >= thresholds[i]) contentAlpha else (contentAlpha * 0.3f).toInt()
                            fillPaint.color = Color.argb(activeAlpha, 255, 255, 255)
                            canvas.drawRoundRect(barRect, barW / 2f, barW / 2f, fillPaint)
                        }
                    }
                    ICON_STYLE_SPEAKER -> {
                        val s = getSelectedAudioStream()
                        val curVol = audioManager.getStreamVolume(s)
                        val muted = curVol == 0 || audioManager.isStreamMute(s)
                        val u = (minSide * 0.22f).coerceIn(dpToPx(4).toFloat(), dpToPx(11).toFloat())
                        glyphPath.reset()
                        glyphPath.moveTo(cx - u * 0.85f, cy - u * 0.36f)
                        glyphPath.lineTo(cx - u * 0.35f, cy - u * 0.36f)
                        glyphPath.lineTo(cx + u * 0.18f, cy - u * 0.82f)
                        glyphPath.lineTo(cx + u * 0.18f, cy + u * 0.82f)
                        glyphPath.lineTo(cx - u * 0.35f, cy + u * 0.36f)
                        glyphPath.lineTo(cx - u * 0.85f, cy + u * 0.36f)
                        glyphPath.close()
                        canvas.drawPath(glyphPath, fillPaint)
                        if (muted) {
                            canvas.drawLine(cx + u * 0.38f, cy - u * 0.38f, cx + u * 0.92f, cy + u * 0.38f, strokePaint)
                            canvas.drawLine(cx + u * 0.92f, cy - u * 0.38f, cx + u * 0.38f, cy + u * 0.38f, strokePaint)
                        } else {
                            barRect.set(cx + u * 0.05f, cy - u * 0.45f, cx + u * 0.65f, cy + u * 0.45f)
                            canvas.drawArc(barRect, -48f, 96f, false, strokePaint)
                            barRect.set(cx + u * 0.22f, cy - u * 0.78f, cx + u * 1.02f, cy + u * 0.78f)
                            canvas.drawArc(barRect, -48f, 96f, false, strokePaint)
                        }
                    }
                    else -> {
                        val chevW = (minSide * 0.20f).coerceIn(dpToPx(4).toFloat(), dpToPx(9).toFloat())
                        val chevH = chevW * 0.58f
                        val gapY = (min(h * 0.18f, minSide * 0.22f)).coerceAtLeast(dpToPx(4).toFloat())
                        glyphPath.reset()
                        glyphPath.moveTo(cx - chevW, cy - gapY + chevH * 0.5f)
                        glyphPath.lineTo(cx, cy - gapY - chevH * 0.5f)
                        glyphPath.lineTo(cx + chevW, cy - gapY + chevH * 0.5f)
                        glyphPath.moveTo(cx - chevW, cy + gapY - chevH * 0.5f)
                        glyphPath.lineTo(cx, cy + gapY + chevH * 0.5f)
                        glyphPath.lineTo(cx + chevW, cy + gapY - chevH * 0.5f)
                        canvas.drawPath(glyphPath, strokePaint)
                    }
                }
            }
        }

        val handle = FrameLayout(this).apply {
            background = overlayDrawable
            addView(
                glyphView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
            addView(
                innerBadge,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }

        val sideBadge = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            textSize = 10f
            setSingleLine(true)
            includeFontPadding = false
            setPadding(dpToPx(7), dpToPx(4), dpToPx(7), dpToPx(4))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E60F172A"))
                cornerRadius = dpToPx(99).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#475569"))
            }
            visibility = View.GONE
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = false
            addView(handle, LinearLayout.LayoutParams(dpToPx(DEFAULT_WIDTH_DP), dpToPx(DEFAULT_HEIGHT_DP)))
            addView(sideBadge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val scrubStepPx = dpToPx(18).toFloat()
        val inwardFlickPx = dpToPx(20).toFloat()

        root.setOnTouchListener { v, event ->
            val targetH = handle.height.takeIf { it > 0 } ?: currentHandleHeightPx()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    wakeFromIdleAndScheduleDim()
                    resetOledShiftToZero()
                    isDragging = false
                    isScrubbing = false
                    repeatActive = false
                    lockedLongPressTriggered = false
                    scrubAccumY = 0f
                    repeatUpperHalf = event.y < targetH / 2f
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    if (!isPositionLocked()) {
                        mainHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS)
                    } else {
                        val lockedAction = prefs.getString(
                            KEY_GESTURE_LOCKED_LONG_PRESS,
                            DEFAULT_GESTURE_LOCKED_LONG_PRESS
                        ) ?: ACTION_ID_NONE
                        if (lockedAction != ACTION_ID_NONE || prefs.getBoolean(KEY_HOLD_TO_REPEAT, true)) {
                            mainHandler.postDelayed(startRepeatRunnable, REPEAT_START_DELAY_MS)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    if (!isDragging) {
                        if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            mainHandler.removeCallbacks(longPressRunnable)
                            mainHandler.removeCallbacks(startRepeatRunnable)
                            mainHandler.removeCallbacks(repeatVolumeRunnable)

                            if (prefs.getBoolean(KEY_SWIPE_SCRUB, true) && abs(dy) > abs(dx) * 1.25f) {
                                if (!isScrubbing) {
                                    isScrubbing = true
                                    scrubAccumY = 0f
                                }
                                val deltaY = dy - scrubAccumY
                                if (abs(deltaY) >= scrubStepPx) {
                                    val steps = (deltaY / scrubStepPx).toInt()
                                    scrubAccumY += steps * scrubStepPx
                                    triggerHapticIfEnabled(v)
                                    adjustVolumeByDelta(-steps)
                                }
                            }
                        }
                    } else if (!isPositionLocked()) {
                        val (clampedX, clampedY) = clampCoordinates(
                            initialX + dx.toInt(),
                            initialY + dy.toInt()
                        )
                        layoutParams.x = clampedX
                        layoutParams.y = clampedY
                        applyAppearanceFromPrefs()
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    mainHandler.removeCallbacks(startRepeatRunnable)
                    mainHandler.removeCallbacks(repeatVolumeRunnable)
                    scheduleAutoDim()

                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    if (repeatActive || isScrubbing || lockedLongPressTriggered) {
                        repeatActive = false
                        isScrubbing = false
                        lockedLongPressTriggered = false
                    } else if (isDragging) {
                        isDragging = false
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                        if (shouldDockToEdge()) {
                            snapCurrentOverlayToNearestEdge()
                        } else {
                            applyAppearanceFromPrefs()
                            savePositionForCurrentOrientation(layoutParams.x, layoutParams.y)
                        }
                    } else if (abs(dx) >= inwardFlickPx && abs(dx) > abs(dy) * 1.15f) {
                        val flickAction = prefs.getString(
                            KEY_GESTURE_INWARD_FLICK,
                            DEFAULT_GESTURE_INWARD_FLICK
                        ) ?: ACTION_ID_MINI_SLIDER
                        executeGestureAction(flickAction, v)
                    } else if (hypot(dx.toDouble(), dy.toDouble()) <= touchSlop) {
                        handleOverlayTap(v, event.y < targetH / 2f)
                        v.performClick()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    mainHandler.removeCallbacks(startRepeatRunnable)
                    mainHandler.removeCallbacks(repeatVolumeRunnable)
                    repeatActive = false
                    isScrubbing = false
                    lockedLongPressTriggered = false
                    scheduleAutoDim()
                    if (isDragging) {
                        isDragging = false
                        handleGlyphView?.invalidate()
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    }
                    true
                }

                else -> false
            }
        }

        floatingView = root
        handleView = handle
        handleGlyphView = glyphView
        innerBadgeView = innerBadge
        sideBadgeView = sideBadge
        applyAppearanceFromPrefs(addIfMissing = false)

        val (clampedX, clampedY) = clampCoordinates(layoutParams.x, layoutParams.y)
        layoutParams.x = clampedX
        layoutParams.y = clampedY
        reorderSideBadgeForScreenEdge()

        safeAddView(root, layoutParams)
    }

    private fun reorderSideBadgeForScreenEdge() {
        val root = floatingView ?: return
        val handle = handleView ?: return
        val sideBadge = sideBadgeView ?: return
        val onRightSide = isOverlayOnRightHalf(layoutParams.x)

        val sideLp = (sideBadge.layoutParams as? LinearLayout.LayoutParams)
            ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        if (onRightSide) {
            sideLp.marginStart = 0
            sideLp.marginEnd = dpToPx(6)
        } else {
            sideLp.marginStart = dpToPx(6)
            sideLp.marginEnd = 0
        }
        sideBadge.layoutParams = sideLp

        val firstChild = root.getChildAt(0)
        val wantSideFirst = onRightSide
        if ((wantSideFirst && firstChild != sideBadge) || (!wantSideFirst && firstChild != handle)) {
            root.removeView(handle)
            root.removeView(sideBadge)
            if (wantSideFirst) {
                root.addView(sideBadge)
                root.addView(handle)
            } else {
                root.addView(handle)
                root.addView(sideBadge)
            }
        }
    }

    private fun handleOverlayTap(view: View, isUpperHalf: Boolean) {
        val doubleAction = prefs.getString(KEY_GESTURE_DOUBLE_TAP, DEFAULT_GESTURE_DOUBLE_TAP) ?: ACTION_ID_NONE
        val tripleAction = prefs.getString(KEY_GESTURE_TRIPLE_TAP, DEFAULT_GESTURE_TRIPLE_TAP) ?: ACTION_ID_NONE
        val popoutMode = prefs.getString(KEY_POPOUT_MODE, POPOUT_SWIPE_INWARD)
        val displayMode = prefs.getString(KEY_DISPLAY_MODE, DEFAULT_DISPLAY_MODE)
        val openSliderOnSingleTap = popoutMode == POPOUT_SINGLE_TAP || displayMode == DISPLAY_SLIDER_FOCUS

        if (doubleAction == ACTION_ID_NONE && tripleAction == ACTION_ID_NONE) {
            triggerHapticIfEnabled(view)
            if (openSliderOnSingleTap) {
                toggleMiniSlider()
            } else {
                adjustVolumeByStep(if (isUpperHalf) 1 else -1)
            }
            return
        }

        tapCount += 1
        tapUpperHalf = isUpperHalf
        tapResolveRunnable?.let { mainHandler.removeCallbacks(it) }

        if (tapCount >= 3 && tripleAction != ACTION_ID_NONE) {
            tapCount = 0
            tapResolveRunnable = null
            executeGestureAction(tripleAction, view)
            return
        }

        val runnable = Runnable {
            val count = tapCount
            tapCount = 0
            tapResolveRunnable = null
            when {
                count >= 3 && tripleAction != ACTION_ID_NONE -> executeGestureAction(tripleAction, view)
                count == 2 && doubleAction != ACTION_ID_NONE -> executeGestureAction(doubleAction, view)
                openSliderOnSingleTap -> {
                    triggerHapticIfEnabled(view)
                    toggleMiniSlider()
                }
                else -> {
                    triggerHapticIfEnabled(view)
                    val stepMultiplier = if (count >= 2) count else 1
                    adjustVolumeByDelta((if (tapUpperHalf) 1 else -1) * stepMultiplier * prefs.getInt(KEY_VOLUME_STEP, DEFAULT_VOLUME_STEP).coerceAtLeast(1))
                }
            }
        }
        tapResolveRunnable = runnable
        mainHandler.postDelayed(runnable, MULTI_TAP_WINDOW_MS)
    }

    private fun executeGestureAction(actionId: String, view: View) {
        if (actionId == ACTION_ID_NONE) return
        triggerHapticIfEnabled(view)
        when (actionId) {
            ACTION_ID_MUTE -> toggleMuteSelectedStream()
            ACTION_ID_PLAY_PAUSE -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Media Play / Pause")
            ACTION_ID_NEXT_TRACK -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, "Next Track")
            ACTION_ID_PREV_TRACK -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous Track")
            ACTION_ID_MINI_SLIDER -> toggleMiniSlider()
            ACTION_ID_TOGGLE_LOCK -> {
                val nextLocked = !isPositionLocked()
                prefs.edit().putBoolean(KEY_POSITION_LOCKED, nextLocked).apply()
                refreshForegroundNotification()
                Toast.makeText(
                    this,
                    if (nextLocked) "Overlay Position Locked" else "Overlay Position Unlocked",
                    Toast.LENGTH_SHORT
                ).show()
            }
            ACTION_ID_TOGGLE_BRIGHTNESS -> {
                val nextEnabled = !prefs.getBoolean(KEY_BRIGHTNESS_HANDLE_ENABLED, false)
                prefs.edit().putBoolean(KEY_BRIGHTNESS_HANDLE_ENABLED, nextEnabled).apply()
                syncBrightnessOverlay()
                syncMiniSliderCallback?.invoke()
                Toast.makeText(
                    this,
                    if (nextEnabled) "Brightness Handle ON" else "Brightness Handle OFF",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun dispatchMediaKey(keyCode: Int, toastLabel: String) {
        val eventTime = SystemClock.uptimeMillis()
        val down = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val up = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(down)
        audioManager.dispatchMediaKeyEvent(up)
        Toast.makeText(this, toastLabel, Toast.LENGTH_SHORT).show()
    }

    private fun safeSetStreamVolume(stream: Int, index: Int, flags: Int): Boolean {
        return try {
            audioManager.setStreamVolume(stream, index, flags)
            true
        } catch (_: SecurityException) {
            notifyDndRestricted()
            false
        }
    }

    private fun safeAdjustStreamVolume(stream: Int, direction: Int, flags: Int): Boolean {
        return try {
            audioManager.adjustStreamVolume(stream, direction, flags)
            true
        } catch (_: SecurityException) {
            notifyDndRestricted()
            false
        }
    }

    private fun notifyDndRestricted() {
        if (!dndWarningShownThisSession) {
            dndWarningShownThisSession = true
            mainHandler.post {
                Toast.makeText(
                    this,
                    "Do Not Disturb restricts Ringtone mute/volume changes",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun toggleMuteSelectedStream() {
        val stream = getSelectedAudioStream()
        val wasMuted = audioManager.isStreamMute(stream)
        val curVol = audioManager.getStreamVolume(stream)
        if (wasMuted || curVol == 0) {
            safeAdjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
            if (audioManager.getStreamVolume(stream) == 0) {
                val restoreVol = (preMuteVolumes[stream] ?: (audioManager.getStreamMaxVolume(stream) / 3).coerceAtLeast(1))
                    .coerceIn(1, getMaxAllowedVolume(stream))
                safeSetStreamVolume(stream, restoreVol, AudioManager.FLAG_SHOW_UI)
            }
        } else {
            preMuteVolumes[stream] = curVol
            safeAdjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
            if (!audioManager.isStreamMute(stream) && audioManager.getStreamVolume(stream) > 0) {
                safeSetStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
            }
        }
        refreshPercentBadge()
        handleGlyphView?.invalidate()
        syncMiniSliderCallback?.invoke()
        refreshForegroundNotification()
    }

    private fun toggleMiniSlider() {
        if (miniSliderView != null) {
            dismissMiniSlider()
        } else {
            showMiniSlider()
        }
    }

    private fun showMiniSlider() {
        dismissMiniSlider()
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dpToPx(12)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F50F172A"))
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#334155"))
            }
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleTv = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            textSize = 12f
        }
        headerRow.addView(titleTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        var mixerExpanded = false
        val mixerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val btnMixerToggle = Button(this).apply {
            text = "Mixer ▾"
            textSize = 10f
            isAllCaps = false
            setSingleLine(true)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.parseColor("#93C5FD"))
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            background = makeServicePill("#172554", "#3B82F6", 8f)
            setOnClickListener {
                mixerExpanded = !mixerExpanded
                mixerContainer.visibility = if (mixerExpanded) View.VISIBLE else View.GONE
                text = if (mixerExpanded) "Mixer ▴" else "Mixer ▾"
                repositionMiniSliderCallback?.invoke()
                resetMiniSliderTimeout()
            }
        }
        headerRow.addView(btnMixerToggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            marginEnd = dpToPx(6)
        })

        val closeBtn = Button(this).apply {
            text = "✕"
            textSize = 11f
            isAllCaps = false
            setSingleLine(true)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            background = makeServicePill("#1E293B", "#334155", 8f)
            setOnClickListener { dismissMiniSlider() }
        }
        headerRow.addView(closeBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        panel.addView(headerRow)

        val seekBar = SeekBar(this)
        val streamRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(8), 0, dpToPx(4))
        }

        val btnMute = Button(this)
        val showBrightness = prefs.getBoolean(KEY_SHOW_BRIGHTNESS_IN_SLIDER, true)
        val tvBrightLabel = TextView(this)
        val brightSeekBar = SeekBar(this).apply { max = 245 }
        val btnBrightHandleToggle = Button(this)
        var isPrimaryBarScrubbing = false
        var isBrightBarScrubbing = false

        data class MixerRowUi(
            val androidStream: Int,
            val muteBtn: Button,
            val sb: SeekBar,
            val valTv: TextView,
            var isScrubbing: Boolean = false
        )
        val mixerRows = mutableListOf<MixerRowUi>()

        fun syncSliderUi() {
            val s = getSelectedAudioStream()
            val maxVol = audioManager.getStreamMaxVolume(s).coerceAtLeast(1)
            val maxAllowed = getMaxAllowedVolume(s)
            val curVol = audioManager.getStreamVolume(s).coerceIn(0, maxAllowed)
            val muted = curVol == 0 || audioManager.isStreamMute(s)
            seekBar.max = maxAllowed
            if (!isPrimaryBarScrubbing && seekBar.progress != curVol) {
                seekBar.progress = curVol
            }
            val pct = ((curVol.toFloat() / maxVol) * 100f).roundToInt().coerceIn(0, 100)
            titleTv.text = "${getStreamLabel()} $pct% ($curVol/$maxVol)"
            btnMute.text = if (muted) "Unmute" else "Mute"

            mixerRows.forEach { mRow ->
                val mMax = audioManager.getStreamMaxVolume(mRow.androidStream).coerceAtLeast(1)
                val mAllowed = getMaxAllowedVolume(mRow.androidStream)
                val mCur = audioManager.getStreamVolume(mRow.androidStream).coerceIn(0, mAllowed)
                val mMuted = mCur == 0 || audioManager.isStreamMute(mRow.androidStream)
                mRow.sb.max = mAllowed
                if (!mRow.isScrubbing && mRow.sb.progress != mCur) {
                    mRow.sb.progress = mCur
                }
                val mPct = ((mCur.toFloat() / mMax) * 100f).roundToInt().coerceIn(0, 100)
                mRow.valTv.text = if (mMuted) "0%" else "$mPct%"
                mRow.muteBtn.text = if (mMuted) "✕" else "🔊"
                mRow.muteBtn.background = makeServicePill(
                    if (mMuted) "#450A0A" else "#1E293B",
                    if (mMuted) "#EF4444" else "#334155",
                    6f
                )
            }

            if (showBrightness) {
                val curBright = readSystemBrightness().coerceIn(10, 255)
                val brightPct = (((curBright - 10) / 245f) * 100f).roundToInt().coerceIn(4, 100)
                val targetBrightProgress = (curBright - 10).coerceIn(0, 245)
                if (!isBrightBarScrubbing && brightSeekBar.progress != targetBrightProgress) {
                    brightSeekBar.progress = targetBrightProgress
                }
                tvBrightLabel.text = "☀ Brightness $brightPct%"
                val handleOn = prefs.getBoolean(KEY_BRIGHTNESS_HANDLE_ENABLED, false)
                btnBrightHandleToggle.text = if (handleOn) "Handle ON" else "Handle OFF"
                btnBrightHandleToggle.background = makeServicePill(
                    if (handleOn) "#451A03" else "#1E293B",
                    if (handleOn) "#F59E0B" else "#334155",
                    8f
                )
                btnBrightHandleToggle.setTextColor(
                    Color.parseColor(if (handleOn) "#FCD34D" else "#CBD5E1")
                )
            }
        }
        syncMiniSliderCallback = { syncSliderUi() }

        val streams = listOf(
            STREAM_MUSIC to "Med",
            STREAM_RING to "Ring",
            STREAM_ALARM to "Alrm",
            STREAM_VOICE to "Call"
        )
        val streamButtons = mutableListOf<Pair<String, Button>>()
        fun refreshStreamButtonColors() {
            val active = prefs.getString(KEY_AUDIO_STREAM, DEFAULT_STREAM) ?: STREAM_MUSIC
            streamButtons.forEach { (key, btn) ->
                val isSelected = key == active
                btn.background = makeServicePill(
                    fillHex = if (isSelected) "#172554" else "#1E293B",
                    strokeHex = if (isSelected) "#3B82F6" else "#334155",
                    radiusDp = 8f
                )
                btn.setTextColor(Color.parseColor(if (isSelected) "#93C5FD" else "#CBD5E1"))
            }
        }
        streams.forEachIndexed { index, (streamKey, label) ->
            val btn = Button(this).apply {
                text = label
                textSize = 11f
                isAllCaps = false
                setSingleLine(true)
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6))
                setOnClickListener {
                    prefs.edit().putString(KEY_AUDIO_STREAM, streamKey).apply()
                    refreshStreamButtonColors()
                    syncSliderUi()
                    refreshPercentBadge()
                    refreshForegroundNotification()
                    resetMiniSliderTimeout()
                }
            }
            streamButtons.add(streamKey to btn)
            val sLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index < streams.size - 1) marginEnd = dpToPx(4)
            }
            streamRow.addView(btn, sLp)
        }
        refreshStreamButtonColors()
        panel.addView(streamRow)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setVolumeClamped(progress)
                    syncSliderUi()
                    resetMiniSliderTimeout()
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {
                isPrimaryBarScrubbing = true
                resetMiniSliderTimeout()
            }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                isPrimaryBarScrubbing = false
                syncSliderUi()
                resetMiniSliderTimeout()
            }
        })
        panel.addView(seekBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpToPx(4)
            bottomMargin = dpToPx(6)
        })

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnDown = Button(this).apply {
            text = "−"
            textSize = 13f
            isAllCaps = false
            setSingleLine(true)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.WHITE)
            setPadding(dpToPx(4), dpToPx(7), dpToPx(4), dpToPx(7))
            background = makeServicePill("#1E293B", "#334155", 8f)
            setOnClickListener {
                adjustVolumeByStep(-1)
                syncSliderUi()
                resetMiniSliderTimeout()
            }
        }
        btnMute.apply {
            text = "Mute"
            textSize = 11f
            isAllCaps = false
            setSingleLine(true)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.parseColor("#FCA5A5"))
            setPadding(dpToPx(4), dpToPx(7), dpToPx(4), dpToPx(7))
            background = makeServicePill("#450A0A", "#EF4444", 8f)
            setOnClickListener {
                toggleMuteSelectedStream()
                syncSliderUi()
                resetMiniSliderTimeout()
            }
        }
        val btnUp = Button(this).apply {
            text = "+"
            textSize = 13f
            isAllCaps = false
            setSingleLine(true)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.WHITE)
            setPadding(dpToPx(4), dpToPx(7), dpToPx(4), dpToPx(7))
            background = makeServicePill("#1E293B", "#334155", 8f)
            setOnClickListener {
                adjustVolumeByStep(1)
                syncSliderUi()
                resetMiniSliderTimeout()
            }
        }

        val gap = dpToPx(4)
        actionRow.addView(btnDown, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = gap })
        actionRow.addView(btnMute, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f).apply { marginEnd = gap })
        actionRow.addView(btnUp, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(actionRow)

        val mediaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnPrev = Button(this).apply {
            text = "⏮ Prev"
            textSize = 11f
            isAllCaps = false
            setSingleLine(true)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6))
            background = makeServicePill("#1E293B", "#334155", 8f)
            setOnClickListener {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous Track")
                resetMiniSliderTimeout()
            }
        }
        val btnPlayPause = Button(this).apply {
            text = "⏯ Play"
            textSize = 11f
            isAllCaps = false
            setSingleLine(true)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.parseColor("#93C5FD"))
            setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6))
            background = makeServicePill("#172554", "#3B82F6", 8f)
            setOnClickListener {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Media Play / Pause")
                resetMiniSliderTimeout()
            }
        }
        val btnNext = Button(this).apply {
            text = "⏭ Next"
            textSize = 11f
            isAllCaps = false
            setSingleLine(true)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.parseColor("#CBD5E1"))
            setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6))
            background = makeServicePill("#1E293B", "#334155", 8f)
            setOnClickListener {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, "Next Track")
                resetMiniSliderTimeout()
            }
        }
        mediaRow.addView(btnPrev, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = gap })
        mediaRow.addView(btnPlayPause, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f).apply { marginEnd = gap })
        mediaRow.addView(btnNext, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(mediaRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dpToPx(6)
        })

        // Expandable 4-stream multi-stream mixer
        val mixerDivider = View(this).apply { setBackgroundColor(Color.parseColor("#334155")) }
        mixerContainer.addView(mixerDivider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)).apply {
            topMargin = dpToPx(8)
            bottomMargin = dpToPx(6)
        })
        val mixerHeaderTv = TextView(this).apply {
            text = "Multi-Stream Mixer"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
        }
        mixerContainer.addView(mixerHeaderTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dpToPx(4)
        })
        val mixerStreamDefs = listOf(
            AudioManager.STREAM_MUSIC to "Med",
            AudioManager.STREAM_RING to "Ring",
            AudioManager.STREAM_ALARM to "Alrm",
            AudioManager.STREAM_VOICE_CALL to "Call"
        )
        mixerStreamDefs.forEach { (mStream, mLabel) ->
            val mRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dpToPx(2), 0, dpToPx(2))
            }
            val mMuteBtn = Button(this).apply {
                text = "🔊"
                textSize = 10f
                isAllCaps = false
                setSingleLine(true)
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setTextColor(Color.WHITE)
                setPadding(dpToPx(6), dpToPx(3), dpToPx(6), dpToPx(3))
                setOnClickListener {
                    val wasMuted = audioManager.isStreamMute(mStream) || audioManager.getStreamVolume(mStream) == 0
                    if (wasMuted) {
                        val restore = (preMuteVolumes[mStream] ?: (audioManager.getStreamMaxVolume(mStream) / 3).coerceAtLeast(1))
                            .coerceIn(1, getMaxAllowedVolume(mStream))
                        safeSetStreamVolume(mStream, restore, 0)
                    } else {
                        preMuteVolumes[mStream] = audioManager.getStreamVolume(mStream)
                        safeSetStreamVolume(mStream, 0, 0)
                    }
                    syncSliderUi()
                    refreshPercentBadge()
                    handleGlyphView?.invalidate()
                    refreshForegroundNotification()
                    resetMiniSliderTimeout()
                }
            }
            val mNameTv = TextView(this).apply {
                text = mLabel
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#CBD5E1"))
                setSingleLine(true)
            }
            val mSb = SeekBar(this)
            val mValTv = TextView(this).apply {
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#93C5FD"))
                gravity = Gravity.END
                setSingleLine(true)
            }
            val rowUi = MixerRowUi(mStream, mMuteBtn, mSb, mValTv)
            mSb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val clamped = progress.coerceIn(0, getMaxAllowedVolume(mStream))
                        safeSetStreamVolume(mStream, clamped, 0)
                        syncSliderUi()
                        refreshPercentBadge()
                        handleGlyphView?.invalidate()
                        refreshForegroundNotification()
                        resetMiniSliderTimeout()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {
                    rowUi.isScrubbing = true
                    resetMiniSliderTimeout()
                }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    rowUi.isScrubbing = false
                    syncSliderUi()
                    resetMiniSliderTimeout()
                }
            })
            mRow.addView(mMuteBtn, LinearLayout.LayoutParams(dpToPx(26), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dpToPx(4) })
            mRow.addView(mNameTv, LinearLayout.LayoutParams(dpToPx(32), LinearLayout.LayoutParams.WRAP_CONTENT))
            mRow.addView(mSb, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            mRow.addView(mValTv, LinearLayout.LayoutParams(dpToPx(30), LinearLayout.LayoutParams.WRAP_CONTENT))
            mixerContainer.addView(mRow)
            mixerRows.add(rowUi)
        }
        panel.addView(mixerContainer)

        if (showBrightness) {
            val divider = View(this).apply { setBackgroundColor(Color.parseColor("#334155")) }
            panel.addView(divider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)).apply {
                topMargin = dpToPx(8)
                bottomMargin = dpToPx(6)
            })

            val brightHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            tvBrightLabel.apply {
                setTextColor(Color.parseColor("#FCD34D"))
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setSingleLine(true)
            }
            btnBrightHandleToggle.apply {
                textSize = 10f
                isAllCaps = false
                setSingleLine(true)
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
                setOnClickListener {
                    val nextOn = !prefs.getBoolean(KEY_BRIGHTNESS_HANDLE_ENABLED, false)
                    prefs.edit().putBoolean(KEY_BRIGHTNESS_HANDLE_ENABLED, nextOn).apply()
                    syncBrightnessOverlay()
                    syncSliderUi()
                    resetMiniSliderTimeout()
                }
            }
            brightHeader.addView(tvBrightLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            brightHeader.addView(btnBrightHandleToggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            panel.addView(brightHeader)

            brightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        setSystemBrightness(10 + progress)
                        val bPct = ((progress / 245f) * 100f).roundToInt().coerceIn(4, 100)
                        tvBrightLabel.text = "☀ Brightness $bPct%"
                        resetMiniSliderTimeout()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {
                    isBrightBarScrubbing = true
                    resetMiniSliderTimeout()
                }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    isBrightBarScrubbing = false
                    syncSliderUi()
                    resetMiniSliderTimeout()
                }
            })
            panel.addView(brightSeekBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dpToPx(4)
            })
        }

        syncSliderUi()
        val scrollContainer = object : ScrollView(this@FloatingVolumeService) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val (_, sh) = getScreenDimensionsPx()
                val avoidSafeNow = prefs.getBoolean(KEY_AVOID_SAFE_ZONES, DEFAULT_AVOID_SAFE_ZONES)
                val insetT = if (avoidSafeNow) safeInsetTop else dpToPx(24)
                val insetB = if (avoidSafeNow) safeInsetBottom else dpToPx(16)
                val availableH = (sh - insetT - insetB - dpToPx(16)).coerceAtLeast(dpToPx(120))
                val maxH = min((sh * 0.72f).toInt().coerceAtLeast(dpToPx(120)), availableH)
                val cappedSpec = MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, cappedSpec)
            }
        }.apply {
            isVerticalScrollBarEnabled = false
            addView(panel)
        }

        val (_, rawScreenH) = getScreenDimensionsPx()
        val (rawScreenW, _) = getScreenDimensionsPx()
        val initAvoidSafe = prefs.getBoolean(KEY_AVOID_SAFE_ZONES, DEFAULT_AVOID_SAFE_ZONES)
        val initLeft = if (initAvoidSafe) safeInsetLeft else 0
        val initRight = if (initAvoidSafe) safeInsetRight else 0
        val sliderWidth = min(
            dpToPx(264),
            (rawScreenW - initLeft - initRight - dpToPx(16)).coerceAtLeast(dpToPx(170))
        )
        fun computeSliderWindowCoords(): Pair<Int, Int> {
            val (screenW, screenH) = getScreenDimensionsPx()
            val avoidSafe = prefs.getBoolean(KEY_AVOID_SAFE_ZONES, DEFAULT_AVOID_SAFE_ZONES)
            val sLeft = if (avoidSafe) safeInsetLeft else 0
            val sTop = if (avoidSafe) safeInsetTop else 0
            val sRight = if (avoidSafe) safeInsetRight else 0
            val sBottom = if (avoidSafe) safeInsetBottom else 0
            scrollContainer.measure(
                View.MeasureSpec.makeMeasureSpec(sliderWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
            )
            val measuredH = scrollContainer.measuredHeight.takeIf { it > 0 } ?: dpToPx(240)
            val rootW = currentRootWidthPx()
            val rootH = currentHandleHeightPx()
            val minX = (sLeft + dpToPx(8)).coerceAtMost((screenW - sliderWidth).coerceAtLeast(0))
            val maxX = (screenW - sRight - sliderWidth - dpToPx(8)).coerceAtLeast(minX)
            val rightCandidateX = (layoutParams.x + rootW + dpToPx(10)).coerceIn(minX, maxX)
            val leftCandidateX = (layoutParams.x - sliderWidth - dpToPx(10)).coerceIn(minX, maxX)
            val preferredX = if (isOverlayOnRightHalf(layoutParams.x)) leftCandidateX else rightCandidateX
            val altX = if (isOverlayOnRightHalf(layoutParams.x)) rightCandidateX else leftCandidateX

            val bannerReserveY = if (deviceBannerView?.isAttachedToWindow == true) {
                max(dpToPx(78), sTop + dpToPx(64))
            } else {
                0
            }
            val minY = max(max(sTop, dpToPx(16)), bannerReserveY)
            val maxY = (screenH - sBottom - measuredH - dpToPx(8)).coerceAtLeast(minY)
            val midY = (layoutParams.y - dpToPx(16)).coerceIn(minY, maxY)

            fun rectOverlap(
                ax: Int, ay: Int, aw: Int, ah: Int,
                bx: Int, by: Int, bw: Int, bh: Int
            ): Long {
                val ox = (min(ax + aw, bx + bw) - max(ax, bx)).coerceAtLeast(0)
                val oy = (min(ay + ah, by + bh) - max(ay, by)).coerceAtLeast(0)
                return ox.toLong() * oy.toLong()
            }

            fun scoreCandidate(cx: Int, cy: Int): Long {
                val volOv = rectOverlap(cx, cy, sliderWidth, measuredH, layoutParams.x, layoutParams.y, rootW, rootH)
                val bView = brightnessFloatingView
                val brightOv = if (bView != null && bView.isAttachedToWindow &&
                    prefs.getBoolean(KEY_BRIGHTNESS_HANDLE_ENABLED, false)
                ) {
                    val bw = brightnessHandleW.takeIf { it > 0 } ?: dpToPx(DEFAULT_BRIGHTNESS_WIDTH_DP)
                    val bh = brightnessHandleH.takeIf { it > 0 } ?: dpToPx(DEFAULT_BRIGHTNESS_HEIGHT_DP)
                    rectOverlap(cx, cy, sliderWidth, measuredH, brightnessLayoutParams.x, brightnessLayoutParams.y, bw, bh)
                } else 0L
                return volOv * 3L + brightOv * 2L + abs(cy - midY).toLong()
            }

            val candidates = listOf(
                preferredX to midY,
                preferredX to minY,
                preferredX to maxY,
                altX to midY,
                altX to minY
            )
            return candidates.minByOrNull { (cx, cy) -> scoreCandidate(cx, cy) } ?: (preferredX to midY)
        }

        val (targetX, targetY) = computeSliderWindowCoords()
        val sliderLp = WindowManager.LayoutParams(
            sliderWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = targetX
            y = targetY
        }

        repositionMiniSliderCallback = {
            if (scrollContainer.isAttachedToWindow) {
                val (nx, ny) = computeSliderWindowCoords()
                sliderLp.x = nx
                sliderLp.y = ny
                safeUpdateViewLayout(scrollContainer, sliderLp)
            }
        }

        if (prefs.getBoolean(KEY_SMOOTH_FADE, true)) {
            scrollContainer.alpha = 0f
            scrollContainer.animate().alpha(1f).setDuration(160).start()
        }

        miniSliderView = scrollContainer
        if (safeAddView(scrollContainer, sliderLp)) {
            resetMiniSliderTimeout()
        } else {
            miniSliderView = null
            syncMiniSliderCallback = null
            repositionMiniSliderCallback = null
        }
    }

    private fun resetMiniSliderTimeout() {
        mainHandler.removeCallbacks(dismissSliderRunnable)
        mainHandler.postDelayed(dismissSliderRunnable, 4500L)
    }

    private fun dismissMiniSlider() {
        mainHandler.removeCallbacks(dismissSliderRunnable)
        syncMiniSliderCallback = null
        repositionMiniSliderCallback = null
        safeRemoveView(miniSliderView)
        miniSliderView = null
    }

    private fun triggerHapticIfEnabled(view: View) {
        val strength = prefs.getString(KEY_HAPTIC_STRENGTH, DEFAULT_HAPTIC_STRENGTH) ?: HAPTIC_MEDIUM
        if (strength == HAPTIC_OFF || !prefs.getBoolean(KEY_HAPTIC_TAP, true)) return

        val durationMs = when (strength) {
            HAPTIC_LIGHT -> 14L
            HAPTIC_STRONG -> 34L
            else -> 22L
        }
        val amplitude = when (strength) {
            HAPTIC_LIGHT -> 85
            HAPTIC_STRONG -> 255
            else -> 165
        }

        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                val safeAmp = if (vibrator.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
                val effect = VibrationEffect.createOneShot(durationMs, safeAmp)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attrs = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_TOUCH)
                        .build()
                    vibrator.vibrate(effect, attrs)
                } else {
                    @Suppress("DEPRECATION")
                    val audioAttrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(effect, audioAttrs)
                }
                return
            }
        } catch (_: Exception) {
            // Fall back to View haptic feedback
        }
        @Suppress("DEPRECATION")
        view.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }

    private fun getStreamLabel(): String {
        return when (prefs.getString(KEY_AUDIO_STREAM, DEFAULT_STREAM)) {
            STREAM_RING -> "RING"
            STREAM_ALARM -> "ALARM"
            STREAM_VOICE -> "CALL"
            else -> "MEDIA"
        }
    }

    private fun getSelectedAudioStream(): Int {
        return when (prefs.getString(KEY_AUDIO_STREAM, DEFAULT_STREAM)) {
            STREAM_RING -> AudioManager.STREAM_RING
            STREAM_ALARM -> AudioManager.STREAM_ALARM
            STREAM_VOICE -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }
    }

    private fun getMaxAllowedVolume(stream: Int): Int {
        val maxVol = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
        val limitPct = prefs.getInt(KEY_MAX_VOLUME_LIMIT_PCT, DEFAULT_MAX_VOLUME_LIMIT_PCT).coerceIn(30, 100)
        return ((maxVol * limitPct) / 100f).roundToInt().coerceIn(1, maxVol)
    }

    private fun adjustVolumeByStep(stepSign: Int) {
        val stepSize = prefs.getInt(KEY_VOLUME_STEP, DEFAULT_VOLUME_STEP).coerceAtLeast(1)
        adjustVolumeByDelta(stepSign * stepSize)
    }

    private fun adjustVolumeByDelta(delta: Int) {
        if (delta == 0) return
        val stream = getSelectedAudioStream()
        val current = audioManager.getStreamVolume(stream)
        setVolumeClamped(current + delta)
    }

    private fun enforceActiveStreamMaxCap() {
        val stream = getSelectedAudioStream()
        val maxAllowed = getMaxAllowedVolume(stream)
        val cur = audioManager.getStreamVolume(stream)
        if (cur > maxAllowed) {
            safeSetStreamVolume(stream, maxAllowed, 0)
        }
    }

    private fun setVolumeClamped(target: Int) {
        val stream = getSelectedAudioStream()
        val maxAllowed = getMaxAllowedVolume(stream)
        val clamped = target.coerceIn(0, maxAllowed)
        safeSetStreamVolume(stream, clamped, AudioManager.FLAG_SHOW_UI)
        refreshPercentBadge()
        syncMiniSliderCallback?.invoke()
        refreshForegroundNotification()
    }

    private fun wakeFromIdleAndScheduleDim() {
        mainHandler.removeCallbacks(autoDimRunnable)
        if (isIdleDimmed) {
            isIdleDimmed = false
            applyAppearanceFromPrefs()
            applyBrightnessAppearance()
        }
        scheduleAutoDim()
    }

    private fun scheduleAutoDim() {
        mainHandler.removeCallbacks(autoDimRunnable)
        val dimSec = prefs.getInt(KEY_AUTO_DIM_SEC, DEFAULT_AUTO_DIM_SEC)
        if (dimSec > 0) {
            mainHandler.postDelayed(autoDimRunnable, dimSec * 1000L)
        } else if (isIdleDimmed) {
            isIdleDimmed = false
            applyAppearanceFromPrefs()
            applyBrightnessAppearance()
        }
    }

    private fun refreshPercentBadge(repositionIfNeeded: Boolean = true) {
        val inner = innerBadgeView ?: return
        val side = sideBadgeView ?: return
        val glyph = handleGlyphView
        val displayMode = prefs.getString(KEY_DISPLAY_MODE, DEFAULT_DISPLAY_MODE) ?: DISPLAY_MINIMAL
        val basePct = prefs.getInt(KEY_TRANSPARENCY_PCT, DEFAULT_TRANSPARENCY_PCT).coerceIn(0, 100)
        val dimPct = prefs.getInt(KEY_DIM_OPACITY_PCT, DEFAULT_DIM_OPACITY_PCT).coerceIn(0, 50)
        val effectivePct = if (isIdleDimmed) min(basePct, dimPct) else basePct

        val widthDp = prefs.getInt(KEY_WIDTH_DP, DEFAULT_WIDTH_DP).coerceAtLeast(1)
        val heightDp = prefs.getInt(KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP).coerceAtLeast(1)
        val fitsInside = widthDp >= 44 && heightDp >= 32
        val hideGlyphForBadge = displayMode == DISPLAY_PERCENT_BADGE && fitsInside
        if (effectivePct == 0 || widthDp < 15 || heightDp < 24 || hideGlyphForBadge) {
            glyph?.visibility = View.GONE
        } else {
            glyph?.visibility = View.VISIBLE
            glyph?.invalidate()
        }

        if (displayMode != DISPLAY_PERCENT_BADGE || effectivePct == 0) {
            inner.visibility = View.GONE
            side.visibility = View.GONE
            if (repositionIfNeeded) {
                floatingView?.let { root ->
                    if (root.isAttachedToWindow) {
                        val (cx, cy) = clampCoordinates(layoutParams.x, layoutParams.y)
                        if (cx != layoutParams.x || cy != layoutParams.y) {
                            layoutParams.x = cx
                            layoutParams.y = cy
                            windowManager.updateViewLayout(root, layoutParams)
                        }
                    }
                }
            }
            return
        }

        val stream = getSelectedAudioStream()
        val maxVol = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
        val curVol = audioManager.getStreamVolume(stream).coerceIn(0, maxVol)
        val muted = curVol == 0 || audioManager.isStreamMute(stream)
        val volPct = ((curVol.toFloat() / maxVol) * 100f).roundToInt().coerceIn(0, 100)
        val label = if (muted) "MUTE" else "$volPct%"

        val badgeAlpha = (effectivePct / 100f).coerceIn(0.3f, 1f)

        if (fitsInside) {
            inner.text = label
            inner.alpha = badgeAlpha
            inner.visibility = View.VISIBLE
            side.visibility = View.GONE
        } else {
            inner.visibility = View.GONE
            side.text = label
            side.alpha = badgeAlpha
            side.visibility = View.VISIBLE
            side.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            if (repositionIfNeeded) {
                floatingView?.let { root ->
                    if (root.isAttachedToWindow && !isDragging) {
                        val onRight = isOverlayOnRightHalf(layoutParams.x)
                        val targetX = if (shouldDockToEdge() && onRight) {
                            getDockedEdgeX(layoutParams.x, currentRootWidthPx())
                        } else {
                            clampCoordinates(layoutParams.x, layoutParams.y).first
                        }
                        reorderSideBadgeForScreenEdge()
                        if (targetX != layoutParams.x) {
                            layoutParams.x = targetX
                            windowManager.updateViewLayout(root, layoutParams)
                        }
                    }
                }
            }
        }
    }

    private fun applyAppearanceFromPrefs(addIfMissing: Boolean = true) {
        val root = floatingView ?: return
        val handle = handleView ?: return

        val widthDp = prefs.getInt(KEY_WIDTH_DP, DEFAULT_WIDTH_DP).coerceAtLeast(1)
        val heightDp = prefs.getInt(KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP).coerceAtLeast(1)
        val basePct =
            prefs.getInt(KEY_TRANSPARENCY_PCT, DEFAULT_TRANSPARENCY_PCT).coerceIn(0, 100)
        val dimPct =
            prefs.getInt(KEY_DIM_OPACITY_PCT, DEFAULT_DIM_OPACITY_PCT).coerceIn(0, 50)
        val effectivePct = if (isIdleDimmed) min(basePct, dimPct) else basePct
        val shape = prefs.getString(KEY_SHAPE, SHAPE_CIRCLE)
        val baseColor = prefs.getInt(KEY_OVERLAY_COLOR, DEFAULT_COLOR)
        val smoothFade = prefs.getBoolean(KEY_SMOOTH_FADE, true)

        val widthPx = dpToPx(widthDp)
        val heightPx = dpToPx(heightDp)
        handleWidthPx = widthPx
        handleHeightPx = heightPx

        refreshPercentBadge(repositionIfNeeded = false)

        val alphaInt = ((effectivePct / 100f) * 255).toInt().coerceIn(0, 255)
        val strokeAlpha = if (alphaInt == 0) 0 else min(255, alphaInt + 45)

        val (clampedX, clampedY) = clampCoordinates(layoutParams.x, layoutParams.y)
        val onRightSide = isOverlayOnRightHalf(clampedX)
        val dockedX = if (shouldDockToEdge() && !isDragging) {
            getDockedEdgeX(clampedX, currentRootWidthPx())
        } else {
            clampedX
        }
        layoutParams.x = dockedX
        layoutParams.y = clampedY

        if (shape == SHAPE_EDGE_TAB) {
            overlayDrawable.shape = GradientDrawable.RECTANGLE
            val r = (min(widthPx, heightPx) * 0.85f).coerceAtLeast(dpToPx(8).toFloat())
            overlayDrawable.cornerRadii = if (onRightSide) {
                floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
            } else {
                floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            }
        } else if (shape == SHAPE_LINE) {
            overlayDrawable.shape = GradientDrawable.RECTANGLE
            overlayDrawable.cornerRadius = min(widthPx, heightPx) / 2f
        } else {
            overlayDrawable.shape = GradientDrawable.OVAL
        }

        overlayDrawable.setColor(
            Color.argb(alphaInt, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        )
        overlayDrawable.setStroke(dpToPx(2), Color.argb(strokeAlpha, 255, 255, 255))

        handle.layoutParams = (handle.layoutParams as? LinearLayout.LayoutParams
            ?: LinearLayout.LayoutParams(widthPx, heightPx)).apply {
            width = widthPx
            height = heightPx
        }
        handle.background = overlayDrawable

        reorderSideBadgeForScreenEdge()
        handleGlyphView?.invalidate()

        if (addIfMissing && root.isAttachedToWindow) {
            safeUpdateViewLayout(root, layoutParams)
            if (smoothFade) {
                root.animate().alpha(1f).setDuration(180).start()
            }
        }
    }

    private fun syncOledShiftState() {
        mainHandler.removeCallbacks(oledShiftRunnable)
        if (prefs.getBoolean(KEY_OLED_PIXEL_SHIFT, false)) {
            mainHandler.postDelayed(oledShiftRunnable, OLED_SHIFT_INTERVAL_MS)
        } else {
            resetOledShiftToZero()
        }
    }

    private fun resetOledShiftToZero() {
        if (oledOffsetPxX == 0 && oledOffsetPxY == 0) return
        applyOledShiftOffset(0, 0)
    }

    private fun applyOledShiftOffset(nextOffX: Int, nextOffY: Int) {
        val deltaX = nextOffX - oledOffsetPxX
        val deltaY = nextOffY - oledOffsetPxY
        oledOffsetPxX = nextOffX
        oledOffsetPxY = nextOffY

        floatingView?.let { view ->
            if (view.isAttachedToWindow) {
                val (cx, cy) = clampCoordinates(layoutParams.x + deltaX, layoutParams.y + deltaY)
                layoutParams.x = cx
                layoutParams.y = cy
                windowManager.updateViewLayout(view, layoutParams)
            }
        }
        brightnessFloatingView?.let { bView ->
            if (bView.isAttachedToWindow) {
                val (bx, by) = clampBrightnessCoordinates(brightnessLayoutParams.x + deltaX, brightnessLayoutParams.y + deltaY)
                brightnessLayoutParams.x = bx
                brightnessLayoutParams.y = by
                windowManager.updateViewLayout(bView, brightnessLayoutParams)
            }
        }
    }

    private fun syncBrightnessOverlay() {
        val enabled = prefs.getBoolean(KEY_BRIGHTNESS_HANDLE_ENABLED, false)
        if (!enabled) {
            removeBrightnessOverlay()
            return
        }
        if (brightnessFloatingView == null) {
            createBrightnessOverlay()
        } else {
            applyBrightnessAppearance()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createBrightnessOverlay() {
        removeBrightnessOverlay()
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val isLandscape = isCurrentlyLandscape()
        val (screenW, screenH) = getScreenDimensionsPx()
        val rawX = if (isLandscape) {
            prefs.getInt(KEY_BRIGHTNESS_POS_X_LANDSCAPE, (screenW * 0.85f).toInt())
        } else {
            prefs.getInt(KEY_BRIGHTNESS_POS_X_PORTRAIT, (screenW * 0.10f).toInt())
        }
        val rawY = if (isLandscape) {
            prefs.getInt(KEY_BRIGHTNESS_POS_Y_LANDSCAPE, (screenH * 0.30f).toInt())
        } else {
            prefs.getInt(KEY_BRIGHTNESS_POS_Y_PORTRAIT, (screenH * 0.56f).toInt())
        }

        brightnessLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rawX
            y = rawY
        }

        val sunTv = TextView(this).apply {
            text = "☀"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setSingleLine(true)
            includeFontPadding = false
        }

        val bView = FrameLayout(this).apply {
            isClickable = true
            isFocusable = false
            background = brightnessDrawable
            addView(
                sunTv,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
            visibility = if (isFullscreenHidden) View.GONE else View.VISIBLE
        }

        val scrubStepPx = dpToPx(16).toFloat()
        bView.setOnTouchListener { v, event ->
            val targetH = v.height.takeIf { it > 0 } ?: brightnessHandleH.coerceAtLeast(dpToPx(64))
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resetOledShiftToZero()
                    isBrightnessDragging = false
                    isBrightnessScrubbing = false
                    brightnessScrubAccumY = 0f
                    brightnessInitialX = brightnessLayoutParams.x
                    brightnessInitialY = brightnessLayoutParams.y
                    brightnessInitialTouchX = event.rawX
                    brightnessInitialTouchY = event.rawY
                    if (!isPositionLocked()) {
                        mainHandler.postDelayed(brightnessLongPressRunnable, LONG_PRESS_TIMEOUT_MS)
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - brightnessInitialTouchX
                    val dy = event.rawY - brightnessInitialTouchY
                    if (!isBrightnessDragging) {
                        if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            mainHandler.removeCallbacks(brightnessLongPressRunnable)
                            if (abs(dy) > abs(dx) * 1.15f) {
                                if (!isBrightnessScrubbing) {
                                    isBrightnessScrubbing = true
                                    brightnessScrubAccumY = 0f
                                }
                                val deltaY = dy - brightnessScrubAccumY
                                if (abs(deltaY) >= scrubStepPx) {
                                    val steps = (deltaY / scrubStepPx).toInt()
                                    brightnessScrubAccumY += steps * scrubStepPx
                                    triggerHapticIfEnabled(v)
                                    adjustSystemBrightnessDelta(-steps * 14)
                                }
                            }
                        }
                    } else if (!isPositionLocked()) {
                        val (cx, cy) = clampBrightnessCoordinates(
                            brightnessInitialX + dx.toInt(),
                            brightnessInitialY + dy.toInt()
                        )
                        brightnessLayoutParams.x = cx
                        brightnessLayoutParams.y = cy
                        windowManager.updateViewLayout(v, brightnessLayoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(brightnessLongPressRunnable)
                    val dx = event.rawX - brightnessInitialTouchX
                    val dy = event.rawY - brightnessInitialTouchY
                    if (isBrightnessScrubbing) {
                        isBrightnessScrubbing = false
                    } else if (isBrightnessDragging) {
                        isBrightnessDragging = false
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                        if (prefs.getBoolean(KEY_SNAP_TO_EDGE, false)) {
                            brightnessLayoutParams.x = getDockedEdgeX(brightnessLayoutParams.x, brightnessHandleW)
                        }
                        val resolved = resolveBrightnessNonOverlap(
                            layoutParams.x,
                            layoutParams.y,
                            brightnessLayoutParams.x,
                            brightnessLayoutParams.y
                        )
                        brightnessLayoutParams.x = resolved.first
                        brightnessLayoutParams.y = resolved.second
                        windowManager.updateViewLayout(v, brightnessLayoutParams)
                        saveBrightnessPosition(brightnessLayoutParams.x, brightnessLayoutParams.y)
                    } else if (hypot(dx.toDouble(), dy.toDouble()) <= touchSlop) {
                        triggerHapticIfEnabled(v)
                        val isUpper = event.y < targetH / 2f
                        adjustSystemBrightnessDelta(if (isUpper) 20 else -20)
                        v.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(brightnessLongPressRunnable)
                    isBrightnessScrubbing = false
                    if (isBrightnessDragging) {
                        isBrightnessDragging = false
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    }
                    true
                }
                else -> false
            }
        }

        brightnessFloatingView = bView
        applyBrightnessAppearance(addIfMissing = false)
        val resolvedInit = resolveBrightnessNonOverlap(
            layoutParams.x,
            layoutParams.y,
            brightnessLayoutParams.x,
            brightnessLayoutParams.y
        )
        brightnessLayoutParams.x = resolvedInit.first
        brightnessLayoutParams.y = resolvedInit.second
        if (!safeAddView(bView, brightnessLayoutParams)) {
            brightnessFloatingView = null
        }
    }

    private fun removeBrightnessOverlay() {
        mainHandler.removeCallbacks(brightnessLongPressRunnable)
        safeRemoveView(brightnessFloatingView)
        brightnessFloatingView = null
    }

    private fun applyBrightnessAppearance(addIfMissing: Boolean = true) {
        val bView = brightnessFloatingView ?: return
        val wDp = prefs.getInt(KEY_BRIGHTNESS_WIDTH_DP, DEFAULT_BRIGHTNESS_WIDTH_DP).coerceIn(16, 80)
        val hDp = prefs.getInt(KEY_BRIGHTNESS_HEIGHT_DP, DEFAULT_BRIGHTNESS_HEIGHT_DP).coerceIn(48, 160)
        val basePct = prefs.getInt(KEY_TRANSPARENCY_PCT, DEFAULT_TRANSPARENCY_PCT).coerceIn(20, 100)
        val dimPct = prefs.getInt(KEY_DIM_OPACITY_PCT, DEFAULT_DIM_OPACITY_PCT).coerceIn(0, 50)
        val effectivePct = if (isIdleDimmed) min(basePct, dimPct) else basePct
        val baseColor = prefs.getInt(KEY_BRIGHTNESS_COLOR, DEFAULT_BRIGHTNESS_COLOR)

        brightnessHandleW = dpToPx(wDp)
        brightnessHandleH = dpToPx(hDp)
        val alphaInt = ((effectivePct / 100f) * 255).toInt().coerceIn(25, 255)

        brightnessDrawable.shape = GradientDrawable.RECTANGLE
        brightnessDrawable.cornerRadius = min(brightnessHandleW, brightnessHandleH) / 2f
        brightnessDrawable.setColor(
            Color.argb(alphaInt, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        )
        brightnessDrawable.setStroke(dpToPx(2), Color.argb(if (isIdleDimmed) 120 else 200, 255, 255, 255))

        brightnessLayoutParams.width = brightnessHandleW
        brightnessLayoutParams.height = brightnessHandleH
        var targetBX = brightnessLayoutParams.x
        if (prefs.getBoolean(KEY_SNAP_TO_EDGE, false) && !isBrightnessDragging) {
            targetBX = getDockedEdgeX(targetBX, brightnessHandleW)
        }
        val resolved = resolveBrightnessNonOverlap(
            layoutParams.x,
            layoutParams.y,
            targetBX,
            brightnessLayoutParams.y
        )
        brightnessLayoutParams.x = resolved.first
        brightnessLayoutParams.y = resolved.second

        safeUpdateViewLayout(bView, brightnessLayoutParams)
    }

    private fun clampBrightnessCoordinates(x: Int, y: Int): Pair<Int, Int> {
        val (screenW, screenH) = getScreenDimensionsPx()
        val bw = brightnessHandleW.takeIf { it > 0 } ?: dpToPx(DEFAULT_BRIGHTNESS_WIDTH_DP)
        val bh = brightnessHandleH.takeIf { it > 0 } ?: dpToPx(DEFAULT_BRIGHTNESS_HEIGHT_DP)
        val maxX = (screenW - bw).coerceAtLeast(0)
        val maxY = (screenH - bh).coerceAtLeast(0)
        if (!prefs.getBoolean(KEY_AVOID_SAFE_ZONES, DEFAULT_AVOID_SAFE_ZONES)) {
            return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
        }
        val minX = safeInsetLeft.coerceIn(0, maxX)
        val safeMaxX = (screenW - safeInsetRight - bw).coerceIn(minX, maxX)
        val minY = safeInsetTop.coerceIn(0, maxY)
        val safeMaxY = (screenH - safeInsetBottom - bh).coerceIn(minY, maxY)
        return x.coerceIn(minX, safeMaxX) to y.coerceIn(minY, safeMaxY)
    }

    private fun saveBrightnessPosition(x: Int, y: Int) {
        val editor = prefs.edit()
        if (isCurrentlyLandscape()) {
            editor.putInt(KEY_BRIGHTNESS_POS_X_LANDSCAPE, x)
            editor.putInt(KEY_BRIGHTNESS_POS_Y_LANDSCAPE, y)
        } else {
            editor.putInt(KEY_BRIGHTNESS_POS_X_PORTRAIT, x)
            editor.putInt(KEY_BRIGHTNESS_POS_Y_PORTRAIT, y)
        }
        editor.apply()
    }

    private fun readSystemBrightness(): Int {
        return try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 170)
        } catch (_: Exception) {
            170
        }
    }

    private fun adjustSystemBrightnessDelta(delta: Int) {
        val current = readSystemBrightness()
        setSystemBrightness(current + delta)
    }

    private fun setSystemBrightness(target: Int) {
        val clamped = target.coerceIn(10, 255)
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(
                this,
                "Allow Modify System Settings to control screen brightness",
                Toast.LENGTH_SHORT
            ).show()
            if (!writeSettingsPromptedThisSession) {
                writeSettingsPromptedThisSession = true
                try {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:$packageName")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                } catch (_: Exception) {
                    // Ignored
                }
            }
            return
        }
        try {
            Settings.System.putInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, clamped)
            syncMiniSliderCallback?.invoke()
        } catch (_: Exception) {
            // Permission revoked mid-session
        }
    }

    private fun parseMinutesOfDay(hhMm: String, fallback: Int): Int {
        val parts = hhMm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return fallback
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return fallback
        return (h.coerceIn(0, 23) * 60) + m.coerceIn(0, 59)
    }

    private fun isUsageAccessGranted(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    private fun queryForegroundAppPackage(): String? {
        if (!isUsageAccessGranted()) return null
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val begin = end - 600_000L
            val events = usm.queryEvents(begin, end)
            val event = UsageEvents.Event()
            var latestPkg: String? = null
            var latestTs = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val isForegroundEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                        event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                } else {
                    @Suppress("DEPRECATION")
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                }
                if (isForegroundEvent && event.timeStamp >= latestTs && !event.packageName.isNullOrEmpty()) {
                    latestTs = event.timeStamp
                    latestPkg = event.packageName
                }
            }
            if (latestPkg != null) {
                latestPkg
            } else {
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end)
                    ?.maxByOrNull { it.lastTimeUsed }
                    ?.packageName
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isQuietHoursCurrentlyActive(): Boolean {
        if (!prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, false)) return false
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMin = parseMinutesOfDay(prefs.getString(KEY_QUIET_START, DEFAULT_QUIET_START) ?: DEFAULT_QUIET_START, 22 * 60 + 30)
        val endMin = parseMinutesOfDay(prefs.getString(KEY_QUIET_END, DEFAULT_QUIET_END) ?: DEFAULT_QUIET_END, 7 * 60)
        return if (startMin <= endMin) {
            nowMin in startMin until endMin
        } else {
            nowMin >= startMin || nowMin < endMin
        }
    }

    private fun getMatchedPerAppRuleObject(fgPkg: String): JSONObject? {
        if (fgPkg.isEmpty() || !prefs.getBoolean(KEY_PER_APP_RULES_ENABLED, false)) return null
        val rulesRaw = prefs.getString(KEY_PER_APP_RULES_JSON, null) ?: return null
        return try {
            val arr = JSONArray(rulesRaw)
            var found: JSONObject? = null
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optBoolean("enabled", true) &&
                    obj.optString("packageName").equals(fgPkg, ignoreCase = true)
                ) {
                    found = obj
                    break
                }
            }
            found
        } catch (_: Exception) {
            null
        }
    }

    private fun getMatchedPerAppRulePresetId(fgPkg: String): String? {
        return getMatchedPerAppRuleObject(fgPkg)?.optString("presetId")
    }

    private fun restorePreAppVolumeIfNeeded() {
        val savedStream = preAppSavedStream ?: return
        val savedVol = preAppSavedVolume
        val shouldRestore = preAppRestoreOnExit
        preAppSavedStream = null
        preAppSavedVolume = null
        preAppRestoreOnExit = false
        if (shouldRestore && savedVol != null) {
            val maxAllowed = getMaxAllowedVolume(savedStream)
            safeSetStreamVolume(savedStream, savedVol.coerceIn(0, maxAllowed), 0)
            refreshPercentBadge()
            handleGlyphView?.invalidate()
            syncMiniSliderCallback?.invoke()
            refreshForegroundNotification()
        }
    }

    private fun applyTargetVolumeForRule(ruleObj: JSONObject) {
        val tVol = ruleObj.optInt("targetVolumePct", -1)
        val rExit = ruleObj.optBoolean("restoreVolumeOnExit", true)
        if (tVol in 0..100) {
            val s = getSelectedAudioStream()
            if (preAppSavedStream != null && preAppSavedStream != s) {
                restorePreAppVolumeIfNeeded()
            }
            if (preAppSavedStream == null) {
                preAppSavedStream = s
                preAppSavedVolume = audioManager.getStreamVolume(s)
                preAppRestoreOnExit = rExit
            } else {
                preAppRestoreOnExit = preAppRestoreOnExit || rExit
            }
            val maxV = audioManager.getStreamMaxVolume(s).coerceAtLeast(1)
            val maxAllowed = getMaxAllowedVolume(s)
            val targetLevel = ((tVol / 100f) * maxV).roundToInt().coerceIn(0, maxAllowed)
            safeSetStreamVolume(s, targetLevel, 0)
            refreshPercentBadge()
            handleGlyphView?.invalidate()
            syncMiniSliderCallback?.invoke()
            refreshForegroundNotification()
        } else {
            restorePreAppVolumeIfNeeded()
        }
    }

    private fun evaluateQuietHoursAndAppRules() {
        val inQuietHours = isQuietHoursCurrentlyActive()

        if (inQuietHours) {
            val targetQuietId = prefs.getString(KEY_QUIET_PRESET_ID, DEFAULT_QUIET_PRESET_ID) ?: DEFAULT_QUIET_PRESET_ID
            if (!lastQuietActive) {
                lastQuietActive = true
                restorePreAppVolumeIfNeeded()
                lastForegroundPackage = ""
                prefs.edit()
                    .remove(KEY_ACTIVE_APP_RULE_LABEL)
                    .putBoolean(KEY_MANUAL_OVERRIDE, false)
                    .apply()
                val prevId = prefs.getString("active_preset_id", "") ?: ""
                if (prevId.isNotEmpty() && prevId != targetQuietId) {
                    prefs.edit().putString("pre_quiet_preset_id", prevId).apply()
                }
                val quietObj = findPresetById(targetQuietId)
                if (quietObj != null && prefs.getString("active_preset_id", "") != quietObj.optString("id")) {
                    applyPresetFromService(quietObj, isManualTap = false)
                }
            }
            return
        } else if (lastQuietActive) {
            lastQuietActive = false
            prefs.edit().putBoolean(KEY_MANUAL_OVERRIDE, false).apply()
            val restoreId = prefs.getString("pre_quiet_preset_id", "") ?: ""
            prefs.edit().remove("pre_quiet_preset_id").apply()
            if (prefs.getBoolean(KEY_PER_APP_RULES_ENABLED, false)) {
                val fgPkg = queryForegroundAppPackage() ?: ""
                val matchedOnExitQuiet = getMatchedPerAppRuleObject(fgPkg)
                val appPresetId = matchedOnExitQuiet?.optString("presetId")
                if (!appPresetId.isNullOrEmpty()) {
                    lastForegroundPackage = fgPkg
                    prefs.edit().putString(KEY_ACTIVE_APP_RULE_LABEL, matchedOnExitQuiet.optString("appLabel") ?: "").apply()
                    findPresetById(appPresetId)?.let { applyPresetFromService(it, isManualTap = false) }
                    applyTargetVolumeForRule(matchedOnExitQuiet)
                    return
                }
            }
            lastForegroundPackage = ""
            val mode = prefs.getString(KEY_AUDIO_DEVICE_MODE, DEFAULT_AUDIO_DEVICE_MODE) ?: DEVICE_MODE_OFF
            if (mode == DEVICE_MODE_AUTO && isExternalAudioConnected()) {
                val hpId = prefs.getString(KEY_HEADPHONE_PRESET_ID, DEFAULT_HEADPHONE_PRESET_ID) ?: ""
                findPresetById(hpId)?.let { applyPresetFromService(it, isManualTap = false) }
                return
            }
            if (restoreId.isNotEmpty() && restoreId != "custom") {
                findPresetById(restoreId)?.let { applyPresetFromService(it, isManualTap = false) }
            }
        }

        if (!prefs.getBoolean(KEY_PER_APP_RULES_ENABLED, false)) {
            restorePreAppVolumeIfNeeded()
            if (!prefs.getString(KEY_ACTIVE_APP_RULE_LABEL, "").isNullOrEmpty()) {
                prefs.edit().remove(KEY_ACTIVE_APP_RULE_LABEL).apply()
            }
            return
        }
        val fgPkg = queryForegroundAppPackage() ?: return
        if (fgPkg == packageName || fgPkg == lastForegroundPackage) return
        lastForegroundPackage = fgPkg
        prefs.edit().putBoolean(KEY_MANUAL_OVERRIDE, false).apply()

        val matchedRuleObj = getMatchedPerAppRuleObject(fgPkg)
        val matchedPresetId = matchedRuleObj?.optString("presetId")
        val matchedLabel = matchedRuleObj?.optString("appLabel") ?: ""
        if (!matchedPresetId.isNullOrEmpty()) {
            prefs.edit().putString(KEY_ACTIVE_APP_RULE_LABEL, matchedLabel).apply()
            val currentActive = prefs.getString("active_preset_id", "") ?: ""
            if (!prefs.contains("pre_app_rule_preset_id") && currentActive.isNotEmpty() && currentActive != matchedPresetId) {
                prefs.edit().putString("pre_app_rule_preset_id", currentActive).apply()
            }
            findPresetById(matchedPresetId)?.let { targetObj ->
                if (prefs.getString("active_preset_id", "") != targetObj.optString("id")) {
                    applyPresetFromService(targetObj, isManualTap = false)
                }
            }
            applyTargetVolumeForRule(matchedRuleObj)
        } else {
            restorePreAppVolumeIfNeeded()
            if (!prefs.getString(KEY_ACTIVE_APP_RULE_LABEL, "").isNullOrEmpty()) {
                prefs.edit().remove(KEY_ACTIVE_APP_RULE_LABEL).apply()
            }
            if (prefs.contains("pre_app_rule_preset_id")) {
                val restoreId = prefs.getString("pre_app_rule_preset_id", "") ?: ""
                prefs.edit().remove("pre_app_rule_preset_id").apply()
                val mode = prefs.getString(KEY_AUDIO_DEVICE_MODE, DEFAULT_AUDIO_DEVICE_MODE) ?: DEVICE_MODE_OFF
                if (mode == DEVICE_MODE_AUTO && isExternalAudioConnected()) {
                    val hpId = prefs.getString(KEY_HEADPHONE_PRESET_ID, DEFAULT_HEADPHONE_PRESET_ID) ?: ""
                    findPresetById(hpId)?.let { applyPresetFromService(it, isManualTap = false) }
                } else if (restoreId.isNotEmpty() && restoreId != "custom") {
                    findPresetById(restoreId)?.let { applyPresetFromService(it, isManualTap = false) }
                }
            }
        }
    }

    private fun savePositionForCurrentOrientation(x: Int, y: Int) {
        val cleanX = (x - oledOffsetPxX).coerceAtLeast(0)
        val cleanY = (y - oledOffsetPxY).coerceAtLeast(0)
        val isLandscape = isCurrentlyLandscape()
        val editor = prefs.edit()
        if (isLandscape) {
            editor.putInt(KEY_POS_X_LANDSCAPE, cleanX)
            editor.putInt(KEY_POS_Y_LANDSCAPE, cleanY)
        } else {
            editor.putInt(KEY_POS_X_PORTRAIT, cleanX)
            editor.putInt(KEY_POS_Y_PORTRAIT, cleanY)
        }
        editor.apply()
    }

    private fun loadPositionForOrientation(isLandscape: Boolean): Pair<Int, Int> {
        val (screenW, screenH) = getScreenDimensionsPx()
        return if (isLandscape) {
            val defaultX = prefs.getInt(KEY_POS_X_LANDSCAPE, (screenW * 0.10f).toInt())
            val defaultY = prefs.getInt(KEY_POS_Y_LANDSCAPE, (screenH * 0.25f).toInt())
            defaultX to defaultY
        } else {
            val defaultX = prefs.getInt(KEY_POS_X_PORTRAIT, (screenW * 0.85f).toInt())
            val defaultY = prefs.getInt(KEY_POS_Y_PORTRAIT, (screenH * 0.38f).toInt())
            defaultX to defaultY
        }
    }

    private fun currentHandleWidthPx(): Int {
        return handleWidthPx.takeIf { it > 0 }
            ?: dpToPx(prefs.getInt(KEY_WIDTH_DP, DEFAULT_WIDTH_DP).coerceAtLeast(1))
    }

    private fun currentHandleHeightPx(): Int {
        return handleHeightPx.takeIf { it > 0 }
            ?: dpToPx(prefs.getInt(KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP).coerceAtLeast(1))
    }

    private fun isSideBadgeActive(): Boolean {
        val displayMode = prefs.getString(KEY_DISPLAY_MODE, DEFAULT_DISPLAY_MODE) ?: DISPLAY_MINIMAL
        if (displayMode != DISPLAY_PERCENT_BADGE) return false
        val basePct = prefs.getInt(KEY_TRANSPARENCY_PCT, DEFAULT_TRANSPARENCY_PCT).coerceIn(0, 100)
        val dimPct = prefs.getInt(KEY_DIM_OPACITY_PCT, DEFAULT_DIM_OPACITY_PCT).coerceIn(0, 50)
        val effectivePct = if (isIdleDimmed) min(basePct, dimPct) else basePct
        if (effectivePct == 0) return false
        val widthDp = prefs.getInt(KEY_WIDTH_DP, DEFAULT_WIDTH_DP).coerceAtLeast(1)
        val heightDp = prefs.getInt(KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP).coerceAtLeast(1)
        return !(widthDp >= 44 && heightDp >= 32)
    }

    private fun currentSideBadgeWidthPx(): Int {
        if (!isSideBadgeActive()) return 0
        val side = sideBadgeView
        if (side != null && side.visibility == View.VISIBLE) {
            val mw = if (side.measuredWidth > 0) side.measuredWidth else {
                side.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                side.measuredWidth
            }
            if (mw > 0) return mw + dpToPx(6)
        }
        return dpToPx(42) + dpToPx(6)
    }

    private fun currentRootWidthPx(): Int {
        return currentHandleWidthPx() + currentSideBadgeWidthPx()
    }

    private fun isOverlayOnRightHalf(rootX: Int = layoutParams.x): Boolean {
        val (screenW, _) = getScreenDimensionsPx()
        val totalW = currentRootWidthPx()
        return rootX + totalW / 2 >= screenW / 2
    }

    private fun clampCoordinates(x: Int, y: Int): Pair<Int, Int> {
        val (screenW, screenH) = getScreenDimensionsPx()
        val viewW = currentRootWidthPx()
        val viewH = currentHandleHeightPx()
        val maxX = (screenW - viewW).coerceAtLeast(0)
        val maxY = (screenH - viewH).coerceAtLeast(0)
        if (!prefs.getBoolean(KEY_AVOID_SAFE_ZONES, DEFAULT_AVOID_SAFE_ZONES)) {
            return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
        }
        val minY = safeInsetTop.coerceIn(0, maxY)
        val safeMaxY = (screenH - safeInsetBottom - viewH).coerceIn(minY, maxY)
        val minX = safeInsetLeft.coerceIn(0, maxX)
        val safeMaxX = (screenW - safeInsetRight - viewW).coerceIn(minX, maxX)
        return x.coerceIn(minX, safeMaxX) to y.coerceIn(minY, safeMaxY)
    }

    private fun getScreenDimensionsPx(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    companion object {
        @Volatile
        var isInstanceRunning: Boolean = false
        const val PREFS_NAME = "FloatingVolumePrefs"
        const val KEY_PRESETS_JSON = "profile_presets_json"
        const val KEY_WIDTH_DP = "width_dp"
        const val KEY_HEIGHT_DP = "height_dp"
        const val KEY_TRANSPARENCY_PCT = "transparency_pct"
        const val KEY_SHAPE = "shape"
        const val KEY_OVERLAY_COLOR = "overlay_color"
        const val KEY_POSITION_LOCKED = "position_locked"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_ROTATE_TOAST = "rotate_toast"

        const val KEY_AUDIO_STREAM = "audio_stream"
        const val KEY_VOLUME_STEP = "volume_step"
        const val KEY_MAX_VOLUME_LIMIT_PCT = "max_volume_limit_pct"
        const val KEY_SWIPE_SCRUB = "swipe_scrub"
        const val KEY_POPOUT_MODE = "popout_slider_mode"
        const val KEY_DISPLAY_MODE = "overlay_display_mode"
        const val KEY_HANDLE_ICON_STYLE = "handle_icon_style"
        const val KEY_SMOOTH_FADE = "smooth_fade_animation"
        const val KEY_HIDE_FULLSCREEN = "hide_in_fullscreen"

        const val KEY_HAPTIC_TAP = "haptic_tap"
        const val KEY_HAPTIC_STRENGTH = "haptic_strength"
        const val KEY_SNAP_TO_EDGE = "snap_to_edge"
        const val KEY_DOUBLE_TAP_MUTE = "double_tap_mute"
        const val KEY_TRIPLE_TAP_MEDIA = "triple_tap_play_pause"
        const val KEY_HOLD_TO_REPEAT = "hold_to_repeat"
        const val KEY_AUTO_BOOT = "auto_start_boot"
        const val KEY_AUTO_DIM_SEC = "auto_dim_seconds"
        const val KEY_DIM_OPACITY_PCT = "dim_opacity_pct"
        const val KEY_IS_SERVICE_RUNNING = "is_service_running"

        const val KEY_OLED_PIXEL_SHIFT = "oled_pixel_shift"
        const val KEY_AVOID_SAFE_ZONES = "avoid_safe_zones"
        const val KEY_BRIGHTNESS_HANDLE_ENABLED = "brightness_handle_enabled"
        const val KEY_BRIGHTNESS_COLOR = "brightness_color"
        const val KEY_BRIGHTNESS_WIDTH_DP = "brightness_width_dp"
        const val KEY_BRIGHTNESS_HEIGHT_DP = "brightness_height_dp"
        const val KEY_SHOW_BRIGHTNESS_IN_SLIDER = "show_brightness_in_slider"
        const val KEY_BRIGHTNESS_POS_X_PORTRAIT = "brightness_pos_x_portrait"
        const val KEY_BRIGHTNESS_POS_Y_PORTRAIT = "brightness_pos_y_portrait"
        const val KEY_BRIGHTNESS_POS_X_LANDSCAPE = "brightness_pos_x_landscape"
        const val KEY_BRIGHTNESS_POS_Y_LANDSCAPE = "brightness_pos_y_landscape"

        const val KEY_GESTURE_DOUBLE_TAP = "gesture_double_tap"
        const val KEY_GESTURE_TRIPLE_TAP = "gesture_triple_tap"
        const val KEY_GESTURE_INWARD_FLICK = "gesture_inward_flick"
        const val KEY_GESTURE_LOCKED_LONG_PRESS = "gesture_locked_long_press"

        const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        const val KEY_QUIET_START = "quiet_start_time"
        const val KEY_QUIET_END = "quiet_end_time"
        const val KEY_QUIET_PRESET_ID = "quiet_preset_id"

        const val KEY_PER_APP_RULES_ENABLED = "per_app_rules_enabled"
        const val KEY_PER_APP_RULES_JSON = "per_app_rules_json"

        const val KEY_AUDIO_DEVICE_MODE = "audio_device_mode"
        const val KEY_HEADPHONE_PRESET_ID = "headphone_preset_id"
        const val KEY_SPEAKER_PRESET_ID = "speaker_preset_id"
        const val KEY_MANUAL_OVERRIDE = "manual_preset_override"
        const val KEY_PRESET_APPLY_SEQ = "preset_apply_seq"
        const val KEY_ACTIVE_APP_RULE_LABEL = "active_app_rule_label"

        const val KEY_POS_X_PORTRAIT = "pos_x_portrait"
        const val KEY_POS_Y_PORTRAIT = "pos_y_portrait"
        const val KEY_POS_X_LANDSCAPE = "pos_x_landscape"
        const val KEY_POS_Y_LANDSCAPE = "pos_y_landscape"

        const val SHAPE_CIRCLE = "circle"
        const val SHAPE_LINE = "line"
        const val SHAPE_EDGE_TAB = "edge_tab"

        const val ICON_STYLE_ARROWS = "arrows"
        const val ICON_STYLE_SPEAKER = "speaker"
        const val ICON_STYLE_BARS = "bars"
        const val ICON_STYLE_DOT = "dot"
        const val ICON_STYLE_NONE = "none"

        const val ACTION_ID_NONE = "none"
        const val ACTION_ID_MUTE = "mute"
        const val ACTION_ID_PLAY_PAUSE = "play_pause"
        const val ACTION_ID_NEXT_TRACK = "next_track"
        const val ACTION_ID_PREV_TRACK = "prev_track"
        const val ACTION_ID_MINI_SLIDER = "mini_slider"
        const val ACTION_ID_TOGGLE_LOCK = "toggle_lock"
        const val ACTION_ID_TOGGLE_BRIGHTNESS = "toggle_brightness"

        const val POPOUT_SWIPE_INWARD = "swipe_inward"
        const val POPOUT_SINGLE_TAP = "single_tap"

        const val DISPLAY_MINIMAL = "minimal"
        const val DISPLAY_PERCENT_BADGE = "percent_badge"
        const val DISPLAY_SLIDER_FOCUS = "slider_focus"

        const val HAPTIC_OFF = "off"
        const val HAPTIC_LIGHT = "light"
        const val HAPTIC_MEDIUM = "medium"
        const val HAPTIC_STRONG = "strong"

        const val DEVICE_MODE_OFF = "off"
        const val DEVICE_MODE_AUTO = "auto_apply"
        const val DEVICE_MODE_TOAST = "toast_confirm"

        const val STREAM_MUSIC = "music"
        const val STREAM_RING = "ring"
        const val STREAM_ALARM = "alarm"
        const val STREAM_VOICE = "voice"
        const val DEFAULT_STREAM = "music"
        const val DEFAULT_DISPLAY_MODE = "slider_focus"
        const val DEFAULT_HANDLE_ICON_STYLE = "arrows"
        const val DEFAULT_AVOID_SAFE_ZONES = true
        const val DEFAULT_HAPTIC_STRENGTH = "medium"
        const val DEFAULT_AUDIO_DEVICE_MODE = "off"
        const val DEFAULT_HEADPHONE_PRESET_ID = "bedtime"
        const val DEFAULT_SPEAKER_PRESET_ID = "gaming"

        const val DEFAULT_WIDTH_DP = 17
        const val DEFAULT_HEIGHT_DP = 80
        const val DEFAULT_TRANSPARENCY_PCT = 45
        const val DEFAULT_AUTO_DIM_SEC = 5
        const val DEFAULT_DIM_OPACITY_PCT = 15
        const val DEFAULT_VOLUME_STEP = 1
        const val DEFAULT_MAX_VOLUME_LIMIT_PCT = 100
        val DEFAULT_COLOR = 0xFF06B6D4.toInt()

        const val DEFAULT_BRIGHTNESS_WIDTH_DP = 46
        const val DEFAULT_BRIGHTNESS_HEIGHT_DP = 102
        val DEFAULT_BRIGHTNESS_COLOR = 0xFFF59E0B.toInt()

        const val DEFAULT_GESTURE_DOUBLE_TAP = "none"
        const val DEFAULT_GESTURE_TRIPLE_TAP = "none"
        const val DEFAULT_GESTURE_INWARD_FLICK = "mini_slider"
        const val DEFAULT_GESTURE_LOCKED_LONG_PRESS = "none"

        const val DEFAULT_QUIET_START = "22:30"
        const val DEFAULT_QUIET_END = "07:00"
        const val DEFAULT_QUIET_PRESET_ID = "bedtime"

        const val ACTION_VOL_UP = "com.example.floatingvolume.ACTION_VOL_UP"
        const val ACTION_VOL_DOWN = "com.example.floatingvolume.ACTION_VOL_DOWN"
        const val ACTION_CYCLE_PRESET = "com.example.floatingvolume.ACTION_CYCLE_PRESET"
        const val ACTION_TOGGLE_MUTE = "com.example.floatingvolume.ACTION_TOGGLE_MUTE"
        const val ACTION_TOGGLE_LOCK = "com.example.floatingvolume.ACTION_TOGGLE_LOCK"
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

        private const val CHANNEL_ID = "floating_volume_channel"
        private const val NOTIFICATION_ID = 1001
        private const val LONG_PRESS_TIMEOUT_MS = 400L
        private const val REPEAT_START_DELAY_MS = 380L
        private const val REPEAT_INTERVAL_MS = 130L
        private const val MULTI_TAP_WINDOW_MS = 295L
        private const val OLED_SHIFT_INTERVAL_MS = 60_000L
        private const val AUTOMATION_TICK_MS = 1_500L
    }
}
