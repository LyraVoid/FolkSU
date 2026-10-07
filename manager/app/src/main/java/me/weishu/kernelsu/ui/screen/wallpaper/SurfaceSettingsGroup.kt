package me.weishu.kernelsu.ui.screen.wallpaper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.component.material.FolkWallpaperSurface
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole

/**
 * One surface's settings card inside the wallpaper settings screen.
 *
 * Keeps the visual frame (wallpaper-backed group, section title) in a single place so every
 * surface rendered by [SurfaceSettingsHost] looks identical to the historical work-card block.
 */
@Composable
fun SurfaceSettingsGroup(
    titleRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    FolkWallpaperSurface(
        role = WallpaperSurfaceRole.Group,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        shape = MaterialTheme.shapes.large,
        fallbackColor = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}
