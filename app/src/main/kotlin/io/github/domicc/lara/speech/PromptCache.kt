package io.github.domicc.lara.speech

import android.content.Context
import android.os.SystemClock
import io.github.domicc.lara.core.audio.Resampler
import io.github.domicc.lara.core.audio.Silence
import io.github.domicc.lara.core.audio.Wav
import io.github.domicc.lara.core.audio.WavResult
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The spoken prompts, prepared ahead of time: each one is synthesised once by the phone's
 * speech engine, brought to the format of the voice link (16 kHz mono) and kept as a file in
 * app-private storage.
 *
 * Why ahead of time: a session must never wait for a speech engine. [get] only looks into
 * memory and, the first time, reads one small file; if the prompt is not there it answers null
 * at once and the caller plays tones instead. Why not let the engine speak: see [SpeechEngine].
 *
 * [warm] does the slow part in the background. It is safe to call from anywhere and as often
 * as wanted, also from a service with the display off, and does nothing once every file is
 * there. Each file carries a hash of its text in its name, so a prompt whose wording changes
 * in a later version is made anew without anybody having to remember it. A change of the voice
 * in the phone's settings is not noticed; [rebuild] is for that.
 */
class PromptCache(private val context: Context, private val journal: Journal) {

    enum class State {
        /** Nothing is being made; [Status.ready] says how many prompts there are. */
        IDLE,

        /** The speech engine is at work. */
        WARMING,

        /** The last attempt left prompts missing; [Status.problem] says why. */
        FAILED,
    }

    /** @param ready prompts that can be played, out of [total] */
    data class Status(val state: State, val ready: Int, val total: Int, val problem: String? = null) {
        val complete: Boolean get() = ready == total
    }

    private val dir = File(context.filesDir, "prompts")
    private val memory = ConcurrentHashMap<String, ShortArray>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val warming = AtomicBoolean(false)
    private val statusFlow = MutableStateFlow(Status(State.IDLE, countReady(), Prompts.all.size))

    /** The sample rate the engine delivered last, for the journal. */
    private var engineRate: Int? = null

    val status: StateFlow<Status> = statusFlow.asStateFlow()

    /**
     * The prompt as 16 kHz mono PCM, or null if it has not been made (yet). Never waits for the
     * speech engine. Callers must not change the array: it is the cached one.
     */
    fun get(prompt: Prompt): ShortArray? {
        memory[prompt.key]?.let { return it }
        val file = fileOf(prompt)
        if (!file.exists()) return null
        return try {
            readPcm(file).also { memory[prompt.key] = it }
        } catch (e: IOException) {
            journal.log("prompt_unreadable", mapOf("key" to prompt.key, "exception" to e.toString()))
            null
        }
    }

    /**
     * Makes the prompts that are missing, in the background.
     *
     * @param reason who asks, for the journal: `main_activity`, `assistant_ready` ...
     */
    fun warm(reason: String) {
        if (!warming.compareAndSet(false, true)) return
        scope.launch {
            try {
                makeMissing(reason)
            } catch (e: Exception) {
                journal.log("prompts_failed", mapOf("reason" to reason, "exception" to e.toString()))
                publish(State.FAILED, "exception")
            } finally {
                warming.set(false)
            }
        }
    }

    /** Throws every prompt away and makes all of them anew, for example after a change of voice. */
    fun rebuild(reason: String) {
        if (warming.get()) return
        memory.clear()
        dir.listFiles()?.forEach { it.delete() }
        publish(State.IDLE, null)
        warm(reason)
    }

    private suspend fun makeMissing(reason: String) {
        dir.mkdirs()
        // files of prompts that no longer exist or have a new wording, and half-written ones
        val wanted = Prompts.all.map { fileOf(it).name }.toSet()
        dir.listFiles()?.filter { it.name !in wanted }?.forEach { it.delete() }
        val missing = Prompts.all.filter { !fileOf(it).exists() }
        if (missing.isEmpty()) {
            publish(State.IDLE, null)
            return
        }
        publish(State.WARMING, null)
        journal.log("prompts_warming", mapOf("reason" to reason, "missing" to missing.size))
        val startedAt = SystemClock.elapsedRealtime()
        val opened = SpeechEngine.open(context)
        val engine = opened.engine
        if (engine == null) {
            journal.log("prompts_failed", mapOf("reason" to reason, "why" to opened.problem, "missing" to missing.size))
            publish(State.FAILED, opened.problem)
            return
        }
        var made = 0
        var failed = 0
        var failedInRow = 0
        val wav = File(context.cacheDir, "prompt.wav")
        try {
            for (prompt in missing) {
                val problem = engine.synthesize(prompt.text, prompt.key, wav) ?: convert(wav, prompt)
                if (problem == null) {
                    made++
                    failedInRow = 0
                    publish(State.WARMING, null)
                } else {
                    failed++
                    journal.log("prompt_failed", mapOf("key" to prompt.key, "why" to problem))
                    // an engine that fails this often has gone away; do not fill the journal
                    if (++failedInRow >= GIVE_UP_AFTER) break
                }
            }
            journal.log(
                "prompts_made",
                mapOf(
                    "reason" to reason,
                    "made" to made,
                    "failed" to failed,
                    "notTried" to missing.size - made - failed,
                    "ms" to SystemClock.elapsedRealtime() - startedAt,
                    "engine" to engine.name,
                    "voice" to engine.voice,
                    "engineRate" to engineRate,
                ),
            )
        } finally {
            engine.close()
            wav.delete()
        }
        if (made == missing.size) publish(State.IDLE, null) else publish(State.FAILED, "synthesis_failed")
    }

    /**
     * Turns the engine's WAVE file into the cached file of [prompt].
     *
     * @return null if that worked, otherwise a short reason for the journal
     */
    private fun convert(wav: File, prompt: Prompt): String? {
        val audio = try {
            when (val read = Wav.read(wav.readBytes())) {
                is WavResult.Audio -> read.audio
                is WavResult.Rejected -> return "wav_" + read.reason
            }
        } catch (e: IOException) {
            return "wav_unreadable"
        }
        engineRate = audio.sampleRate
        val pcm = Silence.trim(Resampler.toVoiceLink(audio), Resampler.VOICE_LINK_RATE)
        if (pcm.isEmpty()) return "silent"
        return try {
            // written under another name first, so that a file with the final name is complete
            val draft = File(dir, fileOf(prompt).name + ".part")
            draft.writeBytes(ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { asShortBuffer().put(pcm) }.array())
            if (!draft.renameTo(fileOf(prompt))) return "rename_failed"
            memory[prompt.key] = pcm
            null
        } catch (e: IOException) {
            "write_failed"
        }
    }

    private fun readPcm(file: File): ShortArray {
        val buffer = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(buffer.remaining()).also(buffer::get)
    }

    private fun fileOf(prompt: Prompt): File =
        File(dir, prompt.key + "-" + Integer.toHexString(prompt.text.hashCode()) + ".pcm")

    private fun countReady(): Int = Prompts.all.count { fileOf(it).exists() }

    private fun publish(state: State, problem: String?) {
        statusFlow.value = Status(state, countReady(), Prompts.all.size, problem)
    }

    private companion object {
        const val GIVE_UP_AFTER = 3
    }
}
