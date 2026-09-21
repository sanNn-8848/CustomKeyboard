package com.romannepali.keyboard.suggestion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CharModelTest {

    private val context =
        ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun emptyPrefix_returnsEmpty() {
        val model = CharModel(context)

        assertTrue(model.generate("").isEmpty())
    }

    @Test
    fun invalidGenerationLimits_returnEmpty() {
        val model = CharModel(context)

        assertTrue(
            model.generate(
                "ka",
                maxResults = 0
            ).isEmpty()
        )

        assertTrue(
            model.generate(
                "ka",
                minLength = 10,
                maxLength = 5
            ).isEmpty()
        )

        assertTrue(
            model.generate(
                "ka",
                maxLength = 0
            ).isEmpty()
        )
    }

    @Test
    fun prefixLongerThanMaximum_returnsEmpty() {
        val model = CharModel(context)

        assertTrue(
            model.generate(
                "kathmandu",
                maxLength = 3
            ).isEmpty()
        )
    }

    @Test
    fun prefixAtMaximumLength_returnsPrefix() {
        val model = CharModel(context)

        assertEquals(
            listOf("kata"),
            model.generate(
                "kata",
                maxResults = 10,
                minLength = 3,
                maxLength = 4
            )
        )
    }

    @Test
    fun scoreEmptyWord_isVeryLow() {
        val model = CharModel(context)

        assertEquals(
            -100.0,
            model.score(""),
            0.0
        )
    }

    @Test
    fun scoreIsCaseInsensitive() {
        val model = CharModel(context)

        assertEquals(
            model.score("kathmandu"),
            model.score("KATHMANDU"),
            0.0
        )
    }
}