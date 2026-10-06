package me.weishu.kernelsu.data.backup

/**
 * Pure helpers for assembling paths on a remote backend.
 *
 * The functions perform no IO and never return an absolute path unless the base itself is
 * absolute.
 */
object WebDavPaths {

    /**
     * Joins a remote [base] with a relative [sub] path using exactly one separator.
     *
     * An empty [base] returns the normalised [sub]; an absolute base keeps its leading slash
     * because it is part of [base].
     */
    fun joinBase(base: String, sub: String): String {
        val normalizedBase = base.trim()
        val normalizedSub = sub.trim().trimStart('/')
        if (normalizedBase.isEmpty()) return normalizedSub
        return normalizedBase.trimEnd('/') + "/" + normalizedSub
    }

    /**
     * Returns every ancestor directory of [path], from the topmost to [path] itself.
     *
     * Leading and trailing separators are ignored, so `/a/b/c`, `a/b/c` and `/a/b/c/` all yield
     * `[a, a/b, a/b/c]`.
     */
    fun parentDirs(path: String): List<String> {
        val parts = path.trim().trim('/').split('/').filter { it.isNotEmpty() }
        val directories = ArrayList<String>(parts.size)
        val current = StringBuilder()
        for (part in parts) {
            if (current.isNotEmpty()) current.append('/')
            current.append(part)
            directories += current.toString()
        }
        return directories
    }
}
