package com.myksoft.build.core

import com.myksoft.build.logger.AppLogger
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.concurrent.thread

/**
 * MYK Build - Gradle Runner (ProcessBuilder Core)
 * Gömülü JDK ve Gradle'ı kullanarak Android içinde sanal bir terminal gibi derleme yapar.
 */
class GradleRunner {

    /**
     * @param projectDir Derlenecek projenin kök dizini
     * @param jdkDir Gömülü JDK'nın bulunduğu dizin
     * @param gradleDir Gömülü Gradle'ın bulunduğu dizin (Eğer gradlew kullanılmayacaksa)
     * @param onLog Terminal View'a anlık log basmak için callback
     * @param onResult Derleme bitince başarı durumunu dönen callback
     */
    fun runBuild(
        projectDir: File,
        jdkDir: File,
        gradleDir: File?,
        onLog: (String) -> Unit,
        onResult: (Boolean) -> Unit
    ) {
        AppLogger.log("GradleRunner", "STARTED", "Initializing build process...")

        // 1. Executable İzinlerini Ayarla (Android 16 SDK 36 Zorunluluğu)
        val javaBin = File(jdkDir, "bin/java")
        val gradlew = File(projectDir, "gradlew")

        if (javaBin.exists()) javaBin.setExecutable(true, false)
        if (gradlew.exists()) gradlew.setExecutable(true, false)

        // 2. ProcessBuilder Kurulumu
        val processBuilder = ProcessBuilder()
        processBuilder.directory(projectDir)

        // Komut: sh -c "./gradlew assembleDebug --offline --daemon --build-cache"
        // (İleride gradle.properties'den de okunabilir, şimdilik hardcoded)
        processBuilder.command("sh", "-c", "./gradlew assembleDebug --offline --daemon --build-cache")

        // 3. Environment (Çevre Değişkenleri) Ayarları
        val env = processBuilder.environment()
        env["JAVA_HOME"] = jdkDir.absolutePath
        
        // PATH değişkenine JDK ve Gradle bin klasörlerini ekle
        val currentPath = env["PATH"] ?: ""
        val newPath = "${jdkDir.absolutePath}/bin:${gradleDir?.absolutePath ?: ""}/bin:$currentPath"
        env["PATH"] = newPath

        // Gradle'a özel JVM argümanları (4GB RAM tahsisi)
        env["GRADLE_OPTS"] = "-Xmx4096m -Dorg.gradle.jvmargs='-Xmx4096m'"

        AppLogger.log("GradleRunner", "ENV_SETUP", "JAVA_HOME=${env["JAVA_HOME"]}")

        try {
            val process = processBuilder.start()

            // 4. Stream Listener (Logları anlık okuma)
            // Standart çıktıyı (stdout) oku
            thread {
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            onLog(it)
                            // AppLogger.log("GradleRunner", "STDOUT", it) // Çok yoğun log olmaması için AppLogger'a atmayabiliriz
                        }
                    }
                }
            }

            // Hata çıktısını (stderr) oku
            thread {
                BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            onLog("[ERROR] $it")
                            AppLogger.log("GradleRunner", "STDERR", it)
                        }
                    }
                }
            }

            // 5. İşlemin bitmesini bekle
            val exitCode = process.waitFor()
            val isSuccess = exitCode == 0

            if (isSuccess) {
                AppLogger.log("GradleRunner", "SUCCESS", "Build finished with exit code 0")
            } else {
                AppLogger.log("GradleRunner", "FAILED", "Build failed with exit code $exitCode")
            }

            onResult(isSuccess)

        } catch (e: Exception) {
            AppLogger.e("GradleRunner", "CRITICAL_ERROR", e)
            onLog("[CRITICAL ERROR] ${e.message}")
            onResult(false)
        }
    }
}
