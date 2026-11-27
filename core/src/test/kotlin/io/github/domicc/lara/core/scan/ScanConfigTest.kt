package io.github.domicc.lara.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanConfigTest {

    @Test
    fun `the defaults spend no scan allowance outside a session`() {
        val config = ScanConfig()
        assertEquals(ScanMode.LOW_POWER, config.idle)
        assertEquals(ScanMode.BALANCED, config.session)
        assertTrue(config.passive)
        assertFalse(config.rescue, "the rescue scan draws on the same hourly allowance")
    }

    @Test
    fun `by default no scan is replaced on a timer`() {
        // replacing a scan resets Android's accounting for it, which is what is being measured
        assertEquals(0, ScanConfig().refreshMinutes)
        assertEquals(24 * 60, ScanConfig.MAX_REFRESH_MINUTES)
    }

    @Test
    fun `the mode follows the count session`() {
        val config = ScanConfig(idle = ScanMode.LOW_POWER, session = ScanMode.LOW_LATENCY)
        assertEquals(ScanMode.LOW_POWER, config.modeFor(sessionOpen = false))
        assertEquals(ScanMode.LOW_LATENCY, config.modeFor(sessionOpen = true))
    }

    @Test
    fun `the words of the modes are fixed`() {
        // stored in the preferences and written to the journal; changing one breaks both
        assertEquals(
            mapOf(
                ScanMode.LOW_POWER to "low_power",
                ScanMode.BALANCED to "balanced",
                ScanMode.LOW_LATENCY to "low_latency",
            ),
            ScanMode.entries.associateWith { it.word },
        )
    }

    @Test
    fun `a mode is found by its word`() {
        for (mode in ScanMode.entries) assertEquals(mode, ScanMode.fromWord(mode.word))
        assertNull(ScanMode.fromWord("LOW_POWER"))
        assertNull(ScanMode.fromWord(""))
        assertNull(ScanMode.fromWord(null))
    }
}
