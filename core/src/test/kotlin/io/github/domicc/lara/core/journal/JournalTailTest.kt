package io.github.domicc.lara.core.journal

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class JournalTailTest {

    private fun read(text: String): Pair<List<String>, Long> {
        val lines = ArrayList<String>()
        val consumed = JournalTail.read(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))) { lines += it }
        return lines to consumed
    }

    @Test
    fun `complete lines are handed over and counted`() {
        assertEquals(listOf("one", "two") to 8L, read("one\ntwo\n"))
    }

    @Test
    fun `a line that is still being written is left for the next call`() {
        val (lines, consumed) = read("one\ntw")
        assertEquals(listOf("one"), lines)
        assertEquals(4, consumed)
        // the next call starts behind what was consumed and now finds the rest
        assertEquals(listOf("two") to 4L, read("one\ntwo\n".substring(consumed.toInt())))
    }

    @Test
    fun `an empty file and a file without a line break yield nothing`() {
        assertEquals(emptyList<String>() to 0L, read(""))
        assertEquals(emptyList<String>() to 0L, read("half a line"))
    }

    @Test
    fun `empty lines are lines`() {
        assertEquals(listOf("", "a", "") to 4L, read("\na\n\n"))
    }

    @Test
    fun `bytes are counted, not characters`() {
        // "Hörgerät" has two characters of two bytes each
        val (lines, consumed) = read("Hörgerät\nnext")
        assertEquals(listOf("Hörgerät"), lines)
        assertEquals(11, consumed)
    }

    @Test
    fun `lines longer than one read are put together`() {
        val long = "x".repeat(200_000)
        val (lines, consumed) = read("$long\nshort\n$long")
        assertEquals(listOf(long, "short"), lines)
        assertEquals(200_001L + 6, consumed)
    }

    @Test
    fun `a stream that delivers a few bytes at a time gives the same result`() {
        val text = "first line\nsecond ä line\nunfinished"
        val trickle = object : ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)) {
            override fun read(b: ByteArray): Int = super.read(b, 0, 3)
        }
        val lines = ArrayList<String>()
        val consumed = JournalTail.read(trickle) { lines += it }
        assertEquals(listOf("first line", "second ä line"), lines)
        assertEquals(text.toByteArray(Charsets.UTF_8).size - "unfinished".length, consumed.toInt())
    }
}
