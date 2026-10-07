package me.weishu.kernelsu.wallpaper.surface

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.ui.graphics.vector.ImageVector
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.model.HomeLayoutStyle

/**
 * Describes one configurable surface: where it sits in the scope chain, which home layouts it
 * belongs to, and which controls it exposes.
 *
 * [available] is the single source of truth for layout gating. The settings UI asks the registry
 * for the descriptors of the active layout instead of testing layout tokens itself, so a
 * layout-exclusive surface (the grid work card, for example) can never leak into another layout.
 *
 * A descriptor is pure data: its [rows] drive the settings UI, its [fields]/[flags] drive storage
 * and the theme container, and [storageStem]/[themeBase] name the image file and the zip entry.
 */
data class SurfaceDescriptor(
    val id: SurfaceId,
    /** Section title shown for this surface. */
    val titleRes: Int,
    /** Section icon shown for this surface. */
    val icon: ImageVector,
    val scope: SurfaceScope,
    /** Home layout tokens this surface belongs to; empty means every layout. */
    val layouts: Set<String> = emptySet(),
    /** The controls this surface exposes. */
    val fields: Set<SurfaceField>,
    /** Extra boolean toggles this surface exposes. */
    val flags: Set<SurfaceFlag> = emptySet(),
    /** The editable rows, in display order. */
    val rows: List<SurfaceRow> = emptyList(),
    val pickIcon: ImageVector = Icons.Filled.Image,
    val clearIcon: ImageVector = Icons.Filled.Delete,
    /** File-name stem of the stored image; null when the surface owns no file. */
    val storageStem: String? = null,
    /** Zip-entry stem used by the theme container; null when the surface is not exported. */
    val themeBase: String? = null,
    /** Historical `theme.json` keys kept for interop with themes from the wider ecosystem. */
    val legacyThemeFields: Map<SurfaceField, String> = emptyMap(),
    val legacyThemeFlags: Map<SurfaceFlag, String> = emptyMap(),
    /** Whether this surface is available for a home layout token such as [HomeLayoutStyle.GRID]. */
    val available: (String) -> Boolean = { true },
)

/**
 * The registry of every configurable wallpaper surface.
 *
 * Only the grid work card is a live surface today. The focus, dashboard and stats slots are
 * registered as reserved placeholders — their descriptors belong to the model but expose no fields
 * yet, so they render nothing and their values never leak. Adding a real surface later is a data
 * change here plus, when needed, a file stem.
 */
object SurfaceRegistry {

    /** Stable id of the grid work-card background surface. */
    val GRID_WORK_CARD = SurfaceId("layout.grid.workCard")

    private val gridWorkCard = SurfaceDescriptor(
        id = GRID_WORK_CARD,
        titleRes = R.string.wallpaper_work_card_section,
        icon = Icons.Filled.Wallpaper,
        scope = SurfaceScope.Slot,
        layouts = setOf(HomeLayoutStyle.GRID),
        fields = setOf(
            SurfaceField.Image,
            SurfaceField.Enabled,
            SurfaceField.Opacity,
            SurfaceField.DualOpacity,
            SurfaceField.DayOpacity,
            SurfaceField.NightOpacity,
            SurfaceField.Dim,
        ),
        flags = setOf(SurfaceFlag.HideIcon, SurfaceFlag.HideText, SurfaceFlag.HideMode),
        rows = listOf(
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_work_card_enable,
                icon = Icons.Filled.Wallpaper,
                field = SurfaceField.Enabled,
                summaryRes = R.string.wallpaper_work_card_enable_summary,
            ),
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_work_card_dual_opacity,
                icon = Icons.Filled.Contrast,
                field = SurfaceField.DualOpacity,
                showWhenEnabled = true,
            ),
            SurfaceOpacityRow(
                singleTitleRes = R.string.wallpaper_work_card_opacity,
                dayTitleRes = R.string.wallpaper_work_card_day_opacity,
                nightTitleRes = R.string.wallpaper_work_card_night_opacity,
                singleIcon = Icons.Filled.Opacity,
                dayNightIcon = Icons.Filled.Contrast,
            ),
            SurfaceSliderRow(
                titleRes = R.string.wallpaper_work_card_dim,
                icon = Icons.Filled.Brightness6,
                field = SurfaceField.Dim,
            ),
            SurfaceImageRow(
                pickTitleRes = R.string.wallpaper_work_card_pick,
                changeTitleRes = R.string.wallpaper_work_card_change,
                clearTitleRes = R.string.wallpaper_work_card_clear,
                pickIcon = Icons.Filled.Image,
                clearIcon = Icons.Filled.Delete,
            ),
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_work_card_hide_check,
                icon = Icons.Filled.CheckCircle,
                flag = SurfaceFlag.HideIcon,
            ),
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_work_card_hide_text,
                icon = Icons.Filled.TextFields,
                flag = SurfaceFlag.HideText,
            ),
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_work_card_hide_mode,
                icon = Icons.AutoMirrored.Filled.Label,
                flag = SurfaceFlag.HideMode,
            ),
        ),
        storageStem = "work_card_background",
        themeBase = "grid_working_card_background",
        legacyThemeFields = mapOf(
            SurfaceField.Enabled to "isGridWorkingCardBackgroundEnabled",
            SurfaceField.Opacity to "gridWorkingCardBackgroundOpacity",
            SurfaceField.Dim to "gridWorkingCardBackgroundDim",
            SurfaceField.DualOpacity to "isGridDualOpacityEnabled",
            SurfaceField.DayOpacity to "gridWorkingCardBackgroundDayOpacity",
            SurfaceField.NightOpacity to "gridWorkingCardBackgroundNightOpacity",
        ),
        legacyThemeFlags = mapOf(
            SurfaceFlag.HideIcon to "isGridWorkingCardCheckHidden",
            SurfaceFlag.HideText to "isGridWorkingCardTextHidden",
            SurfaceFlag.HideMode to "isGridWorkingCardModeHidden",
        ),
        available = { layout -> layout == HomeLayoutStyle.GRID },
    )

    /**
     * Reserved slot ids. They are part of the model so the next UI can be plugged in by filling in
     * its descriptor, but they expose no controls and are unavailable, so nothing renders and no
     * value is written for them yet.
     */
    private val reserved = listOf(
        reservedSlot("layout.focus.card.status", R.string.home_tile_status, Icons.Outlined.AdminPanelSettings),
        reservedSlot("layout.focus.card.manager", R.string.home_tile_manager, Icons.Outlined.AdminPanelSettings),
        reservedSlot("layout.focus.card.device", R.string.home_tile_device, Icons.Outlined.Memory),
        reservedSlot("layout.focus.card.storage", R.string.home_tile_storage, Icons.Outlined.SdStorage),
        reservedSlot("layout.dashboard.hero", R.string.home_tile_status, Icons.Outlined.Memory),
        reservedSlot("layout.stats.topCard", R.string.home_tile_status, Icons.Outlined.SdStorage),
    )

    private fun reservedSlot(id: String, titleRes: Int, icon: ImageVector): SurfaceDescriptor = SurfaceDescriptor(
        id = SurfaceId(id),
        titleRes = titleRes,
        icon = icon,
        scope = SurfaceScope.Slot,
        fields = emptySet(),
        available = { false },
    )

    /** Every registered surface. */
    val all: List<SurfaceDescriptor> = listOf(gridWorkCard) + reserved

    /** The surfaces available for a home layout token, in registration order. */
    fun forLayout(layout: String): List<SurfaceDescriptor> =
        all.filter { it.available(layout) }

    /** The descriptor registered under [id], or null when no surface uses it. */
    fun descriptor(id: SurfaceId): SurfaceDescriptor? = all.firstOrNull { it.id == id }

    /** The surfaces that carry a file and therefore a theme-container asset. */
    fun themeSlots(): List<SurfaceDescriptor> = all.filter { it.themeBase != null }
}
