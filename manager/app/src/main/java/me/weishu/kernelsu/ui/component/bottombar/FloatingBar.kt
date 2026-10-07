package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fletchmckee.liquid.LiquidState
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.ui.LocalMainPagerState

/** Vertical space the floating bar occupies from the screen bottom, excluding system insets. */
fun floatingBarReservedHeight(compact: Boolean, style: FloatingBarConfig.Style): Dp =
    (if (style == FloatingBarConfig.Style.Drawer) 64.dp else if (compact) 68.dp else 72.dp) + 32.dp

/**
 * Floating bottom navigation bar rendered above the page content.
 *
 * Two forms ([FloatingBarConfig.Style]): [Standard][FloatingBarStandard] capsule and the
 * expanding [Drawer][FloatingBarDrawer]. With [FloatingBarConfig.glass] the surface becomes a
 * frosted-glass panel (real-time blur on Android 13+) linked to [liquidState].
 */
@Composable
internal fun FloatingBar(
    navigationBadge: NavigationBadgeState,
    liquidState: LiquidState?,
    modifier: Modifier = Modifier,
) {
    if (!Natives.isFullFeatured()) return
    val mainPagerState = LocalMainPagerState.current
    val revision by FloatingBarConfig.revision.collectAsStateWithLifecycle()
    val style = remember(revision) { FloatingBarConfig.style }
    val compact = remember(revision) { FloatingBarConfig.compact }
    val useGlass = remember(revision) { FloatingBarConfig.glass } && liquidState != null
    val destinations = BottomBarDestination.entries
    val selectedIndex = mainPagerState.selectedPage
    val containerColor = BottomBarControl.style(BottomBarLayout.Floating).containerColor

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
    ) {
        val horizontalPadding = when {
            maxWidth > 600.dp -> 32.dp
            maxWidth > 400.dp -> 24.dp
            else -> 16.dp
        }
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val barShape: Shape = when {
                style == FloatingBarConfig.Style.Drawer -> CircleShape
                compact -> RoundedCornerShape(percent = 50)
                useGlass -> CircleShape
                else -> MaterialTheme.shapes.large
            }
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .clip(barShape)
                    .then(
                        if (useGlass) {
                            Modifier.navBarGlassEffect(shape = barShape, liquidState = liquidState)
                        } else {
                            Modifier
                        }
                    ),
                shape = barShape,
                color = if (useGlass) Color.Transparent else containerColor,
                tonalElevation = if (useGlass) 0.dp else 3.dp,
                shadowElevation = if (useGlass) 0.dp else 8.dp,
            ) {
                if (style == FloatingBarConfig.Style.Drawer) {
                    FloatingBarDrawer(
                        destinations = destinations,
                        selectedIndex = selectedIndex,
                        badge = { badgeFor(it.ordinal, navigationBadge) },
                        onSelect = { mainPagerState.animateToPage(it.ordinal) },
                    )
                } else {
                    FloatingBarStandard(
                        destinations = destinations,
                        selectedIndex = selectedIndex,
                        badge = { badgeFor(it.ordinal, navigationBadge) },
                        onSelect = { mainPagerState.animateToPage(it.ordinal) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun FloatingBadge(badge: NavBadge) {
    when (badge.tone) {
        BadgeTone.Alert -> Badge { Text(badge.count.toString()) }
        BadgeTone.Accent -> Badge(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) { Text(badge.count.toString()) }
    }
}
