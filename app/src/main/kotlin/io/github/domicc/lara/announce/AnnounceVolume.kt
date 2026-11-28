package io.github.domicc.lara.announce

import android.app.NotificationManager
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.UserManager
import io.github.domicc.lara.audio.HearingAidStore
import io.github.domicc.lara.audio.modeWord
import io.github.domicc.lara.core.announce.AnnounceChannel
import io.github.domicc.lara.core.announce.AnnounceTexts
import io.github.domicc.lara.core.announce.AnnounceVolumeRules
import io.github.domicc.lara.core.announce.AnnounceVolumeRules.Action
import io.github.domicc.lara.core.announce.AnnounceVolumeRules.Leftover
import io.github.domicc.lara.core.announce.AnnounceVolumeRules.Restore
import io.github.domicc.lara.core.announce.AnnounceVolumeRules.Why
import io.github.domicc.lara.core.announce.StoredStep
import io.github.domicc.lara.core.announce.VolumeNote
import io.github.domicc.lara.core.announce.VolumeLive
import io.github.domicc.lara.core.announce.VolumeReport
import io.github.domicc.lara.core.announce.VolumeSteps
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * How loud an announcement is: the thin layer between the rules
 * ([AnnounceVolumeRules]) and Android's `AudioManager`.
 *
 * What is behind it (CLAUDE.md, "How loud an announcement is"; read from the Android 17
 * source and from captures of the phone, nothing of this class has run on it):
 * - The hearing aid takes no volume command from the phone. Every volume on the phone is a
 *   digital gain in the phone's mixer, and the highest step is the loudest the phone can
 *   send.
 * - Android 17 plays assistant speech under a volume of its own, which an ordinary app
 *   cannot set. Played as media ([AnnounceChannel.MEDIA], the default), an announcement
 *   follows the media volume instead, which the owner sets with the slider and the keys.
 * - `setStreamVolume` for media needs no permission, acts on whatever output the media are
 *   routed to at the moment of the call, and is dropped without a word in several
 *   situations. So the route is asked right before every read and every write, and every
 *   write is read back.
 * - The media volume for the hearing aid is also the volume of the owner's music in it, and
 *   the Bluetooth stack remembers it for the aid, but takes a new value only while the aid
 *   is its active media device. So what the app changes for an announcement it puts back,
 *   and it keeps a note ([AnnounceVolumeStore.note], [VolumeNote]) from before the write
 *   until a look some time after the restore still finds the hearing aid there.
 * - A note that waits is looked at whenever the route may have come back to the hearing
 *   aid: see [start] for the occasions. Every look asks the route first and writes once
 *   at most.
 *
 * The app writes one stream, media, with no flags (no panel, no sound, no screen), never
 * index 0, and nothing in the assistant channel. Android's assistant volume (stream 11) is
 * only read, for the journal and the status line.
 *
 * Everything here runs on the main thread: the announcer's slice before an announcement
 * ([before]), the looks while it plays ([watch]), the restore after it ([after]), the look
 * at a note that was left over ([resolveLeftover]) and the slider. That is what keeps the
 * restore of one announcement and a look from outside apart ([caring]).
 *
 * No journal line of this class carries an address, the name of a device or a text.
 */
class AnnounceVolume(
    private val context: Context,
    private val journal: Journal,
    private val hearingAid: HearingAidStore,
    val store: AnnounceVolumeStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)

    /**
     * Played as media, with one thing the usage of an assistant brought by itself: no other
     * app may record it. Android lets a screen recorder or a screen share with device audio
     * capture sound that is played as media, through a copy of the stream that no question
     * about the output shows, so the announcer's guard would see nothing of it, and a
     * reminder speaks the title of an appointment. `ALLOW_CAPTURE_BY_NONE` shuts that out,
     * for the app's own track and, because the attributes travel to the speech engine, for
     * the speech. By the Android 17 source the flag takes no part in where the sound goes
     * or under which volume it runs; not tried on the phone (`governing` and `routeTypes`
     * in `announce_volume` would show it).
     */
    private val media = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .setAllowedCapturePolicy(AudioAttributes.ALLOW_CAPTURE_BY_NONE)
        .build()

    /**
     * Exactly what every build before the setting played with, and nothing added: this is
     * the way back to what was heard on the phone. Android never lets another app record
     * this usage.
     */
    private val assistant = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Whether Android puts sound played as [media] under the media volume on this phone. Asked once. */
    private val mediaGoverned: Boolean by lazy {
        runCatching { media.volumeControlStream == AudioManager.STREAM_MUSIC }.getOrDefault(true)
    }

    /**
     * True while an announcement plays whose volume the app has taken into its care: from
     * its note to the end of its restore. A look at a leftover note or a move of the slider
     * in that time would take the announcement's own note for a leftover one.
     */
    private var caring = false

    /** The announcement the app looks after right now, while [caring]. */
    private var current: Applied? = null

    /** When [start] ran, on the elapsed-realtime clock. */
    private var startedAt = 0L

    /**
     * When a note was last marked as restored, on the elapsed-realtime clock; 0 if this
     * process has not done that. A note marked by a process before this one is old enough.
     */
    private var markedAt = 0L

    /** Counts the samples asked for; the newest is the one that may play. */
    private var samples = 0

    /** The looks that are on their way, by what asked for them. */
    private val looks = HashMap<String, Runnable>()

    /** How many times in a row a look has put the owner's value back without one confirming it since. */
    private var putBacks = 0

    /** What became of the volume of the announcement that plays; shown when it is over ([ended]). */
    private var pendingReport: VolumeReport? = null

    private val reportState = MutableStateFlow<VolumeReport?>(null)

    /** What became of the newest announcement and of its volume, for the line under the slider. */
    val report: StateFlow<VolumeReport?> = reportState.asStateFlow()

    /** What an announcement in [channel] is played with. Chime, silence test and speech alike. */
    fun attributes(channel: AnnounceChannel): AudioAttributes = when (channel) {
        AnnounceChannel.MEDIA -> media
        AnnounceChannel.ASSISTANT -> assistant
    }

    /**
     * Call once at every process start: journals the state and, once per Android build, the
     * phone's volume curves, puts back what a process before this one could not, and keeps
     * watch for the moments in which a note that waits can be dealt with.
     *
     * A note waits while the hearing aid is not the media route (or media are muted), and
     * the route comes back in more ways than by the aid connecting: a session over the
     * voice link or a phone call takes the media route without the aid's media device
     * going away, and gives it back without it being added (read from the Android 17
     * source). So, while there is a note, the app looks again about a second after
     * - any audio device was added or removed,
     * - the phone's audio mode went back to normal (the end of a call),
     * - the device for calls and voice changed,
     * - a voice link went down (the end of a session; the report needs the permission for
     *   nearby devices and simply does not come without it),
     * - Do Not Disturb changed (it can mute media, and unmute them).
     * Besides that at the start of the app, when it comes onto the screen, when the section
     * "Ansagen" is opened, when one of its settings changes, and before every announcement.
     * Nothing tells an app that the owner unmuted media by hand; that is found at the next
     * of these looks.
     */
    fun start() {
        startedAt = SystemClock.elapsedRealtime()
        curveOncePerBuild()
        resolveLeftover("app_start")
        // null: on the main thread. Never taken back; the process lives with it.
        audio.registerAudioDeviceCallback(devices, null)
        try {
            audio.addOnModeChangedListener(context.mainExecutor) { mode ->
                if (mode == AudioManager.MODE_NORMAL) lookIfNote("mode")
            }
            audio.addOnCommunicationDeviceChangedListener(context.mainExecutor) { lookIfNote("communication_device") }
            val filter = IntentFilter().apply {
                addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            }
            // Exported: the report about the voice link comes from the Bluetooth app, not
            // from the system uid. Both actions can only be sent by the system.
            context.registerReceiver(reports, filter, Context.RECEIVER_EXPORTED)
        } catch (e: Exception) {
            journal.log("volume_watch_error", mapOf("exception" to e.javaClass.name))
        }
        // A setting that changes while a note waits (the switch off, the other channel) may
        // mean that no announcement will set a step again.
        scope.launch { store.settings.drop(1).collect { if (store.note != null) resolveLeftover("setting") } }
    }

    /** The app came onto the screen. Journals the state of the volumes the owner is about to look at. */
    fun onScreen() {
        // a start of the app that brings its screen up has just written that line
        if (store.note == null && SystemClock.elapsedRealtime() - startedAt < FIRST_REPORT_MS) return
        resolveLeftover("screen")
    }

    /** The section with the slider was opened. */
    fun onSection() = resolveLeftover("section")

    /**
     * Audio devices came or went. For the hearing aid's media device a look follows also
     * without a note: the Bluetooth stack hands Android the volume it remembers for the aid
     * at the connection, on another thread, and the line [LOOK_AFTER_CHANGE_MS] later shows
     * it. Everything else matters only while a note waits.
     */
    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val address = hearingAid.address ?: return
            val aid = addedDevices.any { onlyTheAid(listOf(it), address) }
            if (store.note == null) {
                if (!aid) return
                // Android answers the registration with the devices that are there already,
                // and the start of the app has just looked at those.
                if (SystemClock.elapsedRealtime() - startedAt < FIRST_REPORT_MS) return
            }
            lookLater(if (aid) "aid_route" else "devices", LOOK_AFTER_CHANGE_MS)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = lookIfNote("devices")
    }

    /** A voice link went down, or Do Not Disturb changed. */
    private val reports = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED ->
                    if (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
                        lookIfNote("voice_link")
                    }
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> lookIfNote("dnd")
            }
        }
    }

    /** Something happened after which the hearing aid may be the media route again: a look, if a note waits. */
    private fun lookIfNote(trigger: String) {
        if (store.note != null) lookLater(trigger, LOOK_AFTER_CHANGE_MS)
    }

    /**
     * Looks at the note [delayMs] from now ([resolveLeftover]). A later request with the
     * same [trigger] takes the place of one that is still waiting. While a note waits the
     * phone is kept awake for the look: it may be the one that puts the owner's volume back.
     */
    private fun lookLater(trigger: String, delayMs: Long) {
        looks.remove(trigger)?.let(main::removeCallbacks)
        val wakeLock = if (store.note == null) null else {
            power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lara:volume").apply { acquire(delayMs + LOOK_WAKE_MARGIN_MS) }
        }
        val look = Runnable {
            looks.remove(trigger)
            try {
                resolveLeftover(trigger)
            } finally {
                if (wakeLock?.isHeld == true) wakeLock.release()
            }
        }
        looks[trigger] = look
        main.postDelayed(look, delayMs)
    }

    /**
     * A note was marked as restored just now: the look that confirms it comes
     * [AnnounceVolumeRules.CONFIRM_AFTER_MS] later, and a moment more, because the wait and
     * [settled] go by two different clocks.
     */
    private fun confirmLater() {
        markedAt = SystemClock.elapsedRealtime()
        lookLater("restore_check", AnnounceVolumeRules.CONFIRM_AFTER_MS + CONFIRM_MARGIN_MS)
    }

    /** Whether a note that is marked as restored is old enough to be confirmed. */
    private fun settled(): Boolean = markedAt == 0L || SystemClock.elapsedRealtime() - markedAt >= AnnounceVolumeRules.CONFIRM_AFTER_MS

    // ---- around an announcement

    /**
     * What [before] did about the volume of one announcement, for [after].
     *
     * @param standing the media step the app looks after until the restore; null if it left
     * the volume alone
     * @param trueBefore the step to put back
     * @param routeLost the hearing aid was no longer the media route when the app wanted
     * to write: the announcement must not be played
     */
    class Applied internal constructor(val reason: String, val standing: Int?, val trueBefore: Int, val routeLost: Boolean) {
        /** The step last seen for the hearing aid while the announcement played ([watch]). */
        internal var seen: Int = standing ?: 0
    }

    /**
     * The volume step of an announcement: decides whether the media volume is touched, and
     * if so notes the phone's value, writes the owner's step and reads it back. One slice of
     * the main thread without a suspension, called by the announcer after its last route
     * check and before it asks for the audio focus, so that the value stands before the
     * lead-in silence opens the stream. Journals `announce_volume`, whatever it did.
     *
     * An announcement that sets no step of its own (the switch is off, the other channel,
     * other media play, the silence test) still looks at a note that an earlier one left
     * behind, in the same slice, and puts the owner's value back if the rules say so
     * (`volume_state` with `trigger=announcement`).
     *
     * @param attributes what the announcement is played with
     * @param silence the silence test: no step is written for it
     */
    fun before(reason: String, channel: AnnounceChannel, attributes: AudioAttributes, silence: Boolean): Applied {
        val fields = LinkedHashMap<String, Any?>()
        fields["reason"] = reason
        fields["channel"] = channel.word
        pendingReport = null
        var applied = Applied(reason, standing = null, trueBefore = 0, routeLost = false)
        // What is known so far, kept outside the try: the journal line and the line on the
        // screen must say what was done also if a later question to Android throws.
        var action: Action? = null
        var why: Why? = null
        var plan: AnnounceVolumeRules.Plan? = null
        var oldNote: VolumeNote? = null
        var before = 0
        var got: Int? = null
        // the plan's note is in storage; the write was asked for
        var saved = false
        var wrote = false
        var governsMusic = true

        /** From this line on the value is in the app's care, whatever happens next. */
        fun care(standing: Int, trueBefore: Int) {
            applied = Applied(reason, standing, trueBefore, routeLost = false)
            current = applied
            caring = true
        }
        try {
            val settings = store.settings.value
            val governing = attributes.volumeControlStream
            governsMusic = governing == AudioManager.STREAM_MUSIC
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            oldNote = store.note
            // The route first, then the values: a value says nothing about the route.
            val routeIsAid = mediaToAid()
            val muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
            before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            got = before
            val decided = AnnounceVolumeRules.before(
                AnnounceVolumeRules.Situation(
                    channel = channel,
                    silence = silence,
                    own = settings.own,
                    governsMusic = governsMusic,
                    routeIsAid = routeIsAid,
                    fixed = audio.isVolumeFixed,
                    muted = muted,
                    index = before,
                    max = max,
                    modeNormal = audio.mode == AudioManager.MODE_NORMAL,
                    // before the app's own first sound: this is somebody else's
                    otherMedia = audio.isMusicActive,
                    note = oldNote,
                    noteSettled = settled(),
                ),
            )
            plan = decided
            action = decided.action
            why = decided.why
            decided.rescaled?.let { store.setStep(it, "rescaled") }

            var leftover: String? = null
            val note = decided.note
            val trueBefore = decided.trueBefore ?: before
            if (decided.touches && note != null) {
                if (!store.saveNote(note)) {
                    // nothing to put the value back from, so it is not changed
                    action = Action.SKIPPED
                    why = Why.NOTE_NOT_SAVED
                } else if (decided.action == Action.ALREADY) {
                    // nothing to write, but the keys may change it while the announcement plays
                    AnnounceVolumeRules.standing(decided)?.let { care(it, trueBefore) }
                } else {
                    saved = true
                    val written = if (!mediaToAid()) {
                        AnnounceVolumeRules.routeLostBeforeWrite(decided, oldNote)
                    } else {
                        // the one write; flags 0: no panel, no sound, no screen
                        wrote = true
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, decided.wanted ?: before, 0)
                        val read = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                        got = read
                        // Why a write was not taken is only asked if it was not: nothing
                        // stands between the read and the line that takes the value into care.
                        val refused = read != decided.wanted
                        AnnounceVolumeRules.afterWrite(decided, before, read, oldNote, refused && doNotDisturb(), refused && userRestricted())
                    }
                    written.standing?.let { care(it, trueBefore) }
                    store.replaceNote(written.note)
                    action = written.action
                    why = written.why
                    if (why == Why.ROUTE_NOT_AID) applied = Applied(reason, standing = null, trueBefore = 0, routeLost = true)
                }
            } else {
                if (why == Why.ROUTE_NOT_AID) applied = Applied(reason, standing = null, trueBefore = 0, routeLost = true)
                if (oldNote != null) {
                    // Not this announcement's to use: looked at as at any other moment,
                    // with the route asked again right before a write.
                    val word = carryOut(decided.leftover, oldNote)
                    leftover = word
                    journalState("announcement", word, null)
                    got = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                }
            }

            fields["governing"] = governing
            fields["setting"] = settings.own?.on(max)?.step ?: "phone"
            fields["action"] = action?.word
            fields["why"] = why?.word
            // whose the media values are: only the hearing aid's while this says `aid`
            fields["mRoute"] = routeWord()
            fields["mBefore"] = before
            fields["mWanted"] = decided.wanted
            fields["mGot"] = got
            fields["mMax"] = max
            fields["mMuted"] = muted
            val aIndex = assistantIndex()
            fields["aIndex"] = aIndex
            fields["aMax"] = assistantMax()
            fields["mDb"] = got?.let { runCatching { db(AudioManager.STREAM_MUSIC, it) }.getOrNull() }
            fields["aDb"] = aIndex?.let { runCatching { db(STREAM_ASSISTANT, it) }.getOrNull() }
            fields["zen"] = zenWord(notifications.currentInterruptionFilter)
            fields["mode"] = modeWord(audio.mode)
            fields["routeTypes"] = audio.getAudioDevicesForAttributes(attributes).map { it.type }
            fields["note"] = decided.noteUse.word
            if (leftover != null) fields["leftover"] = leftover
        } catch (e: Exception) {
            // Whatever Android threw: the announcement plays at the value that stands.
            val decided = plan
            if (saved && applied.standing == null && decided != null) {
                // The plan's note is in storage, and the announcement has not taken the
                // value into its care.
                if (!wrote) {
                    // Nothing was written: what stands in storage is what stood there
                    // before, if this plan was using it.
                    runCatching { store.replaceNote(AnnounceVolumeRules.routeLostBeforeWrite(decided, oldNote).note) }
                    action = Action.SKIPPED
                    why = Why.ERROR
                } else {
                    // Whether the write was taken is not known. The value read now tells,
                    // by the same rule as after a write. Without a value the note stays as
                    // it is, and a later look compares it.
                    val now = runCatching { if (mediaToAid()) audio.getStreamVolume(AudioManager.STREAM_MUSIC) else null }.getOrNull()
                    if (now != null) {
                        val written = AnnounceVolumeRules.afterWrite(decided, before, now, oldNote, doNotDisturb = false, userRestricted = false)
                        written.standing?.let { care(it, decided.trueBefore ?: before) }
                        runCatching { store.replaceNote(written.note) }
                        action = written.action
                        why = written.why
                        got = now
                    }
                }
            }
            // what was done stays what the line says; the exception stands next to it
            if (action == null) {
                action = Action.SKIPPED
                why = Why.ERROR
            }
            fields["action"] = action?.word
            fields["why"] = why?.word
            fields["exception"] = e.javaClass.name
        }
        // for the line under the slider, shown when the announcement is over
        if (!silence) pendingReport = VolumeReport(reason, notPlayed = null, action = action, why = why, step = got ?: 0, governsMusic = governsMusic)
        journal.log("announce_volume", fields)
        return applied
    }

    /**
     * Called by the announcer's guard every time it looks at the output, about five times
     * a second while an announcement plays. While the app looks after the volume, the note
     * in storage is kept in step with the value that stands: the volume keys can change it
     * at any moment, and if the route is lost before the restore, a later look must still
     * recognise the value. Journals `announce_volume_seen` when the value has moved.
     */
    fun watch() {
        val applied = current ?: return
        if (!caring) return
        try {
            // the route first: without it the value is another device's
            if (!mediaToAid()) return
            val muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
            val index = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val note = store.note
            val next = AnnounceVolumeRules.watched(note, routeIsAid = true, muted = muted, index = index)
            if (next != null && next != note) {
                store.replaceNote(next)
                journal.log("announce_volume_seen", mapOf("reason" to applied.reason, "mWas" to applied.seen, "mNow" to next.set))
                applied.seen = next.set
            }
        } catch (e: Exception) {
            // the restore reads the value again
        }
    }

    /**
     * Half a second into the speech: which volume group the announcement is really in.
     * Journals `announce_volume_mid`. A speech that is over before that leaves no such line.
     */
    fun mid(reason: String) {
        val fields = LinkedHashMap<String, Any?>()
        fields["reason"] = reason
        try {
            // with nothing else playing: true means the speech is under the media volume
            fields["musicActive"] = audio.isMusicActive
            // whose `mNow` is: only the hearing aid's while this says `aid`
            fields["mRoute"] = routeWord()
            fields["mNow"] = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            fields["aNow"] = assistantIndex()
        } catch (e: Exception) {
            fields["exception"] = e.javaClass.name
        }
        journal.log("announce_volume_mid", fields)
    }

    /**
     * The announcement is over, however it ended: now the line under the slider can say
     * what happened. Before its last word nobody knows whether it was played.
     *
     * @param result how it ended, in the announcer's words; null if it broke off
     */
    fun ended(reason: String, result: String?) {
        val report = pendingReport ?: return
        pendingReport = null
        if (report.reason == reason) reportState.value = report.copy(ended = result ?: "error")
    }

    /**
     * The restore after an announcement, for every way it can end. Waits for the tail of the
     * last word if it was spoken to its end, then, in one slice: asks the route, reads the
     * value, takes over a step the owner set with the keys, and puts the phone's value
     * back. A value that reads back is not yet back for good ([VolumeNote]): the note is
     * marked and a look two seconds later confirms it. Journals `announce_volume_restore`.
     * Does nothing if [before] left the volume alone. To be called inside `NonCancellable`,
     * before the audio focus is given back.
     *
     * @param result how the announcement ended; null if it broke off
     */
    suspend fun after(applied: Applied, result: String?) {
        val standing = applied.standing ?: return
        val fields = LinkedHashMap<String, Any?>()
        fields["reason"] = applied.reason
        try {
            val waitMs = AnnounceVolumeRules.restoreWaitMs(result)
            if (waitMs > 0) delay(waitMs)
            fields["waitedMs"] = waitMs
            // one slice from here: route, read, write, read
            val routeIsAid = mediaToAid()
            val muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
            val now = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val plan = AnnounceVolumeRules.restore(standing, applied.trueBefore, routeIsAid, muted, now, applied.seen, result)
            var back: Int? = null
            val write = plan.write
            val end = when {
                write == null -> AnnounceVolumeRules.RestoreEnd(plan.result, plan.note)
                // the route once more, right before the write
                !mediaToAid() -> AnnounceVolumeRules.restoreRouteLost(plan, applied.trueBefore)
                else -> {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, write, 0)
                    val read = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    back = read
                    AnnounceVolumeRules.afterRestore(plan, applied.trueBefore, read)
                }
            }
            store.replaceNote(end.note)
            fields["result"] = end.result.word
            // only a value of the hearing aid's: without the route the number is another device's
            fields["mNow"] = now.takeIf { routeIsAid }
            fields["mBack"] = back
            fields["note"] = noteWord(end.note)
            plan.adopt?.let { adopted ->
                // The owner pressed the volume keys while it spoke: that is his step from now on.
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                fields["adopted"] = true
                fields["oldStep"] = store.settings.value.step?.on(max)?.step
                fields["newStep"] = adopted
                store.setStep(StoredStep(adopted, max), "keys")
            }
            when {
                end.note?.restored == true -> {
                    putBacks = 0
                    confirmLater()
                }
                // If it was not the hearing aid that went, nothing else will bring a look.
                end.result == Restore.DEFERRED_GOING_AWAY -> lookLater("going_check", GOING_LOOK_MS)
            }
        } catch (e: Exception) {
            // the note stays, and the next look has another try
            fields["result"] = "error"
            fields["exception"] = e.javaClass.name
        } finally {
            caring = false
            current = null
        }
        journal.log("announce_volume_restore", fields)
    }

    /** An announcement was not played at all. For the line under the slider. */
    fun notPlayed(reason: String, why: String) {
        pendingReport = null
        reportState.value = VolumeReport(reason, notPlayed = why)
    }

    // ---- a note that was left over

    /**
     * Looks at a note outside the announcement that wrote it, and journals `volume_state`
     * with what it found. Only while the hearing aid alone is the media route and media are
     * not muted: then, if the value is the one the app set, the owner's value is put back;
     * if the app's restore held, the note is cleared; if the value is another one by now,
     * the note is dropped. With any other route nothing is compared and nothing written.
     *
     * @param trigger why the look happens: `app_start`, `aid_route`, `devices`, `mode`,
     * `communication_device`, `voice_link`, `dnd`, `restore_check`, `going_check`, `screen`,
     * `section`, `setting`
     */
    fun resolveLeftover(trigger: String) {
        var leftover = "none"
        var exception: String? = null
        try {
            val note = store.note
            leftover = when {
                note == null -> "none"
                // the note is that announcement's own, and its restore is on its way
                caring -> "announcing"
                else -> {
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    // the route first, then the values
                    val routeIsAid = mediaToAid()
                    val muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
                    val index = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    carryOut(AnnounceVolumeRules.leftover(note, routeIsAid, muted, index, max, settled()), note)
                }
            }
        } catch (e: Exception) {
            leftover = "error"
            exception = e.javaClass.name
        }
        journalState(trigger, leftover, exception)
    }

    /**
     * Does what the rules decided about [note], in the slice the decision was made in.
     *
     * @return what became of the note, for the journal: the decision's word, or
     * `restore_refused` if the write was not taken
     */
    private fun carryOut(decision: Leftover, note: VolumeNote): String = when (decision) {
        Leftover.None, Leftover.Wait, Leftover.WaitMuted, Leftover.TooEarly -> decision.word
        Leftover.Drop, Leftover.Confirmed -> {
            store.replaceNote(null)
            putBacks = 0
            decision.word
        }
        is Leftover.PutBack ->
            // the route once more, right before the write
            if (!mediaToAid()) {
                Leftover.Wait.word
            } else {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, decision.index, 0)
                val back = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                store.replaceNote(AnnounceVolumeRules.afterPutBack(note, decision.index, back))
                if (back == decision.index) {
                    // Back, but not for good until a look has found the aid still there.
                    // That look comes by itself a few times only: if something keeps
                    // setting the old step again, the app must not answer it for ever.
                    // The other occasions for a look stay.
                    if (++putBacks <= PUT_BACKS_IN_A_ROW) confirmLater() else markedAt = SystemClock.elapsedRealtime()
                    decision.word
                } else {
                    "restore_refused"
                }
            }
    }

    // ---- the slider

    /**
     * What a release of the slider led to.
     *
     * @param sample the step to play a sample with; null if nothing is to be played
     * @param line what the screen says in place of a sample; null if a sample follows
     */
    class Released(val sample: Int?, val line: String?)

    /**
     * The owner let go of the slider at [step]. With "Eigene Lautstärke nur für Ansagen" the
     * step is stored for the announcements, and a sample follows if the hearing aid is the
     * output. Without it the slider is the phone's media volume for the hearing aid itself:
     * the step is written at once and for good, like a press of the volume keys, journaled
     * as `volume_slider`, and a sample follows if Android took it.
     */
    fun release(step: Int, origin: String): Released {
        reportState.value = null
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val wanted = VolumeSteps.clamp(step, max)
        if (store.settings.value.ownLevel) {
            store.setStep(StoredStep(wanted, max), origin)
            return if (mediaToAid()) Released(wanted, null) else Released(null, AnnounceTexts.FROM_NEXT_ANNOUNCEMENT)
        }
        if (caring) return Released(null, AnnounceTexts.BUSY)
        val fields = LinkedHashMap<String, Any?>()
        fields["origin"] = origin
        fields["wanted"] = wanted
        fields["mMax"] = max
        var released = Released(null, AnnounceTexts.notChanged(Why.ERROR))
        try {
            // The route first, then the values. What is written and what not is decided
            // by the rules, like for an announcement.
            val routeIsAid = mediaToAid()
            val muted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
            val before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val plan = AnnounceVolumeRules.sliderWrite(routeIsAid, audio.isVolumeFixed, muted, before, wanted)
            // the route once more, right before the write
            val why = plan.why ?: Why.ROUTE_NOT_AID.takeIf { plan.write && !mediaToAid() }
            // without the route the number is another device's
            fields["before"] = before.takeIf { routeIsAid }
            if (why != null) {
                fields["result"] = Action.SKIPPED.word
                fields["why"] = why.word
                released = Released(null, AnnounceTexts.notChanged(why))
            } else {
                // flags 0: no panel, no sound, no screen
                if (plan.write) audio.setStreamVolume(AudioManager.STREAM_MUSIC, wanted, 0)
                val got = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                fields["got"] = got
                val refused = got != wanted
                val end = AnnounceVolumeRules.afterSliderWrite(before, wanted, got, refused && doNotDisturb(), refused && userRestricted())
                fields["result"] = end.action.word
                if (end.action == Action.REFUSED) {
                    fields["why"] = end.why?.word
                    released = Released(null, AnnounceTexts.refused(end.why))
                } else {
                    // The owner has set this value himself: whatever an announcement left to
                    // be put back is out of date.
                    if (store.note != null) fields["note"] = "dropped"
                    store.replaceNote(null)
                    released = Released(got, null)
                }
            }
        } catch (e: Exception) {
            fields["result"] = Action.SKIPPED.word
            fields["why"] = Why.ERROR.word
            fields["exception"] = e.javaClass.name
        }
        journal.log("volume_slider", fields)
        return released
    }

    /**
     * "Eigene Lautstärke nur für Ansagen". Switched on for the first time it begins with
     * the phone's step for the hearing aid, so that the switch alone changes nothing.
     */
    fun setOwnLevel(on: Boolean, origin: String) {
        store.setOwnLevel(on, origin) {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val phone = if (mediaToAid()) audio.getStreamVolume(AudioManager.STREAM_MUSIC) else null
            StoredStep(VolumeSteps.initial(phone, max), max)
        }
    }

    /** The highest step of the media volume on this phone. */
    fun maxStep(): Int = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    /**
     * A sample is asked for: the question the announcer asks it until its last word. A
     * sample that is no longer the newest is not played, and one that plays is cut off.
     * Called on the main thread.
     */
    fun newestSample(): () -> String? {
        val mine = ++samples
        return { if (mine != samples) "superseded" else null }
    }

    // ---- what Android says right now

    /** The volumes as the screen and the adb answer show them. Cheap enough to call every second. */
    fun live(): VolumeLive {
        val address = hearingAid.address
        val mediaToAid = address != null && onlyTheAid(audio.getAudioDevicesForAttributes(media), address)
        return VolumeLive(
            aidSelected = address != null,
            mediaToAid = mediaToAid,
            assistantToAid = address != null && onlyTheAid(audio.getAudioDevicesForAttributes(assistant), address),
            mediaStep = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            mediaMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            mediaMuted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
            mediaFixed = audio.isVolumeFixed,
            assistantStep = assistantIndex(),
            assistantMax = assistantMax(),
            doNotDisturb = doNotDisturb(),
            mediaGoverned = mediaGoverned,
        )
    }

    /**
     * Whether Android would send media to the selected hearing aid's media device and to
     * nothing else, right now. `setStreamVolume` acts on that route, whatever the app means.
     */
    private fun mediaToAid(): Boolean {
        val address = hearingAid.address ?: return false
        return onlyTheAid(audio.getAudioDevicesForAttributes(media), address)
    }

    /** The media route as a word: `aid`, `other`, `none`, or `no_aid_selected`. */
    private fun routeWord(): String {
        val address = hearingAid.address ?: return "no_aid_selected"
        val outputs = audio.getAudioDevicesForAttributes(media)
        return when {
            outputs.isEmpty() -> "none"
            onlyTheAid(outputs, address) -> "aid"
            else -> "other"
        }
    }

    /** Android's assistant volume, read only; null if this build does not show it to the app. */
    private fun assistantIndex(): Int? = runCatching { audio.getStreamVolume(STREAM_ASSISTANT) }.getOrNull()

    private fun assistantMax(): Int? = runCatching { audio.getStreamMaxVolume(STREAM_ASSISTANT) }.getOrNull()

    /**
     * What step [index] of [stream] is worth on a Bluetooth media device, in decibels, by
     * the phone's own curve; 0 is the loudest. A muted step has no number and gets a word.
     */
    private fun db(stream: Int, index: Int): Any {
        val value = audio.getStreamVolumeDb(stream, index, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        return when {
            value.isNaN() -> "nan"
            value.isInfinite() -> if (value < 0) "-inf" else "inf"
            else -> (value * 100).roundToInt() / 100.0
        }
    }

    /** The decibels of every step from 1 to [max], or the name of what Android threw. */
    private fun curve(stream: Int, max: Int): Any = try {
        (1..max).map { db(stream, it) }
    } catch (e: Exception) {
        e.javaClass.name
    }

    /**
     * Journals `volume_curve` once per Android build: what every step of the media volume
     * and of the assistant volume is worth in decibels on this phone. The numbers in
     * CLAUDE.md are those of the public Android source; the phone runs a later build.
     */
    private fun curveOncePerBuild() {
        if (store.curveBuild == Build.ID) return
        try {
            val mMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val aMax = assistantMax()
            journal.log(
                "volume_curve",
                mapOf(
                    "androidBuild" to Build.ID,
                    "device" to "bluetooth_a2dp",
                    "fromStep" to 1,
                    "mMax" to mMax,
                    "mDb" to curve(AudioManager.STREAM_MUSIC, mMax),
                    "aMax" to aMax,
                    "aDb" to aMax?.let { curve(STREAM_ASSISTANT, it) },
                ),
            )
            store.curveBuild = Build.ID
        } catch (e: Exception) {
            journal.log("volume_curve", mapOf("androidBuild" to Build.ID, "exception" to e.javaClass.name))
        }
    }

    private fun journalState(trigger: String, leftover: String, exception: String?) {
        val fields = LinkedHashMap<String, Any?>()
        fields["trigger"] = trigger
        try {
            val settings = store.settings.value
            val mMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val note = store.note
            fields["channel"] = settings.channel.word
            fields["own"] = settings.own?.on(mMax)?.step ?: "off"
            fields["leadInMs"] = settings.leadIn.ms
            fields["route"] = routeWord()
            fields["mIndex"] = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            fields["mMax"] = mMax
            fields["mMuted"] = audio.isStreamMute(AudioManager.STREAM_MUSIC)
            fields["aIndex"] = assistantIndex()
            fields["aMax"] = assistantMax()
            // declared in the manifest only to learn this: does Android grant it to the
            // sideloaded holder of the assistant role?
            fields["manageAssistantAudio"] =
                if (context.checkSelfPermission(MANAGE_ASSISTANT_AUDIO) == PackageManager.PERMISSION_GRANTED) "granted" else "denied"
            fields["zen"] = zenWord(notifications.currentInterruptionFilter)
            fields["androidBuild"] = Build.ID
            fields["note"] = noteWord(note)
            if (note != null) {
                fields["noteBefore"] = note.before
                fields["noteSet"] = note.set
            }
            fields["leftover"] = leftover
        } catch (e: Exception) {
            fields["stateException"] = e.javaClass.name
        }
        if (exception != null) fields["exception"] = exception
        journal.log("volume_state", fields)
    }

    /**
     * The note in storage as a word: `none`; `pending`, the app's step may still stand;
     * `to_confirm`, the owner's value is back and the look that confirms it is still to come.
     */
    private fun noteWord(note: VolumeNote?): String = when {
        note == null -> "none"
        note.restored -> "to_confirm"
        else -> "pending"
    }

    /** Do Not Disturb is on in one of its forms. It can mute media, and then no write is taken. */
    private fun doNotDisturb(): Boolean = when (notifications.currentInterruptionFilter) {
        NotificationManager.INTERRUPTION_FILTER_PRIORITY,
        NotificationManager.INTERRUPTION_FILTER_ALARMS,
        NotificationManager.INTERRUPTION_FILTER_NONE,
        -> true
        else -> false
    }

    private fun userRestricted(): Boolean =
        runCatching { users.hasUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME) }.getOrDefault(false)

    private fun zenWord(filter: Int): String = when (filter) {
        NotificationManager.INTERRUPTION_FILTER_ALL -> "all"
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"
        NotificationManager.INTERRUPTION_FILTER_NONE -> "none"
        NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> "unknown"
        else -> filter.toString()
    }

    private companion object {
        /**
         * `AudioManager.STREAM_ASSISTANT`, Android's own volume for assistant speech. As a
         * number, because the constant is newer than the oldest Android this app installs
         * on. Only ever read.
         */
        const val STREAM_ASSISTANT = 11

        /** Declared in the manifest, never used: see [journalState]. */
        const val MANAGE_ASSISTANT_AUDIO = "android.permission.MANAGE_ASSISTANT_AUDIO"

        /**
         * How long after something changed (the hearing aid's media device appeared, a
         * voice link went down, a call ended) a note is looked at. The Bluetooth stack hands
         * Android its remembered volume at the connection on another thread, and a route
         * takes a moment to settle.
         */
        const val LOOK_AFTER_CHANGE_MS = 1_000L

        /**
         * How long after an announcement that ended with "the output is going away" the
         * note is looked at once more. Android takes the output away a second after that
         * notice; if the hearing aid is still the route after three, it was another device
         * that went, and the owner's value can be put back.
         */
        const val GOING_LOOK_MS = 3_000L

        /** How much longer than the wait for a look its wake lock lasts at most. */
        const val LOOK_WAKE_MARGIN_MS = 2_000L

        /** How much later than the rule asks the look that confirms a restore is set. */
        const val CONFIRM_MARGIN_MS = 100L

        /** How many times in a row the look that confirms a put-back is set by a put-back. */
        const val PUT_BACKS_IN_A_ROW = 3

        /**
         * Within this time after [start] a report of devices is Android's answer to the
         * registration, and a screen that comes up shows what [start] has just journaled.
         */
        const val FIRST_REPORT_MS = 3_000L
    }
}
