package io.github.domicc.lara.core.journal

/**
 * One line of the journal.
 *
 * Three clocks are stored on purpose: [wallMs] for humans, [elapsedMs] (counts while the phone
 * sleeps) and [uptimeMs] (does not). The difference between the last two tells whether the
 * phone was asleep between two events.
 *
 * @param type short event name such as `tap_intent` or `session_end`
 * @param fields the event-specific part
 * @param ctx device state at the moment of the event (screen, lock, Doze, battery, role ...)
 */
data class JournalRecord(
    val wallMs: Long,
    val elapsedMs: Long,
    val uptimeMs: Long,
    val type: String,
    val fields: Map<String, Any?> = emptyMap(),
    val ctx: Map<String, Any?> = emptyMap(),
)
