package me.weishu.kernelsu.ui.screen.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig

/** Which home layout owns the work (status) card. */
enum class HomeWorkCardLayout {
    Circle,
    Grid,
}

/**
 * The resolved surface for a home work card.
 *
 * [wallpaperRole] is non-null while wallpaper mode is active, which routes the card through
 * [me.weishu.kernelsu.ui.component.material.FolkWallpaperSurface] so that it shares the app-wide
 * wallpaper transparency. Outside wallpaper mode it is null and [containerColor] is used as a
 * solid fill, exactly as before.
 *
 * [workCardBackgroundUri] is non-null only for the grid layout when the user opted into a photo
 * background. In that case [containerColor] is transparent and the card paints the image itself.
 */
@Immutable
data class HomeWorkCardStyle(
    val containerColor: Color,
    val contentColor: Color?,
    val wallpaperRole: WallpaperSurfaceRole?,
    val workCardBackgroundUri: String? = null,
)

/**
 * Unified control core for the home screen work card.
 *
 * Each home layout keeps its own responsibility in [circleStyle] / [gridStyle]; callers only pass
 * their layout and the current state. This is the single place where the work card's surface is
 * decided, so adding a layout means adding one branch instead of editing that layout's card.
 */
object HomeWorkCardControl {

    @Composable
    fun style(
        layout: HomeWorkCardLayout,
        working: Boolean,
        surface: SurfaceConfig? = null,
    ): HomeWorkCardStyle = when (layout) {
        HomeWorkCardLayout.Circle -> circleStyle(working)

        HomeWorkCardLayout.Grid -> gridStyle(working, surface)
    }

    /**
     * CircleUI, plus the status card of every layout that reads as a list of tiles (Focus,
     * Dashboard and Stats). Outside wallpaper mode the card keeps its semantic accent pair; in
     * wallpaper mode it drops to the shared neutral material, because an alpha-reduced accent
     * container loses the contrast of its paired on-colour once a photo shows through.
     */
    @Composable
    private fun circleStyle(working: Boolean): HomeWorkCardStyle {
        val containerColor = if (working) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
        return if (LocalFolkWallpaperTokens.current != null) {
            HomeWorkCardStyle(
                containerColor = containerColor,
                contentColor = MaterialTheme.colorScheme.onSurface,
                wallpaperRole = WallpaperSurfaceRole.Group,
            )
        } else {
            HomeWorkCardStyle(
                containerColor = containerColor,
                contentColor = null,
                wallpaperRole = null,
            )
        }
    }

    /**
     * GridUI. The card keeps its semantic accent pair by default. When the user enabled a work-card
     * photo, the surface drops to a transparent container with white content so the bitmap painted
     * by the card is what shows through.
     */
    @Composable
    private fun gridStyle(working: Boolean, surface: SurfaceConfig?): HomeWorkCardStyle {
        if (surface?.hasImage == true) {
            return HomeWorkCardStyle(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                wallpaperRole = null,
                workCardBackgroundUri = surface.imageUri,
            )
        }
        val containerColor = if (working) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        }
        return HomeWorkCardStyle(
            containerColor = containerColor,
            contentColor = null,
            wallpaperRole = null,
        )
    }
}
