package me.weishu.kernelsu.ui.util

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Spring animation spec used when snapping between main pager pages.
 * Local replacement for the former upstream pager utils.
 */
val PagerNavigationSpringSpec: SpringSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * Animate the pager to [page] using [PagerNavigationSpringSpec].
 */
suspend fun PagerState.springAnimateToPage(page: Int) {
    animateScrollToPage(page = page, animationSpec = PagerNavigationSpringSpec)
}

/**
 * How pager gestures are intercepted.
 *
 * ORDINALS ARE PERSISTED (serialized as an Int in settings and used across
 * `SettingsViewModel.setPagerInterceptionMode`, `ColorPaletteUiState` and
 * `MainActivity`), so names, order and therefore ordinals must not change.
 */
enum class PagerInterceptionMode { Native, CrossAxisInterceptor }

/**
 * Best-effort local replacement for the upstream pager gesture override modifier.
 * The former implementation rewired pointer input for cross-axis interception;
 * we now rely on the pager's native gesture handling in both modes.
 */
@Composable
fun Modifier.pagerGestureOverride(
    pagerState: PagerState,
    mode: PagerInterceptionMode,
    enabled: Boolean,
): Modifier = this
