package com.romannepali.keyboard.clipboard

import android.content.Context

data class ClipboardItem(
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

class ClipboardManager(private val context: Context) {
    
    private val clipboardHistory = mutableListOf<ClipboardItem>()
    private val maxHistorySize = 50
    
    fun copy(text: String) {
        if (text.isBlank()) return
        
        // Remove duplicate if exists
        clipboardHistory.removeAll { it.text == text }
        
        // Add to beginning
        clipboardHistory.add(0, ClipboardItem(text))
        
        // Trim to max size
        if (clipboardHistory.size > maxHistorySize) {
            clipboardHistory.removeAt(clipboardHistory.size - 1)
        }
        
        saveHistory()
    }
    
    fun paste(index: Int): String? {
        if (index < 0 || index >= clipboardHistory.size) return null
        return clipboardHistory[index].text
    }
    
    fun getHistory(): List<ClipboardItem> {
        return clipboardHistory.toList()
    }
    
    fun clearHistory() {
        clipboardHistory.clear()
        saveHistory()
    }
    
    fun removeItem(index: Int) {
        if (index in clipboardHistory.indices) {
            clipboardHistory.removeAt(index)
            saveHistory()
        }
    }

    fun removeByText(text: String) {
        if (clipboardHistory.removeAll { it.text == text }) {
            saveHistory()
        }
    }

    private fun saveHistory() {
        val raw = clipboardHistory.joinToString("\n") { it -> "${it.text}\u0001${it.timestamp}" }
        prefs().edit().putString("history", raw).apply()
    }

    private fun loadHistory() {
        val raw = prefs().getString("history", null) ?: return
        clipboardHistory.clear()
        raw.lineSequence().forEach { line ->
            val idx = line.indexOf('\u0001')
            if (idx > 0) {
                val text = line.substring(0, idx)
                val timestamp = line.substring(idx + 1).toLongOrNull() ?: 0L
                if (text.isNotBlank()) {
                    clipboardHistory.add(ClipboardItem(text, timestamp))
                }
            }
        }
    }

    private fun prefs() =
        context.getSharedPreferences("clipboard_prefs", Context.MODE_PRIVATE)
    
    fun search(query: String): List<ClipboardItem> {
        return clipboardHistory.filter { 
            it.text.contains(query, ignoreCase = true) 
        }
    }
    
    init {
        loadHistory()
    }
}
