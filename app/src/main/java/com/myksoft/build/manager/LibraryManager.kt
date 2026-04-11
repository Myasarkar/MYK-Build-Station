package com.myksoft.build.manager

import android.content.Context
import com.myksoft.build.logger.AppLogger
import org.json.JSONObject
import java.io.File

/**
 * MYK Build - Library Manager (Kütüphane Kontrol Merkezi)
 * Cihazdaki offline kütüphaneleri yönetir ve build öncesi init.gradle betiği oluşturur.
 */
class LibraryManager(private val context: Context) {

    private val localMavenDir = File(context.filesDir, "local_maven")
    private val metadataFile = File(localMavenDir, "metadata.json")

    init {
        if (!localMavenDir.exists()) localMavenDir.mkdirs()
        if (!metadataFile.exists()) metadataFile.writeText("{}")
    }

    /**
     * İstenen kütüphaneyi (şimdilik simüle edilmiş) indirir ve metadata'ya kaydeder.
     * Gerçek senaryoda burada Retrofit ile POM ve AAR/JAR dosyaları indirilir.
     */
    fun downloadAndCacheLibrary(groupId: String, artifactId: String, version: String) {
        AppLogger.log("LibraryManager", "DOWNLOADING", "$groupId:$artifactId:$version")
        
        // Klasör yapısını oluştur (örn: com/squareup/retrofit2/retrofit/2.9.0)
        val groupPath = groupId.replace(".", "/")
        val libDir = File(localMavenDir, "$groupPath/$artifactId/$version")
        libDir.mkdirs()

        // Simülasyon: Boş bir POM ve JAR dosyası oluştur
        File(libDir, "$artifactId-$version.pom").writeText("<!-- Simulated POM -->")
        File(libDir, "$artifactId-$version.jar").writeText("Simulated JAR content")

        updateMetadata("$groupId:$artifactId:$version", true)
        AppLogger.log("LibraryManager", "CACHED", "Library saved to local maven.")
    }

    private fun updateMetadata(libraryKey: String, isCached: Boolean) {
        try {
            val json = JSONObject(metadataFile.readText())
            json.put(libraryKey, isCached)
            metadataFile.writeText(json.toString())
        } catch (e: Exception) {
            AppLogger.e("LibraryManager", "METADATA_ERROR", e)
        }
    }

    /**
     * Gradle'ın yerel depomuzu kullanması için bir init script oluşturur.
     * Bu script, projenin build.gradle dosyasına dokunmadan depoları ezer.
     */
    fun generateInitScript(): File {
        val initScript = File(context.filesDir, "init.gradle")
        val scriptContent = """
            allprojects {
                buildscript {
                    repositories {
                        maven { url 'file://${localMavenDir.absolutePath}' }
                        google()
                        mavenCentral()
                    }
                }
                repositories {
                    maven { url 'file://${localMavenDir.absolutePath}' }
                    google()
                    mavenCentral()
                }
            }
        """.trimIndent()
        
        initScript.writeText(scriptContent)
        AppLogger.log("LibraryManager", "INIT_SCRIPT", "Generated at ${initScript.absolutePath}")
        return initScript
    }
}
