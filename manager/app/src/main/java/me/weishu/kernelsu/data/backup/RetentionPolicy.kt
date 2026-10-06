package me.weishu.kernelsu.data.backup

/**
 * Pure retention decisions over a set of backup entries.
 *
 * Both functions return the entries that should be deleted and never mutate their input.
 */
object RetentionPolicy {

    /**
     * Returns the oldest entries to delete so that each module keeps at most [keepPerModule] of its
     * entries.
     *
     * At least one entry per group is always retained, even when [keepPerModule] is zero or
     * negative. Entries are ordered by [BackupEntry.createdAtEpochMs] descending, then by
     * [BackupEntry.relativePath] descending to keep the decision deterministic across equal
     * timestamps.
     */
    fun prune(entries: List<BackupEntry>, keepPerModule: Int): List<BackupEntry> {
        val keep = keepPerModule.coerceAtLeast(1)
        return entries
            .groupBy { groupKey(it) }
            .values
            .flatMap { group ->
                group.sortedWith(
                    compareByDescending<BackupEntry> { it.createdAtEpochMs }
                        .thenByDescending { it.relativePath },
                ).drop(keep)
            }
    }

    /**
     * Returns the entries older than [cutoffEpochMs].
     *
     * When [keepLatestPerModule] is `true` (the default) the newest entry of each group is never
     * returned, so a group is never pruned to empty.
     */
    fun pruneOlderThan(
        entries: List<BackupEntry>,
        cutoffEpochMs: Long,
        keepLatestPerModule: Boolean = true,
    ): List<BackupEntry> {
        val protectedEntries = if (keepLatestPerModule) {
            entries.groupBy { groupKey(it) }.values.mapNotNull { group ->
                group.maxWithOrNull(
                    compareBy<BackupEntry> { it.createdAtEpochMs }.thenBy { it.relativePath },
                )
            }.toSet()
        } else {
            emptySet()
        }
        return entries.filter { it.createdAtEpochMs < cutoffEpochMs && it !in protectedEntries }
    }

    private fun groupKey(entry: BackupEntry): String = when (entry.kind) {
        BackupKind.MODULE -> "module:${entry.moduleId.orEmpty()}"
        BackupKind.ALLOWLIST, BackupKind.BOOT -> "kind:${entry.kind.value}"
    }
}
