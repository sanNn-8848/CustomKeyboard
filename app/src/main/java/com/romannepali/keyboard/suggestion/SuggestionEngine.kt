package com.romannepali.keyboard.suggestion

import kotlin.math.abs
import kotlin.math.ln

/** Where a candidate came from. Lets the IME decide how to trust it. */
enum class CandidateSource {
    PREFIX, DICTIONARY, LEARNED, PERSONAL, NGRAM, CHAR_MODEL, TYPO, SPLIT_MERGE, REPEATED, EMOJI
}

/** Everything the engine needs to know about what the user is typing. */
data class PredictionContext(
    val currentWord: String,
    val previousWords: List<String> = emptyList(),
    val sentenceStart: Boolean = false,
    val cursorPosition: Int = 0
)

/** A ranked suggestion with enough detail to act on it intelligently. */
data class PredictionResult(
    val word: String,
    val score: Float,
    val confidence: Float,
    val source: CandidateSource,
    val isCorrection: Boolean = false
)

/** Result of an autocorrect decision. */
data class Correction(
    val word: String,
    val confidence: Float,
    val isCorrection: Boolean
)

class SuggestionEngine(private val context: android.content.Context) {
    private val trie = Trie()
    private val ngramModel = NgramModel()
    private val charModel = CharModel(context)
    private val store = DictionaryStore(context)

    private val learnedWords = HashMap<String, Int>()

    private val scoreCache = HashMap<String, Double>()
    private var knownWordsCache: List<String>? = null

    // Context for next-word prediction (previous finished words).
    private val contextWords = mutableListOf<String>()
    private val maxContextSize = 3

    init {
        loadDictionary()
        loadBigrams()
        charModel.loadModel()
        learnedWords.putAll(store.loadLearned())
        store.loadSaved().forEach { trie.insert(it, SAVED_WORD_FREQUENCY) }
    }

    // ------------------------------------------------------------------
    // Layer weights. Tuned by the accuracy/benchmark tests; the constants
    // live here so they are easy to sweep without touching the pipeline.
    // ------------------------------------------------------------------
    private val weights: Map<CandidateSource, Double> = mapOf(
        CandidateSource.PREFIX to 1.2,
        CandidateSource.DICTIONARY to 1.0,
        CandidateSource.LEARNED to 0.9,
        CandidateSource.PERSONAL to 0.9,
        CandidateSource.NGRAM to 1.5,
        CandidateSource.TYPO to 1.3,
        CandidateSource.CHAR_MODEL to 0.25,
        CandidateSource.SPLIT_MERGE to 0.6,
        CandidateSource.REPEATED to 0.7,
        CandidateSource.EMOJI to 0.35
    )

    fun predict(context: PredictionContext, limit: Int = 3): List<PredictionResult> {
        val input = context.currentWord.trim().lowercase()
        if (input.isEmpty()) return emptyList()

        val prev = context.previousWords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val allKnown = getAllKnownWords()

        // Per-candidate, per-source contributions. Kept raw (0..1 scale per layer)
        // and weighted only when the final score is computed.
        val contributions = HashMap<String, HashMap<CandidateSource, Double>>()

        fun bump(word: String, source: CandidateSource, value: Double) {
            val map = contributions.getOrPut(word) { HashMap() }
            map[source] = maxOf(map[source] ?: 0.0, value)
        }

        // 1a. Dictionary / personal / learned prefix matches (all live in the trie).
        val prefixResults = trie.search(input)
        prefixResults.forEach { (word, frequency) ->
            bump(word, CandidateSource.PREFIX, input.length.toDouble() / maxOf(1, word.length))
            bump(word, CandidateSource.DICTIONARY, freqLog(frequency))
            if (frequency == SAVED_WORD_FREQUENCY) bump(word, CandidateSource.PERSONAL, 1.0)
        }

        // 1b. Fuzzy corrections for misspelled input (recovers the nearest known word).
        // Only runs when the input isn't already a recognisable prefix/word, so common
        // prefixes aren't flooded with 1-2 edit "noise" words. Only strong matches
        // (>=60% similar, <=2 edits, length within 3) count as typos.
        if (input.length >= 3 && prefixResults.isEmpty()) {
            for (word in allKnown) {
                if (abs(word.length - input.length) > 3) continue
                val dist = levenshtein(input, word)
                if (dist > 2) continue
                val similarity = 1.0 - dist.toDouble() / maxOf(input.length, word.length, 1)
                if (similarity < 0.6) continue
                bump(word, CandidateSource.TYPO, similarity)
            }
        }

        // 1c. Context: n-gram next-word boost, newest previous word weighted highest.
        prev.forEachIndexed { index, word ->
            val recency = when (index) {
                0 -> 1.0
                1 -> 0.6
                else -> 0.35
            }
            for ((next, frequency) in ngramModel.getNextWordCandidates(word)) {
                if (next.startsWith(input)) {
                    bump(next, CandidateSource.NGRAM, recency * minOf(1.0, frequency / 100.0))
                }
            }
        }

        // 1d. Words this user typed before.
        for ((word, count) in learnedWords) {
            if (word.startsWith(input)) {
                bump(word, CandidateSource.LEARNED, ln(1.0 + count) / 5.0)
            }
        }

        // 1e. Repeated trailing characters: "chaaa" -> "cha", "soooo" -> "so".
        collapseRepeated(input)?.let { base ->
            if (base != input && trie.contains(base)) {
                bump(base, CandidateSource.REPEATED, 0.8)
            }
        }

        // 2. Score each candidate as a weighted sum of its source layers.
        var results = contributions
            .map { (word, sources) ->
                val weighted = HashMap(sources.mapValues { (source, value) -> weights[source]!! * value })
                // The character model participates in ranking, not just fill.
                weighted.merge(
                    CandidateSource.CHAR_MODEL,
                    weights[CandidateSource.CHAR_MODEL]!! * normCharScore(word),
                    Double::plus
                )
                val total = weighted.values.sum()
                val best = weighted.maxByOrNull { it.value }!!.key
                PredictionResult(
                    word = word,
                    score = total.toFloat(),
                    confidence = 0f,
                    source = best,
                    isCorrection = best == CandidateSource.TYPO || best == CandidateSource.REPEATED
                )
            }
            .sortedByDescending { it.score }

        // 3. Fill remaining slots: split/merge, then character model, then emoji.
        val taken = results.map { it.word }.toMutableSet()
        val room = { limit - taken.size }

        if (room() > 0) {
            splitCandidates(input)
                .filter { it !in taken }
                .take(room())
                .forEach {
                    taken.add(it)
                    results = results.toMutableList().apply {
                        add(PredictionResult(it, SPLIT_SCORE, 0f, CandidateSource.SPLIT_MERGE))
                    }
                }
        }

        if (room() > 0) {
            emojiCandidates(input)
                .filter { it !in taken }
                .take(room())
                .forEach {
                    taken.add(it)
                    results = results.toMutableList().apply {
                        add(PredictionResult(it, EMOJI_SCORE, 0f, CandidateSource.EMOJI))
                    }
                }
        }

        if (room() > 0) {
            charModel.generate(
                prefixInput = input,
                maxResults = 20,
                minLength = maxOf(3, input.length),
                maxLength = 15
            ).filter { it !in taken }
                .take(room())
                .forEach {
                    taken.add(it)
                    results = results.toMutableList().apply {
                        add(PredictionResult(it, CHAR_SCORE, 0f, CandidateSource.CHAR_MODEL))
                    }
                }
        }

        // 4. Confidence from the top-2 spread; decays down the list.
        val ranked = results.take(limit)
        if (ranked.isEmpty()) return emptyList()
        return withConfidence(ranked)
    }

    private fun withConfidence(ranked: List<PredictionResult>): List<PredictionResult> {
        // Fills (char-model, emoji, split/merge) are last-resort tiers, not rivals:
        // they must not dilute how confident we are in the real candidates.
        val real = ranked.filter {
            it.source != CandidateSource.CHAR_MODEL &&
                it.source != CandidateSource.EMOJI &&
                it.source != CandidateSource.SPLIT_MERGE
        }

        val top = real.firstOrNull()?.score?.toDouble()
            ?: return ranked.map { it.copy(confidence = 0f) }
        val second = real.getOrNull(1)?.score?.toDouble() ?: -1.0

        val firstConfidence = if (real.size >= 2 && top > 0) {
            val spread = (top - second) / top
            (spread * 0.7 + top * 0.3).coerceIn(0.0, 1.0)
        } else {
            1.0
        }

        return ranked.mapIndexed { index, result ->
            result.copy(confidence = (firstConfidence - 0.15 * index).coerceAtLeast(0.0).toFloat())
        }
    }

    /**
     * Decomposes a no-space string into "prefix suffix" where the prefix is a
     * known word (dictionary, saved or learned) and the suffix is either another
     * known word or a common Nepali grammatical suffix. "lamoharu" -> "lamo haru".
     */
    private fun splitCandidates(word: String): List<String> {
        if (word.length < 4) return emptyList()

        val hits = ArrayList<Pair<String, Pair<Int, Boolean>>>()
        for (len in 2 until word.length) {
            val prefix = word.substring(0, len)
            val suffix = word.substring(len)
            if (suffix.length < 2) continue
            if (!trie.contains(prefix)) continue

            val suffixIsFullWord = trie.contains(suffix)
            if (!suffixIsFullWord && suffix !in KNOWN_SUFFIXES) continue

            hits.add("$prefix $suffix" to (trie.getFrequency(prefix) to suffixIsFullWord))
        }

        hits.sortWith(
            compareByDescending<Pair<String, Pair<Int, Boolean>>> { it.second.second }
                .thenByDescending { it.second.first }
        )
        return hits.map { it.first }.distinct()
    }

    /** Emoji candidates offered when prefix clearly points at a common word. */
    private fun emojiCandidates(input: String): List<String> {
        if (input.length < 2) return emptyList()
        return EMOJI_INPUT
            .filter { (word, _) -> word.startsWith(input) || input.startsWith(word) }
            .map { it.second }
            .distinct()
    }

    /**
     * Performs a conservative autocorrect. Returns a Correction with isCorrection=true
     * only when the input is unknown and a near dictionary word is clearly better.
     */
    fun getAutoCorrection(
        word: String,
        previousWords: List<String> = emptyList()
    ): Correction {
        val input = word.trim().lowercase()
        if (input.isEmpty()) return Correction(input, 0f, false)
        if (trie.contains(input)) return Correction(input, 1f, false)

        val best = predict(PredictionContext(input, previousWords), limit = 5).firstOrNull { result ->
            result.isCorrection &&
                levenshtein(input, result.word) <= 2 &&
                abs(result.word.length - input.length) <= 2
        }

        return if (best != null) {
            Correction(best.word, best.confidence, true)
        } else {
            Correction(input, 0f, false)
        }
    }

    /** Backwards-compatible word-only suggestions (the IME uses [predict]). */
    fun getSuggestions(prefix: String, limit: Int = 3): List<String> =
        predict(PredictionContext(currentWord = prefix), limit = limit).map { it.word }

    fun learnWord(word: String) {
        val clean = word.lowercase()
        if (clean.isBlank()) return
        learnedWords.merge(clean, 1, Int::plus)
        trie.insert(clean, learnedWords[clean] ?: 1)
        knownWordsCache = null
        store.saveLearned(learnedWords)
    }

    fun updateContext(word: String) {
        val clean = word.lowercase()
        if (clean.isBlank()) return

        // Learn a soft bigram from the user's real typing.
        contextWords.lastOrNull()?.let { previous ->
            ngramModel.addBigram(previous, clean, 4)
        }

        contextWords.add(clean)
        if (contextWords.size > maxContextSize) {
            contextWords.removeAt(0)
        }
    }

    fun addPersonalWord(word: String): Boolean {
        val clean = word.trim().lowercase()
        if (clean.isEmpty()) return false
        for (ch in clean) {
            if (!ch.isLetter() && ch != ' ' && ch != '\'') return false
        }

        val saved = store.loadSaved().toMutableList()
        if (clean !in saved) {
            saved.add(clean)
            store.saveSaved(saved)
            trie.insert(clean, SAVED_WORD_FREQUENCY)
            knownWordsCache = null
        }
        return true
    }

    fun removePersonalWord(word: String) {
        val saved = store.loadSaved().toMutableList()
        if (saved.remove(word)) {
            store.saveSaved(saved)
        }
        trie.remove(word)
        knownWordsCache = null
    }

    fun getSavedWords(): List<String> = store.loadSaved()

    fun getLearnedWords(): List<Pair<String, Int>> =
        learnedWords.entries.map { it.key to it.value }
            .sortedByDescending { it.second }

    fun removeLearnedWord(word: String) {
        if (learnedWords.remove(word.lowercase()) != null) {
            trie.remove(word.lowercase())
            knownWordsCache = null
            store.saveLearned(learnedWords)
        }
    }

    fun clearLearnedWords() {
        learnedWords.keys.forEach { trie.remove(it.lowercase()) }
        learnedWords.clear()
        knownWordsCache = null
        store.saveLearned(learnedWords)
    }

    fun restoreLearned(words: Map<String, Int>) {
        words.forEach { (word, count) ->
            learnedWords[word] = count
            trie.insert(word.lowercase(), count)
        }
        knownWordsCache = null
        store.saveLearned(learnedWords)
    }

    /** Collapses a trailing run of 3+ identical letters ("heyyy" -> "hey"). */
    private fun collapseRepeated(word: String): String? {
        if (word.length <= 3) return null
        val last = word.last()
        var run = 0
        for (i in word.indices.reversed()) {
            if (word[i] == last) run++ else break
        }
        if (run < 3) return null
        return word.dropLast(run - 1)
    }

    private fun freqLog(frequency: Int): Double = ln(1.0 + frequency) / 9.0

    private fun getAllKnownWords(): List<String> {
        knownWordsCache?.let { return it }
        return trie.getAllWords()
            .map { it.first }
            .also { knownWordsCache = it }
    }

    private fun charScore(word: String): Double {
        return scoreCache.getOrPut(word) { charModel.score(word) }
    }

    /** Maps the raw log-probability into a 0..1 naturalness value. */
    private fun normCharScore(word: String): Double {
        val raw = charScore(word)
        return (1.0 - (-raw / 25.0)).coerceIn(0.0, 1.0)
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val previous = IntArray(b.length + 1) { it }

        for (i in 1..a.length) {
            val current = IntArray(b.length + 1)
            current[0] = i

            for (j in 1..b.length) {
                val insert = current[j - 1] + 1
                val delete = previous[j] + 1
                val replace = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(insert, delete, replace)
            }

            for (j in current.indices) {
                previous[j] = current[j]
            }
        }

        return previous[b.length]
    }

    // ------------------------------------------------------------------
    // Static dictionary data.
    // ------------------------------------------------------------------

    private fun loadDictionary() {
        val commonWords = dictionaryWords()
        commonWords.forEach { (word, freq) -> trie.insert(word, freq) }
    }

    private fun loadBigrams() {
        val bigrams = listOf(
            "ma" to "garchu", "ma" to "cha", "timi" to "kasto",
            "huss" to "la", "thik" to "cha", "namaste" to "kasto",
            "mero" to "naam", "timro" to "naam", "aaja" to "k",
            "bholi" to "k", "k" to "huncha", "k" to "bhayo",
            "dhanyabaad" to "la", "maaph" to "la", "ramro" to "cha",
            "naramro" to "cha", "thulo" to "cha", "sano" to "cha"
        )

        bigrams.forEach { (word1, word2) ->
            ngramModel.addBigram(word1, word2, 100)
        }
    }

    private fun dictionaryWords(): Map<String, Int> = mapOf(
        // Greetings
        "namaste" to 1000, "namaskar" to 900, "namaste cha" to 800,
        "thik cha" to 850, "la" to 900, "huss" to 800,
        "subha prabhat" to 700, "shubha ratri" to 750, "namaskaram" to 700,

        // Question words
        "kina" to 900, "ke" to 1000, "ko" to 900, "kaha" to 900,
        "kata" to 850, "kahile" to 850, "kina ho" to 800,
        "kina lagyo" to 750, "ke bhayo" to 900, "ke chha" to 950,
        "k kaso" to 800, "kasari" to 800, "kina yo" to 700,
        "kati" to 880, "kasto" to 880, "kato" to 700,
        "keta" to 850, "keto" to 800,

        // Common pronouns & linking words
        "ma" to 1000, "timi" to 950, "hami" to 900, "tapaai" to 850,
        "yesto" to 800, "tyesto" to 750, "nazar" to 700,
        "mero" to 950, "mera" to 900, "meri" to 850, "timro" to 900, "hamro" to 850, "usko" to 800,
        "tero" to 880, "tera" to 850, "teri" to 800, "tesko" to 800,
        "usle" to 750, "uniharu" to 700, "hajur" to 800,
        "unki" to 750, "bhai" to 900, "didi" to 850,

        // Common words
        "yo" to 1000, "tyo" to 900, "eha" to 800, "tyaha" to 750,
        "cha" to 1000, "chaina" to 950, "thyo" to 900, "hola" to 850,
        "garchu" to 900, "garcha" to 850, "gareko" to 800,
        "khaanu" to 750, "khana" to 800, "pini" to 850,
        "sutaunu" to 700, "sutna" to 750, "jana" to 800,
        "aunu" to 850, "janu" to 800, "gumna" to 700,

        // Family
        "bua" to 900, "aamaa" to 950, "dai" to 850, "bhai" to 900,
        "didi" to 850, "bahini" to 900, "kaka" to 800, "kaki" to 800,
        "mama" to 800, "mami" to 800, "sasa" to 750, "sasi" to 750,
        "chhora" to 850, "chhori" to 850, "bou" to 800, "buba" to 850,

        // Numbers
        "ek" to 1000, "dui" to 950, "tin" to 900, "char" to 850,
        "panch" to 800, "chha" to 750, "saat" to 700, "aath" to 650,
        "nau" to 600, "dus" to 550, "ekkis" to 500, "bais" to 450,
        "tis" to 400, "saya" to 500, "hajar" to 450,

        // Days
        "aaja" to 900, "bholi" to 850, "hijo" to 800,
        "paraahi" to 700, "sombaar" to 750, "mangalbaar" to 700,
        "budhabaar" to 700, "bihibaar" to 700, "sukrabaar" to 700,
        "saniibaar" to 700, "aaitabaar" to 650,

        // Time
        "beluka" to 800, "bihaan" to 850, "digra" to 750,
        "rat" to 800, "din" to 850, "mahina" to 700, "barsha" to 650,
        "bihana" to 850, "sandhya" to 750, "aatma" to 700,

        // Actions
        "kam" to 900, "padh" to 850, "lekh" to 800, "bol" to 850,
        "her" to 800, "ja" to 900, "aa" to 950, "de" to 900,
        "lau" to 850, "kha" to 800, "pi" to 800, "soch" to 750,
        "bujh" to 700, "sik" to 750, "sikhaa" to 700,
        "garna" to 900, "lena" to 850, "dinu" to 800, "paunu" to 800,
        "basnu" to 750, "uthnu" to 750, "hidnu" to 700, "daudanu" to 650,
        "khelnu" to 800, "padhnu" to 850, "lekhnu" to 800, "bujhnu" to 750,
        "bolnu" to 850, "sunna" to 800, "hernu" to 750,

        // Adjectives
        "ramro" to 900, "naramro" to 850, "thulo" to 850, "sano" to 850,
        "lamo" to 800, "choto" to 800, "gahro" to 750, "sajilo" to 800,
        "naya" to 850, "purano" to 800, "ramailo" to 900,
        "sundar" to 800, "sundari" to 750, "mitho" to 850,
        "guliyo" to 750, "tito" to 700, "nilo" to 700, "rato" to 800,
        "pahelo" to 750, "hario" to 750, "kalo" to 800, "seto" to 750,

        // Objects
        "ghar" to 900, "kamra" to 800, "bato" to 850, "pasal" to 800,
        "kitab" to 750, "kalam" to 700, "daaki" to 650, "paati" to 700,
        "chhaaro" to 700, "batti" to 650, "paani" to 850, "aago" to 800,
        "geet" to 750, "tara" to 700, "chandrama" to 700, "suraj" to 750,
        "bimala" to 650, "khet" to 700, "bagaicha" to 700,

        // Complex / longer words
        "dhanyabaad" to 950, "dhanyawad" to 900, "maaph garne" to 700,
        "maaph" to 750, "bhagya" to 700, "ekaant" to 700,
        "prasna" to 700, "pratibha" to 700, "shakti" to 700,
        "samaya" to 750, "saman" to 700, "sahai" to 700,
        "jeewan" to 800, "mritra" to 700, "viśwās" to 700,
        "bishwasa" to 750, "krya" to 700, "sandarbha" to 650,
        "sambhanda" to 700, "vachan" to 700, "bishal" to 700,
        "sthiti" to 700, "bichar" to 800, "bichara" to 750,
        "kura" to 900, "katha" to 800, "kahani" to 850,
        "dinhara" to 750, "rahara" to 750, "bachan" to 750,

        // Core verbs & copulas
        "ho" to 1000, "hoina" to 850, "cha" to 1000, "chhaina" to 900,
        "thiyo" to 850, "thiena" to 700, "bhayo" to 900, "huncha" to 900,
        "hunchha" to 850, "paryo" to 800, "parcha" to 750, "lagyo" to 850,
        "khao" to 800, "khan" to 750, "ja" to 900, "aa" to 950, "de" to 900,
        "du" to 700, "gara" to 800, "haru" to 900, "bhaneko" to 750,
        "bhanne" to 800, "bhane" to 750, "garne" to 800, "gare" to 800,
        "garera" to 800, "garchhu" to 800, "garchha" to 800, "garna" to 900,
        "jane" to 850, "jau" to 800, "janchu" to 800, "aau" to 800,
        "aayo" to 900, "aayeo" to 850, "gayo" to 850, "gaeko" to 700,
        "saknu" to 750, "sakda" to 700, "parchhu" to 700, "parchha" to 700,

        // Adverbs & quantifiers
        "dherai" to 900, "thorai" to 800, "ekdam" to 750, "ali" to 850,
        "ek" to 1000, "dui" to 950, "tin" to 900, "char" to 850,
        "panch" to 800, "hajur" to 800, "yahi" to 800, "tyahi" to 750,
        "yasto" to 800, "tyasto" to 750, "sadhai" to 800, "kahilepani" to 650,
        "chitai" to 750, "bistarai" to 750, "pharkera" to 700,

        // People & relationships
        "sathi" to 850, "sathi ho" to 800, "sanchai" to 850, "sancho" to 850,
        "sukhi" to 800, "khusi" to 850, "dukhi" to 750, "maya" to 850,
        "prem" to 800, "logne" to 700, "swasni" to 700, "buhari" to 750,
        "jethan" to 650, "kanchha" to 650, "aama" to 850,
        "aamaa" to 950, "bua" to 900, "haamro" to 800, "timro" to 900,

        // Grammar particles
        "lai" to 900, "le" to 900, "baata" to 850, "bata" to 850,
        "sanga" to 800, "sangai" to 750, "maa" to 850, "ma" to 900,
        "mathi" to 850, "tala" to 850, "agadi" to 800, "pachadi" to 750,
        "najik" to 700, "para" to 700, "bhitra" to 800, "bahira" to 800,
        "mujhi" to 700, "bichma" to 700, "chheu" to 700, "chhaau" to 650,

        // Food & things
        "bhat" to 850, "daal" to 800, "tarkari" to 750, "dahi" to 750,
        "dudh" to 750, "chiya" to 800, "paisa" to 850, "paisa ho" to 750,
        "gheu" to 650, "nun" to 700, "chini" to 700, "paani" to 850,
        "kinmel" to 700, "kura ho" to 750,

        // Places & nature
        "gaun" to 700, "pahar" to 750, "nadi" to 700, "sahara" to 700,
        "sahari" to 700, "banao" to 700, "jungle" to 700,

        // Memorable facts
        "thaha" to 800, "thaha chhaina" to 750, "thik" to 900,
        "galti" to 850, "galat" to 800, "sahi" to 900, "sahi ho" to 800,
        "bilkul" to 800, "thik chha" to 850, "laijau" to 750,
        "line" to 800, "lau" to 850, "hera" to 800, "suna" to 750,
        "aadha" to 750, "pura" to 750, "purnata" to 700,

        // Common phrases (multi-word)
        "mero naam" to 950, "timro naam" to 850, "k huncha" to 800,
        "k bhayo" to 850, "k garnu" to 800, "thik cha" to 900,
        "huss" to 900, "la" to 950, "dhanyabaad" to 800, "maaph" to 750,
        "kasto cha" to 850, "thik thak" to 800, "namaste" to 1000,
        "sarga ko" to 700, "bhu ya" to 700,
        "keta ho" to 850, "keto ho" to 800, "mero keta" to 800,
        "mero naam ram ho" to 750, "keta namaune" to 700,

        // Names / places / everyday words useful for emoji + autocorrect tests
        "manche" to 800, "manxe" to 500, "chhu" to 750, "chha" to 1000,
        "hey" to 700, "so" to 700, "hoina" to 850
    )

    private companion object {
        /** Frequency used for words added to the personal dictionary so they rank high. */
        const val SAVED_WORD_FREQUENCY = 5000

        /** Small fill scores for the last-resort candidate tiers (below layer weights). */
        const val SPLIT_SCORE = 0.75f
        const val CHAR_SCORE = 0.45f
        const val EMOJI_SCORE = 0.25f

        /** Common Nepali grammatical suffixes used by the no-space splitter. */
        val KNOWN_SUFFIXES = setOf(
            "haru", "lai", "le", "ko", "ka", "ki", "ma", "la",
            "bata", "dekhi", "samma", "pani", "nai", "ta", "ni",
            "sanga", "gari", "jasto"
        )

        /** Small words -> emoji map for light emoji suggestions. */
        val EMOJI_INPUT = listOf(
            "khusi" to "\uD83D\uDE0A", "khushi" to "\uD83D\uDE0A", "maya" to "\u2764\uFE0F",
            "prem" to "\uD83D\uDC98", "dherai" to "\uD83D\uDC4D", "khana" to "\uD83C\uDF5A",
            "bhat" to "\uD83C\uDF5A", "ghar" to "\uD83C\uDFE0", "paani" to "\uD83D\uDCA7",
            "suraj" to "\u2600\uFE0F", "chandrama" to "\uD83C\uDF19", "keta" to "\uD83D\uDC66",
            "keti" to "\uD83D\uDC67", "bhai" to "\uD83D\uDC66", "didi" to "\uD83D\uDC67",
            "aama" to "\uD83D\uDC69", "bua" to "\uD83D\uDC68", "thik" to "\uD83D\uDC4C",
            "aaja" to "\uD83D\uDCC5", "bholi" to "\u23F3", "rat" to "\uD83C\uDF19",
            "din" to "\uD83C\uDF06", "kaho" to "\uD83D\uDE4B", "la" to "\uD83D\uDC4B"
        )
    }
}