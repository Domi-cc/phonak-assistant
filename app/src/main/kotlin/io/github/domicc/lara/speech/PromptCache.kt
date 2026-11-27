package io.github.domicc.lara.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.github.domicc.lara.core.audio.Resampler
import io.github.domicc.lara.core.audio.Wav
import io.github.domicc.lara.core.audio.WavResult
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Spoken phrases as ready-to-play audio for the voice link (16 kHz mono).
 *
 * Why not simply let the speech engine talk: its audio is played by the engine's own process.
 * In a session that does not count as "this app is playing", and it cannot be mixed into the
 * one audio track that keeps the voice link to the hearing aid alive. So each phrase is
 * synthesised to a file once, converted, and kept; a session then only plays stored audio and
 * never waits for the engine.
 *
 * [get] never blocks: it returns null while a phrase is not ready, and the caller falls back to
 * a tone.
 */
class PromptCache(private val context: Context, private val journal: Journal) {

    private val directory = File(context.filesDir, "prompts").apply { mkdirs() }
    private val memory = ConcurrentHashMap<String, ShortArray>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val oneAtATime = Mutex()
    private var engine: TextToSpeech? = null

    /** The audio for [text], or null if it has not been synthesised yet. */
    fun get(text: String): ShortArray? {
        memory[text]?.let { return it }
        val file = fileFor(text)
        if (!file.isFile || file.length() == 0L) return null
        return runCatching { readPcm(file) }.getOrNull()?.also { memory[text] = it }
    }

    /** Synthesises whatever of [texts] is missing, in the background. Safe to call repeatedly. */
    fun warm(texts: Collection<String>) {
        scope.launch {
            oneAtATime.withLock {
                val missing = texts.filter { get(it) == null }
                if (missing.isEmpty()) return@withLock
                val tts = engine() ?: run {
                    journal.log("prompts_unavailable", mapOf("missing" to missing.size))
                    return@withLock
                }
                var made = 0
                for (text in missing) if (synthesise(tts, text)) made++
                journal.log("prompts_ready", mapOf("made" to made, "failed" to missing.size - made, "total" to texts.size))
            }
        }
    }

    private suspend fun synthesise(tts: TextToSpeech, text: String): Boolean {
        val wav = File(context.cacheDir, "prompt-" + keyOf(text) + ".wav")
        val done = CompletableDeferred<Boolean>()
        val id = "prompt-" + keyOf(text)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (utteranceId == id) done.complete(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) done.complete(false)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == id) done.complete(false)
            }
        })
        try {
            if (tts.synthesizeToFile(text, Bundle(), wav, id) != TextToSpeech.SUCCESS) return false
            if (withTimeoutOrNull(SYNTHESIS_TIMEOUT_MS) { done.await() } != true) return false
            val pcm = when (val result = Wav.read(wav.readBytes())) {
                is WavResult.Audio -> Resampler.toVoiceLink(result.audio)
                is WavResult.Rejected -> {
                    journal.log("prompt_rejected", mapOf("text" to text, "why" to result.reason))
                    return false
                }
            }
            if (pcm.isEmpty()) return false
            writePcm(fileFor(text), pcm)
            memory[text] = pcm
            return true
        } finally {
            wav.delete()
        }
    }

    /** Creates the engine on first use; null if it or German speech is not available. */
    private suspend fun engine(): TextToSpeech? {
        engine?.let { return it }
        val ready = CompletableDeferred<Int>()
        val tts = withContext(Dispatchers.Main) { TextToSpeech(context) { status -> ready.complete(status) } }
        if (withTimeoutOrNull(ENGINE_TIMEOUT_MS) { ready.await() } != TextToSpeech.SUCCESS) {
            tts.shutdown()
            return null
        }
        val language = tts.setLanguage(Locale.GERMANY)
        if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.shutdown()
            return null
        }
        engine = tts
        return tts
    }

    private fun keyOf(text: String): String = Integer.toHexString(text.hashCode())

    private fun fileFor(text: String): File = File(directory, VERSION + "-" + keyOf(text) + ".pcm")

    private fun writePcm(file: File, pcm: ShortArray) {
        val bytes = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.asShortBuffer().put(pcm)
        file.writeBytes(bytes.array())
    }

    private fun readPcm(file: File): ShortArray {
        val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(buffer.remaining()).also { buffer.get(it) }
    }

    private companion object {
        /** Raise when the voice or the format changes, so that old files are not reused. */
        const val VERSION = "v1"
        const val ENGINE_TIMEOUT_MS = 8_000L
        const val SYNTHESIS_TIMEOUT_MS = 10_000L
    }
}
