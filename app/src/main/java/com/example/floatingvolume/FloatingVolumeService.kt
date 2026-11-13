package com.example.floatingvolume

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlin.math.hypot
import kotlin.math.min

class FloatingVolumeService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var prefs: SharedPreferences

    private var floatingView: View? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val overlayDrawable = GradientDrawable()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isDragging = false
    private var touchSlop = 0

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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        startAsForegroundService()
        createFloatingOverlay()

        // Live sync all appearance and Portrait/Landscape location edits from MainActivity
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applyAppearanceFromPrefs()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(longPressRunnable)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
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
            KEY_POS_X_PORTRAIT, KEY_POS_Y_PORTRAIT, KEY_POS_X_LANDSCAPE, KEY_POS_Y_LANDSCAPE -> {
                moveOverlayToOrientationPosition(
                    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        moveOverlayToOrientationPosition(isLandscape)
    }

    private fun moveOverlayToOrientationPosition(isLandscape: Boolean) {
        val view = floatingView ?: return
        val (savedX, savedY) = loadPositionForOrientation(isLandscape)
        val (clampedX, clampedY) = clampCoordinates(savedX, savedY)

        layoutParams.x = clampedX
        layoutParams.y = clampedY
        if (view.isAttachedToWindow) {
            windowManager.updateViewLayout(view, layoutParams)
        }
    }

    private fun isPositionLocked(): Boolean {
        return prefs.getBoolean(KEY_POSITION_LOCKED, false)
    }

    private fun startAsForegroundService() {
        createNotificationChannel()
        val notification = buildForegroundNotification()

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
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
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
            .setContentText("Tap upper half for Volume Up, lower half for Volume Down")
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

        val isLandscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
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
                        savePositionForCurrentOrientation(layoutParams.x, layoutParams.y)
                    } else {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
                        if (hypot(dx.toDouble(), dy.toDouble()) <= touchSlop) {
                            if (event.y < v.height / 2f) {
                                adjustVolume(AudioManager.ADJUST_RAISE)
                            } else {
                                adjustVolume(AudioManager.ADJUST_LOWER)
                            }
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
        // At 0% opacity, set strokeAlpha to 0 as well so the overlay is completely invisible
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
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun savePositionForCurrentOrientation(x: Int, y: Int) {
        val isLandscape =
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
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
        return if (isLandscape) {
            val defaultX = prefs.getInt(KEY_POS_X_LANDSCAPE, dpToPx(24))
            val defaultY = prefs.getInt(KEY_POS_Y_LANDSCAPE, dpToPx(96))
            defaultX to defaultY
        } else {
            val defaultX = prefs.getInt(KEY_POS_X_PORTRAIT, dpToPx(24))
            val defaultY = prefs.getInt(KEY_POS_Y_PORTRAIT, dpToPx(180))
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
            @Suppress("DEPRECATION")
            val metrics = resources.displayMetrics
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

        const val KEY_POS_X_PORTRAIT = "pos_x_portrait"
        const val KEY_POS_Y_PORTRAIT = "pos_y_portrait"
        const val KEY_POS_X_LANDSCAPE = "pos_x_landscape"
        const val KEY_POS_Y_LANDSCAPE = "pos_y_landscape"

        const val SHAPE_CIRCLE = "circle"
        const val SHAPE_LINE = "line"

        const val DEFAULT_WIDTH_DP = 80
        const val DEFAULT_HEIGHT_DP = 156
        const val DEFAULT_TRANSPARENCY_PCT = 23
        val DEFAULT_COLOR = 0xFF000000.toInt()

        private const val CHANNEL_ID = "floating_volume_channel"
        private const val NOTIFICATION_ID = 1001
        private const val LONG_PRESS_TIMEOUT_MS = 400L
    }
}
