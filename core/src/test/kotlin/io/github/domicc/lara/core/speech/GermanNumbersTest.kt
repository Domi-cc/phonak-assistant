package io.github.domicc.lara.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GermanNumbersTest {

    @Test
    fun `the irregular words are right`() {
        val expected = mapOf(
            1 to "eins",
            7 to "sieben",
            10 to "zehn",
            11 to "elf",
            12 to "zwölf",
            16 to "sechzehn",
            17 to "siebzehn",
            20 to "zwanzig",
            21 to "einundzwanzig",
            30 to "dreißig",
            36 to "sechsunddreißig",
            47 to "siebenundvierzig",
            60 to "sechzig",
            66 to "sechsundsechzig",
            70 to "siebzig",
            77 to "siebenundsiebzig",
            91 to "einundneunzig",
            99 to "neunundneunzig",
        )
        for ((number, word) in expected) assertEquals(word, GermanNumbers.word(number))
    }

    @Test
    fun `every number from 1 to 99 has a word of its own`() {
        val words = (1..GermanNumbers.MAX).map { assertNotNull(GermanNumbers.word(it), "$it") }
        assertEquals(99, words.toSet().size)
        assertTrue(words.all { word -> word.all { it.isLetter() } })
    }

    @Test
    fun `there is no word outside 1 to 99`() {
        assertNull(GermanNumbers.word(0))
        assertNull(GermanNumbers.word(-3))
        assertNull(GermanNumbers.word(100))
    }
}
