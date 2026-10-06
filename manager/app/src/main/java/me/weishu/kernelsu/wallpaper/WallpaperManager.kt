package me.weishu.kernelsu.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.ktx.themeColorOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * File storage and derived-color extraction for the custom wallpaper.
 *
 * Blocking helpers ([computeLuminance], [computeSeed], [applyFile]) must be called off the main
 * thread; the public entry points already hop to [Dispatchers.IO].
 */
object WallpaperManager {

    private const val FILENAME_BASE = "wallpaper"
    private const val WORK_CARD_FILENAME_BASE = "work_card_background"
    private val KNOWN_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")

    /** Copies the picked image into app storage, extracts its color/luminance and enables it. */
    suspend fun save(context: Context, source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val ext = resolveExtension(context, source)
            clearOldFiles(context)
            val target = File(context.filesDir, "$FILENAME_BASE$ext")
            if (!copyToFile(context, source, target)) return@runCatching false
            applyFile(context, target)
            true
        }.getOrDefault(false)
    }

    /** Deletes the stored image and turns the wallpaper off, keeping the user's slider values. */
    fun clear(context: Context) {
        clearOldFiles(context)
        WallpaperConfig.updateUri(null)
        WallpaperConfig.updateEnabled(false)
        WallpaperConfig.updateDerivedSeed(0)
        WallpaperConfig.updateDerivedLuminance(-1f)
        WallpaperConfig.save(context)
    }

    /** Points the config at [target] and (re)derives luminance + seed. Blocking. */
    fun applyFile(context: Context, target: File) {
        val stamped = "${Uri.fromFile(target)}?t=${System.currentTimeMillis()}"
        WallpaperConfig.updateUri(stamped)
        WallpaperConfig.updateDerivedLuminance(computeLuminance(target) ?: -1f)
        WallpaperConfig.updateDerivedSeed(computeSeed(target) ?: 0)
        WallpaperConfig.updateEnabled(true)
        WallpaperConfig.save(context)
    }

    /** Copies the picked image into app storage and points the grid work card at it. */
    suspend fun saveWorkCardBackground(context: Context, source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val ext = resolveExtension(context, source)
            clearOldWorkCardFiles(context)
            val target = File(context.filesDir, "$WORK_CARD_FILENAME_BASE$ext")
            if (!copyToFile(context, source, target)) return@runCatching false
            val stamped = "${Uri.fromFile(target)}?t=${System.currentTimeMillis()}"
            WallpaperConfig.updateWorkCardBackgroundUri(stamped)
            WallpaperConfig.updateWorkCardBackgroundEnabled(true)
            WallpaperConfig.save(context)
            true
        }.getOrDefault(false)
    }

    /** Deletes the stored work-card image and turns the feature off, keeping the user's slider values. */
    fun clearWorkCardBackground(context: Context) {
        clearOldWorkCardFiles(context)
        WallpaperConfig.updateWorkCardBackgroundUri(null)
        WallpaperConfig.updateWorkCardBackgroundEnabled(false)
        WallpaperConfig.save(context)
    }

    /** Backfills color/luminance for a wallpaper that was restored without derived values. */
    fun refreshDerivedIfMissing(context: Context) {
        val path = WallpaperConfig.uri?.let { Uri.parse(it).path } ?: return
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return
        var changed = false
        if (WallpaperConfig.derivedLuminance < 0f) {
            WallpaperConfig.updateDerivedLuminance(computeLuminance(file) ?: -1f)
            changed = true
        }
        if (WallpaperConfig.derivedSeed == 0) {
            WallpaperConfig.updateDerivedSeed(computeSeed(file) ?: 0)
            changed = true
        }
        if (changed) WallpaperConfig.save(context)
    }

    /** Copies [source] into app storage (replacing any existing wallpaper) without touching config. */
    fun replaceFile(context: Context, source: File, extension: String): File {
        clearOldFiles(context)
        val target = File(context.filesDir, "$FILENAME_BASE$extension")
        source.copyTo(target, overwrite = true)
        return target
    }

    /** The currently stored wallpaper file, if any. */
    fun currentFile(context: Context): File? {
        val path = WallpaperConfig.uri?.let { Uri.parse(it).path } ?: return null
        val file = File(path)
        return if (file.exists() && file.length() > 0L) file else null
    }

    /** Copies [source] into app storage (replacing any existing work-card background). */
    fun replaceWorkCardFile(context: Context, source: File, extension: String): File {
        clearOldWorkCardFiles(context)
        val target = File(context.filesDir, "$WORK_CARD_FILENAME_BASE$extension")
        source.copyTo(target, overwrite = true)
        return target
    }

    /** The currently stored work-card background file, if any. */
    fun currentWorkCardFile(context: Context): File? {
        val path = WallpaperConfig.workCardBackgroundUri?.let { Uri.parse(it).path } ?: return null
        val file = File(path)
        return if (file.exists() && file.length() > 0L) file else null
    }

    /**
     * Best-effort extension (with leading dot) for [source], so a GIF stays a GIF.
     *
     * The content itself is checked first, because a picked or imported file may carry a wrong or
     * missing name: `contentResolver.getType` is null for `file://` URIs (the path a theme import
     * comes through), and some theme packages store a PNG/GIF under a `.jpg` entry name. Only when
     * the bytes are not a recognized image does it fall back to the MIME type, then the URI name.
     */
    fun resolveExtension(context: Context, source: Uri): String =
        sniffExtension(context, source)
            .ifEmpty { getFileExtension(context.contentResolver.getType(source)) }
            .ifEmpty { nameExtension(source) }
            .ifEmpty { ".jpg" }

    /** Extension for a MIME type, or "" when the type is unknown. */
    fun getFileExtension(mime: String?): String = when {
        mime == null -> ""
        mime.contains("png", ignoreCase = true) -> ".png"
        mime.contains("webp", ignoreCase = true) -> ".webp"
        mime.contains("gif", ignoreCase = true) -> ".gif"
        mime.contains("jpeg", ignoreCase = true) || mime.contains("jpg", ignoreCase = true) -> ".jpg"
        else -> ""
    }

    /**
     * Extension (with leading dot) matching [file]'s actual bytes, falling back to its name.
     *
     * Used on export so an asset that was stored under a wrong name (a GIF kept as `.jpg`) is still
     * written into the container under the right extension.
     */
    fun resolveFileExtension(file: File): String {
        val sniffed = try {
            file.inputStream().use { input ->
                val head = ByteArray(12)
                classifyHead(head, input.read(head))
            }
        } catch (_: Throwable) {
            ""
        }
        if (sniffed.isNotEmpty()) return sniffed
        val byName = "." + file.extension.lowercase()
        return if (byName in KNOWN_EXTENSIONS) byName else ".jpg"
    }

    private fun nameExtension(source: Uri): String {
        val name = source.path?.substringAfterLast('/') ?: return ""
        val ext = "." + name.substringAfterLast('.', "").lowercase()
        return if (ext in KNOWN_EXTENSIONS) ext else ""
    }

    private fun sniffExtension(context: Context, source: Uri): String = try {
        context.contentResolver.openInputStream(source)?.use { input ->
            val head = ByteArray(12)
            classifyHead(head, input.read(head))
        } ?: ""
    } catch (_: Throwable) {
        ""
    }

    /** Identifies an image format from its leading bytes, or "" when unrecognized. */
    private fun classifyHead(head: ByteArray, read: Int): String = when {
        read < 12 -> ""
        head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() && head[2] == 'F'.code.toByte() -> ".gif"
        head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() -> ".png"
        head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() -> ".jpg"
        head[0] == 'R'.code.toByte() && head[8] == 'W'.code.toByte() -> ".webp"
        else -> ""
    }

    /** Decodes [file] with power-of-two downsampling so neither side exceeds [maxDimension]. */
    fun decodeSampled(file: File, maxDimension: Int): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (_: Throwable) {
            null
        }
    }

    /** Average perceived luminance (0..1) of [file], or null when it cannot be decoded. */
    fun computeLuminance(file: File): Float? {
        val bitmap = decodeSampled(file, 64) ?: return null
        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) {
            bitmap.recycle()
            return null
        }
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        var sum = 0.0
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            sum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        }
        return (sum / pixels.size).toFloat()
    }

    /** Dominant seed color (ARGB) of [file], or null when it cannot be decoded. */
    fun computeSeed(file: File): Int? {
        val bitmap = decodeSampled(file, 256) ?: return null
        return try {
            bitmap.asImageBitmap().themeColorOrNull()?.toArgb()
        } catch (_: Throwable) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun copyToFile(context: Context, source: Uri, target: File): Boolean = try {
        context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
            target.length() > 0L
        } ?: false
    } catch (_: Throwable) {
        target.delete()
        false
    }

    private fun clearOldFiles(context: Context) {
        KNOWN_EXTENSIONS.forEach { ext ->
            val file = File(context.filesDir, "$FILENAME_BASE$ext")
            if (file.exists()) file.delete()
        }
    }

    private fun clearOldWorkCardFiles(context: Context) {
        KNOWN_EXTENSIONS.forEach { ext ->
            val file = File(context.filesDir, "$WORK_CARD_FILENAME_BASE$ext")
            if (file.exists()) file.delete()
        }
    }
}
