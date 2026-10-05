package me.weishu.kernelsu.ui.viewmodel

import androidx.compose.runtime.Immutable
import me.weishu.kernelsu.ui.theme.AppSettings

@Immutable
data class MainActivityUiState(
    val appSettings: AppSettings,
    val pageScale: Float,
    val enableNavigationBadge: Boolean,
    val enableSwipeDismiss: Boolean,
    val pagerInterceptionMode: Int,
    val moduleDescriptionMaxLines: Int = 4,
)
