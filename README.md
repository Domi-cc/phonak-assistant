# Lara im Ohr

"Lara im Ohr" (German for "Lara in the ear") is a native Android app for operating a personal assistant through a hearing aid. The assistant is Lara, a self-hosted [Hermes Agent](https://github.com/NousResearch/hermes-agent). A double tap on the hearing aid or a press on a small pocket remote starts an interaction, and tones or speech come back into the hearing aid. The phone stays locked, with the display off, in a pocket.

The reason: a hearing aid is a microphone and a loudspeaker that its wearer has on all day and that is already connected to the phone. If it can be the whole interface, there is no screen to look at and nothing to take out of the pocket.

Whether Android allows this to a sideloaded app, one that is installed from a file and not from an app store, was not known. Version 0.1 is therefore a **measurement app**. It contains the foundation of the later app (permissions, input paths, audio path, journal) and measures on one real phone what works. It has no menu and no connection to Hermes yet. Nothing here is guaranteed: not on other devices, and not on the tested one after its next system update.

## Status

As of 2026-11-27, on the one phone and the one hearing aid listed below.

| Part | State |
|---|---|
| A sideloaded build can be selected as the phone's digital assistant, and Android keeps its assistant service bound | Verified on the device |
| A double tap on the hearing aid starts the app while the phone is locked and the display is off; the display stays off | Verified on the device |
| The app opens the wideband voice link, records from the hearing-aid microphone and plays audio into the hearing aid | Verified on the device |
| Reception of the Shelly remote (BTHome v2 advertisements, background scan) | Built, not yet measured |
| Count session: every received button press is counted aloud into the hearing aid | Built, not yet measured |
| Spoken numbers, prepared ahead of time with the phone's speech engine, with tones as fallback | Built, not yet tried on the device |
| Control over adb for repeatable series, sharing the journal | Built, not yet tried on the device |
| Media keys as a third input | Planned |
| Spoken menu | Planned |
| Connection to Hermes | Planned |

"Verified" means that it worked on this device on that day. It says nothing yet about behaviour after hours of standby, after a reboot or over many repetitions. Those measurements are what this app is for.

## Tested hardware

| Role | Device |
|---|---|
| Phone | Google Pixel 10 Pro XL, Android 17, build `CP3A.261005.005` |
| Hearing aid | Phonak Audéo EON R, worn on the right ear only. It is connected over Bluetooth Classic: hands-free for calls and voice, plus music streaming |
| Remote | Shelly BLU RC Button 4, model `SBBT-004CUS` (the model without Zigbee). It sends BTHome v2 advertisements over Bluetooth Low Energy |

Nothing else was tried: no other phone, no other Android version, no other hearing aid, no pair of hearing aids, no other remote.

The tap path uses only standard hands-free behaviour (see below), so other Bluetooth Classic hearing aids and headsets with a voice-assistant gesture may behave the same way. That is a guess until someone measures it.

## How it works

### Three input paths

```text
 A  Hearing aid, double tap           B  Pocket remote, button press        C  Media key
    |                                    |                                     |
    | hands-free profile:                | BTHome v2 advertisement             | (planned)
    | "start voice recognition"          | (Bluetooth LE, no connection)       |
    v                                    v                                     v
 Android Bluetooth stack              BLE scan, filtered on the remote      media-key listener
    |                                 (throttled while the display is off)     |
    | starts an activity with            |                                     |
    | android.intent.action.VOICE_COMMAND|                                     |
    v                                    |                                     |
 VoiceCommandActivity                    |                                     |
 (invisible, finishes at once)           |                                     |
    |                                    |                                     |
    +------------------+-----------------+-------------------------------------+
                       |          (also: a button on the app's screen)
                       v
        SessionService: foreground service of type "microphone",
        runs only for the length of one session
                       |
                       v
        AudioLink: voice link to the hearing aid (16 kHz mono)
        out: tones, recorded or spoken audio      in: hearing-aid microphone

 Present the whole time:
   VoiceInteractionService   kept bound by Android while the app is the digital assistant.
                             This binding is where the background rights come from.
   Journal                   every event, with the state of the phone at that moment.
```

Path A is verified on the device. Path B is built and not yet measured. Path C is planned.

The expected division of labour, which version 0.1 is there to check: wake the assistant with a tap on the hearing aid, navigate with the remote, media keys as an extra.

### What surprised us

Each statement is marked with where it comes from. "Read from Android 17 source" refers to the public Android 17.0 release (tag `android-17.0.0_r1`, June 2026). The phone runs a newer build, and its Bluetooth stack is updated separately from the system. A statement from the source is therefore an expectation until it has been measured.

**1. The tap is not an assistant request.**

*Measured on the device.* A double tap makes the hearing aid ask the phone for voice recognition over the hands-free profile. About 60 ms later Android's Bluetooth stack starts an activity with `android.intent.action.VOICE_COMMAND`. This also works with the phone locked and the display off, and the display stays off. Which app receives the intent is ordinary intent resolution and has nothing to do with the assistant role: with no saved default, the tap opened Android's app chooser, although an assistant was selected. The intent carried the Bluetooth device and the profile as extras. This app targets API 36, and the documentation promises the extras only to apps that target a higher level.

*Read from Android 17 source.* `HeadsetSystemInterface.activateVoiceRecognition()` in the Bluetooth module builds the intent without a package name and without looking at any role, and the assistant role defines no preferred activity for this action. The app therefore declares an ordinary exported activity for `VOICE_COMMAND`, and the owner chooses it once with "Always".

**2. The assistant role is needed anyway.**

*Read from Android 17 source.* Android keeps the `VoiceInteractionService` of the selected assistant bound, with flags that give the app the standing of a foreground service, the microphone capability and the right to start things from the background. This binding, not the role by itself, is what allows a microphone foreground service to start and to record real audio while the phone is locked and dark. The role does not grant the microphone permission, and the battery-optimisation exemption is no substitute: it allows a foreground service to start from the background in general, but not one of the microphone type. A tap brings a short allowance of its own (about 10 s after an activity start). A press on the remote brings none, so every other input path rests on the binding.

*Read from Android 17 source.* To qualify, an app must declare three services: the interaction service, a session service and a recognition service. Without the recognition service Android clears the assistant setting again. The role is checked again on every package change, and a build that no longer qualifies loses it silently to the default assistant.

*Measured on the device.* A sideloaded build can be selected as digital assistant, the system binds its service, and with that in place a tap on the locked, dark phone leads to a recording from the hearing-aid microphone. Not measured yet: a control run without the role.

**3. The app has 5 seconds to answer.**

*Read from Android 17 source.* After starting the activity, the Bluetooth stack waits 5 seconds for an app to call `BluetoothHeadset.startVoiceRecognition(device)`. The permission `BLUETOOTH_CONNECT` is enough for that call. If it does not come, the hearing aid gets an error. The stack holds a wake lock only for those 5 seconds and releases it as soon as the app answers, so the app takes a short wake lock of its own as its first step. The connection to the hands-free profile service that the call needs is set up asynchronously, so the app asks for it when its process starts and keeps it.

*Measured on the device.* A second tap within 5 s of an unanswered one is dropped. A tap during a running session ends the session. With the Google app as reference, the voice link was up 0.4 to 0.7 s after the call, in wideband quality (16 kHz).

**4. Asking for the audio route too early cancels the session.**

*Measured on the device.* The obvious next step after answering, `AudioManager.setCommunicationDevice()` on the hearing aid, is a mistake if it comes too early. On this phone the link is created by the audio server on behalf of the Bluetooth process (logcat: `AS.AudioDeviceBroker: setCommunicationRouteForClient for uid: 1002`). If the app's own request arrives before that link is up, it is treated as a "virtual call", and the stack stops the voice-recognition session it has just accepted (logcat: `HeadsetService: startScoUsingVirtualVoiceCall: voice recognition is still active, just called stopVoiceRecognition`). In some attempts the link was then refused (`HeadsetService: isScoAcceptable: rejected SCO`) and never came up. The order that worked: call `startVoiceRecognition`, wait until `BluetoothHeadset.isAudioConnected(device)` is true, and only then call `setCommunicationDevice`.

*Read from Android 17 source.* The app's own request is still needed, because Android drops a communication route about 6 s after the requesting app is neither playing nor recording. So the app registers for the route once the link is up and keeps its own `AudioTrack` running, with silence between sounds. Speech from `TextToSpeech.speak()` is played by the speech engine's process and does not count as the app's own playback. Spoken output will therefore be synthesised into audio first and played by the app.

**5. Android 17 throttles background BLE scanning.**

*Measured on the device.* `adb shell device_config list bluetooth` shows `scan_allowance_throttling_enabled=true`. In the public Android 17.0 source this flag exists but is off.

*Read from Android 17 source.* With the flag on, only an app that is in the foreground on the screen scans freely, and while the display is off no ordinary app is. Every other app, with or without a foreground service, gets an allowance per hour: 6 minutes in the fastest scan mode or 24 minutes in the balanced one. After that its scan is forced into the screen-off low-power mode, which listens for 512 ms every 10.24 s. Scans without a filter are suspended completely while the display is off. None of these values can be changed over adb.

*Expected, not measured.* According to the vendor's changelog this remote model sends for about one second per gesture, with packets about 20 ms apart. By calculation the low-power mode catches that in roughly 15 % of cases, about one press in seven. The remote is therefore expected to be good for navigating inside a session and poor for waking the phone after a long pause. Home Assistant users report for this model that only about every fifteenth press arrived with active scanning and every press with passive scanning. We have not measured that either.

**6. Android 17 mutes background audio.**

*Read from the Android 17 documentation and source.* An app that plays audio must have a visible activity or a running foreground service. With the display off there is no visible activity, so only the foreground service counts. For apps that target API 37 the service must also hold while-in-use rights. An app that breaks the rule gets no error: `AudioTrack.write()` succeeds, the volume is zero, and a request for audio focus fails.

*Measured on the device.* The enforcement is active on this phone, and `adb shell dumpsys audio` logs violations. According to the source they are in its section "Hardening enforcement".

The app therefore plays only from inside its microphone foreground service. It targets API 36 for now, where the more lenient rule applies; `-Plara.targetSdk=37` builds the strict variant.

### Rules the app keeps to

- It never turns the screen on.
- The voice link is open only during a session and is closed on every exit path. A link left open makes Android reject the next tap.
- It never records without a deliberate trigger. A recording counts only if Android reports the hearing aid as the microphone actually used. There is no fallback to the phone's microphone.
- It holds no permanent wake lock, only short ones for the hand-over from a trigger and for a running session.
- Every failure is written to the journal.

The design document (in German) is [docs/superpowers/specs/2026-11-27-mess-app-design.md](docs/superpowers/specs/2026-11-27-mess-app-design.md).

## Install and set-up

You need a phone with Android 16 or newer (the app's `minSdk` is 36; only Android 17 was tested) and a hearing aid that is paired with the phone for calls and whose tap gesture is set to "voice assistant". According to Phonak's documentation for earlier models there is one tap function per ear, so with a single hearing aid it is either the voice assistant or pause/resume. A computer with adb is optional.

The app's own texts are German. The names in quotation marks below are what you see on its screen.

1. **Get the APK.** Download `lara-im-ohr.apk` from the [Releases](../../releases) page, or build it (see "Building from source"). If the Releases page is still empty, building is the only way. A release also carries `lara-im-ohr.apk.sha256`; check the download with `sha256sum -c lara-im-ohr.apk.sha256`. Install the APK by opening the file on the phone, or with `adb install -r lara-im-ohr.apk`.
2. **Open the app.** The upper part of the screen consists of sections that open and close with a tap on their title. The first one is a checklist ("Einrichtung"). Each line shows a tick or a cross. Where Android offers a direct way to fix a line, there is a "Beheben" (fix) button.
3. **Grant the permissions** ("Berechtigungen"): microphone, nearby devices (Bluetooth) and notifications. Android shows a notification while a session runs, which is why the last one is needed.
4. **Select the app as digital assistant** ("Assistentenrolle"). "Beheben" opens Android's voice-input settings. Choose "Lara im Ohr" as the digital assistant app. The line is fulfilled when it reads "Lara · Dienst gebunden", which means that Android has bound the app's assistant service. "Lara gewählt, Dienst noch nicht gebunden" means that the app is selected but the service is not bound yet. From then on the phone's assist gesture also goes to this app instead of your previous assistant, until you switch back in the same settings.
5. **Make the app the target of the hearing-aid tap** ("Hörgeräte-Sprachtaste"). Unlock the phone and double-tap the hearing aid once. Android shows a chooser. Select "Lara im Ohr" and then "Always". The line then reads "→ Lara („Immer“)". The chooser is the only way to do this. There is no setting and no adb command for it (read from Android 17 source).
6. **Exempt the app from battery optimisation** ("Akku-Ausnahme"). "Beheben" opens the system dialog.
7. **Check the hearing aid** ("Hörgerät (Freisprechen)"). The line lists the devices that are connected for hands-free calls and should name your hearing aid.

### The "Always" choice can disappear

Android silently drops the "Always" choice when another app that handles the same intent is installed, or when an installed app starts to handle it (read from Android 17 source: the saved choice is discarded as soon as the set of candidates grows). Nothing tells you. The next tap on a locked, dark phone then opens the chooser behind the lock screen, where nobody sees it, and the hearing aid gets an error after 5 seconds.

The app checks which activity Android would start for the tap and shows the result in the line "Hörgeräte-Sprachtaste":

- "→ Lara („Immer“)": all is well.
- "kein Standard: entsperrt tippen, Lara › „Immer“ wählen": there is no default any more. Repeat step 5.
- "→" followed by a package name: another app holds the default. Clear it in Android's settings for that app ("Open by default"), then repeat step 5.

Uninstalling the app loses the choice as well. See the signing rule under "Building from source".

### After a reboot

Unlock the phone once. Before the first unlock Android neither binds the assistant service nor finds the tap activity (read from Android 17 source). Working before the first unlock is not a goal of version 0.1.

### Checking over adb

```sh
adb shell cmd role get-role-holders android.app.role.ASSISTANT
adb shell cmd package resolve-activity --brief -a android.intent.action.VOICE_COMMAND
```

The first command must print `io.github.domicc.lara`. The second must name `io.github.domicc.lara/.trigger.VoiceCommandActivity`. If it names `android/com.android.internal.app.ResolverActivity`, there is no default and Android would show the chooser.

According to the source, `adb shell cmd role add-role-holder android.app.role.ASSISTANT io.github.domicc.lara` does the same as step 4. We have not verified that command ourselves.

## Trying it

### The echo test

The echo test checks the audio path. It opens the voice link, records a few seconds from the hearing-aid microphone and plays the recording back, so that you hear with your own ear which microphone was used.

Start it in one of these ways:

- Press "Echo-Test jetzt" in the app (section "Messen").
- Press button 3 of the remote during a count session (see below). The test then runs inside that session.
- Send the adb command `lara.ECHO` (see [docs/adb-control.md](docs/adb-control.md)).

The phone's assist gesture is wired to the same test. That trigger is not on the verified list.

Earlier builds started the echo test on the double tap, and that is the combination that was verified on the device. The tap now opens a count session, which uses the same voice link but has not run on the device yet.

What you hear in the hearing aid:

| Sound | Meaning |
|---|---|
| Rising pair of tones | The session is open: the voice link to the hearing aid is up |
| Single higher tone | Speak now |
| Single lower tone | Recording finished |
| Your own voice | The recording, played back |
| Falling pair of tones | The session closed normally |
| Two low buzzes | Error |

All tones are below 1 kHz, so that they stay audible with high-frequency hearing loss.

If the two low buzzes come directly before the playback, the recording was not accepted: Android did not report the hearing aid as the microphone actually used, or the recording was silent. You still hear what was recorded, and the session then ends with the error sound instead of the falling pair.

A second double tap during a session ends it at once. No closing sound follows, because the hearing aid has already closed the link.

If you hear nothing in the hearing aid, the link did not open. The journal says why.

### The count session

Built, not yet tried on the device. This is the measurement "wake by tap, navigate by remote": while the voice link is open, every press of the remote that arrives is answered with its running number, "eins", "zwei", "drei". You count along and hear at once when a press is lost.

Open it in one of these ways:

- Lock the phone, wait until the display is off, and double-tap the hearing aid.
- Press a button of the remote, if "Shelly weckt auf" is switched on (section "Einstellungen"; on by default). That press is number one.
- Press "Zählsitzung starten" in the app, or send the adb command `lara.COUNT`.

In the session:

| You do | You hear |
|---|---|
| Press button 1, 2 or 4, with any gesture | The next number |
| Press button 3 | The echo test, inside the same session |
| Nothing for 20 seconds | The falling pair of tones: the session closes |
| Double-tap the hearing aid | The session ends |

If the packet numbers of the remote show that something it sent did not arrive, you hear "einer fehlt" (or "zwei fehlen" and so on) before the number. The remote numbers every packet it sends, so this can also be a lost beacon and not a lost press. If presses arrive faster than numbers can be spoken, only the newest number is spoken; the journal still has a `session_count` line for every press.

The numbers are spoken by the phone's own speech engine, but not live: the app has each prompt synthesised into a file once, when the main screen is opened or the assistant service is bound, and plays the files itself. The line "Ansagen" in the checklist shows how many are ready. Where a prompt is missing (first start, no speech engine, no German voice), tones stand in: one to five short ticks for the numbers 1 to 5, a three-note figure for every higher number, and one long low tone for a loss.

With "Shelly weckt auf" switched off, a press outside a session opens nothing and plays nothing. It is only journaled and counted. That is the mode for measuring over hours how many presses Android's scan throttling lets through.

### The screen

Everything is on one screen. The upper part scrolls and consists of sections that open and close with a tap on their title; the lower part always shows the journal.

| Section | What is in it |
|---|---|
| "Einrichtung" | The checklist from "Install and set-up", plus the remote, the listener service and the spoken prompts |
| "Messen" | "Echo-Test jetzt", "Zählsitzung starten", and a text field that writes a free-text marker into the journal ("ab jetzt 2 h Ruhe") |
| "Zähler heute" | Events of the remote received and missed, hearing-aid taps, sessions by result. Counted from today's journal; simulated presses are left out |
| "Shelly: Anlernen und Funk-Test" | A scan that runs only while the app is on the screen: pick the remote by pressing one of its buttons ("Anlernen"), forget it again ("Vergessen"), and watch its raw packets with a count of events and of missed events. The switch "Passive Scan-Art" changes the scan type for this scan and for the background scan |
| "Einstellungen" | Scan mode outside and inside a session, the rescue scan, "Shelly weckt auf", and a button that makes the spoken prompts anew |

For running a series of measurements from a computer, see [docs/adb-control.md](docs/adb-control.md).

### The journal

Every event is written to a journal: one line per event with the time, the name of the event and the state of the phone at that moment (display on or off, locked, Doze, battery, role held, assistant service bound). The lower part of the app's screen shows it with the newest line on top ("Protokoll (neueste oben)"). Failures are shown in the error colour. The marks "·dunkel", "·gesperrt" and "·Doze" mean display off, locked and Doze.

To follow it live on a computer:

```sh
adb logcat -s LaraJournal
```

Each line is a JSON object with the keys `t` (wall-clock time in ms), `el` (ms since boot, counting sleep), `up` (ms since boot, not counting sleep), `type`, `f` (the fields of the event) and `ctx` (the state of the phone). If `el` advances more than `up` between two events, the phone slept in between.

A successful echo test started from the screen looks like this. The event names are those at the time of writing.

```text
input_routed                          origin=screen route=start_echo
session_start
session_voice_recognition_requested   the app answered the hearing aid
session_hfp_audio_up                  the Bluetooth stack opened the audio link
session_route_requested
session_link_up                       the rising pair of tones follows
session_speak_now
session_recorded                      microphone used, level, time to the first sound
session_echo_played
session_end                           result=ok
```

A count session opened by a tap begins with `tap_intent` (Android started the app for the tap) and `input_routed`, opens the link with the same lines up to `session_link_up`, and then goes on like this:

```text
session_count_open                    the program takes presses now
shelly_press                          a press of the remote arrived
input_routed                          origin=ble_callback route=to_session
session_count                         number=1, said=true, voice=speech or tones, waitMs
session_count_end                     count, reason=idle or tap
session_end                           result=ok
```

Every `session_*` line carries `ms`, the time since the trigger. Lines to look for when something fails:

- `session_refused`: Android did not let the app start its microphone service.
- `session_end` with a result other than `ok`, for example `link_timeout`, `no_hearing_aid_connected`, `recording_not_from_hearing_aid` or `cancelled`.
- `assistant_unbound`: the system dropped the assistant service.
- `input_dropped`: a press waited for a session that did not open, or arrived as the session closed.
- `session_count` with `missed` above 0, `shelly_press` with `missedBefore` above 0: the remote sent something that did not arrive.

Lines that come from an input say where it came from, in the field `origin` (in `session_*` lines: `trigger`): `tap`, `ble_callback` or `ble_rescue` for the two scans of the remote, `screen`, `adb`, `simulated`, `assist`.

The journal is stored in the app's private storage, one file per day. Its lines contain Bluetooth addresses and device names. The app never sends the journal anywhere by itself. "Heute teilen" and "Alle teilen" above the journal hand today's file or all files to Android's share sheet; check a copy before you post it.

## Building from source

Requirements:

- JDK 21
- Android SDK with platform 37.2 and build-tools 36.0.0: `sdkmanager "platform-tools" "platforms;android-37.2" "build-tools;36.0.0"`

```sh
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The Gradle wrapper fetches Gradle itself. The versions of the Android Gradle Plugin, Kotlin and the libraries are pinned in `gradle/libs.versions.toml`. The Android Gradle Plugin 9 has Kotlin support built in, so do not add the `org.jetbrains.kotlin.android` plugin.

The project has two modules. `:core` is pure Kotlin without Android imports and is tested on the JVM with `./gradlew :core:test`. `:app` is the Android part.

`compileSdk` is 37.2, `minSdk` 36 and `targetSdk` 36. Gradle properties: `-Plara.targetSdk=37`, `-Plara.versionName=...`, `-Plara.versionCode=...`.

Local builds have `versionCode` 1 unless you set it, and Android does not install a lower version code over a higher one. After installing a release, build with `-Plara.versionCode=` set to at least the code of that release. How releases are cut is described in [docs/release.md](docs/release.md).

### One signing key, always

Android lets an update replace an installed app only if both are signed with the same key. An APK that is signed with another key is refused, and the only way forward is to uninstall the app first. Uninstalling removes everything the set-up has built: the assistant role, the permissions, the battery exemption and the "Always" choice for the hearing-aid tap.

The build therefore uses one key for debug and release builds alike:

- If the file `.local/keystore/keystore.properties` exists, the build reads `storeFile`, `storePassword`, `keyAlias` and `keyPassword` from it. `storeFile` is a path relative to the repository root. The `.local` directory is ignored by git.
- Otherwise it reads the same values from the environment variables `LARA_STOREFILE`, `LARA_STOREPASSWORD`, `LARA_KEYALIAS` and `LARA_KEYPASSWORD`.
- Otherwise it falls back to the Android debug key of the computer it runs on.

The consequences: a build you make yourself cannot update an APK from the Releases page, and the other way round. The debug APK that the CI workflow builds is signed with a throw-away key and is not offered for download for the same reason. A build made with the debug key of one computer cannot update a build made on another. Decide once which key the installation on your phone uses, and keep it. Never commit a keystore.

### Two things a change must not break

- Never rename, move or disable `trigger.VoiceCommandActivity`. Android then removes the "Always" choice without a word.
- Keep all three assistant services and their metadata complete. A build that no longer qualifies for the assistant role loses it on installation, and all background rights with it.

`sh tools/check-assistant-metadata.sh` checks both from the source files, without a build. The CI and release workflows run it before they build.

## Debugging toolkit

These are the logcat tags and commands that proved useful while finding out the above. All of them work on a stock phone with USB debugging. Nothing needs root.

```sh
adb logcat -s LaraJournal HeadsetService ActivityTaskManager AS.AudioDeviceBroker BtScan.ScanManager
```

| Tag | What to look for |
|---|---|
| `LaraJournal` | The app's own journal |
| `HeadsetService` | The hands-free part of the Bluetooth stack: `startVoiceRecognitionByHeadset` when the tap arrives, `startVoiceRecognition` when an app answers, `isScoAcceptable: rejected SCO` when the audio link is refused |
| `ActivityTaskManager` | `START u0 {act=android.intent.action.VOICE_COMMAND ... cmp=...}` names the activity that Android started for the tap. `cmp=android/com.android.internal.app.ResolverActivity` means that there was no default and the chooser was opened |
| `AS.AudioDeviceBroker` | The audio server's routing: which app (by uid) requested or cleared the communication route, and when the route changed |
| `BtScan.ScanManager` | The Bluetooth scan manager, for the remote. According to the source it logs the scan parameters that are really applied, as `in scan unit=<interval> / <window>` in units of 0.625 ms |

```sh
adb shell cmd role get-role-holders android.app.role.ASSISTANT
adb shell cmd package resolve-activity --brief -a android.intent.action.VOICE_COMMAND
adb shell dumpsys voiceinteraction
adb shell dumpsys bluetooth_manager
adb shell dumpsys audio
adb shell dumpsys media_session
adb shell device_config list bluetooth
```

| Command | What it answers |
|---|---|
| `cmd role get-role-holders android.app.role.ASSISTANT` | Which package is the digital assistant |
| `cmd package resolve-activity --brief -a android.intent.action.VOICE_COMMAND` | Which activity a tap would start right now |
| `dumpsys voiceinteraction` | Whether the assistant's service is the active one and is bound |
| `dumpsys bluetooth_manager` | The profiles connected to the hearing aid, the state of the hands-free link, the registered BLE scans |
| `dumpsys audio` | The communication route and who requested it, the audio mode, and in the section "Hardening enforcement" the playback that Android muted |
| `dumpsys media_session` | The media sessions and who receives media keys |
| `device_config list bluetooth` | The flags of the Bluetooth stack, among them `scan_allowance_throttling_enabled` |

Two warnings. These outputs contain Bluetooth addresses and device names: remove them before you post a log anywhere. And for measurements after standby the phone must be unplugged, because it does not go into deep sleep on a charger or a USB cable. The journal is stored on the phone for that reason: read it on the app's screen afterwards.

## Related projects

This project stands on other people's work. If you wear hearing aids and want to build something similar, start here.

Every repository below was checked through the GitHub API on 2026-11-27. Most are young projects by one author, tested on that author's own devices. Licences are given because this code base is Apache-2.0. Code under MIT, BSD or Apache-2.0 can be reused with attribution. Projects marked **ideas only** are under GPL or AGPL: read them and learn from them, but do not copy their code into an Apache-2.0 code base. Using such an app as a tool is fine.

### A button on a hearing aid or headset starts an assistant

- [jooray/i-hear-you](https://github.com/jooray/i-hear-you) (MIT): Android remote for GN/ReSound hearing aids in which the aid's own button starts a voice note that is recorded through the aids, transcribed on the phone, sent to a webhook or an agent and answered with speech in the aids. It is the closest relative of this project, has a unit-tested gesture engine and a debug receiver that simulates every input over adb, and does not work with Phonak.
- [kerero/airdots-double-tap](https://github.com/kerero/airdots-double-tap) (MIT, last change 2023): the smallest example of the entry point used here, one invisible activity with an intent filter for `android.intent.action.VOICE_COMMAND` that catches the assistant gesture of Bluetooth earphones.

### The hearing-aid microphone on Android

- [jooray/android-bluetooth-microphone](https://github.com/jooray/android-bluetooth-microphone) (MIT): a written field guide to recording from Bluetooth headsets, LE Audio earbuds and hearing aids, including recording with the screen off and a debugging recipe, measured by its author on a Pixel 10 Pro Fold with Android 17.
- [jooray/third-ear](https://github.com/jooray/third-ear) (GPL-3.0, ideas only): a voice recorder that implements that guide, worth reading for how it decides when a Bluetooth microphone really delivers sound and for its crash-safe recording journal.
- [mptfire/bt-mic](https://github.com/mptfire/bt-mic) (MIT text with an added note, see its LICENSE file): a small implementation of the same guide that keeps its decision logic in pure state machines which are tested without Android.
- [thivyan-studios/hark-app](https://github.com/thivyan-studios/hark-app) (BSD-3-Clause): turns the phone into a remote microphone that streams to hearing aids and shows offline live captions; it shows how to keep track of hearing-aid audio devices and how to recover a stream after a route change.

### Phonak

- [ijustlikeit/phonak-ha](https://github.com/ijustlikeit/phonak-ha) (MIT): Phonak battery, volume, program and mute in Home Assistant through an Android bridge app. Its `PROTOCOL.md` is the only public description of the Sonova control protocol that our search turned up, and `tools/sonova_decode.py` shows how to decode the vendor app's traffic from a Bluetooth snoop log. It can switch the tap setting on and off but receives no tap events.
- [ryleighnewman/Self-Programming-My-Phonak-Hearing-Aids-With-Claude-AI](https://github.com/ryleighnewman/Self-Programming-My-Phonak-Hearing-Aids-With-Claude-AI) (MIT): a wearer's detailed account of adjusting Phonak Audéo Sphere hearing aids with the fitting software. Background reading, not medical advice.

### Shelly BLU and BTHome buttons on Android

- [OferTiber/SaraButton](https://github.com/OferTiber/SaraButton) (MIT): an accessibility app in which a press on a Shelly BLU remote places a phone call. It supports the remote model used here and shows background reception with two scans at once, a callback scan and a `PendingIntent` scan.
- [altinojoao/bthome-bridge](https://github.com/altinojoao/bthome-bridge) (MIT licence file, but the README reserves all rights, so treat it as ideas only until that is cleared up): forwards BTHome button presses to HTTP with the screen off, worth reading for how it renews and watches its scans and for BTHome decryption in Kotlin. The README is in Portuguese.
- [steanlab/BTHome-Logger](https://github.com/steanlab/BTHome-Logger) (MIT): shows unencrypted BTHome data live on the phone, the quickest way to see what a remote really sends.
- [Bluetooth-Devices/bthome-ble](https://github.com/Bluetooth-Devices/bthome-ble) (MIT) and [home-assistant/bthome.io](https://github.com/home-assistant/bthome.io) (no licence file): the reference parser that Home Assistant uses, and the source of the format specification. The parser's tests make good test vectors for a parser of your own.
- [ALLTERCO/shelly-script-examples](https://github.com/ALLTERCO/shelly-script-examples) (Apache-2.0): the vendor's own scripts, among them its BTHome parsing for Shelly BLU devices (`ble/ble-shelly-blu.shelly.js`).
- [theengs/decoder](https://github.com/theengs/decoder) (GPL-3.0, ideas only): a decoder library with a device description and test packets for Shelly's four-button remotes.

### Hermes clients that take the Android assistant role

- [Codename-11/hermes-relay](https://github.com/Codename-11/hermes-relay) (MIT): native client with chat, hands-free voice and an optional assistant role that also starts from the lock screen. Its [issue 774](https://github.com/Codename-11/hermes-relay/issues/774) reports the same finding as ours: a Bluetooth assistant gesture bypasses the default assistant and arrives as `VOICE_COMMAND`.
- [Hy4ri/hermes-mobile](https://github.com/Hy4ri/hermes-mobile) (Apache-2.0): native Compose client under the same licence as this project. It talks to Hermes over the REST API and the WebSocket gateway and declares a `VoiceInteractionService`.
- [JusticeFist/alfatherapp](https://github.com/JusticeFist/alfatherapp) (MIT): phone and Wear OS client whose assistant part is small enough to read in one sitting. It starts a live call from the assistant session and selects a headset with `setCommunicationDevice`.
- [Giton22/kyllene](https://github.com/Giton22/kyllene) (MIT): phone and Wear OS client, both with a `VoiceInteractionService`. It exposes spoken replies as a media session, so that play and pause buttons control them.
- [yuga-hashimoto/openclaw-assistant](https://github.com/yuga-hashimoto/openclaw-assistant) (MIT): voice client for OpenClaw and Hermes with wake word and assistant role. Its README documents the settings for talking to the Hermes API server.

### Home Assistant's Assist, the mature reference for the role

- [home-assistant/android](https://github.com/home-assistant/android) (Apache-2.0): the most mature open app that can be the default assistant, with a start from the lock screen, a microphone foreground service and an activity alias for `VOICE_COMMAND`. Its [issue 7834](https://github.com/home-assistant/android/issues/7834) documents on a Pixel with Android 17 that the voice gesture of a Bluetooth headset does not go to the selected assistant.
- [majorminorlabs/hermes-voice](https://github.com/majorminorlabs/hermes-voice) (MIT): connects a Hermes profile to Home Assistant Assist, a way to talk to Hermes through an existing assistant app without writing one.

### Hermes Agent and other clients

- [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent) (MIT): the agent itself. Its gateway code is the authority for the HTTP interface that clients use.
- [chezgoulet/hermes-vox](https://github.com/chezgoulet/hermes-vox) (GPL-3.0, ideas only): voice-first client with speech processing on the device. Read `docs/ASSISTANT-ROLE.md` for a plain account of what the assistant role does and does not do, and its notes on routing audio to a Bluetooth headset.
- [virse/HermiVoiceApp](https://github.com/virse/HermiVoiceApp) (Apache-2.0): sideloaded push-to-talk app with German and English offline speech recognition through sherpa-onnx. Its README is German first.

### Starting a voice session on a locked phone

- [suddenBook/GPTWake](https://github.com/suddenBook/GPTWake) (the LICENSE file holds an Apache-2.0 notice): starts a voice session from a wake word on a locked tablet with the screen off. Its README has a table of Android constraints measured on a real device, among them that the app had to stay the default assistant to get the microphone under the lock screen.
- [NiallBunting/AssistantVoiceRecorder](https://github.com/NiallBunting/AssistantVoiceRecorder) (GPL-3.0, ideas only): about the smallest complete example of a selected assistant that starts a microphone recording in a foreground service.
- [DicioTeam/dicio-android](https://github.com/DicioTeam/dicio-android) (GPL-3.0, ideas only): offline voice assistant that takes the assistant slot with an activity for `ASSIST` and `VOICE_COMMAND` instead of a `VoiceInteractionService`, which shows the other way to become an assistant.

### A spoken menu without a screen

- [Scottish-Tech-Army/Soundscape-Android](https://github.com/Scottish-Tech-Army/Soundscape-Android) (MIT, see its `LICENSE.md` for the files from other sources): audio navigation for blind people with a spoken, hierarchical menu that is operated with the media buttons of a headset (`AudioMenu.kt`). It is the model for the menu planned here.

### Other remotes, and building your own

- [zmkfirmware/zmk](https://github.com/zmkfirmware/zmk) (MIT) with [Genteure/zmk-bthome](https://github.com/Genteure/zmk-bthome) (MIT): keyboard firmware and a module that adds BTHome advertisements, a starting point for a home-made remote that sends both media keys and BTHome button events.
- [50ButtonsEach/flic2lib-android](https://github.com/50ButtonsEach/flic2lib-android) (its own permissive licence, see `License.txt`): the official Android library for Flic 2 buttons, which hold a connection instead of advertising. Its README says what the vendor recommends for running in the background.

### Speech on the device

- [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache-2.0): speech recognition, speech synthesis, voice activity detection and keyword spotting that run on the phone, with ready Android example apps.
- [ggml-org/whisper.cpp](https://github.com/ggml-org/whisper.cpp) (MIT) and [snakers4/silero-vad](https://github.com/snakers4/silero-vad) (MIT): Whisper speech recognition in C and C++ with Android examples, and a small model that tells speech from silence.

### More for people who wear hearing aids

The streaming projects in this group use ASHA or LE Audio. The hearing aid used here streams over Bluetooth Classic, so they are listed for readers with other hearing aids.

- [Gheop/hearing-aid-presets](https://github.com/Gheop/hearing-aid-presets) (MIT): switches the programs of LE Audio hearing aids from the GNOME desktop through the standard Hearing Access Service, with thorough set-up notes for Linux. Tested by its author on ReSound.
- [thewierdnut/asha_pipewire_sink](https://github.com/thewierdnut/asha_pipewire_sink) (Unlicense): streams audio from Linux to ASHA hearing aids, with a frank account of the state of ASHA on Linux.
- [shermp/Pico-ASHA](https://github.com/shermp/Pico-ASHA) (BSD-3-Clause for the source): a USB audio dongle built from a Raspberry Pi Pico W that streams from any computer to ASHA hearing aids, with a table of hearing aids that work.
- [Tympan/Tympan_Library](https://github.com/Tympan/Tympan_Library) (MIT) and [HoerTech-gGmbH/openMHA](https://github.com/HoerTech-gGmbH/openMHA) (AGPL-3.0, ideas only): an open hardware platform for hearing aids and an open research platform for hearing-aid signal processing, for readers who want to go below the Bluetooth layer.
- [rscircus/awesome-hearing](https://github.com/rscircus/awesome-hearing) (MIT): a list of further projects around hearing aids.

### Testing, debugging, updating

- [google/bumble](https://github.com/google/bumble) (Apache-2.0): a Bluetooth stack in Python that, with a USB dongle, can play a hands-free headset, a hearing aid or an advertising button (`examples/run_hfp_handsfree.py`, `examples/run_advertiser.py`), so that code on the phone can be tried without the real hardware.
- [mauricelam/btsnoop-extcap](https://github.com/mauricelam/btsnoop-extcap) (MIT): shows the phone's Bluetooth traffic live in Wireshark over adb.
- [ImranR98/Obtainium](https://github.com/ImranR98/Obtainium) (GPL-3.0, ideas only as far as its code goes): installs and updates sideloaded apps straight from their GitHub release pages.

## Roadmap

1. Measure the remote: run the count session with each scan setting, at once and after long standby, and decide whether the remote can wake the phone or only navigate.
2. Media keys as a third input.
3. The first signed release. The workflow that builds the APK for a version tag is in the repository; no release has been published with it yet.
4. The spoken menu, with a button layout based on the measurements.
5. The connection to Hermes: speech recognition, the HTTP interface of the Hermes server, and a channel back for reminders.

Not planned: a release in an app store, support for other phones or hearing aids, and control of the hearing aid itself (volume, programs).

## Licence

Apache License 2.0, see [LICENSE](LICENSE).

This project is not affiliated with, endorsed by or supported by Sonova or Phonak, Shelly, Google or Nous Research. Product names are trademarks of their owners. The app is not a medical device and does not change how the hearing aid works.
