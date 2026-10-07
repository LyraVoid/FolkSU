package me.weishu.kernelsu.ui.component.bottombar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.weishu.kernelsu.ksuApp
import java.io.File
import java.io.FileOutputStream

/**
 * Custom icon overrides for the bottom navigation destinations.
 *
 * Each destination keeps at most one picked image, copied into internal storage
 * (`filesDir/nav_icon_<Dest>.png`) so it survives restarts and can be bundled into a `.fpt`
 * theme package. When custom icons are disabled — or a destination has no icon — the default
 * Material vector is used.
 *
 * [revision] ticks on every change so the navigation bars recompose immediately.
 */
object BottomBarIconConfig {
    private const val PREF_PREFIX = "nav_icon_"
    const val ENABLED_KEY = "nav_icon_custom_enabled"
    private const val ICON_SIZE = 128

    private val prefs get() = ksuApp.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun notifyChanged() {
        _revision.value++
    }

    var isEnabled: Boolean
        get() = prefs.getBoolean(ENABLED_KEY, false)
        set(value) {
            prefs.edit { putBoolean(ENABLED_KEY, value) }
            notifyChanged()
        }

    fun getCustomIconUri(destinationName: String): String? =
        prefs.getString(PREF_PREFIX + destinationName, null)

    fun setCustomIconUri(destinationName: String, uri: String?) {
        prefs.edit {
            if (uri != null) putString(PREF_PREFIX + destinationName, uri)
            else remove(PREF_PREFIX + destinationName)
        }
        notifyChanged()
    }

    fun hasCustomIcon(destinationName: String): Boolean = getCustomIconUri(destinationName) != null

    fun iconFile(destinationName: String): File =
        File(ksuApp.filesDir, "nav_icon_$destinationName.png")

    /**
     * Decode [uriString] into a square icon bitmap (center-cropped, downscaled to [targetSize]).
     * Returns null when the image cannot be read.
     */
    fun loadIconBitmap(uriString: String?, targetSize: Int = ICON_SIZE): Bitmap? {
        if (uriString.isNullOrEmpty()) return null
        val uri = Uri.parse(uriString)
        val resolver = ksuApp.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(width, height, targetSize)
            }
            val decoded = resolver.openInputStream(uri)
                ?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null
            centerCropSquare(decoded, targetSize)
        } catch (_: Throwable) {
            null
        }
    }

    /** Copy [sourceUri] into internal storage and mark it as the icon for [destinationName]. */
    fun saveCustomIcon(context: Context, destinationName: String, sourceUri: Uri): Boolean {
        return try {
            val bitmap = loadIconBitmap(sourceUri.toString()) ?: return false
            val target = iconFile(destinationName)
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            setCustomIconUri(destinationName, Uri.fromFile(target).toString())
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun clearCustomIcon(destinationName: String) {
        setCustomIconUri(destinationName, null)
        iconFile(destinationName).takeIf { it.exists() }?.delete()
    }

    fun resetAll() {
        prefs.edit {
            BottomBarDestination.entries.forEach { remove(PREF_PREFIX + it.name) }
            putBoolean(ENABLED_KEY, false)
        }
        BottomBarDestination.entries.forEach { dest ->
            iconFile(dest.name).takeIf { it.exists() }?.delete()
        }
        notifyChanged()
    }

    private fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
        var sample = 1
        val minSide = minOf(width, height)
        while (minSide / (sample * 2) >= target) sample *= 2
        return sample
    }

    private fun centerCropSquare(source: Bitmap, size: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val x = (source.width - side) / 2
        val y = (source.height - side) / 2
        val cropped = if (x == 0 && y == 0 && side == source.width) {
            source
        } else {
            Bitmap.createBitmap(source, x, y, side, side)
        }
        return if (cropped.width == size) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, size, size, true)
        }
    }
}
