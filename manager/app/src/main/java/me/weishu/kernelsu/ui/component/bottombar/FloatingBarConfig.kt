package me.weishu.kernelsu.ui.component.bottombar

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.weishu.kernelsu.ksuApp

/**
 * Persisted appearance for the optional floating bottom navigation bar.
 *
 * Stored in the shared `settings` preferences used by the rest of the app so existing
 * observers keep working. [revision] bumps on every write so the bars recompose immediately.
 */
object FloatingBarConfig {

    private const val PREFS = "settings"
    private const val KEY_STYLE = "floating_bar_style"
    private const val KEY_COMPACT = "floating_bar_compact"
    private const val KEY_GLASS = "floating_bar_glass"
    private const val KEY_AUTO_HIDE = "floating_auto_hide"
    private const val KEY_SWIPE_HIDE = "floating_swipe_hide"

    private const val STYLE_STANDARD = "standard"
    private const val STYLE_DRAWER = "drawer"

    /** The two floating forms: a compact capsule, or a drawer whose selected item expands. */
    enum class Style { Standard, Drawer }

    private val prefs get() = ksuApp.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private fun notifyChanged() {
        _revision.value++
    }

    /** Which floating form to use. */
    var style: Style
        get() = if (prefs.getString(KEY_STYLE, STYLE_DRAWER) == STYLE_STANDARD) Style.Standard else Style.Drawer
        set(value) {
            prefs.edit { putString(KEY_STYLE, if (value == Style.Standard) STYLE_STANDARD else STYLE_DRAWER) }
            notifyChanged()
        }

    /** Standard form only: use a fully-rounded capsule instead of a large-corner rectangle. */
    var compact: Boolean
        get() = prefs.getBoolean(KEY_COMPACT, true)
        set(value) {
            prefs.edit { putBoolean(KEY_COMPACT, value) }
            notifyChanged()
        }

    /** Draw a frosted-glass (real-time blur) surface behind the bar on Android 13+. */
    var glass: Boolean
        get() = prefs.getBoolean(KEY_GLASS, false)
        set(value) {
            prefs.edit { putBoolean(KEY_GLASS, value) }
            notifyChanged()
        }

    /** Hide the bar three seconds after the last interaction, until the user touches it again. */
    var autoHide: Boolean
        get() = prefs.getBoolean(KEY_AUTO_HIDE, true)
        set(value) {
            prefs.edit { putBoolean(KEY_AUTO_HIDE, value) }
            notifyChanged()
        }

    /** Hide the bar while the content scrolls down and reveal it while it scrolls up. */
    var swipeHide: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_HIDE, true)
        set(value) {
            prefs.edit { putBoolean(KEY_SWIPE_HIDE, value) }
            notifyChanged()
        }
}
