package com.romannepali.keyboard.suggestion

import android.content.Context
import org.json.JSONObject
import java.util.Locale
import kotlin.math.ln

/**
 * Character-level language model for Roman Nepali.
 *
 * Loads the trained trigram model from:
 *
 *     app/src/main/assets/char_model.json
 *
 * The model can:
 *  - score Roman Nepali words
 *  - generate plausible words from a prefix
 */
class CharModel(context: Context) {

    private val appContext = context.applicationContext

    private var n = 3

    private val ngramProbabilities = HashMap<String, Double>()

    private val alphabet = "abcdefghijklmnopqrstuvwxyz"

    @Volatile
    private var loaded = false

    /**
     * Load the trained model from Android assets.
     *
     * Loading is synchronized so concurrent callers cannot
     * partially initialize the model.
     */
    @Synchronized
    fun loadModel() {
        if (loaded) return

        try {
            val jsonText = appContext.assets
                .open("char_model.json")
                .bufferedReader()
                .use { it.readText() }

            val root = JSONObject(jsonText)

            val modelN = root.optInt("n", 3)

            // Character n-gram models require at least a bigram.
            n = modelN.coerceIn(2, 5)

            val ngrams = root.optJSONObject("ngrams")
                ?: JSONObject()

            ngramProbabilities.clear()

            val keys = ngrams.keys()

            while (keys.hasNext()) {
                val rawKey = keys.next()

                val key = rawKey
                    .lowercase(Locale.ROOT)
                    .trim()

                if (key.isEmpty()) continue

                val value = ngrams.optDouble(
                    rawKey,
                    Double.NaN
                )

                if (!value.isFinite() || value <= 0.0) {
                    continue
                }

                ngramProbabilities[key] = value
            }

            loaded = true

        } catch (e: Exception) {
            ngramProbabilities.clear()
            loaded = false
            e.printStackTrace()
        }
    }

    /**
     * Kept for compatibility with the old SuggestionEngine.
     *
     * The Android model is pre-trained in Python.
     */
    fun train(text: String) {
        loadModel()
    }

    /**
     * Score how natural a Roman word looks.
     *
     * Higher score = more likely according to the
     * trained Roman Nepali character model.
     */
    fun score(wordInput: String): Double {

        loadModel()

        val word = cleanWord(wordInput)

        if (word.isEmpty()) {
            return -100.0
        }

        val padded = "^".repeat(n - 1) + word + "$"

        var totalScore = 0.0

        for (i in 0..padded.length - n) {

            val gram = padded.substring(i, i + n)

            val probability =
                ngramProbabilities[gram]
                    ?: UNKNOWN_PROBABILITY

            totalScore += ln(
                probability.coerceAtLeast(UNKNOWN_PROBABILITY)
            )
        }

        return totalScore
    }

    /**
     * Generate possible Roman Nepali words beginning
     * with the supplied prefix.
     *
     * These words do NOT need to exist in the Trie.
     */
    fun generate(
        prefixInput: String,
        maxResults: Int = 30,
        minLength: Int = 3,
        maxLength: Int = 15
    ): List<String> {

        loadModel()

        val prefix = cleanWord(prefixInput)

        if (prefix.isEmpty()) {
            return emptyList()
        }

        if (maxResults <= 0) {
            return emptyList()
        }

        if (minLength < 1 || maxLength < 1) {
            return emptyList()
        }

        if (minLength > maxLength) {
            return emptyList()
        }

        if (prefix.length > maxLength) {
            return emptyList()
        }

        if (prefix.length == maxLength) {
            return if (prefix.length >= minLength) {
                listOf(prefix)
            } else {
                emptyList()
            }
        }

        val candidates = HashMap<String, Double>()

        /*
         * Beam search.
         *
         * We keep only the strongest candidates at every
         * generation level so the keyboard doesn't create
         * thousands of nonsense strings.
         */
        var currentLevel = mutableListOf(
            Candidate(prefix, score(prefix))
        )

        while (currentLevel.isNotEmpty()) {

            val nextLevel = ArrayList<Candidate>()

            for (candidate in currentLevel) {

                val word = candidate.word

                /*
                 * A word can finish here.
                 */
                if (word.length >= minLength) {

                    val finalScore =
                        candidate.score + endingScore(word)

                    candidates[word] = maxOf(
                        candidates[word]
                            ?: Double.NEGATIVE_INFINITY,
                        finalScore
                    )
                }

                /*
                 * Stop extending long words.
                 */
                if (word.length >= maxLength) {
                    continue
                }

                /*
                 * Generate next characters.
                 */
                for (char in nextCharacters(word)) {

                    val nextWord = word + char

                    nextLevel.add(
                        Candidate(
                            nextWord,
                            score(nextWord)
                        )
                    )
                }
            }

            /*
             * Beam width.
             */
            currentLevel = nextLevel
                .sortedByDescending { it.score }
                .take(BEAM_WIDTH)
                .toMutableList()
        }

        return candidates
            .entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { it.key }
    }

    /**
     * Find characters that are statistically likely to
     * follow the current word.
     *
     * The context uses the same '^' start padding as score().
     * This is important for short prefixes such as "k".
     */
    private fun nextCharacters(current: String): List<Char> {

        val contextLength = minOf(
            n - 1,
            current.length
        )

        val rawContext = current.takeLast(contextLength)

        val context = if (current.length < n - 1) {
            "^".repeat(n - 1 - current.length) + rawContext
        } else {
            rawContext
        }

        val candidates =
            ArrayList<Pair<Char, Double>>()

        for (char in alphabet) {

            val gram = context + char

            val probability =
                ngramProbabilities[gram]

            if (probability != null && probability > 0.0) {
                candidates.add(
                    char to probability
                )
            }
        }

        /*
         * If there is no exact character continuation,
         * allow vowels as a safe fallback.
         */
        if (candidates.isEmpty()) {
            return FALLBACK_VOWELS
        }

        return candidates
            .sortedByDescending { it.second }
            .take(MAX_NEXT_CHARACTERS)
            .map { it.first }
    }

    /**
     * Small bonus for common Roman Nepali word endings.
     */
    private fun endingScore(word: String): Double {

        return when {
            word.endsWith("a") -> 0.25
            word.endsWith("i") -> 0.20
            word.endsWith("o") -> 0.15
            word.endsWith("e") -> 0.10
            word.endsWith("u") -> 0.10
            else -> 0.0
        }
    }

    /**
     * Remove anything that isn't a-z.
     */
    private fun cleanWord(input: String): String {
        return input
            .lowercase(Locale.ROOT)
            .filter {
                it in 'a'..'z'
            }
    }

    private data class Candidate(
        val word: String,
        val score: Double
    )

    private companion object {
        const val UNKNOWN_PROBABILITY = 0.000001
        const val BEAM_WIDTH = 40
        const val MAX_NEXT_CHARACTERS = 8

        val FALLBACK_VOWELS = listOf(
            'a',
            'e',
            'i',
            'o',
            'u'
        )
    }
}

