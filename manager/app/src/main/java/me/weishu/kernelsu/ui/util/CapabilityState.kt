package me.weishu.kernelsu.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.weishu.kernelsu.DebugFlags
import me.weishu.kernelsu.Natives

/**
 * One immutable snapshot of what the app can currently do. Keeping the four concerns separate
 * stops a transient root-probe failure from mutating the whole layout, and stops a stale success
 * from authorizing privileged work.
 */
@Immutable
data class CapabilityState(
    /** The kernel driver is present and answering. */
    val kernelAvailable: Boolean = false,
    /** This app currently holds a manager identity (fixed or dynamic). */
    val isManager: Boolean = false,
    /** Kernel and manager UAPI agree. */
    val uapiCompatible: Boolean = false,
    /** Root-shell probe state. */
    val rootStatus: RootShellStatus = RootShellStatus.Probing,
) {
    /**
     * Structural ability to render the full shell. Deliberately independent of the transient
     * root probe so a recoverable root hiccup cannot swap the layout or drop navigation.
     */
    val fullLayout: Boolean get() = isManager && uapiCompatible

    /** All privileged operations are currently permitted. */
    val privileged: Boolean get() = fullLayout && rootStatus == RootShellStatus.Ready

    /** A root probe is in flight after the app already qualified structurally. */
    val recovering: Boolean get() = fullLayout && rootStatus == RootShellStatus.Probing

    /** A definite, user-visible root failure (not just a probe in flight). */
    val rootFailed: Boolean get() = fullLayout && rootStatus == RootShellStatus.Unavailable
}

val LocalCapabilityState = staticCompositionLocalOf { CapabilityState() }

/**
 * Full-shell layout decision for composables. Kept as one shared snapshot so the pager, the five
 * home layouts and every navigation bar agree in the same frame. The debug override is preserved
 * so a debug build still exercises the full layout.
 */
@Composable
fun useFullFeaturedLayout(): Boolean {
    if (DebugFlags.forceFullFeatured) return true
    return LocalCapabilityState.current.fullLayout
}

/** Compose-visible privileged gate: structural full layout *and* a ready root shell. */
@Composable
fun usePrivilegedAvailable(): Boolean {
    if (DebugFlags.forceFullFeatured) return true
    return LocalCapabilityState.current.privileged
}

/**
 * Collects the mutable identity and root signals into one snapshot, and owns the refresh
 * triggers. Identity comes from the JNI bridge (which must not cache a revocable MANAGER
 * answer); root comes from [RootShell].
 */
object CapabilityRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val identity = MutableStateFlow(readIdentity())

    private data class Identity(
        val kernelAvailable: Boolean,
        val isManager: Boolean,
        val uapiCompatible: Boolean,
    )

    val state: StateFlow<CapabilityState> =
        combine(identity, RootShell.status, RootShell.generation) { id, root, _ ->
            CapabilityState(
                kernelAvailable = id.kernelAvailable,
                isManager = id.isManager,
                uapiCompatible = id.uapiCompatible,
                rootStatus = root,
            )
        }.stateIn(
            scope,
            SharingStarted.Eagerly,
            CapabilityState(rootStatus = RootShell.currentStatus()),
        )

    fun current(): CapabilityState = state.value

    /** Re-read identity and probe both shells. Safe to call from the main thread. */
    fun refresh() {
        scope.launch {
            identity.value = readIdentity()
            RootShell.awaitReady(false)
            RootShell.awaitReady(true)
        }
    }

    /** Re-read identity only, without forcing a shell build (used on identity changes). */
    fun refreshIdentity() {
        scope.launch { identity.value = readIdentity() }
    }

    private fun readIdentity(): Identity {
        val manager = runCatching { Natives.isManager }.getOrDefault(false)
        val kernelUapi = runCatching { Natives.kernelUAPIVersion }.getOrDefault(0)
        val managerUapi = runCatching { Natives.managerUAPIVersion }.getOrDefault(0)
        val driverPresent = runCatching { Natives.version > 0 }.getOrDefault(false)
        return Identity(
            kernelAvailable = driverPresent || kernelUapi > 0,
            isManager = manager,
            uapiCompatible = manager && kernelUapi > 0 && kernelUapi == managerUapi,
        )
    }
}
