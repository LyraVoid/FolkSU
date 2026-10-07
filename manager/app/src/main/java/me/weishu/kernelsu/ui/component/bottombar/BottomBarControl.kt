package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import me.weishu.kernelsu.wallpaper.FolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.WallpaperMaterial
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole

/**
 * Which bottom-navigation form the app is rendering. Adding a form means adding a case here and a
 * branch in [BottomBarControl], instead of patching the bar composable in place.
 */
enum class BottomBarLayout {
    /** Today's full-width bar pinned to the bottom edge. */
    Docked,

    /** Reserved for the upcoming floating bar (悬浮底栏). */
    Floating,

    /** The side navigation rail hugging the start edge. */
    Rail,
}

/**
 * The resolved bottom-bar surface.
 *
 * [containerColor] is the fill behind the bar itself; `Color.Transparent` means the wallpaper shows
 * through. [scrim] is the soft gradient painted above the bar so scrolling content fades out
 * instead of hard-cutting at the bar edge; it is `null` when no scrim is wanted.
 *
 * [indicatorColor] is the selected item's selection pill. `null` keeps the Material default; in
 * wallpaper mode the bar sets it transparent, because the selected item already switches to the
 * filled icon and a second background block would only re-introduce a floating layer over the photo.
 */
@Immutable
data class BottomBarStyle(
    val containerColor: Color,
    val scrim: WallpaperMaterial?,
    val indicatorColor: Color?,
)

/**
 * The single place where the bottom bar's surface is decided, so the docked bar, the future floating
 * bar and the wide navigation rail can each keep their own responsibility while sharing one rule.
 * Same shape as the home work-card control.
 */
object BottomBarControl {
    @Composable
    fun style(layout: BottomBarLayout): BottomBarStyle {
        val tokens = LocalFolkWallpaperTokens.current
        return when (layout) {
            BottomBarLayout.Docked -> dockedStyle(tokens)
            BottomBarLayout.Floating -> floatingStyle(tokens)
            // The rail follows the docked bar's rule exactly: it is the same "chrome" surface, only
            // laid out along the start edge instead of the bottom, so the wallpaper reads through it.
            BottomBarLayout.Rail -> dockedStyle(tokens)
        }
    }

    /**
     * Outside wallpaper mode the bar keeps its original opaque container. In wallpaper mode it turns
     * transparent so the wallpaper shows through, and only the chrome scrim softens the edge. The
     * scrim is deliberately the [FolkWallpaperTokens.chrome] material rather than the panel one: the
     * bar's labels sit over scrolling content, so the chrome keeps a small alpha floor while panels
     * stay free to fade all the way out.
     */
    @Composable
    private fun dockedStyle(tokens: FolkWallpaperTokens?): BottomBarStyle = if (tokens == null) {
        BottomBarStyle(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrim = null,
            indicatorColor = null,
        )
    } else {
        BottomBarStyle(
            containerColor = Color.Transparent,
            scrim = tokens.chrome,
            indicatorColor = Color.Transparent,
        )
    }

    /**
     * Not built yet. When the floating bar lands it will carry its own pill surface, so this branch
     * is where that shape's container material will be resolved.
     */
    @Composable
    private fun floatingStyle(tokens: FolkWallpaperTokens?): BottomBarStyle = BottomBarStyle(
        containerColor = if (tokens == null) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            tokens.material(WallpaperSurfaceRole.Raised).fill
        },
        scrim = null,
        indicatorColor = null,
    )
}
