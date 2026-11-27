# Verification: LE scan modes on Android 17 (cluster "scan-modes")

Date: 2026-11-27. Nothing here was run on a device. "Confirmed" means: I opened the primary source myself and quote it.
Scope of every code statement: AOSP tag `android-17.0.0_r1` (Bluetooth module commit `c77db469de80f86660bc053bec4dee0c5d4b947c`,
2026-05-15; `git ls-remote` shows tag `android-17.0.0_r1^{}` == branch `android17-release` == this commit).
The Bluetooth stack on the phone is a Mainline APEX and can be newer than this snapshot (see claim 5).

## Sources opened

| What | Where | Ref |
|---|---|---|
| Bluetooth module | `https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/tags/android-17.0.0_r1/...` | c77db469 |
| local sparse clone (checked clean, HEAD == tag; 3 files re-fetched from googlesource and `diff`ed identical) | `<repos>/aosp-bluetooth-a17` | c77db469 |
| Release configs / aconfig flag values | `https://android.googlesource.com/platform/build/release` tag `android-17.0.0_r1` = `c85f4aebe46714235599a9bd6430a0fe30c6e8d5` (2026-05-28 "Set aosp_current to cp2a."), cloned to `.../scratchpad/repos/aosp-build-release-a17`; also branches `android17-security-release` and `main` | |
| aconfig tool defaults | `platform/build` `tools/aconfig/aconfig/src/commands.rs` @ tag | |
| frameworks/base: `ActivityManager.java`, `UidObserverController.java`, `ActivityManagerService.java`, `packages/Shell/AndroidManifest.xml`, `SettingsProvider.java`, `Settings.java` | googlesource @ tag `android-17.0.0_r1` | |
| ConfigInfrastructure: `WritableFlags.java`, `WritableNamespaces.java` | googlesource @ tag | |
| sepolicy: `private/property_contexts`, `private/shell.te` | googlesource @ tag | |
| Docs | `https://developer.android.com/reference/android/bluetooth/le/ScanSettings.Builder`, `https://source.android.com/docs/core/ota/modular-system/bluetooth`, `https://developer.android.com/about/versions/17/qpr3/release-notes` | fetched 2026-11-27 |

Files fetched for this pass are in `<research>/verify-scan-modes-files/`.
Model script: `<research>/scripts/scan_catch_model.py`.

Paths below are relative to `android/app/src/com/android/bluetooth/` in the Bluetooth module unless a repo is named.

---

## Claim 1 - scan parameters. CONFIRMED

`le_scan/ScanUtil.kt` lines 58-69 at the tag:

```kotlin
// Scan params corresponding to regular scan setting
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

Additions the claim leaves out:

* There is a second screen-off mode, `SCAN_MODE_SCREEN_OFF_BALANCED` = 183/730 (used for a foreground app that asked for BALANCED).
* These are defaults. `ScanUtil.windowMillis/intervalMillis` (lines 143-207) read the three normal modes from
  `Settings.Global.BLE_SCAN_{LOW_POWER,BALANCED,LOW_LATENCY}_{WINDOW,INTERVAL}_MS` and the two screen-off modes from
  `adapterService.screenOff*` = DeviceConfig namespace `bluetooth` (`btservice/AdapterService.java` lines 4947-5035).
  The phone can therefore run other numbers than AOSP (server-pushed DeviceConfig, see claim 8).
* One radio setting for everybody: `ScanManager.configureRegularScanParams()` (line 955) picks the most aggressive
  regular client (`ScanUtil.getAggressiveClient`, priority OPPORTUNISTIC 0 < SCREEN_OFF 1 < LOW_POWER 2 <
  SCREEN_OFF_BALANCED 3 < BALANCED 4 < LOW_LATENCY 5, lines 284-295).
* Units: `Utils.millsToUnit` (0.625 ms): 100 ms = 160, 10240 ms = 16384. Log line to look for on the device:
  `configureRegularScanParams(): Set Scan Parameters Native ... (in scan unit=<interval> / <window>)`, tag `BtScan.ScanManager`.

## Claim 2 - screen off: unfiltered suspended, filtered continue. CONFIRMED

* `ScanManager.handleScreenOff()` (line 580) -> `handleSuspendScans()` (line 650):
  `var screenRequirementUnmet = requiresScreenOn(client) && !mScreenOn;` ... `handleStopScan(client); mSuspendedScanClients.add(client);`
* `ScanUtil.kt` line 326: `fun requiresScreenOn(client: ScanClient) = !isOpportunisticScanClient(client) && !client.hasNonEmptyFilters`
* `ScanClient.kt` line 52: `val hasNonEmptyFilters = filters.any { !it.isAllFieldsEmpty }`
* `framework/java/android/bluetooth/le/ScanFilter.java` line 745: `isAllFieldsEmpty() { return EMPTY.equals(this); }`; `equals()` (lines 716-740)
  compares `mDeviceAddress`, `mServiceDataUuid`, `mServiceData`, ... So a filter with a service-data UUID or a device address is non-empty.
* Starting a scan while the screen is already off uses the same test (`ScanManager.startScan`, line 374, "Cannot start unfiltered scan in screen-off.").

Details that matter:

* "Screen on" is `displayManager.displays.any { it.state == Display.STATE_ON }` (`btservice/DisplayListener.kt` line 62). Always-on display
  (DOZE states) counts as off; any other display in STATE_ON (external, virtual) counts as on.
* `setServiceData(uuid, null)` keeps the scan alive but is **not** offloaded: `ScanFilterQueue.addScanFilter` line 247 only adds an entry
  `if (filter.getServiceDataUuid() != null && filter.getServiceData() != null)`. The controller filter slot is then created with no condition
  (feature selection 0) and what it passes depends on the controller. Give the filter data bytes (an empty array is enough: the pattern becomes the
  UUID bytes `D2 FC`) or an address so that the controller does the filtering.
* `treat_empty_filters_as_unfiltered` is `ENABLED / READ_ONLY` in release config `cp2a`
  (`build/release aconfig/cp2a/com.android.bluetooth.flags/treat_empty_filters_as_unfiltered_flag_values.textproto`): the old trick of an empty
  `ScanFilter.Builder().build()` is dead everywhere (`ScanClient.isFiltered`, line 46).

## Claim 3 - foreground keeps 100/100, everything else gets 512/10240. CONFIRMED (for the code at the tag, allowance flag off)

`le_scan/ScanThrottler.kt` lines 68-103:

```kotlin
// background throttling
if (!scanManager.isAppForeground(client) || isForceDowngradedScanClient(client)) {
    val limitMode = if (!isScreenOn) { ScanSettings.SCAN_MODE_SCREEN_OFF } else { ScanSettings.SCAN_MODE_LOW_POWER }
    targetScanMode = minScanMode(limitMode, targetScanMode)
}
// screen off throttling
else if (!isScreenOn) {
    targetScanMode = when (targetScanMode) {
        ScanSettings.SCAN_MODE_LOW_POWER -> ScanSettings.SCAN_MODE_SCREEN_OFF
        ScanSettings.SCAN_MODE_BALANCED, ScanSettings.SCAN_MODE_AMBIENT_DISCOVERY -> ScanSettings.SCAN_MODE_SCREEN_OFF_BALANCED
        ScanSettings.SCAN_MODE_LOW_LATENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
        else -> return false
    }
}
```

The exact test, `le_scan/ScanManager.java`:

```java
private static final int FOREGROUND_SERVICE_IMPORTANCE_CUTOFF = IMPORTANCE_FOREGROUND_SERVICE;   // line 94
private static final boolean DEFAULT_UID_IS_FOREGROUND = true;                                     // line 96
boolean isAppForeground(ScanClient client) {                                                       // line 726
    if (mUidImportanceMap.indexOfKey(client.getAppUid()) < 0) { return DEFAULT_UID_IS_FOREGROUND; }
    return mUidImportanceMap.get(client.getAppUid()) <= IMPORTANCE_FOREGROUND_SERVICE;
}
```

* The map is filled at every scan start: `fetchAppForegroundState()` (line 459) = minimum of `ActivityManager.getPackageImportance(pkg)` over the
  packages of the uid, and afterwards by `mActivityManager.addOnUidImportanceListener(mForegroundServiceImportanceListener, FOREGROUND_SERVICE_IMPORTANCE_CUTOFF)` (line 259).
* `frameworks/base core/java/android/app/ActivityManager.java` @ tag: `IMPORTANCE_FOREGROUND = 100`, `IMPORTANCE_FOREGROUND_SERVICE = 125`,
  `IMPORTANCE_VISIBLE = 200`, `IMPORTANCE_TOP_SLEEPING = 325`, `IMPORTANCE_GONE = 1000`; `procStateToImportance()` (line 4226):
  `... else if (procState >= PROCESS_STATE_IMPORTANT_FOREGROUND) return IMPORTANCE_VISIBLE; else if (procState >= PROCESS_STATE_FOREGROUND_SERVICE) return IMPORTANCE_FOREGROUND_SERVICE; else return IMPORTANCE_FOREGROUND;`
  So "foreground" for the scanner = uid process state PERSISTENT, PERSISTENT_UI, TOP, BOUND_TOP, FOREGROUND_SERVICE or BOUND_FOREGROUND_SERVICE.
  A merely visible process (200) is **not** foreground. A top activity with the device asleep is `IMPORTANCE_TOP_SLEEPING` (325, javadoc:
  "running the foreground UI, but the device is asleep") and is **not** foreground.
* Dead process: `MyUidObserver.onUidGone` -> `onUidImportance(uid, IMPORTANCE_GONE)` -> `handleImportanceChange` (line 808) ->
  `throttleScanModeBackgroundUid` -> `minScanMode(SCAN_MODE_SCREEN_OFF, scanMode)` with the screen off (ScanThrottler lines 199-207).
  PendingIntent scans have no death recipient (`ScanController.onScannerRegistered`, lines 545-558), so they stay registered and are throttled to 512/10240.

Traps I found on the way (all in the same files):

1. **Listener granularity.** The listener cut point is `importanceToProcState(125)` = `PROCESS_STATE_FOREGROUND_SERVICE` (`ActivityManager.java` lines 5049-5061 and 4284-4307);
   `UidObserverController.java` lines 400-402 report only when `lastState <= cutpoint` changes. Transitions between BOUND_FOREGROUND_SERVICE (still
   importance 125) and anything lower are therefore never reported. Only a real own foreground service (procState 4) is tracked reliably.
   Start the scan after `startForeground()` has returned, from the app that owns the service.
2. **Forced clients count as background.** `isForceDowngradedScanClient` = 10-minute timeout flag (claim 4) or the "connecting" downgrade.
3. **Connecting downgrade.** While any of A2DP/HFP/HFP-client/A2DP-sink/LE-Audio is in CONNECTING, LOW_LATENCY clients are set to BALANCED
   (`handleConnectingState`, line 595; `ScanThrottler.downgradeScanModeFromMaxDuty`, line 237) for at most `scan_downgrade_duration_millis` = 6 s.
   A hearing aid that reconnects triggers this.
4. Upgrade-on-start is off with the screen off: `upgrade_le_scan_only_screen_on` is `ENABLED / READ_ONLY` in `aconfig/mainline_2026_02`, which `cp2a` inherits.
5. With `scan_allowance_throttling_enabled` on, this claim no longer holds (claim 5).

## Claim 4 - 10-minute timeout. CONFIRMED (allowance flag off)

* `ScanUtil.kt` line 52: `DEFAULT_SCAN_TIMEOUT = 10.minutes`; overridable by DeviceConfig `bluetooth/scan_timeout_millis` (`AdapterService.java` line ~4999).
* Armed per client at start: `ScanManager.configureTimeout()` lines 494-516: `if (isExemptFromScanTimeout(client)) return;` ... `mHandler.postDelayed(timeoutRunnable, mAdapterService.getScanTimeout().toMillis());`
* Exemption, `ScanUtil.kt` lines 343-354: `isExemptFromScanTimeout(client) = isOpportunisticScanClient(client) || isFirstMatchScanClient(client)` with
  `isFirstMatchScanClient = (client.settings.callbackType and ScanSettings.CALLBACK_TYPE_FIRST_MATCH) != 0`. (MATCH_LOST alone is not exempt.)
* What happens, `handleRegularScanTimeout()` lines 1221-1253:
  ```java
  if (!client.isFiltered()) { setOpportunisticScanClient(client); removeScanFilters(client.getScannerId()); }      // unfiltered -> OPPORTUNISTIC
  else { int maxScanMode = SCAN_MODE_FORCE_DOWNGRADED; client.updateScanMode(minScanMode(scanMode, maxScanMode)); } // filtered -> at most LOW_POWER
  client.getAppScanStats().setScanTimeout(client.getScannerId());
  ```
  with `SCAN_MODE_FORCE_DOWNGRADED = ScanSettings.SCAN_MODE_LOW_POWER` (line 98).
  Stage 1: the filtered scan drops to LOW_POWER = 140/1400 at once (also with the screen off, also from SCREEN_OFF_BALANCED).
  Stage 2: the timeout flag makes `isForceDowngradedScanClient` true, so at the next evaluation with the screen off (next screen-off event, importance
  change, end of a connecting downgrade) `throttleScanMode` gives `SCAN_MODE_SCREEN_OFF` = 512/10240. `dumpsys bluetooth_manager` marks the scan "(Forced)".
* Restart: `handleStopScan()` lines 527-534 removes the runnable; a new `startScan(ScanCallback)` registers a new scanner id and a new timer; the flag
  lives in the per-scanner `LastScan.isTimeout`. Start quota is 5 starts / 30 s (`AppScanStats.isScanningTooFrequently`, callback scans only).

Details:

* The timer is `Handler.postDelayed` = uptime clock: it does not advance while the application processor is suspended. "10 minutes" is a lower bound in wall time.
* `AppScanStats.isScanningTooLong()` (line 339) measures from the start of the app's oldest *concurrent* scan (`scanStartTimestamp` is set only `if (!isScanning())`); it never prevents a downgrade once a timer fires.
* If `scan_allowance_throttling_enabled` is on, `configureTimeout()` and `handleRegularScanTimeout()` return immediately - the timeout does not exist then and the FIRST_MATCH exemption is worth nothing (the allowance applies to all callback types).
* FIRST_MATCH clients are regular clients (`startScan`: batch only if `isBatchClient || isAutoBatchScanClientEnabled`), so a foreground FIRST_MATCH LOW_LATENCY client keeps the shared radio at 100/100 by code. Not device-tested.

## Claim 5 - scan-allowance throttle. PARTLY (exists; rule is stricter than claimed; not enabled in AOSP 17.0 release config; state on the phone undetermined)

Existence, `flags/le_scanning.aconfig` lines 31-36 (package `com.android.bluetooth.flags`, `container: "com.android.bt"`):

```
flag {
    name: "scan_allowance_throttling_enabled"
    namespace: "bluetooth"
    description: "The flag enables scan throttling using allowance."
    bug: "478996075"
}
```
No `is_fixed_read_only`, no `metadata`. Read in `ScanThrottler.isScanAllowanceThrottlingEnabled()` = `Flags.scanAllowanceThrottlingEnabled()`.

Exact rule, `ScanThrottler.applyAllowanceThrottling()` lines 273-291:

```kotlin
val ledger = client.appScanStats.scanAllowanceLedger
if (scanManager.hasPrivilegedPermission(client) || scanManager.isAppForegroundUi(client)) {
    return client.updateScanMode(targetScanMode)
}
if (targetScanMode != ScanSettings.SCAN_MODE_SCREEN_OFF && ledger.spentScanAllowance < getScanAllowance()) {
    val remainingAllowance = getScanAllowance() - ledger.spentScanAllowance
    return applyModeAndScheduleNextJob(client, targetScanMode, ledger, remainingAllowance)
}
recordUsageRunnables.remove(client)?.let { handler.removeCallbacks(it) }
return client.updateScanMode(ScanSettings.SCAN_MODE_SCREEN_OFF)
```

* Allowance: `DEFAULT_SCAN_ALLOWANCE_SECONDS = 6.minutes` (`ScanUtil.kt` line 103), sysprop override `bluetooth.ble.scan.scan_allowance_seconds.config`;
  refill `ALLOWANCE_REFILL_WINDOW = 1.hours` (ScanThrottler line 360).
* Weights (`ScanUtil.kt` lines 91-97): LOW_LATENCY 100, BALANCED / AMBIENT / SCREEN_OFF_BALANCED 25, LOW_POWER 10, SCREEN_OFF 5.
  `convertAllowanceToRemainingTime = allowance * 100 / weight`: 6 min of LOW_LATENCY, or 24 min of (SCREEN_OFF_)BALANCED, or 60 min of LOW_POWER
  (= unlimited, refill delay `max(1h - 60min, 0)` = 0). After that the client is forced to `SCAN_MODE_SCREEN_OFF` (512/10240) - with the screen on, too - until the refill.
* Who is exempt: uid holds granted `BLUETOOTH_PRIVILEGED` (`Util.checkPrivilegedPermission`), or `isAppForegroundUi` = importance `<= IMPORTANCE_FOREGROUND` (100)
  (`ScanManager.java` lines 95, 733-738) = process state TOP / BOUND_TOP / persistent. **A foreground service (125) is not exempt.** With the device asleep the top app is
  TOP_SLEEPING (325), so with the screen off no unprivileged app is exempt. "Apps without a visible activity" is therefore too generous: a visible-but-not-top app is also throttled.
* Ledger is per uid (`ScannerMap.appScanStatsMap` keyed by uid).
* With the flag on: no 10-minute timeout; screen-off and background throttling are applied after `DEFAULT_SCAN_THROTTLE_DELAY = 2.seconds`.
* The code is unfinished at the tag: `TODO(b/478349128)` markers; usage is only booked when the delayed job fires
  (`applyModeAndScheduleNextJob`), and `handleStopScan` cancels that job (`removeRecordUsageRunnable`), so a scan stopped before the job fires books nothing. Do not build on that gap.

Is it enabled?

* `platform/build/release` @ `android-17.0.0_r1`: `git grep -i scan_allowance` over the whole tree returns nothing. There is no
  `scan_allowance_throttling_enabled_flag_values.textproto` in `aconfig/cp2a` (about 140 Bluetooth flag files), `aconfig/mainline_2026_01..04`, nor even `aconfig/trunk_staging` (about 800 Bluetooth flag files).
  Same result on branch `android17-security-release` (aa3bde7b, 2026-04-16) and `main` (stale, 2025-03).
* `release_configs/cp2a.textproto`: `inherits: "mainline_2026_04"`, `inherits: "cp1a"`; `release_config_map.textproto`: `aliases { name: "aosp_current" target: "cp2a" }`;
  `flag_values/cp2a/RELEASE_PLATFORM_SDK_VERSION.textproto` = "37". So `cp2a` is the Android 17.0 config.
* aconfig default for a flag without a value: `DEFAULT_FLAG_STATE = ProtoFlagState::DISABLED` (`build tools/aconfig/aconfig/src/commands.rs` line 74).
  Default permission: `RELEASE_ACONFIG_FLAG_DEFAULT_PERMISSION` = `READ_ONLY` (`flag_values/ap2a|ap3a|bp1a/...`, inherited through bp1a..cp1a to cp2a; applied by `build/soong aconfig/aconfig_declarations.go` `--default-permission`).
* Result: in an AOSP Android 17.0 (`cp2a`) build the flag is **DISABLED and READ_ONLY** (compiled-in `false`).
* **Undetermined for the phone.** No public source exists for Android 17 QPR1/QPR2 (presumably configs `cp3a`/`cp4a`), for Mainline trains after `mainline_2026_04`, or for Google's internal value sets
  (`git ls-remote`: only `android17-release`, `android17-security-release`, tag `android-17.0.0_r1`). Public Gerrit has no change for bug 478996075 or the flag name.
  Context: the Android 17 QPR3 Beta 4 notes (November 2026) list "Excessive Bluetooth background activity consumes an abnormal amount of power and rapidly drains the device battery. (Issue #566820683)" as fixed - Google is working in this area.

Can the module flip it without an OS update? Yes.

* `apex/Android.bp`: `// Mainline bluetooth apex module.  apex { name: "com.android.bt", defaults: ["b-launched-apex-module"], ... apps: ["Bluetooth"], ...}`; the flag container is `com.android.bt`; `ScanThrottler` lives in the Bluetooth APK inside that APEX.
* `https://source.android.com/docs/core/ota/modular-system/bluetooth`: "The Bluetooth module is an optional Mainline module introduced in Android 13. In Android 16 and higher, the module is updatable ... Delivering faster updates with new features, bug fixes ... independently of the Android OS release cycle." Package `com.google.android.bt`.
* If a later build ships the flag READ_WRITE, a server-side flag push could flip it as well. Either way a change arrives with a reboot (APEX activation / flag storage).

Device check (read-only): `adb shell aflags list | grep -i scan_allowance`; `adb shell device_config list bluetooth`; `adb shell pm list packages --apex-only --show-versioncode | grep -i -E "\.bt$|bluetooth"`;
logcat tag `BtScan.ScanThrottler` prints "Apply scan mode ... with available scan allowance" / "Scheduled the next allowance check in ..." only when the flag is on.

## Claim 6 - PendingIntent registrations live in Bluetooth process memory. CONFIRMED

* Storage: `le_scan/ScannerMap.kt` lines 47-48: `private val appScanStatsMap = mutableMapOf<Int, AppScanStats>()`, `private val apps = ArrayDeque<ScannerApp>()`; no file or database code in `ScannerMap.kt` / `ScanController.java`.
* Bluetooth off: `btservice/AdapterState.java` line 390 `case BREDR_STOPPED -> transitionTo(mTurningBleOff);`, line 416 `mAdapterService.bringDownBle();` ->
  `AdapterService.stopScanController()` (lines 1497-1509) -> `ScanController.cleanup()` (lines 212-224): `mScannerMap.clear(); mScanManager.cleanup();`.
  In Android 17 a user "off" always goes all the way down; if "Bluetooth scanning" is on, the system server restarts the stack into BLE_ON afterwards
  (`service/.../BluetoothManagerService.java` line 1737 "Restarting to BLE_ON due to ..."), with a fresh, empty ScanController. Unprivileged apps cannot scan in BLE_ON
  (`ScanBinder.kt` line 91 "Only privileged app can scan when Bluetooth is not ON").
* Reboot / Bluetooth process crash: memory only, so gone. (Not separately verified: no persistence code exists in the two classes.)
* Force-stop: `ScanController.java` line 1064 `pendingIntent.addCancelListener(Runnable::run, mScanIntentCancelListener)`, lines 101-105 `doOnScanThread(() -> stopScan(intent))`.
  `frameworks/base ActivityManagerService.java` lines 4542-4546: `forceStopPackage` calls `forceStopPackageLocked(..., false /* uninstalling */, true /* packageStateStopped */, ...)`;
  lines 5090-5100: `if (packageName == null || uninstalling || packageStateStopped) { ... removePendingIntentsForPackage(packageName, userId, appId, doit, cancelReason) }` with `CANCEL_REASON_OWNER_FORCE_STOPPED`.
* Not removed by a plain process kill. For package replacement (`adb install -r`) the path is `killApplicationSync` -> `forceStopPackageLocked(..., /* uninstalling= */ false, /* packageStateStopped= */ false, ...)`
  (AMS lines 19258-19264), i.e. PendingIntents are **not** cancelled there. [inferred from this one call site; test on the device]
* Re-registering the same PendingIntent is ignored: line 1027 "Ignoring since the same PI scan is already in ScannerMap".

## Claim 7 - passive scan type from SDK 36.1. CONFIRMED (with a correction about what "passive" means)

* `framework/java/android/bluetooth/le/ScanSettings.java` @ tag, lines 167-174: `public static final int SCAN_TYPE_UNKNOWN = 0; SCAN_TYPE_PASSIVE = 1` ("Does passive scanning, scan responses are ignored."), `SCAN_TYPE_ACTIVE = 2`
  ("Does active scanning, scan results are delivered upon scan responses arrive."); `Builder.setScanType` line 544; default `mScanType = SCAN_TYPE_ACTIVE` (line 357). No `@FlaggedApi`, no `@SystemApi`.
* `https://developer.android.com/reference/android/bluetooth/le/ScanSettings.Builder`: "setScanType  Added in version 36.1 ... If scan type is ScanSettings.SCAN_TYPE_PASSIVE, scan results are delivered immediately upon receiving an advertising report, without waiting for scan responses."
* Flag `support_passive_scanning` ("Support passive scanning by delivering adv packet w/o scan response") is `ENABLED` in `build/release aconfig/bp4a` (Android 16 QPR2).

Correction: it is a **host-side delivery rule, not a radio mode**.

* `android/app/jni/com_android_bluetooth_scan.cpp` lines 316-326: `sScanner->SetScanParameters(/* use active scan */ 0x01, ...)` - the controller always scans actively and keeps sending SCAN_REQ.
* `system/gd/hci/le_scanning_reassembler.cc` lines 70-84: a scannable report is now returned at once ("Returns current result and save it for next scan response") and again, merged, when the scan response arrives.
* `le_scan/ScanController.java` lines 374-385: per client,
  `if ((settings.getScanType() == SCAN_TYPE_ACTIVE && requiresScanResponse) || (settings.getScanType() == SCAN_TYPE_PASSIVE && isScanResponse)) { scanTypeMismatch.add(client); continue; }`
  with `requiresScanResponse = (eventType & ET_SCANNABLE_MASK) != 0 && !isScanResponse && !mIsMsftAdvMonitorEnabled`.
  So with the **default (ACTIVE)** settings a scannable advertisement (ADV_IND, ADV_SCAN_IND) reaches the app only if the SCAN_REQ/SCAN_RSP exchange succeeded and the response got through the hardware filter. With PASSIVE the first advertisement is delivered.

## Claim 8 - nothing changeable from unrooted adb. PARTLY (true for the timings that matter with the screen off; false for the three normal modes)

What the scan code reads:

| Source | Keys | Shell-writable on a user build? |
|---|---|---|
| DeviceConfig namespace `bluetooth` (`AdapterService.DeviceConfigListener`, lines 4947-5035; `BatchScanUtil`, `BatchScanThrottler`) | `scan_quota_count`, `scan_quota_window_millis`, `scan_timeout_millis`, `scan_upgrade_duration_millis`, `scan_downgrade_duration_millis`, `screen_off_low_power_window_millis`, `screen_off_low_power_interval_millis`, `screen_off_balanced_window_millis`, `screen_off_balanced_interval_millis`, `report_delay`, `location_denylist_*` | Only `scan_quota_count`, `report_delay`, `location_denylist_*`. Not the timeout, not the screen-off values. |
| `Settings.Global` (`ScanUtil.windowMillis/intervalMillis`, `BatchScanUtil`) | `ble_scan_low_power_window_ms`, `ble_scan_low_power_interval_ms`, `ble_scan_balanced_window_ms`, `ble_scan_balanced_interval_ms`, `ble_scan_low_latency_window_ms`, `ble_scan_low_latency_interval_ms` | **Yes** (`adb shell settings put global ...`). |
| System property (`ScanUtil.getScanAllowance`) | `bluetooth.ble.scan.scan_allowance_seconds.config` | No. |
| aconfig flags | `scan_allowance_throttling_enabled`, `upgrade_le_scan_only_screen_on`, `treat_empty_filters_as_unfiltered`, `early_reject_unauthorized_scans` | No (READ_ONLY in cp2a; not in the adb allowlist). |

Evidence:

* `frameworks/base packages/Shell/AndroidManifest.xml` lines 160-163: `<uses-permission android:name="android.permission.WRITE_DEVICE_CONFIG" android:featureFlag="!android.security.protect_device_config_flags"/>` and
  `<uses-permission android:name="android.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG" />`. `build/release aconfig/bp2a/android.security/protect_device_config_flags_flag_values.textproto`: `state: ENABLED permission: READ_ONLY` (inherited by cp2a). Shell therefore only has the allowlist permission.
* `SettingsProvider.enforceDeviceConfigWritePermission()` lines 2670-2735: without `WRITE_DEVICE_CONFIG` each flag must be in an allowlisted namespace or in `DeviceConfig.getAdbWritableFlags()`, else
  `SecurityException("Permission denial for flag '...'; allowlist permission granted, but must add flag to the allowlist")`.
* `ConfigInfrastructure WritableNamespaces.java`: adservices, autofill, app_compat_overrides, captive_portal_login, connectivity, exo, location, nearby, netd_native, network_security, on_device_personalization, profiling_testing, testing, tethering, tethering_u_or_later_native, thread_network - no `bluetooth`.
  The Bluetooth APEX ships no `etc/writable_namespaces` (prebuilts in `apex/Android.bp`: audio_set_*, bt_did.conf, bt_stack.conf, interop_database.conf).
* `WritableFlags.java` lines 202-219, all `bluetooth/` entries: `acl`, `apm_enhancement_enabled`, `bt_audio_policy_ag`, `bt_audio_policy_hf`, `bt_default_apm_state`, `com.android.bluetooth.flags.le_ase_read_multiple_variable`, `controller`, `device_name_bloomfilter`, `enable_hci_logging`, `hci`, `location_denylist_advertising_data`, `location_denylist_mac`, `location_denylist_name`, `logging_debug_disabled_for_tags`, `logging_debug_enabled_for_all`, `report_delay`, `scanning`, `scan_quota_count`.
* `Settings.java` lines 16214-16263 define the six `ble_scan_*` keys; `SettingsProvider.mutateGlobalSetting` line 1658: `enforceHasAtLeastOnePermission(Manifest.permission.WRITE_SECURE_SETTINGS)`; Shell manifest line 157 holds `WRITE_SECURE_SETTINGS`.
* sepolicy `private/property_contexts` line 32: `bluetooth.  u:object_r:bluetooth_prop:s0` (no exact entry for the allowance property); `private/shell.te` has no `set_prop(shell, bluetooth_prop)`.

Consequence: adb cannot lift the 512 ms / 10.24 s screen-off mode, the 10-minute timeout or the allowance. It can change what LOW_POWER / BALANCED / LOW_LATENCY mean
(values are read whenever the radio parameters are reconfigured). That only affects stage 1 of the timeout downgrade and screen-on background scans, so it is a debugging aid, not a design basis.
Reading is allowed: `adb shell device_config list bluetooth`, `adb shell settings list global | grep ble_scan`.

---

## Catch probability of one 1.5 s burst

Model and assumptions (script `scripts/scan_catch_model.py`, 20 000 random trials per cell, seed 12345):

* Burst length D = 1.5 s. Advertising events every a + advDelay, advDelay uniform 0-10 ms (legacy advertising). Events: about 60 (a = 20 ms), 15 (100 ms), 5 (300 ms).
* Each event = one PDU on channels 37, 38, 39, 1 ms apart, 0.4 ms air time.
* Scanner listens W out of every T on one channel, next channel each interval, random phase. A PDU counts if it lies fully inside a window on the scanned channel.
* Each such PDU is decoded with probability p, independently. p = 1.0 is the geometric upper bound; p = 0.8 a quiet link; p = 0.5 a busy radio (audio stream to the hearing aid, 2.4 GHz Wi-Fi) - assumed values, not measured.
* Not modelled: wake-up latency, controller filter behaviour, SCAN_REQ/SCAN_RSP. For the default ACTIVE scan type with a scannable advertiser roughly square p (0.64 / 0.25 columns of the script).
* Cross-check without jitter and with p = 1 (closed form, union of windows): 100 % in every cell except LOW_POWER/300 ms = 50.0 % and SCREEN_OFF = 19.5 / 18.7 / 16.7 %.

| Mode (window / interval) | adv 20 ms: p=1.0 / 0.8 / 0.5 | adv 100 ms: p=1.0 / 0.8 / 0.5 | adv 300 ms: p=1.0 / 0.8 / 0.5 |
|---|---|---|---|
| LOW_LATENCY 100 / 100 ms | 100 / 100 / 100 % | 100 / 100 / 100 % | 100 / 99.96 / 96.8 % |
| BALANCED and SCREEN_OFF_BALANCED 183 / 730 ms | 100 / 100 / 100 % | 100 / 99.2 / 90.7 % | 99.0 / 83.4 / 56.0 % |
| LOW_POWER 140 / 1400 ms | 100 / 99.99 / 98.1 % | 100 / 87.2 / 61.6 % | 50.4 / 40.3 / 25.2 % |
| SCREEN_OFF 512 / 10240 ms | 19.4 / 19.3 / 18.9 % | 19.4 / 18.8 / 17.0 % | 17.0 / 14.9 / 10.6 % |

Derived: SCREEN_OFF is (W + burst span) / T, about one press in five. With allowance throttling on and a 20 ms advertiser, the hourly average is
0.1 x 100 % + 0.9 x 19.4 % = 27.5 % when LOW_LATENCY is requested (6 min/h) and 0.4 x 100 % + 0.6 x 19.4 % = 51.6 % when BALANCED is requested (24 min/h).

---

## Surprises (not covered by the claims)

1. Allowance throttle, if on, hits foreground services too and removes both the 10-minute timeout and the FIRST_MATCH exemption; under it BALANCED buys 24 min/h, LOW_LATENCY 6 min/h.
2. Importance is sampled at `startScan` and afterwards only tracked across the FOREGROUND_SERVICE process-state boundary; BOUND_FOREGROUND_SERVICE transitions are invisible to the scanner.
3. `SCAN_TYPE_PASSIVE` does not make the radio passive; default ACTIVE settings drop the first scannable advertisement and wait for the scan response.
4. Six `Settings.Global` keys redefine the three normal modes and are adb-writable (and writable by the app after `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS` - not verified).
5. All scan timers (10-minute timeout, 6 s connecting downgrade, allowance jobs) are `Handler.postDelayed` on the uptime clock.
6. Profile CONNECTING (hearing aid reconnect) drops LOW_LATENCY scans to BALANCED for up to 6 s.
7. `setServiceData(uuid, null)` is a valid screen-off filter but is not offloaded to the controller.
8. BALANCED 183/730 catches a 1.5 s burst as well as LOW_LATENCY in the ideal model at a quarter of the radio time; the gap only opens for slow advertising intervals or a busy radio.
9. Defaults worth setting explicitly: PHY stays `PHY_LE_1M` (default, `ScanSettings.java` line 355; with `PHY_LE_ALL_SUPPORTED` the host asks for 1M and Coded scanning with the same window/interval, `ScanUtil.isPhyConfigured` - radio sharing then depends on the controller);
   `MATCH_NUM_MAX_ADVERTISEMENT` is the default and takes `totalTrackable / 4` tracking entries per filter for FIRST_MATCH (`ScanManager.getNumOfTrackingAdvertisements`).
10. `dumpsys bluetooth_manager` shows the requested mode, "(Forced)", "(Suspended)" and results screen-off/on, but not the mode actually applied; that is only in logcat (`BtScan.ScanManager`, `BtScan.ScanThrottler`).
11. Package replacement does not cancel PendingIntents in AMS (only user force-stop and uninstall do), so a PendingIntent scan probably survives `adb install -r` [inferred].

## Still needs a device test

1. `adb shell aflags list | grep -i -E "scan_allowance|upgrade_le_scan|treat_empty"`, `device_config list bluetooth`, `settings list global | grep ble_scan`, Bluetooth APEX version - on this phone, after every Play system update.
2. Logcat proof that a filtered LOW_LATENCY scan from the foreground service stays at "in scan unit=160 / 160" after screen-off, and what is logged at 10 minutes with and without a FIRST_MATCH companion scan.
3. Real catch rate of the Shelly burst per mode with the phone asleep (no wake lock), and again while audio runs to the hearing aid.
4. ACTIVE versus PASSIVE scan type with the hardware filter on service data 0xFCD2 for this (probably scannable) advertiser.
5. PendingIntent scan survival: `am kill`, Bluetooth off/on, reboot, `adb install -r`, `am force-stop`.
6. `adb shell device_config put bluetooth scan_timeout_millis 86400000` (expected: SecurityException) and `adb shell settings put global ble_scan_low_power_window_ms 1400` (expected: accepted).
7. HCI snoop: the scan parameters the controller really receives; offloaded filter and tracking-entry counts of the Pixel 10 Pro controller.
