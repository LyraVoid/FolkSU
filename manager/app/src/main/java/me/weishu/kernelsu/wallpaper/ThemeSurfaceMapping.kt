package me.weishu.kernelsu.wallpaper

import me.weishu.kernelsu.wallpaper.surface.SurfaceConfig
import me.weishu.kernelsu.wallpaper.surface.SurfaceDescriptor
import me.weishu.kernelsu.wallpaper.surface.SurfaceField
import me.weishu.kernelsu.wallpaper.surface.SurfaceRegistry
import me.weishu.kernelsu.wallpaper.surface.SurfaceStore
import me.weishu.kernelsu.wallpaper.surface.isToggle
import org.json.JSONObject

/** FP defaults and legacy aliases map onto the existing surface model, not another config store. */
internal object ThemeSurfaceMapping {
    fun read(descriptor: SurfaceDescriptor, json: JSONObject): SurfaceConfig {
        val defaults = SurfaceConfig()
        var parsed = defaults
        descriptor.fields.forEach { field ->
            if (field == SurfaceField.Image) return@forEach
            val legacyKey = descriptor.legacyThemeFields[field]
            if (field.isToggle) {
                val children = SurfaceRegistry.all.filter { it.parentId == descriptor.id }
                val inherited = if (field == SurfaceField.Enabled && children.isNotEmpty()) {
                    children.any { child ->
                        val key = SurfaceStore.key(child.id, SurfaceField.Enabled)
                        val legacy = child.legacyThemeFields[SurfaceField.Enabled]
                        json.optBoolean(key, legacy?.let { json.optBoolean(it) } ?: false)
                    }
                } else defaults.toggle(field)
                val fallback = legacyKey?.let { json.optBoolean(it, inherited) } ?: inherited
                parsed = parsed.withToggle(field, json.optBoolean(SurfaceStore.key(descriptor.id, field), fallback))
            } else {
                val default = when (field) {
                    SurfaceField.DayOpacity, SurfaceField.NightOpacity -> parsed.opacity
                    SurfaceField.DayDim, SurfaceField.NightDim -> parsed.dim
                    else -> defaults.scalar(field)
                }
                val fallback = legacyKey?.let { json.optDouble(it, default.toDouble()) } ?: default.toDouble()
                parsed = parsed.withScalar(field, json.optDouble(SurfaceStore.key(descriptor.id, field), fallback).toFloat())
            }
        }
        descriptor.flags.forEach { flag ->
            val fallback = descriptor.legacyThemeFlags[flag]?.let { json.optBoolean(it) } ?: false
            parsed = parsed.withFlag(flag, json.optBoolean(SurfaceStore.key(descriptor.id, flag), fallback))
        }
        return parsed
    }

    fun presenceKey(descriptor: SurfaceDescriptor): String? =
        descriptor.legacyThemeFields[SurfaceField.Image]
            ?: descriptor.legacyThemeFields[SurfaceField.Enabled]?.takeIf { descriptor.parentId != null }

    fun wantsImage(descriptor: SurfaceDescriptor, json: JSONObject, parsed: SurfaceConfig, hasFile: Boolean): Boolean =
        if (descriptor.parentId != null && json.has(SurfaceStore.key(descriptor.id, SurfaceField.Enabled))) {
            parsed.enabled
        } else ThemeResourcePolicy.wantsImage(json, presenceKey(descriptor), parsed.enabled, hasFile)
}
