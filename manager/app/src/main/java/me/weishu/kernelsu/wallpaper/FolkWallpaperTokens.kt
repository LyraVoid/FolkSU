package me.weishu.kernelsu.wallpaper

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Which wallpaper surface role a component is drawing.
 *
 * There is deliberately no `Canvas` role: the canvas is the transparent Scaffold background with
 * no material of its own. A component either draws one of these roles or draws nothing.
 */
enum class WallpaperSurfaceRole { Group, Raised, Overlay }

/**
 * One wallpaper-mode material: an opaque semantic [tint] rendered at [alpha] over the wallpaper,
 * plus the content colors that are known to pair with it.
 */
@Immutable
data class WallpaperMaterial(
    val tint: Color,
    val alpha: Float,
    val contentColor: Color,
    val supportingColor: Color,
) {
    val fill: Color get() = tint.copy(alpha = alpha)
}

/**
 * The wallpaper materials, one per [WallpaperSurfaceRole], plus [chrome] for the top/bottom bars.
 *
 * Held only while wallpaper mode is active (see [LocalFolkWallpaperTokens]); consumers fall back to
 * their original solid colors when it is null.
 */
@Immutable
data class FolkWallpaperTokens(
    val group: WallpaperMaterial,
    val raised: WallpaperMaterial,
    val overlay: WallpaperMaterial,
    val chrome: WallpaperMaterial,
) {
    fun material(role: WallpaperSurfaceRole): WallpaperMaterial = when (role) {
        WallpaperSurfaceRole.Group -> group
        WallpaperSurfaceRole.Raised -> raised
        WallpaperSurfaceRole.Overlay -> overlay
    }
}

/**
 * Null outside wallpaper mode; provided by the theme so components can opt into the wallpaper
 * materials without reading [WallpaperConfig] themselves.
 */
val LocalFolkWallpaperTokens = compositionLocalOf<FolkWallpaperTokens?> { null }

/**
 * How translucent a bar may become: at opacity 0 the chrome still keeps this much of its tint.
 *
 * Panels may fade to nothing, but the top and bottom bars carry labels that sit on top of scrolling
 * content, so they hold a small platform of their own.
 */
private const val CHROME_ALPHA_FLOOR = 0.10f

/**
 * Maps the user-facing opacity preference (0..1) onto the role alphas.
 *
 * The preference *is* the surface alpha: 0 lets the wallpaper show through completely and 1 is
 * fully opaque, so every role tracks the slider one-to-one instead of living in a narrowed band.
 * Two exceptions survive on purpose: [overlay] (dialogs and menus) stays near-opaque, because a popup
 * that lets the content behind it read through is not usable; and [chrome] never drops below
 * [CHROME_ALPHA_FLOOR], because the bars' labels sit over scrolling content.
 */
fun resolveFolkWallpaperTokens(colorScheme: ColorScheme, opacity: Float): FolkWallpaperTokens {
    val alpha = opacity.coerceIn(0f, 1f)
    val panel = WallpaperMaterial(
        tint = colorScheme.surface,
        alpha = alpha,
        contentColor = colorScheme.onSurface,
        supportingColor = colorScheme.onSurfaceVariant,
    )
    return FolkWallpaperTokens(
        group = panel,
        raised = panel.copy(alpha = (alpha + 0.12f).coerceAtMost(1f)),
        overlay = WallpaperMaterial(
            tint = colorScheme.surfaceContainerHigh,
            alpha = 0.96f,
            contentColor = colorScheme.onSurface,
            supportingColor = colorScheme.onSurfaceVariant,
        ),
        chrome = panel.copy(alpha = CHROME_ALPHA_FLOOR + (1f - CHROME_ALPHA_FLOOR) * alpha),
    )
}
