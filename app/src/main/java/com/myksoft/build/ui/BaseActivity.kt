package com.myksoft.build.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * MYK Build - Base Activity
 * Tüm ekranlarda ortak kullanılacak dil (Locale) ve tema yönetimini barındırır.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(updateBaseContextLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Material 3 Tema ayarları burada yapılabilir
    }

    private fun updateBaseContextLocale(context: Context): Context {
        // Desteklenen diller: Türkçe, İngilizce, Çince, Hintçe
        val supportedLocales = listOf("tr", "en", "zh", "hi")
        val currentLanguage = Locale.getDefault().language
        
        // Eğer cihazın dili desteklenmiyorsa fallback olarak İngilizce (en) kullan
        val language = if (supportedLocales.contains(currentLanguage)) currentLanguage else "en"
        
        val locale = Locale(language)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        
        return context.createConfigurationContext(configuration)
    }
}
