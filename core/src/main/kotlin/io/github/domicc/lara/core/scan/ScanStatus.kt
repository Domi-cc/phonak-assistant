package io.github.domicc.lara.core.scan

/**
 * What listens for the remote at this moment; attached to every journal line and shown in the
 * setup checklist.
 *
 * @param mode the mode the callback scan asked for; null while there is no callback scan
 * @param passive the scan type that scan really has
 * @param rescue the rescue scan is registered as well
 * @param off why there is no callback scan, if [mode] is null
 */
data class ScanStatus(
    val mode: ScanMode? = null,
    val passive: Boolean = true,
    val rescue: Boolean = false,
    val off: String? = SERVICE_NOT_RUNNING,
) {
    /** For the journal: `low_power/passive`, `balanced/active+rescue`, `off`, `off+rescue`. */
    val word: String
        get() = (mode?.let { it.word + "/" + if (passive) "passive" else "active" } ?: "off") +
            if (rescue) "+rescue" else ""

    companion object {
        const val SERVICE_NOT_RUNNING = "service_not_running"
        const val NO_SCAN_PERMISSION = "no_scan_permission"
        const val BLUETOOTH_OFF = "bluetooth_off"
        const val NOT_ENROLLED = "not_enrolled"
        const val SCAN_FAILED = "scan_failed"
        const val WAITING = "waiting_for_start_budget"
    }
}
