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
     * The last [max] lines of a journal file, given its end. This is what a starting process
     * reads to show the newest events: a day of measurements makes a file of many megabytes,
     * and reading all of it would delay the answer to the trigger that started the process.
     *
     * @param end the last bytes of the file
     * @param fromStart false if [end] begins somewhere inside the file. Its first line is then
     * most likely cut off and is left out.
     * @return the lines without their line breaks, oldest first; empty lines are left out
     */
    fun lastLines(end: ByteArray, fromStart: Boolean, max: Int): List<String> {
        var start = 0
        if (!fromStart) {
            val firstBreak = end.indexOf(LINE_BREAK)
            if (firstBreak < 0) return emptyList()
            start = firstBreak + 1
        }
        return String(end, start, end.size - start, Charsets.UTF_8)
            .split('\n')
            .filter { it.isNotEmpty() }
            .takeLast(max)
    }

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
