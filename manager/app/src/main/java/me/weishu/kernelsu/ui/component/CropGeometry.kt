package me.weishu.kernelsu.ui.component

import kotlin.math.min
import kotlin.math.roundToInt

/** Shared pixel geometry for both the preview and the exported bitmap. */
internal class CropGeometry(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val areaWidth: Float,
    val areaHeight: Float,
    aspect: Float,
) {
    init {
        require(sourceWidth > 0 && sourceHeight > 0)
        require(areaWidth.isFinite() && areaWidth > 0f)
        require(areaHeight.isFinite() && areaHeight > 0f)
        require(aspect.isFinite() && aspect > 0f)
    }

    val fitScale = min(areaWidth / sourceWidth, areaHeight / sourceHeight)
    val displayWidth = sourceWidth * fitScale
    val displayHeight = sourceHeight * fitScale
    val windowWidth = min(displayWidth, displayHeight * aspect)
    val windowHeight = windowWidth / aspect
    val windowLeft = (areaWidth - windowWidth) / 2f
    val windowTop = (areaHeight - windowHeight) / 2f

    fun clampX(offset: Float, zoom: Float): Float {
        val limit = ((displayWidth * zoom - windowWidth) / 2f).coerceAtLeast(0f)
        return offset.coerceIn(-limit, limit)
    }

    fun clampY(offset: Float, zoom: Float): Float {
        val limit = ((displayHeight * zoom - windowHeight) / 2f).coerceAtLeast(0f)
        return offset.coerceIn(-limit, limit)
    }

    fun crop(zoom: Float, offsetX: Float, offsetY: Float): CropRegion {
        require(zoom.isFinite() && zoom >= 1f)
        val scale = fitScale * zoom
        val width = (windowWidth / scale).roundToInt().coerceIn(1, sourceWidth)
        val height = (windowHeight / scale).roundToInt().coerceIn(1, sourceHeight)
        val left = (sourceWidth / 2f - (windowWidth / 2f + clampX(offsetX, zoom)) / scale)
            .roundToInt().coerceIn(0, sourceWidth - width)
        val top = (sourceHeight / 2f - (windowHeight / 2f + clampY(offsetY, zoom)) / scale)
            .roundToInt().coerceIn(0, sourceHeight - height)
        return CropRegion(left, top, width, height)
    }
}

internal data class CropRegion(val left: Int, val top: Int, val width: Int, val height: Int)
