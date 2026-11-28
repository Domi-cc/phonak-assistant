package io.github.domicc.lara.core.announce

/**
 * What the app wrote down before it changed the media volume for an announcement: the value
 * to put back, and the value it set. Kept in storage from before the write until the value
 * is back for good, so that a process that dies in between, or a hearing aid that goes away,
 * does not leave the owner's music at the loudness of an announcement.
 *
 * "Back for good" is more than a write that reads back. Android keeps the media volume for
 * the hearing aid in two places: its own audio service, which is what an app writes and
 * reads, and the Bluetooth stack, which remembers one value per device and hands it back to
 * Android at the next connection. The Bluetooth stack takes a new value only for the aid
 * that is its active media device, and an aid that is switched off stops being that about a
 * second before Android's route shows it (read from the Android 17 source, not tried on the
 * phone). A restore in that second reads back and is forgotten at the next connection. So a
 * note is not cleared by the restore: it is marked [restored] and cleared by a look some
 * time later that still finds the hearing aid as the route.
 *
 * @param before the phone's own value, the one to put back
 * @param set what the app set, or what stood in its place when the app last looked; a
 * later look puts [before] back only while this stands. Never 0.
 * @param restored the app has put [before] back and read it back; [set] is the value that
 * stood before, the one that would come back with the hearing aid if the Bluetooth stack
 * did not hear of the restore
 */
data class VolumeNote(val before: Int, val set: Int, val restored: Boolean = false)

/**
 * The decisions around the loudness of an announcement that need no Android: whether the app
 * may touch the media volume at all, what it sets, how it reads the answer, what it puts
 * back, and what becomes of a note that was left over.
 *
 * Background, in short (CLAUDE.md, "How loud an announcement is"). The hearing aid takes no
 * volume command from the phone, so every volume on the phone is a digital gain in the
 * phone's mixer, and the highest step is the loudest the phone can send. An app can set
 * the media volume without a permission, but only for the output the media are routed to
 * at that moment, and Android drops such a call without a word in several situations. And
 * the media volume for the aid is also the volume of the owner's music in it. So:
 *
 * - The app writes only while the hearing aid alone is the media route, asked right before
 *   every read and every write. The app asks; the rules here take the answer.
 * - It never writes into something that is not its to change: a muted stream, a fixed
 *   volume, a call, other media that are playing (they would be lifted with the
 *   announcement).
 * - Every write is read back, and only a value that reads back as wanted counts.
 * - What it changed it puts back, and what it could not put back it remembers
 *   ([VolumeNote]), until a look has confirmed that the value is back for good.
 * - It never writes 0, never loops, and tries once.
 */
object AnnounceVolumeRules {

    /**
     * How long after the last word the phone's value is put back. The gain is applied in the
     * phone's mixer, to sound that then still travels to the aid; half a second keeps the
     * end of the last word at the loudness of the rest. Not measured.
     */
    const val RESTORE_WAIT_MS = 500L

    /**
     * How long after a restoring write the look comes that confirms it. A hearing aid that
     * was on its way out at the write is gone from Android's route a second later at most
     * (`BECOMING_NOISY_DELAY_MS` in the Android 17 source); if it is still the route after
     * twice that, it was there, and the Bluetooth stack has the value too.
     */
    const val CONFIRM_AFTER_MS = 2_000L

    /** The announcer's word for an announcement that was spoken to its end. */
    const val RESULT_OK = "ok"

    /** The announcer's word for an announcement cut off because Android said the output is about to go. */
    const val RESULT_GOING_AWAY = "output_going_away"

    /** What was done about the volume before an announcement. [word] goes into the journal. */
    enum class Action(val word: String) {
        /** The setting says: play at the phone's value. */
        KEPT("kept"),

        /** A step is set, but something says not to touch the volume now; see [Why]. */
        SKIPPED("skipped"),

        /** The value was the wanted step already. Nothing written, but looked after like [SET]. */
        ALREADY("already"),

        /** The step was written and read back. */
        SET("set"),

        /** The step was written and did not read back; see [Why]. */
        REFUSED("refused"),
    }

    /** Why the volume was not touched, or how a refused write is classed. [word] goes into the journal. */
    enum class Why(val word: String) {
        SILENCE_TEST("silence_test"),
        ASSISTANT_CHANNEL("assistant_channel"),

        /** Android does not put this announcement under the media volume. */
        NOT_MUSIC_STREAM("not_music_stream"),
        ROUTE_NOT_AID("route_not_aid"),
        VOLUME_FIXED("volume_fixed"),

        /** Muted, or at index 0, which is the same to Android. Also a write that read back as 0. */
        MUTED("muted"),

        /** A call, ringing, or another audio mode that is not the normal one. */
        MODE_NOT_NORMAL("mode_not_normal"),

        /** Media of another app play right now. */
        OTHER_MEDIA("other_media"),

        /** The note could not be written to storage, so nothing was changed. */
        NOTE_NOT_SAVED("note_not_saved"),

        /** Android threw; the journal line names the exception. */
        ERROR("error"),

        /** Refused while Do Not Disturb is on. */
        DND("dnd"),

        /** Refused while the user of this phone may not change volumes. */
        USER_RESTRICTION("user_restriction"),

        /** Refused, and nothing the app can see says why. */
        UNKNOWN("unknown"),

        /** The write ended at a value that is neither the old nor the wanted one. */
        OTHER("other"),
    }

    /** What became of a note from an earlier announcement. [word] goes into the journal. */
    enum class NoteUse(val word: String) {
        NONE("none"),

        /** The value was still the one the app had set: the note's value is the one to put back. */
        USED("used"),

        /** Somebody changed the value since: the note was out of date. */
        DROPPED("dropped"),

        /**
         * There is a note, and this announcement does not set a step of its own. The note
         * is then looked at like at any other moment: [Plan.leftover].
         */
        PENDING("pending"),
    }

    /**
     * What the app found right before an announcement.
     *
     * @param silence the silence test, which plays nothing audible
     * @param own the step the owner chose for announcements; null while the switch is off
     * @param governsMusic Android puts this announcement under the media volume
     * @param routeIsAid the selected hearing aid's media device, and nothing else, is where media go
     * @param index the media volume as Android reports it; 0 for a muted stream
     * @param max the highest media step
     * @param modeNormal the phone's audio mode is the normal one
     * @param otherMedia media play already, before the app's own first sound
     * @param note what an earlier announcement left in storage, if anything
     * @param noteSettled for a [VolumeNote.restored] note: [CONFIRM_AFTER_MS] have passed
     * since its restore, or nobody knows when that was
     */
    data class Situation(
        val channel: AnnounceChannel,
        val silence: Boolean,
        val own: StoredStep?,
        val governsMusic: Boolean,
        val routeIsAid: Boolean,
        val fixed: Boolean,
        val muted: Boolean,
        val index: Int,
        val max: Int,
        val modeNormal: Boolean,
        val otherMedia: Boolean,
        val note: VolumeNote?,
        val noteSettled: Boolean = true,
    )

    /**
     * What to do before an announcement.
     *
     * @param action [Action.KEPT], [Action.SKIPPED], [Action.ALREADY] or [Action.SET]
     * @param wanted the step to play with, inside 1..max; null if the volume is not touched
     * @param trueBefore the value to put back afterwards; null if the volume is not touched
     * @param note to be written to storage before anything else happens; null if the
     * volume is not touched, and then a note in storage stays as it is
     * @param rescaled the owner's step on today's scale, if it was stored on another one or
     * outside its scale: to be stored in place of the old one
     * @param leftover for a plan that does not touch the volume: what to do about a note in
     * storage, to be carried out in the same moment, before the announcement plays
     */
    data class Plan(
        val action: Action,
        val why: Why? = null,
        val wanted: Int? = null,
        val trueBefore: Int? = null,
        val noteUse: NoteUse = NoteUse.NONE,
        val note: VolumeNote? = null,
        val rescaled: StoredStep? = null,
        val leftover: Leftover = Leftover.None,
    ) {
        /** The app takes the volume into its care for this announcement: a note, and a restore at the end. */
        val touches: Boolean get() = action == Action.SET || action == Action.ALREADY
    }

    /** Decides what happens to the volume before an announcement. */
    fun before(s: Situation): Plan {
        val pending = if (s.note != null) NoteUse.PENDING else NoteUse.NONE
        // An announcement that sets no step of its own must not walk past a value an earlier
        // one left raised: whatever keeps the app from setting its step now (the switch is
        // off, the owner's podcast plays) is no reason to leave his value undone.
        val leftover = leftover(s.note, s.routeIsAid, s.muted, s.index, s.max, s.noteSettled)
        val own = s.own ?: return Plan(Action.KEPT, noteUse = pending, leftover = leftover)
        val today = own.on(s.max)
        val rescaled = today.takeIf { it != own }
        val why = when {
            s.silence -> Why.SILENCE_TEST
            s.channel != AnnounceChannel.MEDIA -> Why.ASSISTANT_CHANNEL
            !s.governsMusic -> Why.NOT_MUSIC_STREAM
            !s.routeIsAid -> Why.ROUTE_NOT_AID
            s.fixed -> Why.VOLUME_FIXED
            // A set to another index would unmute what the owner or Do Not Disturb muted,
            // and 0 is no value to remember and put back.
            s.muted || s.index <= 0 -> Why.MUTED
            !s.modeNormal -> Why.MODE_NOT_NORMAL
            s.otherMedia -> Why.OTHER_MEDIA
            else -> null
        }
        if (why != null) return Plan(Action.SKIPPED, why, noteUse = pending, rescaled = rescaled, leftover = leftover)

        val wanted = today.step
        // A note from an announcement whose restore was lost, or is not confirmed yet. If
        // the value is the one the app set then, the value to put back is the one from
        // before that announcement.
        val noteUse = when {
            s.note == null -> NoteUse.NONE
            // a note that names 0 as the value to put back is broken: 0 is never written
            s.index == s.note.set && s.note.before >= 1 -> NoteUse.USED
            else -> NoteUse.DROPPED
        }
        val trueBefore = if (noteUse == NoteUse.USED) VolumeSteps.clamp(s.note!!.before, s.max) else s.index
        return Plan(
            action = if (s.index == wanted) Action.ALREADY else Action.SET,
            wanted = wanted,
            trueBefore = trueBefore,
            noteUse = noteUse,
            note = VolumeNote(before = trueBefore, set = wanted),
            rescaled = rescaled,
        )
    }

    /**
     * The value the app looks after during the announcement, for a plan that needed no
     * write: the wanted step if it stood already, null if the volume is not touched.
     */
    fun standing(plan: Plan): Int? = plan.wanted.takeIf { plan.action == Action.ALREADY }

    /**
     * How a write ended, or why it was not made.
     *
     * @param action [Action.SET], [Action.REFUSED], or [Action.SKIPPED] if the route was lost before it
     * @param note what has to stand in storage now; null: no note
     * @param standing the value the app set and has to look after until the restore; null
     * if nothing was changed
     */
    data class Written(val action: Action, val why: Why?, val note: VolumeNote?, val standing: Int?)

    /**
     * Reads the answer to the one write of [plan]. Android sets the index inside the call,
     * so the value read right after it tells whether the call was taken; a call that is
     * dropped says nothing.
     *
     * @param before the value read before the write
     * @param got the value read after it
     * @param oldNote the note that was in storage before [plan]'s note replaced it
     * @param doNotDisturb Do Not Disturb is on in any form
     * @param userRestricted the user of this phone may not change volumes
     */
    fun afterWrite(plan: Plan, before: Int, got: Int, oldNote: VolumeNote?, doNotDisturb: Boolean, userRestricted: Boolean): Written =
        when {
            got == plan.wanted -> Written(Action.SET, null, plan.note, standing = got)
            // Muted in the moment of the write, and what stands under a mute cannot be
            // read. 0 is never noted: a note that named it would later take any muted
            // stream for the app's value. With no note in use, the plan's note is right
            // whichever way it went: if the step was taken it comes back with the unmute and
            // the note recognises it, if not the note is out of date and goes. With a note
            // in use the write was most likely dropped (that is what a mute by Do Not
            // Disturb does), and then the old note still describes what stands.
            got < 1 -> Written(Action.REFUSED, Why.MUTED, if (plan.noteUse == NoteUse.USED) oldNote else plan.note, standing = null)
            // Nothing changed.
            got == before -> Written(Action.REFUSED, refusal(before, got, doNotDisturb, userRestricted), unwritten(plan, oldNote), standing = null)
            // Neither taken nor dropped: the value moved, so it is noted and put back like a
            // step the app set.
            else -> Written(Action.REFUSED, Why.OTHER, VolumeNote(before = plan.trueBefore ?: before, set = got), standing = got)
        }

    /**
     * The hearing aid stopped being the media route between the note and the write: nothing
     * is written, and the announcement is not played.
     *
     * @param oldNote the note that was in storage before [plan]'s note replaced it
     */
    fun routeLostBeforeWrite(plan: Plan, oldNote: VolumeNote?): Written =
        Written(Action.SKIPPED, Why.ROUTE_NOT_AID, unwritten(plan, oldNote), standing = null)

    /**
     * What stands in storage when [plan]'s write did not happen or was not taken: the note
     * from before if this plan was using it, because it still describes what stands, and
     * nothing otherwise. A note that the plan found out of date does not come back: it
     * would later put its old value over one the owner chose.
     */
    private fun unwritten(plan: Plan, oldNote: VolumeNote?): VolumeNote? = oldNote.takeIf { plan.noteUse == NoteUse.USED }

    /**
     * How a write that did not read back as wanted is classed, by what the app can see.
     * For the write of an announcement and for the slider's own.
     *
     * @param before the value read before the write
     * @param got the value read after it
     */
    fun refusal(before: Int, got: Int, doNotDisturb: Boolean, userRestricted: Boolean): Why = when {
        got < 1 -> Why.MUTED
        got != before -> Why.OTHER
        doNotDisturb -> Why.DND
        userRestricted -> Why.USER_RESTRICTION
        else -> Why.UNKNOWN
    }

    /**
     * Keeps the note of a running announcement in step with what stands. While a sound of
     * the media plays the volume keys change the media volume, and the app only reads it
     * again at the restore. If the route is lost before that, a note that still names the
     * app's step would not recognise the value the keys left, and it would stay for good.
     * Asked again and again while the announcement plays.
     *
     * @param index the media volume read now; only the hearing aid's while [routeIsAid]
     * @return the note to keep in storage: [note] itself unless the value has moved
     */
    fun watched(note: VolumeNote?, routeIsAid: Boolean, muted: Boolean, index: Int): VolumeNote? =
        if (note != null && !note.restored && routeIsAid && !muted && index >= 1 && index != note.set) note.copy(set = index) else note

    /** How the restore after an announcement ended. [word] goes into the journal. */
    enum class Restore(val word: String) {
        /** The phone's value is back. */
        RESTORED("restored"),

        /** The value was changed during the announcement and is the new step; the phone's value is back. */
        ADOPTED("adopted"),

        /** The stream was muted during the announcement: left as it is, and the note waits for the unmute. */
        LEFT_MUTED("left_muted"),

        /** The hearing aid is no longer the media route: nothing written, the note stays. */
        DEFERRED_ROUTE("deferred_route"),

        /**
         * Android said the output is about to go. It still reads as the hearing aid, but a
         * write now would not reach the Bluetooth stack: nothing written, the note stays.
         */
        DEFERRED_GOING_AWAY("deferred_going_away"),

        /** The phone's value was the step: nothing to write. */
        NOTHING_TO_RESTORE("nothing_to_restore"),

        /** The write that puts the phone's value back was not taken: the note stays. */
        RESTORE_REFUSED("restore_refused"),
    }

    /**
     * What the restore does.
     *
     * @param result how it ends if [write] is null, and if the write is taken
     * @param write the index to put back; null: nothing is written
     * @param adopt the value found in place of the one the app had set: the owner's new
     * step for announcements; null if the value was untouched
     * @param note what stands in storage if nothing is written; null: no note
     * @param stood the value that stands for the hearing aid as far as the app knows
     */
    data class RestorePlan(val result: Restore, val write: Int?, val adopt: Int?, val note: VolumeNote?, val stood: Int)

    /**
     * Decides the restore. The route comes first: without the hearing aid as the route the
     * value read belongs to another device and says nothing.
     *
     * While a sound of the media plays, the phone's volume keys change the media volume,
     * also in the pocket. A value that is no longer the one the app set was therefore set
     * by the owner, for this announcement: it becomes the step for the next ones, and the
     * phone's own value is still put back.
     *
     * An announcement that ended with [RESULT_GOING_AWAY] is treated like a lost route,
     * although Android still names the hearing aid: see [VolumeNote].
     *
     * @param standing the value the app set (or found) for the announcement
     * @param trueBefore the value to put back
     * @param now the value read now; 0 for a muted stream
     * @param seen the value last seen for the hearing aid while the announcement played
     * ([watched]); [standing] if nothing else was seen
     * @param result how the announcement ended, in the announcer's words; null if it broke off
     */
    fun restore(
        standing: Int,
        trueBefore: Int,
        routeIsAid: Boolean,
        muted: Boolean,
        now: Int,
        seen: Int = standing,
        result: String? = RESULT_OK,
    ): RestorePlan {
        /** Nothing is written now: the note names what a later look will find standing. */
        fun deferred(how: Restore, stands: Int) =
            RestorePlan(how, write = null, adopt = stands.takeIf { it != standing }, note = toPutBack(trueBefore, stands), stood = stands)
        val audible = !muted && now >= 1
        return when {
            !routeIsAid -> deferred(Restore.DEFERRED_ROUTE, seen)
            // the value can still be read, and it is the hearing aid's
            result == RESULT_GOING_AWAY -> deferred(Restore.DEFERRED_GOING_AWAY, if (audible) now else seen)
            // A write would unmute it. Under the mute the step still stands, and it comes
            // back with the unmute: the note waits for that.
            !audible -> RestorePlan(Restore.LEFT_MUTED, write = null, adopt = null, note = toPutBack(trueBefore, seen), stood = seen)
            else -> {
                val adopt = now.takeIf { it != standing }
                val changed = if (adopt != null) Restore.ADOPTED else null
                if (now == trueBefore) RestorePlan(changed ?: Restore.NOTHING_TO_RESTORE, write = null, adopt = adopt, note = null, stood = now)
                else RestorePlan(changed ?: Restore.RESTORED, write = trueBefore, adopt = adopt, note = toPutBack(trueBefore, now), stood = now)
            }
        }
    }

    /** A note that says: [set] stands, [before] is to be put back. None if there is nothing to put back or 0 would be noted. */
    private fun toPutBack(before: Int, set: Int): VolumeNote? = VolumeNote(before, set).takeIf { before >= 1 && set >= 1 && before != set }

    /**
     * How the restore ended, and what stays in storage.
     *
     * @param note null: the note is cleared
     */
    data class RestoreEnd(val result: Restore, val note: VolumeNote?)

    /**
     * Reads the answer to the restoring write of [plan]. A write that reads back does not
     * clear the note: it is marked ([VolumeNote.restored]) and waits for the look that
     * confirms it, [CONFIRM_AFTER_MS] later.
     *
     * @param back the value read after the write
     */
    fun afterRestore(plan: RestorePlan, trueBefore: Int, back: Int): RestoreEnd = when {
        back == plan.write -> RestoreEnd(plan.result, VolumeNote(before = trueBefore, set = plan.stood, restored = true))
        // muted in that moment: 0 is never noted, and the value from before the write is the best there is
        back < 1 -> RestoreEnd(Restore.RESTORE_REFUSED, toPutBack(trueBefore, plan.stood))
        // what stands now is what a later look has to compare with
        else -> RestoreEnd(Restore.RESTORE_REFUSED, toPutBack(trueBefore, back))
    }

    /** The hearing aid stopped being the media route between the read of [plan] and its write: nothing is written. */
    fun restoreRouteLost(plan: RestorePlan, trueBefore: Int): RestoreEnd =
        RestoreEnd(Restore.DEFERRED_ROUTE, toPutBack(trueBefore, plan.stood))

    /**
     * How long to wait after an announcement that ended with [result] before the value is
     * put back. Half a second after one that was spoken to its end: the gain is applied in
     * the phone's mixer, to sound that then still travels to the aid. Not at all after one
     * that was cut off, whatever cut it: nothing of it is on its way any more, and what cut
     * it (a session, a call, another device) is about to take the route.
     */
    fun restoreWaitMs(result: String?): Long = if (result == RESULT_OK) RESTORE_WAIT_MS else 0L

    /** What to do with a note outside the announcement that wrote it. [word] goes into the journal. */
    sealed interface Leftover {
        val word: String

        /** There is no note. */
        data object None : Leftover {
            override val word = "none"
        }

        /** Another device is the route: no compare, no write, the note stays. */
        data object Wait : Leftover {
            override val word = "waiting_route"
        }

        /** The stream is muted: a write would unmute it, and the value under the mute cannot be read. The note stays. */
        data object WaitMuted : Leftover {
            override val word = "waiting_unmute"
        }

        /** A restore that is too fresh to be confirmed. The note stays. */
        data object TooEarly : Leftover {
            override val word = "waiting_check"
        }

        /** The note is out of date or has nothing to put back: clear it. */
        data object Drop : Leftover {
            override val word = "dropped"
        }

        /** The restore held, and the hearing aid is still there: clear the note. */
        data object Confirmed : Leftover {
            override val word = "confirmed"
        }

        /** The app's value stands: write [index], and mark the note if that reads back ([afterPutBack]). */
        data class PutBack(val index: Int) : Leftover {
            override val word = "restored"
        }
    }

    /**
     * Decides what becomes of a note outside the announcement that wrote it: at the start
     * of the app, when the hearing aid becomes the media route again, before an
     * announcement that does not set a step of its own, and whenever the app looks.
     *
     * @param index the media volume read now; only meaningful while [routeIsAid]
     * @param settled for a [VolumeNote.restored] note: [CONFIRM_AFTER_MS] have passed since
     * its restore, or nobody knows when that was
     */
    fun leftover(note: VolumeNote?, routeIsAid: Boolean, muted: Boolean, index: Int, max: Int, settled: Boolean = true): Leftover {
        if (note == null) return Leftover.None
        if (!routeIsAid) return Leftover.Wait
        // Never judged from the index alone: a muted stream reads 0 whatever stands under it.
        if (muted || index <= 0) return Leftover.WaitMuted
        val back = VolumeSteps.clamp(note.before, max)
        return when {
            // The app's value stands, or stands again because the Bluetooth stack handed
            // it back. 0 is never written, and the same value needs no write. (A note that
            // names 0 as set never matches here: the index is at least 1.)
            note.before >= 1 && index == note.set && back != index -> Leftover.PutBack(back)
            note.restored && !settled -> Leftover.TooEarly
            note.restored && index == back -> Leftover.Confirmed
            // somebody set another value since: theirs stands
            else -> Leftover.Drop
        }
    }

    /**
     * Reads the answer to the write of a [Leftover.PutBack].
     *
     * @param wrote the index that was written
     * @param back the value read after the write
     * @return the note to keep: marked, to be confirmed like a restore after an
     * announcement, or as it was if the write was not taken
     */
    fun afterPutBack(note: VolumeNote, wrote: Int, back: Int): VolumeNote = if (back == wrote) note.copy(restored = true) else note

    /**
     * Whether the slider on the screen can be moved. With the switch on it holds the app's
     * own step and needs nothing from Android. With the switch off it is the phone's media
     * volume for the hearing aid itself, and that exists only while the aid alone is the
     * media route and Android lets the value be changed.
     */
    fun sliderEnabled(channel: AnnounceChannel, ownLevel: Boolean, mediaToAid: Boolean, muted: Boolean, fixed: Boolean): Boolean =
        channel == AnnounceChannel.MEDIA && (ownLevel || (mediaToAid && !muted && !fixed))

    /**
     * What a release of the slider does while announcements follow the phone: the slider
     * is then the phone's media volume for the hearing aid itself.
     *
     * @param why why nothing is written; null if the step can be written or stands already
     * @param write the step is to be written; false with no [why]: it stands already
     */
    data class SliderPlan(val why: Why?, val write: Boolean)

    /**
     * Decides the slider's write of the phone's media volume. The same gate as for an
     * announcement, as far as it applies: the route first, never into a fixed volume, never
     * into a muted stream (the write would unmute it).
     *
     * @param before the media volume read now; only the hearing aid's while [routeIsAid]
     * @param wanted the step the slider was let go at, inside 1..max
     */
    fun sliderWrite(routeIsAid: Boolean, fixed: Boolean, muted: Boolean, before: Int, wanted: Int): SliderPlan {
        val why = when {
            !routeIsAid -> Why.ROUTE_NOT_AID
            fixed -> Why.VOLUME_FIXED
            muted || before <= 0 -> Why.MUTED
            else -> null
        }
        return SliderPlan(why, write = why == null && before != wanted)
    }

    /** How the slider's write ended: [Action.SET], [Action.ALREADY] or [Action.REFUSED] with its class. */
    data class SliderEnd(val action: Action, val why: Why?)

    /**
     * Reads the answer to the slider's write, or to the write it did not need.
     *
     * @param got the value read last
     */
    fun afterSliderWrite(before: Int, wanted: Int, got: Int, doNotDisturb: Boolean, userRestricted: Boolean): SliderEnd =
        if (got == wanted) SliderEnd(if (before == wanted) Action.ALREADY else Action.SET, null)
        else SliderEnd(Action.REFUSED, refusal(before, got, doNotDisturb, userRestricted))
}
