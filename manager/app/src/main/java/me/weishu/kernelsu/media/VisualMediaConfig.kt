package me.weishu.kernelsu.media

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File

/** Appearance media owns its storage and settings, independently of image wallpaper slots. */
object VisualMediaConfig {
    var videoEnabled by mutableStateOf(false)
    var videoFilename by mutableStateOf<String?>(null)
        private set
    var videoVolume by mutableFloatStateOf(0f)
    var titleEnabled by mutableStateOf(false)
    var titleFilename by mutableStateOf<String?>(null)
        private set
    var titleDayOpacity by mutableFloatStateOf(1f)
    var titleNightOpacity by mutableFloatStateOf(1f)
    var titleDim by mutableFloatStateOf(0f)
    var titleOffsetX by mutableFloatStateOf(0f)
    var revision by mutableIntStateOf(0)
        private set

    fun file(context: Context, video: Boolean): File? =
        (if (video) videoFilename else titleFilename)?.let { File(context.filesDir, it) }
            ?.takeIf { it.isFile }

    fun load(context: Context) {
        val p = context.getSharedPreferences("visual_media", Context.MODE_PRIVATE)
        videoFilename = p.getString("videoFilename", null)
        titleFilename = p.getString("titleFilename", null)
        applySettings(runCatching { JSONObject(p.getString("config", "{}") ?: "{}") }.getOrDefault(JSONObject()))
    }

    fun save(context: Context) {
        val json = JSONObject().also { writeConfig(it) }
        context.getSharedPreferences("visual_media", Context.MODE_PRIVATE).edit()
            .putString("videoFilename", videoFilename).putString("titleFilename", titleFilename)
            .putString("config", json.toString()).apply()
    }

    fun writeConfig(json: JSONObject) {
        json.put("isVideoBackgroundEnabled", videoEnabled)
        json.put("videoVolume", videoVolume.toDouble())
        json.put("isAdvancedTitleStyleEnabled", titleEnabled)
        json.put("titleImageDayOpacity", titleDayOpacity.toDouble())
        json.put("titleImageNightOpacity", titleNightOpacity.toDouble())
        json.put("titleImageDim", titleDim.toDouble())
        json.put("titleImageOffsetX", titleOffsetX.toDouble())
    }

    fun applySettings(json: JSONObject) {
        videoEnabled = json.optBoolean("isVideoBackgroundEnabled", false)
        videoVolume = json.optDouble("videoVolume", 0.0).toFloat().coerceIn(0f, 1f)
        titleEnabled = json.optBoolean("isAdvancedTitleStyleEnabled", false)
        titleDayOpacity = json.optDouble("titleImageDayOpacity", 1.0).toFloat().coerceIn(0f, 1f)
        titleNightOpacity = json.optDouble("titleImageNightOpacity", 1.0).toFloat().coerceIn(0f, 1f)
        titleDim = json.optDouble("titleImageDim", 0.0).toFloat().coerceIn(0f, 1f)
        titleOffsetX = json.optDouble("titleImageOffsetX", 0.0).toFloat().coerceIn(-2f, 2f)
    }

    suspend fun replace(context: Context, video: Boolean, source: File?) {
        val old = file(context, video)
        val target = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { source?.let {
            val stem = if (video) "video_background" else "title_image"
            File(context.filesDir, "$stem.${it.extension.ifEmpty { if (video) "mp4" else "png" }}")
                .also { target ->
                    if (it.canonicalPath != target.canonicalPath) {
                        val atomic = android.util.AtomicFile(target)
                        val output = atomic.startWrite()
                        try {
                            it.inputStream().use { input -> input.copyTo(output) }
                            atomic.finishWrite(output)
                        } catch (error: Exception) {
                            atomic.failWrite(output)
                            throw error
                        }
                    }
                }
        }.also { target -> if (old != target) old?.delete() } }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (video) videoFilename = target?.name else titleFilename = target?.name
            revision++
        }
    }

    suspend fun select(context: Context, uri: Uri, video: Boolean): Boolean = runCatching {
        val temporary = MediaFiles.import(context, uri, context.cacheDir, "media", if (video) "mp4" else "png")
        try {
            replace(context, video, temporary)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (video) videoEnabled = true else titleEnabled = true
                save(context)
            }
        } finally { temporary.delete() }
    }.isSuccess

    suspend fun clear(context: Context, video: Boolean) {
        replace(context, video, null)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (video) videoEnabled = false else titleEnabled = false
            save(context)
        }
    }
}
