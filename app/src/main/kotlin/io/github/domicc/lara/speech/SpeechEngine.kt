package io.github.domicc.lara.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * The phone's speech engine, used for one thing only: writing German prompts into sound files.
 * It never speaks itself. What an engine plays is played by the engine's own process, which
 * does not keep this app's voice link to the hearing aid open (see the design, section 6.5).
 *
 * Needs no activity and no visible app: the engine is a service this app binds, which Android
 * also allows from the background. [close] releases that binding again.
 */
internal class SpeechEngine private constructor(private val tts: TextToSpeech) {

    /** The result of [open]: an engine that is ready for German, or the reason there is none. */
    class Opened(val engine: SpeechEngine?, val problem: String?)

    private val jobs = ConcurrentHashMap<String, CompletableDeferred<String>>()

    /** The package of the engine in use, for the journal. */
    val name: String? get() = tts.defaultEngine

    /** The voice in use and whether it needs the network, for the journal. */
    val voice: String? get() = tts.voice?.let { it.name + if (it.isNetworkConnectionRequired) " (network)" else "" }

    init {
        // Called on threads of the engine connection; all they do is finish the waiting job.
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) = Unit

                override fun onDone(utteranceId: String) = finish(utteranceId, DONE)

                @Deprecated("Android still calls it on engines that do not report an error code")
                override fun onError(utteranceId: String) = finish(utteranceId, "engine_error")

                override fun onError(utteranceId: String, errorCode: Int) = finish(utteranceId, "engine_error_$errorCode")

                override fun onStop(utteranceId: String, interrupted: Boolean) = finish(utteranceId, "engine_stopped")
            },
        )
    }

    /**
     * Has [text] spoken into [target] as a WAVE file and waits until the file is complete.
     *
     * @param id tells this job apart from others; any text
     * @return null if the file is there, otherwise a short reason for the journal
     */
    suspend fun synthesize(text: String, id: String, target: File): String? {
        val job = CompletableDeferred<String>()
        jobs[id] = job
        val queued = tts.synthesizeToFile(text, Bundle(), target, id)
        if (queued != TextToSpeech.SUCCESS) {
            jobs.remove(id)
            return "not_queued_$queued"
        }
        val result = withTimeoutOrNull(JOB_TIMEOUT_MS) { job.await() }
        jobs.remove(id)
        return when (result) {
            DONE -> null
            null -> "engine_timeout"
            else -> result
        }
    }

    fun close() = tts.shutdown()

    private fun finish(id: String, result: String) {
        jobs[id]?.complete(result)
    }

    companion object {
        private const val DONE = "done"
        private const val OPEN_TIMEOUT_MS = 10_000L
        private const val JOB_TIMEOUT_MS = 15_000L

        /** Connects to the default engine and asks it for German. Never throws. */
        suspend fun open(context: Context): Opened {
            val status = CompletableDeferred<Int>()
            val tts = try {
                // On the main thread, where Android delivers the engine connection anyway.
                withContext(Dispatchers.Main) { TextToSpeech(context.applicationContext) { status.complete(it) } }
            } catch (e: Exception) {
                return Opened(null, "engine_exception $e")
            }
            val ready = withTimeoutOrNull(OPEN_TIMEOUT_MS) { status.await() }
            if (ready != TextToSpeech.SUCCESS) {
                tts.shutdown()
                // -1 here is what Android reports when no engine is installed or it cannot be bound
                return Opened(null, if (ready == null) "engine_timeout" else "engine_init_$ready")
            }
            val language = try {
                tts.setLanguage(Locale.GERMANY)
            } catch (e: Exception) {
                tts.shutdown()
                return Opened(null, "engine_exception $e")
            }
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts.shutdown()
                return Opened(null, "no_german_voice_$language")
            }
            return Opened(SpeechEngine(tts), null)
        }
    }
}
