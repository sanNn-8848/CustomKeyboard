package com.merotype.keyboard.engine

import kotlin.math.max

/**
 * Handles normalization of Roman Nepali text
 * - Fixes spacing issues
 * - Handles repeated characters
 * - Normalizes variant spellings
 * - Removes formatting noise
 */
class NormalizationEngine {

    /**
     * Main normalization pipeline
     */
    fun normalize(input: String): NormalizedText {
        if (input.isBlank()) return NormalizedText(input, emptyList())

        var processed = input.trim()
        val operations = mutableListOf<NormalizationOp>()

        // Step 1: Fix spacing issues
        val spacingFixed = fixSpacing(processed)
        if (spacingFixed != processed) {
            operations.add(NormalizationOp("spacing", processed, spacingFixed))
            processed = spacingFixed
        }

        // Step 2: Handle repeated characters
        val repeatsFixed = reduceRepeatedCharacters(processed)
        if (repeatsFixed != processed) {
            operations.add(NormalizationOp("repeats", processed, repeatsFixed))
            processed = repeatsFixed
        }

        // Step 3: Normalize variant spellings
        val variantsNormalized = normalizeVariants(processed)
        if (variantsNormalized != processed) {
            operations.add(NormalizationOp("variants", processed, variantsNormalized))
            processed = variantsNormalized
        }

        return NormalizedText(processed, operations)
    }

    /**
     * Fix spacing issues (extra spaces, missing spaces)
     */
    private fun fixSpacing(input: String): String {
        // Remove extra spaces between words
        return input
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Reduce repeated characters (hahaha → haha, laaaaa → la)
     */
    private fun reduceRepeatedCharacters(input: String): String {
        val result = StringBuilder()
        var lastChar = Char.MIN_VALUE
        var repeatCount = 0

        for (char in input) {
            if (char == lastChar) {
                repeatCount++
                // Allow max 2 repeats for emphasis
                if (repeatCount <= 1) {
                    result.append(char)
                }
            } else {
                result.append(char)
                lastChar = char
                repeatCount = 0
            }
        }

        return result.toString()
    }

    /**
     * Normalize variant spellings
     */
    private fun normalizeVariants(input: String): String {
        var output = input

        // Common Nepali vowel variants
        val variants = mapOf(
            "aa" to "a",
            "ii" to "i",
            "uu" to "u",
            "ee" to "e",
            "oo" to "o"
        )

        for ((variant, canonical) in variants) {
            output = output.replace(variant, canonical, ignoreCase = true)
        }

        return output
    }

    /**
     * Calculate similarity between two strings (Levenshtein distance)
     */
    fun calculateSimilarity(s1: String, s2: String): Double {
        val distance = levenshteinDistance(s1.lowercase(), s2.lowercase())
        val maxLen = max(s1.length, s2.length)
        return if (maxLen == 0) 1.0 else 1.0 - (distance.toDouble() / maxLen)
    }

    /**
     * Levenshtein distance for fuzzy matching
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }

        return dp[s1.length][s2.length]
    }

    data class NormalizedText(
        val text: String,
        val operations: List<NormalizationOp>
    )

    data class NormalizationOp(
        val type: String,
        val before: String,
        val after: String
    )
}
