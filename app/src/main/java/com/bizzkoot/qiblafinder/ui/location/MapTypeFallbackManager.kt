package com.bizzkoot.qiblafinder.ui.location

import timber.log.Timber

class MapTypeFallbackManager {
    companion object {
        fun getFallbackMapType(originalMapType: MapType, error: Exception): MapType {
            return when (originalMapType) {
                MapType.SATELLITE -> {
                    Timber.w("📍 Satellite map failed, falling back to street map: ${error.message}")
                    MapType.STREET
                }
                MapType.STREET -> {
                    Timber.e("📍 Street map failed, this is unexpected: ${error.message}")
                    MapType.STREET // Keep as is, let error handling take over
                }
            }
        }
    }
}
