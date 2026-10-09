package me.weishu.kernelsu.ui.component.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector
import me.weishu.kernelsu.R

/**
 * The top-level buckets the settings hub is split into.
 *
 * The hub itself only shows these as an icon grid; every actual option lives on the category's own
 * screen. [key] is the stable string carried by [me.weishu.kernelsu.ui.navigation3.Route.SettingsCategory]
 * so a category can be deep-linked without depending on enum ordinals.
 */
enum class SettingsCategory(
    val key: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    GENERAL("general", R.string.settings_category_general, Icons.Filled.Settings),
    APPEARANCE("appearance", R.string.settings_category_appearance, Icons.Filled.Brush),
    BEHAVIOR("behavior", R.string.settings_category_behavior, Icons.Filled.TouchApp),
    FUNCTION("function", R.string.settings_category_function, Icons.Filled.Handyman),
    SECURITY("security", R.string.settings_category_security, Icons.Filled.Security),
    ;

    companion object {
        fun fromKey(key: String): SettingsCategory? = entries.firstOrNull { it.key == key }
    }
}
