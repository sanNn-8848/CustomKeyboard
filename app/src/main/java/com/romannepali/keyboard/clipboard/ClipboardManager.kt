package com.romannepali.keyboard.clipboard

import android.content.Context

data class ClipboardItem(
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * App-wide clipboard history, persisted to "clipboard_prefs".
 *
 * Settings and the keyboard UI used to each render their own in-memory copy of
 * the history while writing to the same prefs file — so a clip deleted in one
 * screen could be resurrected by the other's stale list on its next save.
 * A monotonic revision counter re-syncs any instance from storage before every
 * write, so a deleted item can never come back.
 */
class ClipboardManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: ClipboardManager? = null

        /** Single app-wide instance so all screens share one in-memory list. */
        fun get(context: Context): ClipboardManager =
            instance ?: synchronized(this) {
                instance ?: ClipboardManager(context.applicationContext).also { instance = it }
            }
    }

    private val clipboardHistory = mutableListOf<ClipboardItem>()
    private val maxHistorySize = 50
    private var loadedRevision = 0

    private fun normalize(text: String): String =
        text.trim()
            .trim('"', '\'', '“', '”', '‘', '’', '「', '」', '『', '』', '(', ')', '（', '）')
            .lowercase()

    private fun revision(): Int = prefs().getInt("rev", 0)

    /** Re-reads storage whenever another instance changed it. */
    private fun syncFromStorage() {
        if (revision() != loadedRevision) {
            loadHistory()
        }
    }

    fun copy(text: String) {
        if (text.isBlank()) return
        syncFromStorage()

        // Remove any repeated clip (ignoring case, outer quotes and whitespace)
        val key = normalize(text)
        if (key.isEmpty()) return
        clipboardHistory.removeAll { normalize(it.text) == key }

        // Add to beginning
        clipboardHistory.add(0, ClipboardItem(text))

        // Trim to max size
        if (clipboardHistory.size > maxHistorySize) {
            clipboardHistory.removeAt(clipboardHistory.size - 1)
        }

        saveHistory()
    }

    fun paste(index: Int): String? {
        syncFromStorage()
        if (index < 0 || index >= clipboardHistory.size) return null
        return clipboardHistory[index].text
    }

    fun getHistory(): List<ClipboardItem> {
        syncFromStorage()
        return clipboardHistory.toList()
    }

    fun clearHistory() {
        syncFromStorage()
        clipboardHistory.clear()
        saveHistory()
    }

    fun removeItem(index: Int) {
        syncFromStorage()
        if (index in clipboardHistory.indices) {
            clipboardHistory.removeAt(index)
            saveHistory()
        }
    }

    fun removeByText(text: String) {
        syncFromStorage()
        val key = normalize(text)
        if (clipboardHistory.removeAll { normalize(it.text) == key }) {
            saveHistory()
        }
    }

    private fun saveHistory() {
        val arr = org.json.JSONArray()
        clipboardHistory.forEach { item ->
            val obj = org.json.JSONObject()
            obj.put("t", item.text)
            obj.put("m", item.timestamp)
            arr.put(obj)
        }
        val editor = prefs().edit()
        editor.putString("history", arr.toString())
        val next = revision() + 1
        editor.putInt("rev", next)
        editor.apply()
        loadedRevision = next
    }

    private fun loadHistory() {
        val raw = prefs().getString("history", null)
        val parsed = mutableListOf<ClipboardItem>()
        if (raw != null) {
            try {
                val arr = org.json.JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val text = obj.getString("t")
                    val timestamp = obj.optLong("m", 0L)
                    if (text.isNotBlank()) {
                        parsed.add(ClipboardItem(text, timestamp))
                    }
                }
            } catch (_: Exception) {
                // Legacy newline-delimited format.
                raw.lineSequence().forEach { line ->
                    val idx = line.indexOf('\u0001')
                    if (idx > 0) {
                        val text = line.substring(0, idx)
                        val timestamp = line.substring(idx + 1).toLongOrNull() ?: 0L
                        if (text.isNotBlank()) {
                            parsed.add(ClipboardItem(text, timestamp))
                        }
                    }
                }
            }
        }
        val seen = HashSet<String>()
        val deduped = mutableListOf<ClipboardItem>()
        for (item in parsed) {
            if (seen.add(normalize(item.text))) {
                deduped.add(item)
            }
        }
        clipboardHistory.clear()
        clipboardHistory.addAll(deduped)
        if (parsed.size != deduped.size) {
            saveHistory()
        } else {
            loadedRevision = revision()
        }
    }

    private fun prefs() =
        context.getSharedPreferences("clipboard_prefs", Context.MODE_PRIVATE)

    fun search(query: String): List<ClipboardItem> {
        syncFromStorage()
        return clipboardHistory.filter {
            it.text.contains(query, ignoreCase = true)
        }
    }

    init {
        loadHistory()
    }
}