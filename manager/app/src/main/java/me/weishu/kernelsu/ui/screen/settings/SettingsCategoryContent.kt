package me.weishu.kernelsu.ui.screen.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.FolkMountMode
import me.weishu.kernelsu.data.model.FolkMountProvider
import me.weishu.kernelsu.data.model.HomeLayoutStyle
import me.weishu.kernelsu.ui.component.KsuIsValid
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedDropdownItem
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem

/**
 * Contents of each settings category, split out of the old flat hub. Every function emits its own
 * [SegmentedColumn] groups; the hosting screen owns the scroll container, so these stay layout-free
 * and mirror each other's spacing.
 */

private val GroupModifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 13.dp)

@Composable
fun GeneralCategoryContent(uiState: SettingsUiState, actions: SettingsScreenActions) {
    KsuIsValid {
        SegmentedColumn(
            modifier = GroupModifier,
            content = listOf(
                {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.SystemUpdate,
                        title = stringResource(id = R.string.settings_check_update),
                        summary = stringResource(id = R.string.settings_check_update_summary),
                        checked = uiState.checkUpdate,
                        onCheckedChange = actions.onSetCheckUpdate
                    )
                },
                {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.SystemUpdateAlt,
                        title = stringResource(id = R.string.settings_module_check_update),
                        summary = stringResource(id = R.string.settings_check_update_summary),
                        checked = uiState.checkModuleUpdate,
                        onCheckedChange = actions.onSetCheckModuleUpdate
                    )
                }
            )
        )
    }

    val profileTemplate = stringResource(id = R.string.settings_profile_template)
    KsuIsValid {
        SegmentedColumn(
            modifier = GroupModifier,
            content = listOf {
                SegmentedListItem(
                    onClick = actions.onOpenProfileTemplate,
                    headlineContent = { Text(profileTemplate) },
                    supportingContent = { Text(stringResource(id = R.string.settings_profile_template_summary)) },
                    leadingContent = { Icon(Icons.Filled.Description, profileTemplate) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                )
            }
        )
    }
}

@Composable
fun AppearanceCategoryContent(uiState: SettingsUiState, actions: SettingsScreenActions) {
    SegmentedColumn(
        modifier = GroupModifier,
        content = buildList {
            add {
                SegmentedListItem(
                    onClick = actions.onOpenTheme,
                    headlineContent = { Text(stringResource(id = R.string.settings_theme)) },
                    supportingContent = { Text(stringResource(id = R.string.settings_theme_summary)) },
                    leadingContent = { Icon(Icons.Filled.Palette, stringResource(id = R.string.settings_theme)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                )
            }
            add {
                SegmentedListItem(
                    onClick = actions.onOpenWallpaper,
                    headlineContent = { Text(stringResource(id = R.string.wallpaper_title)) },
                    supportingContent = { Text(stringResource(id = R.string.settings_wallpaper_summary)) },
                    leadingContent = { Icon(Icons.Filled.Wallpaper, stringResource(id = R.string.wallpaper_title)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                )
            }
            add {
                SegmentedListItem(
                    onClick = actions.onOpenFont,
                    headlineContent = { Text(stringResource(id = R.string.font_title)) },
                    supportingContent = { Text(stringResource(id = R.string.settings_font_summary)) },
                    leadingContent = { Icon(Icons.Filled.FontDownload, stringResource(id = R.string.font_title)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                )
            }
            add {
                SegmentedListItem(
                    onClick = actions.onOpenThemeStore,
                    headlineContent = { Text(stringResource(id = R.string.theme_store_title)) },
                    supportingContent = { Text(stringResource(id = R.string.theme_store_subtitle)) },
                    leadingContent = { Icon(Icons.Filled.ShoppingBag, stringResource(id = R.string.theme_store_title)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                    }
                )
            }
            add {
                val supportedHomeLayouts = HomeLayoutStyle.supported
                val homeLayoutItems = supportedHomeLayouts.map { style ->
                    stringResource(
                        when (style) {
                            HomeLayoutStyle.GRID -> R.string.settings_home_layout_grid
                            HomeLayoutStyle.FOCUS -> R.string.settings_home_layout_focus
                            HomeLayoutStyle.DASHBOARD -> R.string.settings_home_layout_dashboard
                            HomeLayoutStyle.STATS -> R.string.settings_home_layout_stats
                            else -> R.string.settings_home_layout_circle
                        }
                    )
                }
                val selectedHomeLayout =
                    supportedHomeLayouts.indexOf(uiState.homeLayoutStyle).coerceAtLeast(0)
                SegmentedDropdownItem(
                    icon = Icons.Filled.GridView,
                    title = stringResource(id = R.string.settings_home_layout),
                    summary = stringResource(id = R.string.settings_home_layout_summary),
                    items = homeLayoutItems,
                    selectedIndex = selectedHomeLayout,
                    onItemSelected = { index ->
                        actions.onSetHomeLayoutStyle(
                            supportedHomeLayouts.getOrElse(index) { HomeLayoutStyle.DEFAULT }
                        )
                    }
                )
            }
        }
    )

    DisplayDensitySection(uiState, actions)
}

@Composable
fun BehaviorCategoryContent(uiState: SettingsUiState, actions: SettingsScreenActions) {
    NavigationSettingsSection(uiState, actions)

    KsuIsValid {
        val suCompatModeItems = listOf(
            stringResource(id = R.string.settings_mode_enable_by_default),
            stringResource(id = R.string.settings_mode_disable_until_reboot),
            stringResource(id = R.string.settings_mode_disable_always),
        )

        SegmentedColumn(
            modifier = GroupModifier,
            content = listOf(
                {
                    val suSummary = when (uiState.suCompatStatus) {
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_sucompat_summary)
                    }
                    SegmentedDropdownItem(
                        icon = Icons.Filled.AdminPanelSettings,
                        title = stringResource(id = R.string.settings_sucompat),
                        summary = suSummary,
                        items = suCompatModeItems,
                        enabled = uiState.suCompatStatus == "supported",
                        selectedIndex = uiState.suCompatMode,
                        onItemSelected = actions.onSetSuCompatMode
                    )
                },
                {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.RestartAlt,
                        title = stringResource(id = R.string.settings_soft_reboot),
                        summary = stringResource(id = R.string.settings_soft_reboot_summary),
                        enabled = !uiState.isLateLoadMode,
                        checked = uiState.isLateLoadMode || uiState.useSoftReboot,
                        onCheckedChange = actions.onSetUseSoftReboot
                    )
                },
                {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.DeveloperMode,
                        title = stringResource(id = R.string.enable_web_debugging),
                        summary = stringResource(id = R.string.enable_web_debugging_summary),
                        checked = uiState.enableWebDebugging,
                        onCheckedChange = actions.onSetEnableWebDebugging
                    )
                },
                {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.FlashOn,
                        title = stringResource(id = R.string.settings_auto_jailbreak),
                        summary = stringResource(id = R.string.settings_auto_jailbreak_summary),
                        enabled = uiState.isLateLoadMode,
                        checked = uiState.autoJailbreak,
                        onCheckedChange = actions.onSetAutoJailbreak
                    )
                }
            )
        )
    }
}

@Composable
fun FunctionCategoryContent(uiState: SettingsUiState, actions: SettingsScreenActions) {
    KsuIsValid {
        SegmentedColumn(
            modifier = GroupModifier,
            content = listOf(
                {
                    val umountSummary = when (uiState.kernelUmountStatus) {
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_kernel_umount_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.Filled.LayersClear,
                        title = stringResource(id = R.string.settings_kernel_umount),
                        summary = umountSummary,
                        enabled = uiState.kernelUmountStatus == "supported",
                        checked = uiState.isKernelUmountEnabled,
                        onCheckedChange = actions.onSetKernelUmountEnabled
                    )
                },
                {
                    SegmentedSwitchItem(
                        icon = Icons.AutoMirrored.Filled.Rule,
                        title = stringResource(id = R.string.settings_umount_modules_default),
                        summary = stringResource(id = R.string.settings_umount_modules_default_summary),
                        checked = uiState.isDefaultUmountModules,
                        onCheckedChange = actions.onSetDefaultUmountModules
                    )
                },
                {
                    val avcSpoofSummary = when (uiState.avcSpoofStatus) {
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_avc_spoof_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.Filled.Security,
                        title = stringResource(id = R.string.settings_avc_spoof),
                        summary = avcSpoofSummary,
                        enabled = uiState.avcSpoofStatus == "supported" && !uiState.isAvcSpoofWriting,
                        checked = uiState.isAvcSpoofEnabled,
                        onCheckedChange = actions.onSetAvcSpoofEnabled
                    )
                },
                {
                    val sulogSummary = when (uiState.sulogStatus) {
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_sulog_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.AutoMirrored.Filled.Article,
                        title = stringResource(id = R.string.settings_sulog),
                        summary = sulogSummary,
                        enabled = uiState.sulogStatus == "supported",
                        checked = uiState.isSulogEnabled,
                        onCheckedChange = actions.onSetSulogEnabled
                    )
                }
            )
        )

        // §6.1: hide the dynamic-manager toggle entirely when the kernel cannot support it, instead
        // of offering a switch that does nothing.
        if (uiState.isDynamicManagerAvailable) {
            SegmentedColumn(
                modifier = GroupModifier,
                content = listOf {
                    SegmentedSwitchItem(
                        icon = Icons.Filled.AdminPanelSettings,
                        title = stringResource(id = R.string.allow_any_dynamic_manager),
                        summary = stringResource(id = R.string.allow_any_dynamic_manager_summary),
                        checked = uiState.allowAnyDynamicManager,
                        onCheckedChange = actions.onSetAllowAnyDynamicManager
                    )
                }
            )
        }

        SegmentedColumn(
            modifier = GroupModifier,
            content = listOf(
                {
                    val folkMountStatus = uiState.folkMountStatus
                    val available = folkMountStatus != null && !uiState.folkMountReadError
                    val mode = folkMountStatus?.configuredMode
                    val modeItems = listOf(
                        stringResource(id = R.string.settings_folk_mount_mode_auto),
                        stringResource(id = R.string.settings_folk_mount_mode_builtin),
                        stringResource(id = R.string.settings_folk_mount_mode_metamodule),
                    )
                    val summary = when {
                        uiState.isFolkMountLoading ->
                            stringResource(id = R.string.settings_folk_mount_summary)

                        !available ->
                            stringResource(id = R.string.settings_folk_mount_unavailable_summary)

                        else -> {
                            val base = stringResource(
                                id = when (mode) {
                                    FolkMountMode.AUTO -> R.string.settings_folk_mount_mode_auto_summary
                                    FolkMountMode.BUILTIN -> R.string.settings_folk_mount_mode_builtin_summary
                                    FolkMountMode.METAMODULE -> R.string.settings_folk_mount_mode_metamodule_summary
                                    null -> R.string.settings_folk_mount_summary
                                }
                            )
                            val bootLine = when (folkMountStatus.bootProvider) {
                                FolkMountProvider.BUILTIN -> stringResource(
                                    id = R.string.settings_folk_mount_boot_provider,
                                    stringResource(id = R.string.settings_folk_mount_provider_builtin)
                                )

                                FolkMountProvider.METAMODULE -> stringResource(
                                    id = R.string.settings_folk_mount_boot_provider,
                                    stringResource(id = R.string.settings_folk_mount_provider_metamodule)
                                )

                                null -> stringResource(id = R.string.settings_folk_mount_boot_provider_unknown)
                            }
                            val compatHint = if (
                                mode == FolkMountMode.BUILTIN && folkMountStatus.metamoduleEnabled
                            ) {
                                stringResource(id = R.string.settings_folk_mount_metamodule_compat_hint)
                            } else {
                                null
                            }
                            listOfNotNull(
                                base,
                                bootLine,
                                stringResource(id = R.string.settings_folk_mount_reboot_hint),
                                compatHint,
                            ).joinToString("\n")
                        }
                    }
                    SegmentedDropdownItem(
                        icon = Icons.Filled.Layers,
                        title = stringResource(id = R.string.settings_folk_mount),
                        summary = summary,
                        items = if (available) modeItems else emptyList(),
                        enabled = available && !uiState.isFolkMountLoading && !uiState.isFolkMountWriting,
                        selectedIndex = mode?.ordinal ?: 0,
                        onItemSelected = { index ->
                            FolkMountMode.entries.getOrNull(index)?.let(actions.onSetFolkMountMode)
                        }
                    )
                }
            )
        )
    }
}

@Composable
fun SecurityCategoryContent(uiState: SettingsUiState, actions: SettingsScreenActions) {
    KsuIsValid {
        SegmentedColumn(
            modifier = GroupModifier,
            content = listOf(
                {
                    val selinuxHideSummary = when (uiState.selinuxHideStatus) {
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_selinux_hide_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.Filled.Security,
                        title = stringResource(id = R.string.settings_selinux_hide),
                        summary = selinuxHideSummary,
                        enabled = uiState.selinuxHideStatus == "supported",
                        checked = uiState.isSelinuxHideEnabled,
                        onCheckedChange = actions.onSetSelinuxHideEnabled
                    )
                },
                {
                    val adbRootSummary = when (uiState.adbRootStatus) {
                        "unsupported" -> stringResource(id = R.string.feature_status_unsupported_summary)
                        "managed" -> stringResource(id = R.string.feature_status_managed_summary)
                        else -> stringResource(id = R.string.settings_adb_root_summary)
                    }
                    SegmentedSwitchItem(
                        icon = Icons.Filled.Adb,
                        title = stringResource(id = R.string.settings_adb_root),
                        summary = adbRootSummary,
                        enabled = uiState.adbRootStatus == "supported",
                        checked = uiState.isAdbRootEnabled,
                        onCheckedChange = actions.onSetAdbRootEnabled
                    )
                }
            )
        )
    }
}
