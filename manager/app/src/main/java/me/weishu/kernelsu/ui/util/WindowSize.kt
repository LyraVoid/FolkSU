package me.weishu.kernelsu.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Whether the automatic navigation mode should pick the side rail, matching FolkPatch:
 * the window must be at least 600dp wide and also wider than it is tall (landscape).
 *
 * Measured with the raw (unscaled) container density, so the page-scale preference does
 * not change which navigation form is chosen.
 */
@Composable
fun shouldUseNavigationRailInAutoMode(): Boolean {
    val windowInfo = LocalWindowInfo.current
    val deviceDensity = LocalResources.current.displayMetrics.density
    val widthDp = windowInfo.containerSize.width / deviceDensity
    val heightDp = windowInfo.containerSize.height / deviceDensity
    return widthDp >= 600f && widthDp > heightDp
}
