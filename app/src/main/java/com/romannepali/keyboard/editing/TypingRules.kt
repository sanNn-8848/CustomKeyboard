package com.romannepali.keyboard.editing

/**
 * Pure text rules used by the IME. Kept free of Android types so they can be
 * unit-tested and so the data flow in RomanNepaliIME stays readable.
 */
object TypingRules {

    /** The word being typed: the trailing run of letters / apostrophes. */
    fun wordAtEnd(text: CharSequence): String =
        text.takeLastWhile { it.isLetter() || it == '\'' }.toString()

    /**
     * [before2] is the (up to) two characters before the caret. A space press is
     * a "double space" only when it follows "<letter or digit><space>", so
     * "Hi. " + space and a space at the start of text stay plain spaces.
     */
    fun isDoubleSpace(before2: String): Boolean =
        before2.length == 2 && before2[1] == ' ' && before2[0].isLetterOrDigit()

    /** Applies the casing style of [typed] ("Mero", "MERO", "mero") to [word]. */
    fun matchCase(word: String, typed: String): String = when {
        typed.length > 1 &&
            typed.any { it.isLetter() } &&
            typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
        typed.isNotEmpty() && typed[0].isUpperCase() ->
            word.replaceFirstChar { it.uppercase() }
        else -> word
    }
}
