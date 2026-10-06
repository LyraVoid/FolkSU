package me.weishu.kernelsu.data.backup

/**
 * A persistence backend for backup artifacts.
 *
 * Implementations address content by a storage-relative path (see [remotePathFor]) and are expected
 * to be stateless between calls. All operations report failures through [Result] rather than
 * throwing, so a failing backend never aborts a batch.
 */
interface BackupStorage {

    /** Verifies that the backend is reachable and writable. */
    suspend fun test(): Result<Unit>

    /** Writes [bytes] at [relativePath], replacing any existing content. */
    suspend fun put(relativePath: String, bytes: ByteArray): Result<Unit>

    /** Reads the content stored at [relativePath]. */
    suspend fun get(relativePath: String): Result<ByteArray>

    /** Lists entries whose path starts with [prefix]. */
    suspend fun list(prefix: String): Result<List<RemoteEntry>>

    /** Deletes the content stored at [relativePath]. */
    suspend fun delete(relativePath: String): Result<Unit>
}
