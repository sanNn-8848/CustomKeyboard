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
    
    private fun saveHistory() {
        val prefs = context.getSharedPreferences("clipboard_prefs", Context.MODE_PRIVATE)
        val texts = clipboardHistory.map { it.text }.toSet()
        prefs.edit().putStringSet("history", texts).apply()
    }
    
    private fun loadHistory() {
        val prefs = context.getSharedPreferences("clipboard_prefs", Context.MODE_PRIVATE)
        val texts = prefs.getStringSet("history", emptySet()) ?: emptySet()
        clipboardHistory.clear()
        texts.forEach { text ->
            clipboardHistory.add(ClipboardItem(text))
        }
    }
    
    fun search(query: String): List<ClipboardItem> {
        return clipboardHistory.filter { 
            it.text.contains(query, ignoreCase = true) 
        }
    }
    
    init {
        loadHistory()
    }
}
