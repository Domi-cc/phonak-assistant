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

**Works on the device** (details and numbers: [`docs/measurements/2026-11-27.md`](docs/measurements/2026-11-27.md)):

- The sideloaded app holds the assistant role; the role survives updates.
- A double tap on the hearing aid starts the app with the phone locked, dark and in Doze. The display stays off.
- Voice link up in 0.5 to 0.8 s, recording from the hearing-aid microphone, playback into the aid.
- In a session opened by the tap, presses on the Shelly remote are received and **spoken** ("Taste 1 kurz"). The session ends after 30 s without a press, on a long press of button 4, or on a second tap; the app says "Beende".
- Unprompted announcements over the media channel; the hourly test announcement fired from Doze 48 ms late.

**Built, not yet on the phone:** see "Unmerged work" below (background listening, so that a
Shelly press can open a session by itself; count session; adb control).

**Not built:** media keys, spoken menu, Hermes connection, speech recognition.

## Decisions the owner made

- Native Android, Kotlin. Maximum debuggability, no artificial limits on platform access.
- One real app that starts as a measurement app, not throw-away prototypes.
- README in English with related projects for other hearing-aid wearers; app texts and the design document in German.
- All sound goes to the Phonak only. **Never** to a car kit, another headset or the phone speaker. He stressed this.
- Presses on the remote are announced in words, not with tones. Every normal end of a session says "Beende".
- The signing key lives in GitHub Actions secrets; GitHub builds the signed APKs. The key is not copied to other machines.
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
  files (`adb exec-out "run-as io.github.domicc.lara sh -c 'cat files/journal/*.jsonl'"` on debug builds).
  Every line has three clocks and the phone state (screen, lock, Doze, role, binding).
- Useful system tags: `HeadsetService`, `HeadsetStateMachine`, `ActivityTaskManager`,
  `AS.AudioDeviceBroker`, `BtScan.ScanManager`. More commands: README, "Debugging toolkit".
- An update restarts the app. Do not install while the owner is in the middle of a test, and not
  around the full hour while the hourly announcement test is on.
- Things only the owner can do: choose the app as digital assistant, pick the hearing aid in the
  app, tap once while unlocked and choose the app with "Always".

## Layout

| Path | What |
|---|---|
| `core/` | pure Kotlin, JVM tests: journal format, BTHome parser, press tracking with loss counting, input model, WAV reading and resampling |
| `app/…/trigger` | the invisible activity for the hearing-aid tap |
| `app/…/assistant` | the three assistant services |
| `app/…/audio` | `AudioLink` (the voice link), `SessionService`, `SessionProgram`s, `HearingAidStore`, tones |
| `app/…/shelly` | store, scan helpers, search and live test, the session scan, `RemoteProgram`, phrases |
| `app/…/speech` | `PromptCache` and session phrases |
| `app/…/announce` | unprompted announcements, hourly test |
| `app/…/journal`, `app/…/ui` | journal, setup checklist, dialogs |
| `docs/design/` | design document for version 0.1 (German); its "Nachtrag" overrides the older text |
| `docs/measurements/` | what was measured on the device |
| `docs/research/` | AI research and verification reports; leads, not facts |
| `docs/release.md` | signed builds, releases, the key |

Not in the repository, only on the owner's desktop under `.local/` (git-ignored): the keystore,
raw device captures (they contain Bluetooth addresses) and the build agents' scratch material.

## Unmerged work

A larger increment was built by agents in a scratch copy of the project and reviewed there. If
the branch `wip/workflow-snapshot` exists, it holds that copy as it was; it is **not** based on
current `main` and must not be merged as it is. Port it:

- New and valuable: `shelly/ListenerService` and friends (a permanent `connectedDevice`
  foreground service with background scan, re-registration on boot, Bluetooth on and alarm, an
  optional rescue scan), `input/` (router and event bus), a count session, `control/` (adb
  control receiver, documented in `docs/adb-control.md`), a redesigned main screen with
  journal sharing, more checklist items.
- Already taken from it: the whole `core/` module, `shelly/ShellyStore.kt`, `shelly/BleScans.kt`,
  the CI workflows and `docs/release.md`.
- Conflicts to expect: its `audio/AudioLink.kt`, `audio/SessionService.kt`, `audio/HeadsetLink.kt`
  are **older** than `main` (they lack rules 4 and 7 above): keep the versions from `main`. Its
  `LaraApp`, `MainActivity`, manifest, `SetupStatus` and `VoiceCommandActivity` were changed on
  both sides and need a careful merge. `main` already has its own simple `RemoteProgram`,
  `SessionScan`, `ShellyLive`, `PromptCache` and announcements; decide per piece which to keep.

If that branch does not exist, the work was either merged or lost; check `git log` for
"background listening".

## Next steps, in order

1. Port the background listener, then measure: can a Shelly press open a session with the phone
   dark, after minutes and after hours? Expect trouble from Android 17's scan allowance
   (`scan_allowance_throttling_enabled=true` on this phone; effect unmeasured). Hold a button for
   ten seconds once and look at what arrives: a continuous hold could be the reliable wake gesture.
2. Longer reference series for the remote (30 paced presses, app on screen, passive against
   active scan type), and the same in a session with the phone in a pocket.
3. Let the hourly test run for a day and read alarm lateness and outcome per Doze state.
4. Update the design document to the measurements, then design the spoken menu.
5. Media keys through `SET_MEDIA_KEY_LISTENER` (adb-grantable, slot was free on this phone).
6. Hermes: HTTP API server (port 8642, bearer key), not the WebSocket; reach it over WireGuard.
   See `docs/research/verify-hermes-api.md`.
7. First release: the owner decides when. The secrets are in place.
