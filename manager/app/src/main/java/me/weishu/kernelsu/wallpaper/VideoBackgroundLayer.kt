package me.weishu.kernelsu.wallpaper

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.weishu.kernelsu.media.VisualMediaConfig
import java.io.File

@Composable
fun VideoBackgroundLayer(file: File, dim: Float, modifier: Modifier = Modifier) {
    val owner = LocalLifecycleOwner.current
    key(file.path, VisualMediaConfig.revision) {
        val holder = remember { arrayOfNulls<VideoView>(1) }
        val players = remember { arrayOfNulls<MediaPlayer>(1) }
        val volume = VisualMediaConfig.videoVolume
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> holder[0]?.start()
                    Lifecycle.Event.ON_STOP -> holder[0]?.pause()
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose {
                owner.lifecycle.removeObserver(observer)
                holder[0]?.stopPlayback()
                holder[0] = null
                players[0] = null
            }
        }
        Box(modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    VideoView(context).apply {
                        holder[0] = this
                        setOnPreparedListener { player ->
                            players[0] = player
                            player.isLooping = true
                            player.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                            player.setVolume(VisualMediaConfig.videoVolume, VisualMediaConfig.videoVolume)
                            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) start()
                        }
                        setOnErrorListener { _, _, _ -> true }
                        setVideoURI(Uri.fromFile(file))
                    }
                },
                update = { view ->
                    runCatching { players[0]?.setVolume(volume, volume) }
                },
                onRelease = { it.stopPlayback() },
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
        }
    }
}
