package com.romannepali.keyboard.editing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingRulesTest {

    @Test fun doubleSpaceAfterLetterOrDigit() {
        assertTrue(TypingRules.isDoubleSpace("a "))
        assertTrue(TypingRules.isDoubleSpace("5 "))
    }

    @Test fun noDoubleSpaceAfterPunctuationOrAtStart() {
        assertFalse(TypingRules.isDoubleSpace(". "))
        assertFalse(TypingRules.isDoubleSpace(" "))
        assertFalse(TypingRules.isDoubleSpace("  "))
        assertFalse(TypingRules.isDoubleSpace(""))
    }

    @Test fun matchCaseKeepsTypedStyle() {
        assertEquals("Mero", TypingRules.matchCase("mero", "Mer"))
        assertEquals("MERO", TypingRules.matchCase("mero", "MER"))
        assertEquals("mero", TypingRules.matchCase("mero", "mer"))
        assertEquals("Mero", TypingRules.matchCase("mero", "I"))
    }

    @Test fun wordAtEndUsesLettersAndApostrophes() {
        assertEquals("don't", TypingRules.wordAtEnd("hello don't"))
        assertEquals("", TypingRules.wordAtEnd("hi. "))
        assertEquals("cd", TypingRules.wordAtEnd("ab12cd"))
    }
}
