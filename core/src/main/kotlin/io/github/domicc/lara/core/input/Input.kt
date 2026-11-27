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
 * @param path which way it took into the app, for a source that has more than one; null otherwise
 * @param missedBefore how many events of the same source were lost right before this one, as
 * far as the source can tell (the Shelly remote numbers its packets); 0 otherwise
 */
data class Input(
    val source: InputSource,
    val elapsedMs: Long,
    val button: Int? = null,
    val gesture: Gesture? = null,
    val path: InputPath? = null,
    val missedBefore: Int = 0,
) {
    /**
     * The one word that says where this input came from: `tap`, `ble_callback`, `ble_rescue`,
     * `screen`, `adb`, `simulated` ... It goes into every journal line the input causes and is
     * the trigger name of a session it starts.
     */
    val origin: String get() = path?.word ?: source.word
}

/**
 * The way an input took into the app, where the source alone does not say it. A press of the
 * Shelly remote can come from either of two Bluetooth scans, or from a test command that only
 * pretends there was one. Measurements must be able to tell these apart.
 *
 * @param word the name used in the journal
 */
enum class InputPath(val word: String) {
    /** The scan of the listener service. */
    BLE_CALLBACK("ble_callback"),

    /** The scan that delivers through a broadcast, also into a process that was just started. */
    BLE_RESCUE("ble_rescue"),

    /** No remote was involved: a test command made this input up. */
    SIMULATED("simulated"),
}

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
