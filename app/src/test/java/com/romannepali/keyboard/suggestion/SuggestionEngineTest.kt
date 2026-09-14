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
        val words = engine.predict(PredictionContext("khus"), limit = 3).map { it.word }
        assertTrue(words.contains("\uD83D\uDE0A"))
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
}