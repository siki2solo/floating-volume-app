package com.example.floatingvolume

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
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
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: SharedPreferences
    private lateinit var audioManager: AudioManager
    private val uiHandler = Handler(Looper.getMainLooper())

    private lateinit var mainRootLayout: LinearLayout
    private lateinit var layoutActivityHeader: LinearLayout
    private lateinit var layoutHeaderInfo: LinearLayout
    private lateinit var tvHeaderStatus: TextView
    private lateinit var btnThemeToggle: Button

    private lateinit var slideViewport: FrameLayout
    private lateinit var slideHome: ScrollView
    private lateinit var slideCustomize: ScrollView
    private lateinit var slideSettings: ScrollView
    private lateinit var slidePermissions: ScrollView

    private lateinit var bottomNavBar: LinearLayout
    private lateinit var tabHome: LinearLayout
    private lateinit var tabCustomize: LinearLayout
    private lateinit var tabSettings: LinearLayout
    private lateinit var tabPermissions: LinearLayout
    private lateinit var tabIndicatorHome: View
    private lateinit var tabIndicatorCustomize: View
    private lateinit var tabIndicatorSettings: View
    private lateinit var tabIndicatorPermissions: View
    private lateinit var ivTabHome: ImageView
    private lateinit var ivTabCustomize: ImageView
    private lateinit var ivTabSettings: ImageView
    private lateinit var ivTabPermissions: ImageView
    private lateinit var tvTabHome: TextView
    private lateinit var tvTabCustomize: TextView
    private lateinit var tvTabSettings: TextView
    private lateinit var tvTabPermissions: TextView
    private lateinit var viewPermDot: View

    private lateinit var tvHomeServiceHint: TextView
    private lateinit var tvPermissionStatus: TextView
    private lateinit var tvAutostartStatus: TextView
    private lateinit var tvWriteSettingsStatus: TextView
    private lateinit var tvUsageAccessStatus: TextView
    private lateinit var btnOverlayPermission: Button
    private lateinit var btnAutostartPermission: Button
    private lateinit var btnWriteSettingsPermission: Button
    private lateinit var btnUsageAccessPermission: Button
    private lateinit var btnStartService: Button
    private lateinit var btnStopService: Button

    private lateinit var btnEditPresets: Button
    private lateinit var layoutPresetStatusRow: LinearLayout
    private lateinit var tvActiveRuleStatus: TextView
    private lateinit var tvPresetSyncBadge: TextView
    private lateinit var layoutPresetManage: LinearLayout
    private lateinit var tvEditingPresetLabel: TextView
    private lateinit var btnSaveCurrentToPreset: Button
    private lateinit var btnDeletePresetInline: Button
    private lateinit var etEditPresetName: EditText
    private lateinit var spinnerEditPresetShape: Spinner
    private lateinit var spinnerEditPresetIcon: Spinner
    private lateinit var etEditPresetOpacity: EditText
    private lateinit var etEditPresetMaxCap: EditText
    private lateinit var btnEditPresetColor: Button
    private lateinit var etNewPresetName: EditText
    private lateinit var btnAddPresetInline: Button
    private lateinit var presetContainer: LinearLayout
    private lateinit var btnResetPresets: Button
    private lateinit var btnExportBackup: Button
    private lateinit var btnImportBackup: Button

    private lateinit var tvCurrentAudioRoute: TextView
    private lateinit var btnDeviceOff: Button
    private lateinit var btnDeviceAuto: Button
    private lateinit var btnDeviceToast: Button
    private lateinit var layoutDevicePresets: LinearLayout
    private lateinit var spinnerHeadphonePreset: Spinner
    private lateinit var spinnerSpeakerPreset: Spinner

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchSwipeScrub: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchSingleTapSlider: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchSmoothFade: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchHideFullscreen: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchSnapToEdge: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchHoldRepeat: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchAutoBoot: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchLockPosition: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchRotateToast: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchOledPixelShift: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchAvoidSafeZones: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchBrightnessHandle: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchBrightnessInSlider: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchQuietHours: Switch

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private lateinit var switchPerAppRules: Switch

    private lateinit var seekBarBrightnessHeight: SeekBar
    private lateinit var tvBrightnessHeightValue: TextView

    private lateinit var spinnerGestureDoubleTap: Spinner
    private lateinit var spinnerGestureTripleTap: Spinner
    private lateinit var spinnerGestureInwardFlick: Spinner
    private lateinit var spinnerGestureLockedLongPress: Spinner

    private lateinit var btnQuietStartTime: Button
    private lateinit var btnQuietEndTime: Button
    private lateinit var spinnerQuietPreset: Spinner
    private lateinit var btnAddPerAppRule: Button
    private lateinit var perAppRulesContainer: LinearLayout

    private lateinit var radioGroupDisplayMode: RadioGroup
    private lateinit var radioGroupStream: RadioGroup
    private lateinit var radioGroupStepSize: RadioGroup
    private lateinit var radioGroupHapticStrength: RadioGroup
    private lateinit var radioGroupShape: RadioGroup
    private lateinit var radioGroupHandleIconStyle: RadioGroup
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
    private lateinit var previewSafeZoneBox: View
    private lateinit var previewBrightnessDot: View
    private lateinit var previewDot: View
    private lateinit var tvPreviewHint: TextView
    private lateinit var tvPosXLabel: TextView
    private lateinit var tvPosYLabel: TextView
    private lateinit var seekBarPosX: SeekBar
    private lateinit var seekBarPosY: SeekBar
    private lateinit var tvSavedPositions: TextView
    private lateinit var btnTestRotate: Button

    private var safeInsetLeft = 0
    private var safeInsetTop = 0
    private var safeInsetRight = 0
    private var safeInsetBottom = 0

    private var currentSlideIndex = 0
    private var presetEditorExpanded = false
    private var selectedEditPresetId: String? = null
    private var syncingInlinePresetEditor = false
    private var presetIdsCache = mutableListOf<String>()
    private var updatingColorSliders = false
    private var updatingPositionSliders = false
    private var syncingViews = false
    private var syncGeneration = 0
    private var suppressPrefReload = false
    private var lastSpinnerPresetSignature = ""

    private val inlineShapeIds = listOf(
        FloatingVolumeService.SHAPE_CIRCLE,
        FloatingVolumeService.SHAPE_LINE,
        FloatingVolumeService.SHAPE_EDGE_TAB
    )
    private val inlineShapeLabels = listOf("Circle", "Line", "Edge Tab")

    private val inlineIconIds = listOf(
        FloatingVolumeService.ICON_STYLE_ARROWS,
        FloatingVolumeService.ICON_STYLE_SPEAKER,
        FloatingVolumeService.ICON_STYLE_BARS,
        FloatingVolumeService.ICON_STYLE_DOT,
        FloatingVolumeService.ICON_STYLE_NONE
    )
    private val inlineIconLabels = listOf("Arrows", "Speaker", "Bars", "Dot", "Minimal")

    private val gestureActionIds = listOf(
        FloatingVolumeService.ACTION_ID_MUTE,
        FloatingVolumeService.ACTION_ID_PLAY_PAUSE,
        FloatingVolumeService.ACTION_ID_NEXT_TRACK,
        FloatingVolumeService.ACTION_ID_PREV_TRACK,
        FloatingVolumeService.ACTION_ID_MINI_SLIDER,
        FloatingVolumeService.ACTION_ID_TOGGLE_LOCK,
        FloatingVolumeService.ACTION_ID_TOGGLE_BRIGHTNESS,
        FloatingVolumeService.ACTION_ID_NONE
    )
    private val gestureActionLabels = listOf(
        "Mute / Unmute Stream",
        "Play / Pause Media",
        "Next Track",
        "Previous Track",
        "Toggle Mini Slider",
        "Lock / Unlock Position",
        "Toggle Brightness Handle",
        "Disabled (None)"
    )

    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeEligible = false
    private var swipeLockedAxis = 0 // 0 = undecided, 1 = horizontal slide swipe, 2 = vertical scroll
    private var touchSlopPx = 24

    private val audioRouteCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            uiHandler.postDelayed({
                updateAudioRouteBadge()
                updatePresetPriorityStatusRow()
            }, 150L)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            uiHandler.postDelayed({
                updateAudioRouteBadge()
                updatePresetPriorityStatusRow()
            }, 150L)
        }
    }

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

    private val writeSettingsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionUi()
    }

    private val usageAccessPermissionLauncher = registerForActivityResult(
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
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        touchSlopPx = ViewConfiguration.get(this).scaledTouchSlop

        val restoredSlide = savedInstanceState?.getInt(STATE_SLIDE_INDEX, 0) ?: 0
        presetEditorExpanded = savedInstanceState?.getBoolean(STATE_EDITOR_EXPANDED, false) ?: false
        selectedEditPresetId = savedInstanceState?.getString(STATE_EDIT_PRESET_ID)

        bindViews()
        setupRootWindowInsets()
        updateLandscapeChromeCompactness()
        layoutPresetManage.visibility = if (presetEditorExpanded) View.VISIBLE else View.GONE
        setupGestureSpinnerAdapters()
        ensureDefaultPositionsExist()
        ensurePresetsExist()
        applyThemeAwareButtonSkins()
        loadSavedSettingsIntoViews()
        setupListeners()
        selectSlide(restoredSlide, animate = false)
        updatePermissionUi()
        updateServiceButtonsUi()
        updateAudioRouteBadge()
    }

    private fun setupRootWindowInsets() {
        mainRootLayout.setOnApplyWindowInsetsListener { v, insets ->
            val l: Int
            val t: Int
            val r: Int
            val b: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sb = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                l = sb.left
                t = sb.top
                r = sb.right
                b = sb.bottom
            } else {
                val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) insets.displayCutout else null
                @Suppress("DEPRECATION")
                l = max(insets.systemWindowInsetLeft, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) (cutout?.safeInsetLeft ?: 0) else 0)
                @Suppress("DEPRECATION")
                t = max(insets.systemWindowInsetTop, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) (cutout?.safeInsetTop ?: 0) else 0)
                @Suppress("DEPRECATION")
                r = max(insets.systemWindowInsetRight, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) (cutout?.safeInsetRight ?: 0) else 0)
                @Suppress("DEPRECATION")
                b = max(insets.systemWindowInsetBottom, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) (cutout?.safeInsetBottom ?: 0) else 0)
            }
            safeInsetLeft = l
            safeInsetTop = t
            safeInsetRight = r
            safeInsetBottom = b
            v.setPadding(l, t, r, b)
            if (!updatingPositionSliders) {
                syncPositionSlidersForSelectedTarget()
            }
            insets
        }
        mainRootLayout.requestApplyInsets()
    }

    private fun updateLandscapeChromeCompactness() {
        val isLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val hPad = dpI(18)
        val topPad = dpI(if (isLand) 8 else 14)
        val botPad = dpI(if (isLand) 7 else 12)
        layoutActivityHeader.setPadding(hPad, topPad, hPad, botPad)
        val navBotPad = dpI(if (isLand) 4 else 8)
        listOf(tabHome, tabCustomize, tabSettings, tabPermissions).forEach { tab ->
            tab.setPadding(0, 0, 0, navBotPad)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SLIDE_INDEX, currentSlideIndex)
        outState.putBoolean(STATE_EDITOR_EXPANDED, presetEditorExpanded)
        outState.putString(STATE_EDIT_PRESET_ID, selectedEditPresetId)
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(this)
        audioManager.registerAudioDeviceCallback(audioRouteCallback, uiHandler)
        updatePermissionUi()
        updateServiceButtonsUi()
        updateAudioRouteBadge()
        updatePresetPriorityStatusRow()
        updateCurrentOrientationLabel()
        refreshSavedPositionsText()
        syncPositionSlidersForSelectedTarget()
    }

    override fun onPause() {
        super.onPause()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        audioManager.unregisterAudioDeviceCallback(audioRouteCallback)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            FloatingVolumeService.KEY_IS_SERVICE_RUNNING -> updateServiceButtonsUi()
            FloatingVolumeService.KEY_MANUAL_OVERRIDE,
            FloatingVolumeService.KEY_ACTIVE_APP_RULE_LABEL,
            KEY_ACTIVE_PRESET_ID -> updatePresetPriorityStatusRow()
            FloatingVolumeService.KEY_PRESET_APPLY_SEQ,
            FloatingVolumeService.KEY_AUDIO_DEVICE_MODE -> {
                if (!syncingViews && !suppressPrefReload) {
                    loadSavedSettingsIntoViews()
                } else {
                    updatePresetPriorityStatusRow()
                }
            }
            FloatingVolumeService.KEY_POS_X_PORTRAIT,
            FloatingVolumeService.KEY_POS_Y_PORTRAIT,
            FloatingVolumeService.KEY_POS_X_LANDSCAPE,
            FloatingVolumeService.KEY_POS_Y_LANDSCAPE -> {
                if (!updatingPositionSliders) {
                    refreshSavedPositionsText()
                    syncPositionSlidersForSelectedTarget()
                }
            }
            FloatingVolumeService.KEY_AVOID_SAFE_ZONES,
            FloatingVolumeService.KEY_BRIGHTNESS_HANDLE_ENABLED,
            FloatingVolumeService.KEY_BRIGHTNESS_COLOR,
            FloatingVolumeService.KEY_BRIGHTNESS_POS_X_PORTRAIT,
            FloatingVolumeService.KEY_BRIGHTNESS_POS_Y_PORTRAIT,
            FloatingVolumeService.KEY_BRIGHTNESS_POS_X_LANDSCAPE,
            FloatingVolumeService.KEY_BRIGHTNESS_POS_Y_LANDSCAPE -> {
                if (!updatingPositionSliders) {
                    syncPositionSlidersForSelectedTarget()
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLand = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        updateLandscapeChromeCompactness()
        updateCurrentOrientationLabel()
        syncingViews = true
        radioGroupOrientTarget.check(
            if (isLand) R.id.radioTargetLandscape else R.id.radioTargetPortrait
        )
        syncingViews = false
        syncPositionSlidersForSelectedTarget()
        refreshSavedPositionsText()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = ev.rawX
                swipeStartY = ev.rawY
                swipeLockedAxis = 0
                swipeEligible = isPointInsideView(slideViewport, ev.rawX.toInt(), ev.rawY.toInt()) &&
                    !isTouchInsideInteractiveHorizontalView(ev.rawX.toInt(), ev.rawY.toInt())
            }

            MotionEvent.ACTION_MOVE -> {
                if (swipeEligible) {
                    val dx = ev.rawX - swipeStartX
                    val dy = ev.rawY - swipeStartY
                    if (swipeLockedAxis == 0 && (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx)) {
                        swipeLockedAxis = if (abs(dx) > abs(dy) * 1.15f) 1 else 2
                        if (swipeLockedAxis == 1) {
                            val cancelEvent = MotionEvent.obtain(ev).apply {
                                action = MotionEvent.ACTION_CANCEL
                            }
                            super.dispatchTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                        }
                    }
                    if (swipeLockedAxis == 1) {
                        val activeView = slideViewForIndex(currentSlideIndex)
                        val clampedDrag = dx.coerceIn(-dpF(90f), dpF(90f))
                        activeView.translationX = clampedDrag * 0.35f
                        return true
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (swipeEligible && swipeLockedAxis == 1) {
                    val dx = ev.rawX - swipeStartX
                    val activeView = slideViewForIndex(currentSlideIndex)
                    activeView.animate().translationX(0f).setDuration(140L).start()

                    val minSwipePx = dpF(42f).coerceAtLeast(touchSlopPx * 1.8f)
                    if (ev.actionMasked == MotionEvent.ACTION_UP && abs(dx) >= minSwipePx) {
                        if (dx < 0 && currentSlideIndex < 3) {
                            selectSlide(currentSlideIndex + 1, direction = 1)
                        } else if (dx > 0 && currentSlideIndex > 0) {
                            selectSlide(currentSlideIndex - 1, direction = -1)
                        }
                    }
                    swipeEligible = false
                    swipeLockedAxis = 0
                    return true
                }
                swipeEligible = false
                swipeLockedAxis = 0
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isPointInsideView(v: View, rawX: Int, rawY: Int): Boolean {
        val rect = Rect()
        v.getGlobalVisibleRect(rect)
        return rect.contains(rawX, rawY)
    }

    private fun isTouchInsideInteractiveHorizontalView(rawX: Int, rawY: Int): Boolean {
        fun checkView(v: View?): Boolean {
            if (v == null || v.visibility != View.VISIBLE) return false
            if (v is SeekBar || v is HorizontalScrollView || v is Spinner) {
                val rect = Rect()
                v.getGlobalVisibleRect(rect)
                if (rect.contains(rawX, rawY)) return true
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    if (checkView(v.getChildAt(i))) return true
                }
            }
            return false
        }
        return checkView(slideViewport)
    }

    private fun slideViewForIndex(idx: Int): View {
        return when (idx) {
            1 -> slideCustomize
            2 -> slideSettings
            3 -> slidePermissions
            else -> slideHome
        }
    }

    private fun dpF(dp: Float): Float = dp * resources.displayMetrics.density
    private fun dpI(dp: Int): Int = (dp * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun isDarkModeActive(): Boolean {
        return prefs.getBoolean(FloatingVolumeService.KEY_DARK_THEME, true)
    }

    private fun makePillDrawable(
        fillHex: String,
        strokeHex: String? = null,
        strokeWidthDp: Int = 1,
        radiusDp: Float = 10f
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(radiusDp)
            setColor(Color.parseColor(fillHex))
            if (strokeHex != null) {
                setStroke(dpI(strokeWidthDp), Color.parseColor(strokeHex))
            }
        }
    }

    private fun bindViews() {
        mainRootLayout = findViewById(R.id.mainRootLayout)
        layoutActivityHeader = findViewById(R.id.layoutActivityHeader)
        layoutHeaderInfo = findViewById(R.id.layoutHeaderInfo)
        tvHeaderStatus = findViewById(R.id.tvHeaderStatus)
        btnThemeToggle = findViewById(R.id.btnThemeToggle)

        slideViewport = findViewById(R.id.slideViewport)
        slideHome = findViewById(R.id.slideHome)
        slideCustomize = findViewById(R.id.slideCustomize)
        slideSettings = findViewById(R.id.slideSettings)
        slidePermissions = findViewById(R.id.slidePermissions)

        bottomNavBar = findViewById(R.id.bottomNavBar)
        tabHome = findViewById(R.id.tabHome)
        tabCustomize = findViewById(R.id.tabCustomize)
        tabSettings = findViewById(R.id.tabSettings)
        tabPermissions = findViewById(R.id.tabPermissions)
        tabIndicatorHome = findViewById(R.id.tabIndicatorHome)
        tabIndicatorCustomize = findViewById(R.id.tabIndicatorCustomize)
        tabIndicatorSettings = findViewById(R.id.tabIndicatorSettings)
        tabIndicatorPermissions = findViewById(R.id.tabIndicatorPermissions)
        ivTabHome = findViewById(R.id.ivTabHome)
        ivTabCustomize = findViewById(R.id.ivTabCustomize)
        ivTabSettings = findViewById(R.id.ivTabSettings)
        ivTabPermissions = findViewById(R.id.ivTabPermissions)
        tvTabHome = findViewById(R.id.tvTabHome)
        tvTabCustomize = findViewById(R.id.tvTabCustomize)
        tvTabSettings = findViewById(R.id.tvTabSettings)
        tvTabPermissions = findViewById(R.id.tvTabPermissions)
        viewPermDot = findViewById(R.id.viewPermDot)

        tvHomeServiceHint = findViewById(R.id.tvHomeServiceHint)
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        tvAutostartStatus = findViewById(R.id.tvAutostartStatus)
        tvWriteSettingsStatus = findViewById(R.id.tvWriteSettingsStatus)
        tvUsageAccessStatus = findViewById(R.id.tvUsageAccessStatus)
        btnOverlayPermission = findViewById(R.id.btnOverlayPermission)
        btnAutostartPermission = findViewById(R.id.btnAutostartPermission)
        btnWriteSettingsPermission = findViewById(R.id.btnWriteSettingsPermission)
        btnUsageAccessPermission = findViewById(R.id.btnUsageAccessPermission)
        btnStartService = findViewById(R.id.btnStartService)
        btnStopService = findViewById(R.id.btnStopService)

        btnEditPresets = findViewById(R.id.btnEditPresets)
        layoutPresetStatusRow = findViewById(R.id.layoutPresetStatusRow)
        tvActiveRuleStatus = findViewById(R.id.tvActiveRuleStatus)
        tvPresetSyncBadge = findViewById(R.id.tvPresetSyncBadge)
        layoutPresetManage = findViewById(R.id.layoutPresetManage)
        tvEditingPresetLabel = findViewById(R.id.tvEditingPresetLabel)
        btnSaveCurrentToPreset = findViewById(R.id.btnSaveCurrentToPreset)
        btnDeletePresetInline = findViewById(R.id.btnDeletePresetInline)
        etEditPresetName = findViewById(R.id.etEditPresetName)
        spinnerEditPresetShape = findViewById(R.id.spinnerEditPresetShape)
        spinnerEditPresetIcon = findViewById(R.id.spinnerEditPresetIcon)
        etEditPresetOpacity = findViewById(R.id.etEditPresetOpacity)
        etEditPresetMaxCap = findViewById(R.id.etEditPresetMaxCap)
        btnEditPresetColor = findViewById(R.id.btnEditPresetColor)
        etNewPresetName = findViewById(R.id.etNewPresetName)
        btnAddPresetInline = findViewById(R.id.btnAddPresetInline)
        presetContainer = findViewById(R.id.presetContainer)
        btnResetPresets = findViewById(R.id.btnResetPresets)
        btnExportBackup = findViewById(R.id.btnExportBackup)
        btnImportBackup = findViewById(R.id.btnImportBackup)

        tvCurrentAudioRoute = findViewById(R.id.tvCurrentAudioRoute)
        btnDeviceOff = findViewById(R.id.btnDeviceOff)
        btnDeviceAuto = findViewById(R.id.btnDeviceAuto)
        btnDeviceToast = findViewById(R.id.btnDeviceToast)
        layoutDevicePresets = findViewById(R.id.layoutDevicePresets)
        spinnerHeadphonePreset = findViewById(R.id.spinnerHeadphonePreset)
        spinnerSpeakerPreset = findViewById(R.id.spinnerSpeakerPreset)

        switchSwipeScrub = findViewById(R.id.switchSwipeScrub)
        switchSingleTapSlider = findViewById(R.id.switchSingleTapSlider)
        switchSmoothFade = findViewById(R.id.switchSmoothFade)
        switchHideFullscreen = findViewById(R.id.switchHideFullscreen)
        switchSnapToEdge = findViewById(R.id.switchSnapToEdge)
        switchHoldRepeat = findViewById(R.id.switchHoldRepeat)
        switchAutoBoot = findViewById(R.id.switchAutoBoot)
        switchLockPosition = findViewById(R.id.switchLockPosition)
        switchRotateToast = findViewById(R.id.switchRotateToast)
        switchOledPixelShift = findViewById(R.id.switchOledPixelShift)
        switchAvoidSafeZones = findViewById(R.id.switchAvoidSafeZones)
        switchBrightnessHandle = findViewById(R.id.switchBrightnessHandle)
        switchBrightnessInSlider = findViewById(R.id.switchBrightnessInSlider)
        switchQuietHours = findViewById(R.id.switchQuietHours)
        switchPerAppRules = findViewById(R.id.switchPerAppRules)

        seekBarBrightnessHeight = findViewById(R.id.seekBarBrightnessHeight)
        tvBrightnessHeightValue = findViewById(R.id.tvBrightnessHeightValue)

        spinnerGestureDoubleTap = findViewById(R.id.spinnerGestureDoubleTap)
        spinnerGestureTripleTap = findViewById(R.id.spinnerGestureTripleTap)
        spinnerGestureInwardFlick = findViewById(R.id.spinnerGestureInwardFlick)
        spinnerGestureLockedLongPress = findViewById(R.id.spinnerGestureLockedLongPress)

        btnQuietStartTime = findViewById(R.id.btnQuietStartTime)
        btnQuietEndTime = findViewById(R.id.btnQuietEndTime)
        spinnerQuietPreset = findViewById(R.id.spinnerQuietPreset)
        btnAddPerAppRule = findViewById(R.id.btnAddPerAppRule)
        perAppRulesContainer = findViewById(R.id.perAppRulesContainer)

        radioGroupDisplayMode = findViewById(R.id.radioGroupDisplayMode)
        radioGroupStream = findViewById(R.id.radioGroupStream)
        radioGroupStepSize = findViewById(R.id.radioGroupStepSize)
        radioGroupHapticStrength = findViewById(R.id.radioGroupHapticStrength)
        radioGroupShape = findViewById(R.id.radioGroupShape)
        radioGroupHandleIconStyle = findViewById(R.id.radioGroupHandleIconStyle)
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
        previewSafeZoneBox = findViewById(R.id.previewSafeZoneBox)
        previewBrightnessDot = findViewById(R.id.previewBrightnessDot)
        previewDot = findViewById(R.id.previewDot)
        tvPreviewHint = findViewById(R.id.tvPreviewHint)
        tvPosXLabel = findViewById(R.id.tvPosXLabel)
        tvPosYLabel = findViewById(R.id.tvPosYLabel)
        seekBarPosX = findViewById(R.id.seekBarPosX)
        seekBarPosY = findViewById(R.id.seekBarPosY)
        tvSavedPositions = findViewById(R.id.tvSavedPositions)
        btnTestRotate = findViewById(R.id.btnTestRotate)
    }

    private fun applyThemeAwareButtonSkins() {
        val dark = isDarkModeActive()
        btnThemeToggle.text = if (dark) "Dark" else "Light"
        btnThemeToggle.background = makePillDrawable(
            fillHex = if (dark) "#1E293B" else "#FFFFFF",
            strokeHex = if (dark) "#334155" else "#CBD5E1",
            radiusDp = 8f
        )

        btnEditPresets.background = makePillDrawable(
            fillHex = if (presetEditorExpanded) "#2563EB" else if (dark) "#1E293B" else "#FFFFFF",
            strokeHex = if (presetEditorExpanded) "#3B82F6" else if (dark) "#334155" else "#CBD5E1",
            radiusDp = 8f
        )
        btnEditPresets.setTextColor(
            if (presetEditorExpanded) Color.WHITE
            else Color.parseColor(if (dark) "#E2E8F0" else "#334155")
        )
        btnEditPresets.text = if (presetEditorExpanded) "Done" else "Edit"

        val statusFill = if (dark) "#0F172A" else "#F8FAFC"
        val statusStroke = if (dark) "#1E293B" else "#E2E8F0"
        layoutPresetStatusRow.background = makePillDrawable(statusFill, statusStroke, radiusDp = 8f)

        val editorCardFill = if (dark) "#0B1220" else "#F1F5F9"
        val editorCardStroke = if (dark) "#334155" else "#CBD5E1"
        layoutPresetManage.background = makePillDrawable(editorCardFill, editorCardStroke, radiusDp = 10f)

        val inputFill = if (dark) "#0F172A" else "#FFFFFF"
        val inputStroke = if (dark) "#334155" else "#CBD5E1"
        etEditPresetName.background = makePillDrawable(inputFill, inputStroke, radiusDp = 8f)
        spinnerEditPresetShape.background = makePillDrawable(inputFill, inputStroke, radiusDp = 8f)
        spinnerEditPresetIcon.background = makePillDrawable(inputFill, inputStroke, radiusDp = 8f)
        etEditPresetOpacity.background = makePillDrawable(inputFill, inputStroke, radiusDp = 8f)
        etEditPresetMaxCap.background = makePillDrawable(inputFill, inputStroke, radiusDp = 8f)
        etNewPresetName.background = makePillDrawable(inputFill, inputStroke, radiusDp = 8f)

        btnSaveCurrentToPreset.background = makePillDrawable("#2563EB", "#3B82F6", radiusDp = 8f)
        btnDeletePresetInline.background = makePillDrawable(
            fillHex = if (dark) "#450A0A" else "#FEF2F2",
            strokeHex = "#EF4444",
            radiusDp = 8f
        )
        btnDeletePresetInline.setTextColor(Color.parseColor(if (dark) "#FCA5A5" else "#B91C1C"))
        btnAddPresetInline.background = makePillDrawable("#059669", "#10B981", radiusDp = 8f)
        btnResetPresets.background = makePillDrawable(
            fillHex = if (dark) "#1E293B" else "#FFFFFF",
            strokeHex = if (dark) "#334155" else "#CBD5E1",
            radiusDp = 8f
        )
        syncInlinePresetEditorViews(preserveFocusedInputs = true)

        btnAddPerAppRule.background = makePillDrawable("#2563EB", "#3B82F6", radiusDp = 8f)
        btnQuietStartTime.background = makePillDrawable(
            fillHex = if (dark) "#1E293B" else "#FFFFFF",
            strokeHex = if (dark) "#334155" else "#CBD5E1",
            radiusDp = 8f
        )
        btnQuietEndTime.background = makePillDrawable(
            fillHex = if (dark) "#1E293B" else "#FFFFFF",
            strokeHex = if (dark) "#334155" else "#CBD5E1",
            radiusDp = 8f
        )

        btnOverlayPermission.background = makePillDrawable("#2563EB", "#3B82F6", radiusDp = 10f)
        btnAutostartPermission.background = makePillDrawable("#0F766E", "#14B8A6", radiusDp = 10f)
        btnWriteSettingsPermission.background = makePillDrawable("#B45309", "#F59E0B", radiusDp = 10f)
        btnUsageAccessPermission.background = makePillDrawable("#4338CA", "#6366F1", radiusDp = 10f)

        viewPermDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#F59E0B"))
        }
    }

    private fun selectSlide(index: Int, animate: Boolean = true, direction: Int = 0) {
        val prevIndex = currentSlideIndex
        currentSlideIndex = index.coerceIn(0, 3)

        val slides = listOf(slideHome, slideCustomize, slideSettings, slidePermissions)
        slides.forEachIndexed { idx, view ->
            if (idx == currentSlideIndex) {
                view.visibility = View.VISIBLE
                view.translationX = 0f
                if (animate && idx != prevIndex) {
                    val shiftDir = if (direction != 0) direction else if (currentSlideIndex > prevIndex) 1 else -1
                    view.translationX = dpF(28f * shiftDir)
                    view.alpha = 0.65f
                    view.animate().translationX(0f).alpha(1f).setDuration(150L).start()
                } else {
                    view.alpha = 1f
                }
            } else {
                view.visibility = View.GONE
            }
        }

        val dark = isDarkModeActive()
        val activeColor = Color.parseColor("#3B82F6")
        val inactiveColor = Color.parseColor(if (dark) "#94A3B8" else "#64748B")

        val indicators = listOf(tabIndicatorHome, tabIndicatorCustomize, tabIndicatorSettings, tabIndicatorPermissions)
        val icons = listOf(ivTabHome, ivTabCustomize, ivTabSettings, ivTabPermissions)
        val labels = listOf(tvTabHome, tvTabCustomize, tvTabSettings, tvTabPermissions)

        for (i in 0..3) {
            val selected = i == currentSlideIndex
            indicators[i].setBackgroundColor(if (selected) activeColor else Color.TRANSPARENT)
            val tint = if (selected) activeColor else inactiveColor
            icons[i].setColorFilter(tint)
            labels[i].setTextColor(tint)
            labels[i].setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun updateAudioDeviceModeUi(mode: String) {
        val dark = isDarkModeActive()
        val pairs = listOf(
            btnDeviceOff to FloatingVolumeService.DEVICE_MODE_OFF,
            btnDeviceAuto to FloatingVolumeService.DEVICE_MODE_AUTO,
            btnDeviceToast to FloatingVolumeService.DEVICE_MODE_TOAST
        )
        pairs.forEach { (btn, key) ->
            val selected = mode == key
            btn.background = makePillDrawable(
                fillHex = if (selected) (if (dark) "#172554" else "#EFF6FF") else (if (dark) "#1E293B" else "#FFFFFF"),
                strokeHex = if (selected) "#3B82F6" else (if (dark) "#334155" else "#CBD5E1"),
                strokeWidthDp = if (selected) 2 else 1,
                radiusDp = 10f
            )
            btn.setTextColor(
                Color.parseColor(
                    if (selected) (if (dark) "#93C5FD" else "#1D4ED8")
                    else (if (dark) "#E2E8F0" else "#334155")
                )
            )
        }
        layoutDevicePresets.visibility =
            if (mode == FloatingVolumeService.DEVICE_MODE_OFF) View.GONE else View.VISIBLE
    }

    private fun isExternalAudioConnectedLocal(): Boolean {
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

    private fun parseMinutesLocal(hhMm: String, fallback: Int): Int {
        val parts = hhMm.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return fallback
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return fallback
        return (h.coerceIn(0, 23) * 60) + m.coerceIn(0, 59)
    }

    private fun isQuietHoursActiveLocal(): Boolean {
        if (!prefs.getBoolean(FloatingVolumeService.KEY_QUIET_HOURS_ENABLED, false)) return false
        val cal = java.util.Calendar.getInstance()
        val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val startMin = parseMinutesLocal(
            prefs.getString(FloatingVolumeService.KEY_QUIET_START, "22:30") ?: "22:30",
            22 * 60 + 30
        )
        val endMin = parseMinutesLocal(
            prefs.getString(FloatingVolumeService.KEY_QUIET_END, "07:00") ?: "07:00",
            7 * 60
        )
        return if (startMin <= endMin) {
            nowMin in startMin until endMin
        } else {
            nowMin >= startMin || nowMin < endMin
        }
    }

    private fun updatePresetPriorityStatusRow() {
        val quietActive = isQuietHoursActiveLocal()
        val perAppEnabled = prefs.getBoolean(FloatingVolumeService.KEY_PER_APP_RULES_ENABLED, false)
        val activeAppLabel = if (perAppEnabled) {
            prefs.getString(FloatingVolumeService.KEY_ACTIVE_APP_RULE_LABEL, "") ?: ""
        } else ""
        val deviceMode = prefs.getString(
            FloatingVolumeService.KEY_AUDIO_DEVICE_MODE,
            FloatingVolumeService.DEFAULT_AUDIO_DEVICE_MODE
        ) ?: FloatingVolumeService.DEVICE_MODE_OFF
        val headphonesActive = deviceMode != FloatingVolumeService.DEVICE_MODE_OFF && isExternalAudioConnectedLocal()

        val ruleText = when {
            quietActive -> "Quiet Hours Active"
            activeAppLabel.isNotEmpty() -> "App Rule: $activeAppLabel"
            headphonesActive -> "Headphone Rule Active"
            else -> "Manual / Custom"
        }
        tvActiveRuleStatus.text = ruleText

        val manualOverride = prefs.getBoolean(FloatingVolumeService.KEY_MANUAL_OVERRIDE, false)
        val activeId = prefs.getString(KEY_ACTIVE_PRESET_ID, "") ?: ""
        when {
            manualOverride -> {
                tvPresetSyncBadge.text = "Manual Override"
                tvPresetSyncBadge.setTextColor(Color.parseColor("#F59E0B"))
            }
            activeId == "custom" || activeId.isEmpty() -> {
                tvPresetSyncBadge.text = "Custom"
                tvPresetSyncBadge.setTextColor(Color.parseColor("#60A5FA"))
            }
            else -> {
                tvPresetSyncBadge.text = "Synced"
                tvPresetSyncBadge.setTextColor(Color.parseColor("#34D399"))
            }
        }
    }

    private fun updateAudioRouteBadge() {
        val hasExternal = isExternalAudioConnectedLocal()
        tvCurrentAudioRoute.text = if (hasExternal) "Headphones" else "Speaker"
        tvCurrentAudioRoute.setTextColor(
            Color.parseColor(if (hasExternal) "#38BDF8" else if (isDarkModeActive()) "#94A3B8" else "#64748B")
        )
    }

    private fun updateServiceButtonsUi() {
        val running = prefs.getBoolean(FloatingVolumeService.KEY_IS_SERVICE_RUNNING, false)
        val dark = isDarkModeActive()
        if (running) {
            btnStartService.text = "●  Floating Service Active"
            btnStartService.background = makePillDrawable(
                fillHex = if (dark) "#064E3B" else "#ECFDF5",
                strokeHex = "#10B981",
                radiusDp = 10f
            )
            btnStartService.setTextColor(Color.parseColor(if (dark) "#6EE7B7" else "#047857"))
            btnStopService.visibility = View.VISIBLE
            btnStopService.background = makePillDrawable(
                fillHex = if (dark) "#450A0A" else "#FEF2F2",
                strokeHex = "#EF4444",
                radiusDp = 10f
            )
            btnStopService.setTextColor(Color.parseColor(if (dark) "#FCA5A5" else "#B91C1C"))
        } else {
            btnStartService.text = "▶  Start Floating Service"
            btnStartService.background = makePillDrawable("#2563EB", "#3B82F6", radiusDp = 10f)
            btnStartService.setTextColor(Color.WHITE)
            btnStopService.visibility = View.GONE
        }
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
                hapticStrength = "medium",
                snapToEdge = true,
                gestureDoubleTap = "none",
                gestureTripleTap = "none",
                gestureInwardFlick = "mini_slider",
                gestureLockedLongPress = "none",
                holdToRepeat = true,
                autoDimSec = 5,
                dimOpacityPct = 15,
                volumeStep = 1,
                maxVolumeLimitPct = 100,
                swipeScrub = true,
                popoutMode = "swipe_inward",
                overlayDisplayMode = "minimal",
                handleIconStyle = "bars",
                smoothFadeAnimation = true,
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
                hapticStrength = "light",
                snapToEdge = true,
                gestureDoubleTap = "mute",
                gestureTripleTap = "play_pause",
                gestureInwardFlick = "mini_slider",
                gestureLockedLongPress = "toggle_lock",
                holdToRepeat = true,
                autoDimSec = 3,
                dimOpacityPct = 5,
                volumeStep = 1,
                maxVolumeLimitPct = 60,
                swipeScrub = true,
                popoutMode = "swipe_inward",
                overlayDisplayMode = "percent_badge",
                handleIconStyle = "arrows",
                smoothFadeAnimation = true,
                hideInFullscreen = false
            ))
            put(buildPresetJson(
                id = "stealth",
                name = "Stealth",
                widthDp = 58,
                heightDp = 124,
                transparencyPct = 12,
                shape = "line",
                colorHex = "#2563EB",
                audioStream = "music",
                hapticTap = true,
                hapticStrength = "light",
                snapToEdge = true,
                gestureDoubleTap = "none",
                gestureTripleTap = "play_pause",
                gestureInwardFlick = "mini_slider",
                gestureLockedLongPress = "none",
                holdToRepeat = true,
                autoDimSec = 0,
                dimOpacityPct = 0,
                volumeStep = 1,
                maxVolumeLimitPct = 100,
                swipeScrub = true,
                popoutMode = "swipe_inward",
                overlayDisplayMode = "minimal",
                handleIconStyle = "dot",
                smoothFadeAnimation = false,
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
        hapticStrength: String,
        snapToEdge: Boolean,
        gestureDoubleTap: String,
        gestureTripleTap: String,
        gestureInwardFlick: String,
        gestureLockedLongPress: String,
        holdToRepeat: Boolean,
        autoDimSec: Int,
        dimOpacityPct: Int,
        volumeStep: Int,
        maxVolumeLimitPct: Int,
        swipeScrub: Boolean,
        popoutMode: String,
        overlayDisplayMode: String,
        handleIconStyle: String,
        smoothFadeAnimation: Boolean,
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
            put("hapticStrength", hapticStrength)
            put("snapToEdge", snapToEdge)
            put("doubleTapMute", gestureDoubleTap != FloatingVolumeService.ACTION_ID_NONE)
            put("tripleTapPlayPause", gestureTripleTap != FloatingVolumeService.ACTION_ID_NONE)
            put("gestureActions", JSONObject().apply {
                put("doubleTap", gestureDoubleTap)
                put("tripleTap", gestureTripleTap)
                put("inwardFlick", gestureInwardFlick)
                put("lockedLongPress", gestureLockedLongPress)
            })
            put("holdToRepeat", holdToRepeat)
            put("autoDimSeconds", autoDimSec)
            put("dimOpacityPct", dimOpacityPct)
            put("volumeStep", volumeStep)
            put("maxVolumeLimitPct", maxVolumeLimitPct)
            put("swipeScrub", swipeScrub)
            put("popoutSliderMode", popoutMode)
            put("overlayDisplayMode", overlayDisplayMode)
            put("handleIconStyle", handleIconStyle)
            put("smoothFadeAnimation", smoothFadeAnimation)
            put("hideInFullscreen", hideInFullscreen)
        }
    }

    private fun buildDefaultPerAppRulesArray(): JSONArray {
        return JSONArray().apply {
            put(JSONObject().apply {
                put("id", "rule_youtube")
                put("appLabel", "YouTube")
                put("packageName", "com.google.android.youtube")
                put("presetId", "gaming")
                put("enabled", true)
                put("targetVolumePct", -1)
                put("restoreVolumeOnExit", true)
            })
            put(JSONObject().apply {
                put("id", "rule_spotify")
                put("appLabel", "Spotify")
                put("packageName", "com.spotify.music")
                put("presetId", "bedtime")
                put("enabled", false)
                put("targetVolumePct", -1)
                put("restoreVolumeOnExit", true)
            })
        }
    }

    private fun ensurePresetsExist() {
        val editor = prefs.edit()
        var dirty = false
        if (!prefs.contains(FloatingVolumeService.KEY_PRESETS_JSON)) {
            editor
                .putString(FloatingVolumeService.KEY_PRESETS_JSON, buildDefaultPresetsArray().toString())
                .putString(FloatingVolumeService.KEY_HEADPHONE_PRESET_ID, "bedtime")
                .putString(FloatingVolumeService.KEY_SPEAKER_PRESET_ID, "gaming")
            dirty = true
        } else {
            val existing = getSavedPresetsArray()
            var migrated = false
            for (i in 0 until existing.length()) {
                val obj = existing.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                if (id == "stealth" && obj.optInt("transparencyPct", 12) == 0) {
                    obj.put("transparencyPct", 12)
                    migrated = true
                }
                if (obj.optJSONObject("gestureActions") == null) {
                    val doubleAct = if (obj.optBoolean("doubleTapMute", false)) FloatingVolumeService.ACTION_ID_MUTE else FloatingVolumeService.ACTION_ID_NONE
                    val tripleAct = if (obj.optBoolean("tripleTapPlayPause", false)) FloatingVolumeService.ACTION_ID_PLAY_PAUSE else FloatingVolumeService.ACTION_ID_NONE
                    val lockedAct = if (id == "bedtime") FloatingVolumeService.ACTION_ID_TOGGLE_LOCK else FloatingVolumeService.ACTION_ID_NONE
                    obj.put("gestureActions", JSONObject().apply {
                        put("doubleTap", doubleAct)
                        put("tripleTap", tripleAct)
                        put("inwardFlick", FloatingVolumeService.ACTION_ID_MINI_SLIDER)
                        put("lockedLongPress", lockedAct)
                    })
                    migrated = true
                }
                if (!obj.has("handleIconStyle") || obj.optString("handleIconStyle", "").isEmpty()) {
                    val inferredIcon = when (id) {
                        "gaming" -> FloatingVolumeService.ICON_STYLE_BARS
                        "stealth" -> FloatingVolumeService.ICON_STYLE_DOT
                        else -> FloatingVolumeService.ICON_STYLE_ARROWS
                    }
                    obj.put("handleIconStyle", inferredIcon)
                    migrated = true
                }
            }
            if (migrated) {
                editor.putString(FloatingVolumeService.KEY_PRESETS_JSON, existing.toString())
                dirty = true
            }
        }
        if (!prefs.contains(FloatingVolumeService.KEY_PER_APP_RULES_JSON)) {
            editor.putString(FloatingVolumeService.KEY_PER_APP_RULES_JSON, buildDefaultPerAppRulesArray().toString())
            dirty = true
        }
        if (dirty) editor.apply()
    }

    private fun getSavedPresetsArray(): JSONArray {
        val raw = prefs.getString(FloatingVolumeService.KEY_PRESETS_JSON, null)
        return try {
            if (raw.isNullOrBlank()) buildDefaultPresetsArray() else JSONArray(raw)
        } catch (_: Exception) {
            buildDefaultPresetsArray()
        }
    }

    private fun savePresetsArray(arr: JSONArray) {
        prefs.edit().putString(FloatingVolumeService.KEY_PRESETS_JSON, arr.toString()).apply()
        renderPresetButtons()
        refreshDevicePresetSpinners()
        renderPerAppRules()
    }

    private fun cleanupDeletedPresetReferences(removedId: String, fallbackId: String) {
        if (removedId.isEmpty() || fallbackId.isEmpty()) return
        val editor = prefs.edit()
        var dirty = false
        if (prefs.getString(FloatingVolumeService.KEY_HEADPHONE_PRESET_ID, "") == removedId) {
            editor.putString(FloatingVolumeService.KEY_HEADPHONE_PRESET_ID, fallbackId)
            dirty = true
        }
        if (prefs.getString(FloatingVolumeService.KEY_SPEAKER_PRESET_ID, "") == removedId) {
            editor.putString(FloatingVolumeService.KEY_SPEAKER_PRESET_ID, fallbackId)
            dirty = true
        }
        if (prefs.getString(FloatingVolumeService.KEY_QUIET_PRESET_ID, "") == removedId) {
            editor.putString(FloatingVolumeService.KEY_QUIET_PRESET_ID, fallbackId)
            dirty = true
        }
        val rulesArr = getSavedPerAppRulesArray()
        var rulesChanged = false
        for (i in 0 until rulesArr.length()) {
            val r = rulesArr.optJSONObject(i) ?: continue
            if (r.optString("presetId") == removedId) {
                r.put("presetId", fallbackId)
                rulesChanged = true
            }
        }
        if (rulesChanged) {
            editor.putString(FloatingVolumeService.KEY_PER_APP_RULES_JSON, rulesArr.toString())
            dirty = true
        }
        if (dirty) editor.apply()
    }

    private fun getSavedPerAppRulesArray(): JSONArray {
        val raw = prefs.getString(FloatingVolumeService.KEY_PER_APP_RULES_JSON, null)
        return try {
            if (raw.isNullOrBlank()) buildDefaultPerAppRulesArray() else JSONArray(raw)
        } catch (_: Exception) {
            buildDefaultPerAppRulesArray()
        }
    }

    private fun savePerAppRulesArray(arr: JSONArray) {
        prefs.edit().putString(FloatingVolumeService.KEY_PER_APP_RULES_JSON, arr.toString()).apply()
        renderPerAppRules()
    }

    private fun findPresetNameById(presetId: String): String {
        val arr = getSavedPresetsArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("id") == presetId) return obj.optString("name", presetId)
        }
        return presetId
    }

    private fun renderPerAppRules() {
        perAppRulesContainer.removeAllViews()
        val arr = getSavedPerAppRulesArray()
        val dark = isDarkModeActive()
        for (i in 0 until arr.length()) {
            val rule = arr.optJSONObject(i) ?: continue
            val label = rule.optString("appLabel", "App")
            val pkgName = rule.optString("packageName", "")
            val presetId = rule.optString("presetId", "")
            val enabled = rule.optBoolean("enabled", true)
            val targetVolPct = rule.optInt("targetVolumePct", -1)
            val restoreOnExit = rule.optBoolean("restoreVolumeOnExit", true)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dpI(10), dpI(8), dpI(10), dpI(8))
                background = makePillDrawable(
                    fillHex = if (dark) "#1E293B" else "#FFFFFF",
                    strokeHex = if (dark) "#334155" else "#CBD5E1",
                    radiusDp = 8f
                )
            }
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    showEditPerAppRuleDialog(i, rule)
                }
            }
            val volBadge = if (targetVolPct in 0..100) {
                " · Vol $targetVolPct%${if (restoreOnExit) " ↺" else ""}"
            } else {
                ""
            }
            val titleTv = TextView(this).apply {
                text = "$label → ${findPresetNameById(presetId)}$volBadge"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(if (dark) "#F8FAFC" else "#0F172A"))
            }
            val subTv = TextView(this).apply {
                text = "$pkgName · Tap to edit"
                textSize = 10f
                setTextColor(Color.parseColor(if (dark) "#94A3B8" else "#64748B"))
            }
            textCol.addView(titleTv)
            textCol.addView(subTv)
            row.addView(textCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val toggleBtn = Button(this).apply {
                text = if (enabled) "ON" else "OFF"
                textSize = 10f
                isAllCaps = false
                setSingleLine(true)
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(dpI(8), dpI(4), dpI(8), dpI(4))
                background = makePillDrawable(
                    fillHex = if (enabled) "#2563EB" else (if (dark) "#0F172A" else "#E2E8F0"),
                    strokeHex = if (enabled) "#3B82F6" else "#475569",
                    radiusDp = 6f
                )
                setTextColor(if (enabled || dark) Color.WHITE else Color.parseColor("#334155"))
                setOnClickListener {
                    val updated = getSavedPerAppRulesArray()
                    updated.optJSONObject(i)?.put("enabled", !enabled)
                    savePerAppRulesArray(updated)
                }
            }
            val delBtn = Button(this).apply {
                text = "✕"
                textSize = 10f
                isAllCaps = false
                setSingleLine(true)
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(dpI(8), dpI(4), dpI(8), dpI(4))
                background = makePillDrawable(
                    fillHex = if (dark) "#450A0A" else "#FEF2F2",
                    strokeHex = "#EF4444",
                    radiusDp = 6f
                )
                setTextColor(Color.parseColor(if (dark) "#FCA5A5" else "#B91C1C"))
                setOnClickListener {
                    val current = getSavedPerAppRulesArray()
                    val next = JSONArray()
                    for (j in 0 until current.length()) {
                        if (j != i) next.put(current.get(j))
                    }
                    savePerAppRulesArray(next)
                }
            }
            row.addView(toggleBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dpI(6)
            })
            row.addView(delBtn)
            perAppRulesContainer.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dpI(6)
            })
        }
    }

    private fun showAddPerAppRuleDialog() {
        showPerAppRuleDialog(editIndex = null, existing = null)
    }

    private fun showEditPerAppRuleDialog(index: Int, existing: JSONObject) {
        showPerAppRuleDialog(editIndex = index, existing = existing)
    }

    private fun showPerAppRuleDialog(editIndex: Int?, existing: JSONObject?) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpI(18), dpI(12), dpI(18), dpI(4))
        }
        val etLabel = EditText(this).apply {
            hint = "App label (e.g. VLC Player)"
            setText(existing?.optString("appLabel", "") ?: "")
        }
        val etPkg = EditText(this).apply {
            hint = "Package ID (e.g. org.videolan.vlc)"
            setText(existing?.optString("packageName", "") ?: "")
        }
        val spPreset = Spinner(this)
        val arr = getSavedPresetsArray()
        val names = mutableListOf<String>()
        val ids = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            ids.add(obj.optString("id", "preset_$i"))
            names.add(obj.optString("name", "Preset"))
        }
        spPreset.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val existingPresetId = existing?.optString("presetId", "") ?: ""
        val selectedPresetIdx = ids.indexOf(existingPresetId).let { if (it >= 0) it else 0 }
        spPreset.setSelection(selectedPresetIdx, false)

        val existingVol = existing?.optInt("targetVolumePct", -1) ?: -1
        val etTargetVol = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Target volume % (optional, 0-100)"
            if (existingVol in 0..100) {
                setText(existingVol.toString())
            }
        }
        val cbRestoreOnExit = CheckBox(this).apply {
            text = "Restore previous volume on app exit"
            isChecked = existing?.optBoolean("restoreVolumeOnExit", true) ?: true
        }

        box.addView(etLabel)
        box.addView(etPkg)
        box.addView(spPreset)
        box.addView(etTargetVol)
        box.addView(cbRestoreOnExit)

        AlertDialog.Builder(this)
            .setTitle(if (editIndex == null) "Add Per-App Preset Rule" else "Edit Per-App Rule")
            .setView(box)
            .setPositiveButton(if (editIndex == null) "Add Rule" else "Save Rule") { _, _ ->
                val pkgName = etPkg.text.toString().trim()
                if (pkgName.isEmpty()) {
                    Toast.makeText(this, "Enter an Android package name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val label = etLabel.text.toString().trim().ifEmpty { pkgName.substringAfterLast('.') }
                val presetId = ids.getOrElse(spPreset.selectedItemPosition) { ids.firstOrNull() ?: "gaming" }
                val rawVol = etTargetVol.text.toString().trim()
                val parsedVol = if (rawVol.isEmpty()) -1 else (rawVol.toIntOrNull()?.coerceIn(0, 100) ?: -1)
                val current = getSavedPerAppRulesArray()
                val ruleJson = JSONObject().apply {
                    put("id", existing?.optString("id", "")?.takeIf { it.isNotEmpty() } ?: "rule_${System.currentTimeMillis()}")
                    put("appLabel", label)
                    put("packageName", pkgName)
                    put("presetId", presetId)
                    put("enabled", existing?.optBoolean("enabled", true) ?: true)
                    put("targetVolumePct", parsedVol)
                    put("restoreVolumeOnExit", cbRestoreOnExit.isChecked)
                }
                if (editIndex != null && editIndex in 0 until current.length()) {
                    current.put(editIndex, ruleJson)
                } else {
                    current.put(ruleJson)
                }
                savePerAppRulesArray(current)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun renderPresetButtons() {
        presetContainer.removeAllViews()
        val arr = getSavedPresetsArray()
        val activeId = prefs.getString(KEY_ACTIVE_PRESET_ID, "")
        val dark = isDarkModeActive()
        val marginPx = dpI(8)
        val hPad = dpI(14)
        val vPad = dpI(8)

        for (i in 0 until arr.length()) {
            val presetObj = arr.optJSONObject(i) ?: continue
            val id = presetObj.optString("id", "preset_$i")
            val name = presetObj.optString("name", "Preset")
            val selected = id == activeId

            val btn = Button(this).apply {
                text = name
                textSize = 12f
                isAllCaps = false
                setSingleLine(true)
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(hPad, vPad, hPad, vPad)
                background = makePillDrawable(
                    fillHex = if (selected) (if (dark) "#172554" else "#EFF6FF") else (if (dark) "#1E293B" else "#FFFFFF"),
                    strokeHex = if (selected) "#3B82F6" else (if (dark) "#334155" else "#CBD5E1"),
                    strokeWidthDp = if (selected) 2 else 1,
                    radiusDp = 10f
                )
                setTextColor(
                    Color.parseColor(
                        if (selected) (if (dark) "#93C5FD" else "#1D4ED8")
                        else (if (dark) "#E2E8F0" else "#334155")
                    )
                )
                setOnClickListener {
                    selectedEditPresetId = id
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

    private fun resolveEditedPresetEntry(): Pair<Int, JSONObject>? {
        val arr = getSavedPresetsArray()
        if (arr.length() == 0) return null
        val activeId = prefs.getString(KEY_ACTIVE_PRESET_ID, "") ?: ""
        val targetId = selectedEditPresetId?.takeIf { it.isNotEmpty() }
            ?: activeId.takeIf { it.isNotEmpty() && it != "custom" }
        if (targetId != null) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optString("id") == targetId) {
                    selectedEditPresetId = targetId
                    return i to obj
                }
            }
        }
        val first = arr.optJSONObject(0) ?: return null
        selectedEditPresetId = first.optString("id", "preset_0")
        return 0 to first
    }

    private fun syncInlinePresetEditorViews(preserveFocusedInputs: Boolean = false) {
        val arr = getSavedPresetsArray()
        val entry = resolveEditedPresetEntry() ?: return
        val preset = entry.second
        val name = preset.optString("name", "Preset")
        val shape = preset.optString("shape", FloatingVolumeService.SHAPE_CIRCLE)
        val iconStyle = preset.optString("handleIconStyle", FloatingVolumeService.DEFAULT_HANDLE_ICON_STYLE)
        val opacity = preset.optInt("transparencyPct", FloatingVolumeService.DEFAULT_TRANSPARENCY_PCT).coerceIn(0, 100)
        val maxCap = preset.optInt("maxVolumeLimitPct", 100).coerceIn(30, 100)
        val colorHex = preset.optString("colorHex", "#2563EB")
        val parsedColor = try { Color.parseColor(colorHex) } catch (_: Exception) { FloatingVolumeService.DEFAULT_COLOR }

        syncingInlinePresetEditor = true
        tvEditingPresetLabel.text = "Editing: $name"
        btnDeletePresetInline.visibility = if (arr.length() > 1) View.VISIBLE else View.GONE

        if (!(preserveFocusedInputs && etEditPresetName.hasFocus())) {
            if (etEditPresetName.text.toString() != name) {
                etEditPresetName.setText(name)
            }
        }
        val shapeIdx = inlineShapeIds.indexOf(shape).let { if (it >= 0) it else 0 }
        if (spinnerEditPresetShape.selectedItemPosition != shapeIdx) {
            spinnerEditPresetShape.setSelection(shapeIdx, false)
        }
        val iconIdx = inlineIconIds.indexOf(iconStyle).let { if (it >= 0) it else 0 }
        if (spinnerEditPresetIcon.selectedItemPosition != iconIdx) {
            spinnerEditPresetIcon.setSelection(iconIdx, false)
        }
        if (!(preserveFocusedInputs && etEditPresetOpacity.hasFocus())) {
            val opStr = opacity.toString()
            if (etEditPresetOpacity.text.toString() != opStr) {
                etEditPresetOpacity.setText(opStr)
            }
        }
        if (!(preserveFocusedInputs && etEditPresetMaxCap.hasFocus())) {
            val capStr = maxCap.toString()
            if (etEditPresetMaxCap.text.toString() != capStr) {
                etEditPresetMaxCap.setText(capStr)
            }
        }
        val dark = isDarkModeActive()
        btnEditPresetColor.background = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            cornerRadius = dpF(8f)
            setColor(parsedColor)
            setStroke(dpI(1), Color.parseColor(if (dark) "#64748B" else "#94A3B8"))
        }
        btnEditPresetColor.text = ""
        spinnerEditPresetShape.post { syncingInlinePresetEditor = false }
    }

    private fun updateEditedPresetInPlace(
        applyLiveToOverlay: Boolean,
        preserveFocusedInputs: Boolean = true,
        mutator: (JSONObject) -> Unit
    ) {
        val arr = getSavedPresetsArray()
        val entry = resolveEditedPresetEntry() ?: return
        val idx = entry.first
        val obj = arr.optJSONObject(idx) ?: return
        val id = obj.optString("id", "preset_$idx")
        mutator(obj)
        arr.put(idx, obj)

        prefs.edit().putString(FloatingVolumeService.KEY_PRESETS_JSON, arr.toString()).apply()
        renderPresetButtons()
        refreshDevicePresetSpinners()
        renderPerAppRules()
        tvEditingPresetLabel.text = "Editing: ${obj.optString("name", "Preset")}"

        if (applyLiveToOverlay) {
            val colorStr = obj.optString("colorHex", "#2563EB")
            val parsedColor = try { Color.parseColor(colorStr) } catch (_: Exception) { FloatingVolumeService.DEFAULT_COLOR }
            val shape = obj.optString("shape", FloatingVolumeService.SHAPE_CIRCLE)
            val iconStyle = obj.optString("handleIconStyle", FloatingVolumeService.DEFAULT_HANDLE_ICON_STYLE)
            val opacity = obj.optInt("transparencyPct", FloatingVolumeService.DEFAULT_TRANSPARENCY_PCT).coerceIn(0, 100)
            val maxCap = obj.optInt("maxVolumeLimitPct", 100).coerceIn(30, 100)
            val nextSeq = prefs.getInt(FloatingVolumeService.KEY_PRESET_APPLY_SEQ, 0) + 1

            suppressPrefReload = true
            prefs.edit()
                .putString(KEY_ACTIVE_PRESET_ID, id)
                .putString(FloatingVolumeService.KEY_SHAPE, shape)
                .putString(FloatingVolumeService.KEY_HANDLE_ICON_STYLE, iconStyle)
                .putInt(FloatingVolumeService.KEY_TRANSPARENCY_PCT, opacity)
                .putInt(FloatingVolumeService.KEY_MAX_VOLUME_LIMIT_PCT, maxCap)
                .putInt(FloatingVolumeService.KEY_OVERLAY_COLOR, parsedColor)
                .putInt(FloatingVolumeService.KEY_PRESET_APPLY_SEQ, nextSeq)
                .apply()
            suppressPrefReload = false

            if (shape == FloatingVolumeService.SHAPE_EDGE_TAB) {
                snapAllOrientationsToEdgeIfNeeded(forceSnap = true)
            }
            syncLiveEditFieldsToCustomizeAndSettings(shape, iconStyle, opacity, maxCap, parsedColor)
            renderPresetButtons()
            updatePresetPriorityStatusRow()
        }
        if (!preserveFocusedInputs) {
            syncInlinePresetEditorViews(preserveFocusedInputs = false)
        }
    }

    private fun syncLiveEditFieldsToCustomizeAndSettings(
        shape: String,
        iconStyle: String,
        opacity: Int,
        maxCap: Int,
        colorInt: Int
    ) {
        syncingViews = true
        val myGen = ++syncGeneration
        radioGroupShape.check(
            when (shape) {
                FloatingVolumeService.SHAPE_LINE -> R.id.radioLine
                FloatingVolumeService.SHAPE_EDGE_TAB -> R.id.radioEdgeTab
                else -> R.id.radioCircle
            }
        )
        radioGroupHandleIconStyle.check(
            when (iconStyle) {
                FloatingVolumeService.ICON_STYLE_SPEAKER -> R.id.radioIconSpeaker
                FloatingVolumeService.ICON_STYLE_BARS -> R.id.radioIconBars
                FloatingVolumeService.ICON_STYLE_DOT -> R.id.radioIconDot
                FloatingVolumeService.ICON_STYLE_NONE -> R.id.radioIconNone
                else -> R.id.radioIconArrows
            }
        )
        seekBarTransparency.progress = (opacity - MIN_ALPHA_PCT).coerceIn(0, MAX_ALPHA_PCT - MIN_ALPHA_PCT)
        tvTransparencyValue.text = "$opacity%"
        seekBarMaxLimit.progress = (maxCap - 30).coerceIn(0, 70)
        tvMaxLimitValue.text = "$maxCap%"
        applyColorToPickerViews(colorInt)
        spinnerQuietPreset.post {
            if (myGen == syncGeneration) syncingViews = false
        }
    }

    private fun showInlinePresetColorDialog(initialColorInt: Int, onPicked: (String) -> Unit) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpI(20), dpI(14), dpI(20), dpI(8))
        }
        var currentColor = initialColorInt
        val preview = View(this).apply {
            setBackgroundColor(currentColor)
        }
        container.addView(preview, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpI(34)).apply {
            bottomMargin = dpI(12)
        })

        val swatchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val swatches = listOf("#2563EB", "#10B981", "#F59E0B", "#E11D48", "#06B6D4", "#F8FAFC")
        lateinit var sbR: SeekBar
        lateinit var sbG: SeekBar
        lateinit var sbB: SeekBar
        var updatingBars = false

        swatches.forEachIndexed { i, hex ->
            val swatchColor = Color.parseColor(hex)
            val btn = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(swatchColor)
                    setStroke(dpI(2), Color.parseColor("#94A3B8"))
                }
                setOnClickListener {
                    currentColor = swatchColor
                    preview.setBackgroundColor(currentColor)
                    updatingBars = true
                    sbR.progress = Color.red(currentColor)
                    sbG.progress = Color.green(currentColor)
                    sbB.progress = Color.blue(currentColor)
                    updatingBars = false
                }
            }
            swatchRow.addView(btn, LinearLayout.LayoutParams(dpI(32), dpI(32)).apply {
                if (i < swatches.lastIndex) marginEnd = dpI(10)
            })
        }
        container.addView(swatchRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dpI(12)
        })

        fun makeChannelBar(label: String, initial: Int): SeekBar {
            container.addView(TextView(this).apply {
                text = label
                textSize = 11f
            })
            val sb = SeekBar(this).apply {
                max = 255
                progress = initial
            }
            container.addView(sb)
            return sb
        }
        sbR = makeChannelBar("Red", Color.red(currentColor))
        sbG = makeChannelBar("Green", Color.green(currentColor))
        sbB = makeChannelBar("Blue", Color.blue(currentColor))

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (updatingBars || !fromUser) return
                currentColor = Color.rgb(sbR.progress, sbG.progress, sbB.progress)
                preview.setBackgroundColor(currentColor)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        sbR.setOnSeekBarChangeListener(listener)
        sbG.setOnSeekBarChangeListener(listener)
        sbB.setOnSeekBarChangeListener(listener)

        AlertDialog.Builder(this)
            .setTitle("Preset Color")
            .setView(container)
            .setPositiveButton("Apply") { _, _ ->
                onPicked(String.format("#%06X", 0xFFFFFF and currentColor))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupGestureSpinnerAdapters() {
        val actionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, gestureActionLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerGestureDoubleTap.adapter = actionAdapter
        spinnerGestureTripleTap.adapter = actionAdapter
        spinnerGestureInwardFlick.adapter = actionAdapter
        spinnerGestureLockedLongPress.adapter = actionAdapter

        val shapeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, inlineShapeLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerEditPresetShape.adapter = shapeAdapter

        val iconAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, inlineIconLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerEditPresetIcon.adapter = iconAdapter
    }

    private fun refreshDevicePresetSpinners() {
        val arr = getSavedPresetsArray()
        val names = mutableListOf<String>()
        presetIdsCache.clear()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            presetIdsCache.add(obj.optString("id", "preset_$i"))
            names.add(obj.optString("name", "Preset"))
        }
        if (names.isEmpty()) return

        val sig = presetIdsCache.joinToString("|") + "::" + names.joinToString("|")
        if (sig != lastSpinnerPresetSignature) {
            lastSpinnerPresetSignature = sig
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinnerHeadphonePreset.adapter = adapter
            spinnerSpeakerPreset.adapter = adapter
            spinnerQuietPreset.adapter = adapter
        }

        val hpId = prefs.getString(FloatingVolumeService.KEY_HEADPHONE_PRESET_ID, "bedtime")
        val spId = prefs.getString(FloatingVolumeService.KEY_SPEAKER_PRESET_ID, "gaming")
        val qId = prefs.getString(FloatingVolumeService.KEY_QUIET_PRESET_ID, "bedtime")
        val hpIdx = presetIdsCache.indexOf(hpId).let { if (it >= 0) it else 0 }
        val spIdx = presetIdsCache.indexOf(spId).let { if (it >= 0) it else 0 }
        val qIdx = presetIdsCache.indexOf(qId).let { if (it >= 0) it else 0 }
        spinnerHeadphonePreset.setSelection(hpIdx, false)
        spinnerSpeakerPreset.setSelection(spIdx, false)
        spinnerQuietPreset.setSelection(qIdx, false)
    }

    private fun applyPresetObject(obj: JSONObject) {
        val colorStr = obj.optString("colorHex", "#2563EB")
        val parsedColor = try { Color.parseColor(colorStr) } catch (_: Exception) { FloatingVolumeService.DEFAULT_COLOR }
        val hapticStrength = obj.optString(
            "hapticStrength",
            if (obj.optBoolean("hapticTap", true)) FloatingVolumeService.HAPTIC_MEDIUM else FloatingVolumeService.HAPTIC_OFF
        )
        val gestures = obj.optJSONObject("gestureActions")
        val gDouble = gestures?.optString("doubleTap")?.takeIf { it.isNotEmpty() }
            ?: if (obj.optBoolean("doubleTapMute", false)) FloatingVolumeService.ACTION_ID_MUTE else FloatingVolumeService.ACTION_ID_NONE
        val gTriple = gestures?.optString("tripleTap")?.takeIf { it.isNotEmpty() }
            ?: if (obj.optBoolean("tripleTapPlayPause", false)) FloatingVolumeService.ACTION_ID_PLAY_PAUSE else FloatingVolumeService.ACTION_ID_NONE
        val gFlick = gestures?.optString("inwardFlick")?.takeIf { it.isNotEmpty() }
            ?: FloatingVolumeService.ACTION_ID_MINI_SLIDER
        val gLocked = gestures?.optString("lockedLongPress")?.takeIf { it.isNotEmpty() }
            ?: FloatingVolumeService.ACTION_ID_NONE
        val nextSeq = prefs.getInt(FloatingVolumeService.KEY_PRESET_APPLY_SEQ, 0) + 1
        val presetId = obj.optString("id", "")
        val shape = obj.optString("shape", FloatingVolumeService.SHAPE_CIRCLE)
        val deviceMode = prefs.getString(
            FloatingVolumeService.KEY_AUDIO_DEVICE_MODE,
            FloatingVolumeService.DEFAULT_AUDIO_DEVICE_MODE
        ) ?: FloatingVolumeService.DEVICE_MODE_OFF
        val anyRuleActive = isQuietHoursActiveLocal() ||
            (prefs.getBoolean(FloatingVolumeService.KEY_PER_APP_RULES_ENABLED, false) &&
                !prefs.getString(FloatingVolumeService.KEY_ACTIVE_APP_RULE_LABEL, "").isNullOrEmpty()) ||
            (deviceMode == FloatingVolumeService.DEVICE_MODE_AUTO && isExternalAudioConnectedLocal())

        if (presetId.isNotEmpty() && presetId != "custom") {
            selectedEditPresetId = presetId
        }

        suppressPrefReload = true
        prefs.edit()
            .putString(KEY_ACTIVE_PRESET_ID, presetId)
            .putBoolean(FloatingVolumeService.KEY_MANUAL_OVERRIDE, anyRuleActive)
            .putInt(FloatingVolumeService.KEY_PRESET_APPLY_SEQ, nextSeq)
            .putInt(FloatingVolumeService.KEY_WIDTH_DP, obj.optInt("widthDp", FloatingVolumeService.DEFAULT_WIDTH_DP))
            .putInt(FloatingVolumeService.KEY_HEIGHT_DP, obj.optInt("heightDp", FloatingVolumeService.DEFAULT_HEIGHT_DP))
            .putInt(FloatingVolumeService.KEY_TRANSPARENCY_PCT, obj.optInt("transparencyPct", FloatingVolumeService.DEFAULT_TRANSPARENCY_PCT))
            .putString(FloatingVolumeService.KEY_SHAPE, shape)
            .putInt(FloatingVolumeService.KEY_OVERLAY_COLOR, parsedColor)
            .putString(FloatingVolumeService.KEY_AUDIO_STREAM, obj.optString("audioStream", FloatingVolumeService.STREAM_MUSIC))
            .putBoolean(FloatingVolumeService.KEY_HAPTIC_TAP, hapticStrength != FloatingVolumeService.HAPTIC_OFF)
            .putString(FloatingVolumeService.KEY_HAPTIC_STRENGTH, hapticStrength)
            .putString(FloatingVolumeService.KEY_GESTURE_DOUBLE_TAP, gDouble)
            .putString(FloatingVolumeService.KEY_GESTURE_TRIPLE_TAP, gTriple)
            .putString(FloatingVolumeService.KEY_GESTURE_INWARD_FLICK, gFlick)
            .putString(FloatingVolumeService.KEY_GESTURE_LOCKED_LONG_PRESS, gLocked)
            .putBoolean(FloatingVolumeService.KEY_DOUBLE_TAP_MUTE, gDouble != FloatingVolumeService.ACTION_ID_NONE)
            .putBoolean(FloatingVolumeService.KEY_TRIPLE_TAP_MEDIA, gTriple != FloatingVolumeService.ACTION_ID_NONE)
            .putBoolean(FloatingVolumeService.KEY_HOLD_TO_REPEAT, obj.optBoolean("holdToRepeat", true))
            .putInt(FloatingVolumeService.KEY_AUTO_DIM_SEC, obj.optInt("autoDimSeconds", 0))
            .putInt(FloatingVolumeService.KEY_DIM_OPACITY_PCT, obj.optInt("dimOpacityPct", 15))
            .putInt(FloatingVolumeService.KEY_VOLUME_STEP, obj.optInt("volumeStep", 1))
            .putInt(FloatingVolumeService.KEY_MAX_VOLUME_LIMIT_PCT, obj.optInt("maxVolumeLimitPct", 100))
            .putBoolean(FloatingVolumeService.KEY_SWIPE_SCRUB, obj.optBoolean("swipeScrub", true))
            .putString(FloatingVolumeService.KEY_POPOUT_MODE, obj.optString("popoutSliderMode", FloatingVolumeService.POPOUT_SWIPE_INWARD))
            .putString(FloatingVolumeService.KEY_DISPLAY_MODE, obj.optString("overlayDisplayMode", FloatingVolumeService.DISPLAY_MINIMAL))
            .putString(FloatingVolumeService.KEY_HANDLE_ICON_STYLE, obj.optString("handleIconStyle", FloatingVolumeService.DEFAULT_HANDLE_ICON_STYLE))
            .putBoolean(FloatingVolumeService.KEY_SMOOTH_FADE, obj.optBoolean("smoothFadeAnimation", true))
            .putBoolean(FloatingVolumeService.KEY_HIDE_FULLSCREEN, obj.optBoolean("hideInFullscreen", false))
            .apply()
        suppressPrefReload = false

        if (shape == FloatingVolumeService.SHAPE_EDGE_TAB) {
            snapAllOrientationsToEdgeIfNeeded(forceSnap = true)
        }
        loadSavedSettingsIntoViews()
        Toast.makeText(this, "Applied ${obj.optString("name", "Preset")}", Toast.LENGTH_SHORT).show()
    }

    private fun captureCurrentSettingsAsPreset(id: String, name: String): JSONObject {
        val colorInt = prefs.getInt(FloatingVolumeService.KEY_OVERLAY_COLOR, FloatingVolumeService.DEFAULT_COLOR)
        val colorHex = String.format("#%06X", 0xFFFFFF and colorInt)
        val hapticStrength = prefs.getString(
            FloatingVolumeService.KEY_HAPTIC_STRENGTH,
            FloatingVolumeService.DEFAULT_HAPTIC_STRENGTH
        ) ?: FloatingVolumeService.HAPTIC_MEDIUM
        return buildPresetJson(
            id = id,
            name = name,
            widthDp = prefs.getInt(FloatingVolumeService.KEY_WIDTH_DP, FloatingVolumeService.DEFAULT_WIDTH_DP),
            heightDp = prefs.getInt(FloatingVolumeService.KEY_HEIGHT_DP, FloatingVolumeService.DEFAULT_HEIGHT_DP),
            transparencyPct = prefs.getInt(FloatingVolumeService.KEY_TRANSPARENCY_PCT, FloatingVolumeService.DEFAULT_TRANSPARENCY_PCT),
            shape = prefs.getString(FloatingVolumeService.KEY_SHAPE, FloatingVolumeService.SHAPE_CIRCLE) ?: FloatingVolumeService.SHAPE_CIRCLE,
            colorHex = colorHex,
            audioStream = prefs.getString(FloatingVolumeService.KEY_AUDIO_STREAM, FloatingVolumeService.DEFAULT_STREAM) ?: FloatingVolumeService.STREAM_MUSIC,
            hapticTap = hapticStrength != FloatingVolumeService.HAPTIC_OFF,
            hapticStrength = hapticStrength,
            snapToEdge = prefs.getBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, false),
            gestureDoubleTap = prefs.getString(FloatingVolumeService.KEY_GESTURE_DOUBLE_TAP, FloatingVolumeService.DEFAULT_GESTURE_DOUBLE_TAP) ?: FloatingVolumeService.ACTION_ID_NONE,
            gestureTripleTap = prefs.getString(FloatingVolumeService.KEY_GESTURE_TRIPLE_TAP, FloatingVolumeService.DEFAULT_GESTURE_TRIPLE_TAP) ?: FloatingVolumeService.ACTION_ID_NONE,
            gestureInwardFlick = prefs.getString(FloatingVolumeService.KEY_GESTURE_INWARD_FLICK, FloatingVolumeService.DEFAULT_GESTURE_INWARD_FLICK) ?: FloatingVolumeService.ACTION_ID_MINI_SLIDER,
            gestureLockedLongPress = prefs.getString(FloatingVolumeService.KEY_GESTURE_LOCKED_LONG_PRESS, FloatingVolumeService.DEFAULT_GESTURE_LOCKED_LONG_PRESS) ?: FloatingVolumeService.ACTION_ID_NONE,
            holdToRepeat = prefs.getBoolean(FloatingVolumeService.KEY_HOLD_TO_REPEAT, true),
            autoDimSec = prefs.getInt(FloatingVolumeService.KEY_AUTO_DIM_SEC, 0),
            dimOpacityPct = prefs.getInt(FloatingVolumeService.KEY_DIM_OPACITY_PCT, 15),
            volumeStep = prefs.getInt(FloatingVolumeService.KEY_VOLUME_STEP, 1),
            maxVolumeLimitPct = prefs.getInt(FloatingVolumeService.KEY_MAX_VOLUME_LIMIT_PCT, 100),
            swipeScrub = prefs.getBoolean(FloatingVolumeService.KEY_SWIPE_SCRUB, true),
            popoutMode = prefs.getString(FloatingVolumeService.KEY_POPOUT_MODE, FloatingVolumeService.POPOUT_SWIPE_INWARD) ?: FloatingVolumeService.POPOUT_SWIPE_INWARD,
            overlayDisplayMode = prefs.getString(FloatingVolumeService.KEY_DISPLAY_MODE, FloatingVolumeService.DISPLAY_MINIMAL) ?: FloatingVolumeService.DISPLAY_MINIMAL,
            handleIconStyle = prefs.getString(FloatingVolumeService.KEY_HANDLE_ICON_STYLE, FloatingVolumeService.DEFAULT_HANDLE_ICON_STYLE) ?: FloatingVolumeService.DEFAULT_HANDLE_ICON_STYLE,
            smoothFadeAnimation = prefs.getBoolean(FloatingVolumeService.KEY_SMOOTH_FADE, true),
            hideInFullscreen = prefs.getBoolean(FloatingVolumeService.KEY_HIDE_FULLSCREEN, false)
        )
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
                selectedEditPresetId = currentId
                val nextSeq = prefs.getInt(FloatingVolumeService.KEY_PRESET_APPLY_SEQ, 0) + 1
                suppressPrefReload = true
                prefs.edit()
                    .putString(KEY_ACTIVE_PRESET_ID, currentId)
                    .putInt(FloatingVolumeService.KEY_PRESET_APPLY_SEQ, nextSeq)
                    .apply()
                suppressPrefReload = false
                savePresetsArray(arr)
                syncInlinePresetEditorViews(preserveFocusedInputs = false)
                updatePresetPriorityStatusRow()
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
                val fallbackId = nextArr.optJSONObject(0)?.optString("id", "") ?: ""
                if (selectedEditPresetId == currentId) {
                    selectedEditPresetId = fallbackId
                }
                if (prefs.getString(KEY_ACTIVE_PRESET_ID, "") == currentId) {
                    prefs.edit().putString(KEY_ACTIVE_PRESET_ID, "custom").apply()
                }
                cleanupDeletedPresetReferences(currentId, fallbackId)
                savePresetsArray(nextArr)
                syncInlinePresetEditorViews(preserveFocusedInputs = false)
                updatePresetPriorityStatusRow()
                Toast.makeText(this, "Deleted $currentName", Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
    }

    private fun loadSavedSettingsIntoViews() {
        syncingViews = true
        val myGen = ++syncGeneration
        renderPresetButtons()

        val deviceMode = prefs.getString(
            FloatingVolumeService.KEY_AUDIO_DEVICE_MODE,
            FloatingVolumeService.DEFAULT_AUDIO_DEVICE_MODE
        ) ?: FloatingVolumeService.DEVICE_MODE_OFF
        updateAudioDeviceModeUi(deviceMode)

        val displayMode = prefs.getString(
            FloatingVolumeService.KEY_DISPLAY_MODE,
            FloatingVolumeService.DEFAULT_DISPLAY_MODE
        )
        radioGroupDisplayMode.check(
            when (displayMode) {
                FloatingVolumeService.DISPLAY_PERCENT_BADGE -> R.id.radioDisplayBadge
                FloatingVolumeService.DISPLAY_SLIDER_FOCUS -> R.id.radioDisplaySliderFocus
                else -> R.id.radioDisplayMinimal
            }
        )

        val hapticStrength = prefs.getString(
            FloatingVolumeService.KEY_HAPTIC_STRENGTH,
            FloatingVolumeService.DEFAULT_HAPTIC_STRENGTH
        )
        radioGroupHapticStrength.check(
            when (hapticStrength) {
                FloatingVolumeService.HAPTIC_OFF -> R.id.radioHapticOff
                FloatingVolumeService.HAPTIC_LIGHT -> R.id.radioHapticLight
                FloatingVolumeService.HAPTIC_STRONG -> R.id.radioHapticStrong
                else -> R.id.radioHapticMedium
            }
        )

        switchSwipeScrub.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_SWIPE_SCRUB, false)
        val popoutMode = prefs.getString(
            FloatingVolumeService.KEY_POPOUT_MODE,
            "swipe_inward"
        )
        switchSingleTapSlider.isChecked = popoutMode == FloatingVolumeService.POPOUT_SINGLE_TAP
        switchSmoothFade.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_SMOOTH_FADE, true)
        switchHideFullscreen.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_HIDE_FULLSCREEN, false)
        switchSnapToEdge.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, true)
        switchHoldRepeat.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_HOLD_TO_REPEAT, true)
        switchAutoBoot.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_AUTO_BOOT, false)
        switchLockPosition.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_POSITION_LOCKED, false)
        switchRotateToast.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_ROTATE_TOAST, true)
        switchOledPixelShift.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_OLED_PIXEL_SHIFT, false)
        switchAvoidSafeZones.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_AVOID_SAFE_ZONES, FloatingVolumeService.DEFAULT_AVOID_SAFE_ZONES)
        switchBrightnessHandle.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_BRIGHTNESS_HANDLE_ENABLED, false)
        switchBrightnessInSlider.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_SHOW_BRIGHTNESS_IN_SLIDER, true)
        switchQuietHours.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_QUIET_HOURS_ENABLED, false)
        switchPerAppRules.isChecked =
            prefs.getBoolean(FloatingVolumeService.KEY_PER_APP_RULES_ENABLED, false)

        val bHeightDp = prefs.getInt(
            FloatingVolumeService.KEY_BRIGHTNESS_HEIGHT_DP,
            FloatingVolumeService.DEFAULT_BRIGHTNESS_HEIGHT_DP
        ).coerceIn(48, 160)
        seekBarBrightnessHeight.progress = bHeightDp - 48
        tvBrightnessHeightValue.text = "$bHeightDp dp"

        btnQuietStartTime.text = "Start: ${prefs.getString(FloatingVolumeService.KEY_QUIET_START, "22:30")}"
        btnQuietEndTime.text = "End: ${prefs.getString(FloatingVolumeService.KEY_QUIET_END, "07:00")}"

        fun idxForAction(key: String, fallback: String): Int {
            val saved = prefs.getString(key, fallback) ?: fallback
            return gestureActionIds.indexOf(saved).let { if (it >= 0) it else 0 }
        }
        spinnerGestureDoubleTap.setSelection(idxForAction(FloatingVolumeService.KEY_GESTURE_DOUBLE_TAP, "none"), false)
        spinnerGestureTripleTap.setSelection(idxForAction(FloatingVolumeService.KEY_GESTURE_TRIPLE_TAP, "none"), false)
        spinnerGestureInwardFlick.setSelection(idxForAction(FloatingVolumeService.KEY_GESTURE_INWARD_FLICK, "mini_slider"), false)
        spinnerGestureLockedLongPress.setSelection(idxForAction(FloatingVolumeService.KEY_GESTURE_LOCKED_LONG_PRESS, "none"), false)

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
            when (shape) {
                FloatingVolumeService.SHAPE_LINE -> R.id.radioLine
                FloatingVolumeService.SHAPE_EDGE_TAB -> R.id.radioEdgeTab
                else -> R.id.radioCircle
            }
        )

        val handleIconStyle = prefs.getString(
            FloatingVolumeService.KEY_HANDLE_ICON_STYLE,
            FloatingVolumeService.DEFAULT_HANDLE_ICON_STYLE
        )
        radioGroupHandleIconStyle.check(
            when (handleIconStyle) {
                FloatingVolumeService.ICON_STYLE_SPEAKER -> R.id.radioIconSpeaker
                FloatingVolumeService.ICON_STYLE_BARS -> R.id.radioIconBars
                FloatingVolumeService.ICON_STYLE_DOT -> R.id.radioIconDot
                FloatingVolumeService.ICON_STYLE_NONE -> R.id.radioIconNone
                else -> R.id.radioIconArrows
            }
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
        refreshDevicePresetSpinners()
        renderPerAppRules()
        updatePresetPriorityStatusRow()
        syncInlinePresetEditorViews(preserveFocusedInputs = true)
        spinnerQuietPreset.post {
            if (myGen == syncGeneration) {
                syncingViews = false
            }
        }
    }

    private fun markCustomPresetAndApply(applyBlock: (SharedPreferences.Editor) -> Unit) {
        suppressPrefReload = true
        val editor = prefs.edit()
        val wasPreset = prefs.getString(KEY_ACTIVE_PRESET_ID, "") != "custom"
        if (wasPreset) {
            editor.putString(KEY_ACTIVE_PRESET_ID, "custom")
        }
        applyBlock(editor)
        editor.apply()
        suppressPrefReload = false
        if (wasPreset) {
            renderPresetButtons()
            updatePresetPriorityStatusRow()
        }
    }

    private fun setupListeners() {
        tabHome.setOnClickListener { selectSlide(0) }
        tabCustomize.setOnClickListener { selectSlide(1) }
        tabSettings.setOnClickListener { selectSlide(2) }
        tabPermissions.setOnClickListener { selectSlide(3) }
        layoutHeaderInfo.setOnClickListener { selectSlide(3) }

        btnThemeToggle.setOnClickListener {
            val nextDark = !isDarkModeActive()
            prefs.edit().putBoolean(FloatingVolumeService.KEY_DARK_THEME, nextDark).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (nextDark) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        btnEditPresets.setOnClickListener {
            presetEditorExpanded = !presetEditorExpanded
            layoutPresetManage.visibility = if (presetEditorExpanded) View.VISIBLE else View.GONE
            if (presetEditorExpanded) {
                syncInlinePresetEditorViews(preserveFocusedInputs = false)
            }
            applyThemeAwareButtonSkins()
        }

        btnSaveCurrentToPreset.setOnClickListener {
            val entry = resolveEditedPresetEntry() ?: return@setOnClickListener
            val idx = entry.first
            val existing = entry.second
            val currentId = existing.optString("id", "preset_$idx")
            val currentName = etEditPresetName.text.toString().trim().ifEmpty {
                existing.optString("name", "Preset")
            }
            val arr = getSavedPresetsArray()
            val updated = captureCurrentSettingsAsPreset(currentId, currentName)
            arr.put(idx, updated)
            val nextSeq = prefs.getInt(FloatingVolumeService.KEY_PRESET_APPLY_SEQ, 0) + 1
            suppressPrefReload = true
            prefs.edit()
                .putString(KEY_ACTIVE_PRESET_ID, currentId)
                .putInt(FloatingVolumeService.KEY_PRESET_APPLY_SEQ, nextSeq)
                .apply()
            suppressPrefReload = false
            savePresetsArray(arr)
            syncInlinePresetEditorViews(preserveFocusedInputs = false)
            updatePresetPriorityStatusRow()
            Toast.makeText(this, "Saved current settings to $currentName", Toast.LENGTH_SHORT).show()
        }

        btnDeletePresetInline.setOnClickListener {
            val arr = getSavedPresetsArray()
            if (arr.length() <= 1) return@setOnClickListener
            val entry = resolveEditedPresetEntry() ?: return@setOnClickListener
            val removedIdx = entry.first
            val removedName = entry.second.optString("name", "Preset")
            val removedId = entry.second.optString("id", "")
            val nextArr = JSONArray()
            for (i in 0 until arr.length()) {
                if (i != removedIdx) nextArr.put(arr.get(i))
            }
            val fallbackId = nextArr.optJSONObject(0)?.optString("id", "") ?: ""
            selectedEditPresetId = fallbackId
            if (prefs.getString(KEY_ACTIVE_PRESET_ID, "") == removedId) {
                prefs.edit().putString(KEY_ACTIVE_PRESET_ID, "custom").apply()
            }
            cleanupDeletedPresetReferences(removedId, fallbackId)
            savePresetsArray(nextArr)
            syncInlinePresetEditorViews(preserveFocusedInputs = false)
            updatePresetPriorityStatusRow()
            Toast.makeText(this, "Deleted $removedName", Toast.LENGTH_SHORT).show()
        }

        etEditPresetName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (syncingInlinePresetEditor) return
                val nextName = s?.toString() ?: ""
                if (nextName.trim().isEmpty()) return
                updateEditedPresetInPlace(applyLiveToOverlay = false, preserveFocusedInputs = true) { obj ->
                    obj.put("name", nextName.trim())
                }
            }
        })

        spinnerEditPresetShape.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (syncingInlinePresetEditor || position !in inlineShapeIds.indices) return
                val nextShape = inlineShapeIds[position]
                updateEditedPresetInPlace(applyLiveToOverlay = true, preserveFocusedInputs = true) { obj ->
                    obj.put("shape", nextShape)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinnerEditPresetIcon.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (syncingInlinePresetEditor || position !in inlineIconIds.indices) return
                val nextIcon = inlineIconIds[position]
                updateEditedPresetInPlace(applyLiveToOverlay = true, preserveFocusedInputs = true) { obj ->
                    obj.put("handleIconStyle", nextIcon)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        fun commitOpacityInput(clearFocusAfter: Boolean) {
            val clamped = (etEditPresetOpacity.text.toString().trim().toIntOrNull() ?: 80).coerceIn(0, 100)
            syncingInlinePresetEditor = true
            if (etEditPresetOpacity.text.toString() != clamped.toString()) {
                etEditPresetOpacity.setText(clamped.toString())
                etEditPresetOpacity.setSelection(etEditPresetOpacity.text.length)
            }
            syncingInlinePresetEditor = false
            updateEditedPresetInPlace(applyLiveToOverlay = true, preserveFocusedInputs = !clearFocusAfter) { obj ->
                obj.put("transparencyPct", clamped)
            }
            if (clearFocusAfter) etEditPresetOpacity.clearFocus()
        }

        etEditPresetOpacity.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (syncingInlinePresetEditor) return
                val raw = s?.toString()?.trim() ?: return
                val valInt = raw.toIntOrNull() ?: return
                if (valInt !in 0..100) return
                updateEditedPresetInPlace(applyLiveToOverlay = true, preserveFocusedInputs = true) { obj ->
                    obj.put("transparencyPct", valInt)
                }
            }
        })
        etEditPresetOpacity.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitOpacityInput(clearFocusAfter = true)
                true
            } else false
        }
        etEditPresetOpacity.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !syncingInlinePresetEditor) {
                commitOpacityInput(clearFocusAfter = false)
            }
        }

        fun commitMaxCapInput(clearFocusAfter: Boolean) {
            val clamped = (etEditPresetMaxCap.text.toString().trim().toIntOrNull() ?: 100).coerceIn(30, 100)
            syncingInlinePresetEditor = true
            if (etEditPresetMaxCap.text.toString() != clamped.toString()) {
                etEditPresetMaxCap.setText(clamped.toString())
                etEditPresetMaxCap.setSelection(etEditPresetMaxCap.text.length)
            }
            syncingInlinePresetEditor = false
            updateEditedPresetInPlace(applyLiveToOverlay = true, preserveFocusedInputs = !clearFocusAfter) { obj ->
                obj.put("maxVolumeLimitPct", clamped)
            }
            if (clearFocusAfter) etEditPresetMaxCap.clearFocus()
        }

        etEditPresetMaxCap.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (syncingInlinePresetEditor) return
                val raw = s?.toString()?.trim() ?: return
                val valInt = raw.toIntOrNull() ?: return
                if (valInt !in 30..100) return
                updateEditedPresetInPlace(applyLiveToOverlay = true, preserveFocusedInputs = true) { obj ->
                    obj.put("maxVolumeLimitPct", valInt)
                }
            }
        })
        etEditPresetMaxCap.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                commitMaxCapInput(clearFocusAfter = true)
                true
            } else false
        }
        etEditPresetMaxCap.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !syncingInlinePresetEditor) {
                commitMaxCapInput(clearFocusAfter = false)
            }
        }

        btnEditPresetColor.setOnClickListener {
            val entry = resolveEditedPresetEntry() ?: return@setOnClickListener
            val hex = entry.second.optString("colorHex", "#2563EB")
            val parsed = try { Color.parseColor(hex) } catch (_: Exception) { FloatingVolumeService.DEFAULT_COLOR }
            showInlinePresetColorDialog(parsed) { pickedHex ->
                updateEditedPresetInPlace(applyLiveToOverlay = true, preserveFocusedInputs = false) { obj ->
                    obj.put("colorHex", pickedHex)
                }
                syncInlinePresetEditorViews(preserveFocusedInputs = false)
            }
        }

        btnAddPresetInline.setOnClickListener {
            val arr = getSavedPresetsArray()
            val name = etNewPresetName.text.toString().trim().ifEmpty { "Custom ${arr.length() + 1}" }
            val newId = "custom_${System.currentTimeMillis()}"
            arr.put(captureCurrentSettingsAsPreset(newId, name))
            selectedEditPresetId = newId
            val nextSeq = prefs.getInt(FloatingVolumeService.KEY_PRESET_APPLY_SEQ, 0) + 1
            suppressPrefReload = true
            prefs.edit()
                .putString(KEY_ACTIVE_PRESET_ID, newId)
                .putInt(FloatingVolumeService.KEY_PRESET_APPLY_SEQ, nextSeq)
                .apply()
            suppressPrefReload = false
            etNewPresetName.setText("")
            savePresetsArray(arr)
            syncInlinePresetEditorViews(preserveFocusedInputs = false)
            updatePresetPriorityStatusRow()
            Toast.makeText(this, "Added $name", Toast.LENGTH_SHORT).show()
        }

        btnDeviceOff.setOnClickListener {
            prefs.edit().putString(FloatingVolumeService.KEY_AUDIO_DEVICE_MODE, FloatingVolumeService.DEVICE_MODE_OFF).apply()
            updateAudioDeviceModeUi(FloatingVolumeService.DEVICE_MODE_OFF)
        }
        btnDeviceAuto.setOnClickListener {
            prefs.edit().putString(FloatingVolumeService.KEY_AUDIO_DEVICE_MODE, FloatingVolumeService.DEVICE_MODE_AUTO).apply()
            updateAudioDeviceModeUi(FloatingVolumeService.DEVICE_MODE_AUTO)
        }
        btnDeviceToast.setOnClickListener {
            prefs.edit().putString(FloatingVolumeService.KEY_AUDIO_DEVICE_MODE, FloatingVolumeService.DEVICE_MODE_TOAST).apply()
            updateAudioDeviceModeUi(FloatingVolumeService.DEVICE_MODE_TOAST)
        }

        spinnerHeadphonePreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (syncingViews || position !in presetIdsCache.indices) return
                prefs.edit().putString(FloatingVolumeService.KEY_HEADPHONE_PRESET_ID, presetIdsCache[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        spinnerSpeakerPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (syncingViews || position !in presetIdsCache.indices) return
                prefs.edit().putString(FloatingVolumeService.KEY_SPEAKER_PRESET_ID, presetIdsCache[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        radioGroupDisplayMode.setOnCheckedChangeListener { _, checkedId ->
            if (syncingViews) return@setOnCheckedChangeListener
            val mode = when (checkedId) {
                R.id.radioDisplayBadge -> FloatingVolumeService.DISPLAY_PERCENT_BADGE
                R.id.radioDisplaySliderFocus -> FloatingVolumeService.DISPLAY_SLIDER_FOCUS
                else -> FloatingVolumeService.DISPLAY_MINIMAL
            }
            markCustomPresetAndApply { it.putString(FloatingVolumeService.KEY_DISPLAY_MODE, mode) }
        }

        radioGroupHapticStrength.setOnCheckedChangeListener { _, checkedId ->
            if (syncingViews) return@setOnCheckedChangeListener
            val strength = when (checkedId) {
                R.id.radioHapticOff -> FloatingVolumeService.HAPTIC_OFF
                R.id.radioHapticLight -> FloatingVolumeService.HAPTIC_LIGHT
                R.id.radioHapticStrong -> FloatingVolumeService.HAPTIC_STRONG
                else -> FloatingVolumeService.HAPTIC_MEDIUM
            }
            markCustomPresetAndApply {
                it.putString(FloatingVolumeService.KEY_HAPTIC_STRENGTH, strength)
                    .putBoolean(FloatingVolumeService.KEY_HAPTIC_TAP, strength != FloatingVolumeService.HAPTIC_OFF)
            }
        }

        switchSwipeScrub.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            markCustomPresetAndApply { it.putBoolean(FloatingVolumeService.KEY_SWIPE_SCRUB, isChecked) }
        }

        switchSingleTapSlider.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            val mode = if (isChecked) FloatingVolumeService.POPOUT_SINGLE_TAP else FloatingVolumeService.POPOUT_SWIPE_INWARD
            markCustomPresetAndApply { it.putString(FloatingVolumeService.KEY_POPOUT_MODE, mode) }
        }

        switchSmoothFade.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            markCustomPresetAndApply { it.putBoolean(FloatingVolumeService.KEY_SMOOTH_FADE, isChecked) }
        }

        switchHideFullscreen.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            markCustomPresetAndApply { it.putBoolean(FloatingVolumeService.KEY_HIDE_FULLSCREEN, isChecked) }
        }

        switchSnapToEdge.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, isChecked).apply()
            if (isChecked) {
                snapAllOrientationsToEdgeIfNeeded(forceSnap = true)
            } else {
                syncPositionSlidersForSelectedTarget()
                refreshSavedPositionsText()
            }
        }

        btnResetPresets.setOnClickListener {
            val defaults = buildDefaultPresetsArray()
            selectedEditPresetId = defaults.optJSONObject(0)?.optString("id", "gaming")
            prefs.edit().putString(KEY_ACTIVE_PRESET_ID, "custom").apply()
            savePresetsArray(defaults)
            syncInlinePresetEditorViews(preserveFocusedInputs = false)
            updatePresetPriorityStatusRow()
            Toast.makeText(this, "Restored default presets", Toast.LENGTH_SHORT).show()
        }

        btnExportBackup.setOnClickListener {
            exportBackupLauncher.launch("floating-volume-backup.json")
        }

        btnImportBackup.setOnClickListener {
            importBackupLauncher.launch(arrayOf("application/json", "*/*"))
        }

        switchHoldRepeat.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            markCustomPresetAndApply { it.putBoolean(FloatingVolumeService.KEY_HOLD_TO_REPEAT, isChecked) }
        }

        switchAutoBoot.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_AUTO_BOOT, isChecked).apply()
            if (isChecked && !isBackgroundAutostartGranted()) {
                Toast.makeText(
                    this,
                    "Tip: Grant Background Autostart permission on Permissions tab",
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
            markCustomPresetAndApply { it.putString(FloatingVolumeService.KEY_AUDIO_STREAM, stream) }
        }

        radioGroupStepSize.setOnCheckedChangeListener { _, checkedId ->
            if (syncingViews) return@setOnCheckedChangeListener
            val step = when (checkedId) {
                R.id.radioStep2 -> 2
                R.id.radioStep3 -> 3
                R.id.radioStep5 -> 5
                else -> 1
            }
            markCustomPresetAndApply { it.putInt(FloatingVolumeService.KEY_VOLUME_STEP, step) }
        }

        seekBarMaxLimit.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pct = 30 + progress
                tvMaxLimitValue.text = "$pct%"
                if (fromUser) {
                    markCustomPresetAndApply { it.putInt(FloatingVolumeService.KEY_MAX_VOLUME_LIMIT_PCT, pct) }
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
            markCustomPresetAndApply { it.putInt(FloatingVolumeService.KEY_AUTO_DIM_SEC, seconds) }
        }

        seekBarDimOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvDimOpacityValue.text = "$progress%"
                if (fromUser) {
                    markCustomPresetAndApply { it.putInt(FloatingVolumeService.KEY_DIM_OPACITY_PCT, progress) }
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

        btnWriteSettingsPermission.setOnClickListener {
            requestWriteSettingsPermissionIfNeeded()
        }

        btnUsageAccessPermission.setOnClickListener {
            requestUsageAccessPermissionIfNeeded()
        }

        switchOledPixelShift.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_OLED_PIXEL_SHIFT, isChecked).apply()
        }

        switchAvoidSafeZones.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_AVOID_SAFE_ZONES, isChecked).apply()
        }

        switchBrightnessHandle.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_BRIGHTNESS_HANDLE_ENABLED, isChecked).apply()
            if (isChecked && !Settings.System.canWrite(this)) {
                Toast.makeText(this, "Grant Modify System Settings to control brightness", Toast.LENGTH_LONG).show()
                selectSlide(3)
            }
        }

        switchBrightnessInSlider.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_SHOW_BRIGHTNESS_IN_SLIDER, isChecked).apply()
        }

        seekBarBrightnessHeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val h = 48 + progress
                tvBrightnessHeightValue.text = "$h dp"
                if (fromUser) {
                    prefs.edit().putInt(FloatingVolumeService.KEY_BRIGHTNESS_HEIGHT_DP, h).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        val brightSwatches = mapOf(
            R.id.btnBrightAmber to Color.parseColor("#F59E0B"),
            R.id.btnBrightCyan to Color.parseColor("#06B6D4"),
            R.id.btnBrightEmerald to Color.parseColor("#10B981"),
            R.id.btnBrightWhite to Color.parseColor("#F8FAFC")
        )
        brightSwatches.forEach { (btnId, colorInt) ->
            findViewById<Button>(btnId).setOnClickListener {
                prefs.edit().putInt(FloatingVolumeService.KEY_BRIGHTNESS_COLOR, colorInt).apply()
            }
        }

        fun bindGestureSpinner(spinner: Spinner, prefKey: String) {
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (syncingViews || position !in gestureActionIds.indices) return
                    val actionId = gestureActionIds[position]
                    markCustomPresetAndApply { editor ->
                        editor.putString(prefKey, actionId)
                        if (prefKey == FloatingVolumeService.KEY_GESTURE_DOUBLE_TAP) {
                            editor.putBoolean(FloatingVolumeService.KEY_DOUBLE_TAP_MUTE, actionId != FloatingVolumeService.ACTION_ID_NONE)
                        } else if (prefKey == FloatingVolumeService.KEY_GESTURE_TRIPLE_TAP) {
                            editor.putBoolean(FloatingVolumeService.KEY_TRIPLE_TAP_MEDIA, actionId != FloatingVolumeService.ACTION_ID_NONE)
                        }
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        bindGestureSpinner(spinnerGestureDoubleTap, FloatingVolumeService.KEY_GESTURE_DOUBLE_TAP)
        bindGestureSpinner(spinnerGestureTripleTap, FloatingVolumeService.KEY_GESTURE_TRIPLE_TAP)
        bindGestureSpinner(spinnerGestureInwardFlick, FloatingVolumeService.KEY_GESTURE_INWARD_FLICK)
        bindGestureSpinner(spinnerGestureLockedLongPress, FloatingVolumeService.KEY_GESTURE_LOCKED_LONG_PRESS)

        switchQuietHours.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_QUIET_HOURS_ENABLED, isChecked).apply()
        }

        fun openTimePicker(prefKey: String, labelPrefix: String, btn: Button, defaultHhMm: String) {
            val parts = (prefs.getString(prefKey, defaultHhMm) ?: defaultHhMm).split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 22
            val minVal = parts.getOrNull(1)?.toIntOrNull() ?: 0
            TimePickerDialog(this, { _, h, m ->
                val formatted = String.format("%02d:%02d", h, m)
                prefs.edit().putString(prefKey, formatted).apply()
                btn.text = "$labelPrefix: $formatted"
            }, hour, minVal, true).show()
        }
        btnQuietStartTime.setOnClickListener {
            openTimePicker(FloatingVolumeService.KEY_QUIET_START, "Start", btnQuietStartTime, "22:30")
        }
        btnQuietEndTime.setOnClickListener {
            openTimePicker(FloatingVolumeService.KEY_QUIET_END, "End", btnQuietEndTime, "07:00")
        }
        spinnerQuietPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (syncingViews || position !in presetIdsCache.indices) return
                prefs.edit().putString(FloatingVolumeService.KEY_QUIET_PRESET_ID, presetIdsCache[position]).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        switchPerAppRules.setOnCheckedChangeListener { _, isChecked ->
            if (syncingViews) return@setOnCheckedChangeListener
            prefs.edit().putBoolean(FloatingVolumeService.KEY_PER_APP_RULES_ENABLED, isChecked).apply()
            if (isChecked && !isUsageAccessGranted()) {
                Toast.makeText(this, "Grant Usage Access on Permissions tab for per-app detection", Toast.LENGTH_LONG).show()
                selectSlide(3)
            }
        }
        btnAddPerAppRule.setOnClickListener {
            showAddPerAppRuleDialog()
        }

        btnStartService.setOnClickListener {
            if (prefs.getBoolean(FloatingVolumeService.KEY_IS_SERVICE_RUNNING, false)) {
                return@setOnClickListener
            }
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(
                    this,
                    "Grant Display Over Other Apps permission first",
                    Toast.LENGTH_SHORT
                ).show()
                selectSlide(3)
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
                    markCustomPresetAndApply { it.putInt(FloatingVolumeService.KEY_WIDTH_DP, widthDp) }
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
                    markCustomPresetAndApply { it.putInt(FloatingVolumeService.KEY_HEIGHT_DP, heightDp) }
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
                    markCustomPresetAndApply { it.putInt(FloatingVolumeService.KEY_TRANSPARENCY_PCT, alphaPct) }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        radioGroupShape.setOnCheckedChangeListener { _, checkedId ->
            if (syncingViews) return@setOnCheckedChangeListener
            val shape = when (checkedId) {
                R.id.radioLine -> FloatingVolumeService.SHAPE_LINE
                R.id.radioEdgeTab -> FloatingVolumeService.SHAPE_EDGE_TAB
                else -> FloatingVolumeService.SHAPE_CIRCLE
            }
            markCustomPresetAndApply { it.putString(FloatingVolumeService.KEY_SHAPE, shape) }
            if (shape == FloatingVolumeService.SHAPE_EDGE_TAB) {
                snapAllOrientationsToEdgeIfNeeded(forceSnap = true)
            } else {
                updateOrientationPreviewBox()
            }
            updatePresetPriorityStatusRow()
        }

        radioGroupHandleIconStyle.setOnCheckedChangeListener { _, checkedId ->
            if (syncingViews) return@setOnCheckedChangeListener
            val iconStyle = when (checkedId) {
                R.id.radioIconSpeaker -> FloatingVolumeService.ICON_STYLE_SPEAKER
                R.id.radioIconBars -> FloatingVolumeService.ICON_STYLE_BARS
                R.id.radioIconDot -> FloatingVolumeService.ICON_STYLE_DOT
                R.id.radioIconNone -> FloatingVolumeService.ICON_STYLE_NONE
                else -> FloatingVolumeService.ICON_STYLE_ARROWS
            }
            markCustomPresetAndApply { it.putString(FloatingVolumeService.KEY_HANDLE_ICON_STYLE, iconStyle) }
            updatePresetPriorityStatusRow()
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
            if (!syncingViews) {
                syncPositionSlidersForSelectedTarget()
            }
        }

        val posSliderListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (updatingPositionSliders || !fromUser) return
                val isLandscapeTarget =
                    radioGroupOrientTarget.checkedRadioButtonId == R.id.radioTargetLandscape
                val (maxX, maxY) = getMaxBoundsForOrientation(isLandscapeTarget)
                val (safeMinX, safeMaxX) = getSafeXRangeForOrientation(isLandscapeTarget, maxX)
                val (safeMinY, safeMaxY) = getSafeYRangeForOrientation(isLandscapeTarget, maxY)
                val xPct = seekBarPosX.progress
                val yPct = seekBarPosY.progress
                val activeShape = prefs.getString(FloatingVolumeService.KEY_SHAPE, FloatingVolumeService.SHAPE_CIRCLE)
                val dockEdge = activeShape == FloatingVolumeService.SHAPE_EDGE_TAB ||
                    prefs.getBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, false)
                val pxX = if (dockEdge) {
                    if (xPct < 50) safeMinX else safeMaxX
                } else {
                    (((xPct / 100f) * (safeMaxX - safeMinX)) + safeMinX).toInt().coerceIn(safeMinX, safeMaxX)
                }
                val finalXPct = if (dockEdge) (if (pxX == safeMinX) 0 else 100) else xPct
                val pxY = (((yPct / 100f) * (safeMaxY - safeMinY)) + safeMinY).toInt().coerceIn(safeMinY, safeMaxY)

                updatingPositionSliders = true
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
                updatingPositionSliders = false
                updatePositionLabels(isLandscapeTarget, pxX, pxY, finalXPct, yPct)
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
            val brightInt = prefs.getInt(FloatingVolumeService.KEY_BRIGHTNESS_COLOR, FloatingVolumeService.DEFAULT_BRIGHTNESS_COLOR)
            val brightHex = String.format("#%06X", 0xFFFFFF and brightInt)
            val gesturesObj = JSONObject().apply {
                put("doubleTap", prefs.getString(FloatingVolumeService.KEY_GESTURE_DOUBLE_TAP, FloatingVolumeService.DEFAULT_GESTURE_DOUBLE_TAP))
                put("tripleTap", prefs.getString(FloatingVolumeService.KEY_GESTURE_TRIPLE_TAP, FloatingVolumeService.DEFAULT_GESTURE_TRIPLE_TAP))
                put("inwardFlick", prefs.getString(FloatingVolumeService.KEY_GESTURE_INWARD_FLICK, FloatingVolumeService.DEFAULT_GESTURE_INWARD_FLICK))
                put("lockedLongPress", prefs.getString(FloatingVolumeService.KEY_GESTURE_LOCKED_LONG_PRESS, FloatingVolumeService.DEFAULT_GESTURE_LOCKED_LONG_PRESS))
            }
            val quietObj = JSONObject().apply {
                put("enabled", prefs.getBoolean(FloatingVolumeService.KEY_QUIET_HOURS_ENABLED, false))
                put("startTime", prefs.getString(FloatingVolumeService.KEY_QUIET_START, "22:30"))
                put("endTime", prefs.getString(FloatingVolumeService.KEY_QUIET_END, "07:00"))
                put("presetId", prefs.getString(FloatingVolumeService.KEY_QUIET_PRESET_ID, "bedtime"))
            }
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
                put("hapticStrength", prefs.getString(FloatingVolumeService.KEY_HAPTIC_STRENGTH, FloatingVolumeService.DEFAULT_HAPTIC_STRENGTH))
                put("snapToEdge", prefs.getBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, false))
                put("doubleTapMute", prefs.getBoolean(FloatingVolumeService.KEY_DOUBLE_TAP_MUTE, false))
                put("tripleTapPlayPause", prefs.getBoolean(FloatingVolumeService.KEY_TRIPLE_TAP_MEDIA, false))
                put("holdToRepeat", prefs.getBoolean(FloatingVolumeService.KEY_HOLD_TO_REPEAT, true))
                put("autoStartBoot", prefs.getBoolean(FloatingVolumeService.KEY_AUTO_BOOT, false))
                put("positionLocked", prefs.getBoolean(FloatingVolumeService.KEY_POSITION_LOCKED, false))
                put("autoDimSeconds", prefs.getInt(FloatingVolumeService.KEY_AUTO_DIM_SEC, 0))
                put("dimOpacityPct", prefs.getInt(FloatingVolumeService.KEY_DIM_OPACITY_PCT, 15))
                put("volumeStep", prefs.getInt(FloatingVolumeService.KEY_VOLUME_STEP, 1))
                put("maxVolumeLimitPct", prefs.getInt(FloatingVolumeService.KEY_MAX_VOLUME_LIMIT_PCT, 100))
                put("swipeScrub", prefs.getBoolean(FloatingVolumeService.KEY_SWIPE_SCRUB, true))
                put("popoutSliderMode", prefs.getString(FloatingVolumeService.KEY_POPOUT_MODE, FloatingVolumeService.POPOUT_SWIPE_INWARD))
                put("overlayDisplayMode", prefs.getString(FloatingVolumeService.KEY_DISPLAY_MODE, FloatingVolumeService.DISPLAY_MINIMAL))
                put("handleIconStyle", prefs.getString(FloatingVolumeService.KEY_HANDLE_ICON_STYLE, FloatingVolumeService.DEFAULT_HANDLE_ICON_STYLE))
                put("smoothFadeAnimation", prefs.getBoolean(FloatingVolumeService.KEY_SMOOTH_FADE, true))
                put("hideInFullscreen", prefs.getBoolean(FloatingVolumeService.KEY_HIDE_FULLSCREEN, false))
                put("oledPixelShift", prefs.getBoolean(FloatingVolumeService.KEY_OLED_PIXEL_SHIFT, false))
                put("avoidSafeZones", prefs.getBoolean(FloatingVolumeService.KEY_AVOID_SAFE_ZONES, FloatingVolumeService.DEFAULT_AVOID_SAFE_ZONES))
                put("brightnessHandleEnabled", prefs.getBoolean(FloatingVolumeService.KEY_BRIGHTNESS_HANDLE_ENABLED, false))
                put("brightnessColorHex", brightHex)
                put("brightnessHeightDp", prefs.getInt(FloatingVolumeService.KEY_BRIGHTNESS_HEIGHT_DP, FloatingVolumeService.DEFAULT_BRIGHTNESS_HEIGHT_DP))
                put("showBrightnessInSlider", prefs.getBoolean(FloatingVolumeService.KEY_SHOW_BRIGHTNESS_IN_SLIDER, true))
                put("gestureActions", gesturesObj)
                put("quietHours", quietObj)
                put("perAppRulesEnabled", prefs.getBoolean(FloatingVolumeService.KEY_PER_APP_RULES_ENABLED, false))
                put("perAppRules", getSavedPerAppRulesArray())
                put("audioDeviceMode", prefs.getString(FloatingVolumeService.KEY_AUDIO_DEVICE_MODE, FloatingVolumeService.DEFAULT_AUDIO_DEVICE_MODE))
                put("headphonePresetId", prefs.getString(FloatingVolumeService.KEY_HEADPHONE_PRESET_ID, "bedtime"))
                put("speakerPresetId", prefs.getString(FloatingVolumeService.KEY_SPEAKER_PRESET_ID, "gaming"))
                put("presets", getSavedPresetsArray())
            }
            val root = JSONObject().apply {
                put("schemaVersion", 6)
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
            if (s.has("brightnessColorHex")) {
                try {
                    editor.putInt(FloatingVolumeService.KEY_BRIGHTNESS_COLOR, Color.parseColor(s.getString("brightnessColorHex")))
                } catch (_: Exception) {}
            }
            if (s.has("audioStream")) editor.putString(FloatingVolumeService.KEY_AUDIO_STREAM, s.optString("audioStream"))
            if (s.has("darkTheme")) editor.putBoolean(FloatingVolumeService.KEY_DARK_THEME, s.optBoolean("darkTheme"))
            if (s.has("hapticStrength")) {
                val strength = s.optString("hapticStrength")
                editor.putString(FloatingVolumeService.KEY_HAPTIC_STRENGTH, strength)
                editor.putBoolean(FloatingVolumeService.KEY_HAPTIC_TAP, strength != FloatingVolumeService.HAPTIC_OFF)
            } else if (s.has("hapticTap")) {
                val tap = s.optBoolean("hapticTap")
                editor.putBoolean(FloatingVolumeService.KEY_HAPTIC_TAP, tap)
                editor.putString(FloatingVolumeService.KEY_HAPTIC_STRENGTH, if (tap) FloatingVolumeService.HAPTIC_MEDIUM else FloatingVolumeService.HAPTIC_OFF)
            }
            if (s.has("snapToEdge")) editor.putBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, s.optBoolean("snapToEdge"))
            if (s.has("doubleTapMute")) {
                val legacyDouble = s.optBoolean("doubleTapMute")
                editor.putBoolean(FloatingVolumeService.KEY_DOUBLE_TAP_MUTE, legacyDouble)
                editor.putString(
                    FloatingVolumeService.KEY_GESTURE_DOUBLE_TAP,
                    if (legacyDouble) FloatingVolumeService.ACTION_ID_MUTE else FloatingVolumeService.ACTION_ID_NONE
                )
            }
            if (s.has("tripleTapPlayPause")) {
                val legacyTriple = s.optBoolean("tripleTapPlayPause")
                editor.putBoolean(FloatingVolumeService.KEY_TRIPLE_TAP_MEDIA, legacyTriple)
                editor.putString(
                    FloatingVolumeService.KEY_GESTURE_TRIPLE_TAP,
                    if (legacyTriple) FloatingVolumeService.ACTION_ID_PLAY_PAUSE else FloatingVolumeService.ACTION_ID_NONE
                )
            }
            if (s.has("holdToRepeat")) editor.putBoolean(FloatingVolumeService.KEY_HOLD_TO_REPEAT, s.optBoolean("holdToRepeat"))
            if (s.has("autoStartBoot")) editor.putBoolean(FloatingVolumeService.KEY_AUTO_BOOT, s.optBoolean("autoStartBoot"))
            if (s.has("positionLocked")) editor.putBoolean(FloatingVolumeService.KEY_POSITION_LOCKED, s.optBoolean("positionLocked"))
            if (s.has("autoDimSeconds")) editor.putInt(FloatingVolumeService.KEY_AUTO_DIM_SEC, s.optInt("autoDimSeconds"))
            if (s.has("dimOpacityPct")) editor.putInt(FloatingVolumeService.KEY_DIM_OPACITY_PCT, s.optInt("dimOpacityPct"))
            if (s.has("volumeStep")) editor.putInt(FloatingVolumeService.KEY_VOLUME_STEP, s.optInt("volumeStep"))
            if (s.has("maxVolumeLimitPct")) editor.putInt(FloatingVolumeService.KEY_MAX_VOLUME_LIMIT_PCT, s.optInt("maxVolumeLimitPct"))
            if (s.has("swipeScrub")) editor.putBoolean(FloatingVolumeService.KEY_SWIPE_SCRUB, s.optBoolean("swipeScrub"))
            if (s.has("popoutSliderMode")) editor.putString(FloatingVolumeService.KEY_POPOUT_MODE, s.optString("popoutSliderMode"))
            if (s.has("overlayDisplayMode")) editor.putString(FloatingVolumeService.KEY_DISPLAY_MODE, s.optString("overlayDisplayMode"))
            if (s.has("handleIconStyle")) editor.putString(FloatingVolumeService.KEY_HANDLE_ICON_STYLE, s.optString("handleIconStyle"))
            if (s.has("smoothFadeAnimation")) editor.putBoolean(FloatingVolumeService.KEY_SMOOTH_FADE, s.optBoolean("smoothFadeAnimation"))
            if (s.has("hideInFullscreen")) editor.putBoolean(FloatingVolumeService.KEY_HIDE_FULLSCREEN, s.optBoolean("hideInFullscreen"))
            if (s.has("oledPixelShift")) editor.putBoolean(FloatingVolumeService.KEY_OLED_PIXEL_SHIFT, s.optBoolean("oledPixelShift"))
            if (s.has("avoidSafeZones")) editor.putBoolean(FloatingVolumeService.KEY_AVOID_SAFE_ZONES, s.optBoolean("avoidSafeZones"))
            if (s.has("brightnessHandleEnabled")) editor.putBoolean(FloatingVolumeService.KEY_BRIGHTNESS_HANDLE_ENABLED, s.optBoolean("brightnessHandleEnabled"))
            if (s.has("brightnessHeightDp")) editor.putInt(FloatingVolumeService.KEY_BRIGHTNESS_HEIGHT_DP, s.optInt("brightnessHeightDp"))
            if (s.has("showBrightnessInSlider")) editor.putBoolean(FloatingVolumeService.KEY_SHOW_BRIGHTNESS_IN_SLIDER, s.optBoolean("showBrightnessInSlider"))
            s.optJSONObject("gestureActions")?.let { g ->
                if (g.has("doubleTap")) {
                    val act = g.optString("doubleTap")
                    editor.putString(FloatingVolumeService.KEY_GESTURE_DOUBLE_TAP, act)
                    editor.putBoolean(FloatingVolumeService.KEY_DOUBLE_TAP_MUTE, act != FloatingVolumeService.ACTION_ID_NONE)
                }
                if (g.has("tripleTap")) {
                    val act = g.optString("tripleTap")
                    editor.putString(FloatingVolumeService.KEY_GESTURE_TRIPLE_TAP, act)
                    editor.putBoolean(FloatingVolumeService.KEY_TRIPLE_TAP_MEDIA, act != FloatingVolumeService.ACTION_ID_NONE)
                }
                if (g.has("inwardFlick")) editor.putString(FloatingVolumeService.KEY_GESTURE_INWARD_FLICK, g.optString("inwardFlick"))
                if (g.has("lockedLongPress")) editor.putString(FloatingVolumeService.KEY_GESTURE_LOCKED_LONG_PRESS, g.optString("lockedLongPress"))
            }
            s.optJSONObject("quietHours")?.let { q ->
                if (q.has("enabled")) editor.putBoolean(FloatingVolumeService.KEY_QUIET_HOURS_ENABLED, q.optBoolean("enabled"))
                if (q.has("startTime")) editor.putString(FloatingVolumeService.KEY_QUIET_START, q.optString("startTime"))
                if (q.has("endTime")) editor.putString(FloatingVolumeService.KEY_QUIET_END, q.optString("endTime"))
                if (q.has("presetId")) editor.putString(FloatingVolumeService.KEY_QUIET_PRESET_ID, q.optString("presetId"))
            }
            if (s.has("perAppRulesEnabled")) editor.putBoolean(FloatingVolumeService.KEY_PER_APP_RULES_ENABLED, s.optBoolean("perAppRulesEnabled"))
            s.optJSONArray("perAppRules")?.let { rArr ->
                editor.putString(FloatingVolumeService.KEY_PER_APP_RULES_JSON, rArr.toString())
            }
            if (s.has("audioDeviceMode")) editor.putString(FloatingVolumeService.KEY_AUDIO_DEVICE_MODE, s.optString("audioDeviceMode"))
            if (s.has("headphonePresetId")) editor.putString(FloatingVolumeService.KEY_HEADPHONE_PRESET_ID, s.optString("headphonePresetId"))
            if (s.has("speakerPresetId")) editor.putString(FloatingVolumeService.KEY_SPEAKER_PRESET_ID, s.optString("speakerPresetId"))
            val presetsArr = s.optJSONArray("presets")
            if (presetsArr != null && presetsArr.length() > 0) {
                editor.putString(FloatingVolumeService.KEY_PRESETS_JSON, presetsArr.toString())
            }
            editor.apply()
            ensurePresetsExist()

            loadSavedSettingsIntoViews()
            Toast.makeText(this, "Backup restored", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid backup file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveOverlayColor(colorInt: Int) {
        markCustomPresetAndApply { it.putInt(FloatingVolumeService.KEY_OVERLAY_COLOR, colorInt) }
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
        val displayMode = prefs.getString(
            FloatingVolumeService.KEY_DISPLAY_MODE,
            FloatingVolumeService.DEFAULT_DISPLAY_MODE
        ) ?: FloatingVolumeService.DISPLAY_MINIMAL
        val alphaPct = prefs.getInt(
            FloatingVolumeService.KEY_TRANSPARENCY_PCT,
            FloatingVolumeService.DEFAULT_TRANSPARENCY_PCT
        )
        val hasSideBadge = displayMode == FloatingVolumeService.DISPLAY_PERCENT_BADGE &&
            alphaPct > 0 && !(widthDp >= 44 && heightDp >= 32)
        val sideBadgePx = if (hasSideBadge) (48 * metrics.density).toInt() else 0
        val wPx = (widthDp * metrics.density).toInt().coerceAtLeast(1) + sideBadgePx
        val hPx = (heightDp * metrics.density).toInt().coerceAtLeast(1)
        return (screenW - wPx).coerceAtLeast(1) to (screenH - hPx).coerceAtLeast(1)
    }

    private fun getSafeXRangeForOrientation(isLandscape: Boolean, maxX: Int): Pair<Int, Int> {
        if (!prefs.getBoolean(FloatingVolumeService.KEY_AVOID_SAFE_ZONES, FloatingVolumeService.DEFAULT_AVOID_SAFE_ZONES)) {
            return 0 to maxX
        }
        val currentlyLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape != currentlyLand) return 0 to maxX
        val minX = safeInsetLeft.coerceIn(0, maxX)
        val safeMaxX = (maxX - safeInsetRight).coerceIn(minX, maxX)
        return minX to safeMaxX
    }

    private fun getSafeYRangeForOrientation(isLandscape: Boolean, maxY: Int): Pair<Int, Int> {
        if (!prefs.getBoolean(FloatingVolumeService.KEY_AVOID_SAFE_ZONES, FloatingVolumeService.DEFAULT_AVOID_SAFE_ZONES)) {
            return 0 to maxY
        }
        val currentlyLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape != currentlyLand) return 0 to maxY
        val minY = safeInsetTop.coerceIn(0, maxY)
        val safeMaxY = (maxY - safeInsetBottom).coerceIn(minY, maxY)
        return minY to safeMaxY
    }

    private fun snapAllOrientationsToEdgeIfNeeded(forceSnap: Boolean = false) {
        val activeShape = prefs.getString(
            FloatingVolumeService.KEY_SHAPE,
            FloatingVolumeService.SHAPE_CIRCLE
        )
        val shouldDock = forceSnap ||
            prefs.getBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, false) ||
            activeShape == FloatingVolumeService.SHAPE_EDGE_TAB
        if (!shouldDock) return

        val (maxPX, _) = getMaxBoundsForOrientation(false)
        val (maxLX, _) = getMaxBoundsForOrientation(true)
        val (minPX, safeMaxPX) = getSafeXRangeForOrientation(false, maxPX)
        val (minLX, safeMaxLX) = getSafeXRangeForOrientation(true, maxLX)
        val pX = prefs.getInt(FloatingVolumeService.KEY_POS_X_PORTRAIT, safeMaxPX)
        val lX = prefs.getInt(FloatingVolumeService.KEY_POS_X_LANDSCAPE, minLX)
        val dockedPX = if (pX <= (minPX + safeMaxPX) / 2) minPX else safeMaxPX
        val dockedLX = if (lX <= (minLX + safeMaxLX) / 2) minLX else safeMaxLX

        prefs.edit()
            .putInt(FloatingVolumeService.KEY_POS_X_PORTRAIT, dockedPX)
            .putInt(FloatingVolumeService.KEY_POS_X_LANDSCAPE, dockedLX)
            .apply()
        syncPositionSlidersForSelectedTarget()
        refreshSavedPositionsText()
    }

    private fun syncPositionSlidersForSelectedTarget() {
        updatingPositionSliders = true
        val isLandscapeTarget =
            radioGroupOrientTarget.checkedRadioButtonId == R.id.radioTargetLandscape
        val (maxX, maxY) = getMaxBoundsForOrientation(isLandscapeTarget)
        val (safeMinX, safeMaxX) = getSafeXRangeForOrientation(isLandscapeTarget, maxX)
        val (safeMinY, safeMaxY) = getSafeYRangeForOrientation(isLandscapeTarget, maxY)
        val activeShape = prefs.getString(
            FloatingVolumeService.KEY_SHAPE,
            FloatingVolumeService.SHAPE_CIRCLE
        )
        val shouldDock = prefs.getBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, false) ||
            activeShape == FloatingVolumeService.SHAPE_EDGE_TAB
        val rawX = if (isLandscapeTarget) {
            prefs.getInt(FloatingVolumeService.KEY_POS_X_LANDSCAPE, (maxX * 0.10f).toInt())
        } else {
            prefs.getInt(FloatingVolumeService.KEY_POS_X_PORTRAIT, (maxX * 0.88f).toInt())
        }
        val x = if (shouldDock) {
            if (rawX <= (safeMinX + safeMaxX) / 2) safeMinX else safeMaxX
        } else {
            rawX.coerceIn(safeMinX, safeMaxX)
        }
        val y = (if (isLandscapeTarget) {
            prefs.getInt(FloatingVolumeService.KEY_POS_Y_LANDSCAPE, (maxY * 0.25f).toInt())
        } else {
            prefs.getInt(FloatingVolumeService.KEY_POS_Y_PORTRAIT, (maxY * 0.38f).toInt())
        }).coerceIn(safeMinY, safeMaxY)
        val spanX = (safeMaxX - safeMinX).coerceAtLeast(1)
        val spanY = (safeMaxY - safeMinY).coerceAtLeast(1)
        val xPct = (((x - safeMinX).toFloat() / spanX) * 100f).toInt().coerceIn(0, 100)
        val yPct = (((y - safeMinY).toFloat() / spanY) * 100f).toInt().coerceIn(0, 100)
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

        val avoidSafe = prefs.getBoolean(
            FloatingVolumeService.KEY_AVOID_SAFE_ZONES,
            FloatingVolumeService.DEFAULT_AVOID_SAFE_ZONES
        )
        val metrics = resources.displayMetrics
        val shortSide = min(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
        val longSide = max(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
        val targetScreenW = if (isLandscapeTarget) longSide else shortSide
        val targetScreenH = if (isLandscapeTarget) shortSide else longSide
        val currentlyLand = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (avoidSafe && currentlyLand == isLandscapeTarget &&
            (safeInsetLeft > 0 || safeInsetTop > 0 || safeInsetRight > 0 || safeInsetBottom > 0)
        ) {
            previewSafeZoneBox.visibility = View.VISIBLE
            previewSafeZoneBox.background = GradientDrawable().apply {
                setColor(Color.argb(18, 16, 185, 129))
                setStroke(dpI(1), Color.argb(140, 16, 185, 129), 4f * density, 3f * density)
            }
            val safeLp = (previewSafeZoneBox.layoutParams as FrameLayout.LayoutParams).apply {
                leftMargin = ((safeInsetLeft.toFloat() / targetScreenW) * boxWidthPx).toInt()
                topMargin = ((safeInsetTop.toFloat() / targetScreenH) * boxHeightPx).toInt()
                rightMargin = ((safeInsetRight.toFloat() / targetScreenW) * boxWidthPx).toInt()
                bottomMargin = ((safeInsetBottom.toFloat() / targetScreenH) * boxHeightPx).toInt()
            }
            previewSafeZoneBox.layoutParams = safeLp
        } else {
            previewSafeZoneBox.visibility = View.GONE
        }

        if (prefs.getBoolean(FloatingVolumeService.KEY_BRIGHTNESS_HANDLE_ENABLED, false)) {
            previewBrightnessDot.visibility = View.VISIBLE
            val bColor = prefs.getInt(
                FloatingVolumeService.KEY_BRIGHTNESS_COLOR,
                FloatingVolumeService.DEFAULT_BRIGHTNESS_COLOR
            )
            previewBrightnessDot.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6f * density
                setColor(bColor)
                setStroke(dpI(1), Color.WHITE)
            }
            val bwPx = (prefs.getInt(FloatingVolumeService.KEY_BRIGHTNESS_WIDTH_DP, FloatingVolumeService.DEFAULT_BRIGHTNESS_WIDTH_DP) * density).toInt()
            val bhPx = (prefs.getInt(FloatingVolumeService.KEY_BRIGHTNESS_HEIGHT_DP, FloatingVolumeService.DEFAULT_BRIGHTNESS_HEIGHT_DP) * density).toInt()
            val maxBX = (targetScreenW - bwPx).coerceAtLeast(1)
            val maxBY = (targetScreenH - bhPx).coerceAtLeast(1)
            val rawBX = if (isLandscapeTarget) {
                prefs.getInt(FloatingVolumeService.KEY_BRIGHTNESS_POS_X_LANDSCAPE, (targetScreenW * 0.85f).toInt())
            } else {
                prefs.getInt(FloatingVolumeService.KEY_BRIGHTNESS_POS_X_PORTRAIT, (targetScreenW * 0.10f).toInt())
            }.coerceIn(0, maxBX)
            val rawBY = if (isLandscapeTarget) {
                prefs.getInt(FloatingVolumeService.KEY_BRIGHTNESS_POS_Y_LANDSCAPE, (targetScreenH * 0.30f).toInt())
            } else {
                prefs.getInt(FloatingVolumeService.KEY_BRIGHTNESS_POS_Y_PORTRAIT, (targetScreenH * 0.56f).toInt())
            }.coerceIn(0, maxBY)
            val bDotW = (9 * density).toInt()
            val bDotH = (14 * density).toInt()
            previewBrightnessDot.translationX = (rawBX.toFloat() / maxBX) * (boxWidthPx - bDotW).coerceAtLeast(0)
            previewBrightnessDot.translationY = (rawBY.toFloat() / maxBY) * (boxHeightPx - bDotH).coerceAtLeast(0)
        } else {
            previewBrightnessDot.visibility = View.GONE
        }

        val colorInt = prefs.getInt(
            FloatingVolumeService.KEY_OVERLAY_COLOR,
            FloatingVolumeService.DEFAULT_COLOR
        )
        val shape = prefs.getString(
            FloatingVolumeService.KEY_SHAPE,
            FloatingVolumeService.SHAPE_CIRCLE
        )
        val dotDrawable = GradientDrawable().apply {
            this.shape = if (shape == FloatingVolumeService.SHAPE_LINE || shape == FloatingVolumeService.SHAPE_EDGE_TAB) {
                GradientDrawable.RECTANGLE
            } else {
                GradientDrawable.OVAL
            }
            if (shape == FloatingVolumeService.SHAPE_LINE) {
                cornerRadius = 8f * density
            } else if (shape == FloatingVolumeService.SHAPE_EDGE_TAB) {
                val r = 10f * density
                cornerRadii = if (seekBarPosX.progress < 50) {
                    floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
                } else {
                    floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
                }
            }
            setColor(colorInt)
            setStroke((1.5f * density).toInt().coerceAtLeast(1), Color.WHITE)
        }
        previewDot.background = dotDrawable

        val dotW = (14 * density).toInt()
        val dotH = (22 * density).toInt()
        val maxPreviewX = (boxWidthPx - dotW).coerceAtLeast(0)
        val maxPreviewY = (boxHeightPx - dotH).coerceAtLeast(0)
        val shouldDock = shape == FloatingVolumeService.SHAPE_EDGE_TAB ||
            prefs.getBoolean(FloatingVolumeService.KEY_SNAP_TO_EDGE, false)
        val effectiveXPct = if (shouldDock) {
            if (seekBarPosX.progress < 50) 0 else 100
        } else {
            seekBarPosX.progress
        }
        previewDot.translationX = (effectiveXPct / 100f) * maxPreviewX
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

    private fun isUsageAccessGranted(): Boolean {
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
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestWriteSettingsPermissionIfNeeded() {
        if (!Settings.System.canWrite(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:$packageName")
            )
            writeSettingsPermissionLauncher.launch(intent)
        } else {
            Toast.makeText(this, "Modify System Settings already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestUsageAccessPermissionIfNeeded() {
        if (!isUsageAccessGranted()) {
            try {
                usageAccessPermissionLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
                usageAccessPermissionLauncher.launch(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            }
        } else {
            Toast.makeText(this, "Usage Access already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchFloatingService() {
        val serviceIntent = Intent(this, FloatingVolumeService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun updatePermissionUi() {
        val overlayOk = Settings.canDrawOverlays(this)
        val autostartOk = isBackgroundAutostartGranted()
        val writeOk = Settings.System.canWrite(this)
        val usageOk = isUsageAccessGranted()

        val overlayPart = if (overlayOk) "Overlay OK" else "Overlay Needed"
        val autoPart = if (autostartOk) "Autostart OK" else "Autostart Tip"
        val ssb = SpannableStringBuilder("$overlayPart · $autoPart")
        ssb.setSpan(
            ForegroundColorSpan(Color.parseColor(if (overlayOk) "#10B981" else "#F59E0B")),
            0,
            overlayPart.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        val autoStartIdx = overlayPart.length + 3
        ssb.setSpan(
            ForegroundColorSpan(Color.parseColor(if (autostartOk) "#10B981" else if (isDarkModeActive()) "#94A3B8" else "#64748B")),
            autoStartIdx,
            ssb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tvHeaderStatus.text = ssb

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

        tvWriteSettingsStatus.text = if (writeOk) {
            "Modify system settings (Brightness): Granted"
        } else {
            "Modify system settings (Brightness): Optional · Needed for Brightness Handle"
        }
        btnWriteSettingsPermission.isEnabled = !writeOk
        btnWriteSettingsPermission.alpha = if (writeOk) 0.55f else 1f

        tvUsageAccessStatus.text = if (usageOk) {
            "Usage access (Per-App rules): Granted"
        } else {
            "Usage access (Per-App rules): Optional · Needed for Per-App Presets"
        }
        btnUsageAccessPermission.isEnabled = !usageOk
        btnUsageAccessPermission.alpha = if (usageOk) 0.55f else 1f

        tvHomeServiceHint.visibility = if (overlayOk) View.GONE else View.VISIBLE
        viewPermDot.visibility = if (overlayOk) View.GONE else View.VISIBLE

        btnOverlayPermission.isEnabled = !overlayOk
        btnOverlayPermission.alpha = if (overlayOk) 0.55f else 1f
        btnAutostartPermission.text = if (autostartOk) {
            "Background Autostart Granted (Open OEM / Battery)"
        } else {
            "Grant Background Autostart Permission"
        }
        btnStartService.isEnabled = true
    }

    companion object {
        private const val KEY_ACTIVE_PRESET_ID = "active_preset_id"
        private const val STATE_SLIDE_INDEX = "state_slide_index"
        private const val STATE_EDITOR_EXPANDED = "state_editor_expanded"
        private const val STATE_EDIT_PRESET_ID = "state_edit_preset_id"
        private const val MIN_WIDTH_DP = 1
        private const val MAX_WIDTH_DP = 160
        private const val MIN_HEIGHT_DP = 24
        private const val MAX_HEIGHT_DP = 220
        private const val MIN_ALPHA_PCT = 0
        private const val MAX_ALPHA_PCT = 100
    }
}
