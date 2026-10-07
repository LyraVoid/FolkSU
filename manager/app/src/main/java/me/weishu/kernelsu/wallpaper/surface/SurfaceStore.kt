package me.weishu.kernelsu.wallpaper.surface

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.edit
import me.weishu.kernelsu.ksuApp

/**
 * Persistence for [SurfaceConfig]s, keyed by [SurfaceId].
 *
 * v1 keeps every surface independent: booleans and scalars live under `surface.<id>.<field>` so
 * adding a UI never touches a global object again. The first load migrates the historical keys a
 * surface used before the registry existed (the grid work card's `work_card_*` keys) and then
 * writes the new keys; the legacy entries are left in place so an older build can still read them.
 */
object SurfaceStore {

    private const val PREFS_NAME = "wallpaper_surfaces"
    private const val LEGACY_PREFS_NAME = "wallpaper"
    private const val KEY_PREFIX = "surface"

    private val configs = mutableStateMapOf<SurfaceId, SurfaceConfig>()

    /** The current values of [id]; an unwritten surface falls back to the type defaults. */
    fun config(id: SurfaceId): SurfaceConfig = configs[id] ?: SurfaceConfig()

    /** Applies [transform] to [id] in memory. Call [save] to persist. */
    fun update(id: SurfaceId, transform: (SurfaceConfig) -> SurfaceConfig) {
        configs[id] = transform(config(id))
    }

    /** Loads every registered surface, migrating legacy keys on first run. */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val legacy = context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        SurfaceRegistry.all.forEach { descriptor ->
            if (descriptor.fields.isEmpty() && descriptor.flags.isEmpty()) return@forEach
            if (prefs.contains(markerKey(descriptor.id))) {
                configs[descriptor.id] = readNew(prefs, descriptor)
            } else {
                val config = readMigrating(prefs, legacy, descriptor)
                configs[descriptor.id] = config
                write(prefs, descriptor, config)
                prefs.edit { putBoolean(markerKey(descriptor.id), true) }
            }
        }
    }

    /** Persists every loaded surface. */
    fun save(context: Context = ksuApp) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        SurfaceRegistry.all.forEach { descriptor ->
            if (descriptor.fields.isEmpty() && descriptor.flags.isEmpty()) return@forEach
            val config = configs[descriptor.id] ?: return@forEach
            write(prefs, descriptor, config)
        }
    }

    /** Drops the in-memory values, so the next read sees the defaults again. */
    fun reset() {
        configs.clear()
    }

    /** Storage key for [field] in [id]; also used as the theme json key. */
    fun key(id: SurfaceId, field: SurfaceField): String =
        "$KEY_PREFIX.${id.value}.${field.storageKey}"

    /** Storage key for [flag] in [id]; also used as the theme json key. */
    fun key(id: SurfaceId, flag: SurfaceFlag): String =
        "$KEY_PREFIX.${id.value}.flag.${flag.storageKey}"

    private fun markerKey(id: SurfaceId) = "$KEY_PREFIX.${id.value}.migrated"

    private fun readNew(prefs: SharedPreferences, d: SurfaceDescriptor): SurfaceConfig {
        val id = d.id
        return SurfaceConfig(
            imageUri = prefs.getString(key(id, SurfaceField.Image), null),
            enabled = prefs.getBoolean(key(id, SurfaceField.Enabled), false),
            opacity = prefs.getFloat(key(id, SurfaceField.Opacity), 1f),
            dualOpacity = prefs.getBoolean(key(id, SurfaceField.DualOpacity), false),
            dayOpacity = prefs.getFloat(key(id, SurfaceField.DayOpacity), 1f),
            nightOpacity = prefs.getFloat(key(id, SurfaceField.NightOpacity), 1f),
            dim = prefs.getFloat(key(id, SurfaceField.Dim), 0.3f),
            dualDim = prefs.getBoolean(key(id, SurfaceField.DualDim), false),
            dayDim = prefs.getFloat(key(id, SurfaceField.DayDim), 0.3f),
            nightDim = prefs.getFloat(key(id, SurfaceField.NightDim), 0.3f),
            flags = d.flags.filterTo(mutableSetOf()) { prefs.getBoolean(key(id, it), false) },
        )
    }

    /**
     * Reads [d] preferring the new keys and falling back to the descriptor's legacy keys, so a user
     * upgrading from before the registry keeps their work-card values.
     */
    private fun readMigrating(
        prefs: SharedPreferences,
        legacy: SharedPreferences,
        d: SurfaceDescriptor,
    ): SurfaceConfig {
        val id = d.id

        fun string(field: SurfaceField): String? {
            val k = key(id, field)
            if (prefs.contains(k)) return prefs.getString(k, null)
            return legacyKey(id, field)?.let { if (legacy.contains(it)) legacy.getString(it, null) else null }
        }
        fun boolean(field: SurfaceField, default: Boolean): Boolean {
            val k = key(id, field)
            if (prefs.contains(k)) return prefs.getBoolean(k, default)
            return legacyKey(id, field)?.let { legacy.getBoolean(it, default) } ?: default
        }
        fun float(field: SurfaceField, default: Float): Float {
            val k = key(id, field)
            if (prefs.contains(k)) return prefs.getFloat(k, default)
            return legacyKey(id, field)?.let { legacy.getFloat(it, default) } ?: default
        }
        fun flag(flag: SurfaceFlag): Boolean =
            legacyFlagKey(id, flag)?.let { legacy.getBoolean(it, false) } ?: false

        return SurfaceConfig(
            imageUri = string(SurfaceField.Image),
            enabled = boolean(SurfaceField.Enabled, false),
            opacity = float(SurfaceField.Opacity, 1f),
            dualOpacity = boolean(SurfaceField.DualOpacity, false),
            dayOpacity = float(SurfaceField.DayOpacity, 1f),
            nightOpacity = float(SurfaceField.NightOpacity, 1f),
            dim = float(SurfaceField.Dim, 0.3f),
            dualDim = boolean(SurfaceField.DualDim, false),
            dayDim = float(SurfaceField.DayDim, 0.3f),
            nightDim = float(SurfaceField.NightDim, 0.3f),
            flags = d.flags.filterTo(mutableSetOf()) { flag(it) },
        )
    }

    private fun write(prefs: SharedPreferences, d: SurfaceDescriptor, config: SurfaceConfig) {
        val id = d.id
        prefs.edit {
            if (SurfaceField.Image in d.fields) putString(key(id, SurfaceField.Image), config.imageUri)
            if (SurfaceField.Enabled in d.fields) putBoolean(key(id, SurfaceField.Enabled), config.enabled)
            if (SurfaceField.Opacity in d.fields) putFloat(key(id, SurfaceField.Opacity), config.opacity)
            if (SurfaceField.DualOpacity in d.fields) putBoolean(key(id, SurfaceField.DualOpacity), config.dualOpacity)
            if (SurfaceField.DayOpacity in d.fields) putFloat(key(id, SurfaceField.DayOpacity), config.dayOpacity)
            if (SurfaceField.NightOpacity in d.fields) putFloat(key(id, SurfaceField.NightOpacity), config.nightOpacity)
            if (SurfaceField.Dim in d.fields) putFloat(key(id, SurfaceField.Dim), config.dim)
            if (SurfaceField.DualDim in d.fields) putBoolean(key(id, SurfaceField.DualDim), config.dualDim)
            if (SurfaceField.DayDim in d.fields) putFloat(key(id, SurfaceField.DayDim), config.dayDim)
            if (SurfaceField.NightDim in d.fields) putFloat(key(id, SurfaceField.NightDim), config.nightDim)
            d.flags.forEach { putBoolean(key(id, it), config.hasFlag(it)) }
        }
    }

    /** The historical pre-registry key for [field] of [id], or null when it never had one. */
    private fun legacyKey(id: SurfaceId, field: SurfaceField): String? =
        if (id == SurfaceRegistry.GRID_WORK_CARD) {
            when (field) {
                SurfaceField.Image -> "work_card_background_uri"
                SurfaceField.Enabled -> "work_card_background_enabled"
                SurfaceField.Opacity -> "work_card_opacity"
                SurfaceField.Dim -> "work_card_dim"
                SurfaceField.DualOpacity -> "work_card_dual_opacity_enabled"
                SurfaceField.DayOpacity -> "work_card_day_opacity"
                SurfaceField.NightOpacity -> "work_card_night_opacity"
                else -> null
            }
        } else {
            null
        }

    private fun legacyFlagKey(id: SurfaceId, flag: SurfaceFlag): String? =
        if (id == SurfaceRegistry.GRID_WORK_CARD) {
            when (flag) {
                SurfaceFlag.HideIcon -> "work_card_check_hidden"
                SurfaceFlag.HideText -> "work_card_text_hidden"
                SurfaceFlag.HideMode -> "work_card_mode_hidden"
            }
        } else {
            null
        }

    private val SurfaceField.storageKey: String
        get() = when (this) {
            SurfaceField.Image -> "image"
            SurfaceField.Enabled -> "enabled"
            SurfaceField.Opacity -> "opacity"
            SurfaceField.DualOpacity -> "dual_opacity"
            SurfaceField.DayOpacity -> "day_opacity"
            SurfaceField.NightOpacity -> "night_opacity"
            SurfaceField.Dim -> "dim"
            SurfaceField.DualDim -> "dual_dim"
            SurfaceField.DayDim -> "day_dim"
            SurfaceField.NightDim -> "night_dim"
        }

    private val SurfaceFlag.storageKey: String
        get() = when (this) {
            SurfaceFlag.HideIcon -> "hide_icon"
            SurfaceFlag.HideText -> "hide_text"
            SurfaceFlag.HideMode -> "hide_mode"
        }
}
