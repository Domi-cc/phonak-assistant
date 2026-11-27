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
