package io.github.domicc.lara.core.control

import io.github.domicc.lara.core.input.Gesture
import io.github.domicc.lara.core.input.TapProgram
import io.github.domicc.lara.core.scan.ScanConfig
import io.github.domicc.lara.core.scan.ScanMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AdbArgumentsTest {

    private val current = ScanConfig(
        idle = ScanMode.LOW_POWER,
        session = ScanMode.BALANCED,
        passive = true,
        rescue = false,
        refreshMinutes = 0,
    )

    private fun settings(vararg extras: Pair<String, Any?>) = AdbArguments.settings(mapOf(*extras), current)

    private fun rejected(block: () -> Unit): String = assertFailsWith<Rejected>(block = block).message.orEmpty()

    @Test
    fun `without parameters nothing changes`() {
        assertEquals(SettingsChange(current, wake = null, tap = null), settings())
    }

    @Test
    fun `named settings change and the others stay`() {
        val change = settings("idle" to "balanced", "rescue" to true, "refresh" to 60)
        assertEquals(current.copy(idle = ScanMode.BALANCED, rescue = true, refreshMinutes = 60), change.scan)
        assertEquals(null, change.wake)
        assertEquals(null, change.tap)

        assertEquals(current.copy(session = ScanMode.LOW_LATENCY, passive = false), settings("session" to "low_latency", "passive" to false).scan)
    }

    @Test
    fun `wake and tap are settings of their own`() {
        assertEquals(SettingsChange(current, wake = false, tap = TapProgram.COUNT), settings("wake" to false, "tap" to "count"))
        assertEquals(TapProgram.ECHO, settings("tap" to "echo").tap)
    }

    @Test
    fun `a flag and a number may arrive as text`() {
        // --es where --ez or --ei was meant
        assertEquals(true, settings("wake" to "true").wake)
        assertEquals(true, settings("wake" to "on").wake)
        assertEquals(false, settings("wake" to "0").wake)
        assertEquals(15, settings("refresh" to "15").scan.refreshMinutes)
    }

    @Test
    fun `values of the wrong kind are rejected with what was expected`() {
        assertEquals("idle must be one of low_power balanced low_latency", rejected { settings("idle" to "fast") })
        assertEquals("session must be one of low_power balanced low_latency", rejected { settings("session" to 2) })
        assertEquals("passive must be true or false", rejected { settings("passive" to "yes") })
        assertEquals("wake must be true or false", rejected { settings("wake" to 1) })
        assertEquals("refresh must be a whole number", rejected { settings("refresh" to "soon") })
        assertEquals("refresh must be a whole number", rejected { settings("refresh" to 1.5) })
        assertEquals("tap must be one of echo count", rejected { settings("tap" to "menu") })
    }

    @Test
    fun `a refresh outside its range is rejected, not bent into it`() {
        assertEquals(0, settings("refresh" to 0).scan.refreshMinutes)
        assertEquals(1440, settings("refresh" to 1440).scan.refreshMinutes)
        assertEquals("refresh must be 0 to 1440 (minutes)", rejected { settings("refresh" to -5) })
        assertEquals("refresh must be 0 to 1440 (minutes)", rejected { settings("refresh" to 1441) })
    }

    @Test
    fun `a mistyped name is rejected instead of ignored`() {
        val message = rejected { settings("idel" to "balanced", "wake" to true) }
        assertTrue(message.startsWith("unknown setting idel;"), message)
    }

    @Test
    fun `one bad value rejects the whole command`() {
        // the good values in front of it must not get through
        rejected { settings("idle" to "balanced", "rescue" to true, "refresh" to "x") }
        rejected { settings("wake" to false, "tap" to "x") }
    }

    @Test
    fun `a press defaults to one plain press of button 1`() {
        assertEquals(SimulatedPress(1, Gesture.PRESS, 0), AdbArguments.press(emptyMap()))
    }

    @Test
    fun `button, gesture and loss of a press can be named`() {
        assertEquals(
            SimulatedPress(3, Gesture.LONG_DOUBLE, 2),
            AdbArguments.press(mapOf("button" to 3, "gesture" to "long_double", "missed" to "2")),
        )
        for (gesture in Gesture.entries) {
            assertEquals(gesture, AdbArguments.press(mapOf("gesture" to gesture.name.lowercase())).gesture)
        }
    }

    @Test
    fun `a press outside what the remote can send is rejected`() {
        assertEquals("button must be 1 to 4", rejected { AdbArguments.press(mapOf("button" to 0)) })
        assertEquals("button must be 1 to 4", rejected { AdbArguments.press(mapOf("button" to 5)) })
        assertEquals("button must be a whole number", rejected { AdbArguments.press(mapOf("button" to "two")) })
        assertEquals("missed must be 0 to 99", rejected { AdbArguments.press(mapOf("missed" to 100)) })
        assertEquals("missed must be 0 to 99", rejected { AdbArguments.press(mapOf("missed" to -1)) })
        assertTrue(rejected { AdbArguments.press(mapOf("gesture" to "PRESS")) }.startsWith("gesture must be one of press double"))
    }

    @Test
    fun `text is whatever was sent, as text`() {
        assertEquals("ab jetzt 2 h Ruhe", AdbArguments.text(mapOf("text" to "ab jetzt 2 h Ruhe"), "text"))
        assertEquals("7", AdbArguments.text(mapOf("text" to 7), "text"))
        assertEquals(null, AdbArguments.text(emptyMap(), "text"))
    }
}
