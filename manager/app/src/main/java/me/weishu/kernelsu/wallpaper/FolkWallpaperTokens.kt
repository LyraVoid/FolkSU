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
 * Maps the user-facing opacity preference (0..1, "surface carrying strength") onto concrete role
 * alphas. The roles keep a fixed hierarchy gap so they stay distinguishable even though a wallpaper
 * cannot express elevation through the usual surface color steps.
 */
fun resolveFolkWallpaperTokens(colorScheme: ColorScheme, opacity: Float): FolkWallpaperTokens {
    val strength = opacity.coerceIn(0f, 1f)
    val groupAlpha = 0.32f + 0.32f * strength
    val raisedAlpha = (groupAlpha + 0.16f).coerceAtMost(0.94f)
    // Chrome always has scrolled content passing underneath it, so it needs a near-opaque platform:
    // even at 0.9 a dark row bleeds through as a ghost and collides with the bar labels. Without a
    // backdrop blur, integration comes from the gradient tail instead of from a low alpha.
    val chromeAlpha = (0.96f + 0.03f * strength).coerceAtMost(0.99f)
    return FolkWallpaperTokens(
        group = WallpaperMaterial(
            tint = colorScheme.surface,
            alpha = groupAlpha,
            contentColor = colorScheme.onSurface,
            supportingColor = colorScheme.onSurfaceVariant,
        ),
        raised = WallpaperMaterial(
            tint = colorScheme.surface,
            alpha = raisedAlpha,
            contentColor = colorScheme.onSurface,
            supportingColor = colorScheme.onSurfaceVariant,
        ),
        overlay = WallpaperMaterial(
            tint = colorScheme.surfaceContainerHigh,
            alpha = 0.96f,
            contentColor = colorScheme.onSurface,
            supportingColor = colorScheme.onSurfaceVariant,
        ),
        chrome = WallpaperMaterial(
            tint = colorScheme.surface,
            alpha = chromeAlpha,
            contentColor = colorScheme.onSurface,
            supportingColor = colorScheme.onSurfaceVariant,
        ),
    )
}
