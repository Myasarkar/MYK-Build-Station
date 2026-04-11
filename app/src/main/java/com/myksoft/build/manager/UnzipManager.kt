package com.myksoft.build.manager

import android.content.Context
import android.net.Uri
import com.myksoft.build.logger.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * MYK Build - Unzip Manager
 * Kullanıcının seçtiği .zip dosyasını güvenli bir şekilde private depolama alanına çıkarır
 * ve projenin geçerliliğini (build.gradle) kontrol eder.
 */
class UnzipManager(private val context: Context) {

    /**
     * @param zipUri Seçilen ZIP dosyasının URI'si (Scoped Storage uyumlu)
     * @param projectName Çıkarılacak klasörün adı
     * @return Çıkarılan projenin kök dizini (File) veya hata durumunda null
     */
    fun extractProject(zipUri: Uri, projectName: String): File? {
        val projectsDir = File(context.filesDir, "projects")
        if (!projectsDir.exists()) projectsDir.mkdirs()

        val destDir = File(projectsDir, projectName)
        if (destDir.exists()) {
            destDir.deleteRecursively() // Eski kalıntıları temizle
        }
        destDir.mkdirs()

        AppLogger.log("UnzipManager", "STARTED", "Extracting to: ${destDir.absolutePath}")

        try {
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var zipEntry = zis.nextEntry
                    while (zipEntry != null) {
                        val newFile = File(destDir, zipEntry.name)

                        // Güvenlik: Zip Slip Zafiyetini Önleme
                        val destDirPath = destDir.canonicalPath
                        val destFilePath = newFile.canonicalPath
                        if (!destFilePath.startsWith(destDirPath + File.separator)) {
                            AppLogger.log("UnzipManager", "SECURITY_ERROR", "Zip Slip detected: ${zipEntry.name}")
                            throw SecurityException("Entry is outside of the target dir: ${zipEntry.name}")
                        }

                        if (zipEntry.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            // Dosyanın ebeveyn klasörleri yoksa oluştur
                            newFile.parentFile?.mkdirs()
                            
                            FileOutputStream(newFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            // AppLogger.log("UnzipManager", "EXTRACTED", newFile.name) // Çok fazla log olmaması için kapatılabilir
                        }
                        zipEntry = zis.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("UnzipManager", "FAILED", e)
            return null
        }

        AppLogger.log("UnzipManager", "SUCCESS", "Extraction completed.")

        // Akıllı Kontrol: build.gradle veya build.gradle.kts var mı?
        return if (isValidAndroidProject(destDir)) {
            AppLogger.log("UnzipManager", "VALIDATION", "Valid Android Project found.")
            destDir
        } else {
            AppLogger.log("UnzipManager", "VALIDATION_FAILED", "build.gradle missing in root.")
            null
        }
    }

    private fun isValidAndroidProject(dir: File): Boolean {
        // Zip içindeki ana klasörü bul (genelde zip adı ile aynı bir kök klasör olur)
        val rootFiles = dir.listFiles() ?: return false
        
        // Eğer zip içinde tek bir klasör varsa (örn: MyProject/), asıl kök dizin odur
        val actualRoot = if (rootFiles.size == 1 && rootFiles[0].isDirectory) {
            rootFiles[0]
        } else {
            dir
        }

        val hasGradle = File(actualRoot, "build.gradle").exists() || File(actualRoot, "build.gradle.kts").exists()
        val hasSettings = File(actualRoot, "settings.gradle").exists() || File(actualRoot, "settings.gradle.kts").exists()

        return hasGradle || hasSettings
    }
}
