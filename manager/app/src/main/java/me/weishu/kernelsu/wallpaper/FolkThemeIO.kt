package me.weishu.kernelsu.wallpaper

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import me.weishu.kernelsu.ui.component.bottombar.BottomBarDestination
import me.weishu.kernelsu.ui.component.bottombar.BottomBarIconConfig
import me.weishu.kernelsu.ui.theme.FontConfig
import me.weishu.kernelsu.ui.theme.FontMode
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
 * One themed asset that the `.fpt` container can carry.
 *
 * The container is a flat list of [ThemedAsset]s: adding a new themed asset means writing one
 * implementation and registering it in [FolkThemeIO.assets] — no changes to the container code.
 * Each entry owns three things:
 *  - [base]: the file-name stem inside the zip (`<base>.<ext>`), which also identifies the asset
 *    when importing;
 *  - [writeConfig]: the `theme.json` keys this asset contributes on export;
 *  - [apply]: reading those keys back and applying [file] (or clearing the asset when it is null).
 */
internal interface ThemedAsset {
    val base: String

    fun currentFile(context: Context): File?

    fun writeConfig(json: JSONObject)

    suspend fun apply(context: Context, json: JSONObject, file: File?)
}

/**
 * A themed asset that owns several files at once — one per bottom-bar destination, for example.
 *
 * Registered alongside the single-file [ThemedAsset]s in [FolkThemeIO.groups]. A group decides
 * which zip entries it owns by reading its own `theme.json` metadata, so its file names are free.
 */
internal interface ThemedAssetGroup {
    fun writeConfig(json: JSONObject)

    /** The zip entries to write on export, keyed by entry name (e.g. `nav_icon_Home.png`). */
    fun currentFiles(context: Context): Map<String, File>

    /**
     * Apply this group from an imported theme. [imported] maps every zip entry name to the file
     * that was extracted for it.
     */
    suspend fun apply(context: Context, json: JSONObject, imported: Map<String, File>)
}

/**
 * The user's main background wallpaper and its opacity/blur/scrim settings.
 */
private object MainWallpaperAsset : ThemedAsset {
    override val base = "background"

    override fun currentFile(context: Context): File? = WallpaperManager.currentFile(context)

    override fun writeConfig(json: JSONObject) {
        json.put("isBackgroundEnabled", WallpaperConfig.enabled)
        json.put("backgroundOpacity", WallpaperConfig.opacity.toDouble())
        json.put("backgroundBlur", WallpaperConfig.blur.toDouble())
        json.put("backgroundDim", WallpaperConfig.dim.toDouble())
        json.put("isDualBackgroundDimEnabled", WallpaperConfig.dualDimEnabled)
        json.put("backgroundDayDim", WallpaperConfig.dayDim.toDouble())
        json.put("backgroundNightDim", WallpaperConfig.nightDim.toDouble())
        json.put("isMultiBackgroundEnabled", WallpaperConfig.multiBackgroundEnabled)
    }

    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        val enabled = json.optBoolean("isBackgroundEnabled", false)
        if (enabled && file != null) {
            val extension = WallpaperManager.resolveExtension(context, Uri.fromFile(file))
            val target = WallpaperManager.replaceFile(context, file, extension)
            WallpaperManager.applyFile(context, target)
        } else {
            WallpaperManager.clear(context)
        }
        // Apply before the per-page assets so a multi-mode theme is remembered even when no page
        // image survives; each page asset still turns multi mode on when it restores an image.
        WallpaperConfig.updateMultiBackgroundEnabled(
            json.optBoolean("isMultiBackgroundEnabled", WallpaperConfig.multiBackgroundEnabled)
        )
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
    }
}

/**
 * The home work card's own background image. It is an independent feature: it round-trips even when
 * the main wallpaper is off.
 *
 * The key names and the asset base are shared with the wider theme ecosystem, so a theme exported
 * elsewhere can be imported here (and vice versa).
 */
private object WorkCardBackgroundAsset : ThemedAsset {
    override val base = "grid_working_card_background"

    override fun currentFile(context: Context): File? = WallpaperManager.currentWorkCardFile(context)

    override fun writeConfig(json: JSONObject) {
        json.put("isGridWorkingCardBackgroundEnabled", WallpaperConfig.workCardBackgroundEnabled)
        json.put("gridWorkingCardBackgroundOpacity", WallpaperConfig.workCardOpacity.toDouble())
        json.put("gridWorkingCardBackgroundDim", WallpaperConfig.workCardDim.toDouble())
        json.put("isGridDualOpacityEnabled", WallpaperConfig.workCardDualOpacityEnabled)
        json.put("gridWorkingCardBackgroundDayOpacity", WallpaperConfig.workCardDayOpacity.toDouble())
        json.put("gridWorkingCardBackgroundNightOpacity", WallpaperConfig.workCardNightOpacity.toDouble())
        json.put("isGridWorkingCardCheckHidden", WallpaperConfig.workCardCheckHidden)
        json.put("isGridWorkingCardTextHidden", WallpaperConfig.workCardTextHidden)
        json.put("isGridWorkingCardModeHidden", WallpaperConfig.workCardModeHidden)
    }

    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        val enabled = json.optBoolean("isGridWorkingCardBackgroundEnabled", false)
        if (enabled && file != null) {
            WallpaperManager.saveWorkCardBackground(context, Uri.fromFile(file))
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
    }
}

/**
 * The stored file for a page background, or null when multi mode is off or the page is unset.
 *
 * The page assets below carry only a zip entry each: the shared theme format stores no per-page
 * numbers, and their base names are shared with the wider theme ecosystem for interop.
 */
private fun currentPageFile(uri: String?): File? {
    if (!WallpaperConfig.multiBackgroundEnabled) return null
    val path = uri?.let { Uri.parse(it).path } ?: return null
    val file = File(path)
    return if (file.exists() && file.length() > 0L) file else null
}

/** Restores a page background from [file], or clears that page when the theme has none. */
private suspend fun applyPageBackground(context: Context, page: Int, file: File?) {
    if (file != null) {
        WallpaperManager.savePageBackground(context, page, Uri.fromFile(file))
    } else {
        WallpaperManager.clearPageBackground(context, page)
    }
}

private object HomeBackgroundAsset : ThemedAsset {
    override val base = "background_home"

    override fun currentFile(context: Context): File? =
        currentPageFile(WallpaperConfig.homeBackgroundUri)

    override fun writeConfig(json: JSONObject) = Unit

    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        applyPageBackground(context, WallpaperConfig.PAGE_HOME, file)
    }
}

private object SuperuserBackgroundAsset : ThemedAsset {
    override val base = "background_superuser"

    override fun currentFile(context: Context): File? =
        currentPageFile(WallpaperConfig.superuserBackgroundUri)

    override fun writeConfig(json: JSONObject) = Unit

    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        applyPageBackground(context, WallpaperConfig.PAGE_SUPERUSER, file)
    }
}

private object ModuleBackgroundAsset : ThemedAsset {
    override val base = "background_system_module"

    override fun currentFile(context: Context): File? =
        currentPageFile(WallpaperConfig.moduleBackgroundUri)

    override fun writeConfig(json: JSONObject) = Unit

    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        applyPageBackground(context, WallpaperConfig.PAGE_MODULE, file)
    }
}

private object SettingsBackgroundAsset : ThemedAsset {
    override val base = "background_settings"

    override fun currentFile(context: Context): File? =
        currentPageFile(WallpaperConfig.settingsBackgroundUri)

    override fun writeConfig(json: JSONObject) = Unit

    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        applyPageBackground(context, WallpaperConfig.PAGE_SETTINGS, file)
    }
}

/**
 * The app font. Only [FontMode.CUSTOM] owns a file; the other two modes are pure configuration, so
 * they round-trip through `theme.json` alone.
 *
 * The key names and the `font.ttf` asset name are shared with the wider theme ecosystem, so a theme
 * exported elsewhere imports here (and vice versa).
 */
private object FontAsset : ThemedAsset {
    override val base = "font"

    override fun currentFile(context: Context): File? {
        if (FontConfig.fontMode != FontMode.CUSTOM) return null
        val filename = FontConfig.customFontFilename ?: return null
        val file = File(context.filesDir, filename)
        return if (file.exists() && file.length() > 0L) file else null
    }

    override fun writeConfig(json: JSONObject) {
        json.put("fontMode", FontConfig.fontMode.serializedName)
        json.put("isFontEnabled", FontConfig.isCustomFontEnabled)
    }

    override suspend fun apply(context: Context, json: JSONObject, file: File?) {
        // Themes written before the three-mode setting only carry the legacy boolean.
        val mode = FontMode.fromSerializedName(json.optString("fontMode").ifEmpty { null })
            ?: if (json.optBoolean("isFontEnabled", false)) FontMode.CUSTOM else FontMode.SYSTEM_DEFAULT

        when {
            mode != FontMode.CUSTOM -> FontConfig.setFontMode(context, mode)
            file != null -> FontConfig.applyCustomFont(context, file)
            else -> FontConfig.setFontMode(context, FontMode.APP_DEFAULT)
        }
    }
}

/**
 * The user's custom bottom-navigation icons.
 *
 * Stored using the FolkPatch ecosystem keys: `navIconCustomEnabled` plus a `navIcons` object that
 * maps a canonical destination name to its zip file name (`nav_icon_<themeKey>.png`). The
 * canonical names are mapped onto our own [BottomBarDestination]s, so a theme exported by either
 * app can be imported by the other.
 */
private object NavIconsAsset : ThemedAssetGroup {
    private const val KEY_ENABLED = "navIconCustomEnabled"
    private const val KEY_ICONS = "navIcons"

    private fun entryName(destination: BottomBarDestination) =
        "nav_icon_${destination.themeKey}.png"

    override fun writeConfig(json: JSONObject) {
        json.put(KEY_ENABLED, BottomBarIconConfig.isEnabled)
        if (!BottomBarIconConfig.isEnabled) return
        val icons = JSONObject()
        BottomBarDestination.entries.forEach { destination ->
            if (BottomBarIconConfig.iconFile(destination.name).exists()) {
                icons.put(destination.themeKey, entryName(destination))
            }
        }
        if (icons.length() > 0) json.put(KEY_ICONS, icons)
    }

    override fun currentFiles(context: Context): Map<String, File> {
        if (!BottomBarIconConfig.isEnabled) return emptyMap()
        return buildMap {
            BottomBarDestination.entries.forEach { destination ->
                val file = BottomBarIconConfig.iconFile(destination.name)
                if (file.exists() && file.length() > 0L) put(entryName(destination), file)
            }
        }
    }

    override suspend fun apply(context: Context, json: JSONObject, imported: Map<String, File>) {
        val enabled = json.optBoolean(KEY_ENABLED, false) ||
            json.optBoolean(BottomBarIconConfig.ENABLED_KEY, false)
        val icons = json.optJSONObject(KEY_ICONS)

        BottomBarDestination.entries.forEach { destination ->
            val name = icons?.optString(destination.themeKey).orEmpty()
            val source = if (enabled) findImported(imported, name) else null
            val target = BottomBarIconConfig.iconFile(destination.name)
            if (source != null) {
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
                BottomBarIconConfig.setCustomIconUri(destination.name, Uri.fromFile(target).toString())
            } else {
                BottomBarIconConfig.clearCustomIcon(destination.name)
            }
        }
        BottomBarIconConfig.isEnabled = enabled
        BottomBarIconConfig.notifyChanged()
    }

    private fun findImported(imported: Map<String, File>, name: String): File? {
        if (name.isEmpty()) return null
        return imported[name]
            ?: imported.entries.firstOrNull { zipStem(it.key) == name.substringBefore('.') }?.value
    }
}

/** The file-name stem of a zip entry: `nav_icon_Home.png` -> `nav_icon_Home`. */
private fun zipStem(entryName: String): String =
    entryName.substringBefore('.').substringAfterLast('/')

/**
 * Reads and writes the `.fpt` background theme container.
 *
 * The container is `[16-byte IV][AES/CBC/PKCS5Padding(ZIP)]`; the zip holds `theme.json` plus one
 * `<base>.<ext>` entry per themed asset. See [ThemedAsset] for how to add one.
 */
object FolkThemeIO {
    private const val CONTAINER_KEY = "FolkPatchThemeSecretKey2025"
    private const val ENTRY_CONFIG = "theme.json"
    private const val IV_SIZE = 16
    private const val IMPORT_DIR = "wallpaper_import"

    const val FILE_NAME = "wallpaper.fpt"

    /** The registered themed assets, in export order. Add a themed asset by adding one entry here. */
    private val assets: List<ThemedAsset> = listOf(
        MainWallpaperAsset,
        WorkCardBackgroundAsset,
        HomeBackgroundAsset,
        SuperuserBackgroundAsset,
        ModuleBackgroundAsset,
        SettingsBackgroundAsset,
        FontAsset,
    )

    /** Multi-file themed assets, registered alongside the single-file [assets]. */
    private val groups: List<ThemedAssetGroup> = listOf(NavIconsAsset)

    suspend fun exportBackground(context: Context, target: Uri, name: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val entries = buildList {
                    assets.forEach { asset ->
                        asset.currentFile(context)?.let { file ->
                            val extension = WallpaperManager.resolveFileExtension(file).removePrefix(".")
                            add("${asset.base}.$extension" to file)
                        }
                    }
                    groups.forEach { group ->
                        group.currentFiles(context).forEach { (entryName, file) ->
                            add(entryName to file)
                        }
                    }
                }
                // Nothing to export only when every asset is absent; a lone work card is enough.
                if (entries.isEmpty()) return@runCatching false

                val config = JSONObject().apply {
                    assets.forEach { it.writeConfig(this) }
                    groups.forEach { it.writeConfig(this) }
                    put("meta_name", name)
                    put("meta_type", "background")
                    put("meta_version", 1)
                }
                val zipped = zip(config.toString().toByteArray(Charsets.UTF_8), entries)
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
            val imported = mutableMapOf<String, File>()
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
                                imported[entry.name] = outFile
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                val json = config ?: return@runCatching false
                assets.forEach { asset ->
                    val file = imported.entries.firstOrNull { zipStem(it.key) == asset.base }?.value
                    asset.apply(context, json, file)
                }
                groups.forEach { it.apply(context, json, imported) }
                WallpaperConfig.save(context)
                true
            } finally {
                dest.deleteRecursively()
            }
        }.getOrDefault(false)
    }

    private fun zip(config: ByteArray, entries: List<Pair<String, File>>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_CONFIG))
            zip.write(config)
            zip.closeEntry()
            entries.forEach { (name, file) ->
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
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
        "AES"
    )

    private fun isSafeChild(root: File, child: File): Boolean {
        val canonicalRoot = root.canonicalPath
        val canonicalChild = child.canonicalPath
        return canonicalChild == canonicalRoot || canonicalChild.startsWith(canonicalRoot + File.separator)
    }
}
