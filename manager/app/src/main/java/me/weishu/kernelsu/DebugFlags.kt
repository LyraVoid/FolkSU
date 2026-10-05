package me.weishu.kernelsu

/**
 * Debug-only switches for local UI development.
 *
 * Every value here is forced to `false` in release builds via [BuildConfig.DEBUG].
 */
object DebugFlags {
    /**
     * Pretend a matching KernelSU kernel is present so the whole manager UI
     * (bottom bar and the SuperUser / Module / Settings pages) can be previewed
     * on a device without KernelSU installed.
     *
     * Debug builds only. Data loaded from the kernel may be empty, but the
     * layout and navigation stay reachable.
     */
    val forceFullFeatured: Boolean = BuildConfig.DEBUG
}
