package com.bizzkoot.qiblafinder.ui.compass

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Persistence for compass-screen preferences.
 *
 * Follows the established [com.bizzkoot.qiblafinder.ui.location.ManualLocationPreferences]
 * idiom: a dedicated SharedPreferences file with typed getters/setters.
 *
 * NOTE: the `keep_screen_on` key is intentionally stable — a future Settings screen
 * should read/write the same key so the in-compass toggle and Settings stay in sync.
 */
class CompassPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getKeepScreenOn(): Boolean = prefs.getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON)

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_KEEP_SCREEN_ON, enabled) }
    }

    private companion object {
        const val PREFS_NAME = "compass_prefs"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val DEFAULT_KEEP_SCREEN_ON = true
    }
}
