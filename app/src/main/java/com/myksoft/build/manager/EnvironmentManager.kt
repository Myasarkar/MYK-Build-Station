package com.myksoft.build.manager

import android.content.Context
import com.myksoft.build.logger.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * MYK Build - Environment Manager (InitializationService)
 * Uygulama ilk açıldığında internetten JDK ve Gradle paketlerini indirip Internal Storage'a çıkartır.
 */
class EnvironmentManager(private val context: Context) {

    private val envDir = File(context.filesDir, "core_env")
    private val jdkDir = File(envDir, "jdk")
    private val gradleDir = File(envDir, "gradle")

    suspend fun checkAndSetupEnvironment(
        onProgress: (String, Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!envDir.exists()) envDir.mkdirs()

            if (!isJdkReady()) {
                val jdkUrl = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.10%2B7/OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.10_7.tar.gz"
                val jdkArchive = File(envDir, "jdk.tar.gz")
                downloadFile(jdkUrl, jdkArchive) { progress ->
                    onProgress("Downloading JDK 17 (ARM64)...", progress)
                }
                onProgress("Extracting JDK...", 100)
                extractTarGz(jdkArchive, jdkDir)
                jdkArchive.delete()
            }

            if (!isGradleReady()) {
                val gradleUrl = "https://services.gradle.org/distributions/gradle-8.10.2-bin.zip"
                val gradleArchive = File(envDir, "gradle.zip")
                downloadFile(gradleUrl, gradleArchive) { progress ->
                    onProgress("Downloading Gradle 8.10.2...", progress)
                }
                onProgress("Extracting Gradle...", 100)
                extractZip(gradleArchive, gradleDir)
                gradleArchive.delete()
            }

            true
        } catch (e: Exception) {
            AppLogger.e("EnvManager", "SETUP_FAILED", e)
            false
        }
    }

    private fun isJdkReady(): Boolean {
        return jdkDir.exists() && jdkDir.listFiles()?.isNotEmpty() == true
    }

    private fun isGradleReady(): Boolean {
        return gradleDir.exists() && gradleDir.listFiles()?.isNotEmpty() == true
    }

    fun getJdkHome(): File? {
        return jdkDir.listFiles()?.firstOrNull { it.isDirectory }
    }

    fun getGradleHome(): File? {
        return gradleDir.listFiles()?.firstOrNull { it.isDirectory }
    }

    private fun downloadFile(urlString: String, dest: File, onProgress: (Int) -> Unit) {
        var currentUrl = urlString
        var connection: HttpURLConnection
        var redirects = 0
        
        while (true) {
            connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            val status = connection.responseCode
            if (status in 300..399) {
                currentUrl = connection.getHeaderField("Location")
                redirects++
                if (redirects > 5) throw Exception("Too many redirects")
                continue
            }
            break
        }

        val fileLength = connection.contentLength
        val input = connection.inputStream
        val output = FileOutputStream(dest)

        val data = ByteArray(8192)
        var total: Long = 0
        var count: Int
        var lastProgress = 0

        while (input.read(data).also { count = it } != -1) {
            total += count
            if (fileLength > 0) {
                val progress = (total * 100 / fileLength).toInt()
                if (progress != lastProgress) {
                    lastProgress = progress
                    onProgress(progress)
                }
            }
            output.write(data, 0, count)
        }
        output.flush()
        output.close()
        input.close()
    }

    private fun extractTarGz(archive: File, destDir: File) {
        destDir.mkdirs()
        // Android cihazlarda tar komutu genellikle mevcuttur.
        val process = ProcessBuilder("tar", "-xzf", archive.absolutePath, "-C", destDir.absolutePath)
            .redirectErrorStream(true)
            .start()
        process.waitFor()
    }

    private fun extractZip(archive: File, destDir: File) {
        destDir.mkdirs()
        ZipInputStream(archive.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(destDir, entry.name)
                
                // Güvenlik: Zip Slip Zafiyetini Önleme
                val destDirPath = destDir.canonicalPath
                val destFilePath = file.canonicalPath
                if (!destFilePath.startsWith(destDirPath + File.separator)) {
                    throw SecurityException("Entry is outside of the target dir: ${entry.name}")
                }

                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                entry = zis.nextEntry
            }
        }
    }
}
