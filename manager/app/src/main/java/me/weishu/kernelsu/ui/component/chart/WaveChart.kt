package me.weishu.kernelsu.ui.component.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.weishu.kernelsu.ui.theme.FolkType

/** One sample placed on the unit square: [x] and [y] both run from 0 to 1. */
private data class ChartPoint(val x: Float, val y: Float)

/** How much of the canvas height the wave may use, leaving the rest as breathing room. */
private const val WaveHeightFraction = 0.85f

/** Maps the samples onto the unit square, stretching the value axis to its own range. */
private fun normalizePoints(samples: List<Float>): List<ChartPoint> {
    if (samples.isEmpty()) return emptyList()
    val min = samples.min()
    val max = samples.max()
    val range = (max - min).coerceAtLeast(1f)
    return samples.mapIndexed { index, value ->
        val x = if (samples.size <= 1) 0.5f else index.toFloat() / (samples.size - 1)
        ChartPoint(x = x, y = (value - min) / range)
    }
}

/** Slides every point from where it was to where it is, so a new sample grows in instead of jumping. */
private fun interpolatePoints(
    previous: List<ChartPoint>,
    current: List<ChartPoint>,
    progress: Float,
): List<ChartPoint> {
    if (current.isEmpty()) return emptyList()
    if (previous.isEmpty()) return current
    return current.mapIndexed { index, point ->
        val from = previous.getOrNull(index) ?: return@mapIndexed point
        ChartPoint(
            x = from.x + (point.x - from.x) * progress,
            y = from.y + (point.y - from.y) * progress,
        )
    }
}

/** The smoothed curve through [points]: quadratic segments joined at the midpoint of each pair. */
private fun buildWavePath(points: List<ChartPoint>, size: Size): Path {
    val height = size.height * WaveHeightFraction
    val path = Path()
    if (points.isEmpty()) return path

    path.moveTo(points[0].x * size.width, height - points[0].y * height)
    for (index in 1 until points.size - 1) {
        val point = points[index]
        val next = points[index + 1]
        path.quadraticTo(
            point.x * size.width,
            height - point.y * height,
            (point.x + next.x) / 2f * size.width,
            height - (point.y + next.y) / 2f * height,
        )
    }
    path.lineTo(points.last().x * size.width, height - points.last().y * height)
    return path
}

/** Closes the curve down to the baseline so it can be filled with the fading gradient. */
private fun buildFillPath(line: Path, size: Size): Path = Path().apply {
    addPath(line)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

/**
 * A wave chart of [samples]: the latest value over its label, then the samples as a smoothed curve
 * over a fading fill.
 *
 * The value axis stretches to whatever the samples happen to span, so a flat metric still reads as
 * a line instead of collapsing to zero; a single sample draws as a flat rule until the next one
 * arrives.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WaveChart(
    label: String,
    value: String,
    samples: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 110.dp,
) {
    val animatable = remember { Animatable(0f) }
    var previous by remember { mutableStateOf<List<ChartPoint>>(emptyList()) }
    var current by remember { mutableStateOf<List<ChartPoint>>(emptyList()) }
    val growthSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(samples) {
        val next = normalizePoints(samples)
        previous = current
        current = next
        animatable.snapTo(0f)
        animatable.animateTo(targetValue = 1f, animationSpec = growthSpec)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = value, style = FolkType.Title, color = color)
            Text(
                text = label,
                style = FolkType.Caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val points = interpolatePoints(previous, current, animatable.value)
            if (points.size < 2) {
                drawLine(
                    color = color.copy(alpha = 0.35f),
                    start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round,
                )
                return@Canvas
            }
            val line = buildWavePath(points, size)
            drawPath(
                path = buildFillPath(line, size),
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f)),
                    startY = 0f,
                    endY = size.height,
                ),
            )
            drawPath(
                path = line,
                color = color,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
