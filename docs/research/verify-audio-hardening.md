# Verification: audio-hardening cluster (Android 17, Pixel 10 Pro)

Date: 2026-11-27. Method: adversarial re-check of seven first-pass claims against primary sources that I
opened myself. Nothing here was run on a device.

## 0. Sources and their limits

| What | Where | Note |
|---|---|---|
| Platform source | `android.googlesource.com`, tag `refs/tags/android-17.0.0_r1` | Tag and branch `android17-release` point to the same commits: frameworks/base `94b4c163b7df` (2026-05-21), frameworks/av `475269ec44ac` (2026-05-14), packages/modules/Bluetooth `c77db469de80`. Build ID of that tag: `CP2A.260605.016` (source.android.com build-numbers page). |
| `refs/heads/main` | same host | frameworks/base `main` head is dated 2025-03-26. It lags by more than a year and was **not** used. |
| Flag values | `platform/build/release` at the same tag, `aconfig/cp2a/...` and `release_configs/cp2a.textproto` | `cp2a` is the Android 17 release configuration (`inherits: "cp1a"`). |
| Official docs | developer.android.com, fetched today | bg-audio page, behaviour changes (target 17), fgs/restrictions-bg-start, local-network-permission, AudioManager reference. |
| Local copies | `<research>/verify-audio-hardening-src/` | Every file quoted below was downloaded again from the tag; the first pass's copies were not trusted. |
| History | blobless shallow clones in `<repos>/verify-av` and `.../repos/verify-fwb` | Only used for `git log` / `git show`. Gitiles log pages need a sign-in. |

**Limit that applies to every code-level statement:** the only public Android 17 platform source is
`android-17.0.0_r1` (June 2026). The phone is, five months later, probably on a quarterly release
(the developer site lists Android 17 QPR1 betas `CP31.*` until August 2026 and QPR2 betas `CP41.*` in
September and October 2026). No QPR source is published. Flags, exemptions and shell command names on the
phone can differ. Read the build first: `adb shell getprop ro.build.id`.

---

## Claim 1. Background audio hardening exists; all apps need an FGS; API 37 targets need WIU

**Verdict: partly.** The documented rule is exactly as claimed. The released code adds three things the
claim does not say and that change how the app must be built.

### 1.1 Official text (confirmed)

https://developer.android.com/about/versions/17/changes/bg-audio

> "All apps running on Android 17 that have these background audio interactions must have a visible
> activity or must be running a foreground service that is not of type `SHORT_SERVICE`. This applies whether
> or not the app targets API level 37."

> "If an app targets Android 17 (API level 37), there is an additional restriction. If the app is running in
> the background, the app must be running a foreground service that has while-in-use (WIU) capabilities.
> ... However, the requirement for WIU capabilities is waived if the app has been granted the exact alarm
> permission, and it is making changes to audio streams that have the `USAGE_ALARM` attribute."

> "If the app tries to call audio APIs while the app is not in a valid lifecycle, the audio playback and
> volume change APIs fail silently without throwing an exception or providing a failure message. The audio
> focus API fails with the result code `AUDIOFOCUS_REQUEST_FAILED`."

Affected APIs per the page's table: `AudioTrack.write()`, `AAudioStream_write`, OpenSL ES (and media3,
ExoPlayer, Oboe on top of them); `AudioManager.requestAudioFocus()`; `setStreamVolume()`, `setStreamMute()`,
`adjustStreamVolume()`, `adjustVolume()`, `adjustSuggestedStreamVolume()`, `setRingerMode()`.
`setCommunicationDevice`, `setMode`, `startBluetoothSco` and recording are **not** in the list.

### 1.2 Enforcing code (confirmed, android-17.0.0_r1)

Three app-ops, all default `MODE_FOREGROUND` (`core/java/android/app/AppOpsManager.java` 3184, 3584-3587):

```java
new AppOpInfo.Builder(OP_TAKE_AUDIO_FOCUS, OPSTR_TAKE_AUDIO_FOCUS, "TAKE_AUDIO_FOCUS")
    .setDefaultMode(AppOpsManager.MODE_FOREGROUND).build(),
new AppOpInfo.Builder(OP_CONTROL_AUDIO, OPSTR_CONTROL_AUDIO,
        "CONTROL_AUDIO").setDefaultMode(AppOpsManager.MODE_FOREGROUND).build(),
new AppOpInfo.Builder(OP_CONTROL_AUDIO_PARTIAL, OPSTR_CONTROL_AUDIO_PARTIAL,
        "CONTROL_AUDIO_PARTIAL").setDefaultMode(AppOpsManager.MODE_FOREGROUND).build(),
/** @hide Similar to {@link OP_CONTROL_AUDIO}, but doesn't require capabilities. */
```

How `MODE_FOREGROUND` is evaluated (`services/core/java/com/android/server/appop/AppOpsUidStateTrackerImpl.java`
130-183):

```java
public int evalMode(int uid, int code, int mode) {
    if (mode != MODE_FOREGROUND) { return mode; }
    ...
private int evalModeInternal(int uid, int code, int uidState, int uidCapability) {
    if (getUidAppWidgetVisible(uid) || mActivityManagerInternal.isPendingTopUid(uid)
            || mActivityManagerInternal.isTempAllowlistedForFgsWhileInUse(uid)) {
        return MODE_ALLOWED;
    }
    int opCapability = getOpCapability(code);
    if (opCapability != PROCESS_CAPABILITY_NONE) {
        if ((uidCapability & opCapability) == 0) { return MODE_IGNORED; } else { return MODE_ALLOWED; }
    }
    if (uidState > AppOpsManager.resolveFirstUnrestrictedUidState(code)) { return MODE_IGNORED; }
    return MODE_ALLOWED;
}
    case OP_CONTROL_AUDIO: return PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
```

So:

- **Partial level** (`OP_CONTROL_AUDIO_PARTIAL` for playback and volume, `OP_TAKE_AUDIO_FOCUS` for focus):
  allowed when the UID state is at most `UID_STATE_FOREGROUND` (500). `AppOpsUidStateTracker.processStateToUidState`
  maps process states up to `PROCESS_STATE_BOUND_FOREGROUND_SERVICE` into that range; everything from
  `PROCESS_STATE_IMPORTANT_FOREGROUND` down is `UID_STATE_BACKGROUND` or `UID_STATE_CACHED`.
- **Full level** (`OP_CONTROL_AUDIO`): allowed when the UID holds
  `PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL`. A foreground service with while-in-use allowed grants it
  whatever its type (`services/core/java/com/android/server/am/psc/OomAdjusterImpl.java` 1703-1712):
  `if (s.isFgsAllowedWiu_forCapabilities()) { ... if (roForegroundAudioControl()) { capabilityFromFGS |= PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL; }`.
- Both levels are also allowed for a UID on the FGS-while-in-use temp allowlist (media button dispatch, about
  10 s) and for a visible app widget.

Playback, native side (`frameworks/av/services/audioflinger/Tracks.cpp` 3726-3779):

```cpp
const auto overrided = cb.getHardeningOverride();
if (overrided == ENABLE || overrided == THROW) { return {EnforcementLevel::FULL, NONE}; }
else if (overrided == DISABLE) { return {EnforcementLevel::NONE, OVERRIDE}; }
if (usage == AUDIO_USAGE_VIRTUAL_SOURCE || media::permission::isSystemUsage(usage)) {
    return {EnforcementLevel::NONE, SYSTEM_USAGE}; }
if (pp.checkPermission(PermissionEnum::MODIFY_AUDIO_ROUTING, uid).value_or(false) ||
    pp.checkPermission(PermissionEnum::MODIFY_PHONE_STATE, uid).value_or(false)) {
    return {EnforcementLevel::NONE, PRIVILEGED_APP}; }
if (hardening_strict()) {
    if (hardening_usage()) { if (usage == AUDIO_USAGE_ALARM) { /* exact-alarm permission -> PARTIAL */ } }
    ...
    if (pp.checkPermission(PermissionEnum::BLUETOOTH_CONNECT, uid).value_or(false)) {
        return {EnforcementLevel::PARTIAL, PRIVILEGED_APP};
    }
    if (pp.getHighestTargetSdkForUid(uid).value_or(1000) < 37) {
        return {EnforcementLevel::PARTIAL, TARGET_SDK};
    }
    return {EnforcementLevel::FULL, NONE};
} else if (hardening_partial()) { return {EnforcementLevel::PARTIAL, FLAG_DISABLED}; }
```

`IAfTrack.h` 409-419: `case PARTIAL: return !hasOpControlPartial(); case FULL: return !hasOpControlFull();`.
The mixer then sets the track volume to zero (`Threads.cpp` 5878-5880:
`if (track->isPlaybackRestricted() || track->getPortMute()) { volume = 0.f; }`) and reports
`muteFromOpAudioControl` to the audio service (5927). The write path is untouched: `AudioTrack.write()` succeeds
and the playback position advances. The level is fixed when the track is created (constructor of
`AfPlaybackCommon`); the two ops are watched afterwards, so a track un-mutes when the app gains the state.
Internal patch/output tracks pass `shouldPlaybackHarden=false`.

Focus (`services/core/java/com/android/server/audio/HardeningEnforcer.java` 405-495, called from
`AudioService.requestAudioFocus` 12793-12802, which returns `AUDIOFOCUS_REQUEST_FAILED`): same ladder. Order of
the default branch: privileged -> `USAGE_ALARM` with exact-alarm permission -> `!hardeningPartial() && isPreVic`
-> **`holdsPermission(BLUETOOTH_CONNECT)` -> `DENIED_IF_PARTIAL`** -> `isPreCinnamonBun` -> `DENIED_IF_PARTIAL`
-> `!hardeningStrict()` -> else `DENIED_IF_FULL`.

Volume and ringer (`blockVolumeMethod`, 275-392, five call sites in `AudioService`): privileged -> allowed;
exact-alarm permission -> partial; `targetSdk < CINNAMON_BUN` -> partial; else full. **No `BLUETOOTH_CONNECT`
branch here.**

`AudioService.setCommunicationDevice` (8048-8112), `setMode`, `startBluetoothSco`: no call into
`HardeningEnforcer`. They only write a "discouraged API usage" metric.

Flag values in the Android 17 release configuration (`build/release`, `aconfig/cp2a/com.android.media.audio/`):
`hardening_strict`, `hardening_partial`, `hardening_partial_volume`, `hardening_usage`, `hardening_bfgs` are all
`state: ENABLED, permission: READ_ONLY`. `ro_foreground_audio_control` is `ENABLED` since `ap3a`.

### 1.3 What the claim misses

1. **`BLUETOOTH_CONNECT` waives the strict level for playback and focus** in the released code, also for
   API 37 targets. Commit `51f3b96d49e4` in frameworks/av and `d1c7955637a4` in frameworks/base, both titled
   "[audio] BT perm WIU exemption", `Bug: 500958826`, `Flag: EXEMPT BUGFIX`, `Test: manual, app w/
   BLUETOOTH_CONNECT`, April 2026. Not in the documentation. Our app must hold that permission anyway.
2. **"Visible activity" does not exist with the display off.** When no display is awake,
   `ActivityTaskManagerService.sleepIfNeededLocked()` sets `mTopProcessState = PROCESS_STATE_TOP_SLEEPING`
   (5796). That state maps to `UID_STATE_CACHED`. An activity started by the hearing aid's voice command on a
   dark phone therefore does not satisfy even the partial level by itself.
3. **The strict requirement is a process capability, not literally "an FGS with WIU".** The capability also
   arrives through service bindings (claims 2 and 5).
4. **Short service:** in this code a `shortService` FGS still gets `PROCESS_STATE_FOREGROUND_SERVICE`
   (`OomAdjusterImpl` 1441-1455). I found no code that excludes it from the partial level. Irrelevant for us;
   do not use that type.

### 1.4 Silent or not

Silent for the app. Visible for the owner: the audio service logs every denial (and every denial that an
exemption bypassed) to logcat and to `dumpsys audio`, section "## Hardening enforcement":

- playback (`AudioService.java` 1006-1015, tag `AS.AudioService`):
  `"AudioHardening background playback " + (bypassed ? "would be " : "") + "muted for " + pkg + " (" + uid + "), level: partial|full, reason: N, usage: ..."`
  with `reason` = `HardeningExemptionReason` ordinal: 0 NONE, 1 SYSTEM_USAGE, 2 PRIVILEGED_APP, 3 FLAG_DISABLED,
  4 ALARM, 5 OVERRIDE, 6 TARGET_SDK.
- focus and volume (`HardeningEnforcer.java`, tag `AS.HardeningEnforcer`):
  `"AudioHardening focus request for req N [would be ]ignored for pkg (uid), clientId, level: full|partial usage: N exemption: N"`.
- native, every track creation (tag `AF::AfPlaybackCommon`):
  `"creating track with enforcement level %d reason %s shouldHarden %d"` (0 NONE, 1 PARTIAL, 2 FULL).

The "would be" lines are the useful ones: at targetSdk 36 a line "would be muted ... level: full" says that
the app did not hold the audio-control capability at that moment, i.e. a strict build would have been muted.

---

## Claim 2. The active VoiceInteractionService app is exempt or satisfies the rule automatically

**Verdict: confirmed from documentation and source, not from a device.** It is not an exemption in the
hardening code. It follows from how the system binds the service.

- Documentation, bg-audio page: "System started FGS: Foreground services are granted WIU access if they are
  started by system-server delegation (for example, from the Telecom jetpack library), or by system bindings
  representing an elevated foreground state to perform dedicated functionality (such as for a
  `VoiceInteractionService`)."
- Documentation, https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start ,
  "Exemptions from restrictions on while-in-use permissions": "The service starts by an app which provides the
  `VoiceInteractionService`."
- Code, `services/voiceinteraction/.../VoiceInteractionManagerServiceImpl.java` 1321-1329:
  `mContext.bindServiceAsUser(intent, mConnection, Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE | Context.BIND_INCLUDE_CAPABILITIES | Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS, new UserHandle(mUser));`
- Code, `OomAdjusterImpl.java`: the client is system_server (persistent, `app.setCurCapability(PROCESS_CAPABILITY_ALL)`,
  1264). For a client above TOP with `BIND_FOREGROUND_SERVICE`: `clientProcState = PROCESS_STATE_BOUND_FOREGROUND_SERVICE`
  (2107-2108). With `BIND_INCLUDE_CAPABILITIES`: `capability |= client.getCurCapability();` (1930-1931).
  `PROCESS_CAPABILITY_ALL` contains `PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL` and
  `PROCESS_CAPABILITY_FOREGROUND_MICROPHONE` (`ActivityManager.java` 1069-1077). The capability is kept because
  the strip only applies below BFGS: `if (procState > PROCESS_STATE_BOUND_FOREGROUND_SERVICE) { ... if (hardeningBfgs()) { capability &= ~PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL; } }` (2233-2239).
- Result: the UID of the selected assistant is at `PROCESS_STATE_BOUND_FOREGROUND_SERVICE` (partial level
  passes) and holds the audio-control capability (full level passes) for as long as the system keeps the
  binding, with the display off and without any foreground service of its own.
- A foreground service that this app starts from the background also gets while-in-use: the binding flag puts a
  background-start token on the host process (`ActiveServices.java` 4412-4413
  `s.setAllowedBgActivityStartsByBinding(true)`, `ServiceRecord.java` 1503-1505 `BackgroundStartPrivileges.ALLOW_BAL`),
  and `shouldAllowFgsWhileInUsePermissionLocked` accepts it (9226-9242, comment "The binding flag
  BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS is also allowed by the check here.").

Conditions: the app must be the *selected* voice interaction service (assistant role with a qualifying
service), the audio must be played by the same UID, and the binding must be live (after first unlock). If the
user picks another assistant, everything above disappears without an error.

---

## Claim 3. targetSdk 36 on Android 17: any foreground service is enough for playback. 36 or 37?

**Verdict: confirmed** (documentation in 1.1 and code in 1.2: below 37 the level is PARTIAL, and PARTIAL only
needs the process state of any foreground service, with or without while-in-use). Additions:

- The same is true for focus (`OP_TAKE_AUDIO_FOCUS`) and for the volume calls.
- In the released code an app with `BLUETOOTH_CONNECT` gets the same treatment at targetSdk 37 for playback
  and focus (not for volume).
- This is about playback only. The microphone needs `PROCESS_CAPABILITY_FOREGROUND_MICROPHONE`
  (`AppOpsUidStateTrackerImpl.getOpCapability`), which needs a while-in-use `microphone` service or the
  assistant binding, at any targetSdk.
- Whether the app may *start* a foreground service from a BLE scan result at all is a separate question.

### Which targetSdk

Facts I verified:

| Topic | targetSdk 36 | targetSdk 37 |
|---|---|---|
| Playback, focus | partial level (documented) | full level (documented); partial in r1 code while `BLUETOOTH_CONNECT` is granted (undocumented) |
| Volume / ringer calls | partial level | full level, no Bluetooth waiver |
| Assistant binding (claim 2) | passes | passes |
| Extras on `ACTION_VOICE_COMMAND` | Javadoc (`core/java/android/content/Intent.java`): "For apps targeting or running on devices with SDK version BAKLAVA or lower, the extras ... are not included". **Code:** `HeadsetSystemInterface.activateVoiceRecognition()` (packages/modules/Bluetooth, android-17.0.0_r1, 350-356) does `intent.putExtra(BluetoothDevice.EXTRA_DEVICE, fromDevice); intent.putExtra(BluetoothProfile.EXTRA_PROFILE, BluetoothProfile.HEADSET);` unconditionally before `startActivity`. `grep` over the whole `hfp` directory finds no target-SDK or compat check. The same two lines are already in `android16-qpr2-release`. | guaranteed by the Javadoc |
| Local network | "If your app targets SDK 36 or lower, local network access is implicitly granted using the INTERNET permission. For these lower target SDKs, don't add ACCESS_LOCAL_NETWORK to your manifest" | `ACCESS_LOCAL_NETWORK` runtime permission required for any LAN address ("Making an outgoing TCP connection: yes"); it is in the `NEARBY_DEVICES` group: "If a user has already granted another permission in this group (such as Bluetooth permissions), they won't be re-prompted" (https://developer.android.com/privacy-and-security/local-network-permission) |

Recommendation: **`compileSdk 37`, `targetSdk 36` for the first version, with targetSdk as a Gradle property.**

- Everything that works at 37 works at 36; the reverse is not true (a service without while-in-use, the
  volume calls, a LAN connection without the extra permission). 36 removes three silent failure modes.
- The one documented advantage of 37, the Bluetooth extras, is delivered by the released Bluetooth code to
  every target. Treat the extras as optional anyway and fall back to the stored hearing-aid address.
- The move to 37 can be decided by evidence: run the 36 build and look for
  "AudioHardening ... would be muted ... level: full". No such line during real sessions means the app held
  the capability every time and 37 is safe.
- The earlier reports disagree with each other (bt-audio.md and speech.md say 36, toolchain-release.md says
  37). This settles it at 36 for v1.

---

## Claim 4. A communication route is dropped about 6 s after the UID stops playing and recording

**Verdict: confirmed** (android-17.0.0_r1).

`services/core/java/com/android/server/audio/AudioDeviceBroker.java` 567-569:

```java
// check playback or record activity after 6 seconds for UIDs
private static final int CLIENT_INACTIVITY_DEBOUNCE_MS = 6000;
private ActivityDebouncer mCommunicationActivityDebouncer;
```

Chain: `AudioService.onPlaybackConfigChange` / `onRecordingConfigChange` (5612-5616, 5721-5725) post the
union of `mPlaybackMonitor.getPlaybackActiveUids()` and `mRecordMonitor.getRecordingActiveUids()` ->
`ActivityDebouncer.update()` sends `MSG_L_COMMUNICATION_ROUTE_INACTIVE` delayed by 6000 ms for a UID that left
the set and cancels it when the UID comes back -> `updateCommunicationClientActivity(uid, false)` ->
`RouteSelectionStack.updateActiveForUid` -> `onUpdateCommunicationRouteClient` (2594-2606):
"No longer any communication route, stop SCO, if it is started." `RouteClient.isActive()` is
`mIsPrivileged || mStreamActive` (296); privileged means `MODIFY_PHONE_STATE`.

A new request starts the same clock: `addCommunicationRouteClient` calls
`mCommunicationActivityDebouncer.simulateActive(uid)` ("consider the uid as starting active"), which queues the
inactivity message unless the UID is already active.

Details that matter:

1. **The request is not deleted.** The client stays in the stack as inactive. `topClient()` (126-137) returns
   the last *active* client. When the UID plays or records again, `mUidActiveConsumer` marks it active and the
   route, including SCO, is requested again without a new `setCommunicationDevice()` call. The app has to pay
   the SCO set-up time again, and it must call `clearCommunicationDevice()` when a session ends, otherwise any
   later playback of the app re-opens SCO.
2. **What counts as playing** (`media/java/android/media/AudioPlaybackConfiguration.java` 786-815):
   ```java
   private boolean isMuteAffectingActiveState() {
       return (mMutedState & MUTED_BY_CLIENT_VOLUME) != 0 || (mMutedState & MUTED_BY_VOLUME_SHAPER) != 0
           || (mMutedState & MUTED_BY_OP_PLAY_AUDIO) != 0 || (mMutedState & MUTED_BY_STREAM_VOLUME) != 0
           || (mMutedState & MUTED_BY_PORT_VOLUME) != 0 || (mMutedState & MUTED_BY_OP_CONTROL_AUDIO) != 0;
   }
   public boolean isActive() { switch (mPlayerState) { case PLAYER_STATE_STARTED: return !isMuteAffectingActiveState(); ... default: return false; } }
   ```
   A player muted by hardening (`MUTED_BY_OP_CONTROL_AUDIO`), by `AudioTrack.setVolume(0f)`, by a volume
   shaper at zero or by a stream volume of zero is **not active**. A started track that carries zero-valued
   samples at normal volume is active.
3. **What counts as recording** (`RecordingActivityMonitor.java` 242-252):
   `x.isActiveConfiguration() && !x.getConfig().isClientSilenced()`. A recording that the system silences
   (no microphone capability) does not count.
4. If the app also owns the audio mode, the bar is higher and has the same 6 s:
   `CHECK_MODE_FOR_UID_PERIOD_MS = 6000` (`AudioService.java` 572), playback only with
   `USAGE_VOICE_COMMUNICATION[_SIGNALLING]`, recording only with `AudioSource.VOICE_COMMUNICATION` (5626-5647).

Link to claim 1: hardening does not block `setCommunicationDevice()`, but a hardening-muted track is inactive,
so the route is deselected and SCO closed after 6 s. "SCO opens and closes again after six seconds" is the
symptom of a muted player or a silenced recorder.

---

## Claim 5. `TextToSpeech.speak()` plays in the engine's process, so synthesise to a buffer and play yourself

**Verdict: partly.** First half confirmed. The stated reason "for the hardening rules" is not supported by the
code; the 6 s rule is the real reason.

- Confirmed: `core/java/android/speech/tts/BlockingAudioTrack.java` 226-228, executed inside
  `TextToSpeechService`, which is the engine's service:
  `AudioTrack audioTrack = new AudioTrack(mAudioParams.mAudioAttributes, audioFormat, bufferSizeInBytes, AudioTrack.MODE_STREAM, mAudioParams.mSessionId);`
  No `AttributionSource` of the caller is involved (`grep` over both files). The client binds with
  `mContext.bindService(intent, this, Context.BIND_AUTO_CREATE)` (`TextToSpeech.java` 2426).
- Hardening: the platform deliberately lets the capability follow the binding.
  `services/core/java/com/android/server/am/psc/OomAdjuster.java` 2286-2294:
  ```java
  protected int getAudioCapabilitiesFromClient(ProcessRecordInternal client) {
      // Similar to bfsl/cpu, there isn't a compelling reason to prevent the capability
      // to control/play audio from propagating through binds: if the client has the
      // capability, we generally want the service it binds to to hold the capability as well
      // (e.g. TTS).
      return client.getCurCapability() & PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;
  }
  ```
  applied to every service binding (`OomAdjusterImpl.java` 1927), and the bound service inherits the client's
  process state when that is better (2162-2163). So an engine bound by our app is audible whenever our app
  would be. The engine's own target SDK and permissions decide its level, which we do not control.
- Routing: the communication route is applied per strategy, not per UID. An engine track with
  `USAGE_VOICE_COMMUNICATION` (settable through `TextToSpeech.setAudioAttributes`) would follow the route. [inferred]
- The real problem: the engine's playback is attributed to the **engine's UID**. It does not keep *our* route
  client active (claim 4). During a long answer spoken by the engine, with our microphone closed, the SCO link
  is torn down after 6 s unless our own UID plays or records at the same time.

So `synthesizeToFile()` / `onAudioAvailable()` plus our own `AudioTrack` remains the better design (one UID,
one track, control over pre-roll and caching). It is a choice for routing stability, not a hardening necessity.

---

## Claim 6. `getAvailableCommunicationDevices` + `setCommunicationDevice(TYPE_BLUETOOTH_SCO)`; `startBluetoothSco` deprecated; callable from a background service

**Verdict: confirmed.**

- https://developer.android.com/reference/android/media/AudioManager : `startBluetoothSco` "Added in API
  level 8, Deprecated in API level 34 ... This method was deprecated in API level 34. Use
  AudioManager.setCommunicationDevice(AudioDeviceInfo) instead." Same in the tag
  (`media/java/android/media/AudioManager.java` 3423-3425: `@deprecated Use {@link AudioManager#setCommunicationDevice(AudioDeviceInfo)} instead.` / `@Deprecated public void startBluetoothSco()`).
- `setCommunicationDevice` Javadoc (9823-9875): "only devices in a sink role ... can be specified. The
  matching source device is selected automatically by the platform." / "The selection is active as long as the
  requesting application process lives, until `clearCommunicationDevice` is called or until the device is
  disconnected." / "This API replaces the following deprecated APIs: `startBluetoothSco()`,
  `stopBluetoothSco()`, `setSpeakerphoneOn(boolean)`". Not deprecated in API 37.
- `AudioDeviceBroker.VALID_COMMUNICATION_DEVICE_TYPES` (533-549) contains `TYPE_BLUETOOTH_SCO` (and, new,
  `TYPE_BLE_HEARING_AID`).
- Background: `AudioService.setCommunicationDevice` (8048-8112) has no permission check, no process-state
  check and no hardening call. It validates the device, looks up `MODIFY_PHONE_STATE` to mark the client
  privileged, and posts the request. `BtHelper` has no caller-state check either. The call is accepted from
  any state; whether the route *stays* depends on claim 4 and therefore, in the background, on un-muted
  playback (claim 1) or un-silenced recording.
- `setMode(MODE_IN_COMMUNICATION)` is not required for the route. `AudioManager.createAudioModeSession()` is
  `@SystemApi(client = MODULE_LIBRARIES)` with `MODIFY_PHONE_STATE`; not for us.

---

## Claim 7. adb switches that make hardening visible

**Verdict: partly.** The switches exist. The name in the official documentation is not the name the released
code parses.

- Documentation (bg-audio page): `adb shell cmd audio set-enable-hardening <enable|disable|throw>`.
- Code, `services/core/java/com/android/server/audio/AudioManagerShellCommand.java`, android-17.0.0_r1:
  ```java
  case "set-hardening":   return setHardeningOverride();      // 105-106
  case "clear-hardening": return clearHardeningOverride();    // 107-108
  pw.println("  set-hardening <1|enable|0|disable|throw>");   // 182
  pw.println("    Enables (1), disables (0) or throws on audio hardening enforcement");
  pw.println("  clear-hardening");
  pw.println("    Clears the hardening override, returning to default behavior");
  // 525-541: "enable","true","1" | "disable","false","0" | "throw" | "default"
  ```
  The string `set-enable-hardening` does not occur in the file.
- Where the documented name comes from: in `android16-release`, `android16-qpr1-release` and
  `android16-qpr2-release` the same file has `case "set-hardening": return setEnableHardening();` while its
  **help text** prints `"  set-enable-hardening <1|0>"`. The dispatched name has been `set-hardening`
  throughout; only the help line said otherwise, and Android 17 corrected the help line.
- Trap: `onCommand` ends with `switch (cmd) { ... } return 0;`. An unknown sub-command prints nothing and
  returns success. Typing the documented command on a build like r1 does nothing and reports no error. The
  working command prints `calling AudioManager.setHardeningOverride(HARDENING_...)`.
- Rights: `AudioService.onShellCommand` requires `MANAGE_AUDIO_POLICY`; `setHardeningOverride` is
  `@EnforcePermission(MODIFY_AUDIO_SETTINGS_PRIVILEGED)`. `packages/Shell/AndroidManifest.xml` declares both
  (334, 746). No root needed.

What the modes do (enum `IAudioPolicyService.HardeningOverride`: DEFAULT 0, DISABLE 1, ENABLE 2, THROW 3):

| Command | Effect |
|---|---|
| `adb shell cmd audio set-hardening enable` | Full level for every app: no target-SDK, Bluetooth, alarm, usage or privileged-permission waiver for playback (`Tracks.cpp` 3735-3737); focus and volume blocked whenever `OP_CONTROL_AUDIO` is not allowed. |
| `adb shell cmd audio set-hardening throw` | Like `enable`, plus `throw new IllegalStateException(msg)` in the focus and volume paths (`HardeningEnforcer` 380-382, 485-487) and `track->poison()` for restricted tracks (`Threads.cpp` 5914-5918; documentation: "the write method persistently returns an error code. For playback modes without explicit writes, the app crashes"). |
| `adb shell cmd audio set-hardening disable` | No enforcement at all. |
| `adb shell cmd audio clear-hardening` (or `set-hardening default`) | Back to the normal rules. |

Properties: global (all apps), kept in an `AtomicInteger` in `AudioService` (1389) and pushed to the audio
server again when it restarts (1533-1535); nothing is persisted, so it is gone after a reboot. The level of a
track is fixed at creation, so set the mode before the app creates its `AudioTrack`.

Reading the result: `adb shell dumpsys audio` prints `mHardeningOverride=<n>` followed by the
"## Hardening enforcement" event log (14502-14503); logcat tags `AS.HardeningEnforcer`, `AS.AudioService`,
`AF::AfPlaybackCommon`. `adb shell cmd audio` without arguments prints the help of the installed build
(`cmd audio help` prints nothing, for the same reason as above).

---

## Surprises

1. **Undocumented Bluetooth waiver** (1.3). Apps holding `BLUETOOTH_CONNECT` are only held to the partial level
   for playback and focus. Added as a bug fix in April 2026. Do not build on it.
2. **Documented adb command name is wrong for released code, and wrong names fail silently** (claim 7).
3. **Muted is inactive** (claim 4, item 2). Consequences beyond the route: `PlaybackActivityMonitor.anonymizeForPublicConsumption`
   (811-822) only returns `config.isActive()` entries, so `AudioManager.getActivePlaybackConfigurations()` does
   not list a hardening-muted player. An app can use that as a self-check: start the prompt, and if no
   configuration with its own audio attributes shows up, it is being muted. [inferred from code]
4. **Display off means no "visible activity"** (1.3, item 2).
5. **A per-app owner override follows from the code**: `evalMode` returns any mode other than
   `MODE_FOREGROUND` unchanged (`AppOpsUidStateTrackerImpl` 131-133), the op names are `CONTROL_AUDIO`,
   `CONTROL_AUDIO_PARTIAL`, `TAKE_AUDIO_FOCUS`, and the shell holds `MANAGE_APP_OPS_MODES`. So
   `adb shell appops set <package> CONTROL_AUDIO allow` (and the two others) should exempt one app, and app-op
   modes are stored across reboots, unlike `set-hardening`. `roles.xml` does exactly this for the shell role
   ("App ops needed to allow background access to audio APIs for CTS"). Not tested; a debugging aid and a
   last-resort fallback, not a design.
6. **Assistant-specific audio constructs in Android 17**: `AudioManager.MODE_ASSISTANT_CONVERSATION` and a
   public `STREAM_ASSISTANT` (both flagged, both flags `ENABLED` in `cp2a`). `setMode(MODE_ASSISTANT_CONVERSATION)`
   requires `android.permission.MANAGE_ASSISTANT_AUDIO` (`signature|role`), and `roles.xml` grants that
   permission to the holder of `android.app.role.ASSISTANT`. Effect in `AudioService`: volume keys are directed
   to the assistant stream ("MODE_ASSISTANT_CONVERSATION active, directing volume event to ..."); a handler
   in this mode never counts as an active mode owner (`isActive()` starts with
   `mMode != MODE_ASSISTANT_CONVERSATION &&`). Not needed for v1; worth a look if volume handling during a
   dialogue becomes a problem.
7. **Media-key grace:** a UID on the FGS-while-in-use temp allowlist passes all three ops without any service
   (`evalModeInternal`, 145-148). `ActiveServices` describes that list as used "when MediaSessionService's
   bluetooth button or play/resume/stop commands are issued. The typical temp allowlist duration is 10 seconds."

---

## Design consequences in one place

1. One UID and one process for the VoiceInteractionService, the triggers and all audio. The assistant binding
   is the primary source of both capabilities; an own foreground service is the second line.
2. Always start a (non-short) foreground service for a session anyway. It gives the partial level without the
   role and makes the session visible to the user.
3. `targetSdk 36`, `compileSdk 37`, switchable.
4. Own `AudioTrack` with `USAGE_VOICE_COMMUNICATION`, kept started with zero samples (never `setVolume(0)`)
   between prompts inside a session; first audio within 6 s of `setCommunicationDevice()`;
   `clearCommunicationDevice()` at session end.
5. No audible TTS through `TextToSpeech.speak()` while SCO is in use; synthesise to PCM.
6. Self-diagnosis in the app: log `AUDIOFOCUS_REQUEST_FAILED`, `AudioRecordingConfiguration.isClientSilenced()`,
   and the active-playback self-check. Debug sessions with `cmd audio set-hardening throw`.

## Still needs the device

1. `adb shell getprop ro.build.id`; `adb shell cmd audio` (help text): which hardening commands the installed
   build has.
2. Assistant role held, no foreground service, display off: is a tone audible, and does
   `dumpsys audio` stay free of `AudioHardening` lines for the package? Repeat under `set-hardening enable`.
3. targetSdk 36 build during real sessions from all three triggers: any "would be muted ... level: full" line?
4. targetSdk 37 build with a service that has no while-in-use and without the role: audible (Bluetooth waiver
   present) or muted (waiver gone)? Logcat `AF::AfPlaybackCommon: creating track with enforcement level`.
5. Six-second rule on the hearing aid: silence after `setCommunicationDevice()`, time until SCO closes, and
   whether the route really returns by itself when the track plays again; latency of that return.
6. Zero-sample keep-alive: does the aid stay in its streaming programme, and what does it cost in battery.
7. `ACTION_VOICE_COMMAND` at targetSdk 36: are `EXTRA_DEVICE` and `EXTRA_PROFILE` present?
8. `throw` mode: what exactly the app sees (exception on focus, error code on `write`).
9. If wanted: `appops set <package> CONTROL_AUDIO allow` and its survival across reboot and app update.
