package io.github.domicc.lara.assistant

import android.content.Intent
import android.os.RemoteException
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Stub. Android only accepts an assistant that also declares a recognition service; this app
 * does not offer speech recognition to other apps, so every request is refused.
 */
class LaraRecognitionService : RecognitionService() {

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        try {
            listener?.error(SpeechRecognizer.ERROR_CLIENT)
        } catch (e: RemoteException) {
            // caller is gone; nothing to report
        }
    }

    override fun onCancel(listener: Callback?) = Unit

    override fun onStopListening(listener: Callback?) = Unit
}
