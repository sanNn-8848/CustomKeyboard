package com.romannepali.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Test

class RomanNormalizerTest {

    @Test
    fun longVowelVariants_normalizeTogether() {
        assertEquals("aj", RomanNormalizer.normalize("aaja"))
        assertEquals("aj", RomanNormalizer.normalize("aja"))
        assertEquals("aj", RomanNormalizer.normalize("aaj"))
    }

    @Test
    fun khusiVariants_normalizeTogether() {
        assertEquals("khusi", RomanNormalizer.normalize("khusi"))
        assertEquals("khusi", RomanNormalizer.normalize("khushi"))
    }

    @Test
    fun trailingA_isRemoved() {
        assertEquals("ket", RomanNormalizer.normalize("keta"))
        assertEquals("ket", RomanNormalizer.normalize("ketaa"))
    }

    @Test
    fun casingAndWhitespace_areNormalized() {
        assertEquals("ket", RomanNormalizer.normalize("  KETAA  "))
    }

    @Test
    fun emptyInput_returnsEmpty() {
        assertEquals("", RomanNormalizer.normalize(""))
        assertEquals("", RomanNormalizer.normalize("   "))
    }

    @Test
    fun singleA_isPreserved() {
        assertEquals("a", RomanNormalizer.normalize("a"))
    }

    @Test
    fun aspirationVariants_normalize() {
        assertEquals("s", RomanNormalizer.normalize("sh"))
        assertEquals("ch", RomanNormalizer.normalize("chh"))
        assertEquals("jh", RomanNormalizer.normalize("jh"))
        assertEquals("ph", RomanNormalizer.normalize("ph"))
        assertEquals("bh", RomanNormalizer.normalize("bh"))
    }

    @Test
    fun distinctPhonemes_keepSeparateKeys() {
        // Parity with build_roman_vocab.py: jh/ph/bh are intentionally kept,
        // so aaja (today) and ajha (still/yet) must not merge.
        assertEquals("aj", RomanNormalizer.normalize("aaja"))
        assertEquals("ajh", RomanNormalizer.normalize("ajha"))
        assertEquals(false, RomanNormalizer.normalize("aaja") == RomanNormalizer.normalize("ajha"))
        assertEquals(false, RomanNormalizer.normalize("phul") == RomanNormalizer.normalize("pul"))
        assertEquals(false, RomanNormalizer.normalize("bhag") == RomanNormalizer.normalize("bag"))
    }

    @Test
    fun ordinaryWords_keepDistinctKeys() {
        assertEquals("pani", RomanNormalizer.normalize("pani"))
        assertEquals("namaste", RomanNormalizer.normalize("namaste"))
        assertKeysDiffer("keta", "kela")
    }

    private fun assertKeysDiffer(a: String, b: String) {
        val keyA = RomanNormalizer.normalize(a)
        val keyB = RomanNormalizer.normalize(b)
        assertEquals("expected distinct keys", false, keyA == keyB)
    }
}