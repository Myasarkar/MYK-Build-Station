package com.myksoft.build.core

import android.content.Context
import android.content.Intent
import com.myksoft.build.logger.AppLogger
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * MYK Build - Global Crash Handler
 * Beklenmedik çökmeleri yakalar, loglar ve kullanıcıya CrashActivity üzerinden teknik döküm sunar.
 */
class GlobalCrashHandler private constructor(private val context: Context) : Thread.UncaughtExceptionHandler {

    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        val stringWriter = StringWriter()
        exception.printStackTrace(PrintWriter(stringWriter))
        val stackTrace = stringWriter.toString()

        AppLogger.e("GlobalCrash", "FATAL", exception)

        // CrashActivity'yi başlat
        val intent = Intent(context, CrashActivity::class.java).apply {
            putExtra("CRASH_REPORT", stackTrace)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)

        // Uygulamayı güvenli bir şekilde sonlandır
        exitProcess(1)
    }

    companion object {
        fun init(context: Context) {
            Thread.setDefaultUncaughtExceptionHandler(GlobalCrashHandler(context.applicationContext))
        }
    }
}
