package io.github.domicc.lara.core.journal

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Reads a journal file line by line while the app keeps appending to it. Whoever follows the
 * file remembers how far it got and continues there the next time, instead of reading a file
 * of many megabytes again for every new line.
 */
object JournalTail {

    private const val CHUNK_BYTES = 64 * 1024
    private const val LINE_BREAK = '\n'.code.toByte()

    /**
     * Hands every complete line of [stream] to [onLine], without its line break. A last line
     * without a line break is still being written and is left for the next call.
     *
     * @return the number of bytes up to and including the last line break. The caller skips
     * that many bytes of the file before the next call.
     */
    fun read(stream: InputStream, onLine: (String) -> Unit): Long {
        val chunk = ByteArray(CHUNK_BYTES)
        val line = ByteArrayOutputStream()
        var consumed = 0L
        var unfinished = 0L
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) return consumed
            var start = 0
            for (i in 0 until read) {
                // a line break is one byte in UTF-8 and never part of another character
                if (chunk[i] != LINE_BREAK) continue
                line.write(chunk, start, i - start)
                onLine(String(line.toByteArray(), Charsets.UTF_8))
                line.reset()
                consumed += unfinished + (i - start) + 1
                unfinished = 0
                start = i + 1
            }
            line.write(chunk, start, read - start)
            unfinished += read - start
        }
    }
}
