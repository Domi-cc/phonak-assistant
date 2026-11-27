package io.github.domicc.lara.core.scan

import kotlin.test.Test
import kotlin.test.assertEquals

class ScanStatusTest {

    @Test
    fun `before the service runs nothing scans`() {
        val status = ScanStatus()
        assertEquals("off", status.word)
        assertEquals(ScanStatus.SERVICE_NOT_RUNNING, status.off)
    }

    @Test
    fun `the word names mode and scan type of the callback scan`() {
        assertEquals("low_power/passive", ScanStatus(ScanMode.LOW_POWER, passive = true, off = null).word)
        assertEquals("balanced/active", ScanStatus(ScanMode.BALANCED, passive = false, off = null).word)
        assertEquals("low_latency/passive", ScanStatus(ScanMode.LOW_LATENCY, passive = true, off = null).word)
    }

    @Test
    fun `the rescue scan is named whether or not the callback scan runs`() {
        assertEquals("balanced/active+rescue", ScanStatus(ScanMode.BALANCED, passive = false, rescue = true, off = null).word)
        // scripts that read the word must expect this one too
        assertEquals("off+rescue", ScanStatus(rescue = true, off = ScanStatus.SCAN_FAILED).word)
    }
}
