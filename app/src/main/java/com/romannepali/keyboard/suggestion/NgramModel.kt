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

    fun addBigram(word1: String, word2: String, frequency: Int = 1) {
        val key = word1.lowercase()
        bigrams.getOrPut(key) { HashMap() }
            .merge(word2.lowercase(), frequency, Int::plus)
        unigrams.merge(word1.lowercase(), frequency, Int::plus)
        unigrams.merge(word2.lowercase(), frequency, Int::plus)
    }

    fun getNextWordCandidates(previousWord: String): List<Pair<String, Int>> {
        val key = previousWord.lowercase()
        val candidates = bigrams[key] ?: return emptyList()
        return candidates.entries
            .sortedByDescending { it.value }
            .map { Pair(it.key, it.value) }
    }

    /**
     * Combined next-word candidates for a two-word context, strongest first.
     * Trigram continuations lead, then the bigram of the immediately previous
     * word, then the earlier word.
     */
    fun getNextWordCandidates(prev1: String, prev2: String): List<Pair<String, Int>> {
        val scored = HashMap<String, Int>()

        trigrams["${prev1.lowercase()} ${prev2.lowercase()}"]
            ?.forEachIndexed { index, next ->
                scored.merge(next, PRIME_TRIGRAM - index, Int::plus)
            }

        bigrams[prev2.lowercase()]?.forEach { (next, count) ->
            scored.merge(next, count, Int::plus)
        }
        bigrams[prev1.lowercase()]?.forEach { (next, count) ->
            scored.merge(next, count * BIGRAM_DAMPING, Int::plus)
        }

        return scored.entries
            .sortedByDescending { it.value }
            .map { Pair(it.key, it.value) }
    }

    fun getTopNextWords(previousWord: String, limit: Int = 3): List<String> =
        getNextWordCandidates(previousWord).take(limit).map { it.first }

    fun getTopNextWords(prev1: String, prev2: String, limit: Int = 3): List<String> =
        getNextWordCandidates(prev1, prev2).take(limit).map { it.first }

    fun getUnigramFrequency(word: String): Int =
        unigrams[word.lowercase()] ?: 0

    fun getBigramProbability(word1: String, word2: String): Float {
        val key = word1.lowercase()
        val bigramCount = bigrams[key]?.get(word2.lowercase()) ?: 0
        val unigramCount = unigrams[key] ?: 1
        return bigramCount.toFloat() / unigramCount.toFloat()
    }

    fun mergeUserContext(contextWords: List<String>) {
        unigrams = HashMap(unigrams)
        for (i in 0 until contextWords.size - 1) {
            addBigram(contextWords[i], contextWords[i + 1], USER_CONTEXT_BIGRAM_WEIGHT)
        }
    }

    fun loadModel(unigramFrequencies: Map<String, Int>) {
        if (loadedBigrams != null) {
            bigrams.putAll(loadedBigrams!!)
        }
        if (loadedTrigrams != null) {
            trigrams.putAll(loadedTrigrams!!)
        }
        unigrams = HashMap(unigramFrequencies)
    }

    private companion object {
        const val PRIME_TRIGRAM = 10_000
        const val BIGRAM_DAMPING = 2
        const val USER_CONTEXT_BIGRAM_WEIGHT = 8

        var loadedBigrams: HashMap<String, HashMap<String, Int>>? = null
        var loadedTrigrams: HashMap<String, List<String>>? = null

        /** Parses the asset JSON once; result is shared across engine instances. */
        fun ensureLoaded(context: Context) {
            if (loadedBigrams != null && loadedTrigrams != null) return
            try {
                val text = context.assets.open("word_ngrams.json")
                    .bufferedReader().use { it.readText() }
                val root = JSONObject(text)

                val bi = root.optJSONObject("bigrams") ?: JSONObject()
                val parsedBigrams = HashMap<String, HashMap<String, Int>>()
                val keys = bi.keys()
                while (keys.hasNext()) {
                    val word = keys.next()
                    val map = bi.optJSONObject(word) ?: continue
                    val inner = HashMap<String, Int>()
                    val k2 = map.keys()
                    while (k2.hasNext()) {
                        val next = k2.next()
                        inner[next] = map.optInt(next, 0)
                    }
                    parsedBigrams[word] = inner
                }

                val tri = root.optJSONObject("trigrams") ?: JSONObject()
                val parsedTrigrams = HashMap<String, List<String>>()
                val k3 = tri.keys()
                while (k3.hasNext()) {
                    val ctx = k3.next()
                    val arr = tri.optJSONArray(ctx) ?: continue
                    val list = ArrayList<String>(arr.length())
                    for (i in 0 until arr.length()) {
                        list.add(arr.optString(i))
                    }
                    parsedTrigrams[ctx] = list
                }

                loadedBigrams = parsedBigrams
                loadedTrigrams = parsedTrigrams
            } catch (e: Exception) {
                loadedBigrams = HashMap()
                loadedTrigrams = HashMap()
            }
        }
    }

    init {
        ensureLoaded(context)
    }
}