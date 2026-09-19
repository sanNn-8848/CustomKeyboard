package com.romannepali.keyboard.suggestion

import org.json.JSONObject
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
    private val ngramModel = NgramModel(context)
    private val charModel = CharModel(context)
    private val store = DictionaryStore(context)

    private val learnedWords = HashMap<String, Int>()
    private val suppressedWords = store.loadSuppressed().toMutableSet()
    private val favoriteWords = store.loadFavorites().toMutableSet()

    // Base ranks from the shipped vocabulary, so removing a learned/personal
    // entry can restore the exact original frequency instead of killing it.
    private val baseFrequencies = HashMap<String, Int>()

    private val scoreCache = HashMap<String, Double>()
    private var knownWordsCache: List<String>? = null

    // Variant-group data from roman_words.json (canonical spelling per group).
    private var variantGroups: Map<String, List<String>> = emptyMap()
    private var wordToGroupKey: Map<String, String> = emptyMap()

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
        CandidateSource.LEARNED to 1.0,
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
        val prev = context.previousWords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        // Word boundary: generate the NEXT word from context bigrams/trigrams.
        if (input.isEmpty()) return predictNextWord(prev, limit)

        val allKnown = getAllKnownWords()

        val contributions = HashMap<String, HashMap<CandidateSource, Double>>()

        fun bump(word: String, source: CandidateSource, value: Double) {
            val map = contributions.getOrPut(word) { HashMap() }
            map[source] = maxOf(map[source] ?: 0.0, value)
        }

        // 1a. Dictionary / personal / learned prefix matches (all live in the trie).
        val prefixResults = trie.search(input)
        prefixResults
            .take(MAX_PREFIX_CANDIDATES)
            .forEach { (word, frequency) ->
                bump(word, CandidateSource.PREFIX, input.length.toDouble() / maxOf(1, word.length))
                bump(word, CandidateSource.DICTIONARY, freqLog(frequency))
                if (frequency == SAVED_WORD_FREQUENCY) bump(word, CandidateSource.PERSONAL, 1.0)
            }

        // 1b. Variant expansion: the canonical spelling of the user's normalized
        // input (e.g. "aja" -> canonical "aaja") even though it doesn't prefix-match.
        expandVariants(input)
            .filter { it !in contributions }
            .forEach { word ->
                bump(word, CandidateSource.PREFIX, VARIANT_BOOST)
                bump(word, CandidateSource.DICTIONARY, freqLog(trie.getFrequency(word)))
            }
        // Collapse all variants of the same group into the canonical spelling so a
        // keystroke surfaces one clean candidate (khusi/khushi/khusii -> khusi).
        dedupeVariants(contributions)
        if (contributions.isEmpty()) {
            // Also try matching a partial normalized key (typing "khush").
            val prefixKey = RomanNormalizer.normalize(input)
            if (prefixKey.isNotEmpty()) {
                variantGroups.forEach { (key, members) ->
                    if (key.startsWith(prefixKey)) {
                        dedupeVariants(contributions)
                        members.forEach { word ->
                            bump(word, CandidateSource.PREFIX, VARIANT_BOOST)
                            bump(word, CandidateSource.DICTIONARY, freqLog(trie.getFrequency(word)))
                        }
                        dedupeVariants(contributions)
                    }
                }
            }
        }

        // 1c. Fuzzy corrections for misspelled input (recover nearest known word).
        if (input.length >= 3 && contributions.isEmpty()) {
            for (word in allKnown) {
                if (abs(word.length - input.length) > 3) continue
                val dist = levenshtein(input, word)
                if (dist > 2) continue
                val similarity = 1.0 - dist.toDouble() / maxOf(input.length, word.length, 1)
                if (similarity < 0.6) continue
                // Nearby + COMMON wins: "namsate" -> "namaste", never the rare "namste".
                bump(word, CandidateSource.TYPO, similarity * 0.6 + freqLog(trie.getFrequency(word)))
            }
        }

        // 1d. Context: n-gram next-word boost, newest previous word weighted highest.
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

        // 1e. Words this user typed before are heavily personalised: any learned
        // word matching the prefix outranks generic dictionary matches.
        for ((word, count) in learnedWords) {
            if (word.startsWith(input)) {
                bump(word, CandidateSource.LEARNED, minOf(1.0, 0.6 + ln(1.0 + count) / 2.5))
            }
        }

        // 1f. Repeated trailing characters: "chaaa" -> "cha", "soooo" -> "so".
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

        // 2b. Favorites ride the magnetic rail: boost so they clear equal peers.
        if (favoriteWords.isNotEmpty()) {
            results = results
                .map {
                    if (it.word.lowercase() in favoriteWords)
                        it.copy(score = it.score + FAVORITE_BOOST)
                    else it
                }
                .sortedByDescending { it.score }
        }

        // 3. Fill remaining slots: split/merge, then emoji. Character-model fills are
        // disabled because they fabricate words that don't exist or make sense (e.g.
        // "lageet", "chhaau") — the keyboard should only ever suggest real words.

        val taken = results.map { it.word }.toMutableSet()
        val room = { limit - taken.size }

        // Split "meroharu" -> "mero haru" only when the string is not already a known
        // word; splitting "paani" produces junk splits that crowd out better fills.
        if (room() > 0 && !trie.contains(input)) {
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

        // 4. Confidence from the top-2 spread; decays down the list.
        val filtered = results.filter { it.word !in suppressedWords }
        val ranked = filtered.take(limit)
        if (ranked.isEmpty()) return emptyList()
        return withConfidence(ranked)
    }

    /** Next-word prediction at a word boundary, generated purely from the
     *  vocabulary + context n-grams. Returns real words only. */
    private fun predictNextWord(prev: List<String>, limit: Int): List<PredictionResult> {
        if (prev.isEmpty()) return emptyList()

        val next = if (prev.size >= 2) {
            ngramModel.getNextWordCandidates(prev[prev.size - 2], prev.last())
        } else {
            ngramModel.getNextWordCandidates(prev.last())
        }

        val seen = HashSet<String>()
        val results = ArrayList<PredictionResult>()
        for ((word, frequency) in next) {
            if (results.size >= limit) break
            if (word in suppressedWords) continue
            if (!trie.contains(word)) continue
            // One candidate tile per spelling (khusi/khushi/khusii -> khusi).
            val canonical = canonicalWord(word)
            if (!seen.add(canonical)) continue
            results.add(
                PredictionResult(
                    word = canonical,
                    score = (frequency + 1).toFloat(),
                    confidence = (1.0 - 0.1 * results.size).coerceAtLeast(0.0).toFloat(),
                    source = CandidateSource.NGRAM
                )
            )
        }
        return results.take(limit)
    }

    private fun canonicalWord(word: String): String {
        val key = wordToGroupKey[word] ?: return word
        val members = variantGroups[key] ?: return word
        return if (members.size > 1) members[0] else word
    }

    /** Returns words whose normalized key exactly matches the input's key. */
    private fun expandVariants(input: String): List<String> {
        val key = RomanNormalizer.normalize(input)
        if (key.isEmpty()) return emptyList()
        val members = variantGroups[key] ?: return emptyList()
        if (members.size <= 1) return emptyList()
        return members
    }

    /** Collapses group variants in the contributions map to the canonical word. */
    private fun dedupeVariants(contributions: HashMap<String, HashMap<CandidateSource, Double>>) {
        val toCanonical = HashMap<String, String>()
        for (word in contributions.keys) {
            val canon = canonicalWord(word)
            if (canon != word) toCanonical[word] = canon
        }
        for ((variant, canon) in toCanonical) {
            val sources = contributions.remove(variant) ?: continue
            val target = contributions.getOrPut(canon) { HashMap() }
            for ((source, value) in sources) {
                target[source] = maxOf(target[source] ?: 0.0, value)
            }
        }
    }

    private fun withConfidence(ranked: List<PredictionResult>): List<PredictionResult> {
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
     * Decomposes a no-space string into "prefix suffix".
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
        val count = (learnedWords[clean] ?: 0) + 1
        learnedWords[clean] = count
        // Never let a learned count clobber a saved/dictionary rank.
        if (count > trie.getFrequency(clean)) {
            trie.insert(clean, count)
        }
        knownWordsCache = null
        if (learnedWords.size > MAX_LEARNED_WORDS) trimLearnedWords()
        store.saveLearned(learnedWords)
    }

    fun updateContext(word: String) {
        val clean = word.lowercase()
        if (clean.isBlank()) return

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
        val clean = word.trim().lowercase()
        val saved = store.loadSaved().toMutableList()
        if (saved.remove(clean)) {
            store.saveSaved(saved)
        }
        restoreTrieEntry(clean)
        knownWordsCache = null
    }

    fun getSavedWords(): List<String> = store.loadSaved()

    /** Hides a word from future suggestions without erasing its learned history. */
    fun suppress(word: String) {
        if (suppressedWords.add(word.lowercase())) {
            store.saveSuppressed(suppressedWords)
        }
    }

    /** Re-enables a previously suppressed word (used by undo and settings). */
    fun unsuppress(word: String) {
        if (suppressedWords.remove(word.lowercase())) {
            store.saveSuppressed(suppressedWords)
        }
    }

    fun getSuppressedWords(): List<String> = suppressedWords.toList().sorted()

    fun clearSuppressedWords() {
        suppressedWords.clear()
        store.saveSuppressed(suppressedWords)
    }

    /** Boosts a word so it surfaces near the top of suggestions. */
    fun favorite(word: String) {
        if (favoriteWords.add(word.lowercase())) {
            store.saveFavorites(favoriteWords)
            scoreCache.clear()
        }
    }

    fun unfavorite(word: String) {
        if (favoriteWords.remove(word.lowercase())) {
            store.saveFavorites(favoriteWords)
            scoreCache.clear()
        }
    }

    fun isFavorite(word: String): Boolean = word.lowercase() in favoriteWords

    fun getFavorites(): List<String> = favoriteWords.toList().sorted()

    fun clearFavorites() {
        favoriteWords.clear()
        store.saveFavorites(favoriteWords)
        scoreCache.clear()
    }

    fun getLearnedWords(): List<Pair<String, Int>> =
        learnedWords.entries.map { it.key to it.value }
            .sortedByDescending { it.second }

    fun removeLearnedWord(word: String) {
        if (learnedWords.remove(word.lowercase()) != null) {
            restoreTrieEntry(word)
            knownWordsCache = null
            store.saveLearned(learnedWords)
        }
    }

    fun clearLearnedWords() {
        learnedWords.keys.forEach { restoreTrieEntry(it) }
        learnedWords.clear()
        knownWordsCache = null
        store.saveLearned(learnedWords)
    }

    fun restoreLearned(words: Map<String, Int>) {
        words.forEach { (word, count) ->
            learnedWords[word] = count
            // Restore the word's rightful rank (novel words come back at full count).
            if (count > trie.getFrequency(word.lowercase())) {
                trie.insert(word.lowercase(), count)
            }
        }
        knownWordsCache = null
        store.saveLearned(learnedWords)
    }

    /** Puts a word back to its pre-learning trie state: personal words keep their
     *  boost, dictionary words their base frequency, novel words disappear. */
    private fun restoreTrieEntry(word: String) {
        val key = word.lowercase()
        when {
            key in store.loadSaved() -> trie.insert(key, SAVED_WORD_FREQUENCY)
            baseFrequencies.containsKey(key) -> trie.insert(key, baseFrequencies.getValue(key))
            else -> trie.remove(key)
        }
    }

    /** Bounds the learned bucket: drops lowest-count words first so the personal
     *  history keeps only the user's genuinely frequent vocabulary. */
    private fun trimLearnedWords() {
        val overflow = learnedWords.size - MAX_LEARNED_WORDS
        if (overflow <= 0) return
        learnedWords.entries
            .sortedBy { it.value }
            .take(overflow)
            .forEach { (word, _) ->
                learnedWords.remove(word)
                restoreTrieEntry(word)
            }
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
    // Dictionary data: real vocabulary + variant groups from assets.
    // ------------------------------------------------------------------

    private fun loadDictionary() {
        val (words, groups, wordToKey) = loadVocabularyAsset()
            ?: loadEmbeddedFallback()

        variantGroups = groups

        val wordToGroup = HashMap<String, String>()
        groups.forEach { (key, members) -> members.forEach { wordToGroup[it] = key } }
        wordToGroup.putAll(wordToKey)
        wordToGroupKey = wordToGroup

        words.forEach { (word, freq) -> trie.insert(word, freq) }
        baseFrequencies.clear()
        baseFrequencies.putAll(words)
    }

    /** Loads roman_words.json once per process; trie is rebuilt per engine. */
    private fun loadVocabularyAsset(): Triple<List<Pair<String, Int>>, Map<String, List<String>>, Map<String, String>>? {
        companionVocab?.let { return it }
        return try {
            val text = context.assets.open("roman_words.json")
                .bufferedReader().use { it.readText() }
            val root = JSONObject(text)

            val words = ArrayList<Pair<String, Int>>()
            val arr = root.optJSONArray("words") ?: throw RuntimeException("missing words")
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val w = obj.optString("w")
                val f = obj.optInt("f", 0)
                if (w.isNotEmpty()) words.add(w to f)
            }

            val groups = HashMap<String, List<String>>()
            val groupObj = root.optJSONObject("groups") ?: JSONObject()
            val keys = groupObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val ja = groupObj.optJSONArray(key) ?: continue
                val members = ArrayList<String>(ja.length())
                for (i in 0 until ja.length()) {
                    members.add(ja.optString(i))
                }
                groups[key] = members
            }

            Triple<List<Pair<String, Int>>, Map<String, List<String>>, Map<String, String>>(
                words,
                groups,
                emptyMap()
            ).also { companionVocab = it }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadBigrams() {
        val unigramFrequencies = HashMap<String, Int>()
        trie.getAllWords().forEach { (word, freq) -> unigramFrequencies[word] = freq }
        ngramModel.loadModel(unigramFrequencies)
        // Personalised context bigrams (user typing habits).
        ngramModel.mergeUserContext(contextWords)
    }

    private fun loadEmbeddedFallback(): Triple<List<Pair<String, Int>>, Map<String, List<String>>, Map<String, String>> {
        val words = dictionaryWords().toList()
        return Triple(words, emptyMap(), emptyMap())
    }

    private fun dictionaryWords(): Map<String, Int> = mapOf(
        "namaste" to 1000, "namaskar" to 900, "la" to 900, "huss" to 800,
        "kina" to 900, "ke" to 1000, "ko" to 900, "kaha" to 900,
        "kata" to 850, "ma" to 1000, "timi" to 950, "hami" to 900,
        "mero" to 950, "ramro" to 900, "ghar" to 900, "cha" to 1000,
        "chaina" to 950, "garchu" to 900, "garcha" to 850, "janchu" to 800,
        "aaja" to 900, "bholi" to 850, "hey" to 700, "haru" to 900,
        "khusi" to 850, "khushi" to 850, "maya" to 850, "din" to 850,
        "kitab" to 750, "dhanyabaad" to 950, "garna" to 900, "khana" to 800
    )

    private companion object {
        /** Frequency used for words added to the personal dictionary so they rank high. */
        const val SAVED_WORD_FREQUENCY = 5000

        /** Small fill scores for the last-resort candidate tiers (below layer weights). */
        const val SPLIT_SCORE = 0.75f
        const val EMOJI_SCORE = 0.25f

        /** Extra score added to favorites so they rank ahead of equal peers. */
        const val FAVORITE_BOOST = 0.6f

        /** Prefix-layer value for variant expansions that don't prefix-match. */
        const val VARIANT_BOOST = 0.55

        /** Hard cap on prefix-scanned words so no single keystroke blows up. */
        const val MAX_PREFIX_CANDIDATES = 300

        /** Bound on the learned-words bucket; the lowest-count words are trimmed first. */
        const val MAX_LEARNED_WORDS = 2000

        /** Cached parsed asset so unit tests reuse the big JSON across engines. */
        var companionVocab: Triple<List<Pair<String, Int>>, Map<String, List<String>>, Map<String, String>>? = null

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