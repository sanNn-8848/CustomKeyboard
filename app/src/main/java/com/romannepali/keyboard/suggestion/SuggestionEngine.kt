package com.romannepali.keyboard.suggestion

import kotlin.math.ln

class SuggestionEngine(private val context: android.content.Context) {
    private val trie = Trie()
    private val ngramModel = NgramModel()
    private val charModel = CharModel(context)
    private val store = DictionaryStore(context)

    private val learnedWords = HashMap<String, Int>()

    private val scoreCache = HashMap<String, Double>()
    private var knownWordsCache: List<String>? = null

    // Context for next-word prediction
    private val contextWords = mutableListOf<String>()
    private val maxContextSize = 3

    init {
    loadDictionary()
    charModel.loadModel()
    learnedWords.putAll(store.loadLearned())
    store.loadSaved().forEach { trie.insert(it, SAVED_WORD_FREQUENCY) }
      }

    private fun loadDictionary() {
        // Load common Roman Nepali words (frequency = higher number ranks first)
        val commonWords = mapOf(
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
            "usle" to 750, "uniharu" to 700, "timi" to 950, "tapaai" to 850, "hajur" to 800,
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

            // Complex / longer words (what you asked for)
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
            "mero naam ram ho" to 750, "keta namaune" to 700
        )

        commonWords.forEach { (word, freq) ->
            trie.insert(word, freq)
        }

        // Load bigrams
        loadBigrams()
    }

    private fun loadBigrams() {
        // Common word pairs in Roman Nepali
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

    private fun loadCharModel() {
        charModel.loadModel()
    }

    fun getSuggestions(prefix: String, limit: Int = 3): List<String> {
        val input = prefix.trim().lowercase()
        if (input.isEmpty()) return emptyList()

        val parts = input.split(Regex("\\s+"))
        val currentWord = parts.last()
        val previousWord = if (parts.size >= 2) parts[parts.size - 2] else null

        val knownWords = getAllKnownWords()
        val candidates = HashMap<String, Double>()

        /*
         * -------------------------------------------------
         * TIER 1: KNOWN words.
         * Prefix matches, fuzzy corrections, context and
         * learned words always outrank generated inventions.
         * -------------------------------------------------
         */

        // 1a. Words in the dictionary that start with the typed prefix.
        trie.search(currentWord).forEach { (word, frequency) ->
            val score = currentWord.length * 8.0
                    + 8.0
                    + ln(frequency + 1.0) * 3.0
                    + charScore(word) * 0.25
            candidates[word] = maxOf(candidates[word] ?: Double.NEGATIVE_INFINITY, score)
        }

        // 1b. Fuzzy corrections: the typed word is misspelled,
        //     recover the nearest known word (handles spaces too).
        if (currentWord.length >= 3) {
            for (word in knownWords) {
                if (kotlin.math.abs(word.length - currentWord.length) > 3) continue

                val distance = levenshtein(currentWord, word)
                if (distance <= 2) {
                    val frequency = trie.getFrequency(word)
                    val score = spellingScore(currentWord, word)
                            + charScore(word) * 0.25
                            + ln(frequency + 1.0) * 4.0
                    candidates[word] = maxOf(candidates[word] ?: Double.NEGATIVE_INFINITY, score)
                }
            }
        }

        // 1c. Context: next-word predictions.
        if (previousWord != null) {
            for ((word, frequency) in ngramModel.getNextWordCandidates(previousWord)) {
                if (!word.startsWith(currentWord)) continue
                candidates[word] = (candidates[word] ?: 0.0) + ln(frequency + 1.0) * 4.0
            }
        }

        // 1d. Words learned from this user's typing.
        for ((word, frequency) in learnedWords) {
            if (!word.startsWith(currentWord)) continue
            val score = currentWord.length * 8.0 + ln(frequency + 1.0) * 3.0
            candidates[word] = maxOf(candidates[word] ?: Double.NEGATIVE_INFINITY, score)
        }

        val knownResult = candidates
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .distinct()
            .take(limit)

        // Fill any remaining slots from the character model.
        val result = ArrayList(knownResult)
        // Split no-space compounds into known words ("lamoharu" -> "lamo haru").
        if (result.size < limit) {
            val splits = splitCandidates(currentWord)
                .filter { it !in result }
            result.addAll(splits.take(limit - result.size))
        }
        // Fill any remaining slots from the character model.
        if (result.size < limit) {
            val generated = charModel.generate(
                prefixInput = currentWord,
                maxResults = 20,
                minLength = maxOf(3, currentWord.length),
                maxLength = 15
            )
            val fill = generated
                .filter { it !in result }
                .sortedByDescending {
                    charScore(it) * 0.35 + spellingScore(currentWord, it)
                }
                .take(limit - result.size)
            result.addAll(fill)
        }

        return result
    }

    /**
     * Decomposes a no-space string into "prefix suffix" where the prefix is a
     * known word (dictionary, saved or learned) and the suffix is either another
     * known word or a common Nepali grammatical suffix. This lets the keyboard
     * merge/split compounds like "lamoharu" -> "lamo haru".
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

    fun getAutoCorrection(word: String): String? {
        // Check if word is in dictionary
        if (trie.contains(word.lowercase())) {
            return null // No correction needed
        }

        // Find similar words via fuzzy matching
        val candidates = getAllKnownWords()
            .filter { kotlin.math.abs(it.length - word.length) <= 2 }
            .map { it to levenshtein(word.lowercase(), it) }
            .filter { it.second <= 2 }
            .sortedBy { it.second }

        return candidates.firstOrNull()?.first
    }

    fun learnWord(word: String) {
        learnedWords.merge(word.lowercase(), 1, Int::plus)
        trie.insert(word.lowercase(), learnedWords[word.lowercase()] ?: 1)
        knownWordsCache = null
        store.saveLearned(learnedWords)
    }

    fun updateContext(word: String) {
        contextWords.add(word.lowercase())
        if (contextWords.size > maxContextSize) {
            contextWords.removeAt(0)
        }
    }

    /** Words the user added to their personal dictionary. */
    fun getSavedWords(): List<String> = store.loadSaved()

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

    /** Words this user has typed (learned automatically). */
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

    /** Clears auto-learned words, keeping the user's saved dictionary. */
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

    private fun getAllKnownWords(): List<String> {
        knownWordsCache?.let { return it }
        return trie.getAllWords()
            .map { it.first }
            .also { knownWordsCache = it }
    }

    private fun charScore(word: String): Double {
        return scoreCache.getOrPut(word) { charModel.score(word) }
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

    private fun spellingScore(input: String, candidate: String): Double {
        val distance = levenshtein(input, candidate)
        val maxLength = maxOf(input.length, candidate.length, 1)
        val similarity = 1.0 - distance.toDouble() / maxLength.toDouble()
        return similarity * 5.0
    }

    private companion object {
        /** Frequency used for words added to the personal dictionary so they rank high. */
        const val SAVED_WORD_FREQUENCY = 5000

        /** Common Nepali grammatical suffixes used by the no-space splitter. */
        val KNOWN_SUFFIXES = setOf(
            "haru", "lai", "le", "ko", "ka", "ki", "ma", "la",
            "bata", "dekhi", "samma", "pani", "nai", "ta", "ni",
            "sanga", "gari", "jasto"
        )
    }
}