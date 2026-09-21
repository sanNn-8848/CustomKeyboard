package com.romannepali.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrieTest {

    @Test
    fun insert_normalizesCasing() {
        val trie = Trie()
        trie.insert("Namaste", 5)

        assertTrue("lowercase should be found", trie.contains("namaste"))
        assertTrue("uppercase should be found", trie.contains("NAMASTE"))
        assertEquals("frequency should be stored", 5, trie.getFrequency("NAMASTE"))

        val words = trie.getAllWords().map { it.first }
        assertTrue("search returns the word", trie.search("nam").any { it.first == "namaste" })
        assertTrue("stored words should be lowercase", words.contains("namaste"))
        assertFalse("no mixed-case copy", words.contains("Namaste"))
    }

    @Test
    fun insert_ignoresEmptyAndInvalid() {
        val trie = Trie()
        trie.insert("", 5)
        trie.insert("   ", 3)
        trie.insert("mero", 0)
        trie.insert("mero", -2)

        assertFalse("empty word must not be inserted", trie.contains(""))
        assertEquals("empty word has no frequency", 0, trie.getFrequency(""))
        assertFalse("non-positive frequency must be ignored", trie.contains("mero"))
        assertTrue("no valid words stored", trie.getAllWords().isEmpty())
    }

    @Test
    fun remove_normalizesCasingAndPrunes() {
        val trie = Trie()
        trie.insert("namaste", 5)

        assertTrue("remove should succeed", trie.remove("NAMASTE"))
        assertFalse("word should be gone", trie.contains("namaste"))
        assertEquals("frequency should be gone", 0, trie.getFrequency("namaste"))
        assertFalse("missing word remove should fail", trie.remove("missing"))
        assertFalse("empty remove should fail", trie.remove(""))
    }

    @Test
    fun remove_preservesPrefixWords() {
        val trie = Trie()
        trie.insert("namaste", 5)
        trie.insert("namaskar", 3)

        assertTrue(trie.remove("namaste"))
        assertFalse("removed word gone", trie.contains("namaste"))
        assertTrue("prefix sibling survives", trie.contains("namaskar"))
    }
}