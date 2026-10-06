package me.weishu.kernelsu.data.backup

/**
 * Pure duplicate decisions for backup writes.
 *
 * Content identity is the artifact SHA-256; the relative path is the storage identity. An entry
 * already present at the same path is a duplicate only when its stored hash matches, so a changed
 * artifact overwrites the old one. A hash already present at another path is also treated as a
 * duplicate, matching the content-addressed dedup rule.
 */
object DuplicatePolicy {

    /**
     * Returns `true` when [relativePath] with [sha256] is already present in [existingByPath].
     *
     * [existingByPath] maps storage-relative paths to their optional stored hash. A `null` hash
     * means the backend could not report one; in that case only an identical hash at another path
     * can classify the artifact as a duplicate. A blank [sha256] is never treated as a duplicate.
     */
    fun isDuplicate(
        existingByPath: Map<String, String?>,
        relativePath: String,
        sha256: String,
    ): Boolean {
        if (sha256.isBlank()) return false
        val samePathHash = existingByPath[relativePath]
        if (samePathHash != null) return samePathHash.equals(sha256, ignoreCase = true)
        return existingByPath.values.any { it != null && it.equals(sha256, ignoreCase = true) }
    }
}
