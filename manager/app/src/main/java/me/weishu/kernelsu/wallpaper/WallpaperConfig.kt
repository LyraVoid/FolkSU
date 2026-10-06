package me.weishu.kernelsu.wallpaper

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import me.weishu.kernelsu.ksuApp

/**
 * Snapshot state for the single custom wallpaper.
 *
 * Fields are Compose state so that reading them inside composition (theme, background layer,
 * settings screen) reacts to edits without a ViewModel. Persistence lives in a dedicated
 * prefs file; callers that mutate a field are responsible for invoking [save].
 */
object WallpaperConfig {

    private const val PREFS_NAME = "wallpaper"
    private const val KEY_ENABLED = "wallpaper_enabled"
    private const val KEY_URI = "wallpaper_uri"
    private const val KEY_OPACITY = "wallpaper_opacity"
    private const val KEY_BLUR = "wallpaper_blur"
    private const val KEY_DIM = "wallpaper_dim"
    private const val KEY_DUAL_DIM = "wallpaper_dual_dim_enabled"
    private const val KEY_DAY_DIM = "wallpaper_day_dim"
    private const val KEY_NIGHT_DIM = "wallpaper_night_dim"
    private const val KEY_SEED = "wallpaper_derived_seed"
    private const val KEY_LUMINANCE = "wallpaper_derived_luminance"
    private const val KEY_USE_COLOR = "wallpaper_use_color"

    private const val KEY_WORK_CARD_BACKGROUND_URI = "work_card_background_uri"
    private const val KEY_WORK_CARD_BACKGROUND_ENABLED = "work_card_background_enabled"
    private const val KEY_WORK_CARD_OPACITY = "work_card_opacity"
    private const val KEY_WORK_CARD_DIM = "work_card_dim"
    private const val KEY_WORK_CARD_DUAL_OPACITY_ENABLED = "work_card_dual_opacity_enabled"
    private const val KEY_WORK_CARD_DAY_OPACITY = "work_card_day_opacity"
    private const val KEY_WORK_CARD_NIGHT_OPACITY = "work_card_night_opacity"
    private const val KEY_WORK_CARD_CHECK_HIDDEN = "work_card_check_hidden"
    private const val KEY_WORK_CARD_TEXT_HIDDEN = "work_card_text_hidden"
    private const val KEY_WORK_CARD_MODE_HIDDEN = "work_card_mode_hidden"

    const val DEFAULT_OPACITY = 0.6f
    const val DEFAULT_DIM = 0.2f
    const val DEFAULT_NIGHT_DIM = 0.4f
    const val DEFAULT_WORK_CARD_DIM = 0.3f

    /** Store opaque (1.0) surfaces entirely hide the wallpaper; keep a gentle default. */
    var enabled by mutableStateOf(false)
        private set
    var uri by mutableStateOf<String?>(null)
        private set

    /** Alpha applied to the surface/container roles while the wallpaper is active. */
    var opacity by mutableFloatStateOf(DEFAULT_OPACITY)
        private set

    /** Background blur radius in dp. */
    var blur by mutableFloatStateOf(0f)
        private set

    /** Black scrim over the wallpaper when dual dim is off. */
    var dim by mutableFloatStateOf(DEFAULT_DIM)
        private set
    var dualDimEnabled by mutableStateOf(false)
        private set
    var dayDim by mutableFloatStateOf(DEFAULT_DIM)
        private set
    var nightDim by mutableFloatStateOf(DEFAULT_NIGHT_DIM)
        private set

    /** ARGB seed extracted from the wallpaper; 0 means "not derived". */
    var derivedSeed by mutableIntStateOf(0)
        private set

    /** Perceived luminance of the wallpaper in 0..1; -1f means "unknown". */
    var derivedLuminance by mutableFloatStateOf(-1f)
        private set

    var useWallpaperColor by mutableStateOf(false)
        private set

    /** Optional image painted as the background of the grid work card. */
    var workCardBackgroundUri by mutableStateOf<String?>(null)
        private set
    var workCardBackgroundEnabled by mutableStateOf(false)
        private set

    /** Alpha applied to the work-card image; 1.0 keeps it fully opaque. */
    var workCardOpacity by mutableFloatStateOf(1f)
        private set

    /** Black scrim over the work-card image for text readability. */
    var workCardDim by mutableFloatStateOf(DEFAULT_WORK_CARD_DIM)
        private set
    var workCardDualOpacityEnabled by mutableStateOf(false)
        private set
    var workCardDayOpacity by mutableFloatStateOf(1f)
        private set
    var workCardNightOpacity by mutableFloatStateOf(1f)
        private set

    var workCardCheckHidden by mutableStateOf(false)
        private set
    var workCardTextHidden by mutableStateOf(false)
        private set
    var workCardModeHidden by mutableStateOf(false)
        private set

    /** URI that should currently be painted, or null when the wallpaper is off. */
    val activeUri: String? get() = if (enabled) uri else null

    val isActive: Boolean get() = enabled && !uri.isNullOrEmpty()

    /** True when the grid work card should paint [workCardBackgroundUri] instead of an accent fill. */
    val hasWorkCardBackground: Boolean get() = workCardBackgroundEnabled && !workCardBackgroundUri.isNullOrEmpty()

    fun effectiveDim(isDark: Boolean): Float =
        if (dualDimEnabled) (if (isDark) nightDim else dayDim) else dim

    fun effectiveWorkCardOpacity(isDark: Boolean): Float =
        if (workCardDualOpacityEnabled) {
            if (isDark) workCardNightOpacity else workCardDayOpacity
        } else {
            workCardOpacity
        }

    fun updateEnabled(value: Boolean) {
        enabled = value
    }

    fun updateUri(value: String?) {
        uri = value
    }

    fun updateOpacity(value: Float) {
        opacity = value.coerceIn(0f, 1f)
    }

    fun updateBlur(value: Float) {
        blur = value.coerceIn(0f, 64f)
    }

    fun updateDim(value: Float) {
        dim = value.coerceIn(0f, 1f)
    }

    fun updateDualDimEnabled(value: Boolean) {
        dualDimEnabled = value
    }

    fun updateDayDim(value: Float) {
        dayDim = value.coerceIn(0f, 1f)
    }

    fun updateNightDim(value: Float) {
        nightDim = value.coerceIn(0f, 1f)
    }

    fun updateDerivedSeed(value: Int) {
        derivedSeed = value
    }

    fun updateDerivedLuminance(value: Float) {
        derivedLuminance = value
    }

    fun updateUseWallpaperColor(value: Boolean) {
        useWallpaperColor = value
    }

    fun updateWorkCardBackgroundUri(value: String?) {
        workCardBackgroundUri = value
    }

    fun updateWorkCardBackgroundEnabled(value: Boolean) {
        workCardBackgroundEnabled = value
    }

    fun updateWorkCardOpacity(value: Float) {
        workCardOpacity = value.coerceIn(0f, 1f)
    }

    fun updateWorkCardDim(value: Float) {
        workCardDim = value.coerceIn(0f, 1f)
    }

    fun updateWorkCardDualOpacityEnabled(value: Boolean) {
        workCardDualOpacityEnabled = value
    }

    fun updateWorkCardDayOpacity(value: Float) {
        workCardDayOpacity = value.coerceIn(0f, 1f)
    }

    fun updateWorkCardNightOpacity(value: Float) {
        workCardNightOpacity = value.coerceIn(0f, 1f)
    }

    fun updateWorkCardCheckHidden(value: Boolean) {
        workCardCheckHidden = value
    }

    fun updateWorkCardTextHidden(value: Boolean) {
        workCardTextHidden = value
    }

    fun updateWorkCardModeHidden(value: Boolean) {
        workCardModeHidden = value
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(KEY_ENABLED, false)
        uri = prefs.getString(KEY_URI, null)
        opacity = prefs.getFloat(KEY_OPACITY, DEFAULT_OPACITY)
        blur = prefs.getFloat(KEY_BLUR, 0f)
        dim = prefs.getFloat(KEY_DIM, DEFAULT_DIM)
        dualDimEnabled = prefs.getBoolean(KEY_DUAL_DIM, false)
        dayDim = prefs.getFloat(KEY_DAY_DIM, DEFAULT_DIM)
        nightDim = prefs.getFloat(KEY_NIGHT_DIM, DEFAULT_NIGHT_DIM)
        derivedSeed = prefs.getInt(KEY_SEED, 0)
        derivedLuminance = prefs.getFloat(KEY_LUMINANCE, -1f)
        useWallpaperColor = prefs.getBoolean(KEY_USE_COLOR, false)
        workCardBackgroundUri = prefs.getString(KEY_WORK_CARD_BACKGROUND_URI, null)
        workCardBackgroundEnabled = prefs.getBoolean(KEY_WORK_CARD_BACKGROUND_ENABLED, false)
        workCardOpacity = prefs.getFloat(KEY_WORK_CARD_OPACITY, 1f)
        workCardDim = prefs.getFloat(KEY_WORK_CARD_DIM, DEFAULT_WORK_CARD_DIM)
        workCardDualOpacityEnabled = prefs.getBoolean(KEY_WORK_CARD_DUAL_OPACITY_ENABLED, false)
        workCardDayOpacity = prefs.getFloat(KEY_WORK_CARD_DAY_OPACITY, 1f)
        workCardNightOpacity = prefs.getFloat(KEY_WORK_CARD_NIGHT_OPACITY, 1f)
        workCardCheckHidden = prefs.getBoolean(KEY_WORK_CARD_CHECK_HIDDEN, false)
        workCardTextHidden = prefs.getBoolean(KEY_WORK_CARD_TEXT_HIDDEN, false)
        workCardModeHidden = prefs.getBoolean(KEY_WORK_CARD_MODE_HIDDEN, false)
    }

    fun save(context: Context = ksuApp) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_ENABLED, enabled)
            putString(KEY_URI, uri)
            putFloat(KEY_OPACITY, opacity)
            putFloat(KEY_BLUR, blur)
            putFloat(KEY_DIM, dim)
            putBoolean(KEY_DUAL_DIM, dualDimEnabled)
            putFloat(KEY_DAY_DIM, dayDim)
            putFloat(KEY_NIGHT_DIM, nightDim)
            putInt(KEY_SEED, derivedSeed)
            putFloat(KEY_LUMINANCE, derivedLuminance)
            putBoolean(KEY_USE_COLOR, useWallpaperColor)
            putString(KEY_WORK_CARD_BACKGROUND_URI, workCardBackgroundUri)
            putBoolean(KEY_WORK_CARD_BACKGROUND_ENABLED, workCardBackgroundEnabled)
            putFloat(KEY_WORK_CARD_OPACITY, workCardOpacity)
            putFloat(KEY_WORK_CARD_DIM, workCardDim)
            putBoolean(KEY_WORK_CARD_DUAL_OPACITY_ENABLED, workCardDualOpacityEnabled)
            putFloat(KEY_WORK_CARD_DAY_OPACITY, workCardDayOpacity)
            putFloat(KEY_WORK_CARD_NIGHT_OPACITY, workCardNightOpacity)
            putBoolean(KEY_WORK_CARD_CHECK_HIDDEN, workCardCheckHidden)
            putBoolean(KEY_WORK_CARD_TEXT_HIDDEN, workCardTextHidden)
            putBoolean(KEY_WORK_CARD_MODE_HIDDEN, workCardModeHidden)
        }
    }

    fun reset() {
        enabled = false
        uri = null
        opacity = DEFAULT_OPACITY
        blur = 0f
        dim = DEFAULT_DIM
        dualDimEnabled = false
        dayDim = DEFAULT_DIM
        nightDim = DEFAULT_NIGHT_DIM
        derivedSeed = 0
        derivedLuminance = -1f
        useWallpaperColor = false
        workCardBackgroundUri = null
        workCardBackgroundEnabled = false
        workCardOpacity = 1f
        workCardDim = DEFAULT_WORK_CARD_DIM
        workCardDualOpacityEnabled = false
        workCardDayOpacity = 1f
        workCardNightOpacity = 1f
        workCardCheckHidden = false
        workCardTextHidden = false
        workCardModeHidden = false
    }
}
