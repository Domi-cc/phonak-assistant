package io.github.domicc.lara.assistant

import android.service.voice.VoiceInteractionService
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.audio.SessionService

/**
 * The privilege anchor. While the owner has selected this app as digital assistant, the system
 * keeps this service bound. That binding, not the role itself, is what lets the app start a
 * microphone session and play audio from the background with the display off.
 */
class LaraVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        ready = true
        val app = LaraApp.instance
        app.journal.log("assistant_bound")
        app.headset.connect()
    }

    override fun onShutdown() {
        ready = false
        LaraApp.instance.journal.log("assistant_unbound")
        super.onShutdown()
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        LaraApp.instance.journal.log("assist_from_keyguard")
        SessionService.start(this, "assist_keyguard", null)
    }

    companion object {
        /** True while the system has this service bound and ready. */
        @Volatile
        var ready: Boolean = false
            private set
    }
}
