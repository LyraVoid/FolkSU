package me.weishu.kernelsu.wallpaper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.weishu.kernelsu.media.MusicConfig
import me.weishu.kernelsu.media.MusicManager
import me.weishu.kernelsu.media.SoundEffectConfig
import me.weishu.kernelsu.media.VisualMediaConfig
import org.json.JSONObject
import java.io.File

/** FP's JSON filename is also its ZIP entry name; these assets therefore use the group API. */
internal object MusicThemeAsset : ThemedAssetGroup {
    override fun writeConfig(json: JSONObject) {
        json.put("isMusicEnabled", MusicConfig.isMusicEnabled)
        json.put("musicFilename", MusicConfig.musicFilename ?: "")
        json.put("musicVolume", MusicConfig.volume.toDouble())
        json.put("isAutoPlayEnabled", MusicConfig.isAutoPlayEnabled)
        json.put("isLoopingEnabled", MusicConfig.isLoopingEnabled)
    }

    override fun currentFiles(context: Context): Map<String, File> =
        MusicConfig.getMusicFile(context)?.takeIf { MusicConfig.isMusicEnabled }
            ?.let { mapOf(it.name to it) } ?: emptyMap()

    override suspend fun apply(context: Context, json: JSONObject, imported: Map<String, File>) {
        val name = json.optString("musicFilename")
        val source = imported[name]
        // Never turn untrusted theme metadata into a filesystem path.
        val target = source?.let {
            File(MusicConfig.getMusicDir(context), it.name).also { target -> it.copyTo(target, true) }
        }
        withContext(Dispatchers.Main) {
            MusicManager.stop()
            val old = MusicConfig.getMusicFile(context)
            if (old != target) old?.delete()
            MusicConfig.setMusicFilenameValue(target?.name)
            MusicConfig.setMusicEnabledState(json.optBoolean("isMusicEnabled") && target != null)
            MusicConfig.setVolumeValue(json.optDouble("musicVolume", 1.0).toFloat().coerceIn(0f, 1f))
            MusicConfig.setAutoPlayEnabledState(json.optBoolean("isAutoPlayEnabled"))
            MusicConfig.setLoopingEnabledState(json.optBoolean("isLoopingEnabled"))
            MusicConfig.save(context)
            MusicManager.reload()
        }
    }

    override suspend fun reset(context: Context) = apply(context, JSONObject(), emptyMap())
}

internal object SoundThemeAsset : ThemedAssetGroup {
    private val presetEntry: String
        get() = "sound_effect_preset_${SoundEffectConfig.presetName}.wav"

    override fun writeConfig(json: JSONObject) {
        json.put("isSoundEffectEnabled", SoundEffectConfig.isSoundEffectEnabled)
        json.put("soundEffectFilename", if (SoundEffectConfig.sourceType == SoundEffectConfig.SOURCE_TYPE_PRESET)
            presetEntry else SoundEffectConfig.soundEffectFilename ?: "")
        json.put("soundEffectScope", SoundEffectConfig.scope)
    }

    override fun currentFiles(context: Context): Map<String, File> {
        if (!SoundEffectConfig.isSoundEffectEnabled) return emptyMap()
        val file = if (SoundEffectConfig.sourceType == SoundEffectConfig.SOURCE_TYPE_PRESET) {
            File(context.cacheDir, presetEntry).also { target ->
                context.assets.open("sound/${SoundEffectConfig.presetName}.wav").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        } else SoundEffectConfig.getSoundEffectFile(context)?.takeIf { it.isFile }
        return file?.let { mapOf(it.name to it) } ?: emptyMap()
    }

    override suspend fun apply(context: Context, json: JSONObject, imported: Map<String, File>) {
        val source = imported[json.optString("soundEffectFilename")]
        val target = source?.let {
            File(SoundEffectConfig.getSoundEffectDir(context), it.name).also { target -> it.copyTo(target, true) }
        }
        withContext(Dispatchers.Main) {
            val old = SoundEffectConfig.getSoundEffectFile(context)
            if (old != target) old?.delete()
            SoundEffectConfig.setFilenameValue(target?.name)
            SoundEffectConfig.setEnabledState(json.optBoolean("isSoundEffectEnabled") && target != null)
            SoundEffectConfig.setSourceTypeValue(SoundEffectConfig.SOURCE_TYPE_LOCAL)
            SoundEffectConfig.setScopeValue(json.optString("soundEffectScope", SoundEffectConfig.SCOPE_GLOBAL))
            SoundEffectConfig.save(context)
        }
    }

    override suspend fun reset(context: Context) = apply(context, JSONObject(), emptyMap())
}

internal class VisualMediaThemeAsset(private val video: Boolean) : ThemedAsset {
    override val base = if (video) "video_background" else "title_image"
    override fun currentFile(context: Context): File? = VisualMediaConfig.file(context, video)
    override fun extension(file: File): String = if (video) file.extension else super.extension(file)
    override fun writeConfig(json: JSONObject) = VisualMediaConfig.writeConfig(json)
    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        VisualMediaConfig.replace(context, video, file)
        withContext(Dispatchers.Main) {
            VisualMediaConfig.applySettings(json)
            VisualMediaConfig.save(context)
        }
    }
    override suspend fun reset(context: Context) {
        VisualMediaConfig.clear(context, video)
        withContext(Dispatchers.Main) {
            if (video) VisualMediaConfig.videoVolume = 0f else {
                VisualMediaConfig.titleDayOpacity = 1f
                VisualMediaConfig.titleNightOpacity = 1f
                VisualMediaConfig.titleDim = 0f
                VisualMediaConfig.titleOffsetX = 0f
            }
            VisualMediaConfig.save(context)
        }
    }
}
