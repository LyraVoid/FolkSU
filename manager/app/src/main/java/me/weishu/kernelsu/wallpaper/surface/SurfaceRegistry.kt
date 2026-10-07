package me.weishu.kernelsu.wallpaper.surface

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Wallpaper
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
    val pickIcon: ImageVector = Icons.Outlined.Image,
    val clearIcon: ImageVector = Icons.Outlined.Delete,
    /** Whether this surface is available for a home layout token such as [HomeLayoutStyle.GRID]. */
    val available: (String) -> Boolean = { true },
)

/**
 * The registry of every configurable wallpaper surface.
 *
 * v1 registers only the grid work card; global and per-page surfaces are added in a later stage.
 * Keeping the registry authoritative now means the settings host can already filter by layout.
 */
object SurfaceRegistry {

    private val gridWorkCard = SurfaceDescriptor(
        id = SurfaceId("layout.grid.workCard"),
        titleRes = R.string.wallpaper_work_card_section,
        icon = Icons.Outlined.Wallpaper,
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
        available = { layout -> layout == HomeLayoutStyle.GRID },
    )

    /** Every registered surface. */
    val all: List<SurfaceDescriptor> = listOf(gridWorkCard)

    /** The surfaces available for a home layout token, in registration order. */
    fun forLayout(layout: String): List<SurfaceDescriptor> =
        all.filter { it.available(layout) }

    /** The descriptor registered under [id], or null when no surface uses it. */
    fun descriptor(id: SurfaceId): SurfaceDescriptor? = all.firstOrNull { it.id == id }
}
