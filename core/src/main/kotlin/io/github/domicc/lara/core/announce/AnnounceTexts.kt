package io.github.domicc.lara.core.announce

import io.github.domicc.lara.core.announce.AnnounceVolumeRules.Action
import io.github.domicc.lara.core.announce.AnnounceVolumeRules.Why

/**
 * What Android says about the volumes right now, as far as the screen shows it.
 *
 * @param aidSelected a hearing aid was chosen in the app
 * @param mediaToAid the selected hearing aid's media device, and nothing else, is where media go
 * @param assistantToAid the same for sound played as assistant speech
 * @param mediaStep the media volume; only the hearing aid's while [mediaToAid]; 0 for a muted stream
 * @param assistantStep Android's assistant volume; null if Android does not tell
 * @param doNotDisturb Do Not Disturb is on in any form
 * @param mediaGoverned Android puts sound that is played as media and speech under the media
 * volume on this phone. If not, the step shown is not what an announcement is played with.
 */
data class VolumeLive(
    val aidSelected: Boolean,
    val mediaToAid: Boolean,
    val assistantToAid: Boolean,
    val mediaStep: Int,
    val mediaMax: Int,
    val mediaMuted: Boolean,
    val mediaFixed: Boolean,
    val assistantStep: Int?,
    val assistantMax: Int?,
    val doNotDisturb: Boolean,
    val mediaGoverned: Boolean = true,
) {
    /** Media are muted for the hearing aid: by the mute flag or by index 0, which is the same to Android. */
    val mediaSilent: Boolean get() = mediaMuted || mediaStep <= 0
}

/**
 * What became of one announcement and of its volume, for the line the screen shows after a
 * sample. Made when the announcement is over, not before: until its last word nobody knows
 * whether it was played.
 *
 * @param reason who asked for the announcement
 * @param notPlayed why the announcement was not begun at all; null if it was
 * @param action what was done about the volume; null if the announcement was not begun
 * @param step the media step the announcement was played with
 * @param governsMusic Android put this announcement under the media volume; if not, [step]
 * says nothing about how loud it was
 * @param ended how the announcement ended, in the announcer's words: "ok" if it was spoken
 * to its end
 */
data class VolumeReport(
    val reason: String,
    val notPlayed: String? = null,
    val action: Action? = null,
    val why: Why? = null,
    val step: Int = 0,
    val governsMusic: Boolean = true,
    val ended: String = AnnounceVolumeRules.RESULT_OK,
)

/**
 * The German lines of the section "Ansagen" and the sentence of the loudness sample. Here and
 * not in the screen's code, so that the wording is tested like the rules.
 */
object AnnounceTexts {

    /** The reason the announcer journals for the sample that follows a move of the slider. */
    const val SAMPLE_REASON = "volume_sample"

    const val OWN_LEVEL = "Eigene Lautstärke nur für Ansagen"
    const val OWN_LEVEL_DETAIL =
        "die App stellt die Medienlautstärke für die Dauer der Ansage und danach zurück · aus: Ansagen sind so laut wie die Medien des Telefons"
    const val LEAD_IN = "Vorlauf vor der Ansage"
    const val LEAD_IN_DETAIL =
        "Stille vor dem Ton, damit das Hörgerät umschalten kann · länger für die Hörprobe, ob der Anfang zu leise ist · " +
            "länger heißt auch: das Hörgerät bleibt bei jeder Ansage länger im Streaming-Programm"
    const val CHANNEL = "Lautstärke-Kanal"
    const val CHANNEL_DETAIL =
        "Medien: so laut wie die Medienlautstärke, mit Regler und Lautstärketasten zu stellen · " +
            "Assistent: Androids eigene Assistenten-Lautstärke, wie in den Builds davor"

    /** Under the slider while announcements follow the phone. */
    const val PHONE_SLIDER_DETAIL = "Der Regler stellt die Medienlautstärke des Telefons fürs Hörgerät, dauerhaft, wie die Lautstärketasten."

    /**
     * Under the slider while the app sets its own step. That the volume keys reach an
     * announcement is read from the Android source and not tried on the phone, and the line
     * says so.
     */
    const val OWN_SLIDER_DETAIL =
        "Der Regler gilt nur für Ansagen. Soweit bekannt, ändern die Lautstärketasten während einer Ansage diese Stufe " +
            "(noch nicht am Telefon geprüft). Die höchste Stufe ist das Lauteste, was das Telefon senden kann."

    /** In place of the slider in the assistant channel. */
    const val ASSISTANT_DETAIL =
        "In diesem Kanal verstellt die App nichts. Soweit bekannt, erreichen die Lautstärketasten diese Lautstärke nur, " +
            "während eine Ansage läuft und das Display an ist."

    const val FROM_NEXT_ANNOUNCEMENT = "Das Hörgerät ist gerade nicht der Tonausgang. Die Stufe gilt ab der nächsten Ansage."

    /** A press on the slider that came while the app looks after the volume of an announcement. */
    const val BUSY = "Gerade läuft eine Ansage. Bitte danach noch einmal."

    private const val NOT_THE_OUTPUT = "Das Hörgerät ist gerade nicht der Tonausgang"
    private const val NO_LONGER_THE_OUTPUT = "Das Hörgerät ist nicht mehr der Tonausgang"
    private const val NO_AID = "Kein Hörgerät gewählt"

    /** What the sample says: "Lautstärke Stufe 18". */
    fun sample(step: Int): String = "Lautstärke Stufe $step"

    /** "Stufe 18 von 25". */
    fun step(step: Int, max: Int): String = "Stufe $step von $max"

    fun leadIn(leadIn: LeadIn): String = when (leadIn) {
        LeadIn.SHORT -> "0,5 s"
        LeadIn.MEDIUM -> "1,5 s"
        LeadIn.LONG -> "3 s"
    }

    fun channel(channel: AnnounceChannel): String = when (channel) {
        AnnounceChannel.MEDIA -> "Medien"
        AnnounceChannel.ASSISTANT -> "Assistent"
    }

    /**
     * For the folded section: "Stufe 18 von 25" with a step of the app's own, "wie Telefon ·
     * Stufe 15 von 25" without. A step of the phone is only named while it is the hearing
     * aid's: with another output the number belongs to that one.
     */
    fun summary(settings: AnnounceSettings, live: VolumeLive): String {
        if (settings.channel == AnnounceChannel.ASSISTANT) {
            val step = live.assistantStep
            val max = live.assistantMax
            return if (live.assistantToAid && step != null && max != null) "Assistent-Kanal · ${step(step, max)}" else "Assistent-Kanal"
        }
        settings.own?.let { own -> return step(own.on(live.mediaMax).step, live.mediaMax) }
        return when {
            !live.mediaToAid -> "wie Telefon"
            live.mediaSilent -> "wie Telefon · stumm"
            else -> "wie Telefon · " + step(live.mediaStep, live.mediaMax)
        }
    }

    /**
     * The one line that tells what an announcement would be played with right now. It
     * names what the app sees and no more: with media muted and Do Not Disturb on it says
     * both and not which caused which, and in the assistant channel, where the app cannot
     * see a mute at all, it says that Do Not Disturb is on.
     */
    fun status(settings: AnnounceSettings, live: VolumeLive): String {
        if (!live.aidSelected) return NO_AID
        if (settings.channel == AnnounceChannel.ASSISTANT) {
            val step = live.assistantStep
            val max = live.assistantMax
            if (!live.assistantToAid) return NOT_THE_OUTPUT
            val line =
                if (step == null || max == null) "Android zeigt die Assistenten-Lautstärke nicht; die App kann sie nicht stellen"
                else "Android spielt Ansagen mit ${step(step, max)}; die App kann das nicht stellen"
            return if (live.doNotDisturb) "$line · „Bitte nicht stören“ ist an und kann Ansagen stumm schalten" else line
        }
        return when {
            !live.mediaToAid -> NOT_THE_OUTPUT
            live.mediaSilent && live.doNotDisturb -> "Medien sind stumm, und „Bitte nicht stören“ ist an: Ansagen sind jetzt nicht zu hören"
            live.mediaSilent -> "Die Medienlautstärke ist stumm geschaltet: Ansagen sind jetzt nicht zu hören"
            !live.mediaGoverned -> "Android führt Ansagen auf diesem Telefon nicht unter der Medienlautstärke: Regler und Stufe wirken nicht auf sie"
            else -> "Medienlautstärke fürs Hörgerät: " + step(live.mediaStep, live.mediaMax)
        }
    }

    /**
     * The step the slider stands at: the app's own step on today's scale, or, while
     * announcements follow the phone, the phone's step for the hearing aid. Null if there is
     * no step to show: the assistant channel, or a phone value that is not the aid's.
     */
    fun sliderStep(settings: AnnounceSettings, live: VolumeLive): Int? = when {
        settings.channel != AnnounceChannel.MEDIA -> null
        settings.own != null -> settings.own!!.on(live.mediaMax).step
        live.mediaToAid && !live.mediaSilent -> live.mediaStep
        else -> null
    }

    /**
     * "Stufe 18 von 25" next to the slider, "Stufe – von 25" while there is no step to show.
     *
     * @param dragged the step under the finger while the slider is being moved
     */
    fun sliderLabel(settings: AnnounceSettings, live: VolumeLive, dragged: Int? = null): String {
        val step = dragged ?: sliderStep(settings, live)
        return if (step == null) "Stufe – von ${live.mediaMax}" else step(step, live.mediaMax)
    }

    /** "Android hat die Änderung nicht übernommen (Bitte nicht stören)". */
    fun refused(why: Why?): String = "Android hat die Änderung nicht übernommen (${refusal(why)})"

    private fun refusal(why: Why?): String = when (why) {
        Why.DND -> "Bitte nicht stören"
        Why.USER_RESTRICTION -> "Lautstärke ist auf diesem Telefon gesperrt"
        Why.OTHER -> "Android hat einen anderen Wert gesetzt"
        Why.MUTED -> "Medien wurden stumm geschaltet"
        else -> "Grund unbekannt"
    }

    /** "Nicht verstellt, weil gerade etwas anderes im Hörgerät spielt." */
    fun notChanged(why: Why?): String = "Nicht verstellt, weil ${untouched(why)}."

    /** How "Nicht verstellt, weil …" goes on. */
    private fun untouched(why: Why?): String = when (why) {
        Why.SILENCE_TEST -> "der Stille-Test nichts Hörbares spielt"
        Why.ASSISTANT_CHANNEL -> "der Kanal „Assistent“ gewählt ist"
        Why.NOT_MUSIC_STREAM -> "Android diese Ansage nicht unter der Medienlautstärke führt"
        Why.ROUTE_NOT_AID -> "das Hörgerät nicht der Tonausgang ist"
        Why.VOLUME_FIXED -> "die Lautstärke auf diesem Telefon fest ist"
        Why.MUTED -> "die Medienlautstärke stumm geschaltet ist"
        Why.MODE_NOT_NORMAL -> "das Telefon in einem Anruf ist"
        Why.OTHER_MEDIA -> "gerade etwas anderes im Hörgerät spielt"
        Why.NOTE_NOT_SAVED -> "die App sich den alten Wert nicht merken konnte"
        Why.ERROR -> "Android einen Fehler gemeldet hat"
        Why.DND -> "„Bitte nicht stören“ an ist"
        Why.USER_RESTRICTION -> "die Lautstärke auf diesem Telefon gesperrt ist"
        Why.UNKNOWN, Why.OTHER, null -> "Android es nicht zulässt"
    }

    /**
     * The truth after a sample: "Gespielt mit Stufe 18", or what Android did not take, or
     * why nothing was played, or that it was cut off. Null if there is nothing to say,
     * because a newer sample has taken this one's place.
     */
    fun afterSample(report: VolumeReport): String? {
        when (report.notPlayed) {
            null -> Unit
            "superseded" -> return null
            "no_hearing_aid_selected" -> return "Nicht gespielt: $NO_AID."
            "hearing_aid_not_connected", "output_is_not_the_hearing_aid", "output_changed" -> return "Nicht gespielt: $NOT_THE_OUTPUT."
            "session_busy" -> return "Nicht gespielt: Eine Sitzung läuft."
            else -> return "Nicht gespielt, siehe Protokoll (${report.notPlayed})."
        }
        // begun, but not spoken to its end: how much of it was heard nobody knows
        when (report.ended) {
            AnnounceVolumeRules.RESULT_OK -> Unit
            "superseded" -> return null
            "output_lost", AnnounceVolumeRules.RESULT_GOING_AWAY -> return "Abgebrochen: $NO_LONGER_THE_OUTPUT."
            "session_busy" -> return "Abgebrochen: Eine Sitzung läuft."
            else -> return "Abgebrochen, siehe Protokoll (${report.ended})."
        }
        if (!report.governsMusic) {
            return "Gespielt, aber Android führt diese Ansage nicht unter der Medienlautstärke: Die Stufe wirkt nicht auf sie."
        }
        val played = if (report.step <= 0) "Nichts zu hören." else "Gespielt mit Stufe ${report.step}."
        return when (report.action) {
            Action.REFUSED -> refused(report.why) + ". " + played
            Action.SKIPPED -> notChanged(report.why) + " " + played
            else -> played.removeSuffix(".")
        }
    }
}
