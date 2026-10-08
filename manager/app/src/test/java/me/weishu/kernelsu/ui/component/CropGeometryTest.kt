package me.weishu.kernelsu.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropGeometryTest {
    @Test
    fun `square frame retains the entire square image`() {
        val geometry = CropGeometry(1200, 1200, 1080f, 1800f, 1f)
        assertEquals(CropRegion(0, 0, 1200, 1200), geometry.crop(1f, 0f, 0f))
        assertEquals(geometry.windowWidth, geometry.windowHeight, 0f)
    }

    @Test
    fun `wide frame crops the middle of a square without distorting it`() {
        val geometry = CropGeometry(1200, 1200, 1080f, 1800f, 2f)
        assertEquals(CropRegion(0, 300, 1200, 600), geometry.crop(1f, 0f, 0f))
    }

    @Test
    fun `portrait frame crops a landscape source`() {
        val geometry = CropGeometry(1600, 800, 1080f, 1800f, 0.5f)
        assertEquals(CropRegion(600, 0, 400, 800), geometry.crop(1f, 0f, 0f))
    }

    @Test
    fun `zoom and drag use the same source coordinates as the preview`() {
        val geometry = CropGeometry(1200, 1200, 1080f, 1800f, 1f)
        assertEquals(CropRegion(150, 450, 600, 600), geometry.crop(2f, 270f, -270f))
    }

    @Test
    fun `drag limits never expose space outside the source`() {
        for (aspect in listOf(0.5f, 1f, 1.4f, 2f)) {
            for (zoom in listOf(1f, 2f, 6f)) {
                val geometry = CropGeometry(2047, 1333, 1001f, 701f, aspect)
                for (direction in listOf(-1f, 1f)) {
                    val region = geometry.crop(zoom, direction * 100000f, direction * 100000f)
                    assertTrue(region.left >= 0 && region.top >= 0)
                    assertTrue(region.left + region.width <= geometry.sourceWidth)
                    assertTrue(region.top + region.height <= geometry.sourceHeight)
                    assertTrue(kotlin.math.abs(region.width - region.height * aspect) <= 2f)
                    val scale = geometry.fitScale * zoom
                    val previewLeft = geometry.areaWidth / 2f + geometry.clampX(direction * 100000f, zoom) +
                        (region.left - geometry.sourceWidth / 2f) * scale
                    assertEquals(geometry.windowLeft, previewLeft, scale)
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid aspect is rejected`() {
        CropGeometry(1200, 1200, 1080f, 1800f, Float.NaN)
    }
}
