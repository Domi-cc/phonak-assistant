package io.github.domicc.lara.announce

import android.content.Context
import androidx.core.content.edit
import io.github.domicc.lara.core.announce.AnnounceChannel
import io.github.domicc.lara.core.announce.AnnounceSettings
import io.github.domicc.lara.core.announce.LeadIn
import io.github.domicc.lara.core.announce.StoredStep
import io.github.domicc.lara.core.announce.VolumeNote
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the app remembers about the loudness of its announcements between starts: the
 * settings of the section "Ansagen" (channel, own step for announcements, lead-in), the note
 * of a media volume it changed and has not put back for good yet, and the Android build whose
 * volume curves it has journaled. Kept in app-private preferences.
 *
 * Every change of a setting is journaled as `announce_setting`, with all four values and with
 * who changed it (`origin`: the screen, adb, the volume keys during an announcement, or the
 * app itself when Android's scale has changed): how loud an announcement was can only be
 * read together with the settings in force.
 */
class AnnounceVolumeStore(context: Context, private val journal: Journal) {

    private val prefs = context.getSharedPreferences("announce_volume", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    /** The copy that is read; storage is only read at the start. */
    @Volatile
    private var noteNow: VolumeNote? = loadNote()

    val settings: StateFlow<AnnounceSettings> = state.asStateFlow()

    fun setChannel(channel: AnnounceChannel, origin: String) = change("channel", origin) { it.copy(channel = channel) }

    fun setLeadIn(leadIn: LeadIn, origin: String) = change("lead_in", origin) { it.copy(leadIn = leadIn) }

    /**
     * "Eigene Lautstärke nur für Ansagen".
     *
     * @param firstStep the step to begin with if none was ever chosen; only asked then
     */
    fun setOwnLevel(on: Boolean, origin: String, firstStep: () -> StoredStep) =
        change("own", origin) { it.copy(ownLevel = on, step = if (on) it.step ?: firstStep() else it.step) }

    /** The step for announcements. Kept also while the switch is off. */
    fun setStep(step: StoredStep, origin: String) = change("step", origin) { it.copy(step = step) }

    @Synchronized
    private fun change(what: String, origin: String, to: (AnnounceSettings) -> AnnounceSettings) {
        val before = state.value
        val after = to(before)
        if (after == before) return
        prefs.edit {
            putString(KEY_CHANNEL, after.channel.word)
            putBoolean(KEY_OWN, after.ownLevel)
            putInt(KEY_LEAD_IN, after.leadIn.ms)
            after.step?.let {
                putInt(KEY_STEP, it.step)
                putInt(KEY_STEP_MAX, it.max)
            }
        }
        journal.log("announce_setting", mapOf("changed" to what, "origin" to origin) + fields(after))
        state.value = after
    }

    /**
     * The media volume the app changed for an announcement and has not put back for good;
     * null if there is none. See [VolumeNote] and
     * [io.github.domicc.lara.core.announce.AnnounceVolumeRules].
     */
    val note: VolumeNote? get() = noteNow

    /**
     * Writes the note to storage before this returns, not some time later: it has to be
     * there before the volume is changed, because the process may not live to put the
     * value back.
     *
     * @return false if it could not be written; the volume must not be changed then
     */
    fun saveNote(note: VolumeNote): Boolean {
        val written = write(note)
        if (written) noteNow = note else journal.log("announce_volume_store_error", mapOf("what" to "note"))
        return written
    }

    /**
     * Puts [note] in the place of the one in storage, or clears it with null. Also before
     * this returns: a note that came back after a process death, although its restore was
     * done, would wait for the value it names and then put an old value over one the owner
     * chose. It happens a few times per announcement at most.
     *
     * If storage does not take it the copy in memory is changed all the same, so that this
     * process goes by what really happened; the journal gets a line.
     */
    fun replaceNote(note: VolumeNote?) {
        if (note == noteNow) return
        if (!write(note)) journal.log("announce_volume_store_error", mapOf("what" to if (note == null) "note_clear" else "note_replace"))
        noteNow = note
    }

    /**
     * Into storage before this returns: `commit`, and its answer is what this returns.
     *
     * @return whether storage has it
     */
    private fun write(note: VolumeNote?): Boolean {
        if (note == null) {
            val cleared = prefs.edit().remove(KEY_NOTE_BEFORE).remove(KEY_NOTE_SET).remove(KEY_NOTE_RESTORED).commit()
            return cleared
        }
        val written = prefs.edit()
            .putInt(KEY_NOTE_BEFORE, note.before)
            .putInt(KEY_NOTE_SET, note.set)
            .putBoolean(KEY_NOTE_RESTORED, note.restored)
            .commit()
        return written
    }

    /** The Android build (`Build.ID`) whose volume curves are in the journal; null if none is. */
    var curveBuild: String?
        get() = prefs.getString(KEY_CURVE_BUILD, null)
        set(value) = prefs.edit { putString(KEY_CURVE_BUILD, value) }

    /** A value that is missing or unreadable falls back to its default. */
    private fun load(): AnnounceSettings {
        val step = prefs.getInt(KEY_STEP, NONE).takeIf { it != NONE }
        val max = prefs.getInt(KEY_STEP_MAX, NONE).takeIf { it != NONE }
        return AnnounceSettings(
            channel = AnnounceChannel.fromWord(prefs.getString(KEY_CHANNEL, null)) ?: AnnounceChannel.DEFAULT,
            ownLevel = prefs.getBoolean(KEY_OWN, false),
            step = if (step != null && max != null) StoredStep(step, max) else null,
            leadIn = LeadIn.fromMs(prefs.getInt(KEY_LEAD_IN, NONE)) ?: LeadIn.DEFAULT,
        )
    }

    private fun loadNote(): VolumeNote? {
        val before = prefs.getInt(KEY_NOTE_BEFORE, NONE)
        val set = prefs.getInt(KEY_NOTE_SET, NONE)
        return if (before == NONE || set == NONE) null else VolumeNote(before, set, prefs.getBoolean(KEY_NOTE_RESTORED, false))
    }

    companion object {
        private const val KEY_CHANNEL = "channel"
        private const val KEY_OWN = "own_level"
        private const val KEY_STEP = "step"
        private const val KEY_STEP_MAX = "step_max"
        private const val KEY_LEAD_IN = "lead_in_ms"
        private const val KEY_NOTE_BEFORE = "note_before"
        private const val KEY_NOTE_SET = "note_set"
        private const val KEY_NOTE_RESTORED = "note_restored"
        private const val KEY_CURVE_BUILD = "curve_build"
        private const val NONE = -1

        /** The settings as a journal line names them: `own` is the step or "off". */
        fun fields(settings: AnnounceSettings): Map<String, Any?> = mapOf(
            "channel" to settings.channel.word,
            "own" to (settings.own?.step ?: "off"),
            "step" to settings.step?.step,
            "stepMax" to settings.step?.max,
            "leadInMs" to settings.leadIn.ms,
        )
    }
}
