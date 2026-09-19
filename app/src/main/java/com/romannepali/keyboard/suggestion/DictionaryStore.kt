package com.romannepali.keyboard.suggestion

import android.content.Context

/**
 * Persistent storage for the three user-dictionary buckets:
 *  - learned words (auto-typed, with use counts)
 *  - saved words (user's personal dictionary, never wiped by "clear learned")
 *  - suppressed words (user explicitly hides from suggestions, reversible via undo)
 */
class DictionaryStore(context: Context) {

    private val prefs = context.getSharedPreferences("merotype_dict", Context.MODE_PRIVATE)

    @Synchronized
    fun loadLearned(): Map<String, Int> {
        val raw = prefs.getString(KEY_LEARNED, null) ?: return emptyMap()
        val map = HashMap<String, Int>()
        raw.lineSequence().forEach { line ->
            val idx = line.indexOf('\t')
            if (idx > 0) {
                val word = line.substring(0, idx)
                val times = line.substring(idx + 1).toIntOrNull() ?: return@forEach
                if (word.isNotBlank()) map[word] = times
            }
        }
        return map
    }

    @Synchronized
    fun saveLearned(words: Map<String, Int>) {
        val raw = words.entries
            .sortedByDescending { it.value }
            .joinToString("\n") { "${it.key}\t${it.value}" }
        prefs.edit()
            .putString(KEY_LEARNED, raw)
            .putInt(KEY_LEARNED_REV, learnedRevision() + 1)
            .apply()
    }

    /** Monotonic counter bumped on every learned-words write, so a second engine
     *  instance can detect that another screen changed the bucket and re-read
     *  before it overwrites it (otherwise deleted words come back). */
    @Synchronized
    fun learnedRevision(): Int = prefs.getInt(KEY_LEARNED_REV, 0)

    @Synchronized
    fun loadSaved(): List<String> {
        val set = prefs.getStringSet(KEY_SAVED, emptySet()) ?: emptySet()
        return set.toList().sorted()
    }

    @Synchronized
    fun saveSaved(words: List<String>) {
        prefs.edit().putStringSet(KEY_SAVED, words.toSet()).apply()
    }

    @Synchronized
    fun loadSuppressed(): Set<String> =
        prefs.getStringSet(KEY_SUPPRESSED, emptySet()) ?: emptySet()

    @Synchronized
    fun saveSuppressed(words: Set<String>) {
        prefs.edit().putStringSet(KEY_SUPPRESSED, words).apply()
    }

    @Synchronized
    fun loadFavorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()

    @Synchronized
    fun saveFavorites(words: Set<String>) {
        prefs.edit().putStringSet(KEY_FAVORITES, words).apply()
    }

    @Synchronized
    fun loadCaseOverrides(): Map<String, String> {
        val set = prefs.getStringSet(KEY_CASE_OVERRIDES, emptySet()) ?: emptySet()
        val map = HashMap<String, String>()
        set.forEach { raw ->
            val idx = raw.indexOf('\u0001')
            if (idx > 0) {
                map[raw.substring(0, idx)] = raw.substring(idx + 1)
            }
        }
        return map
    }

    /** Persists the remembered suggestion case per word: "upper" / "lower". */
    @Synchronized
    fun saveCaseOverrides(overrides: Map<String, String>) {
        val set = overrides.entries.map { "${it.key}\u0001${it.value}" }.toSet()
        prefs.edit().putStringSet(KEY_CASE_OVERRIDES, set).apply()
    }

    private companion object {
        const val KEY_LEARNED = "learned"
        const val KEY_LEARNED_REV = "learned_rev"
        const val KEY_SAVED = "saved"
        const val KEY_SUPPRESSED = "suppressed"
        const val KEY_FAVORITES = "favorites"
        const val KEY_CASE_OVERRIDES = "case_overrides"
    }
}