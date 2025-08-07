package com.bizzkoot.qiblafinder

import android.app.Application
import timber.log.Timber

class QiblaFinderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Check if we're in debug mode using application info flags
        val isDebuggable = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (isDebuggable) {
            Timber.plant(Timber.DebugTree())
        }
    }
}