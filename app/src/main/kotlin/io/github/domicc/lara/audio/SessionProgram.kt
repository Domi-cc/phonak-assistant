package io.github.domicc.lara.audio

import io.github.domicc.lara.LaraApp
import io.github.domicc.lara.shelly.RemoteProgram

/**
 * What happens inside one open voice link. The service opens the link (start tone included),
 * runs the program and closes the link with the program's result.
 */
fun interface SessionProgram {
    /**
     * @return the session result word: "ok", or a short reason that ends up in `session_end`
     * and makes the link play the error tone
     */
    suspend fun run(link: AudioLink): String
}

/** The programs a trigger can start, by name. */
object SessionPrograms {
    /**
     * Every press on the Shelly remote is announced in words ("Taste 1 kurz"). What a tap
     * starts while a remote is enrolled, and what a press on the remote opens by itself.
     */
    const val REMOTE = "remote"

    /** The echo test. What a tap starts while no remote is enrolled. */
    const val ECHO = "echo"

    /** The count session, a tool for measurement series: started from the screen or over adb. */
    const val COUNT = "count"

    fun create(name: String?): SessionProgram = when (name) {
        REMOTE -> RemoteProgram(LaraApp.instance)
        COUNT -> LaraApp.instance.let { CountProgram(it.router, it.countSounds, it.shellyStore) }
        // also for a name this build does not know
        else -> EchoProgram()
    }

    /**
     * True for the programs that listen to the remote. Only for them the scan is switched to
     * its session mode and the router keeps presses that arrive while the link is opening.
     */
    fun takesInputs(name: String?): Boolean = name == REMOTE || name == COUNT
}
