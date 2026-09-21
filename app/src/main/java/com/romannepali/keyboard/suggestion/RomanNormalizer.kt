package com.romannepali.keyboard.suggestion

import java.util.Locale

/**
 * Normalizes Roman-Nepali spellings to a canonical phonetic key so that
 * variant spellings of the same word group together:
 *
 *   aaja / aja / aaj -> "aj"
 *   khusi / khushi   -> "khusi"
 *   keta / ketaa     -> "ket"
 *
 * Must stay in sync with the Python `normalize_key`
 * in build_roman_vocab.py.
 */
object RomanNormalizer {

    fun normalize(word: String): String {
        var w = word
            .trim()
            .lowercase(Locale.ROOT)

        if (w.isEmpty()) return ""

        // Long repeated vowels collapse to their short form.
        w = w
            .replace("aa", "a")
            .replace("ee", "i")
            .replace("ii", "i")
            .replace("oo", "u")
            .replace("uu", "u")

        // Common Roman-Nepali diphthong variants.
        w = w
            .replace("ai", "e")
            .replace("ei", "e")
            .replace("au", "o")
            .replace("ou", "o")

        // Aspiration alternates that are the same phoneme share a key.
        // Parity with build_roman_vocab.py: jh/ph/bh are deliberately kept
        // distinct so unrelated words (aaja/ajha) never merge.
        w = w
            .replace("chh", "ch")
            .replace("sh", "s")

        // A trailing short 'a' is usually the inherent vowel.
        // Keep the single-letter word "a" unchanged.
        if (w.length > 1 && w.endsWith('a')) {
            w = w.dropLast(1)
        }

        return w
    }
}