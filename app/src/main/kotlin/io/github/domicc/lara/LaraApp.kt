package io.github.domicc.lara

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import io.github.domicc.lara.announce.Announcer
import io.github.domicc.lara.announce.HourlyAnnouncement
import io.github.domicc.lara.audio.HeadsetLink
import io.github.domicc.lara.audio.HearingAidStore
import io.github.domicc.lara.audio.SessionService
import io.github.domicc.lara.journal.Journal
import io.github.domicc.lara.shelly.ShellyLive
import io.github.domicc.lara.shelly.ShellyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Process-wide singletons. Created before any activity, service or receiver runs. */
class LaraApp : Application() {

    lateinit var journal: Journal
        private set

    lateinit var hearingAid: HearingAidStore
        private set

    lateinit var headset: HeadsetLink
        private set

    lateinit var announcer: Announcer
        private set

    lateinit var shelly: ShellyStore
        private set

    lateinit var shellyLive: ShellyLive
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        journal = Journal(this)
        hearingAid = HearingAidStore(this)
        headset = HeadsetLink(this, journal, hearingAid)
        announcer = Announcer(this, journal, headset)
        shelly = ShellyStore(this, journal)
        shellyLive = ShellyLive(this, journal, shelly)
        journal.log(
            "process_start",
            mapOf(
                "pid" to Process.myPid(),
                "version" to BuildConfig.VERSION_NAME,
                "targetSdk" to applicationInfo.targetSdkVersion,
                "build" to Build.ID,
                "previousExit" to previousExit(),
            ),
        )
        headset.connect()
        HourlyAnnouncement.ensureScheduled(this)
        CoroutineScope(Dispatchers.Default).launch { SessionService.recoverAfterCrash(this@LaraApp) }
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

    companion object {
        lateinit var instance: LaraApp
            private set
    }
}
