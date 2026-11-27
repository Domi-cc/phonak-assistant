# Related and adjacent open projects for "Lara im Ohr"

Research date: 2026-11-27. All repository metadata (licence, primary language, last push, stars) was read with
`gh api repos/<owner>/<repo>` on that day unless a line says otherwise. "Last push" is `pushed_at`.

Legend for claims: **[V]** verified (I saw it in the repository, in a primary source or in the API response; the
location is given), **[I]** inferred (reasoned from sources), **[U]** unknown / not checked.

Licence shorthand for an Apache-2.0 project like ours:

- **copy OK**: MIT, BSD, ISC, Apache-2.0, Unlicense, zlib-style. Code can be taken over with attribution.
- **ideas only**: GPL-3.0, AGPL-3.0, LGPL (for copied source). Read it, learn from it, do not paste it into an
  Apache-2.0 code base. Using such an app as a separate tool is of course fine.
- **read only**: no licence file. Legally all rights reserved, whatever the README says.

"Flag" column: `dormant` = no push for 12 months or more, `archived` = archived on GitHub, `slow` = 9 to 12 months.

---

## 1. The eight projects we already knew: verification result

All eight exist. None of the links in the inherited notes is dead.

| Repo | Licence | Lang | Last push | Stars | Verdict on the inherited notes |
|---|---|---|---|---|---|
| [OferTiber/SaraButton](https://github.com/OferTiber/SaraButton) | MIT | Java | 2026-08-07 | 0 | Notes are right, with two corrections: it is Java, not Kotlin, and it supports three models, not one. |
| [ijustlikeit/phonak-ha](https://github.com/ijustlikeit/phonak-ha) | MIT | Kotlin + Python | 2026-11-23 | 0 | Notes are right. Tap control is only an on/off setting; there is no tap event. |
| [suddenBook/GPTWake](https://github.com/suddenBook/GPTWake) | Apache-2.0 text in LICENSE (API: NOASSERTION) | Java | 2026-09-08 | 14 | Notes are right. Much more valuable than the notes suggest: it documents measured lock-screen constraints. |
| [serrebidev/BlindPilot-Android](https://github.com/serrebidev/BlindPilot-Android) | MIT | Kotlin | 2026-11-24 | 1 | Notes are right. It is a TalkBack chat app (screen on), not a screen-off audio UI. |
| [darth-modder/hermes-mobile-client](https://github.com/darth-modder/hermes-mobile-client) | MIT | TypeScript (Expo) | 2026-09-22 | 4 | Notes are right. |
| [50ButtonsEach/flic2lib-android](https://github.com/50ButtonsEach/flic2lib-android) | custom permissive, zlib-style (API: NOASSERTION) | Java | 2026-09-22 | 21 | Notes are right. |
| [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent) | MIT | Python | 2026-11-27 | 266,540 | Exists. No official Android app in the repository. |
| [ImranR98/Obtainium](https://github.com/ImranR98/Obtainium) | GPL-3.0 | Dart | 2026-11-23 | 21,970 | Exists, actively released. |

Details and evidence:

**SaraButton** [V]
- README: "turns supported Shelly BLU remote button presses into direct phone calls". Supported remotes table in
  README: `SBBT-004CUS` (BTHome device ID `0x0007`), `SBBT-104CUS` (`0x0016`), `SBBT-102C` (`0x0017`). Same constants in
  `app/src/main/java/com/ofertiber/sarabutton/ShellyButtonDevice.java`
  (`DEVICE_ID_RC_BUTTON_4_ZB = 0x0016`). The Shelly documentation for the RC Button 4 ZB gives the same ID
  ("0x16", model "SBBT-104CUS") at https://shelly-api-docs.shelly.cloud/docs-ble/Devices/BLU_ZB/button4_ZB/ [V].
- Only unencrypted BTHome v2: `BthomeParser.parse()` returns an empty packet when `version != 2 || encrypted`.
- Two scan paths at once: a foreground-service `ScanCallback` scan and a `PendingIntent` scan
  (`BleScanManager.startPersistentScan`, receiver `BleScanReceiver`, `directBootAware="true"`).
- Latest release v2.4.2 on 2026-08-07. Zero stars, one author. Treat as a good reference, not as a proof.

**phonak-ha** [V]
- README "Tested with": "Phonak Audéo i90 Sphere", "Samsung Android phone S26".
- README "myPhonak": "myPhonak keeps its own session with the aids open while it runs, even in the background, and
  the aids then ignore the bridge".
- Tap control is a setting only: `android/app/src/main/java/app/phonakbridge/Protocol.kt` line 56
  `const val PARAM_TAP_CONTROL = 0x00FC // settings: u8 0/1, side (1 = left, 2 = right)`. A search of the whole
  repository for "tap" finds no notification or event for an actual tap. So this project cannot give us input path A.
- `PROTOCOL.md` is the most useful file for a Phonak owner: proprietary GATT service `d099cd20-...`, the aid is three
  Bluetooth devices (classic "R-Phonak hearing aid" for audio and calls, plus one LE device per aid used only by
  myPhonak), Sonova company id 642, and how to capture the traffic with the HCI snoop log.
- Commands reach the aids "at the phone's next check-in (up to 30 s)", so it is not a low-latency control path.

**GPTWake** [V for what the repository says; the measurements are the author's, on the author's tablet]
- README: "always-on voice wake word for Android that opens a ChatGPT GPT-Live voice session from a locked,
  screen-off tablet". Uses sherpa-onnx keyword spotting.
- The README table "System constraints ... Each of the following was verified on a physical device" is the best
  public write-up I found of what we are about to test. Quotes are in section 5.
- LICENSE is a 17-line Apache-2.0 notice; GitHub cannot classify it, hence NOASSERTION.

**BlindPilot-Android** [V]
- README: "Accessible AI chat for blind Android users, built for TalkBack and Backtalk", "Remote Hermes over its
  WebSocket gateway: sessions, history, slash commands, questions, and approvals", "This is a pre-release."
- Latest release v0.2.2 on 2026-11-24. No VoiceInteractionService, no MediaSession, no BLE scanning in the source
  (grep over the clone found none).

**hermes-mobile-client** [V]
- README: "connects to a `hermes serve` gateway you run yourself, over WebSocket JSON-RPC (`/api/ws`) and REST
  (`/api/*`)", "thin client". Example: `hermes serve --host 0.0.0.0 --port 9128`.
- Has a `server-plugin/hermes-push` directory and `docs/CONNECTING.md`, `docs/PARITY.md`.

**flic2lib-android** [V]
- README: "The official library for Flic 2 and Flic Duo buttons on Android", hosted on JitPack, tags up to 2.1.1.
- README "Background execution": "The official way of avoiding the process to be killed is to use a Foreground
  Service", with a `Flic2SampleService` example.
- `License.txt`: "Copyright (c) 2019 Shortcut Labs AB ... Permission is hereby granted to use, copy, modify, and
  distribute this software, or portions hereof, for any purpose, without fee" with three zlib-style conditions.

**hermes-agent** [V]
- Latest release "Hermes Agent v0.23.4", 2026-11-24. Top-level `apps/` contains `bootstrap-installer`, `desktop`,
  `shared`. There is no Android app there, so every Android client is third party [I].
- `gateway/platforms/` contains `api_server.py`, `api_server_openai_routes.py`, `api_server_profile_prefix.py`,
  `api_server_runs.py`, `base_streaming_tts.py`, `base_auto_tts.py` [V, file names only; I did not read them].

**Obtainium** [V]
- Latest release v1.7.5 on 2026-11-23. README lists GitHub as an app source and recommends "Verified Apps"
  (privacyguides/verified-apps-android) for checking signing certificates.

---

## 2. The fifteen most useful projects, ranked

| # | Project | Licence | Why it is first-class for us |
|---|---|---|---|
| 1 | [home-assistant/android](https://github.com/home-assistant/android) | Apache-2.0 | Production VoiceInteractionService with keyguard launch, `VOICE_COMMAND` alias, microphone foreground service and wake word, in our licence. |
| 2 | [jooray/i-hear-you](https://github.com/jooray/i-hear-you) | MIT | The nearest relative: a hearing-aid button starts a voice note recorded through the aids, transcribed on the phone and posted to a webhook; replies are played into the aids. For GN/ReSound, not Phonak. |
| 3 | [jooray/android-bluetooth-microphone](https://github.com/jooray/android-bluetooth-microphone) | MIT | A 467-line field guide to recording from hearing-aid and headset microphones on a Pixel, including screen-off recording and a debugging recipe. |
| 4 | [Codename-11/hermes-relay](https://github.com/Codename-11/hermes-relay) | MIT | Hermes client that is already an Android assistant: keyguard launch, VAD, barge-in, diagnostics. |
| 5 | [OferTiber/SaraButton](https://github.com/OferTiber/SaraButton) | MIT | Exactly our remote (SBBT-104CUS) read on Android in the background. |
| 6 | [altinojoao/bthome-bridge](https://github.com/altinojoao/bthome-bridge) | MIT file | Screen-off BTHome scan with a scan watchdog and AES-CCM decryption in Kotlin. |
| 7 | [Scottish-Tech-Army/Soundscape-Android](https://github.com/Scottish-Tech-Army/Soundscape-Android) | MIT (own code) | A real hierarchical audio menu driven by media buttons, for blind users. |
| 8 | [suddenBook/GPTWake](https://github.com/suddenBook/GPTWake) | Apache-2.0 text | Measured rules for microphone and activity starts on a locked, screen-off device. |
| 9 | [google/bumble](https://github.com/google/bumble) | Apache-2.0 | A virtual headset, hearing aid, keyboard and beacon on the desk: tests all three input paths without the real hardware. |
| 10 | [yuga-hashimoto/openclaw-assistant](https://github.com/yuga-hashimoto/openclaw-assistant) | MIT | Assistant role plus media-button trigger plus Hermes API server in one Kotlin app. |
| 11 | [Genteure/zmk-bthome](https://github.com/Genteure/zmk-bthome) | MIT | Answers the open hardware question: one DIY remote that sends HID media keys and BTHome button events. |
| 12 | [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Apache-2.0 | On-device STT, TTS, VAD and keyword spotting with ready Android sample apps. |
| 13 | [ijustlikeit/phonak-ha](https://github.com/ijustlikeit/phonak-ha) | MIT | The only open description of the Phonak (Sonova) BLE control protocol. |
| 14 | [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent) | MIT | The backend. |
| 15 | [ImranR98/Obtainium](https://github.com/ImranR98/Obtainium) | GPL-3.0 | Updates a sideloaded APK straight from GitHub Releases. |

Close runners-up: 50ButtonsEach/flic2lib-android (fallback remote with an official SDK), virse/HermiVoiceApp,
JusticeFist/alfatherapp, jooray/third-ear, chezgoulet/hermes-vox, keymapperorg/KeyMapper, Bluetooth-Devices/bthome-ble,
Hy4ri/hermes-mobile, NiallBunting/AssistantVoiceRecorder, kerero/airdots-double-tap.

---

## 3. Full catalogue by topic

Columns: licence · primary language · last push · stars · flag. Then one sentence each on what it is, why a reader
with a hearing aid would care, and what our app could borrow.

### 3.1 Hermes Agent and its Android clients

GitHub search for Hermes clients returned more than 40 Android projects. The ones below are those that do something
relevant to a voice-only, locked-phone client. Transport statements are quotes from the clients' READMEs; I did not
check them against the hermes-agent source.

| Project | Licence · lang · push · stars | What it is | Why interesting | What to borrow |
|---|---|---|---|---|
| [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent) | MIT · Python · 2026-11-27 · 266,540 | The self-hosted agent itself. | It is the backend every project in this table talks to. | The gateway code under `gateway/platforms/api_server*.py` is the authority for the wire protocol. |
| [Codename-11/hermes-relay](https://github.com/Codename-11/hermes-relay) | MIT · Kotlin · 2026-11-23 · 463 | Native client with chat, hands-free voice, optional assistant role, wake word; also on Google Play. | Closest existing "Hermes as the Android assistant" app. | `assistant/HermesVoiceInteractionService.kt` (overrides `onLaunchVoiceAssistFromKeyguard`, passes `EXTRA_FROM_KEYGUARD`), `audio/VadEngine.kt` (Silero VAD, 512-sample frames at 16 kHz), `audio/BargeInListener.kt`, `wake/MicrophoneOwnershipCoordinator.kt`, `diagnostics/DiagnosticsLog.kt`. Copy OK. |
| [Hy4ri/hermes-mobile](https://github.com/Hy4ri/hermes-mobile) | Apache-2.0 · Kotlin · 2026-11-26 · 509 | Native Compose client, on F-Droid, targetSdk 37; connects "over its REST API and WebSocket gateway". | Same licence as ours, most starred native Kotlin client. | Gateway client, session model and its VoiceInteractionService declaration (`res/xml/voice_interaction_service.xml`). Copy OK. |
| [JusticeFist/alfatherapp](https://github.com/JusticeFist/alfatherapp) | MIT · Kotlin · 2026-11-24 · 22 | Client with "Assistant: Hold the power button" and a Wear OS companion. | Smallest readable assistant-role implementation for Hermes (553 lines in `assist/`). | `assist/AssistService.kt`: a session that owns its own window and starts a live call from `onShow`; `voice/LiveCall.kt` lines 362-380 select a headset with `setCommunicationDevice` and record with `VOICE_COMMUNICATION`. Copy OK. |
| [Giton22/kyllene](https://github.com/Giton22/kyllene) | MIT · Kotlin (KMP) · 2026-11-27 · 350 | Client for `hermes dashboard`, phone and Wear OS, both with a VoiceInteractionService. | Shows read-aloud through a `MediaSession` (`notify/ReadAloudService.kt`, callbacks `onPlay` and `onPause` at lines 81-84). | How spoken replies are exposed as a media session so that play and pause buttons control them. Copy OK. |
| [yuga-hashimoto/openclaw-assistant](https://github.com/yuga-hashimoto/openclaw-assistant) | MIT · Kotlin · 2026-08-29 · 325 | "WakeHermesClaw": voice client for OpenClaw and Hermes with wake word, assistant role, Wear OS. | Has all three of our trigger ideas in one app: assistant role, media button, wake word. | Hermes API server settings from its README (port 8642, `/v1/chat/completions`, `/v1/runs`, `Authorization: Bearer`, model `hermes-agent`, `GET /v1/models`, `/health`). Its media-button receiver is a warning, see section 5.4. |
| [chezgoulet/hermes-vox](https://github.com/chezgoulet/hermes-vox) | GPL-3.0 · Kotlin · 2026-11-20 · 16 | Voice-first client: open line with VAD, on-device Silero VAD, Supertonic TTS and Whisper base.en. | The best written explanation of Bluetooth headset microphone routing I found. | Ideas only: `CommRoute.kt` (API 31+ `setCommunicationDevice`, legacy SCO below), structured `event=... action=...` logging. |
| [virse/HermiVoiceApp](https://github.com/virse/HermiVoiceApp) | Apache-2.0 · Kotlin · 2026-11-13 · 3 | Sideloaded push-to-talk app; German and English offline STT with sherpa-onnx; README is German first. | German speech recognition plus Hermes plus sideload updater, in our licence. | `stt/SherpaRecognizer.kt`, `stt/ModelManager.kt` (about 57 MB per language model), `api/HermesApiSender.kt` (profile path `…/p/<botname>/v1/chat/completions`), `update/*` (self-update with SHA-256 check). Copy OK. |
| [TheSethRose/Hermes-Walkie-Talkie](https://github.com/TheSethRose/Hermes-Walkie-Talkie) | no licence · Kotlin · 2026-06-27 · 6 | Push-to-talk app plus a small Bun gateway that calls the Hermes CLI. | Shows the "own tiny gateway" architecture. | Read only: `audio/AudioRouteManager.kt`, `audio/SileroOnnxVad.kt`, `service/VoiceSessionService.kt`. |
| [serrebidev/BlindPilot-Android](https://github.com/serrebidev/BlindPilot-Android) | MIT · Kotlin · 2026-11-24 · 1 | TalkBack-first chat app with remote Hermes over the WebSocket gateway. | Written by and for blind users; shows what approvals and questions look like without sight. | Gateway session, question and approval handling. Copy OK. |
| [darth-modder/hermes-mobile-client](https://github.com/darth-modder/hermes-mobile-client) | MIT · TypeScript · 2026-09-22 · 4 | Expo thin client for `hermes serve` (`/api/ws` JSON-RPC, `/api/*` REST). | Documents connecting, auth secret and push plugin. | `docs/CONNECTING.md` and the JSON-RPC message shapes; not the code (React Native). |
| [majorminorlabs/hermes-voice](https://github.com/majorminorlabs/hermes-voice) | MIT · Python · 2026-11-11 · 756 | Bridges a Hermes profile into Home Assistant Assist. | A no-code route: Home Assistant's Android app already is an assistant with keyguard support. | The idea of reusing Assist as the voice front end and Hermes as the conversation agent. |
| [Adolanium/hermes-gadget-sdk](https://github.com/Adolanium/hermes-gadget-sdk) | MIT · C++ · 2026-11-26 · 593 | ESP32-S3 and Raspberry Pi firmware for hold-to-talk Hermes devices, with a Hermes plugin. | A DIY hardware button that talks to Hermes without a phone. | The Gadget plugin protocol, as an example of a purpose-built voice endpoint in Hermes. |
| [cogwheel0/conduit](https://github.com/cogwheel0/conduit) | GPL-3.0 · Dart · 2026-11-25 · 2,528 | Flutter client for Open WebUI, OpenAI-compatible servers and Hermes. | Most popular multi-backend client. | Ideas only. |
| [rusty4444/ares](https://github.com/rusty4444/ares) | MIT · Dart · 2026-11-27 · 573 | Flutter Hermes client over LAN or Tailscale. | Popular, active. | Little for us (Flutter). |
| [raulvidis/hermes-android](https://github.com/raulvidis/hermes-android) | MIT · Kotlin · 2026-11-26 · 510 | Bridge app plus Python toolset so Hermes can control the phone. | The reverse direction: agent acts on the phone. | Tool definitions if Lara should later trigger phone actions. |

Three transports appear in these READMEs [V as README statements, U against the server]:
1. `hermes dashboard`, conventionally port 9119, REST plus WebSocket (hermes-relay, alfatherapp, kyllene, Hy4ri).
2. `hermes serve`, WebSocket JSON-RPC at `/api/ws` plus REST at `/api/*`, example port 9128 (hermes-mobile-client).
3. The API server, OpenAI-compatible, default port 8642, bearer key (openclaw-assistant, hermes-vox, HermiVoiceApp).

### 3.2 Android assistants and assistant-role apps

| Project | Licence · lang · push · stars | What it is | Why interesting | What to borrow |
|---|---|---|---|---|
| [home-assistant/android](https://github.com/home-assistant/android) | Apache-2.0 · Kotlin · 2026-11-27 · 4,085 | Home Assistant companion app; Assist can be the default assistant, with on-device wake word. | The most mature open assistant-role app, in our licence, release 2026.11.1. | See section 5.1: manifest with `AssistVoiceInteractionService` (`foregroundServiceType="microphone"`), `supportsLaunchVoiceAssistFromKeyguard="true"`, `AssistActivity` with `showWhenLocked="true"`, an alias for `android.intent.action.VOICE_COMMAND`. Copy OK. |
| [DicioTeam/dicio-android](https://github.com/DicioTeam/dicio-android) | GPL-3.0 · Kotlin · 2026-10-23 · 1,558 | Offline assistant with skills, Vosk STT and openWakeWord ("Hey Dicio"). Moved from Stypox/dicio-android. | Fully on-device, speaks answers, has German sentences (`app/src/main/sentences/de`) and maps `de` to `vosk-model-small-de-0.15`. | Ideas only: it takes the assistant slot with an activity for `ASSIST` and `VOICE_COMMAND` instead of a VoiceInteractionService, and its skill and sentence-matching design. |
| [suddenBook/GPTWake](https://github.com/suddenBook/GPTWake) | Apache-2.0 text · Java · 2026-09-08 · 14 | Wake word that opens ChatGPT voice on a locked, screen-off tablet. | Proves a voice session can start with the display off, and says exactly how. | The constraints table (section 5.3), the transparent `showWhenLocked` shim activity, `LOCKED_BOOT_COMPLETED` recovery, `testkit.sh` (logcat tags, `dumpsys batterystats`, `dumpsys meminfo`), `docs/power.md`. |
| [NiallBunting/AssistantVoiceRecorder](https://github.com/NiallBunting/AssistantVoiceRecorder) | GPL-3.0 · Kotlin · 2026-11-02 · 0 | Minimal app: selected assistant starts a microphone foreground-service recording. | The smallest end-to-end "assistant gesture starts the mic" example (about 1,300 lines). | Ideas only: session to foreground-service hand-off; a good template for our prototype A. |
| [brownard/Ava](https://github.com/brownard/Ava) | Apache-2.0 · Kotlin · 2026-06-18 · 188 | Android voice satellite for Home Assistant using the ESPHome protocol. | A second Apache-2.0 voice endpoint in Kotlin. | Its audio pipeline, not reviewed in depth [U]. Copy OK. |
| [msp1974/ViewAssist_Companion_App](https://github.com/msp1974/ViewAssist_Companion_App) | Apache-2.0 · Python + Android · 2026-11-09 · 549 | Android app and integration for View Assist satellites. | Another maintained satellite. | Little beyond Ava. |
| [Nailik/rhasspy_mobile](https://github.com/Nailik/rhasspy_mobile) | MIT · Kotlin · 2026-11-25 · 86 | Rhasspy satellite with local wake word. | Kotlin voice satellite with local wake word. | Not reviewed in depth [U]. Copy OK. |
| [AndraxDev/speak-gpt](https://github.com/AndraxDev/speak-gpt) | Apache-2.0 · Kotlin · 2026-10-30 · 448 | Voice assistant for OpenAI-compatible APIs. | Popular LLM assistant in our licence. | UI-heavy; little for a screen-off app. |
| [Edw590/VISOR---Android-Version-Assistant](https://github.com/Edw590/VISOR---Android-Version-Assistant) | Apache-2.0 · Java · 2025-11-20 · 41 · **dormant** | Assistant started "when I broke my phone's screen", meant to operate a phone with no usable display. | Same premise as ours: the screen does not exist. | Module list in its README (speech queue with priorities, command recogniser). |
| [Kaljurand/K6nele](https://github.com/Kaljurand/K6nele) | Apache-2.0 · Java · 2026-08-29 · 289 | Speech-to-text service and UI for other apps. | Long-lived `RecognitionService` implementation. | How to implement the `RecognitionService` every VoiceInteractionService must name. |
| [openclaw/openclaw](https://github.com/openclaw/openclaw) (`apps/android`) | MIT · TypeScript + Kotlin · 2026-11-27 · 395,321 | Comparable self-hosted agent with an official Android app (chat, Talk, voice wake, Wear OS). | The best-funded comparable client. | Talk mode behaviour: mute "sends silence while keeping the recording session open". |
| [just-ai/aimybox-android-assistant](https://github.com/just-ai/aimybox-android-assistant) | Apache-2.0 · Kotlin · 2024-01-29 · 280 · **dormant** | Embeddable assistant SDK. | Historic reference. | Nothing current. |

A GitHub code search for `supportsLaunchVoiceAssistFromKeyguard="true"` in XML returned 241 files. Apart from the
projects above it lists many small personal assistants; I did not review them one by one.

### 3.3 Eyes-free, audio-only and button-driven interfaces

| Project | Licence · lang · push · stars | What it is | Why interesting | What to borrow |
|---|---|---|---|---|
| [Scottish-Tech-Army/Soundscape-Android](https://github.com/Scottish-Tech-Army/Soundscape-Android) | MIT for own code (API: NOASSERTION, see `LICENSE.md` "New code") · Kotlin · 2026-11-27 · 11 | Android port of Microsoft Soundscape: spatial-audio navigation for blind people. | It ships what we are designing: a spoken menu operated with headset media buttons while the phone is in the pocket. | `shared/.../services/mediacontrol/AudioMenu.kt` (253 lines: menu stack, `next()`, `select()`, lazy submenus, "Main Menu" escape, earcons on enter and exit), `SoundscapeMediaSessionCallback.kt`, `SoundscapeDummyMediaPlayer.kt` (a Media3 `SimpleBasePlayer` that exists only to receive buttons). Copy OK. See section 5.2. |
| [google/talkback](https://github.com/google/talkback) | Apache-2.0 · Java · 2026-04-01 · 527 | Open-source drop of Android's screen reader. | The reference for speech queues, earcons and interruption rules. | Speech controller and feedback-queue design. |
| [trypsynth/backtalk](https://github.com/trypsynth/backtalk) | Apache-2.0 · Java · 2026-11-25 · 76 | Community fork of TalkBack. | Actively developed, where TalkBack's public repo moves slowly. | Same as TalkBack, with current code. |
| [pilot51/voicenotify](https://github.com/pilot51/voicenotify) | Apache-2.0 · Kotlin · 2026-11-20 · 244 | Speaks notifications through TTS. | A long-lived app whose whole output is speech. README: "Choice of speaking when screen or headset is on or off, or while in silent/vibrate mode", "Quiet Time". | TTS queueing, audio focus handling, "only when a headset is connected" conditions. Copy OK. |
| [jonathancruzate/muse-android-auto](https://github.com/jonathancruzate/muse-android-auto) | MIT · Java · 2026-11-24 · 1 | Android Auto media app whose player controls drive a voice session ("Play = mic live, Pause = mute, Stop = hang up"). | A neat mapping of transport controls to conversation states. | The mapping itself, for path C. |
| [GoldenGuts/bluetooth-tv-remote](https://github.com/GoldenGuts/bluetooth-tv-remote) | GPL-3.0 · Java · 2026-10-15 · 3 | "Eyes-free TV remote: swipe, tap, feel." | Interaction design without looking. | Ideas only. |
| [brltty/brltty](https://github.com/brltty/brltty) | LGPL-2.1 · C · 2026-11-22 · 126 | Braille screen reader, also for Android. | For deaf-blind readers. | Nothing direct. |
| [microsoft/soundscape](https://github.com/microsoft/soundscape) / [soundscape-community/soundscape](https://github.com/soundscape-community/soundscape) | MIT · Swift · 2023-08-29 **archived** / 2026-11-26 | The iOS original and its community fork. | Origin of the callout and earcon design. | Earcon sounds are MIT licensed. |

### 3.4 BLE buttons on Android: BTHome, Shelly BLU, Flic

| Project | Licence · lang · push · stars | What it is | Why interesting | What to borrow |
|---|---|---|---|---|
| [OferTiber/SaraButton](https://github.com/OferTiber/SaraButton) | MIT · Java · 2026-08-07 · 0 | Shelly BLU button press places a phone call. | Our exact remote, read in the background on Android 6 to 15, built as an accessibility aid. | `BleScanManager.java` (dual scan, address filter first and service-data filter `0000fcd2-...` as fallback), `BthomeParser.java` (object sizes, stops on unknown objects), `MonitoringService.java` (`SCAN_REFRESH_MILLIS = 4 min`, `RETRY_DELAY_MILLIS = 10 s`), release pipeline with SHA-256 files and `gh attestation verify`. Copy OK. |
| [altinojoao/bthome-bridge](https://github.com/altinojoao/bthome-bridge) | MIT in LICENSE (README footer says "Todos os direitos reservados") · Kotlin · 2026-10-13 · 2 | Phone as a BTHome-to-HTTP gateway "com a app fechada e o ecrã apagado". | Targets our hardest question, the click after a long screen-off time, and already added watchdogs for it. | `ble/GestorScan.kt` (PendingIntent scan, preventive renewal every 20 min, alarm-based restart), `ble/BTHomeCripto.kt` (AES-CCM on top of `AES/ECB/NoPadding`, checked against the bthome.io test vector), `ble/GestorCombinacoes.kt` (click sequences). Ask the author about the licence contradiction before copying. |
| [Kehaku/BLU-Button-Alert](https://github.com/Kehaku/BLU-Button-Alert) | MIT · Java · 2026-10-04 · 0 | Shelly BLU Button Tough sends an emergency alert via ntfy. | Second independent Android reader of Shelly buttons. | Cross-check of the parsing. |
| [steanlab/BTHome-Logger](https://github.com/steanlab/BTHome-Logger) | MIT · Kotlin · 2026-10-13 · 4 | Shows live unencrypted BTHome v1 and v2 data. | Handy to see what the remote really sends. | A debug screen for our app. Copy OK. |
| [wb8wka/BTHOME2_decoder](https://github.com/wb8wka/BTHOME2_decoder) | no licence · Kotlin · 2026-09-27 · 0 | Scanner and decoder with AES-CCM and per-device keys. | Decoder for encrypted remotes. | Read only. |
| [falkorichter/open-bthome-button-app](https://github.com/falkorichter/open-bthome-button-app) | no licence · Kotlin · 2026-10-17 · 0 | Makes a phone act as a BTHome button and measures address rotation. | A second phone becomes a test remote. | Read only: `docs/feasibility.md`, `docs/address-rotation.md`. Use the APK as a test sender. |
| [50ButtonsEach/flic2lib-android](https://github.com/50ButtonsEach/flic2lib-android) | zlib-style · Java · 2026-09-22 · 21 | Official Flic 2 and Flic Duo SDK. | A connection-based button with vendor support for background use. | The fallback hardware path; README "Background execution" foreground-service sample. |
| [s4ysolutions/itag](https://github.com/s4ysolutions/itag) | GPL-3.0 · Java · 2026-09-18 · 57 | Manager for cheap iTag key finders; README: a double click on the tag's button makes the phone ring. | The cheapest possible BLE button, read over a GATT connection. | Ideas only: keeping GATT connections alive in the background [I]. |
| [AltBeacon/android-beacon-library](https://github.com/AltBeacon/android-beacon-library) | Apache-2.0 · Java · 2026-11-21 · 2,914 | The standard beacon library. | Ten years of background-scan workarounds in one place. | Background scan strategies per Android version. |
| [nordicsemi/Android-Scanner-Compat-Library](https://github.com/nordicsemi/Android-Scanner-Compat-Library) | BSD-3-Clause · Java · 2026-11-26 · 796 | Scanner compatibility layer, including PendingIntent scans. | Maintained by the chip vendor. | Reference for scan filters and offloaded batching. |
| [nordicsemi/Kotlin-BLE-Library](https://github.com/nordicsemi/Kotlin-BLE-Library) | BSD-3-Clause · Kotlin · 2026-11-27 · 534 | Coroutine BLE library. | Modern Kotlin API. | Only if we later connect to a device. |
| [JuulLabs/kable](https://github.com/JuulLabs/kable) | Apache-2.0 · Kotlin · 2026-11-27 · 1,206 | Multiplatform coroutine BLE. | Same. | Same. |
| [Freeyourgadget/Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge) (Codeberg) | AGPL-3.0 · Java · 2026-11-27 · 2,059 | Vendor-free companion app for watches and earbuds. Verified through the Codeberg API, not GitHub. | Shows how to reverse-engineer and keep a wearable connected all day. | Ideas only. |
| [librepods-org/librepods](https://github.com/librepods-org/librepods) | GPL-3.0 · Kotlin · 2026-11-09 · 30,994 | AirPods features on Android; README has a section "Accessibility Settings and Hearing Aid" and lists "Head Gestures". | Earbud gestures and hearing-aid settings on Android without the vendor ecosystem. | Ideas only. |

### 3.5 BTHome format, parsers and Shelly tooling

| Project | Licence · lang · push · stars | What it is | What to borrow |
|---|---|---|---|
| [home-assistant/bthome.io](https://github.com/home-assistant/bthome.io) | no licence file · HTML · 2026-11-25 · 27 | Source of the format specification site. | The object table and the encryption test vector. |
| [Bluetooth-Devices/bthome-ble](https://github.com/Bluetooth-Devices/bthome-ble) | MIT · Python · 2026-11-26 · 145 | The reference parser used by Home Assistant. | `src/bthome_ble/parser.py`, `event.py`, `bthome_v2_encryption.py` and its test suite as golden vectors for our Kotlin parser. Copy OK. |
| [theengs/decoder](https://github.com/theengs/decoder) | GPL-3.0 · C · 2026-11-26 · 221 | Decoder library with per-device files. | Ideas only: `src/devices/SBBT_004CEU_US_json.h`, `SBBT_002C_json.h` and the `_ENCR` variants describe Shelly BLU buttons. |
| [theengs/app](https://github.com/theengs/app) | GPL-3.0 · C++ (Qt) · 2026-11-24 · 49 | Mobile app that reads BLE sensors and pushes to MQTT. | A ready tool to watch advertisements on the phone. |
| [ALLTERCO/shelly-script-examples](https://github.com/ALLTERCO/shelly-script-examples) | Apache-2.0 · JavaScript · 2026-11-11 · 336 | Vendor scripts. | `ble/ble-shelly-blu.shelly.js` and `ble/ble-shelly-btn.shelly.js` are the vendor's own BTHome parsing. Copy OK. |
| [pantano03/shelly-blu-rc4-production](https://github.com/pantano03/shelly-blu-rc4-production) | MIT · Lua · 2026-07-27 · 0 | SmartThings driver for the RC Button 4. | Second opinion on button numbering. |
| [triochi/shelly_remote_homey_app](https://github.com/triochi/shelly_remote_homey_app) | GPL-3.0 · JavaScript · 2026-08-10 · 0 | Homey app for the BLU Remote ZB. | Ideas only, for the other Shelly remote in the notes. |
| [kondas/SBRC-005B](https://github.com/kondas/SBRC-005B) | no licence · 2026-10-21 · 0 | Hardware notes for the BLU Remote Control ZB. | Read only. |
| [seime/openhab-bthome](https://github.com/seime/openhab-bthome) | no licence · Java · 2026-11-23 · 2 | openHAB binding. | Read only: a JVM parser. |
| [koenvervloesem/BTHome-Kaitai-Struct](https://github.com/koenvervloesem/BTHome-Kaitai-Struct) | MIT · 2024-06-30 · 5 · **dormant** | Formal Kaitai description of the format. | Could generate a parser; old. |
| [steanlab/BLE-Beacon](https://github.com/steanlab/BLE-Beacon) | MIT · C · 2026-10-25 · 9 | Flipper Zero app that simulates BTHome and other beacons. | Test sender if a Flipper is at hand. |

### 3.6 DIY remotes: BLE events plus HID media keys

| Project | Licence · lang · push · stars · flag | What it is | What to borrow |
|---|---|---|---|
| [zmkfirmware/zmk](https://github.com/zmkfirmware/zmk) | MIT · C · 2026-11-16 · 4,340 | Wireless keyboard firmware on Zephyr for nRF52 boards; sends HID including media keys. | The firmware base for a pocket remote with months of battery life. |
| [Genteure/zmk-bthome](https://github.com/Genteure/zmk-bthome) | MIT · C · 2026-01-15 · 9 · slow | ZMK module: "BLE BTHome v2 advertisements", "Optional encryption", "Configurable amount of buttons with all press types". Needs `CONFIG_BT_EXT_ADV_MAX_ADV_SET=2`. README: "Tested working on an actual keyboard with ZMK main branch as of January 2026." | One device, both paths: HID keys over the connection (path C) and BTHome button events as advertisements (path B). This is the combination the notes call unverified. It is still unverified on our phone. |
| [espruino/Espruino](https://github.com/espruino/Espruino) and [espruino/EspruinoDocs](https://github.com/espruino/EspruinoDocs) | see LICENSE in each repo (API: NOASSERTION; I did not classify it) · C / JS · 2026-11-27 · 2,984 / 268 | JavaScript firmware for Puck.js and other nRF52 buttons. | `modules/BTHome.js` and `modules/ble_hid_controls.js` both exist in EspruinoDocs, so a Puck.js can be scripted to do both [I: I did not test them together]. |
| [knflrpn/BTHome_C3Button](https://github.com/knflrpn/BTHome_C3Button) | no licence · C++ · 2026-11-02 · 10 | "Ultra-low power BT Home buttons using generic ESP32-C3 Super Mini board". | Read only: deep-sleep button design. |
| [bathoryr/BLE-battery-button](https://github.com/bathoryr/BLE-battery-button) | MIT · C++ · 2026-11-20 · 0 | nRF52840 battery BTHome button. | Small Arduino-style starting point. |
| [Chreece/BTHomeV2-ESP32-example](https://github.com/Chreece/BTHomeV2-ESP32-example) | MIT · C++ · 2026-10-05 · 79 | BTHome v2 with encryption on ESP32. | Encrypted sender for tests. |
| [markusg1234/ESPHome-espidf_ble_keyboard](https://github.com/markusg1234/ESPHome-espidf_ble_keyboard) | GPL-3.0 · 2026-11-27 · 63 | ESPHome component: ESP32 as BLE HID keyboard and remote. | Quick HID test device from YAML. |
| [h2zero/NimBLE-Arduino](https://github.com/h2zero/NimBLE-Arduino) | Apache-2.0 · C · 2026-11-26 · 1,152 | NimBLE for ESP32 and nRF5x in Arduino. | Base for a custom HID plus advertising firmware. |
| [jeffrizzo/bthome-zephyr](https://github.com/jeffrizzo/bthome-zephyr) | MIT · C · 2024-03-05 · 12 · **dormant** | BTHome events on Zephyr. | Reference only. |
| [agittins/NarfButt](https://github.com/agittins/NarfButt) | no licence · C++ · 2024-02-17 · 10 · **dormant** | BTHome remote controls on nRF51. | Reference only. |
| [GraceGRD/flic-twist-bthome-v2](https://github.com/GraceGRD/flic-twist-bthome-v2) | MIT · C · 2024-05-06 · 12 · **dormant** | Custom firmware that makes a Flic Twist speak BTHome. | Shows a commercial button can be reflashed. |
| [T-vK/ESP32-BLE-Keyboard](https://github.com/T-vK/ESP32-BLE-Keyboard) | no licence · C++ · 2024-08-07 · 3,205 · **dormant** | The classic ESP32 BLE keyboard library with media keys. | Widely copied, unmaintained, no licence. Prefer NimBLE or ZMK. |

### 3.7 Media buttons and hardware keys with the screen off

| Project | Licence · lang · push · stars | What it is | What to borrow |
|---|---|---|---|
| [androidx/media](https://github.com/androidx/media) | Apache-2.0 · Java · 2026-11-27 · 3,095 | Jetpack Media3 (`MediaSession`, `MediaSessionService`). | The supported way to receive media buttons in the background. |
| [keymapperorg/KeyMapper](https://github.com/keymapperorg/KeyMapper) | GPL-3.0 · Kotlin · 2026-11-26 · 2,876 | Remaps hardware keys, headset buttons included; release v4.7.2 on 2026-11-25. | Ideas only, and one fact: screen-off detection needs its privileged "Expert Mode". String `tip_screen_off_trigger_message`: "This key map cannot be detected when the screen is off because the trigger was not recorded with Expert Mode." So key interception through an accessibility service is not a screen-off path [V for the string, I for the conclusion]. |
| [AntennaPod/AntennaPod](https://github.com/AntennaPod/AntennaPod) | GPL-3.0 · Java · 2026-11-27 · 8,366 | Podcast player. | Ideas only: a long-lived player whose media-button handling is battle-tested [I]. |
| [PaulWoitaschek/Voice](https://github.com/PaulWoitaschek/Voice) | GPL-3.0 · Kotlin · 2026-11-27 · 3,316 | Audiobook player. | Ideas only: a current Kotlin audio player to compare session-service structure against [I]. |
| [quite/mumla](https://github.com/quite/mumla) | GPL-3.0 · Java · 2026-10-23 · 133 | Mumble voice-chat client (GitHub mirror of the GitLab repo). | Ideas only. I expected hardware-key push-to-talk here but could not confirm it from the README [U]. |
| [renyuneyun/Easer](https://github.com/renyuneyun/Easer) | GPL-3.0 · Java · 2026-07-21 · 998 | Event-driven automation app. | Ideas only. |
| [kerero/airdots-double-tap](https://github.com/kerero/airdots-double-tap) | MIT · Kotlin · 2023-08-21 · 64 · **dormant** | Earbud double tap, which normally calls the assistant, skips the song instead. README: "Will work with other earphones that activate personal assistant"; tested on Android 8.1 to 10. | The whole trick is one `Theme.NoDisplay` activity with `android:showOnLockScreen="true"` and an intent filter for `android.intent.action.VOICE_COMMAND`. This is the smallest existing example of path A's entry point. Copy OK, but old. |
| [android/uamp](https://github.com/android/uamp) | Apache-2.0 · Kotlin · 2026-01-09 · 13,154 · **archived** | Google's former sample audio app. | Historic sample only. |

### 3.8 On-device speech for Android

| Project | Licence · lang · push · stars · flag | What it is | What to borrow |
|---|---|---|---|
| [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Apache-2.0 · C++ · 2026-11-27 · 16,278 | STT, TTS, VAD, keyword spotting; release v2.0.3 on 2026-11-22. | The `android/` directory has ready apps: `SherpaOnnxVad`, `SherpaOnnxVadAsr`, `SherpaOnnxKws`, `SherpaOnnxTts`, `SherpaOnnxTtsEngine`, `SherpaOnnxSimulateStreamingAsr`, `SherpaOnnxAar`, plus `fdroid-reproducible-builds.md`. Copy OK. Check the licence of each model and of the espeak-ng data some TTS voices need [U]. |
| [ggml-org/whisper.cpp](https://github.com/ggml-org/whisper.cpp) | MIT · C++ · 2026-11-26 · 55,584 | Whisper in C/C++; release v1.9.6 on 2026-11-23. Moved from ggerganov/whisper.cpp. | `examples/whisper.android` and `examples/whisper.android.java`. |
| [snakers4/silero-vad](https://github.com/snakers4/silero-vad) | MIT · Python · 2026-11-26 · 10,781 | Voice activity detector; release v6.3 on 2026-11-23. | The ONNX model; `examples/java-example`. |
| [gkonovalov/android-vad](https://github.com/gkonovalov/android-vad) | MIT · C · 2025-07-15 · 512 · **dormant** | Android VAD library (WebRTC, Silero, Yamnet). | Used by hermes-relay. No push for 16 months; sherpa-onnx VAD is the maintained alternative. |
| [OHF-Voice/piper1-gpl](https://github.com/OHF-Voice/piper1-gpl) | GPL-3.0 · C++ · 2026-11-24 · 6,420 | Current home of Piper TTS; release v1.8.0. | Linking it would make our app GPL. Piper voices can be run through sherpa-onnx instead (HayaiApp/HayaiTTS describes itself as shipping "voices via sherpa-onnx (Piper / Kokoro / Kitten / Matcha ...)") [I]. |
| [rhasspy/piper](https://github.com/rhasspy/piper) | MIT · C++ · 2025-08-26 · 11,335 · **archived** | The old Piper repository. | Superseded by piper1-gpl. |
| [woheller69/ttsEngine](https://github.com/woheller69/ttsEngine) | GPL-3.0 · Kotlin · 2026-07-20 · 825 | System TTS engine (SherpaTTS) [I for the name; the API description is empty]. | Could be used through Android's `TextToSpeech` API. Warning from its README: because of Google's developer verification "this app will no longer work on certified Android devices after that time" (2026/2027). Do not depend on it. |
| [siva-sub/NekoSpeak](https://github.com/siva-sub/NekoSpeak) | MIT · Kotlin · 2026-05-19 · 209 | System TTS engine with Kokoro, KittenTTS, Pocket-TTS, Piper. | Same use; code is copy OK. |
| [alphacep/vosk-api](https://github.com/alphacep/vosk-api) | Apache-2.0 · 2026-10-15 · 15,295 | Offline STT with small models. | Used by Dicio, which downloads `vosk-model-small-de-0.15.zip` for German (`VoskInputDevice.kt` line 463). |
| [dscripka/openWakeWord](https://github.com/dscripka/openWakeWord) | Apache-2.0 code · 2025-12-30 · 2,979 · slow | Wake word framework. | We do not want always-listening; relevant only as an optional mode. |
| [OHF-Voice/micro-wake-word](https://github.com/OHF-Voice/micro-wake-word) | Apache-2.0 · Python · 2026-07-06 · 982 | Wake word models used by Home Assistant on Android. | Same remark. |
| [woheller69/whisperIME](https://github.com/woheller69/whisperIME) | MIT · Java · 2026-11-26 · 662 | Whisper as an input method; README: "can also be selected as system-wide voice input (RecognitionService)". | A system-wide offline recogniser we could call through `SpeechRecognizer`. Copy OK. |
| [soupslurpr/Transcribro](https://github.com/soupslurpr/Transcribro) | ISC · Kotlin · 2026-11-12 · 770 | On-device speech keyboard and service. | Same. |
| [moonshine-ai/moonshine](https://github.com/moonshine-ai/moonshine) | custom (API: NOASSERTION) · C++ · 2026-11-10 · 11,614 | Low-latency STT for voice agents. | Check the licence first [U]. |
| [soniqo/speech-core](https://github.com/soniqo/speech-core) | Apache-2.0 · C++ · 2026-11-23 · 102 | VAD, streaming STT, TTS with a voice-agent pipeline, Android included. | Alternative to sherpa-onnx. |
| [argmaxinc/WhisperKitAndroid](https://github.com/argmaxinc/WhisperKitAndroid) | MIT · Kotlin · 2026-01-24 · 212 · **archived** | Whisper on Android. | Archived; skip. |

### 3.9 Projects for people with hearing aids

| Project | Licence · lang · push · stars | What it is | What to borrow |
|---|---|---|---|
| [jooray/i-hear-you](https://github.com/jooray/i-hear-you) | MIT · Kotlin · 2026-11-22 · 1 | "An open-source Android remote for GN hearing aids (ReSound and its sister brands) ... maps the aid's own button to phone actions"; "voice notes recorded from the aids, transcribed on the phone and sent to a webhook". | `docs/VOICE-NOTES.md` (press on the aid starts a note; input chosen from the communication devices; waits at most 3 s plus 500 ms; spoken "go ahead" cue; five seconds of exact zeros trigger a warning; transcription "headless, with the screen locked"), the button bridge (README: "handle 53 press/release -> short/long/double -> media control"), the reconnect supervisor with backoff. Copy OK. It needs a GATT connection to GN aids, so nothing transfers to Phonak directly. |
| [jooray/android-bluetooth-microphone](https://github.com/jooray/android-bluetooth-microphone) | MIT · docs · 2026-11-22 · 2 | `SKILL.md`: how to record from a Bluetooth headset, LE Audio earbuds or hearing aids on Android. "Measured on a Pixel 10 Pro Fold with Android 17, LE Audio hearing aids and a classic SCO headset." | The recipe and its numbers, quoted in section 5.5. Also its section "Background recording (headset button, screen off)" and the logcat filter `AHal::BT|AHal::Router`. Copy OK. |
| [ijustlikeit/phonak-ha](https://github.com/ijustlikeit/phonak-ha) | MIT · Kotlin + Python · 2026-11-23 · 0 | Phonak volume, program, mute, battery in Home Assistant through an Android bridge. | `PROTOCOL.md`, `tools/sonova_decode.py` (decodes an HCI snoop log), `android/.../Protocol.kt`. Lets Lara announce the battery level or switch the program. Copy OK. |
| [jooray/third-ear](https://github.com/jooray/third-ear) | GPL-3.0 · Kotlin · 2026-11-26 · 11 | Recorder that uses "headsets, LE Audio hearing aids, the car's hands-free microphone". | Ideas only (the same author's MIT guide above carries the lessons): wait up to 8 s for the Bluetooth microphone, fall back to the phone once with an audible cue, silence watchdog for "exact digital zeros, Android silencing the app". |
| [thivyan-studios/hark-app](https://github.com/thivyan-studios/hark-app) | BSD-3-Clause · Kotlin + C++ · 2026-11-17 · 41 | Phone as a remote microphone that streams to hearing aids, with live captions (sherpa-onnx). | `service/HearingDeviceMonitor.kt` (tracks hearing-aid audio devices with `AudioDeviceCallback`), "Automatic background stream recovery on audio route changes or screen-off events". Copy OK. |
| [thewierdnut/asha_pipewire_sink](https://github.com/thewierdnut/asha_pipewire_sink) | Unlicense · C++ · 2026-09-21 · 53 | ASHA hearing-aid streaming for Linux. | For readers who want their aids on a Linux desktop. |
| [Gheop/hearing-aid-presets](https://github.com/Gheop/hearing-aid-presets) | MIT · JavaScript · 2026-11-06 · 2 | Switch LE Audio hearing-aid programs from GNOME (tested on ReSound). | Shows the standard preset service in use. |
| [ryleighnewman/Self-Programming-My-Phonak-Hearing-Aids-With-Claude-AI](https://github.com/ryleighnewman/Self-Programming-My-Phonak-Hearing-Aids-With-Claude-AI) | MIT · docs · 2026-10-01 · 5 | A user's write-up of fitting their own Audéo I90 Sphere. | Context for Phonak owners; not code. |
| [JediLin/Hearing-Aids-Fitting-Software-Update-Checkers](https://github.com/JediLin/Hearing-Aids-Fitting-Software-Update-Checkers) | no licence · Python · 2026-11-23 · 40 | Scripts that check vendors' fitting-software update servers. | For the DIY-fitting crowd. |
| [rscircus/awesome-hearing](https://github.com/rscircus/awesome-hearing) | MIT · list · 2026-11-19 · 8 | "Awesome List for Hearing Aids & Hearing Aid Technology". | A place to find more, and to list our project. |
| [tuckercr/wakewordapp](https://github.com/tuckercr/wakewordapp) | no licence · Kotlin · 2026-11-19 · 35 | Alerts hearing-impaired users when their name is said. | A different assistive idea. |
| [HoerTech-gGmbH/openMHA](https://github.com/HoerTech-gGmbH/openMHA) | AGPL-3.0 · C · 2026-08-05 · 346 | Open research hearing-aid software. | Background. |
| [Tympan/Tympan_Library](https://github.com/Tympan/Tympan_Library) | MIT · C++ · 2026-11-16 · 154 | Open-source hearing-aid hardware library. | Background. |

### 3.10 Installing and updating a sideloaded app

| Project | Licence · lang · push · stars | What it is | What to borrow |
|---|---|---|---|
| [ImranR98/Obtainium](https://github.com/ImranR98/Obtainium) | GPL-3.0 · Dart · 2026-11-23 · 21,970 | Installs and updates apps from their release pages. | Publish one signed APK per GitHub release and add an Obtainium link to the README, as hermes-vox and Hy4ri/hermes-mobile do. |
| [privacyguides/verified-apps-android](https://github.com/privacyguides/verified-apps-android) | MIT · Kotlin · 2026-11-26 · 209 | Verification tool that Obtainium's README recommends. | Publish the signing certificate SHA-256 in the README, as SaraButton does. |
| [soupslurpr/AppVerifier](https://github.com/soupslurpr/AppVerifier) | ISC · Kotlin · 2026-09-18 · 1,251 | Checks an APK's signing certificate against a published fingerprint. | Same. |
| [komi-store/komi-store](https://github.com/komi-store/komi-store) | Apache-2.0 · Kotlin · 2026-11-25 · 22,587 | App store for releases on GitHub, Codeberg, Forgejo (formerly GitHub Store). | Second update channel that needs nothing from us. |
| [accrescent/accrescent](https://github.com/accrescent/accrescent) | Apache-2.0 · Kotlin · 2026-11-26 · 2,342 | Security-focused app store. | Not needed for one user. |
| [RikkaApps/Shizuku](https://github.com/RikkaApps/Shizuku) | Apache-2.0 · Kotlin · 2025-06-18 · 34,839 · **dormant** | Lets normal apps call system APIs with adb rights. | Optional "no artificial limits" lever. Upstream has had no push for 17 months; the fork thedjchi/Shizuku (2026-07-15) is more recent. |
| [tytydraco/LADB](https://github.com/tytydraco/LADB) | API: NOASSERTION, not classified · Kotlin · 2026-07-26 · 2,490 | adb shell on the phone itself. | Granting permissions without a PC. |
| [MuntashirAkon/AppManager](https://github.com/MuntashirAkon/AppManager) | GPL-3.0-or-later per README SPDX line (API: NOASSERTION) · Java · 2026-11-03 · 9,745 | Package manager and viewer. | Inspect app ops, permissions and battery settings of our own app. |

Platform fact relevant to sideloading [V, Google support page "Understanding Android developer verification",
https://support.google.com/android-developer-console/answer/16561738]: "Effective September 30, 2026, these new
developer verification protections will go live for users in Indonesia, Singapore, and Thailand" and "Verification
capability will be expanded globally for all apps on certified Android devices in 2027." The same page: "Developers
and power users can still use Android Debug Bridge (ADB) to build, test, and install modified or unverified apps on
their own devices". It also describes a free limited distribution account ("up to 20 devices"). So `adb install`
stays open; installing a GitHub Releases APK by tapping it on the phone may need developer verification or the
advanced flow once enforcement reaches the owner's country [I].

### 3.11 Debugging Bluetooth on Android

| Project | Licence · lang · push · stars · flag | What it is | What to borrow |
|---|---|---|---|
| [google/bumble](https://github.com/google/bumble) | Apache-2.0 · Python · 2026-11-26 · 595 | Bluetooth stack in Python for a USB dongle. README: supports "GAP, L2CAP, ATT, GATT, SMP, SDP, RFCOMM, HFP, HID and A2DP". | `examples/run_hfp_handsfree.py` (a fake headset for path A), `examples/run_advertiser.py` (send BTHome frames for path B), `examples/keyboard.py`, `run_hid_device.py`, `run_avrcp.py` (media keys for path C), `run_asha_sink.py` and `run_hap_server.py` (a fake hearing aid). File names verified; I did not run them. |
| [mauricelam/btsnoop-extcap](https://github.com/mauricelam/btsnoop-extcap) | MIT · Rust · 2025-12-23 · 8 · slow | Wireshark extcap plugin "for capturing bluetooth packets from Android devices" over adb. | Live HCI view in Wireshark while pressing the remote. |
| [kellyiscute/hciscope](https://github.com/kellyiscute/hciscope) | GPL-3.0 · Kotlin · 2026-10-06 · 4 | "Live Bluetooth HCI packet viewer for rooted Android phones", exports btsnoop. | On-phone view; needs root or Shizuku. |
| [duhow/nobita](https://github.com/duhow/nobita) | no licence · Kotlin · 2026-09-20 · 5 | Records Bluetooth traffic on the phone with Shizuku. | Capture without a PC. |
| [wireshark/wireshark](https://github.com/wireshark/wireshark) | GPL-2.0 · C · 2026-11-27 · 10,335 | Read-only mirror of the GitLab repo. | Reads `btsnoop_hci.log` from `adb bugreport`. |
| [nccgroup/Sniffle](https://github.com/nccgroup/Sniffle) | GPL-3.0 · Python · 2025-09-25 · 1,211 · **dormant** | "A sniffer for Bluetooth 5 and 4.x LE". | Sees what the remote sends over the air, independent of the phone; needs extra radio hardware [I]. |
| [hbldh/bleak](https://github.com/hbldh/bleak) | MIT · Python · 2026-11-21 · 2,584 | Python BLE client. | Ten-line laptop scanner to confirm the remote advertises. |
| [BLE-Research-Group/MetaRadar](https://github.com/BLE-Research-Group/MetaRadar) | GPL-3.0 · Kotlin · 2026-01-22 · 1,601 · slow | Android BLE environment monitor with filters and background scanning. | Ideas only: a long-running scanner app. |
| [Genymobile/scrcpy](https://github.com/Genymobile/scrcpy) | Apache-2.0 · C · 2026-11-26 · 155,909 | Mirror and control the phone from a PC. | Operate the phone during tests without touching it. |
| [google/perfetto](https://github.com/google/perfetto) | Apache-2.0 · C++ · 2026-11-27 · 6,852 | System tracing. | Wake locks, alarms and scheduling during Doze. |
| [F0x1d/LogFox](https://github.com/F0x1d/LogFox) | GPL-3.0 · Kotlin · 2026-10-15 · 1,399 | Logcat reader on the device. | Read logs after a walk without a PC. |
| [bluez/bluez](https://github.com/bluez/bluez) | GPL-2.0 · C · 2026-11-26 · 1,189 | Linux Bluetooth stack with `btmon`. | Laptop-side monitor. |
| [nordicsemi/Android-nRF-Connect](https://github.com/nordicsemi/Android-nRF-Connect) | no licence · 2025-08-28 · 1,736 | "Documentation and issue tracker for nRF Connect for Android." | Not open source. Still the usual scanner app. |
| [google/battery-historian](https://github.com/google/battery-historian) | Apache-2.0 text · Go · 2019-07-15 · 5,395 · **archived** | Bugreport analyser. | Archived; use Perfetto. |
| [seemoo-lab/internalblue](https://github.com/seemoo-lab/internalblue) | no licence file detected · Python · 2024-08-21 · 794 · **dormant** | Firmware-level Bluetooth experiments for Broadcom chips. | Not needed. |

---

## 4. What our app could borrow, by input path

**Path A, hearing-aid tap to the default assistant**
- Manifest and metadata pattern: home-assistant/android (Apache-2.0), hermes-relay (MIT), alfatherapp (MIT),
  openclaw-assistant (MIT), Hy4ri/hermes-mobile (Apache-2.0). All five declare a `VoiceInteractionService` with
  `supportsLaunchVoiceAssistFromKeyguard="true"` (hermes-relay, alfatherapp, openclaw-assistant and Home Assistant
  verified in `res/xml/*.xml`; Hy4ri verified for the service declaration only).
- Home Assistant, Dicio and airdots-double-tap register an activity for `android.intent.action.VOICE_COMMAND`.
  That is the intent the Bluetooth module raises when a headset asks for voice recognition [V]: AOSP
  `packages/modules/Bluetooth`, tag `android-17.0.0_r1`,
  `android/app/src/com/android/bluetooth/hfp/HeadsetSystemInterface.java` line 350
  `Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);` followed by `mHeadsetService.startActivity(intent)`;
  the same branch has `vap/VapServerService.kt` line 147 doing the same for LE Audio with
  `EXTRA_PROFILE = BluetoothProfile.LE_AUDIO`. So our first prototype should register both a
  VoiceInteractionService and a `VOICE_COMMAND` activity and log which one the Phonak tap reaches. Whether the
  Phonak tap sends that request at all is not known [U].
- GPTWake's finding that the default assistant's voice interaction service can capture audio under the keyguard,
  while another app cannot, is the strongest argument for owning the assistant role ourselves [V as the author's
  measurement].
- Test rig without the hearing aid: Bumble `examples/run_hfp_handsfree.py` on a USB dongle [I].

**Path B, Shelly BLU RC Button 4 ZB**
- Parser and device IDs: SaraButton `BthomeParser.java`, cross-checked with Bluetooth-Devices/bthome-ble.
- Scan strategy to test first: `PendingIntent` scan with a `ScanFilter` on the device address (SaraButton) or on
  service data UUID `0xFCD2` (bthome-bridge), `SCAN_MODE_LOW_LATENCY`.
- Watchdogs worth copying as test hypotheses: SaraButton refreshes scans every 4 minutes and holds a renewable
  partial wake lock; bthome-bridge renews every 20 minutes and restarts after 60 s of silence through
  `AlarmManager.setExactAndAllowWhileIdle`. They disagree with each other, which tells us nobody has a settled
  answer; our own Doze soak test decides.
- Encryption: bthome-bridge `BTHomeCripto.kt` (Kotlin, MIT file) or port from bthome-ble
  `bthome_v2_encryption.py`.
- Test senders: falkorichter/open-bthome-button-app on a second phone, Bumble `run_advertiser.py`, a Flipper with
  steanlab/BLE-Beacon.

**Path C, media buttons**
- Soundscape-Android shows the working recipe: a Media3 `MediaSession` backed by a dummy `SimpleBasePlayer`, and
  `onMediaButtonEvent` mapped to menu actions.
- The Android rule that makes this fragile is quoted in section 5.4.
- A DIY remote that also covers path B: ZMK with the zmk-bthome module.

**Audio menu and speech**
- Menu model: Soundscape `AudioMenu.kt` (stack of levels, NEXT wraps, SELECT enters or executes, "Main Menu" as last
  child of every submenu, earcons for enter and exit, no inactivity timeout).
- STT, TTS and VAD: sherpa-onnx sample apps; HermiVoiceApp for a German configuration in Apache-2.0.
- Headset or hearing-aid microphone routing: jooray/android-bluetooth-microphone `SKILL.md` first, then i-hear-you
  `docs/VOICE-NOTES.md`, alfatherapp `LiveCall.kt`, hermes-vox `CommRoute.kt` (ideas), third-ear `AndroidSources.kt`
  (ideas), hark-app `HearingDeviceMonitor.kt`.

**Debuggability**
- GPTWake `testkit.sh` (logcat tag conventions, `dumpsys batterystats --reset`, `dumpsys meminfo`).
- hermes-vox key=value log lines (`event=comm-route action=select-failed err=...`).
- hermes-relay `diagnostics/DiagnosticsLog.kt` and `reliability/ReliabilityReport.kt`.
- btsnoop-extcap or hciscope for HCI, phonak-ha `tools/sonova_decode.py` as an example of decoding a vendor
  protocol from a snoop log.

---

## 5. Evidence: excerpts and constants

### 5.1 home-assistant/android, `app/src/main/AndroidManifest.xml` (default branch, read 2026-11-27)

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE"/>
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
<uses-permission android:name="android.permission.MANAGE_ASSISTANT_AUDIO"/>
...
<activity android:name=".assist.AssistActivity"
    android:exported="true"
    android:launchMode="singleTask"
    android:showWhenLocked="true" ...>
    <intent-filter>
        <action android:name="android.intent.action.ASSIST" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
<activity-alias
    android:name=".assist.VoiceCommandIntentActivity"
    android:targetActivity=".assist.AssistActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VOICE_COMMAND" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity-alias>
<!-- Voice Interaction Service for being the system's default assistant -->
<service
    android:name=".assist.service.AssistVoiceInteractionService"
    android:exported="true"
    android:foregroundServiceType="microphone"
    android:permission="android.permission.BIND_VOICE_INTERACTION">
```

`app/src/main/res/xml/voice_interaction_service.xml`:

```xml
<!-- recognitionService is required by the framework metadata parser even though wake-word logic stays in the VoiceInteractionService. -->
<voice-interaction-service ...
    android:sessionService="...assist.service.AssistVoiceInteractionSessionService"
    android:recognitionService="...assist.service.AssistRecognitionService"
    android:supportsAssist="true"
    android:supportsLaunchVoiceAssistFromKeyguard="true"
    android:settingsActivity="...settings.SettingsActivity" />
```

`AssistVoiceInteractionService.kt` (423 lines) overrides `onLaunchVoiceAssistFromKeyguard()` (line 148), calls
`startForeground(..., ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)` (line 253-256) and `showSession(args, 0)`
(line 275). I did not check what `MANAGE_ASSISTANT_AUDIO` grants [U].

### 5.2 Soundscape-Android audio menu

`shared/src/commonMain/kotlin/org/scottishtecharmy/soundscape/services/mediacontrol/AudioMenu.kt`:

```kotlin
/**
 * AudioMenu provides a hierarchical, navigable audio menu controlled by media buttons.
 *
 * - NEXT    : advance to next item at the current level (wraps at end)
 * - SELECT  : enter a sub-menu, or execute a leaf action
 *
 * PREVIOUS is not menu navigation - AudioMenuMediaControls gives it to [cycleCalloutDetail],
 * which is wanted more often than stepping backwards through a menu that wraps anyway.
 *
 * There is no inactivity timeout — the current position is remembered until changed.
 */
```

`app/src/main/java/org/scottishtecharmy/soundscape/services/mediacontrol/SoundscapeMediaSessionCallback.kt` handles
`KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS`, `KEYCODE_MEDIA_SKIP_FORWARD`,
`KEYCODE_MEDIA_SKIP_BACKWARD` in `onMediaButtonEvent`, and ignores key-up "to prevent double-firing". The author
notes: "So far I've only seen KEYCODE_MEDIA_PLAY_PAUSE, KEYCODE_MEDIA_PREVIOUS and KEYCODE_MEDIA_NEXT, though that
may be specific to my phone." User documentation: `docs/users/help-using-media-controls.*.md`.

### 5.3 GPTWake README, "System constraints" (author's measurements, Android 14+, a tablet)

| Constraint | Resolution | Measured |
|---|---|---|
| ChatGPT cannot obtain the microphone under keyguard | ChatGPT must remain the system default assistant, so its voice interaction service runs at `PROC_STATE_PERSISTENT` | Taking the role away yields `RECORD_AUDIO duration=0`; restoring it restores capture |
| Background activity launch is blocked by BAL | `SYSTEM_ALERT_WINDOW` exemption | Without it: `Background activity launch blocked!`; with it: allowed |
| A microphone foreground service cannot be started from the background | A transparent `showWhenLocked` shim activity supplies a visible moment | Succeeds with the screen off under a secure keyguard, without turning the screen on |
| No automatic recovery after reboot | `LOCKED_BOOT_COMPLETED` to shim to FGS, inside the 20 second temporary allowlist | Listening 1.42 s before the first unlock |

Also from the README: "`startActivity()` does not throw when BAL blocks it, so success must be confirmed through a
side channel rather than assumed." These are one developer's results on one device. They are hypotheses for our
Pixel, not facts about it.

### 5.4 Media buttons: the platform rule, and a pattern that probably does not work

developer.android.com/media/legacy/media-buttons, "Finding a media session" [V]:

> If you are running Android 8.0 (API level 26) or later, the system tries to find the last app with a MediaSession
> that played audio locally. If the session is still active Android sends the event directly to it. Otherwise, if
> the session is not active and it has a mediabutton receiver, Android sends the event to the receiver, which will
> restart the session and so it can receive the event. [...] If the session does not have a media button receiver
> the system discards the media button event and nothing will happen.

openclaw-assistant registers only a manifest receiver and no `MediaSession` (grep for `MediaSession` in
`app/src/main/java` finds nothing):

```xml
<receiver android:name=".receiver.MediaButtonReceiver" android:exported="true">
    <intent-filter android:priority="1000">
        <action android:name="android.intent.action.MEDIA_BUTTON" />
    </intent-filter>
</receiver>
```

Given the rule above, this receiver should not get button presses on current Android unless the app was the last one
to play audio through a session [I]. Soundscape's dummy-player session is the pattern to follow. Path C will also
lose the buttons whenever another app, for example a music player, played audio more recently [I].

### 5.5 Hearing-aid microphone: the constants and the numbers

`third-ear/app/src/main/kotlin/app/thirdear/audio/BluetoothChoice.kt` uses private copies of three device types:

```kotlin
private const val TYPE_BLUETOOTH_SCO = 7
private const val TYPE_BLE_HEADSET = 26
private const val TYPE_BLE_HEARING_AID = 33
```

Type 33 is real, but only from Android 17 [V]. AOSP `frameworks/base`,
`media/java/android/media/AudioDeviceInfo.java`:

- branch `android17-release`:
  ```java
  /** A device type describing a Bluetooth Low Energy (BLE) hearing aid. */
  @FlaggedApi(FLAG_BLE_HEARING_AID_DEVICE)
  public static final int TYPE_BLE_HEARING_AID = 33;
  ```
  followed by `TYPE_BLE_CENTRAL = 34` and `TYPE_BLE_CENTRAL_BROADCAST = 35`.
- branches `android16-qpr2-release` and `refs/heads/main`: the list ends at `TYPE_MULTICHANNEL_GROUP = 32`.

Method note for everyone on the project: `refs/heads/main` on android.googlesource.com does **not** contain the
Android 17 APIs. I first concluded from `main` that the constant was invented, which was wrong. Read
`android17-release` (tag `android-17.0.0_r1`) for a phone on Android 17.

`TYPE_HEARING_AID = 23` ("A device type describing a Hearing Aid.") is the older type, present in all three
branches.

For our Phonak the LE types may not matter: phonak-ha `PROTOCOL.md` says the aid is a "classic (BR/EDR) 'R-Phonak
hearing aid' for audio/calls", so the microphone path is probably `TYPE_BLUETOOTH_SCO`; confirm on the device [I].

Numbers from jooray/android-bluetooth-microphone `SKILL.md` (author's measurements, "Pixel 10 Pro Fold, Android 17,
with LE Audio hearing aids, plus one classic-SCO headset"):

- "Wait for `OnCommunicationDeviceChangedListener` to report it (timeout about 3 s), then sleep about 500 ms more."
- "After `startRecording()`, expect the Bluetooth microphone to take **2.5 to 3 s** to come up (we measured 1.7 to
  3.5 s on LE Audio)." and "SCO routing took about 550 ms."
- "Start a silence primer on an `AudioTrack` (`USAGE_VOICE_COMMUNICATION`) right after `startRecording()`. Without
  playback to the headset, some LE Audio HALs deliver only zeros for the whole recording."
- "Give the first sound a generous deadline (8 s worked). Then fall back to the phone mic once, with an audible cue".
- Section "Background recording (headset button, screen off)": "Use a foreground service with
  `foregroundServiceType="microphone"`", "On Android 14 and later that type can only be started while the app is
  visible (a while-in-use permission). After a reboot or an app update the service can run but cannot record until
  the user opens the app once.", "From the background you may write to the clipboard. You may not start activities."
- Verification recipe: "record, `adb shell input keyevent 26` (or let the phone doze), wait 30 s, stop, then compare
  the file's duration with wall time and look for runs of exact zeros, e.g.
  `ffmpeg -i f.m4a -af silencedetect=n=-120dB:d=0.5 -f null -`".

The "open the app once after reboot" limit is exactly what owning the assistant role may avoid (GPTWake's first
table row and alfatherapp's comment "The current assistant may start a microphone service from the background, so
the call runs here over any app, even locked."). Both are other people's observations; our prototype A must show it
on the Pixel [I].

### 5.6 BLE scan settings used by the two Shelly readers

SaraButton `BleScanManager.java`, PendingIntent scan:

```java
static final ParcelUuid BTHOME_UUID = ParcelUuid.fromString("0000fcd2-0000-1000-8000-00805f9b34fb");
...
new ScanSettings.Builder()
    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
    .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH | ScanSettings.CALLBACK_TYPE_MATCH_LOST)
    .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
    .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
    .setReportDelay(0)
```

with the comment "Address filters handle vendor differences in how BTHome service data is exposed and let both
configured remotes wake the app." `MonitoringService.java`: `SCAN_REFRESH_MILLIS = 4L * 60L * 1_000L`,
`WAKE_LOCK_TIMEOUT_MILLIS = SCAN_REFRESH_MILLIS + 60_000L`, `RETRY_DELAY_MILLIS = 10_000L`; foreground service type
`connectedDevice|location`. `BleScanReceiver` uses `goAsync()` and keeps the broadcast alive for 2 s.

bthome-bridge `ble/GestorScan.kt`: `SCAN_MODE_LOW_LATENCY`, `CALLBACK_TYPE_ALL_MATCHES`, service-data filter with an
empty mask, `TEMPO_INATIVIDADE_MS = 60_000L`, `INTERVALO_VERIFICACAO_MS = 30_000L`, `RENOVACAO_MS = 20 * 60_000L`.
The author's reason, translated from the Portuguese comment: Android turns long-running BLE scans (about 30 minutes)
into "opportunistic" scans, where the app only receives advertisements while another app is scanning, so the scan is
renewed well before that limit. That is the author's explanation; I did not verify it against AOSP [U].

Shelly's own page for the remote [V]: "Button events are single advertisements with a duration of 1500ms", beacon
interval "beacon mode: 12s, normal mode: 15min", objects `0x01` battery, `0x3A` button (one per button), `0xF0` device
type id, `0xF1`/`0xF2` firmware version.

### 5.7 KeyMapper and the screen-off limit

`base/src/main/res/values/strings.xml`: "This key map cannot be detected when the screen is off because the trigger
was not recorded with Expert Mode. Re-record the trigger with Expert Mode to enable screen-off remapping." and
"We've introduced a *free* feature called Expert Mode that replaces the old screen off remapping option."

---

## 6. Licence notes for an Apache-2.0 repository

- Safe to copy with attribution: home-assistant/android, Hy4ri/hermes-mobile, HermiVoiceApp, sherpa-onnx, bumble,
  androidx/media, AltBeacon, ALLTERCO scripts (all Apache-2.0); hermes-relay, alfatherapp, kyllene,
  openclaw-assistant, SaraButton, phonak-ha, i-hear-you, android-bluetooth-microphone, airdots-double-tap,
  BlindPilot-Android, bthome-ble, zmk, zmk-bthome, whisper.cpp,
  silero-vad, Soundscape-Android own code (all MIT); hark-app, Nordic libraries (BSD-3-Clause).
- Ideas only: hermes-vox, third-ear, AssistantVoiceRecorder, Dicio, KeyMapper, Obtainium, theengs, piper1-gpl,
  AntennaPod, Voice, Gadgetbridge (AGPL), LibrePods, MetaRadar, hciscope.
- Read only, no licence: Hermes-Walkie-Talkie, BTHOME2_decoder, open-bthome-button-app, BTHome_C3Button, NarfButt,
  ESP32-BLE-Keyboard, nobita, openhab-bthome, wakewordapp.
- Unclear, ask or check before use: bthome-bridge (MIT LICENSE file, but the README ends with "Copyright © Altino
  Natalino. Todos os direitos reservados."), GPTWake (Apache-2.0 notice only), flic2lib-android (custom permissive
  text), moonshine, Espruino, LADB.
- Model licences are separate from code licences. I did not check any speech model licence [U].

---

## 7. Corrections and additions to the inherited planning notes

1. SaraButton is written in Java and supports three Shelly models, not only `SBBT-104CUS`.
2. The notes say a remote that does paths B and C at the same time is "bislang nicht verifiziert". There is now a
   concrete open candidate: ZMK plus zmk-bthome (HID over the connection, BTHome via a second advertising set).
   Still untested with our phone.
3. The notes list two Hermes clients. There are several native Kotlin clients that already hold the assistant role
   (hermes-relay, alfatherapp, kyllene, Hy4ri/hermes-mobile, openclaw-assistant). Reading them before writing
   prototype A will save time.
4. The notes treat GPTWake mainly as a wake-word app we do not want. Its constraints table is directly relevant to
   our blocker "Hörgeräte-Mikro als Eingang bei gesperrtem Pixel".
5. The notes do not mention any existing audio menu. Soundscape-Android has one, MIT licensed.
6. phonak-ha gives no tap event, only a tap-control on/off setting (`PARAM_TAP_CONTROL = 0x00FC`). The notes already
   say "kein Beleg für ... Empfang eines konkreten Doppeltipp-Triggers"; this is now confirmed from the source.
7. The notes name no project for the blocker "Hörgeräte-Mikro als Eingang bei gesperrtem Pixel". There are two
   MIT ones by the same author, measured on a Pixel 10 Pro Fold: jooray/android-bluetooth-microphone and
   jooray/i-hear-you.
8. Nothing in the notes about Google's developer verification. `adb install` is explicitly exempt; tap-to-install
   from GitHub Releases may be affected from 2027 (section 3.10).

---

## 8. Dropped, not found, or flagged

- `google/eyes-free`: HTTP 404. Only third-party mirrors of the old Eyes-Free project exist (for example
  RudolfWeeber/eyes-free, last push 2013). Dropped.
- `bumble-project/bumble`: 404; the project is `google/bumble`.
- `LibrePods/librepods`: 404; the project is `librepods-org/librepods`.
- Redirects: `Stypox/dicio-android` is now `DicioTeam/dicio-android`; `sds100/KeyMapper` is
  `keymapperorg/KeyMapper`; `ggerganov/whisper.cpp` is `ggml-org/whisper.cpp`; `NordicSemiconductor/*` is
  `nordicsemi/*`; `usefulsensors/moonshine` is `moonshine-ai/moonshine`; `rainxchzed/Github-Store` is
  `komi-store/komi-store`; `toverainc/willow` is `HeyWillow/willow`.
- No open project found that receives a Phonak tap gesture. The closest thing to an assistant driven from a hearing
  aid is jooray/i-hear-you, for GN/ReSound aids only.
  The repository search for "phonak" returned 30 results; only phonak-ha (and its older GPL-3.0 sibling
  ijustlikeit/Phonak_ha, last push 2026-10-11) and the self-fitting write-up are relevant.
- No open Android app found that is designed for media-button triggering with the screen off as its main purpose.
  Repository searches for that combination returned nothing useful (see section 9).
- Dormant or archived, listed above with a flag: T-vK/ESP32-BLE-Keyboard, dmamontov/esphome-blekeyboard
  (2024-02-23), rhasspy/piper, android/uamp, google/battery-historian, seemoo-lab/internalblue,
  gkonovalov/android-vad, RikkaApps/Shizuku, just-ai/aimybox-android-assistant, brandall76/Saiy-PS (2018),
  microsoft/soundscape, jeffrizzo/bthome-zephyr, agittins/NarfButt, GraceGRD/flic-twist-bthome-v2,
  Edw590/VISOR, nccgroup/Sniffle, argmaxinc/WhisperKitAndroid, rhasspy/wyoming-satellite (archived),
  PunchThrough/ble-starter-android (2024-05-31), dariuszseweryn/RxAndroidBle (2025-06-06),
  ElishaAz/Sayboard (2025-07-01), OpenEarable/open-earable (2024-10-18), stanford-oval/almond-android (archived).
- Verified to exist but left out of the tables as too far from our use: theengs/gateway, 1technophile/OpenMQTTGateway,
  esphome/esphome, custom-components/ble_monitor, Ernst79/bleparser, pvvx/ATC_MiThermometer, rbaron/b-parasite,
  livekit/client-sdk-android, pipecat-ai/pipecat-client-android, open-webui/open-webui, google/oboe, google/liblc3,
  espeak-ng/espeak-ng, RHVoice/RHVoice, HeyWillow/willow, OHF-Voice/linux-voice-assistant,
  esphome/home-assistant-voice-pe, Gamer92000/echo-dot-assist, panel-assistant/android, knoop7/Ava-Pro,
  niedev/RTranslator, Picovoice/porcupine, f-droid/fdroidclient, Droid-ify/client.

---

## 9. Searches and checks that were run

Environment notes: the installed `gh` has no `gh search` subcommand, so all searches went through
`gh api -X GET search/repositories -f q=...` and `search/code`. The scratch directories are shared with other
research agents of the same workflow; some clones I read (`i-hear-you`, `android-bluetooth-microphone`,
`airdots-double-tap`, `aosp-bluetooth-a17`) were made by them. I verified each of those repositories myself with
`gh api repos/...` and by reading the files quoted. Two queries, `volume button screen off android trigger` and
`bluetooth shutter remote android trigger`, hit the search rate limit (30 requests per minute, shared with the other
agents) and returned nothing; the first was not repeated, the second was rerun as query 36.

Repository searches (`search/repositories`, best match unless "stars" is noted):

1. `hermes agent android`
2. `hermes-agent mobile`
3. `hermes agent voice` (stars)
4. `topic:hermes-agent language:kotlin`
5. `topic:hermes-agent android`
6. `bthome android`
7. `bthome language:kotlin`
8. `bthome language:java`
9. `bthome parser` (stars)
10. `bthome` (stars)
11. `shelly blu` (stars)
12. `shelly blu button android`
13. `android voice assistant VoiceInteractionService`
14. `android default assistant LLM voice language:kotlin` (stars)
15. `voice assistant android open source language:kotlin stars:>100` (stars)
16. `android assistant lock screen voice`
17. `home assistant android voice satellite` (stars)
18. `wyoming satellite android` (stars)
19. `eyes-free android` (stars)
20. `audio menu android blind`
21. `blind android app talkback language:kotlin` (stars)
22. `android screen off voice interface accessibility blind`
23. `audio-only interface android` (stars)
24. `topic:blind topic:android` (stars)
25. `topic:visually-impaired android` (stars)
26. `spoken menu android text to speech navigation buttons`
27. `android media button trigger screen off`
28. `bluetooth headset button assistant android language:kotlin`
29. `media button mapper android` (stars)
30. `android headset button remap` (stars)
31. `bluetooth remote button android trigger action screen off`
32. `push to talk bluetooth button android` (stars)
33. `headset button android assistant trigger` (stars)
34. `bluetooth media button android app trigger` (stars)
35. `android MediaSession media button background trigger kotlin`
36. `bluetooth shutter remote android` (stars)
37. `phonak` (stars)
38. `hearing aid android bluetooth` (stars)
39. `hearing aids ASHA` (stars)
40. `sonova hearing aid` (stars)
41. `bluetooth hearing aid microphone android` (stars)
42. `hearing aid tap control`
43. `hearing aid` (stars)
44. `hearing aid app android language:kotlin` (stars)
45. `LE Audio hearing aid` (stars)
46. `screen reader android` (stars)
47. `voice menu android TextToSpeech eyes free hands free headset buttons`
48. `audio first android app headphones buttons navigation spoken`
49. `android accessibility voice control open source` (stars)
50. `piper tts android` (stars)
51. `sherpa-onnx android` (stars)
52. `silero vad android` (stars)
53. `whisper android offline speech recognition language:kotlin` (stars)
54. `android tts engine neural offline` (stars)
55. `wake word android` (stars)
56. `ble hid media remote esp32` (stars)
57. `nrf52 ble remote hid buttons zephyr` (stars)
58. `esphome ble keyboard` (stars)
59. `zmk macropad remote media nrf52840` (stars)
60. `puck.js hid media button` (stars)
61. `nrf52840 bthome button` (stars)
62. `esp32 ble hid consumer control nimble deep sleep remote` (stars)
63. `theengs` (stars)
64. `btsnoop` (stars)
65. `ble scanner android language:kotlin stars:>50` (stars)
66. `bluetooth hci snoop android analyzer` (stars)
67. `ble advertisement simulator beacon android advertise test` (stars)
68. `obtainium` (stars)
69. `android apk updater github releases self-update library` (stars)
70. `ble button android background PendingIntent scan`
71. `itag ble button android` (stars)

Queries 14, 20, 22, 23, 26, 28, 30, 31, 33, 34, 35, 40, 42, 47, 48, 57, 59, 60, 62, 66, 67 and 70 returned no results.
GitHub repository search matches names, descriptions and topics only, so long descriptive queries find little; the
useful hits came from short queries and from reading code.

Code searches (`search/code`):

1. `repo:Scottish-Tech-Army/Soundscape-Android MediaSession`
2. `repo:Scottish-Tech-Army/Soundscape-Android AudioMenu`
3. `repo:keymapperorg/KeyMapper screen off getevent`
4. `supportsLaunchVoiceAssistFromKeyguard="true" language:XML` (241 results)

Direct checks:

- `gh api repos/<owner>/<repo>` for more than 200 repositories (existence, licence, language, `pushed_at`, stars,
  archived), plus `releases/latest`, `contents/...` and `readme` for the ones quoted.
- Local grep over shallow clones for `VoiceInteractionService`, `BIND_VOICE_INTERACTION`, `ACTION_ASSIST`,
  `VOICE_COMMAND`, `MediaSession`, `MEDIA_BUTTON`, `setCommunicationDevice`, `startBluetoothSco`, `TYPE_BLE_HEADSET`,
  `BluetoothLeScanner`, `startScan(`, sherpa/whisper/silero/vosk/openwakeword.
- Web: developer.android.com/media/legacy/media-buttons; support.google.com/android-developer-console/answer/16561738;
  shelly-api-docs.shelly.cloud/docs-ble/Devices/BLU_ZB/button4_ZB/; one web search for the developer-verification
  timeline.
- AOSP via android.googlesource.com (`?format=TEXT`): `frameworks/base/media/java/android/media/AudioDeviceInfo.java`
  on `main`, `android16-qpr2-release` and `android17-release`;
  `packages/modules/Bluetooth/android/app/src/com/android/bluetooth/hfp/HeadsetSystemInterface.java` on `main`.
- Codeberg API for Freeyourgadget/Gadgetbridge.

Nothing from any cloned repository was built or run.

---

## 10. Draft for the README section "Related projects"

English draft; translate if the README ends up in German. Every link was verified on 2026-11-27.

```markdown
## Related projects

Lara im Ohr stands on other people's work. If you wear hearing aids and want to build something similar, start here.

### Hearing aids as an interface
- [jooray/i-hear-you](https://github.com/jooray/i-hear-you) (MIT): Android remote for GN/ReSound aids. The aid's own
  button starts a voice note that is recorded through the aids, transcribed on the phone and sent to a webhook.
- [jooray/android-bluetooth-microphone](https://github.com/jooray/android-bluetooth-microphone) (MIT): field guide to
  recording from hearing-aid and headset microphones on Android, including with the screen off.
- [ijustlikeit/phonak-ha](https://github.com/ijustlikeit/phonak-ha) (MIT): Phonak volume, program, mute and battery
  from Home Assistant, with notes on the Sonova Bluetooth protocol.
- [thivyan-studios/hark-app](https://github.com/thivyan-studios/hark-app) (BSD-3-Clause): phone as a remote
  microphone that streams to hearing aids, with offline captions.
- [thewierdnut/asha_pipewire_sink](https://github.com/thewierdnut/asha_pipewire_sink) (Unlicense): ASHA hearing-aid
  streaming on Linux.
- [rscircus/awesome-hearing](https://github.com/rscircus/awesome-hearing): a list of more hearing-aid projects.

### Assistants you can call without looking
- [home-assistant/android](https://github.com/home-assistant/android) (Apache-2.0): Assist as the default Android
  assistant, also from the lock screen.
- [DicioTeam/dicio-android](https://github.com/DicioTeam/dicio-android) (GPL-3.0): fully offline voice assistant.
- [suddenBook/GPTWake](https://github.com/suddenBook/GPTWake): starts a voice session on a locked, screen-off device
  and documents the Android limits it had to work around.
- [Scottish-Tech-Army/Soundscape-Android](https://github.com/Scottish-Tech-Army/Soundscape-Android) (MIT): audio
  navigation for blind people, with a spoken menu operated by headset buttons.

### Hermes Agent and its Android clients
- [NousResearch/hermes-agent](https://github.com/NousResearch/hermes-agent) (MIT): the agent Lara talks to.
- [Codename-11/hermes-relay](https://github.com/Codename-11/hermes-relay) (MIT),
  [Hy4ri/hermes-mobile](https://github.com/Hy4ri/hermes-mobile) (Apache-2.0),
  [Giton22/kyllene](https://github.com/Giton22/kyllene) (MIT),
  [JusticeFist/alfatherapp](https://github.com/JusticeFist/alfatherapp) (MIT): native clients that can take the
  Android assistant role.
- [chezgoulet/hermes-vox](https://github.com/chezgoulet/hermes-vox) (GPL-3.0),
  [virse/HermiVoiceApp](https://github.com/virse/HermiVoiceApp) (Apache-2.0),
  [yuga-hashimoto/openclaw-assistant](https://github.com/yuga-hashimoto/openclaw-assistant) (MIT): voice-first clients.
- [serrebidev/BlindPilot-Android](https://github.com/serrebidev/BlindPilot-Android) (MIT): chat client built for
  screen-reader users.
- [darth-modder/hermes-mobile-client](https://github.com/darth-modder/hermes-mobile-client) (MIT): Expo thin client.

### Bluetooth buttons and remotes
- [OferTiber/SaraButton](https://github.com/OferTiber/SaraButton) (MIT): Shelly BLU buttons trigger phone calls; an
  accessibility aid and the closest relative of our remote path.
- [altinojoao/bthome-bridge](https://github.com/altinojoao/bthome-bridge): Shelly BLU button to HTTP, working with
  the screen off.
- [home-assistant/bthome.io](https://github.com/home-assistant/bthome.io) and
  [Bluetooth-Devices/bthome-ble](https://github.com/Bluetooth-Devices/bthome-ble) (MIT): the BTHome format and its
  reference parser.
- [50ButtonsEach/flic2lib-android](https://github.com/50ButtonsEach/flic2lib-android): official SDK for Flic buttons.
- [zmkfirmware/zmk](https://github.com/zmkfirmware/zmk) with
  [Genteure/zmk-bthome](https://github.com/Genteure/zmk-bthome) (MIT): build your own remote that sends media keys
  and BTHome button events.
- [keymapperorg/KeyMapper](https://github.com/keymapperorg/KeyMapper) (GPL-3.0): remap headset and hardware buttons.

### Speech on the device
- [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache-2.0): speech recognition, speech synthesis and
  voice activity detection with Android examples.
- [ggml-org/whisper.cpp](https://github.com/ggml-org/whisper.cpp) (MIT), [snakers4/silero-vad](https://github.com/snakers4/silero-vad) (MIT),
  [OHF-Voice/piper1-gpl](https://github.com/OHF-Voice/piper1-gpl) (GPL-3.0).

### Installing, updating, debugging
- [ImranR98/Obtainium](https://github.com/ImranR98/Obtainium) (GPL-3.0): installs and updates this app from GitHub
  Releases.
- [google/bumble](https://github.com/google/bumble) (Apache-2.0): a Bluetooth stack in Python that can play a
  headset, a hearing aid, a keyboard or a beacon for testing.
- [mauricelam/btsnoop-extcap](https://github.com/mauricelam/btsnoop-extcap) (MIT): watch the phone's Bluetooth
  traffic live in Wireshark.
- [Genymobile/scrcpy](https://github.com/Genymobile/scrcpy) (Apache-2.0): see and control the phone from a computer.
```
