package me.weishu.kernelsu.ui.component.bottombar

import androidx.annotation.StringRes
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.util.shouldUseNavigationRailInAutoMode
import me.weishu.kernelsu.ui.util.springAnimateToPage

class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
    private val animatePageChanges: Boolean,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) return

        navJob?.cancel()

        selectedPage = targetIndex
        isNavigating = true

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                if (animatePageChanges) {
                    pagerState.springAnimateToPage(targetIndex)
                } else {
                    pagerState.scrollToPage(targetIndex)
                }
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    animatePageChanges: Boolean = true,
): MainPagerState {
    return remember(pagerState, coroutineScope, animatePageChanges) {
        MainPagerState(pagerState, coroutineScope, animatePageChanges)
    }
}

@Immutable
data class NavigationBadgeState(
    val superuserCount: Int = 0,
    val moduleEnabledCount: Int = 0,
    val moduleUpdatableCount: Int = 0,
)

enum class BottomBarDestination(
    @get:StringRes val label: Int,
    val iconSelected: ImageVector,
    val iconNotSelected: ImageVector,
    /**
     * Canonical destination name used inside `.fpt` theme packages. Kept aligned with the
     * FolkPatch ecosystem so bottom-bar icons can round-trip between both apps.
     */
    val themeKey: String,
) {
    Home(R.string.home, Icons.Filled.Home, Icons.Outlined.Home, "Home"),
    SuperUser(R.string.superuser, Icons.Filled.Shield, Icons.Outlined.Shield, "SuperUser"),
    Module(R.string.module, Icons.Filled.Extension, Icons.Outlined.Extension, "AModule"),
    Setting(R.string.settings, Icons.Filled.Settings, Icons.Outlined.Settings, "Settings")
}

internal enum class BadgeTone { Alert, Accent }

@Immutable
internal data class NavBadge(val count: Int, val tone: BadgeTone)

internal fun badgeFor(index: Int, state: NavigationBadgeState): NavBadge? = when (index) {
    BottomBarDestination.SuperUser.ordinal ->
        state.superuserCount.takeIf { it > 0 }?.let { NavBadge(it, BadgeTone.Accent) }

    BottomBarDestination.Module.ordinal -> when {
        state.moduleUpdatableCount > 0 -> NavBadge(state.moduleUpdatableCount, BadgeTone.Alert)
        state.moduleEnabledCount > 0 -> NavBadge(state.moduleEnabledCount, BadgeTone.Accent)
        else -> null
    }

    else -> null
}

@Composable
fun useNavigationRail(): Boolean {
    val modeRevision = NavModeConfig.revision.collectAsStateWithLifecycle().value
    val mode = remember(modeRevision) { NavModeConfig.mode }
    return when (mode) {
        NavMode.Rail -> true
        NavMode.Bottom, NavMode.Floating -> false
        NavMode.Auto -> shouldUseNavigationRailInAutoMode()
    }
}

@Composable
fun BottomBar(navigationBadge: NavigationBadgeState) {
    BottomBarMaterial(navigationBadge)
}

@Composable
fun SideRail(
    navigationBadge: NavigationBadgeState,
    modifier: Modifier = Modifier,
) {
    NavigationRailMaterial(navigationBadge, modifier)
}
