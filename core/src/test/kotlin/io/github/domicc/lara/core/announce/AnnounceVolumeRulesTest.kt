package io.github.domicc.lara.core.announce

import io.github.domicc.lara.core.announce.AnnounceVolumeRules.Action
import io.github.domicc.lara.core.announce.AnnounceVolumeRules.Leftover
import io.github.domicc.lara.core.announce.AnnounceVolumeRules.NoteUse
import io.github.domicc.lara.core.announce.AnnounceVolumeRules.Restore
import io.github.domicc.lara.core.announce.AnnounceVolumeRules.Situation
import io.github.domicc.lara.core.announce.AnnounceVolumeRules.Why
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnnounceVolumeRulesTest {

    /** The ordinary case: the aid is the media route, the phone stands at 12 of 25, the owner chose 20. */
    private fun situation(
        channel: AnnounceChannel = AnnounceChannel.MEDIA,
        silence: Boolean = false,
        own: StoredStep? = StoredStep(20, 25),
        governsMusic: Boolean = true,
        routeIsAid: Boolean = true,
        fixed: Boolean = false,
        muted: Boolean = false,
        index: Int = 12,
        max: Int = 25,
        modeNormal: Boolean = true,
        otherMedia: Boolean = false,
        note: VolumeNote? = null,
        noteSettled: Boolean = true,
    ) = Situation(channel, silence, own, governsMusic, routeIsAid, fixed, muted, index, max, modeNormal, otherMedia, note, noteSettled)

    private fun before(situation: Situation) = AnnounceVolumeRules.before(situation)

    // ---- before an announcement

    @Test
    fun `with the switch off the phone's value stands and nothing is noted`() {
        val plan = before(situation(own = null))
        assertEquals(Action.KEPT, plan.action)
        assertNull(plan.why)
        assertNull(plan.wanted)
        assertNull(plan.note)
        assertFalse(plan.touches)
        assertEquals(NoteUse.NONE, plan.noteUse)
    }

    @Test
    fun `the chosen step is set and the phone's value is noted before the write`() {
        val plan = before(situation())
        assertEquals(Action.SET, plan.action)
        assertEquals(20, plan.wanted)
        assertEquals(12, plan.trueBefore)
        assertEquals(VolumeNote(before = 12, set = 20), plan.note)
        assertEquals(NoteUse.NONE, plan.noteUse)
        assertTrue(plan.touches)
        assertNull(plan.rescaled)
    }

    @Test
    fun `a value that is already the chosen step needs no write but is looked after all the same`() {
        val plan = before(situation(index = 20))
        assertEquals(Action.ALREADY, plan.action)
        assertEquals(20, plan.wanted)
        assertEquals(20, plan.trueBefore)
        // noted too: the owner may press the keys during the announcement
        assertEquals(VolumeNote(before = 20, set = 20), plan.note)
        assertTrue(plan.touches)
    }

    @Test
    fun `in the assistant channel the app never writes`() {
        val plan = before(situation(channel = AnnounceChannel.ASSISTANT))
        assertEquals(Action.SKIPPED, plan.action)
        assertEquals(Why.ASSISTANT_CHANNEL, plan.why)
        assertNull(plan.note)
        assertFalse(plan.touches)
    }

    @Test
    fun `an announcement that is not governed by the media volume is left alone`() {
        val plan = before(situation(governsMusic = false))
        assertEquals(Action.SKIPPED, plan.action)
        assertEquals(Why.NOT_MUSIC_STREAM, plan.why)
        assertFalse(plan.touches)
    }

    @Test
    fun `nothing is written while the aid is not the media route`() {
        val plan = before(situation(routeIsAid = false))
        assertEquals(Action.SKIPPED, plan.action)
        assertEquals(Why.ROUTE_NOT_AID, plan.why)
        assertNull(plan.note)
        assertFalse(plan.touches)
    }

    @Test
    fun `a fixed volume is left alone`() {
        assertEquals(Why.VOLUME_FIXED, before(situation(fixed = true)).why)
    }

    @Test
    fun `a muted stream stays muted`() {
        val plan = before(situation(muted = true, index = 0))
        assertEquals(Action.SKIPPED, plan.action)
        assertEquals(Why.MUTED, plan.why)
        assertNull(plan.note)
    }

    @Test
    fun `a stream that reports muted is left alone whatever index it shows`() {
        // a write of another index would unmute it
        val plan = before(situation(muted = true, index = 12))
        assertEquals(Action.SKIPPED, plan.action)
        assertEquals(Why.MUTED, plan.why)
        assertNull(plan.note)
        assertFalse(plan.touches)
    }

    @Test
    fun `index 0 is a muted stream whatever the mute flag says`() {
        // a set to another index would unmute it, and 0 is nothing to put back
        val plan = before(situation(muted = false, index = 0))
        assertEquals(Action.SKIPPED, plan.action)
        assertEquals(Why.MUTED, plan.why)
        assertFalse(plan.touches)
    }

    @Test
    fun `nothing is written in a call or another audio mode`() {
        assertEquals(Why.MODE_NOT_NORMAL, before(situation(modeNormal = false)).why)
    }

    @Test
    fun `other media that is playing is not lifted with the announcement`() {
        val plan = before(situation(otherMedia = true))
        assertEquals(Action.SKIPPED, plan.action)
        assertEquals(Why.OTHER_MEDIA, plan.why)
        assertNull(plan.note)
        assertFalse(plan.touches)
    }

    @Test
    fun `the silence test never writes`() {
        val plan = before(situation(silence = true))
        assertEquals(Action.SKIPPED, plan.action)
        assertEquals(Why.SILENCE_TEST, plan.why)
        // and with the switch off it is the phone's value like every announcement
        assertEquals(Action.KEPT, before(situation(silence = true, own = null)).action)
    }

    @Test
    fun `the first reason in the list is the one that is named`() {
        val all = situation(
            channel = AnnounceChannel.ASSISTANT, silence = true, governsMusic = false, routeIsAid = false,
            fixed = true, muted = true, index = 0, modeNormal = false, otherMedia = true,
        )
        assertEquals(Why.SILENCE_TEST, before(all).why)
        assertEquals(Why.ASSISTANT_CHANNEL, before(all.copy(silence = false)).why)
        assertEquals(Why.NOT_MUSIC_STREAM, before(all.copy(silence = false, channel = AnnounceChannel.MEDIA)).why)
        val media = all.copy(silence = false, channel = AnnounceChannel.MEDIA, governsMusic = true)
        assertEquals(Why.ROUTE_NOT_AID, before(media).why)
        assertEquals(Why.VOLUME_FIXED, before(media.copy(routeIsAid = true)).why)
        assertEquals(Why.MUTED, before(media.copy(routeIsAid = true, fixed = false)).why)
        assertEquals(Why.MODE_NOT_NORMAL, before(media.copy(routeIsAid = true, fixed = false, muted = false, index = 12)).why)
        assertEquals(
            Why.OTHER_MEDIA,
            before(media.copy(routeIsAid = true, fixed = false, muted = false, index = 12, modeNormal = true)).why,
        )
    }

    @Test
    fun `a note that this announcement does not use stays, and the line says so`() {
        val note = VolumeNote(before = 12, set = 20)
        assertEquals(NoteUse.PENDING, before(situation(own = null, note = note)).noteUse)
        assertEquals(NoteUse.PENDING, before(situation(otherMedia = true, note = note)).noteUse)
        assertNull(before(situation(otherMedia = true, note = note)).note)
    }

    @Test
    fun `an announcement that leaves the volume alone still looks at a note that was left over`() {
        // The restore of an earlier announcement was put off: 20 stands for the aid, the
        // owner had 12. Whatever keeps this announcement from setting a step of its own,
        // his value is put back before it plays.
        val note = VolumeNote(before = 12, set = 20)
        // the switch is off by now
        assertEquals(Leftover.PutBack(12), before(situation(own = null, index = 20, note = note)).leftover)
        // his podcast plays, at the announcement's step: down to his own value all the same
        assertEquals(Leftover.PutBack(12), before(situation(otherMedia = true, index = 20, note = note)).leftover)
        assertEquals(Leftover.PutBack(12), before(situation(channel = AnnounceChannel.ASSISTANT, index = 20, note = note)).leftover)
        assertEquals(Leftover.PutBack(12), before(situation(silence = true, index = 20, note = note)).leftover)
        assertEquals(Leftover.PutBack(12), before(situation(governsMusic = false, index = 20, note = note)).leftover)
        // somebody set another value since: theirs stands, the note goes
        assertEquals(Leftover.Drop, before(situation(own = null, index = 15, note = note)).leftover)
    }

    @Test
    fun `the look at a leftover note before an announcement keeps the rules of every look`() {
        val note = VolumeNote(before = 12, set = 20)
        // another device is the route: its 20 is not compared with anything
        assertEquals(Leftover.Wait, before(situation(own = null, routeIsAid = false, index = 20, note = note)).leftover)
        assertEquals(Leftover.Wait, before(situation(routeIsAid = false, index = 20, note = note)).leftover)
        // muted: a write would unmute
        assertEquals(Leftover.WaitMuted, before(situation(own = null, muted = true, index = 0, note = note)).leftover)
        assertEquals(Leftover.WaitMuted, before(situation(muted = true, index = 20, note = note)).leftover)
        // no note, nothing to look at
        assertEquals(Leftover.None, before(situation(own = null)).leftover)
    }

    @Test
    fun `an announcement that sets its own step uses the note itself and leaves nothing to a look`() {
        val plan = before(situation(index = 20, note = VolumeNote(before = 12, set = 20)))
        assertEquals(NoteUse.USED, plan.noteUse)
        assertEquals(Leftover.None, plan.leftover)
    }

    // ---- the leftover note at the next announcement

    @Test
    fun `a leftover note is used if the value is still the one the app set`() {
        // the restore of the announcement before was lost: 20 stands, the owner had 12
        val plan = before(situation(index = 20, note = VolumeNote(before = 12, set = 20)))
        assertEquals(Action.ALREADY, plan.action)
        assertEquals(NoteUse.USED, plan.noteUse)
        assertEquals(12, plan.trueBefore)
        assertEquals(VolumeNote(before = 12, set = 20), plan.note)
    }

    @Test
    fun `a used note keeps the owner's value also when the step has changed since`() {
        val plan = before(situation(own = StoredStep(23, 25), index = 20, note = VolumeNote(before = 12, set = 20)))
        assertEquals(Action.SET, plan.action)
        assertEquals(23, plan.wanted)
        assertEquals(12, plan.trueBefore)
        assertEquals(VolumeNote(before = 12, set = 23), plan.note)
        assertEquals(NoteUse.USED, plan.noteUse)
    }

    @Test
    fun `a leftover note is dropped if somebody changed the value since`() {
        val plan = before(situation(index = 14, note = VolumeNote(before = 12, set = 20)))
        assertEquals(Action.SET, plan.action)
        assertEquals(NoteUse.DROPPED, plan.noteUse)
        assertEquals(14, plan.trueBefore)
        assertEquals(VolumeNote(before = 14, set = 20), plan.note)
    }

    @Test
    fun `a used note never brings back an index outside the scale`() {
        // noted against a scale of 30
        assertEquals(25, before(situation(index = 20, note = VolumeNote(before = 28, set = 20))).trueBefore)
    }

    @Test
    fun `a note that would put back 0 is dropped, because 0 is never written`() {
        val plan = before(situation(index = 20, note = VolumeNote(before = 0, set = 20)))
        assertEquals(NoteUse.DROPPED, plan.noteUse)
        assertEquals(20, plan.trueBefore)
    }

    // ---- the step against another max

    @Test
    fun `a step chosen against another max is rescaled and reported`() {
        val plan = before(situation(own = StoredStep(20, 25), index = 5, max = 15))
        assertEquals(Action.SET, plan.action)
        assertEquals(12, plan.wanted)
        assertEquals(StoredStep(12, 15), plan.rescaled)
        assertEquals(VolumeNote(before = 5, set = 12), plan.note)
    }

    @Test
    fun `a rescaled step is reported also when nothing is written`() {
        // so that the store is put right at the first chance
        assertEquals(StoredStep(12, 15), before(situation(own = StoredStep(20, 25), max = 15, otherMedia = true)).rescaled)
    }

    @Test
    fun `step 0 and a step beyond max are bent into the scale and never sent as they are`() {
        val zero = before(situation(own = StoredStep(0, 25)))
        assertEquals(1, zero.wanted)
        assertEquals(VolumeNote(before = 12, set = 1), zero.note)
        assertEquals(StoredStep(1, 25), zero.rescaled)

        val beyond = before(situation(own = StoredStep(40, 25)))
        assertEquals(25, beyond.wanted)
        assertEquals(StoredStep(25, 25), beyond.rescaled)
    }

    // ---- after the write

    private fun written(
        situation: Situation,
        got: Int,
        doNotDisturb: Boolean = false,
        userRestricted: Boolean = false,
    ) = AnnounceVolumeRules.afterWrite(before(situation), situation.index, got, situation.note, doNotDisturb, userRestricted)

    @Test
    fun `a write that reads back as wanted is accepted and restored later`() {
        val written = written(situation(), got = 20)
        assertEquals(Action.SET, written.action)
        assertNull(written.why)
        assertEquals(20, written.standing)
        assertEquals(VolumeNote(before = 12, set = 20), written.note)
    }

    @Test
    fun `a refused write clears the note and is classified by what the app can see`() {
        val unknown = written(situation(), got = 12)
        assertEquals(Action.REFUSED, unknown.action)
        assertEquals(Why.UNKNOWN, unknown.why)
        assertNull(unknown.note)
        assertNull(unknown.standing)

        assertEquals(Why.DND, written(situation(), got = 12, doNotDisturb = true).why)
        assertEquals(Why.USER_RESTRICTION, written(situation(), got = 12, userRestricted = true).why)
        // Do Not Disturb is asked first
        assertEquals(Why.DND, written(situation(), got = 12, doNotDisturb = true, userRestricted = true).why)
    }

    @Test
    fun `a refused write clears a note that was out of date`() {
        // 20 was the app's once; the owner has set 14 since. That note must not come back
        // and put 12 over a 20 he may choose later.
        val written = written(situation(index = 14, note = VolumeNote(before = 12, set = 20)), got = 14)
        assertEquals(Action.REFUSED, written.action)
        assertNull(written.note)
        assertNull(written.standing)
    }

    @Test
    fun `a refused write leaves a note that was in use as it was`() {
        // 20 still stands from the announcement before; the new step 23 was not taken
        val old = VolumeNote(before = 12, set = 20)
        val written = written(situation(own = StoredStep(23, 25), index = 20, note = old), got = 20)
        assertEquals(Action.REFUSED, written.action)
        assertEquals(old, written.note)
        assertNull(written.standing)
    }

    @Test
    fun `a write that ends at a third value is noted with that value and put back later`() {
        // capped by Android, or changed by somebody in the same moment
        val written = written(situation(), got = 17)
        assertEquals(Action.REFUSED, written.action)
        assertEquals(Why.OTHER, written.why)
        assertEquals(VolumeNote(before = 12, set = 17), written.note)
        assertEquals(17, written.standing)
    }

    @Test
    fun `a write that ends at a third value keeps the owner's value from a note that was in use`() {
        // 20 still stands from an announcement whose restore was lost; the owner had 12.
        // After a process death the look has to put back his 12, not the app's old 20.
        val written = written(situation(own = StoredStep(23, 25), index = 20, note = VolumeNote(before = 12, set = 20)), got = 21)
        assertEquals(Action.REFUSED, written.action)
        assertEquals(Why.OTHER, written.why)
        assertEquals(VolumeNote(before = 12, set = 21), written.note)
        assertEquals(21, written.standing)
    }

    @Test
    fun `a stream that reads 0 after the write was muted in that moment, and 0 is never noted`() {
        // What stands under the mute cannot be read. The note says what the write would
        // have set, for the look after the unmute; the announcement does not look after it.
        val written = written(situation(), got = 0)
        assertEquals(Action.REFUSED, written.action)
        assertEquals(Why.MUTED, written.why)
        assertEquals(VolumeNote(before = 12, set = 20), written.note)
        assertNull(written.standing)
        // with a note in use the write was most likely dropped: the old note still describes what stands
        val old = VolumeNote(before = 12, set = 20)
        assertEquals(old, written(situation(own = StoredStep(23, 25), index = 20, note = old), got = 0).note)
    }

    @Test
    fun `a value that was already the step stands without a write`() {
        val plan = before(situation(index = 20))
        assertEquals(20, AnnounceVolumeRules.standing(plan))
        assertNull(AnnounceVolumeRules.standing(before(situation(own = null))))
        assertNull(AnnounceVolumeRules.standing(before(situation(otherMedia = true))))
    }

    @Test
    fun `a step that is still to be written does not stand yet`() {
        // only the read after the write says whether it stands
        assertNull(AnnounceVolumeRules.standing(before(situation())))
    }

    @Test
    fun `a route that is lost between the note and the write leaves nothing written and no new note`() {
        val plan = before(situation())
        val lost = AnnounceVolumeRules.routeLostBeforeWrite(plan, oldNote = null)
        assertEquals(Action.SKIPPED, lost.action)
        assertEquals(Why.ROUTE_NOT_AID, lost.why)
        assertNull(lost.note)
        assertNull(lost.standing)
    }

    @Test
    fun `a route lost before the write keeps a note that was in use and not one that was out of date`() {
        val old = VolumeNote(before = 12, set = 20)
        // in use: 20 still stands for the aid, and 12 is still to be put back
        val used = before(situation(own = StoredStep(23, 25), index = 20, note = old))
        assertEquals(old, AnnounceVolumeRules.routeLostBeforeWrite(used, old).note)
        // out of date: the owner has set 15 since. It must not be in storage again.
        val dropped = before(situation(index = 15, note = old))
        assertEquals(NoteUse.DROPPED, dropped.noteUse)
        assertNull(AnnounceVolumeRules.routeLostBeforeWrite(dropped, old).note)
    }

    // ---- while it plays

    @Test
    fun `the note follows a value that the keys changed during the announcement`() {
        // the owner pressed volume up: 21 stands, and that is what a later look has to recognise
        val note = VolumeNote(before = 12, set = 20)
        assertEquals(VolumeNote(before = 12, set = 21), AnnounceVolumeRules.watched(note, routeIsAid = true, muted = false, index = 21))
        // nothing changed
        assertEquals(note, AnnounceVolumeRules.watched(note, routeIsAid = true, muted = false, index = 20))
    }

    @Test
    fun `the note does not follow a value of another device or of a muted stream`() {
        val note = VolumeNote(before = 12, set = 20)
        assertEquals(note, AnnounceVolumeRules.watched(note, routeIsAid = false, muted = false, index = 7))
        assertEquals(note, AnnounceVolumeRules.watched(note, routeIsAid = true, muted = true, index = 21))
        assertEquals(note, AnnounceVolumeRules.watched(note, routeIsAid = true, muted = false, index = 0))
        assertNull(AnnounceVolumeRules.watched(null, routeIsAid = true, muted = false, index = 21))
    }

    @Test
    fun `a note that is marked as restored is nobody's to follow`() {
        // it names the value from before the restore, on purpose
        val marked = VolumeNote(before = 12, set = 20, restored = true)
        assertEquals(marked, AnnounceVolumeRules.watched(marked, routeIsAid = true, muted = false, index = 12))
    }

    // ---- the restore

    private fun restore(
        standing: Int = 20,
        trueBefore: Int = 12,
        routeIsAid: Boolean = true,
        muted: Boolean = false,
        now: Int = 20,
        seen: Int = standing,
        result: String? = "ok",
    ) = AnnounceVolumeRules.restore(standing, trueBefore, routeIsAid, muted, now, seen, result)

    @Test
    fun `after the announcement the phone's value is put back`() {
        val plan = restore()
        assertEquals(12, plan.write)
        assertNull(plan.adopt)
        val end = AnnounceVolumeRules.afterRestore(plan, trueBefore = 12, back = 12)
        assertEquals(Restore.RESTORED, end.result)
    }

    @Test
    fun `a restore that reads back keeps its note, marked, until a later look has confirmed it`() {
        // Android took the value. Whether the Bluetooth stack recorded it for the aid is
        // not known yet: it records only for the aid that is its active media device, and
        // an aid that is switched off stops being that a second before Android's route
        // shows it. So the note stays, with the value that stood, and says "restored".
        val end = AnnounceVolumeRules.afterRestore(restore(), trueBefore = 12, back = 12)
        assertEquals(VolumeNote(before = 12, set = 20, restored = true), end.note)
        // after the keys the value that stood is the owner's 22: that is what would come back
        val adopted = AnnounceVolumeRules.afterRestore(restore(now = 22), trueBefore = 12, back = 12)
        assertEquals(VolumeNote(before = 12, set = 22, restored = true), adopted.note)
    }

    @Test
    fun `if the aid is no longer the route nothing is written and the note stays`() {
        val plan = restore(routeIsAid = false, now = 7)
        assertEquals(Restore.DEFERRED_ROUTE, plan.result)
        assertNull(plan.write)
        assertNull(plan.adopt)
        assertEquals(VolumeNote(before = 12, set = 20), plan.note)
    }

    @Test
    fun `the route is asked before the value, so a foreign value is never adopted`() {
        // 7 is the speaker's index; it must not become the step for the aid
        val plan = restore(routeIsAid = false, muted = true, now = 7)
        assertEquals(Restore.DEFERRED_ROUTE, plan.result)
        assertNull(plan.adopt)
        assertEquals(VolumeNote(before = 12, set = 20), plan.note)
    }

    @Test
    fun `an output that is going away is a lost route, although it still reads as the aid`() {
        // The aid was switched off in the middle of the announcement. Android keeps it as
        // the route for another second and would take the write, but the Bluetooth stack
        // has let go of it already and would hand the raised step back at the next
        // connection, with no note left. So: nothing written, and the note stays for the
        // look after that connection.
        val plan = restore(result = "output_going_away")
        assertEquals(Restore.DEFERRED_GOING_AWAY, plan.result)
        assertNull(plan.write)
        assertNull(plan.adopt)
        assertEquals(VolumeNote(before = 12, set = 20), plan.note)
        // and when the aid is back with the step the Bluetooth stack remembered
        assertEquals(Leftover.PutBack(12), leftover(plan.note, index = 20))
    }

    @Test
    fun `a deferred restore with nothing to put back leaves no note`() {
        assertNull(restore(standing = 20, trueBefore = 20, routeIsAid = false, now = 7).note)
        assertNull(restore(standing = 20, trueBefore = 20, result = "output_going_away").note)
    }

    @Test
    fun `keys pressed before the route was lost are taken over, and the note names what stands`() {
        // volume up once during the announcement, seen while the aid was the route; then
        // a session took the route
        val lost = restore(routeIsAid = false, now = 7, seen = 21)
        assertEquals(Restore.DEFERRED_ROUTE, lost.result)
        assertNull(lost.write)
        assertEquals(21, lost.adopt)
        assertEquals(VolumeNote(before = 12, set = 21), lost.note)
        // the route is back at 21: the note recognises its value
        assertEquals(Leftover.PutBack(12), leftover(lost.note, index = 21))

        // going away: the value can still be read, and it is the aid's
        val going = restore(now = 22, seen = 21, result = "output_going_away")
        assertEquals(22, going.adopt)
        assertEquals(VolumeNote(before = 12, set = 22), going.note)
        // going away and muted in the same moment: 0 says nothing, the value last seen stands
        val goingMuted = restore(muted = true, now = 0, seen = 21, result = "output_going_away")
        assertEquals(Restore.DEFERRED_GOING_AWAY, goingMuted.result)
        assertEquals(21, goingMuted.adopt)
        assertEquals(VolumeNote(before = 12, set = 21), goingMuted.note)
    }

    @Test
    fun `keys pressed before the stream was muted are in the note that waits for the unmute`() {
        val plan = restore(muted = true, now = 0, seen = 22)
        assertEquals(Restore.LEFT_MUTED, plan.result)
        assertNull(plan.write)
        assertEquals(VolumeNote(before = 12, set = 22), plan.note)
    }

    @Test
    fun `a stream that was muted during the announcement is left muted, and the note waits for the unmute`() {
        // Under the mute the app's step still stands, and it comes back with the unmute.
        for (plan in listOf(restore(muted = true, now = 0), restore(muted = false, now = 0), restore(muted = true, now = 20))) {
            assertEquals(Restore.LEFT_MUTED, plan.result)
            assertNull(plan.write)
            assertNull(plan.adopt)
            assertEquals(VolumeNote(before = 12, set = 20), plan.note)
        }
        // muted: nothing is compared and nothing written
        assertEquals(Leftover.WaitMuted, leftover(VolumeNote(12, 20), muted = true, index = 0))
        // unmuted, and the step is back: the owner's value is put back
        assertEquals(Leftover.PutBack(12), leftover(VolumeNote(12, 20), index = 20))
    }

    @Test
    fun `a value changed during the announcement becomes the new step, and the phone's value is still put back`() {
        // the owner pressed volume up twice while it spoke
        val plan = restore(now = 22)
        assertEquals(22, plan.adopt)
        assertEquals(12, plan.write)
        val end = AnnounceVolumeRules.afterRestore(plan, trueBefore = 12, back = 12)
        assertEquals(Restore.ADOPTED, end.result)
    }

    @Test
    fun `keys pressed while the phone's value was the step change the step and not the phone's value`() {
        val plan = restore(standing = 20, trueBefore = 20, now = 22)
        assertEquals(Restore.ADOPTED, plan.result)
        assertEquals(22, plan.adopt)
        assertEquals(20, plan.write)
    }

    @Test
    fun `keys that end at the phone's own value are adopted without a write`() {
        val plan = restore(now = 12)
        assertEquals(Restore.ADOPTED, plan.result)
        assertEquals(12, plan.adopt)
        assertNull(plan.write)
        assertNull(plan.note)
    }

    @Test
    fun `when the phone's value is the step there is nothing to restore`() {
        val plan = restore(standing = 20, trueBefore = 20, now = 20)
        assertEquals(Restore.NOTHING_TO_RESTORE, plan.result)
        assertNull(plan.write)
        assertNull(plan.adopt)
        assertNull(plan.note)
    }

    @Test
    fun `a restoring write that is not taken keeps a note of what stands now`() {
        val plan = restore()
        val end = AnnounceVolumeRules.afterRestore(plan, trueBefore = 12, back = 20)
        assertEquals(Restore.RESTORE_REFUSED, end.result)
        assertEquals(VolumeNote(before = 12, set = 20), end.note)

        // after the keys: what stands is the owner's 22, and that is what a later look compares with
        val adopted = restore(now = 22)
        val refused = AnnounceVolumeRules.afterRestore(adopted, trueBefore = 12, back = 22)
        assertEquals(Restore.RESTORE_REFUSED, refused.result)
        assertEquals(VolumeNote(before = 12, set = 22), refused.note)
        // the step is adopted all the same
        assertEquals(22, adopted.adopt)
    }

    @Test
    fun `a restoring write into a stream that was muted in that moment never notes 0`() {
        val end = AnnounceVolumeRules.afterRestore(restore(), trueBefore = 12, back = 0)
        assertEquals(Restore.RESTORE_REFUSED, end.result)
        assertEquals(VolumeNote(before = 12, set = 20), end.note)
    }

    @Test
    fun `a route that is lost between the read and the restoring write defers, and the note names what stands`() {
        // the keys were seen only in this read
        val plan = restore(now = 22)
        val end = AnnounceVolumeRules.restoreRouteLost(plan, trueBefore = 12)
        assertEquals(Restore.DEFERRED_ROUTE, end.result)
        assertEquals(VolumeNote(before = 12, set = 22), end.note)
    }

    @Test
    fun `the wait before the restore is only for an announcement that was spoken to its end`() {
        assertEquals(500L, AnnounceVolumeRules.RESTORE_WAIT_MS)
        // the tail of the last word is still on its way to the aid
        assertEquals(500L, AnnounceVolumeRules.restoreWaitMs("ok"))
        // cut off: nothing is on its way, and whatever cut it is about to take the route
        for (cut in listOf("output_lost", "output_going_away", "session_busy", "phone_call", "superseded", "timeout", "engine_error", null)) {
            assertEquals(0L, AnnounceVolumeRules.restoreWaitMs(cut), cut)
        }
    }

    // ---- a note left over, outside an announcement

    private fun leftover(
        note: VolumeNote?,
        routeIsAid: Boolean = true,
        muted: Boolean = false,
        index: Int,
        max: Int = 25,
        settled: Boolean = true,
    ) = AnnounceVolumeRules.leftover(note, routeIsAid, muted, index, max, settled)

    @Test
    fun `without a note there is nothing to resolve`() {
        assertEquals(Leftover.None, leftover(null, index = 20))
    }

    @Test
    fun `a leftover note waits while another device is the route`() {
        // the index read then is that device's: no compare, no write
        assertEquals(Leftover.Wait, leftover(VolumeNote(12, 20), routeIsAid = false, index = 20))
    }

    @Test
    fun `a leftover note puts the owner's value back if the app's value still stands`() {
        assertEquals(Leftover.PutBack(12), leftover(VolumeNote(12, 20), index = 20))
    }

    @Test
    fun `a leftover note is dropped if the value is another one by now`() {
        assertEquals(Leftover.Drop, leftover(VolumeNote(12, 20), index = 15))
    }

    @Test
    fun `a leftover note waits while the stream is muted, whatever index it shows`() {
        // muted reads 0, and the step under the mute comes back with the unmute
        assertEquals(Leftover.WaitMuted, leftover(VolumeNote(12, 20), index = 0))
        assertEquals(Leftover.WaitMuted, leftover(VolumeNote(12, 20), muted = true, index = 0))
        // a write would unmute it
        assertEquals(Leftover.WaitMuted, leftover(VolumeNote(12, 20), muted = true, index = 20))
    }

    @Test
    fun `a note that names 0 as the value the app set is broken and never leads to a write`() {
        // a muted stream reads 0: this note would take it for the app's value and unmute it
        assertEquals(Leftover.WaitMuted, leftover(VolumeNote(12, 0), index = 0))
        assertEquals(Leftover.Drop, leftover(VolumeNote(12, 0), index = 5))
    }

    @Test
    fun `a leftover note with nothing to put back is dropped`() {
        assertEquals(Leftover.Drop, leftover(VolumeNote(20, 20), index = 20))
        // 0 is never written
        assertEquals(Leftover.Drop, leftover(VolumeNote(0, 20), index = 20))
    }

    @Test
    fun `a leftover note from a longer scale puts back the loudest step there is`() {
        assertEquals(Leftover.PutBack(15), leftover(VolumeNote(22, 10), index = 10, max = 15))
    }

    // ---- a restore that waits to be confirmed

    private val restored = VolumeNote(before = 12, set = 20, restored = true)

    @Test
    fun `a restore is confirmed when the aid is still the route some time later`() {
        assertEquals(2_000L, AnnounceVolumeRules.CONFIRM_AFTER_MS)
        assertEquals(Leftover.Confirmed, leftover(restored, index = 12))
    }

    @Test
    fun `a restore is not confirmed before that time has passed`() {
        // the aid may be on its way out, and only its absence a moment later tells
        assertEquals(Leftover.TooEarly, leftover(restored, index = 12, settled = false))
        assertEquals(Leftover.TooEarly, before(situation(own = null, note = restored, noteSettled = false)).leftover)
        assertEquals(Leftover.Confirmed, before(situation(own = null, note = restored)).leftover)
    }

    @Test
    fun `a restored note waits while the aid is away and acts when it comes back with the old step`() {
        // the aid went within the moment of the restore: the Bluetooth stack never heard of the 12
        assertEquals(Leftover.Wait, leftover(restored, routeIsAid = false, index = 9))
        // back, with the 20 the Bluetooth stack remembered: the owner's value once more
        assertEquals(Leftover.PutBack(12), leftover(restored, index = 20))
        // also right after the connection, before the time to confirm has passed
        assertEquals(Leftover.PutBack(12), leftover(restored, index = 20, settled = false))
        // back with the owner's value: it had been recorded after all
        assertEquals(Leftover.Confirmed, leftover(restored, index = 12))
        // back with something else: somebody set it, and theirs stands
        assertEquals(Leftover.Drop, leftover(restored, index = 16))
    }

    @Test
    fun `a put-back by a look is marked and confirmed like a restore after an announcement`() {
        val note = VolumeNote(before = 12, set = 20)
        assertEquals(restored, AnnounceVolumeRules.afterPutBack(note, wrote = 12, back = 12))
        // not taken: the note stays as it was, for the next look
        assertEquals(note, AnnounceVolumeRules.afterPutBack(note, wrote = 12, back = 20))
    }

    @Test
    fun `the next announcement takes a restored note like any other`() {
        // the value is the owner's: the note is out of date, and his value is the one to put back
        val settledPlan = before(situation(index = 12, note = restored))
        assertEquals(NoteUse.DROPPED, settledPlan.noteUse)
        assertEquals(12, settledPlan.trueBefore)
        assertEquals(VolumeNote(before = 12, set = 20), settledPlan.note)
        // the old step is back (the aid reconnected): the note's value is the one to put back
        val raised = before(situation(own = StoredStep(23, 25), index = 20, note = restored))
        assertEquals(NoteUse.USED, raised.noteUse)
        assertEquals(12, raised.trueBefore)
        assertEquals(VolumeNote(before = 12, set = 23), raised.note)
    }

    @Test
    fun `the words a look journals`() {
        assertEquals("none", Leftover.None.word)
        assertEquals("waiting_route", Leftover.Wait.word)
        assertEquals("waiting_unmute", Leftover.WaitMuted.word)
        assertEquals("waiting_check", Leftover.TooEarly.word)
        assertEquals("dropped", Leftover.Drop.word)
        assertEquals("confirmed", Leftover.Confirmed.word)
        assertEquals("restored", Leftover.PutBack(12).word)
    }

    // ---- from one end to the other

    @Test
    fun `an ordinary announcement, from the note to the cleared note`() {
        val situation = situation()
        val plan = before(situation)
        // the note is in storage before the write
        assertEquals(VolumeNote(before = 12, set = 20), plan.note)
        val written = AnnounceVolumeRules.afterWrite(plan, situation.index, got = 20, oldNote = null, doNotDisturb = false, userRestricted = false)
        val standing = written.standing!!
        val trueBefore = plan.trueBefore!!
        val restore = AnnounceVolumeRules.restore(standing, trueBefore, routeIsAid = true, muted = false, now = 20)
        assertEquals(12, restore.write)
        val end = AnnounceVolumeRules.afterRestore(restore, trueBefore, back = 12)
        assertEquals(Restore.RESTORED, end.result)
        // two seconds later the aid is still the route: done
        assertEquals(Leftover.Confirmed, leftover(end.note, index = 12))
    }

    @Test
    fun `an aid that is switched off in the moment of the restore gets its value back at the next connection`() {
        // The restore read back, but the Bluetooth stack had let go of the aid: its memory
        // for the aid still holds the announcement's 20.
        val end = AnnounceVolumeRules.afterRestore(restore(), trueBefore = 12, back = 12)
        // two seconds later the aid is gone: the note waits
        assertEquals(Leftover.Wait, leftover(end.note, routeIsAid = false, index = 9))
        // the aid connects, the Bluetooth stack hands the 20 back, the look puts the 12 back
        val back = leftover(end.note, index = 20)
        assertEquals(Leftover.PutBack(12), back)
        // now the aid is the active device, and the look two seconds later finds it still there
        val again = AnnounceVolumeRules.afterPutBack(end.note!!, wrote = 12, back = 12)
        assertEquals(Leftover.Confirmed, leftover(again, index = 12))
    }

    @Test
    fun `a process that dies during an announcement leaves a note that the next start puts right`() {
        val note = before(situation()).note
        // the new process finds 20 still standing for the aid
        assertEquals(Leftover.PutBack(12), leftover(note, index = 20))
    }

    @Test
    fun `a hearing aid that goes away during an announcement gets its value back when it returns`() {
        val plan = before(situation())
        val restore = AnnounceVolumeRules.restore(20, plan.trueBefore!!, routeIsAid = false, muted = false, now = 9)
        assertEquals(Restore.DEFERRED_ROUTE, restore.result)
        assertEquals(plan.note, restore.note)
        // while the speaker is the route, its 9 is not compared with anything
        assertEquals(Leftover.Wait, leftover(plan.note, routeIsAid = false, index = 9))
        // the aid is back, and the Bluetooth stack has handed Android the 20 it remembered
        assertEquals(Leftover.PutBack(12), leftover(plan.note, index = 20))
        // or the owner has set another value in the meantime: his stands
        assertEquals(Leftover.Drop, leftover(plan.note, index = 16))
    }

    // ---- the slider

    @Test
    fun `with the switch on the slider holds the stored step and works without the aid`() {
        assertTrue(AnnounceVolumeRules.sliderEnabled(AnnounceChannel.MEDIA, ownLevel = true, mediaToAid = false, muted = true, fixed = false))
    }

    @Test
    fun `with the switch off the slider is the phone's media volume for the aid and needs the aid`() {
        fun enabled(mediaToAid: Boolean = true, muted: Boolean = false, fixed: Boolean = false) =
            AnnounceVolumeRules.sliderEnabled(AnnounceChannel.MEDIA, ownLevel = false, mediaToAid, muted, fixed)
        assertTrue(enabled())
        assertFalse(enabled(mediaToAid = false))
        assertFalse(enabled(muted = true))
        assertFalse(enabled(fixed = true))
    }

    @Test
    fun `in the assistant channel there is nothing for the slider to set`() {
        assertFalse(AnnounceVolumeRules.sliderEnabled(AnnounceChannel.ASSISTANT, ownLevel = true, mediaToAid = true, muted = false, fixed = false))
        assertFalse(AnnounceVolumeRules.sliderEnabled(AnnounceChannel.ASSISTANT, ownLevel = false, mediaToAid = true, muted = false, fixed = false))
    }

    // ---- the slider's direct write of the phone's media volume

    private fun sliderWrite(routeIsAid: Boolean = true, fixed: Boolean = false, muted: Boolean = false, before: Int = 12, wanted: Int = 15) =
        AnnounceVolumeRules.sliderWrite(routeIsAid, fixed, muted, before, wanted)

    @Test
    fun `the slider writes the phone's media volume while the aid is the route`() {
        val plan = sliderWrite()
        assertNull(plan.why)
        assertTrue(plan.write)
    }

    @Test
    fun `the slider writes nothing without the route, into a fixed volume or into a muted stream`() {
        assertEquals(Why.ROUTE_NOT_AID, sliderWrite(routeIsAid = false).why)
        assertEquals(Why.VOLUME_FIXED, sliderWrite(fixed = true).why)
        assertEquals(Why.MUTED, sliderWrite(muted = true).why)
        // index 0 is a muted stream
        assertEquals(Why.MUTED, sliderWrite(before = 0).why)
        for (plan in listOf(sliderWrite(routeIsAid = false), sliderWrite(fixed = true), sliderWrite(muted = true), sliderWrite(before = 0))) {
            assertFalse(plan.write)
        }
        // muted, although the slider stands where the hidden index is: still nothing to write, and a reason
        assertEquals(Why.MUTED, sliderWrite(muted = true, before = 15, wanted = 15).why)
        // the route is the first question: without it the value is another device's
        assertEquals(Why.ROUTE_NOT_AID, sliderWrite(routeIsAid = false, fixed = true, muted = true, before = 0).why)
    }

    @Test
    fun `the slider does not write a value that stands already`() {
        val plan = sliderWrite(before = 15, wanted = 15)
        assertNull(plan.why)
        assertFalse(plan.write)
        assertEquals(Action.ALREADY, AnnounceVolumeRules.afterSliderWrite(before = 15, wanted = 15, got = 15, doNotDisturb = false, userRestricted = false).action)
    }

    @Test
    fun `the slider's write is read back and classed like the write for an announcement`() {
        fun end(got: Int, doNotDisturb: Boolean = false, userRestricted: Boolean = false) =
            AnnounceVolumeRules.afterSliderWrite(before = 12, wanted = 15, got = got, doNotDisturb = doNotDisturb, userRestricted = userRestricted)
        assertEquals(Action.SET, end(15).action)
        assertNull(end(15).why)
        assertEquals(Action.REFUSED, end(12).action)
        assertEquals(Why.UNKNOWN, end(12).why)
        assertEquals(Why.DND, end(12, doNotDisturb = true).why)
        assertEquals(Why.USER_RESTRICTION, end(12, userRestricted = true).why)
        assertEquals(Why.DND, end(12, doNotDisturb = true, userRestricted = true).why)
        // a third value
        assertEquals(Action.REFUSED, end(14).action)
        assertEquals(Why.OTHER, end(14).why)
        // Do Not Disturb does not explain a third value
        assertEquals(Why.OTHER, end(14, doNotDisturb = true).why)
        // muted in that moment
        assertEquals(Why.MUTED, end(0).why)
    }

    @Test
    fun `the words of the journal`() {
        assertEquals(listOf("kept", "skipped", "already", "set", "refused"), Action.entries.map { it.word })
        assertEquals(
            listOf("restored", "adopted", "left_muted", "deferred_route", "deferred_going_away", "nothing_to_restore", "restore_refused"),
            Restore.entries.map { it.word },
        )
        assertEquals(listOf("none", "used", "dropped", "pending"), NoteUse.entries.map { it.word })
        assertEquals("dnd", Why.DND.word)
        assertEquals("user_restriction", Why.USER_RESTRICTION.word)
        assertEquals("unknown", Why.UNKNOWN.word)
        assertEquals("other_media", Why.OTHER_MEDIA.word)
    }
}
