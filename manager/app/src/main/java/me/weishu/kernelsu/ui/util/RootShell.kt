package me.weishu.kernelsu.ui.util

import android.os.SystemClock
import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.BuildConfig
import me.weishu.kernelsu.ksuApp

private const val ROOT_SHELL_TAG = "RootShell"

/** Recoverable root-access state of one shell slot. */
enum class RootShellStatus { Probing, Ready, Unavailable }

private fun ksuDaemonPath(): String =
    ksuApp.applicationInfo.nativeLibraryDir + File.separator + "libksud.so"

/**
 * Thread-safe owner of the two privileged shells (normal and global-mount).
 *
 * The previous implementation kept the `Shell` values in object-init `val`s and cached a
 * non-root `sh` fallback forever, so a single failed probe made the app report "no root" until
 * the process was restarted. Here a slot rebuilds itself when its shell is missing, dead or
 * non-root, and a non-root result is never treated as final: callers may probe again after a
 * backoff cooldown once the underlying grant is restored.
 *
 * [status] is a lock-free snapshot safe to read from the Compose main thread; only
 * [obtain]/[awaitReady] build a shell and they run off the main thread.
 */
object RootShell {
    private const val RETRY_COOLDOWN_MS = 4_000L
    private const val DRAIN_TIMEOUT_SECONDS = 5L
    private const val MAX_CONSECUTIVE_FAILURES = 6

    private class Slot(val globalMnt: Boolean) {
        @Volatile
        var shell: Shell? = null

        @Volatile
        var status: RootShellStatus = RootShellStatus.Probing

        @Volatile
        var consecutiveFailures: Int = 0

        @Volatile
        var lastAttemptElapsed: Long = 0L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val defaultSlot = Slot(false)
    private val globalSlot = Slot(true)
    private val buildLock = Any()

    private val _status = MutableStateFlow(RootShellStatus.Probing)

    /** Root availability of the default shell, for the UI capability snapshot. */
    val status: StateFlow<RootShellStatus> = _status.asStateFlow()

    private val _generation = MutableStateFlow(0L)

    /** Bumped on every (re)build so observers can invalidate identity/shell caches. */
    val generation: StateFlow<Long> = _generation.asStateFlow()

    private fun slot(globalMnt: Boolean): Slot = if (globalMnt) globalSlot else defaultSlot

    /** Non-blocking snapshot: last known root status of the default shell. */
    fun isRootAvailable(): Boolean = defaultSlot.status == RootShellStatus.Ready

    fun currentStatus(globalMnt: Boolean = false): RootShellStatus = slot(globalMnt).status

    /** The cached shell, or null when no build has completed yet. Never blocks. */
    fun peek(globalMnt: Boolean = false): Shell? = slot(globalMnt).shell

    /**
     * Returns a shell to run privileged commands with. Rebuilds when the cached one is missing,
     * dead or non-root, then caches the result. A non-root build is only served until the next
     * retry cooldown so a transient failure can recover without restarting the app.
     */
    fun obtain(globalMnt: Boolean = false): Shell {
        val s = slot(globalMnt)
        val cached = s.shell
        if (cached != null && cached.isAlive && cached.isRoot) return cached

        val now = SystemClock.elapsedRealtime()
        synchronized(buildLock) {
            val current = s.shell
            if (current != null && current.isAlive && current.isRoot) return current
            if (s.lastAttemptElapsed != 0L && now - s.lastAttemptElapsed < cooldownFor(s)) {
                // Within the backoff window: serve whatever we have (possibly a stale shell) so
                // callers fail cleanly instead of blocking again; a later call retries.
                current?.let { return it }
            }
            s.lastAttemptElapsed = now
        }
        val built = build(globalMnt)
        publish(globalMnt, built)
        return s.shell ?: built
    }

    /**
     * Suspending refresh used by the capability repository: resolves once a shell has been
     * (re)built. Never throws; a non-root result is reported as [RootShellStatus.Unavailable].
     */
    suspend fun awaitReady(globalMnt: Boolean = false): RootShellStatus {
        val s = slot(globalMnt)
        if (s.shell?.let { it.isAlive && it.isRoot } == true) return RootShellStatus.Ready
        val built = withContext(Dispatchers.IO) { obtain(globalMnt) }
        return if (built.isAlive && built.isRoot) RootShellStatus.Ready else RootShellStatus.Unavailable
    }

    /** Fire-and-forget rebuild; safe to call from the main thread. */
    fun probe(globalMnt: Boolean = false) {
        scope.launch { obtain(globalMnt) }
    }

    /** Process-wide warm-up so the first snapshot is produced away from the main thread. */
    fun initialize() {
        probe(false)
        probe(true)
    }

    private fun cooldownFor(s: Slot): Long {
        val failures = s.consecutiveFailures
        if (failures == 0) return RETRY_COOLDOWN_MS
        return RETRY_COOLDOWN_MS shl (failures - 1).coerceAtMost(4)
    }

    private fun build(globalMnt: Boolean): Shell {
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        val builder = Shell.Builder.create()
        return try {
            if (globalMnt) {
                builder.build(ksuDaemonPath(), "debug", "su", "-g")
            } else {
                builder.build(ksuDaemonPath(), "debug", "su")
            }
        } catch (e: Throwable) {
            Log.w(ROOT_SHELL_TAG, "ksu shell failed (globalMnt=$globalMnt)", e)
            try {
                if (globalMnt) builder.build("su", "-mm") else builder.build("su")
            } catch (e2: Throwable) {
                Log.e(ROOT_SHELL_TAG, "su shell failed (globalMnt=$globalMnt)", e2)
                builder.build("sh")
            }
        }
    }

    private fun publish(globalMnt: Boolean, shell: Shell) {
        val s = slot(globalMnt)
        val previous: Shell?
        synchronized(buildLock) {
            previous = s.shell
            s.shell = shell
            if (shell.isAlive && shell.isRoot) {
                s.status = RootShellStatus.Ready
                s.consecutiveFailures = 0
            } else {
                s.status = RootShellStatus.Unavailable
                s.consecutiveFailures =
                    (s.consecutiveFailures + 1).coerceAtMost(MAX_CONSECUTIVE_FAILURES + 1)
            }
        }
        if (!globalMnt) _status.value = s.status
        _generation.value += 1
        // Retire the replaced shell without killing tasks still running on it. waitAndClose
        // drains the current/pending jobs first, then closes.
        if (previous != null && previous !== shell) {
            scope.launch {
                runCatching { previous.waitAndClose(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
                    .onFailure { Log.w(ROOT_SHELL_TAG, "failed to retire previous shell", it) }
            }
        }
    }
}
