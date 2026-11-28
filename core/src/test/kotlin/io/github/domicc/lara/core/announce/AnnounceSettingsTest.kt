package io.github.domicc.lara.core.announce

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnnounceSettingsTest {

    @Test
    fun `announcements follow the media volume unless the owner chooses otherwise`() {
        val settings = AnnounceSettings()
        assertEquals(AnnounceChannel.MEDIA, settings.channel)
        assertEquals(false, settings.ownLevel)
        assertNull(settings.step)
        assertEquals(LeadIn.SHORT, settings.leadIn)
        assertEquals(450, settings.leadIn.ms)
    }

    @Test
    fun `the channels have the words the journal and the commands use`() {
        assertEquals("media", AnnounceChannel.MEDIA.word)
        assertEquals("assistant", AnnounceChannel.ASSISTANT.word)
        assertEquals(AnnounceChannel.MEDIA, AnnounceChannel.fromWord("media"))
        assertEquals(AnnounceChannel.ASSISTANT, AnnounceChannel.fromWord("assistant"))
        assertNull(AnnounceChannel.fromWord("Media"))
        assertNull(AnnounceChannel.fromWord(null))
    }

    @Test
    fun `the lead-in is one of three lengths`() {
        assertEquals(listOf(450, 1_500, 3_000), LeadIn.entries.map { it.ms })
        assertEquals(LeadIn.MEDIUM, LeadIn.fromMs(1_500))
        assertEquals(LeadIn.LONG, LeadIn.fromMs(3_000))
        assertNull(LeadIn.fromMs(500))
        assertNull(LeadIn.fromMs(null))
    }

    @Test
    fun `a step is kept inside 1 to max and is never 0`() {
        assertEquals(1, VolumeSteps.clamp(0, 25))
        assertEquals(1, VolumeSteps.clamp(-3, 25))
        assertEquals(25, VolumeSteps.clamp(26, 25))
        assertEquals(18, VolumeSteps.clamp(18, 25))
        // a scale without steps still has no 0 to send
        assertEquals(1, VolumeSteps.clamp(5, 0))
    }

    @Test
    fun `a step chosen against one max is carried to another in proportion`() {
        assertEquals(9, VolumeSteps.rescale(15, fromMax = 25, toMax = 15))
        assertEquals(22, VolumeSteps.rescale(18, fromMax = 25, toMax = 30))
        // the loudest stays the loudest, the quietest stays audible
        assertEquals(15, VolumeSteps.rescale(25, fromMax = 25, toMax = 15))
        assertEquals(30, VolumeSteps.rescale(25, fromMax = 25, toMax = 30))
        assertEquals(1, VolumeSteps.rescale(1, fromMax = 25, toMax = 5))
        assertEquals(18, VolumeSteps.rescale(18, fromMax = 25, toMax = 25))
    }

    @Test
    fun `a step that was stored out of its range is brought into the new one`() {
        assertEquals(1, VolumeSteps.rescale(0, fromMax = 25, toMax = 15))
        assertEquals(15, VolumeSteps.rescale(40, fromMax = 25, toMax = 15))
        // a max that was never a scale: the step is taken as it is
        assertEquals(5, VolumeSteps.rescale(5, fromMax = 0, toMax = 25))
        assertEquals(25, VolumeSteps.rescale(99, fromMax = -1, toMax = 25))
    }

    @Test
    fun `a stored step answers for today's max`() {
        assertEquals(StoredStep(18, 25), StoredStep(18, 25).on(25))
        assertEquals(StoredStep(9, 15), StoredStep(15, 25).on(15))
        // step 0 or beyond max, as a broken store could hold it
        assertEquals(StoredStep(1, 25), StoredStep(0, 25).on(25))
        assertEquals(StoredStep(25, 25), StoredStep(31, 25).on(25))
    }

    @Test
    fun `the first own step is the phone's step for the aid, so that switching on changes nothing`() {
        assertEquals(15, VolumeSteps.initial(phoneStep = 15, max = 25))
        assertEquals(25, VolumeSteps.initial(phoneStep = 25, max = 25))
    }

    @Test
    fun `without a readable step for the aid the first own step is three fifths of the scale`() {
        assertEquals(15, VolumeSteps.initial(phoneStep = null, max = 25))
        assertEquals(9, VolumeSteps.initial(phoneStep = null, max = 15))
        // muted reads 0, and that is no step
        assertEquals(15, VolumeSteps.initial(phoneStep = 0, max = 25))
        assertEquals(15, VolumeSteps.initial(phoneStep = 40, max = 25))
        assertEquals(1, VolumeSteps.initial(phoneStep = null, max = 1))
    }

    @Test
    fun `the own step counts only while the switch is on`() {
        val step = StoredStep(18, 25)
        assertNull(AnnounceSettings(ownLevel = false, step = step).own)
        assertEquals(step, AnnounceSettings(ownLevel = true, step = step).own)
        // on without a step: nothing to set, the phone's value stands
        assertNull(AnnounceSettings(ownLevel = true, step = null).own)
    }
}
