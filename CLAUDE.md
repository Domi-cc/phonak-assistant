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

## State on 28 November 2026

**Worked on the device** with the build of 27 November 2026 (details and numbers: [`docs/measurements/2026-11-27.md`](docs/measurements/2026-11-27.md)):

- The sideloaded app holds the assistant role; the role survives updates.
- A double tap on the hearing aid starts the app with the phone locked, dark and in Doze. The display stays off.
- Voice link up in 0.5 to 0.8 s, recording from the hearing-aid microphone, playback into the aid.
- In a session opened by the tap, presses on the Shelly remote are received and **spoken** ("Taste 1 kurz"). The session ends after 30 s without a press, on a long press of button 4, or on a second tap; the app says "Beende".
- Unprompted announcements over the media channel; the hourly test announcement fired from Doze 48 ms late.

**Built on top of that, not yet on the phone** (the branch `background-listener`; how it came
about: "History of the background-listening increment" below). It compiles, lint reports no errors and
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

**Built after that, on 28 November 2026, not yet on the phone either** (the branch
`calendar-reminders`, on top of `background-listener`; design:
[`docs/design/kalender-ansagen.md`](docs/design/kalender-ansagen.md)). It compiles, lint reports no errors
and the core tests pass. When this was written nothing of it had run on a device; since then
the owner has reported on a reminder he heard (see "The owner's first word on the calendar
reminders" below; which build that was is inferred, not said), and nothing else of it is
confirmed:

- `calendar/CalendarReminders`: spoken reminders for appointments. Ten minutes before one begins
  the announcer says "In 10 Minuten: Zahnarzt" over the media channel. The appointments come
  from Android's calendar storage (`CalendarContract.Instances`, permission `READ_CALENDAR`),
  which holds every calendar synced to the phone: no Google API, no network. Off by default.
- One alarm for whatever has to happen next (`calendar/CalendarAlarmReceiver`). A new plan
  at process start, on that alarm, on a changed setting, and when the calendar storage reports a
  change. What is due, what waits, what is given up or was missed and what counts as announced
  is decided in `core/calendar/ReminderPlanner`, with tests.
- The section "Kalender" on the screen (switch, permission, list of calendars, next
  announcement, test button); `lara.CALENDAR` over adb and `calendar=` in `lara.STATUS`.
- **`announce/Announcer` changed with it, and that is a measured path:** it now watches the
  output from the chime to the last word and cuts an announcement off when the hearing aid
  goes away or another device takes the sound over. Before, it asked once, seconds before
  the speech, and Android would have played the rest of a sentence on the phone's speaker.
  The hourly announcement, "Ansage jetzt" and "Beende" after a tap run through the same code.

**Built after that, on the same day, not yet on the phone either** (the same branch, on top of
the commit tagged `apk/20`; why and what for: "What a sound channel does to the hearing aid"
below). It compiles, lint reports no errors and the core tests pass; nothing of it has run on a
device:

- `audio/AidChannels`: journals all day when a sound channel to the hearing aid is really open,
  whoever opened it (`aid_media_stream` for the media stream, `hfp_audio_state` for the voice
  link), with the time it was open and whether it was this app's. "Zähler heute" adds the
  times up.
- A safety net for the voice link: a link that is still up after its session is looked at
  again 3 s, 10 s and 30 s later and reported, in the journal (`aid_voice_link_stuck`) and
  with one vibration. It is stopped once more only if the session's own stop was not taken;
  by the Android source a second stop closes nothing otherwise. The decision is in
  `core/audio/StuckVoiceLink`, with tests.
- Two buttons for the ear test in the section "Starten": "Stille-Test (5 s)" and "Klingt
  wieder normal". A press in that section plays no click sound. The steps for the owner:
  [`docs/measurements/hoerprobe.md`](docs/measurements/hoerprobe.md).
- **`SessionService` and `Announcer` changed with it, and both are measured paths.**
  `SessionService` no longer writes `hfp_audio_state` itself (`AidChannels` does, all day) and
  hands a link that stays up to the follow-up; nothing inside a session changed its order or
  its timing, and `AudioLink` only gained a property that keeps what its stop returned. The
  recovery after a process death (`recoverAfterCrash`) is new: it asks for the stop for the
  selected hearing aid only (before: for every connected hands-free device), it stops
  nothing next to a session that runs or is on its way, in a phone call or while another
  hands-free device has its audio up, and without the hands-free service it keeps its flag
  and waits for the service. `Announcer` tells which announcement is playing (`playing`,
  `lastEnded`), has a second entry `playSilence`, and its chime is played by a helper that
  the silence test shares.

**The owner's first word on the calendar reminders.** He reports: "Terminerinnerung viel zu
leise". So a reminder has been heard in the hearing aid. Which build that was he did not say:
presumably the one tagged `apk/20`, the newest APK he has and the first with the reminders.
That is an inference from the tag, to be confirmed by him. Nothing else of that build is
confirmed here, and no journal of it has been read.

**Built in answer to that, not yet on the phone** (the same branch; why and how: "How loud an
announcement is" below). It compiles, lint reports no errors and the core tests pass; nothing
of it has run on a device:

- A section "Ansagen" on the screen. The **volume channel** of the announcements is a setting,
  and its default changed: chime and speech are now played as media and follow the phone's
  media volume. Before, they were played as assistant speech, and Android 17 keeps a volume of
  its own for that, which an app cannot set and which stood low on his phone. A slider in the
  real steps of the media volume, with a spoken sample ("Lautstärke Stufe 18") when it is let
  go. A switch "Eigene Lautstärke nur für Ansagen": the app sets its own step for the length
  of an announcement and puts the phone's value back. The silence in front of an announcement
  as a setting, for an ear test.
- `announce/AnnounceVolume` carries that out, and for the first time **the app writes a
  volume**: the phone's media volume, and only with that switch on or when the owner moves the
  slider. What it may touch, what it puts back, what it remembers until the value is back for
  good and when it looks again is decided in `core/announce/AnnounceVolumeRules`, with tests.
- "Erinnerung testen" in the section "Kalender", and `lara.REHEARSE` over adb: the reminder
  for the next appointment exactly as it sounds ten minutes before it, or with the title
  "Beispieltermin" if there is none to name.
- New journal lines that measure what the source could not tell: `announce_volume`,
  `announce_volume_mid`, `announce_volume_seen`, `announce_volume_restore`, `volume_state`,
  `volume_curve`, `announce_setting`, `volume_slider`.
- **`announce/Announcer` changed with it, and that is a measured path.** What an announcement
  is played with is chosen per announcement, and the default is another usage than the one
  that was heard on the phone, with a flag that keeps other apps from recording it. The
  volume step sits between the last check of the output and the request for the audio focus,
  the guard lets `AnnounceVolume` read the volume at each of its looks, the restore is in the
  block that ends every announcement, and the wake lock lasts 35 s at most instead of 25.
- The steps for the owner: [`docs/measurements/lautstaerke.md`](docs/measurements/lautstaerke.md).

**Not built:** media keys, spoken menu, Hermes connection, speech recognition.

## Decisions the owner made

- Native Android, Kotlin. Maximum debuggability, no artificial limits on platform access.
- One real app that starts as a measurement app, not throw-away prototypes.
- README in English with related projects for other hearing-aid wearers; app texts and the design document in German.
- All sound goes to the Phonak only. **Never** to a car kit, another headset or the phone speaker. He stressed this.
- **The app must not get in the way of the hearing aid's own job**, amplifying the surroundings. His words, on 28 November 2026: "Bitte beachten: die App soll nicht die eigtl Funktion des Hörgerätes stoppen. Kannst du prüfen ob das passiert? Z.b. weil der Soundkanal die Hörverstärkung stoppt oder sowas?" That is the decision. What the agents derived from it, and he has not confirmed: a sound channel to the hearing aid is opened only for as long as something is to be heard or said, it is never kept warm, and whatever opens one is weighed against the hearing time it costs. Today's sessions do not keep to the first two, and that is known: a session holds the voice link open with silence for the 30 s it waits for the next press (rule 5 is why it plays silence; how long it waits is his to decide). Change nothing on the strength of the derived rules without asking him. What is known about the cost, and what is still his to decide: "What a sound channel does to the hearing aid".
- Presses on the remote are announced in words, not with tones. Every normal end of a session says "Beende".
- The count session (running numbers instead of words) is a measurement tool he did not ask for. It stays available for series, from the screen and over adb, but it is never what a tap or a press starts by default.
- The hearing aid is chosen once, by him, on the screen; the remote is searched and picked on the screen. Addresses are shown shortened there.
- A spoken reminder ten minutes before an appointment: "In 10 Minuten: <Titel>". "Erstmal einfach": one fixed lead time, nothing to configure. The appointments come from the calendars on the phone (Google Calendar and whatever else is synced there), and which calendars are announced can be ticked on the screen.
- **Announcements have to be loud enough, and how loud is his to set.** His words:
  "Terminerinnerung viel zu leise, wir brauchen die Möglichkeit diese Durchsagen von der
  Lautstärke anzupassen", and he asked for "ein Testbutton um die nächste Eventansage zu
  simulieren". That is the request. (He did not name the build he heard; see the state
  above.) How it was answered (the volume channel, the app's own
  step with its restore, the sample, the button "Erinnerung testen") was decided by the
  agents from the research; he has not seen it yet. "How loud an announcement is" has it.
- **Titles of appointments never go into the journal**, and neither do descriptions, locations, names of calendars or account names (those are e-mail addresses). He shares the journal for analysis, and it also goes to logcat. Counts, ids, times and lengths only; the same holds for adb answers and for exception texts. On the phone's own screen they may be shown.
- The signing key lives in GitHub Actions secrets; GitHub builds the signed APKs. The key is not copied to other machines.
- The app shows its build number, and every APK he gets has a higher one than the last. He tests without a computer next to him and names the build when he reports. How that is kept: "Build, install, release" below.
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
  It is the build number the app shows at the top of its screen ("Build 18 · 0.1.0-dev · 9ff1cad").
  The journal carries it as `versionCode`, with `commit` and `uncommitted`, in `process_start` and
  in the first line of every day file (`journal_day`).
- **Every APK the owner gets has a higher build number than the one before.** The number only
  follows the commit count, so this is a rule for whoever builds:
  - Commit, then build. A build from uncommitted changes shows "Build 18+ … geändert" and is not
    handed over. One APK per commit (a build with `-Plara.targetSdk=37` says so in its line).
  - When an APK goes to him, tag its commit `apk/<number>` and push the tag. `git tag -l 'apk/*'`
    is the record of what he has had. The next APK is built from a commit that contains the
    newest of these tags; then its number is higher.
  - A commit with an `apk/` tag is final: no amend, no rebase, no squash. A correction is a new commit.
  - Merge pull requests with a merge commit. A squash lowers the commit count, and Android
    refuses to install a lower `versionCode` over a higher one.
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
- The app can be driven over adb (start a session, change scan settings and the settings of the
  announcements, write a marker, simulate a press, ask for the status, ask for a new plan of the
  calendar reminders, rehearse a reminder):
  [`docs/adb-control.md`](docs/adb-control.md). Not tried on the phone yet.
- Useful system tags: `HeadsetService`, `HeadsetStateMachine`, `ActivityTaskManager`,
  `AS.AudioDeviceBroker`, `BtScan.ScanManager`. More commands: README, "Debugging toolkit".
- An update restarts the app. Do not install while the owner is in the middle of a test, and not
  around the full hour while the hourly announcement test is on.
- Things only the owner can do: choose the app as digital assistant, pick the hearing aid in the
  app, tap once while unlocked and choose the app with "Always".

## Layout

| Path | What |
|---|---|
| `core/` | pure Kotlin, 471 JVM tests: build label, journal format and names, BTHome parser, press tracking with loss counting, input model, routing (`Routing`, `WaitingInputs`), count logic, scan settings and start budget, adb parameters, day statistics with the channel times and their wording (`DayStats`, `ChannelTexts`), what a look at a voice link that stayed up does (`audio/StuckVoiceLink`), WAV reading, resampling, silence trimming, the calendar reminders (`calendar`: `ReminderPlanner`, `CalendarChoice`, the spoken lines in `CalendarTexts`), the loudness of announcements (`announce`: the settings, the rules for the media volume around an announcement in `AnnounceVolumeRules`, the lines of the screen in `AnnounceTexts`) |
| `app/…/trigger` | the invisible activity for the hearing-aid tap; hands the voice button of other devices on |
| `app/…/assistant` | the three assistant services; `onReady` starts the listener and warms the phrases |
| `app/…/input` | `InputRouter` (the only caller of `SessionService.start`), `TapStore` |
| `app/…/audio` | `AudioLink` (the voice link), `SessionService`, `SessionPrograms`, `EchoProgram`, `CountProgram`, `HearingAidStore`, tones, `AidChannels` (when a sound channel to the hearing aid is really open; the follow-up of a voice link that stays up) |
| `app/…/shelly` | `ShellyStore`, `ListenerService` with `RemoteScan`, `ListenerStarter` and `RescueScanReceiver`, `ShellyIntake` (packets to presses to the input bus), `ForegroundScan` (search and live test), `RemoteProgram`, phrases, tone fallback |
| `app/…/speech` | `PromptCache` with `SpeechEngine`, the phrase lists (`Prompts`), `CountSounds` |
| `app/…/announce` | unprompted announcements over the media channel, with the guard that cuts one off when its output changes; hourly test; the silence test; `AnnounceVolume` and `AnnounceVolumeStore` (how loud an announcement is: the settings, the phone's media volume around an announcement, the journal lines about Android's volumes) |
| `app/…/calendar` | `CalendarReminders` (plans, announces, owns the one alarm and the observer), `CalendarReader` (the phone's calendar storage), `CalendarStore` (switch, choice of calendars, what was announced), `CalendarAlarmReceiver` |
| `app/…/control` | `AdbReceiver` |
| `app/…/journal`, `app/…/ui` | journal, day counters, sharing; setup checklist and the sections of the one screen |
| `docs/design/` | design document for version 0.1 (German); its "Nachtrag" overrides the older text. `kalender-ansagen.md`: the calendar reminders |
| `docs/measurements/` | what was measured on the device. `hoerprobe.md`: the ear test, step by step, for the owner (German). `lautstaerke.md`: the loudness of the announcements, step by step, for the owner (German) |
| `docs/research/` | AI research and verification reports; leads, not facts. `hearing-function.md`: what a sound channel does to the hearing aid, with every source. `announce-volume.md`: what decides the loudness of an announcement, with every source |
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
  fall back to the virtual call (rule 4). Not measured yet. If the audio is still up after
  the 1.5 s (`session_link_down down=false`), the service is freed all the same and the link
  goes to the follow-up in `AidChannels`, outside the session.
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

## How the calendar reminders fit in

Worth knowing before changing anything there:

- **One plan, one alarm.** `CalendarReminders.requestPlan` reads the calendars, announces what
  is due and sets the one alarm for the next thing that has to happen: a reminder, a new try
  while a session or a phone call runs (every 20 s), or a look into the calendar, which comes
  after six hours at the latest also when a reminder is planned further ahead. All of these
  are exact alarms; only the look into a calendar with nothing ahead is inexact. Plans run
  one after the other; the alarm of the newest stands.
- **The planner decides, the app carries out.** `ReminderPlanner.plan` gets what the calendar
  lists, what is done with and what waited, and answers with what to announce, what waits on,
  what is given up, what was missed and when to wake. `ReminderPlan.settled` is remembered
  (`CalendarStore.markHandled`) before anything is spoken. Change a rule there, with a test.
- **A failed read does not move the alarm.** A plan that cannot read the calendar sets the
  alarm again for the time the last good plan chose, or for a new try to read before that
  (after one minute, then twice as long each time, six hours at most:
  `ReminderPlanner.retryAfterFailure`). A calendar storage that does not answer is told
  after 3 s to drop the question (`CalendarReader`): one read that hangs would otherwise
  hold up every later plan.
- **Nothing audible except through `Announcer.announce`.** If the hearing aid is not the only
  output the announcer skips and journals that, and the appointment still counts as announced:
  no retry, no vibration, no other way out. The announcer watches the output until the last
  word (`OutputGuard` in `Announcer`: the notice that an output is about to go, the reports
  of audio devices, and a question every 200 ms) and stops the speech engine when the sound
  is no longer the hearing aid's alone. None of the three signals has been tried on the phone.
- **Once per occurrence.** An occurrence is remembered (event id and begin) before the
  announcer is called; a moved appointment has a new begin and is announced again. One case
  is said twice: a single occurrence of a series that is changed after its reminder, because
  the calendar storage then lists it as a new event with the old begin
  (`core/calendar/CalendarInstance.kt`). Left as it is; the design document says so.
- **Not into a session, not into a phone call.** While `SessionService.busy`, or while the
  audio mode is one of a call (`InputRouter.isCall`, ringing included), the reminder waits and
  is given up when the appointment begins (`calendar_waiting`, `calendar_gave_up`). The same
  question is handed to the announcer (`stopIf`), so a session that starts while a reminder is
  spoken cuts it off, and a second reminder that waited behind the first is skipped rather
  than spoken into the session. A reminder that waited is released one second after the
  session was seen to be over, because Android needs a moment to send media to the hearing
  aid again. The feature only reads the flag and the mode: it touches neither the voice link
  nor the session service nor the remote's scan. **The phone call is not something the owner
  asked for.** It came out of the review, on the model of the router; tell him, and take it
  out of `CalendarReminders.holdBack` if he wants the reminder dropped instead.
- **A reminder that did not come leaves a line.** An appointment that the plan before had
  ahead of it and that has begun without being announced or given up is journaled once as
  `calendar_missed` and remembered. The first plan of a process has no plan before it and
  counts everything that began within the last hour. An appointment entered after its begin
  is not missed. `calendar_plan` counts what it left out by reason (`allDay`, `cancelled`,
  `declined`, `unselected`). With these the journal can tell "never seen" from "seen too late".
- **Choices are forgotten with their calendar.** Android numbers a new calendar one above the
  highest number in use, so the number of a removed calendar comes back. Every plan drops the
  ticks of calendars that are gone (`calendar_selection_forgotten`).
- **Titles stay out of everything that is written.** `CalendarInstance` and `Reminder` leave
  them out of `toString`; `PhoneCalendar` and `CalendarStatus` are no data classes for the same
  reason; the announcer is called with `logText = false` and journals `textLength`; exceptions
  are journaled by class name, without their message. Keep to that with every new journal line.
- **"Erinnerung testen" is no reminder.** `CalendarReminders.rehearse` speaks
  `CalendarTexts.rehearsal`: the sentence of a reminder ten minutes ahead, with the titles of
  the next appointment in the window, through the same call of the announcer as a real one
  (`logText = false`, the same `stopIf`). Without an appointment to name it says
  "In 10 Minuten: Beispieltermin": while the reminders are off (the calendar is not touched
  then), without the permission, when the calendar cannot be read, and when the next day
  holds nothing. It plans nothing and remembers nothing. Its journal line is `calendar_test`
  with `kind=rehearsal`, `events`, `sample`, `sampleWhy` and `textLength`; the announcer's
  reason is `calendar_rehearsal`. "Nächsten Termin jetzt ansagen" is unchanged.
- **Off means off.** While "Termine ansagen" is off the app does not touch the calendar
  storage. The observer for changes is registered only while the feature is on and the
  permission is held: Android refuses the registration with a `SecurityException` otherwise
  (read from the Android source, not tried).

## What a sound channel does to the hearing aid

The owner asked on 28 November 2026 whether the app can stop what the hearing aid is there
for, for example because a sound channel stops the amplification. That was checked from three
sides (the app's code, Phonak's documents, the Android 17 source), and each side was checked
again by a second reader. The full report with every source:
[`docs/research/hearing-function.md`](docs/research/hearing-function.md). Nothing in it was
measured and nobody has listened yet. This is the short form; every statement says where it
comes from.

**What is known**

- *Code.* The app has no command for the aid's amplification and sets no audio mode. All it
  does to the aid is open one of two sound channels: the media channel (A2DP) for an
  announcement, the voice link (hands-free) for a session. When this was checked it set no
  volume either. Since then it can set one, the phone's media volume, which is a gain in the
  phone and no command to the aid: "How loud an announcement is".
- *Phonak.* Every channel makes the aid leave its hearing program for a streaming program
  and return afterwards (knowledge base, not specific to the EON). The names of the programs
  are from the EON's product information: "Media speech + mic" and "Media music + mic" for
  the media channel, "Phone call + mic" for a call over the hands-free link. Knowledge base
  again: "Hearing aid microphones will stay on by default while receiving streaming signals.
  The balance between hearing aid microphones and streaming signals is customizable." Any
  sound of the phone does the same, however short.
- *Not in any Phonak document.* How much quieter the surroundings are in those programs: it
  is a setting of the individual fitting with no published default. How long the aid takes
  to come back. Which program a voice-recognition link without a phone call selects.
- *Forum reports about predecessor models.* Wearers describe a clear change of the
  surroundings while the streaming program is active: mostly a dip, in one case for 7 to
  8 s after a single key click on the phone, and for some the opposite, surroundings that
  get louder. A hint, not a fact about this aid.
- *Android 17 source.* Media channel: Android starts the Bluetooth stream at the first
  write, silence included, and suspends it 3 s after the last track has ended (AudioFlinger's
  standby, forced to at least 3 s for A2DP). For ordinary PCM playback there is no
  supported way to shorten that; whether another kind of output would close sooner on this
  phone is unknown (hearing-function.md, A3.a). One announcement therefore holds the stream
  for about (`announce_end` minus `announce_start`) + 3 s, roughly 6 to 7 s with the lead-in
  of 0.45 s that every build so far had. Since the lead-in is a setting, 1.5 s or 3 s add
  about 1 s or 2.5 s to that for every announcement (`leadInMs` in `announce_start`). The
  phone's own sounds (notification, touch and lock sounds) open the same stream with the
  same 3 s.
- *Android 17 source (`A2dpStateMachine`).* Every time a device's media profile connects,
  Android reports "not playing" for it, with "playing" as the state before, although no
  stream was open.
- *Code.* Voice link: held open with silence for the whole session (rule 5). That is about
  33 to 35 s for a remote session that runs out without a press, 30 s of it the idle wait,
  and about 13 s for an echo test; every press starts the 30 s anew, and a second tap or a
  long press of button 4 ends a session earlier. A single press on the pocket remote opens
  a session by default. This is the app's largest share.
- *Android 17 source, inferred for this phone.* When the voice link closes, the session's
  output is still awake and may be re-routed to the aid's media device. That would add a
  silent media stream of up to 3 s after every session.
- *Android 17 source (`HeadsetService`, `HeadsetStateMachine`).* `stopVoiceRecognition`
  closes the audio only while Android has a recognition marked as started, and every stop
  that gets that far clears the mark. Otherwise it closes nothing, sends the hearing aid an
  error result, and takes away the request of a tap that is waiting for its answer. Asked
  for a device that is not the active hands-free device, it acts on the active one. While
  hands-free audio is up Android refuses a tap and every start of a recognition. A start
  that finds an earlier recognition still marked ends that one and is refused itself. A
  phone call that comes in ends a started recognition. When Android's attempt to close the
  audio fails, or has not come through after 30 s, it reports "connected" a second time,
  with "connected" as the state before; whether anything tries again after that was not
  established. An attempt to open the audio that is refused or fails at once is reported
  as "disconnected" with "disconnected" before.
- *Unmeasured, all of it.* How long the channels are really open on this phone, and what
  this aid with its fitting does meanwhile. The first the journal can show now. The second
  only the ear can tell.

**The instrument** (built on 28 November 2026, not yet on the phone)

- `audio/AidChannels` journals what the Bluetooth stack reports for the selected hearing
  aid, all day, with one receiver that lives as long as the process:
  - `aid_media_stream` with `state=playing` or `state=stopped`: the media stream was really
    started or suspended, for sounds of any app. `stopped` carries `onMs`, how long it was
    open. `announcing=true` (with `announceReason`) means that an announcement of this app
    was playing or had ended within the last 4 s: the app's share. `announcing=false` is a
    sound of the phone or of another app.
  - `aid_media_stream` with `state=not_playing` is no stream. Android reports "not playing"
    every time the aid's media profile connects (the aid is switched on, comes back into
    range or leaves its charger), and this is the word for such a report with no `playing`
    before it. No counter reads it. A stream that was playing already when the process
    started ends with the same line, and its time is in no number.
  - `hfp_audio_state` with `state=connecting`, `connected` or `disconnected`: the voice link.
    `session` says whether a session of this app held it, `mode` is the phone's audio mode
    (a phone call reads `in_call`), and `disconnected` carries `onMs`. It also carries
    `callMs` if the phone went into a call while the link was up or coming up: the part of
    `onMs` after that. A call that comes in during a session may take the session's link
    over without a line in between, and that part is not the session's. A second
    `connected` with `repeated=true` means that Android did not get the audio closed;
    `onMs` counts from the first. `state=not_connected` is no link: Android's report of an
    attempt at the audio that was refused or failed at once.
  - `aid_channels_watching`: from this line on the reports arrive. Before it, or after
    `aid_channels_no_permission`, no line does not mean no channel. The line is written
    once per process and a process lives for days, so the first line of every later day
    file, `journal_day`, says it again as `aidChannelsWatching=true` or `false`.
    `aid_channels_at_start` says whether the voice link was up when the process started;
    for the media stream that is unknown until its first report.
- How to read it:
  - Android's tail after an announcement: the time of `aid_media_stream state=stopped` minus
    the time of `announce_end`. Expected: about 3 s.
  - What one announcement costs: `onMs` of the `stopped` line with `announcing=true`.
  - Whether a session is followed by a media stream: an `aid_media_stream state=playing`
    with `announcing=false` in the seconds after `hfp_audio_state state=disconnected`. Only
    with the phone's own sounds off (the last point of this list): a touch on the screen in
    those seconds reads the same.
  - The aid's own way back: the time of `marker text=surroundings_normal` minus the time of
    the `stopped` or `disconnected` line before it.
  - Whether the aid reacts to the stream itself or only to sound in it: what the owner
    heard in the silence test, next to `announce_start silence=true` and the
    `aid_media_stream` lines around it. A test counts only if its `playing` line comes
    after `announce_start silence=true`: a stream that was open before had a sound in it.
  - **The phone's own sounds open the same stream, a touch on the screen included.**
    Android plays a click for a press on a button (with Compose 1.13 for every clickable;
    "tap and click sounds" are on by default, and not played in vibrate or silent mode).
    Typing, notifications and the lock sound do the same. Each one reads
    `aid_media_stream ... announcing=false`, or keeps the stream of an announcement open
    for longer. The section "Starten" plays no click for its own buttons
    (`SoundEffectOnInteraction`, read from the Compose library and not tried on the
    phone); every other touch in the app still does. For the ear test the phone is put
    on vibrate.
- "Zähler heute" shows how often and how long each channel was open today: "Musik-Kanal"
  split into "App" and "anderes", "Sprach-Kanal" into "Sitzungen" and "anderes" (phone
  calls, the `callMs` of a session's link included). A channel is counted at the line that
  closes it (`core/stats/DayStats`); a closing line without `onMs` is an opening "ohne
  Dauer". One such line gets its length all the same: the `disconnected` of a voice link
  that outlived its process. The process died in a session, the new one did not see the
  link come up, so its line has no `onMs` and reads `session=false`. The counter takes the
  length from the clocks of that line and of the dead process's `connected` line, and the
  session from the dead process's lines.
- Two buttons in the section "Starten". "Stille-Test (5 s)" plays five seconds of pure
  silence over the media channel, through the announcer's checks and its guard, with no
  chime, no speech engine and no request for the audio focus (`announce_start` and
  `announce_end` with `reason=screen_silence` and `silence=true`). With Android's 3 s the
  stream is open for about 8 s. "Klingt wieder normal" writes
  `marker text=surroundings_normal`; the owner presses it the moment the room sounds as
  before.
- The instrument sees what the phone sends. What the aid does with it (which program, how
  loud the surroundings) it cannot see.

**The safety net for the voice link**

- A session whose audio is still up when its wait runs out (`session_link_down down=false`)
  hands the link to `AidChannels.followStuckLink`, which looks again 3 s, 10 s and 30 s
  later. A look ends the follow-up with `aid_voice_link_follow_up end=...` if a new session
  is busy (`new_session`), if the phone is in a call or ringing (`phone_call`), if the link
  was reported down at any moment since the session began to close (`went_down`), if it
  is down now (`is_down`), or if the reports do not show that it came up while a session
  was busy (`not_the_sessions`: another app's audio that was on the aid before the session
  and is there still). The follow-up of a later session takes its place (`replaced`), and
  it does not start at all without the reports of the instrument (`not_watching`).
  Otherwise the look writes `aid_voice_link_stuck`, and the first such look of a follow-up
  vibrates. A third look that still finds the link up is followed by
  `aid_voice_link_gave_up`. **It never touches a link that the session did not open**; that
  is what the reasons to end are for. The rule is in `core/audio/StuckVoiceLink`: change it
  there, with a test.
- **Mostly it can only report.** By the Android source (above) a stop closes the audio only
  while Android has a recognition marked as started, and the session's own stop has cleared
  that mark if Android took it. So a follow-up calls `stopVoiceRecognition` once at most, at
  its first look that finds the link stuck, and only if the session's stop was not taken or
  never made (`voiceRecognitionStopped` false or null in `session_end`;
  `aid_voice_link_stuck action=stop_again`). In every other case the line reads
  `action=report_only`: an ordinary app has no stronger call. By the same source the one
  stop cannot take away a waiting tap or another app's recognition, because Android
  accepts neither while the audio is up.
- A look runs on the main thread from its questions to the stop, and so does the recovery
  after a process death (`SessionService.recoverAfterCrash`). Sessions are asked for,
  accepted and freed on that thread, so neither can stop the link of a session that has
  just begun.
- The recovery asks for one stop, for the selected hearing aid, when the process before
  left its flag behind (`session_recovered_after_crash`, with `voiceLink` as found). It
  stops nothing and says why in `skipped`: next to a session that runs or is on its way
  (`session_running`, `session_starting`; the flag stays, it is that session's now), after
  a session of the new process has cleared the flag (`flag_cleared`), in a phone call
  (`phone_call`), and while another hands-free device has its audio up
  (`other_device_audio`: Android would apply the stop to that device).

**Decisions that are the owner's and still open.** He has not been asked yet, and the
behaviour is unchanged:

1. The 30 s a session waits for the next press, and whether a press of the remote should
   open a session at all, or only a short one until a second press shows that it was meant.
   This is the largest share of channel time, and a press can come from a pocket.
2. "Beende" over the media channel after a second tap. The aid has just closed the voice
   link, and the word opens the media channel: a second program switch. He asked for
   "Beende" at every end.
3. The hourly announcement at night.
4. The last sentence of README.md, "it does not change anything about how a hearing aid
   amplifies sound". With what is known now it says too much: the app has no command for
   the amplification, but every sound it plays makes the aid change program. A text that
   holds: "It has no command for a hearing aid's amplification. Like every sound a phone
   plays, its sound makes the aid switch to its streaming program for as long as it plays."
   The paragraph is his and was left as it is.

The ear test and a day of the journal's numbers come first: they say what each of the first
three costs.

## How loud an announcement is

The owner's report was "Terminerinnerung viel zu leise" (presumably about the build tagged
`apk/20`; he did not name it), with the request to make the loudness of the announcements
adjustable. A research pass, each report checked again by a
second reader, read the Android 17 source (tag `android-17.0.0_r1`) and captures from his
phone of 27 November 2026. The full report with every source:
[`docs/research/announce-volume.md`](docs/research/announce-volume.md). The phone runs a later
build than the public source, **nothing of what was built from it has run on the phone, and
nobody has listened yet.** This is the short form; every statement says where it comes from.
Three things below are not in that report: the Bluetooth stack's condition in point 3 and
the points 8 and 9 were read from the same source tag in the review of this build.

Two words that must not be mixed up. The **media channel** is the Bluetooth path (A2DP) every
announcement takes to the hearing aid, as opposed to the voice link. The **volume channel** is
new: which of Android's volumes an announcement is played under. Both volume channels use the
media channel.

**What is known**

1. *Android 17 source, Google's release notes, captures from the phone.* Android 17 gives
   sound that is played as assistant speech (`USAGE_ASSISTANT`) a volume of its own:
   `STREAM_ASSISTANT`, stream 11, steps 0 to 15, default 5, apart from the media volume.
   Every build before this one played chime and speech that way. On the phone that stream stood at
   5, then 13, then 8 of 15 on 27 November. By the curve in the public source those are about
   -28.5, -5 and -18.5 dB under full level. What moved it were the hearing aid's own
   hands-free volume reports during a voice session. An ordinary app
   cannot set stream 11: that takes `MANAGE_ASSISTANT_AUDIO`, a permission of the assistant
   role, and whether a sideloaded holder of the role is granted it is untested. On this
   phone the media volume has no effect on such an announcement. Not shown by any capture:
   that the phone's own, closed policy table puts this app's announcement under stream 11
   as the public source does. The journal now asks (`governing`, and `musicActive` in
   `announce_volume_mid`).
2. *Captures from the phone.* The hearing aid takes no absolute volume from the phone
   (AVRCP). So every volume on the phone is a digital gain in the phone's mixer, applied
   per track, by the curve of that track's volume group at that group's step. The highest
   step is 0 dB, and that is the loudest the phone can send.
3. *Android 17 source.* An app can set the media volume, `setStreamVolume(STREAM_MUSIC,
   step, 0)`, without a permission. The call acts on the output the media are routed to at
   the moment of the call; there is no call for a named device. The step is set inside the
   call, so reading it right afterwards tells whether the call was taken. It is dropped
   without an error by Android 17's background hardening (this app, as the selected
   assistant, is at the state of a bound foreground service, which passes; untried), by Do
   Not Disturb that mutes media, by a user restriction, and on devices with a fixed volume.
   A muted stream reads as 0, and writing another step unmutes it; the step under the mute
   stays and comes back with the unmute. The value is saved per kind of device after 500 ms
   and remembered per Bluetooth device by the Bluetooth stack, which hands it back to
   Android at the next connection. **The Bluetooth stack takes a new value only for the
   device that is its active media device at that moment**
   (`AvrcpTargetService`, on Android's report of a changed media volume). When the hearing
   aid is switched off or put into its charger, the stack lets go of it first and tells
   Android afterwards (`A2dpService.removeActiveDevice`); Android then sends its notice
   that the output is going and keeps the aid as the route for one more second
   (`BECOMING_NOISY_DELAY_MS`) if something plays or holds the audio focus. A write in that
   second is taken by Android, reads back, and is forgotten: at the next connection the
   Bluetooth stack hands back the value from before it.
4. *Android 17 source.* With flags 0 such a call shows no panel, plays no sound and does not
   wake the screen.
5. *Android 17 source, inferred and untried.* While a track of the media volume group plays,
   the phone's volume keys change the media volume, also with the display off. For a track
   played as assistant speech they do not: with the display off the keys only act on media.
6. *Android 17 source.* The gains of the speech engine and of an app's own track end at 1.0.
   Nothing in the public API makes a track louder than its volume allows.
7. *Unknown.* Whether the hearing aid fades a stream in while it changes into its streaming
   program. Wearers of earlier Phonak generations report 2 to 3 s for the change (forums).
   In the journals of 27 November the first word came about 1.1 s after the stream opened,
   and the whole announcement was over about 2.6 s later. Only the ear can tell.
8. *Android 17 source.* Sound played as media can be recorded by another app that the owner
   lets capture the screen with its sound (a screen recorder, a screen share in a video
   call); sound played as assistant speech cannot. The copy for such a capture is a second
   output that the question for the route does not show (`AudioPolicyManager`: "secondary
   not used for audible audio routing, only used for duplication for playback capture"). A
   track that is built with `ALLOW_CAPTURE_BY_NONE` is left out of it. The flag takes no
   part in choosing the route or the volume.
9. *Android 17 source.* Under a request for the audio focus that may duck, Android turns the
   music of other apps down by about 35 dB if the request is made as an assistant, and by
   about 14 dB for every other usage (`PlaybackActivityMonitor`).

**What was built from it** (28 November 2026, not yet on the phone)

- **The volume channel is a setting, "Lautstärke-Kanal", and its default is "Medien".** Chime,
  silence test and speech are then played with `USAGE_MEDIA` and follow the phone's media
  volume, which the owner sets with the ordinary slider and with the keys (point 5: also
  during an announcement, in the pocket) and which the hearing aid does not rewrite.
  "Assistent" is `USAGE_ASSISTANT`, exactly what the builds before did: a way back without
  a new build, and something to compare with. In that channel the app never writes a volume.
  The choice is read when it is an announcement's turn, and everything of that announcement
  uses the same attributes: both tracks, the speech engine (set before every `speak`), the
  request for the audio focus, and every question about the output.
- **Played as media, an announcement may not be recorded by anybody** (point 8): the media
  attributes carry `ALLOW_CAPTURE_BY_NONE`, for the app's own track and, because the
  attributes travel to the speech engine, for the speech. A reminder speaks the title of an
  appointment, and the guard could not see a recording. The attributes of the channel
  "Assistent" are left exactly as the builds before had them; Android never lets that usage
  be recorded. Untried on the phone: that the speech engine keeps the flag, and that
  `governing` and `routeTypes` in `announce_volume` read the same as without it.
- **Played as media, an announcement goes where the phone's media go.** With "Dieses
  Smartphone" chosen in Android's output switcher the announcer finds the wrong output and
  skips (`announce_skipped why=output_is_not_the_hearing_aid`, with `channel=media`). That
  nothing is ever played on anything but the selected hearing aid stands above everything
  here.
- **Two ways to set the loudness, one switch: "Eigene Lautstärke nur für Ansagen".**
  - *Off (the default).* Announcements play at the phone's media volume for the hearing aid
    as it is. The app writes nothing around an announcement. The slider on the screen shows
    that volume and sets it, for good, like the volume keys (`volume_slider`); it can be
    moved only while the hearing aid alone is the media output, the stream is not muted and
    the volume is not fixed.
  - *On.* The slider holds a step of the app's own, from 1 to Android's maximum, stored with
    the maximum it was chosen against and rescaled if that ever differs. Around each
    announcement the app sets that step and puts the phone's value back.
- **The volume step of an announcement** (`AnnounceVolume.before`, the rules in
  `core/announce/AnnounceVolumeRules`): one slice of the main thread without a suspension,
  after the announcer's second check of the output and before the audio focus, so that the
  value stands before the lead-in silence opens the stream. The media output must be the
  selected hearing aid's A2DP device alone; that is asked right before every read and
  every write and never judged from a volume. The app does not touch the volume, and the
  announcement plays at the phone's value, in the assistant channel, when Android does not
  put the announcement under the media volume, with a fixed volume, with a muted stream or
  step 0, in a call or any audio mode but the normal one, while other media play (they
  would be lifted with the announcement), and for the silence test. Otherwise: the phone's
  value is written into a note in storage, with the step that is about to be set, **before**
  the write; then one write, read back. A value that reads back as wanted is accepted.
  Anything else is a refusal, classed by what the app can see (`dnd`, `user_restriction`,
  `unknown`; `other` for a third value, which is noted and put back like a step of the
  app's; `muted` if the stream reads 0 after the write), with one attempt and no second.
- **While the announcement plays** the guard's look at the output, five times a second, is
  also a look at the volume (`AnnounceVolume.watch`): a value the volume keys changed
  (point 5) is written into the note at once (`announce_volume_seen`), so that the note
  names what stands also if the route is lost before the restore.
- **The restore** (`AnnounceVolume.after`), for every way an announcement can end (the last
  word, the guard, the timeout, an exception, a cancellation), before the audio focus is
  given back and while the wake lock holds. Half a second after the last word if the
  announcement was spoken to its end; at once if it was cut off, because then nothing is
  on its way and whatever cut it is about to take the route. The route first, and without
  the hearing aid nothing is written and the note stays (`deferred_route`). **An
  announcement that ended because Android said the output is going
  (`output_going_away`) counts as a lost route although the aid still reads as the route**
  (`deferred_going_away`): by point 3 a write in that second would be forgotten. A muted
  stream is left muted, and the note waits for the unmute (`left_muted`). A value that is
  no longer the one the app set was changed with the volume keys during the announcement
  and **becomes the new step** (`adopted`), also when the restore itself is put off. The
  phone's value is put back and read back.
- **A restore that reads back is not yet back for good** (point 3). The note is not cleared
  but marked as restored, with the value that stood before, and a look two seconds later
  (`volume_state trigger=restore_check`) clears it if the hearing aid is still the media
  output (`leftover=confirmed`): an aid that was on its way out at the write is gone by
  then. If it is gone, the note waits, and when the aid comes back with the old step, the
  owner's value is put back once more, this time while the Bluetooth stack listens. The
  same marking and the same second look follow every put-back by a look.
- **A note that waits** (the process died, the hearing aid went away, a session or a call
  took the route, media were muted, or Android did not take the restore) is looked at only
  while the hearing aid alone is the media output and media are not muted. If the value is
  the one the note names as set, the owner's value is put back; if the owner's value
  stands, a marked note is cleared; if it is another value, the note is dropped. With any
  other output nothing is compared and nothing written. The occasions: **every
  announcement** (one that sets its own step uses the note itself; one that does not, with
  the switch off, in the other channel, while other media play or for the silence test,
  looks at it in the same moment and puts the owner's value back before it plays), the
  start of the app, the app coming onto the screen, the section "Ansagen" being opened, a
  changed setting, and, only while a note waits, about a second after any audio device
  came or went, after the audio mode went back to normal, after the device for calls
  changed, after a voice link went down (the end of a session) and after Do Not Disturb
  changed. The route comes back from a session or a call without the aid's media device
  being added, which is why its arrival alone is not enough. For these looks the phone is
  kept awake for the second or two they wait.
- **The app never** calls `adjustStreamVolume` or `setStreamMute`, never writes a stream
  other than media, never passes flags other than 0, never writes step 0, and never writes
  stream 11. `MANAGE_ASSISTANT_AUDIO` is declared in the manifest for one reason: so that
  `volume_state` tells whether Android grants it to this app.
- **The sample.** Letting go of the slider plays "Lautstärke Stufe 18" through
  `Announcer.announce` with the reason `volume_sample`: the same checks, the same write,
  the same guard, the same restore. Only the newest sample plays. The line under the
  slider says what happened when the sample is over, not before: "Gespielt mit Stufe 18",
  that Android did not take the change and why, or "Abgebrochen: …" if it was cut off. If
  Android does not put announcements under the media volume on this phone (`governing` is
  not 3), the status line and that line say so instead of naming a step.
- **The lead-in** is a setting for the ear test of point 7: the silence in front of the
  chime, 0.45 s (as before), 1.5 s or 3 s.
- **"Erinnerung testen"** in the section "Kalender" plays the reminder for the next
  appointment as it sounds ten minutes before it (`CalendarReminders.rehearse`).

**The limits**

- The highest step is the loudest the phone can send (point 2). If that is still too quiet,
  no volume helps. What is left then is the acoustician, who can raise the gain of the
  aid's streaming programs, or a digital boost in the app (playing the speech itself,
  normalised, or a loudness effect), which was deliberately left out of this build. The
  chime is at a quarter of full scale (`chimeAmplitude` in `announce_start`) and was not
  changed either.
- With the switch on, the phone's media volume really is raised for about four seconds per
  announcement. The Bluetooth stack remembers that value for the hearing aid at once, and
  Android saves it half a second later. The restore, the note and the look that confirms
  the restore are what stands between that and the owner's music at the loudness of an
  announcement.
- Between the question for the output and the write there is a gap that no app can close.
- Do Not Disturb that mutes media mutes the announcements, in both channels (by the source
  the assistant usage is muted with the media), and no setting of the app changes that. In
  the channel "Medien" the status line says that media are muted and that Do Not Disturb is
  on, without claiming which caused which. In the channel "Assistent" the app cannot see a
  mute; the line only says that Do Not Disturb is on.
- **Other apps' music is turned down less under an announcement in the channel "Medien"**
  (point 9): about 14 dB instead of the 35 dB the builds before got, because the request
  for the audio focus carries the announcement's attributes. With the switch on the app
  also leaves the volume alone while other media play. An announcement over music is
  therefore harder to follow than before. Kept as designed; to be judged by ear, against
  the channel "Assistent".
- **A longer lead-in costs hearing time.** With 3 s the hearing aid stays in its streaming
  program about 2.5 s longer for every announcement, the hourly one and every reminder, for
  as long as the setting stays. The longer values are for the ear test; afterwards the
  shortest that sounds right. The line on the screen says so.
- During the lead-in and the chime the guard's question for the output is answered by the
  app's own track, which is tied to the hearing aid (see the KDoc of `OutputGuard`): a
  change of the media output that leaves the aid connected is only seen when the chime is
  over. With a lead-in of 3 s that stretch is 3.3 s long.

**The instrument**

- `announce_start` now carries `channel`, `leadInMs` and `chimeAmplitude`.
- `announce_volume`, once per announcement, before the lead-in: `channel`; `governing` (the
  stream Android puts this announcement under: 3 is media, 11 the assistant volume);
  `setting` (`phone` or the step); `action` (`kept`: the switch is off; `set`; `already`;
  `skipped`; `refused`) and `why` (for `skipped`: `silence_test`, `assistant_channel`,
  `not_music_stream`, `route_not_aid`, `volume_fixed`, `muted`, `mode_not_normal`,
  `other_media`, `note_not_saved`, `error`; for `refused`: `dnd`, `user_restriction`,
  `unknown`, `other`, `muted`); `mRoute` (the media output as a word, see `route` below:
  the media values of the line are the hearing aid's only while it says `aid`, which in the
  channel "Assistent" it need not); `mBefore`, `mWanted`, `mGot`, `mMax`, `mMuted` for media;
  `aIndex`, `aMax` for stream 11; `mDb`, `aDb`, what those steps are worth in decibels on a
  Bluetooth media device by the phone's own curve; `zen` (Do Not Disturb), `mode`,
  `routeTypes`, `note` (`none`, `used`, `dropped`, `pending`), and, when the announcement
  looked at a note it did not use, `leftover` (the words of `volume_state`). If Android
  threw on the way, `exception` stands next to what was done.
- `announce_volume_mid`, half a second into the speech: `musicActive`, `mRoute`, `mNow`,
  `aNow`. With nothing else playing, `musicActive=true` means the speech really is under
  the media volume, `false` that it is not. This is the check of point 1 on the phone
  itself. A speech that is over within that half second leaves no such line.
- `announce_volume_seen`, while an announcement plays whose volume the app set: the media
  step changed (`mWas`, `mNow`), which is the volume keys. With its time it shows that a
  key press reached the announcement.
- `announce_volume_restore`, only for an announcement whose volume the app took into its
  care (`action` was `set` or `already`, or `refused` with `why=other`): `result`
  (`restored`, `adopted`, `left_muted`, `deferred_route`, `deferred_going_away`,
  `nothing_to_restore`, `restore_refused`, or `error` with `exception`), `mNow`, `mBack`,
  `waitedMs`, `note` (what stays in storage: `none`, `pending`, or `to_confirm` after a
  restore that read back), and for an adopted step `oldStep` and `newStep`.
- `volume_state` whenever the app looks at a note, whether there is one or not (`trigger`:
  `app_start`; `aid_route`, a second after the hearing aid's media device appeared, which
  also shows the value the Bluetooth stack handed back; `restore_check`, two seconds after
  a restore or a put-back; `going_check`, three seconds after an announcement that ended
  with `output_going_away`; `announcement`; `devices`, `mode`, `communication_device`,
  `voice_link`, `dnd`, each a second after that changed and only while a note waits;
  `screen`, when the app comes onto the screen; `section`, when the section "Ansagen" is
  opened; `setting`): the settings, `route` (`aid`, `other`, `none`, `no_aid_selected`),
  both volumes, `manageAssistantAudio` (`granted` or `denied`), the Android build, `note`
  (`none`, `pending`, `to_confirm`, with `noteBefore` and `noteSet`), and `leftover` (what
  became of it: `none`; `restored`, the owner's value was put back; `confirmed`, a restore
  held and the note is cleared; `dropped`; `waiting_route`; `waiting_unmute`;
  `waiting_check`, a restore too fresh to be confirmed; `restore_refused`; `announcing`
  while an announcement still looks after it itself; `error` with `exception`).
- `volume_curve`, once per Android build (remembered across updates of the app): the
  decibels of every step of both volumes for a Bluetooth media device. The numbers in
  point 1 are the public source's, not the phone's.
- `announce_setting` for every change of a setting, with who made it (`origin`: `screen`,
  `adb`, `keys` for an adopted step, `rescaled`), and `volume_slider` when the slider wrote
  the phone's media volume (`before`, `wanted`, `got`; `result` is `set`, `already`,
  `skipped` or `refused`, with `why`).
- `announce_volume_store_error` if storage did not take a note, and `volume_watch_error` if
  one of the watches for the looks could not be set up.
- None of these lines carries an address, the name of a device or a text.

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

1. **The loudness of the announcements, on the phone.** His request, and everything about it
   is read from the source and untried. The steps for him:
   [`docs/measurements/lautstaerke.md`](docs/measurements/lautstaerke.md). In this order, and
   with the journal next to it ("How loud an announcement is", "The instrument"):
   - *Is the lever the right one?* With the default settings and nothing else playing,
     "Ansage jetzt": `announce_volume` must read `channel=media governing=3`, and
     `announce_volume_mid` `musicActive=true`. If it reads `governing=11` or
     `musicActive=false`, this phone does not put the announcement under the media volume
     and the design does not hold: stop and report. Then the same in the channel "Assistent"
     (expected: `governing=11`, `musicActive=false`), to have the comparison.
   - *Does an announcement played as media come at all from the pocket?* The hourly
     announcement or a reminder with the phone locked, dark and in Doze, as the one played
     as assistant speech did on 27 November (`announce_end result=ok`, and heard). By the
     source it should; if it does not, the channel "Assistent" is the way back.
   - *What do the steps mean here?* `volume_state` (both volumes, and
     `manageAssistantAudio`: does the role bring the permission?) and `volume_curve` (the
     phone's real decibels per step) come by themselves at the first start.
   - *Does the phone's own slider do it?* Switch off, slider at a few steps: each sample
     audibly louder, `volume_slider result=set`. Is the highest step loud enough, in a quiet
     room and outdoors? That decides whether anything beyond a volume is needed.
   - *The app's own step.* Switch on: `announce_volume action=set` with `mGot` equal to
     `mWanted`, in every situation an announcement comes in: a sample from the screen, the
     hourly announcement and a reminder with the phone dark and locked, and in Doze. A
     `refused` with `why=unknown` there is the background hardening. After each one
     `announce_volume_restore result=restored note=to_confirm`, the last word as loud as the
     rest, the phone's media slider where it was, and two seconds later `volume_state
     trigger=restore_check leftover=confirmed`.
   - *The keys.* Volume up during an announcement, phone in the pocket:
     `announce_volume_seen` at the moment of the press, `announce_volume_restore
     result=adopted`, and the next announcement at the new step.
   - *Nothing is left raised when the aid goes.* Switch the hearing aid off in the middle of
     an announcement: nothing from the phone's speaker, `announce_end` with
     `output_going_away` or `output_lost`, `announce_volume_restore` with
     `deferred_going_away` or `deferred_route` and **no** `mBack` (nothing was written), the
     speaker's own volume untouched. After the aid is back: `volume_state trigger=aid_route`
     with `mIndex` at the announcement's step (the value the Bluetooth stack handed back)
     and `leftover=restored`, two seconds later `trigger=restore_check leftover=confirmed`,
     and the media volume for the aid as before. This is the check of point 3 of "What is
     known", which is read from the source: if the aid comes back at the owner's value
     without a put-back, the Bluetooth stack behaves differently here. Then once with the
     aid switched off right after the last word, and once with the app killed in the middle
     of an announcement (`trigger=app_start`).
   - *Nothing is left raised when a session takes the route.* Switch on, the hourly
     announcement or "Ansage jetzt", and tap the hearing aid while it speaks:
     `announce_end result=output_lost` or `session_busy`, `deferred_route`; after the
     session `volume_state trigger=voice_link leftover=restored` (or the "Beende" of that
     session uses the note itself: `announce_volume note=used`). Then start music: as loud
     as before the announcement.
   - *Other media.* With music playing: `action=skipped why=other_media`, and the music
     not louder. And by ear, in both channels: how well is the announcement understood over
     the music? In the channel "Medien" the music is turned down less (point 9).
   - *Do Not Disturb* with media muted: the status line says so, nothing is written.
   - *The lead-in.* A long announcement with 0.45 s, 1.5 s and 3 s in front: does the
     beginning sound quieter than the rest with the short one?
   - *The media output changed during the lead-in.* Lead-in 3 s, "Ansage jetzt", and
     during the silence choose "Dieses Smartphone" in Android's output switcher: listen at
     the phone. Nothing of the speech may come out of it; expected is `announce_end
     result=output_lost` before the first word. If a syllable is heard, the guard's blind
     stretch during the app's own track is real and has to be closed.
   - *No recording.* Start Android's screen recorder with device audio, play "Erinnerung
     testen", stop: the recording must be silent where the reminder was, in both channels.
     And `announce_volume` must still read `governing=3` and the hearing aid's type in
     `routeTypes` with the capture flag on the attributes.
   - "Erinnerung testen" with and without an appointment in the next day.

   The whole list of checks, also those that need no app (the audio balance in Android's
   accessibility settings, the aid's device type in the Bluetooth settings):
   `docs/research/announce-volume.md`, section 11.
2. **The ear test.** It answers the owner's question, and only he can do it:
   [`docs/measurements/hoerprobe.md`](docs/measurements/hoerprobe.md). Install the build and look
   for `aid_channels_watching` in the journal. Then, with the phone on vibrate (its own
   click and notification sounds open the same channel) and a radio at a steady level:
   "Ansage jetzt", "Stille-Test (5 s)" and a session, each followed by "Klingt wieder
   normal" the moment the room sounds as before; then "Heute teilen". How to read the journal: "What a
   sound channel does to the hearing aid" above. The same journal shows whether the reports
   about the media stream reach the app at all (read from the source, not tried). The open
   decisions listed there come after it. If the tap or the announcement does not work at
   all, the next step comes first. Do it at one fixed loudness: an announcement that is too
   quiet to follow says little about the surroundings.
3. **Check the ported build on the phone** before anything else is built on it.
   In this order: the tap still opens a remote session and "Taste 1 kurz" is still spoken
   (the remote is now heard through the listener's scan); `listener_start` and `scan_started`
   in the journal, the notification "Lara lauscht auf die Fernbedienung"; a press with the
   display on opens a session; the same with the phone locked and dark (expect
   `session_refused` if the assistant binding does not carry a microphone start from a scan
   callback: that is the open question of this increment); a second tap now says "Beende"
   over the media channel; a press during "Beende" opens the next session and its
   `session_voice_recognition_requested` says `accepted=true` (if not, the wait behind
   `session_link_down` is too short or looks at the wrong thing); `lara.STATUS` over adb.

   With the same install, the calendar reminders. They do not depend on the remote, so check
   them even if the points above make trouble. First the announcer, because every
   announcement now runs through its new guard: "Ansage jetzt" and "Beende" after a tap are
   still heard to the end (`announce_end result=ok`; a false alarm of the guard would read
   `output_lost`). Then switch on "Termine ansagen" in the section "Kalender" and grant the
   permission; the calendars are listed with the ticks the calendar app has; "Nächsten Termin
   jetzt ansagen" is heard. **Press that button again and switch the hearing aid off in the
   middle of the sentence: nothing may come out of the phone** (`announce_end` with
   `output_going_away` or `output_lost`). An appointment created twelve minutes ahead shows
   up as `calendar_plan reason=calendar_changed` and is announced ten minutes before it
   begins, with the phone locked and dark (`calendar_alarm` has the lateness of the alarm); a
   declined, a cancelled and an all-day appointment stay silent (`calendar_plan` counts
   them); a reminder during a phone call waits (`calendar_waiting why=phone_call`); a tap
   while a reminder is spoken cuts it off; nothing of a title is in the journal. The whole
   list: `docs/design/kalender-ansagen.md`, "Am Gerät noch zu prüfen".
4. Then measure: can a Shelly press open a session with the phone dark, after minutes and after
   hours? Expect trouble from Android 17's scan allowance
   (`scan_allowance_throttling_enabled=true` on this phone; effect unmeasured). Compare the idle
   scan modes and the rescue scan. Hold a button for ten seconds once and look at what arrives:
   a continuous hold could be the reliable wake gesture.
5. Longer reference series for the remote (30 paced presses, app on screen, passive against
   active scan type), and the same in a session with the phone in a pocket. The count session
   and the adb commands are made for this.
6. Let the hourly test run for a day and read alarm lateness and outcome per Doze state. The
   same day gives the phone's own share of channel time ("Zähler heute").
7. Update the design document to the measurements, then design the spoken menu.
8. Media keys through `SET_MEDIA_KEY_LISTENER` (adb-grantable, slot was free on this phone).
9. Hermes: HTTP API server (port 8642, bearer key), not the WebSocket; reach it over WireGuard.
   See `docs/research/verify-hermes-api.md`.
10. First release: the owner decides when. The secrets are in place.

Known gaps of the port, none of them urgent: the journal has no size limit and old day files
are never deleted; `shelly_packet` writes one line per advertisement on the main thread; a tap
that Android delivers while the service is still busy is turned down (with a vibration) and
not started afterwards; a session cannot hear the remote if the listener service is not
running and cannot be started; the router and the session programs have no unit tests of
their own (their decisions are tested in `core`).

Known gaps of the calendar reminders: a single occurrence of a series that is changed after
its reminder is announced a second time; a reminder that waits for a session is tried every
20 s, so one whose session ends in the last seconds before the appointment can be given up
all the same; of two reminders that fall due together the second is lost if a session starts
while the first is spoken (`announce_skipped why=session_busy`); what waited for the end of a
session when the process ended gets no `calendar_gave_up` line (it shows up as
`calendar_missed` if the process is back within the hour); the first plan after a process
start reports as missed also what it could never have announced (an appointment entered
after its begin while the process was gone); an announcement longer than 20 s is cut off
(`announce_end result=timeout`); everything in `app/…/calendar`, the section on the screen
and the announcer's guard have no unit tests of their own (the decisions are tested in
`core`, the calls into Android are not), and how long a read of the calendar takes has not
been measured.

Known gaps around the sound channels. Found in the audit of 28 November 2026 and
deliberately not fixed: `AudioLink.close` writes the closing phrase with a blocking write
that has no time limit, before `stopVoiceRecognition` (if the audio system ever stopped
taking samples the stop would not be reached; no way to trigger that was found);
announcements queue without limit and are not merged, each with its own warm-up and chime;
Android's assist gesture starts an echo test, a voice link with a recording, also when it is
made by accident; the tap activity is exported without a permission, so any app on the phone
can open a voice link to the selected hearing aid; a stop that finds nothing to stop (after a
second tap, in the recovery, and in the one stop of a follow-up) makes Android send the
hearing aid an error result, and whether the aid reacts to it is unknown.

Gaps of the safety net: a link that stays up although Android took the session's stop is
reported and nothing more, because nothing an ordinary app can call closes it; a recovery
that finds a session running or on its way steps aside, and if the dead process left a
recognition marked as started, that session's start is refused once and it runs over the
virtual call of rule 4 (`session_recovered_after_crash` with `skipped` and
`expect=session_start_refused_once`, then `session_voice_recognition_requested
accepted=false`); `SessionService` ends a session on the audio report of any hands-free
device, not only the hearing aid's (older than this change, behaviour unchanged; the journal
now says `session_interrupted why=hfp_audio_other_device`).

Gaps of the instrument itself: that the report about the media stream reaches an ordinary
app on this phone is read from the source and not tried, and without it `aid_media_stream`
never appears and the media counter stays at zero; whose a media stream is goes by the line
that closes it, so music into which an announcement falls counts as "anderes", a sound of
the phone that ends within a second after an announcement counts as "App", and one that
ends later (a notification, a touch on the screen) turns the whole stream, the announcement
included, into "anderes"; a media stream that was playing when the process started is in no
number, and its end reads `not_playing`; if the Bluetooth process dies while a stream plays,
no stop is reported, and the "not playing" at the aid's next connect closes that stream
with an `onMs` of hours; a media stream that Android opens by itself after a session, if
there is one, counts as "anderes" although a session caused it; that the phone's audio mode
stays `normal` during a session is read from the source and not measured, and with a call
mode there the counter would book sessions under "anderes" (`mode` on the session's
`hfp_audio_state` lines shows it); the length of a voice link that outlived its process is
taken from the clocks of two lines, which is wrong if that link went down and another came
up while no process was there to see it; the time of a line is the time the report reached
the app, and whether a report can be held back while the phone sleeps has not been tried
(the journal's clocks show it); a channel opened before midnight is booked whole on the day
it closes; `AidChannels`, the silence test and the changed recovery have no unit tests of
their own (the decisions of the follow-up and the counting are tested in `core`); the
journal gains two lines for every sound the phone plays into the hearing aid.

Known gaps of the loudness setting. **Nothing of it has run on the phone**: that an
announcement played as media is played from the background at all, that Android puts it
under the media volume there, that the app's write is
taken from the background, in Doze and from an alarm, that the volume keys reach an
announcement in the pocket, that half a second before the restore is enough, that the
report of the hearing aid's media device arrives and a second after it is late enough, that
the Bluetooth stack takes a new value only for its active device and that an aid on its way
out is gone from the route within two seconds, that the speech engine keeps the flag
against recording, all of that is read from the public source of another build. Between
the question for the output and the write there is a gap no app can close: a hearing aid
that goes away in exactly that moment gets its value changed on whatever output comes
next. The note: it is looked at when something tells the app that the route may be back
(the list is in "How loud an announcement is"), and nothing tells an app that the owner
unmuted media by hand or chose another output in Android's switcher, so after those two a
restore that was put off stays undone until the next announcement, the next look from the
screen or the next of the other occasions; a restore that Android refused is tried again
at those occasions and not by a timer; a note whose value somebody else happened to set
again is taken for the app's own, and that includes the two seconds after every restore in
which the note still stands; keys pressed in the last fifth of a second before a route is
lost are not seen, and then the step is not taken over and the note does not recognise the
value; every changed value during an announcement is taken for a key press; and a value
that Android bent to a third one is put back but not offered as a step. If the stream is
muted in the very moment of the app's write while a note from an earlier announcement is
in use, the app cannot tell which of two values stands under the mute and keeps the older
note. Other media are
recognised by one question right before the write (`isMusicActive`): music that starts
during an announcement plays at the announcement's step until the restore. An
announcement that follows another waits 1.2 s after the end of the first (`OWN_TAIL_MS`),
because Android counts a track that has just stopped as playing for a moment; whether
1.2 s is enough on the phone is not measured, and if it is not, the second one stays at
the phone's value (`why=other_media`). In the channel "Medien" other
apps' music is turned down by about 14 dB under an announcement instead of 35 dB. With the
switch on, a step
below the phone's value makes announcements quieter than the media. The slider of the switch-off mode writes the phone's volume for
whatever is the media output at the moment of the write; the route is asked right before.
Played as media, announcements depend on the phone's choice of a media output, and with
another output chosen there they are skipped, the reminders included; with media muted
they are silent, and only the journal and the status line say so. During the lead-in and
the chime the guard does not see a change of the media output that leaves the hearing aid
connected (3.3 s with the longest lead-in); the speech is stopped at the next look, which
by the timing of the engine is before its first word, but that is a race and not a
guarantee. The speech engine is
told what to play with before every announcement; that it follows from one announcement to
the next is not tried. The screen reads Android's volumes once a second on the main thread
while it is visible, and the tap that opens the section "Ansagen" still plays Android's
click (both left for the redesign of the screen). `AnnounceVolume`, its store, the section
on the screen and the changed `Announcer` have no unit tests of their own (the decisions are
tested in `core`); which reason "Erinnerung testen" journals for its sample title
(`sampleWhy`) is decided in `CalendarReminders` and has no test either.
The journal gains two lines per announcement with the switch off (`announce_volume`,
`announce_volume_mid`) and four with it on (also `announce_volume_restore` and the
`volume_state` of the look that confirms the restore).
