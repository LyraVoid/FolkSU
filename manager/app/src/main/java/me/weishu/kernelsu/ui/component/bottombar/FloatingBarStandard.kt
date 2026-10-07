package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Standard floating form: a capsule with an animated selection indicator that slides between
 * destinations. Geometry follows [FloatingBarConfig.compact]; the indicator gains a gentle
 * inflation when [FloatingBarConfig.glass] is on.
 */
@Composable
internal fun FloatingBarStandard(
    destinations: List<BottomBarDestination>,
    selectedIndex: Int,
    badge: (BottomBarDestination) -> NavBadge?,
    onSelect: (BottomBarDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (destinations.isEmpty()) return
    val compact = FloatingBarConfig.compact
    val glassEnabled = FloatingBarConfig.glass
    val itemSize = if (compact) 52.dp else 56.dp
    val itemSpacing = if (compact) 6.dp else 4.dp
    val containerPadding = if (compact) 8.dp else 7.dp
    val barHeight = if (compact) 68.dp else 72.dp
    val itemShape = if (compact || glassEnabled) CircleShape else MaterialTheme.shapes.large
    val density = LocalDensity.current

    val indicatorPadding by animateDpAsState(
        targetValue = if (glassEnabled) 3.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "floatingIndicatorPadding",
    )
    val indicatorScale by animateFloatAsState(
        targetValue = if (glassEnabled) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "floatingIndicatorScale",
    )
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "floatingIndicatorIndex",
    )

    val navBarWidth = itemSize * destinations.size + itemSpacing * (destinations.size - 1) + containerPadding * 2

    Box(modifier = modifier.width(navBarWidth).height(barHeight)) {
        Box(Modifier.fillMaxSize().padding(horizontal = containerPadding)) {
            val itemSizePx = with(density) { itemSize.toPx() }
            val spacingPx = with(density) { itemSpacing.toPx() }
            val indicatorPaddingPx = with(density) { indicatorPadding.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 8.dp)
                    .offset {
                        IntOffset(
                            x = ((itemSizePx + spacingPx) * animatedIndex - indicatorPaddingPx).roundToInt(),
                            y = 0,
                        )
                    }
                    .width(itemSize + indicatorPadding * 2)
                    .graphicsLayer {
                        scaleX = indicatorScale
                        scaleY = indicatorScale
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = indicatorPadding)
                        .width(itemSize)
                        .background(color = MaterialTheme.colorScheme.secondaryContainer, shape = itemShape),
                )
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEachIndexed { index, destination ->
                    val selected = index == selectedIndex
                    val navBadge = badge(destination)
                    val tint = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .size(itemSize)
                            .clip(itemShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { if (!selected) onSelect(destination) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (navBadge != null) {
                            BadgedBox(badge = { FloatingBadge(navBadge) }) {
                                NavBarIcon(destination, selected, tint = tint)
                            }
                        } else {
                            NavBarIcon(destination, selected, tint = tint)
                        }
                    }
                }
            }
        }
    }
}
