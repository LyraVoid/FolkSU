package me.weishu.kernelsu.ui.screen.home

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.theme.FolkType

/** A single live metric drawn as a wavy ring with its value in the middle. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StatusCircle(
    value: String,
    label: String,
    progress: Float?,
    color: Color,
) {
    val fraction = (progress ?: 0f).coerceIn(0f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
            if (fraction > 0f) {
                CircularWavyProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    trackColor = color.copy(alpha = 0.2f),
                    amplitude = { 1f },
                    wavelength = 24.dp,
                )
            } else {
                CircularWavyProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = color.copy(alpha = 0.2f),
                    amplitude = { 1f },
                    wavelength = 24.dp,
                )
            }
            Text(text = value, style = MaterialTheme.typography.titleLargeEmphasized)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A labelled usage bar with its used and total size. */
@Composable
internal fun StorageBar(
    label: String,
    usedBytes: Long,
    totalBytes: Long,
    color: Color,
) {
    val context = LocalContext.current
    val progress = if (totalBytes > 0L) {
        (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    } else {
        0f
    }

    val configuration = LocalConfiguration.current
    val sizeText = remember(context, configuration, usedBytes, totalBytes) {
        "${Formatter.formatFileSize(context, usedBytes)} / " +
            Formatter.formatFileSize(context, totalBytes)
    }

    MetricBar(
        label = label,
        value = sizeText,
        progress = progress,
        color = color,
    )
}

/** One labelled usage bar; every row of the stats board shares this shape. */
@Composable
internal fun MetricBar(
    label: String,
    value: String,
    progress: Float,
    color: Color,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = FolkType.Summary)
            Text(
                text = value,
                style = FolkType.Summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.small),
            color = color,
            trackColor = color.copy(alpha = 0.2f),
        )
    }
}
