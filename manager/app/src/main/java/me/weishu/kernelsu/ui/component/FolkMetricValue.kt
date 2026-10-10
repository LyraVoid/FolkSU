package me.weishu.kernelsu.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/** A metric whose missing value is explicit, rather than misleadingly displayed as zero. */
@Composable
fun FolkMetricValue(
    value: String?,
    unavailableLabel: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleLarge,
    unavailableStyle: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurface,
    unavailableColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = value ?: unavailableLabel,
        modifier = modifier,
        style = if (value != null) style else unavailableStyle,
        color = if (value != null) color else unavailableColor,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
