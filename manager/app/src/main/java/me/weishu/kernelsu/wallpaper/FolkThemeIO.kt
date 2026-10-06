package me.weishu.kernelsu.wallpaper

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable theme container for the background subset.
 *
 * Container layout: `[16-byte IV][AES/CBC/PKCS5Padding(DEFLATE zip)]`, where the zip holds a
 * `theme.json` descriptor plus the wallpaper asset. The key is a fixed shared secret so the
 * container round-trips with the wider theme ecosystem; only the background keys are read/written.
 */
object FolkThemeIO {

    private const val CONTAINER_KEY = "FolkPatchThemeSecretKey2025"
    private const val ENTRY_CONFIG = "theme.json"
    private const val IV_SIZE = 16
    private const val IMPORT_DIR = "wallpaper_import"

    const val FILE_NAME = "wallpaper.fpt"

    /** Writes the active wallpaper + its subset of theme keys to [target]. */
    suspend fun exportBackground(context: Context, target: Uri, name: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = WallpaperManager.currentFile(context) ?: return@runCatching false
                val config = JSONObject().apply {
                    put("isBackgroundEnabled", WallpaperConfig.enabled)
                    put("backgroundOpacity", WallpaperConfig.opacity.toDouble())
                    put("backgroundBlur", WallpaperConfig.blur.toDouble())
                    put("backgroundDim", WallpaperConfig.dim.toDouble())
                    put("isDualBackgroundDimEnabled", WallpaperConfig.dualDimEnabled)
                    put("backgroundDayDim", WallpaperConfig.dayDim.toDouble())
                    put("backgroundNightDim", WallpaperConfig.nightDim.toDouble())
                    put("meta_name", name)
                    put("meta_type", "background")
                    put("meta_version", 1)
                }
                val zipped = zip(config.toString().toByteArray(Charsets.UTF_8), source)
                val encrypted = encrypt(zipped)
                context.contentResolver.openOutputStream(target)?.use { it.write(encrypted) }
                    ?: return@runCatching false
                true
            }.getOrDefault(false)
        }

    /** Reads a background subset container and applies it. Returns false on any malformed input. */
    suspend fun importBackground(context: Context, source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(source)?.use { it.readBytes() }
                ?: return@runCatching false
            if (bytes.size <= IV_SIZE) return@runCatching false
            val decrypted = decrypt(bytes)
            val dest = File(context.cacheDir, IMPORT_DIR).apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }
            var config: JSONObject? = null
            var backgroundFile: File? = null
            try {
                ZipInputStream(ByteArrayInputStream(decrypted)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val outFile = File(dest, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else if (isSafeChild(dest, outFile)) {
                            outFile.parentFile?.mkdirs()
                            outFile.outputStream().use { zip.copyTo(it) }
                            when {
                                entry.name == ENTRY_CONFIG -> config = JSONObject(outFile.readText())
                                entry.name.substringBefore('.').substringAfterLast('/') == "background" ->
                                    backgroundFile = outFile
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                val json = config ?: return@runCatching false
                applyImported(context, json, backgroundFile)
                true
            } finally {
                dest.deleteRecursively()
            }
        }.getOrDefault(false)
    }

    private fun applyImported(context: Context, json: JSONObject, backgroundFile: File?) {
        val enabled = json.optBoolean("isBackgroundEnabled", false)
        if (enabled && backgroundFile != null) {
            val ext = "." + backgroundFile.name.substringAfterLast('.', "jpg")
            val target = WallpaperManager.replaceFile(context, backgroundFile, ext)
            WallpaperManager.applyFile(context, target)
        } else {
            WallpaperManager.clear(context)
        }
        WallpaperConfig.updateOpacity(
            json.optDouble("backgroundOpacity", WallpaperConfig.opacity.toDouble()).toFloat()
        )
        WallpaperConfig.updateBlur(
            json.optDouble("backgroundBlur", WallpaperConfig.blur.toDouble()).toFloat()
        )
        WallpaperConfig.updateDim(
            json.optDouble("backgroundDim", WallpaperConfig.dim.toDouble()).toFloat()
        )
        WallpaperConfig.updateDualDimEnabled(
            json.optBoolean("isDualBackgroundDimEnabled", WallpaperConfig.dualDimEnabled)
        )
        WallpaperConfig.updateDayDim(
            json.optDouble("backgroundDayDim", WallpaperConfig.dayDim.toDouble()).toFloat()
        )
        WallpaperConfig.updateNightDim(
            json.optDouble("backgroundNightDim", WallpaperConfig.nightDim.toDouble()).toFloat()
        )
        WallpaperConfig.save(context)
    }

    private fun zip(config: ByteArray, image: File): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_CONFIG))
            zip.write(config)
            zip.closeEntry()
            val ext = image.extension.ifEmpty { "jpg" }
            zip.putNextEntry(ZipEntry("background.$ext"))
            image.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(), IvParameterSpec(iv))
        return iv + cipher.doFinal(plain)
    }

    private fun decrypt(container: ByteArray): ByteArray {
        val iv = container.copyOfRange(0, IV_SIZE)
        val payload = container.copyOfRange(IV_SIZE, container.size)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), IvParameterSpec(iv))
        return cipher.doFinal(payload)
    }

    private fun secretKey(): SecretKeySpec =
        SecretKeySpec(
            MessageDigest.getInstance("SHA-256").digest(CONTAINER_KEY.toByteArray(Charsets.UTF_8)),
            "AES",
        )

    private fun isSafeChild(root: File, child: File): Boolean {
        val canonicalRoot = root.canonicalPath
        val canonicalChild = child.canonicalPath
        return canonicalChild == canonicalRoot || canonicalChild.startsWith(canonicalRoot + File.separator)
    }
}
