package me.weishu.kernelsu.ui.screen.settings

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuOpen
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BlurCircular
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.R
import me.weishu.kernelsu.ui.component.bottombar.BottomBarDestination
import me.weishu.kernelsu.ui.component.bottombar.BottomBarIconConfig
import me.weishu.kernelsu.ui.component.bottombar.FloatingBarConfig
import me.weishu.kernelsu.ui.component.bottombar.NavMode
import me.weishu.kernelsu.ui.component.bottombar.NavModeConfig
import me.weishu.kernelsu.ui.component.material.FolkIconButton
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSliderItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.ui.component.material.TonalCard

private val SectionModifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp)

/**
 * Navigation and chrome settings lifted out of the former "theme" screen: the navigation badge,
 * predictive back, custom bottom-bar icons and the bottom-bar / floating-bar configuration.
 *
 * Badge and predictive-back go through [SettingsScreenActions] because they are ViewModel state
 * (predictive back also flips the platform back callback). The icon and bar configs are written to
 * their own config objects directly, exactly as the theme screen did.
 */
@Composable
fun NavigationSettingsSection(uiState: SettingsUiState, actions: SettingsScreenActions) {
    SegmentedColumn(
        modifier = SectionModifier,
        content = listOf(
            {
                SegmentedSwitchItem(
                    icon = Icons.Rounded.Pin,
                    title = stringResource(id = R.string.settings_navigation_badge),
                    summary = stringResource(id = R.string.settings_navigation_badge_summary),
                    checked = uiState.enableNavigationBadge,
                    onCheckedChange = actions.onSetEnableNavigationBadge
                )
            }
        )
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        SegmentedColumn(
            modifier = SectionModifier,
            content = listOf(
                {
                    SegmentedSwitchItem(
                        icon = Icons.AutoMirrored.Rounded.MenuOpen,
                        title = stringResource(id = R.string.settings_enable_predictive_back),
                        summary = stringResource(id = R.string.settings_enable_predictive_back_summary),
                        checked = uiState.enablePredictiveBack,
                        onCheckedChange = actions.onSetEnablePredictiveBack
                    )
                }
            )
        )
    }

    NavCustomIconsSection()
    NavModeSection()
}

@Composable
private fun NavCustomIconsSection() {
    val context = LocalContext.current
    val revision by BottomBarIconConfig.revision.collectAsState()
    val customEnabled = remember(revision) { BottomBarIconConfig.isEnabled }
    var iconPickTarget by remember { mutableStateOf<BottomBarDestination?>(null) }
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val destination = iconPickTarget
        iconPickTarget = null
        if (uri != null && destination != null) {
            val saved = BottomBarIconConfig.saveCustomIcon(context, destination.name, uri)
            Toast.makeText(
                context,
                context.getString(if (saved) R.string.nav_icon_set else R.string.nav_icon_set_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    SegmentedColumn(
        modifier = SectionModifier,
        content = buildList<@Composable () -> Unit> {
            add {
                SegmentedSwitchItem(
                    icon = Icons.Rounded.Apps,
                    title = stringResource(R.string.settings_nav_custom_icons),
                    summary = stringResource(R.string.settings_nav_custom_icons_summary),
                    checked = customEnabled,
                    onCheckedChange = { BottomBarIconConfig.isEnabled = it },
                )
            }
            if (customEnabled) {
                BottomBarDestination.entries.forEach { destination ->
                    add {
                        NavIconItemRow(
                            destination = destination,
                            revision = revision,
                            onPick = {
                                iconPickTarget = destination
                                iconPicker.launch("image/*")
                            },
                            onClear = {
                                BottomBarIconConfig.clearCustomIcon(destination.name)
                            },
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun NavModeSection() {
    val navModeRevision by NavModeConfig.revision.collectAsState()
    val navMode = remember(navModeRevision) { NavModeConfig.mode }
    val floatingRevision by FloatingBarConfig.revision.collectAsState()
    val floatingStyle = remember(floatingRevision) { FloatingBarConfig.style }
    val floatingCompact = remember(floatingRevision) { FloatingBarConfig.compact }
    val floatingGlass = remember(floatingRevision) { FloatingBarConfig.glass }
    val floatingAutoHide = remember(floatingRevision) { FloatingBarConfig.autoHide }
    val floatingSwipeHide = remember(floatingRevision) { FloatingBarConfig.swipeHide }
    val glassBlurStrength = remember(floatingRevision) { FloatingBarConfig.glassBlurStrength }
    val glassTransparency = remember(floatingRevision) { FloatingBarConfig.glassTransparency }
    val glassHighlightStrength = remember(floatingRevision) { FloatingBarConfig.glassHighlightStrength }
    val glassSpecular = remember(floatingRevision) { FloatingBarConfig.glassSpecular }
    val glassInnerGlow = remember(floatingRevision) { FloatingBarConfig.glassInnerGlow }
    val glassBorder = remember(floatingRevision) { FloatingBarConfig.glassBorder }
    val toPercent: (Float) -> String = { "${(it * 100).roundToInt()}%" }
    val navModes = NavMode.entries
    val navModeLabels = listOf(
        stringResource(R.string.settings_nav_mode_auto),
        stringResource(R.string.settings_nav_mode_bottom),
        stringResource(R.string.settings_nav_mode_rail),
        stringResource(R.string.settings_nav_mode_floating),
    )
    val styles = FloatingBarConfig.Style.entries
    val styleLabels = listOf(
        stringResource(R.string.settings_floating_bar_style_standard),
        stringResource(R.string.settings_floating_bar_style_drawer),
    )

    SegmentedColumn(
        modifier = SectionModifier,
        content = buildList<@Composable () -> Unit> {
            add {
                SegmentedDropdownItem(
                    icon = Icons.Rounded.SpaceDashboard,
                    title = stringResource(R.string.settings_nav_mode),
                    summary = stringResource(R.string.settings_nav_mode_summary),
                    items = navModeLabels,
                    selectedIndex = navModes.indexOf(navMode).coerceAtLeast(0),
                    onItemSelected = { NavModeConfig.mode = navModes[it] },
                )
            }
            if (navMode == NavMode.Floating) {
                add {
                    SegmentedDropdownItem(
                        icon = Icons.Rounded.Style,
                        title = stringResource(R.string.settings_floating_bar_style),
                        items = styleLabels,
                        selectedIndex = styles.indexOf(floatingStyle).coerceAtLeast(0),
                        onItemSelected = { FloatingBarConfig.style = styles[it] },
                    )
                }
                add {
                    SegmentedSwitchItem(
                        icon = Icons.Rounded.BlurOn,
                        title = stringResource(R.string.settings_floating_bar_glass),
                        summary = stringResource(R.string.settings_floating_bar_glass_summary),
                        checked = floatingGlass,
                        onCheckedChange = { FloatingBarConfig.glass = it },
                    )
                }
                if (floatingGlass) {
                    add {
                        SegmentedSliderItem(
                            icon = Icons.Rounded.BlurCircular,
                            title = stringResource(R.string.settings_navbar_glass_blur_strength),
                            value = glassBlurStrength,
                            valueRange = 0f..1f,
                            valueText = toPercent,
                            onValueChangeFinished = { FloatingBarConfig.glassBlurStrength = it },
                        )
                    }
                    add {
                        SegmentedSliderItem(
                            icon = Icons.Rounded.Opacity,
                            title = stringResource(R.string.settings_navbar_glass_transparency),
                            value = glassTransparency,
                            valueRange = 0f..1f,
                            valueText = toPercent,
                            onValueChangeFinished = { FloatingBarConfig.glassTransparency = it },
                        )
                    }
                    add {
                        SegmentedSliderItem(
                            icon = Icons.Rounded.Brightness7,
                            title = stringResource(R.string.settings_navbar_glass_highlight_strength),
                            value = glassHighlightStrength,
                            valueRange = 0f..1f,
                            valueText = toPercent,
                            onValueChangeFinished = { FloatingBarConfig.glassHighlightStrength = it },
                        )
                    }
                    add {
                        SegmentedSwitchItem(
                            icon = Icons.Rounded.FlashOn,
                            title = stringResource(R.string.settings_navbar_glass_specular),
                            summary = stringResource(R.string.settings_navbar_glass_specular_summary),
                            checked = glassSpecular,
                            onCheckedChange = { FloatingBarConfig.glassSpecular = it },
                        )
                    }
                    add {
                        SegmentedSwitchItem(
                            icon = Icons.Rounded.WbTwilight,
                            title = stringResource(R.string.settings_navbar_glass_inner_glow),
                            summary = stringResource(R.string.settings_navbar_glass_inner_glow_summary),
                            checked = glassInnerGlow,
                            onCheckedChange = { FloatingBarConfig.glassInnerGlow = it },
                        )
                    }
                    add {
                        SegmentedSwitchItem(
                            icon = Icons.Rounded.BorderOuter,
                            title = stringResource(R.string.settings_navbar_glass_border),
                            summary = stringResource(R.string.settings_navbar_glass_border_summary),
                            checked = glassBorder,
                            onCheckedChange = { FloatingBarConfig.glassBorder = it },
                        )
                    }
                } else {
                    add {
                        SegmentedSwitchItem(
                            icon = Icons.Rounded.RoundedCorner,
                            title = stringResource(R.string.settings_floating_bar_compact),
                            checked = floatingCompact,
                            onCheckedChange = { FloatingBarConfig.compact = it },
                        )
                    }
                }
                add {
                    SegmentedSwitchItem(
                        icon = Icons.Rounded.VisibilityOff,
                        title = stringResource(R.string.settings_floating_auto_hide),
                        summary = stringResource(R.string.settings_floating_auto_hide_summary),
                        checked = floatingAutoHide,
                        onCheckedChange = { FloatingBarConfig.autoHide = it },
                    )
                }
                add {
                    SegmentedSwitchItem(
                        icon = Icons.Rounded.SwipeUp,
                        title = stringResource(R.string.settings_floating_swipe_hide),
                        summary = stringResource(R.string.settings_floating_swipe_hide_summary),
                        checked = floatingSwipeHide,
                        onCheckedChange = { FloatingBarConfig.swipeHide = it },
                    )
                }
            }
        }
    )
}

@Composable
private fun NavIconItemRow(
    destination: BottomBarDestination,
    revision: Int,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    val uri = remember(revision, destination.name) {
        BottomBarIconConfig.getCustomIconUri(destination.name)
    }
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, uri) {
        value = if (uri != null) {
            withContext(Dispatchers.IO) { BottomBarIconConfig.loadIconBitmap(uri) }
        } else {
            null
        }
    }

    SegmentedListItem(
        onClick = onPick,
        headlineContent = { Text(stringResource(destination.label)) },
        supportingContent = {
            Text(
                stringResource(
                    if (uri != null) R.string.nav_icon_custom_selected else R.string.nav_icon_default
                )
            )
        },
        leadingContent = {
            val loaded = bitmap
            if (loaded != null) {
                Image(
                    bitmap = loaded.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    imageVector = destination.iconSelected,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
            }
        },
        trailingContent = if (uri != null) {
            {
                FolkIconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.nav_icon_clear),
                    )
                }
            }
        } else {
            null
        },
    )
}

/**
 * Global display density controls that are not colour: page zoom and the module-description line
 * count. Kept beside their TonalCard sliders, mirroring how the old theme screen rendered them.
 */
@Composable
fun DisplayDensitySection(uiState: SettingsUiState, actions: SettingsScreenActions) {
    TonalCard(modifier = SectionModifier) {
        val sliderState = rememberSliderState(
            value = uiState.pageScale,
            trackRange = 0.8f..1.1f
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.AspectRatio,
                    contentDescription = stringResource(id = R.string.settings_page_scale),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_page_scale),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(id = R.string.settings_page_scale_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${(sliderState.value * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Slider(
                state = sliderState,
                onValueChangeFinished = { actions.onSetPageScale(sliderState.value) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    TonalCard(modifier = SectionModifier) {
        val sliderState = rememberSliderState(
            value = uiState.moduleDescriptionMaxLines.toFloat(),
            steps = 3,
            trackRange = 1f..5f
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Description,
                    contentDescription = stringResource(id = R.string.settings_module_description_max_lines),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_module_description_max_lines),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(id = R.string.settings_module_description_max_lines_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${sliderState.value.roundToInt()} " + stringResource(R.string.unit_lines),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Slider(
                state = sliderState,
                onValueChangeFinished = {
                    actions.onSetModuleDescriptionMaxLines(sliderState.value.roundToInt())
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
