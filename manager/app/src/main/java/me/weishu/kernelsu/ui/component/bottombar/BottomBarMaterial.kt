package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import me.weishu.kernelsu.ui.util.useFullFeaturedLayout
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.ui.component.material.ChromeEdge
import me.weishu.kernelsu.ui.component.material.WallpaperChromeZone

@Composable
fun BottomBarMaterial(navigationBadge: NavigationBadgeState) {
    val fullFeatured = useFullFeaturedLayout()
    if (!fullFeatured) return

    val mainPagerState = LocalMainPagerState.current

    val barStyle = BottomBarControl.style(BottomBarLayout.Docked)
    WallpaperChromeZone(edge = ChromeEdge.Bottom, material = barStyle.scrim) {
        ShortNavigationBar(
            containerColor = barStyle.containerColor,
            windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            )
        ) {
            BottomBarDestination.entries.forEachIndexed { index, destination ->
                val selected = mainPagerState.selectedPage == index
                ShortNavigationBarItem(
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            me.weishu.kernelsu.media.MediaFeedback.navigation()
                            mainPagerState.animateToPage(index)
                        }
                    },
                    icon = {
                        NavigationIconWithBadge(
                            destination = destination,
                            selected = selected,
                            badge = badgeFor(index, navigationBadge),
                        )
                    },
                    label = {
                        Text(
                            stringResource(destination.label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = barStyle.indicatorColor?.let {
                        ShortNavigationBarItemDefaults.colors(selectedIndicatorColor = it)
                    } ?: ShortNavigationBarItemDefaults.colors(),
                )
            }
        }
    }
}

@Composable
internal fun NavigationIconWithBadge(
    destination: BottomBarDestination,
    selected: Boolean,
    badge: NavBadge?,
) {
    if (badge != null) {
        BadgedBox(
            badge = {
                when (badge.tone) {
                    BadgeTone.Alert -> Badge {
                        Text(badge.count.toString())
                    }

                    BadgeTone.Accent -> Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(badge.count.toString())
                    }
                }
            }
        ) {
            NavBarIcon(destination, selected)
        }
    } else {
        NavBarIcon(destination, selected)
    }
}
