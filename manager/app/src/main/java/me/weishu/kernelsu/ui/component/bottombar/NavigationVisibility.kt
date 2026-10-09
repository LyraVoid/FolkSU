package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Navigation-mode signals the floating bar exposes to page content, mirroring FolkPatch's
 * `NavigationVisibilityLocals`. A page's own floating elements (e.g. the module FAB) read them to
 * ride with the bar instead of guessing its position.
 *
 * All three default to the non-floating case, so any screen rendered outside the floating branch
 * (rail, traditional bottom bar) behaves as if there were no floating bar.
 */

/** Whether the floating navigation bar is the active navigation mode. */
val LocalIsFloatingNavMode = compositionLocalOf { false }

/** Whether the floating navigation bar is currently on screen; it animates with auto-hide/swipe-hide. */
val LocalFloatingBarVisible = compositionLocalOf { true }

/** Vertical space the floating bar reserves at the bottom, or 0.dp in every other nav mode. */
val LocalFloatingBarReservedHeight = compositionLocalOf { 0.dp }
