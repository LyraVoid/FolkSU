package me.weishu.kernelsu.wallpaper.surface

/**
 * Position of a configurable surface in the scope chain (L0..L3).
 *
 * v1 keeps the model flat: every registered surface owns its own values and no surface inherits
 * from a broader scope yet. The enum only reserves the vocabulary so a resolver can be added
 * later without renaming anything.
 */
enum class SurfaceScope { Global, Page, Layout, Slot }

/**
 * Stable identifier for a configurable wallpaper surface.
 *
 * Examples: `global`, `page.home`, `layout.grid`, `layout.grid.workCard`. Values are dotted,
 * lowercase and never localized, so they are safe as preference-key fragments and as theme
 * metadata.
 */
@JvmInline
value class SurfaceId(val value: String) {
    override fun toString(): String = value
}
