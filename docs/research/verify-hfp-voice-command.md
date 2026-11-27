# Verification: cluster hfp-voice-command (HFP AT+BVRA -> ACTION_VOICE_COMMAND)

Date: 2026-11-27. Nothing here was run on a device. Every "confirmed" below rests on source I opened myself at the
Android 17 release tag. Where the only evidence is a third-party report, it says so.

## 0. Sources and versions

| What | Ref | Commit |
|---|---|---|
| `platform/packages/modules/Bluetooth` | `refs/tags/android-17.0.0_r1` (= head of `android17-release`) | `c77db469de80f86660bc053bec4dee0c5d4b947c` |
| `platform/frameworks/base` | `refs/tags/android-17.0.0_r1` (= head of `android17-release`) | `94b4c163b7dfe5ce3607f7bb8456f9573f7de57d` |
| `platform/frameworks/native`, `platform/packages/modules/Permission` | `refs/tags/android-17.0.0_r1` | fetched per file |
| Bluetooth `android17-security-release` | `f8d5c2889871aea741c5c265b90d215136374896` | `HeadsetService.java` and the VR part of `HeadsetSystemInterface.java` are byte-identical to r1 |

`git ls-remote` shows `android-17.0.0_r1` is the only Android 17 platform tag (plus `android-security-17.0.0_r1..r3`).
There is no `android17-qpr1-release` branch. `refs/heads/main` of the Bluetooth module is stale (2025-03-17) and was not used.

Version caveat that applies to every verdict:
- `android-17.0.0_r1` is build `CP2A.260605.016` (source.android.com build-numbers page, local copy `research/buildnumbers.txt` line 434).
- Field reports from October/November 2026 show Pixels on build `CP3A.261005.005`. Source for that build is not public.
- The Bluetooth stack is a Mainline module (`com.google.android.bluetooth` in the logs) and can be updated outside OS updates.
- So the code I read is the newest public code, not provably the code on the phone. The two field logs from `CP3A.261005.005` match the r1 code path.

Local copies of what I fetched: `research/verify-hfp/` (single files) and `repos/verify-hfp-fwb/` (sparse checkout of frameworks/base at the tag).
The earlier clone `repos/aosp-bluetooth-a17` is at the right commit (checked with `git rev-parse HEAD`).

URL pattern for every file cited: `https://android.googlesource.com/<project>/+/refs/tags/android-17.0.0_r1/<path>#<line>`.

---

## Claim 1 - AT+BVRA=1 ends in `startActivity(ACTION_VOICE_COMMAND)`; role and VoiceInteractionService play no part

**Verdict: confirmed** (source, r1).

Call chain, each step opened:

1. `system/bta/ag/bta_ag_cmd.cc` line 97: `{"+BVRA", BTA_AG_AT_BVRA_EVT, BTA_AG_AT_SET, BTA_AG_AT_INT, 0, 1},`
   Only values 0 and 1 are accepted. Line 1132: if the AG feature `BTA_AG_FEAT_VREC` is off the stack answers ERROR itself.
   `system/btif/src/btif_hf.cc` lines 175-178: `BTA_AG_FEAT_VREC` is in the default AG feature set (overridable by sysprop `bluetooth.hfp.hf_features`).
2. `system/btif/src/btif_hf.cc` line 612:
   `case BTA_AG_AT_BVRA_EVT: bt_hf_callbacks->VoiceRecognitionCallback((p_data->val.num == 1) ? BTHF_VR_STATE_STARTED : BTHF_VR_STATE_STOPPED, ...)`
3. `android/app/src/com/android/bluetooth/hfp/HeadsetStateMachine.java` line 1937:
   ```java
   private void processVrEvent(int state) {
       if (state == HeadsetHalConstants.VR_STATE_STARTED) {
           if (!mHeadsetService.startVoiceRecognitionByHeadset(mDevice)) {
               mNativeInterface.atResponseCode(mDevice, HeadsetHalConstants.AT_RESPONSE_ERROR, 0);
           }
   ```
4. `hfp/HeadsetService.java` line 1639 `startVoiceRecognitionByHeadset`, line 1711: `if (!mSystemInterface.activateVoiceRecognition(fromDevice)) {`
5. `hfp/HeadsetSystemInterface.java` lines 349-364:
   ```java
   boolean activateVoiceRecognition(BluetoothDevice fromDevice) {
       Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);
       intent.putExtra(BluetoothDevice.EXTRA_DEVICE, fromDevice);
       intent.putExtra(BluetoothProfile.EXTRA_PROFILE, BluetoothProfile.HEADSET);
       intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
       ...
           mHeadsetService.startActivity(intent);
   ```
   No package, no component, no role lookup. `ActivityNotFoundException` and `SecurityException` make it return false (headset gets ERROR at once).

Role not involved:
- `PermissionController/res/xml/roles.xml` at the tag: the `android.app.role.ASSISTANT` role has no `<preferred-activities>` block. `grep VOICE_COMMAND roles.xml` returns nothing. The roles that do have preferred activities are HOME, DIALER, SMS, NOTES and AUTOMOTIVE_NAVIGATION.
- `AssistantRoleBehavior.java` mentions only `Intent.ACTION_ASSIST` (line 169).
- `grep -rn VOICE_COMMAND` over `services/core/.../wm`, `pm`, `am`, `policy`, `media` and `core/java/android/app` of frameworks/base at the tag hits nothing outside `Intent.java`.
- Resolution is `ResolveIntentHelper.chooseBestActivity` (line 153): one match -> that one; several of equal priority -> saved preferred activity, else the system resolver (line 207 `ri = new ResolveInfo(mResolveInfoSupplier.get());`).

The stack refuses before the activity start (headset gets ERROR immediately), `HeadsetService.java` lines 1643-1719, when:
- a previous session was not stopped (`mVoiceRecognitionStarted`): it calls `stopVoiceRecognition` itself and logs "please try again". One gesture is lost.
- `!isAudioModeIdle()`: a call, a virtual call or voice recognition is active.
- `isAudioOn()`: SCO is already connected. In the non-AMSCO path it also calls `disconnectAudio()`.
- another headset request is pending.
- `setActiveDevice(fromDevice)` fails.

Field evidence on Pixel / Android 17 (third party, read by me through `gh api`):
- home-assistant/android issue 7834, comment 2026-11-08T20:14:51Z:
  `START u0 { act=android.intent.action.VOICE_COMMAND flg=0x10000000 cmp=com.google.android.googlequicksearchbox/com.google.android.voicesearch.handsfree.HandsFreeActivity } with LAUNCH_MULTIPLE from uid 1002 (com.google.android.bluetooth) result code=0`
  while Home Assistant held the assistant role. The log was reformatted by an AI tool (the BAL verdict that r1 prints before `result code` is missing).
- Codename-11/hermes-relay issue 774: same on a Pixel 8a, build `CP3A.261005.005`, with the app's `VoiceInteractionService` active.

---

## Claim 2 - only a partial wake lock of about 5 s; nothing turns the display on

**Verdict: confirmed** (source, r1).

- `HeadsetSystemInterface.java` lines 70-72:
  `powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ":VoiceRecognition"); mVoiceRecognitionWakeLock.setReferenceCounted(false);`
- `HeadsetService.java` line 157: `@VisibleForTesting static int sStartVrTimeoutMs = 5000;`
  lines 1724-1725: `if (!mSystemInterface.getVoiceRecognitionWakeLock().isHeld()) { mSystemInterface.getVoiceRecognitionWakeLock().acquire(sStartVrTimeoutMs); }`
- It is released early, in `startVoiceRecognition` (lines 855-857), when the app answers.
- `grep -rn "wakeUp|userActivity|ACQUIRE_CAUSES_WAKEUP|FULL_WAKE_LOCK|SCREEN_BRIGHT|SCREEN_DIM|KeyguardManager|requestDismissKeyguard|turnScreenOn"` over the whole Bluetooth app source at the tag: no hit. Every `newWakeLock` in the app is `PARTIAL_WAKE_LOCK`.
- Window manager side, `wm/Task.java` line 5423: the display is woken only `if (next != null && next.canTurnScreenOn() && !mWmService.mPowerManager.isInteractive())`.
  `wm/ActivityRecord.java` line 9311: `canTurnScreenOn()` returns false unless the activity has the turn-screen-on flag.

Consequence found on the way: once the app has answered, the Bluetooth stack holds no wake lock for the session. The app must hold its own from the first line of the trampoline.

---

## Claim 3 - app must call `BluetoothHeadset.startVoiceRecognition(device)` within about 5 s; BLUETOOTH_CONNECT is enough; SCO opens

**Verdict: confirmed** (source, r1), with conditions the claim does not mention.

- Timeout: `HeadsetService.java` lines 1720-1722 post `VoiceRecognitionTimeoutEvent` after `sStartVrTimeoutMs` (5000 ms).
  Its `run()` (lines 1607-1625) sends `HeadsetStateMachine.VOICE_RECOGNITION_RESULT, 0 /* fail */`.
  `HeadsetStateMachine.java` lines 1062-1079 turn that into `AT_RESPONSE_ERROR`; success turns into `AT_RESPONSE_OK`.
- Public API: `framework/java/android/bluetooth/BluetoothHeadset.java` line 717
  `@RequiresLegacyBluetoothPermission @RequiresBluetoothConnectPermission @RequiresPermission(BLUETOOTH_CONNECT) public boolean startVoiceRecognition(BluetoothDevice device)`.
  `framework/api/current.txt` line 882 lists it in the public API (also `isVoiceRecognitionSupported` 880, `stopVoiceRecognition` 883).
- Server side: `hfp/HeadsetServiceBinder.java` line 174 -> `getService(source)` -> `Util.checkProfileAvailable`, `Util.checkCallerIsSystemOrActiveOrManagedUser`, `Util.enforceConnectPermissionForDataDelivery` (`Util.kt` line 672: `enforcePermissionForDataDelivery(context, BLUETOOTH_CONNECT, source, message)`). No privileged permission.
- SCO: `HeadsetService.startVoiceRecognition` (line 786). With a pending headset request it cancels the timeout, releases the wake lock, sends `VOICE_RECOGNITION_RESULT, 1 /* success */` (line 889), then
  `if (!mSystemInterface.isScoManagedByAudioEnabled()) { stateMachine.sendMessage(HeadsetStateMachine.CONNECT_AUDIO, device);`
  -> `HeadsetStateMachine` line 1293 -> `mNativeInterface.connectAudio(mDevice)` -> `btif_hf.cc` `ConnectAudio` -> `BTA_AgAudioOpen`.
  If "SCO managed by audio" is on, the Bluetooth process instead calls `am.setCommunicationDevice(<TYPE_BLUETOOTH_SCO device>)` (`HeadsetSystemInterface.startScoViaAudioManager`, lines 415-436) and the audio framework creates the link.
  Which variant a Pixel 10 Pro uses is unknown. `AudioManager.isScoManagedByAudio()` is `@SystemApi @hide`. `dumpsys audio` prints `android.media.audio.scoManagedByAudio:` (`AudioService.java` line 5745).

`startVoiceRecognition` returns false (and the pending headset request then times out with ERROR) when:
- `isVoiceRecognitionSupported(device)` is false: `btif_hf.cc` line 1059 `if (!(btif_hf_cb[idx].peer_feat & BTA_AG_PEER_FEAT_VREC)) return UNSUPPORTED`. The hearing aid must advertise voice recognition in `AT+BRSF`.
- the audio mode is not idle, SCO is already on, the device is not connected, or `mVoiceRecognitionStarted` is still true.

Useful detail: with a pending headset request the service ignores a wrong `device` argument and "fall[s] back to requesting device" (lines 841-851).
Without a pending request the same call sends `+BVRA: 1` to the headset and opens SCO (`VOICE_RECOGNITION_START`, line 891). That is an app-initiated way to open the link for triggers that do not come from the hearing aid.

The proxy must exist first: `BluetoothAdapter.getProfileProxy(..., BluetoothProfile.HEADSET)` is asynchronous. `startVoiceRecognition` on an unattached proxy logs "Proxy not attached to service" and returns false.

---

## Claim 4 - locked, display off: the trampoline still gets `onCreate`; display stays off

**Verdict: partly.** True after the first unlock since boot, by source. Not true before first unlock unless the activity is direct-boot aware. Not device-tested.

### 4.1 Background-activity-launch rules (the gap the first pass left open)

The Bluetooth app has `android:persistent="false"` (`android/app/AndroidManifest.xml` line 98) and requests neither `START_ACTIVITIES_FROM_BACKGROUND` nor `SYSTEM_ALERT_WINDOW`. UID 1002 is not in the allowlist (`BackgroundActivityStartController.java` lines 1275-1277 list only ROOT, SYSTEM, NFC). The exemption is indirect:

1. `service/src/com/android/server/bluetooth/BluetoothManagerService.java` line 1498: `int flags = Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT;` (system_server binds the Bluetooth app).
2. `services/core/java/com/android/server/am/psc/OomAdjusterImpl.java` lines 2014-2022:
   ```java
   if (cr.hasFlag(Context.BIND_ABOVE_CLIENT | Context.BIND_IMPORTANT)) {
       if (clientAdj >= PERSISTENT_SERVICE_ADJ) {
           newAdj = clientAdj;
       } else {
           // make this service persistent
           newAdj = PERSISTENT_SERVICE_ADJ;
           schedGroup = SCHED_GROUP_DEFAULT;
           procState = ActivityManager.PROCESS_STATE_PERSISTENT;
   ```
3. `BackgroundActivityStartController.java` line 452: `mIsCallingUidPersistentSystemProcess = mCallingUidProcState <= ActivityManager.PROCESS_STATE_PERSISTENT_UI;`
   lines 1296-1299: `if (state.mIsCallingUidPersistentSystemProcess) { return new BalVerdict(BAL_ALLOW_ALLOWLISTED_COMPONENT, "callingUid is persistent system process").allowNewTask(); }`
4. `checkActivityAllowedToStart` (line 1512): `FLAG_ACTIVITY_NEW_TASK` plus `balVerdict.allowsNewTask()` -> allowed.

So the expected logcat on r1 is `... from uid 1002 (com.google.android.bluetooth) (BAL_ALLOW_ALLOWLISTED_COMPONENT ...) result code=0`.

### 4.2 Keyguard and sleeping state

`grep -n -i "sleep|keyguard|isInteractive|wakeUp"` in `ActivityStarter.java` finds no abort or defer. Path for a new task:
- `ActivityStarter.startActivityInner` -> `RootWindowContainer.resumeFocusedTasksTopActivities` -> `Task.resumeTopActivityUncheckedLocked` (line 5369) -> `TaskFragment.resumeTopActivity`.
- Task visibility (`WindowContainerVisibilityHelperImpl.getTaskFragmentVisibility`) is z-order only. No sleep or keyguard test.
- `TaskFragment.java` line 1680, for an activity whose process is not attached: `next.setVisibility(true); mTaskSupervisor.startSpecificActivity(next, true, true);`
- `ActivityTaskSupervisor.realStartActivityLocked` (line 809) schedules `LaunchActivityItem` plus, line 1063, `lifecycleItem = new ResumeActivityItem(...)`. No sleep or keyguard condition.
- Afterwards `Task.java` lines 5421-5429:
  ```java
  if (shouldSleepActivities()) {
      final ActivityRecord next = topRunningActivity(true /* focusableOnly */);
      if (next != null && next.canTurnScreenOn()
              && !mWmService.mPowerManager.isInteractive()) {
          mTaskSupervisor.wakeUp(getDisplayId(), "resumeTop-turnScreenOnFlag");
          next.setCurrentLaunchCanTurnScreenOn(false);
      } else {
          checkReadyForSleep();
      }
  }
  ```
  Without the turn-screen-on flag the task is put back to sleep. `ActivityRecord.java` line 5975: on a sleeping display an activity counts as visible only if `canTurnScreenOn()`.

Result by source: `onCreate` -> `onStart` -> `onResume`, then pause and stop within moments. The display is not touched. An activity that calls `finish()` in `onCreate` goes straight to `onDestroy`.

### 4.3 Exceptions

- **Before first unlock.** `pm/ComputerEngine.java` lines 2786-2790: `if (umInternal.isUserUnlockingOrUnlocked(userId)) { flags |= MATCH_DIRECT_BOOT_AWARE | MATCH_DIRECT_BOOT_UNAWARE; } else { flags |= MATCH_DIRECT_BOOT_AWARE; }`.
  The Bluetooth app is `directBootAware="true"` (manifest line 102) and can serve the hearing aid before the first unlock. A trampoline that is not direct-boot aware is then not resolvable.
- **App Lock (new in Android 17).** `TaskFragment.java` line 1365: an activity "locked by App Lock" is not resumed, an overlay is shown instead.
- The device may run newer window-manager code than r1.

### 4.4 Corroboration (third party)

- hermes-relay issue 774 / PR 775: an exported `VOICE_COMMAND` activity "launches Hermes successfully with the screen both unlocked and locked" on a Pixel 8a, Android 17. It does not say whether the display was off.
- `suddenBook/GPTWake` README line 31: an activity start "Succeeds with the screen off under a secure keyguard, without turning the screen on".

### 4.5 Bonus found in the same code

`wm/ActivityTaskManagerService.java` line 348: `static final long ACTIVITY_BG_START_GRACE_PERIOD_MS = 10 * 1000;`
`am/ActiveServices.java` lines 9233-9234: `if (pr.getWindowProcessController().areBackgroundFgsStartsAllowed()) { return REASON_ACTIVITY_STARTER; }`
An activity launch or finish in the app within the last 10 s lets it start a foreground service with while-in-use (microphone) access, provided app switching is allowed. That is the mechanism behind "shim activity" designs. Not device-tested.

---

## Claim 5 - extras name the device and profile, delivered only to apps targeting API 37

**Verdict: partly.** The extras exist. The "only for target 37" part is what the javadoc says, not what the code does.

- Javadoc, `core/java/android/content/Intent.java` lines 1599-1653 (constant at line 1653):
  "For apps targeting or running on devices with SDK version `BAKLAVA` or lower, the extras `BluetoothDevice#EXTRA_DEVICE` and `BluetoothProfile#EXTRA_PROFILE` are not included as part of the intent. For apps targeting versions higher than `BAKLAVA`, the extras ... are included as part of the intent."
- Code, `HeadsetSystemInterface.java` lines 351-352: `intent.putExtra(BluetoothDevice.EXTRA_DEVICE, fromDevice); intent.putExtra(BluetoothProfile.EXTRA_PROFILE, BluetoothProfile.HEADSET);` with no condition.
  There is no target-SDK or compat-change check in the Bluetooth app (only four uses of the two actions: `HeadsetSystemInterface` 350/375, `vap/VapServerService.kt` 147/163), and no code in frameworks/base `wm`, `pm`, `am` that strips them.
- The same unconditional `putExtra` lines are already in `android-16.0.0_r4` / `android16-qpr2-release` (file fetched, lines 349-353). `android16-release` and `android16-qpr1-release` still have `activateVoiceRecognition()` without extras. So "running on devices with SDK BAKLAVA or lower: not included" is also inaccurate for 16 QPR2.
- Values: `BluetoothDevice.EXTRA_DEVICE` = `"android.bluetooth.device.extra.DEVICE"` (Parcelable `BluetoothDevice`); `BluetoothProfile.EXTRA_PROFILE` = `"android.bluetooth.profile.extra.PROFILE"` (`BluetoothProfile.java` line 67, public in `framework/api/current.txt` line 1137), int, `BluetoothProfile.HEADSET` for HFP, `BluetoothProfile.LE_AUDIO` from the new LE Audio voice-assistant server (`vap/VapServerService.kt` line 149).

Google's module build on the phone may gate the extras as documented. Treat them as optional.

---

## Claim 6 - Google app also handles the intent; "Always" preference; adb; no-preference case

**Verdict: partly.** The mechanism is confirmed in source. That the Google app is a handler rests on third-party logs only. "Survives" needs qualifying. adb can inspect and clear, not set.

### 6.1 Google app as handler - third-party evidence only

home-assistant/android issue 7834 (Pixel, Android 17, `CP3A.261005.005`, Google app 17.72.17), comments of 2026-11-08:
- query for `VOICE_COMMAND` + `DEFAULT` returned three activities, "all marked `isDefault=true` with `priority=0`": Google `...handsfree.HandsFreeActivity`, Alexa, Home Assistant.
- `dumpsys package` showed `android.intent.action.VOICE_COMMAND: com.google.android.googlequicksearchbox/...HandsFreeActivity mMatch=0x100000 mAlways=true` with "Selected from:" the three.
- After Settings > Apps > Google > Open by default > Clear default preferences, `cmd package resolve-activity --brief -a android.intent.action.VOICE_COMMAND -c android.intent.category.DEFAULT` returned `android/com.android.internal.app.ResolverActivity`, and the reporter (a human, last comment) wrote "once I cleared it home assistant works!".
- `pm disable-user` on Google's activity failed with "Shell cannot change component state for ComponentInfo{...HandsFreeActivity} to 3". That exact string is in `pm/PackageManagerService.java` line 4218, which supports the report's authenticity.
I could not open the Google app's manifest. No primary source.

### 6.2 Resolution and the preference - source

- `pm/ResolveIntentHelper.java` lines 153-207: one candidate -> used directly. Several: "If the first activity has a higher priority, or a different default, then it is always desirable to pick it"; else the saved preferred activity; else the resolver.
- `pm/resolution/ComponentResolver.java` lines 504-515: "non-privileged applications can never define a priority >0" -> our filter cannot win by priority. A privileged app can. If the Google app ever raised its filter priority it would always win and no user choice could override it. Today it is 0 per the report.
- "Always" in the chooser: `core/java/com/android/internal/app/ResolverActivity.java` line 1611 `pm.addUniquePreferredActivity(filter, bestMatch, set, intent.getComponent());`. The default resolver is created with `supportsAlwaysUseOption = true` (line 414).
- Persistence: `pm/Settings.java` lines 2371-2380 write `<preferred-activities>` into the per-user package restrictions file -> survives reboot.
- App update: `pm/RemovePackageHelper.clearPackageStateForUserLIF` lines 284-293: "Personal data is preserved if the DELETE_KEEP_DATA flag is on" -> `return;` before "Step 4: clear perferred activities" -> survives an update.

The preference is lost when (all source, `pm/ComputerEngine.java`):
- line 3453-3497: the candidate set changed and is not a subset of the remembered set ->
  `Slog.i(TAG, "Result set changed, dropping preferred activity for " + intent + " type " + resolvedType);` and the entry is re-added as "last chosen" only.
  Trigger: any newly installed app with a `VOICE_COMMAND` activity, or an existing app adding or enabling another matching activity. If candidates only disappear, the preference is kept (lines 3454-3472).
- lines 3409-3424: `"Removing dangling preferred activity: "` when the preferred component no longer exists or is disabled. Renaming the activity, or switching an activity-alias off and on, kills it.
- `PreferredActivityHelper.java` line 620 (Settings "Reset app preferences"), "Clear default preferences" on our app, uninstall.

### 6.3 adb

`pm/PackageManagerShellCommand.java` at the tag:
- inspect: `resolve-activity` (line 240), `query-activities` (line 242); `dumpsys package preferred` and `preferred-xml` (`pm/DumpHelper.java` lines 209-211).
- clear: line 396 `case "clear-package-preferred-activities"`, help line 5320 `clear-package-preferred-activities <PACKAGE>`. Shell holds `SET_PREFERRED_APPLICATIONS` (`packages/Shell/AndroidManifest.xml` line 155).
- set: there is no command. The only setter in the shell is `set-home-activity` (a role call). Shell cannot disable a single component of another app (line 4200-4219).
- Practical route: phone unlocked, `adb shell am start -a android.intent.action.VOICE_COMMAND` (or the gesture itself) opens the chooser; the owner taps our app and "Always". Not scriptable without hidden APIs.

### 6.4 No preference, phone locked and dark - source

1. `chooseBestActivity` returns the resolver. PackageManagerService builds its `ActivityInfo` in code (lines 8546-8558): flags are `FLAG_EXCLUDE_FROM_RECENTS | FLAG_RELINQUISH_TASK_IDENTITY | FLAG_CAN_DISPLAY_ON_REMOTE_DEVICES | FLAG_HARDWARE_ACCELERATED`. No show-when-locked, no turn-screen-on.
2. `startActivity` succeeds, so `activateVoiceRecognition` returns true and the 5 s timer starts.
3. The chooser sits behind the keyguard on a dark display. Nobody calls `startVoiceRecognition`.
4. After 5000 ms: `VOICE_RECOGNITION_RESULT, 0 /* fail */` -> `AT_RESPONSE_ERROR`. The stale chooser is there when the phone is next unlocked.
If no activity matches at all (for example before first unlock), `ActivityNotFoundException` -> ERROR at once.

---

## Claim 7 - long press HEADSETHOOK / KEYCODE_VOICE_ASSIST "goes through the assistant role"

**Verdict: refuted** for Android 17 r1. The route is different from AT+BVRA, but it also ends in an ordinary implicit activity intent. Only `KEYCODE_ASSIST` reaches the assistant service.

- `KEYCODE_HEADSETHOOK` (79; `data/keyboards/Generic.kl` line 248 `key 226 HEADSETHOOK`), and `KEYCODE_MEDIA_PLAY_PAUSE` on non-TV devices:
  `policy/PhoneWindowManager.java` lines 4755-4783 (`interceptKeyBeforeQueueing`): when not passed to an app, `mBroadcastWakeLock.acquire()` (a `PARTIAL_WAKE_LOCK`, line 2372) and `MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK`.
  Lines 5158-5181 (`dispatchMediaKeyRepeatWithWakeLock` at 5171): the policy itself synthesises the long press after `ViewConfiguration.getKeyRepeatTimeout()`: `KeyEvent.changeTimeRepeat(event, ..., 1, event.getFlags() | KeyEvent.FLAG_LONG_PRESS)`.
  `media/MediaSessionService.java` line 3385 `isFirstLongPressKeyEvent` = `FLAG_LONG_PRESS` and `getRepeatCount() == 1`; line 2967 `isVoiceKey`; lines 2929-2965 `startVoiceInput`:
  ```java
  if (!isLocked && pm.isScreenOn()) {
      voiceIntent = new Intent(android.speech.RecognizerIntent.ACTION_WEB_SEARCH);
  } else {
      voiceIntent = new Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE);
      voiceIntent.putExtra(RecognizerIntent.EXTRA_SECURE, isLocked && mKeyguardManager.isKeyguardSecure());
  }
  ...
  mContext.startActivityAsUser(voiceIntent, UserHandle.CURRENT);
  ```
  Action strings: `android.speech.action.VOICE_SEARCH_HANDS_FREE`, `android.speech.action.WEB_SEARCH`. No role, no `VoiceInteractionManagerService`.
  The long press of a voice key is consumed here; a media session of ours only ever sees the short press.
  Screen: `frameworks/native` `services/inputflinger/reader/mapper/KeyboardInputMapper.cpp` lines 357-360 set `POLICY_FLAG_WAKE` for external devices except `keyboardType != ALPHABETIC && isMediaKey(keyCode)`; `AKEYCODE_HEADSETHOOK` is in `isMediaKey` (line 72). So no wake from a headset or media remote. `KeyEvent.isWakeKey` does not list it either.
- `KEYCODE_VOICE_ASSIST` (231; `Generic.kl` line 424 `key 582 VOICE_ASSIST`):
  `PhoneWindowManager.java` lines 4816-4826: on key-up `MSG_LAUNCH_VOICE_ASSIST_WITH_WAKE_LOCK`; lines 5190-5206 `launchVoiceAssistWithWakeLock`: keyguard off -> `ACTION_WEB_SEARCH`; keyguard on -> `DeviceIdleManager.endIdle("voice-search")`, `ACTION_VOICE_SEARCH_HANDS_FREE` with `EXTRA_SECURE = true`; `startActivityAsUser(voiceIntent, UserHandle.CURRENT_OR_SELF)`. Again an implicit activity.
  Screen: `AKEYCODE_VOICE_ASSIST` is not in `isMediaKey` (lines 66-93), so a key-down from an external device carries `POLICY_FLAG_WAKE`; `isWakeKeyWhenScreenOff` (lines 5020-5042) returns `true` for it by default. The display turns on.
- `KEYCODE_ASSIST` (219; `key 583 ASSIST`): lines 4803-4815 -> `launchAssistAction` -> `SearchManager.launchAssist` -> SystemUI `AssistManager` -> `mAssistUtils.showSessionForActiveService(...)` (SystemUI `assist/AssistManager.java` line 489). This one does use the role holder's `VoiceInteractionService`. It wakes the display only if `config_wakeOnAssistKeyPress` (line 5029-5030).
- AVRCP cannot produce any of these. `avrcp/AvrcpTargetService.java` lines 549-551: `KeyEvent event = new KeyEvent(action, keyCode); mAudioManager.dispatchMediaKeyEvent(event);` - no repeat count, no `FLAG_LONG_PRESS`, not through the input pipeline. `avrcp/helpers/AvrcpPassthrough.toKeyCode` maps to `MEDIA_PLAY`, `MEDIA_PAUSE`, `MEDIA_STOP`, `MEDIA_NEXT`, ... and has no `HEADSETHOOK`, `MEDIA_PLAY_PAUSE`, `ASSIST` or `VOICE_ASSIST`.
  A Bluetooth headset's "hold for assistant" is the headset sending AT+BVRA (claim 1), not an AVRCP long press.

---

## Surprises (not covered by the claims)

1. Public source ends at `CP2A.260605.016`; phones are on `CP3A`. Bluetooth is a Mainline module. A logcat check on the device is the only proof for the actual build.
2. The Bluetooth app's right to start an activity from the background is not an explicit grant. It follows from `BIND_IMPORTANT` making its process `PROCESS_STATE_PERSISTENT`.
3. The `ACTION_VOICE_COMMAND` extras are attached unconditionally since 16 QPR2; the documented target-SDK gate is not in the code.
4. The "Always" preference is dropped silently as soon as one more app offers a `VOICE_COMMAND` activity. The next gesture on a dark phone then fails with ERROR after 5 s.
5. Before the first unlock after a reboot only direct-boot-aware activities resolve.
6. The stack answers ERROR and loses the gesture if SCO is already up or the last session was not closed with `stopVoiceRecognition`. In the non-AMSCO path it also tears the existing SCO link down.
7. `startVoiceRecognition` needs the hearing aid to advertise voice recognition in `AT+BRSF`; it also works app-initiated (sends `+BVRA: 1`, opens SCO).
8. SCO may be created by the audio framework ("SCO managed by audio"), by a `setCommunicationDevice` call made from the Bluetooth process.
9. In the Bluetooth-managed path `CONNECT_AUDIO` calls `setA2dpSuspended(true)` and `setLeAudioSuspended(true)` (`HeadsetStateMachine.java` lines 1300-1301). Media-stream playback is suspended while the voice link is up.
10. The end of a session started by the headset (`AT+BVRA=0`) is announced with `sendBroadcast(new Intent(Intent.ACTION_STOP_VOICE_COMMAND))` without flags (`HeadsetSystemInterface.java` lines 374-380). It is a protected broadcast (`core/res/AndroidManifest.xml` line 862) and an implicit one, so only a runtime-registered receiver gets it. The sender is UID 1002, not system; whether the receiver must be registered `RECEIVER_EXPORTED` was not traced.
11. A trampoline launch opens a 10 s window in which a foreground service with microphone access may be started (`REASON_ACTIVITY_STARTER`).
12. The AT+BVRA path does not end Doze. The `KEYCODE_VOICE_ASSIST` path does (`dim.endIdle("voice-search")`).
13. Android 17 adds `android.permission.MANAGE_ASSISTANT_AUDIO` (`core/res/AndroidManifest.xml` line 7559, `signature|role`, flagged, hidden API: "control of audio behavior related to Assistant experiences, including routing, mode change and volume control"). `roles.xml` grants it to the assistant role holder behind `android.media.audio.Flags.manageAssistantAudioPermission`. The role also grants the `SYSTEM_ALERT_WINDOW` app-op permission.
14. Android 17 has an App Lock feature that intercepts activity resume.
15. Shell cannot disable Google's handler component; only the whole package.
16. Only `AT+BVRA=0/1` is accepted (table entry min 0, max 1). Enhanced voice recognition values get ERROR from the parser.

## Still needs a device run

See the structured result. Short list: full `START ... (BAL_...)` logcat line and `onCreate` with the display off; the same before first unlock; HCI snoop for `AT+BRSF`/`AT+BVRA` from the hearing aid and how long it waits for OK; `dumpsys audio | grep scoManagedByAudio`; handler list and priorities for `VOICE_COMMAND`; chooser "Always" flow and its survival; delivery of `ACTION_STOP_VOICE_COMMAND`; microphone foreground-service start from the trampoline.
