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

    // Serializes the read-modify-write of KEY_LEARNED(+REV) so a newer save
    // can never be overwritten by an older snapshot queued behind it.
    private val learnedStoreLock = Any()

    @Synchronized
    fun loadLearned(): MutableMap<String, Int> {
        val raw = prefs.getString(KEY_LEARNED, null) ?: return mutableMapOf()
        val map = mutableMapOf<String, Int>()

        raw.lineSequence().forEach { line ->
            val separator = line.indexOf('\t')

            // Ignore empty or malformed entries.
            if (separator <= 0 || separator >= line.lastIndex) return@forEach

            val word = line.substring(0, separator)
                .trim()
                .lowercase()

            if (word.isEmpty()) return@forEach

            val count = line.substring(separator + 1)
                .trim()
                .toIntOrNull()
                ?: return@forEach

            // Learned counts must be positive.
            if (count <= 0) return@forEach

            map[word] = maxOf(
                map[word] ?: 0,
                count
            )
        }

        return map
    }

    @Synchronized
    fun saveLearned(words: Map<String, Int>) {
        synchronized(learnedStoreLock) {
            val normalized = mutableMapOf<String, Int>()

            for ((rawWord, rawCount) in words) {
                val word = rawWord.trim().lowercase()

                if (word.isEmpty()) continue
                if (rawCount <= 0) continue

                normalized[word] = maxOf(
                    normalized[word] ?: 0,
                    rawCount
                )
            }

            val encoded = normalized
                .entries
                .sortedByDescending { it.value }
                .joinToString("\n") { (word, count) ->
                    "$word\t$count"
                }

            val currentEncoded =
                prefs.getString(KEY_LEARNED, "") ?: ""

            // Nothing actually changed.
            if (encoded == currentEncoded) {
                return
            }

            val nextRevision = prefs.getLong(KEY_LEARNED_REV, 0L) + 1L

            val editor = prefs.edit()
            editor.putString(KEY_LEARNED, encoded)
            editor.putLong(KEY_LEARNED_REV, nextRevision)
            editor.apply()
        }
    }

    /** Monotonic counter bumped on every learned-words write, so a second engine
     *  instance can detect that another screen changed the bucket and re-read
     *  before it overwrites it (otherwise deleted words come back). */
    @Synchronized
    fun learnedRevision(): Long {
        synchronized(learnedStoreLock) {
            return prefs.getLong(KEY_LEARNED_REV, 0L)
        }
    }

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
    fun loadSuppressed(): Set<String> {
        return prefs
            .getStringSet(KEY_SUPPRESSED, emptySet())
            ?.toSet()
            ?: emptySet()
    }

    @Synchronized
    fun saveSuppressed(words: Set<String>) {
        prefs.edit().putStringSet(KEY_SUPPRESSED, words).apply()
    }

    @Synchronized
    fun loadFavorites(): Set<String> {
        return prefs
            .getStringSet(KEY_FAVORITES, emptySet())
            ?.toSet()
            ?: emptySet()
    }

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

