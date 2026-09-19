package com.romannepali.keyboard.suggestion

/**
 * Normalizes Roman-Nepali spellings to a canonical phonetic key so that
 * variant spellings of the same word group together:
 *   aaja/aja/aaj  -> "aj"
 *   khusi/khushi  -> "khusi"
 *   keta/ketaa    -> "ket"
 *
 * Must stay in sync with the Python `normalize_key` in build_roman_vocab.py.
 */
object RomanNormalizer {

    fun normalize(word: String): String {
        var w = word.lowercase()
        if (w.isEmpty()) return ""

        // Long repeated vowels collapse to their short form.
        w = w.replace("aa", "a")
            .replace("ee", "i")
            .replace("ii", "i")
            .replace("oo", "u")
            .replace("uu", "u")
            .replace("ai", "e")
            .replace("ei", "e")
            .replace("au", "o")
            .replace("ou", "o")

        // Aspiration/denasal alternates share a key.
        w = w.replace("chh", "ch")
            .replace("sh", "s")
            .replace("jh", "j")
            .replace("ph", "p")
            .replace("bh", "b")

        // A trailing short 'a' is usually the inherent vowel -> drop it.
        if (w.endsWith("a") && w != "a") {
            w = w.dropLast(1)
        }

        return w.trim()
    }
}