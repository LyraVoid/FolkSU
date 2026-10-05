package me.weishu.kernelsu.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath

/**
 * A rounded rectangle whose corners ease out of the edges instead of meeting them in a circular
 * arc, which is what makes the same radius read as moulded rather than cut out. The outline comes
 * from androidx.graphics.shapes, which arrives with Material and gives smoothing up before radius
 * when an edge is too short for both.
 */
class ContinuousCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        if (size.isEmpty() || topStart + topEnd + bottomEnd + bottomStart == 0f) {
            return Outline.Rectangle(size.toRect())
        }
        val polygon = RoundedPolygon.rectangle(
            width = size.width,
            height = size.height,
            perVertexRounding = perVertexRounding(
                topStart = topStart,
                topEnd = topEnd,
                bottomEnd = bottomEnd,
                bottomStart = bottomStart,
                layoutDirection = layoutDirection,
            ),
            centerX = size.width / 2f,
            centerY = size.height / 2f,
        )
        return Outline.Generic(polygon.toPath().asComposePath())
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): ContinuousCornerShape = ContinuousCornerShape(topStart, topEnd, bottomEnd, bottomStart)

    override fun lerp(other: Any?, t: Float): Any? {
        val target = when (other) {
            null, RectangleShape -> ContinuousCornerShape(0.dp)
            is ContinuousCornerShape -> other
            // Any other shape has to animate on its own; the caller falls back to a plain fade.
            else -> return null
        }
        return ContinuousCornerShape(
            topStart = lerpCornerSize(topStart, target.topStart, t),
            topEnd = lerpCornerSize(topEnd, target.topEnd, t),
            bottomEnd = lerpCornerSize(bottomEnd, target.bottomEnd, t),
            bottomStart = lerpCornerSize(bottomStart, target.bottomStart, t),
        )
    }

    // CornerBasedShape treats any corner-based shape with the same radii as equal, which would let
    // an outdated outline survive a swap from RoundedCornerShape.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContinuousCornerShape) return false
        return topStart == other.topStart &&
            topEnd == other.topEnd &&
            bottomEnd == other.bottomEnd &&
            bottomStart == other.bottomStart
    }

    override fun hashCode(): Int {
        var result = topStart.hashCode()
        result = 31 * result + topEnd.hashCode()
        result = 31 * result + bottomEnd.hashCode()
        result = 31 * result + bottomStart.hashCode()
        return result
    }

    override fun toString(): String =
        "ContinuousCornerShape(topStart = $topStart, topEnd = $topEnd, bottomEnd = $bottomEnd, " +
            "bottomStart = $bottomStart)"
}

fun ContinuousCornerShape(radius: Dp): ContinuousCornerShape = ContinuousCornerShape(CornerSize(radius))

fun ContinuousCornerShape(corner: CornerSize): ContinuousCornerShape =
    ContinuousCornerShape(corner, corner, corner, corner)

fun ContinuousCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): ContinuousCornerShape = ContinuousCornerShape(
    topStart = CornerSize(topStart),
    topEnd = CornerSize(topEnd),
    bottomEnd = CornerSize(bottomEnd),
    bottomStart = CornerSize(bottomStart),
)

/** 0 is a circular arc, 1 as square as the rounding allows; 0.6 reads as one curve rather than an
 *  arc joined to a line. */
private const val CornerSmoothing = 0.6f

private fun perVertexRounding(
    topStart: Float,
    topEnd: Float,
    bottomEnd: Float,
    bottomStart: Float,
    layoutDirection: LayoutDirection,
): List<CornerRounding> {
    val leftToRight = layoutDirection == LayoutDirection.Ltr
    val topLeft = if (leftToRight) topStart else topEnd
    val topRight = if (leftToRight) topEnd else topStart
    val bottomRight = if (leftToRight) bottomEnd else bottomStart
    val bottomLeft = if (leftToRight) bottomStart else bottomEnd
    // graphics-shapes lists a rectangle's vertices from its bottom right corner, clockwise.
    return listOf(bottomRight, bottomLeft, topLeft, topRight).map { radius ->
        CornerRounding(radius = radius, smoothing = CornerSmoothing)
    }
}

private fun lerpCornerSize(start: CornerSize, stop: CornerSize, fraction: Float): CornerSize =
    object : CornerSize {
        override fun toPx(shapeSize: Size, density: Density): Float =
            lerp(start.toPx(shapeSize, density), stop.toPx(shapeSize, density), fraction)
    }

/**
 * The corner radii the app is allowed to use, so a screen picks a step instead of inventing one.
 * A step is named after its radius rather than a role, which keeps "same shape as before"
 * checkable against a screenshot.
 */
object FolkShape {
    /** Icon tiles and fills inside a container. */
    val Corner12: CornerBasedShape = ContinuousCornerShape(12.dp)

    /** Rows and inner cards. */
    val Corner16: CornerBasedShape = ContinuousCornerShape(16.dp)

    /** Group surfaces - the 20dp the settings pages are calibrated against. */
    val Corner20: CornerBasedShape = ContinuousCornerShape(20.dp)

    /** Dialogs and larger inner surfaces. */
    val Corner24: CornerBasedShape = ContinuousCornerShape(24.dp)

    /** Sheets and the outer corner of a grouped list. */
    val Corner28: CornerBasedShape = ContinuousCornerShape(28.dp)

    /**
     * The frame every alert dialog shares. A little softer than [Corner28] so a dialog, which is
     * small on screen, still reads clearly as a floating panel rather than a page.
     */
    val Dialog: CornerBasedShape = ContinuousCornerShape(30.dp)

    /** Capsule. */
    val CornerFull: RoundedCornerShape = RoundedCornerShape(50)

    /**
     * The ladder as Material's five slots, so a component we do not style ourselves sits on the
     * same corner family. The steps go in order, which grows the small end the most - stock 4dp and
     * 8dp read as "cut out" next to a panel - while the top slot keeps 28dp for dialogs and sheets.
     */
    val materialShapes: Shapes = Shapes(
        extraSmall = Corner12,
        small = Corner16,
        medium = Corner20,
        large = Corner24,
        extraLarge = Corner28,
    )
}
