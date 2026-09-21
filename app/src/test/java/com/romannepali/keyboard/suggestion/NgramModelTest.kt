package com.romannepali.keyboard.suggestion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NgramModelTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun addBigram_surfacesStrongestCandidate() {
        val model = NgramModel(context)
        model.addBigram("mero", "ramro", 4)
        model.addBigram("mero", "haru", 1)
        model.addBigram("ramro", "cha", 3)

        val afterMero = model.getNextWordCandidates("mero")
        assertEquals("ramro", afterMero.first().first)
        assertEquals("ramro should lead by count", 4, afterMero.first().second)

        val afterRamro = model.getNextWordCandidates("ramro")
        assertEquals("cha", afterRamro.first().first)
        assertEquals("cha should have count 3", 3, afterRamro.first().second)
    }

    @Test
    fun addBigram_isCaseInsensitiveForLookup() {
        val model = NgramModel(context)
        model.addBigram("Mero", "Ramro", 3)

        assertEquals("ramro", model.getNextWordCandidates("MERO").first().first)
        assertEquals("ramro", model.getNextWordCandidates("mero").first().first)
        assertEquals(
            "mixed-case lookup works",
            3,
            model.getNextWordCandidates("MeRo").first().second
        )
    }

    @Test
    fun addBigram_ignoresEmptyAndInvalid() {
        val model = NgramModel(context)
        model.addBigram("", "ramro", 4)
        model.addBigram("mero", "", 4)
        model.addBigram("mero", "ramro", 0)
        model.addBigram("mero", "ramro", -3)

        assertTrue("empty words produce no bigrams", model.getNextWordCandidates("").isEmpty())
        assertTrue("non-positive counts produce no bigrams", model.getNextWordCandidates("mero").isEmpty())
        assertTrue("no unigram pollution", model.getUnigramFrequency("ramro") == 0)
    }

    @Test
    fun getBigramProbability_returnsZeroForMissingOrEmpty() {
        val model = NgramModel(context)
        model.addBigram("mero", "ramro", 2)

        assertEquals(0f, model.getBigramProbability("", "ramro"))
        assertEquals(0f, model.getBigramProbability("mero", ""))
        assertEquals(0f, model.getBigramProbability("mero", "missing"))
        assertEquals(1f, model.getBigramProbability("mero", "ramro"))
    }

    @Test
    fun instances_doNotShareUserAddedBigrams() {
        val a = NgramModel(context)
        val b = NgramModel(context)

        a.addBigram("mero", "ramro", 4)
        a.addBigram("ramro", "cha", 3)

        assertTrue("instance A learned the bigram", a.getNextWordCandidates("mero").isNotEmpty())
        assertTrue("instance B must NOT see instance A's bigrams", b.getNextWordCandidates("mero").isEmpty())
        assertTrue(
            "instance A's second learned bigram is isolated too",
            b.getNextWordCandidates("ramro").isEmpty()
        )
    }

    @Test
    fun loadModel_isolatesUserBigramsFromPeerInstances() {
        val first = NgramModel(context)
        first.loadModel(mapOf("ma" to 10))
        val assetBaseline = first.getNextWordCandidates("ma")
            .first { it.first == "pani" }
            .second

        // Mutate a bigram that also exists in the shipped asset. Without the
        // deep copy, this would poison the shared cache and leak into `second`.
        first.addBigram("ma", "pani", 7)

        val second = NgramModel(context)
        second.loadModel(mapOf("ma" to 10))
        val secondCount = second.getNextWordCandidates("ma")
            .first { it.first == "pani" }
            .second

        assertEquals("peer instance must keep the pristine asset count", assetBaseline, secondCount)
        assertEquals(
            "first instance's user bigram must not leak",
            assetBaseline + 7,
            first.getNextWordCandidates("ma").first { it.first == "pani" }.second
        )
    }
}