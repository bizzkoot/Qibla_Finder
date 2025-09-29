package com.bizzkoot.qiblafinder.ui.location

import android.content.Context
import android.content.SharedPreferences

data class ManualLocationCacheConfig(
    val limitMb: Int
)

class ManualLocationPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastMapType(): MapType {
        val stored = prefs.getString(KEY_LAST_MAP_TYPE, MapType.STREET.name)
        return runCatching { MapType.valueOf(stored ?: MapType.STREET.name) }
            .getOrElse { MapType.STREET }
    }

    fun setLastMapType(mapType: MapType) {
        prefs.edit().putString(KEY_LAST_MAP_TYPE, mapType.name).apply()
    }

    fun getCacheConfig(): ManualLocationCacheConfig {
        val limit = prefs.getInt(KEY_CACHE_LIMIT_MB, DEFAULT_CACHE_LIMIT_MB)
        return ManualLocationCacheConfig(limitMb = limit)
    }

    fun setCacheLimitMb(limitMb: Int) {
        val safeLimit = limitMb.coerceIn(MIN_CACHE_LIMIT_MB, MAX_CACHE_LIMIT_MB)
        prefs.edit().putInt(KEY_CACHE_LIMIT_MB, safeLimit).apply()
    }

    private companion object {
        private const val PREFS_NAME = "manual_location_prefs"
        private const val KEY_LAST_MAP_TYPE = "last_map_type"
        private const val KEY_CACHE_LIMIT_MB = "cache_limit_mb"
        private const val DEFAULT_CACHE_LIMIT_MB = 60
        private const val MIN_CACHE_LIMIT_MB = 30
        private const val MAX_CACHE_LIMIT_MB = 120
    }
}
