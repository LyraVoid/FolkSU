package me.weishu.kernelsu.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.PaletteStyle
import me.weishu.kernelsu.ui.webui.MonetColorsProvider
import me.weishu.kernelsu.wallpaper.LocalFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.LocalWallpaperDim
import me.weishu.kernelsu.wallpaper.WallpaperConfig
import me.weishu.kernelsu.wallpaper.adaptColorScheme
import me.weishu.kernelsu.wallpaper.guardedDim
import me.weishu.kernelsu.wallpaper.resolveFolkWallpaperTokens
import me.weishu.kernelsu.wallpaper.useDarkNeutral

// Default dark ripple alpha (~10% pressed) is nearly invisible on near-black surfaces, so boost
// it for a clear press feedback at night. Light mode keeps Compose's default.
private val DarkRippleAlpha = RippleAlpha(
    draggedAlpha = 0.32f,
    focusedAlpha = 0.24f,
    hoveredAlpha = 0.16f,
    pressedAlpha = 0.24f,
)

@Composable
fun MaterialKernelSUTheme(
    appSettings: AppSettings,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = appSettings.colorMode.isDark || (appSettings.colorMode.isSystem && systemDarkTheme)
    val amoledMode = appSettings.colorMode.isAmoled
    val dynamicColor = appSettings.keyColor == 0

    val wallpaperActive = WallpaperConfig.isActive
    val wallpaperSeed = if (
        wallpaperActive && WallpaperConfig.useWallpaperColor && WallpaperConfig.derivedSeed != 0
    ) {
        Color(WallpaperConfig.derivedSeed)
    } else {
        Color.Unspecified
    }
    val baseSeed = when {
        wallpaperSeed != Color.Unspecified -> wallpaperSeed
        dynamicColor -> Color.Unspecified
        else -> Color(appSettings.keyColor)
    }

    val colorScheme = rememberKernelSUColorScheme(
        seedColor = baseSeed,
        isDark = darkTheme,
        isAmoled = amoledMode,
        paletteStyle = appSettings.paletteStyle,
        colorSpec = appSettings.colorSpec,
    )

    // In wallpaper mode the neutral roles follow the wallpaper's effective brightness so text stays
    // legible; a dark theme never flips to light, readability there is left to the night dim.
    val wallpaperDim = WallpaperConfig.effectiveDim(darkTheme)
    val darkNeutral = useDarkNeutral(darkTheme, WallpaperConfig.derivedLuminance, wallpaperDim)
    val neutralScheme = rememberKernelSUColorScheme(
        seedColor = baseSeed,
        isDark = darkNeutral,
        isAmoled = false,
        paletteStyle = PaletteStyle.Neutral,
        colorSpec = appSettings.colorSpec,
    )
    val chosenNeutral = if (wallpaperActive && darkNeutral != darkTheme) neutralScheme else colorScheme
    val adaptedColorScheme = adaptColorScheme(
        base = colorScheme,
        neutral = chosenNeutral,
        active = wallpaperActive,
    )
    val wallpaperTokens = if (wallpaperActive) {
        resolveFolkWallpaperTokens(adaptedColorScheme, WallpaperConfig.opacity)
    } else {
        null
    }
    val renderDim = if (wallpaperActive && darkNeutral && !darkTheme) {
        guardedDim(wallpaperDim, adaptedColorScheme) ?: wallpaperDim
    } else {
        wallpaperDim
    }.takeIf { wallpaperActive }

    // In wallpaper mode the top of the screen is the wallpaper itself, so the system-bar icon
    // brightness must follow the adapted content brightness, not just the theme mode.
    val systemBarDark = darkTheme || (wallpaperActive && darkNeutral)

    LaunchedEffect(systemBarDark) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !systemBarDark
            isAppearanceLightNavigationBars = !systemBarDark
        }
    }

    val animatedColorScheme = adaptedColorScheme.animateAsState()

    MaterialExpressiveTheme(
        colorScheme = animatedColorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = remember { getTypography(FontFamily.Default) },
        shapes = FolkShape.materialShapes,
        content = {
            // alpha28 has no non-deprecated way to override the ripple alpha, so the constructor is
            // suppressed rather than the alpha left at a value that disappears on near-black.
            @Suppress("DEPRECATION")
            val rippleConfiguration = if (darkTheme) {
                RippleConfiguration(rippleAlpha = DarkRippleAlpha)
            } else {
                LocalRippleConfiguration.current
            }
            CompositionLocalProvider(
                LocalRippleConfiguration provides rippleConfiguration,
                LocalWallpaperDim provides renderDim,
                LocalFolkWallpaperTokens provides wallpaperTokens,
            ) {
                MonetColorsProvider.UpdateCss(colorScheme)
                content()
            }
        }
    )
}
