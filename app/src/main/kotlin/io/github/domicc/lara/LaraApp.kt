package io.github.domicc.lara

import android.app.ActivityManager
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import io.github.domicc.lara.announce.Announcer
import io.github.domicc.lara.announce.HourlyAnnouncement
import io.github.domicc.lara.audio.AidChannels
import io.github.domicc.lara.audio.HeadsetLink
import io.github.domicc.lara.audio.HearingAidStore
import io.github.domicc.lara.audio.SessionService
import io.github.domicc.lara.calendar.CalendarReminders
import io.github.domicc.lara.calendar.CalendarStore
import io.github.domicc.lara.core.input.Input
import io.github.domicc.lara.input.InputRouter
import io.github.domicc.lara.input.TapStore
import io.github.domicc.lara.journal.DayCounters
import io.github.domicc.lara.journal.Journal
import io.github.domicc.lara.shelly.ShellyIntake
import io.github.domicc.lara.shelly.ShellyStore
import io.github.domicc.lara.speech.CountSounds
import io.github.domicc.lara.speech.PromptCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Process-wide singletons. Created before any activity, service or receiver runs. */
class LaraApp : Application() {

    lateinit var journal: Journal
        private set

    /** Which Bluetooth device is the hearing aid. Nothing audible goes anywhere else. */
    lateinit var hearingAid: HearingAidStore
        private set

    lateinit var headset: HeadsetLink
        private set

    /** Unprompted speech over the media channel. */
    lateinit var announcer: Announcer
        private set

    /**
     * Journals when a sound channel to the hearing aid is really open, whoever opened it, and
     * looks after a voice link that stays up after its session.
     */
    lateinit var channels: AidChannels
        private set

    /** The spoken phrases for the voice link, made ahead of time. */
    lateinit var prompts: PromptCache
        private set

    /** What the count session plays: those phrases, or tones where one is missing. */
    lateinit var countSounds: CountSounds
        private set

    /** Which remote is enrolled and how the app scans for it. */
    lateinit var shellyStore: ShellyStore
        private set

    /** Where every advertisement of the enrolled remote ends up, whichever scan delivered it. */
    lateinit var shelly: ShellyIntake
        private set

    /** What a hearing-aid tap starts. */
    lateinit var tapStore: TapStore
        private set

    /** Where every input ends up and is turned into an action. */
    lateinit var router: InputRouter
        private set

    /** Today's numbers for the screen, counted from the journal. */
    lateinit var counters: DayCounters
        private set

    /** Whether appointments are announced, and of which calendars. */
    lateinit var calendarStore: CalendarStore
        private set

    /** Spoken reminders ten minutes before an appointment. */
    lateinit var calendar: CalendarReminders
        private set

    private val inputBus = MutableSharedFlow<Input>(extraBufferCapacity = INPUT_BUFFER)

    /**
     * Everything the wearer does, from every input path, as it happens. Nothing is replayed to
     * a consumer that subscribes later: a press must never start a recording long after it was
     * made.
     */
    val inputs: SharedFlow<Input> = inputBus.asSharedFlow()

    /**
     * Puts [input] on the bus. Never blocks; an input that nobody listens for or that no
     * consumer has room for is journaled as `input_dropped`.
     *
     * @return how many consumers are listening right now; with 0 the input is lost
     */
    fun publish(input: Input): Int {
        val consumers = inputBus.subscriptionCount.value
        val lost = when {
            consumers == 0 -> "no_consumer"
            !inputBus.tryEmit(input) -> "bus_full"
            else -> null
        }
        if (lost != null) journal.log("input_dropped", InputRouter.fields(input) + ("reason" to lost))
        return consumers
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        journal = Journal(this)
        hearingAid = HearingAidStore(this)
        headset = HeadsetLink(this, journal, hearingAid)
        announcer = Announcer(this, journal, headset)
        channels = AidChannels(this, journal, headset, announcer)
        prompts = PromptCache(this, journal)
        countSounds = CountSounds(prompts)
        shellyStore = ShellyStore(this, journal)
        tapStore = TapStore(this, journal)
        shelly = ShellyIntake(this, journal, shellyStore) { publish(it) }
        counters = DayCounters(journal)
        calendarStore = CalendarStore(this, journal)
        calendar = CalendarReminders(this, journal, calendarStore, announcer)
        // before anything can publish an input: the bus replays nothing
        router = InputRouter(this).also { it.start() }
        journal.log(
            "process_start",
            // first which build of the app this is, then which Android it runs on
            BuildInfo.fields + mapOf(
                "pid" to Process.myPid(),
                "targetSdk" to applicationInfo.targetSdkVersion,
                "androidBuild" to Build.ID,
                "sdkFull" to Build.VERSION.SDK_INT_FULL,
                "btModule" to bluetoothModule(),
                // what the loss count of the first event of the remote is measured against
                "shellyLastId" to shellyStore.lastPacketId,
                "previousExit" to previousExit(),
            ),
        )
        headset.connect()
        channels.start()
        // From here on, because a day file that this process start begins gets the line
        // `aid_channels_watching` or `aid_channels_no_permission` anyway. Later days have
        // neither: the process lives on, and the owner shares single days.
        journal.dayFields = { mapOf("aidChannelsWatching" to channels.watching) }
        HourlyAnnouncement.ensureScheduled(this)
        calendar.start()
        // Undispatched: the recovery reads its flag here and now, before any session of this
        // process can set it, and only then goes on to another thread to wait.
        CoroutineScope(Dispatchers.Default).launch(start = CoroutineStart.UNDISPATCHED) {
            SessionService.recoverAfterCrash(this@LaraApp)
        }
    }

    /** Why the previous process of this app ended; explains gaps in the journal. */
    private fun previousExit(): String? = try {
        getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(packageName, 0, 1)
            .firstOrNull()
            ?.let { "reason=${it.reason} ${it.description ?: ""}".trim() }
    } catch (e: Exception) {
        null
    }

    /**
     * Version of the Bluetooth stack. It is updated through Google Play independently of the
     * system build, and scan throttling lives in it, so measurements are only comparable while
     * this stays the same. Null (and journaled as such) if Android does not show it to an
     * ordinary app.
     */
    private fun bluetoothModule(): String? {
        val listed = try {
            packageManager.getInstalledModules(PackageManager.MATCH_ALL).mapNotNull { it.packageName }
        } catch (e: Exception) {
            emptyList()
        }
        val names = listed.filter { it.endsWith(".bt") || it.endsWith(".bluetooth") } + BLUETOOTH_MODULES
        for (name in names.distinct()) {
            try {
                val info = packageManager.getPackageInfo(name, PackageManager.PackageInfoFlags.of(PackageManager.MATCH_APEX.toLong()))
                return "$name ${info.versionName} (${info.longVersionCode})"
            } catch (e: Exception) {
                // not installed under this name, or not visible: try the next one
            }
        }
        return null
    }

    companion object {
        private const val INPUT_BUFFER = 64

        /** Google's build of the module and the open-source one; also listed in the manifest. */
        private val BLUETOOTH_MODULES = listOf("com.google.android.bt", "com.android.bt")

        lateinit var instance: LaraApp
            private set
    }
}
