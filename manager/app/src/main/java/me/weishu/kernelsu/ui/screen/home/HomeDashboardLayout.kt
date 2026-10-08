package me.weishu.kernelsu.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.Natives
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.theme.FolkType
import me.weishu.kernelsu.wallpaper.WallpaperSurfaceRole
import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import me.weishu.kernelsu.wallpaper.surface.SurfaceStore

/**
 * DashboardUI: one wide hero banner over the counters and the system facts.
 *
 * The facts card is the one every layout ends with, so the wide form only has to decide whether it
 * sits beside the counters or under them.
 */
@Composable
internal fun DashboardHomeContent(
    state: HomeUiState,
    actions: HomeActions,
    superuserCount: Int,
    moduleEnabledCount: Int,
) {
    val fullFeatured = Natives.isFullFeatured()
    val heroBackground = SurfaceStore.config(SurfaceRegistry.DASHBOARD_HERO).takeIf { it.hasImage }
    Column(verticalArrangement = Arrangement.spacedBy(TileSpacing)) {
        DashboardHeroCard(state = state, actions = actions, background = heroBackground)
        if (fullFeatured && isWideLayout(withOrientation = false)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(TileSpacing),
            ) {
                CountCardPair(
                    superuserCount = superuserCount,
                    moduleEnabledCount = moduleEnabledCount,
                    onOpenSuperUser = actions.onOpenSuperUser,
                    onOpenModule = actions.onOpenModule,
                    layout = CountCardLayout.Vertical,
                    modifier = Modifier.weight(1f),
                    emphasis = CountCardEmphasis.Value,
                )
                InfoCard(
                    systemInfo = state.systemInfo,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            if (fullFeatured) {
                CountCardPair(
                    superuserCount = superuserCount,
                    moduleEnabledCount = moduleEnabledCount,
                    onOpenSuperUser = actions.onOpenSuperUser,
                    onOpenModule = actions.onOpenModule,
                    layout = CountCardLayout.Horizontal,
                    emphasis = CountCardEmphasis.Value,
                )
            }
            InfoCard(systemInfo = state.systemInfo)
        }
    }
}

/**
 * The Dashboard banner: the state icon and title over the three facts the hero was built to carry
 * (the working version, the kernel and SELinux).
 *
 * It is a plain card, exactly like the ones under it: the state is read from the icon and the
 * wording, so the surface never has to switch to an accent role.
 */
@Composable
private fun DashboardHeroCard(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    background: SurfaceConfig? = null,
) {
    val ksuActive = state.ksuVersion != null
    val notInstalled = !ksuActive && state.kernelVersion.isGKI()

    val statusIcon = when {
        ksuActive -> Icons.Rounded.CheckCircle
        notInstalled -> Icons.Rounded.Warning
        else -> Icons.Rounded.Block
    }
    val statusTitle = when {
        ksuActive -> stringResource(R.string.home_working)
        notInstalled -> stringResource(R.string.home_not_installed)
        else -> stringResource(R.string.home_unsupported)
    }
    val statusSummary = when {
        ksuActive -> stringResource(R.string.home_working_version, "${state.ksuVersion}-${state.kernelUAPIVersion}")
        notInstalled -> stringResource(R.string.home_click_to_install)
        else -> stringResource(R.string.home_unsupported_reason)
    }
    val jailbreak = notInstalled && state.isSELinuxPermissive
    val backgroundUri = background?.imageUri?.takeIf { it.isNotEmpty() }
    val overImage = backgroundUri != null
    val subColor = if (overImage) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant

    HomeCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = if (overImage) Color.Transparent else MaterialTheme.colorScheme.surfaceBright,
        contentColor = if (overImage) Color.White else contentColorFor(MaterialTheme.colorScheme.surfaceBright),
        wallpaperRole = if (overImage) null else WallpaperSurfaceRole.Group,
        onClick = {
            if (!state.isLateLoadMode) {
                actions.onInstallClick()
            }
        },
    ) {
        Box {
            if (background != null && backgroundUri != null) {
                SurfaceBackgroundImage(uri = backgroundUri, surface = background)
            }
            CompositionLocalProvider(LocalHomeTileCardOverImage provides overImage) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = statusTitle,
                            modifier = Modifier.size(36.dp),
                            tint = if (overImage) Color.White else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = statusTitle, style = FolkType.Title)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = statusSummary,
                                style = FolkType.Summary,
                                color = subColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (jailbreak) {
                            Spacer(Modifier.width(16.dp))
                            StatusJailbreakButton(onClick = actions.onJailbreakClick)
                        }
                    }
                    val stateTexts = statusStateTexts(state)
                    if (stateTexts.isNotEmpty()) {
                        Text(
                            text = stateTexts.joinToString(" · ") { (label, value) -> "${label}: $value" },
                            style = FolkType.Caption,
                            color = subColor,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        HeroFact(
                            label = stringResource(R.string.module_version),
                            value = if (ksuActive) "${state.ksuVersion}-${state.kernelUAPIVersion}" else "—",
                            modifier = Modifier.weight(1f),
                        )
                        HeroFact(
                            label = stringResource(R.string.home_kernel),
                            value = state.systemInfo.kernelVersion,
                            modifier = Modifier.weight(1f),
                        )
                        HeroFact(
                            label = stringResource(R.string.home_selinux_status),
                            value = selinuxDisplayName(state.systemInfo.selinuxStatus),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** One label over its value, the column unit of the hero banner. */
@Composable
private fun HeroFact(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = FolkType.Caption,
            color = if (LocalHomeTileCardOverImage.current) {
                Color.White.copy(alpha = 0.8f)
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = FolkType.Summary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
