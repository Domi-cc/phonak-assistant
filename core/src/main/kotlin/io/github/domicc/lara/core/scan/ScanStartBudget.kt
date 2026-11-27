package io.github.domicc.lara.core.scan

import kotlin.math.max

/**
 * Keeps the app below Android's limit for scan starts. Android counts per app: when the five
 * scans it stopped last all began within the past 30 seconds, the next start is dropped, and
 * the app is told nothing (Android 17 source: no error callback for "scanning too frequently").
 * Changing the scan mode means starting one scan and stopping another, so a few quick mode
 * changes reach that limit and would leave the app without a scan and without knowing it.
 *
 * The budget is one start lower than Android's as a margin. Times are milliseconds on any
 * clock that never runs backwards.
 */
class ScanStartBudget(private val maxStarts: Int = 4, private val windowMs: Long = 30_000) {

    /** The newest [maxStarts] starts, oldest first. */
    private val starts = ArrayDeque<Long>()

    /** How long a start has to wait at [nowMs]; 0 if it may happen now. Does not book it. */
    @Synchronized
    fun waitMs(nowMs: Long): Long {
        if (starts.size < maxStarts) return 0
        return max(0, starts.first() + windowMs - nowMs)
    }

    /** Books a start that happened at [nowMs], whether or not it was within the budget. */
    @Synchronized
    fun record(nowMs: Long) {
        starts.addLast(nowMs)
        while (starts.size > maxStarts) starts.removeFirst()
    }
}
