package me.weishu.kernelsu.media

import android.app.Application
import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Main-process, foreground-only player. All player operations run on the main thread. */
object MusicManager : DefaultLifecycleObserver {
    private var initialized = false
    private var player: MediaPlayer? = null
    private var prepared = false
    private var requested = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var progressJob: Job? = null
    private val playingState = MutableStateFlow(false)
    private val positionState = MutableStateFlow(0)
    private val durationState = MutableStateFlow(0)
    private val errorState = MutableStateFlow(false)
    val isPlaying = playingState.asStateFlow()
    val currentPosition = positionState.asStateFlow()
    val duration = durationState.asStateFlow()
    val hasError = errorState.asStateFlow()

    private val foreground: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    fun init(context: Context) {
        if (initialized || Application.getProcessName() != context.packageName) return
        initialized = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun play() {
        if (!MusicConfig.isMusicEnabled) return
        requested = true
        errorState.value = false
        if (player == null) prepare() else if (prepared && foreground) start()
    }

    private fun prepare() {
        if (!initialized) return
        val file = MusicConfig.getMusicFile(me.weishu.kernelsu.ksuApp) ?: return
        val current = MediaPlayer()
        player = current
        runCatching {
            current.setDataSource(file.path)
            current.setVolume(MusicConfig.volume, MusicConfig.volume)
            current.isLooping = MusicConfig.isLoopingEnabled
            current.setOnPreparedListener {
                if (player === it) {
                    prepared = true
                    durationState.value = it.duration
                    if (requested && foreground && MusicConfig.isMusicEnabled) start()
                }
            }
            current.setOnCompletionListener {
                if (!it.isLooping) {
                    requested = false
                    playingState.value = false
                    positionState.value = 0
                    progressJob?.cancel()
                }
            }
            current.setOnErrorListener { _, _, _ -> fail(); true }
            current.prepareAsync()
        }.onFailure { fail(it) }
    }

    private fun start() {
        runCatching {
            player?.start()
            playingState.value = true
            progressJob?.cancel()
            progressJob = scope.launch {
                while (isActive) {
                    positionState.value = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
                    delay(500)
                }
            }
        }.onFailure { fail(it) }
    }

    fun pause() {
        requested = false
        suspendPlayback()
    }

    private fun suspendPlayback() {
        progressJob?.cancel()
        if (prepared) runCatching { if (player?.isPlaying == true) player?.pause() }
        playingState.value = false
    }

    fun toggle() { if (requested) pause() else play() }

    fun seekTo(position: Int) {
        if (!prepared) return
        val target = position.coerceIn(0, durationState.value)
        runCatching { player?.seekTo(target); positionState.value = target }
    }

    fun stop() {
        progressJob?.cancel()
        val old = player
        player = null
        prepared = false
        requested = false
        runCatching { old?.release() }
        playingState.value = false
        positionState.value = 0
        durationState.value = 0
    }

    fun updateVolume(volume: Float) { runCatching { player?.setVolume(volume, volume) } }
    fun updateLooping(looping: Boolean) { runCatching { player?.isLooping = looping } }

    fun reload() {
        val resume = requested || MusicConfig.isAutoPlayEnabled
        stop()
        if (MusicConfig.isMusicEnabled && resume && foreground) play()
    }

    private fun fail(error: Throwable? = null) {
        Log.w("MusicManager", "Unable to play background music", error)
        stop()
        errorState.value = true
    }

    override fun onStart(owner: LifecycleOwner) {
        if (MusicConfig.isMusicEnabled && (requested || MusicConfig.isAutoPlayEnabled)) play()
    }

    override fun onStop(owner: LifecycleOwner) { suspendPlayback() }
}
