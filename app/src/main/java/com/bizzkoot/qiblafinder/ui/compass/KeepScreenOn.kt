package com.bizzkoot.qiblafinder.ui.compass

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
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
 * - Power-save mode (PRD §3.5 mitigation): while the device is in battery-saver
 *   mode the flag is never applied, regardless of the toggle — the display is the
 *   largest battery consumer, so keep-screen-on is suppressed there. The flag
 *   re-applies automatically when power-save mode turns off.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

    DisposableEffect(enabled, lifecycleOwner) {
        val previous = view.keepScreenOn // capture before we set it
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        // Re-applies the flag whenever lifecycle or power-save state changes.
        fun applyKeepScreenOn() {
            val resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            view.keepScreenOn = enabled && resumed && !powerManager.isPowerSaveMode
        }

        val powerSaveReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                applyKeepScreenOn()
            }
        }
        ContextCompat.registerReceiver(
            context,
            powerSaveReceiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> applyKeepScreenOn()
                Lifecycle.Event.ON_PAUSE -> view.keepScreenOn = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Apply initial state only while RESUMED (the flag is a no-op otherwise)
        applyKeepScreenOn()
        onDispose {
            view.keepScreenOn = previous // restore prior value; don't clobber a shared window flag
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(powerSaveReceiver)
        }
    }
}
