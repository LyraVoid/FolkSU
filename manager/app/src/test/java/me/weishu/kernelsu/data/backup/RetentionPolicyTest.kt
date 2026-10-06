package me.weishu.kernelsu.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionPolicyTest {

    @Test
    fun `empty input prunes nothing`() {
        assertTrue(RetentionPolicy.prune(emptyList(), 5).isEmpty())
    }

    @Test
    fun `keeping more than present prunes nothing`() {
        val entries = entriesAt("m", 1000, 2000)
        assertTrue(RetentionPolicy.prune(entries, 5).isEmpty())
    }

    @Test
    fun `exactly at the limit prunes nothing`() {
        val entries = entriesAt("m", 1000, 2000, 3000)
        assertTrue(RetentionPolicy.prune(entries, 3).isEmpty())
    }

    @Test
    fun `one over the limit prunes the oldest`() {
        val entries = entriesAt("m", 1000, 2000, 3000)
        assertEquals(listOf(1000L), RetentionPolicy.prune(entries, 2).map { it.createdAtEpochMs })
    }

    @Test
    fun `a non positive limit still keeps the newest entry`() {
        val entries = entriesAt("m", 1000, 2000, 3000)
        val pruned = RetentionPolicy.prune(entries, 0)
        assertEquals(listOf(1000L, 2000L), pruned.map { it.createdAtEpochMs }.sorted())
    }

    @Test
    fun `each module is grouped independently`() {
        val entries = entriesAt("first", 1000, 2000) + entriesAt("second", 500)
        val pruned = RetentionPolicy.prune(entries, 1)
        assertEquals(listOf(1000L), pruned.map { it.createdAtEpochMs })
        assertEquals("first", pruned.single().moduleId)
    }

    @Test
    fun `equal timestamps are broken deterministically`() {
        val entries = listOf(
            entry("m", 1000, "a"),
            entry("m", 1000, "b"),
        )
        assertEquals(listOf("a"), RetentionPolicy.prune(entries, 1).map { it.relativePath })
    }

    @Test
    fun `older than cutoff deletes entries before the cutoff`() {
        val entries = entriesAt("m", 1000, 2000, 3000)
        val pruned = RetentionPolicy.pruneOlderThan(entries, 2500)
        assertEquals(listOf(1000L, 2000L), pruned.map { it.createdAtEpochMs })
    }

    @Test
    fun `older than cutoff never deletes the newest entry by default`() {
        val entries = entriesAt("m", 1000, 2000, 3000)
        val pruned = RetentionPolicy.pruneOlderThan(entries, 3500)
        assertEquals(listOf(1000L, 2000L), pruned.map { it.createdAtEpochMs })
    }

    @Test
    fun `older than cutoff deletes the newest when protection is disabled`() {
        val entries = entriesAt("m", 1000, 2000, 3000)
        val pruned = RetentionPolicy.pruneOlderThan(entries, 3500, keepLatestPerModule = false)
        assertEquals(listOf(1000L, 2000L, 3000L), pruned.map { it.createdAtEpochMs })
    }

    @Test
    fun `older than cutoff on empty input prunes nothing`() {
        assertTrue(RetentionPolicy.pruneOlderThan(emptyList(), 1000).isEmpty())
    }

    private fun entriesAt(moduleId: String, vararg times: Long): List<BackupEntry> =
        times.map { entry(moduleId, it, "$moduleId-$it") }

    private fun entry(moduleId: String, createdAt: Long, path: String): BackupEntry = BackupEntry(
        id = path,
        kind = BackupKind.MODULE,
        moduleId = moduleId,
        displayName = moduleId,
        createdAtEpochMs = createdAt,
        relativePath = path,
    )
}
