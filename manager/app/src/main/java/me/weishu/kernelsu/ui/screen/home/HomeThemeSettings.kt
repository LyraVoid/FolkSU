package me.weishu.kernelsu.ui.screen.home

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl

internal data class HomeThemeSettings(val statsTopLayout: String, val listWorkingCardModeHidden: Boolean)

@Composable
internal fun rememberHomeThemeSettings(): HomeThemeSettings {
    val context = LocalContext.current.applicationContext
    val repository = remember { SettingsRepositoryImpl() }
    fun read() = HomeThemeSettings(repository.statsTopLayout, repository.listWorkingCardModeHidden)
    var settings by remember { mutableStateOf(read()) }
    DisposableEffect(context) {
        val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == "stats_top_layout" || key == "list_working_card_mode_hidden") settings = read()
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return settings
}
