package me.weishu.kernelsu.ui.screen.wallpaper

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.component.material.SegmentedColumn

/**
 * One surface's settings group inside the wallpaper settings screen.
 *
 * The rows are supplied by [SurfaceSettingsHost] as [SegmentedColumn] content, so every slot picks
 * up the shared grouped wallpaper styling and only lists the controls its descriptor declares.
 */
@Composable
fun SurfaceSettingsGroup(
    titleRes: Int,
    rows: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    SegmentedColumn(
        modifier = modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp),
        title = stringResource(titleRes),
        content = rows,
    )
}
