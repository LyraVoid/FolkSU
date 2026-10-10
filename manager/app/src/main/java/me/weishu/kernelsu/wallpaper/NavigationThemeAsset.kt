package me.weishu.kernelsu.wallpaper

import android.content.Context
import android.net.Uri
import me.weishu.kernelsu.ui.component.bottombar.BottomBarDestination
import me.weishu.kernelsu.ui.component.bottombar.BottomBarIconConfig
import org.json.JSONObject
import java.io.File

internal object NavIconsAsset : ThemedAssetGroup {
    private fun entryName(destination: BottomBarDestination) = "nav_icon_${destination.themeKey}.png"

    override fun writeConfig(json: JSONObject) {
        json.put("navIconCustomEnabled", BottomBarIconConfig.isEnabled)
        json.remove("navIcons")
        if (!BottomBarIconConfig.isEnabled) return
        val icons = JSONObject()
        BottomBarDestination.entries.forEach { destination ->
            if (BottomBarIconConfig.iconFile(destination.name).exists()) {
                icons.put(destination.themeKey, entryName(destination))
            }
        }
        if (icons.length() > 0) json.put("navIcons", icons)
    }

    override fun currentFiles(context: Context): Map<String, File> {
        if (!BottomBarIconConfig.isEnabled) return emptyMap()
        return buildMap {
            BottomBarDestination.entries.forEach { destination ->
                val file = BottomBarIconConfig.iconFile(destination.name)
                if (file.isFile && file.length() > 0L) put(entryName(destination), file)
            }
        }
    }

    override suspend fun apply(context: Context, json: JSONObject, imported: Map<String, File>) {
        val enabled = json.optBoolean("navIconCustomEnabled", false) ||
            json.optBoolean(BottomBarIconConfig.ENABLED_KEY, false)
        val icons = json.optJSONObject("navIcons")
        BottomBarDestination.entries.forEach { destination ->
            val name = icons?.optString(destination.themeKey).orEmpty()
            val source = if (enabled) imported[name] else null
            val target = BottomBarIconConfig.iconFile(destination.name)
            if (source != null) {
                target.parentFile?.mkdirs()
                source.copyTo(target, overwrite = true)
                BottomBarIconConfig.setCustomIconUri(destination.name, Uri.fromFile(target).toString())
            }
        }
        BottomBarIconConfig.isEnabled = enabled
        BottomBarIconConfig.notifyChanged()
    }

    override suspend fun reset(context: Context) { BottomBarIconConfig.resetAll() }
}
