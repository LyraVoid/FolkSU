package me.weishu.kernelsu.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupManifestTest {

    private val moduleMeta = ModuleBackupMeta(
        moduleId = "example.module",
        name = "Example Module",
        versionName = "1.2.3",
        versionCode = 123,
        author = "someone",
        metamodule = true,
        originalFileName = "Example-1.2.3.zip",
        sizeBytes = 1234567,
        sha256 = "e3b0c442",
        source = BackupOrigin.ARCHIVED_INSTALLER,
        createdAt = "2026-10-06T12:00:00Z",
    )

    private val bootMeta = BootBackupMeta(
        partition = "init_boot",
        sha1 = "da39a3ee",
        sizeBytes = 8388608,
        device = "ONEPLUS_A",
        fingerprint = "OnePlus/A:16/BUILD",
        createdAt = "2026-10-06T12:00:00Z",
    )

    private val manifest = BackupManifest(
        updatedAt = "2026-10-06T12:00:00Z",
        entries = listOf(
            BackupManifestEntry(
                path = "modules/example.module/example.module-1.2.3-123-20261006_120000.zip",
                sha256 = "e3b0c442",
                sizeBytes = 1234567,
                kind = BackupKind.MODULE,
                createdAt = "2026-10-06T12:00:00Z",
            ),
            BackupManifestEntry(
                path = "boot/init_boot-stock-da39a3ee.img",
                sha256 = "da39a3ee",
                sizeBytes = 8388608,
                kind = BackupKind.BOOT,
                createdAt = "2026-10-06T12:00:00Z",
            ),
        ),
    )

    @Test
    fun `module meta round trips through json`() {
        assertEquals(moduleMeta, ModuleBackupMeta.fromJson(moduleMeta.toJson()).getOrThrow())
    }

    @Test
    fun `boot meta round trips through json`() {
        assertEquals(bootMeta, BootBackupMeta.fromJson(bootMeta.toJson()).getOrThrow())
    }

    @Test
    fun `manifest round trips through json`() {
        assertEquals(manifest, BackupManifest.fromJson(manifest.toJson()).getOrThrow())
    }

    @Test
    fun `module meta ignores unknown fields`() {
        val json = moduleMeta.toJson()
            .replaceFirst("{", "{\n  \"futureField\": 7,")
        assertEquals(moduleMeta, ModuleBackupMeta.fromJson(json).getOrThrow())
    }

    @Test
    fun `module meta rejects a wrong schema`() {
        val json = moduleMeta.toJson().replace(MODULE_BACKUP_SCHEMA, "someone.else.module")
        assertEquals(BackupFormatError.UNSUPPORTED_SCHEMA, ModuleBackupMeta.fromJson(json).failureError())
    }

    @Test
    fun `module meta rejects an unknown source`() {
        val json = moduleMeta.toJson().replaceFirst("\"source\": \"archived_installer\"", "\"source\": \"guess\"")
        assertEquals(BackupFormatError.INVALID_FIELD, ModuleBackupMeta.fromJson(json).failureError())
    }

    @Test
    fun `malformed json yields a typed failure`() {
        assertEquals(BackupFormatError.MALFORMED, BackupManifest.fromJson("{").failureError())
    }

    @Test
    fun `manifest rejects an unknown kind`() {
        val json = manifest.toJson().replaceFirst("\"kind\": \"module\"", "\"kind\": \"kernel\"")
        assertEquals(BackupFormatError.INVALID_FIELD, BackupManifest.fromJson(json).failureError())
    }

    private fun Result<*>.failureError(): BackupFormatError? =
        (exceptionOrNull() as? BackupFormatException)?.error
}
