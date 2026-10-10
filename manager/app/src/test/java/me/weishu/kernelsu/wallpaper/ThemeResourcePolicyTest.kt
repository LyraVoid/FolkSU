package me.weishu.kernelsu.wallpaper

import me.weishu.kernelsu.wallpaper.surface.SurfaceField
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import me.weishu.kernelsu.wallpaper.surface.SurfaceStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ThemeResourcePolicyTest {
    @Test fun resourcePriorityDoesNotDependOnZipEntryOrder() {
        val files = linkedMapOf("background.webp" to File("webp"), "background.png" to File("png"),
            "background.jpg" to File("jpg"), "video_background.mkv" to File("mkv"),
            "video_background.mp4" to File("mp4"), "font.otf" to File("otf"), "font.ttf" to File("ttf"))
        assertEquals(File("jpg"), ThemeResourcePolicy.select("background", files))
        assertEquals(File("mp4"), ThemeResourcePolicy.select("video_background", files))
        assertEquals(File("ttf"), ThemeResourcePolicy.select("font", files))
    }

    @Test fun dashboardImagePresenceIsIndependentOfTheFeatureSwitch() {
        val descriptor = SurfaceRegistry.descriptor(SurfaceRegistry.DASHBOARD_HERO)!!
        val json = JSONObject().put("isDashboardCardBackgroundEnabled", false).put("hasDashboardCardBg", true)
            .put(SurfaceStore.key(descriptor.id, SurfaceField.Enabled), false)
        var parsed = ThemeSurfaceMapping.read(descriptor, json)
        assertFalse(parsed.enabled)
        assertTrue(ThemeSurfaceMapping.wantsImage(descriptor, json, parsed, true))
        json.put("hasDashboardCardBg", false).put(SurfaceStore.key(descriptor.id, SurfaceField.Enabled), true)
        parsed = ThemeSurfaceMapping.read(descriptor, json)
        assertTrue(parsed.enabled)
        assertFalse(ThemeSurfaceMapping.wantsImage(descriptor, json, parsed, true))
    }

    @Test fun focusMasterFollowsPresenceUnlessExplicitlyDisabled() {
        val descriptor = SurfaceRegistry.descriptor(SurfaceRegistry.FOCUS)!!
        val json = JSONObject().put("hasFocusCardKernelBg", true).put("focusCardBgDim", 0.6)
            .put("focusCardBgOpacity", 0.7)
        var parsed = ThemeSurfaceMapping.read(descriptor, json)
        assertTrue(parsed.enabled)
        assertEquals(0.6f, parsed.dayDim, 0.0001f)
        assertEquals(0.6f, parsed.nightDim, 0.0001f)
        assertEquals(0.7f, parsed.dayOpacity, 0.0001f)
        json.put("isFocusCardBackgroundEnabled", false)
        parsed = ThemeSurfaceMapping.read(descriptor, json)
        assertFalse(parsed.enabled)
        val child = SurfaceRegistry.descriptor(SurfaceRegistry.FOCUS_CARD_KERNEL)!!
        assertTrue(ThemeSurfaceMapping.wantsImage(child, json, ThemeSurfaceMapping.read(child, json), true))
    }

    @Test fun gridUsesSwitchWhileFocusUsesPresenceAndMissingFilesNeverRequireAnImage() {
        val grid = SurfaceRegistry.descriptor(SurfaceRegistry.GRID_WORK_CARD)!!
        val focus = SurfaceRegistry.descriptor(SurfaceRegistry.FOCUS_CARD_APP)!!
        val json = JSONObject().put("isGridWorkingCardBackgroundEnabled", true)
        assertTrue(ThemeSurfaceMapping.wantsImage(grid, json, ThemeSurfaceMapping.read(grid, json), true))
        assertFalse(ThemeSurfaceMapping.wantsImage(grid, json, ThemeSurfaceMapping.read(grid, json), false))
        assertFalse(ThemeSurfaceMapping.wantsImage(focus, json, ThemeSurfaceMapping.read(focus, json), true))
    }

    @Test fun surfaceNumbersUseFpDefaultsRatherThanPreviousThemeValues() {
        val grid = SurfaceRegistry.descriptor(SurfaceRegistry.GRID_WORK_CARD)!!
        val parsed = ThemeSurfaceMapping.read(grid, JSONObject())
        assertFalse(parsed.enabled)
        assertEquals(1f, parsed.opacity, 0f)
        assertEquals(0.3f, parsed.dim, 0f)
        assertTrue(parsed.flags.isEmpty())
    }
}
