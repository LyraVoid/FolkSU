package me.weishu.kernelsu.wallpaper

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object ThemeExportService {
    suspend fun export(context: Context, target: Uri, metadata: ThemeMetadata): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val retained = File(context.filesDir, "theme_passthrough")
                val original = File(retained, "theme.json").takeIf { it.isFile }
                    ?.let { JSONObject(it.readText()) } ?: JSONObject()
                val json = JSONObject(original.toString())
                json.remove("navIcons")
                FolkThemeIO.assets.forEach { it.writeConfig(json) }
                FolkThemeIO.groups.forEach { it.writeConfig(json) }
                ThemeSettingsMapping.write(json)
                metadata.write(json)
                val applied = File(retained, "applied.json").takeIf { it.isFile }
                    ?.let { JSONObject(it.readText()) }
                // An unsupported enum is retained until the corresponding local setting changes.
                listOf("customColor", "colorGenerationMode", "colorStandard", "colorStyle", "colorContrast",
                    "fontMode", "homeLayoutStyle", "statsTopLayout", "soundEffectScope").forEach { field ->
                    if (original.has(field) && applied != null &&
                        applied.opt(field) == json.opt(field) && original.opt(field) != applied.opt(field)) {
                        json.put(field, original.get(field))
                    }
                }
                // Retain unknown color tokens unless the user changed the mapped setting.
                if (original.has("customColor") &&
                    ThemeSettingsMapping.colors[original.optString("customColor")] == json.optInt("folksu_keyColor")) {
                    json.put("customColor", original.get("customColor"))
                }
                val entries = linkedMapOf<String, File>()
                val ownedBases = FolkThemeIO.assets.map { it.base }.toSet()
                val ownedNames = buildSet {
                    listOf("musicFilename", "soundEffectFilename").forEach { add(original.optString(it)) }
                    original.optJSONObject("navIcons")?.let { icons ->
                        listOf("Home", "SuperUser", "AModule", "Settings").forEach { add(icons.optString(it)) }
                    }
                }
                retained.listFiles()?.filter { it.isFile && it.name !in setOf("theme.json", "applied.json") &&
                    it.name.substringBefore('.') !in ownedBases && it.name !in ownedNames }
                    ?.forEach { entries[it.name] = it }
                FolkThemeIO.assets.forEach { asset ->
                    asset.currentFile(context)?.let { entries["${asset.base}.${asset.extension(it)}"] = it }
                }
                FolkThemeIO.groups.forEach { group -> group.currentFiles(context).forEach { (name, file) ->
                    require(name !in entries) { "Conflicting resource name: $name" }
                    entries[name] = file
                } }
                original.optJSONObject("navIcons")?.let { originalIcons ->
                    originalIcons.keys().forEach { destination ->
                        val name = originalIcons.optString(destination)
                        if (destination !in setOf("Home", "SuperUser", "AModule", "Settings") && name in entries) {
                            val icons = json.optJSONObject("navIcons") ?: JSONObject().also { json.put("navIcons", it) }
                            icons.put(destination, name)
                        }
                    }
                }
                ThemeValidation.validate(JSONObject(json.toString()), entries)
                val output = context.contentResolver.openOutputStream(target) ?: error("Cannot write theme")
                output.use { FptContainer.write(json, entries, it) }
                metadata.save(context)
            }
        }
}
