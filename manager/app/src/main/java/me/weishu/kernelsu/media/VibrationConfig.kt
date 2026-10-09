package me.weishu.kernelsu.media

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

object VibrationConfig {
    private const val PREFS_NAME = "vibration_settings"
    private const val KEY_ENABLED = "vibration_enabled"
    private const val KEY_INTENSITY = "vibration_intensity"
    private const val KEY_SCOPE = "vibration_scope"

    const val SCOPE_GLOBAL = "global"
    const val SCOPE_BOTTOM_BAR = "bottom_bar"

    var isVibrationEnabled: Boolean by mutableStateOf(false)
        private set

    var vibrationIntensity: Float by mutableFloatStateOf(0.5f)
        private set

    var scope: String by mutableStateOf(SCOPE_GLOBAL)
        private set

    fun setEnabledState(enabled: Boolean) {
        isVibrationEnabled = enabled
    }

    fun setIntensityValue(value: Float) {
        vibrationIntensity = value.coerceIn(0f, 1f)
    }

    fun setScopeValue(value: String) {
        scope = if (value == SCOPE_BOTTOM_BAR) value else SCOPE_GLOBAL
    }

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isVibrationEnabled = prefs.getBoolean(KEY_ENABLED, false)
        setIntensityValue(prefs.getFloat(KEY_INTENSITY, 0.5f))
        setScopeValue(prefs.getString(KEY_SCOPE, SCOPE_GLOBAL) ?: SCOPE_GLOBAL)
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_ENABLED, isVibrationEnabled)
            .putFloat(KEY_INTENSITY, vibrationIntensity)
            .putString(KEY_SCOPE, scope)
            .apply()
    }
}
