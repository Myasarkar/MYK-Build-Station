package com.myksoft.build.logger

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MYK Build - Internal Logger
 * Tüm kritik süreçleri (Zip, Process, Permission) zamansal olarak kaydeder.
 * Format: [SAAT] [İŞLEM] [DURUM] Detaylar
 */
object AppLogger {
    private const val TAG = "MYKBuild"
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.ENGLISH)

    // Bellek içi logları Terminal View'da göstermek için tutabiliriz
    private val inMemoryLogs = mutableListOf<String>()

    fun log(action: String, status: String, details: String? = null) {
        val time = dateFormat.format(Date())
        val logMessage = "[$time] [$action] [$status] ${details ?: ""}".trim()
        
        Log.d(TAG, logMessage)
        synchronized(inMemoryLogs) {
            inMemoryLogs.add(logMessage)
            // Bellek şişmesini önlemek için son 1000 logu tut
            if (inMemoryLogs.size > 1000) {
                inMemoryLogs.removeAt(0)
            }
        }
    }

    fun e(action: String, status: String, error: Throwable) {
        val time = dateFormat.format(Date())
        val logMessage = "[$time] [$action] [$status] ${error.message}"
        
        Log.e(TAG, logMessage, error)
        synchronized(inMemoryLogs) {
            inMemoryLogs.add("$logMessage\n${Log.getStackTraceString(error)}")
        }
    }

    fun getLogs(): List<String> {
        return synchronized(inMemoryLogs) {
            inMemoryLogs.toList()
        }
    }
}
