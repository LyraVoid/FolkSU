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
 * Portable container for the wallpaper feature, so a background can be moved between devices and
 * between the sibling projects that share the same format. The container is
 * `[16-byte IV][AES/CBC/PKCS5Padding(zip)]`, the zip holds `theme.json` plus one image per enabled
 * background.
 *
 * The main background and the work-card background are independent features: each carries its own
 * enable flag and its own image, and either one alone is a valid package.
 */
object FolkThemeIO {

    private const val CONTAINER_KEY = "FolkPatchThemeSecretKey2025"
    private const val ENTRY_CONFIG = "theme.json"
    private const val IV_SIZE = 16
    private const val IMPORT_DIR = "wallpaper_import"

    /** Zip entry base name of the main background image. */
    private const val MAIN_ASSET_BASE = "background"

    /**
     * Zip entry base name of the work-card background image. Kept identical to the shared format so
     * packages round-trip with the sibling projects.
     */
    private const val WORK_CARD_ASSET_BASE = "grid_working_card_background"

    const val FILE_NAME = "wallpaper.fpt"

    suspend fun exportBackground(context: Context, target: Uri, name: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val mainFile = WallpaperManager.currentFile(context)
                val workCardFile = WallpaperManager.currentWorkCardFile(context)
                if (mainFile == null && workCardFile == null) return@runCatching false
                val config = JSONObject().apply {
                    put("isBackgroundEnabled", WallpaperConfig.enabled)
                    put("backgroundOpacity", WallpaperConfig.opacity.toDouble())
                    put("backgroundBlur", WallpaperConfig.blur.toDouble())
                    put("backgroundDim", WallpaperConfig.dim.toDouble())
                    put("isDualBackgroundDimEnabled", WallpaperConfig.dualDimEnabled)
                    put("backgroundDayDim", WallpaperConfig.dayDim.toDouble())
                    put("backgroundNightDim", WallpaperConfig.nightDim.toDouble())
                    // Work card is its own feature, so it is written (and later read) even when the
                    // main background is disabled.
                    put("isGridWorkingCardBackgroundEnabled", WallpaperConfig.workCardBackgroundEnabled)
                    put("gridWorkingCardBackgroundOpacity", WallpaperConfig.workCardOpacity.toDouble())
                    put("gridWorkingCardBackgroundDim", WallpaperConfig.workCardDim.toDouble())
                    put("isGridDualOpacityEnabled", WallpaperConfig.workCardDualOpacityEnabled)
                    put("gridWorkingCardBackgroundDayOpacity", WallpaperConfig.workCardDayOpacity.toDouble())
                    put("gridWorkingCardBackgroundNightOpacity", WallpaperConfig.workCardNightOpacity.toDouble())
                    put("isGridWorkingCardCheckHidden", WallpaperConfig.workCardCheckHidden)
                    put("isGridWorkingCardTextHidden", WallpaperConfig.workCardTextHidden)
                    put("isGridWorkingCardModeHidden", WallpaperConfig.workCardModeHidden)
                    put("meta_name", name)
                    put("meta_type", "background")
                    put("meta_version", 1)
                }
                val zipped = zip(config.toString().toByteArray(Charsets.UTF_8), mainFile, workCardFile)
                val encrypted = encrypt(zipped)
                context.contentResolver.openOutputStream(target)?.use { it.write(encrypted) }
                    ?: return@runCatching false
                true
            }.getOrDefault(false)
        }

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
            var workCardFile: File? = null
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
                            if (entry.name == ENTRY_CONFIG) {
                                config = JSONObject(outFile.readText())
                            } else {
                                when (entry.name.substringBefore('.').substringAfterLast('/')) {
                                    MAIN_ASSET_BASE -> backgroundFile = outFile
                                    WORK_CARD_ASSET_BASE -> workCardFile = outFile
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                val json = config ?: return@runCatching false
                applyImported(context, json, backgroundFile, workCardFile)
                true
            } finally {
                dest.deleteRecursively()
            }
        }.getOrDefault(false)
    }

    private suspend fun applyImported(
        context: Context,
        json: JSONObject,
        backgroundFile: File?,
        workCardFile: File?,
    ) {
        val enabled = json.optBoolean("isBackgroundEnabled", false)
        if (enabled && backgroundFile != null) {
            val ext = "." + backgroundFile.name.substringAfterLast('.', "jpg")
            val target = WallpaperManager.replaceFile(context, backgroundFile, ext)
            WallpaperManager.applyFile(context, target)
        } else {
            WallpaperManager.clear(context)
        }
        WallpaperConfig.updateOpacity(json.optDouble("backgroundOpacity", WallpaperConfig.opacity.toDouble()).toFloat())
        WallpaperConfig.updateBlur(json.optDouble("backgroundBlur", WallpaperConfig.blur.toDouble()).toFloat())
        WallpaperConfig.updateDim(json.optDouble("backgroundDim", WallpaperConfig.dim.toDouble()).toFloat())
        WallpaperConfig.updateDualDimEnabled(
            json.optBoolean("isDualBackgroundDimEnabled", WallpaperConfig.dualDimEnabled)
        )
        WallpaperConfig.updateDayDim(json.optDouble("backgroundDayDim", WallpaperConfig.dayDim.toDouble()).toFloat())
        WallpaperConfig.updateNightDim(
            json.optDouble("backgroundNightDim", WallpaperConfig.nightDim.toDouble()).toFloat()
        )

        // Work card: independent of the main background above.
        val workCardEnabled = json.optBoolean("isGridWorkingCardBackgroundEnabled", false)
        if (workCardEnabled && workCardFile != null) {
            WallpaperManager.saveWorkCardBackground(context, Uri.fromFile(workCardFile))
        } else {
            WallpaperManager.clearWorkCardBackground(context)
        }
        WallpaperConfig.updateWorkCardOpacity(
            json.optDouble("gridWorkingCardBackgroundOpacity", WallpaperConfig.workCardOpacity.toDouble()).toFloat()
        )
        WallpaperConfig.updateWorkCardDim(
            json.optDouble("gridWorkingCardBackgroundDim", WallpaperConfig.workCardDim.toDouble()).toFloat()
        )
        WallpaperConfig.updateWorkCardDualOpacityEnabled(
            json.optBoolean("isGridDualOpacityEnabled", WallpaperConfig.workCardDualOpacityEnabled)
        )
        WallpaperConfig.updateWorkCardDayOpacity(
            json.optDouble("gridWorkingCardBackgroundDayOpacity", WallpaperConfig.workCardDayOpacity.toDouble()).toFloat()
        )
        WallpaperConfig.updateWorkCardNightOpacity(
            json.optDouble("gridWorkingCardBackgroundNightOpacity", WallpaperConfig.workCardNightOpacity.toDouble()).toFloat()
        )
        WallpaperConfig.updateWorkCardCheckHidden(
            json.optBoolean("isGridWorkingCardCheckHidden", WallpaperConfig.workCardCheckHidden)
        )
        WallpaperConfig.updateWorkCardTextHidden(
            json.optBoolean("isGridWorkingCardTextHidden", WallpaperConfig.workCardTextHidden)
        )
        WallpaperConfig.updateWorkCardModeHidden(
            json.optBoolean("isGridWorkingCardModeHidden", WallpaperConfig.workCardModeHidden)
        )

        WallpaperConfig.save(context)
    }

    private fun zip(config: ByteArray, mainImage: File?, workCardImage: File?): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_CONFIG))
            zip.write(config)
            zip.closeEntry()
            mainImage?.let { image ->
                val ext = image.extension.ifEmpty { "jpg" }
                zip.putNextEntry(ZipEntry("$MAIN_ASSET_BASE.$ext"))
                image.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            workCardImage?.let { image ->
                val ext = image.extension.ifEmpty { "jpg" }
                zip.putNextEntry(ZipEntry("$WORK_CARD_ASSET_BASE.$ext"))
                image.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
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

    private fun secretKey(): SecretKeySpec = SecretKeySpec(
        MessageDigest.getInstance("SHA-256").digest(CONTAINER_KEY.toByteArray(Charsets.UTF_8)),
        "AES",
    )

    private fun isSafeChild(root: File, child: File): Boolean {
        val canonicalRoot = root.canonicalPath
        val canonicalChild = child.canonicalPath
        return canonicalChild == canonicalRoot || canonicalChild.startsWith(canonicalRoot + File.separator)
    }
}
