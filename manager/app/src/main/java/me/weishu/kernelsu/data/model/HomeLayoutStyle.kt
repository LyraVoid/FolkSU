package me.weishu.kernelsu.data.model

/**
 * Tokens persisted under the `home_layout_style` preference key.
 *
 * The strings are fixed and intentionally kept identical to the tokens the appearance settings
 * write, so a stored configuration maps onto the same layout. Unknown or legacy tokens fall back
 * to [CIRCLE] instead of failing.
 */
object HomeLayoutStyle {
    /** The standard single-column home. */
    const val CIRCLE = "circle"

    /** The two-column card grid home. */
    const val GRID = "kernelsu"

    /** Layout used when the preference is missing or holds an unsupported token. */
    const val DEFAULT = CIRCLE

    /** Normalise a stored value to a supported token. */
    fun fromValue(value: String?): String = when (value) {
        GRID -> GRID
        CIRCLE -> CIRCLE
        else -> DEFAULT
    }

    /** Every supported token, in display order. */
    val supported: List<String> = listOf(CIRCLE, GRID)
}
