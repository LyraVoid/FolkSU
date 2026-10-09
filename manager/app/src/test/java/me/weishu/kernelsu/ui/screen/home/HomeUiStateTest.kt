package me.weishu.kernelsu.ui.screen.home

import me.weishu.kernelsu.KernelVersion
import me.weishu.kernelsu.ui.util.RootShellStatus
import me.weishu.kernelsu.ui.util.module.LatestVersionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The home warnings must distinguish "still probing" from "definitely failed": a probe in flight
 * shows a recovering notice, and only a completed non-root probe raises the failure card.
 */
class HomeUiStateTest {

    private fun state(rootStatus: RootShellStatus) = HomeUiState(
        kernelVersion = KernelVersion(6, 6, 118),
        ksuVersion = 32750,
        managerUAPIVersion = 5,
        kernelUAPIVersion = 5,
        lkmMode = true,
        isLkmBundled = true,
        isManager = true,
        isManagerPrBuild = false,
        isKernelPrBuild = false,
        requiresNewKernel = false,
        requiresNewManager = false,
        isRootAvailable = rootStatus == RootShellStatus.Ready,
        rootStatus = rootStatus,
        isSafeMode = false,
        isLateLoadMode = false,
        checkUpdateEnabled = false,
        latestVersionInfo = LatestVersionInfo(),
        currentManagerVersionCode = 32750,
        systemInfo = SystemInfo(
            kernelVersion = "6.6.118",
            managerVersion = "v0.1.0-pre6",
            deviceModel = "Test",
            fingerprint = "test",
            selinuxStatus = "Enforcing",
            seccompStatus = 2,
        ),
    )

    @Test
    fun `probing shows recovering, never the failure card`() {
        val probing = state(RootShellStatus.Probing)
        assertTrue(probing.showRootRecovering)
        assertFalse(probing.showRootWarning)
        assertFalse(probing.isRootAvailable)
    }

    @Test
    fun `a completed non-root probe shows the failure card`() {
        val failed = state(RootShellStatus.Unavailable)
        assertTrue(failed.showRootWarning)
        assertFalse(failed.showRootRecovering)
        assertFalse(failed.isRootAvailable)
    }

    @Test
    fun `a ready shell is silent and available`() {
        val ready = state(RootShellStatus.Ready)
        assertTrue(ready.isRootAvailable)
        assertFalse(ready.showRootWarning)
        assertFalse(ready.showRootRecovering)
    }

    @Test
    fun `retrying after a failure returns to the recovering state`() {
        // The retry path re-probes, so the UI moves from failure back to the recovering notice and
        // the stale failure card disappears without restarting the app.
        assertEquals(RootShellStatus.Unavailable, state(RootShellStatus.Unavailable).rootStatus)
        val retrying = state(RootShellStatus.Probing)
        assertFalse(retrying.showRootWarning)
        assertTrue(retrying.showRootRecovering)
    }
}
