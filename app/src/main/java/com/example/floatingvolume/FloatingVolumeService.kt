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
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class FloatingVolumeService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var windowManager: WindowManager
    private lateinit var audioManager: AudioManager
    private lateinit var displayManager: DisplayManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var prefs: SharedPreferences

    private var floatingView: View? = null
    private var fullscreenSentinelView: View? = null
    private var miniSliderView: LinearLayout? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val overlayDrawable = GradientDrawable()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isDragging = false
    private var isScrubbing = false
    private var repeatActive = false
    private var repeatUpperHalf = true
    private var isIdleDimmed = false
    private var isFullscreenHidden = false
    private var touchSlop = 0
    private var scrubAccumY = 0f
    private var lastAppliedLandscape: Boolean? = null

    private var pendingSingleTapRunnable: Runnable? = null
    private var lastTapTimeMs = 0L

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val longPressRunnable = Runnable {
        if (isPositionLocked() || isScrubbing) return@Runnable
        isDragging = true
        floatingView?.apply {
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
        if (!isPositionLocked() || !prefs.getBoolean(KEY_HOLD_TO_REPEAT, true)) return@Runnable
        repeatActive = true
        triggerHapticIfEnabled(view)
        adjustVolumeByStep(if (repeatUpperHalf) 1 else -1)
        mainHandler.postDelayed(repeatVolumeRunnable, REPEAT_INTERVAL_MS)
    }

    private val autoDimRunnable = Runnable {
        if (prefs.getInt(KEY_AUTO_DIM_SEC, DEFAULT_AUTO_DIM_SEC) <= 0) return@Runnable
        isIdleDimmed = true
        applyAppearanceFromPrefs()
    }

    private val dismissSliderRunnable = Runnable {
        dismissMiniSlider()
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
        createFullscreenSentinel()
        createFloatingOverlay()
        scheduleAutoDim()

        displayManager.registerDisplayListener(displayListener, mainHandler)
        prefs.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_VOL_UP -> {
                wakeFromIdleAndScheduleDim()
                adjustVolumeByStep(1)
            }
            ACTION_VOL_DOWN -> {
                wakeFromIdleAndScheduleDim()
                adjustVolumeByStep(-1)
            }
            ACTION_TOGGLE_MUTE -> {
                wakeFromIdleAndScheduleDim()
                audioManager.adjustStreamVolume(
                    getSelectedAudioStream(),
                    AudioManager.ADJUST_TOGGLE_MUTE,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            ACTION_TOGGLE_LOCK -> {
                val nextLocked = !isPositionLocked()
                prefs.edit().putBoolean(KEY_POSITION_LOCKED, nextLocked).apply()
                refreshForegroundNotification()
            }
            else -> {
                applyAppearanceFromPrefs()
                handleOrientationCheck(showNotice = false)
                scheduleAutoDim()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(longPressRunnable)
        mainHandler.removeCallbacks(startRepeatRunnable)
        mainHandler.removeCallbacks(repeatVolumeRunnable)
        mainHandler.removeCallbacks(autoDimRunnable)
        mainHandler.removeCallbacks(dismissSliderRunnable)
        pendingSingleTapRunnable?.let { mainHandler.removeCallbacks(it) }
        displayManager.unregisterDisplayListener(displayListener)
        prefs.unregisterOnSharedPreferenceChangeListener(this)

        prefs.edit().putBoolean(KEY_IS_SERVICE_RUNNING, false).apply()
        requestTileRefresh()

        dismissMiniSlider()
        fullscreenSentinelView?.let { view ->
            if (view.isAttachedToWindow) windowManager.removeView(view)
        }
        fullscreenSentinelView = null
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
            KEY_AUTO_DIM_SEC, KEY_DIM_OPACITY_PCT -> {
                wakeFromIdleAndScheduleDim()
            }
            KEY_HIDE_FULLSCREEN -> {
                if (!prefs.getBoolean(KEY_HIDE_FULLSCREEN, false)) {
                    applyFullscreenState(false)
                }
            }
            KEY_POSITION_LOCKED -> {
                refreshForegroundNotification()
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

    private fun refreshForegroundNotification() {
        val modeLabel = if (isCurrentlyLandscape()) "Landscape" else "Portrait"
        val lockState = if (isPositionLocked()) "Locked" else "Unlocked"
        notificationManager.notify(
            NOTIFICATION_ID,
            buildForegroundNotification("$modeLabel · Position $lockState")
        )
    }

    private fun buildServiceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val actionIntent = Intent(this, FloatingVolumeService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            requestCode,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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

        val lockLabel = if (isPositionLocked()) "Unlock" else "Lock"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(0, "Vol -", buildServiceActionPendingIntent(ACTION_VOL_DOWN, 10))
            .addAction(0, "Vol +", buildServiceActionPendingIntent(ACTION_VOL_UP, 11))
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

        val sentinel = View(this)
        val lp = WindowManager.LayoutParams(
            0,
            0,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sentinel.setOnApplyWindowInsetsListener { _, insets ->
                val statusHidden = !insets.isVisible(WindowInsets.Type.statusBars())
                applyFullscreenState(statusHidden)
                insets
            }
        } else {
            @Suppress("DEPRECATION")
            sentinel.setOnSystemUiVisibilityChangeListener { visibility ->
                val statusHidden = (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) != 0
                applyFullscreenState(statusHidden)
            }
        }

        fullscreenSentinelView = sentinel
        windowManager.addView(sentinel, lp)
    }

    private fun applyFullscreenState(fullscreenDetected: Boolean) {
        val shouldHide = fullscreenDetected && prefs.getBoolean(KEY_HIDE_FULLSCREEN, false)
        if (isFullscreenHidden == shouldHide) return
        isFullscreenHidden = shouldHide
        floatingView?.visibility = if (shouldHide) View.GONE else View.VISIBLE
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

        val view = View(this).apply {
            isClickable = true
            isFocusable = false
            background = overlayDrawable
        }

        val scrubStepPx = dpToPx(18).toFloat()
        val inwardFlickPx = dpToPx(28).toFloat()

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    wakeFromIdleAndScheduleDim()
                    isDragging = false
                    isScrubbing = false
                    repeatActive = false
                    scrubAccumY = 0f
                    repeatUpperHalf = event.y < v.height / 2f
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    if (!isPositionLocked()) {
                        mainHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS)
                    } else if (prefs.getBoolean(KEY_HOLD_TO_REPEAT, true)) {
                        mainHandler.postDelayed(startRepeatRunnable, REPEAT_START_DELAY_MS)
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

                            if (prefs.getBoolean(KEY_SWIPE_SCRUB, true) && abs(dy) >= abs(dx)) {
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
                        windowManager.updateViewLayout(v, layoutParams)
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

                    if (repeatActive || isScrubbing) {
                        repeatActive = false
                        isScrubbing = false
                    } else if (isDragging) {
                        isDragging = false
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                        if (prefs.getBoolean(KEY_SNAP_TO_EDGE, false)) {
                            snapCurrentOverlayToNearestEdge()
                        } else {
                            savePositionForCurrentOrientation(layoutParams.x, layoutParams.y)
                        }
                    } else if (abs(dx) >= inwardFlickPx && abs(dx) > abs(dy)) {
                        triggerHapticIfEnabled(v)
                        toggleMiniSlider()
                    } else if (hypot(dx.toDouble(), dy.toDouble()) <= touchSlop) {
                        handleOverlayTap(v, event.y < v.height / 2f)
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
                    scheduleAutoDim()
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
        val popoutMode = prefs.getString(KEY_POPOUT_MODE, POPOUT_SWIPE_INWARD)
        if (popoutMode == POPOUT_SINGLE_TAP) {
            triggerHapticIfEnabled(view)
            toggleMiniSlider()
            return
        }

        val doubleTapMuteEnabled = prefs.getBoolean(KEY_DOUBLE_TAP_MUTE, false)
        val stepDir = if (isUpperHalf) 1 else -1

        if (!doubleTapMuteEnabled) {
            triggerHapticIfEnabled(view)
            adjustVolumeByStep(stepDir)
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastTapTimeMs <= DOUBLE_TAP_TIMEOUT_MS) {
            pendingSingleTapRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingSingleTapRunnable = null
            lastTapTimeMs = 0L
            triggerHapticIfEnabled(view)
            audioManager.adjustStreamVolume(
                getSelectedAudioStream(),
                AudioManager.ADJUST_TOGGLE_MUTE,
                AudioManager.FLAG_SHOW_UI
            )
        } else {
            lastTapTimeMs = now
            val runnable = Runnable {
                triggerHapticIfEnabled(view)
                adjustVolumeByStep(stepDir)
                pendingSingleTapRunnable = null
            }
            pendingSingleTapRunnable = runnable
            mainHandler.postDelayed(runnable, DOUBLE_TAP_TIMEOUT_MS)
        }
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

        val stream = getSelectedAudioStream()
        val maxVol = audioManager.getStreamMaxVolume(stream).coerceAtLeast(1)
        val maxAllowed = getMaxAllowedVolume(stream)
        val currentVol = audioManager.getStreamVolume(stream).coerceIn(0, maxAllowed)

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dpToPx(12)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE0F172A"))
                cornerRadius = dpToPx(16).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#475569"))
            }
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleTv = TextView(this).apply {
            text = "VOL $currentVol/$maxVol (Cap $maxAllowed)"
            setTextColor(Color.WHITE)
            textSize = 12f
        }
        headerRow.addView(titleTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        val closeBtn = Button(this).apply {
            text = "X"
            textSize = 11f
            setOnClickListener { dismissMiniSlider() }
        }
        headerRow.addView(closeBtn, LinearLayout.LayoutParams(dpToPx(42), dpToPx(34)))
        panel.addView(headerRow)

        val seekBar = SeekBar(this).apply {
            max = maxAllowed
            progress = currentVol
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        setVolumeClamped(progress)
                        titleTv.text = "VOL $progress/$maxVol (Cap $maxAllowed)"
                        resetMiniSliderTimeout()
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) = resetMiniSliderTimeout()
                override fun onStopTrackingTouch(sb: SeekBar?) = resetMiniSliderTimeout()
            })
        }
        panel.addView(seekBar, LinearLayout.LayoutParams(dpToPx(188), LinearLayout.LayoutParams.WRAP_CONTENT))

        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnDown = Button(this).apply {
            text = "-"
            setOnClickListener {
                adjustVolumeByStep(-1)
                val updated = audioManager.getStreamVolume(stream).coerceIn(0, maxAllowed)
                seekBar.progress = updated
                titleTv.text = "VOL $updated/$maxVol (Cap $maxAllowed)"
                resetMiniSliderTimeout()
            }
        }
        val btnMute = Button(this).apply {
            text = "Mute"
            setOnClickListener {
                audioManager.adjustStreamVolume(stream, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
                resetMiniSliderTimeout()
            }
        }
        val btnUp = Button(this).apply {
            text = "+"
            setOnClickListener {
                adjustVolumeByStep(1)
                val updated = audioManager.getStreamVolume(stream).coerceIn(0, maxAllowed)
                seekBar.progress = updated
                titleTv.text = "VOL $updated/$maxVol (Cap $maxAllowed)"
                resetMiniSliderTimeout()
            }
        }
        val btnLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        actionRow.addView(btnDown, btnLp)
        actionRow.addView(btnMute, btnLp)
        actionRow.addView(btnUp, btnLp)
        panel.addView(actionRow)

        val (screenW, screenH) = getScreenDimensionsPx()
        val sliderWidth = dpToPx(212)
        val targetX = if (layoutParams.x > screenW / 2) {
            (layoutParams.x - sliderWidth - dpToPx(10)).coerceAtLeast(dpToPx(8))
        } else {
            (layoutParams.x + layoutParams.width + dpToPx(10)).coerceAtMost((screenW - sliderWidth - dpToPx(8)).coerceAtLeast(0))
        }
        val targetY = layoutParams.y.coerceIn(dpToPx(24), (screenH - dpToPx(160)).coerceAtLeast(dpToPx(24)))

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

        miniSliderView = panel
        windowManager.addView(panel, sliderLp)
        resetMiniSliderTimeout()
    }

    private fun resetMiniSliderTimeout() {
        mainHandler.removeCallbacks(dismissSliderRunnable)
        mainHandler.postDelayed(dismissSliderRunnable, 3500L)
    }

    private fun dismissMiniSlider() {
        mainHandler.removeCallbacks(dismissSliderRunnable)
        miniSliderView?.let { view ->
            if (view.isAttachedToWindow) {
                windowManager.removeView(view)
            }
        }
        miniSliderView = null
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

    private fun setVolumeClamped(target: Int) {
        val stream = getSelectedAudioStream()
        val maxAllowed = getMaxAllowedVolume(stream)
        val clamped = target.coerceIn(0, maxAllowed)
        audioManager.setStreamVolume(stream, clamped, AudioManager.FLAG_SHOW_UI)
    }

    private fun wakeFromIdleAndScheduleDim() {
        mainHandler.removeCallbacks(autoDimRunnable)
        if (isIdleDimmed) {
            isIdleDimmed = false
            applyAppearanceFromPrefs()
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
        }
    }

    private fun applyAppearanceFromPrefs(addIfMissing: Boolean = true) {
        val view = floatingView ?: return

        val widthDp = prefs.getInt(KEY_WIDTH_DP, DEFAULT_WIDTH_DP).coerceAtLeast(1)
        val heightDp = prefs.getInt(KEY_HEIGHT_DP, DEFAULT_HEIGHT_DP).coerceAtLeast(1)
        val basePct =
            prefs.getInt(KEY_TRANSPARENCY_PCT, DEFAULT_TRANSPARENCY_PCT).coerceIn(0, 100)
        val dimPct =
            prefs.getInt(KEY_DIM_OPACITY_PCT, DEFAULT_DIM_OPACITY_PCT).coerceIn(0, 50)
        val effectivePct = if (isIdleDimmed) min(basePct, dimPct) else basePct
        val shape = prefs.getString(KEY_SHAPE, SHAPE_CIRCLE)
        val baseColor = prefs.getInt(KEY_OVERLAY_COLOR, DEFAULT_COLOR)

        val widthPx = dpToPx(widthDp)
        val heightPx = dpToPx(heightDp)
        val alpha = ((effectivePct / 100f) * 255).toInt().coerceIn(0, 255)
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
        const val KEY_VOLUME_STEP = "volume_step"
        const val KEY_MAX_VOLUME_LIMIT_PCT = "max_volume_limit_pct"
        const val KEY_SWIPE_SCRUB = "swipe_scrub"
        const val KEY_POPOUT_MODE = "popout_slider_mode"
        const val KEY_HIDE_FULLSCREEN = "hide_in_fullscreen"

        const val KEY_HAPTIC_TAP = "haptic_tap"
        const val KEY_SNAP_TO_EDGE = "snap_to_edge"
        const val KEY_DOUBLE_TAP_MUTE = "double_tap_mute"
        const val KEY_HOLD_TO_REPEAT = "hold_to_repeat"
        const val KEY_AUTO_BOOT = "auto_start_boot"
        const val KEY_AUTO_DIM_SEC = "auto_dim_seconds"
        const val KEY_DIM_OPACITY_PCT = "dim_opacity_pct"
        const val KEY_IS_SERVICE_RUNNING = "is_service_running"

        const val KEY_POS_X_PORTRAIT = "pos_x_portrait"
        const val KEY_POS_Y_PORTRAIT = "pos_y_portrait"
        const val KEY_POS_X_LANDSCAPE = "pos_x_landscape"
        const val KEY_POS_Y_LANDSCAPE = "pos_y_landscape"

        const val SHAPE_CIRCLE = "circle"
        const val SHAPE_LINE = "line"

        const val POPOUT_SWIPE_INWARD = "swipe_inward"
        const val POPOUT_SINGLE_TAP = "single_tap"

        const val STREAM_MUSIC = "music"
        const val STREAM_RING = "ring"
        const val STREAM_ALARM = "alarm"
        const val STREAM_VOICE = "voice"
        const val DEFAULT_STREAM = "music"

        const val DEFAULT_WIDTH_DP = 44
        const val DEFAULT_HEIGHT_DP = 152
        const val DEFAULT_TRANSPARENCY_PCT = 55
        const val DEFAULT_AUTO_DIM_SEC = 5
        const val DEFAULT_DIM_OPACITY_PCT = 15
        const val DEFAULT_VOLUME_STEP = 1
        const val DEFAULT_MAX_VOLUME_LIMIT_PCT = 100
        val DEFAULT_COLOR = 0xFF06B6D4.toInt()

        const val ACTION_VOL_UP = "com.example.floatingvolume.ACTION_VOL_UP"
        const val ACTION_VOL_DOWN = "com.example.floatingvolume.ACTION_VOL_DOWN"
        const val ACTION_TOGGLE_MUTE = "com.example.floatingvolume.ACTION_TOGGLE_MUTE"
        const val ACTION_TOGGLE_LOCK = "com.example.floatingvolume.ACTION_TOGGLE_LOCK"

        private const val CHANNEL_ID = "floating_volume_channel"
        private const val NOTIFICATION_ID = 1001
        private const val LONG_PRESS_TIMEOUT_MS = 400L
        private const val REPEAT_START_DELAY_MS = 380L
        private const val REPEAT_INTERVAL_MS = 130L
        private const val DOUBLE_TAP_TIMEOUT_MS = 220L
    }
}
