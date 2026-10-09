package me.weishu.kernelsu.media

import android.content.Context
import me.weishu.kernelsu.ksuApp

/** Called only for navigation-control selections, not swipes or programmatic page changes. */
object MediaFeedback {
    fun navigation(context: Context = ksuApp) {
        if (SoundEffectConfig.scope == SoundEffectConfig.SCOPE_BOTTOM_BAR) SoundEffectManager.play(context)
        if (VibrationConfig.scope == VibrationConfig.SCOPE_BOTTOM_BAR) VibrationManager.vibrate(context)
    }

    fun touch(context: Context) {
        if (SoundEffectConfig.scope == SoundEffectConfig.SCOPE_GLOBAL) SoundEffectManager.play(context)
        if (VibrationConfig.scope == VibrationConfig.SCOPE_GLOBAL) VibrationManager.vibrate(context)
    }
}
