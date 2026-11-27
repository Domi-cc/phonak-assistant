# Speech research: STT, TTS, end-of-speech and microphone rules (German, Pixel 10 Pro, locked, screen off)

Research date: 2026-11-27. Nothing here was run on a device. Evidence labels used throughout:

- **[V] verified** = seen in a primary source (Android docs, AOSP `android17-release` source, vendor docs, the repository's own code or model card). Source given.
- **[I] inferred** = reasoned from verified sources; needs the device test named in section 6.
- **[U] unknown** = no primary source found.
- **[3P]** = a third-party field report (someone else's device notes or benchmark). Useful, but not verified by me and not on this device.

Local copies of everything fetched are under
`<research>/` (`pages/` = docs as text, `aosp/` = AOSP files, `thirdparty/` = field reports, `sherpa_*_assets.tsv` = model lists).
AOSP files are from branch `android17-release` of `android.googlesource.com` unless stated; line numbers refer to that branch as fetched on 2026-11-27.

## 0. Facts that frame everything else

| Fact | Status | Source |
|---|---|---|
| Android 17 is API level 37 and is released; AOSP has an `android17-release` branch | [V] | `git ls-remote https://android.googlesource.com/platform/frameworks/base` lists `refs/heads/android17-release`; docs say "Android 17 (API level 37)" (https://developer.android.com/about/versions/17/behavior-changes-17) |
| A Pixel 10 Pro (`blazer`, Tensor G5) runs Android 17 builds (`CP2A.260805.005`, Aug 2026) | [3P] | `thirdparty/ostadix_report.md` (github.com/lostadi/OSTADIX `audits/pixel-ai-20260915/REPORT.md`) |
| Tensor G5 CPU = 1 big + 5 medium + 2 little cores (Google wording); Cortex-X4 / A725 / A520 comes from leaks, not a Google spec sheet | [3P] | androidauthority.com/google-tensor-g5-3583168, notebookcheck Tensor G5 page |
| No public speech benchmark names Tensor G5 or Pixel 10. Real-time factors on this phone are **unknown** | [U] | searched 2026-11-27 |

## 1. Microphone rules on Android 14, 15, 16 and 17

### 1.1 What the documentation says

**RECORD_AUDIO is a while-in-use permission and the microphone foreground service is gated on it.** [V]
https://developer.android.com/develop/background-work/services/fgs/service-types (section Microphone):

> Runtime prerequisites: Request and be granted the RECORD_AUDIO runtime permission.
> Note: The RECORD_AUDIO runtime permission is subject to while-in-use restrictions. For this reason, you cannot create a microphone foreground service while your app is in the background and you cannot launch a microphone foreground service from a BOOT_COMPLETED receiver, with a few exceptions.

Manifest: `android:foregroundServiceType="microphone"`, permission `FOREGROUND_SERVICE_MICROPHONE`, constant `FOREGROUND_SERVICE_TYPE_MICROPHONE`.

**The general background-start exemptions do not help for the microphone.** [V]
https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start (last updated 2026-11-16):

> In addition, if an app wants to launch a foreground service that needs while-in-use permissions (for example, body sensor, camera, microphone, or location permissions), it cannot create the service while the app is in the background, even if the app falls into one of the exemptions from background start restrictions.

> On Android 14 (API level 34) or higher ... if your app is in the background, and it tries to create a foreground service of type camera, location, or microphone, the system sees that your app doesn't currently have the required permissions, and it throws a SecurityException.

> Note: On versions of Android lower than Android 14 ... the system would let you create the service, but the service wouldn't have access to the needed resources ... On Android 14 or higher, your app gets the exception as soon as it tries to create the foreground service.

So "battery optimisation off", "Companion Device Manager" and "SYSTEM_ALERT_WINDOW" (all in the list "Exemptions from background start restrictions") let a background component start *a* foreground service, but not one that may record.

**The documented exemptions from the while-in-use restriction** (same page, section "Exemptions from restrictions on while-in-use permissions"): [V]

> - A system component starts the service.
> - The service starts by interacting with app widgets.
> - The service starts by interacting with a notification.
> - The service starts as a PendingIntent that is sent from a different, visible app.
> - The service starts by an app that is a device policy controller that runs in device owner mode.
> - The service starts by an app which provides the VoiceInteractionService.
> - The service starts by an app that has the START_ACTIVITIES_FROM_BACKGROUND privileged permission.

Logcat marker when a service was started without the capability:
`Foreground service started from background can not have location/camera/microphone access: service SERVICE_NAME` [V] (same page; string also at `ActiveServices.java:2660`).

**Capture without foreground state returns silence, not an error.** [V]
https://developer.android.com/media/platform/sharing-audio-input:

> One more change was added in Android 9: only apps running in the foreground (or a foreground service) could capture the audio input. When an app without a foreground service or foreground UI component started to capture, the app continued running but received silence, even if it was the only app capturing audio at the time.

Same page, concurrency rules that matter here: "Two ordinary apps can never capture audio at the same time"; "The Assistant can receive audio (no matter whether it's in the foreground or background) unless another app using a privacy-sensitive audio source is already capturing"; "A voice call is active if the audio mode returned by AudioManager.getMode() is MODE_IN_CALL or MODE_IN_COMMUNICATION ... The call always receives audio."

**Per-version changes** [V]

| Version | Change relevant here | Source |
|---|---|---|
| 14 (API 34) | Foreground service types mandatory; microphone type checked at creation; `SecurityException` instead of silent degradation; microphone FGS may not be launched from `BOOT_COMPLETED` | fgs/restrictions-bg-start; https://developer.android.com/about/versions/15/behavior-changes-15 ("microphone (this restriction has been in place for microphone since Android 14)") |
| 15 (API 35) | `BOOT_COMPLETED` may no longer launch `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection`, `microphone` FGS. `SYSTEM_ALERT_WINDOW` exemption needs a *visible* overlay. Apps targeting 35 "must be the top app or running a foreground service in order to request audio focus" (else `AUDIOFOCUS_REQUEST_FAILED`). 6-hour timeout applies to `dataSync` and `mediaProcessing` only | behavior-changes-15, sections "Changes to foreground services", "Restrictions on requesting audio focus" |
| 16 (API 36) | No microphone or FGS-type change found in either behaviour-change page. Local network permission is opt-in | https://developer.android.com/about/versions/16/behavior-changes-16 and `/behavior-changes-all` (headings checked) |
| 17 (API 37) | **Background audio hardening** (playback, audio focus, volume) and mandatory `ACCESS_LOCAL_NETWORK` for apps targeting 37 | see 1.4 |

### 1.2 How the VoiceInteractionService exemption actually works (AOSP)

This is the mechanism the whole design can lean on, because the app has to be the default assistant anyway for input path A.

1. The system binds the selected assistant's `VoiceInteractionService` with four flags. [V]
   `services/voiceinteraction/java/com/android/server/voiceinteraction/VoiceInteractionManagerServiceImpl.java:1322-1328`:
   ```java
   mContext.bindServiceAsUser(
           ...
           Context.BIND_AUTO_CREATE
                   | Context.BIND_FOREGROUND_SERVICE
                   | Context.BIND_INCLUDE_CAPABILITIES
                   | Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS,
   ```
2. `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS` puts a background-start token on the hosting process. [V]
   `ActiveServices.java:4412-4413` (`if (c.hasFlag(Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS)) { s.setAllowedBgActivityStartsByBinding(true);`), `ServiceRecord.java:1503-1505` (`if (mIsAllowedBgActivityStartsByBinding) return BackgroundStartPrivileges.ALLOW_BAL;`).
3. The while-in-use decision accepts that token for **any caller in the same UID**. [V]
   `ActiveServices.java:9164` `shouldAllowFgsWhileInUsePermissionLocked(...)`, lines 9224-9238:
   ```java
   // Allow FGS while-in-use if the WindowManager allows background activity start.
   // ... The binding flag BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS is also allowed by the check here.
   final Integer allowedType = mAm.mProcessList.searchEachLruProcessesLOSP(false, pr -> {
       if (pr.uid == callingUid) {
           if (pr.getWindowProcessController().areBackgroundFgsStartsAllowed()) {
               return REASON_ACTIVITY_STARTER;
   ```
   and `BackgroundLaunchProcessController.java:194-210` (`isBackgroundStartAllowedByToken`: when checking for an FGS start, any token with `allowsBackgroundFgsStarts()` returns `BAL_ALLOW_TOKEN`).
4. Permission to start at all follows from the same result. [V] `ActiveServices.java:9503-9507`: `shouldAllowFgsStartForegroundNoBindingCheckLocked(...) { int ret = allowWhileInUse; ...`.
5. `BIND_INCLUDE_CAPABILITIES` copies the system's capabilities onto the assistant process. [V]
   `OomAdjusterImpl.java:1930-1931` (`if (cr.hasFlag(Context.BIND_INCLUDE_CAPABILITIES)) { capability |= client.getCurCapability(); }`) and, for a persistent client with `BIND_FOREGROUND_SERVICE`, `clientProcState = PROCESS_STATE_BOUND_FOREGROUND_SERVICE` (`OomAdjusterImpl.java:2100-2108`). `Context.java:502-514` documents the flag as letting "the other app ... have while-in-use access such as location, camera, microphone from background".
6. A microphone FGS with while-in-use allowed gets the microphone and audio-control capabilities. [V] `OomAdjusterImpl.java:1700-1727` (`if (s.isFgsAllowedWiu_forCapabilities()) { ... PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL ... (fgsType & FOREGROUND_SERVICE_TYPE_MICROPHONE) != 0 ? PROCESS_CAPABILITY_FOREGROUND_MICROPHONE : 0`).
7. The type policy for `microphone` is `allOf [FOREGROUND_SERVICE_MICROPHONE]`, `anyOf [CAPTURE_AUDIO_HOTWORD, CAPTURE_AUDIO_OUTPUT, CAPTURE_MEDIA_OUTPUT, CAPTURE_TUNER_AUDIO_INPUT, CAPTURE_VOICE_COMMUNICATION_OUTPUT, RECORD_AUDIO]`, `foregroundOnlyPermission = true`. [V] `core/java/android/app/ForegroundServiceTypePolicy.java:470-489`.

**Conclusion [I]:** while this app is the selected digital assistant and its `VoiceInteractionService` is bound, a `BroadcastReceiver` woken by a BLE scan `PendingIntent` can call `startForegroundService()` for a service of type `microphone` and that service is allowed to record, with the phone locked and the screen off. The `VoiceInteractionService`, the receiver and the recording service must be in the same application (same UID). The assistant process itself already holds the microphone capability through the binding, which is how assistants record without a foreground service; starting an explicit microphone FGS is still the documented and more debuggable route.

Conditions under which this silently stops being true [I]:
- The user selects another assistant (Gemini) in Settings. Then path B has no microphone at all.
- After a reboot and before the first unlock, or if the system has not (re)bound the service.
- The check is per start: a service that was started without the capability does not gain it later.

Other entries in the same function, for completeness [V] (`ActiveServices.java:9164-9300`): caller UID state at or above TOP; caller has a visible activity; `ROOT/SYSTEM/NFC/SHELL` UID (so `adb shell am start-foreground-service` always succeeds, which makes a shell test a false positive); a 10-second temp allowlist "used when MediaSessionService's bluetooth button or play/resume/stop commands are issued" (lines 9241-9253, `REASON_TEMP_ALLOWED_WHILE_IN_USE`); `START_ACTIVITIES_FROM_BACKGROUND`; a package allowlist that contains only the attention service and system captions service (lines 876-881); device owner.

### 1.3 What each background trigger may do

| Trigger | Start any FGS? | Microphone (while-in-use)? | Basis |
|---|---|---|---|
| A. Assistant gesture reaching our `VoiceInteractionService` | yes | yes | [V] doc exemption "app which provides the VoiceInteractionService"; [V] bind flags above |
| B. BLE scan result `PendingIntent` broadcast, app **is** the selected assistant | yes | yes | [I] steps 1-4 above. Device test T1 |
| B. BLE scan result broadcast, app is **not** the assistant | only with a BFSL exemption (battery optimisation off, CDM permission) | **no**: `SecurityException` at `startForeground(type microphone)` for targetSdk >= 34 | [V] fgs/restrictions-bg-start |
| C. Media button dispatched by the system to our media session / receiver | yes | yes, within a temp-allowlist window (typically 10 s per the AOSP comment) | [V] `ActiveServices.java:9241-9253`; [V] Android 17 doc: "primary exceptions that grant WIU are ... notification clicks, widget interactions, or media key events from an external device" |
| `BOOT_COMPLETED` | not for microphone | no | [V] behavior-changes-15 |
| A long-running FGS that was started while the app was visible (types `connectedDevice\|microphone`) | n/a | yes for as long as that service instance lives | [I] capability is fixed at start (`setFgsRestrictionLocked`); dies with the process and cannot be restarted from boot |

Third-party confirmation that the non-exempt case really yields silence: hermes-relay ADR 43 (`repos/hermes-relay/docs/decisions.md:2617-2650`): an overlay window "does not make its owning process foreground for Android's while-in-use microphone app-op. The first capture could begin during the foreground grace window, while later captures opened successfully but received silenced PCM." [3P]. A keyboard project logged `App op 27 missing, silencing record AttributionSourceState{... packageName: com.google.android.tts ...}` with `curCapability=--------`, and working capture once a binding with capabilities existed (`thirdparty/physiboard_dictation.md`, row D22) [3P].

### 1.4 Android 17: background audio hardening (affects TTS playback, not only the microphone)

https://developer.android.com/about/versions/17/changes/bg-audio [V]:

> All apps running on Android 17 that have these background audio interactions must have a visible activity or must be running a foreground service that is not of type SHORT_SERVICE. This applies whether or not the app targets API level 37.
> If an app targets Android 17 (API level 37), there is an additional restriction. If the app is running in the background, the app must be running a foreground service that has while-in-use (WIU) capabilities.
> If the app tries to call audio APIs while the app is not in a valid lifecycle, the audio playback and volume change APIs fail silently without throwing an exception or providing a failure message. The audio focus API fails with the result code AUDIOFOCUS_REQUEST_FAILED.

> - Background-Started FGS (BFSL): Most do not grant WIU access. The primary exceptions that grant WIU are interactions involving explicit user intent for example, notification clicks, widget interactions, or media key events from an external device.
> - System started FGS: Foreground services are granted WIU access if they are started by system-server delegation ... or by system bindings representing an elevated foreground state to perform dedicated functionality (such as for a VoiceInteractionService).

Affected APIs: `AudioTrack.write()`, `AAudioStream_write`, OpenSL ES ("Playback is silenced. No exceptions"), `AudioManager.requestAudioFocus()`, volume and ringer APIs.

Test hooks documented on that page [V]:
- `adb shell cmd audio set-enable-hardening <enable|disable|throw>` (`throw` turns silent failures into `IllegalStateException` / write errors).
- `adb shell dumpsys audio` or logcat, entries prefixed `AudioHardening` with the package name. `level: full` = an FGS runs but without WIU; `level: partial` = no FGS at all.

AOSP detail [V]: enforcement uses app-ops `OP_TAKE_AUDIO_FOCUS`, `OP_CONTROL_AUDIO_PARTIAL`, `OP_CONTROL_AUDIO` (`services/core/java/com/android/server/audio/HardeningEnforcer.java`), apps with targetSdk below `CINNAMON_BUN` stay on the partial level, and the audio-control capability deliberately propagates over service bindings: `OomAdjuster.java:2286-2294`, "if the client has the capability, we generally want the service it binds to to hold the capability as well (e.g. TTS)."

Consequences [I]:
- A spoken menu started by a BLE press needs a running foreground service on Android 17 no matter what the app targets. The same assistant-role mechanism that grants the microphone also grants the WIU capability that targetSdk 37 would require.
- Because the app is sideloaded, targetSdk is a free choice. Target 36 keeps the app on the partial level (any non-short FGS is enough) and avoids the mandatory `ACCESS_LOCAL_NETWORK` runtime permission; target 37 works too if test T1/T3 pass.
- Failures are silent by design. The debug build should run with `set-enable-hardening throw` during development.

### 1.5 Locked device and screen off

- No lock-state condition exists in the capture policy or in the recognition framework. [V by absence] `frameworks/av/services/audiopolicy/service/AudioPolicyService.cpp` (`updateUidStates_l`, line 882 ff.) has no keyguard or lock reference; neither have `RecognitionService.java` or `SpeechRecognizer.java`. The conditions are: app-op present (`if (!current->hasOp()) allowCapture = false`, line 1124), sensor privacy off (line 952: "if Sensor Privacy is enabled then all recordings should be silenced"), UID not idle, and the concurrency rules.
- The global microphone toggle (sensor privacy) silences everything, including the assistant. [V] same file.
- Whether Google's own recognizer or TTS app adds a lock check of its own is **unknown** [U]. Device tests T4 and T5.
- Edge case in the capture policy for an assistant UID [V, `AudioPolicyService.cpp:1116-1160`]: when an accessibility service is on top or an RTT call is active, an assistant is only granted capture for sources `VOICE_RECOGNITION` or `HOTWORD`. Irrelevant with the screen off, but worth knowing when debugging with TalkBack or Voice Access enabled.
- While this app owns `MODE_IN_COMMUNICATION` and is itself recording, a second recorder in another UID (for example Google's recognizer opening the microphone itself) is not allowed to capture [V, line 1098: `canCaptureCommunication = recordClient->canBypassConcurrentPolicy || !isPhoneStateOwnerActive || recordUid == mPhoneStateOwnerUid`]. This is one reason to capture audio in our own process and hand PCM to whatever recogniser is used (section 2).

### 1.6 Debug commands for this area

| Question | Command or marker | Status |
|---|---|---|
| Did the FGS get while-in-use? | logcat `Foreground service started from background can not have location/camera/microphone access` | [V] |
| Why was a start allowed or denied? | `adb shell dumpsys activity services <package>` (prints the allow-start / allow-WIU reason codes of each `ServiceRecord`) | [I] from `ServiceRecord.dump` |
| Does the process hold the capabilities? | `adb shell dumpsys activity processes <package>`, `curCapability=` flags, letters `L C M N F U` = location, camera, microphone, network, BFSL, user-network (`ActivityManager.printCapabilitiesSummary`, `ActivityManager.java:1097-1103`) | [V] |
| Is the microphone app-op in foreground mode and when was it rejected? | `adb shell appops get <package> RECORD_AUDIO` | [3P] usage shown in physiboard D22/D27 |
| Was a recording silenced? | audioserver logcat `App op 27 missing, silencing record` | [3P] |
| Audio hardening | `adb shell cmd audio set-enable-hardening throw`, `dumpsys audio` `AudioHardening` | [V] |
| Shell-started services always pass | `SHELL_UID` is in the allowed list (`ActiveServices.java:9199-9212`), so trigger tests must come from the real BLE path | [V] |

## 2. Speech-to-text options

### 2.0 Design consequence that applies to every option: capture the audio yourself

Record in our own process (one `AudioRecord`, in the microphone FGS) and hand 16 kHz mono PCM16 to whichever recogniser is configured. Reasons:
- Our process is the one that provably holds the microphone capability (1.2). A recogniser in another UID opening the microphone itself is subject to the proxy app-op check and to the "phone state owner" rule (1.5).
- The input device (hearing-aid microphone over Bluetooth) is selected in one place.
- Every utterance can be written to a WAV file in debug builds and replayed through any engine.
- Android's own recogniser accepts caller-supplied audio since API 33: `RecognizerIntent.EXTRA_AUDIO_SOURCE` = "Optional ParcelFileDescriptor pointing to an already opened audio source for the recognizer to use ... If this extra is not set or the recognizer does not support this feature, the recognizer will open the mic", with `EXTRA_AUDIO_SOURCE_CHANNEL_COUNT` (default 1), `_ENCODING` (default PCM 16 bit), `_SAMPLING_RATE` (default 16000). [V] `core/java/android/speech/RecognizerIntent.java:146-190`.

### 2.1 (a) Android `SpeechRecognizer`

**Which engines back it** 

| Entry point | Engine | Status |
|---|---|---|
| `createSpeechRecognizer(context)` | the service in `Settings.Secure.voice_recognition_service`. On two GMS phones on Android 16/17 this was `com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService` ("Speech Recognition & Synthesis", Play listing updated 2026-11-24) | [3P] `thirdparty/traceworkbench_voice.md`, `thirdparty/physiboard_dictation.md` D4; Play listing [V] says the app "provides speech-to-text functionality to Google and other third party apps". For the Pixel 10 Pro itself: [I] |
| `createOnDeviceSpeechRecognizer(context)` | the component named by the framework string `config_defaultOnDeviceSpeechRecognitionService` (`SpeechRecognizer.java:311-316`); empty in AOSP, set by a device overlay. Pixel overlays set `com.google.android.as/com.google.android.apps.miphone.aiai.app.AiAiSpeechRecognitionService` (Android System Intelligence, SODA engine with language packs) | [V] for the mechanism; [3P] for the value (Pixel 4-6 overlay dumps in github.com/NeutronBlobs/android_vendor_google_devices; Pixel 10 Pro audit `thirdparty/ostadix_report.md`: "`SpeechRecognizer` -> `AiAiSpeechRecognitionService` -> SODA connector -> installed language pack") |
| `createSpeechRecognizer(context, ComponentName)` | any installed `RecognitionService`, including open-source ones (see 2.2, "as a separate app") | [V] API |

Check on the device: `adb shell settings get secure voice_recognition_service` and `adb shell cmd overlay lookup android android:string/config_defaultOnDeviceSpeechRecognitionService`.

**API rules** [V] `SpeechRecognizer.java:41-52`: "This class's methods must be invoked only from the main application thread"; "the caller MUST invoke destroy()"; "The implementation of this API is likely to stream audio to remote servers ... not intended to be used for continuous recognition". `createOnDeviceSpeechRecognizer` throws `UnsupportedOperationException` when no on-device service is configured. `checkRecognitionSupport()` and `triggerModelDownload()` (API 33) report and fetch on-device language packs. `EXTRA_PREFER_OFFLINE`, `EXTRA_SEGMENTED_SESSION`, `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` and the others carry the note "Depending on the recognizer implementation, these values may have no effect."

**Permission behaviour from a service** [V] `RecognitionService.java:129-170, 854-869`: the service runs `PermissionChecker.checkPermissionAndStartDataDelivery(RECORD_AUDIO, caller attribution source)` and answers `ERROR_INSUFFICIENT_PERMISSIONS` (9) when the *calling app* does not currently have the microphone app-op in an allowed state. The preflight check is skipped when `EXTRA_AUDIO_SOURCE` is present, the data-delivery check is not. So from a background component without the capability the result is error 9 or silence; from our microphone FGS it passes [I].

**German** : ML Kit's documentation describes its Basic mode as "the traditional on-device speech recognition model, similar to the SpeechRecognizer API" and lists `de-DE (beta)` for it, with the note "Languages in Beta may have a slightly higher word error rate" [V] https://developers.google.com/ml-kit/genai/speech-recognition/android. That German runs fully offline through `createOnDeviceSpeechRecognizer` on this Pixel 10 Pro with the pack installed is [I]; word error rate for German is [U].

**Latency** [3P, English, Galaxy S23 on Android 17, audio fed through `EXTRA_AUDIO_SOURCE`]: result 0.03 to 0.18 s after the clip ends for short commands; `createOnDeviceSpeechRecognizer` failed 13 of 40 clips with `ERROR_CLIENT`/no match when sessions followed each other quickly, while the default recogniser with `EXTRA_PREFER_OFFLINE` failed 1 of 40 (`thirdparty/traceworkbench_voice.md`).

**Known failure patterns** [3P unless marked]:
- Google's recogniser plays a sound each time it opens or closes the microphone itself; feeding audio through `EXTRA_AUDIO_SOURCE` avoids that (`repos/alfatherapp/app/src/main/java/app/hermesandroid/voice/Wake.kt:120-122`; physiboard D7).
- It closes about 2 s after sound stops and may deliver the whole utterance as the last partial followed by an empty final (physiboard D2, D3).
- A recogniser object kept between sessions goes stale ("Connection to speech recognition service lost"); create one per session (physiboard D20).
- Extras with the wrong Java type are ignored with only a logcat line (physiboard D15, D23).
- It requests transient-exclusive audio focus for every session (physiboard D14), which interacts with our own playback.
- Package visibility can hide `com.google.android.as` from the caller (physiboard D18); declare `<queries>` for `android.speech.RecognitionService`.
- Behaviour while the device is locked: no rule in the framework (1.5) [V]; Google-side behaviour [U].

**Licence and debuggability**: proprietary Google apps that update through Play without notice; the only diagnostics are error codes and Google's logcat tags. Lowest debuggability of all options.

**ML Kit GenAI Speech Recognition** (new, alpha, `com.google.mlkit:genai-speech-recognition:1.0.0-alpha2`) [V] https://developers.google.com/ml-kit/genai/speech-recognition/android and https://developers.google.com/ml-kit/genai:
- Advanced mode (Gemini Nano): `de-DE` supported, "Pixel 10 and Pixel 11 series". Basic mode: `de-DE (beta)`, API 31+. Custom audio through `AudioSource.fromPfd()` (raw PCM16, mono, 16 kHz).
- **Not usable for this project**: "GenAI API inference is permitted only when the app is the top foreground application. Using the API when the app is not in the foreground, including using a foreground service, will result in an ErrorCode.BACKGROUND_USE_BLOCKED response." Also: "This API is not supported on devices with an unlocked bootloader." Whether the background block also applies to Basic mode is [U].

### 2.2 (b) Engines bundled in the app

**sherpa-onnx** (k2-fsa) is the practical carrier: Apache-2.0, v2.0.3 released 2026-11-22, Kotlin API, Android examples for streaming ASR, VAD + ASR, TTS and a system TTS engine, AAR `sherpa-onnx-2.0.3.aar` 49 MB (all ABIs) or via JitPack `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:<tag>`. [V] `repos/sherpa-onnx` (commit 7a924305, `CMakeLists.txt:38`, `android/README.md`, `sherpa-onnx/kotlin-api/`), GitHub releases API.

German-capable ASR models published in the sherpa-onnx `asr-models` release (sizes are the compressed archives; list in `sherpa_asr_assets.tsv`) [V]:

| Model archive | Size | Type | Model licence | German accuracy from the model card |
|---|---|---|---|---|
| `sherpa-onnx-streaming-zipformer-de-kroko-2025-08-06` | 54.9 MB | streaming transducer | Kroko community models: CC-BY-SA [V hf.co/Banafo/Kroko-ASR] | not published [U] |
| `sherpa-onnx-nemo-stt_de_fastconformer_hybrid_large_pc-int8` (CTC) / `...-transducer-...-int8` | 99.7 / 103.1 MB | offline, German only, outputs punctuation and capitals, ~115 M parameters | CC-BY-4.0 [V hf.co/nvidia/stt_de_fastconformer_hybrid_large_pc] | card has per-dataset tables (MCV, MLS, VoxPopuli); not extracted |
| `sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8` | 146.6 MB | offline, 4 languages | CC-BY-4.0 | WER 4.81 % MLS-de, 5.94 % MCV-16.1-de [V model card] |
| `sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8` | 464.6 MB | offline, 25 European languages, 600 M parameters | CC-BY-4.0 | WER 5.04 % FLEURS-de, 4.84 % CoVoST-de [V model card] |
| `sherpa-onnx-whisper-{tiny,base,small,medium,turbo,large-v3}` | 111 / 198 / 610 / 1842 / 538 / 1019 MB | offline, multilingual | MIT | Whisper, not German-specific |
| `sherpa-onnx-cohere-transcribe-14-lang-int8-2026-04-01` | 1621 MB | offline, 14 languages incl. German | Apache-2.0 [V hf.co/CohereLabs/cohere-transcribe-03-2026] | not extracted |
| `vosk-model-small-de-0.15-onnx(-int8)` | 42.1 / 33.4 MB | Kaldi model converted for sherpa-onnx (uploaded 2026-11-27) | Apache-2.0 | 13.75 % Tuda-de, 30.67 % podcast [V alphacephei.com/vosk/models] |

Kotlin presets for the Kroko German model, Canary and Parakeet v3 exist in `sherpa-onnx/kotlin-api/OnlineRecognizer.kt:498`, `OfflineRecognizer.kt:678` and `:768` [V].
Not German: Moonshine exports in sherpa-onnx (en, es, zh, ja, ko, vi, uk, ar), SenseVoice (zh/en/ja/ko/yue), the Nemotron streaming exports referenced in the Kotlin API (`...-en-0.6b...`) [V asset names].

**whisper.cpp** (ggml-org): MIT, v1.9.6 (2026-11-23), Android example, built-in Silero VAD support. Model files: tiny 75 MiB, base 142 MiB, small 466 MiB, medium 1.5 GiB, large-v3-turbo 1.5 GiB (q5_0: 547 MiB); memory about 273 MB / 388 MB / 852 MB / 2.1 GB for tiny/base/small/medium [V README and `models/README.md`]. No streaming; fixed 30-second window.

**Vosk** (alphacep/vosk-api): Apache-2.0, last release v0.3.50 in 2024-04. German: `vosk-model-small-de-0.15` 45 MB (WER 13.75 % Tuda-de / 30.67 % podcast), `vosk-model-de-0.21` 1.9 GB (9.83 % / 24.00 %) [V]. Streaming, grammar-restricted recognition possible, clearly lower accuracy than the neural models above.

**Real-time factor on Tensor G5: unknown** [U]. Neighbouring measurements, all third party:

| Device | Engine / model | Result | Source |
|---|---|---|---|
| Pixel 7 (Tensor G2), 4 threads, real-time feed | sherpa-onnx streaming zipformer (en) | RTF 0.15-0.21, peak RSS 240-296 MB | `repos/hermes-vox/docs/STT-AB.md` |
| same | sherpa-onnx Nemotron streaming 0.6b (en), 160 ms chunks | RTF 0.68-0.76, RSS about 900 MB | same |
| Galaxy S10 (Exynos 9820, 2019), 30 s clip | sherpa-onnx Parakeet TDT 0.6b v3 | RTF 0.09 | voiceping offline STT benchmark, updated 2026-09-21 |
| same | sherpa-onnx Whisper base / small / turbo | RTF 0.13 / 0.41 / 0.60 | same |
| same | whisper.cpp Whisper tiny | RTF 3.52 | same |
| Galaxy S23 (SM8550), `adb shell`, 4 threads, process start included | whisper.cpp base.en / small.en | 0.54x / 1.7x real time | `thirdparty/traceworkbench_voice.md` |

Reading [I]: on a 2025 flagship CPU the offline NVIDIA models and the small streaming zipformers should run several times faster than real time on CPU; whisper.cpp without acceleration is the slow path; the 0.6 B streaming models are borderline. Measure before choosing (test T6). A sideloaded app has no access to the Tensor TPU through these runtimes [I].

**Behaviour when locked**: these engines are plain native code in our own process; nothing platform-specific applies beyond having the microphone (section 1) and CPU time, which a foreground service provides [I]. Debuggability is the best of all options: own logs, fixed model version, reproducible from WAV files.

**As a separate app instead of bundled**: open-source `RecognitionService` implementations can be installed next to the app and reached through the standard `SpeechRecognizer` API, for example "Offline Voice Input" (github.com/notune/android_transcribe_app, MIT, Parakeet TDT on device, lists German, "System speech service (RecognitionService) ... Recognition runs invisibly in the background with automatic endpointing") [V README]. `repos/third-ear/app/src/main/kotlin/app/thirdear/pipeline/Transcriber.kt` shows the provider-selection code (GPL, read only) [V].

### 2.3 (c) Server-side STT next to the Hermes host

| Server | Licence | Notes | Source |
|---|---|---|---|
| faster-whisper (SYSTRAN) | MIT, v1.2.1 | CTranslate2 Whisper; German with `large-v3`, `large-v3-turbo` (MIT) or the German fine-tune `primeline/whisper-large-v3-turbo-german` (Apache-2.0) | [V] GitHub and Hugging Face APIs |
| speaches | MIT, v0.9.0-rc.3 | OpenAI-compatible `/v1/audio/transcriptions`, SSE streaming, realtime API, TTS through Piper and Kokoro | [V] README |
| wyoming-faster-whisper (OHF-Voice) | MIT | backends faster-whisper, transformers, `sherpa` (Parakeet TDT v3, 25 languages) and `sherpa --sherpa-streaming` ("de, en, es, fr (the published Kroko models)") | [V] README |
| sherpa-onnx websocket server | Apache-2.0 | same models as on the phone; `android/SherpaOnnxWebSocket` is a client example | [V] `repos/sherpa-onnx/android/README.md` |
| Voxtral Mini 4B Realtime (Mistral) | Apache-2.0 weights | streaming, 13 languages incl. German; needs a GPU | [V] hf.co/mistralai/Voxtral-Mini-4B-Realtime-2602 card metadata |
| NVIDIA Parakeet TDT 0.6b v3 / Canary 1b v2 | CC-BY-4.0 | 25 languages incl. German | [V] HF card metadata |

Latency is hardware-dependent and the Hermes host's CPU/GPU is not known to me [U]. Transport cost is small: 16 kHz PCM16 is 32 kB/s, a 5-second question is 160 kB uncompressed.
Locked/screen-off behaviour is a networking question: the request runs from the foreground service; apps targeting API 37 need `ACCESS_LOCAL_NETWORK` to reach LAN addresses [V behavior-changes-17]. Debuggability is very good: the server keeps the audio and logs, and a failed utterance can be replayed with `curl`.

### 2.4 (d) What Hermes Agent itself offers

Checked in `repos/hermes-agent` at commit `28ef6e4e` (2026-11-27). Licence MIT [V `pyproject.toml`].

- **STT providers** [V `website/docs/user-guide/features/tts.md:495-560`]: `local` (faster-whisper, default model `base`; `tiny, base, small, medium, large-v3, turbo`), `groq`, `openai`, `mistral` (Voxtral), `xai`, `elevenlabs`, `deepinfra`, plus command providers (any CLI, e.g. whisper.cpp or Parakeet) and Python plugin providers. Language hint: `stt.language` (example value `"en"`; set `de`). A filter removes known Whisper hallucination phrases [V `voice-mode.md` "Hallucination Filter"].
- **Live STT** only with `openai`, `xai`, `elevenlabs` or a streaming plugin; "Local whisper, Groq, Mistral and DeepInfra keep using the file path" [V `voice-mode.md:177-196`].
- **TTS providers** [V `tts.md:19-36`]: Edge TTS (default, cloud, free), ElevenLabs, OpenAI, MiniMax, Mistral Voxtral TTS, Gemini TTS, xAI, DeepInfra, and local NeuTTS, KittenTTS, **Piper** ("44 languages", voices downloaded on first use; German voices exist). Streaming TTS is sentence by sentence.
- **Where a client can reach this**:
  - The OpenAI-compatible **API server** has no audio: `_STATIC_FEATURE_FLAGS = {... "audio_api": False, "realtime_voice": False, ...}` [V `gateway/platforms/api_server.py:48-57`]; its documented limitations list no audio endpoints [V `api-server.md`].
  - The **dashboard web server** (the backend Hermes Desktop and the web app use) has audio routes [V `hermes_cli/web_routers/audio.py`]:
    - `POST /api/audio/transcribe` body `{"data_url": "data:audio/...;base64,...", "mime_type": ...}`, limit 25 MB, returns `{"ok": true, "transcript": "...", "provider": "..."}`; accepted types include wav, ogg, webm, m4a, mp3, flac (lines 44-52, 89-150).
    - `POST /api/audio/speak` body `{"text": "..."}`, returns a base64 data URL of the synthesised file (lines 298-353).
    - `WS /api/audio/transcribe-stream`: client sends `{"sample_rate": N}` then binary s16le mono PCM, `{"eos": true}`; server sends `partial` / `final` / `error` (lines 527-540).
    - `WS /api/audio/speak-stream`: client sends `{"text": ...}` frames and `{"done": true}`, `{"stop": true}` for barge-in; server sends `{"type":"start","sample_rate":N,"channels":1}`, binary PCM, `{"type":"end"}` (lines 610-632).
    - `GET /api/audio/voice-config` hands the resolved provider settings and credential to a desktop client so it can call cloud providers directly.
    - The WebSockets need a "socket ticket" minted by `/api/webapp/socket-ticket` (comment at lines 539 and 633). The exact auth flow for a non-desktop client was not traced here [U]; these routes are not part of the surface advertised by `/v1/capabilities`, so they can change between Hermes versions [I].
- The CLI voice mode's own end-of-speech rule is plain energy: RMS threshold 200 for 0.3 s to confirm speech, then 3.0 s of silence, 15 s no-speech timeout [V `voice-mode.md:160-169`]. Too slow for this project, but it shows Hermes does not provide a client-side VAD.
- A third-party project already wraps Hermes' built-in STT/TTS behind a small HTTP gateway for an Android push-to-talk client: `repos/Hermes-Walkie-Talkie` (`STT_PROVIDER=hermes`, `TTS_PROVIDER=hermes`; no licence file, so reference only) [V README].

### 2.5 STT comparison

| | Latency after end of speech | German quality | Offline | Locked / screen off | Licence | Debuggability |
|---|---|---|---|---|---|---|
| Android default recogniser (Google) with `EXTRA_PREFER_OFFLINE` | about 0.03-0.2 s [3P, English] | [U]; pack flagged beta in ML Kit's list | yes if pack installed [I] | allowed by framework from our FGS [I]; Google-side [U] | proprietary | poor |
| Android on-device recogniser (ASI) | similar [3P] | same model family [I] | yes | same; flaky under rapid reuse [3P] | proprietary | poor |
| ML Kit GenAI, Advanced | n/a | de-DE supported | yes | **blocked in background, FGS included** [V] | proprietary | n/a |
| sherpa-onnx + Kroko streaming de (55 MB) | streaming; endpoint rule decides | [U], small model | yes | yes [I] | Apache-2.0 code, CC-BY-SA model | very good |
| sherpa-onnx + Canary 180m flash (147 MB) or German FastConformer (100 MB) | utterance length x RTF; RTF [U] on G5 | WER about 5-6 % on read German [V cards] | yes | yes [I] | Apache-2.0 code, CC-BY-4.0 model | very good |
| sherpa-onnx + Parakeet TDT 0.6b v3 (465 MB) | RTF 0.09 on a 2019 Exynos [3P] | WER about 5 % FLEURS/CoVoST German [V card] | yes | yes [I] | Apache-2.0 code, CC-BY-4.0 model | very good |
| whisper.cpp small / turbo-q5 | slow on CPU [3P] | good | yes | yes [I] | MIT | good |
| Vosk small-de | streaming, fast | WER 14-31 % [V] | yes | yes [I] | Apache-2.0 | good |
| Server: Hermes dashboard `/api/audio/transcribe` or own faster-whisper/Parakeet server | upload + server time; [U] for this host | best available (large models) | no (but Hermes needs the network anyway) | yes from FGS [I] | MIT (Hermes, faster-whisper) | very good |

## 3. Text-to-speech options

### 3.1 Android `TextToSpeech` with the Google engine

- Engine: "Speech Recognition & Synthesis" `com.google.android.tts` ("Speech Services powers applications to read the text on your screen aloud"; updated 2026-11-24) [V Play listing].
- Manifest: apps targeting Android 11+ "should declare `TextToSpeech.Engine#INTENT_ACTION_TTS_SERVICE` in the `queries` elements" (`android.intent.action.TTS_SERVICE`) [V `TextToSpeech.java:71-80`].
- The client binds the engine with plain `BIND_AUTO_CREATE` [V `TextToSpeech.java:2426`]; usable from a `Service`, no UI needed [I].
- **`speak()` plays inside the engine's process**: the `AudioTrack` is created in `BlockingAudioTrack.java:226` from the `PlaybackSynthesisCallback` (`TextToSpeechService.java:1042`) [V]. The client controls routing only through `setAudioAttributes(AudioAttributes)` (`TextToSpeech.java:1522`) and the legacy `KEY_PARAM_STREAM`, `KEY_PARAM_VOLUME`, `KEY_PARAM_SESSION_ID`; there is no `setPreferredDevice` anywhere in `TextToSpeech.java` or `BlockingAudioTrack.java` [V by grep].
- **`synthesizeToFile()` gives the audio to the app**: to a `File` or, since API 30, to a `ParcelFileDescriptor` (`TextToSpeech.java:1909`). `UtteranceProgressListener.onBeginSynthesis(utteranceId, sampleRateInHz, audioFormat, channelCount)` announces the format and `onAudioAvailable(utteranceId, byte[] audio)` delivers chunks while synthesis runs: "The audio parameter is a copy of what will be synthesized to the speakers (... `speak`) or written to the file system (for `synthesizeToFile`)" [V `UtteranceProgressListener.java:78-121`]. So the app can play the chunks on its own `AudioTrack`, pick the output device, save them, and cache them.
- Voices: `getVoices()`, `Voice.getLocale()`, `Voice.getQuality()`, `Voice.getLatency()`, `Voice.isNetworkConnectionRequired()` (`Voice.java:126-157`), feature `Engine.KEY_FEATURE_NOT_INSTALLED = "notInstalled"` (`TextToSpeech.java:678`) [V]. Which German voices the Google engine offers offline on this phone, and their names, are [U] from primary docs; enumerate on the device (test T4).
- Android 17 hardening: playback by `speak()` is done by the engine UID, not ours. AOSP says the audio capability is meant to follow the binding "(e.g. TTS)" (`OomAdjuster.java:2286-2294`) [V], but whether Google's engine stays audible in every state when our app only has a background-started FGS is [I]. Playing the PCM ourselves removes the question.
- Latency and German quality of the Google voices: [U] (no primary numbers); in practice the engine is used by TalkBack with the screen off.
- Licence: proprietary; behaviour can change with Play updates. Debuggability: medium when we own the PCM (we can save exactly what was played), poor with `speak()`.

**ML Kit GenAI Text to Speech** (alpha, `com.google.mlkit:genai-text-to-speech:1.0.0-alpha1`) [V https://developers.google.com/ml-kit/genai/text-to-speech/android]: `de-DE` supported, 8 voices (`star02` ... `star16`), devices "Pixel 8, Pixel 9, Pixel 10, Pixel 11", needs the current `com.google.android.tts`, returns `AudioAvailable` chunks. The general ML Kit GenAI rule "permitted only when the app is the top foreground application ... including using a foreground service" is stated for GenAI inference through AICore; whether it applies to this TTS API is [U]. Do not build on it before testing.

### 3.2 Bundled engines

| Engine | German | Size | Licence | Notes |
|---|---|---|---|---|
| **Piper** voices through sherpa-onnx (`vits-piper-de_DE-*`) | 51 German archives: `thorsten` low/medium/high, `thorsten_emotional`, `kerstin`, `karlsson`, `eva_k`, `ramona`, `pavoque`, `miro`, `dii`, ... | 20 MB (int8 medium) to 110 MB (high) | sherpa-onnx Apache-2.0. Voice data: `thorsten`, `thorsten_emotional`, `kerstin` CC0; `mls` CC-BY-4.0; `pavoque` CC-BY-NC-SA; `karlsson`/`eva_k`/`ramona` "see URL" [V `rhasspy/piper-voices` MODEL_CARD files] | **Needs a phonemiser**: sherpa-onnx 2.0.0 removed espeak-ng and piper-phonemize ("Remove espeak-ng and piper-phonemize from sherpa-onnx (#4055)", `CHANGELOG.md:110`); its Android TTS examples add `com.github.csukuangfj.piper-phonemize:piper-phonemize:v1.6.1` (`android/SherpaOnnxTts/app/build.gradle:41-42`), and "There is no German lexicon, so this model is phonemize-only" (`nodejs-addon-examples/test_tts_non_streaming_vits_piper_de_phonemize.js`). `csukuangfj/piper-phonemize` and `espeak-ng` are **GPL-3.0** [V GitHub API]. Shipping them inside the APK makes the distributed binary a GPL-3 combined work even if this repository's own code stays Apache-2.0 [I] |
| Piper upstream | same voices | | `rhasspy/piper` MIT but archived; active development is `OHF-Voice/piper1-gpl`, **GPL-3.0**, v1.8.0 [V] | |
| **Supertonic 3** through sherpa-onnx (`sherpa-onnx-supertonic-3-tts-int8-2026-05-11`) | yes, one of 31 languages [V model card, `scripts/supertonic/gen_calib_configs.py`] | 122.8 MB | model OpenRAIL-M, sample code MIT; about 99 M parameters | no espeak-ng (character-based front end with a unicode indexer) [V `sherpa-onnx/kotlin-api/Tts.kt:85-102, 304-311`]. German quality and speed on this phone [U]. hermes-vox ships Supertonic (v2, no German) on Android [V its README] |
| Kokoro-82M (`kokoro-multi-lang-v1_0`, 126 MB int8) | **no**: voices are American/British English, Japanese, Mandarin, Spanish, French, Hindi, Italian, Brazilian Portuguese [V hf.co/hexgrad/Kokoro-82M `VOICES.md`] | | Apache-2.0 | not an option for German |
| KittenTTS, Matcha, ZipVoice, Pocket TTS export of 2026-01-26 in sherpa-onnx | English / Chinese only as packaged [V asset names]; upstream `kyutai/pocket-tts` card metadata now lists German [V], not verified further | | | |
| `vits-mms-deu` (103 MB), `vits-coqui-de-css10` (64 MB) | yes | | MMS weights are CC-BY-NC [I, not re-checked] | older, lower quality |

**As a separate engine app instead of bundled**: the sherpa-onnx project ships `SherpaOnnxTtsEngine` ("you can use it to replace the system TTS engine") [V `android/README.md`], and SherpaTTS (github.com/woheller69/ttsEngine, GPL-3.0, V3.4 2026-07) packages Piper voices as a system TTS engine [V GitHub API]. An app that only talks to such an engine through `android.speech.tts.TextToSpeech` across a process boundary does not link GPL code [I]. This makes the Android `TextToSpeech` API itself a swap interface: Google voice today, Thorsten tomorrow, no code change.

Real-time factor of any of these on Tensor G5: [U]. Piper medium voices are known to run faster than real time on Raspberry Pi class CPUs [I].

### 3.3 Server-side TTS

- Through Hermes: `POST /api/audio/speak` (whole file as base64) or `WS /api/audio/speak-stream` (PCM frames, sentence by sentence), using whatever `tts.provider` is configured; with `piper` and a German voice this is local to the Hermes host and free [V 2.4].
- Standalone: speaches (Piper, Kokoro), Piper's own HTTP server, Chatterbox multilingual (MIT, lists `de`), Qwen3-TTS (Apache-2.0, lists `de`), Voxtral TTS (CC-BY-NC-4.0, lists `de`) [V Hugging Face card metadata only; quality and speed not evaluated].
- Cost: every spoken menu item would depend on the network. Unsuitable for menu navigation, acceptable for long agent answers.

### 3.4 TTS comparison

| | First-audio latency | German | Offline | Routing control | Licence | Debuggability |
|---|---|---|---|---|---|---|
| Pre-rendered prompt cache (WAV/PCM files made once with any engine) | lowest: file read | whatever engine made them | yes | full (own `AudioTrack`) | follows the engine | best |
| Android TTS, Google engine, `synthesizeToFile` + own `AudioTrack` | [U], engine start + synthesis | yes [I; voices to enumerate] | yes with installed voice [I] | full | proprietary engine | good |
| Android TTS, `speak()` | [U] | same | same | AudioAttributes only [V] | proprietary | poor |
| sherpa-onnx + Supertonic 3 | [U] | yes [V], quality [U] | yes | full | Apache-2.0 + OpenRAIL-M | very good |
| sherpa-onnx + Piper Thorsten | [U], small model | yes, CC0 voice | yes | full | **GPL-3 phonemiser in the APK** | very good |
| Separate engine app (SherpaTTS, sherpa-onnx TTS engine) via `TextToSpeech` | [U] | yes (Thorsten) | yes | full with `synthesizeToFile` | engine GPL, our app unaffected [I] | good |
| Hermes `/api/audio/speak(-stream)` | network + synthesis | depends on provider | no | full (we play PCM) | MIT | very good |

## 4. End of speech

**Push-to-talk with a second press** needs no model and no tuning, cannot cut the user off in a pause, is immune to background noise picked up by a hearing-aid microphone, and its failure modes are visible in a log (two timestamps). Costs: an extra press; a late or lost "stop" event leaves the microphone open, so a hard maximum duration and a no-speech timeout are required either way. Hold-to-talk is not available on a remote that only emits press-type advertisements [I; the BTHome side is another report].

**Voice activity detection** options:

| Detector | Size | Licence | Android integration | Notes |
|---|---|---|---|---|
| Silero VAD v6.3 (2026-11-23) | ONNX about 0.6-2.3 MB (`silero_vad.onnx` 644 kB, `silero_vad_v5.onnx` 2.3 MB in the sherpa-onnx release) | MIT | sherpa-onnx `Vad` class (`SileroVadModelConfig`: `threshold 0.5`, `minSilenceDuration 0.25`, `minSpeechDuration 0.25`, `windowSize 512`); or android-vad | "One audio chunk (30+ ms) takes less than 1ms to be processed on a single CPU thread"; 8 and 16 kHz; trained on "over 6000 languages" [V README] |
| WebRTC VAD (GMM) | 158 kB | BSD (WebRTC) | `gkonovalov/android-vad` (MIT, v2.0.10, last commit 2025-07-15): `VadWebRTC`, frame sizes 160/320/480 at 16 kHz, modes NORMAL to VERY_AGGRESSIVE | "may demonstrate relatively lower accuracy when it comes to differentiating speech from background noise" [V README] |
| TEN VAD | `ten-vad.onnx` 332 kB | Apache-2.0 **with extra conditions** (no deployment that "competes with Agora's offerings") [V LICENSE] | sherpa-onnx `TenVadModelConfig` (`windowSize 256`) | avoid for licence clarity |
| Smart Turn v3.2 (pipecat) | 8 MB int8 | BSD-2-Clause | ONNX, no ready Android wrapper found | semantic end-of-turn on top of a VAD; 23 languages including German; takes the last 8 s of 16 kHz audio [V README] |
| Engine endpointing | none | | sherpa-onnx streaming recognisers have endpoint rules (hermes-vox uses a 500 ms trailing-silence rule); Google's recogniser ends about 1-2 s after speech [3P] | comes for free with a streaming engine |

Both approaches run "without always listening": the VAD only sees audio after the activation press.
A VAD is useful even with push-to-talk: (1) reject recordings that contain no speech before they reach Whisper-family models, which invent text on silence (Hermes ships a filter of "26 known hallucination phrases" for that reason [V `voice-mode.md`]); (2) trim leading and trailing silence; (3) auto-stop as a safety net.

## 5. Recommendation

### 5.1 First version (optimise for: works locked with the screen off, easy to debug)

1. **Microphone access**: the app is the default assistant; `VoiceInteractionService`, BLE receiver and the interaction foreground service live in one application process/UID. Each interaction starts one FGS (`microphone` plus the type the Bluetooth/remote side needs) from whichever trigger fired, and stops it when the interaction ends. Build with targetSdk 36 first; run debug sessions with `cmd audio set-enable-hardening throw`.
2. **Capture**: one `AudioCapture` component owning the `AudioRecord`, emitting 16 kHz mono PCM16 frames, writing a WAV per utterance in debug builds.
3. **End of speech**: push-to-talk with a second press, hard cap (for example 30 s), no-speech timeout, and a Silero VAD gate (via sherpa-onnx `Vad` or android-vad) used only to reject empty recordings and trim silence.
4. **STT**: server-side, one HTTP request per utterance, behind an `SttEngine` interface. Either Hermes' `POST /api/audio/transcribe` (configure `stt.provider: local`, model `turbo` or `large-v3`, `stt.language: de`) or a small OpenAI-compatible server (speaches / faster-whisper) beside Hermes. Rationale: the assistant needs the network for Hermes anyway, large models give the best German, the phone needs no model management, and every failure can be replayed with `curl` from the saved WAV. This depends on the host's speed [U] and on the network path with the screen off (test T7).
5. **TTS**: two tiers behind a `TtsEngine` interface plus a `PromptCache`.
   - Menu and status prompts ("Wetter", "Termine", "To-dos", "Abgebrochen", earcons): synthesised once and cached as PCM/WAV, played from our own `AudioTrack`. Instant, offline, identical every time.
   - Dynamic text: Android `TextToSpeech` with an installed (non-network) German voice, using `synthesizeToFile`/`onAudioAvailable` and our own `AudioTrack`. No bundled model, no GPL question, engine replaceable in system settings.
6. **Instrumentation**: log per interaction the trigger, FGS start result, capability flags, capture RMS, bytes sent, STT time, TTS time-to-first-audio, playback underruns; keep the last N utterance WAVs.

### 5.2 Later

- **On-device STT** with sherpa-onnx as a second `SttEngine`: start with `sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8` (accuracy) or `sherpa-onnx-nemo-stt_de_fastconformer_hybrid_large_pc-int8` / Canary 180m flash (size), and the Kroko German streaming model if partial results matter. Choose by measurement with German recordings made through the hearing-aid microphone (test T6). `repos/HermiVoiceApp` (Apache-2.0) is a working German sherpa-onnx + Hermes push-to-talk app to copy from.
- **Google recogniser adapter** (`SpeechRecognizer` + `EXTRA_AUDIO_SOURCE` + `EXTRA_PREFER_OFFLINE`) as an optional third engine, for comparison only.
- **VAD auto-stop** as the default end-of-speech, with the button as override; then Smart Turn for fewer premature cut-offs.
- **Streaming**: Hermes `speak-stream` / `transcribe-stream` or a streaming on-device recogniser, once the batch path is solid.
- **On-device neural TTS**: Supertonic 3 through sherpa-onnx (no GPL dependency) if the Google voice is not intelligible enough through the hearing aid; Piper Thorsten through a separately installed engine app.
- targetSdk 37 after tests T1 and T3 pass with hardening enabled.

### 5.3 What sits behind an interface (swappable without touching the state machine)

| Interface | Implementations |
|---|---|
| `AudioCapture` | phone microphone, Bluetooth hearing-aid microphone, WAV file (tests) |
| `EndpointDetector` | button, timeout, Silero VAD, VAD + Smart Turn |
| `SttEngine` (`transcribe(pcm) -> text`, optional partials) | Hermes dashboard, OpenAI-compatible server, sherpa-onnx offline, sherpa-onnx streaming, Android `SpeechRecognizer` |
| `TtsEngine` (`synthesize(text) -> PCM stream`) | Android `TextToSpeech` (any installed engine), sherpa-onnx (Supertonic/Piper), Hermes speak / speak-stream |
| `PromptCache` | files generated on first run, or shipped assets |
| `AudioSink` | own `AudioTrack` with chosen attributes and device |

Not swappable: the assistant-role dependency for microphone access from the BLE path (1.2), and the foreground-service requirement for anything audible on Android 17 (1.4).

## 6. Load-bearing claims and how to test them on the Pixel

| ID | Claim | Status | Device test |
|---|---|---|---|
| T1 | With the app selected as digital assistant, a BLE-broadcast-started FGS of type `microphone` is allowed and records real audio while locked, screen off, after hours idle | inferred from AOSP + doc | Select app as assistant. Lock, screen off, wait 2 h unplugged. Trigger a real BLE press. Expect no `SecurityException`/`ForegroundServiceStartNotAllowedException`, no logcat line "can not have location/camera/microphone access", capture RMS above noise floor, no audioserver "silencing record". Repeat with another assistant selected: expect the exception. Never test with `adb shell am start-foreground-service` (shell UID is exempt) |
| T2 | The exemption is back after reboot once the phone was unlocked once; absent before first unlock | inferred | Reboot, trigger before unlock (expect failure, log it), unlock once, lock, trigger again |
| T3 | On Android 17 our FGS can play audio and take audio focus from the BLE path, for targetSdk 36 and 37 | doc verified, behaviour inferred | `adb shell cmd audio set-enable-hardening throw`; play a prompt from the BLE-triggered service; check `dumpsys audio` for `AudioHardening` entries with the package name |
| T4 | Android `TextToSpeech` works from the service while locked, offline, with a German embedded voice, and delivers PCM through `onAudioAvailable` | API verified, behaviour inferred | Airplane mode, locked: list voices (`locale=de-DE`, `!isNetworkConnectionRequired`, no `notInstalled` feature), call `synthesizeToFile`, record sample rate, time to first chunk, total time |
| T5 | Google `SpeechRecognizer` accepts `EXTRA_AUDIO_SOURCE` from our FGS while locked and transcribes German offline | 3P for English/unlocked | Feed 20 German WAVs through default + `EXTRA_PREFER_OFFLINE` and through `createOnDeviceSpeechRecognizer`; log error codes, `checkRecognitionSupport` output, latency |
| T6 | sherpa-onnx German models run well below real time on Tensor G5 | unknown | Bench APK (see hermes-vox `android/bench` idea): Parakeet v3 int8, German FastConformer int8, Canary flash, Kroko streaming; 1/2/4 threads; RTF, load time, peak RSS; screen on vs off |
| T7 | A foreground-service HTTP round trip to the Hermes host works with the screen off after long idle, and STT latency is acceptable | unknown | Timed `POST` of a 5 s WAV every 30 min overnight; log DNS/connect/transfer/server time |
| T8 | Hermes dashboard audio routes are usable by a non-desktop client | routes verified, auth unknown | `curl` `POST /api/audio/transcribe` and `/api/audio/speak` with the app's credentials against the installed Hermes version |
| T9 | The hearing-aid microphone delivers audio that the chosen STT handles (sample rate over Bluetooth may be 8 or 16 kHz) | unknown, other report | Record through the hearing aid, check actual sample rate, run through each STT engine, compare with phone microphone |
| T10 | ML Kit GenAI speech APIs are blocked without a top activity | doc verified | optional: call from the FGS, expect `BACKGROUND_USE_BLOCKED` |

## 7. Corrections and gaps in the inherited planning notes

1. The notes (section 6, "Dokumentierte Bausteine") present "ein korrekt zulässiger Foreground Service" as sufficient for the BLE path and list microphone use as a separate open test. The documentation is explicit that a background-started foreground service cannot be of type `microphone` "even if the app falls into one of the exemptions from background start restrictions". Path B gets the microphone only because the app also holds the assistant role (or through another listed exemption). The three input paths are therefore not independent for speech input.
2. The notes do not mention Android 17's background audio hardening. Spoken output from a BLE-triggered component is silently muted unless a foreground service is running (all apps) with while-in-use capability (apps targeting API 37).
3. The notes say (section 8) "Sichtbares Android-Mikrofonindikator-Verhalten ist Systemvorgabe". Correct, and additionally the global microphone toggle silences all recording including the assistant (AOSP `AudioPolicyService.cpp:952`).
4. The notes treat "TTS/STT-Implementierung" as a later Hermes integration detail (section 10, item 6). Hermes' OpenAI-compatible API server has no audio (`"audio_api": False`); speech relay exists only on the dashboard web server (`/api/audio/*`). The choice of Hermes endpoint and the speech design are coupled.
5. The notes name "Pixel 11 Pro / Android 17" as the phone; the project context says Pixel 10 Pro. Android 17 (API 37) is the relevant platform either way, per third-party reports of Pixel 10 Pro builds on Android 17.
6. The notes list GPTWake as a reference for "Start eines Sprachsystems bei gesperrtem Android". Its README confirms that (sherpa-onnx keyword spotting, "locked, screen-off tablet"), but it is an always-listening design and does not show the assistant-role microphone path used here.
7. Nothing in the notes about speech engines is contradicted, because the notes contain no concrete STT/TTS claims.

## 8. Reusable projects (speech-related; also candidates for the README's "related projects" list)

| Project | What to take | Licence |
|---|---|---|
| k2-fsa/sherpa-onnx | Kotlin API, `android/SherpaOnnxVadAsr`, `SherpaOnnxSimulateStreamingAsr`, `SherpaOnnxTtsEngine`, model zoo | Apache-2.0 (models have their own licences) |
| virse/HermiVoiceApp (`repos/HermiVoiceApp`) | German push-to-talk Hermes client; `app/src/main/java/de/hermi/voice/stt/SherpaRecognizer.kt`, `ModelManager.kt` (Kroko German model download and use) | Apache-2.0 |
| Codename-11/hermes-relay | assistant-role + voice architecture decisions (`docs/decisions.md` ADR on VoiceInteractionService and ADR 43 on the microphone FGS), Silero VAD + sherpa KWS versions | MIT |
| Giton22/kyllene, JusticeFist/alfatherapp | Hermes dashboard clients with `SpeechRecognizer` on-device dictation; alfatherapp `voice/Wake.kt` shows `EXTRA_AUDIO_SOURCE` + segmented session fed from own `AudioRecord` | MIT |
| chezgoulet/hermes-vox | on-device STT A/B method and bench harness idea (`docs/STT-AB.md`, `android/bench`), Silero VAD + Supertonic + Whisper pipeline | GPL-3.0 (read for ideas, do not copy into Apache-2.0 code) |
| NiallBunting/AssistantVoiceRecorder | minimal "assistant gesture starts a microphone FGS" app (`assistant/RecorderAssistantSession.kt`, `recording/RecordingService.kt`) | GPL-3.0 (ideas only) |
| jooray/third-ear | headless transcription through third-party `RecognitionService` apps, silence watchdog for "Android silencing the app" | GPL-3.0 (ideas only) |
| thivyan-studios/hark-app | Hearing Aid Relay Kit: phone as remote microphone to hearing aids (ASHA/A2DP/SCO), treble boost and limiter DSP, sherpa-onnx live captions | BSD-3-Clause |
| suddenBook/GPTWake | sherpa-onnx keyword spotting from a locked, screen-off device | Apache-2.0 |
| notune/android_transcribe_app | Parakeet-based offline `RecognitionService` app, German supported | MIT |
| DicioTeam/dicio-android | FOSS Android assistant with Vosk STT and skill framework | GPL-3.0 |
| gkonovalov/android-vad | WebRTC / Silero / Yamnet VAD wrappers | MIT |
| snakers4/silero-vad, pipecat-ai/smart-turn | VAD and end-of-turn models | MIT, BSD-2-Clause |
| SYSTRAN/faster-whisper, speaches-ai/speaches, OHF-Voice/wyoming-faster-whisper | server-side STT | MIT |
| TheSethRose/Hermes-Walkie-Talkie | PTT Android client + voice gateway that calls Hermes' built-in STT/TTS | no licence file: reference only |

## 9. Risks and unknowns

- The microphone path for BLE presses depends on the assistant role and on AOSP behaviour read from source; Pixel builds can differ by feature flags (`roForegroundAudioControl`, `hardeningBfgs`, `fgsWiuQsTileClicked` appear in the code).
- Android 17 audio failures are silent. Without `set-enable-hardening throw` and the `AudioHardening` log, a muted prompt looks like a routing bug.
- Google speech components are black boxes that update through Play; the German offline pack state, beeps, focus requests and error codes can change.
- ML Kit GenAI speech APIs are foreground-only and refuse unlocked bootloaders. An owner who unlocks the bootloader for debuggability loses them entirely.
- Bundling Piper for German pulls GPL-3.0 code (espeak-ng) into the APK. Kroko models are CC-BY-SA, NVIDIA models CC-BY-4.0 (attribution), Supertonic OpenRAIL-M (use restrictions).
- No Tensor G5 numbers exist for any bundled engine. Large models (465 MB+) cost RAM and load time after the process was killed.
- Bluetooth hands-free audio may be narrowband; STT accuracy through the hearing-aid microphone is untested.
- Whisper-family models invent text on silence or noise; an accidental button press must not become an agent command. Gate with VAD and require a minimum amount of speech.
- Hermes dashboard audio routes are not in the advertised stable API; pin the Hermes version and keep them behind `SttEngine`/`TtsEngine`.
- A user with high-frequency hearing loss may need a specific voice, rate and equalisation; this argues for owning the PCM path so that DSP can be added (hark-app shows a treble shelf and limiter).

## 10. Source index

Android documentation (fetched 2026-11-27):
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/about/versions/15/behavior-changes-15
- https://developer.android.com/about/versions/16/behavior-changes-16 , https://developer.android.com/about/versions/16/behavior-changes-all
- https://developer.android.com/about/versions/17/behavior-changes-17 , https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/about/versions/17/changes/bg-audio
- https://developer.android.com/media/platform/sharing-audio-input
- https://developers.google.com/ml-kit/genai , https://developers.google.com/ml-kit/genai/speech-recognition/android , https://developers.google.com/ml-kit/genai/text-to-speech/android
- https://play.google.com/store/apps/details?id=com.google.android.tts
- https://developers.googleblog.com/on-device-speech-recognition-and-text-to-speech-in-google-ai-edge-gallery/ (2026-11-10; announces ML Kit speech APIs and LiteRT bundles for Parakeet-TDT 0.6B, PocketTTS, Kokoro, Phonon)

AOSP `android17-release` (base URL `https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/`):
- `services/core/java/com/android/server/am/ActiveServices.java`, `ServiceRecord.java`, `psc/OomAdjuster.java`, `psc/OomAdjusterImpl.java`
- `services/core/java/com/android/server/wm/BackgroundLaunchProcessController.java`, `WindowProcessController.java`
- `services/voiceinteraction/java/com/android/server/voiceinteraction/VoiceInteractionManagerServiceImpl.java`
- `services/core/java/com/android/server/media/MediaSessionService.java`, `services/core/java/com/android/server/audio/HardeningEnforcer.java`
- `core/java/android/app/ForegroundServiceTypePolicy.java`, `core/java/android/app/ActivityManager.java`, `core/java/android/content/Context.java`
- `core/java/android/speech/SpeechRecognizer.java`, `RecognitionService.java`, `RecognizerIntent.java`
- `core/java/android/speech/tts/TextToSpeech.java`, `TextToSpeechService.java`, `BlockingAudioTrack.java`, `UtteranceProgressListener.java`, `Voice.java`
- `https://android.googlesource.com/platform/frameworks/av/+/refs/heads/android17-release/services/audiopolicy/service/AudioPolicyService.cpp`

Repositories read locally (`.../scratchpad/repos/`): `hermes-agent` (28ef6e4e), `sherpa-onnx` (7a924305, v2.0.3), `android-vad` (1b006d0a), `HermiVoiceApp`, `hermes-relay`, `hermes-vox`, `kyllene`, `alfatherapp`, `third-ear`, `AssistantVoiceRecorder`, `GPTWake`, `hark-app`, `Hermes-Walkie-Talkie`, `dicio-android`, `openclaw-assistant`.

GitHub and Hugging Face API lookups (licences, release tags, model metadata) on 2026-11-27: k2-fsa/sherpa-onnx releases `asr-models` and `tts-models`; ggml-org/whisper.cpp v1.9.6; snakers4/silero-vad v6.3; SYSTRAN/faster-whisper v1.2.1; speaches-ai/speaches; OHF-Voice/piper1-gpl v1.8.0; rhasspy/piper (archived); csukuangfj/piper-phonemize; espeak-ng/espeak-ng; TEN-framework/ten-vad; pipecat-ai/smart-turn; nvidia/parakeet-tdt-0.6b-v3; nvidia/canary-180m-flash; nvidia/stt_de_fastconformer_hybrid_large_pc; hexgrad/Kokoro-82M; Supertone/supertonic-3; Banafo/Kroko-ASR; rhasspy/piper-voices; https://alphacephei.com/vosk/models.

Third-party field reports (not verified): github.com/lostadi/OSTADIX `audits/pixel-ai-20260915/REPORT.md`; github.com/mogesjohnson/trace-workbench `docs/VOICE_RESEARCH.md`; github.com/brobata/physiboard `docs/spec/dictation.md`; voiceping offline speech transcription benchmark (updated 2026-09-21); androidauthority.com and notebookcheck.net on Tensor G5.
