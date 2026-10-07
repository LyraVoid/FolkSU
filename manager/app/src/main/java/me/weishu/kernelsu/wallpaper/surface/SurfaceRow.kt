package me.weishu.kernelsu.wallpaper.surface

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One editable row of a surface's settings, in display order.
 *
 * A [SurfaceDescriptor] lists its rows as data; the settings host renders each row from the
 * surface's current [SurfaceConfig]. Presentation (title, icon) lives with the descriptor, so
 * registering a new surface is a data change instead of a new settings screen.
 */
sealed interface SurfaceRow {
    /** Tuning rows belong to the surface's enabled block and stay hidden while the surface is off. */
    val showWhenEnabled: Boolean
}

/** A switch bound to [field] or to the standalone [flag] toggle. */
data class SurfaceToggleRow(
    val titleRes: Int,
    val icon: ImageVector,
    val field: SurfaceField? = null,
    val flag: SurfaceFlag? = null,
    val summaryRes: Int? = null,
    override val showWhenEnabled: Boolean = false,
) : SurfaceRow

/** A 0..1 slider bound to [field]. */
data class SurfaceSliderRow(
    val titleRes: Int,
    val icon: ImageVector,
    val field: SurfaceField,
    override val showWhenEnabled: Boolean = true,
) : SurfaceRow

/**
 * The opacity block: one slider bound to [SurfaceField.Opacity], or a day and a night slider when
 * [SurfaceField.DualOpacity] is on. The dual toggle itself is a separate [SurfaceToggleRow].
 */
data class SurfaceOpacityRow(
    val singleTitleRes: Int,
    val dayTitleRes: Int,
    val nightTitleRes: Int,
    val singleIcon: ImageVector,
    val dayNightIcon: ImageVector,
    override val showWhenEnabled: Boolean = true,
) : SurfaceRow

/** The pick/change action for [SurfaceField.Image] and, once an image is set, its clear action. */
data class SurfaceImageRow(
    val pickTitleRes: Int,
    val changeTitleRes: Int,
    val clearTitleRes: Int,
    val pickIcon: ImageVector,
    val clearIcon: ImageVector,
    override val showWhenEnabled: Boolean = true,
) : SurfaceRow
