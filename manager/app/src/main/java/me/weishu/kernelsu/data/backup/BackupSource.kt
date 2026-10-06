package me.weishu.kernelsu.data.backup

/**
 * A backend that produces backup payloads and can restore them.
 *
 * A source knows how to enumerate the data it protects ([list]), turn that data into artifacts
 * ([export]) and apply an entry back to the system ([restore]). Persistence is a separate concern,
 * handled by a [BackupStorage]. Every method reports failures through [Result] rather than throwing.
 */
interface BackupSource {

    /** The kind of data this source handles. */
    val kind: BackupKind

    /** Lists the entries this source currently knows about. */
    suspend fun list(): Result<List<BackupEntry>>

    /** Produces the artifacts that should be persisted by a [BackupStorage]. */
    suspend fun export(): Result<List<BackupArtifact>>

    /** Applies [entry] back to the system. */
    suspend fun restore(entry: BackupEntry): Result<Unit>

    /** Deletes [entry] from the source's own inventory, when one exists. */
    suspend fun delete(entry: BackupEntry): Result<Unit>
}

/**
 * Resolves the [BackupSource] for a [BackupKind].
 *
 * When several sources declare the same kind, the last one wins; callers that need a stable choice
 * should not register duplicates.
 */
class BackupSourceRegistry(sources: List<BackupSource>) {

    private val byKind: Map<BackupKind, BackupSource> = sources.associateBy { it.kind }

    /** Returns the source registered for [kind], or `null` when none is available. */
    fun sourceFor(kind: BackupKind): BackupSource? = byKind[kind]

    /** The kinds that have a registered source. */
    val kinds: Set<BackupKind> get() = byKind.keys
}
