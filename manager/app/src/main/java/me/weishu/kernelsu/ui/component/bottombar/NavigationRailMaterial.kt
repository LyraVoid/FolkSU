package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.ui.LocalMainPagerState
import me.weishu.kernelsu.ui.component.material.ChromeEdge
import me.weishu.kernelsu.ui.component.material.WallpaperChromeZone

/**
 * Side navigation rail matching the FolkPatch rail: an icon-only [NavigationRail] whose items are
 * vertically centered. It is fixed — there is no collapse control and no label-bearing wide
 * variant — so the side bar looks the same on phones and tablets.
 *
 * In wallpaper mode it shares the docked bottom bar's rule ([BottomBarControl]): the rail turns
 * transparent so the wallpaper reads through, and a [ChromeEdge.Start] scrim softly fades it into
 * the content instead of ending in a hard color band.
 */
@Composable
fun NavigationRailMaterial(
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
) {
    if (!Natives.isFullFeatured()) return

    val mainPagerState = LocalMainPagerState.current
    val railStyle = BottomBarControl.style(BottomBarLayout.Rail)
    WallpaperChromeZone(
        edge = ChromeEdge.Start,
        modifier = modifier,
        material = railStyle.scrim,
    ) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = railStyle.containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            windowInsets = railWindowInsets(),
        ) {
            Spacer(Modifier.weight(1f))
            BottomBarDestination.entries.forEachIndexed { index, destination ->
                val selected = mainPagerState.selectedPage == index
                NavigationRailItem(
                    selected = selected,
                    onClick = {
                        if (!selected) {
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
                    label = { Text(stringResource(destination.label)) },
                    alwaysShowLabel = false,
                    colors = railStyle.indicatorColor?.let {
                        NavigationRailItemDefaults.colors(indicatorColor = it)
                    } ?: NavigationRailItemDefaults.colors(),
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun railWindowInsets(): WindowInsets =
    WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
