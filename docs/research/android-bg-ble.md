# Receiving one BLE advertisement burst on a locked, screen-off Pixel 10 Pro

Research date: 2026-11-27. Scope: Android 16 (API 36) and Android 17 (API 37), Pixel 10 Pro, sideloaded app.
Status legend: **[VERIFIED]** seen in a primary source (URL / file / constant given), **[INFERRED]** reasoned from
verified sources, **[UNKNOWN]** not established, needs a device test.

Nothing in this report was run on a device. Every catch-probability figure is a model estimate.

## 0. Sources and how they were read

| What | Where | Revision |
|---|---|---|
| AOSP Bluetooth module, Android 17 | `https://android.googlesource.com/platform/packages/modules/Bluetooth` branch `android17-release` | `c77db469de80f86660bc053bec4dee0c5d4b947c` (2026-05-15, "26Q2-release") |
| AOSP Bluetooth module, Android 16 QPR2 | branch `android16-qpr2-release` | `5323f31677c7dfa04de2e6e9fce1012ef4edaff2` (2025-10-30) |
| AOSP Bluetooth module, Android 16 | branch `android16-release` | `4b73ee6039271ffbf71ebdc8c109fc98eac8e137` (2025-04-09) |
| frameworks/base (ActiveServices, PendingIntentRecord, PowerManagerService, DeviceIdleController, CDM, VoiceInteraction) | `https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/...` | branch head on 2026-11-27 |
| ConfigInfrastructure (shell-writable DeviceConfig allowlist) | `https://android.googlesource.com/platform/packages/modules/ConfigInfrastructure/+/refs/heads/android17-release/framework/java/android/provider/WritableFlags.java` | branch head |
| developer.android.com / source.android.com pages | fetched as `.md.txt` on 2026-11-27 | - |
| Shelly BLE docs | `https://shelly-api-docs.shelly.cloud/docs-ble/...` | fetched 2026-11-27 |
| SaraButton | `https://github.com/OferTiber/SaraButton` (MIT) | `48975ae2464f3a8903c09c00d44b014cb6e3c5a2` (2026-08-07) |

Local sparse clones (read only): `<repos>/aosp-bluetooth-a17`,
`.../repos/aosp-bluetooth-android16-qpr2-release`, `.../repos/aosp-bluetooth-android16-release`, `.../repos/SaraButton`.
Fetched pages: `<research>/pages/`.

Caveats that apply to everything below:

* **[VERIFIED]** The public AOSP `main` branch of the Bluetooth module is stale (head dated 2025-03-17). The newest public
  code is `android17-release` (May 2026). No `android17-qpr1/qpr2` branch or tag exists in the public repo
  (`git ls-remote`: only `android17-release`, `android17-security-release`, tag `android-17.0.0_r1`).
* **[VERIFIED]** Bluetooth is a Mainline module (APEX `com.google.android.bt`, since Android 13) -
  `https://source.android.com/docs/core/ota/modular-system`. Google can therefore change LE-scan behaviour through a
  Google Play system update without an OS update. The code on the phone can be newer than anything quoted here.
* **[UNKNOWN]** The values of aconfig flags on the production Pixel build (in particular
  `scan_allowance_throttling_enabled`). AOSP only declares the flags; the release configuration is not in this repo.

## 0.1 Which Android version does a Pixel 10 Pro run today?

* **[VERIFIED]** Android 17 is released: AOSP tag `android-17.0.0_r1` and branch `android17-release` exist; the
  Android Developers Blog post "Android 17" is dated June 2026 (`https://android-developers.googleblog.com/2026/06/Android-17.html`);
  press reports give 2026-06-16 for the stable Pixel rollout (`https://9to5google.com/2026/06/16/google-android-17-pixel-launch/`).
* **[VERIFIED, secondary source]** Android 17 QPR1 shipped with the September 2026 Pixel Drop; Android 17 QPR2 started
  rolling out on 2026-11-10. 9to5Google lists "Pixel 10 Pro: CP4A.261105.040.A1"
  (`https://9to5google.com/2026/11/10/android-17-qpr2-november-pixel-update/`). The rollout is phased.
* **[INFERRED]** An up-to-date Pixel 10 Pro on 2026-11-27 runs **Android 17 QPR2 (API level 37, build CP4A.261105.040.A1)**,
  or Android 17 QPR1 if the phased update has not arrived. It shipped with Android 16 in August 2025.
  Confirm with `adb shell getprop ro.build.fingerprint` and `adb shell getprop ro.build.version.sdk_full` (or `.sdk`).
* Consequence: the Android 17 code path is the relevant one; Android 16 is quoted only for comparison.

---

## 1. What the LE scanner really does with the screen off

All file paths in this section are under
`android/app/src/com/android/bluetooth/le_scan/` of the `android17-release` branch unless stated otherwise.

### 1.1 Window / interval per scan mode (the actual constants) [VERIFIED]

`ScanUtil.kt` lines 50-69:

```kotlin
const val DEFAULT_SCAN_QUOTA_COUNT = 5
@JvmField val DEFAULT_SCAN_QUOTA_WINDOW = 30.seconds.toJavaDuration()
@JvmField val DEFAULT_SCAN_TIMEOUT = 10.minutes.toJavaDuration()
@JvmField val DEFAULT_SCAN_UPGRADE_DURATION = 6.seconds.toJavaDuration()
@JvmField val DEFAULT_SCAN_DOWNGRADE_DURATION_BT_CONNECTING = 6.seconds.toJavaDuration()
val DEFAULT_SCAN_THROTTLE_DELAY = 2.seconds

const val SCAN_MODE_LOW_POWER_WINDOW_MS = 140
const val SCAN_MODE_LOW_POWER_INTERVAL_MS = 1400
const val SCAN_MODE_BALANCED_WINDOW_MS = 183
const val SCAN_MODE_BALANCED_INTERVAL_MS = 730
const val SCAN_MODE_LOW_LATENCY_WINDOW_MS = 100
const val SCAN_MODE_LOW_LATENCY_INTERVAL_MS = 100

@JvmField val SCAN_MODE_SCREEN_OFF_LOW_POWER_WINDOW = 512.milliseconds.toJavaDuration()
@JvmField val SCAN_MODE_SCREEN_OFF_LOW_POWER_INTERVAL = 10240.milliseconds.toJavaDuration()
@JvmField val SCAN_MODE_SCREEN_OFF_BALANCED_WINDOW = 183.milliseconds.toJavaDuration()
@JvmField val SCAN_MODE_SCREEN_OFF_BALANCED_INTERVAL = 730.milliseconds.toJavaDuration()
```

| Mode actually programmed into the controller | Window | Interval | Duty | Public API? |
|---|---|---|---|---|
| `SCAN_MODE_LOW_LATENCY` (2) | 100 ms | 100 ms | 100 % | yes |
| `SCAN_MODE_BALANCED` (1) / `AMBIENT_DISCOVERY` (3) | 183 ms | 730 ms | 25 % | yes / system |
| `SCAN_MODE_LOW_POWER` (0) | 140 ms | 1400 ms | 10 % | yes |
| `SCAN_MODE_SCREEN_OFF_BALANCED` (5) | 183 ms | 730 ms | 25 % | `@Hide`, framework internal |
| `SCAN_MODE_SCREEN_OFF` (4) | 512 ms | 10240 ms | 5 % | `@Hide`, framework internal |
| `SCAN_MODE_OPPORTUNISTIC` (-1) | never drives the radio | - | 0 | yes |

The same six numbers and the same four screen-off numbers are in `android16-release` (`ScanManager.java` lines 83-95)
and `android16-qpr2-release` (`ScanUtil.kt` lines 38-41). **[VERIFIED]**

Where the numbers can be overridden **[VERIFIED]**: the three normal modes read `Settings.Global.BLE_SCAN_*_WINDOW_MS / _INTERVAL_MS`
(`ScanUtil.windowMillis/intervalMillis`); the two screen-off modes read DeviceConfig namespace `bluetooth`, keys
`screen_off_low_power_window_millis`, `screen_off_low_power_interval_millis`, `screen_off_balanced_window_millis`,
`screen_off_balanced_interval_millis`; timeout and quota read `scan_timeout_millis`, `scan_quota_count`,
`scan_quota_window_millis`, `scan_upgrade_duration_millis`, `scan_downgrade_duration_millis`
(`btservice/AdapterService.java` lines 4952-5035).
**[VERIFIED]** On user builds adb can only write allowlisted DeviceConfig flags; for namespace `bluetooth` the allowlist
contains `scan_quota_count`, `report_delay`, `location_denylist_*`, logging and a few others, but **not**
`scan_timeout_millis` and **not** the `screen_off_*` keys (`WritableFlags.java` lines 202-219; `bluetooth` is not in
`WritableNamespaces.java`). **[INFERRED]** So without root the screen-off parameters and the timeout cannot be changed
from adb. `adb shell settings put global ble_scan_low_latency_window_ms ...` should still work but does not touch the
screen-off modes. Device test in section 7.

One set of parameters drives the radio for all apps: `ScanManager.configureRegularScanParams()` (line 955) takes the
most aggressive mode among all regular clients (`ScanUtil.getAggressiveClient`, priority order
OPPORTUNISTIC 0 < SCREEN_OFF 1 < LOW_POWER 2 < SCREEN_OFF_BALANCED 3 < BALANCED 4 < LOW_LATENCY 5) and calls
`setScanParameters`. Filters only decide which reports are delivered to whom. **[VERIFIED]**
Corollary **[INFERRED]**: any client of ours that is allowed to stay in LOW_LATENCY sets the duty cycle for all our other
clients too. Another app scanning aggressively (Play services Fast Pair / Find Hub) can also raise it, but that is luck.

### 1.2 Which scans are paused at screen-off, which continue [VERIFIED]

"Screen on" for the Bluetooth stack is `displayManager.displays.any { it.state == Display.STATE_ON }`
(`btservice/DisplayListener.kt` line 62). Always-on display (`STATE_DOZE`) counts as off. Lock state plays no role.

`ScanManager.handleScreenOff()` (line 580) does three things:

1. `handleSuspendScans()`: every regular client for which `requiresScreenOn(client)` is true is stopped and parked in
   `mSuspendedScanClients` until the screen turns on. `ScanUtil.kt` line 326:
   ```kotlin
   fun requiresScreenOn(client: ScanClient) = !isOpportunisticScanClient(client) && !client.hasNonEmptyFilters
   ```
   and `ScanClient.kt`: `val hasNonEmptyFilters = filters.any { !it.isAllFieldsEmpty }`,
   `ScanFilter.isAllFieldsEmpty() = EMPTY.equals(this)`.
   So: **unfiltered scans, and scans whose filters are all empty `ScanFilter.Builder().build()` objects, are paused.**
   `startScan()` applies the same test (line 377, log text "Cannot start unfiltered scan in screen-off.").
2. `updateRegularScanClientsScreenOff()`: the remaining (filtered) clients are re-moded by `ScanThrottler`, see 1.4.
3. `updateRegularScanToBatchScanClients()`: only clients that opted in with
   `CALLBACK_TYPE_ALL_MATCHES_AUTO_BATCH` are converted to batch scans (report delay >= 10 min). Ordinary
   `ALL_MATCHES` and `FIRST_MATCH` clients stay regular, immediate-delivery scans.

Also paused: unfiltered scans without the `neverForLocation` flag while Location is off (`requiresLocationOn`).

A laptop-only path can stop all LE scans on system suspend: `ScanSuspendManager` + `btservice/AdapterSuspend.java`,
enabled only if flag `adapter_suspend_mgmt` is on **and** sysprop `bluetooth.power.suspend.stop_le_scan.enabled` is
true (default `false`, `AdapterService.java` lines 1104-1128; the trigger is lid/device-state driven). **[VERIFIED]**
**[UNKNOWN]** whether the Pixel sets the property - check `adb shell getprop | grep bluetooth.power.suspend` (expected: empty).

### 1.3 Which ScanFilter kinds count as a "real" filter [VERIFIED]

For the screen-off rule any filter with at least one field set counts: device address, device name, service UUID,
service data UUID (with or without data), manufacturer data, advertising-data-type. All are "non-empty".

For hardware offload (`ScanFilterQueue.addScanFilter`, lines 187-270) these become controller (APCF) entries:
`TYPE_DEVICE_ADDRESS 0`, `TYPE_SERVICE_UUID 2`, `TYPE_SOLICIT_UUID 3`, `TYPE_LOCAL_NAME 4`, `TYPE_MANUFACTURER_DATA 5`,
`TYPE_SERVICE_DATA 6`, `TYPE_TRANSPORT_DISCOVERY_DATA 7`, `TYPE_ADVERTISING_DATA_TYPE 8`. Details that matter for us:

* Service data is offloaded only if the data array is non-null:
  `if (filter.getServiceDataUuid() != null && filter.getServiceData() != null)` (line 247). The offloaded pattern is
  the UUID bytes followed by the data with an all-ones mask on the UUID part. For BTHome use
  `setServiceData(ParcelUuid("0000fcd2-0000-1000-8000-00805f9b34fb"), byteArrayOf())` - a non-null, empty array gives the
  pattern `D2 FC`. With `null` data the filter still keeps the scan alive at screen-off, but nothing is offloaded:
  the feature selection is 0, which equals `ALL_PASS_FILTER_SELECTION`, so the controller reports every advert in
  range and the match happens in the host (`ScanController.matchesFilters`). **[VERIFIED code, INFERRED consequence]**
* Device address: the public `setDeviceAddress(String)` builder (type PUBLIC, no IRK) needs no privileged permission
  (`ScanBinder.enforcePrivilegedPermissionIfNeeded`, lines 247-264). In the native layer the APCF address type is
  forced to "not applicable" so public and random-static addresses both match
  (`system/gd/hci/le_scanning_manager_impl.cc` lines 1038-1058, comment "Addresses type not applicable (0x02) must be specified").
* Offload is used only when the controller reports >= 10 filters:
  `isOffloadedFilteringSupported = numOfOffloadedScanFilterSupported >= MIN_OFFLOADED_FILTERS (10)`.
  Indices 0-3 are reserved (`initFilterIndexStack`). If a client asks for more filters than are free it silently
  falls back to an all-pass controller filter (`shouldUseAllPassFilter`).
* All fields inside one `ScanFilter` are ANDed (`LIST_LOGIC_TYPE = 0x1111111`, `FILTER_LOGIC_TYPE = 1`); separate filters are ORed.

**[UNKNOWN]** `isOffloadedFilteringSupported()` / `isOffloadedScanBatchingSupported()` and the tracked-advertiser count
on the Pixel 10 Pro. The Bluetooth controller part of the Pixel 10 Pro could not be identified from primary sources.

### 1.4 Mode throttling: foreground vs background app, screen on vs off [VERIFIED]

"Foreground" for the scanner means **uid importance <= `IMPORTANCE_FOREGROUND_SERVICE`** (`ScanManager.java` lines 94,
726-731; importance is fetched with `ActivityManager.getPackageImportance` at scan start and tracked with
`addOnUidImportanceListener(..., IMPORTANCE_FOREGROUND_SERVICE)`). An app with a running foreground service is
"foreground"; a cached or dead app is not.

`ScanThrottler.throttleScanMode()` (lines 68-103):

```kotlin
// background throttling
if (!scanManager.isAppForeground(client) || isForceDowngradedScanClient(client)) {
    val limitMode = if (!isScreenOn) SCAN_MODE_SCREEN_OFF else SCAN_MODE_LOW_POWER
    targetScanMode = minScanMode(limitMode, targetScanMode)
}
// screen off throttling
else if (!isScreenOn) {
    targetScanMode = when (targetScanMode) {
        SCAN_MODE_LOW_POWER -> SCAN_MODE_SCREEN_OFF
        SCAN_MODE_BALANCED, SCAN_MODE_AMBIENT_DISCOVERY -> SCAN_MODE_SCREEN_OFF_BALANCED
        SCAN_MODE_LOW_LATENCY -> SCAN_MODE_LOW_LATENCY
        else -> return false
    }
}
```

Resulting table (filtered scans only; unfiltered ones are paused):

| App state | Requested | Screen ON | Screen OFF |
|---|---|---|---|
| foreground UI or foreground service | LOW_LATENCY | 100/100 | **100/100** |
| foreground UI or foreground service | BALANCED | 183/730 | 183/730 (`SCREEN_OFF_BALANCED`) |
| foreground UI or foreground service | LOW_POWER | 140/1400 | **512/10240** (`SCREEN_OFF`) |
| background (no FGS, cached, dead; PendingIntent scan) | anything | at most 140/1400 | **512/10240** |
| any client flagged "timed out" or "downgraded" | anything | at most 140/1400 | 512/10240 |

The Android 16 QPR2 code (`ScanManager.updateScanModeScreenOff`, lines 905-934) implements the same table. **[VERIFIED]**

Other automatic changes **[VERIFIED]**:

* **10-minute scan timeout.** `configureTimeout()` posts a runnable after `getScanTimeout()` (10 min). On expiry
  (`handleRegularScanTimeout`, line 1221): an unfiltered scan becomes OPPORTUNISTIC; a filtered scan is capped at
  LOW_POWER (`SCAN_MODE_FORCE_DOWNGRADED`) and flagged. A flagged client is treated like a background client afterwards,
  i.e. 512/10240 from the next screen-off evaluation on. **Exempt: OPPORTUNISTIC and `CALLBACK_TYPE_FIRST_MATCH` clients**
  (`isExemptFromScanTimeout`, `ScanUtil.kt` line 343). Stopping and starting the scan creates a fresh record and clears the flag.
  The timer is a `Handler.postDelayed`, which runs on `SystemClock.uptimeMillis()`; that clock stops in deep sleep
  (`https://developer.android.com/reference/android/os/SystemClock`). **[INFERRED]** The 10 minutes are therefore 10
  minutes of *awake* time of the phone, which can be hours of wall-clock time in a pocket - but it will have expired
  "after hours of idle".
* **Start quota.** `isScanningTooFrequently()`: the 6th `startScan(ScanCallback)` within 30 s fails with
  `SCAN_FAILED_SCANNING_TOO_FREQUENTLY`. The check is only in `registerAndStartScan` (callback scans), not in
  `registerPiAndStartScan` (`ScanController.java` lines 866-884 vs 1015-1068).
* **Connecting downgrade.** While any audio profile is connecting, LOW_LATENCY clients are capped at BALANCED for 6 s
  (`handleConnectingState`, `downgradeScanModeFromMaxDuty`).
* **Start upgrade.** A new scan of a foreground app is raised one level for 6 s; with flag
  `upgrade_le_scan_only_screen_on` only while the screen is on.
* **Android 17 only: scan-allowance throttling behind flag `scan_allowance_throttling_enabled`**
  (`flags/le_scanning.aconfig`, bug 478996075; `ScanThrottler.applyAllowanceThrottling`, line 273). If the flag is on,
  every app that is neither privileged nor foreground-**UI** (`IMPORTANCE_FOREGROUND`) gets an allowance of
  `DEFAULT_SCAN_ALLOWANCE_SECONDS = 6 min` of LOW_LATENCY-equivalent scanning, weighted
  (`WEIGHT_LOW_LATENCY 100`, `WEIGHT_BALANCED 25`, `WEIGHT_LOW_POWER 10`, `WEIGHT_SCREEN_OFF_LOW_POWER 5`), refilled after
  `ALLOWANCE_REFILL_WINDOW = 1 h`. When spent the client is forced to `SCAN_MODE_SCREEN_OFF`. With the flag on the
  10-minute timeout is skipped. A foreground *service* does not exempt the app; only visible UI or
  `BLUETOOTH_PRIVILEGED` does. In the published code the usage is only booked when the delayed job fires and the job is
  cancelled by `stopScan` (`removeRecordUsageRunnable`), and the code carries `TODO(b/478349128)` markers - it looks
  unfinished. **[UNKNOWN]** whether the flag is enabled on the Pixel build; this is the single biggest unknown for
  Android 17. Sysprop override of the allowance: `bluetooth.ble.scan.scan_allowance_seconds.config`.

### 1.5 Active vs passive scan type (new, matters for scannable advertisers) [VERIFIED]

`ScanSettings.Builder.setScanType(int)` exists since SDK **36.1** (Android 16 QPR2)
(`https://developer.android.com/reference/android/bluetooth/le/ScanSettings.Builder#setScanType(int)`: "Added in version 36.1").
The default is `SCAN_TYPE_ACTIVE` = "scan results are delivered upon scan responses arrive". `ScanController.java`
lines 373-385 (Android 17):

```java
boolean isScanResponse = (eventType & ET_SCAN_RESPONSE_MASK) != 0;
boolean requiresScanResponse = (eventType & ET_SCANNABLE_MASK) != 0 && !isScanResponse && !mIsMsftAdvMonitorEnabled;
if ((settings.getScanType() == ScanSettings.SCAN_TYPE_ACTIVE && requiresScanResponse)
        || (settings.getScanType() == ScanSettings.SCAN_TYPE_PASSIVE && isScanResponse)) {
    scanTypeMismatch.add(client);
    continue;
}
```

So with default settings a *scannable* advertisement (ADV_IND / ADV_SCAN_IND) is delivered to an `ALL_MATCHES` client
only after the SCAN_REQ / SCAN_RSP exchange succeeded. With `SCAN_TYPE_PASSIVE` the advertisement is delivered at once.
Shelly documents "Manufacturer data in scan response" for BLU devices
(`https://shelly-api-docs.shelly.cloud/docs-ble/common`), so BLU adverts are probably scannable.
**[UNKNOWN]** the PDU type of the button-press adverts of the RC Button 4 ZB. **Recommendation: always set
`SCAN_TYPE_PASSIVE` when `Build.VERSION.SDK_INT_FULL >= 36.1`.**

---

## 2. Hardware-offloaded filtering, FIRST_MATCH, match modes, batching - do they change the odds?

Short answer **[INFERRED from verified code]**: none of them makes the radio listen more. The listen schedule is only the
window/interval of section 1. Offloaded filtering decides whether the application processor is woken; it is what makes
a screen-off scan cheap, not what makes it catch more.

* **Offloaded filter (APCF)** **[VERIFIED]**: the vendor command set is specified in
  `https://source.android.com/docs/core/connect/bluetooth/hci_requirements` ("Advertising Packet Content Filter").
  With a narrow filter (our MAC + service data FCD2) the controller raises an HCI event only for our button, the
  phone can stay suspended in between. **[INFERRED]** The controller keeps scanning while the application processor is
  suspended and wakes the host for a matching report; this is the design intent but was not proven for the Pixel 10.
* **`CALLBACK_TYPE_FIRST_MATCH` / `MATCH_LOST`** **[VERIFIED]**: delivery mode `DELIVERY_MODE_ON_FOUND_LOST`
  (`ScanUtil.deliveryMode`). Parameters sent to the controller (`configureFilterParameter`, `ScanUtil.kt` lines 78-84,
  `ScanManager.java` line 1422): on-found timeout `500 ms` for `MATCH_MODE_AGGRESSIVE` (x3 = 1500 ms for
  `MATCH_MODE_STICKY`), on-found sightings `1` (aggressive) / `4` (sticky), on-lost timeout fixed `10000 ms`,
  tracking entries 1 / 2 / max/4 for `MATCH_NUM_ONE/FEW/MAX_ADVERTISEMENT`. Spec wording: "onfound_timeout: Time for
  firmware to linger and collect additional advertisements before reporting"; "If the count exceeds
  onfound_timeout_cnt, it's reported OnFound immediately thereafter"; "If an advertisement, after being found, is not
  seen contiguously for the lost_timeout period, it will immediately be reported lost."
  Consequences: (a) FIRST_MATCH can need more than one sighting inside 500 ms, so on a sparse scan it is slightly
  *worse* than ALL_MATCHES; use AGGRESSIVE, never STICKY. (b) A second press within 10 s produces no new FIRST_MATCH -
  useless for menu navigation, fine as a wake-up. (c) The FIRST_MATCH result carries the advert payload of the sighting
  (`ScanController.onTrackAdvFoundLost` builds the `ScanResult` from `trackingInfo.getResult()`), if the controller
  supplies it. (d) FIRST_MATCH clients are exempt from the 10-minute timeout, which indirectly *raises* the odds
  because they can hold the radio in LOW_LATENCY indefinitely (subject to 1.4). (e) FIRST_MATCH and report delay need
  offload support: `isScanSupported()` returns false otherwise and `startScan` only logs "Scan settings not supported" -
  no error callback.
* **Batching (`setReportDelay > 0`)** **[VERIFIED]**: results are stored in the controller and fetched on an alarm.
  Floor `DEFAULT_REPORT_DELAY_FLOOR_MS = 5000`; with the screen off the fetch interval floor is
  `SCREEN_OFF_MINIMUM_DELAY_FLOOR_DEFAULT = 20000 ms` multiplied by back-off `{1, 1, 2, 2, 4}` (up to 80 s), starting
  60 s after screen-off (`BatchScanThrottler.kt`). Batch scans also run at a lower duty: LOW_LATENCY is mapped to
  the balanced parameters, everything else to low-power, auto-batch to 512/10240 (`BatchScanUtil.kt`). Batching adds
  5-80 s latency and never improves the catch rate. Do not use it for a remote.
* **`CALLBACK_TYPE_ALL_MATCHES_AUTO_BATCH`**: opt-in only, report delay >= 10 min. Irrelevant.

---

## 3. PendingIntent scans: what survives what

Mechanism **[VERIFIED]**: `registerPiAndStartScan` stores the `PendingIntent` in the in-memory `ScannerMap` of the
Bluetooth process. Results are sent with `pendingIntent.send(mAdapterService, 0, extrasIntent)`
(`ScanController.java` lines 506-521) with extras `EXTRA_LIST_SCAN_RESULT`, `EXTRA_CALLBACK_TYPE`, or `EXTRA_ERROR_CODE`.
API doc: "Use this method of scanning if your process is not always running and it should be started when scan results
are available." Background guide: "If the app process is not running ... Call startScan() with a PendingIntent ... to
get notified when a device matching your filter is scanned"
(`https://developer.android.com/develop/connectivity/bluetooth/ble/background`).

| Situation | Delivered? | Evidence | Re-register? |
|---|---|---|---|
| App process dead (LMK, `am kill`, swipe-away) | Yes, the broadcast starts the process | **[VERIFIED]** doc + code path; registration lives in the Bluetooth process | no |
| Doze (light or deep) | Yes in principle: nothing in the scan path depends on Doze; an explicit PendingIntent broadcast is not deferred | **[INFERRED]** | no |
| ... but at which duty cycle | 512/10240 unless the app is "foreground" (FGS) | **[VERIFIED]** 1.4 | - |
| Bluetooth off then on | No. `ScanController.cleanup()` runs `mScannerMap.clear()` (line 219) | **[VERIFIED]** | **yes**, on `BluetoothAdapter.ACTION_STATE_CHANGED -> STATE_ON` |
| Bluetooth process crash / module update | No (same in-memory map) | **[INFERRED]** | yes |
| Reboot | No; nothing is persisted | **[INFERRED]** (no persistence code in `ScannerMap.kt` / `ScanController.java`) | **yes**, in `BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED` |
| Force-stop | No. Force-stop cancels the app's PendingIntents (`ActivityManagerService.forceStopPackageLocked`: `removePendingIntentsForPackage(..., CANCEL_REASON_OWNER_FORCE_STOPPED)` when `packageStateStopped`); the scanner listens with `PendingIntent.addCancelListener` and stops the scan (`mScanIntentCancelListener`, line 101) | **[VERIFIED]** | yes, but only after the user (or adb) starts the app again; a stopped app receives no broadcasts |
| App update (`adb install -r`) | **[UNKNOWN]** whether the PendingIntent survives | - | re-register in `MY_PACKAGE_REPLACED` |
| BLUETOOTH_SCAN revoked | No | - | after re-grant |

Notes:

* Re-registering the same PendingIntent is harmless: "Ignoring since the same PI scan is already in ScannerMap"
  (`registerPiAndStartScan`). A periodic idempotent `startScan(pi)` is therefore a valid keep-alive. **[VERIFIED]**
* `BluetoothAdapter.ACTION_STATE_CHANGED` is **not** on the implicit-broadcast exception list
  (`https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions`; listed are
  `BluetoothDevice.ACTION_ACL_CONNECTED/DISCONNECTED`, `BluetoothHeadset/BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED`,
  `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`). **[VERIFIED]** A manifest receiver cannot see Bluetooth being toggled; a
  live process (the foreground service) must register it at runtime.
* **The scan PendingIntent does not grant the right to start a foreground service.** The Bluetooth module attaches a
  temporary allowlist (`REASON_BLUETOOTH_BROADCAST`, `TEMPORARY_ALLOW_LIST_TYPE_FOREGROUND_SERVICE_ALLOWED`,
  `DEFAULT_TEMP_ALLOW_LIST_DURATION_MS = 20_000`, `Util.kt` lines 69-70, 526-547) only to broadcasts sent with
  `getTempBroadcastOptions()`: ACL connect/disconnect, bond state, headset / A2DP / hearing-aid / LE-audio / HID
  connection state. Scan results are sent without options, and `PendingIntentRecord.sendInner` only allowlists when a
  duration was registered for that PendingIntent. **[VERIFIED code]** The docs agree: "In versions lower than Android
  12 you can start a foreground service directly from the PendingIntent object." The receiver therefore needs another
  exemption (section 4) or must work inside the broadcast's own time budget.
* Location: declare `BLUETOOTH_SCAN` with `android:usesPermissionFlags="neverForLocation"`. Then no location permission
  and no Location toggle are needed. Results that look like iBeacon / Eddystone are dropped for such apps (default
  denylist `"⊈0016AAFE40/00FFFFFFF0,⊆0016AAFE/00FFFFFF,⊆00FF4C0002/00FFFFFFFF"`, `AdapterService.java`); BTHome service
  data `16 D2 FC ..` does not match it. **[VERIFIED]**
* A `ScanCallback` (binder) scan needs a live, unfrozen process: when the process dies the death recipient stops the
  scan (`handleDeadScanClient`). **[VERIFIED]** **[INFERRED]** A cached process is frozen by the platform and receives
  callbacks late; callback scans belong inside the foreground service.

---

## 4. Foreground services

### 4.1 Type `connectedDevice` on API 34+ [VERIFIED]

`https://developer.android.com/develop/background-work/services/fgs/service-types` ("Connected device"):

* manifest: `android:foregroundServiceType="connectedDevice"`, permission `FOREGROUND_SERVICE_CONNECTED_DEVICE`
  (plus `FOREGROUND_SERVICE`), constant `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE`;
* runtime prerequisite, "At least one of the following conditions must be true": declare one of `CHANGE_NETWORK_STATE`,
  `CHANGE_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`, `NFC`, `TRANSMIT_IR`; **or** "Request and be granted at least one
  of the following runtime permissions: `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_SCAN`, `UWB_RANGING`";
  **or** call `UsbManager.requestPermission()`.
* No time limit is documented for this type (the 6 h / 24 h limit applies to `dataSync` and `mediaProcessing`; `shortService` is ~3 min).
* The page itself suggests the companion presence API and "the Bluetooth scan API" as alternatives; that is advice, not a rule.

### 4.2 Starting a foreground service from the background [VERIFIED]

Rule since Android 12 (target 31+): not allowed, `ForegroundServiceStartNotAllowedException`, except for the list at
`https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start`. Entries relevant here, quoted:

* "After the device reboots and receives the ACTION_BOOT_COMPLETED, ACTION_LOCKED_BOOT_COMPLETED, or
  ACTION_MY_PACKAGE_REPLACED intent action in a broadcast receiver."
* "Your app uses the Companion Device Manager and declares the REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND
  permission or the REQUEST_COMPANION_RUN_IN_BACKGROUND permission. Whenever possible, use
  REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND."
* "The user turns off battery optimizations for your app."
* "Your app holds the SYSTEM_ALERT_WINDOW permission. Note: If your app targets Android 15 or higher, it must have the
  SYSTEM_ALERT_WINDOW permission and the app must currently have a visible overlay window."
* "The user performs an action on a UI element related to your app" (notification, widget, bubble, activity).
* "Your app invokes an exact alarm to complete an action that the user requests."
* high-priority FCM, geofencing, current input method, device/profile owner, time/locale changes, NFC transaction.

The list as fetched on 2026-11-27 contains **no "Bluetooth broadcast" entry**. The mechanism still exists in code
(section 3: 20 s allowlist on connection-type Bluetooth broadcasts) but it is not a documented promise any more, and
it never covered LE scan results. **[VERIFIED]**

The same list in code, `ActiveServices.shouldAllowFgsStartForegroundNoBindingCheckLocked` (android17-release, lines
9503-9660), in order: uid at TOP; a process of the uid at `PROCESS_STATE_BOUND_TOP` or better, or at
`BOUND_FOREGROUND_SERVICE` or better when the uid holds capability `PROCESS_CAPABILITY_BFSL`; activity became invisible
less than `DEFAULT_FG_TO_BG_FGS_GRACE_DURATION = 5 s` ago; `START_FOREGROUND_SERVICES_FROM_BACKGROUND` permission;
background-start privileges on the caller; `SYSTEM_ALERT_WINDOW` (+ visible overlay for target 35);
**companion app with one of the two companion permissions (`REASON_COMPANION_DEVICE_MANAGER`)**;
**`isAllowlistedForFgsStartLOSP(uid)` = app id in the device-idle "except idle" allowlist (battery-optimisation
exemption, `REASON_SYSTEM_ALLOW_LISTED`) or in the temporary FGS allowlist**; demo mode; profile owner;
`OP_SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS`; active VPN; current input method.
(`ActivityManagerService.isAllowlistedForFgsStartLOSP`, line 7215.) **[VERIFIED]**

### 4.3 BOOT_COMPLETED per Android version [VERIFIED]

* Android 14: `microphone` cannot be launched from `BOOT_COMPLETED`; types and runtime prerequisites are mandatory;
  while-in-use types (camera, location, microphone, body sensors) cannot be *created* while the app is in the background.
* Android 15 (apps targeting 35+): "`BOOT_COMPLETED` receivers are not allowed to launch the following types of
  foreground services: dataSync, camera, mediaPlayback, phoneCall, mediaProjection, microphone"
  (`https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed`).
* Code, Android 17: `ActivityManagerConstants.DEFAULT_FGS_BOOT_COMPLETED_ALLOWLIST = CONNECTED_DEVICE | HEALTH |
  REMOTE_MESSAGING | SYSTEM_EXEMPTED | SPECIAL_USE | LOCATION`; `ActiveServices.shouldAllowBootCompletedStart` throws
  "FGS type ... not allowed to start from BOOT_COMPLETED!" for anything else. **`connectedDevice` may be started from
  `BOOT_COMPLETED` on Android 14, 15, 16 and 17.**
* Android 16: no new foreground-service start rule on the behaviour-change pages (only: jobs running next to an FGS now
  count against job quota). Android 17: no new start rule either, but see 4.4.
* Test switch: `adb shell am compat enable FGS_BOOT_COMPLETED_RESTRICTIONS <pkg>`;
  `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED <pkg>`.

### 4.4 While-in-use capability and Android 17 "background audio hardening" (cross-cutting, decisive) [VERIFIED]

A foreground service started from the background normally has **no while-in-use (WIU) capability**: no microphone, and
on Android 17 no audio output.

* Microphone: "you cannot create a `microphone` foreground service while your app is in the background and you cannot
  launch a `microphone` foreground service from a `BOOT_COMPLETED` receiver, with a few exceptions" (service-types page).
* Android 17 (`https://developer.android.com/about/versions/17/changes/bg-audio`): "All apps running on Android 17
  that have these background audio interactions must have a visible activity or must be running a foreground service
  that is not of type SHORT_SERVICE. This applies whether or not the app targets API level 37." and "If an app targets
  Android 17 (API level 37) ... the app must be running a foreground service that has while-in-use (WIU) capabilities."
  Failure is silent: "the audio playback and volume change APIs fail silently ... The audio focus API fails with the
  result code AUDIOFOCUS_REQUEST_FAILED." Explicit example: "if your app starts a foreground service in response to
  BOOT_COMPLETE and attempts to interact with audio, it will be suppressed."
  Debug switch: `adb shell cmd audio set-enable-hardening <enable|disable|throw>`; look for `AudioHardening` in
  `adb shell dumpsys audio` / logcat.
* Documented WIU exemptions (restrictions-bg-start page): "A system component starts the service"; widget;
  notification; PendingIntent sent from a different visible app; device owner; **"The service starts by an app which
  provides the VoiceInteractionService"**; `START_ACTIVITIES_FROM_BACKGROUND`.
* Code: `VoiceInteractionManagerServiceImpl` binds the active VoiceInteractionService with
  `BIND_AUTO_CREATE | BIND_FOREGROUND_SERVICE | BIND_INCLUDE_CAPABILITIES | BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS`
  (android17-release, lines 1322-1328). `ActiveServices.shouldAllowFgsWhileInUsePermissionLocked` grants WIU when a
  process of the uid "areBackgroundFgsStartsAllowed" ("The binding flag BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS is also
  allowed by the check here"), and for the 10 s `mFgsWhileInUseTempAllowList` "used when MediaSessionService's bluetooth
  button or play/resume/stop commands are issued". **[VERIFIED code]**

**[INFERRED]** Consequences for this project:

1. Being the user-selected assistant (path A) is not just an input path. The system binding keeps the process at
   `PROCESS_STATE_BOUND_FOREGROUND_SERVICE`, which (a) counts as "foreground" for the LE scan throttler
   (`ActivityManager.RunningAppProcessInfo.procStateToImportance`: every state from `PROCESS_STATE_FOREGROUND_SERVICE` up to but excluding `PROCESS_STATE_IMPORTANT_FOREGROUND` maps to `IMPORTANCE_FOREGROUND_SERVICE` - verified), (b) keeps wake locks and network usable in Doze
   (section 6), (c) carries the BFSL capability for starting foreground services, and (d) is the only route that gives a
   BLE-button-triggered session microphone and (on target 37) audio rights with the screen off.
2. A media-button press (path C) carries its own 10 s WIU allowance. A BLE advertisement (path B) carries nothing.
3. A plain app (not assistant) that receives a BLE press with the screen off can start a `connectedDevice` FGS (if
   battery-exempt or companion) but **cannot record audio**, and on Android 17 with target 37 cannot play audio.
   Targeting API 36 removes the WIU requirement for playback (an FGS is then enough); the microphone restriction remains.

---

## 5. CompanionDeviceManager (CDM)

### 5.1 Can an advertise-on-press button be associated?

* **[VERIFIED]** The normal `associate()` flow shows a system chooser that scans with a `BluetoothLeDeviceFilter`; the
  device must be advertising while the dialog is open. For the Shelly that means pressing during the dialog or using
  its pairing mode ("To enter BLE pairing, press any button 4 times or hold any two buttons simultaneously for 10
  seconds", duration 1 min - Shelly RC Button 4 ZB page). Doc note: CDM has "limited filtering and no support for
  non-bonded rotating MAC addresses" - not a problem for a fixed-address button.
* **[VERIFIED code]** There is a shell path: `adb shell cmd companiondevice associate USER_ID PACKAGE --mac-address <MAC>`
  (older syntax `associate USER_ID PACKAGE MAC`), `CompanionDeviceShellCommand.java`. Also
  `simulate-device-appeared ASSOCIATION_ID`, `start-observing-device-presence-association-id`, `list USER_ID`.
  **[UNKNOWN]** whether an unrooted `adb shell` passes the permission check on a production Pixel - try it.

### 5.2 What presence observation really is [VERIFIED]

`startObservingDevicePresence(ObservingDevicePresenceRequest)` (API 36; the String variant is deprecated): "For Bluetooth
LE devices, this is based on scanning for device with the given address." Needs
`REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`; callbacks arrive in `CompanionDeviceService.onDevicePresenceEvent()`; "The
service is bound when the companion device is within BLE range or connected using Bluetooth."

The scan the system runs for it (`services/companion/.../devicepresence/BleDeviceProcessor.java`, android17-release):

```java
final ScanFilter filter = new ScanFilter.Builder().setDeviceAddress(macAddress).build();
...
private static final ScanSettings SCAN_SETTINGS = new ScanSettings.Builder()
        .setCallbackType(CALLBACK_TYPE_FIRST_MATCH | CALLBACK_TYPE_MATCH_LOST)
        .setScanMode(SCAN_MODE_LOW_POWER)
        .build();
```

`SCAN_MODE_LOW_POWER` from a foreground uid becomes `SCAN_MODE_SCREEN_OFF` = **512 ms every 10.24 s** when the screen
is off (1.4). **[INFERRED]** CDM presence therefore has the *worst* duty cycle available, gives only "appeared /
disappeared" (no button number, no press type), and cannot see a second press within the 10 s on-lost window. It is
not a way to receive button presses. Binding flags: `BIND_ALMOST_PERCEPTIBLE` for normal associations,
`BIND_TREAT_LIKE_VISIBLE_FOREGROUND_SERVICE` for self-managed ones; unbind delay 5 s (`CompanionServiceConnector.java`).

### 5.3 What the association and companion permissions give [VERIFIED code unless marked]

| Benefit | Condition | Source |
|---|---|---|
| Start a foreground service from the background, any time | any active association + `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` or `REQUEST_COMPANION_RUN_IN_BACKGROUND` | `ActiveServices` lines 9588-9601 (`REASON_COMPANION_DEVICE_MANAGER`); `CompanionExemptionProcessor.updateAtm` feeds all active associations |
| Battery-optimisation allowlist | `REQUEST_COMPANION_RUN_IN_BACKGROUND` **and a device currently present** (`shouldExempt = ... && hasPresentDevices`) | `CompanionExemptionProcessor.exemptPackageAsSystem` (android17-release) |
| Metered data in background | `REQUEST_COMPANION_USE_DATA_IN_BACKGROUND` and a device present | same file |
| Scan results for the associated MAC without location permission | association with a MAC, not self-managed | `ScanController.getAssociatedDevices` + per-result check |
| Process bound while device "present" | presence observation | 5.2 |
| Microphone / WIU | **none** | not in `shouldAllowFgsWhileInUsePermissionLocked` |

**[INFERRED]** For a button that is "present" for about ten seconds per press, the presence-conditioned benefits are
worthless; the unconditional FGS-start exemption is the only real gain, and the user-granted battery-optimisation
exemption gives the same. CDM is optional here. If it is used, associate the *hearing aid* rather than the button: a
connected Bluetooth device is "present" for as long as it is connected.

---

## 6. Doze and App Standby

Documented Doze restrictions (`https://developer.android.com/training/monitoring-device-state/doze-standby`): "Suspends
network access. Ignores wake locks. Defers standard AlarmManager alarms ... Doesn't perform Wi-Fi scans. Doesn't let
sync adapters run. Doesn't let JobScheduler run." Exempt apps: "An app that is partially exempt can use the network and
hold partial wake locks during Doze and App Standby. However, other restrictions still apply". The doc says nothing
about Bluetooth scanning. **[VERIFIED]**

Doze timing defaults (`DeviceIdleController.java`, android17-release) **[VERIFIED]**: light idle after 4 min of screen-off
inactivity, light idle period 5 min growing to 15 min, light maintenance budget 1-5 min; deep: inactive 30 min + idle
after inactive 30 min + sensing 4 min + locating 30 s, idle period 1 h growing to 6 h; any motion ends deep Doze
(`motion` shell command simulates it). **[INFERRED]** A phone carried in a trouser pocket rarely reaches deep Doze; a
phone lying on a desk for hours does. Both must be tested.

What the code does:

| Aspect | Behaviour in Doze | Source |
|---|---|---|
| LE scanning in the controller | not touched by `DeviceIdleController`; governed only by section 1 (screen state, app importance, timeout, allowance) | **[INFERRED]** - no deviceidle hook in the Bluetooth scan code |
| Delivery to a `ScanCallback` in a running FGS process | works; the process is not frozen | **[INFERRED]** |
| Partial wake locks | disabled in device idle only if the app is not allowlisted **and** its process state is worse than `PROCESS_STATE_BOUND_FOREGROUND_SERVICE` (`PowerManagerService.setWakeLockDisabledStateLocked`, lines 4834-4845). An app with a foreground service (or the bound assistant service) keeps its wake locks even without exemption. Light idle blocks wake locks only if flag `disableWakelocksInLightIdle` is on. | **[VERIFIED]** |
| Network | allowed in idle / battery saver if `procState <= FOREGROUND_THRESHOLD_STATE (= PROCESS_STATE_BOUND_FOREGROUND_SERVICE)` or the uid has `PROCESS_CAPABILITY_POWER_RESTRICTED_NETWORK` (`NetworkPolicyManager.isProcStateAllowedWhileIdleOrPowerSaveMode`) | **[VERIFIED]** |
| Alarms | standard alarms deferred; `setExactAndAllowWhileIdle` works but is rate-limited | **[VERIFIED]** doc |
| Jobs / WorkManager | not run until a maintenance window | **[VERIFIED]** doc |
| `Handler.postDelayed` timers | stop while the CPU sleeps (uptime clock) - affects any "refresh the scan every N minutes" loop that is not alarm-driven or wake-locked | **[VERIFIED]** SystemClock doc |

What the battery-optimisation exemption changes **[VERIFIED]**: (1) foreground-service start from the background is
allowed (4.2); (2) network and partial wake locks in Doze even without a foreground service; (3) App Standby buckets no
longer restrict the app. What it does **not** change: the LE scan duty cycle (the scan throttler looks only at uid
importance, privileged permission and screen state), WIU / microphone, the Android 17 audio rule.
Granting it: `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with permission `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
(the Play restriction on that intent does not apply to a sideloaded app), or
`adb shell dumpsys deviceidle whitelist +<pkg>`.

App Standby: an app with a foreground service counts as active. **[INFERRED]** Do not let the user set the app to
"Restricted" battery usage; that state blocks foreground services.

---

## 7. Putting it together

### 7.1 The remote: what a press looks like on air

* **[VERIFIED]** Shelly BLU RC Button 4 ZB (SBBT-104CUS, device id 0x16): "The device transmits BLE advertising packets
  when one or more buttons are pressed (1 or more times)." "Button events: Single, with duration 1500ms."
  "Beacon: Periodic, with interval: beacon mode: 12s, normal mode: 15min." Objects: `0x3A` button x4 (1x/2x/3x short,
  1x/2x/3x long, hold, none), `0x01` battery; common objects include `0x00` packet id ("revolving counter").
  (`https://shelly-api-docs.shelly.cloud/docs-ble/Devices/BLU_ZB/button4_ZB/`, `.../docs-ble/common`)
* **[VERIFIED]** BTHome v2: service data UUID `0xFCD2`; device-information byte bit 0 = encryption, bit 2 =
  "trigger based device" (`https://bthome.io/format/`). A Shelly button press is therefore service data
  `D2 FC 44 ...` (plain) or `D2 FC 45 ...` (encrypted).
* **[UNKNOWN]** the advertising interval inside the 1500 ms, the number of advertising events, the PDU type
  (scannable or not), the address type, and the extra delay the multi-click detection adds before the burst starts.
  An older BLU changelog entry reads "Change beacon packets timing: 4 pulses, 150mSec delay" for a different firmware
  line - a hint, not a fact for this device. **This is the first thing to measure** (7.4, step 1).

### 7.2 Catch probability per configuration (model, not measurement) [INFERRED]

Model: burst length T = 1.5 s (documented), advertising events every a = 100-150 ms on all three advertising channels,
scanner listens W out of every I on one channel, per-event reception probability p = 0.8 when the scanner is listening
(coexistence with the hearing-aid link and 2.4 GHz Wi-Fi is not modelled and will cost something).
For I > T the chance that the burst meets a window is about (T + W - a) / I.

| # | Configuration with the screen off | Radio schedule | Estimated catch of the first press (T = 1.5 s) | T = 1 s / 3 s |
|---|---|---|---|---|
| 1 | App "foreground" (running FGS or bound assistant service), filtered scan, LOW_LATENCY, mode still intact | 100/100 ms | **about 97-99 %** (10-15 chances) | about 95-99 % / 99 % |
| 2 | Same, BALANCED requested | 183/730 ms, >= 366 ms of listening inside any 1.5 s | about 90-98 % if a <= 150 ms; drops towards 80 % if a is 300 ms | about 80-95 % / 99 % |
| 3 | Same, LOW_POWER requested | 512/10240 ms | **about 15-20 %** | about 12-15 % / 31-34 % |
| 4 | No foreground status (dead or cached process), PendingIntent scan, any requested mode | 512/10240 ms | **about 15-20 %** | about 12-15 % / 31-34 % |
| 5 | CDM presence observation only | 512/10240 ms, FIRST_MATCH | about 15-20 %, and no button data | same |
| 6 | Configuration 1 after the 10-minute timeout fired, scan never restarted | 140/1400 ms at first, 512/10240 ms after the next screen-off evaluation | about 60-90 %, then 15-20 % | - |
| 7 | Android 17 with `scan_allowance_throttling_enabled` on and allowance spent | 512/10240 ms | about 15-20 % | - |
| 8 | Unfiltered or empty-filter scan | paused | 0 % | 0 % |

Reading: there are only two regimes. Either the radio is held in LOW_LATENCY / balanced (first press very likely
caught) or it falls to the 5 % screen-off mode (four out of five first presses lost). Everything in the design has to
serve keeping regime one alive for hours.

### 7.3 Recommended configuration

**Process and privileges**

1. One always-on foreground service, `foregroundServiceType="connectedDevice"`, started from the setup activity and
   re-started from `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED` (direct-boot aware) and `MY_PACKAGE_REPLACED`,
   `START_STICKY`. This is what makes the uid "foreground" for the scanner.
2. Hold the assistant role (VoiceInteractionService). Besides path A it gives the bound-foreground process state and
   the WIU route (4.4). Treat it as a hard prerequisite for path B to open the microphone.
3. Battery-optimisation exemption (user dialog or adb). Gives FGS restart from any background trigger and Doze
   wake locks / network without relying on the service being up.
4. Optional: CDM association (hearing aid or button MAC) + `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND`
   as a second, independent FGS-start exemption.
5. `BLUETOOTH_SCAN` with `neverForLocation`, `BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`,
   `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
6. Consider `targetSdk 36` while the audio path is being proven (4.4).

**Two concurrent scans, same filter** - one `ScanFilter` with `setDeviceAddress(buttonMac)` **and**
`setServiceData(FCD2, byteArrayOf())` (ANDed, both offloadable):

* **Scan A, events.** `ScanCallback` inside the foreground service. `SCAN_MODE_LOW_LATENCY`,
  `CALLBACK_TYPE_ALL_MATCHES`, report delay 0, `setScanType(SCAN_TYPE_PASSIVE)` on SDK >= 36.1. Delivers every advert
  of every press; de-duplicate on the BTHome packet id.
* **Scan B, anchor and wake-up.** `PendingIntent.getBroadcast` to an explicit manifest receiver
  (`FLAG_MUTABLE | FLAG_UPDATE_CURRENT`, never `FLAG_CANCEL_CURRENT`). `SCAN_MODE_LOW_LATENCY`,
  `CALLBACK_TYPE_FIRST_MATCH | CALLBACK_TYPE_MATCH_LOST`, `MATCH_MODE_AGGRESSIVE`, `MATCH_NUM_ONE_ADVERTISEMENT`.
  It is exempt from the 10-minute timeout, so it can keep the shared radio parameters at 100/100 after scan A has been
  downgraded; it survives the death of the app process; its receiver restarts the service (allowed by exemption 3 or 4).
  If offloaded filtering is not supported, replace it by a PendingIntent `ALL_MATCHES` scan.

**Maintenance**

* Re-issue both scans (stop + start for A, idempotent start for B) on: service start, boot, package replaced,
  `BluetoothAdapter.STATE_ON` (runtime receiver in the service), `onScanFailed`, and a periodic
  `setExactAndAllowWhileIdle` alarm of about 9 minutes (or longer if the measurement shows B alone holds LOW_LATENCY).
  Stay below 5 callback-scan starts per 30 s.
* Do **not** copy SaraButton's permanent partial wake lock and 4-minute `Handler` refresh blindly: it keeps the CPU
  awake around the clock. Measure first whether delivery works from suspend; add a wake lock only if it does not.
* Take a short wake lock in the callback / receiver before doing work; the Bluetooth process's own wake lock ends
  right after the hand-over.
* Self-monitoring: log every result with wall time, `elapsedRealtime`, `uptimeMillis`, screen state,
  `PowerManager.isDeviceIdleMode()` / `isDeviceLightIdleMode()`, RSSI, packet id, callback type, and which scan
  delivered it. Export the log; that is the debuggability the owner asked for.

**Honest limit.** If the device turns out to run with scan-allowance throttling enabled, an unprivileged app cannot hold
LOW_LATENCY with the screen off for more than minutes per hour and the first-press rate falls to regime two. The
fallbacks are then: a remote that keeps a bonded GATT/HID connection (no scanning involved), a remote that advertises
much longer per press, or accepting "press until the confirmation tone" as the interaction rule.

### 7.4 The measurement that has to be run

Build a probe APK (the scan part of the real app plus the logger) and a ground truth: a second receiver that is
always listening (an nRF52 / ESP32 sniffer or a second phone with the screen on in LOW_LATENCY) logging BTHome packet ids.

Step 1 - characterise the remote (screen on, probe in the foreground, LOW_LATENCY, PASSIVE, HCI snoop log enabled in
Developer options): press 30 times; from the snoop log read adverts per press, spacing, total duration, PDU type
(`ScanResult.isConnectable`, event type), address type, and the delay between finger and first advert.

Step 2 - read the platform state once:

```
adb shell getprop ro.build.fingerprint
adb shell pm list packages --apex-only --show-versioncode | grep -i -E "bt|bluetooth"
adb shell aflags list | grep -i -E "scan_allowance|upgrade_le_scan|adapter_suspend|treat_empty_filters"
adb shell device_config list bluetooth
adb shell settings list global | grep -i ble_scan
adb shell getprop | grep -i -E "bluetooth\.(power\.suspend|ble)"
adb shell dumpsys bluetooth_manager            # per-app "LE scans", "Scan time per mode", "Number of results (ScreenOff/ScreenOn/Total)", ongoing scans
adb shell dumpsys activity processes <pkg> | grep -i -E "procstate|curProcState"
adb shell cmd companiondevice list 0
adb shell device_config put bluetooth scan_timeout_millis 86400000 ; adb shell device_config get bluetooth scan_timeout_millis   # expected to be refused on a user build
```

and watch the scanner decide: `adb logcat -v time -s BtScan.ScanManager BtScan.ScanThrottler BtScan.ScanController BtScan.ScanUtil`
(tag prefix `BtScan.` in Android 17). The line "Set Scan Parameters Native ... (in scan unit=<interval> / <window>)"
shows the real parameters in 0.625 ms units: 160/160 = low latency, 1168/292 = balanced, 2240/224 = low power,
16384/819 = screen-off.

Step 3 - the matrix. Per cell at least 50 single presses, at least 15 s apart (so `MATCH_LOST` fires in between);
record caught / missed against the ground truth and the latency of the first delivered advert; report the rate with a
Wilson 95 % interval.

Configurations: (a) recommended A + B with FGS; (b) A only; (c) B only with the app process killed; (d) PendingIntent
`ALL_MATCHES` with the process killed and no FGS; (e) BALANCED in FGS; (f) with and without battery-optimisation
exemption; (g) with and without the assistant role; (h) with the hearing aid connected and streaming vs not connected.

States: screen on; screen off 1 min; forced light Doze; forced deep Doze; real idle, unplugged, 30 min / 2 h / 8 h on a
desk and in a pocket; 15 min and 2 h after the last scan (re)start; after `am kill`; after Bluetooth off/on; after
reboot before and after the first unlock; after `adb install -r`.

Forcing the states:

```
adb shell dumpsys battery unplug                 # pretend to be on battery (USB otherwise blocks Doze)
adb shell input keyevent KEYCODE_SLEEP           # screen off
adb shell dumpsys deviceidle force-idle light    # light Doze
adb shell dumpsys deviceidle force-idle deep     # deep Doze   (plain "force-idle" = deep)
adb shell dumpsys deviceidle step deep           # or walk the state machine step by step
adb shell dumpsys deviceidle get light ; adb shell dumpsys deviceidle get deep
adb shell dumpsys deviceidle motion              # simulate motion, leaves deep Doze
adb shell dumpsys deviceidle unforce ; adb shell dumpsys battery reset
adb shell dumpsys deviceidle whitelist +<pkg>    # battery-optimisation exemption   (-<pkg> removes it)
adb shell am set-inactive <pkg> true ; adb shell am set-standby-bucket <pkg> rare
adb shell am kill <pkg>                          # kill the process, keeps PendingIntents
adb shell am force-stop <pkg>                    # negative control: must stop the scan
adb shell svc bluetooth disable ; adb shell svc bluetooth enable
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED <pkg>
adb shell cmd audio set-enable-hardening throw   # make Android 17 audio suppression loud
```

Forced Doze over USB is a functional check only. The real answer needs the phone unplugged for hours with on-device
logging (or adb over Wi-Fi), because suspend behaviour and the uptime-based timers differ on USB power.

Pass criterion to agree on beforehand, for example: first-press catch >= 95 % (lower bound of the interval >= 90 %) in
every state, median latency under 500 ms.

---

## 8. Pixel 10 / Tensor G5 Bluetooth quirks reported in 2025-2026

* **[VERIFIED, secondary]** January 2026 Pixel update: reports of Wi-Fi failing and "Bluetooth refuses to turn on" on
  Pixel 8 Pro, Pixel 10 and Pixel 10 Pro XL among others; no workaround at the time, Google had not acknowledged it
  (`https://9to5google.com/2026/01/26/latest-google-pixel-update-causing-wi-fi-and-bluetooth-issues-for-some/`). This
  is a radio bring-up regression, not a scanning bug.
* **[VERIFIED, secondary]** The November 2026 update (Android 17 QPR2) lists generic fixes: "stability and background
  performance for Bluetooth device connectivity" and "stability and connection reliability for Bluetooth accessories
  under certain conditions" (9to5Google, same article as 0.1).
* **[UNKNOWN]** No primary-source report (Google Issue Tracker, AOSP bug) specific to LE *scanning* on the Pixel 10 /
  Tensor G5 was found. Searches returned only generic Android threads (scans stopping with the screen off on unfiltered
  scans; OEM-specific timers on Xiaomi / OnePlus). Absence of reports is not evidence of absence.
* **[UNKNOWN]** The Bluetooth / Wi-Fi combo chip of the Pixel 10 Pro and its APCF capabilities (filter count, tracked
  advertisers, batch storage) could not be confirmed from a primary source.
* Risk that follows from 0: scan behaviour can change with a monthly Play system update of `com.google.android.bt`.
  Keep the probe's self-test in the shipped app and re-run the matrix after updates.

---

## 9. Reusable material

| What | From | License | Note |
|---|---|---|---|
| Dual-scan pattern (callback `ALL_MATCHES` in an FGS + PendingIntent `FIRST_MATCH|MATCH_LOST`), boot / package-replaced / Bluetooth-state recovery, direct-boot-aware receiver and service, BTHome parser with tests | `https://github.com/OferTiber/SaraButton` (`BleScanManager.java`, `MonitoringService.java`, `BleScanReceiver.java`, `BootReceiver.java`, `BthomeParser.java`) | MIT | Java, target API 35, 0 stars, last push 2026-08-07. Uses a permanent partial wake lock and a 4-minute refresh; does not set `SCAN_TYPE_PASSIVE`; plain BTHome only. Good reference, not proof of reliability. |
| Scan constants and throttling logic to mirror in documentation and tests | AOSP `packages/modules/Bluetooth` `le_scan/` | Apache-2.0 | read for behaviour; nothing to link against |
| PendingIntent scan sample | `https://github.com/android/platform-samples` (`BLEScanIntentSample.kt`, linked from the background BLE guide) | Apache-2.0 | not read in depth |
| CDM samples | same repo (`CompanionDeviceManagerSample.kt`, `CompanionDeviceSampleService.kt`) | Apache-2.0 | not read in depth |

## 10. Corrections to the inherited planning notes

* The notes list `CompanionDeviceManager` / `CompanionDeviceService` as an approach that "can enable waking the app" for
  path B. For an advertise-on-press button it cannot serve as the event path: the system's presence scan is
  LOW_POWER + FIRST_MATCH (512 ms / 10.24 s with the screen off) and carries no button data. CDM is useful only for
  the FGS-start exemption.
* The notes treat "foreground service permitted" and "BLE operations continue in every energy state" as one open
  point. The decisive variable is not Doze but the Bluetooth scan throttler: screen state x app importance x 10-minute
  timeout (x Android 17 allowance flag). Doze does not appear in that logic at all.
* The notes say SaraButton "supports specifically the model SBBT-104CUS". Its README lists three: SBBT-004CUS (0x0007),
  SBBT-104CUS (0x0016), SBBT-102C (0x0017).
* The notes do not mention that the assistant role is what gives a BLE-triggered session microphone access (and, for
  target 37, audio output) with the screen off; they list path A only as an input path.
* The notes do not mention Android 17's background audio hardening, which silently mutes audio from a foreground
  service started at boot when the app targets API 37.
* The notes cite `https://developer.android.com/develop/connectivity/bluetooth/ble/background` as the basis; that page
  does not promise delivery timing, and it states that a foreground service cannot be started from the scan
  PendingIntent on Android 12+.
