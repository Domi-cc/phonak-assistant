# Lara im Ohr: context for an AI coding session

This file is the hand-over. Read it first; it is written so that a new session, on any machine,
can continue without the conversation that produced the code.

## What this is

A native Kotlin Android app for **one** phone and **one** hearing aid. The owner wants to use
his self-hosted AI assistant "Lara" (a Hermes Agent) through a Phonak hearing aid and a small
pocket remote, while the phone stays **locked, with the display off, in his pocket**.

Version 0.1 is a **measurement app**: it finds out on real hardware what Android allows. Menu and
Hermes connection come after the measurements.

- Phone: Google Pixel 10 Pro XL, Android 17 (SDK 37.1).
- Hearing aid: Phonak Audéo EON R, right ear only. Bluetooth Classic: hands-free (HFP) + media (A2DP).
- Remote: Shelly BLU RC Button 4, model `SBBT-004CUS` (the one **without** Zigbee). BTHome v2 advertisements.
- Distribution: sideloaded APK (adb, GitHub Releases). No Play Store.
- Repository: public, Apache-2.0. App id `io.github.domicc.lara`.

## State on 27 November 2026

**Worked on the device** with the build of that day (details and numbers: [`docs/measurements/2026-11-27.md`](docs/measurements/2026-11-27.md)):

- The sideloaded app holds the assistant role; the role survives updates.
- A double tap on the hearing aid starts the app with the phone locked, dark and in Doze. The display stays off.
- Voice link up in 0.5 to 0.8 s, recording from the hearing-aid microphone, playback into the aid.
- In a session opened by the tap, presses on the Shelly remote are received and **spoken** ("Taste 1 kurz"). The session ends after 30 s without a press, on a long press of button 4, or on a second tap; the app says "Beende".
- Unprompted announcements over the media channel; the hourly test announcement fired from Doze 48 ms late.

**Built on top of that, not yet on the phone** (the branch `background-listener`; how it came
about: "History of the background-listening increment" below). It compiles, lint is clean and
the core tests pass. **None of it has run on a device**, and it changed the way the measured
paths above take through the code:

- `shelly/ListenerService`: a permanent `connectedDevice` foreground service that scans for the
  enrolled remote all day, started from the assistant service's `onReady`, after boot and
  update, and whenever the app comes onto the screen. Optional rescue scan, optional refresh alarm.
- `input/InputRouter`: every trigger ends there. A press with no session open opens a **remote
  session** and is the first press announced (setting "Shelly weckt auf", default on). Not
  during a phone call, and not for a press that reaches the app more than 20 s late.
- Three session programs: `remote` (presses in words; what a tap starts with a remote enrolled
  and what a press opens), `echo` (what a tap starts without a remote), `count` (running
  numbers, a tool for series; from the screen and adb only, unless the tap setting says so).
- **One scan in a session:** the listener's scan is switched to its session mode by
  `SessionService` and stays there for 15 s after the session. The session's own scan
  (`SessionScan`) is gone.
- `control/AdbReceiver` and [`docs/adb-control.md`](docs/adb-control.md); day counters and
  journal sharing; a screen made of folding sections above the journal.

**Not built:** media keys, spoken menu, Hermes connection, speech recognition.

## Decisions the owner made

- Native Android, Kotlin. Maximum debuggability, no artificial limits on platform access.
- One real app that starts as a measurement app, not throw-away prototypes.
- README in English with related projects for other hearing-aid wearers; app texts and the design document in German.
- All sound goes to the Phonak only. **Never** to a car kit, another headset or the phone speaker. He stressed this.
- Presses on the remote are announced in words, not with tones. Every normal end of a session says "Beende".
- The count session (running numbers instead of words) is a measurement tool he did not ask for. It stays available for series, from the screen and over adb, but it is never what a tap or a press starts by default.
- The hearing aid is chosen once, by him, on the screen; the remote is searched and picked on the screen. Addresses are shown shortened there.
- The signing key lives in GitHub Actions secrets; GitHub builds the signed APKs. The key is not copied to other machines.
- The app shows its build number, and every APK he gets has a higher one than the last. He tests without a computer next to him and names the build when he reports.
- He likes fast iterations with something to try on the phone, and short status messages in German.

## Rules learned the hard way (do not break them)

1. **The hearing-aid tap is not routed through the assistant role.** Android's Bluetooth stack
   starts an activity with `android.intent.action.VOICE_COMMAND`. `trigger.VoiceCommandActivity`
   receives it. Never rename, move or disable that component: Android silently drops the owner's
   "Always" choice.
2. **Keep the three assistant services** (`VoiceInteractionService`, session service, recognition
   service) and their XML complete. A build without them is dropped from the role without a
   message. `tools/check-assistant-metadata.sh` guards this in CI.
3. **Answer the tap with `BluetoothHeadset.startVoiceRecognition` within 5 s**, and always call
   `stopVoiceRecognition` at the end. A link left open makes Android reject the next tap.
4. **In a voice-recognition session never call `AudioManager.setCommunicationDevice` yourself.**
   It makes the audio server open a "virtual call" and the Bluetooth stack cancels the session.
   This cost one tap in three in the first build. See the KDoc of `audio.AudioLink`.
5. **The voice link only lives while a stream is active.** One `AudioTrack` plays for the whole
   session; between sounds it gets silence (`AudioLink.silence`).
6. **Speech inside a session must be played by the app itself.** `TextToSpeech.speak` plays in the
   engine's process. `speech.PromptCache` synthesises to files, converts to 16 kHz mono and caches.
7. **Only the selected hearing aid** (`audio.HearingAidStore`, by address). Nothing audible before
   the link to it is up; failures before that vibrate. The voice button of other devices is handed
   on to the phone's own assistant.
8. **Start BLE scans from a running foreground service** and always with a filter; request the
   passive scan type. Android samples the app's importance when a scan starts.
9. **One signing key and one application id for every build.** A different key means uninstall,
   and uninstall loses the role, the permissions and the "Always" choice.
10. **Never turn the screen on, never record without a deliberate trigger, never fail silently**
    (tone, vibration and a journal line).

## Build, install, release

```sh
# JDK 21 and the Android SDK (platform 37.2, build-tools 36.0.0)
./gradlew :core:test :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- Versions are pinned in `gradle/libs.versions.toml`. AGP 9 has Kotlin built in: do not add the
  `org.jetbrains.kotlin.android` plugin. `compileSdk` is 37.2 (`release(37) { minorApiLevel = 2 }`),
  `minSdk` 36, `targetSdk` 36 (`-Plara.targetSdk=37` for the strict Android 17 audio rules).
- `versionCode` is the number of commits, locally and in CI, so later builds install over earlier ones.
  It is the build number the app shows at the top of its screen ("Build 18 · 0.1.0-dev · 9a1591f")
  and writes into `process_start`. **Commit before you build an APK that goes to the phone**: only
  a commit raises the number. A build from uncommitted changes shows "Build 18+ … geändert" and
  must not be handed over.
- An APK sent through the chat has to stay under 30 MiB: build with `-Plara.compressDex=true`.
- **Without the key** (any machine but the owner's desktop): a local build is signed with a debug
  key and **cannot** replace the installed app. Push the branch and let GitHub build it:
  `gh workflow run release.yml --ref <branch>`, then `gh run download`. See [`docs/release.md`](docs/release.md).
- A release is a tag `vX.Y.Z` on `main`. No release has been cut yet; the owner wants to decide when.
- Git: branch, pull request, wait for `ci`, merge. The owner allowed merging. Commits carry his
  GitHub no-reply address (set in the local git config of his desktop clone); do not commit with
  another address. Pushing workflow files needs SSH or a token with the `workflow` scope.

## Working with the phone

- Use **adb over Wi-Fi**: `adb mdns services`, then `adb connect <ip:port>`. A phone on a USB cable
  never reaches deep sleep, so cable measurements say little.
- The app's journal is the main tool: on the screen, in logcat (`adb logcat -s LaraJournal`) and as
  files (`adb exec-out "run-as io.github.domicc.lara sh -c 'cat files/journal/*.jsonl'"` on debug builds,
  or "Heute teilen" on the screen). Every line has three clocks and the phone state (screen, lock,
  Doze, role, binding, scan). The screen leaves out `shelly_packet` (one line per advertisement);
  logcat and the files have it.
- The app can be driven over adb (start a session, change scan settings, write a marker, simulate
  a press, ask for the status): [`docs/adb-control.md`](docs/adb-control.md). Not tried on the phone yet.
- Useful system tags: `HeadsetService`, `HeadsetStateMachine`, `ActivityTaskManager`,
  `AS.AudioDeviceBroker`, `BtScan.ScanManager`. More commands: README, "Debugging toolkit".
- An update restarts the app. Do not install while the owner is in the middle of a test, and not
  around the full hour while the hourly announcement test is on.
- Things only the owner can do: choose the app as digital assistant, pick the hearing aid in the
  app, tap once while unlocked and choose the app with "Always".

## Layout

| Path | What |
|---|---|
| `core/` | pure Kotlin, 216 JVM tests: journal format and names, BTHome parser, press tracking with loss counting, input model, routing (`Routing`, `WaitingInputs`), count logic, scan settings and start budget, adb parameters, day statistics, WAV reading, resampling, silence trimming |
| `app/…/trigger` | the invisible activity for the hearing-aid tap; hands the voice button of other devices on |
| `app/…/assistant` | the three assistant services; `onReady` starts the listener and warms the phrases |
| `app/…/input` | `InputRouter` (the only caller of `SessionService.start`), `TapStore` |
| `app/…/audio` | `AudioLink` (the voice link), `SessionService`, `SessionPrograms`, `EchoProgram`, `CountProgram`, `HearingAidStore`, tones |
| `app/…/shelly` | `ShellyStore`, `ListenerService` with `RemoteScan`, `ListenerStarter` and `RescueScanReceiver`, `ShellyIntake` (packets to presses to the input bus), `ForegroundScan` (search and live test), `RemoteProgram`, phrases, tone fallback |
| `app/…/speech` | `PromptCache` with `SpeechEngine`, the phrase lists (`Prompts`), `CountSounds` |
| `app/…/announce` | unprompted announcements over the media channel, hourly test |
| `app/…/control` | `AdbReceiver` |
| `app/…/journal`, `app/…/ui` | journal, day counters, sharing; setup checklist and the sections of the one screen |
| `docs/design/` | design document for version 0.1 (German); its "Nachtrag" overrides the older text |
| `docs/measurements/` | what was measured on the device |
| `docs/research/` | AI research and verification reports; leads, not facts |
| `docs/adb-control.md` | the adb commands |
| `docs/release.md` | signed builds, releases, the key |

Not in the repository, only on the owner's desktop under `.local/` (git-ignored): the keystore,
raw device captures (they contain Bluetooth addresses) and the build agents' scratch material.

## How the inputs fit together

Worth knowing before changing anything between a trigger and a sound:

- **One way in.** Tap, remote, screen, adb and assist gesture all end in `input/InputRouter`.
  What an input does is decided in `core` (`Routing`), with tests. Nothing else calls
  `SessionService.start`.
- **One session at a time.** `SessionService` is busy from accepting a session until its link is
  closed, "Beende" included, **and the hands-free audio is really down** (`session_link_down`,
  at most 1.5 s). A trigger in that time is turned down (`session_busy`; a tap also vibrates).
  Every session that is over, also one that failed to open or that Android refused, is reported
  to the router through `SessionService.endHandler`. The router then gives up presses that
  waited for a session which never listened, and opens the next session for a press made
  during "Beende". Why the wait for the audio: by the Android 17 source `startVoiceRecognition` is
  refused while the audio of the previous session is still closing, and `AudioLink` would then
  fall back to the virtual call (rule 4). Not measured yet.
- **One scan for the remote.** `ListenerService` owns it. A session that listens to the remote
  (`remote`, `count`) only switches its mode (`ListenerService.setSessionOpen`, called by
  `SessionService`). The way back to the idle mode is taken 15 s after the session: sessions
  that follow each other then cost no scan start, and a press during "Beende" is heard. Scan
  starts are booked into one budget (`BleScans.starts`): Android counts them per app.
- **In the remote session a press never starts a recording.** Every button is announced, the
  long press of button 4 ends the session, and that is all: a press can open this session by
  itself, also by accident. The echo test runs inside a session only when the screen, adb or
  the assist gesture asks for it; the count session, which is always started on purpose, also
  runs it on button 3. A failed echo test inside a session is journaled and does not turn
  "Beende" into the error tone.
- **A press does not open a session into a phone call** (`AudioManager.mode` is one of the
  call modes, ringing included): `input_dropped reason=phone_call` and the vibration. The
  tap, the screen and adb are not held back.
- **Presses reach programs through the router's inbox.** `ShellyIntake` turns packets into
  presses and publishes them on the bus in `LaraApp`; the router hands them to the running
  program (`openSession` / `closeSession`). The press that opened a session waits in the router
  and is the first one the program gets.
- **Phrases are identified by their text.** `speech/Prompts` lists all of them; a cached file
  is named after the hash of its text. Add a phrase there and it is synthesised at the next
  start; a session never waits for the engine and falls back to tones.
- The end of a session runs in `withContext(NonCancellable)` for a reason: a cancelled
  coroutine (second tap, link lost) is thrown out of its clean-up at the first suspension
  otherwise. Before the port that silently skipped everything after closing the link. For the
  same reason the session is launched with `CoroutineStart.ATOMIC`: a session cancelled before
  its first line ran would otherwise never clean up and leave the service busy for good.
- After a second tap the service is freed first and "Beende" follows over the media channel
  half a second later, unless a new session has begun by then.

## History of the background-listening increment

On 27 November 2026 agents built a larger increment in a scratch copy that started from an older
state of the project: listener service, router, count session, adb control, a new screen. It was
reviewed there, but its audio files lacked rules 4 and 7. It was ported onto `main` by hand
(branch `background-listener`); if a branch `wip/workflow-snapshot` exists, it holds the scratch
copy as it was and must not be merged.

What the port decided, where the two sides disagreed:

- `audio/AudioLink.kt`, `audio/HeadsetLink.kt`, `audio/Tones.kt` and the announcements are the
  ones that ran on the phone. `AudioLink` only gained the shared journal names and a public
  `vibrateFailure`.
- `SessionService` kept its session rules (30 s idle is the program's, five minutes its own,
  "Beende" at every normal end) and gained: the busy check that covers a closing session, a wake
  lock per session, `endHandler`, the call to the listener, and a vibration when Android refuses
  a start.
- The scratch copy let a tap and a press start the count session. The owner wants words: tap and
  press start the remote session; the count session is the third program.
- The session's own scan and the old search dialog are gone; `ForegroundScan` and the section
  "Shelly: Suchen und Live-Test" replace them, with shortened addresses and the list of events
  with burst sizes that the old live test had.
- `PromptCache` is the scratch copy's (status, rebuild, trimming) but keeps the file names of the
  version on the phone, so the phrases already synthesised there stay usable.
- Not taken: the scratch copy's README, design document, CI workflows and `abortOnError = true`.

Two reviews of the port, before it reached the phone, changed this:

- The port had given button 3 (long) an echo test inside the remote session, and a failed echo
  test ended a session with the error tone. Both are gone: the owner asked for presses in
  words, a press may open a session from a pocket, and a normal end says "Beende".
- `SessionService` waits for the hands-free audio to be down before it is free, launches the
  session atomically, frees itself before the media-channel "Beende" and releases its wake
  lock last.
- The scan lingers in session mode for 15 s (`ListenerService.setSessionOpen`).
- The router holds presses back during a phone call and drops presses that arrive too late
  (`Route.TOO_LATE`), and a tap that is turned down as busy vibrates.
- Looked at and left alone: a scan of the session's own as a fallback when the listener cannot
  be started (it would be a second owner of the scan; the journal shows the case as
  `session_remote_no_scan why=service_not_running` next to `listener_refused`), and remembering
  a tap that met a busy service (after the last sound the service is now busy only while the
  link closes, and by the Android source the Bluetooth stack rejects taps itself in that time;
  not measured).

## Next steps, in order

1. **Install the ported build and check it on the phone** before anything else is built on it.
   In this order: the tap still opens a remote session and "Taste 1 kurz" is still spoken
   (the remote is now heard through the listener's scan); `listener_start` and `scan_started`
   in the journal, the notification "Lara lauscht auf die Fernbedienung"; a press with the
   display on opens a session; the same with the phone locked and dark (expect
   `session_refused` if the assistant binding does not carry a microphone start from a scan
   callback: that is the open question of this increment); a second tap now says "Beende"
   over the media channel; a press during "Beende" opens the next session and its
   `session_voice_recognition_requested` says `accepted=true` (if not, the wait behind
   `session_link_down` is too short or looks at the wrong thing); `lara.STATUS` over adb.
2. Then measure: can a Shelly press open a session with the phone dark, after minutes and after
   hours? Expect trouble from Android 17's scan allowance
   (`scan_allowance_throttling_enabled=true` on this phone; effect unmeasured). Compare the idle
   scan modes and the rescue scan. Hold a button for ten seconds once and look at what arrives:
   a continuous hold could be the reliable wake gesture.
3. Longer reference series for the remote (30 paced presses, app on screen, passive against
   active scan type), and the same in a session with the phone in a pocket. The count session
   and the adb commands are made for this.
4. Let the hourly test run for a day and read alarm lateness and outcome per Doze state.
5. Update the design document to the measurements, then design the spoken menu.
6. Media keys through `SET_MEDIA_KEY_LISTENER` (adb-grantable, slot was free on this phone).
7. Hermes: HTTP API server (port 8642, bearer key), not the WebSocket; reach it over WireGuard.
   See `docs/research/verify-hermes-api.md`.
8. First release: the owner decides when. The secrets are in place.

Known gaps of the port, none of them urgent: the journal has no size limit and old day files
are never deleted; `shelly_packet` writes one line per advertisement on the main thread; a tap
that Android delivers while the service is still busy is turned down (with a vibration) and
not started afterwards; a session cannot hear the remote if the listener service is not
running and cannot be started; the router and the session programs have no unit tests of
their own (their decisions are tested in `core`).
