package me.weishu.kernelsu.media

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

object VibrationManager {

    fun vibrate(context: Context) {
        if (!VibrationConfig.isVibrationEnabled) return
        if (VibrationConfig.vibrationIntensity <= 0f) return

        try {
            val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return

            if (vibrator.hasVibrator()) {
                // Map 0.0-1.0 to 1-255
                val intensity = (VibrationConfig.vibrationIntensity * 255).toInt().coerceIn(1, 255)
                // A short duration for a "tick" or "click" feel
                val duration = 30L

                val amplitude = if (vibrator.hasAmplitudeControl()) intensity else VibrationEffect.DEFAULT_AMPLITUDE
                vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            }
        } catch (e: Exception) {
            // Catch all exceptions including SecurityException to prevent crash
            android.util.Log.e("VibrationManager", "Failed to vibrate", e)
        }
    }
}
