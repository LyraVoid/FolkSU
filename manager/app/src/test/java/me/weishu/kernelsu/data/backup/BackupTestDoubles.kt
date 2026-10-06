package me.weishu.kernelsu.data.backup

/** In-memory [BackupStorage] used to exercise the engine without any IO. */
class InMemoryBackupStorage(private val label: String = "memory") : BackupStorage {

    val files: MutableMap<String, ByteArray> = LinkedHashMap()

    /** Returns a failure for [put] when it returns a non-null throwable. */
    var putFailure: ((relativePath: String) -> Throwable?)? = null

    /** When non-null, every [list] call fails with this throwable. */
    var listFailure: Throwable? = null

    override suspend fun test(): Result<Unit> = Result.success(Unit)

    override suspend fun put(relativePath: String, bytes: ByteArray): Result<Unit> {
        putFailure?.invoke(relativePath)?.let { return Result.failure(it) }
        files[relativePath] = bytes
        return Result.success(Unit)
    }

    override suspend fun get(relativePath: String): Result<ByteArray> {
        val bytes = files[relativePath]
            ?: return Result.failure(NoSuchElementException("missing: $relativePath"))
        return Result.success(bytes)
    }

    override suspend fun list(prefix: String): Result<List<RemoteEntry>> {
        listFailure?.let { return Result.failure(it) }
        return Result.success(
            files
                .filterKeys { it.startsWith(prefix) }
                .map { (path, bytes) ->
                    RemoteEntry(relativePath = path, sizeBytes = bytes.size.toLong(), sha256 = bytes.sha256())
                },
        )
    }

    override suspend fun delete(relativePath: String): Result<Unit> {
        files.remove(relativePath)
        return Result.success(Unit)
    }

    override fun toString(): String = label
}

/** Scriptable [BackupSource] used to exercise the engine without any IO. */
class FakeBackupSource(
    override val kind: BackupKind,
    private val artifacts: List<BackupArtifact> = emptyList(),
    private val entries: List<BackupEntry> = emptyList(),
    private val exportFailure: Throwable? = null,
) : BackupSource {

    var restoreCount: Int = 0
        private set

    var lastRestored: BackupEntry? = null
        private set

    override suspend fun list(): Result<List<BackupEntry>> = Result.success(entries)

    override suspend fun export(): Result<List<BackupArtifact>> {
        exportFailure?.let { return Result.failure(it) }
        return Result.success(artifacts)
    }

    override suspend fun restore(entry: BackupEntry): Result<Unit> {
        restoreCount++
        lastRestored = entry
        return Result.success(Unit)
    }

    override suspend fun delete(entry: BackupEntry): Result<Unit> = Result.success(Unit)

    override fun toString(): String = "source:${kind.value}"
}
