# Default-assistant role and headset/hearing-aid activation on a locked, screen-off Pixel

Research date: 2026-11-27. Nothing in this report was run on a device. Every statement is tagged
**[verified]** (seen in a primary source that is cited), **[inferred]** (reasoned from cited sources) or
**[unknown]**.

Sources used

| Source | Revision |
|---|---|
| AOSP `platform/frameworks/base`, branch `android17-release` | `94b4c163b7dfe5ce3607f7bb8456f9573f7de57d` |
| AOSP `platform/packages/modules/Bluetooth`, `android17-release` | `c77db469de80f86660bc053bec4dee0c5d4b947c` |
| AOSP `platform/packages/modules/Permission`, `android17-release` | `1093eca7407576c72e51b1a88550ee0667a6e5dc` |
| AOSP `platform/frameworks/native`, `android17-release` | `ae266dcb706d083868578cfedce381ef44488a07` |
| AOSP `platform/frameworks/av`, `android17-release` | `475269ec44acec82792be6b01fb8e16357d4d2c8` |
| `home-assistant/android` (Apache-2.0) | `b4d7cbb9` (2026-11-26) |
| `DicioTeam/dicio-android` (GPL-3.0; formerly `Stypox/dicio-android`) | `3d1c1543` (2026-10-23) |
| `suddenBook/GPTWake` (Apache-2.0 notice in `LICENSE`; GitHub API reports `NOASSERTION`) | `2e31652b` (2026-09-09) |
| `Codename-11/hermes-relay` (MIT) | `da782a8d` (2026-10-20) |
| `kerero/airdots-double-tap` (MIT) | `57faa02c` (2023-08-21) |
| `omarqaterge/hermes-mobile-app` (MIT) | `ce6916c0` (2026-11-23) |
| Phonak Audéo E (EON) user guide, doc no. 029-1462-02 | PDF from phonak.com |

AOSP files are browsable at
`https://android.googlesource.com/<project>/+/refs/heads/android17-release/<path>`.
Local copies are under
`<repos>/aosp/`.

---

## 0. The answer in one page

1. **The hearing-aid "voice assistant" gesture does not go to the default assistant.** In AOSP 17 the
   Bluetooth stack answers HFP `AT+BVRA=1` by calling
   `startActivity(new Intent(Intent.ACTION_VOICE_COMMAND))`. This is a plain implicit activity intent.
   It is resolved by PackageManager (preferred activity or chooser). `VoiceInteractionService` and
   `ROLE_ASSISTANT` are not consulted. [verified in source; confirmed by three independent 2024–2026
   field reports, two of them on Pixel + Android 17]
2. **So path A needs an exported Activity with an `android.intent.action.VOICE_COMMAND` filter, and
   the user must make it the "Always" handler once, while the phone is unlocked.** On a Pixel the
   Google app (`com.google.android.googlequicksearchbox/com.google.android.voicesearch.handsfree.HandsFreeActivity`)
   also handles this intent. [verified from issue-tracker logs, not from a device of ours]
3. **Holding the assistant role is still worth it, for privileges, not for the trigger.** The selected
   `VoiceInteractionService` is bound by `system_server` with
   `BIND_AUTO_CREATE | BIND_FOREGROUND_SERVICE | BIND_INCLUDE_CAPABILITIES | BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS`.
   That gives the app a permanently running process, the microphone "while-in-use" capability in the
   background, the right to start a microphone foreground service from the background, and the right
   to start activities from the background. [verified in source and in developer.android.com]
4. **Nothing in the Bluetooth path turns the screen on.** The stack takes a 5 s `PARTIAL_WAKE_LOCK`
   only. The screen turns on only if the activity that handles the intent asks for it
   (`turnScreenOn`). [verified in source; "screen stays off" confirmed by two third-party reports]
5. **The app has 5 seconds to answer** with `BluetoothHeadset.startVoiceRecognition(device)`,
   otherwise the phone sends `ERROR` to the headset and no SCO audio link is opened. [verified]
6. **Phonak:** the EON user guide lists Tap Control with "start/stop voice assistant" and the
   profiles "HFP (Hands-free profile), A2DP, PBP". Phonak does not document the AT command. That the
   tap sends `AT+BVRA` is an inference with medium-high confidence. A single (right) aid carries both
   the tap sensor and the Bluetooth Classic link, so a single-aid fitting should work. [inferred]

---

## 1. Becoming the default digital assistant (Android 16/17)

### 1.1 Role definition

`platform/packages/modules/Permission`, `PermissionController/res/xml/roles.xml`, lines 108–175
[verified; label and description attributes and the `<app-ops>` block omitted here]:

```xml
<role
    name="android.app.role.ASSISTANT"
    behavior="AssistantRoleBehavior"
    defaultHolders="config_defaultAssistant"
    exclusive="true"
    exclusivity="user"
    fallBackToDefaultHolder="true"
    showNone="true"
    overrideUserWhenGranting="true"
    requestable="false"
    uiBehavior="AssistantRoleUiBehavior">
    <required-components>
        <!-- Qualified components are determined int AssistantRoleBehavior. ... -->
    </required-components>
    <permissions>
        <permission-set name="sms" />
        <permission name="android.permission.READ_CALL_LOG" />
        <permission name="android.permission.ACCESS_BLOBS_ACROSS_USERS" minSdkVersion="31" />
        <permission name="android.permission.READ_ASSISTANT_APP_SEARCH_DATA" minSdkVersion="33"/>
        <permission name="android.permission.SUBSCRIBE_TO_KEYGUARD_LOCKED_STATE" minSdkVersion="33" />
        <permission name="android.permission.EXECUTE_APP_ACTION" minSdkVersion="34" />
        <permission name="android.permission.MANAGE_CONTENT_SUGGESTIONS" minSdkVersion="35" optionalMinSdkVersion="34" />
        <permission name="android.permission.EMBED_ANY_APP_IN_UNTRUSTED_MODE" minSdkVersion="35" />
        <permission name="android.permission.SHOW_POWER_MENU" featureFlag="android.app.Flags.statusbarApiShowPowerMenu" />
        <permission name="android.permission.MANAGE_ASSISTANT_AUDIO" featureFlag="android.media.audio.Flags.manageAssistantAudioPermission" />
    </permissions>
    <app-op-permissions>
        <app-op-permission name="android.permission.SYSTEM_ALERT_WINDOW" />
    </app-op-permissions>
    ...
</role>
```

Consequences:

- `requestable="false"`: the app cannot call `RoleManager.createRequestRoleIntent` for this role.
  The user (or adb) must select it. [verified]
- The role does **not** grant `RECORD_AUDIO`. It must be requested at runtime as usual. [verified]
- The role grants the `SYSTEM_ALERT_WINDOW` app-op, but only if the app requests that permission in
  its manifest. `AppOpPermissions.grantAsUser` returns early with
  `if (!ArrayUtils.contains(packageInfo.requestedPermissions, appOpPermission)) return false;`
  (`PermissionController/role-controller/java/com/android/role/controller/model/AppOpPermissions.java`).
  [verified]

### 1.2 Qualification rules

`PermissionController/role-controller/java/com/android/role/controller/behavior/AssistantRoleBehavior.java`,
`getQualifyingPackagesInternal` and `isAssistantVoiceInteractionService` [verified]. A package
qualifies if **either** is true:

1. (not on low-RAM devices) it has a service that answers
   `android.service.voice.VoiceInteractionService`, whose `android:permission` is exactly
   `android.permission.BIND_VOICE_INTERACTION`, and whose `android.voice_interaction` meta-data XML
   sets `sessionService`, `recognitionService` and `supportsAssist="true"`:

   ```java
   if (sessionService == null || recognitionService == null || !supportsAssist) {
       return false;
   }
   ```
2. it has an **exported** activity that matches `Intent.ACTION_ASSIST` with `CATEGORY_DEFAULT`.

When the role holder changes, `VoiceInteractionManagerService.RoleObserver.onRoleHoldersChanged`
writes `Settings.Secure.ASSISTANT` and `Settings.Secure.VOICE_INTERACTION_SERVICE` to the first
service of that package with `supportsAssist`. If the package has no such service it falls back to
its `ACTION_ASSIST` activity and leaves `voice_interaction_service` empty.
(`services/voiceinteraction/java/com/android/server/voiceinteraction/VoiceInteractionManagerService.java`,
line ~3088) [verified]

### 1.3 The voice-interaction XML and its attributes

`frameworks/base/core/res/res/values/attrs.xml`, `<declare-styleable name="VoiceInteractionService">`
and the parser `core/java/android/service/voice/VoiceInteractionServiceInfo.java` [verified]:

| Attribute | Meaning (source comment) | Needed by us |
|---|---|---|
| `sessionService` | "The service that hosts active voice interaction sessions. This is required." Parse error `No sessionService specified` if missing. | yes |
| `recognitionService` | "From Android 12 onward, this attribute does nothing. However, we still require it to be set ... the system will reset the current assistant if this isn't specified." Parse error `No recognitionService specified` if missing. | yes (a stub `RecognitionService`) |
| `settingsActivity` | optional settings entry | optional |
| `supportsAssist` | "capable of handling the assist action". Required for the role and for `RoleObserver` to pick the service. | yes, `true` |
| `supportsLaunchVoiceAssistFromKeyguard` | "capable of being launched from the keyguard". Gates `onLaunchVoiceAssistFromKeyguard()`. | set `true`, but see 2.5: no caller found in AOSP 17 SystemUI |
| `supportsLocalInteraction` | lets an Activity call `startLocalVoiceInteraction` | no |
| `hotwordDetectionService`, `visualQueryDetectionService` | `@hide @SystemApi` | no |
| `usesAssistData`, `usesAssistScreenshots`, `usesAssistStructureScreenContent` | New, flag `android.service.voice.flags.FLAG_ENABLE_ASSIST_RESOURCE_ATTRIBUTES`. "By default, this value is false." For services targeting `CINNAMON_BUN` (Android 17) the platform strips `SHOW_WITH_ASSIST` / `SHOW_WITH_SCREENSHOT` / `SHOW_WITH_ASSIST_STRUCTURE_SCREEN_CONTENT` unless declared (`enforceSessionFlags`). | leave unset: we never read the screen |

The root tag must be `voice-interaction-service` (`Meta-data does not start with
voice-interaction-service tag`). The service must carry
`android:permission="android.permission.BIND_VOICE_INTERACTION"` (`Service does not require
permission ...`). [verified]

### 1.4 How the user selects it

- Settings → Apps → Default apps → Digital assistant app → Default digital assistant app. On a
  Pixel with Android 17 this screen is
  `com.google.android.permissioncontroller/com.android.permissioncontroller.role.ui.v37.AssistantSettingsActivity`,
  reachable with `Settings.ACTION_VOICE_INPUT_SETTINGS`. [verified from the logcat excerpt in
  home-assistant/android#7834; inferred for our device]
- adb: `adb shell cmd role add-role-holder android.app.role.ASSISTANT <package>` and
  `adb shell cmd role get-role-holders android.app.role.ASSISTANT`. The commands exist in
  `service/java/com/android/role/RoleShellCommand.java`, and the Shell package holds
  `MANAGE_ROLE_HOLDERS` (`frameworks/base/packages/Shell/AndroidManifest.xml` line 315). [verified
  in source; not run]

### 1.5 Extra hurdles for a sideloaded app

- **Enhanced Confirmation Mode (restricted settings):** in Android 17 the protected list is SMS
  permissions, `BIND_DEVICE_ADMIN`, accessibility service, notification listener,
  `SYSTEM_ALERT_WINDOW`, usage stats, `ROLE_DIALER` and `ROLE_SMS`
  (`service/java/com/android/ecm/EnhancedConfirmationService.java`, lines 196–210).
  `ROLE_ASSISTANT` is **not** in the list. Selecting a sideloaded app as assistant is not blocked.
  [verified]
- The "Display over other apps" toggle is in that list for packages whose install source is
  `PACKAGE_SOURCE_LOCAL_FILE` or `PACKAGE_SOURCE_DOWNLOADED_FILE`. That affects only the manual
  toggle. The role grants the app-op by itself (1.1). Whether an adb install counts as guarded was
  not traced. [verified for the list; unknown for adb]
- **Android developer verification:** Google's announced plan is enforcement from 2026-09-30 in
  Brazil, Indonesia, Singapore and Thailand and "globally ... in 2027" for certified devices. adb
  installs stay possible and an "advanced flow" for unverified apps exists
  (https://developer.android.com/developer-verification/guides,
  https://android-developers.googleblog.com/2026/03/android-developer-verification.html).
  Current enforcement status in the owner's country was not checked on a device. [inferred]
- **Boot-time reset on devices without a default speech recogniser:** see 4.1. Not expected on a
  stock Pixel. [verified in source; device-specific part inferred]
- No Play policy check, no signature or privileged-permission requirement was found in the role or
  in `VoiceInteractionManagerService` for a third-party assistant. Hotword DSP
  (`hotwordDetectionService`) is `@SystemApi` and out of reach, which does not matter here.
  [verified]

---

## 2. What happens on `AT+BVRA=1` in AOSP 17

### 2.1 Native stack to Java

1. `system/bta/ag/bta_ag_cmd.cc`: table entry `{"+BVRA", BTA_AG_AT_BVRA_EVT, BTA_AG_AT_SET,
   BTA_AG_AT_INT, 0, 1}`. If the AG does not advertise voice recognition
   (`!(p_scb->features & BTA_AG_FEAT_VREC)`) it answers `ERROR` (`BTA_AG_ERR_OP_NOT_SUPPORTED`).
2. `system/btif/src/btif_hf.cc` line 612:
   ```cpp
   case BTA_AG_AT_BVRA_EVT:
     bt_hf_callbacks->VoiceRecognitionCallback(
             (p_data->val.num == 1) ? BTHF_VR_STATE_STARTED : BTHF_VR_STATE_STOPPED,
             btif_hf_cb[idx].connected_bda);
   ```
3. `HeadsetStateMachine.processVrEvent` (line 1937):
   ```java
   if (state == HeadsetHalConstants.VR_STATE_STARTED) {
       if (!mHeadsetService.startVoiceRecognitionByHeadset(mDevice)) {
           mNativeInterface.atResponseCode(mDevice, HeadsetHalConstants.AT_RESPONSE_ERROR, 0);
       }
   } else if (state == HeadsetHalConstants.VR_STATE_STOPPED) {
       if (mHeadsetService.stopVoiceRecognitionByHeadset(mDevice)) { ... AT_RESPONSE_OK ...
   ```
   [all verified]

### 2.2 `HeadsetService.startVoiceRecognitionByHeadset` (line 1639)

Rejects (and the headset gets `ERROR`) when [verified]:

- a previous voice-recognition session was not stopped: `mVoiceRecognitionStarted` is still true.
  The service then calls `stopVoiceRecognition` itself, logs "please try again" and returns false.
  One gesture is lost.
- `!isAudioModeIdle()`: a telecom call, a virtual call or voice recognition is active.
- `isAudioOn()`: SCO is already connected.
- another request is pending (`mVoiceRecognitionTimeoutEvent != null`).
- `setActiveDevice(fromDevice)` fails.
- `mSystemInterface.activateVoiceRecognition(fromDevice)` fails.

On success:

```java
mVoiceRecognitionTimeoutEvent = new VoiceRecognitionTimeoutEvent(fromDevice);
mStateMachinesThreadHandler.postDelayed(mVoiceRecognitionTimeoutEvent, sStartVrTimeoutMs);

if (!mSystemInterface.getVoiceRecognitionWakeLock().isHeld()) {
    mSystemInterface.getVoiceRecognitionWakeLock().acquire(sStartVrTimeoutMs);
}
```

with `@VisibleForTesting static int sStartVrTimeoutMs = 5000;` (line 157). If the timeout fires,
`VoiceRecognitionTimeoutEvent.run()` sends `VOICE_RECOGNITION_RESULT, 0 /* fail */`, which becomes
`AT_RESPONSE_ERROR`. [verified]

### 2.3 The intent that is issued

`HeadsetSystemInterface.activateVoiceRecognition` (line 349) [verified]:

```java
boolean activateVoiceRecognition(BluetoothDevice fromDevice) {
    Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);
    intent.putExtra(BluetoothDevice.EXTRA_DEVICE, fromDevice);
    intent.putExtra(BluetoothProfile.EXTRA_PROFILE, BluetoothProfile.HEADSET);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    Log.d(TAG, "activateVoiceRecognition, fromDevice: " + fromDevice);
    try {
        mHeadsetService.startActivity(intent);
    } catch (ActivityNotFoundException e) { ... return false; }
    catch (SecurityException se) { ... return false; }
    return true;
}
```

- **Which component resolves it:** whatever PackageManager resolves for an implicit activity intent
  with action `android.intent.action.VOICE_COMMAND`. No package is set, no role is read. There is
  no hard-coded Google package in AOSP. [verified]
- **On Pixel / Android 17 (build `CP3A.261005.005`)**, two reports show the same thing:
  - home-assistant/android#7834 (2026-11-08), logcat:
    `START u0 {act=android.intent.action.VOICE_COMMAND flg=0x10000000 cmp=com.google.android.googlequicksearchbox/com.google.android.voicesearch.handsfree.HandsFreeActivity} with LAUNCH_MULTIPLE from uid 1002 (com.google.android.bluetooth) result code=0`.
    `dumpsys package` showed a preferred-activity entry `mAlways=true` for Google, "Selected from"
    Home Assistant, Google and Alexa. After *Settings → Apps → Google → Open by default → Clear
    default preferences*, `cmd package resolve-activity` returned
    `android/com.android.internal.app.ResolverActivity`, and after choosing Home Assistant the
    reporter wrote "once I cleared it home assistant works!". The shell could not disable Google's
    component (`Shell cannot change component state`).
  - Codename-11/hermes-relay#774 / PR #775 (2026-10-21), Pixel 8a, Nothing Ear (3): with Hermes-Relay
    as default assistant (`mBound = true`) the earbud gesture still went to Google. After adding a
    separate exported `VOICE_COMMAND` activity the reporter confirmed it "launches Hermes
    successfully with the screen both unlocked and locked". The build was a sideloaded APK.
  These are issue-tracker reports, partly written with AI tools. I treat the logcat lines as
  credible because they match the AOSP code exactly. [inferred]
- When Google's `HandsFreeActivity` receives the intent while Google is not the default assistant,
  it opens the default-assistant settings page (#7834 log; librepods-org/librepods#868 reports the
  same on HyperOS). [inferred]
- **Extras:** the `Intent.ACTION_VOICE_COMMAND` javadoc in Android 17 says the extras
  `BluetoothDevice.EXTRA_DEVICE` and `BluetoothProfile.EXTRA_PROFILE` are delivered only "for apps
  targeting versions higher than `BAKLAVA`", i.e. targetSdk 37+. [verified]
- **LE Audio:** Android 17 adds a Bluetooth "Voice Assistant profile" server
  (`android/app/src/com/android/bluetooth/vap/VapServerService.kt`, copyright 2026). It fires the
  same `ACTION_VOICE_COMMAND` with `EXTRA_PROFILE = BluetoothProfile.LE_AUDIO`. One trampoline
  therefore serves Classic and future LE Audio hearing aids. [verified]

### 2.4 Wake lock, screen, keyguard

- Wake lock: `powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + ":VoiceRecognition")`,
  i.e. tag `HeadsetSystemInterface:VoiceRecognition`, not reference counted, acquired for 5000 ms.
  A partial wake lock keeps the CPU on and does not light the display. [verified]
- No call to `PowerManager.wakeUp`, no `DeviceIdleManager.endIdle`, no keyguard call exists in
  `HeadsetService`, `HeadsetStateMachine` or `HeadsetSystemInterface` (grep for
  `wakeUp|Keyguard|DeviceIdle|endIdle|ACQUIRE_CAUSES|userActivity` returns nothing). [verified]
- An activity start does not turn the screen on by itself. `Activity.setTurnScreenOn` javadoc:
  "Normally an activity will be transitioned to the stopped state if it is started while the screen
  if off, but with this flag set the activity will cause the screen to turn on". `setShowWhenLocked`
  javadoc: "Normally an activity will be transitioned to the stopped state if it is started while
  the lockscreen is up". So without `turnScreenOn` the handler activity is created and then
  stopped, and the display stays off. [verified in docs; lifecycle order on device inferred]
- Field evidence that the screen stays off: Dicio PR #443 ("Locked, screen off: one press → the
  screen stays off, the recording is unsilenced about 300 ms after the press"), GPTWake README
  ("Succeeds with the screen off under a secure keyguard, without turning the screen on"). Both use
  `showWhenLocked` without `turnScreenOn`. [inferred]
- The Bluetooth app does not declare `START_ACTIVITIES_FROM_BACKGROUND` in its manifest (it declares
  `START_FOREGROUND_SERVICES_FROM_BACKGROUND`, `DEVICE_POWER`, `WAKE_LOCK`). Why its background
  activity start is allowed was not traced. The logs above show `result code=0` from uid 1002.
  [verified for the manifest; unknown for the exact exemption]

### 2.5 What the app must do in return

`Intent.ACTION_VOICE_COMMAND` javadoc, Android 17 (`core/java/android/content/Intent.java`,
line 1598) [verified]:

> if the `EXTRA_PROFILE` is `BluetoothProfile.HEADSET`, the app should call the following APIs to
> start voice assistant session.
> - `BluetoothHeadset#startVoiceRecognition`
> - `AudioRecord#setPreferredDevice()` for the `AudioDeviceInfo#TYPE_BLUETOOTH_SCO` device whose
>   MAC address matches the address received in `BluetoothDevice#EXTRA_DEVICE`
> - `AudioRecord#startRecording()`

For `LE_AUDIO` the list is `setPreferredDevice(TYPE_BLE_HEADSET ...)` and `startRecording()`.

`HeadsetService.startVoiceRecognition(device)` (line 786) [verified]:

- permission: `BLUETOOTH_CONNECT` only (`BluetoothHeadset.startVoiceRecognition` annotations and
  `HeadsetServiceBinder.getService`).
- if a headset request is pending it cancels the timeout, releases the wake lock and sends
  `VOICE_RECOGNITION_RESULT, 1 /* success */` → `AT_RESPONSE_OK`.
- then SCO: `stateMachine.sendMessage(HeadsetStateMachine.CONNECT_AUDIO, device)`, or, when
  `AudioManager.isScoManagedByAudio()` is true, `am.setCommunicationDevice(<SCO device>)` from the
  Bluetooth process. Which variant a Pixel 10 Pro uses is **unknown**.
- returns false if `isVoiceRecognitionSupported(device)` is false, audio mode is not idle or audio
  is already on.

Ending: the app must call `BluetoothHeadset.stopVoiceRecognition(device)`. If it does not, the
next `AT+BVRA=1` is consumed by the "workaround" branch (TODO b/79660380) and fails. When the
headset sends `AT+BVRA=0`, `stopVoiceRecognitionByHeadset` disconnects SCO and
`deactivateVoiceRecognition` sends the broadcast `Intent.ACTION_STOP_VOICE_COMMAND`
(`android.intent.action.STOP_VOICE_COMMAND`, flagged API) with `sendBroadcast(intent)` and no
flags. A runtime-registered receiver in an always-running component is the safe way to receive it.
[verified for the code; receiver delivery inferred]

### 2.6 Other headset routes

| Route | Code | Result | Screen |
|---|---|---|---|
| HFP `AT+BVRA=1` | 2.1–2.5 | activity intent `android.intent.action.VOICE_COMMAND` | stays off unless handler asks |
| LE Audio VAP (new in 17) | `vap/VapServerService.kt` | same intent, `EXTRA_PROFILE = LE_AUDIO` | same |
| Long press `KEYCODE_HEADSETHOOK` or (non-TV) `KEYCODE_MEDIA_PLAY_PAUSE` | `MediaSessionService.startVoiceInput` (line 2929), `isVoiceKey` (line 2967) | not locked and screen on → `RecognizerIntent.ACTION_WEB_SEARCH` (`android.speech.action.WEB_SEARCH`); locked or screen off → `RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE` (`android.speech.action.VOICE_SEARCH_HANDS_FREE`) with `EXTRA_SECURE = isLocked && isKeyguardSecure()`; partial wake lock `handleMediaEvent` | media keys from non-alphabetic external devices are not wake keys |
| `KEYCODE_VOICE_ASSIST` (Linux key 582 in `data/keyboards/Generic.kl`) | `PhoneWindowManager.interceptKeyBeforeQueueing` → `launchVoiceAssistWithWakeLock` (line 5190) | keyguard off → `ACTION_WEB_SEARCH`; keyguard on → `DeviceIdleManager.endIdle("voice-search")`, then `ACTION_VOICE_SEARCH_HANDS_FREE` with `EXTRA_SECURE=true` | **risk:** `KeyboardInputMapper.cpp` sets `POLICY_FLAG_WAKE` for key-down from external devices unless `isMediaKey(keyCode)`; `AKEYCODE_VOICE_ASSIST` is not in `isMediaKey`, and `isWakeKeyWhenScreenOff` returns true by default. A HID "voice command" key would wake the display. [inferred from code] |
| `KEYCODE_ASSIST` (key 583) | `PhoneWindowManager` → `launchAssistAction` → `SearchManager.launchAssist` → SystemUI `AssistManager.startAssist` | `showSessionForActiveService(args, SHOW_SOURCE_ASSIST_GESTURE, ...)` if the assistant is a service, else `ACTION_ASSIST` activity | wakes only if `mWakeOnAssistKeyPress` |
| AVRCP passthrough | `AvrcpTargetService` → `mAudioManager.dispatchMediaKeyEvent(new KeyEvent(action, keyCode))`; `AvrcpPassthrough.toKeyCode` has no assist/voice key | ordinary media keys, no repeat/long-press flag, so no long-press voice trigger from AVRCP | no input-pipeline wake |
| "Voice assist from keyguard" | `VoiceInteractionManagerService.launchVoiceAssistFromKeyguard` → `VoiceInteractionService.onLaunchVoiceAssistFromKeyguard()` | In the Android 17 SystemUI source the only references to `launchVoiceAssistFromKeyguard` and `canVoiceAssistBeLaunchedFromKeyguard` are their definitions in `assist/AssistManager.java`. No caller. Pixel's SystemUI is closed source. | [verified for AOSP; unknown for Pixel] |
| `ACTION_VOICE_ASSIST` | `PhoneWindowManager.launchVoiceAssist`: comment "Does nothing on keyguard except for watches." | power-key style launch only | n/a |

All of the first four routes end in an **activity intent**, none in the `VoiceInteractionService`.
Only `KEYCODE_ASSIST`, the power-button long press and gestures reach the service
(`showSessionForActiveService`). [verified]

---

## 3. Evidence from real third-party assistants

### 3.1 Home Assistant Companion (`home-assistant/android`, Apache-2.0)

`app/src/main/AndroidManifest.xml` lines 554–616 [verified]:

- `.assist.AssistActivity`, exported, `android:showWhenLocked="true"`, filter `ACTION_ASSIST`.
- `<activity-alias android:name=".assist.VoiceCommandIntentActivity" android:targetActivity=".assist.AssistActivity">`
  with filter `android.intent.action.VOICE_COMMAND` + `DEFAULT`. The alias can be switched on and off
  from settings ("Open with headset button").
- `.assist.service.AssistVoiceInteractionService`, `android:foregroundServiceType="microphone"`,
  `android:permission="android.permission.BIND_VOICE_INTERACTION"`, meta-data
  `@xml/voice_interaction_service` = `sessionService`, `recognitionService`,
  `supportsAssist="true"`, `supportsLaunchVoiceAssistFromKeyguard="true"`, `settingsActivity`.
- a stub `AssistRecognitionService` that answers `ERROR_CLIENT`; `res/xml-v31/recognition_service.xml`
  sets `android:selectableAsDefault="false"`.
- permissions include `SYSTEM_ALERT_WINDOW` and `MANAGE_ASSISTANT_AUDIO`.
- The service itself calls `startForeground(NOTIFICATION_ID, ..., FOREGROUND_SERVICE_TYPE_MICROPHONE)`
  for wake-word listening and `showSession(args, 0)` on a hit
  (`assist/service/AssistVoiceInteractionService.kt`).
- The session only starts the activity and finishes; `finish()` is posted "so it runs after the
  framework's doShow() completes ... causing BadTokenException" otherwise
  (`AssistVoiceInteractionSession.kt`).
- When locked, `AssistActivity.updateShowWhenLocked` calls `setShowWhenLocked(true)` **and**
  `setTurnScreenOn(true)`. Home Assistant wants the screen. We must not copy that part.

Issue tracker:

- #7834 (2026-11-08): see 2.3. Role holder did not receive the headset trigger until the
  `VOICE_COMMAND` preferred activity was changed.
- #6433 (open): "Bluetooth headset microphone not used for Assistant voice input (audio input
  remains on phone mic)". The app records with the default source and never selects SCO.
- #7869, #7825 (2026-11): on GrapheneOS / LineageOS without a default speech recogniser the
  assistant silently unbinds after every reboot. `dumpsys voiceinteraction` shows "(No active
  implementation)", `voice_interaction_service` is empty while the role is still held. See 4.1.
- #4457 (2024): users complained that the app reacted to the headset long-press they use to switch
  the headset off. That is why the alias is optional.

### 3.2 Dicio (`DicioTeam/dicio-android`, GPL-3.0)

- Manifest: no `VoiceInteractionService`. `MainActivity` has one filter with `ACTION_ASSIST` and
  `ACTION_VOICE_COMMAND`, `android:showOnLockScreen="true"`. It qualifies for the role through the
  activity rule only. [verified]
- PR #228 (2024) added `ACTION_VOICE_COMMAND`; a tester confirmed "That APK works great!" (#226).
- PR #443 (open, 2026-09-27), from the author's logcat on an Android 16 phone:
  "A long press on the headset button makes the headset send `AT+BVRA`, and
  `HeadsetService.startVoiceRecognitionByHeadset` then starts `MainActivity` with
  `ACTION_VOICE_COMMAND`. This path is the same whether the phone is locked or not."
  "**Locked:** `MainActivity` receives the intent and starts listening, but it does not show above
  the keyguard ... It is stopped about 130 ms later, and AudioFlinger silences the recording
  (`AF::RecordTrack: setSilenced ... (silenced)`), so Vosk only receives silence."
  The fix: `setShowWhenLocked(true)` without `setTurnScreenOn` for `ACTION_VOICE_COMMAND`, plus a
  `foregroundServiceType="microphone"` service started from `onResume()`. Reported result: "Locked,
  screen off: one press → the screen stays off ... Vosk recognizes the speech and the answer is
  spoken." The PR is unmerged.
- #116 / #229: the phone microphone is used instead of the headset microphone.
- #475 (2026-11-20): as default assistant without a `VoiceInteractionService` and without
  `SYSTEM_ALERT_WINDOW` in the manifest, starting other apps from the background is blocked on
  Android 14+ ("Background activity launch blocked").
- #154, #305: speech input fails when the screen is off or locked.

Lesson: an activity-only assistant gets no background privileges. Its microphone is silenced as soon
as its activity is stopped under the keyguard.

### 3.3 GPTWake (`suddenBook/GPTWake`)

- The repository exists. Licence: the `LICENSE` file is a shortened Apache-2.0 notice ("Licensed
  under the Apache License, Version 2.0 ... Full text: https://www.apache.org/licenses/LICENSE-2.0.txt").
  GitHub's API reports `NOASSERTION` because the file is not the full text. [verified]
- It is **not** an assistant. It is a wake-word listener that starts the ChatGPT app. README:
  "This app must never take the assistant role for itself; that role has to stay with ChatGPT or
  lock-screen capture breaks."
- Its measured table (Android 16, Lenovo TB355FU tablet, not a Pixel):
  - "ChatGPT cannot obtain the microphone under keyguard | ChatGPT must remain the system default
    assistant ... | Taking the role away yields `RECORD_AUDIO duration=0`; restoring it restores
    capture"
  - "Background activity launch is blocked by BAL | `SYSTEM_ALERT_WINDOW` exemption"
  - "A microphone foreground service cannot be started from the background | A transparent
    `showWhenLocked` shim activity supplies a visible moment | Succeeds with the screen off under a
    secure keyguard, without turning the screen on"
  - "`startActivity()` does not throw when BAL blocks it, so success must be confirmed through a
    side channel"
- `ShimActivity`: `android:showWhenLocked="true" android:turnScreenOn="false"
  android:excludeFromRecents="true" android:noHistory="true"`, theme
  `android:Theme.Translucent.NoTitleBar`; it calls `startForegroundService` from `onResume` (plus
  300 ms) or on window focus and finishes on an ACK from the service.

Lesson: the role holder can record under the keyguard, other apps cannot without a visible moment.
This is the best field evidence for section 4.

### 3.4 Others found

- **Codename-11/hermes-relay** (MIT): a Hermes Agent client with a full `VoiceInteractionService`
  (`sessionService` in a separate `:assistant_session` process, stub recognition service,
  `supportsAssist`, `supportsLaunchVoiceAssistFromKeyguard`, `supportsLocalInteraction`). Issue #774
  and PR #775: see 2.3. The closest existing project to ours.
- **kerero/airdots-double-tap** (MIT): the exact trampoline pattern. One activity with
  `android:theme="@android:style/Theme.NoDisplay"`, `android:showOnLockScreen="true"`,
  `launchMode="singleInstance"` and a `VOICE_COMMAND` filter. `onCreate` does the work and calls
  `finish()`. README: "The device will ask you with which app to complete the action / Select
  always". Tested by its author on Android 8.1–12. It turns an earbud assistant gesture into "next
  track".
- **WSTxda/SwitchAI** (GPL-3.0): assistant switcher. #9: a user reports the chooser listing "Bixby,
  Alexa, and Google" on a headset long-press and that adding
  `<action android:name="android.intent.action.VOICE_COMMAND" />` made the app selectable. #91
  (2026-08): with the phone locked, ChatGPT is launched by the headset gesture but "does not react
  to anything I say while the phone remains locked"; it works after unlocking.
- **omarqaterge/hermes-mobile-app** (MIT): Hermes on-device client; `ACTION_ASSIST` +
  `VOICE_COMMAND` on its main activity, no `VoiceInteractionService`.
- **android/appfunctions** `agent` sample (Apache-2.0, Google, 2026): an official third-party
  assistant sample with the same three-service layout and the same XML attributes.
- **librepods-org/librepods** (GPL-3.0) #868: confirms Google's handler opens the default-assistant
  settings when another assistant is selected.

---

## 4. Privileges of the currently selected `VoiceInteractionService`

### 4.1 Kept bound without a foreground service

`VoiceInteractionManagerServiceImpl.startLocked()` (line 1318) [verified]:

```java
mBound = mContext.bindServiceAsUser(
        intent, mConnection,
        Context.BIND_AUTO_CREATE
                | Context.BIND_FOREGROUND_SERVICE
                | Context.BIND_INCLUDE_CAPABILITIES
                | Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS,
        new UserHandle(mUser));
```

- Class javadoc and developer.android.com: "The current VoiceInteractionService that has been
  selected by the user is kept always running by the system". [verified]
- Process state: for a client above TOP (persistent `system_server`),
  `cr.hasFlag(Context.BIND_FOREGROUND_SERVICE)` gives
  `clientProcState = PROCESS_STATE_BOUND_FOREGROUND_SERVICE`
  (`services/core/java/com/android/server/am/psc/OomAdjusterImpl.java`, line ~2107). [verified]
- Capabilities: `if (cr.hasFlag(Context.BIND_INCLUDE_CAPABILITIES)) { capability |=
  client.getCurCapability(); }` and fixed-adj processes get `PROCESS_CAPABILITY_ALL`
  (same file, lines 1930 and 1264). [verified]
- Also set for the role holder: `mAtmInternal.setAllowAppSwitches(TAG, serviceInfo.applicationInfo.uid, mCurUser)`.
  [verified]
- **Boot-time caveat.** `initForUserNoTracing` runs at every boot. It returns early ("Current
  interactor/recognizer okay, done!") only if `Settings.Secure.VOICE_RECOGNITION_SERVICE` points to
  an installed recogniser whose `RecognitionServiceInfo.isSelectableAsDefault()` is true. Otherwise
  it calls `findAvailInteractor(userHandle, null)`, which skips every non-system package
  (`if ((cur.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) continue;`), and then
  `setCurInteractor(null, userHandle)`. A sideloaded assistant is unbound although it still holds
  the role. This is what Home Assistant #7869 and #7825 describe. On a stock Pixel a Google
  recogniser is normally the default, so the early return applies. [verified in source; Pixel state
  inferred]

### 4.2 Recording audio and starting a microphone foreground service from the background

- App-op: `AppOpsUidStateTrackerImpl.evalModeInternal` maps `OP_RECORD_AUDIO` to
  `PROCESS_CAPABILITY_FOREGROUND_MICROPHONE` and returns `MODE_ALLOWED` when the uid has it
  (`services/core/java/com/android/server/appop/AppOpsUidStateTrackerImpl.java`, lines 144–183).
  The bound service process has it through `BIND_INCLUDE_CAPABILITIES`. [verified]
- Foreground-service start: `ActiveServices.shouldAllowFgsStartForegroundNoBindingCheckLocked`
  allows `procstate <= PROCESS_STATE_BOUND_FOREGROUND_SERVICE` when the uid has
  `PROCESS_CAPABILITY_BFSL` (line 9523). [verified]
- While-in-use for the service: `shouldAllowFgsWhileInUsePermissionLocked` (line 9164) returns
  `REASON_ACTIVITY_STARTER` when `areBackgroundFgsStartsAllowed()`; the comment says "The binding
  flag BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS is also allowed by the check here." There is also
  `shouldAllowFgsWhileInUsePermissionByBindingsLocked`: "bound by a persistent process with
  BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS". [verified]
- Official documentation, "Exemptions from restrictions on while-in-use permissions":
  "The service starts by an app which provides the `VoiceInteractionService`."
  (https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
  [verified]
- Audio policy: `AudioPolicyService::updateUidStates_l` (frameworks/av) ignores clients in
  `APP_STATE_IDLE` and otherwise allows "Any client AND The assistant is not on TOP AND is on TOP
  or latest started AND there is no active privacy sensitive capture or call". [verified for the
  comment block; the exact outcome for our uid is inferred]
- Field evidence: GPTWake's role experiment (3.3) and Home Assistant's wake word running in the
  service as a microphone foreground service. [inferred]
- Fallback when the app is not the role holder: a just-started activity gives a 10 s window.
  `ACTIVITY_BG_START_GRACE_PERIOD_MS = 10 * 1000` (`ActivityTaskManagerService.java` line 348) is
  used by `BackgroundLaunchProcessController` for both activity and FGS starts. [verified for the
  constant; that it is enough under a keyguard with the screen off is inferred from GPTWake and
  Dicio PR #443, which both needed `showWhenLocked`]

### 4.3 Starting activities from the background

- `BackgroundLaunchProcessController.isBackgroundStartAllowedByToken` returns
  `BAL_ALLOW_TOKEN, "process allowed by token"` with `allowNewTask()` for a binding made with
  `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS`; `ActiveServices.bindServiceLocked` sets
  `s.setAllowedBgActivityStartsByBinding(true)` for that flag and requires the binding
  caller to hold `START_ACTIVITIES_FROM_BACKGROUND`, which `system_server` does. [verified]
- Plus the role's `SYSTEM_ALERT_WINDOW` app-op if declared (1.1). For apps targeting Android 15+
  that permission alone no longer allows a background FGS start: "it must have the
  `SYSTEM_ALERT_WINDOW` permission *and* the app must currently have a visible overlay window".
  [verified in docs and in `ActiveServices` `FGS_SAW_RESTRICTIONS`]

### 4.4 A session without UI and without turning the screen on

`core/java/android/service/voice/VoiceInteractionSession.java` [verified]:

- `setUiEnabled(boolean)`: "Control whether the UI layer for this session is enabled. It is enabled
  by default. If set to false, you will not be able to provide a UI through
  `onCreateContentView()`."
- `doShow` calls `onPrepareShow(args, flags)` first, then `ensureWindowAdded()`, which does nothing
  when `mUiEnabled` is false (`if (mUiEnabled && !mWindowAdded)`). So calling `setUiEnabled(false)`
  in `onPrepareShow` yields a session with no window.
- If a window is created it is `TYPE_VOICE_INTERACTION` with `FLAG_HARDWARE_ACCELERATED |
  FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_INSET_DECOR`. No turn-screen-on flag.
- No `wakeUp` call exists in `VoiceInteractionSessionConnection` or
  `VoiceInteractionManagerServiceImpl` (only a power boost and a low-power-standby allowlist entry).
- `onLockscreenShown()` default implementation is `hide()`. Override it if a session must survive
  the lock screen appearing.
- `setKeepAwake`: "By default, the system holds a wake lock for it while in this state" refers to a
  running voice activity, not to the display.
- Android 17 "self-trigger" rule (`enforceSessionFlags`, `isSelfTriggerAllowed`): a session that the
  service starts itself from the background only loses the screen-content flags. The session
  itself is not blocked.

For this project a session is not needed for paths A, B and C. The service is the always-running
anchor; the dialogue can run in a normal microphone foreground service of the same app.

---

## 5. Phonak Tap Control "voice assistant" on Android

Verified from Phonak documents:

- Phonak Audéo E (EON) user guide 029-1462-02
  (https://www.phonak.com/content/dam/celum/phonak/master-assets/documents/hearing-instruments/eon/audeo-e-r/user-guides/ph-user-guide-audeo-e-92x125-029-1462-02-en.pdf.coredownload.pdf),
  valid for "Audéo E-R (E90/E70/E50/E30)" and "Audéo E-Sphere (E90/E70/E50)":
  - chapter 9: "If paired with a Bluetooth® enabled device, multiple functions can be accessed using
    Tap Control e.g. accept/end phone call, pause/resume streaming, start/stop voice assistant.
    This is indicated in your individual "Hearing aid instructions". To use Tap Control, tap the
    top of your ear twice."
  - radio data: "Core version: Bluetooth® Core 5.3, Dual-Mode. Profiles supported: HFP (Hands-free
    profile), A2DP, PBP (Public Broadcast Profile)".
  - "Phone calls are "hands free", this means your voice is picked up by the hearing aid's
    microphones and transmitted back to the phone."
  - "Your hearing aid can be connected to up to two devices and paired to up to eight devices."
  - The EON guide has no sentence restricting Tap Control to certain performance levels. The
    previous generation's guide (Audéo I-R, 029-1357-02) says "Tap Control is only available for
    models I90 and I70." Whether an E50 or E30 has it is **unknown**.
- Phonak training course for Target 7.0 (AudiologyOnline, 2020-09-21, transcript
  https://www.audiologyonline.com/files/event/35800/35806/transcript-9-21-20-cont-paradiseintarget7-0.pdf):
  "by default ... Tap Control is turned on, so right and left ear both accept and end a phone call.
  And then you have some customization here, so you and your patient can decide what do you want,
  you know, your right ear, your left ear to do, or both ears to do, stop and resume streaming, or
  start and stop your voice assistant, like Siri, or like Google." The wearer can change it in the
  myPhonak app. Tap Control needs the motion sensor of the rechargeable models.
- Phonak knowledge base
  (https://www.phonak.com/en-us/support/knowledge-base/articles/what-hearing-aid-should-be-selected-from-the-list-on-the-mobile-phone):
  for models before Infinio Ultra, the entry "R-Phonak or L-Phonak" is "the hearing aid connected
  to the phone via Bluetooth Classic for streaming and calls"; it is normal that only one aid has
  it and the hearing care professional sets the side. With one hearing aid only that side appears.
- Phonak knowledge base on accidental activation
  (https://www.phonak.com/en-au/support/knowledge-base/articles/why-do-my-phonak-hearing-aids-hang-up-calls-pause-streaming-or-activate-voice-assistant-unexpectedly):
  high Tap Control sensitivity can trigger the voice assistant unexpectedly; set it to "Firm".

Inferences:

- **Mechanism: HFP voice-recognition activation (`AT+BVRA=1`, and `AT+BVRA=0` for "stop").** Phonak
  does not name the command. Reasons: HFP is the only listed profile that can carry such a request;
  "start/stop" matches the two BVRA values; on Samsung phones the tap opened Bixby although Google
  was the default assistant and was fixed by disabling Bixby Voice
  (https://forum.hearingtracker.com/t/phonak-paradise-tap-control-and-google-assistant/58895),
  which is how `ACTION_VOICE_COMMAND` resolution behaves and not how the assistant role behaves.
  Confidence: medium-high. [inferred]
- **Single right aid:** each rechargeable aid has its own motion sensor (functions are assignable
  per ear) and in a one-aid fitting that aid holds the Bluetooth Classic link. The voice-assistant
  function must be assigned to the right ear in Target or myPhonak. It then replaces "pause/resume
  streaming" on that ear, because the per-ear choice is one or the other. Confidence: medium-high.
  No Phonak text says "works with one aid" in those words. [inferred]
- **Two-device caveat:** with two active Bluetooth connections the request may go to the other
  device. A Pixel 6 user reports that the aids sometimes "lost assistant priority" until
  Bluetooth was reconnected (same forum thread). [inferred, single report]

Unknown: whether the EON firmware sends `AT+BVRA` exactly as older generations, the latency from
tap to command, and behaviour while A2DP is streaming.

---

## 6. Recommendation

### 6.1 Manifest

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.INTERNET" />
    <!-- Granted as an app-op by ROLE_ASSISTANT, but only if declared here (roles.xml). -->
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <!-- Needed to see competing handlers when self-checking the resolution. -->
    <queries>
        <intent><action android:name="android.intent.action.VOICE_COMMAND" /></intent>
        <intent><action android:name="android.speech.action.VOICE_SEARCH_HANDS_FREE" /></intent>
    </queries>

    <application ...>

        <!-- Path A. Bluetooth HFP AT+BVRA=1 and LE Audio VAP both start this intent. -->
        <activity
            android:name=".trigger.AssistantGestureActivity"
            android:exported="true"
            android:theme="@android:style/Theme.NoDisplay"
            android:showWhenLocked="true"
            android:turnScreenOn="false"
            android:excludeFromRecents="true"
            android:noHistory="true"
            android:taskAffinity="" />

        <activity-alias
            android:name=".trigger.BluetoothVoiceCommand"
            android:targetActivity=".trigger.AssistantGestureActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.VOICE_COMMAND" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity-alias>

        <!-- Long-press HEADSETHOOK / PLAY_PAUSE and KEYCODE_VOICE_ASSIST while locked. -->
        <activity-alias
            android:name=".trigger.HandsFreeVoiceSearch"
            android:targetActivity=".trigger.AssistantGestureActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.speech.action.VOICE_SEARCH_HANDS_FREE" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity-alias>

        <!-- Privilege anchor. Selected in Settings as default digital assistant. -->
        <service
            android:name=".assistant.LaraVoiceInteractionService"
            android:exported="true"
            android:permission="android.permission.BIND_VOICE_INTERACTION">
            <intent-filter>
                <action android:name="android.service.voice.VoiceInteractionService" />
            </intent-filter>
            <meta-data
                android:name="android.voice_interaction"
                android:resource="@xml/voice_interaction_service" />
        </service>

        <service
            android:name=".assistant.LaraSessionService"
            android:exported="true"
            android:permission="android.permission.BIND_VOICE_INTERACTION" />

        <service
            android:name=".assistant.LaraRecognitionService"
            android:exported="true"
            android:permission="android.permission.BIND_VOICE_INTERACTION">
            <intent-filter>
                <action android:name="android.speech.RecognitionService" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
            <meta-data android:name="android.speech" android:resource="@xml/recognition_service" />
        </service>

        <!-- The dialogue itself. Started by every input path. -->
        <service
            android:name=".session.DialogueService"
            android:exported="false"
            android:foregroundServiceType="microphone" />
    </application>
</manifest>
```

`res/xml/voice_interaction_service.xml`:

```xml
<voice-interaction-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:sessionService="PACKAGE.assistant.LaraSessionService"
    android:recognitionService="PACKAGE.assistant.LaraRecognitionService"
    android:supportsAssist="true"
    android:supportsLaunchVoiceAssistFromKeyguard="true" />
```

Notes on choices:

- `targetSdk` 37, so that `EXTRA_DEVICE` and `EXTRA_PROFILE` arrive (2.3).
- `Theme.NoDisplay` with `finish()` in `onCreate` shows nothing even when the screen is on. This is
  the airdots-double-tap pattern. `showWhenLocked="true"` and `turnScreenOn="false"` are stated
  explicitly. The activity never reaches a visible state.
- Aliases keep the two entry routes separately loggable and separately switchable
  (`PackageManager.setComponentEnabledSetting`), as Home Assistant does.
- No `ACTION_ASSIST` activity is required: the service alone qualifies for the role (1.2).
- Do **not** put `android:selectableAsDefault="false"` on the recognition service unless a device
  test shows a Google recogniser is always the default (4.1). The trade-off: a selectable stub
  could become the system recogniser on a phone that has no other one.
- Leave `usesAssist*` unset: no screen content is requested.

### 6.2 Service and activity skeleton (Kotlin)

```kotlin
class AssistantGestureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        val profile = intent.getIntExtra(BluetoothProfile.EXTRA_PROFILE, -1)   // HEADSET or LE_AUDIO
        InputRouter.onAssistantGesture(applicationContext, intent.action, device, profile)
        finish()                                  // mandatory for Theme.NoDisplay
    }
}

class LaraVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        // Always-running anchor: get the BluetoothHeadset proxy NOW so the 5 s budget is not spent on it.
        BluetoothProxyHolder.connect(this)
        // Runtime receivers: Intent.ACTION_STOP_VOICE_COMMAND, BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED
        // Start path B (BLE scan) and path C (MediaSession) owners here as well.
        ResolutionWatchdog.check(this)            // does VOICE_COMMAND still resolve to us?
    }
    override fun onLaunchVoiceAssistFromKeyguard() =
        InputRouter.onAssistantGesture(applicationContext, "keyguard", null, -1)
}

class LaraSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?) = object : VoiceInteractionSession(this) {
        override fun onPrepareShow(args: Bundle?, showFlags: Int) { setUiEnabled(false) }
        override fun onShow(args: Bundle?, showFlags: Int) {
            InputRouter.onAssistantGesture(context, "assist-session", null, -1)
            finish()   // post it instead if a window is ever enabled (Home Assistant hit BadTokenException)
        }
        override fun onLockscreenShown() { /* default would hide() */ }
    }
}

class DialogueService : Service() {
    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        wakeLock.acquire(SESSION_TIMEOUT_MS)                       // PARTIAL_WAKE_LOCK, our own
        // Path A, EXTRA_PROFILE == HEADSET: answer within 5000 ms.
        //   headset.startVoiceRecognition(device)                 -> AT OK + SCO
        //   wait for ACTION_AUDIO_STATE_CHANGED == STATE_AUDIO_CONNECTED
        //   audioRecord.setPreferredDevice(<TYPE_BLUETOOTH_SCO with device.address>); startRecording()
        // Always at the end, on every exit path:
        //   headset.stopVoiceRecognition(device)
        return START_NOT_STICKY
    }
}
```

`InputRouter.onAssistantGesture` calls `startForegroundService(Intent(ctx, DialogueService::class.java))`.
All three input paths call the same router.

### 6.3 Failure modes to expect

| # | Failure | Signature | Countermeasure |
|---|---|---|---|
| 1 | No "Always" handler for `VOICE_COMMAND`: `ResolverActivity` opens invisibly behind the keyguard, 5 s later the headset gets `ERROR` | logcat `cmp=android/com.android.internal.app.ResolverActivity`; `HeadsetService` timeout | one-time setup while unlocked; spoken self-check at start |
| 2 | Google's `HandsFreeActivity` is the preferred handler | logcat `cmp=com.google.android.googlequicksearchbox/...HandsFreeActivity`; settings page opens | `adb shell pm clear-package-preferred-activities com.google.android.googlequicksearchbox`, then choose ours |
| 3 | Preference silently dropped when any other app with a `VOICE_COMMAND` activity is installed | logcat `Result set changed, dropping preferred activity for Intent { act=android.intent.action.VOICE_COMMAND` (`ComputerEngine.java` line 3488) | `ResolutionWatchdog` on `ACTION_PACKAGE_ADDED` and at start |
| 4 | 5 s budget missed (proxy not ready, cold start because the role was lost) | `VoiceRecognitionTimeoutEvent`, headset error tone | keep the proxy in the always-running service |
| 5 | Next gesture ignored once | `startVoiceRecognitionByHeadset: voice recognition is still active, just called stopVoiceRecognition ... please try again` | call `stopVoiceRecognition` on every exit path |
| 6 | Gesture rejected during a call or while SCO is up | `audio mode not idle`, `audio is still active` | none; announce nothing |
| 7 | Recording comes from the phone microphone | Home Assistant #6433, Dicio #116 | `setPreferredDevice` to the SCO input and verify with `AudioRecord.getRoutedDevice()` |
| 8 | Recording is silent | `AF::RecordTrack: setSilenced`; `Foreground service started from background can not have location/camera/microphone access` | must be role holder; microphone-type FGS; check `dumpsys voiceinteraction` |
| 9 | Service unbound after reboot | `dumpsys voiceinteraction` → `(No active implementation)`; `settings get secure voice_interaction_service` empty | check `voice_recognition_service`; health check after boot |
| 10 | Role or preference lost after reinstall with another signing key | role holder empty | one stable signing key for adb and release builds; `adb install -r` |
| 11 | Screen lights up | another handler with `turnScreenOn`; a HID device sending `KEYCODE_VOICE_ASSIST` | be the only preferred handler; do not use HID voice keys |
| 12 | Tap Control absent or mis-assigned on the aid | no `AT+BVRA` in the HCI log | audiologist: model level, right-ear assignment, sensitivity |
| 13 | Accidental double-tap opens the microphone | Phonak knowledge-base article | earcon on start, short no-speech timeout, "Firm" sensitivity |
| 14 | Request goes to a second connected Bluetooth device | no log on the phone | keep only the phone connected during tests |
| 15 | Doze: the Bluetooth path holds only a 5 s partial wake lock and does not end idle | work stalls after 5 s | own wake lock in `DialogueService` |

### 6.4 A test that can be run today with any ordinary Bluetooth headset

Requirement: a headset whose button triggers the *phone's* assistant through HFP (typical: long
press or triple press of the multi-function button). Headsets with a built-in Google Assistant
integration may use a proprietary channel instead and are unsuitable.

Build a spike APK (targetSdk 37) with exactly the manifest above. `DialogueService` does four things
and logs each with a timestamp to logcat and to a file: (1) play a short tone, (2) call
`startVoiceRecognition`, (3) record 5 s and log the RMS level and `getRoutedDevice()`, (4) play the
recording back and call `stopVoiceRecognition`.

```sh
adb install -r lara-spike.apk
adb shell pm grant PACKAGE android.permission.RECORD_AUDIO
adb shell pm grant PACKAGE android.permission.BLUETOOTH_CONNECT
adb shell pm grant PACKAGE android.permission.POST_NOTIFICATIONS

# 1. Role
adb shell cmd role add-role-holder android.app.role.ASSISTANT PACKAGE      # or via Settings
adb shell cmd role get-role-holders android.app.role.ASSISTANT
adb shell settings get secure voice_interaction_service                    # must name our service
adb shell settings get secure voice_recognition_service                    # must not be null
adb shell dumpsys voiceinteraction | head -40                              # mBound=true

# 2. Intent resolution
adb shell cmd package query-activities --brief -a android.intent.action.VOICE_COMMAND -c android.intent.category.DEFAULT
adb shell pm clear-package-preferred-activities com.google.android.googlequicksearchbox
#    Phone unlocked: press the headset assistant button, pick the app, "Always".
adb shell cmd package resolve-activity --brief -a android.intent.action.VOICE_COMMAND -c android.intent.category.DEFAULT

# 3. Dry run without a headset: lock the phone, screen off, then
adb shell am start -a android.intent.action.VOICE_COMMAND -f 0x10000000
adb shell dumpsys power | grep -E "mWakefulness="                           # expect Asleep

# 4. Real run: phone locked, screen off, in a pocket. Press the headset button.
adb logcat -v threadtime | grep -E "HeadsetService|HeadsetSystemInterface|HeadsetStateMachine|ActivityTaskManager: START|VoiceInteractionManager|RecordTrack|Lara"
```

Pass criteria for step 4:

1. `HeadsetService: startVoiceRecognitionByHeadset: from <addr>` and
   `HeadsetSystemInterface: activateVoiceRecognition, fromDevice: <addr>`.
2. `ActivityTaskManager: START u0 {act=android.intent.action.VOICE_COMMAND ... cmp=PACKAGE/.trigger.BluetoothVoiceCommand} ... from uid 1002`.
3. `HeadsetService: startVoiceRecognition: device=<addr>` from our uid less than 5 s after line 1.
4. The tone and the playback are heard in the headset; RMS is clearly above the noise floor;
   `getRoutedDevice()` is `TYPE_BLUETOOTH_SCO`.
5. `mWakefulness=Asleep` throughout; no display activity.
6. A second button press produces `stopVoiceRecognitionByHeadset` and our stop handling.

Repeat step 4 after a reboot and after 2+ hours untouched and unplugged (real Doze). Do not
force-stop the app between runs: that kills the bound service process. To see the raw `AT+BVRA`, enable "Bluetooth HCI snoop log" in developer
options, reproduce, take `adb bugreport` and open `btsnoop_hci.log` in Wireshark (filter `bthfp`).
The same capture later proves or disproves the Phonak inference in section 5.

---

## 7. Corrections to the inherited planning notes

1. Notes, architecture table row A: "Phonak-Geste löst systemweiten Android-Sprachassistenten aus
   (`VoiceInteractionService` und zugehörige Session)". **Wrong mechanism.** A hands-free
   voice-recognition request starts an *activity* with `ACTION_VOICE_COMMAND`. The service and the
   role are not involved in routing it.
2. Notes, section 6: "`VoiceInteractionService` ... Dies ist der korrekte Integrationsweg für
   Eingang A". **Incomplete.** The service gives privileges. The entry point for A is an exported
   activity plus a one-time "Always" choice.
3. Notes treat "default assistant role set" as sufficient setup for A (test plan: "Default-Assistant-Rolle
   einrichten; Doppeltipp am Phonak ... testen"). **Unsupported.** Two Pixel/Android 17 reports show
   the role holder not receiving the headset gesture.
4. Notes on GPTWake: accurate that it needs ChatGPT to hold the role and listens permanently. Add:
   licence is Apache-2.0 by its `LICENSE` notice (GitHub shows no SPDX id); measured on a Lenovo
   tablet with Android 16, not on a Pixel.
5. Notes say the phone was "als Pixel 11 Pro / Android 17 bezeichnet"; the task brief says Pixel 10
   Pro. Unresolved. The AOSP analysis here is for Android 17; Android 16 was not diffed.
6. Notes: "Phonak RemoteControl ... kein dokumentierter Weg zum Android-Assistenten" — nothing found
   that contradicts it.
7. The projects named in the notes exist: `ijustlikeit/phonak-ha` (MIT), `OferTiber/SaraButton`
   (MIT), `serrebidev/BlindPilot-Android` (MIT), `darth-modder/hermes-mobile-client` (MIT),
   `NousResearch/hermes-agent` (MIT). Their internals were not reviewed here.

---

## 8. Related projects worth linking in a README

| Project | Licence | Why it is interesting |
|---|---|---|
| https://github.com/home-assistant/android | Apache-2.0 | Complete third-party assistant: role service, session, stub recogniser, `VOICE_COMMAND` alias with a settings switch, wake word inside the service |
| https://github.com/Codename-11/hermes-relay | MIT | Hermes Agent client that is a default assistant; PR #775 adds the Bluetooth entry and was confirmed locked on a Pixel 8a |
| https://github.com/omarqaterge/hermes-mobile-app | MIT | Hermes on the phone (Termux), assistant and headset entry through one activity |
| https://github.com/kerero/airdots-double-tap | MIT | Smallest example of turning an earbud assistant gesture into your own action with a no-display activity |
| https://github.com/suddenBook/GPTWake | Apache-2.0 (notice) | Measured lock-screen constraints: role, background activity launch, microphone service |
| https://github.com/DicioTeam/dicio-android | GPL-3.0 | Offline assistant; PR #443 documents the locked-phone headset path. GPL: read, do not copy into an Apache-2.0 code base |
| https://github.com/WSTxda/SwitchAI | GPL-3.0 | Assistant switcher; issues show what breaks when the launched app is not the role holder |
| https://github.com/android/appfunctions | Apache-2.0 | Google's 2026 sample agent with a `VoiceInteractionService` |
| https://github.com/librepods-org/librepods | GPL-3.0 | AirPods on Android; same `VOICE_COMMAND` routing problem (#868) |
| https://github.com/Kaljurand/K6nele | Apache-2.0 | Long-lived speech-recognition service app with `VOICE_COMMAND` handling |
| https://github.com/ijustlikeit/phonak-ha | MIT | Phonak control over BLE from Android (from the notes; not reviewed here) |
| https://github.com/OferTiber/SaraButton | MIT | Shelly BLU buttons on Android (from the notes; not reviewed here) |

---

## 9. Open points

- Pixel 10 Pro specifics: SCO managed by audio or by Bluetooth; default recogniser component;
  whether Pixel SystemUI ever calls `onLaunchVoiceAssistFromKeyguard`.
- Exact lifecycle of a `Theme.NoDisplay` activity started by uid 1002 while the device is asleep
  behind a secure keyguard (expected: `onCreate` runs; to be confirmed with the dry run in 6.4).
- Whether `RECORD_AUDIO` capture in a microphone FGS of the role holder is unsilenced after hours
  of Doze.
- Phonak EON: command actually sent, Tap Control availability per performance level, behaviour
  with one aid and with two active Bluetooth connections.
- Android 16 was not compared line by line; all code citations are Android 17.
