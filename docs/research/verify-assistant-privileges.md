# Verification: assistant-role privileges (cluster "assistant-privileges")

Date: 2026-11-27. Nothing here was run on a device. Every verdict rests on source or documents that I opened
myself in this pass. Where a statement is only reasoned from those sources it is marked **[inferred]**.

## 0. What was checked, and against which version

| Project | Ref used | Commit |
|---|---|---|
| `platform/frameworks/base` | `refs/tags/android-17.0.0_r1` | `94b4c163b7dfe5ce3607f7bb8456f9573f7de57d` |
| `platform/frameworks/av` | `refs/tags/android-17.0.0_r1` | `475269ec44acec82792be6b01fb8e16357d4d2c8` |
| `platform/packages/modules/Permission` | `refs/tags/android-17.0.0_r1` | `1093eca7407576c72e51b1a88550ee0667a6e5dc` |
| `platform/packages/modules/Bluetooth` | `refs/tags/android-17.0.0_r1` | `c77db469de80f86660bc053bec4dee0c5d4b947c` |
| `platform/build/release` (flag values) | `refs/tags/android-17.0.0_r1` | `c85f4aebe46714235599a9bd6430a0fe30c6e8d5` |

URL pattern: `https://android.googlesource.com/<project>/+/refs/tags/android-17.0.0_r1/<path>#<line>`.
My own copies: `<research>/vv2/src/refs_tags_android-17.0.0_r1/`.

Version facts that matter:

- The tag `android-17.0.0_r1` and the branch head `android17-release` are the same commit in all four projects. The
  first pass used the branch; its local files are byte-identical to the tag for the six files I compared
  (`VoiceInteractionManagerServiceImpl.java`, `ActiveServices.java`, `OomAdjusterImpl.java`,
  `AudioPolicyService.cpp`, `roles.xml`, `AppOpsUidStateTrackerImpl.java`) and for `ScanManager.java`.
- `android-17.0.0_r1` is build `CP2A.260605.016` (source.android.com build-number table, saved by the first pass).
  Field reports from November 2026 show Pixels on `CP3A.2610xx` (Android 17 QPR1). **No QPR1 source is published.**
  The only later public ref is `android17-security-release` (head 2026-09-03). I diffed it against the tag: no change
  in `VoiceInteractionManagerServiceImpl.java`, `ActiveServices.java`, `BackgroundLaunchProcessController.java`,
  `OomAdjusterImpl.java`, `AppOpsUidStateTrackerImpl.java`, `AudioPolicyService.cpp`, `ScanThrottler.kt`,
  `roles.xml`, `AssistantRoleBehavior.java`; `VoiceInteractionManagerService.java` differs only by a safe-mode
  guard; `AudioService.updateAssistantUIdLocked` is unchanged.
- `refs/heads/main` of `frameworks/base` is stale (head commit dated 2025-03-26). It was not used.
- Bluetooth (`com.android.bt`) and Permission are Mainline modules. The device can carry a newer module than the tag.

## 1. Verdicts in one table

| # | Claim (short) | Verdict |
|---|---|---|
| 1 | Sideloaded app qualifies for `ROLE_ASSISTANT`; selectable in Settings; `cmd role add-role-holder` works | confirmed (source); adb command not run |
| 2 | Service kept bound with flags that give bound-foreground-service state, capabilities and background-activity-start | confirmed |
| 3 | Microphone FGS from the background is allowed and gets while-in-use because ActiveServices exempts the VoiceInteractionService app | partly: outcome right, mechanism is the bind flag, not a named exemption; lookup per UID, token per process |
| 4 | AudioRecord gets real audio with screen off and locked | confirmed in source, with one correction: a sideloaded role holder is an ordinary UID for the audio policy |
| 5 | Scan manager treats the role holder as foreground, so no always-on FGS needed to avoid screen-off scan mode | partly |
| 6 | Bound again after reboot without user action, not before first unlock unless direct-boot aware | confirmed, with a boot-time reset condition |
| 7 | FGS from a BLE scan PendingIntent broadcast is not allowed without an exemption; is the role holder exempt for the start itself? | confirmed; decision: yes, while the service is bound |

---

## 2. Claim 1 - role qualification, Settings, adb

**Qualification rule.** `PermissionController/role-controller/java/com/android/role/controller/behavior/AssistantRoleBehavior.java`:

```java
// line 194
if (!android.Manifest.permission.BIND_VOICE_INTERACTION.equals(si.permission)) { return false; }
...
// line 229
if (sessionService == null || recognitionService == null || !supportsAssist) { return false; }
```

and, as the second way in, any exported activity for `Intent.ACTION_ASSIST` (`MATCH_DEFAULT_ONLY`). The service
branch is skipped on low-RAM devices (`if (!userActivityManager.isLowRamDevice())`). No system-app, signature or
installer check exists in this file or in `Role.isPackageQualifiedAsUser` (`model/Role.java` line 650 ff.).

**Role definition.** `PermissionController/res/xml/roles.xml` lines 108-175: `exclusive="true"`,
`fallBackToDefaultHolder="true"`, `requestable="false"`, `defaultHolders="config_defaultAssistant"`. Permissions
granted by the role: SMS set, `READ_CALL_LOG`, `ACCESS_BLOBS_ACROSS_USERS`, `READ_ASSISTANT_APP_SEARCH_DATA`,
`SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE`, `EXECUTE_APP_ACTION`, `MANAGE_CONTENT_SUGGESTIONS`,
`EMBED_ANY_APP_IN_UNTRUSTED_MODE`, `SHOW_POWER_MENU`, `MANAGE_ASSISTANT_AUDIO` (flagged). **Not** `RECORD_AUDIO`,
**not** `CAPTURE_AUDIO_HOTWORD`, not `START_FOREGROUND_SERVICES_FROM_BACKGROUND`.

**Restricted settings.** `service/java/com/android/ecm/EnhancedConfirmationService.java` lines 192-210: the protected
roles are `ROLE_DIALER` and `ROLE_SMS` only.

**adb.** `service/java/com/android/role/RoleShellCommand.java` line 81 (`case "add-role-holder": return runAddRoleHolder();`)
calls `mRoleManager.addRoleHolderAsUser(...)`. `RoleService.addRoleHolderAsUser` (line 682) enforces
`Manifest.permission.MANAGE_ROLE_HOLDERS`, protection `signature|installer|module`
(`frameworks/base/core/res/AndroidManifest.xml` line 7190). The Shell package declares it
(`frameworks/base/packages/Shell/AndroidManifest.xml` line 315) and is platform-signed. The controller then runs
the same qualification check (`RoleControllerServiceImpl.onAddRoleHolder`, line 256: "Package does not qualify for
the role"). So the adb path cannot bypass qualification, and nothing restricts it to debug builds.

**Field evidence (third party, not mine).**
- home-assistant/android#7869 (2026-11-12), Pixel 7 Pro, Android 17 `CP3A.261005.002.A1`:
  `adb shell cmd role get-role-holders android.app.role.ASSISTANT` returns the third-party package.
- `hermes-vox/docs/ASSISTANT-ROLE.md` (cloned repo) lists `adb shell cmd role add-role-holder android.app.role.ASSISTANT <pkg>`
  with results "measured on a Pixel 7 (Android 17, CP3A.261005.002.A1)". Project documentation, not independently checked.

**Traps found while trying to break the claim.**
1. *Missing `recognitionService` loses the assistant on every reinstall.* GitHub issue anthropics/claude-code#41696
   (Pixel 10 Pro Fold, Android 16): "the `assistant` and `voice_interaction_service` Secure Settings are cleared on
   every APK reinstall". I opened that reporter's `voice_interaction_service.xml`: it has `sessionService` and
   `supportsAssist` but **no `recognitionService`**. That matches
   `VoiceInteractionManagerService.resetServicesIfNoRecognitionService` (line 3221), called from
   `onSomePackagesChanged` when the package re-appears: `setCurInteractor(null, userHandle); resetCurAssistant(userHandle);`.
   The claim's list of three attributes is therefore exactly the minimum.
2. *The role is re-validated on every package change.* `RoleControllerServiceImpl` lines 148-171: "Removing package
   that no longer qualifies for the role", then the default or fallback holder is added (lines 173-213). On a Pixel
   that is the Google app. A build that breaks the service meta-data silently hands the role back to Google.
3. `requestable="false"`: the app cannot ask for the role with `RoleManager.createRequestRoleIntent`.

**Verdict: confirmed** (source). The adb command was not executed by me.

---

## 3. Claim 2 - the binding and what it carries

`frameworks/base/services/voiceinteraction/java/com/android/server/voiceinteraction/VoiceInteractionManagerServiceImpl.java`, lines 1318-1333:

```java
void startLocked() {
    Intent intent = new Intent(VoiceInteractionService.SERVICE_INTERFACE);
    intent.setComponent(mComponent);
    mBound =
            mContext.bindServiceAsUser(
                    intent,
                    mConnection,
                    Context.BIND_AUTO_CREATE
                            | Context.BIND_FOREGROUND_SERVICE
                            | Context.BIND_INCLUDE_CAPABILITIES
                            | Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS,
                    new UserHandle(mUser));
```

Effect of each flag, followed through `services/core/java/com/android/server/am/psc/OomAdjusterImpl.java`:

- The client is `system_server`. Fixed-adj processes get everything (line 1253-1264):
  `app.setCurCapability(PROCESS_CAPABILITY_ALL); // BFSL allowed`. `PROCESS_CAPABILITY_ALL` includes
  `PROCESS_CAPABILITY_FOREGROUND_MICROPHONE` (`core/java/android/app/ActivityManager.java` lines 1069-1077).
- `BIND_INCLUDE_CAPABILITIES` (line 1930): `if (cr.hasFlag(Context.BIND_INCLUDE_CAPABILITIES)) { capability |= client.getCurCapability(); }`
- `BIND_FOREGROUND_SERVICE` (lines 2096-2108), inside `if (clientProcState < PROCESS_STATE_TOP)`:
  `} else if (cr.hasFlag(Context.BIND_FOREGROUND_SERVICE)) { clientProcState = PROCESS_STATE_BOUND_FOREGROUND_SERVICE;`
  It is the plain flag, not `BIND_FOREGROUND_SERVICE_WHILE_AWAKE` (which is conditioned on `isDeviceFullyAwake()`),
  so the state does not drop when the screen goes off.
- Capabilities survive at that state (line 2234): "Procstates below BFGS should never have these capabilities" -
  stripping applies only when `procState > PROCESS_STATE_BOUND_FOREGROUND_SERVICE`.
- `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS`: `ActiveServices.bindServiceLocked` line 4275 requires the binder to hold
  `START_ACTIVITIES_FROM_BACKGROUND`; line 4412 `s.setAllowedBgActivityStartsByBinding(true)`;
  `ServiceRecord.getBackgroundStartPrivilegesWithExclusiveToken` (line 1503) returns
  `BackgroundStartPrivileges.ALLOW_BAL`; `core/java/android/app/BackgroundStartPrivileges.java` line 36:
  "Allow activity starts (and implies allowing foreground service starts)" = `(true, true, null)`. The token is put
  on the host process (`ServiceRecord.setProcess`, line 1257-1261; `ProcessRecord.addOrUpdateBackgroundStartPrivileges`).
- UID level: `OomAdjuster.updateAppUidRecLSP` line 1582: `uidRec.setCurCapability(uidRec.getCurCapability() | app.getCurCapability());`

"Kept bound": class javadoc, `core/java/android/service/voice/VoiceInteractionService.java` lines 73-75: "The current
VoiceInteractionService that has been selected by the user is kept always running by the system". Rebinding paths I
read: force-stop (`onHandleForceStop`, line 3268: "The user is force stopping our current interactor, restart the
service."), stop from the task manager (`onBindingDied` -> `handleUserStop`, line 596), package update
(`onSomePackagesChanged` -> `switchImplementationIfNeededLocked(true)`).

Not bound: before first unlock for a non-direct-boot-aware service (claim 6), in safe mode
(`if (!mSafeMode)`, line 975), when `Settings.Secure.VOICE_INTERACTION_SERVICE` is empty (activity-only assistant, or
the boot-time reset in claim 6).

**Verdict: confirmed.**

Side finding (future risk, not a present defect): Android 17 contains a second, parallel capability computation,
`psc/CapabilityController.java`. Its filter for service bindings is (lines 293-295)
`// TODO: b/476905700 - Add more policies.` / `return evaluateBfslPolicy(edge) | evaluateAudioPolicy(edge) | evaluateCpuTimePolicy(edge);`
i.e. it does not yet model `BIND_INCLUDE_CAPABILITIES` for the microphone. At this tag it is not authoritative:
`OomAdjusterImpl.java` line 937 "Only triggers computations without using its result." If a later release switches
to it before that TODO is done, the microphone capability of a bound assistant would disappear. Check
`curCapability` on the real build.

---

## 4. Claim 3 - microphone FGS from the background

There is **no check named after VoiceInteractionService, the assistant role or any role holder** in
`ActiveServices.java` (grep for `voiceinteract`, `ROLE_ASSISTANT`, `REASON_ROLE` finds only `REASON_ROLE_EMERGENCY`;
`AppRestrictionController` knows `ROLE_DIALER` and `ROLE_EMERGENCY`). The exemption is a consequence of the bind
flags in claim 2. The exact checks:

While-in-use, `ActiveServices.shouldAllowFgsWhileInUsePermissionLocked`, lines 9226-9240:

```java
// Allow FGS while-in-use if the WindowManager allows background activity start.
// This is mainly to get the 10 seconds grace period if any activity in the caller has
// either started or finished very recently. The binding flag
// BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS is also allowed by the check here.
final Integer allowedType = mAm.mProcessList.searchEachLruProcessesLOSP(false, pr -> {
    if (pr.uid == callingUid) {
        if (pr.getWindowProcessController().areBackgroundFgsStartsAllowed()) {
            return REASON_ACTIVITY_STARTER;
        }
    }
    return null;
});
```

`areBackgroundFgsStartsAllowed()` -> `BackgroundLaunchProcessController.areBackgroundActivityStartsAllowed` with
`CHECK_FOR_FGS_START`; lines 143-149 "Allow if the token is explicitly allowed", and lines 205-210:
`if (isCheckingForFgsStart) { if (!backgroundStartPrivileges.allowsBackgroundFgsStarts()) { continue; } return new BalVerdict(BAL_ALLOW_TOKEN, "process allowed by token"); }`

The start itself, `shouldAllowFgsStartForegroundNoBindingCheckLocked`, line 9507: `int ret = allowWhileInUse;` -
any while-in-use reason also allows the start. Independent second path, lines 9517-9527:
`(procstate <= PROCESS_STATE_BOUND_TOP) || (uidBfsl && (procstate <= PROCESS_STATE_BOUND_FOREGROUND_SERVICE))`.

Type check for `microphone`, `validateForegroundServiceType` line 2985-2987 passes `r.isFgsAllowedWiu_forStart()`
into `ForegroundServiceTypePolicy`; policy `FGS_TYPE_POLICY_MICROPHONE`
(`core/java/android/app/ForegroundServiceTypePolicy.java` lines 470-489) is all-of `FOREGROUND_SERVICE_MICROPHONE`,
any-of (..., `RECORD_AUDIO`), `foregroundOnlyPermission = true`; lines 1260-1264:
`case MODE_FOREGROUND: return !isFgsTypeFgPermissionEnforcementEnabled() || allowWhileInUse ? PERMISSION_GRANTED : PERMISSION_DENIED;`

The evaluation happens when `startForegroundService()` is called (`startServiceLocked` line 1054,
`setFgsRestrictionLocked(callingPackage, callingPid, callingUid, service, r, backgroundStartPrivileges, false)`).

**Per UID or per component?** The token belongs to the `ServiceRecord` of the VoiceInteractionService component and
is attached to the *process* that hosts it. The lookup iterates all processes with `pr.uid == callingUid`. So: the
caller may be any component in any process of the same UID (BLE callback, manifest receiver, another service); a
different UID gets nothing. The exemption exists exactly as long as the binding and its host process exist.

Official wording (https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start, "Last
updated 2026-11-16 UTC"), list "Exemptions from restrictions on while-in-use permissions": "The service starts by
an app which provides the VoiceInteractionService."

**Verdict: partly.** The outcome is supported by code and documentation. The detail that is wrong: ActiveServices
does not "exempt the app that provides the active VoiceInteractionService" by identity. It honours a
background-activity-start token on any process of the calling UID.

---

## 5. Claim 4 - real audio with screen off and locked

Three gates, all read at the tag.

**(a) App-op.** `services/core/java/com/android/server/appop/AppOpsUidStateTrackerImpl.java`, lines 144-160 and 175-177:

```java
int opCapability = getOpCapability(code);
if (opCapability != PROCESS_CAPABILITY_NONE) {
    if ((uidCapability & opCapability) == 0) { return MODE_IGNORED; } else { return MODE_ALLOWED; }
}
...
case OP_RECORD_AUDIO:
case OP_RECEIVE_EXPLICIT_USER_INTERACTION_AUDIO:
    return PROCESS_CAPABILITY_FOREGROUND_MICROPHONE;
```

Per UID. The bound assistant UID has the capability (claim 2) with or without its own foreground service.

**(b) Audio policy.** `frameworks/av/services/audiopolicy/service/AudioPolicyService.cpp`, `updateUidStates_l`
(line 882 ff.). Relevant lines: 952 sensor privacy silences everything; 968 `if (appState == APP_STATE_IDLE) { continue; }`;
1042 "if no active client with UI on Top, consider latest active as top"; 1116-1122
`if (!isAssistantOnTop || isActiveAssistant) { allowCapture = (isTopOrLatestActive || isTopOrLatestSensitive) && allowSensitiveCapture && canCaptureIfInCallOrCommunication; }`;
1124 `if (!current->hasOp()) { // Never allow capture if app op is denied`. `apmStatFromAmState` (line 1219):
unknown -> IDLE, `<= PROCESS_STATE_TOP` -> TOP, everything else -> FOREGROUND. A UID at bound-foreground-service
state is active, hence `APP_STATE_FOREGROUND`, hence eligible as "latest active".

**(c) Lock or screen state.** grep for `keyguard`, `screen`, `interactive`, `isDeviceLocked` in
`AudioPolicyService.cpp`, `AudioPolicyInterfaceImpl.cpp`, `AudioRecordClient.cpp`, `AppOpsService.java`,
`AppOpsUidStateTrackerImpl.java`: no rule. An ordinary app is silenced under the keyguard because its UID loses the
capability or goes idle once its activity is stopped, not because of the lock. The assistant differs only in that the
system binding keeps capability and state.

**Correction that matters: the audio policy does not treat a sideloaded role holder as an assistant.**
`services/core/java/com/android/server/audio/AudioService.java`, `updateAssistantUIdLocked`, lines 3489-3499:

```java
if (!TextUtils.isEmpty(packageName)) {
    PackageManager pm = mContext.getPackageManager();
    if (pm.checkPermission(CAPTURE_AUDIO_HOTWORD, packageName) == PackageManager.PERMISSION_GRANTED) {
        try { assistantUid = pm.getPackageUidAsUser(packageName, getCurrentUserIdFromCache()); }
```

`CAPTURE_AUDIO_HOTWORD` is `signature|privileged|role` (`core/res/AndroidManifest.xml` line 7518) and the ASSISTANT
role does not grant it (roles.xml above; only `SYSTEM_AMBIENT_AUDIO_INTELLIGENCE` does, line 996). So
`mUidPolicy->isAssistantUid(ourUid)` is false. The official page agrees
(https://developer.android.com/media/platform/sharing-audio-input, "Last updated 2026-10-28 UTC"): "The Assistant is
a privileged app because it is pre-installed and it holds the role RoleManager.ROLE_ASSISTANT. Any other
pre-installed app with this role is treated similarly." and "Two ordinary apps can never capture audio at the same
time."

Consequences of being "ordinary":
- Our capture is allowed when our UID is the top or the most recently started non-HOTWORD recorder, no other app
  holds a privacy-sensitive capture (`CAMCORDER`, `VOICE_COMMUNICATION`), and no call is active
  (`isInCall && !canCaptureCall` -> silence; `canCaptureCall` needs `CAPTURE_AUDIO_OUTPUT`).
- Source `HOTWORD` is not available (`AudioPolicyInterfaceImpl.cpp` line 756: `check_perm(CAPTURE_AUDIO_HOTWORD, ...)`).
- The assistant-only restriction "with an accessibility service on top only VOICE_RECOGNITION or HOTWORD" (lines
  1130-1153; cited in the first pass's `speech.md` line 159) does **not** apply to us.
- Silencing is immediate for apps targeting 34+: `AudioPolicyInterfaceImpl.cpp` lines 912-916, "apps which targetSdk
  below 34 can continue unsilenced recording when they move to a bkgd UID state".

Field evidence (third party): GPTWake README, Android 16 tablet: with ChatGPT (not a system app) as default assistant
capture under keyguard works; "Taking the role away yields RECORD_AUDIO duration=0; restoring it restores capture".

**Verdict: confirmed** in source, with the correction above. Not measured.

---

## 6. Claim 5 - LE scan manager and "foreground"

Test used, `packages/modules/Bluetooth/android/app/src/com/android/bluetooth/le_scan/ScanManager.java`:

```java
// line 94
private static final int FOREGROUND_SERVICE_IMPORTANCE_CUTOFF = IMPORTANCE_FOREGROUND_SERVICE;
// lines 726-731
boolean isAppForeground(ScanClient client) {
    if (mUidImportanceMap.indexOfKey(client.getAppUid()) < 0) { return DEFAULT_UID_IS_FOREGROUND; }
    return mUidImportanceMap.get(client.getAppUid()) <= IMPORTANCE_FOREGROUND_SERVICE;
}
```

The map is filled (i) at every scan start, `fetchAppForegroundState` (line 459):
`importance = Math.min(importance, mActivityManager.getPackageImportance(packageName));` and (ii) by
`mActivityManager.addOnUidImportanceListener(mForegroundServiceImportanceListener, FOREGROUND_SERVICE_IMPORTANCE_CUTOFF)` (line 259).

Mapping, `core/java/android/app/ActivityManager.java` lines 4239-4245:
`} else if (procState >= PROCESS_STATE_IMPORTANT_FOREGROUND) { return IMPORTANCE_VISIBLE; } else if (procState >= PROCESS_STATE_FOREGROUND_SERVICE) { return IMPORTANCE_FOREGROUND_SERVICE; }`.
`PROCESS_STATE_BOUND_FOREGROUND_SERVICE` lies between the two, so it maps to `IMPORTANCE_FOREGROUND_SERVICE` (125).
**The role holder does count as foreground.** That part of the claim holds.

What the claim leaves out:

1. *Foreground is necessary, not sufficient.* `ScanThrottler.throttleScanMode` (`ScanThrottler.kt` lines 68-103): for
   a foreground app with the screen off `SCAN_MODE_LOW_POWER -> SCAN_MODE_SCREEN_OFF` (512 ms / 10240 ms),
   `BALANCED -> SCREEN_OFF_BALANCED` (183/730), `LOW_LATENCY -> LOW_LATENCY`. Scans without a non-empty filter are
   suspended with the screen off (`requiresScreenOn`, `ScanUtil.kt`).
2. *The 10-minute timeout ignores foreground state.* `DEFAULT_SCAN_TIMEOUT = 10.minutes` (`ScanUtil.kt` line 52);
   `handleRegularScanTimeout` (ScanManager line 1221) caps a filtered scan at `SCAN_MODE_LOW_POWER`, and line 76 of
   the throttler `if (!scanManager.isAppForeground(client) || isForceDowngradedScanClient(client))` then limits it to
   `SCAN_MODE_SCREEN_OFF` with the screen off. Exempt: `CALLBACK_TYPE_FIRST_MATCH` clients
   (`isExemptFromScanTimeout`, line 343) or a restart of the scan.
3. *Stale importance.* The listener's cut point is converted with `importanceToProcState` (ActivityManager lines
   4301-4302: `importance >= IMPORTANCE_FOREGROUND_SERVICE -> PROCESS_STATE_FOREGROUND_SERVICE`), and
   `UidObserverController` (lines 396-402) reports only when `procState <= cutpoint` changes. Bound-foreground-service
   (one step below foreground-service) is on the *background* side of that cut. A change from a background state to
   bound-foreground-service is therefore **not reported**. A real foreground service crosses the cut and is reported.
   If a scan is started before the VoiceInteractionService binding exists (for example from a boot receiver), the
   scan manager keeps the old "background" value until the scan is restarted.
4. *The binding is not always there* (claim 6).
5. *Flag `scan_allowance_throttling_enabled`* (`flags/le_scanning.aconfig`, bug 478996075). With it,
   `applyAllowanceThrottling` (`ScanThrottler.kt` line 273) exempts only
   `scanManager.hasPrivilegedPermission(client) || scanManager.isAppForegroundUi(client)` (importance <=
   `IMPORTANCE_FOREGROUND`); everyone else gets `DEFAULT_SCAN_ALLOWANCE_SECONDS = 6.minutes` of LOW_LATENCY
   equivalent per `ALLOWANCE_REFILL_WINDOW = 1.hours` (weights 100/25/10), then `SCAN_MODE_SCREEN_OFF`. Neither the
   role binding nor a foreground service helps. I looked up the value: in `platform/build/release` at the tag,
   `aconfig/cp2a/com.android.bluetooth.flags/` (141 files) and `aconfig/trunk_staging/...` (800 files) contain no
   entry for it, so the AOSP default is DISABLED. The flag is not marked read-only and Bluetooth is a Mainline
   module, so the device state is unknown.

**Verdict: partly.** Correct: bound-foreground-service passes the scan manager's foreground test. Incorrect as a
design rule: "no foreground service needed" holds only if scans are started or restarted after the binding exists,
and foreground status alone does not keep a scan out of the screen-off mode.

---

## 7. Claim 6 - after reboot

`VoiceInteractionManagerService.java`:

- line 313-318: `onUserUnlocking` -> `mServiceStub.initForUser(...)`; `mServiceStub.switchImplementationIfNeeded(false);`
- line 982-984, in `switchImplementationIfNeededNoTracingLocked`:
  `serviceInfo = AppGlobals.getPackageManager().getServiceInfo(serviceComponent, 0, mCurUser);` - flags `0`.
- `services/core/java/com/android/server/pm/ComputerEngine.java` lines 2783-2790: "Caller expressed no opinion, so
  match based on user state": unlocking or unlocked -> `MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE`,
  otherwise `MATCH_DIRECT_BOOT_AWARE` only.

So at `PHASE_THIRD_PARTY_APPS_CAN_START` (`systemRunning`) with a locked user a non-direct-boot-aware service resolves
to null and nothing is bound; at unlock it resolves and `startLocked()` binds it. A direct-boot-aware service is bound
at boot and is not rebound at unlock (`force == false`, same component). No app launch and no `BOOT_COMPLETED`
receiver is involved.

Condition that can break it (first pass already had it; I re-read the code and found a matching field report):
`initForUserNoTracing` (lines 753-870) returns early ("Current interactor/recognizer okay, done!", line 848) only if
`Settings.Secure.VOICE_RECOGNITION_SERVICE` names an installed recogniser with `isSelectableAsDefault()`. Otherwise
`findAvailInteractor(userHandle, null)` skips every non-system package (line 1048) and `setCurInteractor(...)`
overwrites our setting. home-assistant/android#7869, Pixel 7 Pro, GrapheneOS, Android 17 `CP3A.261005.002.A1`: role
still held, `voice_interaction_service` empty, `voice_recognition_service` null, `dumpsys voiceinteraction` says
"(No active implementation)", logcat "no auto selectable voice recognition services found for user 0". This is also
evidence that the QPR1 build behaves like the tag here. On a stock Pixel a Google recogniser is expected to be set;
that is an assumption until read from the device.

**Verdict: confirmed.**

---

## 8. Claim 7 - FGS start from a BLE scan PendingIntent broadcast

- Scan results are sent without options: `le_scan/ScanController.java` line 513
  `pendingIntent.send(mAdapterService, 0, extrasIntent);`. `PendingIntentRecord.sendInner` allow-lists only when a
  duration was registered for that PendingIntent (lines 558-613) and, for broadcasts, passes
  `getBackgroundStartPrivilegesForActivitySender(mAllowBgActivityStartsForBroadcastSender, allowlistToken, options, callingUid)`
  (lines 669-683), which is empty here.
- Without another reason `shouldAllowFgsStartForegroundNoBindingCheckLocked` returns `REASON_DENIED` and
  `startServiceLocked` throws `ForegroundServiceStartNotAllowedException` (line 1099 ff.). Battery-optimisation
  exemption is one of the listed reasons (`isAllowlistedForFgsStartLOSP`, `REASON_SYSTEM_ALLOW_LISTED`); the
  documentation says "The user turns off battery optimizations for your app."
- **Decision: the bound role holder is exempt for the start itself.** Line 9507 `int ret = allowWhileInUse;` with
  `REASON_ACTIVITY_STARTER` from the token; second path line 9524 (bound-foreground-service state plus BFSL
  capability). In the documentation this is the entry "Your app can start an activity from the background" in
  "Exemptions from background start restrictions"; the VoiceInteractionService line is listed only under the
  while-in-use exemptions.
- Caveat for the alternative: battery-optimisation exemption allows the start but not the type. For a `microphone`
  service the type check needs while-in-use (section 4) and otherwise throws `SecurityException` ("... and the app
  must be in the eligible state/exemptions to access the foreground only permission"). The doc says the same: "it
  cannot create the service while the app is in the background, even if the app falls into one of the exemptions
  from background start restrictions".

**Verdict: confirmed.** The exemption is tied to the live binding, not to holding the role.

---

## 9. Surprises (not covered by the claims)

1. A sideloaded role holder is an ordinary app for audio capture concurrency (section 5).
2. Every privilege depends on the binding. There is no role check in ActivityManager. Activity-only assistants,
   the pre-unlock state and the boot-time reset all mean "no privileges".
3. Missing `recognitionService` -> assistant settings cleared at each reinstall (claude-code#41696 plus source).
4. Role re-validation on package change with fallback to the default holder (Google on a Pixel).
5. Scan manager: importance cut point makes bound-foreground-service transitions invisible (section 6, item 3).
6. Scan allowance throttling exists behind a server-changeable flag; default off in AOSP release config `cp2a`.
7. Parallel `CapabilityController` does not yet carry the microphone capability over bindings (section 3).
8. The device build (CP3A, QPR1) has no public source; Bluetooth and Permission are Mainline modules.
9. Force-stop does not unset the assistant; the system rebinds at once. Force-stop still cancels the app's
   PendingIntents, so a PendingIntent scan must be registered again from `VoiceInteractionService.onReady()`.
10. `MANAGE_ASSISTANT_AUDIO` (`signature|role`, flagged) is granted by the role and gates `STREAM_ASSISTANT` volume
    and `MODE_ASSISTANT_CONVERSATION` (`AudioService.java` lines 4371, 5442, 7481). Not explored further.

## 10. Still to be tested on the device

See the structured result; in short: identity of build and modules, role assignment by Settings and by adb, binding
after reboot before and after first unlock, `curCapability` with screen off after a long idle period, a BLE-triggered
microphone FGS with RMS measurement and a control run without the role, scan importance and effective scan mode from
`dumpsys bluetooth_manager`, state of `scan_allowance_throttling_enabled`, capture behaviour while another app
records and during a call.

## 11. Sources opened in this pass

- AOSP files listed in the sections above (tag `android-17.0.0_r1`), plus `android17-security-release` for diffs.
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start (2026-11-16)
- https://developer.android.com/media/platform/sharing-audio-input (2026-10-28)
- GitHub: home-assistant/android issues 7869, 7825, 6535, 6637, PR 6639, PR 7259; anthropics/claude-code issue 41696
  and the reporter's repository files `voice_interaction_service.xml`, `AndroidManifest.xml`, `deploy.sh`.
- Local clones from the first pass: `hermes-vox/docs/ASSISTANT-ROLE.md`, `GPTWake/README.md`, `home-assistant/android`
  at `b4d7cbb9` (`AssistVoiceInteractionService.kt`).
