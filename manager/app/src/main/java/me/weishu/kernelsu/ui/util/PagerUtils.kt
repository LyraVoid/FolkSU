package me.weishu.kernelsu.ui.util

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.PagerState

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
