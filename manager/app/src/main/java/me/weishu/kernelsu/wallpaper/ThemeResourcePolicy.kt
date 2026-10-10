package me.weishu.kernelsu.wallpaper

import org.json.JSONObject
import java.io.File

/** FP's resource priority and independent image-presence/feature-enable semantics. */
internal object ThemeResourcePolicy {
    fun select(base: String, files: Map<String, File>): File? {
        val extensions = when (base) {
            "font" -> listOf("ttf", "otf")
            "video_background" -> listOf("mp4", "webm", "mkv")
            else -> listOf("jpg", "png", "gif", "webp", "jpeg")
        }
        return extensions.firstNotNullOfOrNull { files["$base.$it"] }
    }

    fun wantsImage(json: JSONObject, presenceKey: String?, enabled: Boolean, hasFile: Boolean): Boolean =
        if (presenceKey != null) json.optBoolean(presenceKey, false) else enabled && hasFile
}
