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

    const val DEFAULT_OPACITY = 0.6f
    const val DEFAULT_DIM = 0.2f
    const val DEFAULT_NIGHT_DIM = 0.4f

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

    /** URI that should currently be painted, or null when the wallpaper is off. */
    val activeUri: String? get() = if (enabled) uri else null

    val isActive: Boolean get() = enabled && !uri.isNullOrEmpty()

    fun effectiveDim(isDark: Boolean): Float =
        if (dualDimEnabled) (if (isDark) nightDim else dayDim) else dim

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
    }
}
