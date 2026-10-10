package me.weishu.kernelsu.data.susfs

import org.junit.Assert.*
import org.junit.Test

class SusfsStatusTest {
    @Test fun `version alone and unknown protocol never enable mutations`() {
        assertFalse(SusfsStatus(version = "v2.3.0").detected)
        val unknown = SusfsStatus(protocol = "unknown_protocol", version = "v9", features = listOf("CONFIG_KSU_SUSFS_SUS_PATH"))
        assertTrue(unknown.detected)
        assertFalse(unknown.supports("SUS_PATH"))
    }
    @Test fun `GKI is not an LKM extension marker`() {
        val builtin = SusfsStatus(protocol = "compatible", implementation = "builtin", variant = "GKI", procNodes = listOf("susfs_kstat"))
        assertFalse(builtin.proc("susfs_kstat"))
    }
    @Test fun `LKM without exposed proc controls disables extensions`() {
        val lkm = SusfsStatus(protocol = "compatible", implementation = "lkm", features = listOf("CONFIG_KSU_SUSFS_SUS_KSTAT"))
        assertTrue(lkm.supports("SUS_KSTAT"))
        assertFalse(lkm.proc("susfs_kstat"))
        assertTrue(lkm.copy(procNodes = listOf("susfs_kstat")).proc("susfs_kstat"))
    }
    @Test fun `root loss disables stale capabilities and recovery enables fresh ones`() {
        val ready = SusfsStatus(protocol = "compatible", features = listOf("CONFIG_KSU_SUSFS_SUS_PATH"))
        assertTrue(ready.supports("SUS_PATH"))
        assertFalse(ready.copy(protocol = "root_unavailable").supports("SUS_PATH"))
        assertFalse(ready.copy(error = "Invalid config").writable)
    }
    @Test fun `CLI status preserves saved versus applied`() {
        val status = SusfsStatus.parse("""{"detection":{"status":"compatible","version":"v2.3.0","variant":"GKI","implementation":"builtin","features":[],"proc_nodes":[]},"management":"saved","automatic":false,"config_error":null}""")
        assertEquals("saved", status.management)
        assertFalse(status.automatic)
    }
}
