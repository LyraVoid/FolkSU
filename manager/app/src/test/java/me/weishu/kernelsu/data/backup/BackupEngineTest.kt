package me.weishu.kernelsu.data.backup

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEngineTest {

    private val epochMs = 1_700_000_000_000L

    @Test
    fun `happy path writes payload and sidecar`() {
        val storage = InMemoryBackupStorage()
        val artifact = moduleArtifact("example.module", "1.0", 1, "payload")
        val engine = engineWith(artifact, storage)

        val result = runBlocking { engine.backup(BackupKind.MODULE) }

        val zipPath = "modules/example.module/${artifact.fileName}"
        val metaPath = "modules/example.module/${artifact.metaFileName}"
        assertTrue(result.isSuccess)
        assertEquals(listOf(zipPath, metaPath), result.written)
        assertEquals("payload", String(storage.files.getValue(zipPath)))
        assertTrue(storage.files.containsKey(metaPath))
    }

    @Test
    fun `identical content is skipped`() {
        val storage = InMemoryBackupStorage()
        val artifact = moduleArtifact("example.module", "1.0", 1, "payload")
        val zipPath = "modules/example.module/${artifact.fileName}"
        storage.files[zipPath] = artifact.bytes

        val result = runBlocking { engineWith(artifact, storage).backup(BackupKind.MODULE) }

        assertTrue(result.isSuccess)
        assertEquals(listOf(zipPath), result.skipped)
        assertTrue(result.written.isEmpty())
    }

    @Test
    fun `a failing backend reports a partial run`() {
        val healthy = InMemoryBackupStorage("healthy")
        val broken = InMemoryBackupStorage("broken").apply {
            putFailure = { Throwable("disk full") }
        }
        val artifact = moduleArtifact("example.module", "1.0", 1, "payload")
        val engine = BackupEngine(
            BackupSourceRegistry(listOf(FakeBackupSource(BackupKind.MODULE, artifacts = listOf(artifact)))),
            listOf(healthy, broken),
        )

        val result = runBlocking { engine.backup(BackupKind.MODULE) }

        assertFalse(result.isSuccess)
        assertTrue(result.isPartial)
        assertFalse(result.isFailure)
        assertEquals(2, result.written.size)
        assertEquals(2, result.failures.size)
        assertTrue(result.failures.all { it.storage == "broken" })
    }

    @Test
    fun `every backend failing reports a failure`() {
        val broken = InMemoryBackupStorage("broken").apply {
            putFailure = { Throwable("disk full") }
        }
        val artifact = moduleArtifact("example.module", "1.0", 1, "payload")
        val result = runBlocking { engineWith(artifact, broken).backup(BackupKind.MODULE) }

        assertTrue(result.isFailure)
        assertFalse(result.isPartial)
        assertTrue(result.written.isEmpty())
        assertEquals(2, result.failures.size)
    }

    @Test
    fun `a listing failure is recorded and aborts that backend`() {
        val storage = InMemoryBackupStorage("offline").apply {
            listFailure = Throwable("connection refused")
        }
        val artifact = moduleArtifact("example.module", "1.0", 1, "payload")
        val result = runBlocking { engineWith(artifact, storage).backup(BackupKind.MODULE) }

        assertTrue(result.isFailure)
        assertEquals(listOf("list"), result.failures.map { it.operation })
        assertEquals("connection refused", result.failures.single().cause.message)
    }

    @Test
    fun `a missing source reports a failure`() {
        val engine = BackupEngine(BackupSourceRegistry(emptyList()), listOf(InMemoryBackupStorage()))
        val result = runBlocking { engine.backup(BackupKind.ALLOWLIST) }

        assertTrue(result.isFailure)
        assertEquals(listOf("source"), result.failures.map { it.operation })
    }

    @Test
    fun `an export failure reports a failure`() {
        val source = FakeBackupSource(BackupKind.MODULE, exportFailure = Throwable("capture failed"))
        val engine = BackupEngine(BackupSourceRegistry(listOf(source)), listOf(InMemoryBackupStorage()))
        val result = runBlocking { engine.backup(BackupKind.MODULE) }

        assertTrue(result.isFailure)
        assertEquals(listOf("export"), result.failures.map { it.operation })
    }

    @Test
    fun `restore delegates to the source`() {
        val source = FakeBackupSource(BackupKind.MODULE)
        val engine = BackupEngine(BackupSourceRegistry(listOf(source)), emptyList())
        val entry = BackupEntry(id = "example.module", kind = BackupKind.MODULE, displayName = "example.module")

        assertTrue(runBlocking { engine.restore(BackupKind.MODULE, entry) }.isSuccess)
        assertEquals(1, source.restoreCount)
        assertEquals(entry, source.lastRestored)
    }

    @Test
    fun `restore without a source fails`() {
        val engine = BackupEngine(BackupSourceRegistry(emptyList()), emptyList())
        val entry = BackupEntry(id = "x", kind = BackupKind.BOOT, displayName = "x")
        assertTrue(runBlocking { engine.restore(BackupKind.BOOT, entry) }.isFailure)
    }

    @Test
    fun `registry resolves only registered kinds`() {
        val source = FakeBackupSource(BackupKind.MODULE)
        val registry = BackupSourceRegistry(listOf(source))

        assertEquals(source, registry.sourceFor(BackupKind.MODULE))
        assertNull(registry.sourceFor(BackupKind.BOOT))
        assertEquals(setOf(BackupKind.MODULE), registry.kinds)
    }

    private fun engineWith(
        artifact: BackupArtifact,
        storage: InMemoryBackupStorage = InMemoryBackupStorage(),
    ): BackupEngine = BackupEngine(
        BackupSourceRegistry(listOf(FakeBackupSource(BackupKind.MODULE, artifacts = listOf(artifact)))),
        listOf(storage),
    )

    private fun moduleArtifact(
        moduleId: String,
        versionName: String,
        versionCode: Int,
        payload: String,
    ): BackupArtifact {
        val bytes = payload.toByteArray()
        val fileName = moduleArchiveName(moduleId, versionName, versionCode, epochMs)
        return BackupArtifact(
            kind = BackupKind.MODULE,
            moduleId = moduleId,
            fileName = fileName,
            metaFileName = moduleMetaFileName(fileName),
            bytes = bytes,
            sha256 = bytes.sha256(),
            metaJson = """{"schema":"folksu.module.backup","version":1}""",
        )
    }
}
