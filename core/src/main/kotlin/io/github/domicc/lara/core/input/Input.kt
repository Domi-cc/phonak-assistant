package io.github.domicc.lara.core.input

/**
 * Something the wearer did, whichever way it reached the phone. Every trigger is turned into
 * this one type so that the app can route, count and journal all of them the same way.
 *
 * @param elapsedMs when it happened, in milliseconds on the clock that keeps counting while the
 * phone sleeps (Android's elapsedRealtime), like [io.github.domicc.lara.core.journal.JournalRecord.elapsedMs]
 * @param button which button, where the source has several: 1 to 4 on the Shelly remote, the
 * Android key code for a media key; null otherwise
 * @param gesture how it was pressed, where the source can tell; null otherwise
 */
data class Input(
    val source: InputSource,
    val elapsedMs: Long,
    val button: Int? = null,
    val gesture: Gesture? = null,
)

/**
 * Where an [Input] came from.
 *
 * @param word the name used for this source in the journal and as session trigger
 */
enum class InputSource(val word: String) {
    /** Double tap on the hearing aid; arrives as a hands-free voice command. */
    HEARING_AID_TAP("tap"),

    /** A button of the Shelly remote; arrives as a Bluetooth advertisement. */
    SHELLY("shelly"),

    /** A media key of a headset or remote. */
    MEDIA_KEY("media_key"),

    /** A button on the app's own screen. */
    SCREEN("screen"),

    /** A command sent from a computer, for repeatable test runs. */
    ADB("adb"),

    /** Android's own gesture for the digital assistant. */
    ASSIST_GESTURE("assist"),
}

/**
 * How a button was pressed. The vocabulary is the one of the Shelly remote, which decides on
 * the gesture itself before it sends anything.
 */
enum class Gesture {
    PRESS,
    DOUBLE,
    TRIPLE,
    LONG,
    LONG_DOUBLE,
    LONG_TRIPLE,
    HOLD,
    HOLD_RELEASE,

    /** The button was used, but the remote named a gesture this app does not know. */
    UNKNOWN,
}
