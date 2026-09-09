package com.romannepali.keyboard.suggestion

import android.content.Context
import org.json.JSONObject
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
 *
 * Example:
 *
 *     generate("ka")
 *
 * can produce:
 *     kata
 *     kati
 *     kasto
 *     kato
 *     kaha
 */
class CharModel(private val context: Context) {

    private var n = 3

    private val ngramProbabilities = HashMap<String, Double>()

    private val alphabet = "abcdefghijklmnopqrstuvwxyz"

    private var loaded = false

    /**
     * Load the trained model from Android assets.
     */
    fun loadModel() {
        if (loaded) return

        try {
            val jsonText = context.assets
                .open("char_model.json")
                .bufferedReader()
                .use { it.readText() }

            val root = JSONObject(jsonText)

            n = root.optInt("n", 3)

            val ngrams = root.getJSONObject("ngrams")

            val keys = ngrams.keys()

            while (keys.hasNext()) {
                val key = keys.next()
                val value = ngrams.getDouble(key)

                ngramProbabilities[key] = value
            }

            loaded = true

        } catch (e: Exception) {
            e.printStackTrace()
            loaded = false
        }
    }

    /**
     * Kept for compatibility with the old SuggestionEngine.
     *
     * The real Android model is loaded from char_model.json,
     * so this method is intentionally not used for training.
     */
    fun train(text: String) {
        // The model is pre-trained in Python.
        // Android loads char_model.json instead.
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
                ngramProbabilities[gram] ?: 0.000001

            totalScore += ln(
                probability.coerceAtLeast(0.000001)
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

        if (prefix.length >= maxLength) {
            return listOf(prefix)
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
                        candidate.score +
                                endingScore(word)

                    candidates[word] = maxOf(
                        candidates[word] ?: Double.NEGATIVE_INFINITY,
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

                    val nextScore = score(nextWord)

                    nextLevel.add(
                        Candidate(
                            nextWord,
                            nextScore
                        )
                    )
                }
            }

            /*
             * Beam width.
             *
             * Only keep the best 40 partial words.
             */
            currentLevel = nextLevel
                .sortedByDescending { it.score }
                .take(40)
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
     */
    private fun nextCharacters(current: String): List<Char> {

        val contextLength = minOf(
            n - 1,
            current.length
        )

        val context =
            current.takeLast(contextLength)

        val candidates =
            ArrayList<Pair<Char, Double>>()

        for (char in alphabet) {

            val gram = context + char

            /*
             * For a trigram model:
             *
             * current = "ka"
             * context = "ka"
             *
             * gram = "kat"
             */
            val probability =
                ngramProbabilities[gram]

            if (probability != null) {
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
            return listOf(
                'a',
                'e',
                'i',
                'o',
                'u'
            )
        }

        return candidates
            .sortedByDescending { it.second }
            .take(8)
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
            .lowercase()
            .filter {
                it in 'a'..'z'
            }
    }

    private data class Candidate(
        val word: String,
        val score: Double
    )
}
