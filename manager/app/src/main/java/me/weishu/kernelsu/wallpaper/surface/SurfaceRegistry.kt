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
    /**
     * Aspect ratio (width / height) of the surface's on-screen box. The crop frame is locked to it
     * so the stored image matches the card one-to-one; [SurfaceBounds] overrides it with the box
     * actually measured on screen.
     */
    val aspect: Float = 1.6f,
    /** File-name stem of the stored image; null when the surface owns no file. */
    val storageStem: String? = null,
    /** Zip-entry stem used by the theme container; null when the surface is not exported. */
    val themeBase: String? = null,
    /** Historical `theme.json` keys kept for interop with themes from the wider ecosystem. */
    val legacyThemeFields: Map<SurfaceField, String> = emptyMap(),
    val legacyThemeFlags: Map<SurfaceFlag, String> = emptyMap(),
    /** Whether this surface is available for a home layout token such as [HomeLayoutStyle.GRID]. */
    val available: (String) -> Boolean = { true },
    /**
     * Id of the surface that owns this one's shared master switch, when this is a child of a grouped
     * feature. The focus cards are children of [FOCUS], whose `enabled` bit is the single gate the
     * settings host and the renderer both consult. Null for top-level surfaces.
     */
    val parentId: SurfaceId? = null,
)

/**
 * The registry of every configurable wallpaper surface.
 *
 * The grid work card, the focus cards and the dashboard hero are live surfaces: each renders its
 * stored image and exposes its rows for its own layout. The stats top card is described but stays
 * unavailable, so it renders nothing and its settings never leak; its values still round-trip
 * through the theme container. Flipping [SurfaceDescriptor.available] on is what turns one into a
 * live surface once its rendering exists.
 */
object SurfaceRegistry {

    /** Stable id of the grid work-card background surface. */
    val GRID_WORK_CARD = SurfaceId("layout.grid.workCard")

    /** Shared config of the focus layout's card backgrounds; its four cards own only their image. */
    val FOCUS = SurfaceId("layout.focus")

    /** The four focus-layout card slots, mirroring the wider ecosystem's card ids. */
    val FOCUS_CARD_KERNEL = SurfaceId("layout.focus.card.kernel")
    val FOCUS_CARD_APP = SurfaceId("layout.focus.card.app")
    val FOCUS_CARD_DEVICE = SurfaceId("layout.focus.card.device")
    val FOCUS_CARD_STORAGE = SurfaceId("layout.focus.card.storage")

    /** The dashboard layout's hero card. */
    val DASHBOARD_HERO = SurfaceId("layout.dashboard.hero")

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
            SurfaceScalarRow(
                singleTitleRes = R.string.wallpaper_work_card_opacity,
                dayTitleRes = R.string.wallpaper_work_card_day_opacity,
                nightTitleRes = R.string.wallpaper_work_card_night_opacity,
                singleField = SurfaceField.Opacity,
                dayField = SurfaceField.DayOpacity,
                nightField = SurfaceField.NightOpacity,
                dualField = SurfaceField.DualOpacity,
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
        aspect = 1.0f,
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
     * The focus layout shares one set of dim/opacity values across its four cards, so the shared
     * values live on this parent surface and each card below stores only its own image. This
     * mirrors the wider ecosystem's keys exactly and keeps the two halves from drifting.
     */
    private val focus = SurfaceDescriptor(
        id = FOCUS,
        titleRes = R.string.settings_home_layout_focus,
        icon = Icons.Outlined.AdminPanelSettings,
        scope = SurfaceScope.Layout,
        layouts = setOf(HomeLayoutStyle.FOCUS),
        fields = setOf(
            SurfaceField.Enabled,
            SurfaceField.Opacity,
            SurfaceField.DualOpacity,
            SurfaceField.DayOpacity,
            SurfaceField.NightOpacity,
            SurfaceField.Dim,
            SurfaceField.DualDim,
            SurfaceField.DayDim,
            SurfaceField.NightDim,
        ),
        legacyThemeFields = mapOf(
            SurfaceField.Enabled to "isFocusCardBackgroundEnabled",
            SurfaceField.Dim to "focusCardBgDim",
            SurfaceField.DualDim to "isFocusCardDualDimEnabled",
            SurfaceField.DayDim to "focusCardBgDayDim",
            SurfaceField.NightDim to "focusCardBgNightDim",
            SurfaceField.Opacity to "focusCardBgOpacity",
            SurfaceField.DualOpacity to "isFocusCardDualOpacityEnabled",
            SurfaceField.DayOpacity to "focusCardBgDayOpacity",
            SurfaceField.NightOpacity to "focusCardBgNightOpacity",
        ),
        rows = listOf(
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_focus_enable,
                icon = Icons.Filled.Wallpaper,
                field = SurfaceField.Enabled,
                summaryRes = R.string.wallpaper_focus_enable_summary,
            ),
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_work_card_dual_opacity,
                icon = Icons.Filled.Contrast,
                field = SurfaceField.DualOpacity,
                showWhenEnabled = true,
            ),
            SurfaceScalarRow(
                singleTitleRes = R.string.wallpaper_work_card_opacity,
                dayTitleRes = R.string.wallpaper_work_card_day_opacity,
                nightTitleRes = R.string.wallpaper_work_card_night_opacity,
                singleField = SurfaceField.Opacity,
                dayField = SurfaceField.DayOpacity,
                nightField = SurfaceField.NightOpacity,
                dualField = SurfaceField.DualOpacity,
                singleIcon = Icons.Filled.Opacity,
                dayNightIcon = Icons.Filled.Contrast,
            ),
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_dual_dim,
                icon = Icons.Filled.Brightness6,
                field = SurfaceField.DualDim,
                showWhenEnabled = true,
            ),
            SurfaceScalarRow(
                singleTitleRes = R.string.wallpaper_work_card_dim,
                dayTitleRes = R.string.wallpaper_day_dim,
                nightTitleRes = R.string.wallpaper_night_dim,
                singleField = SurfaceField.Dim,
                dayField = SurfaceField.DayDim,
                nightField = SurfaceField.NightDim,
                dualField = SurfaceField.DualDim,
                singleIcon = Icons.Filled.Brightness6,
                dayNightIcon = Icons.Filled.Contrast,
            ),
        ),
        available = { layout -> layout == HomeLayoutStyle.FOCUS },
    )

    private val focusCards = listOf(
        focusCard(
            FOCUS_CARD_KERNEL,
            R.string.home_tile_status,
            Icons.Outlined.AdminPanelSettings,
            "focus_card_kernel_bg",
            "hasFocusCardKernelBg",
        ),
        focusCard(
            FOCUS_CARD_APP,
            R.string.home_tile_manager,
            Icons.Outlined.Memory,
            "focus_card_app_bg",
            "hasFocusCardAppBg",
        ),
        focusCard(
            FOCUS_CARD_DEVICE,
            R.string.home_tile_device,
            Icons.Outlined.Memory,
            "focus_card_device_bg",
            "hasFocusCardDeviceBg",
        ),
        focusCard(
            FOCUS_CARD_STORAGE,
            R.string.home_tile_storage,
            Icons.Outlined.SdStorage,
            "focus_card_storage_bg",
            "hasFocusCardStorageBg",
        ),
    )

    /** The dashboard layout's hero card, which owns an image plus its own dim/opacity values. */
    private val dashboardHero = SurfaceDescriptor(
        id = DASHBOARD_HERO,
        titleRes = R.string.settings_home_layout_dashboard,
        icon = Icons.Outlined.Memory,
        scope = SurfaceScope.Slot,
        layouts = setOf(HomeLayoutStyle.DASHBOARD),
        fields = setOf(
            SurfaceField.Image,
            SurfaceField.Enabled,
            SurfaceField.Opacity,
            SurfaceField.DualOpacity,
            SurfaceField.DayOpacity,
            SurfaceField.NightOpacity,
            SurfaceField.Dim,
            SurfaceField.DualDim,
            SurfaceField.DayDim,
            SurfaceField.NightDim,
        ),
        rows = listOf(
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_surface_enable,
                icon = Icons.Filled.Wallpaper,
                field = SurfaceField.Enabled,
                summaryRes = R.string.wallpaper_surface_enable_summary,
            ),
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_work_card_dual_opacity,
                icon = Icons.Filled.Contrast,
                field = SurfaceField.DualOpacity,
                showWhenEnabled = true,
            ),
            SurfaceScalarRow(
                singleTitleRes = R.string.wallpaper_work_card_opacity,
                dayTitleRes = R.string.wallpaper_work_card_day_opacity,
                nightTitleRes = R.string.wallpaper_work_card_night_opacity,
                singleField = SurfaceField.Opacity,
                dayField = SurfaceField.DayOpacity,
                nightField = SurfaceField.NightOpacity,
                dualField = SurfaceField.DualOpacity,
                singleIcon = Icons.Filled.Opacity,
                dayNightIcon = Icons.Filled.Contrast,
            ),
            SurfaceToggleRow(
                titleRes = R.string.wallpaper_dual_dim,
                icon = Icons.Filled.Brightness6,
                field = SurfaceField.DualDim,
                showWhenEnabled = true,
            ),
            SurfaceScalarRow(
                singleTitleRes = R.string.wallpaper_work_card_dim,
                dayTitleRes = R.string.wallpaper_day_dim,
                nightTitleRes = R.string.wallpaper_night_dim,
                singleField = SurfaceField.Dim,
                dayField = SurfaceField.DayDim,
                nightField = SurfaceField.NightDim,
                dualField = SurfaceField.DualDim,
                singleIcon = Icons.Filled.Brightness6,
                dayNightIcon = Icons.Filled.Contrast,
            ),
            SurfaceImageRow(
                pickTitleRes = R.string.wallpaper_pick,
                changeTitleRes = R.string.wallpaper_change,
                clearTitleRes = R.string.wallpaper_clear,
                pickIcon = Icons.Filled.Image,
                clearIcon = Icons.Filled.Delete,
            ),
        ),
        storageStem = "dashboard_card_bg",
        themeBase = "dashboard_card_bg",
        aspect = 2.0f,
        legacyThemeFields = mapOf(
            SurfaceField.Image to "hasDashboardCardBg",
            SurfaceField.Enabled to "isDashboardCardBackgroundEnabled",
            SurfaceField.Dim to "dashboardCardBgDim",
            SurfaceField.DualDim to "isDashboardCardDualDimEnabled",
            SurfaceField.DayDim to "dashboardCardBgDayDim",
            SurfaceField.NightDim to "dashboardCardBgNightDim",
            SurfaceField.Opacity to "dashboardCardBgOpacity",
            SurfaceField.DualOpacity to "isDashboardCardDualOpacityEnabled",
            SurfaceField.DayOpacity to "dashboardCardBgDayOpacity",
            SurfaceField.NightOpacity to "dashboardCardBgNightOpacity",
        ),
        available = { layout -> layout == HomeLayoutStyle.DASHBOARD },
    )

    /**
     * A focus card that stores its own image; the shared values live on [focus]. The image also
     * carries the surface's Enabled bit so a card keeps its photo across a restart, and that bit is
     * the same presence flag the wider ecosystem records as `has<Card>Bg`.
     */
    private fun focusCard(
        id: SurfaceId,
        titleRes: Int,
        icon: ImageVector,
        themeBase: String,
        legacyKey: String,
    ): SurfaceDescriptor = SurfaceDescriptor(
        id = id,
        titleRes = titleRes,
        icon = icon,
        scope = SurfaceScope.Slot,
        layouts = setOf(HomeLayoutStyle.FOCUS),
        fields = setOf(SurfaceField.Image, SurfaceField.Enabled),
        rows = listOf(
            SurfaceImageRow(
                pickTitleRes = R.string.wallpaper_pick,
                changeTitleRes = R.string.wallpaper_change,
                clearTitleRes = R.string.wallpaper_clear,
                pickIcon = Icons.Filled.Image,
                clearIcon = Icons.Filled.Delete,
                showWhenEnabled = false,
            ),
        ),
        storageStem = themeBase,
        themeBase = themeBase,
        aspect = 1.4f,
        legacyThemeFields = mapOf(SurfaceField.Enabled to legacyKey),
        available = { layout -> layout == HomeLayoutStyle.FOCUS },
        parentId = FOCUS,
    )

    /** Reserved slot for the stats layout; the model is in place but exposes no controls yet. */
    private val statsTopCard = reservedSlot("layout.stats.topCard", R.string.home_tile_status, Icons.Outlined.SdStorage)

    private fun reservedSlot(id: String, titleRes: Int, icon: ImageVector): SurfaceDescriptor = SurfaceDescriptor(
        id = SurfaceId(id),
        titleRes = titleRes,
        icon = icon,
        scope = SurfaceScope.Slot,
        fields = emptySet(),
        available = { false },
    )

    /** Every registered surface. */
    val all: List<SurfaceDescriptor> =
        listOf(gridWorkCard, focus) + focusCards + listOf(dashboardHero, statsTopCard)

    /** The surfaces available for a home layout token, in registration order. */
    fun forLayout(layout: String): List<SurfaceDescriptor> =
        all.filter { it.available(layout) }

    /** The descriptor registered under [id], or null when no surface uses it. */
    fun descriptor(id: SurfaceId): SurfaceDescriptor? = all.firstOrNull { it.id == id }

    /**
     * The surfaces that carry theme data: either a file, or historical `theme.json` keys, so their
     * values round-trip through the theme container. A config-only surface (the focus parent) has no
     * file but is still exported for its shared dim/opacity values.
     */
    fun themeSlots(): List<SurfaceDescriptor> =
        all.filter {
            it.themeBase != null || it.legacyThemeFields.isNotEmpty() || it.legacyThemeFlags.isNotEmpty()
        }
}
