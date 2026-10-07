package me.weishu.kernelsu.wallpaper.surface

import androidx.compose.runtime.Immutable

/**
 * The configurable families a surface may expose.
 *
 * A [SurfaceDescriptor] lists the fields it supports; the settings host then renders exactly those
 * rows, so a surface never shows a control that has no effect on it.
 */
enum class SurfaceField {
    Image,
    Enabled,
    Opacity,
    DualOpacity,
    DayOpacity,
    NightOpacity,
    Dim,
    DualDim,
    DayDim,
    NightDim,
}

/**
 * Extra boolean toggles a surface may expose beyond the image/dim/opacity families.
 *
 * These generalize the grid work card's original hide switches so any surface can hide parts of
 * its own content.
 */
enum class SurfaceFlag { HideIcon, HideText, HideMode }

/**
 * The independently stored values of one surface.
 *
 * v1 does not resolve a scope chain: each surface persists and reads its own [SurfaceConfig] with
 * no fallback to a broader scope. The defaults match the historical work-card values, so a surface
 * that has never been written reads back the previous behavior.
 */
@Immutable
data class SurfaceConfig(
    val imageUri: String? = null,
    val enabled: Boolean = false,
    val opacity: Float = 1f,
    val dualOpacity: Boolean = false,
    val dayOpacity: Float = 1f,
    val nightOpacity: Float = 1f,
    val dim: Float = 0.3f,
    val dualDim: Boolean = false,
    val dayDim: Float = 0.3f,
    val nightDim: Float = 0.3f,
    val flags: Set<SurfaceFlag> = emptySet(),
) {
    /** True when the surface should paint [imageUri] (enabled with a non-empty image). */
    val hasImage: Boolean get() = enabled && !imageUri.isNullOrEmpty()

    fun hasFlag(flag: SurfaceFlag): Boolean = flag in flags

    /** Returns a copy with [flag] added or removed. */
    fun withFlag(flag: SurfaceFlag, value: Boolean): SurfaceConfig =
        copy(flags = if (value) flags + flag else flags - flag)

    /** Alpha for the image, honoring the day/night switch when [dualOpacity] is on. */
    fun effectiveOpacity(isDark: Boolean): Float =
        if (dualOpacity) (if (isDark) nightOpacity else dayOpacity) else opacity

    /** Scrim strength, honoring the day/night switch when [dualDim] is on. */
    fun effectiveDim(isDark: Boolean): Float =
        if (dualDim) (if (isDark) nightDim else dayDim) else dim
}
