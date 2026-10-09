package me.weishu.kernelsu.ui.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four capability concerns are deliberately independent. These tests pin the contract that a
 * transient root probe can never change the structural layout, and that only a ready shell can
 * authorize privileged work.
 */
class CapabilityStateTest {

    private fun state(
        manager: Boolean = true,
        uapi: Boolean = true,
        root: RootShellStatus,
    ) = CapabilityState(
        kernelAvailable = true,
        isManager = manager,
        uapiCompatible = uapi,
        rootStatus = root,
    )

    @Test
    fun `full layout is structural and ignores the root probe`() {
        // A manager with matching UAPI keeps the full shell while probing and even while the root
        // probe has failed; the layout must not flicker with a recoverable hiccup.
        assertTrue(state(root = RootShellStatus.Probing).fullLayout)
        assertTrue(state(root = RootShellStatus.Ready).fullLayout)
        assertTrue(state(root = RootShellStatus.Unavailable).fullLayout)
    }

    @Test
    fun `full layout needs both manager identity and matching uapi`() {
        assertFalse(state(manager = false, root = RootShellStatus.Ready).fullLayout)
        assertFalse(state(uapi = false, root = RootShellStatus.Ready).fullLayout)
    }

    @Test
    fun `privileged requires a ready root shell`() {
        assertTrue(state(root = RootShellStatus.Ready).privileged)
        assertFalse(state(root = RootShellStatus.Probing).privileged)
        assertFalse(state(root = RootShellStatus.Unavailable).privileged)
    }

    @Test
    fun `recovering and failed are distinct and exclusive`() {
        val probing = state(root = RootShellStatus.Probing)
        val failed = state(root = RootShellStatus.Unavailable)

        assertTrue(probing.recovering)
        assertFalse(probing.rootFailed)

        assertTrue(failed.rootFailed)
        assertFalse(failed.recovering)

        assertFalse(probing.privileged)
        assertFalse(failed.privileged)
    }

    @Test
    fun `recovering and failed stay silent when the app never qualified`() {
        // A non-manager device must not show a root-recovery or root-failure state: those are
        // reserved for a structurally capable device whose root is transient or broken.
        assertFalse(state(manager = false, root = RootShellStatus.Probing).recovering)
        assertFalse(state(manager = false, root = RootShellStatus.Unavailable).rootFailed)
        assertFalse(state(uapi = false, root = RootShellStatus.Unavailable).rootFailed)
    }

    @Test
    fun `default snapshot is a silent probe`() {
        val default = CapabilityState()
        assertFalse(default.fullLayout)
        assertFalse(default.privileged)
        assertFalse(default.recovering)
        assertFalse(default.rootFailed)
    }
}
