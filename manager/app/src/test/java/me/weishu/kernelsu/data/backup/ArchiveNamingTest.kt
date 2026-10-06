package me.weishu.kernelsu.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveNamingTest {

    private val epochMs = 1_700_000_000_000L

    @Test
    fun `timestamp formats and parses in utc`() {
        assertEquals("20231114_221320", backupTimestamp(epochMs))
        assertEquals(epochMs, parseBackupTimestamp("20231114_221320"))
        assertNull(parseBackupTimestamp("not-a-timestamp"))
    }

    @Test
    fun `iso timestamp is a utc instant`() {
        assertEquals("2023-11-14T22:13:20Z", backupIsoTimestamp(epochMs))
    }

    @Test
    fun `module archive name follows the contract`() {
        assertEquals(
            "example.module-1.2.3-123-20231114_221320.zip",
            moduleArchiveName("example.module", "1.2.3", 123, epochMs),
        )
    }

    @Test
    fun `module meta name replaces the zip suffix`() {
        assertEquals("example.module-1.2.3-123-20231114_221320.meta.json", moduleMetaFileName("example.module-1.2.3-123-20231114_221320.zip"))
    }

    @Test
    fun `allowlist and boot names follow the contract`() {
        assertEquals("allowlist-20231114_221320.json", allowlistFileName(epochMs))
        assertEquals("init_boot-stock-abc123.img", bootBackupName("init_boot", "abc123"))
        assertEquals("init_boot-stock-abc123.meta.json", bootMetaFileName("init_boot-stock-abc123.img"))
    }

    @Test
    fun `sanitize removes separators and traversal`() {
        val cleaned = sanitizeSegment("../../etc/passwd")
        assertFalse(cleaned.contains('/'))
        assertFalse(cleaned.contains(".."))
        assertEquals("_", sanitizeSegment(".."))
        assertEquals("_", sanitizeSegment("   "))
        assertEquals("a_b", sanitizeSegment("a/b"))
    }

    @Test
    fun `sanitize keeps legal characters`() {
        assertEquals("example.module", sanitizeSegment("example.module"))
        assertEquals("1.2.3-beta_1", sanitizeSegment("1.2.3-beta_1"))
    }

    @Test
    fun `remote path is relative and free of traversal`() {
        assertEquals(
            "modules/example.module/example.module-1.2.3-123-20231114_221320.zip",
            remotePathFor(BackupKind.MODULE, "example.module", "example.module-1.2.3-123-20231114_221320.zip"),
        )
        assertEquals("allowlist/allowlist-20231114_221320.json", remotePathFor(BackupKind.ALLOWLIST, null, "allowlist-20231114_221320.json"))
        assertEquals("boot/init_boot-stock-abc.img", remotePathFor(BackupKind.BOOT, null, "init_boot-stock-abc.img"))
    }

    @Test
    fun `remote path neutralises a malicious module id`() {
        val path = remotePathFor(BackupKind.MODULE, "../../evil", "../../x.zip")
        assertTrue(path.startsWith("modules/"))
        assertFalse(path.contains(".."))
        assertEquals(2, path.count { it == '/' })
    }

    @Test
    fun `module relative path is grouped by module`() {
        assertEquals("modules/example.module/a.zip", moduleRelativePath("example.module", "a.zip"))
    }
}
