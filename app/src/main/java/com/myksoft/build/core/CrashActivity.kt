package com.myksoft.build.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * MYK Build - Crash Recovery Screen
 * Uygulama çöktüğünde kullanıcıya teknik dökümü gösterir ve kopyalama imkanı sunar.
 */
class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Basit bir UI oluşturuyoruz (XML olmadan, programatik olarak)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E")) // Koyu tema
        }

        val titleView = TextView(this).apply {
            text = "MYK Build Beklenmeyen Bir Hata İle Karşılaştı"
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }

        val crashReport = intent.getStringExtra("CRASH_REPORT") ?: "Bilinmeyen Hata"

        val errorView = TextView(this).apply {
            text = crashReport
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#FF5252"))
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#2D2D2D"))
        }

        val scrollView = android.widget.ScrollView(this).apply {
            addView(errorView)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val copyButton = Button(this).apply {
            text = "Uygulama Hatasını Kopyala"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("MYK Build Crash Report", crashReport)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@CrashActivity, "Hata panoya kopyalandı!", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(titleView)
        layout.addView(scrollView)
        layout.addView(copyButton)

        setContentView(layout)
    }
}
