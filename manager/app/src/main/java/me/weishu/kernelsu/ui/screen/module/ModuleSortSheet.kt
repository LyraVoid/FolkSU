package me.weishu.kernelsu.ui.screen.module

import android.R
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.weishu.kernelsu.data.model.Module
import me.weishu.kernelsu.data.model.ModuleSortGroup
import me.weishu.kernelsu.data.model.ModuleSortPriorityGroups
import me.weishu.kernelsu.ui.component.material.ExpressiveDialog
import me.weishu.kernelsu.ui.component.material.FolkTextButton
import me.weishu.kernelsu.ui.component.material.SegmentedCheckboxItem
import me.weishu.kernelsu.ui.component.material.SegmentedColumn
import me.weishu.kernelsu.ui.component.material.SegmentedListItem
import me.weishu.kernelsu.ui.component.material.SegmentedSwitchItem
import me.weishu.kernelsu.R as AppR

/**
 * The list's sort controls: which kinds of module are lifted above the alphabet, whether switched-on
 * modules float to the top within each kind, and a manual order that overrides them both. Opened
 * from the module top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModuleSortSheet(
    show: Boolean,
    groups: Set<ModuleSortGroup>,
    enabledFirst: Boolean,
    customOrder: List<String>,
    displayModules: List<Module>,
    onDismiss: () -> Unit,
    onGroupChange: (ModuleSortGroup, Boolean) -> Unit,
    onEnabledFirstChange: (Boolean) -> Unit,
    onCustomOrderChange: (List<String>) -> Unit,
    onResetCustomOrder: () -> Unit,
) {
    if (!show) return

    var orderSnapshot by remember { mutableStateOf<List<Module>>(emptyList()) }
    var showOrderDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        )
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(AppR.string.module_sort),
                style = MaterialTheme.typography.titleLarge
            )

            SegmentedColumn(title = stringResource(AppR.string.module_sort_priority)) {
                ModuleSortPriorityGroups.forEach { group ->
                    item(key = group.value) {
                        SegmentedCheckboxItem(
                            title = stringResource(group.titleRes()),
                            summary = stringResource(group.summaryRes()),
                            checked = group in groups,
                            onCheckedChange = { onGroupChange(group, it) },
                        )
                    }
                }
            }

            SegmentedColumn {
                item {
                    SegmentedSwitchItem(
                        title = stringResource(AppR.string.module_sort_enabled_first),
                        checked = enabledFirst,
                        onCheckedChange = onEnabledFirstChange,
                    )
                }
            }

            SegmentedColumn {
                item {
                    SegmentedListItem(
                        onClick = {
                            orderSnapshot = displayModules
                            showOrderDialog = true
                        },
                        headlineContent = { Text(stringResource(AppR.string.module_sort_custom_order)) },
                        supportingContent = {
                            Text(stringResource(AppR.string.module_sort_custom_order_summary))
                        },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        },
                    )
                }
                if (customOrder.isNotEmpty()) {
                    item {
                        SegmentedListItem(
                            onClick = onResetCustomOrder,
                            headlineContent = {
                                Text(stringResource(AppR.string.module_sort_reset_custom_order))
                            },
                            supportingContent = {
                                Text(stringResource(AppR.string.module_sort_reset_custom_order_summary))
                            },
                        )
                    }
                }
            }
        }
    }

    if (showOrderDialog) {
        ModuleCustomOrderDialog(
            modules = orderSnapshot,
            onDismiss = { showOrderDialog = false },
            onOrderChange = onCustomOrderChange,
            onReset = {
                onResetCustomOrder()
                showOrderDialog = false
            },
        )
    }
}

/**
 * The manual order editor. The order is written on each drop so leaving with the back gesture
 * keeps whatever was arranged.
 */
@Composable
private fun ModuleCustomOrderDialog(
    modules: List<Module>,
    onDismiss: () -> Unit,
    onOrderChange: (List<String>) -> Unit,
    onReset: () -> Unit,
) {
    var ordered by remember(modules) { mutableStateOf(modules) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var draggedDistance by remember { mutableStateOf(0f) }
    val reorderThreshold = with(LocalDensity.current) { 40.dp.toPx() }
    val dragToReorder = stringResource(AppR.string.module_sort_drag_to_reorder)

    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AppR.string.module_sort_custom_order)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                itemsIndexed(ordered, key = { _, module -> module.id }) { _, module ->
                    val isDragging = draggedId == module.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isDragging) {
                                    Modifier
                                        .zIndex(1f)
                                        .graphicsLayer { translationY = draggedDistance }
                                } else {
                                    Modifier.animateItem()
                                }
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = module.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = Icons.Filled.DragHandle,
                            contentDescription = dragToReorder,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
                                .semantics { contentDescription = dragToReorder }
                                .pointerInput(module.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggedId = module.id
                                            draggedDistance = 0f
                                        },
                                        onDragEnd = {
                                            draggedId = null
                                            draggedDistance = 0f
                                        },
                                        onDragCancel = {
                                            draggedId = null
                                            draggedDistance = 0f
                                        },
                                    ) { change, dragAmount ->
                                        change.consume()
                                        draggedDistance += dragAmount.y
                                        val currentIndex = ordered.indexOfFirst { it.id == module.id }
                                        val targetIndex = when {
                                            draggedDistance > reorderThreshold -> currentIndex + 1
                                            draggedDistance < -reorderThreshold -> currentIndex - 1
                                            else -> currentIndex
                                        }
                                        if (currentIndex >= 0 && targetIndex in ordered.indices && targetIndex != currentIndex) {
                                            ordered = ordered.toMutableList().apply {
                                                add(targetIndex, removeAt(currentIndex))
                                            }
                                            onOrderChange(ordered.map { it.id })
                                            draggedDistance -= if (targetIndex > currentIndex) {
                                                reorderThreshold
                                            } else {
                                                -reorderThreshold
                                            }
                                        }
                                    }
                                },
                        )
                    }
                }
            }
        },
        confirmButton = {
            FolkTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            FolkTextButton(onClick = onReset) {
                Text(stringResource(AppR.string.module_sort_reset_custom_order))
            }
        },
    )
}

private fun ModuleSortGroup.titleRes(): Int = when (this) {
    ModuleSortGroup.MetaModule -> AppR.string.module_sort_group_metamodule
    ModuleSortGroup.Zygisk -> AppR.string.module_sort_group_zygisk
    ModuleSortGroup.LSPosed -> AppR.string.module_sort_group_lsposed
    ModuleSortGroup.WebUi -> AppR.string.module_sort_group_webui
    ModuleSortGroup.ActionScript -> AppR.string.module_sort_group_action
}

private fun ModuleSortGroup.summaryRes(): Int = when (this) {
    ModuleSortGroup.MetaModule -> AppR.string.module_sort_group_metamodule_summary
    ModuleSortGroup.Zygisk -> AppR.string.module_sort_group_zygisk_summary
    ModuleSortGroup.LSPosed -> AppR.string.module_sort_group_lsposed_summary
    ModuleSortGroup.WebUi -> AppR.string.module_sort_group_webui_summary
    ModuleSortGroup.ActionScript -> AppR.string.module_sort_group_action_summary
}
