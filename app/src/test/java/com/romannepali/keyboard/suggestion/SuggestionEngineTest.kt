package com.romannepali.keyboard.suggestion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SuggestionEngineTest {

    private lateinit var engine: SuggestionEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        engine = SuggestionEngine(context)
    }

    @Test
    fun knownPrefixSuggestions_includeDictionaryWords() {
        val words = engine.predict(PredictionContext("nama"), limit = 5).map { it.word }
        assertTrue(words.contains("namaste"))
        assertTrue(words.contains("namaskar"))
    }

    @Test
    fun knownWord_isNotACorrection() {
        val correction = engine.getAutoCorrection("namaste")
        assertFalse(correction.isCorrection)
        assertEquals("namaste", correction.word)
    }

    @Test
    fun typo_correctsToNearestWord_withConfidence() {
        val correction = engine.getAutoCorrection("namsate")
        assertTrue(correction.isCorrection)
        assertEquals("namaste", correction.word)
        assertTrue("confidence too low: ${correction.confidence}", correction.confidence > 0.5f)
    }

    @Test
    fun garbageInput_doesNotAutocorrect_keepsOriginal() {
        val correction = engine.getAutoCorrection("qzx")
        assertFalse(correction.isCorrection)
        assertEquals("qzx", correction.word)
    }

    @Test
    fun noSpaceString_suggestsSplitWord() {
        val words = engine.predict(PredictionContext("meroharu"), limit = 3).map { it.word }
        assertTrue(words.contains("mero haru"))
    }

    @Test
    fun contextWord_boostsNgramNextWord() {
        val first = engine.predict(
            PredictionContext(currentWord = "ga", previousWords = listOf("ma")),
            limit = 1
        ).first()
        assertEquals("garchu", first.word)
    }

    @Test
    fun repeatedTrailingCharacters_collapseToBaseWord() {
        val first = engine.predict(PredictionContext("heyyy"), limit = 1).first()
        assertEquals("hey", first.word)
    }

    @Test
    fun personalWord_suggestsFirst() {
        engine.addPersonalWord("meroketo")
        val first = engine.predict(PredictionContext("merok"), limit = 1).first()
        assertEquals("meroketo", first.word)
    }

    @Test
    fun learnedWord_appearsInSuggestions() {
        engine.learnWord("bhatindaun")
        val words = engine.predict(PredictionContext("bhat"), limit = 5).map { it.word }
        assertTrue("got=${words}", words.contains("bhatindaun"))
    }

    @Test
    fun emoji_fillsRemainingSuggestionSlot() {
        // "paani" has exactly one real word, so the emoji tile fills slot 2.
        val words = engine.predict(PredictionContext("paani"), limit = 3).map { it.word }
        assertTrue("got=${words}", words.contains("\uD83D\uDCA7"))
    }

    @Test
    fun suppressedWord_isHiddenFromSuggestions() {
        engine.learnWord("bhatindaun")
        val before = engine.predict(PredictionContext("bhat"), limit = 5).map { it.word }
        assertTrue(before.contains("bhatindaun"))

        engine.suppress("bhatindaun")
        val after = engine.predict(PredictionContext("bhat"), limit = 5).map { it.word }
        assertFalse("suppressed word still suggested: $after", after.contains("bhatindaun"))

        engine.unsuppress("bhatindaun")
        val restored = engine.predict(PredictionContext("bhat"), limit = 5).map { it.word }
        assertTrue("undo did not restore word: $restored", restored.contains("bhatindaun"))
    }

    @Test
    fun suppressedWords_areListedAndClearable() {
        engine.suppress("namaste")
        engine.suppress("namaskar")
        assertTrue(engine.getSuppressedWords().contains("namaste"))
        engine.clearSuppressedWords()
        assertTrue(engine.getSuppressedWords().isEmpty())
        engine.unsuppress("namaste")
        assertTrue(engine.getSuppressedWords().isEmpty())
    }

    @Test
    fun favorites_areListedAndClearable() {
        engine.favorite("namaste")
        engine.favorite("kathmandu")
        assertTrue(engine.getFavorites().contains("namaste"))
        engine.unfavorite("namaste")
        assertFalse(engine.getFavorites().contains("namaste"))
        assertTrue(engine.isFavorite("kathmandu"))
        engine.clearFavorites()
        assertTrue(engine.getFavorites().isEmpty())
    }

    @Test
    fun favorites_persistAcrossInstances() {
        engine.favorite("kathmandu")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fresh = SuggestionEngine(context)
        try {
            assertTrue("favorite lost on reload", fresh.isFavorite("kathmandu"))
        } finally {
            fresh.clearFavorites()
        }
    }

    @Test
    fun favoritedWords_stillFlowThroughPredictions() {
        engine.learnWord("bhatindaun")
        engine.favorite("bhatindaun")
        val words = engine.predict(PredictionContext("bhat"), limit = 5).map { it.word }
        assertTrue("favorite not suggested: $words", words.contains("bhatindaun"))
        engine.clearFavorites()
    }

    @Test
    fun knownPrefix_doesNotGetFloodedByCharModelFills() {
        // "na" has many real dictionary matches; junk char-model words must not swamp it.
        val results = engine.predict(PredictionContext("na"), limit = 7)
        val charFills = results.count { it.source == CandidateSource.CHAR_MODEL }
        assertTrue("too many char-model fills: $charFills for ${results.map { it.word }}", charFills <= 1)
    }

    @Test
    fun predictionLatency_isReasonable() {
        val iterations = 3000
        val durations = LongArray(iterations)
        repeat(iterations) { i ->
            val start = System.nanoTime()
            engine.predict(PredictionContext("na"), limit = 3)
            durations[i] = System.nanoTime() - start
        }
        durations.sort()
        val p50 = durations[iterations / 2].toDouble() / 1_000_000.0
        val p95 = durations[(iterations * 95) / 100].toDouble() / 1_000_000.0
        println("prediction latency p50=${"%.2f".format(p50)}ms p95=${"%.2f".format(p95)}ms")
        assertTrue("p95 too slow: $p95 ms", p95 < 100.0)
    }

    @Test
    fun nextWordPredictions_surfaceRealWords() {
        engine.updateContext("ma")
        engine.updateContext("garchu")
        val results = engine.predict(
            PredictionContext(currentWord = "", previousWords = listOf("ma")),
            limit = 5
        )
        assertTrue("next-word list is empty: $results", results.isNotEmpty())
        val words = results.map { it.word }
        assertTrue("plain next word missing (canonical dedup bug): $words", words.contains("garchu"))
        assertEquals("duplicate canonical tiles: $words", words.size, words.toSet().size)
    }

    @Test
    fun removingLearnedWord_restoresDictionaryWord() {
        engine.learnWord("namaste")
        engine.removeLearnedWord("namaste")
        val words = engine.predict(PredictionContext("nama"), limit = 5).map { it.word }
        assertTrue("dictionary word wiped after learned cleanup: $words", words.contains("namaste"))
    }

    @Test
    fun removingLearnedWord_restoresPersonalWord() {
        engine.addPersonalWord("meroketo")
        engine.learnWord("meroketo")
        engine.removeLearnedWord("meroketo")
        val words = engine.predict(PredictionContext("merok"), limit = 5).map { it.word }
        assertTrue("personal word wiped after learned cleanup: $words", words.contains("meroketo"))
    }

    @Test
    fun clearingLearnedWords_keepsDictionaryAndPersonalWords() {
        engine.addPersonalWord("meroketo")
        engine.learnWord("namaste")
        engine.learnWord("meroketo")
        engine.learnWord("bhatindaun")
        engine.clearLearnedWords()
        val dict = engine.predict(PredictionContext("nama"), limit = 5).map { it.word }
        assertTrue("dictionary word wiped: $dict", dict.contains("namaste"))
        val personal = engine.predict(PredictionContext("merok"), limit = 5).map { it.word }
        assertTrue("personal word wiped: $personal", personal.contains("meroketo"))
        val novel = engine.predict(PredictionContext("bhat"), limit = 5).map { it.word }
        assertFalse("novel learned word should vanish: $novel", novel.contains("bhatindaun"))
    }
}