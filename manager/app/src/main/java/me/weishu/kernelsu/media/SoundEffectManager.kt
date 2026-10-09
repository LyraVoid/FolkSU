package me.weishu.kernelsu.media

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

object SoundEffectManager {
    private const val TAG = "SoundEffectManager"
    private var mediaPlayer: MediaPlayer? = null
    
    // Use Main dispatcher as MediaPlayer must be created/accessed on same thread or handle synch
    // But prepare can be async.
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    fun play(context: Context) {
        if (!SoundEffectConfig.isSoundEffectEnabled) return
        
        // Scope check is done by the caller (onClick listener)
        
        val sourceType = SoundEffectConfig.sourceType
        
        scope.launch {
            playSound(context, sourceType, SoundEffectConfig.presetName, SoundEffectConfig.soundEffectFilename, "sound")
        }
    }

    fun playStartup(context: Context) {
        if (!SoundEffectConfig.isStartupSoundEnabled) return
        
        val sourceType = SoundEffectConfig.startupSourceType
        
        scope.launch {
            playSound(context, sourceType, SoundEffectConfig.startupPresetName, SoundEffectConfig.startupSoundFilename, "start")
        }
    }

    private fun playSound(context: Context, sourceType: String, presetName: String, filename: String?, assetDir: String) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
            } else {
                mediaPlayer?.reset()
            }

            mediaPlayer?.apply {
                if (sourceType == SoundEffectConfig.SOURCE_TYPE_PRESET) {
                    context.assets.openFd("$assetDir/$presetName.wav").use { afd ->
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    }
                } else {
                    if (filename == null) return
                    val file = File(SoundEffectConfig.getSoundEffectDir(context), filename)
                    if (!file.exists()) return
                    setDataSource(context, Uri.fromFile(file))
                }
                
                setOnPreparedListener { mp ->
                    if (mp === mediaPlayer) mp.start()
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error: $what, $extra")
                    release()
                    true
                }
                prepareAsync() // Use async to avoid blocking UI
                setOnCompletionListener { if (it === mediaPlayer) release() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play sound effect", e)
            mediaPlayer?.release()
            mediaPlayer = null // Reset on hard failure
        }
    }

    fun release() {
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
    }
}
