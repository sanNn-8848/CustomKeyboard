package com.romannepali.keyboard.suggestion

import android.content.Context
import org.json.JSONObject

/**
 * Frequency model for next-word prediction.
 *
 * Loaded from `word_ngrams.json` (built by build_ngram_model.py):
 *   { "bigrams": { "<prev>": { "<next>": count, ... }, ... },
 *     "trigrams": { "<prev1> <prev2>": ["next", ...], ... } }
 *
 * The model predicts the *next* word, not just completes the current prefix.
 * Words are only ever surfaced if the engine gates them against the vocabulary.
 */
class NgramModel(context: Context) {

    private val bigrams = HashMap<String, HashMap<String, Int>>()
    private val trigrams = HashMap<String, List<String>>()
    private var unigrams = HashMap<String, Int>()

    fun addBigram(
        word1: String,
        word2: String,
        frequency: Int = 1
    ) {
        val first = word1.trim().lowercase()
        val second = word2.trim().lowercase()

        if (first.isEmpty() || second.isEmpty() || frequency <= 0) {
            return
        }

        bigrams
            .getOrPut(first) { HashMap() }
            .merge(second, frequency, Int::plus)

        unigrams.merge(first, frequency, Int::plus)
        unigrams.merge(second, frequency, Int::plus)
    }

    fun getNextWordCandidates(
        previousWord: String
    ): List<Pair<String, Int>> {
        val key = previousWord.trim().lowercase()

        if (key.isEmpty()) return emptyList()

        val candidates = bigrams[key] ?: return emptyList()

        return candidates.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    }

    /**
     * Combined next-word candidates for a two-word context, strongest first.
     * Trigram continuations lead, then the bigram of the immediately previous
     * word, then the earlier word.
     */
    fun getNextWordCandidates(
        prev1: String,
        prev2: String
    ): List<Pair<String, Int>> {
        val first = prev1.trim().lowercase()
        val second = prev2.trim().lowercase()

        if (first.isEmpty() || second.isEmpty()) {
            return emptyList()
        }

        val scored = HashMap<String, Int>()

        trigrams["$first $second"]
            ?.forEachIndexed { index, next ->
                val cleanNext = next.trim().lowercase()

                if (cleanNext.isNotEmpty()) {
                    scored.merge(
                        cleanNext,
                        PRIME_TRIGRAM - index,
                        Int::plus
                    )
                }
            }

        bigrams[second]?.forEach { (next, count) ->
            if (count > 0) {
                scored.merge(next, count, Int::plus)
            }
        }

        bigrams[first]?.forEach { (next, count) ->
            if (count > 0) {
                scored.merge(
                    next,
                    count * BIGRAM_DAMPING,
                    Int::plus
                )
            }
        }

        return scored.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    }

    fun getTopNextWords(
        previousWord: String,
        limit: Int = 3
    ): List<String> {
        if (limit <= 0) return emptyList()

        return getNextWordCandidates(previousWord)
            .take(limit)
            .map { it.first }
    }

    fun getTopNextWords(
        prev1: String,
        prev2: String,
        limit: Int = 3
    ): List<String> {
        if (limit <= 0) return emptyList()

        return getNextWordCandidates(prev1, prev2)
            .take(limit)
            .map { it.first }
    }

    fun getUnigramFrequency(word: String): Int {
        val clean = word.trim().lowercase()

        if (clean.isEmpty()) return 0

        return unigrams[clean] ?: 0
    }

    fun getBigramProbability(
        word1: String,
        word2: String
    ): Float {
        val first = word1.trim().lowercase()
        val second = word2.trim().lowercase()

        if (first.isEmpty() || second.isEmpty()) {
            return 0f
        }

        val bigramCount = bigrams[first]?.get(second) ?: 0
        val unigramCount = unigrams[first] ?: 0

        if (bigramCount <= 0 || unigramCount <= 0) {
            return 0f
        }

        return bigramCount.toFloat() / unigramCount.toFloat()
    }

    /**
     * Kept for compatibility with older callers.
     *
     * The current architecture keeps live context in RomanNepaliIME, so this
     * method should not be used to maintain a second live context history.
     */
    fun mergeUserContext(contextWords: List<String>) {
        if (contextWords.size < 2) return

        for (i in 0 until contextWords.size - 1) {
            addBigram(
                contextWords[i],
                contextWords[i + 1],
                USER_CONTEXT_BIGRAM_WEIGHT
            )
        }
    }

    fun loadModel(unigramFrequencies: Map<String, Int>) {
        bigrams.clear()
        trigrams.clear()

        loadedBigrams?.forEach { (word, nextWords) ->
            bigrams[word] = HashMap(nextWords)
        }

        loadedTrigrams?.forEach { (context, nextWords) ->
            trigrams[context] = nextWords.toList()
        }

        unigrams = HashMap()

        unigramFrequencies.forEach { (word, frequency) ->
            val clean = word.trim().lowercase()

            if (clean.isNotEmpty() && frequency > 0) {
                unigrams[clean] = frequency
            }
        }
    }

    private companion object {
        const val PRIME_TRIGRAM = 10_000
        const val BIGRAM_DAMPING = 2
        const val USER_CONTEXT_BIGRAM_WEIGHT = 8

        @Volatile
        private var loadedBigrams:
            HashMap<String, HashMap<String, Int>>? = null

        @Volatile
        private var loadedTrigrams:
            HashMap<String, List<String>>? = null

        private val loadLock = Any()

        /**
         * Parses the asset JSON once; result is shared across engine instances.
         *
         * The returned structures are treated as immutable snapshots.
         * Each NgramModel receives deep copies in loadModel().
         */
        fun ensureLoaded(context: Context) {
            if (loadedBigrams != null && loadedTrigrams != null) {
                return
            }

            synchronized(loadLock) {
                if (loadedBigrams != null && loadedTrigrams != null) {
                    return
                }

                try {
                    val text = context.assets
                        .open("word_ngrams.json")
                        .bufferedReader()
                        .use { it.readText() }

                    val root = JSONObject(text)

                    val bi = root.optJSONObject("bigrams")
                        ?: JSONObject()

                    val parsedBigrams =
                        HashMap<String, HashMap<String, Int>>()

                    val keys = bi.keys()

                    while (keys.hasNext()) {
                        val rawWord = keys.next()
                        val word = rawWord.trim().lowercase()

                        if (word.isEmpty()) continue

                        val map = bi.optJSONObject(rawWord)
                            ?: continue

                        val inner = HashMap<String, Int>()
                        val k2 = map.keys()

                        while (k2.hasNext()) {
                            val rawNext = k2.next()
                            val next = rawNext.trim().lowercase()
                            val count = map.optInt(rawNext, 0)

                            if (next.isNotEmpty() && count > 0) {
                                inner[next] = count
                            }
                        }

                        if (inner.isNotEmpty()) {
                            parsedBigrams[word] = inner
                        }
                    }

                    val tri = root.optJSONObject("trigrams")
                        ?: JSONObject()

                    val parsedTrigrams =
                        HashMap<String, List<String>>()

                    val k3 = tri.keys()

                    while (k3.hasNext()) {
                        val rawContext = k3.next()

                        val contextWords = rawContext
                            .trim()
                            .lowercase()
                            .split(Regex("\\s+"))
                            .filter { it.isNotEmpty() }

                        if (contextWords.size < 2) continue

                        val context =
                            "${contextWords[contextWords.size - 2]} " +
                            contextWords[contextWords.size - 1]

                        val arr = tri.optJSONArray(rawContext)
                            ?: continue

                        val list = ArrayList<String>(arr.length())

                        for (i in 0 until arr.length()) {
                            val next = arr.optString(i)
                                .trim()
                                .lowercase()

                            if (next.isNotEmpty()) {
                                list.add(next)
                            }
                        }

                        if (list.isNotEmpty()) {
                            parsedTrigrams[context] = list
                        }
                    }

                    loadedBigrams = parsedBigrams
                    loadedTrigrams = parsedTrigrams

                } catch (_: Exception) {
                    loadedBigrams = HashMap()
                    loadedTrigrams = HashMap()
                }
            }
        }
    }

    init {
        ensureLoaded(context.applicationContext)
    }
}