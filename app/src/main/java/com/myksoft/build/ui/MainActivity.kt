package com.myksoft.build.ui

import android.content.Context
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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.myksoft.build.R
import com.myksoft.build.core.GradleRunner
import com.myksoft.build.databinding.ActivityMainBinding
import com.myksoft.build.logger.AppLogger
import com.myksoft.build.manager.EnvironmentManager
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

    private lateinit var binding: ActivityMainBinding

    private lateinit var terminalAdapter: TerminalAdapter
    private lateinit var performanceMonitor: PerformanceMonitor
    private lateinit var unzipManager: UnzipManager
    private lateinit var gradleRunner: GradleRunner
    private lateinit var errorParser: ErrorParser
    private lateinit var environmentManager: EnvironmentManager

    private var buildStartTime: Long = 0

    // Dosya Seçici (SAF)
    private val zipPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { processSelectedZip(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initViews()
        initManagers()
        checkStoragePermission()
        setupEnvironment()
    }

    private fun setupEnvironment() {
        binding.btnPickZip.isEnabled = false
        binding.downloadOverlay.visibility = View.VISIBLE
        logToTerminal(">>> Checking embedded environment...")

        lifecycleScope.launch(Dispatchers.Main) {
            val isReady = environmentManager.checkAndSetupEnvironment { status, progress ->
                lifecycleScope.launch(Dispatchers.Main) {
                    binding.tvDownloadStatus.text = status
                    binding.pbDownload.progress = progress
                }
            }

            if (isReady) {
                binding.downloadOverlay.visibility = View.GONE
                binding.btnPickZip.isEnabled = true
                logToTerminal(">>> Environment is ready! You can now select a ZIP file.")
            } else {
                binding.tvDownloadStatus.text = "Environment setup failed!"
                logToTerminal("[ERROR] Failed to initialize environment. Check internet connection.")
            }
        }
    }

    private fun initViews() {
        terminalAdapter = TerminalAdapter()
        binding.rvTerminal.layoutManager = LinearLayoutManager(this)
        binding.rvTerminal.adapter = terminalAdapter

        binding.btnPickZip.setOnClickListener {
            zipPickerLauncher.launch("application/zip")
        }
    }

    private fun initManagers() {
        performanceMonitor = PerformanceMonitor(this)
        unzipManager = UnzipManager(this)
        gradleRunner = GradleRunner()
        errorParser = ErrorParser()
        environmentManager = EnvironmentManager(this)
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
        binding.errorCardContainer.root.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        binding.btnPickZip.isEnabled = false

        logToTerminal(">>> ZIP dosyası seçildi, çıkarma işlemi başlıyor...")

        lifecycleScope.launch(Dispatchers.IO) {
            val projectName = "Project_${System.currentTimeMillis()}"
            val projectDir = unzipManager.extractProject(uri, projectName)

            withContext(Dispatchers.Main) {
                if (projectDir != null) {
                    logToTerminal(">>> Çıkarma başarılı. Proje dizini: ${projectDir.absolutePath}")
                    startBuildProcess(projectDir)
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.btnPickZip.isEnabled = true
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
                binding.tvRamUsage.text = "RAM: ${usedRam}MB / ${totalRam}MB"
                val elapsedSeconds = (System.currentTimeMillis() - buildStartTime) / 1000
                binding.tvBuildTimer.text = "Süre: ${elapsedSeconds}s"
            },
            onCriticalMemory = {
                logToTerminal("[WARNING] Kritik RAM seviyesi! Derleme yavaşlayabilir.")
            }
        )

        // Gömülü JDK ve Gradle dizinlerini belirle (Internal Storage'dan dinamik olarak)
        val jdkDir = environmentManager.getJdkHome()
        val gradleDir = environmentManager.getGradleHome()

        if (jdkDir == null || gradleDir == null) {
            logToTerminal("[ERROR] Gömülü JDK veya Gradle bulunamadı! Lütfen uygulamayı yeniden başlatın.")
            binding.progressBar.visibility = View.GONE
            binding.btnPickZip.isEnabled = true
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val allLogs = mutableListOf<String>()

            gradleRunner.runBuild(
                projectDir = projectDir,
                jdkDir = jdkDir,
                gradleDir = gradleDir,
                onLog = { logLine ->
                    allLogs.add(logLine)
                    lifecycleScope.launch(Dispatchers.Main) {
                        terminalAdapter.addLog(logLine)
                        binding.rvTerminal.scrollToPosition(terminalAdapter.itemCount - 1)
                    }
                },
                onResult = { isSuccess ->
                    lifecycleScope.launch(Dispatchers.Main) {
                        performanceMonitor.stopMonitoring()
                        binding.progressBar.visibility = View.GONE
                        binding.btnPickZip.isEnabled = true

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
            
            binding.errorCardContainer.tvErrorFile.text = if (firstError.isCausedBy) "Sistem Hatası" else "${firstError.filePath}:${firstError.line}"
            binding.errorCardContainer.tvErrorMessage.text = firstError.message
            
            binding.errorCardContainer.root.visibility = View.VISIBLE
        }
    }

    private fun logToTerminal(message: String) {
        terminalAdapter.addLog(message)
        binding.rvTerminal.scrollToPosition(terminalAdapter.itemCount - 1)
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
