package com.myksoft.build.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.myksoft.build.R
import com.myksoft.build.core.GradleRunner
import com.myksoft.build.logger.AppLogger
import com.myksoft.build.manager.UnzipManager
import com.myksoft.build.utils.ErrorParser
import com.myksoft.build.utils.PerformanceMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MYK Build - Main Dashboard & Terminal
 * Uygulamanın orkestra şefi. Tüm modülleri yönetir ve UI ile bağlar.
 */
class MainActivity : BaseActivity() {

    private lateinit var tvRamUsage: TextView
    private lateinit var tvBuildTime: TextView
    private lateinit var rvTerminal: RecyclerView
    private lateinit var fabSelectZip: FloatingActionButton
    private lateinit var errorCardLayout: LinearLayout
    private lateinit var tvErrorFile: TextView
    private lateinit var tvErrorMessage: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var terminalAdapter: TerminalAdapter
    private lateinit var performanceMonitor: PerformanceMonitor
    private lateinit var unzipManager: UnzipManager
    private lateinit var gradleRunner: GradleRunner
    private lateinit var errorParser: ErrorParser

    private var buildStartTime: Long = 0

    // Dosya Seçici (SAF)
    private val zipPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processSelectedZip(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initManagers()
        checkStoragePermission()
    }

    private fun initViews() {
        tvRamUsage = findViewById(R.id.tvRamUsage)
        tvBuildTime = findViewById(R.id.tvBuildTime)
        rvTerminal = findViewById(R.id.rvTerminal)
        fabSelectZip = findViewById(R.id.fabSelectZip)
        errorCardLayout = findViewById(R.id.errorCardLayout)
        tvErrorFile = findViewById(R.id.tvErrorFile)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        progressBar = findViewById(R.id.progressBar)

        terminalAdapter = TerminalAdapter()
        rvTerminal.layoutManager = LinearLayoutManager(this)
        rvTerminal.adapter = terminalAdapter

        fabSelectZip.setOnClickListener {
            zipPickerLauncher.launch("application/zip")
        }
    }

    private fun initManagers() {
        performanceMonitor = PerformanceMonitor(this)
        unzipManager = UnzipManager(this)
        gradleRunner = GradleRunner()
        errorParser = ErrorParser()
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
                Toast.makeText(this, "Lütfen tüm dosyalara erişim izni verin (Android 11+)", Toast.LENGTH_LONG).show()
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 100)
            }
        }
    }

    private fun processSelectedZip(uri: Uri) {
        terminalAdapter.clearLogs()
        errorCardLayout.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        fabSelectZip.isEnabled = false

        logToTerminal(">>> ZIP dosyası seçildi, çıkarma işlemi başlıyor...")

        lifecycleScope.launch(Dispatchers.IO) {
            val projectName = "Project_${System.currentTimeMillis()}"
            val projectDir = unzipManager.extractProject(uri, projectName)

            withContext(Dispatchers.Main) {
                if (projectDir != null) {
                    logToTerminal(">>> Çıkarma başarılı. Proje dizini: ${projectDir.absolutePath}")
                    startBuildProcess(projectDir)
                } else {
                    progressBar.visibility = View.GONE
                    fabSelectZip.isEnabled = true
                    logToTerminal("[ERROR] ZIP çıkarma başarısız veya geçerli bir Android projesi bulunamadı!")
                    vibrateDevice()
                }
            }
        }
    }

    private fun startBuildProcess(projectDir: File) {
        logToTerminal(">>> Derleme motoru başlatılıyor...")
        buildStartTime = System.currentTimeMillis()

        // Performans izlemeyi başlat
        performanceMonitor.startMonitoring(
            coroutineScope = lifecycleScope,
            onUpdate = { usedRam, totalRam ->
                tvRamUsage.text = "RAM: ${usedRam}MB / ${totalRam}MB"
                val elapsedSeconds = (System.currentTimeMillis() - buildStartTime) / 1000
                tvBuildTime.text = "Süre: ${elapsedSeconds}s"
            },
            onCriticalMemory = {
                logToTerminal("[WARNING] Kritik RAM seviyesi! Derleme yavaşlayabilir.")
            }
        )

        // Gömülü JDK ve Gradle dizinlerini belirle (Assets'ten çıkarıldığını varsayıyoruz)
        val embeddedEnvDir = File(filesDir, "embedded_env")
        val jdkDir = File(embeddedEnvDir, "jdk-17-arm64")
        val gradleDir = File(embeddedEnvDir, "gradle-8.10.2")

        // Eğer henüz indirilmemişse uyarı ver (Şimdilik simüle ediyoruz)
        if (!jdkDir.exists()) {
            logToTerminal("[WARNING] Gömülü JDK bulunamadı! Lütfen CI/CD veya manuel olarak indirin.")
            // Şimdilik test için devam etmesine izin veriyoruz, gerçekte burada durmalı
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val allLogs = mutableListOf<String>()

            gradleRunner.runBuild(
                projectDir = projectDir,
                jdkDir = jdkDir,
                gradleDir = gradleDir,
                onLog = { logLine ->
                    allLogs.add(logLine)
                    withContext(Dispatchers.Main) {
                        terminalAdapter.addLog(logLine)
                        rvTerminal.scrollToPosition(terminalAdapter.itemCount - 1)
                    }
                },
                onResult = { isSuccess ->
                    withContext(Dispatchers.Main) {
                        performanceMonitor.stopMonitoring()
                        progressBar.visibility = View.GONE
                        fabSelectZip.isEnabled = true

                        if (isSuccess) {
                            logToTerminal(">>> BUILD SUCCESSFUL! APK hazır.")
                        } else {
                            logToTerminal(">>> BUILD FAILED! Hatalar analiz ediliyor...")
                            vibrateDevice()
                            analyzeErrors(allLogs)
                        }
                    }
                }
            )
        }
    }

    private fun analyzeErrors(logs: List<String>) {
        val parsedErrors = errorParser.parseLogs(logs)
        
        if (parsedErrors.isNotEmpty()) {
            val firstError = parsedErrors.first()
            
            tvErrorFile.text = if (firstError.isCausedBy) "Sistem Hatası" else "${firstError.filePath}:${firstError.line}"
            tvErrorMessage.text = firstError.message
            
            errorCardLayout.visibility = View.VISIBLE
        }
    }

    private fun logToTerminal(message: String) {
        terminalAdapter.addLog(message)
        rvTerminal.scrollToPosition(terminalAdapter.itemCount - 1)
    }

    private fun vibrateDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        performanceMonitor.stopMonitoring()
    }
}
