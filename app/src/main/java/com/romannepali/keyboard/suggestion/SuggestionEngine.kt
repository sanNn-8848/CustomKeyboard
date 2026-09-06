package com.romannepali.keyboard.suggestion

class SuggestionEngine {
    private val trie = Trie()
    private val ngramModel = NgramModel()
    private val learnedWords = HashMap<String, Int>()
    
    // Context for next-word prediction
    private val contextWords = mutableListOf<String>()
    private val maxContextSize = 3

    init {
        loadDictionary()
    }

    private fun loadDictionary() {
        // Load common Roman Nepali words
        val commonWords = mapOf(
            // Greetings
            "namaste" to 1000, "namaskar" to 900, "kasto cha" to 800,
            "thik cha" to 850, "la" to 900, "huss" to 800,
            
            // Common words
            "ma" to 1000, "timi" to 950, "hami" to 900, "tapaai" to 850,
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
            "nau" to 600, "dus" to 550,
            
            // Days
            "aajha" to 900, "bholi" to 850, "hijo" to 800,
            "sombaar" to 750, "mangalbaar" to 700, "budhabaar" to 700,
            "bihibaar" to 700, "sukrabaar" to 700, "saniibaar" to 700,
            
            // Time
            "beluka" to 800, "bihaan" to 850, "digra" to 750,
            "rat" to 800, "din" to 850, "mahina" to 700, "barsha" to 650,
            
            // Actions
            "kam" to 900, "padh" to 850, "lekh" to 800, "bol" to 850,
            "her" to 800, "ja" to 900, "aa" to 950, "de" to 900,
            "lau" to 850, "kha" to 800, "pi" to 800, "soch" to 750,
            "bujh" to 700, "sik" to 750, "sikhaa" to 700,
            
            // Adjectives
            "ramro" to 900, "naramro" to 850, "thulo" to 850, "sano" to 850,
            "lamo" to 800, "choto" to 800, "gahro" to 750, "sajilo" to 800,
            "naya" to 850, "purano" to 800, "ramailo" to 900,
            
            // Objects
            "ghar" to 900, "kamra" to 800, "bato" to 850, "pasal" to 800,
            "kitab" to 750, "kalam" to 700, "daaki" to 650, "paati" to 700,
            "chhaaro" to 700, "batti" to 650, "paani" to 850, "aago" to 800,
            
            // Common phrases
            "mero naam" to 900, "timro naam" to 850, "k huncha" to 800,
            "k bhayo" to 850, "k garnu" to 800, "thik cha" to 900,
            "huss" to 900, "la" to 950, "dhanyabaad" to 800, "maaph" to 750
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

    fun getSuggestions(prefix: String, limit: Int = 3): List<String> {
        val suggestions = mutableListOf<String>()
        
        // 1. Get prefix matches from Trie
        val prefixMatches = trie.search(prefix.lowercase())
        suggestions.addAll(prefixMatches.map { it.first })
        
        // 2. Get next-word predictions based on context
        if (contextWords.isNotEmpty()) {
            val lastWord = contextWords.last()
            val nextWordCandidates = ngramModel.getTopNextWords(lastWord, 5)
            
            // Add next-word predictions that start with prefix
            nextWordCandidates.forEach { candidate ->
                if (candidate.startsWith(prefix.lowercase()) && !suggestions.contains(candidate)) {
                    suggestions.add(0, candidate) // Add to front
                }
            }
        }
        
        // 3. Add learned words
        learnedWords.entries
            .filter { it.key.startsWith(prefix.lowercase()) }
            .sortedByDescending { it.value }
            .forEach { (word, _) ->
                if (!suggestions.contains(word)) {
                    suggestions.add(word)
                }
            }
        
        return suggestions.take(limit)
    }

    fun getAutoCorrection(word: String): String? {
        // Check if word is in dictionary
        if (trie.contains(word.lowercase())) {
            return null // No correction needed
        }
        
        // Find similar words
        val suggestions = trie.search(word.lowercase())
        
        // Return the most frequent suggestion if it's close enough
        return suggestions.firstOrNull()?.first
    }

    fun learnWord(word: String) {
        learnedWords.merge(word.lowercase(), 1, Int::plus)
        trie.insert(word.lowercase(), learnedWords[word.lowercase()] ?: 1)
    }

    fun updateContext(word: String) {
        contextWords.add(word.lowercase())
        if (contextWords.size > maxContextSize) {
            contextWords.removeAt(0)
        }
    }
}
