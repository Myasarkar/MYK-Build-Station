package com.myksoft.build.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.myksoft.build.R

/**
 * MYK Build - Terminal Adapter
 * Gradle loglarını en düşük RAM tüketimiyle RecyclerView üzerinde listeler.
 */
class TerminalAdapter : RecyclerView.Adapter<TerminalAdapter.LogViewHolder>() {

    private val logs = mutableListOf<String>()

    fun addLog(log: String) {
        logs.add(log)
        // Çok fazla log birikmesini önle (Son 2000 satır)
        if (logs.size > 2000) {
            logs.removeAt(0)
            notifyItemRemoved(0)
        }
        notifyItemInserted(logs.size - 1)
    }

    fun clearLogs() {
        val size = logs.size
        logs.clear()
        notifyItemRangeRemoved(0, size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_terminal_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(logs[position])
    }

    override fun getItemCount(): Int = logs.size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvLog: TextView = itemView.findViewById(R.id.tvLogLine)

        fun bind(log: String) {
            tvLog.text = log
            
            // Log seviyesine göre renklendirme
            when {
                log.contains("[ERROR]") || log.contains("FAILED") || log.contains("Exception") -> {
                    tvLog.setTextColor(Color.parseColor("#FF5252")) // Kırmızı
                }
                log.contains("[WARNING]") || log.contains("WARN") -> {
                    tvLog.setTextColor(Color.parseColor("#FFD740")) // Sarı
                }
                log.contains("SUCCESS") || log.contains("BUILD SUCCESSFUL") -> {
                    tvLog.setTextColor(Color.parseColor("#69F0AE")) // Yeşil
                }
                else -> {
                    tvLog.setTextColor(Color.parseColor("#E0E0E0")) // Standart Gri/Beyaz
                }
            }
        }
    }
}
