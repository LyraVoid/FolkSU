package me.weishu.kernelsu.ui.component.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** How wide the donut ring is drawn. */
private val PieStrokeWidth = 20.dp

/** The gap left between two neighbouring slices, in degrees. */
private const val SliceGapDegrees = 2f

/** One count in the pie: what it is, how many, and the theme colour that carries it. */
@Immutable
data class PieSlice(
    val label: String,
    val value: Int,
    val color: Color,
)

/**
 * The home-screen counts as a donut, so a layout can show a proportion instead of a bare number.
 *
 * Slices without a count are dropped, which keeps the ring from repeating the track colour; a pie
 * where nothing is counted shows the bare track. The caller sizes the chart with [modifier].
 */
@Composable
fun ModulePieChart(
    slices: List<PieSlice>,
    centerLabel: String,
    modifier: Modifier = Modifier,
) {
    val active = slices.filter { it.value > 0 }
    val total = active.sumOf { it.value }
    val gap = if (active.size > 1) SliceGapDegrees else 0f
    val available = 360f - gap * active.size
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = PieStrokeWidth.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            if (total <= 0) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                return@Canvas
            }

            var startAngle = -90f
            active.forEach { slice ->
                val sweep = slice.value.toFloat() / total * available
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                startAngle += sweep + gap
            }
        }
        Text(
            text = centerLabel,
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
    }
}
