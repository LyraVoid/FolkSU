package me.weishu.kernelsu.ui.screen.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole

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
 */
@Immutable
data class HomeWorkCardStyle(
    val containerColor: Color,
    val contentColor: Color?,
    val wallpaperRole: WallpaperSurfaceRole?,
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
    ): HomeWorkCardStyle = when (layout) {
        HomeWorkCardLayout.Circle -> circleStyle(working)
        HomeWorkCardLayout.Grid -> gridStyle(working)
    }

    /**
     * CircleUI. Outside wallpaper mode the card keeps its semantic accent pair; in wallpaper mode
     * it drops to the shared neutral material, because an alpha-reduced accent container loses the
     * contrast of its paired on-colour once a photo shows through.
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
     * GridUI. Investigated only for now: the grid home card is not wired to wallpaper transparency
     * yet, so it keeps its semantic accent pair exactly as before.
     */
    @Composable
    private fun gridStyle(working: Boolean): HomeWorkCardStyle {
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
