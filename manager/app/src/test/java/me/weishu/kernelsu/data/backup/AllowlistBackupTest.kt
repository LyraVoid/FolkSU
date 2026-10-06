package me.weishu.kernelsu.data.backup

import me.weishu.kernelsu.Natives
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowlistBackupTest {

    private val profile = Natives.Profile(
        name = "com.example.app",
        currentUid = 10123,
        allowSu = true,
        rootUseDefault = false,
        rootTemplate = "custom-template",
        uid = 0,
        gid = 0,
        groups = listOf(3003, 1000),
        capabilities = listOf(21, 0),
        context = "u:r:ksu:s0",
        namespace = 1,
        nonRootUseDefault = false,
        umountModules = false,
        rules = "allow app_rule",
        flags = 1L,
    )

    private val document = AllowlistDocument(
        exportedAt = "2026-10-06T12:00:00Z",
        appVersion = "1.0.0 (10000)",
        kernelUapiVersion = 32669,
        entries = listOf(profile.toDto("com.example.app", "com.example.app")),
    )

    @Test
    fun `document round trips through json`() {
        val parsed = AllowlistDocument.fromJson(document.toJson()).getOrThrow()
        assertEquals(document, parsed)
    }

    @Test
    fun `profile round trips through dto`() {
        val entry = document.entries.single()
        assertEquals(profile, entry.toProfile())
    }

    @Test
    fun `unknown fields are ignored`() {
        val json = document.toJson()
            .replaceFirst("{", "{\n  \"futureField\": {\"nested\": true},")
            .replaceFirst(
                "\"key\": \"com.example.app\"",
                "\"key\": \"com.example.app\", \"alsoUnknown\": 42",
            )
        val parsed = AllowlistDocument.fromJson(json).getOrThrow()
        assertEquals(profile.toDto("com.example.app", "com.example.app"), parsed.entries.single())
    }

    @Test
    fun `malformed json yields a typed malformed failure`() {
        val result = AllowlistDocument.fromJson("{ not json")
        assertEquals(BackupFormatError.MALFORMED, result.failureError())
    }

    @Test
    fun `unsupported schema yields a typed failure`() {
        val json = document.toJson().replace(ALLOWLIST_SCHEMA, "someone.else.allowlist")
        assertEquals(BackupFormatError.UNSUPPORTED_SCHEMA, AllowlistDocument.fromJson(json).failureError())
    }

    @Test
    fun `unsupported version yields a typed failure`() {
        val json = document.toJson().replaceFirst("\"version\": 1", "\"version\": 2")
        assertEquals(BackupFormatError.UNSUPPORTED_VERSION, AllowlistDocument.fromJson(json).failureError())
    }

    @Test
    fun `empty entries yield a typed empty failure`() {
        val json = document.toJson().replace(Regex("\"entries\": \\[[\\s\\S]*]"), "\"entries\": []")
        assertEquals(BackupFormatError.EMPTY, AllowlistDocument.fromJson(json).failureError())
    }

    @Test
    fun `missing uid yields a typed invalid field failure`() {
        val json = document.toJson().replaceFirst("\"uid\": 10123,", "")
        assertEquals(BackupFormatError.INVALID_FIELD, AllowlistDocument.fromJson(json).failureError())
    }

    @Test
    fun `out of range namespace yields a typed invalid field failure`() {
        val json = document.toJson().replaceFirst("\"namespace\": 1", "\"namespace\": 7")
        assertEquals(BackupFormatError.INVALID_FIELD, AllowlistDocument.fromJson(json).failureError())
    }

    @Test
    fun `blank key yields a typed invalid field failure`() {
        val json = document.toJson().replaceFirst("\"key\": \"com.example.app\"", "\"key\": \"\"")
        assertEquals(BackupFormatError.INVALID_FIELD, AllowlistDocument.fromJson(json).failureError())
    }

    @Test
    fun `validate rejects an empty document`() {
        val empty = document.copy(entries = emptyList())
        assertEquals(BackupFormatError.EMPTY, empty.validate().failureError())
    }

    @Test
    fun `validate accepts a well formed document`() {
        assertTrue(document.validate().isSuccess)
    }

    private fun Result<*>.failureError(): BackupFormatError? =
        (exceptionOrNull() as? BackupFormatException)?.error
}
