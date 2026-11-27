package io.github.domicc.lara.input

import android.content.Context
import androidx.core.content.edit
import io.github.domicc.lara.core.input.TapProgram
import io.github.domicc.lara.journal.Journal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What a hearing-aid tap starts, remembered between starts: the setting "Hörgeräte-Tap startet".
 *
 * The default is the echo test. That is what the design says for version 0.1, and it is the
 * one combination that has run on the device: tap, voice link, recording from the hearing aid.
 * The count session by tap is the measurement "wake by tap, navigate by Shelly" and has to be
 * chosen deliberately. Every change is journaled: what a `tap_intent` line led to can only be
 * read together with the setting in force.
 */
class TapStore(context: Context, private val journal: Journal) {

    private val prefs = context.getSharedPreferences("input", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(TapProgram.fromWord(prefs.getString(KEY_TAP, null)) ?: TapProgram.ECHO)

    val program: StateFlow<TapProgram> = state.asStateFlow()

    @Synchronized
    fun setProgram(program: TapProgram) {
        if (program == state.value) return
        prefs.edit { putString(KEY_TAP, program.word) }
        journal.log("tap_setting", mapOf("program" to program.word))
        state.value = program
    }

    private companion object {
        const val KEY_TAP = "tap_program"
    }
}
