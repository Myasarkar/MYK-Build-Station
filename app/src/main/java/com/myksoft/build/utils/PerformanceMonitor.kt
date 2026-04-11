package com.myksoft.build.utils

import android.app.ActivityManager
import android.content.Context
import com.myksoft.build.logger.AppLogger
import kotlinx.coroutines.*

/**
 * MYK Build - Performance Monitor (Kaynak İzleme Servisi)
 * Derleme sırasında her 2 saniyede bir RAM ve işlemci durumunu ölçer.
 * Kritik seviyelerde "Safety Valve" (Güvenlik Sübabı) tetikler.
 */
class PerformanceMonitor(private val context: Context) {

    private var monitorJob: Job? = null
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /**
     * @param coroutineScope İzlemenin çalışacağı scope (genelde ViewModel scope)
     * @param onUpdate UI'ı güncellemek için callback (Kullanılan RAM MB, Toplam RAM MB)
     * @param onCriticalMemory RAM kritik seviyeye ulaştığında tetiklenecek callback
     */
    fun startMonitoring(
        coroutineScope: CoroutineScope,
        onUpdate: (usedRamMb: Long, totalRamMb: Long) -> Unit,
        onCriticalMemory: () -> Unit
    ) {
        if (monitorJob?.isActive == true) return

        AppLogger.log("PerfMonitor", "STARTED", "Performance monitoring initiated.")

        monitorJob = coroutineScope.launch(Dispatchers.IO) {
            while (isActive) {
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)

                val totalRamMb = memoryInfo.totalMem / (1024 * 1024)
                val availRamMb = memoryInfo.availMem / (1024 * 1024)
                val usedRamMb = totalRamMb - availRamMb

                // UI Güncellemesi (Main Thread'de yapılmalı)
                withContext(Dispatchers.Main) {
                    onUpdate(usedRamMb, totalRamMb)

                    // Safety Valve: Boş RAM %15'in altına düşerse veya sistem lowMemory bayrağı çekerse
                    val freeRamPercentage = availRamMb.toFloat() / totalRamMb.toFloat()
                    if (memoryInfo.lowMemory || freeRamPercentage < 0.15f) {
                        AppLogger.log("PerfMonitor", "WARNING", "CRITICAL MEMORY! Free: ${freeRamPercentage * 100}%")
                        onCriticalMemory()
                    }
                }

                // 2 saniyede bir ölçüm yap
                delay(2000)
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        AppLogger.log("PerfMonitor", "STOPPED", "Performance monitoring halted.")
    }
}
