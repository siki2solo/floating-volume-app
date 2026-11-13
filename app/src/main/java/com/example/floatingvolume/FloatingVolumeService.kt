package com.example.floatingvolume

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.TileService
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.hypot
import kotlin.math.min

class FloatingVolumeService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var displayManager: DisplayManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var prefs: SharedPreferences

    private var floatingView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val overlayDrawable = GradientDrawable()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isDragging = false
    private var touchSlop = 0
    private var lastAppliedLandscape: Boolean? = null

    private var pendingSingleTapRunnable: Runnable? = null
    private var lastTapTimeMs = 0L

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val longPressRunnable = Runnable {
        if (isPositionLocked()) return@Runnable
        isDragging = true
        floatingView?.apply {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            animate().scaleX(1.08f).scaleY(1.08f).setDuration(120).start()
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            handleOrientationCheck(showNotice = true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        prefs.edit().putBoolean(KEY_IS_SERVICE_RUNNING, true).apply()
        requestTileRefresh()

        startAsForegroundService()
        createFloatingOverlay()

        displayManager.registerDisplayListener(displayListener, mainHandler)
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applyAppearanceFromPrefs()
        handleOrientationCheck(showNotice = false)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(longPressRunnable)
        pendingSingleTapRunnable?.let { mainHandler.removeCallbacks(it) }
        displayManager.unregisterDisplayListener(displayListener)
        prefs.unregisterOnSharedPreferenceChangeListener(this)

        prefs.edit().putBoolean(KEY_IS_SERVICE_RUNNING, false).apply()
        requestTileRefresh()

        floatingView?.let { view ->
            if (view.isAttachedToWindow) {
                windowManager.removeView(view)
            }
        }
        floatingView = null
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            KEY_WIDTH_DP, KEY_HEIGHT_DP, KEY_TRANSPARENCY_PCT, KEY_SHAPE, KEY_OVERLAY_COLOR -> {
                applyAppearanceFromPrefs()
            }
            KEY_SNAP_TO_EDGE -> {
                if (prefs.getBoolean(KEY_SNAP_TO_EDGE, false)) {
                    snapCurrentOverlayToNearestEdge()
                }
            }
            KEY_POS_X_PORTRAIT, KEY_POS_Y_PORTRAIT, KEY_POS_X_LANDSCAPE, KEY_POS_Y_LANDSCAPE -> {
                moveOverlayToOrientationPosition(isCurrentlyLandscape(), showNotice = false)
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

    private fun requestTileRefresh() {
        TileService.requestListeningState(
            this,
            ComponentName(this, FloatingVolumeTileService::class.java)
        )
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

    private fun moveOverlayToOrientationPosition(isLandscape: Boolean, showNotice: Boolean) {
        val view = floatingView ?: return
        val (savedX, savedY) = loadPositionForOrientation(isLandscape)
        val (clampedX, clampedY) = clampCoordinates(savedX, savedY)

        layoutParams.x = clampedX
        layoutParams.y = clampedY
        lastAppliedLandscape = isLandscape

        if (view.isAttachedToWindow) {
            windowManager.updateViewLayout(view, layoutParams)
        }

        val modeLabel = if (isLandscape) "Landscape" else "Portrait"
        notificationManager.notify(
            NOTIFICATION_ID,
            buildForegroundNotification("$modeLabel spot: ($clampedX, $clampedY)")
        )

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
        val (screenW, _) = getScreenDimensionsPx()
        val viewW = if (layoutParams.width > 0) layoutParams.width else dpToPx(DEFAULT_WIDTH_DP)
        val maxX = (screenW - viewW).coerceAtLeast(0)
        val centerX = layoutParams.x + viewW / 2
        val snappedX = if (centerX < screenW / 2) 0 else maxX

        layoutParams.x = snappedX
        if (view.isAttachedToWindow) {
            windowManager.updateViewLayout(view, layoutParams)
        }
        savePositionForCurrentOrientation(layoutParams.x, layoutParams.y)
    }

    private fun isPositionLocked(): Boolean {
        return prefs.getBoolean(KEY_POSITION_LOCKED, false)
    }

    private fun startAsForegroundService() {
        createNotificationChannel()
        val modeLabel = if (isCurrentlyLandscape()) "Landscape" else "Portrait"
        val notification = buildForegroundNotification("Active in $modeLabel mode")

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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Volume Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
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

        val view = View(this).apply {
            isClickable = true
            isFocusable = false
            background = overlayDrawable
        }

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    if (!isPositionLocked()) {
                        mainHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    if (!isDragging) {
                        if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                            mainHandler.removeCallbacks(longPressRunnable)
                        }
                    } else if (!isPositionLocked()) {
                        val (clampedX, clampedY) = clampCoordinates(
                            initialX + dx.toInt(),
                            initialY + dy.toInt()
                        )
                        layoutParams.x = clampedX
                        layoutParams.y = clampedY
                        windowManager.updateViewLayout(v, layoutParams)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    if (isDragging) {
                        isDragging = false
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                        if (prefs.getBoolean(KEY_SNAP_TO_EDGE, false)) {
                            snapCurrentOverlayToNearestEdge()
                        } else {
                            savePositionForCurrentOrientation(layoutParams.x, layoutParams.y)
                        }
                    } else {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (hypot(dx.toDouble(), dy.toDouble()) <= touchSlop) {
                            handleOverlayTap(v, event.y < v.height / 2f)
                            v.performClick()
                        }
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    if (isDragging) {
                        isDragging = false
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    }
                    true
                }

                else -> false
            }
        }

        floatingView = view
        applyAppearanceFromPrefs(addIfMissing = false)

        val (clampedX, clampedY) = clampCoordinates(layoutParams.x, layoutParams.y)
        layoutParams.x = clampedX
        layoutParams.y = clampedY

        windowManager.addView(view, layoutParams)
    }

    private fun handleOverlayTap(view: View, isUpperHalf: Boolean) {
        val doubleTapMuteEnabled = prefs.getBoolean(KEY_DOUBLE_TAP_MUTE, false)
        val direction = if (isUpperHalf) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER

        if (!doubleTapMuteEnabled) {
            triggerHapticIfEnabled(view)
            adjustVolume(direction)
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastTapTimeMs <= DOUBLE_TAP_TIMEOUT_MS) {
            pendingSingleTapRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingSingleTapRunnable = null
            lastTapTimeMs = 0L
            triggerHapticIfEnabled(view)
            adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE)
        } else {
            lastTapTimeMs = now
            val runnable = Runnable {
                triggerHapticIfEnabled(view)
                adjustVolume(direction)
                pendingSingleTapRunnable = null
            }
            pendingSingleTapRunnable = runnable
            mainHandler.postDelayed(runnable, DOUBLE_TAP_TIMEOUT_MS)
        }
    }

    private fun triggerHapticIfEnabled(view: View) {
        if (prefs.getBoolean(KEY_HAPTIC_TAP, true)) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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

    private fun applyAppearanceFromPrefs(addIfMissing: Boolean = true) {
        val view = floatingView ?: return

        val widthDp = prefs.getInt(KEY_WIDTH_DP, DEFAULT_WIDTH_DP).coerceAtLeast(1)
        val heightDp = prefs.getInt(KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP).coerceAtLeast(1)
        val transparencyPct =
            prefs.getInt(KEY_TRANSPARENCY_PCT, DEFAULT_TRANSPARENCY_PCT).coerceIn(0, 100)
        val shape = prefs.getString(KEY_SHAPE, SHAPE_CIRCLE)
        val baseColor = prefs.getInt(KEY_OVERLAY_COLOR, DEFAULT_COLOR)

        val widthPx = dpToPx(widthDp)
        val heightPx = dpToPx(heightDp)
        val alpha = ((transparencyPct / 100f) * 255).toInt().coerceIn(0, 255)
        val strokeAlpha = if (alpha == 0) 0 else min(255, alpha + 45)

        if (shape == SHAPE_LINE) {
            overlayDrawable.shape = GradientDrawable.RECTANGLE
            overlayDrawable.cornerRadius = min(widthPx, heightPx) / 2f
        } else {
            overlayDrawable.shape = GradientDrawable.OVAL
        }

        overlayDrawable.setColor(
            Color.argb(alpha, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
        )
        overlayDrawable.setStroke(dpToPx(2), Color.argb(strokeAlpha, 255, 255, 255))

        layoutParams.width = widthPx
        layoutParams.height = heightPx
        val (clampedX, clampedY) = clampCoordinates(layoutParams.x, layoutParams.y)
        layoutParams.x = clampedX
        layoutParams.y = clampedY

        view.background = overlayDrawable
        if (addIfMissing && view.isAttachedToWindow) {
            windowManager.updateViewLayout(view, layoutParams)
        }
    }

    private fun adjustVolume(direction: Int) {
        audioManager.adjustStreamVolume(
            getSelectedAudioStream(),
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun savePositionForCurrentOrientation(x: Int, y: Int) {
        val isLandscape = isCurrentlyLandscape()
        val editor = prefs.edit()
        if (isLandscape) {
            editor.putInt(KEY_POS_X_LANDSCAPE, x)
            editor.putInt(KEY_POS_Y_LANDSCAPE, y)
        } else {
            editor.putInt(KEY_POS_X_PORTRAIT, x)
            editor.putInt(KEY_POS_Y_PORTRAIT, y)
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

    private fun clampCoordinates(x: Int, y: Int): Pair<Int, Int> {
        val (screenW, screenH) = getScreenDimensionsPx()
        val viewW = if (layoutParams.width > 0) layoutParams.width else dpToPx(DEFAULT_WIDTH_DP)
        val viewH = if (layoutParams.height > 0) layoutParams.height else dpToPx(DEFAULT_HEIGHT_DP)
        val maxX = (screenW - viewW).coerceAtLeast(0)
        val maxY = (screenH - viewH).coerceAtLeast(0)
        return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
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
        const val PREFS_NAME = "FloatingVolumePrefs"
        const val KEY_WIDTH_DP = "width_dp"
        const val KEY_HEIGHT_DP = "height_dp"
        const val KEY_TRANSPARENCY_PCT = "transparency_pct"
        const val KEY_SHAPE = "shape"
        const val KEY_OVERLAY_COLOR = "overlay_color"
        const val KEY_POSITION_LOCKED = "position_locked"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_ROTATE_TOAST = "rotate_toast"

        const val KEY_AUDIO_STREAM = "audio_stream"
        const val KEY_HAPTIC_TAP = "haptic_tap"
        const val KEY_SNAP_TO_EDGE = "snap_to_edge"
        const val KEY_DOUBLE_TAP_MUTE = "double_tap_mute"
        const val KEY_AUTO_BOOT = "auto_start_boot"
        const val KEY_IS_SERVICE_RUNNING = "is_service_running"

        const val KEY_POS_X_PORTRAIT = "pos_x_portrait"
        const val KEY_POS_Y_PORTRAIT = "pos_y_portrait"
        const val KEY_POS_X_LANDSCAPE = "pos_x_landscape"
        const val KEY_POS_Y_LANDSCAPE = "pos_y_landscape"

        const val SHAPE_CIRCLE = "circle"
        const val SHAPE_LINE = "line"

        const val STREAM_MUSIC = "music"
        const val STREAM_RING = "ring"
        const val STREAM_ALARM = "alarm"
        const val STREAM_VOICE = "voice"
        const val DEFAULT_STREAM = "music"

        const val DEFAULT_WIDTH_DP = 56
        const val DEFAULT_HEIGHT_DP = 112
        const val DEFAULT_TRANSPARENCY_PCT = 82
        val DEFAULT_COLOR = 0xFF2563EB.toInt()

        private const val CHANNEL_ID = "floating_volume_channel"
        private const val NOTIFICATION_ID = 1001
        private const val LONG_PRESS_TIMEOUT_MS = 400L
        private const val DOUBLE_TAP_TIMEOUT_MS = 220L
    }
}
