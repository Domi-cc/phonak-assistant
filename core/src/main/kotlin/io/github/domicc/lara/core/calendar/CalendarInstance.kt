package io.github.domicc.lara.core.calendar

/**
 * What identifies one occurrence of an appointment: the event and the moment it begins. The
 * app remembers the keys it has announced, so that no occurrence is said twice. An
 * appointment that is moved gets another key and is announced again for its new time; every
 * occurrence of a recurring appointment has the same event id and a key of its own.
 *
 * One case does get a second announcement: a single occurrence of a series that is changed
 * after its reminder (another room, a link added). Android's calendar storage keeps such an
 * occurrence as an event of its own with a new id, so to the app it is a new appointment at
 * that time. The same goes for a calendar whose synchronisation deletes a changed
 * appointment and writes it anew.
 */
data class InstanceKey(val eventId: Long, val beginMs: Long) {

    /** The form in which a key is kept between starts. */
    fun encode(): String = "$eventId$SEPARATOR$beginMs"

    companion object {
        private const val SEPARATOR = '@'

        /** Reads what [encode] wrote; null for anything else. */
        fun decode(text: String): InstanceKey? {
            val parts = text.split(SEPARATOR)
            if (parts.size != 2) return null
            return InstanceKey(parts[0].toLongOrNull() ?: return null, parts[1].toLongOrNull() ?: return null)
        }
    }
}

/**
 * One occurrence of an appointment as the phone's calendar storage lists it. A recurring
 * appointment appears once per occurrence.
 *
 * The title is somebody's private business. It may be spoken into the hearing aid and shown on
 * the phone's own screen and goes nowhere else: [toString] leaves it out, so that an instance
 * that ends up in a log line or in the text of an exception says nothing about the appointment.
 *
 * @param beginMs wall-clock time in milliseconds since 1970
 * @param cancelled the appointment is marked as cancelled
 * @param declined the owner was invited and said no
 * @param title as stored; null or blank if the appointment has none
 */
data class CalendarInstance(
    val eventId: Long,
    val calendarId: Long,
    val beginMs: Long,
    val allDay: Boolean,
    val cancelled: Boolean,
    val declined: Boolean,
    val title: String?,
) {
    val key: InstanceKey get() = InstanceKey(eventId, beginMs)

    override fun toString(): String =
        "CalendarInstance(eventId=$eventId, calendarId=$calendarId, beginMs=$beginMs, allDay=$allDay, " +
            "cancelled=$cancelled, declined=$declined, titleLength=${title?.length})"
}
