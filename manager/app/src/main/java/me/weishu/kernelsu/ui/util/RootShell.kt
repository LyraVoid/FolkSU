package me.weishu.kernelsu.ui.util

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.ksuApp
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * State of one root-shell slot. [Probing] means a build is running or scheduled; it is emphatically
 * not a failure, so the UI shows a placeholder instead of a warning while it is set.
 */
enum class RootShellStatus { Probing, Ready, Unavailable }

private const val ROOT_SHELL_TAG = "RootShell"

private fun ksuDaemonPath() = ksuApp.applicationInfo.nativeLibraryDir + File.separator + "libksud.so"

/**
 * Owns the two root shells (default and global-mount) and rebuilds them on demand.
 *
 * The shells are never cached as a permanent result: a non-root fallback (plain `sh`) is treated as
 * a failed probe and is retried, so authorizing the app or fixing the kernel can restore root
 * without restarting the process. Concurrent probes are single-flight per slot, and a replaced
 * shell is drained rather than killed so tasks already queued on it can finish.
 */
object RootShell {
    private const val RETRY_COOLDOWN_MS = 4_000L
    private const val MAX_COOLDOWN_SHIFT = 4
    private const val DRAIN_TIMEOUT_SECONDS = 5L
    private const val MAX_CONSECUTIVE_FAILURES = 6

    private class Slot(val globalMnt: Boolean) {
        val mutex = Any()

        @Volatile
        var shell: Shell? = null

        @Volatile
        var status = RootShellStatus.Probing

        @Volatile
        var consecutiveFailures = 0

        @Volatile
        var lastAttemptElapsed = 0L

        /** The build currently in progress, shared by every concurrent caller. */
        @Volatile
        var inFlight: Deferred<Shell?>? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val defaultSlot = Slot(false)
    private val globalSlot = Slot(true)

    private val _status = MutableStateFlow(RootShellStatus.Probing)
    val status: StateFlow<RootShellStatus> = _status.asStateFlow()

    private val _generation = MutableStateFlow(0L)
    val generation: StateFlow<Long> = _generation.asStateFlow()

    private fun slot(globalMnt: Boolean) = if (globalMnt) globalSlot else defaultSlot

    private fun ready(s: Slot): Shell? = s.shell?.takeIf { it.isAlive && it.isRoot }

    fun isRootAvailable(): Boolean = ready(defaultSlot) != null

    fun currentStatus(globalMnt: Boolean = false): RootShellStatus {
        val s = slot(globalMnt)
        return if (ready(s) != null) RootShellStatus.Ready else s.status
    }

    fun peek(globalMnt: Boolean = false): Shell? = ready(slot(globalMnt))

    /**
     * Blocking accessor for legacy synchronous call sites. It never builds on the main thread:
     * there it only kicks a background probe and returns whatever shell already exists. Code that
     * needs a real root shell must use [awaitRoot].
     */
    fun obtain(globalMnt: Boolean = false): Shell {
        val s = slot(globalMnt)
        ready(s)?.let { return it }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            probe(globalMnt)
        } else {
            runBlocking { ensure(globalMnt) }
        }
        return ready(s) ?: s.shell ?: error("Root shell is not available")
    }

    /**
     * Waits for a ready root shell, rebuilding once if needed. Returns [RootShellStatus.Unavailable]
     * when no root shell could be obtained, and never reports a plain shell as ready.
     */
    suspend fun awaitReady(globalMnt: Boolean = false): RootShellStatus {
        val s = slot(globalMnt)
        if (ready(s) != null) return RootShellStatus.Ready
        ensure(globalMnt)
        return if (ready(s) != null) RootShellStatus.Ready else RootShellStatus.Unavailable
    }

    /**
     * Returns a ready root shell or throws. Privileged operations must go through this so a failed
     * probe can never silently run a command on a non-root fallback shell.
     */
    suspend fun awaitRoot(globalMnt: Boolean = false): Shell {
        if (awaitReady(globalMnt) != RootShellStatus.Ready) {
            throw IllegalStateException("Root shell is not available")
        }
        return ready(slot(globalMnt)) ?: throw IllegalStateException("Root shell is not available")
    }

    fun probe(globalMnt: Boolean = false) {
        scope.launch { ensure(globalMnt) }
    }

    fun initialize() {
        probe(false)
        probe(true)
    }

    private fun cooldownFor(s: Slot): Long =
        if (s.consecutiveFailures == 0) RETRY_COOLDOWN_MS
        else RETRY_COOLDOWN_MS shl (s.consecutiveFailures - 1).coerceAtMost(MAX_COOLDOWN_SHIFT)

    /**
     * Single-flight rebuild. The first caller starts the build; every concurrent caller awaits the
     * same [Deferred], so a probe burst produces exactly one shell. A failed probe schedules one
     * bounded backoff retry, which stops after [MAX_CONSECUTIVE_FAILURES].
     */
    private suspend fun ensure(globalMnt: Boolean): Shell? {
        val s = slot(globalMnt)
        ready(s)?.let { return it }

        val deferred: Deferred<Shell?> = synchronized(s.mutex) {
            ready(s)?.let { return it }

            val now = SystemClock.elapsedRealtime()
            // Still inside the backoff window: serve the stale shell (if any) instead of hammering.
            if (s.shell != null && now - s.lastAttemptElapsed < cooldownFor(s)) {
                return s.shell
            }

            val existing = s.inFlight
            if (existing != null && existing.isActive) {
                return@synchronized existing
            }

            s.status = RootShellStatus.Probing
            if (!globalMnt) _status.value = RootShellStatus.Probing
            s.lastAttemptElapsed = now

            val build = scope.async(start = CoroutineStart.LAZY) {
                val built = try {
                    withContext(Dispatchers.IO) { buildShell(globalMnt) }
                } catch (t: Throwable) {
                    Log.w(ROOT_SHELL_TAG, "root shell build failed", t)
                    null
                }
                publish(s, globalMnt, built)
                if (ready(s) == null) scheduleRetry(globalMnt, s)
                built
            }
            build.invokeOnCompletion {
                synchronized(s.mutex) { if (s.inFlight === build) s.inFlight = null }
            }
            s.inFlight = build
            build
        }

        return deferred.await()
    }

    /** One bounded backoff retry, scheduled off the caller so a failure cannot spin. */
    private fun scheduleRetry(globalMnt: Boolean, s: Slot) {
        if (s.consecutiveFailures > MAX_CONSECUTIVE_FAILURES) return
        val delayMs = cooldownFor(s)
        scope.launch {
            delay(delayMs)
            if (ready(s) == null) ensure(globalMnt)
        }
    }

    private fun publish(s: Slot, globalMnt: Boolean, shell: Shell?) {
        var replaced: Shell? = null
        synchronized(s.mutex) {
            if (shell != null && shell.isAlive && shell.isRoot) {
                replaced = s.shell
                s.shell = shell
                s.status = RootShellStatus.Ready
                s.consecutiveFailures = 0
            } else {
                // Never store a non-root fallback as the result: record the failure and retry.
                s.status = RootShellStatus.Unavailable
                s.consecutiveFailures =
                    (s.consecutiveFailures + 1).coerceAtMost(MAX_CONSECUTIVE_FAILURES + 1)
            }
            if (!globalMnt) _status.value = s.status
            _generation.value += 1
        }
        val previous = replaced
        if (shell != null && previous != null && previous !== shell) {
            scope.launch {
                try {
                    // Drain queued tasks first; on timeout the old shell is left alive on purpose
                    // so in-flight work is not aborted.
                    previous.waitAndClose(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                } catch (t: Throwable) {
                    Log.w(ROOT_SHELL_TAG, "draining replaced root shell failed", t)
                }
            }
        }
    }

    /**
     * Tries the embedded ksud daemon first, then a PATH `su`. The final `sh` fallback is returned
     * only so the caller can observe a non-root shell; [publish] treats it as a failure.
     */
    private fun buildShell(globalMnt: Boolean): Shell {
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        val builder = Shell.Builder.create()
        return try {
            if (globalMnt) {
                builder.build(ksuDaemonPath(), "debug", "su", "-g")
            } else {
                builder.build(ksuDaemonPath(), "debug", "su")
            }
        } catch (t: Throwable) {
            Log.w(ROOT_SHELL_TAG, "ksud root shell failed, trying su", t)
            try {
                if (globalMnt) builder.build("su", "-mm") else builder.build("su")
            } catch (t2: Throwable) {
                Log.e(ROOT_SHELL_TAG, "su failed, falling back to sh", t2)
                builder.build("sh")
            }
        }
    }
}
