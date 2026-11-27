# Related projects for people who build things with hearing aids

Research date: 2026-11-27. Scope: open projects and write-ups for someone who wears hearing aids and wants to build software or hardware around them. Feeds the "related projects" section of the README and tells us what the app can learn from them.

## 0. How to read this

Marks used on statements:

- **[V] verified**: seen today in a primary source (GitHub API response, a file in the repository, AOSP source, vendor page). The source is named.
- **[I] inferred**: reasoned from verified sources, not directly stated by one.
- **[U] unknown**: could not be established.

Metadata (licence, language, last push, stars) for every repository below comes from `gh api repos/<owner>/<name>` on 2026-11-27. "Devices" is what the project's own README says it was tested with; I did not test anything on hardware. "Unmaintained" means archived, or no push since 2025-05-27 (18 months).

Tooling note: the local `gh` is 2.4.0 and has no `gh search`; all searches went through `gh api search/repositories` and `gh api search/code`.

## 1. What matters most (short)

1. **There is a small, very recent cluster of exactly-our-kind projects, all from Oct-Nov 2026**: `jooray/android-bluetooth-microphone`, `jooray/i-hear-you`, `jooray/third-ear`, `mptfire/bt-mic`, `ijustlikeit/phonak-ha`. They are young, single-author and each tested on one or two devices, but they document the same problems we will hit. [V]
2. **`jooray/android-bluetooth-microphone` is the single most useful document for our microphone path.** It was measured on a Pixel 10 Pro Fold with Android 17, LE Audio hearing aids and one classic SCO headset, and gives a tested recipe for getting Android to record from the Bluetooth microphone with the screen off. [V] (SKILL.md, "Device-specific notes")
3. **`jooray/i-hear-you` is the closest architectural relative**: hearing-aid button -> gesture engine -> voice note from the aids' microphone -> on-device transcription -> webhook or agent chat -> reply spoken back into the aids. It is for GN/ReSound aids, not Phonak. [V]
4. **`ijustlikeit/phonak-ha` is the only public implementation of the Sonova/Phonak control protocol I could find.** A GitHub code search for its service UUID returns only this repository. It reads and writes battery, volume, program, mute and the tap-control on/off setting. It does **not** receive tap gesture events. [V]
5. **No open project was found that receives a Phonak tap gesture in a third-party app**, and none that makes a custom assistant answer the tap. Phonak's own knowledge base only says Tap Control can "access smartphones' voice assistant". How the phone is told (presumably HFP voice-recognition activation) is [I]; nobody has published a capture. This stays a device test for us.
6. **Android's standard preset API for LE Audio hearing aids is closed to us.** `BluetoothHapClient` is `@hide @SystemApi` and `selectPreset` needs `BLUETOOTH_PRIVILEGED` in AOSP main. [V] A sideloaded app cannot use it; projects that switch programs do it over a vendor GATT protocol (phonak-ha, i-hear-you) or over raw HAS GATT on Linux (hearing-aid-presets).
7. **Brands with no open control project found**: Unitron, Widex, Signia, Bernafon, Starkey (only unlicensed static-analysis notes), Cochlear/MED-EL control (only ASHA streaming). Oticon has one (audifern, macOS). GN/ReSound has two (i-hear-you, hearing-aid-presets). Phonak has one (phonak-ha). [V] for "search returned nothing usable"; absence elsewhere is [U].

## 2. Top 15, ranked by usefulness for our reader and our app

### 2.1 jooray/android-bluetooth-microphone (write-up, also an agent skill)

- URL: https://github.com/jooray/android-bluetooth-microphone
- Licence MIT, no code language (two Markdown files), last push 2026-11-22, 2 stars, created 2026-10-30. Maintained. [V]
- What: how to record from a Bluetooth headset, LE Audio earbuds or hearing aids on Android, including screen-off recording and on-device transcription.
- Devices: "Measured on a Pixel 10 Pro Fold with Android 17, LE Audio hearing aids and a classic SCO headset" (README); the SCO headset is a Sony WH-1000XM6 (SKILL.md). [V]
- Why interesting: same phone family and OS generation as ours; covers both the LE Audio and the classic SCO microphone path. Phonak streams over Bluetooth Classic, so the SCO numbers are the relevant ones for us [I].
- What to borrow (all [V] as statements of the document, none verified by us on a device):
  - Routing recipe: pick the device from `AudioManager.availableCommunicationDevices`, call `setCommunicationDevice()`, wait for `OnCommunicationDeviceChangedListener` (timeout about 3 s) plus about 500 ms, then record with `MediaRecorder.AudioSource.VOICE_COMMUNICATION` and `setPreferredDevice()`. "`MIC` and `VOICE_RECOGNITION` silently record from the phone".
  - Timing: "SCO routing took about 550 ms"; on LE Audio "1.7 to 3.5 s from `startRecording()` to the first audio".
  - Signal "go ahead" on the **first non-zero PCM frame**, not at `startRecording()`. Exact zeros mean no real audio.
  - Silence primer: play 3 s of zeros on an `AudioTrack` with `USAGE_VOICE_COMMUNICATION` right after `startRecording()`; without playback one Pixel HAL logged `skip kBleRestart due to no ble edge connected` and delivered only zeros.
  - Pad cue files with leading silence (they used 2 s) because the first fraction of a second is clipped while the link changes mode.
  - Give the first sound a generous deadline (8 s), fall back to the phone microphone once with an audible cue, never reopen the stream to "fix" the wait.
  - Always `clearCommunicationDevice()` afterwards, or the headset stays in call mode.
  - Foreground service type `microphone`: "On Android 14 and later that type can only be started while the app is visible ... After a reboot or an app update the service can run but cannot record until the user opens the app once."
  - Debug recipe: `adb logcat -v time | grep -E "BtRec|AHal::BT|AHal::Router"`, `adb shell dumpsys bluetooth_manager`, `dumpsys media.audio_policy`, `dumpsys audio`.
  - Screen-off verification: record, `adb shell input keyevent 26`, wait, then `ffmpeg -i f.m4a -af silencedetect=n=-120dB:d=0.5 -f null -` to find runs of exact zeros.
- Cross-check done: the document says Android 17 has an LE hearing-aid device type 33 that is not a public constant in SDK 36. AOSP branch `android17-release`, `frameworks/base/media/java/android/media/AudioDeviceInfo.java` line 218-219 has `@FlaggedApi(FLAG_BLE_HEARING_AID_DEVICE) public static final int TYPE_BLE_HEARING_AID = 33;`. AOSP `main` at the time of fetching still ends at `TYPE_MULTICHANNEL_GROUP = 32`. [V] So the claim is correct for the Android 17 release branch.

### 2.2 jooray/i-hear-you (Android remote for GN hearing aids)

- URL: https://github.com/jooray/i-hear-you , website https://cypherpunk.today/i-hear-you/
- Licence MIT, Kotlin, last push 2026-11-22, 1 star, created 2026-11-03, latest release v0.3.3 (2026-11-22). Maintained. [V]
- What: offline Android remote for GN aids: volume, mute, programs, battery; maps the aid's own push button to phone actions; records voice notes from the aids and sends them to a webhook or a White Noise (Marmot) group; reads replies into the aids.
- Devices: "Tested on hardware: ReSound Nexia NX560S and NX460S with Nexia CROS CX160S (GN protocol 5.1)". Recognises the GN brands ReSound, Beltone, Jabra Enhance, Danalogic, Interton. "Aids from other makers (Phonak, Oticon, Signia, Widex, Starkey and others) are not supported". [V]
- Why interesting: it is the only open app found where a control on the hearing aid itself triggers a voice interaction with an agent and the answer comes back as speech in the aid. Its docs even name our backend family: "Agents behind MDK's connector (Hermes etc.) must allow the app's npub" (docs/VOICE-NOTES.md). [V]
- What to borrow:
  - `app/src/main/java/app/ihearyou/button/ButtonEngine.kt`: pure, unit-tested gesture engine turning press/release into SHORT / LONG / DOUBLE (`longMs = 400`, `doubleWindowMs = 1_300`; the comment explains the aid reports the second press about 1.1 s after the first). Same shape as our input router needs for BTHome button events. [V]
  - `app/src/debug/java/app/ihearyou/debug/DebugReceiver.kt`: debug-build-only broadcast receiver that drives the service from adb (`adb shell am broadcast -n app.ihearyou/.debug.DebugReceiver -a app.ihearyou.debug.GESTURE --es side right --es gesture LONG`). Directly useful for our "maximum debuggability" goal: every input path can be simulated without hardware. [V]
  - Audio cue vocabulary (docs/VOICE-NOTES.md): spoken "go ahead" while already recording, distinct cues for saved / sent / failed, a spoken confirmation of what a press chose "so a press read as another gesture is noticed at once". [V]
  - Webhook contract: `multipart/form-data` with `metadata` JSON (`microphone: hearing_aid|bluetooth|phone`), `transcript`, `audio`; only `https://` accepted. [V]
  - Reply playback: TTS with transient audio focus, a short press stops a reply. [V]
  - Manifest pattern: one service with `android:foregroundServiceType="connectedDevice|microphone"`; boot receiver also listens to `MY_PACKAGE_REPLACED`. [V]
  - Vendor-app conflict handling: `<queries>` for the vendor app package only to warn the user it may hold the aids. [V]
- Limits: protocol is GN-specific (service `0000fefe-...`, encrypted handle channel, ECDH P-256 + AES-128-CTR + MIC, docs/PROTOCOL.md). Nothing in it talks to Phonak. [V]

### 2.3 ijustlikeit/phonak-ha (Phonak in Home Assistant through an Android bridge)

- URL: https://github.com/ijustlikeit/phonak-ha
- Licence MIT, Kotlin + Python, last push 2026-11-23, 0 stars, created 2026-10-12, release v0.5.0 (2026-11-23) with `PhonakBridge-0.1.3.apk`. Maintained. [V]
- Predecessor: https://github.com/ijustlikeit/Phonak_ha (GPL-3.0, Python, last push 2026-10-11) is the earlier upload of the same project; link only the new one. [V]
- What: Home Assistant custom integration plus the "Phonak Bridge" Android app. The app talks the Sonova LE protocol to the aids and polls a Home Assistant webhook every 30 s.
- Devices: "Phonak Audéo i90 Sphere", "Samsung Android phone S26". The author's own words: "My best guess is that it should work with any of the newer Sphere or Lumity lines ... I have no way of testing any other Phonak hearing aids nor any other Android phones". [V]
- Why interesting: the only public Phonak control protocol notes. Battery level of each aid, active program, volume and mute are things our spoken menu could read out or change.
- Protocol facts (PROTOCOL.md, `android/app/src/main/java/app/phonakbridge/Protocol.kt`) [V as content of the repo, not verified by us]:
  - Service `d099cd20-b2ea-11e5-bf01-0002a5d5c51b`; request char `2016ec20-b2eb-11e5-a307-0002a5d5c51b`; response `388706a0-b2eb-11e5-a790-0002a5d5c51b`; link `43802b40-b2eb-11e5-b6c5-0002a5d5c51b`; open `2c1b1820-b2eb-11e5-8f66-0002a5d5c51b`.
  - Parameters: `0x00ae` battery %, `0x002b` active program, `0x0033` volume offset (s8), `0x0045` muted, `0x00FC` tap control on/off per side.
  - "Each aid is three Bluetooth 'devices' on a phone: the classic (BR/EDR) 'R-Phonak hearing aid' for audio/calls (only the right aid here), and one LE device per aid ... used only by myPhonak."
  - "The aid drops the session after ~3 s without a request"; "Quick reconnects are refused".
  - "myPhonak keeps both LE links and its Sonova session open in the background. A second app's session open on the same link then fails."
  - "Any app can open GATT to them with `TRANSPORT_LE`; Android encrypts with the existing bond."
  - Sonova company id 642 in the advertisement while connectable.
- What it does **not** do: `PARAM_TAP_CONTROL = 0x00FC` is a boolean setting ("settings: u8 0/1, side"). There is no notification handler for tap events anywhere in `AidSession.kt` or `Protocol.kt`. So this project is not evidence that a third-party app can receive a Phonak double tap. [V]
- What to borrow: `tools/sonova_decode.py` (decode myPhonak traffic from an HCI snoop log) and the capture procedure; the bridge app's `connectedDevice` foreground service + boot receiver; the safety rule of never writing volume outside the range the vendor app was seen using (-3..+2).
- Risk for us: running a second Sonova session next to myPhonak breaks one of them. If we ever add aid control, it has to be optional and off by default. [I]

### 2.4 jooray/third-ear (reference implementation of the microphone recipe)

- URL: https://github.com/jooray/third-ear
- Licence GPL-3.0 (permanent fork of Fossify Voice Recorder), Kotlin, last push 2026-11-26, 11 stars, release v0.1.9 (2026-11-22). Maintained. [V]
- What: voice recorder that records from Bluetooth and hearing-aid microphones, transcribes on the phone, posts to a webhook or agent chat and plays answers back; has Quick Settings tiles, app shortcuts and an Android Auto surface.
- Devices: Android 12+; LE Audio hearing aids, SCO headsets, car hands-free (README). [V]
- Why interesting: working code behind write-up 2.1, with tests.
- What to borrow (ideas only: GPL-3.0 code cannot be copied into our Apache-2.0 app): `app/src/main/kotlin/app/thirdear/audio/SourcePolicy.kt` (first-sound deadline and one-shot fallback as a pure, unit-tested function), `ZeroRunDetector.kt`, `FrameJournal.kt` (crash-safe recording journal: a killed process loses at most the last second), `pipeline/MediaPauser.kt`, `pipeline/Backoff.kt`. The README also shows that shortcuts make every action bindable from Key Mapper or Tasker, a cheap extra input path.

### 2.5 thivyan-studios/hark-app (phone as remote microphone for hearing aids)

- URL: https://github.com/thivyan-studios/hark-app
- Licence BSD-3-Clause, Kotlin + C++, last push 2026-11-17, 41 stars, created 2025-05-04, release v26.11.11. Maintained. [V]
- What: streams the phone microphone with low latency to hearing aids (Oboe engine, treble boost, limiter) and shows offline live captions with sherpa-onnx.
- Devices: "Built specifically for ASHA ... devices, with optional support for A2DP, SCO, and BLE headsets"; minSdk 28. [V]
- Why interesting: most-starred Android app in this niche that is permissively licensed; solves "table microphone" without vendor hardware.
- What to borrow: `service/HearingDeviceMonitor.kt` (device detection by `AudioDeviceInfo.TYPE_HEARING_AID`, `TYPE_BLUETOOTH_SCO`, `TYPE_BLE_HEADSET`, `TYPE_BLE_SPEAKER`), the split of the streaming foreground service into focus controller / streaming controller / notification helper, "automatic background stream recovery on audio route changes or screen-off events", and the sherpa-onnx streaming Zipformer integration if we want on-device speech-to-text. BSD-3 is compatible with Apache-2.0 if the notice is kept.

### 2.6 mptfire/bt-mic (small clean implementation of 2.1)

- URL: https://github.com/mptfire/bt-mic
- Licence MIT (GitHub API says NOASSERTION because the LICENSE file carries an extra provenance paragraph; the text is the MIT licence), Kotlin, last push 2026-11-01, 0 stars. One-day project, so treat as a sample. [V]
- What: voice-notes recorder that follows the SKILL.md recipes, "plus hardenings".
- Why interesting / borrow: its layout is the one we want: `policy/` "PURE state machines (host-JVM tested, no Android)", `audio/` (BtRoute, InputPicker, Primer, Recorder, CuePlayer, Verifier), `engine/`, `service/` (microphone FGS, BootReceiver), and a UI that is a "state mirror only - never touches the recorder". MIT, so code can be reused with notice.

### 2.7 Gheop/hearing-aid-presets (LE Audio hearing aids on Linux, HAS presets)

- URL: https://github.com/Gheop/hearing-aid-presets
- Licence MIT, JavaScript (GNOME Shell extension) + shell, last push 2026-11-06, 2 stars. Maintained. [V]
- What: switches hearing-aid programs from the GNOME top bar through the standard Hearing Access Service, with per-ear volume, battery and a connect script; long setup notes for BlueZ and PipeWire.
- Devices: "Tested with a pair of ReSound Vivia 960 on Fedora 44 (kernel 7.1 and 7.2, BlueZ 5.87, PipeWire 1.6.8 ...)". Lists Phonak Audéo Infinio / Sphere Infinio among makers "listing LE Audio support at the time of writing, none of them tested by us". [V]
- Why interesting: best public description of what the standard services look like in practice (BAP for audio, HAS for programs, VCS/MICS for volume and microphone) and of their pitfalls.
- What to borrow: the knowledge that HAS preset control works over plain GATT when the OS does not block it; `docs/DEVICES.md` and `docs/CONTROLLERS.md` as a model for a compatibility list fed by issue reports. Whether an Android app may open the HAS characteristics of a bonded LE Audio aid is [U] and not relevant while our aid is on Bluetooth Classic.

### 2.8 sabino/audifern (Oticon control on macOS)

- URL: https://github.com/sabino/audifern
- Licence GPL-3.0, Swift, last push 2026-10-23, 2 stars, release v0.9.1. Maintained. [V]
- What: macOS app and menu-bar control for Oticon aids: volume and mute per ear, fitted programs, battery with alerts, widgets.
- Devices: "Currently tested with one pair of Oticon Intent 3 hearing aids; other models are unverified." "Advanced features also need compatible authentication configured locally; credentials are not included." [V]
- Why interesting: the only open Oticon/Demant control client found; shows that authentication material can be the blocker.
- Borrow: wording and structure of its safety, privacy and "not affiliated" notices; battery alert logic as a feature idea.

### 2.9 lpintes/ashavol (second GATT client next to Android's own hearing-aid stack)

- URL: https://github.com/lpintes/ashavol
- No licence file (all rights reserved by default), Java, last push 2026-11-02, 0 stars. [V]
- What: Android foreground service that overrides the volume Android sends to ASHA devices.
- Devices: Motorola edge 40 neo with two Cochlear Nucleus sound processors; nothing else tested. [V]
- Why interesting: demonstrates, on a real device, that a normal app can hold its own GATT connection to bonded hearing devices and write the ASHA Volume characteristic (`00e4ca9e-ab14-41e4-8823-f9e70c7e91df`, service `FDF0`) while Android streams. [V as README statement]
- Borrow: the technique (extra GATT client beside the system profile), and the list of events it reacts to (stream volume change, audio mode change, stream start through AudioStatusPoint `38663f1a-e711-4cac-b641-326b56404837`). No code reuse: no licence.

### 2.10 google/bumble (Python Bluetooth stack with ASHA and HAP)

- URL: https://github.com/google/bumble
- Licence Apache-2.0, Python, last push 2026-11-26, 595 stars. Maintained. [V]
- What: full Bluetooth stack in Python. Contains `bumble/profiles/asha.py`, `bumble/profiles/hap.py`, `apps/auracast.py`, `examples/run_asha_sink.py`, `examples/run_hap_server.py`. [V via contents API]
- Why interesting: lets a laptop with a USB dongle pretend to be a hearing aid, so Android routing code can be exercised without the real aid.
- Borrow: a test rig. `joshNosConvo/hearing-device-emulator` (https://github.com/joshNosConvo/hearing-device-emulator , no licence, Python, last push 2026-10-23, 0 stars) shows the idea: a CLI on top of Bumble or BlueALSA that emulates a Cochlear processor so that the phone reports `AudioDeviceInfo.TYPE_HEARING_AID`, and a `cochlear-phone-clip` preset that emulates a classic A2DP/HFP headset. [V as README statement] The classic-headset emulation is the variant that resembles our Phonak path [I].

### 2.11 thewierdnut/asha_pipewire_sink (ASHA on Linux, user space)

- URL: https://github.com/thewierdnut/asha_pipewire_sink
- Licence Unlicense, C++, last push 2026-09-21, 53 stars, created 2024-06-04. Maintained. [V]
- What: sample ASHA implementation that creates PipeWire sinks for connected ASHA hearing devices.
- Why interesting: the README is the most honest status report on ASHA: "ASHA is dead. Long live ASHA", LE Audio aids "are dual mode", and "As of bluez 5.77 asha has been directly integrated into the bluez and wireplumber stacks" (do not run both). [V]
- Borrow: `snoop_analyze.cxx` as an example of analysing HCI snoop logs; the connection-interval and data-length-extension findings for anyone who streams over LE.
- Note for Phonak owners: Phonak streams over Bluetooth Classic, not ASHA, so this family (2.11, 2.12, section 4.C) is for the README audience, not for our app. Basis: phonak-ha PROTOCOL.md describes a classic BR/EDR audio device; that Phonak never offers ASHA on any model is [I].

### 2.12 shermp/Pico-ASHA (ASHA on a Raspberry Pi Pico W)

- URL: https://github.com/shermp/Pico-ASHA , docs https://shermp.github.io/Pico-ASHA
- Licence BSD-3-Clause for the source (binaries are bound by the BTstack licence), C++, last push 2026-10-19, 58 stars, release v0.3.0 (2026-01-05). Maintained. [V]
- What: a USB audio dongle built from a Pico W that streams to ASHA hearing aids from any computer.
- Devices: README table lists as working: Cochlear Baha 6 Max, Nucleus 7, Nucleus 8, MED-EL SONNET 3, Oticon More 2, Philips HearLink 9030/9050, Signia 5AX and Pure Charge&Go T 7AX, Sonic Radiant SE 60, Widex Moment 220, Audio Service R S 7.6; Starkey Arc AI pairs but does not stream. No Phonak in the list. [V]
- Why interesting: the most-used DIY hardware in this field and the best public compatibility table. Spin-offs: `Jah-On/pashac` (web UI, GPL-3.0), `Drohnald/Audio2ASHA` (adds analog input), `ekwilinski/usb-asha` (same idea on an nRF52840 dongle, no licence).
- Borrow: nothing for the app; link for readers.

### 2.13 librepods-org/librepods (AirPods features, including Hearing Aid, on Android and Linux)

- URL: https://github.com/librepods-org/librepods (the old path `kavishdevar/librepods` redirects here)
- Licence GPL-3.0, Kotlin, last push 2026-11-09, 30,994 stars, latest tag `rewrite-v2.0.0-alpha.4` (2026-10-23). Maintained. [V]
- What: reverse-engineered Apple Accessory Protocol client; README section "Accessibility Settings and Hearing Aid": "All hearing aid customizations can be done from Android (linux soon), including setting the audiogram result." The feature table marks Hearing Aid, Transparency customisation and Loud Sound Reduction as needing root / VendorID spoofing. [V]
- Why interesting: by far the largest community project that touches hearing-aid functions; proof that a vendor accessory protocol can be documented in the open (`docs/control_commands.md`).
- Borrow: project hygiene for a reverse-engineering repo (protocol docs separate from code, feature/compatibility table). Related: `7nihilate/OpenAirCare` (AGPL-3.0, Rust, Linux tool for AirPods Pro transparency profile), `brianpht/librepods-rs`.

### 2.14 ryleighnewman/Self-Programming-My-Phonak-Hearing-Aids-With-Claude-AI (write-up)

- URL: https://github.com/ryleighnewman/Self-Programming-My-Phonak-Hearing-Aids-With-Claude-AI , original post https://ryleighnewman.com/blog/self-programming-my-phonak-spheres-with-ai
- Licence MIT, Markdown only, last push 2026-10-01, 5 stars. One-off article. [V]
- What: a wearer of Phonak Audéo I90 Sphere reprograms the aids with Phonak Target 12.0.1 and a Noahlink Wireless 2, using two AI sessions (supervisor and operator) with every prompt published. Carries a clear "not medical advice" warning. [V]
- Why interesting: same aid family as ours; the most detailed public DIY-fitting write-up for Phonak.
- Borrow: nothing for the app; a good "further reading" link, with the warning repeated.

### 2.15 Tympan/Tympan_Library (open-source hearing aid platform)

- URL: https://github.com/Tympan/Tympan_Library , hardware https://github.com/Tympan/Tympan_Rev_F_Hardware (CERN-OHL-P-2.0, last push 2025-12-26)
- Licence MIT, C++, last push 2026-11-16, 154 stars, created 2017. Maintained. [V]
- What: Arduino/Teensy library and open hardware for building and researching hearing aids.
- Why interesting: the reference answer to "can I build my own hearing aid"; active for nine years.
- Borrow: nothing for the app.

## 3. Platform facts checked while reading these projects

| Statement | Status | Source |
|---|---|---|
| `BluetoothHapClient` (LE Audio hearing-aid presets) is `@hide @SystemApi`; `selectPreset` requires `BLUETOOTH_CONNECT` and `BLUETOOTH_PRIVILEGED` | [V] | AOSP main, `packages/modules/Bluetooth/framework/java/android/bluetooth/BluetoothHapClient.java`, lines 60-63 and 767-770 |
| `AudioDeviceInfo.TYPE_HEARING_AID = 23`, `TYPE_BLE_HEADSET = 26`, `TYPE_BLE_SPEAKER = 27` | [V] | AOSP main, `frameworks/base/media/java/android/media/AudioDeviceInfo.java`, lines 143-174 |
| `TYPE_BLE_HEARING_AID = 33` exists, flagged `FLAG_BLE_HEARING_AID_DEVICE` | [V] | AOSP branch `android17-release`, same file, lines 218-219. Not present in the copy of `main` I fetched. |
| `BluetoothHearingAid` (ASHA) is a public class | [V] | https://developer.android.com/reference/android/bluetooth/BluetoothHearingAid (HTTP 200, page title) |
| Phonak Tap Control "can control: accept/end call for streaming; pause/resume streaming from a connected Bluetooth device; access smartphones' voice assistant"; "available only in specific hearing aid models" | [V] | https://www.phonak.com/en-int/support/knowledge-base/articles/myphonak-and-tap-control |
| The tap reaches the phone as an HFP voice-recognition request and Android then starts the default assistant | [I] | Not stated by Phonak. Forum reports (below) only show that the phone's assistant choice matters. Must be tested. |
| A custom default assistant is started by the Phonak tap with the screen off | [U] | No source found. Forum thread "Phonak Paradise Tap Control and Google Assistant" (2021-2023) reports intermittent failures and Samsung phones preferring Bixby; it does not cover custom assistants or the locked state. |
| ASHA is in BlueZ (`profiles/audio/asha.c`, `src/shared/asha.c`) and PipeWire (`spa/plugins/bluez5/asha-codec-g722.c`) | [V] | GitHub contents API on `bluez/bluez` and `PipeWire/pipewire` |
| BlueZ has no HAP/HAS plugin file under `profiles/audio/` | [V] | Directory listing of `bluez/bluez/profiles/audio` (asha, bap, bass, ccp, csip, gmap, mcp, micp, tmap, vcp; no hap); code search for "Hearing Access" in `bluez/bluez` returns 0 files |
| Zephyr has a Hearing Access Service server and client (`subsys/bluetooth/audio/has.c`, `has_client.c`) and a hearing-aid sample (`samples/bluetooth/audio/hap_ha`) | [V] | GitHub contents API on `zephyrproject-rtos/zephyr` |

## 4. Full catalogue by category

Columns: repository (link), licence, language, last push, stars, then one line each for what / devices / why / borrow. All rows [V] for existence and metadata on 2026-11-27.

### 4.A Android apps that work with real hearing aids

| Repository | Licence | Lang | Last push | Stars | Notes |
|---|---|---|---|---|---|
| [jooray/i-hear-you](https://github.com/jooray/i-hear-you) | MIT | Kotlin | 2026-11-22 | 1 | See 2.2. GN/ReSound. Button bridge + voice notes + agent replies. |
| [ijustlikeit/phonak-ha](https://github.com/ijustlikeit/phonak-ha) | MIT | Kotlin | 2026-11-23 | 0 | See 2.3. Phonak Audéo Sphere i90. |
| [jooray/third-ear](https://github.com/jooray/third-ear) | GPL-3.0 | Kotlin | 2026-11-26 | 11 | See 2.4. Any Bluetooth mic incl. LE Audio aids. |
| [mptfire/bt-mic](https://github.com/mptfire/bt-mic) | MIT | Kotlin | 2026-11-01 | 0 | See 2.6. |
| [thivyan-studios/hark-app](https://github.com/thivyan-studios/hark-app) | BSD-3-Clause | Kotlin | 2026-11-17 | 41 | See 2.5. ASHA aids. |
| [lpintes/ashavol](https://github.com/lpintes/ashavol) | none | Java | 2026-11-02 | 0 | See 2.9. Cochlear Nucleus over ASHA. |
| [chueh0000/LC3-Hearing-Aid](https://github.com/chueh0000/LC3-Hearing-Aid) | none | Kotlin | 2026-09-10 | 0 | Phone-as-hearing-aid for LE Audio headsets (Oboe full duplex, EQ, compressor, ADPF hints). Why: shows the latency argument for LE Audio. Borrow: ADPF hint idea for an audio thread; no licence, so ideas only. |
| [TheXLreal/AshaOS](https://github.com/TheXLreal/AshaOS) | Apache-2.0 | C | 2026-09-23 | 0 | Experimental LineageOS 23.2 build for Pixel 5 that injects PC audio into Android's ASHA encoder. README in Russian; status "Beta" with audio dropouts. Why: shows how far one must go (patched Bluetooth module) to bypass `AudioTrack`. Borrow: nothing. |
| [Monirul-Islam80/helping-hand](https://github.com/Monirul-Islam80/helping-hand) | none | Java | 2025-11-28 | 1 | Wear OS + phone app that drives the Oticon Companion app through an AccessibilityService (`MyAccessibilityService.java`, tap coordinates). No README. Why: the "automate the vendor app" route. Borrow: only as a last-resort idea. Stale (one-day project a year ago). |
| [johnohhh1/ble-radar-hunt](https://github.com/johnohhh1/ble-radar-hunt) | MIT | Kotlin | 2026-10-15 | 1 | Find a lost Bluetooth device (hearing aid, earbud) by RSSI. Borrow: nothing. |
| [VHAINNOVATIONS/Mobile-Distance-Hearing-Aid-Application](https://github.com/VHAINNOVATIONS/Mobile-Distance-Hearing-Aid-Application) | Apache-2.0 | Java | 2014-07-27 | 12 | 2014 US Veterans Affairs prototype for remote Phonak fitting on Android 4.4; needs Phonak fitting hardware. **Unmaintained**, historical only. |

### 4.B Proprietary control protocols (reverse engineering)

| Repository | Licence | Lang | Last push | Stars | Notes |
|---|---|---|---|---|---|
| [ijustlikeit/phonak-ha](https://github.com/ijustlikeit/phonak-ha) `PROTOCOL.md` | MIT | - | 2026-11-23 | 0 | Sonova/Phonak. From HCI snoop logs of the author's own aids. See 2.3. |
| [jooray/i-hear-you](https://github.com/jooray/i-hear-you) `docs/PROTOCOL.md` | MIT | - | 2026-11-22 | 1 | GN (ReSound, Beltone, Jabra Enhance, Danalogic, Interton). Verified by the author on Nexia hardware. |
| [sabino/audifern](https://github.com/sabino/audifern) | GPL-3.0 | Swift | 2026-10-23 | 2 | Oticon Intent 3 on macOS. See 2.8. |
| [sk408/hearing_aid_control](https://github.com/sk408/hearing_aid_control) | **none** | TypeScript | 2026-08-10 | 0 | 44 Markdown files in `docs/` with UUID maps and command tables for Philips/Oticon ("POLARIS", service `56772eaf-2153-4f74-acf3-4368d99fbf5a`), Rexton/WSA, Starkey ("Piccolo", service `9a04f079-9840-4286-ab92-e65be0885f95`) and ReSound; plus a Web Bluetooth app. Its own SPEC.md says "Status: Pre-development" and the data comes from static analysis of vendor apps. **Caveats**: no licence, confidence marks are the author's, no hardware test is documented, and the Starkey UUID is also the well-known PlayReady system id (code search returns 3,768 unrelated hits), so treat every value as unverified. Why: the only public notes at all for Starkey, Rexton and Philips. Borrow: nothing without verification. |
| [sk408/hearing_aid_control_android](https://github.com/sk408/hearing_aid_control_android) | none | TypeScript | 2026-08-09 | 0 | React Native companion of the above. Same caveats. |
| [librepods-org/librepods](https://github.com/librepods-org/librepods) `docs/control_commands.md` | GPL-3.0 | Kotlin | 2026-11-09 | 30,994 | Apple AAP incl. hearing-aid settings. See 2.13. |

Code search for the Sonova service UUID `d099cd20-b2ea-11e5-bf01-0002a5d5c51b` returns exactly two files, both in phonak-ha. For the GN command characteristic `1959A468-...` it returns only i-hear-you. [V]

### 4.C ASHA (Audio Streaming for Hearing Aids) outside Android

Spec: https://source.android.com/docs/core/connect/bluetooth/asha [V]

| Repository | Licence | Lang | Last push | Stars | Notes |
|---|---|---|---|---|---|
| [bluez/bluez](https://github.com/bluez/bluez) (`profiles/audio/asha.c`) | GPL-2.0 | C | 2026-11-26 | 1,189 | Upstream Linux ASHA. Write-up: "A Brimful of ASHA", Arun Raghavan, 2025-01-07, https://arunraghavan.net/2025/01/a-brimful-of-asha/ [V] |
| [PipeWire/pipewire](https://github.com/PipeWire/pipewire) (GitHub mirror) | MIT per upstream (API: NOASSERTION) | C | 2026-11-27 | 2,177 | ASHA G.722 codec plugin, LE Audio. Write-up: Collabora, "Implementing Bluetooth LE Audio & Auracast on Linux systems", 2025-11-24, https://www.collabora.com/news-and-blog/blog/2025/11/24/implementing-bluetooth-le-audio-and-auracast-on-linux-systems/ [V] |
| [thewierdnut/asha_pipewire_sink](https://github.com/thewierdnut/asha_pipewire_sink) | Unlicense | C++ | 2026-09-21 | 53 | See 2.11. |
| [shermp/Pico-ASHA](https://github.com/shermp/Pico-ASHA) | BSD-3-Clause | C++ | 2026-10-19 | 58 | See 2.12. |
| [Jah-On/pashac](https://github.com/Jah-On/pashac) | GPL-3.0 | - | 2026-06-13 | 0 | Web interface for a Pico-ASHA device. |
| [Drohnald/Audio2ASHA](https://github.com/Drohnald/Audio2ASHA) | BSD-3-Clause text (API: NOASSERTION) | C++ | 2026-10-23 | 0 | Pico + WM8782 ADC: USB and 3.5 mm audio to ASHA. |
| [ekwilinski/usb-asha](https://github.com/ekwilinski/usb-asha) | none | C | 2026-11-21 | 0 | USB audio to ASHA on a Raytac nRF52840 dongle; editions for MED-EL SONNET 3. |
| [Sk9l9tik/oticon-connect](https://github.com/Sk9l9tik/oticon-connect) | MIT (API: NOASSERTION) | C++ | 2026-11-23 | 0 | Linux to ASHA without BlueZ (BTstack over libusb + PipeWire). Tested: Oticon More 2, Realtek RTL8761B. One aid only. |
| [tvqt/asha-mac](https://github.com/tvqt/asha-mac) | GPL-3.0 | C | 2026-10-06 | 1 | ASHA on macOS for Macs without MFi hearing support; needs SIP off once. Tested: Oticon Real 1, MacBook Air M1. |
| [stythelad/asha-windows](https://github.com/stythelad/asha-windows) | MIT | Python | 2026-11-01 | 3 | Documents why ASHA cannot work on Windows (no public L2CAP CoC API); scanning and GATT only. Useful as a negative result. |
| [Ja4e/ASHA4LINUX](https://github.com/Ja4e/ASHA4LINUX) | AGPL-3.0 | Python | 2026-09-23 | 3 | Helper scripts for ASHA and bimodal (CI + hearing aid) streaming on Linux. |
| [osimarr/oticon-steamdeck](https://gitea.com/osimarr/oticon-steamdeck) (Gitea, not GitHub) | MIT | Shell | updated 2026-08-06 | 0 | Arch/Steam Deck packaging for Oticon ASHA aids. Verified through the Gitea API. |
| [Jah-On/LASHA](https://github.com/Jah-On/LASHA) | GPL-3.0 | Rust | 2023-11-26 | 3 | Early Linux ASHA driver. **Unmaintained.** |
| [hxk1633/MacASHAStreamerApp](https://github.com/hxk1633/MacASHAStreamerApp) | none | Swift | 2023-11-24 | 3 | Early macOS attempt, empty README. **Unmaintained.** |
| [stefnotch/asha-rt](https://github.com/stefnotch/asha-rt) | MIT | C++ | 2023-07-29 | 0 | Early experiment. **Unmaintained.** |
| [sk408/diy](https://github.com/sk408/diy) | BSD-3-Clause | C++ | 2025-03-12 | 0 | Pico-ASHA derivative, no description. **Unmaintained.** |

### 4.D LE Audio, Hearing Access Profile, Auracast

Specs: https://www.bluetooth.com/specifications/specs/hearing-access-profile-1-0/ and https://www.bluetooth.com/specifications/specs/hearing-access-service-1-0/ [V, both HTTP 200]

| Repository | Licence | Lang | Last push | Stars | Notes |
|---|---|---|---|---|---|
| [Gheop/hearing-aid-presets](https://github.com/Gheop/hearing-aid-presets) | MIT | JavaScript | 2026-11-06 | 2 | See 2.7. |
| [patkub/linux-le-hearing-aids](https://github.com/patkub/linux-le-hearing-aids) | none | - | 2026-05-30 | 0 | One-page guide: ReSound Vivia 9 on Ubuntu 26.04 (`ControllerMode = le`, `Experimental = true`). |
| [google/bumble](https://github.com/google/bumble) | Apache-2.0 | Python | 2026-11-26 | 595 | See 2.10. HAP, ASHA and Auracast in Python. |
| [zephyrproject-rtos/zephyr](https://github.com/zephyrproject-rtos/zephyr) | Apache-2.0 | C | 2026-11-27 | 17,198 | HAS server/client and `samples/bluetooth/audio/hap_ha`: build your own LE Audio hearing-aid peripheral. |
| [nrfconnect/sdk-nrf](https://github.com/nrfconnect/sdk-nrf) | LicenseRef-Nordic-5-Clause (API: NOASSERTION) | C | 2026-11-27 | 1,459 | `applications/nrf_audio` LE Audio reference application. |
| [soerengraae/HARC](https://github.com/soerengraae/HARC) | none | C | 2026-02-10 | 0 | "Archive of a Hearing Aid Remote Controle following the HARC role definition by Bluetooth SIG", nRF52832 + Zephyr. Why: a hardware pocket remote for LE Audio aids, the standards-based cousin of our Shelly button. Archive by its own description. |
| [felipepimentab/tiresias-firmware](https://github.com/felipepimentab/tiresias-firmware) | Nordic licence (API: NOASSERTION) | C | 2026-10-20 | 0 | Zephyr/NCS firmware for a hearing-aid development kit. |
| [napthemax/auracast-sender](https://github.com/napthemax/auracast-sender) | MIT | Python | 2026-09-14 | 5 | Mac to Auracast hearing aids through a FlooGoo FMA120 dongle; GUI + REST API. Tested: ReSound Nexia. Good "hard-won compatibility lessons" list (hearing aids only receive standard-quality broadcasts). Forum thread: https://forum.hearingtracker.com/t/created-an-app-for-mac-for-auracast-to-my-resound-nexia-hearing-aids/114302 |
| [Flairmesh/FlooCast](https://github.com/Flairmesh/FlooCast) | BSD-3-Clause | Python | 2026-09-21 | 59 | Vendor configuration tool for the FMA120 LE Audio dongle. |
| [Flairmesh/FlooCatchLinux](https://github.com/Flairmesh/FlooCatchLinux) | MIT | C++ | 2026-03-28 | 2 | Linux receiver app for the FMA120. |
| [auracast-research/auracast-hackers-toolkit](https://github.com/auracast-research/auracast-hackers-toolkit) | none | C | 2026-10-06 | 41 | Zephyr-based security research toolkit: sniffing, BIS hijacking (BISON), DoS. Why: what "public broadcast into my ears" means for security. |
| [auracast-research/auracast-packet-visualizer](https://github.com/auracast-research/auracast-packet-visualizer) | none | TypeScript | 2026-09-14 | 2 | Visualises BIS configurations and captures. |
| [MadManJohnSmith/auracast-assistant](https://github.com/MadManJohnSmith/auracast-assistant) | Apache-2.0 | - | 2026-11-07 | 0 | CLI Auracast assistant for Linux; three weeks old. |
| [siewedu/auracast_assistant](https://github.com/siewedu/auracast_assistant) | none | Dart | 2025-07-20 | 15 | Flutter Auracast assistant demo. Stale (16 months). |
| [axute/auracast-bau-qr-code](https://github.com/axute/auracast-bau-qr-code) | MIT | HTML | 2026-01-16 | 2 | Browser generator for Broadcast Audio URI QR codes. |
| [malarisch/esp32s31-auracast-transmitter](https://github.com/malarisch/esp32s31-auracast-transmitter) | none | C | 2026-10-21 | 3 | Auracast transmitter on ESP32-S31. |

### 4.E Apple: MFi hearing devices and AirPods hearing features

| Repository | Licence | Lang | Last push | Stars | Notes |
|---|---|---|---|---|---|
| [librepods-org/librepods](https://github.com/librepods-org/librepods) | GPL-3.0 | Kotlin | 2026-11-09 | 30,994 | See 2.13. |
| [7nihilate/OpenAirCare](https://github.com/7nihilate/OpenAirCare) | AGPL-3.0 | Rust | 2026-11-05 | 1 | Linux utility for AirPods Pro 2/3 transparency audio profile. |
| [brianpht/librepods-rs](https://github.com/brianpht/librepods-rs) | none | Rust | 2026-09-13 | 13 | Minimal AAP implementation in Rust. |
| [PShiw/airpods-hearing-aid-enabler](https://github.com/PShiw/airpods-hearing-aid-enabler) | MIT | Python | 2026-09-15 | 0 | Notes on enabling the region-locked AirPods Pro hearing-aid feature. Region-lock workaround: mention with care or leave out of the README. |
| [aikidovation/HearingSwitch](https://github.com/aikidovation/HearingSwitch) | MIT | Swift | 2026-11-13 | 1 | macOS menu-bar switch between speakers and MFi hearing devices. States: "macOS has no public way for an app to take hearing devices from an iPhone". |
| [JustinZhou88/QingTing](https://github.com/JustinZhou88/QingTing) | MIT | Swift | 2026-09-30 | 0 | Mac/iPhone microphone, denoised (DeepFilterNet 3, RNNoise), streamed to MFi aids; classroom use. UI in Chinese. |
| [Aurora-Akira/SoundBridge](https://github.com/Aurora-Akira/SoundBridge) | MIT | Swift | 2026-11-17 | 0 | Windows PC audio through the hearing aids connected to an iPhone. |
| Gist: [Apple AirPods Bluetooth Packets](https://gist.github.com/rithvikvibhu/45e24bbe5ade30125f152383daf07016) | - | - | - | - | Credited by librepods for the hearing-aid feature. [V, HTTP 200] |

### 4.F Home Assistant and automation

| Repository | Licence | Lang | Last push | Stars | Notes |
|---|---|---|---|---|---|
| [ijustlikeit/phonak-ha](https://github.com/ijustlikeit/phonak-ha) | MIT | Kotlin/Python | 2026-11-23 | 0 | The only hearing-aid Home Assistant integration found. See 2.3. |
| [ijustlikeit/Phonak_ha](https://github.com/ijustlikeit/Phonak_ha) | GPL-3.0 | Python | 2026-10-11 | 0 | Superseded by the row above. |

Searches for Home Assistant integrations of other brands, ESPHome, MQTT and openHAB returned nothing relevant. [V for the empty result]

### 4.G Fitting software and DIY fitting

| Repository / page | Licence | Lang | Last push | Stars | Notes |
|---|---|---|---|---|---|
| [ryleighnewman/Self-Programming-My-Phonak-Hearing-Aids-With-Claude-AI](https://github.com/ryleighnewman/Self-Programming-My-Phonak-Hearing-Aids-With-Claude-AI) | MIT | - | 2026-10-01 | 5 | See 2.14. |
| [JediLin/Hearing-Aids-Fitting-Software-Update-Checkers](https://github.com/JediLin/Hearing-Aids-Fitting-Software-Update-Checkers) | none | Python | 2026-11-23 | 40 | Scripts that query vendors' update servers for fitting software versions ("such as Phonak Target"; changelog mentions Unitron, Phonak Roger Upgrader, MedRx). Active. |
| [Markismus/Hearing-Aid-Software-Downloaders](https://github.com/Markismus/Hearing-Aid-Software-Downloaders) | none | - | 2022-10-28 | 6 | Predecessor idea. **Unmaintained.** |
| [andrastoth1980/acoustic-log](https://github.com/andrastoth1980/acoustic-log) | none | JavaScript | 2026-11-25 | 0 | Offline PWA to log listening situations and fitting notes for the audiologist (EN/DE/FR/HU/PL). |
| HearingTracker forum, category "Hearing Aid Self-Fitting and Adjusting" | - | - | - | - | https://forum.hearingtracker.com/c/hearing-aid-self-fitting-and-adjusting/15 [V, link present on the forum's category page] |

No open-source replacement for vendor fitting software (such as Phonak Target) was found. [V for the empty result]

### 4.H Open hearing-aid hardware and DSP

| Repository | Licence | Lang | Last push | Stars | Notes |
|---|---|---|---|---|---|
| [Tympan/Tympan_Library](https://github.com/Tympan/Tympan_Library) | MIT | C++ | 2026-11-16 | 154 | See 2.15. |
| [Tympan/Tympan_Rev_F_Hardware](https://github.com/Tympan/Tympan_Rev_F_Hardware) | CERN-OHL-P-2.0 | C++ | 2025-12-26 | 7 | Current hardware revision. |
| [Tympan/Tympan_Remote_App](https://github.com/Tympan/Tympan_Remote_App) | none | TypeScript | 2024-11-19 | 3 | Phone remote for Tympan. **Unmaintained.** |
| [HoerTech-gGmbH/openMHA](https://github.com/HoerTech-gGmbH/openMHA) | AGPL-3.0 | C | 2026-08-05 | 346 | Open Master Hearing Aid: real-time hearing-aid signal processing platform (Oldenburg). |
| [m-r-s/hearingaid-prototype](https://github.com/m-r-s/hearingaid-prototype) | GPL-3.0 | MATLAB | 2021-10-20 | 137 | Raspberry Pi + openMHA wearable prototype. **Unmaintained.** |
| [nihospr01/OpenSpeechPlatform](https://github.com/nihospr01/OpenSpeechPlatform) | BSD-2-Clause | C | 2022-09-01 | 16 | UCSD Open Speech Platform. **Unmaintained.** (The older `OpenSpeechPlatform-UCSD` path now resolves to `nihospr01/OpenSpeechPlatform-2020B`, last push 2021-09-01.) |
| [OpenEarable/open-earable-2](https://github.com/OpenEarable/open-earable-2) | Nordic licence text (API: NOASSERTION) | C | 2026-11-23 | 192 | Open-source ear-worn sensing platform with wireless audio (site: open-earable.teco.edu). v1: [OpenEarable/open-earable](https://github.com/OpenEarable/open-earable), MIT, last push 2024-10-18, 345 stars, superseded. |
| [BoysTownOrg/chapro](https://github.com/BoysTownOrg/chapro) | CC0-1.0 | C | 2026-08-06 | 27 | Library of hearing-aid signal-processing functions (wide dynamic-range compression, adaptive feedback cancellation). |
| [claritychallenge/clarity](https://github.com/claritychallenge/clarity) | MIT | Python | 2026-11-23 | 200 | Toolkit of the Clarity machine-learning challenges for hearing aids. |
| [3DTune-In/3dti_AudioToolkit](https://github.com/3DTune-In/3dti_AudioToolkit) | LGPL-3.0 | C++ | 2026-09-25 | 278 | Binaural audio with hearing-loss and hearing-aid simulation. |
| [bhamla-lab/LoCHAid-2020-PLOS-ONE](https://github.com/bhamla-lab/LoCHAid-2020-PLOS-ONE) | none | - | 2022-02-11 | 209 | Design files of the low-cost LoCHAid paper. **Unmaintained** paper artefact. |
| [MrPaivas/ESP32-hearing-aid-with-audiometry](https://github.com/MrPaivas/ESP32-hearing-aid-with-audiometry) | Apache-2.0 | C | 2026-11-01 | 0 | ESP32 experiment with pure-tone audiometry and NAL-R fitting. New, untested by others. |
| [hfvoip/j20ha](https://github.com/hfvoip/j20ha) | MIT | C | 2025-09-22 | 1 | Hearing-aid firmware "in one RSL20 chip" with Classic Bluetooth support (repository description). |

### 4.I Hearing tests, amplifier apps, tinnitus

| Repository | Licence | Lang | Last push | Stars | Notes |
|---|---|---|---|---|---|
| [woheller69/audiometry](https://github.com/woheller69/audiometry) | GPL-3.0 | Java | 2026-09-29 | 85 | Android hearing test. |
| [HMAKT99/OpenHearing](https://github.com/HMAKT99/OpenHearing) | GPL-3.0 | Kotlin | 2026-07-09 | 23 | Android hearing assistance through any earbuds (hearing check, per-ear profile, amplification). Article: https://dev.to/arun_kt_bb670b3a571f5efd8/apple-locked-hearing-assistance-inside-airpods-so-i-built-an-open-source-version-for-any-earbuds-2lkh (the article links this repository 12 times) [V]. |
| [vatbub/HearingAidAndroid](https://github.com/vatbub/HearingAidAndroid) | Apache-2.0 | Java | 2023-01-23 | 28 | Older phone-as-hearing-aid app. **Unmaintained.** |
| [r4ai/tinnitus-reducer-acrn](https://github.com/r4ai/tinnitus-reducer-acrn) | MIT | Svelte | 2026-11-25 | 23 | ACRN tinnitus tone therapy in the browser. Active. |
| [headphonejames/acrn](https://github.com/headphonejames/acrn) | GPL-2.0 | HTML | 2017-08-31 | 140 | The original ACRN web implementation. **Unmaintained.** |
| [Aaronminer1/tinnitus-suite](https://github.com/Aaronminer1/tinnitus-suite) | none | JavaScript | 2026-03-08 | 12 | Browser tinnitus assessment, tone matching, notched noise. |
| [denis-1337/NotchedMusic](https://github.com/denis-1337/NotchedMusic) | Apache-2.0 | Kotlin | 2026-11-27 | 0 | Android + Wear OS notched-music player. Three days old. |

None of the tinnitus tools has medical validation that I verified; the README should say so if it links them.

### 4.J Live captioning

| Repository | Licence | Lang | Last push | Stars | Notes |
|---|---|---|---|---|---|
| [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Apache-2.0 | C++ | 2026-11-27 | 16,277 | On-device speech-to-text, TTS and VAD with Android bindings. Used by hark-app and samoylenkodmitry/live-captions. Borrow: candidate engine if we ever want offline STT/TTS; licence fits ours. |
| [thivyan-studios/hark-app](https://github.com/thivyan-studios/hark-app) | BSD-3-Clause | Kotlin | 2026-11-17 | 41 | Captions + streaming to aids. See 2.5. |
| [samoylenkodmitry/live-captions](https://github.com/samoylenkodmitry/live-captions) | none | Kotlin | 2026-06-06 | 7 | Offline captions on a Pixel 9 Pro with sherpa-onnx (Whisper/Zipformer/Parakeet) + ML Kit. |
| [Sal7one/Hearth](https://github.com/Sal7one/Hearth) | Apache-2.0 | C++ | 2026-11-16 | 17 | Offline captions, translation, STT/TTS on Android, Windows, macOS. |
| [arbelonson-source/ozen](https://github.com/arbelonson-source/ozen) | AGPL-3.0 | Kotlin | 2026-11-23 | 4 | On-device captions for deaf and hard-of-hearing users, iOS and Android. |
| [Techlm77/NowCaption](https://github.com/Techlm77/NowCaption) | MIT | Python | 2026-11-19 | 23 | On-device live captions for Linux (Flatpak). |
| [abb128/LiveCaptions](https://github.com/abb128/LiveCaptions) | GPL-3.0 | C | 2025-02-27 | 1,815 | Linux desktop live captions. **Unmaintained** (21 months). |
| [google/live-transcribe-speech-engine](https://github.com/google/live-transcribe-speech-engine) | Apache-2.0 | Java | 2022-07-20 | 1,502 | Client libraries of Google's Live Transcribe. **Archived.** |

### 4.K Adjacent building blocks (not hearing-aid specific)

| Repository | Licence | Lang | Last push | Stars | Notes |
|---|---|---|---|---|---|
| [home-assistant/android](https://github.com/home-assistant/android) | Apache-2.0 | Kotlin | 2026-11-27 | 4,085 | Has a real `VoiceInteractionService` (`app/src/main/kotlin/io/homeassistant/companion/android/assist/service/AssistVoiceInteractionService.kt`, `res/xml/voice_interaction_service.xml`). Reference for our input path A, same licence as ours. [V via code search] |
| [DicioTeam/dicio-android](https://github.com/DicioTeam/dicio-android) | GPL-3.0 | Kotlin | 2026-10-23 | 1,558 | Open-source voice assistant. Manifest handles `android.intent.action.ASSIST` and `android.intent.action.VOICE_COMMAND` and exposes a `RecognitionService`. [V in a local clone at commit 3d1c154] The old path `Stypox/dicio-android` redirects here. |
| [keymapperorg/KeyMapper](https://github.com/keymapperorg/KeyMapper) | GPL-3.0 | Kotlin | 2026-11-26 | 2,877 | Remaps hardware buttons; third-ear exposes shortcuts for it. Cheap way to prototype input path C. |
| [OferTiber/SaraButton](https://github.com/OferTiber/SaraButton) | MIT | Java | 2026-08-07 | 0 | "Open-source Android accessibility app for Shelly BLU button remotes". Exists as the planning notes say. |
| [suddenBook/GPTWake](https://github.com/suddenBook/GPTWake) | no standard licence detected (API: NOASSERTION) | Java | 2026-09-08 | 14 | Wake-word start of ChatGPT Voice. Exists as the planning notes say; check the licence text before reuse. |
| [SOVIS-TEAM/SoundVisualizer-Android](https://github.com/SOVIS-TEAM/SoundVisualizer-Android) | Apache-2.0 | Kotlin | 2026-11-26 | 7 | Shows direction, loudness and type of sounds on screen for deaf and hard-of-hearing users. |
| [ChickenD233/FPS-SoundRadar](https://github.com/ChickenD233/FPS-SoundRadar) | Apache-2.0 | C++ | 2026-09-21 | 3 | Game audio radar and right-ear mono downmix for one-sided hearing loss (Windows). Relevant to single-sided listeners like our "one right aid" setup. |
| [Meantub/high-pitch-noise-gen](https://github.com/Meantub/high-pitch-noise-gen) | none | Python | 2022-12-01 | 0 | Keeps a Phonak TV Connector V2 awake with an inaudible tone. **Unmaintained**, curiosity. Rust twin: `Meantub/high-pitch-noise-gen-rs`. |

### 4.L Lists, communities and articles

| Resource | Notes |
|---|---|
| [rscircus/awesome-hearing](https://github.com/rscircus/awesome-hearing) | MIT, last push 2026-11-19, 8 stars. Short awesome list (openMHA, TASCAR, hearingaid-prototype, OpenEarable, SoundVisualizer). The best place to submit our project and the jooray/phonak-ha cluster, none of which it lists yet. [V] |
| [KJSanchez/awesome-hearing-aid](https://github.com/KJSanchez/awesome-hearing-aid) | No licence, last push 2024-02-23, 3 stars. Market and audiology resources. **Unmaintained.** |
| HearingTracker forum | Categories "Hearing Aid Self-Fitting and Adjusting" (`/c/hearing-aid-self-fitting-and-adjusting/15`) and "Hearing Loss Apps" (`/c/hearing-loss-apps/65`). Threads verified reachable: "ASHA for Linux Systems" https://forum.hearingtracker.com/t/asha-for-linux-systems/88522 (links asha_pipewire_sink), "Phonak Paradise Tap Control and Google Assistant" https://forum.hearingtracker.com/t/phonak-paradise-tap-control-and-google-assistant/58895 . |
| Hackaday | "Smartphones And The Next Generation Of Hearing Aids" (2026-08-18, https://hackaday.com/2026/08/18/smartphones-and-the-next-generation-of-hearing-aids/ ), "Tech In Plain Sight: Hearing Aids" (2025-03-26, https://hackaday.com/2025/03/26/tech-in-plain-sight-hearing-aids/ ), "Meet Tympan, The Open Hardware Hearing Aid" (2019-03-01, https://hackaday.com/2019/03/01/meet-tympan-the-open-hardware-hearing-aid/ ). All HTTP 200. The 2026 article links no open-source repositories. |

## 5. Dropped or flagged

| Candidate | Decision | Reason |
|---|---|---|
| [dfgarcia1964-design/noahlink-pro](https://github.com/dfgarcia1964-design/noahlink-pro) | **Do not link** | Presents itself as a "Phonak Hearing Aid Bluetooth Control App" with "Real Device Detection". Its `PHONAK_BLE_PROTOCOL_ANALYSIS.md` is marked "En investigación (Fase 1)" and contains "Ejemplos Hipotéticos" plus wrong assignments (it calls `0x181D` a "Hearing Aid Audio Service"; `0x181D` is the Weight Scale service). No Sonova UUID appears. The README claim is not backed by the repository. [V] |
| [douglasrubims/phonak-apple](https://github.com/douglasrubims/phonak-apple) | Dropped | Empty repository ("You appear to have cloned an empty repository"). [V] |
| [5310H/sig_tasker](https://github.com/5310H/sig_tasker) | Dropped | Only a one-line README ("Tasker to control Signia hearing aid app"), no content. [V] |
| [sk408/hearing_aid_control](https://github.com/sk408/hearing_aid_control) | Linked with caveats (4.B) | No licence, static analysis only, "Pre-development". |
| [PShiw/airpods-hearing-aid-enabler](https://github.com/PShiw/airpods-hearing-aid-enabler), `JerseyRiver/airpods-hearing-aid-hk-guide` | Editorial decision | Region-lock workarounds for a regulated feature. I would leave them out of the README. |
| Dozens of MATLAB/Python coursework repositories named "digital hearing aid", Oticon "Audio Explorers" contest entries, clinic websites | Not listed | No value for our reader. |
| Product pages for `tympan.org` (HTTP 403 to my client) and `openmha.org` (connection failed) | Not verified | Use the GitHub links instead. |

Planning-notes check: of the repositories the notes name, I checked three (`ijustlikeit/phonak-ha`, `OferTiber/SaraButton`, `suddenBook/GPTWake`); all three exist. The Hermes clients and the Flic SDK were outside this sweep. `ijustlikeit/phonak-ha` matches the notes' description; the notes' caution ("kein Beleg für ... Empfang eines konkreten Doppeltipp-Triggers") is correct, see 2.3. The notes' product name "Audéo EON" does not occur in any repository or page I read; I did not check Phonak's product catalogue, so that name is [U].

## 6. What our app can learn (collected)

1. **Microphone over Bluetooth** (from 2.1, 2.2, 2.4, 2.6): communication device + `VOICE_COMMUNICATION`, first-non-zero-frame as the real "listening" moment, silence primer, padded cues, single audible fallback, `clearCommunicationDevice()`. Expect about 0.5 s on SCO; that number is from one Sony headset, not from a Phonak aid. [V as source statement, U for our device]
2. **Foreground-service rule**: a `microphone` foreground service cannot begin recording when started from the background on Android 14+ (2.1, 2.2). Both projects had to accept "open the app once after reboot". This is the strongest argument for testing input path A (VoiceInteractionService) early, because an assistant session is the platform's intended way to get the microphone from the background. That the assistant role lifts the restriction in our exact case is [I] and belongs to the Android-platform research, not to this sweep.
3. **Pure state machines with host-JVM tests** for gestures, source policy and fallback (ButtonEngine, SourcePolicy, bt-mic `policy/`).
4. **adb-drivable debug receiver** in debug builds to simulate every input (2.2).
5. **Audio cue design**: distinct tones for start, saved, sent, failed; spoken confirmation of what was understood; cues padded with silence.
6. **Vendor app coexistence**: both control projects fight the vendor app for the LE link (phonak-ha vs myPhonak, i-hear-you vs ReSound Smart 3D). Aid control must be optional.
7. **Optional Phonak status readout** (battery, program) using the phonak-ha protocol notes (MIT), only after our own HCI capture confirms them on our aid model.
8. **Test rig without the aid**: Bumble-based emulation of a classic HFP headset or an ASHA device (2.10).
9. **Compatibility list fed by issues** (hearing-aid-presets `docs/DEVICES.md`, Pico-ASHA status table): every one of these projects is "tested on my pair only"; ours will be too, and should say so in the same plain way.
10. **Disclaimers**: every serious project carries "not affiliated with <vendor>", "not a medical device", and a volume-safety rule. Ours needs the first two.

## 7. Gaps (things looked for and not found)

- A third-party Android app that receives a **Phonak tap gesture** or any documentation of what the tap sends. [U]
- A custom **VoiceInteractionService assistant built for hearing-aid users**. Searches "hearing aid voice assistant", "hearing aid assistant android", "hearing aids in:readme VoiceInteractionService", "hearing aid tap", "hearing aid double tap" returned nothing relevant. Ours may be the first public one; [U] beyond GitHub search.
- Open control projects for Unitron, Widex, Signia, Bernafon, Starkey hardware. Unitron is a Sonova brand, so the Sonova protocol may apply; that is [I] and untested.
- Home Assistant integrations for any brand other than Phonak.
- Open-source fitting software.
- Web search added little beyond GitHub: the search engine results for reverse-engineering write-ups returned general BLE guides, patents and vendor pages.

## 8. Draft for the README "Related projects" section

```markdown
## Related projects

Other open projects for people who wear hearing aids and like to build things.
Checked on 2026-11-27. Most are young, single-author projects tested on one pair of aids.
None of them is affiliated with a hearing-aid manufacturer, and neither are we.

### Talking to an assistant through hearing aids (closest to this project)
- [I-Hear-You](https://github.com/jooray/i-hear-you) (MIT): Android remote for GN/ReSound aids. Maps the aid's button to phone actions, records voice notes from the aids, sends them to a webhook or an agent and reads the reply into the aids.
- [Recording from a Bluetooth microphone on Android](https://github.com/jooray/android-bluetooth-microphone) (MIT): the write-up we learned the most from. Measured on a Pixel 10 Pro Fold with Android 17.
- [Third Ear](https://github.com/jooray/third-ear) (GPL-3.0) and [bt-mic](https://github.com/mptfire/bt-mic) (MIT): voice recorders that implement that write-up.
- [HARK](https://github.com/thivyan-studios/hark-app) (BSD-3-Clause): turns the phone into a remote microphone for ASHA hearing aids, with offline live captions.

### Controlling hearing aids (volume, programs, battery)
- Phonak: [phonak-ha](https://github.com/ijustlikeit/phonak-ha) (MIT): Home Assistant integration with an Android bridge app and notes on the Sonova protocol. Tested on Audéo Sphere i90.
- GN / ReSound: [I-Hear-You](https://github.com/jooray/i-hear-you) on Android, [hearing-aid-presets](https://github.com/Gheop/hearing-aid-presets) (MIT) on Linux/GNOME through the standard Hearing Access Service.
- Oticon: [Audifern](https://github.com/sabino/audifern) (GPL-3.0) on macOS.
- AirPods hearing features: [LibrePods](https://github.com/librepods-org/librepods) (GPL-3.0) on Android and Linux.

### Streaming from a computer to hearing aids
- ASHA on Linux: built into [BlueZ](https://github.com/bluez/bluez) and PipeWire ([background](https://arunraghavan.net/2025/01/a-brimful-of-asha/)); alternative [asha_pipewire_sink](https://github.com/thewierdnut/asha_pipewire_sink).
- ASHA from any USB port: [Pico-ASHA](https://github.com/shermp/Pico-ASHA) (Raspberry Pi Pico W), with a table of working hearing aids and implant processors.
- ASHA on macOS: [asha-mac](https://github.com/tvqt/asha-mac).
- LE Audio on Linux: [hearing-aid-presets](https://github.com/Gheop/hearing-aid-presets) has the most complete setup notes.
- Auracast from a Mac: [auracast-sender](https://github.com/napthemax/auracast-sender).

### Building or testing Bluetooth hearing devices
- [Bumble](https://github.com/google/bumble) (Apache-2.0): Python Bluetooth stack with ASHA, Hearing Access Profile and Auracast.
- [Zephyr](https://github.com/zephyrproject-rtos/zephyr): Hearing Access Service and a hearing-aid sample (`samples/bluetooth/audio/hap_ha`).
- [Auracast Hacker's Toolkit](https://github.com/auracast-research/auracast-hackers-toolkit): security research on Auracast.

### Open hearing-aid hardware and signal processing
- [Tympan](https://github.com/Tympan/Tympan_Library), [openMHA](https://github.com/HoerTech-gGmbH/openMHA), [OpenEarable 2](https://github.com/OpenEarable/open-earable-2), [CHAPRO](https://github.com/BoysTownOrg/chapro), [Clarity Challenge toolkit](https://github.com/claritychallenge/clarity).

### Fitting
- [Self-programming Phonak Sphere aids with AI assistance](https://github.com/ryleighnewman/Self-Programming-My-Phonak-Hearing-Aids-With-Claude-AI): a detailed write-up. Not medical advice.
- [Fitting software update checkers](https://github.com/JediLin/Hearing-Aids-Fitting-Software-Update-Checkers).

### Live captions and other tools
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (on-device speech recognition), [NowCaption](https://github.com/Techlm77/NowCaption) (Linux), [audiometry](https://github.com/woheller69/audiometry) (Android hearing test), [tinnitus-reducer-acrn](https://github.com/r4ai/tinnitus-reducer-acrn).

### More lists
- [awesome-hearing](https://github.com/rscircus/awesome-hearing)
```

## 9. Searches run

GitHub repository search (`gh api search/repositories`), each query once, 12 to 50 results read:

- Brands: `phonak`, `sonova`, `unitron hearing`, `unitron hearing aid`, `unitron`, `oticon`, `demant hearing`, `resound hearing`, `resound hearing aid`, `resound gn bluetooth`, `gn hearing`, `signia`, `signia hearing`, `signia hearing aid`, `ws audiology`, `rexton`, `widex`, `widex hearing`, `starkey hearing`, `starkey hearing aid`, `bernafon`, `philips hearlink`, `hearlink`, `jabra enhance`, `beltone`, `cochlear nucleus`, `med-el`, `advanced bionics`, `eargo`, `lexie hearing`, `sony hearing aid`, `bose hearing aid`, `nuheara`, `phonak roger`, `roger hearing`, `tv connector hearing`
- Generic: `hearing aid bluetooth`, `hearing aids`, `hearing-aid in:name,description,topics`, `topic:hearing-aid`, `topic:hearing-aids`, `topic:hearing-loss`, `hearing device bluetooth`, `hörgerät`, `hoergeraet`, `hearing in:name aid in:name android stars:>5`
- Protocols: `topic:asha`, `asha hearing aid`, `Audio Streaming for Hearing Aids`, `asha pipewire`, `asha bluetooth hearing`, `asha linux hearing`, `asha hearing mac`, `asha pico`, `asha in:readme hearing aids l2cap`, `mfi hearing`, `made for iphone hearing`, `hearing access profile`, `hearing access service HAS bluetooth`, `le audio hearing`, `le audio broadcast android`, `bluetooth le audio linux hearing aids`, `auracast`, `auracast hearing`, `auracast assistant`, `hfp hearing aid`, `hands-free profile hearing aids`, `hearing aids sco microphone`, `airpods hearing aid`, `airpods hearing`
- Control and reverse engineering: `hearing aid remote control`, `hearing aid control app`, `hearing aid volume program`, `hearing aid reverse engineering`, `hearing aid protocol`, `hearing aid BLE`, `hearing aid gatt`, `hearing aid python bleak`, `hearing aid web bluetooth`, `hearing aid battery`, `hearing aid find lost`, `hearing aid in:readme bluetooth reverse engineer`, `hearing aids in:readme btsnoop`, `hearing aid in:readme gatt characteristic volume program`, `hearing aid companion app open source`
- Automation: `hearing aid home assistant`, `hearing aids homeassistant`, `esphome hearing`, `hearing aids openhab OR mqtt`, `hearing aid tasker`, `hearing aid wear os`, `hearing aid notifications`, `hearing aid tts announcements`, `hearing aids in:readme webhook`
- Platforms: `hearing aid linux`, `hearing aid pipewire`, `hearing aid gnome`, `hearing aid windows streaming`, `hearing aid macos`, `hearing aids macos menu bar`, `hearing aid swift`, `hearing aid android app kotlin`, `hearing aid stream`, `hearing aid microphone android`, `bluetooth microphone android hearing`, `sound amplifier android hearing`
- Assistants: `hearing aid voice assistant`, `hearing aid assistant android`, `hearing aid tap`, `hearing aid double tap`, `hearing aids agent voice`, `hearing aid mcp`, `hearing aid claude`, `hearing aids in:readme VoiceInteractionService`, `hearing aids assistant in:readme android lock screen`
- Hardware and DSP: `tympan`, `openMHA`, `open source hearing aid`, `hearing aid esp32`, `hearing aid nrf`, `hearing aid zephyr`, `telecoil`, `hearing loop`
- Fitting: `hearing aid fitting software`, `hearing aid programming`, `hearing aid diy fitting`, `noahlink`, `noahlink wireless`, `hi-pro hearing`, `audiogram`
- Other purposes: `tinnitus`, `tinnitus masker`, `notched music tinnitus`, `live captions hard of hearing`, `live caption android offline`, `hearing aid captions glasses`, `smart glasses captions deaf`, `hearing loss app`, `cochlear implant bluetooth`, `cochlear implant app`

GitHub code search (`gh api search/code`): `d099cd20-b2ea-11e5-bf01-0002a5d5c51b` (Sonova service), `2016ec20-b2eb-11e5-a307-0002a5d5c51b`, `56772eaf-2153-4f74-acf3-4368d99fbf5a`, `1959A468-3234-4C18-9E78-8DAF8D9DBF61` (GN command), `9a04f079-9840-4286-ab92-e65be0885f95`, `7d74f4bd-c74a-4431-862c-cce884371592`, `6333651e-c481-4a3e-9169-7c902aad37bb` and `f0d4de7e-4a88-476c-9d9f-1937b0996cc0` (ASHA characteristics), `Sonova hearing aid bluetooth protocol`, `myPhonak btsnoop`, `hearing aid HFP tap assistant android`, plus repository-scoped checks in `librepods-org/librepods`, `home-assistant/android`, `zephyrproject-rtos/zephyr`.

Web searches (generic, no personal or project names): reverse engineering of hearing-aid BLE control protocols; open Phonak/myPhonak protocol work; ASHA on Linux with BlueZ and PipeWire; hearing-aid tap control and the default assistant on Android; Android hearing-aid preset API (`BluetoothHapClient`); open-source hearing-aid projects (Tympan, Open Speech Platform, openMHA); Hackaday hearing-aid coverage; Home Assistant hearing-aid integrations; voice assistant or LLM agent through a hearing-aid button.

Pages fetched and read: Phonak knowledge base "myPhonak and Tap Control"; arunraghavan.net "A Brimful of ASHA"; Collabora LE Audio article; HearingTracker threads and category index; Hackaday 2026-08-18 article; AOSP `BluetoothHapClient.java` (main) and `AudioDeviceInfo.java` (main and android17-release) from android.googlesource.com.

Repositories shallow-cloned and read under `scratchpad/repos/`: phonak-ha, Phonak_ha, i-hear-you, android-bluetooth-microphone, third-ear, hark-app, hearing_aid_control, oticon-connect, audifern, hearing-aid-presets, awesome-hearing, awesome-hearing-aid, asha_pipewire_sink, phonak-apple (empty), Self-Programming-My-Phonak-Hearing-Aids-With-Claude-AI, sig_tasker, linux-le-hearing-aids, HARC, asha-windows, auracast-sender. Others were read through the GitHub contents and README API. Nothing was built or run.

Raw data: `scratchpad/research/hearing/verified.tsv` (API metadata of 100 repositories), `results.tsv` (about 800 unique search hits), `queries.txt`.
