package io.github.domicc.lara.audio

import io.github.domicc.lara.LaraApp

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
    const val ECHO = "echo"
    const val COUNT = "count"

    fun create(name: String?): SessionProgram = when (name) {
        COUNT -> LaraApp.instance.let { CountProgram(it, it.router, it.announcer) }
        // also for a name this build does not know
        else -> EchoProgram()
    }
}
