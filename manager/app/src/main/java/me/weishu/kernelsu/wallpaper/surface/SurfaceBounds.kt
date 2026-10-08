package me.weishu.kernelsu.wallpaper.surface

/**
 * The on-screen box of every surface, recorded from layout callbacks while the card is painted.
 *
 * The crop step reads the box of the surface being edited so the crop frame can be locked to the
 * exact aspect the card is drawn at, which is what makes the stored image match the card
 * one-to-one. Values are written during layout and only read when a pick starts, so a plain map
 * (no composition state) is enough and cannot trigger a recomposition loop.
 */
object SurfaceBounds {

    private val aspects = HashMap<SurfaceId, Float>()

    /** Records the measured pixel box of [id]; ignores empty boxes. */
    fun report(id: SurfaceId, width: Int, height: Int) {
        if (width > 0 && height > 0) {
            aspects[id] = width.toFloat() / height.toFloat()
        }
    }

    /** The measured aspect ratio of [id], or null when the card has not been laid out yet. */
    fun aspect(id: SurfaceId): Float? = aspects[id]
}

/**
 * The aspect the crop frame must lock to for [id]: the box measured on screen, or the descriptor's
 * declared aspect when the card has not been painted yet.
 */
fun surfaceAspect(id: SurfaceId): Float =
    SurfaceBounds.aspect(id) ?: SurfaceRegistry.descriptor(id)?.aspect ?: 1.6f
