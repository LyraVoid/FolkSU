package me.weishu.kernelsu.wallpaper

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.edit
import me.weishu.kernelsu.ksuApp
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig
import me.weishu.kernelsu.wallpaper.surface.SurfaceFlag
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import me.weishu.kernelsu.wallpaper.surface.SurfaceStore

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

    private const val KEY_MULTI_BACKGROUND_ENABLED = "multi_background_enabled"
    private const val KEY_HOME_BACKGROUND_URI = "home_background_uri"
    private const val KEY_SUPERUSER_BACKGROUND_URI = "superuser_background_uri"
    private const val KEY_MODULE_BACKGROUND_URI = "module_background_uri"
    private const val KEY_SETTINGS_BACKGROUND_URI = "settings_background_uri"
    private const val KEY_HOME_BACKGROUND_LUMINANCE = "home_background_luminance"
    private const val KEY_HOME_BACKGROUND_SEED = "home_background_seed"
    private const val KEY_SUPERUSER_BACKGROUND_LUMINANCE = "superuser_background_luminance"
    private const val KEY_SUPERUSER_BACKGROUND_SEED = "superuser_background_seed"
    private const val KEY_MODULE_BACKGROUND_LUMINANCE = "module_background_luminance"
    private const val KEY_MODULE_BACKGROUND_SEED = "module_background_seed"
    private const val KEY_SETTINGS_BACKGROUND_LUMINANCE = "settings_background_luminance"
    private const val KEY_SETTINGS_BACKGROUND_SEED = "settings_background_seed"

    const val DEFAULT_OPACITY = 0.6f
    const val DEFAULT_DIM = 0.2f
    const val DEFAULT_NIGHT_DIM = 0.4f

    /** Main pager indexes used to key the per-page backgrounds. */
    const val PAGE_HOME = 0
    const val PAGE_SUPERUSER = 1
    const val PAGE_MODULE = 2
    const val PAGE_SETTINGS = 3

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

    /**
     * Master switch for per-page backgrounds. When true each main page paints its own image;
     * a page without one simply shows nothing. When false [uri] is used for every page.
     */
    var multiBackgroundEnabled by mutableStateOf(false)
        private set

    var homeBackgroundUri by mutableStateOf<String?>(null)
        private set
    var superuserBackgroundUri by mutableStateOf<String?>(null)
        private set
    var moduleBackgroundUri by mutableStateOf<String?>(null)
        private set
    var settingsBackgroundUri by mutableStateOf<String?>(null)
        private set

    /** Per-page luminance in 0..1; -1f means "unknown". */
    var homeBackgroundLuminance by mutableFloatStateOf(-1f)
        private set
    var superuserBackgroundLuminance by mutableFloatStateOf(-1f)
        private set
    var moduleBackgroundLuminance by mutableFloatStateOf(-1f)
        private set
    var settingsBackgroundLuminance by mutableFloatStateOf(-1f)
        private set

    /** Per-page ARGB seed; 0 means "not derived". */
    var homeBackgroundSeed by mutableIntStateOf(0)
        private set
    var superuserBackgroundSeed by mutableIntStateOf(0)
        private set
    var moduleBackgroundSeed by mutableIntStateOf(0)
        private set
    var settingsBackgroundSeed by mutableIntStateOf(0)
        private set

    /**
     * The grid work card's background surface. Persistence lives in [SurfaceStore]; WallpaperConfig
     * only exposes the slot so existing callers keep their names.
     */
    val workCardSurface: SurfaceConfig get() = SurfaceStore.config(SurfaceRegistry.GRID_WORK_CARD)

    /** Optional image painted as the background of the grid work card. */
    val workCardBackgroundUri: String? get() = workCardSurface.imageUri
    val workCardBackgroundEnabled: Boolean get() = workCardSurface.enabled

    /** Alpha applied to the work-card image; 1.0 keeps it fully opaque. */
    val workCardOpacity: Float get() = workCardSurface.opacity

    /** Black scrim over the work-card image for text readability. */
    val workCardDim: Float get() = workCardSurface.dim
    val workCardDualOpacityEnabled: Boolean get() = workCardSurface.dualOpacity
    val workCardDayOpacity: Float get() = workCardSurface.dayOpacity
    val workCardNightOpacity: Float get() = workCardSurface.nightOpacity

    val workCardCheckHidden: Boolean get() = workCardSurface.hasFlag(SurfaceFlag.HideIcon)
    val workCardTextHidden: Boolean get() = workCardSurface.hasFlag(SurfaceFlag.HideText)
    val workCardModeHidden: Boolean get() = workCardSurface.hasFlag(SurfaceFlag.HideMode)

    /** The single wallpaper URI, or null when the wallpaper is off. The layer picks per page. */
    val activeUri: String? get() = if (enabled) uri else null

    /**
     * Whether wallpaper mode is on for the app. The master [enabled] switch gates every mode: with
     * it off nothing is active, even if a per-page image is still remembered. In multi mode it is
     * enough that any page has an image, because the theme is global even though the painted image
     * is per page.
     */
    val isActive: Boolean
        get() = (me.weishu.kernelsu.media.VisualMediaConfig.videoEnabled &&
            me.weishu.kernelsu.media.VisualMediaConfig.videoFilename != null) || enabled && if (multiBackgroundEnabled) {
            listOf(homeBackgroundUri, superuserBackgroundUri, moduleBackgroundUri, settingsBackgroundUri)
                .any { !it.isNullOrEmpty() }
        } else {
            !uri.isNullOrEmpty()
        }

    /**
     * Background URI to paint for [page] (0..3), or null when the master switch is off. In multi
     * mode only that page's own URI applies.
     */
    fun pageUri(page: Int): String? =
        if (!enabled) null else if (multiBackgroundEnabled) pageOwnUri(page) else uri

    /** Background luminance to adapt against for [page] (0..3); -1f when the master switch is off. */
    fun pageLuminance(page: Int): Float =
        if (!enabled) -1f else if (multiBackgroundEnabled) pageOwnLuminance(page) else derivedLuminance

    /** Background seed color for [page] (0..3); 0 when the master switch is off. */
    fun pageSeed(page: Int): Int =
        if (!enabled) 0 else if (multiBackgroundEnabled) pageOwnSeed(page) else derivedSeed

    private fun pageOwnUri(page: Int): String? = when (page) {
        PAGE_HOME -> homeBackgroundUri
        PAGE_SUPERUSER -> superuserBackgroundUri
        PAGE_MODULE -> moduleBackgroundUri
        PAGE_SETTINGS -> settingsBackgroundUri
        else -> null
    }

    private fun pageOwnLuminance(page: Int): Float = when (page) {
        PAGE_HOME -> homeBackgroundLuminance
        PAGE_SUPERUSER -> superuserBackgroundLuminance
        PAGE_MODULE -> moduleBackgroundLuminance
        PAGE_SETTINGS -> settingsBackgroundLuminance
        else -> -1f
    }

    private fun pageOwnSeed(page: Int): Int = when (page) {
        PAGE_HOME -> homeBackgroundSeed
        PAGE_SUPERUSER -> superuserBackgroundSeed
        PAGE_MODULE -> moduleBackgroundSeed
        PAGE_SETTINGS -> settingsBackgroundSeed
        else -> 0
    }

    /** True when the grid work card should paint its image instead of an accent fill. */
    val hasWorkCardBackground: Boolean get() = workCardSurface.hasImage

    fun effectiveDim(isDark: Boolean): Float =
        if (dualDimEnabled) (if (isDark) nightDim else dayDim) else dim

    fun effectiveWorkCardOpacity(isDark: Boolean): Float = workCardSurface.effectiveOpacity(isDark)

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

    fun updateMultiBackgroundEnabled(value: Boolean) {
        multiBackgroundEnabled = value
    }

    fun updateHomeBackgroundUri(value: String?) {
        homeBackgroundUri = value
    }

    fun updateSuperuserBackgroundUri(value: String?) {
        superuserBackgroundUri = value
    }

    fun updateModuleBackgroundUri(value: String?) {
        moduleBackgroundUri = value
    }

    fun updateSettingsBackgroundUri(value: String?) {
        settingsBackgroundUri = value
    }

    fun updateHomeBackgroundLuminance(value: Float) {
        homeBackgroundLuminance = value
    }

    fun updateSuperuserBackgroundLuminance(value: Float) {
        superuserBackgroundLuminance = value
    }

    fun updateModuleBackgroundLuminance(value: Float) {
        moduleBackgroundLuminance = value
    }

    fun updateSettingsBackgroundLuminance(value: Float) {
        settingsBackgroundLuminance = value
    }

    fun updateHomeBackgroundSeed(value: Int) {
        homeBackgroundSeed = value
    }

    fun updateSuperuserBackgroundSeed(value: Int) {
        superuserBackgroundSeed = value
    }

    fun updateModuleBackgroundSeed(value: Int) {
        moduleBackgroundSeed = value
    }

    fun updateSettingsBackgroundSeed(value: Int) {
        settingsBackgroundSeed = value
    }

    fun updateWorkCardBackgroundUri(value: String?) =
        SurfaceStore.update(SurfaceRegistry.GRID_WORK_CARD) { it.copy(imageUri = value) }

    fun updateWorkCardBackgroundEnabled(value: Boolean) =
        SurfaceStore.update(SurfaceRegistry.GRID_WORK_CARD) { it.copy(enabled = value) }

    fun updateWorkCardOpacity(value: Float) =
        SurfaceStore.update(SurfaceRegistry.GRID_WORK_CARD) { it.copy(opacity = value.coerceIn(0f, 1f)) }

    fun updateWorkCardDim(value: Float) =
        SurfaceStore.update(SurfaceRegistry.GRID_WORK_CARD) { it.copy(dim = value.coerceIn(0f, 1f)) }

    fun updateWorkCardDualOpacityEnabled(value: Boolean) =
        SurfaceStore.update(SurfaceRegistry.GRID_WORK_CARD) { it.copy(dualOpacity = value) }

    fun updateWorkCardDayOpacity(value: Float) =
        SurfaceStore.update(SurfaceRegistry.GRID_WORK_CARD) { it.copy(dayOpacity = value.coerceIn(0f, 1f)) }

    fun updateWorkCardNightOpacity(value: Float) =
        SurfaceStore.update(SurfaceRegistry.GRID_WORK_CARD) { it.copy(nightOpacity = value.coerceIn(0f, 1f)) }

    fun updateWorkCardCheckHidden(value: Boolean) =
        SurfaceStore.update(SurfaceRegistry.GRID_WORK_CARD) { it.withFlag(SurfaceFlag.HideIcon, value) }

    fun updateWorkCardTextHidden(value: Boolean) =
        SurfaceStore.update(SurfaceRegistry.GRID_WORK_CARD) { it.withFlag(SurfaceFlag.HideText, value) }

    fun updateWorkCardModeHidden(value: Boolean) =
        SurfaceStore.update(SurfaceRegistry.GRID_WORK_CARD) { it.withFlag(SurfaceFlag.HideMode, value) }

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
        multiBackgroundEnabled = prefs.getBoolean(KEY_MULTI_BACKGROUND_ENABLED, false)
        homeBackgroundUri = prefs.getString(KEY_HOME_BACKGROUND_URI, null)
        superuserBackgroundUri = prefs.getString(KEY_SUPERUSER_BACKGROUND_URI, null)
        moduleBackgroundUri = prefs.getString(KEY_MODULE_BACKGROUND_URI, null)
        settingsBackgroundUri = prefs.getString(KEY_SETTINGS_BACKGROUND_URI, null)
        homeBackgroundLuminance = prefs.getFloat(KEY_HOME_BACKGROUND_LUMINANCE, -1f)
        superuserBackgroundLuminance = prefs.getFloat(KEY_SUPERUSER_BACKGROUND_LUMINANCE, -1f)
        moduleBackgroundLuminance = prefs.getFloat(KEY_MODULE_BACKGROUND_LUMINANCE, -1f)
        settingsBackgroundLuminance = prefs.getFloat(KEY_SETTINGS_BACKGROUND_LUMINANCE, -1f)
        homeBackgroundSeed = prefs.getInt(KEY_HOME_BACKGROUND_SEED, 0)
        superuserBackgroundSeed = prefs.getInt(KEY_SUPERUSER_BACKGROUND_SEED, 0)
        moduleBackgroundSeed = prefs.getInt(KEY_MODULE_BACKGROUND_SEED, 0)
        settingsBackgroundSeed = prefs.getInt(KEY_SETTINGS_BACKGROUND_SEED, 0)
        SurfaceStore.load(context)
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
            putBoolean(KEY_MULTI_BACKGROUND_ENABLED, multiBackgroundEnabled)
            putString(KEY_HOME_BACKGROUND_URI, homeBackgroundUri)
            putString(KEY_SUPERUSER_BACKGROUND_URI, superuserBackgroundUri)
            putString(KEY_MODULE_BACKGROUND_URI, moduleBackgroundUri)
            putString(KEY_SETTINGS_BACKGROUND_URI, settingsBackgroundUri)
            putFloat(KEY_HOME_BACKGROUND_LUMINANCE, homeBackgroundLuminance)
            putFloat(KEY_SUPERUSER_BACKGROUND_LUMINANCE, superuserBackgroundLuminance)
            putFloat(KEY_MODULE_BACKGROUND_LUMINANCE, moduleBackgroundLuminance)
            putFloat(KEY_SETTINGS_BACKGROUND_LUMINANCE, settingsBackgroundLuminance)
            putInt(KEY_HOME_BACKGROUND_SEED, homeBackgroundSeed)
            putInt(KEY_SUPERUSER_BACKGROUND_SEED, superuserBackgroundSeed)
            putInt(KEY_MODULE_BACKGROUND_SEED, moduleBackgroundSeed)
            putInt(KEY_SETTINGS_BACKGROUND_SEED, settingsBackgroundSeed)
        }
        SurfaceStore.save(context)
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
        multiBackgroundEnabled = false
        homeBackgroundUri = null
        superuserBackgroundUri = null
        moduleBackgroundUri = null
        settingsBackgroundUri = null
        homeBackgroundLuminance = -1f
        superuserBackgroundLuminance = -1f
        moduleBackgroundLuminance = -1f
        settingsBackgroundLuminance = -1f
        homeBackgroundSeed = 0
        superuserBackgroundSeed = 0
        moduleBackgroundSeed = 0
        settingsBackgroundSeed = 0
        SurfaceStore.reset()
    }
}

/**
 * The settled main pager index (0..3) that wallpaper lookups resolve against.
 *
 * Provided once at the activity root so the theme and the background layer agree on the current
 * page without threading the index through every composable.
 */
val LocalWallpaperPage = staticCompositionLocalOf { 0 }
