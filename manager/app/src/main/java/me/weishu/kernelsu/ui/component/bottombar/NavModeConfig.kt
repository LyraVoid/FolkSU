package me.weishu.kernelsu.ui.component.bottombar

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.weishu.kernelsu.ksuApp

/**
 * Navigation form, aligned with the FolkPatch `nav_mode` preference.
 *
 * [Auto] follows the window width size class (Medium and Expanded get the side rail), while
 * the other values force a form. This replaces the former `floating_bar_enabled` flag.
 */
enum class NavMode {
    Auto, Bottom, Rail, Floating;

    val storageValue: String get() = name.lowercase()

    companion object {
        fun fromValue(value: String?): NavMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: Auto
    }
}

/**
 * Persisted navigation mode. [revision] bumps on every write so the navigation chrome
 * recomposes immediately.
 */
object NavModeConfig {

    private const val PREFS = "settings"
    private const val KEY_MODE = "nav_mode"
    private const val KEY_LEGACY_FLOATING = "floating_bar_enabled"

    private val prefs get() = ksuApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    var mode: NavMode
        get() {
            prefs.getString(KEY_MODE, null)?.let { return NavMode.fromValue(it) }
            // One-time migration: `floating_bar_enabled` never shipped, so only an explicit
            // value (dev devices) is honored; a fresh install defaults to Floating, like FolkPatch.
            return if (prefs.contains(KEY_LEGACY_FLOATING)) {
                if (prefs.getBoolean(KEY_LEGACY_FLOATING, false)) NavMode.Floating else NavMode.Auto
            } else {
                NavMode.Floating
            }
        }
        set(value) {
            prefs.edit { putString(KEY_MODE, value.storageValue) }
            _revision.value++
        }
}
