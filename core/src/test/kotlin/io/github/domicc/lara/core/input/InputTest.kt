package io.github.domicc.lara.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InputTest {

    @Test
    fun `button and gesture are optional`() {
        val tap = Input(InputSource.HEARING_AID_TAP, elapsedMs = 123_456)
        assertNull(tap.button)
        assertNull(tap.gesture)
        assertEquals(tap, Input(InputSource.HEARING_AID_TAP, 123_456))
    }

    @Test
    fun `the origin is the path where there is one and the source otherwise`() {
        assertEquals("tap", Input(InputSource.HEARING_AID_TAP, 1).origin)
        assertEquals("screen", Input(InputSource.SCREEN, 1).origin)
        assertEquals("adb", Input(InputSource.ADB, 1).origin)
        assertEquals("ble_callback", Input(InputSource.SHELLY, 1, button = 1, path = InputPath.BLE_CALLBACK).origin)
        assertEquals("ble_rescue", Input(InputSource.SHELLY, 1, button = 1, path = InputPath.BLE_RESCUE).origin)
        assertEquals("simulated", Input(InputSource.SHELLY, 1, button = 1, path = InputPath.SIMULATED).origin)
    }

    @Test
    fun `the journal words of the sources are fixed`() {
        // journals written by earlier versions use these words; changing one breaks their analysis
        assertEquals(
            mapOf(
                InputSource.HEARING_AID_TAP to "tap",
                InputSource.SHELLY to "shelly",
                InputSource.MEDIA_KEY to "media_key",
                InputSource.SCREEN to "screen",
                InputSource.ADB to "adb",
                InputSource.ASSIST_GESTURE to "assist",
            ),
            InputSource.entries.associateWith { it.word },
        )
    }
}
