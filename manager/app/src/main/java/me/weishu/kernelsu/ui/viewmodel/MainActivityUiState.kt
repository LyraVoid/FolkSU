package me.weishu.kernelsu.ui.viewmodel

import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.data.model.HomeLayoutStyle
import me.weishu.kernelsu.ui.theme.AppSettings

@Immutable
data class MainActivityUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableNavigationBadge: Boolean,
    val moduleDescriptionMaxLines: Int = 4,
    val homeLayoutStyle: String = HomeLayoutStyle.DEFAULT,
)
