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
 * The default is the remote session, in which every press on the remote is announced in
 * words; while no remote is enrolled, a tap with this setting runs the echo test (decided in
 * [io.github.domicc.lara.core.input.Routing]). That is what the tap did on the device before
 * this setting existed. The echo test and the count session have to be chosen deliberately.
 * Every change is journaled: what a `tap_intent` line led to can only be read together with
 * the setting in force.
 */
class TapStore(context: Context, private val journal: Journal) {

    private val prefs = context.getSharedPreferences("input", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(TapProgram.fromWord(prefs.getString(KEY_TAP, null)) ?: TapProgram.REMOTE)

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
