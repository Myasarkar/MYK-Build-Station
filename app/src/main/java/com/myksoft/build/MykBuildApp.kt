package com.myksoft.build

import android.app.Application
import com.myksoft.build.core.GlobalCrashHandler
import com.myksoft.build.logger.AppLogger

/**
 * MYK Build - Application Class
 * Uygulama ayağa kalkarken kritik servisleri başlatır.
 */
class MykBuildApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // 1. Global Crash Handler'ı başlat
        GlobalCrashHandler.init(this)
        
        // 2. Logger'ı başlat ve ilk logu at
        AppLogger.log("Application", "STARTED", "MYK Build Engine Initialized")
    }
}
