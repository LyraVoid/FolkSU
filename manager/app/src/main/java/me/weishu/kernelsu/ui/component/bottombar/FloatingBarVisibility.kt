package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Visibility state for the floating bottom bar.
 *
 * Mirrors FolkPatch: an optional three second auto-hide timer plus an optional
 * hide-while-scrolling-down behavior. Both are driven by [FloatingBarConfig] and default to on.
 */
@Stable
class FloatingBarVisibilityState internal constructor() {

    /** Whether the content is currently being scrolled downwards. */
    var isScrollingDown by mutableStateOf(false)
        private set

    /** Whether the bar should currently be shown when the auto-hide timer is armed. */
    var visible by mutableStateOf(true)
        private set

    /** Bumped on every interaction so the auto-hide timer restarts from scratch. */
    var autoHideKey by mutableIntStateOf(0)
        private set

    private var scrollOffset by mutableFloatStateOf(0f)
    private var previousScrollOffset by mutableFloatStateOf(0f)

    /** Watches content scrolling to flip [isScrollingDown] and restart the auto-hide timer. */
    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val delta = available.y
            if (delta != 0f) {
                onUserInteraction()
                val newOffset = scrollOffset + delta
                scrollOffset = newOffset
                val scrollDelta = previousScrollOffset - newOffset
                if (abs(scrollDelta) > THRESHOLD) {
                    isScrollingDown = scrollDelta > 0
                    previousScrollOffset = newOffset
                }
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            previousScrollOffset = scrollOffset
            return super.onPostFling(consumed, available)
        }
    }

    /** The user touched the bar or scrolled the content: show the bar and re-arm the timer. */
    fun onUserInteraction() {
        visible = true
        autoHideKey++
    }

    /** Force a fully visible idle state, e.g. when the app returns to the foreground. */
    fun reset() {
        onUserInteraction()
        isScrollingDown = false
        scrollOffset = 0f
        previousScrollOffset = 0f
    }

    /** Hide the bar without changing the armed state; used by the auto-hide timer. */
    internal fun hide() {
        visible = false
    }

    private companion object {
        const val THRESHOLD = 50f
    }
}

@Composable
fun rememberFloatingBarVisibilityState(
    enabled: Boolean,
    autoHide: Boolean,
    swipeHide: Boolean,
): FloatingBarVisibilityState {
    val state = remember { FloatingBarVisibilityState() }
    LaunchedEffect(enabled, autoHide, state.autoHideKey) {
        if (enabled && autoHide && state.visible) {
            delay(AUTO_HIDE_DELAY_MS)
            state.hide()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (enabled && (autoHide || swipeHide)) {
            state.reset()
        }
    }
    return state
}

private const val AUTO_HIDE_DELAY_MS = 3_000L
