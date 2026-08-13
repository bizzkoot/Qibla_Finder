package com.bizzkoot.qiblafinder.ui.compass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Keeps the device screen on while this composable is composed and the window is
 * resumed, using the permissionless window flag `FLAG_KEEP_SCREEN_ON`
 * (via [android.view.View.setKeepScreenOn]) — NOT a WakeLock.
 *
 * Lifecycle behavior:
 * - Entering the screen while enabled sets the flag (only if already RESUMED).
 * - ON_PAUSE clears the flag (the app is backgrounded — never hold the screen there).
 * - ON_RESUME restores it if still enabled.
 * - Disposal (navigating away) restores the previously-held flag value so we never
 *   clobber a flag another route may have set on the same window.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    DisposableEffect(enabled, lifecycleOwner) {
        val previous = view.keepScreenOn // capture before we set it
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.keepScreenOn = enabled
                Lifecycle.Event.ON_PAUSE -> view.keepScreenOn = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Apply initial state only while RESUMED (the flag is a no-op otherwise)
        view.keepScreenOn =
            enabled && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        onDispose {
            view.keepScreenOn = previous // restore prior value; don't clobber a shared window flag
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
