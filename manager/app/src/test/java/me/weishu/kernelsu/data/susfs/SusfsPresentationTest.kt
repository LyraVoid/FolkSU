package me.weishu.kernelsu.data.susfs

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SusfsPresentationTest {
    @Test fun `receipt separates acknowledgements errors and reboot requests`() {
        val report = SusfsApplyReport.parse("""{"status":"failed","applied":["path:/a"],"failures":{"uname":"SUSFS returned error -22"},"pending_reboot":["kstat:/b"]}""")
        assertEquals(listOf("path:/a"), report.applied)
        assertEquals("SUSFS returned error -22", report.failures["uname"])
        assertEquals(listOf("kstat:/b"), report.pendingReboot)
        assertEquals("failed", report.status)
    }
    @Test fun `paths and uname count UTF8 bytes not displayed characters`() {
        assertTrue(SusfsInputs.validPath("/data/local/tmp/test"))
        listOf("/", "relative", "/a/../b", "/a//b", "/a b", "/a\n").forEach { assertFalse(SusfsInputs.validPath(it)) }
        assertFalse(SusfsInputs.validPath("/" + "界".repeat(85)))
        assertTrue(SusfsInputs.validText("default", 65))
        assertFalse(SusfsInputs.validText("", 65))
        assertFalse(SusfsInputs.validText("界".repeat(22), 65))
    }
    @Test fun `editing fresh config preserves unrelated rules despite reordering`() {
        val original = """{"kind":"path","path":"/a","looping":false}"""
        val config = JSONObject("""{"rules":[{"kind":"map","path":"/b"},$original]}""")
        SusfsInputs.replaceRule(config, original, JSONObject("""{"kind":"path","path":"/a","looping":true}"""))
        assertEquals("/b", config.getJSONArray("rules").getJSONObject(0).getString("path"))
        assertTrue(config.getJSONArray("rules").getJSONObject(1).getBoolean("looping"))
    }
    @Test fun `stale edits cannot overwrite a concurrent change`() {
        val original = """{"kind":"path","path":"/a","looping":false}"""
        val config = JSONObject("""{"rules":[{"kind":"path","path":"/a","looping":true}]}""")
        assertThrows(IllegalArgumentException::class.java) { SusfsInputs.replaceRule(config, original, null) }
        assertEquals(1, config.getJSONArray("rules").length())
    }
    @Test fun `import preview never implies complete daemon validation`() {
        val json = """{"schema":1,"automatic":true,"settings":{},"rules":[],"lkm":{"hidden_modules":[],"mount_prefixes":[]},"unknown":1}"""
        assertTrue(SusfsInputs.preview(json).getBoolean("automatic"))
        assertThrows(IllegalArgumentException::class.java) { SusfsInputs.preview(json.replace("\"schema\":1", "\"schema\":2")) }
        assertThrows(IllegalArgumentException::class.java) { SusfsInputs.preview(" ".repeat(128 * 1024 + 1)) }
    }
}
