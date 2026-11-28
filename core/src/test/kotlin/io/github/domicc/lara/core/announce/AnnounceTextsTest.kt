package io.github.domicc.lara.core.announce

import io.github.domicc.lara.core.announce.AnnounceVolumeRules.Action
import io.github.domicc.lara.core.announce.AnnounceVolumeRules.Why
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnnounceTextsTest {

    private val phone = AnnounceSettings()
    private val own = AnnounceSettings(ownLevel = true, step = StoredStep(18, 25))
    private val assistant = AnnounceSettings(channel = AnnounceChannel.ASSISTANT)

    /** The aid is the output of both channels; media at 15 of 25, Android's assistant volume at 8 of 15. */
    private fun live(
        aidSelected: Boolean = true,
        mediaToAid: Boolean = true,
        assistantToAid: Boolean = true,
        mediaStep: Int = 15,
        mediaMax: Int = 25,
        mediaMuted: Boolean = false,
        mediaFixed: Boolean = false,
        assistantStep: Int? = 8,
        assistantMax: Int? = 15,
        doNotDisturb: Boolean = false,
        mediaGoverned: Boolean = true,
    ) = VolumeLive(
        aidSelected, mediaToAid, assistantToAid, mediaStep, mediaMax, mediaMuted, mediaFixed, assistantStep, assistantMax, doNotDisturb,
        mediaGoverned,
    )

    @Test
    fun `the sample says its step`() {
        assertEquals("Lautstärke Stufe 18", AnnounceTexts.sample(18))
        assertEquals("volume_sample", AnnounceTexts.SAMPLE_REASON)
    }

    @Test
    fun `a step is named with its scale`() {
        assertEquals("Stufe 18 von 25", AnnounceTexts.step(18, 25))
    }

    // ---- the folded section

    @Test
    fun `folded, the section names the step of the announcements`() {
        assertEquals("Stufe 18 von 25", AnnounceTexts.summary(own, live()))
        // also without the aid: the step is the app's own
        assertEquals("Stufe 18 von 25", AnnounceTexts.summary(own, live(mediaToAid = false)))
    }

    @Test
    fun `folded, the own step is shown on today's scale`() {
        assertEquals("Stufe 11 von 15", AnnounceTexts.summary(own, live(mediaMax = 15)))
    }

    @Test
    fun `folded and following the phone, the section names the phone's step for the aid`() {
        assertEquals("wie Telefon · Stufe 15 von 25", AnnounceTexts.summary(phone, live()))
    }

    @Test
    fun `folded, no step is named that belongs to another output`() {
        assertEquals("wie Telefon", AnnounceTexts.summary(phone, live(mediaToAid = false, mediaStep = 7)))
        assertEquals("wie Telefon · stumm", AnnounceTexts.summary(phone, live(mediaMuted = true, mediaStep = 0)))
        // the mute flag alone is enough: the index under it is not what plays
        assertEquals("wie Telefon · stumm", AnnounceTexts.summary(phone, live(mediaMuted = true, mediaStep = 15)))
    }

    @Test
    fun `folded, the assistant channel is named with Android's step`() {
        assertEquals("Assistent-Kanal · Stufe 8 von 15", AnnounceTexts.summary(assistant, live()))
        assertEquals("Assistent-Kanal", AnnounceTexts.summary(assistant, live(assistantToAid = false)))
        assertEquals("Assistent-Kanal", AnnounceTexts.summary(assistant, live(assistantStep = null, assistantMax = null)))
    }

    // ---- the status line

    @Test
    fun `the status line names the media volume for the aid`() {
        assertEquals("Medienlautstärke fürs Hörgerät: Stufe 15 von 25", AnnounceTexts.status(phone, live()))
        assertEquals("Medienlautstärke fürs Hörgerät: Stufe 15 von 25", AnnounceTexts.status(own, live()))
    }

    @Test
    fun `the status line says when the aid is not the output`() {
        assertEquals("Das Hörgerät ist gerade nicht der Tonausgang", AnnounceTexts.status(phone, live(mediaToAid = false)))
        // the assistant channel has its own route
        assertEquals("Das Hörgerät ist gerade nicht der Tonausgang", AnnounceTexts.status(assistant, live(assistantToAid = false)))
        assertEquals(
            "Android spielt Ansagen mit Stufe 8 von 15; die App kann das nicht stellen",
            AnnounceTexts.status(assistant, live(mediaToAid = false)),
        )
    }

    @Test
    fun `the status line says when no hearing aid is chosen`() {
        assertEquals("Kein Hörgerät gewählt", AnnounceTexts.status(phone, live(aidSelected = false, mediaToAid = false)))
        assertEquals("Kein Hörgerät gewählt", AnnounceTexts.status(assistant, live(aidSelected = false)))
    }

    @Test
    fun `the status line says when media are muted`() {
        assertEquals(
            "Die Medienlautstärke ist stumm geschaltet: Ansagen sind jetzt nicht zu hören",
            AnnounceTexts.status(phone, live(mediaMuted = true, mediaStep = 0)),
        )
        // index 0 is mute
        assertEquals(
            "Die Medienlautstärke ist stumm geschaltet: Ansagen sind jetzt nicht zu hören",
            AnnounceTexts.status(own, live(mediaStep = 0)),
        )
        // and so is the mute flag over an index that is not 0
        assertEquals(
            "Die Medienlautstärke ist stumm geschaltet: Ansagen sind jetzt nicht zu hören",
            AnnounceTexts.status(phone, live(mediaMuted = true, mediaStep = 15)),
        )
    }

    @Test
    fun `with media muted and Do Not Disturb on the status line names both and claims no cause`() {
        // the app sees the two facts, not which of them muted the media
        assertEquals(
            "Medien sind stumm, und „Bitte nicht stören“ ist an: Ansagen sind jetzt nicht zu hören",
            AnnounceTexts.status(phone, live(mediaMuted = true, mediaStep = 0, doNotDisturb = true)),
        )
    }

    @Test
    fun `the status line says when Android does not put announcements under the media volume`() {
        // then the step shown is not what an announcement is played with
        assertEquals(
            "Android führt Ansagen auf diesem Telefon nicht unter der Medienlautstärke: Regler und Stufe wirken nicht auf sie",
            AnnounceTexts.status(phone, live(mediaGoverned = false)),
        )
        assertEquals(
            "Android führt Ansagen auf diesem Telefon nicht unter der Medienlautstärke: Regler und Stufe wirken nicht auf sie",
            AnnounceTexts.status(own, live(mediaGoverned = false)),
        )
        // nothing to say about that while the aid is not the output, or in the other channel
        assertEquals("Das Hörgerät ist gerade nicht der Tonausgang", AnnounceTexts.status(phone, live(mediaToAid = false, mediaGoverned = false)))
        assertEquals(
            "Android spielt Ansagen mit Stufe 8 von 15; die App kann das nicht stellen",
            AnnounceTexts.status(assistant, live(mediaGoverned = false)),
        )
    }

    @Test
    fun `Do Not Disturb that lets media through changes nothing in the status line`() {
        assertEquals("Medienlautstärke fürs Hörgerät: Stufe 15 von 25", AnnounceTexts.status(phone, live(doNotDisturb = true)))
    }

    @Test
    fun `the status line of the assistant channel tells what Android plays with`() {
        assertEquals(
            "Android spielt Ansagen mit Stufe 8 von 15; die App kann das nicht stellen",
            AnnounceTexts.status(assistant, live()),
        )
        assertEquals(
            "Android zeigt die Assistenten-Lautstärke nicht; die App kann sie nicht stellen",
            AnnounceTexts.status(assistant, live(assistantStep = null, assistantMax = null)),
        )
    }

    @Test
    fun `in the assistant channel the status line says when Do Not Disturb is on`() {
        // whether it mutes the assistant volume the app cannot see
        assertEquals(
            "Android spielt Ansagen mit Stufe 8 von 15; die App kann das nicht stellen · „Bitte nicht stören“ ist an und kann Ansagen stumm schalten",
            AnnounceTexts.status(assistant, live(doNotDisturb = true)),
        )
        assertEquals("Das Hörgerät ist gerade nicht der Tonausgang", AnnounceTexts.status(assistant, live(assistantToAid = false, doNotDisturb = true)))
    }

    // ---- the slider

    @Test
    fun `the slider shows the own step, on today's scale`() {
        assertEquals(18, AnnounceTexts.sliderStep(own, live()))
        assertEquals(18, AnnounceTexts.sliderStep(own, live(mediaToAid = false)))
        assertEquals(11, AnnounceTexts.sliderStep(own, live(mediaMax = 15)))
        assertEquals("Stufe 18 von 25", AnnounceTexts.sliderLabel(own, live()))
    }

    @Test
    fun `following the phone, the slider shows the phone's step for the aid or nothing`() {
        assertEquals(15, AnnounceTexts.sliderStep(phone, live()))
        assertEquals("Stufe 15 von 25", AnnounceTexts.sliderLabel(phone, live()))
        assertNull(AnnounceTexts.sliderStep(phone, live(mediaToAid = false, mediaStep = 7)))
        assertNull(AnnounceTexts.sliderStep(phone, live(mediaMuted = true, mediaStep = 0)))
        // muted over an index that is not 0: that index is not what plays
        assertNull(AnnounceTexts.sliderStep(phone, live(mediaMuted = true, mediaStep = 15)))
        assertEquals("Stufe – von 25", AnnounceTexts.sliderLabel(phone, live(mediaToAid = false, mediaStep = 7)))
    }

    @Test
    fun `while the slider is dragged its label follows the finger`() {
        assertEquals("Stufe 21 von 25", AnnounceTexts.sliderLabel(own, live(), dragged = 21))
    }

    // ---- after a sample

    private fun report(
        action: Action? = null,
        why: Why? = null,
        step: Int = 18,
        notPlayed: String? = null,
        governsMusic: Boolean = true,
        ended: String = "ok",
    ) = VolumeReport(AnnounceTexts.SAMPLE_REASON, notPlayed, action, why, step, governsMusic, ended)

    @Test
    fun `after a sample the screen says the step it was played with`() {
        assertEquals("Gespielt mit Stufe 18", AnnounceTexts.afterSample(report(Action.SET)))
        assertEquals("Gespielt mit Stufe 18", AnnounceTexts.afterSample(report(Action.ALREADY)))
        assertEquals("Gespielt mit Stufe 15", AnnounceTexts.afterSample(report(Action.KEPT, step = 15)))
    }

    @Test
    fun `after a refused change the screen says so, with the reason and the step that played`() {
        assertEquals(
            "Android hat die Änderung nicht übernommen (Bitte nicht stören). Gespielt mit Stufe 12.",
            AnnounceTexts.afterSample(report(Action.REFUSED, Why.DND, step = 12)),
        )
        assertEquals(
            "Android hat die Änderung nicht übernommen (Lautstärke ist auf diesem Telefon gesperrt). Gespielt mit Stufe 12.",
            AnnounceTexts.afterSample(report(Action.REFUSED, Why.USER_RESTRICTION, step = 12)),
        )
        assertEquals(
            "Android hat die Änderung nicht übernommen (Grund unbekannt). Gespielt mit Stufe 12.",
            AnnounceTexts.afterSample(report(Action.REFUSED, Why.UNKNOWN, step = 12)),
        )
        assertEquals(
            "Android hat die Änderung nicht übernommen (Android hat einen anderen Wert gesetzt). Gespielt mit Stufe 17.",
            AnnounceTexts.afterSample(report(Action.REFUSED, Why.OTHER, step = 17)),
        )
        assertEquals(
            "Android hat die Änderung nicht übernommen (Medien wurden stumm geschaltet). Nichts zu hören.",
            AnnounceTexts.afterSample(report(Action.REFUSED, Why.MUTED, step = 0)),
        )
    }

    @Test
    fun `a sample that was cut off does not claim to have been played`() {
        // the hearing aid was switched off during the lead-in: nothing was heard
        assertEquals(
            "Abgebrochen: Das Hörgerät ist nicht mehr der Tonausgang.",
            AnnounceTexts.afterSample(report(Action.SET, ended = "output_lost")),
        )
        assertEquals(
            "Abgebrochen: Das Hörgerät ist nicht mehr der Tonausgang.",
            AnnounceTexts.afterSample(report(Action.SET, ended = "output_going_away")),
        )
        assertEquals("Abgebrochen: Eine Sitzung läuft.", AnnounceTexts.afterSample(report(Action.SET, ended = "session_busy")))
        // the speech engine refused, timed out, or the announcement broke off
        assertEquals("Abgebrochen, siehe Protokoll (engine_refused).", AnnounceTexts.afterSample(report(Action.SET, ended = "engine_refused")))
        assertEquals("Abgebrochen, siehe Protokoll (timeout).", AnnounceTexts.afterSample(report(Action.KEPT, ended = "timeout")))
        assertEquals("Abgebrochen, siehe Protokoll (error).", AnnounceTexts.afterSample(report(Action.SET, ended = "error")))
    }

    @Test
    fun `a sample that was cut off by a newer one leaves no line`() {
        assertNull(AnnounceTexts.afterSample(report(Action.SET, ended = "superseded")))
    }

    @Test
    fun `a sample that Android does not play under the media volume does not name a step`() {
        // the slider set the media volume, but that is not what the sample was played with
        assertEquals(
            "Gespielt, aber Android führt diese Ansage nicht unter der Medienlautstärke: Die Stufe wirkt nicht auf sie.",
            AnnounceTexts.afterSample(report(Action.KEPT, step = 15, governsMusic = false)),
        )
        assertEquals(
            "Gespielt, aber Android führt diese Ansage nicht unter der Medienlautstärke: Die Stufe wirkt nicht auf sie.",
            AnnounceTexts.afterSample(report(Action.SKIPPED, Why.NOT_MUSIC_STREAM, step = 12, governsMusic = false)),
        )
    }

    @Test
    fun `after a sample the app left alone the screen says why`() {
        assertEquals(
            "Nicht verstellt, weil gerade etwas anderes im Hörgerät spielt. Gespielt mit Stufe 12.",
            AnnounceTexts.afterSample(report(Action.SKIPPED, Why.OTHER_MEDIA, step = 12)),
        )
        assertEquals(
            "Nicht verstellt, weil das Telefon in einem Anruf ist. Gespielt mit Stufe 12.",
            AnnounceTexts.afterSample(report(Action.SKIPPED, Why.MODE_NOT_NORMAL, step = 12)),
        )
        assertEquals(
            "Nicht verstellt, weil die Medienlautstärke stumm geschaltet ist. Nichts zu hören.",
            AnnounceTexts.afterSample(report(Action.SKIPPED, Why.MUTED, step = 0)),
        )
        // every reason has its words
        for (why in Why.entries) {
            val text = AnnounceTexts.afterSample(report(Action.SKIPPED, why, step = 12))
            assertTrue(text != null && text.startsWith("Nicht verstellt, weil "), why.name)
        }
    }

    @Test
    fun `a sample that did not play says why`() {
        assertEquals(
            "Nicht gespielt: Das Hörgerät ist gerade nicht der Tonausgang.",
            AnnounceTexts.afterSample(report(notPlayed = "output_is_not_the_hearing_aid")),
        )
        assertEquals(
            "Nicht gespielt: Das Hörgerät ist gerade nicht der Tonausgang.",
            AnnounceTexts.afterSample(report(notPlayed = "hearing_aid_not_connected")),
        )
        // the output changed between the checks and the first sound
        assertEquals(
            "Nicht gespielt: Das Hörgerät ist gerade nicht der Tonausgang.",
            AnnounceTexts.afterSample(report(notPlayed = "output_changed")),
        )
        assertEquals("Nicht gespielt: Kein Hörgerät gewählt.", AnnounceTexts.afterSample(report(notPlayed = "no_hearing_aid_selected")))
        assertEquals("Nicht gespielt: Eine Sitzung läuft.", AnnounceTexts.afterSample(report(notPlayed = "session_busy")))
        assertEquals("Nicht gespielt, siehe Protokoll (no_speech_engine).", AnnounceTexts.afterSample(report(notPlayed = "no_speech_engine")))
    }

    @Test
    fun `a sample that a newer one replaced leaves no line`() {
        assertNull(AnnounceTexts.afterSample(report(notPlayed = "superseded")))
    }

    @Test
    fun `the line for a step chosen while the aid is away`() {
        assertEquals(
            "Das Hörgerät ist gerade nicht der Tonausgang. Die Stufe gilt ab der nächsten Ansage.",
            AnnounceTexts.FROM_NEXT_ANNOUNCEMENT,
        )
    }

    @Test
    fun `the line for a refused change of the phone's own volume`() {
        assertEquals("Android hat die Änderung nicht übernommen (Bitte nicht stören)", AnnounceTexts.refused(Why.DND))
    }

    @Test
    fun `the lines for a change of the phone's own volume that the app did not make`() {
        assertEquals("Nicht verstellt, weil das Hörgerät nicht der Tonausgang ist.", AnnounceTexts.notChanged(Why.ROUTE_NOT_AID))
        assertEquals("Nicht verstellt, weil die Medienlautstärke stumm geschaltet ist.", AnnounceTexts.notChanged(Why.MUTED))
        assertEquals("Nicht verstellt, weil die Lautstärke auf diesem Telefon fest ist.", AnnounceTexts.notChanged(Why.VOLUME_FIXED))
        assertEquals("Gerade läuft eine Ansage. Bitte danach noch einmal.", AnnounceTexts.BUSY)
    }

    @Test
    fun `the choices have the words of the screen`() {
        assertEquals("0,5 s", AnnounceTexts.leadIn(LeadIn.SHORT))
        assertEquals("1,5 s", AnnounceTexts.leadIn(LeadIn.MEDIUM))
        assertEquals("3 s", AnnounceTexts.leadIn(LeadIn.LONG))
        assertEquals("Medien", AnnounceTexts.channel(AnnounceChannel.MEDIA))
        assertEquals("Assistent", AnnounceTexts.channel(AnnounceChannel.ASSISTANT))
        assertEquals("Eigene Lautstärke nur für Ansagen", AnnounceTexts.OWN_LEVEL)
        assertEquals("Vorlauf vor der Ansage", AnnounceTexts.LEAD_IN)
        assertEquals("Lautstärke-Kanal", AnnounceTexts.CHANNEL)
    }

    @Test
    fun `what is only read from the source is not stated as a fact on the screen`() {
        // that the volume keys reach an announcement in the pocket has not been tried on the phone
        assertTrue(AnnounceTexts.OWN_SLIDER_DETAIL.contains("Soweit bekannt"))
        assertTrue(AnnounceTexts.OWN_SLIDER_DETAIL.contains("noch nicht am Telefon geprüft"))
        assertTrue(AnnounceTexts.ASSISTANT_DETAIL.contains("Soweit bekannt"))
    }

    @Test
    fun `the line of the lead-in says what a longer one costs`() {
        // the owner's rule: whatever keeps a sound channel open is weighed against hearing time
        assertTrue(AnnounceTexts.LEAD_IN_DETAIL.contains("länger im Streaming-Programm"))
    }
}
