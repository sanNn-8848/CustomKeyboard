package com.romannepali.keyboard.suggestion

class NgramModel {
    // Bigram model: maps word pairs to frequency
    private val bigrams = HashMap<String, HashMap<String, Int>>()
    
    // Unigram model: maps words to frequency
    private val unigrams = HashMap<String, Int>()

    fun addBigram(word1: String, word2: String, frequency: Int = 1) {
        val key = word1.lowercase()
        bigrams.getOrPut(key) { HashMap() }
            .merge(word2.lowercase(), frequency, Int::plus)
        
        // Also update unigrams
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

    fun getTopNextWords(previousWord: String, limit: Int = 3): List<String> {
        return getNextWordCandidates(previousWord)
            .take(limit)
            .map { it.first }
    }

    fun getUnigramFrequency(word: String): Int {
        return unigrams[word.lowercase()] ?: 0
    }

    fun getBigramProbability(word1: String, word2: String): Float {
        val key = word1.lowercase()
        val bigramCount = bigrams[key]?.get(word2.lowercase()) ?: 0
        val unigramCount = unigrams[key] ?: 1
        return bigramCount.toFloat() / unigramCount.toFloat()
    }
}
