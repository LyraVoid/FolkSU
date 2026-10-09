package me.weishu.kernelsu.ui.component.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.component.material.TonalCard
import me.weishu.kernelsu.ui.component.material.folkPressScale
import me.weishu.kernelsu.ui.theme.FolkShape

/** One tappable entry in [SettingsCategoryGrid]. */
data class SettingsCategoryEntry(
    val category: SettingsCategory,
    val onClick: () -> Unit,
)

/**
 * The settings hub's category picker: a [TonalCard] holding a grid of the [SettingsCategory]
 * entries. The column count follows the available width (see [adaptiveColumns]); a short final row
 * is padded with weighted spacers so the remaining tiles keep the same width as a full row.
 *
 * TonalCard (rather than a plain Surface) is deliberate: it carries the grouped-surface colour and
 * joins the wallpaper transparency layering, so this panel behaves like every other group.
 */
@Composable
fun SettingsCategoryGrid(
    entries: List<SettingsCategoryEntry>,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return

    BoxWithConstraints(modifier = modifier) {
        val columns = adaptiveColumns(entries.size, maxWidth)
        TonalCard(
            modifier = Modifier.fillMaxWidth(),
            shape = FolkShape.Corner20,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                entries.chunked(columns).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { entry ->
                            CategoryCell(entry = entry, modifier = Modifier.weight(1f))
                        }
                        repeat(columns - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * How many tiles fit per row at [available] width. A wide screen folds the whole set into one row
 * instead of stretching four tiles across a tablet; a narrow phone drops to three.
 */
private fun adaptiveColumns(count: Int, available: Dp): Int = when {
    available >= 600.dp -> count.coerceAtLeast(1)
    available >= 360.dp -> 4
    else -> 3
}

@Composable
private fun CategoryCell(
    entry: SettingsCategoryEntry,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .clip(FolkShape.Corner12)
            .folkPressScale(interactionSource)
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                entry.onClick()
            }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = entry.category.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(entry.category.titleRes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
