package io.github.domicc.lara.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.audio.SessionService

/** Required by the assistant role. The assist gesture ends up here. */
class LaraSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = LaraSession(this)
}

/** Shows no window: it hands over to the audio session and hides again. */
private class LaraSession(context: Context) : VoiceInteractionSession(context) {

    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        LaraApp.instance.journal.log("assist_gesture", mapOf("showFlags" to showFlags))
        SessionService.start(context, "assist", null)
        hide()
    }
}
