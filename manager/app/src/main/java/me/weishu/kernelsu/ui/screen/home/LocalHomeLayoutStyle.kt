package me.weishu.kernelsu.ui.screen.home

import androidx.compose.runtime.staticCompositionLocalOf
import me.weishu.kernelsu.data.model.HomeLayoutStyle

/**
 * The active home layout token, provided once at the top of the app so switching the preference
 * recomposes the home screen without a restart.
 */
val LocalHomeLayoutStyle = staticCompositionLocalOf { HomeLayoutStyle.DEFAULT }
