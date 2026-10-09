package me.weishu.kernelsu.media

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VisualMediaConfigTest {
    @After
    fun reset() = VisualMediaConfig.applySettings(JSONObject())

    @Test
    fun folkPatchFieldsKeepNormalizedTitleOffsetDuringRoundTrip() {
        val source = JSONObject()
            .put("isVideoBackgroundEnabled", true)
            .put("videoVolume", 0.25)
            .put("isAdvancedTitleStyleEnabled", true)
            .put("titleImageDayOpacity", 0.8)
            .put("titleImageNightOpacity", 0.4)
            .put("titleImageDim", 0.3)
            .put("titleImageOffsetX", 1.5)
        VisualMediaConfig.applySettings(source)
        val exported = JSONObject().also(VisualMediaConfig::writeConfig)
        for (key in source.keys()) {
            if (source.get(key) is Number) assertEquals(source.getDouble(key), exported.getDouble(key), 0.00001)
            else assertEquals(source.get(key), exported.get(key))
        }
        assertEquals(1.5f, VisualMediaConfig.titleOffsetX, 0f)
    }

    @Test
    fun imageOnlyThemeClearsPreviousVisualMediaSettings() {
        VisualMediaConfig.videoEnabled = true
        VisualMediaConfig.titleEnabled = true
        VisualMediaConfig.titleOffsetX = 1f
        VisualMediaConfig.applySettings(JSONObject())
        assertFalse(VisualMediaConfig.videoEnabled)
        assertFalse(VisualMediaConfig.titleEnabled)
        assertEquals(0f, VisualMediaConfig.videoVolume, 0f)
        assertEquals(1f, VisualMediaConfig.titleDayOpacity, 0f)
        assertEquals(0f, VisualMediaConfig.titleOffsetX, 0f)
    }

    @Test
    fun malformedRangesAreClampedBeforeRendering() {
        VisualMediaConfig.applySettings(JSONObject()
            .put("videoVolume", 5).put("titleImageDim", -1).put("titleImageOffsetX", -200))
        assertEquals(1f, VisualMediaConfig.videoVolume, 0f)
        assertEquals(0f, VisualMediaConfig.titleDim, 0f)
        assertEquals(-2f, VisualMediaConfig.titleOffsetX, 0f)
    }

    @Test
    fun videoThemeEntryRetainsItsContainerExtension() {
        val asset = me.weishu.kernelsu.wallpaper.VisualMediaThemeAsset(true)
        assertEquals("video_background", asset.base)
        assertEquals("mp4", asset.extension(java.io.File("video_background.mp4")))
        assertEquals("webm", asset.extension(java.io.File("video_background.webm")))
        assertEquals("mkv", asset.extension(java.io.File("video_background.mkv")))
    }
}
