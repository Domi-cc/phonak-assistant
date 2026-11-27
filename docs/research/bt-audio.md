# Two-way audio between our app and one Phonak hearing aid on a locked, screen-off Pixel

Research date: 2026-11-27. All web pages and source files were fetched on that day.
AOSP excerpts come from the branches `android16-release` (API 36) and `android17-release` (API 37) on
android.googlesource.com. Local copies of every file quoted are under
`<repos>/aosp-files/<branch>/<project>/<path>`.

Status markers used on every statement:

- **[V]** verified: seen in a primary source (vendor document, developer.android.com, AOSP source). Source is given.
- **[I]** inferred: reasoned from verified sources, not stated by any of them.
- **[U]** unknown: no primary source found. Needs a device test.
- **[C]** community report (forum, reverse engineering). Treat as a hint, not as a fact.

Nothing in this report has been run on a device. Everything about the real Phonak plus Pixel combination
is at best [I] until the test in section 6 has been run.

---

## 0. Short answers

1. **The Phonak Audéo EON exists.** [V] Launched 24 August 2026 (Germany: September 2026). Models: Audéo EON R,
   Audéo EON Sphere, CROS EON R. The inherited notes say "CROS EON"; the product name is "CROS EON R".
2. **Radio:** Bluetooth Core 5.3, dual-mode. Profiles: HFP, A2DP, PBP (Auracast broadcast receive). [V]
   No LE Audio unicast and no ASHA. [V for the published profile list, I for "therefore none"]
   The only microphone path into Android is **Classic HFP over a SCO/eSCO link**.
3. **Android device types:** the aid shows up as `TYPE_BLUETOOTH_SCO` (input and output) and `TYPE_BLUETOOTH_A2DP`
   (output). It is not `TYPE_HEARING_AID` (ASHA, output only), not `TYPE_BLE_HEADSET` and not the new
   `TYPE_BLE_HEARING_AID` (API 37). [I, from the profile list plus the AOSP type mapping]
4. **API sequence:** `AudioManager.getAvailableCommunicationDevices()` -> `setCommunicationDevice(scoDevice)` ->
   wait for `OnCommunicationDeviceChangedListener` -> `AudioTrack` with `USAGE_VOICE_COMMUNICATION` and
   `AudioRecord` -> `clearCommunicationDevice()`. The communication-device API exists since API level 31;
   `startBluetoothSco()` / `stopBluetoothSco()` are marked "deprecated in API level 34". [V]
5. **The phone is almost certainly on Android 17 (API 37), not 16.** Android 17 was released on 16 June 2026 for
   supported Pixels. [V] It adds "background audio hardening", which silently mutes playback and fails audio
   focus for apps in the wrong lifecycle state. [V] This matters more for our design than anything Bluetooth.
6. **Locked and screen-off does not change audio routing.** [I] What changes is *permission to record and to play*:
   `RECORD_AUDIO` is a while-in-use permission. [V] The cleanest way to hold it in the background is to be the
   device's default assistant: the system binds the active `VoiceInteractionService` with
   `BIND_INCLUDE_CAPABILITIES`. [V]
7. **`BluetoothHeadset.startVoiceRecognition()` is public and needs only `BLUETOOTH_CONNECT`.** [V] It is the
   mandatory answer to a headset-initiated voice command (`AT+BVRA=1`): the Bluetooth stack starts an *activity*
   with `Intent.ACTION_VOICE_COMMAND` and waits 5 seconds for that call. [V] This is not routed through
   `VoiceInteractionService`, which is what the inherited notes assume.
8. **Recommendation for v1:** one "SCO session" per interaction, opened on activation and closed at the end,
   used for prompts and for recording. Verify the routed input device before every recording and refuse to record
   if it is not `TYPE_BLUETOOTH_SCO`. Details in section 6.

---

## 1. Hardware facts

### 1.1 Does "Audéo EON" exist?

| Statement | Status | Source |
|---|---|---|
| Phonak launched the EON platform on 24 Aug 2026. US orders from that day; "Germany, France, the United Kingdom, Australia and others, to follow in September". | [V] | Sonova media release, https://www.sonova.com/wp-content/uploads/2026/08/Phonak_Media-Release_EON.pdf : "Stäfa, Switzerland – August 24, 2026 – Phonak today launched EON, its next-generation hearing aid platform." |
| Portfolio: "Phonak Audéo EON Sphere (mild to profound hearing loss)", "Phonak Audéo EON R (mild to profound hearing loss)", "Phonak CROS EON R (unilateral hearing loss)", chargers "ChargerGo RIC I E" and "Charger RIC E". | [V] | same PDF, section "The EON portfolio" |
| Chips: HYPERSONIC (signal processing), ERA (wireless). "Behind these connectivity experiences is the proprietary ERA chip". | [V] | PDF; https://audiologyblog.phonakpro.com/phonak-eon-connectivity-excellence/ (17 Sep 2026) |
| Predecessor: Infinio (2024), Infinio Ultra (2025). EON is the successor. | [I] | The blog and knowledge base treat "Infinio Ultra and newer" as one behaviour group |
| Bluetooth SIG listing 295886 "Louis", published 2026-08-24, "Phonak Hearing aid with Bluetooth 5.3", models E30-R, E50-R, E70-R, E90-R, E50/E70/E90-Sphere, CROS E-R and Trial/Demo variants. | [V] | `POST https://qualificationapi.bluetooth.com/api/Platform/Listings/Search` (search "Sonova") and `/api/Platform/Listings/Details/295886`. The profile ICS of the listing needs a login and was not readable. |
| FCC ID KWC-ERF, IC 2262A-ERF. | [V] | EON R user guide, page 52 |

The inherited notes name "Audéo EON R", "Audéo EON Sphere" and "CROS EON". The first two are right. The third is
"CROS EON R".

### 1.2 Bluetooth version and profiles

Source: user guide "Phonak Audéo E-R / E-Sphere", document 029-1462-03/V1.00/2026-03,
https://www.phonak.com/content/dam/celum/phonak/master-assets/documents/hearing-instruments/eon/audeo-e-r/user-guides/ph-user-guide-audeo-e-92x125-029-1462-03-en-us.pdf

Radio table on page 53, verbatim:

```
Antenna type           Resonant loop antenna
Operation frequency    2.4 GHz – 2.48 GHz
Modulation             GFSK, Pi/4-DQPSK, 8-DPSK, GMSK
Radiated power         < 2.5 mW
Bluetooth®
Core version           Bluetooth® Core 5.3, Dual-Mode
Profiles supported     HFP (Hands-free profile), A2DP,
                       PBP (Public Broadcast Profile)
```

| Statement | Status | Source |
|---|---|---|
| Core 5.3, dual-mode (BR/EDR plus LE). | [V] | table above |
| Classic HFP: yes. | [V] | table above |
| Classic A2DP: yes. | [V] | table above |
| AVRCP: **not listed**. Tap Control offers "pause/resume streaming", so some remote-control channel exists. | [V] not listed / [I] AVRCP in practice | table above; user guide chapter 9 |
| LE Audio unicast (BAP/TMAP/HAP): not listed. Phonak says it stays on Classic for calls and streaming. | [V] | table above; https://audiologyblog.phonakpro.com/what-to-know-about-bluetooth-classic-bluetooth-le-audio-and-auracast-broadcast-and-why-phonak-uses-bluetooth-classic-today/ : "At Phonak, we continue to use Bluetooth Classic because it provides the most universal and seamless connectivity experience" |
| Auracast: receive only (PBP broadcast sink). Needs myPhonak 7.5 or newer. Supports "High Quality (HQ) bitrate as well as the Standard Quality (SQ) bitrate". | [V] | KB https://www.phonak.com/en-us/support/knowledge-base/articles/what-do-i-need-to-use-auracast-broadcast ; EON connectivity blog |
| ASHA: not listed anywhere. | [I] not supported | absence in the guide and in Phonak's Bluetooth pages |
| LE is used for the myPhonak app link and for fitting. | [V] | Infinio Ultra blog: "Bluetooth LE supports low-power data exchange (for instance, connecting to the myPhonak app or NOAHLink Wireless during a fitting session...)" |
| The CROS EON R transmitter lists "Bluetooth® Core 5.3, LE Audio", modulation "GFSK, GMSK" (no EDR modulation). | [V] | CROS user guide 029-1461-03, https://www.phonak.com/content/dam/celum/phonak/master-assets/documents/hearing-instruments/eon/cros-e-r/user-guides/ph-user-guide-cros-e-92x125-029-1461-03-en-us.pdf |

Consequence [I]: a future firmware with bidirectional LE Audio is not announced. The architecture must not depend on it.

### 1.3 Which aid carries the Classic link, pairing entries, single aid

| Statement | Status | Source |
|---|---|---|
| Up to and including Infinio (non-Ultra): one aid, chosen by the fitter in Target as the "Bluetooth Side", holds the Classic link and is also "the side used for own voice pickup". The phone showed three entries (one Classic, two `LE_`). | [V] for older generations | https://www.audiologyonline.com/ask-the-experts/phonak-troubleshoot-bluetooth-issues-27921 (expert Q&A about Phonak devices, 14 Jul 2021; a trade-site article, not a Phonak document); KB https://www.phonak.com/en-au/support/knowledge-base/articles/three-hearing-aids-in-the-bluetooth-phone-list |
| Infinio Ultra and newer (this includes EON): with two aids the phone shows two entries, "R-Phonak hearing aid" and "L-Phonak hearing aid". "If you wear one hearing aid, then you see only one of them in your Bluetooth menu. Either the L (left) or R (right)." | [V] | KB https://www.phonak.com/en-us/support/knowledge-base/articles/what-hearing-aid-should-be-selected-from-the-list-on-the-mobile-phone |
| EON with two aids: "left-right diversity is designed to use the hearing aid with the stronger connection to support wireless stability". | [V] | EON connectivity blog |
| One-step pairing: pairing through myPhonak also sets up calls and streaming. | [V] | KB https://www.phonak.com/en-us/support/knowledge-base/articles/connecting-phonak-hearing-aid-to-an-android-smartphone (note at the end) |
| With a single right aid, that aid holds the Classic link and its microphones are the hands-free microphones. | [I] | follows from the two rows above; there is no second aid |
| Which aid picks up the voice in a binaural EON fitting. | [U] | not documented for EON |

### 1.4 Hands-free microphone

| Statement | Status | Source |
|---|---|---|
| "Phone calls are 'hands free', this means your voice is picked up by the hearing aid's microphones and transmitted back to the phone." | [V] | EON R user guide, chapter 14 |
| A fitter can switch hands-free calls off: "Hands-free Calls are disabled in Target ... This setting allows using your hearing aids in high-security environments, such as SCIF, by disabling hands-free calls. Only your hearing care professional can enable it again". | [V] | KB https://www.phonak.com/en-us/support/knowledge-base/articles/why-cant-i-take-a-call-by-pressing-the-button-on-my-phonak-hearing-aids |
| The user can switch "Bluetooth streaming" off in myPhonak on Infinio Ultra and later. | [V] | same KB article |
| Call audio bandwidth is a setting: "Normal sound quality (fixed bandwidth)" or "Enhanced sound quality (adaptive bandwidth)", under Devices > Hearing aid settings > Bluetooth phone calls. The aids must be restarted after a change. | [V] | KB https://www.phonak.com/en-us/support/knowledge-base/articles/myphonak-app-how-to-change-the-bandwidth |
| "Fixed" means narrowband CVSD only, "adaptive" means HFP codec negotiation with wideband mSBC. | [I] | Phonak does not name the codecs. |
| LC3 super-wideband (HFP 1.9) support in EON. | [U] | Phonak publishes no HFP version. |
| Quality of the behind-the-ear microphones for speech recognition, wind and noise behaviour, own-voice beamforming. | [U] | needs recordings |
| The microphone uplink is on air for as long as the SCO link is open, also while our app only plays a prompt. The phone discards it unless an `AudioRecord` is running. | [I] | SCO links are bidirectional by design. Relevant for the "microphone only after activation" requirement: the app-level guarantee holds, the radio-level one does not while SCO is used for output. |

### 1.5 Paired and connected devices

| Statement | Status | Source |
|---|---|---|
| "Your hearing aid can be connected to up to two devices and paired to up to eight devices." | [V] | EON R user guide, chapter 13.2 |
| Two simultaneous Classic connections (multipoint) is a Phonak feature. | [V] | Classic-vs-LE blog: "Phonak hearing aids go further by offering two simultaneous connections with Bluetooth Classic" |
| CROS EON R system: "can be connected to one device at a time and paired to up to eight devices". "During streaming of phone calls or music to the hearing aid, the signal from the CROS device is no longer transmitted". | [V] | CROS user guide chapters 12.2 and 22.1 |
| Pairing mode lasts 3 minutes after switch-on. Reconnect out of range: short press on the button. | [V] | EON R user guide chapter 13 |

### 1.6 Tap Control

| Statement | Status | Source |
|---|---|---|
| "multiple functions can be accessed using Tap Control e.g. accept/end phone call, pause/resume streaming, start/stop voice assistant ... To use Tap Control, tap the top of your ear twice." | [V] | EON R user guide chapter 9 |
| Configured in myPhonak under Devices; sensitivity adjustable on some models. | [V] | KB https://www.phonak.com/en-us/support/knowledge-base/articles/myphonak-and-tap-control |
| "start/stop voice assistant" is sent as HFP `AT+BVRA=1` / `AT+BVRA=0`. | [I] | standard HFP mechanism; not stated by Phonak. Device test: logcat line `startVoiceRecognitionByHeadset` from `HeadsetService`. |
| During an open call (including Android's "virtual call", see 3.4) a double tap sends hang-up. | [I] | user guide: "The phone call can also be ended by tapping the top of your ear twice." |

### 1.7 Streaming start latency and program switching

| Statement | Status | Source |
|---|---|---|
| "When the hearing aids receive an audio signal from the smartphone a streaming program will be activated automatically. When the streaming is finished, the program that was most recently used will be activated." | [V] | KB https://www.phonak.com/en-us/support/knowledge-base/articles/hearing-aids-switch-program-when-receiving-a-sound-notification |
| Phonak's own advice against unwanted switching: "switch off tones from the smartphone settings, e.g., system tones, touch tones, keyboard tones, screen lock tones ..." | [V] | same article |
| Screen-reader speech counts as streaming: "the hearing aids perceive the voice of these functionalities as streaming and switch automatically to the streaming program". | [V] | KB https://www.phonak.com/en-us/support/knowledge-base/articles/hearing-aids-switch-to-streaming-program-when-talkback-or-voiceover-is-activated |
| After a call the aids "return to the last program you were using". | [V] | KB https://www.phonak.com/en-us/support/knowledge-base/articles/switch-between-talking-on-the-phone-and-watching-tv |
| The mix between streamed sound and surroundings is adjustable ("Ambient balance"). | [V] | myPhonak 7.5 user guide, page 12 |
| Time from stream start to audible sound; whether the first syllable is lost; whether a beep marks the switch. | [U] | Phonak publishes no figures. |
| A user of an Audéo I90 Sphere: "The HAs will take maybe three seconds to click out of BT streaming." | [C] | forum.hearingtracker.com/t/.../114825 (Aug 2026) |
| On an Infinio i90 Sphere the aid reports program id `0x07` "Bluetooth streaming & Mic" over its LE control link, "entered by the aid while the phone streams". The active program id is readable (parameter `0x002b`). | [C] | github.com/ijustlikeit/phonak-ha, PROTOCOL.md (MIT, reverse engineered). Could serve as a measuring instrument for program-switch timing. |

---

## 2. How Android exposes such a device

Source: `frameworks/base/media/java/android/media/AudioDeviceInfo.java` (both branches).

| Constant | Value | Meaning | Direction | Applies to Phonak EON? |
|---|---|---|---|---|
| `TYPE_BLUETOOTH_SCO` | 7 | Classic HFP/HSP audio link | output and input (`DEVICE_IN_BLUETOOTH_SCO_HEADSET`) | **Yes.** The only input path. [I] |
| `TYPE_BLUETOOTH_A2DP` | 8 | Classic media stream | output (a `DEVICE_IN_BLUETOOTH_A2DP` mapping exists [V]; it is for the phone acting as sink [I]) | **Yes**, for media output. [I] |
| `TYPE_HEARING_AID` | 23 | "A device type describing a Hearing Aid." Used for ASHA. | **output only**: there is no `DEVICE_IN_*` entry for it in `INT_TO_EXT_DEVICE_MAPPING` / `EXT_TO_INT_INPUT_DEVICE_MAPPING` [V] | No. Phonak does not list ASHA. [I] |
| `TYPE_BLE_HEADSET` | 26 | LE Audio unicast headset | output and input | No. [I] |
| `TYPE_BLE_SPEAKER` | 27 | LE Audio speaker | output | No |
| `TYPE_BLE_BROADCAST` | 30 | the phone as Auracast *source* | output | Not a microphone path. The aid can receive Auracast, but that is one-way. [I] |
| `TYPE_BLE_HEARING_AID` | 33 | new in API 37: "A device type describing a Bluetooth Low Energy (BLE) hearing aid." (`@FlaggedApi(FLAG_BLE_HEARING_AID_DEVICE)`) | output and input | No. This is for LE Audio hearing aids (HAP). [V constant, I applicability] |
| `TYPE_BLE_CENTRAL`, `TYPE_BLE_CENTRAL_BROADCAST` | 34, 35 | new in API 37, phone in peripheral role | n/a | No |

Official wording for the new type: "Bluetooth LE Audio hearing aid support: Android now includes a specific device
category for Bluetooth Low Energy (BLE) Audio hearing aids with the new AudioDeviceInfo.TYPE_BLE_HEARING_AID
constant". [V] https://developer.android.com/blog/posts/android-17-is-here (16 Jun 2026)

Which types may be passed to `setCommunicationDevice()`: `AudioDeviceBroker.VALID_COMMUNICATION_DEVICE_TYPES` [V]:
`TYPE_BUILTIN_SPEAKER, TYPE_BLUETOOTH_SCO, TYPE_WIRED_HEADSET, TYPE_USB_HEADSET, TYPE_BUILTIN_EARPIECE,
TYPE_WIRED_HEADPHONES, TYPE_HEARING_AID, TYPE_BLE_HEADSET, TYPE_USB_DEVICE, TYPE_BLE_SPEAKER, TYPE_LINE_ANALOG,
TYPE_HDMI, TYPE_AUX_LINE, TYPE_BUS` and, on API 37, `TYPE_BLE_HEARING_AID`.
**`TYPE_BLUETOOTH_A2DP` is not in the list.** Passing an A2DP device throws
`IllegalArgumentException("invalid device type: ...")`. [V] `AudioService.setCommunicationDevice`

Practical consequences:

- Code must select by capability, not by product: "an available communication device of a type that has a matching
  input device". For Phonak today that is `TYPE_BLUETOOTH_SCO`. The same code then also works with an LE Audio
  hearing aid (`TYPE_BLE_HEADSET` / `TYPE_BLE_HEARING_AID`) if the hardware is ever swapped. [I]
- Bluetooth addresses in audio device objects are only real for callers holding `BLUETOOTH_CONNECT`;
  otherwise the audio service strips them (`AudioService.isBluetoothPrividged()`). [V] We need the permission anyway.
- Android's "hearing devices" settings and the Android 17 "granular audio routing for hearing aids" feature are
  built around hearing-aid device categories. Whether a Classic-only Phonak is treated as a hearing device by
  Settings is [U]. Do not rely on it.

---

## 3. Recommended API sequence (API 36 and API 37)

### 3.1 What is deprecated

| API | State | Source |
|---|---|---|
| `AudioManager.startBluetoothSco()` | "Added in API level 8. Deprecated in API level 34." "Use `AudioManager#setCommunicationDevice(AudioDeviceInfo)` instead." | [V] https://developer.android.com/reference/android/media/AudioManager ; `@Deprecated` in AudioManager.java, both branches |
| `AudioManager.stopBluetoothSco()` | "deprecated in API level 34. Use `AudioManager#clearCommunicationDevice()` instead." | [V] same |
| `setCommunicationDevice()`, `clearCommunicationDevice()`, `getAvailableCommunicationDevices()`, `addOnCommunicationDeviceChangedListener()` | "Added in API level 31" | [V] same reference page |
| `AudioManager.setSpeakerphoneOn()`, `isBluetoothScoOn()`, `isSpeakerphoneOn()` | replaced by the communication-device API | [V] javadoc of `setCommunicationDevice` / `getCommunicationDevice` |
| `setBluetoothScoOn()` | not deprecated but "should only be used by applications that replace the platform-wide management of audio settings or the main telephony application" | [V] |
| Migration note | "Starting in Android 13 (API level 33), applications must migrate from `AudioManager#startBluetoothSco` to `AudioManager#setCommunicationDevice` to support BLE audio headsets. This change is backward-compatible with Hands-Free Profile (HFP) devices." | [V] https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-manager |
| `setCommunicationDevice()` on API 37 | still public and not deprecated. The server now logs it, `setMode`, `startBluetoothSco` and others as "discouraged API usage" metrics (`logDiscouragedApiUsage(... AUDIO_API_COMMUNICATION_DEVICE ...)`). No behaviour change. | [V] AudioService.java, android17-release |
| `AudioManager.createAudioModeSession()` (API 37) | `@SystemApi(client = MODULE_LIBRARIES)`, needs `MODIFY_PHONE_STATE`. **Not available to us.** | [V] AudioManager.java, android17-release |

### 3.2 The sequence

```kotlin
val am = getSystemService(AudioManager::class.java)

// 1. Find the hearing aid as a communication device (sink role).
val scoOut = am.availableCommunicationDevices
    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO /* && it.address == configuredAddress */ }
    ?: return fail("HFP not connected")

// 2. Audio focus, so other players pause instead of being dragged onto the SCO link (see 4.4).
val attrs = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
    .setAudioAttributes(attrs).setOnAudioFocusChangeListener { /* abort on loss */ }.build()
if (am.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) log("focus denied: lifecycle?")

// 3. Ask for the route and WAIT for confirmation. Never assume it is there when the call returns.
val ready = CompletableDeferred<Unit>()
val l = AudioManager.OnCommunicationDeviceChangedListener { d -> if (d?.id == scoOut.id) ready.complete(Unit) }
am.addOnCommunicationDeviceChangedListener(executor, l)
check(am.setCommunicationDevice(scoOut))          // false = rejected
withTimeout(5_000) { ready.await() }              // docs allow up to 30 s; fail fast for a menu

// 4. Output: our own AudioTrack, 16 kHz mono PCM16.
val track = AudioTrack.Builder().setAudioAttributes(attrs)
    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(16_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
    .setTransferMode(AudioTrack.MODE_STREAM).build()
track.play()                                      // then: pre-roll, earcon, speech (see 4.3)

// 5. Input.
val scoIn = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
    .first { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
val rec = AudioRecord.Builder()
    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION) // VOICE_RECOGNITION is a test variant, see 3.5
    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(16_000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
    .setBufferSizeInBytes(/* >= 2 * minBufferSize */).build()
check(rec.state == AudioRecord.STATE_INITIALIZED) // build() can return an uninitialised instance
rec.setPreferredDevice(scoIn)                     // belt and braces; does not open the link by itself
rec.startRecording()
// 6. PROVE the route before trusting a single sample (privacy requirement).
val routed = rec.routedDevice                     // null unless currently recording
val cfg = am.activeRecordingConfigurations        // audioDevice, isClientSilenced, format
if (routed?.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO || cfg.any { it.isClientSilenced }) abort()

// 7. End of session, always, also on every error path.
rec.stop(); rec.release(); track.stop(); track.release()
am.clearCommunicationDevice()
am.abandonAudioFocusRequest(focus)
am.removeOnCommunicationDeviceChangedListener(l)
```

Sources for the individual statements:

| Statement | Status | Source |
|---|---|---|
| "only devices in a sink role ... can be specified. The matching source device is selected automatically by the platform." | [V] | javadoc of `setCommunicationDevice` |
| "The selection is active as long as the requesting application process lives, until `clearCommunicationDevice` is called or until the device is disconnected. It is therefore important for applications to clear the request when a call ends". | [V] | same |
| Priority between apps: "the priority is given to the application currently controlling the audio mode ... the latest application having selected mode MODE_IN_COMMUNICATION or mode MODE_IN_CALL". | [V] | same |
| "After you set a communication device, wait a maximum of 30 seconds. ... If an error occurs, call `clearCommunicationDevice`, and then call `setCommunicationDevice`." | [V] | https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-manager |
| Google's own sample does exactly this and does **not** call `setMode()`: `selectDevice()` wraps `setCommunicationDevice` in `withTimeout(30 s)` and waits for `OnCommunicationDeviceChangedListener`; the loopback uses `USAGE_VOICE_COMMUNICATION` and `AudioSource.VOICE_COMMUNICATION`. | [V] | github.com/android/platform-samples, `samples/connectivity/audio/.../AudioDeviceState.kt` and `AudioLoopSource.kt` (Apache-2.0) |
| `AudioRecord.getRoutedDevice()`: "The query is only valid if the AudioRecord is currently recording. If it is not, getRoutedDevice() will return null." | [V] | AudioRecord.java |
| `AudioRecordingConfiguration.isClientSilenced()`: "Returns true if the audio returned to the client is currently being silenced by the audio framework due to concurrent capture policy (e.g the capturing application does not have an active foreground process or service anymore)." | [V] | AudioRecordingConfiguration.java |
| `AudioRecordingConfiguration.getFormat()` returns "the audio format at which audio is recorded on this Android device", which "may differ from the client application recording format". Its sample rate (8000 / 16000 / 32000) should reveal the negotiated SCO codec class. | [V] API / [I] codec inference | AudioRecordingConfiguration.java |
| `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`: "Examples of exclusive and transient audio focus requests are voice memo recording and speech recognition, during which the system shouldn't play any notifications, and media playback should have paused." | [V] | AudioManager.java |
| `setCommunicationDevice()` itself has no permission or foreground check in the server; `setMode()` and `startBluetoothSco()` check `MODIFY_AUDIO_SETTINGS`. | [V] | AudioService.java |
| `AudioRecord.setPreferredDevice(scoInput)` alone does not create the SCO link. The link is created by the Bluetooth stack when the audio service asks for it (section 3.4). Without the link the SCO input delivers silence. | [I] | no primary statement; follows from 3.4. Part of the device test. |

### 3.3 Who owns the route: the six-second rule

This is the part most likely to bite. A non-privileged app's route request is only honoured while the app is
"active". Source: `AudioDeviceBroker.java` (android16-release); `RouteSelectionStack.java` plus
`ActivityDebouncer.java` (android17-release).

```java
// android16-release, AudioDeviceBroker.java
/**
 * Returns the communication client with the highest priority:
 * - 1) the client which is currently also controlling the audio mode
 * - 2) the first client in the stack if there is no audio mode owner
 * - 3) no client otherwise
 */
private CommunicationRouteClient topCommunicationRouteClient() { ... }

// check playback or record activity after 6 seconds for UIDs
private static final int CHECK_CLIENT_STATE_DELAY_MS = 6000;

public boolean isActive() {            // CommunicationRouteClient
    return !mDisabled && (mIsPrivileged || mRecordingActive || mPlaybackActive);
}
// addCommunicationRouteClient():
//   "initialize the inactive client's state as active and check it after 6 seconds"
```

```java
// android17-release, AudioDeviceBroker.java / RouteSelectionStack.java
private static final int CLIENT_INACTIVITY_DEBOUNCE_MS = 6000;
// selectedDevice(): "1) the client which is currently also controlling the audio mode
//                    2) the top active client in the stack if there is no audio mode owner
//                    3) empty otherwise"
public boolean isActive() { return mIsPrivileged || mStreamActive; }   // RouteClient
```

What counts as activity [V]:

- For the route client (no `setMode` involved): any active playback of our UID or any non-silenced recording of
  our UID. android16: `updateCommunicationRouteClientsActivity()` checks `config.getClientUid() == crc.getUid()`
  with `config.isActive()` for playback and `!config.isClientSilenced()` for recording. android17: the union of
  `mPlaybackMonitor.getPlaybackActiveUids()` and `mRecordMonitor.getRecordingActiveUids()`.
- For the audio-mode owner (only if we call `setMode(MODE_IN_COMMUNICATION)`), the bar is higher:
  "It requests mode MODE_IN_COMMUNICATION, and it is either playing or recording for VOICE_COMMUNICATION"
  (`SetModeDeathHandler.isActive()`); playback counts only with `USAGE_VOICE_COMMUNICATION` or
  `USAGE_VOICE_COMMUNICATION_SIGNALLING`, recording only with `AudioSource.VOICE_COMMUNICATION` and not silenced.
  Grace period: `CHECK_MODE_FOR_UID_PERIOD_MS = 6000`.

Design consequences [I]:

1. After `setCommunicationDevice()` we have about six seconds to start playing or recording. If our UID then
   stays silent for more than six seconds (waiting for the server, the user thinking), the route is dropped and
   the SCO link is closed. Keep one `AudioTrack` in the playing state for the whole session and feed it zeros or
   a quiet "thinking" earcon between prompts.
2. **`TextToSpeech.speak()` does not count as our playback.** The framework's TTS path creates the track inside
   the TTS engine's process: `BlockingAudioTrack.createStreamingAudioTrack()` does
   `new AudioTrack(mAudioParams.mAudioAttributes, audioFormat, bufferSizeInBytes, AudioTrack.MODE_STREAM, mAudioParams.mSessionId)`.
   [V] `frameworks/base/core/java/android/speech/tts/BlockingAudioTrack.java`. The playback is attributed to
   the engine's UID. It would not keep our route alive, and on Android 17 the engine's own lifecycle state decides
   whether it is audible. Use `TextToSpeech.synthesizeToFile()` (or server-side TTS) and play the PCM through our
   own `AudioTrack`. This also gives us control over pre-roll and caching.
3. `setMode(MODE_IN_COMMUNICATION)` is not required and Google's sample does not use it. It would give our
   request priority over other apps' requests, at the price of platform-wide side effects (volume keys control
   call volume, other apps see a "call"). Leave it out of v1 and decide after the device test.
4. If another app owns `MODE_IN_COMMUNICATION` or a phone call is active, our request loses. From the legacy
   javadoc: "if a call is received or sent while an application is using the SCO connection, the connection will
   be lost for the application and NOT returned automatically when the call ends." Listen for the communication
   device changing away and end the session cleanly.

### 3.4 What actually happens on the Bluetooth side

| Statement | Status | Source |
|---|---|---|
| `setCommunicationDevice(SCO)` reaches `BtHelper.startBluetoothSco()` with `SCO_MODE_UNDEFINED`, which resolves to `SCO_MODE_VIRTUAL_CALL` and calls `BluetoothHeadset.startScoUsingVirtualVoiceCall()`. | [V] | AudioDeviceBroker.setCommunicationDevice, BtHelper.requestScoState / connectBluetoothScoAudioHelper (android16-release) |
| On API 36 the mode can be overridden per device through `Settings.Global` key `"bluetooth_sco_channel_" + address` (0 = virtual call, 2 = voice recognition). On API 37 this key and the VR branch are gone; only the virtual call remains. | [V] | BtHelper.java, both branches. Debug knob for API 36 only: `adb shell settings put global bluetooth_sco_channel_<MAC> 2` |
| A virtual call fakes an outgoing call towards the headset: `phoneStateChanged(0, 0, CALL_STATE_DIALING ...)`, then `CALL_STATE_ALERTING`, then one active call. | [V] | HeadsetService.startScoUsingVirtualVoiceCall (android17-release) |
| So the hearing aid believes a phone call is running and switches to its call behaviour. | [I] | consequence of the row above |
| A hang-up from the headset ends it: "Close the virtual call if active. Virtual call should be terminated for CHUP callback event". | [V] | HeadsetSystemInterface.hangupCall |
| Three mutually exclusive SCO modes exist: "Mode 1: Telecom call ... Mode 2: Virtual call through startScoUsingVirtualVoiceCall() Mode 3: Voice recognition through startVoiceRecognition(BluetoothDevice). When one mode is active, other mode cannot be started." | [V] | class comment of HeadsetService |
| `startScoUsingVirtualVoiceCall()` is `@SystemApi` and needs `MODIFY_PHONE_STATE` plus `BLUETOOTH_PRIVILEGED`. Apps reach it only indirectly through `setCommunicationDevice`. | [V] | BluetoothHeadset.java |
| SCO opened by someone else (state `SCO_STATE_ACTIVE_EXTERNAL`, for example after `BluetoothHeadset.startVoiceRecognition`) is picked up by the audio service: `mDeviceBroker.setBluetoothScoOn(true, "BtHelper.onScoAudioStateChanged ...")`. Routing then follows the SCO device. | [V] | BtHelper.onScoAudioStateChanged (android16-release) |
| Newer builds may let the audio HAL own the SCO link ("SCO managed by audio", system property read through `BluetoothProperties.isScoManagedByAudioEnabled()`, class `AmScoHelper`: "start/stop doesn't actually start or stop SCO ... it handles priming SCO for non telecom/BT clients"). | [V] code exists / [U] whether enabled on Pixel 10 Pro | BtHelper.java, AudioDeviceBroker.java (android17-release). Check: `adb shell getprop \| grep -i sco` |

### 3.5 Audio source for the recorder

Input device selection, `frameworks/av/services/audiopolicy/enginedefault/src/Engine.cpp`,
`Engine::getDeviceForInputSource()` (android17-release):

```cpp
audio_devices_t commDeviceType =
    getPreferredDeviceTypeForLegacyStrategy(availableOutputDevices, STRATEGY_PHONE);
...
case AUDIO_SOURCE_VOICE_RECOGNITION:
case AUDIO_SOURCE_UNPROCESSED:
    if (audio_is_bluetooth_out_sco_device(commDeviceType)) {
        device = availableDevices.getDevice(
                AUDIO_DEVICE_IN_BLUETOOTH_SCO_HEADSET, String8(""), AUDIO_FORMAT_DEFAULT);
        if (device != nullptr) break;
    }
```

The same test exists for `AUDIO_SOURCE_DEFAULT`, `AUDIO_SOURCE_MIC`, `AUDIO_SOURCE_VOICE_COMMUNICATION` and
`AUDIO_SOURCE_HOTWORD`. [V] So in the AOSP default engine, once the communication device is the SCO device,
every ordinary source records from the SCO microphone, and when it is not, they fall back to wired, USB, then the
built-in microphone, without any error. That silent fallback is exactly the failure the product must never have,
hence the check in step 6 of 3.2. Whether the Pixel uses this engine unchanged is [U].

| Source | Javadoc | Use for us |
|---|---|---|
| `VOICE_COMMUNICATION` (7) | "tuned for voice communications such as VoIP. It will for instance take advantage of echo cancellation or automatic gain control if available." Privacy-sensitive by default (`AudioRecord.Builder.setPrivacySensitive` doc). | **Default for v1.** It is the source Google's sample uses, the only one the default engine also maps to LE Audio inputs, and the only one that counts for mode-owner activity (3.3). [V facts, I choice] |
| `VOICE_RECOGNITION` (6) | "Microphone audio source tuned for voice recognition." | Test variant (case T1b). In the AOSP default engine it follows the SCO device, see the excerpt above. It might give the recogniser a less processed signal. Use it only if the device test shows the SCO route and a better result. [V code, U on the Pixel] |
| `MIC` / `DEFAULT` | generic | no advantage |
| `UNPROCESSED` | raw | same remark as `VOICE_RECOGNITION` |

Conflicting evidence, to be settled on the device: an independent guide measured on a Pixel 10 Pro Fold with
Android 17 states "Record with `MediaRecorder.AudioSource.VOICE_COMMUNICATION`. `MIC` and `VOICE_RECOGNITION`
silently record from the phone", and narrows it elsewhere to "do not open LE Audio inputs". [C]
github.com/jooray/android-bluetooth-microphone, SKILL.md. For LE Audio that matches the AOSP code (the
`VOICE_RECOGNITION` branch has no LE headset case). For Classic SCO the AOSP default engine says otherwise, but a
Pixel may ship a different policy engine or configuration. [U] Until T1b has been run, treat every source except
`VOICE_COMMUNICATION` as "may fall back to the phone microphone without an error".

With a SCO microphone the noise reduction that matters happens inside the hearing aid. [I]

### 3.6 AudioAttributes for output

Output device selection, `Engine::getDevicesForStrategyInt()` and `filterOutputDevicesForStrategy()`
(android17-release) [V]:

```cpp
// remove A2DP and LE Audio devices whenever BT SCO is in use
if (isBtScoActive(availableOutputDevices)) { ... remove A2DP ... remove BLE ... }
...
case STRATEGY_DTMF: case STRATEGY_ACCESSIBILITY: case STRATEGY_SONIFICATION_RESPECTFUL:
case STRATEGY_REROUTING: case STRATEGY_ASSISTANT: case STRATEGY_MEDIA: {
    ...
    if (isBtScoActive(availableOutputDevices)) {
        devices2 = availableOutputDevices.getFirstDevicesFromTypes(
                { AUDIO_DEVICE_OUT_BLUETOOTH_SCO_CARKIT, AUDIO_DEVICE_OUT_BLUETOOTH_SCO_HEADSET,
                  AUDIO_DEVICE_OUT_BLUETOOTH_SCO});
    }
```

`isBtScoActive()` is true when "a SCO device is connected" and "the preferred device for PHONE strategy is BT SCO:
this is controlled only by java AudioService and is only true if the SCO audio link as been confirmed active by BT."

| Usage | While the SCO session is open | While no SCO session is open |
|---|---|---|
| `USAGE_VOICE_COMMUNICATION` | SCO (preferred device of the communication strategy). Also the only usage that counts for mode-owner activity. **Use this inside a session.** [V] | follows the phone strategy; may go to A2DP or the earpiece. Do not use outside a session. [I] |
| `USAGE_ASSISTANT` ("audio responses to user queries, audio instructions or help utterances") | SCO as well, because media and assistant strategies follow SCO while it is active. [V] | A2DP to the hearing aid. Right choice for an output-only prompt without a session. [I] |
| `USAGE_MEDIA` | SCO too (this is why other apps' music lands on the call link) [V] | A2DP |
| `USAGE_ASSISTANCE_ACCESSIBILITY` | SCO (the audio service sets the same preferred device for the accessibility strategy) [V] `updateCommunicationRoute` | A2DP |

Volume: output on SCO is governed by the call volume stream and is synchronised with the headset's speaker gain
(`HeadsetStateMachine.processVolumeEvent` sets `STREAM_VOICE_CALL` / `STREAM_BLUETOOTH_SCO`). [V] It is separate
from media volume. Set it once during setup; on Android 17 volume changes from the background are themselves
subject to hardening (section 5.2).

New assistant-specific audio API, **not needed for v1 but worth watching** [V]:

- `AudioManager.MODE_ASSISTANT_CONVERSATION` ("Added in version 36.1"): "should only be used by applications that
  introduce an interactive assistant communication which would prioritize the audio management around streams with
  `AudioAttributes#USAGE_ASSISTANT`. To use `MODE_ASSISTANT_CONVERSATION`, the calling app must be allowed to manage
  assistant audio (see `canManageAssistantAudio()`)."
- `AudioManager.STREAM_ASSISTANT` ("Added in API level 37").
- `AudioManager.canManageAssistantAudio()` ("Added in version 37.2"): "To request access, use
  `Settings#ACTION_MANAGE_ASSISTANT_AUDIO`".
  Source for all three: https://developer.android.com/reference/android/media/AudioManager
- In the android17-release source the mode is gated by `android.permission.MANAGE_ASSISTANT_AUDIO`
  (protection level `signature|role`), and `roles.xml` grants that permission to the holder of
  `android.app.role.ASSISTANT`, behind feature flags. `adb shell dumpsys audio` prints
  `android.media.audio.manageAssistantAudioPermission:`.
- What this mode does to Bluetooth routing and to the microphone path is [U]. Whether the Pixel build has the flags
  on, and which minor SDK version (`Build.VERSION.SDK_INT_FULL`) it runs, is [U]. Do not build v1 on it.

### 3.7 SCO set-up latency and codecs

| Statement | Status | Source |
|---|---|---|
| "As the SCO connection establishment can take several seconds, applications should not rely on the connection to be available when the method returns". | [V] | javadoc of `startBluetoothSco` |
| Timeouts in the stack: codec negotiation `BTA_AG_CODEC_NEGOTIATION_TIMEOUT_MS (3 * 1000)`; audio connecting state `sConnectTimeoutMs = 30000`; audio service `SET_COMMUNICATION_DEVICE_TIMEOUT_MS = 3000`, `BT_HEADSET_CNCT_TIMEOUT_MS = 3000`. | [V] | bta_ag_sco.cc, HeadsetStateMachine.java, AudioDeviceBroker.java |
| Typical real set-up time with a connected headset is roughly 0.5 to 2 seconds. | [I] | experience value, not from a source. **Measure it** (test T1). |
| One published measurement: "SCO routing took about 550 ms" on a Pixel 10 Pro Fold with Android 17 and a Sony WH-1000XM6; LE Audio needed far longer ("1.7 to 3.5 s from `startRecording()` to the first real audio"). The same guide adds a fixed 500 ms settle delay after the callback and treats exact-zero samples as "no real audio yet". | [C] | github.com/jooray/android-bluetooth-microphone (MIT), SKILL.md, "Device-specific notes" |
| Codecs Android can negotiate: CVSD (settings S4, S3, S1), mSBC (T2, then T1), LC3 super-wideband (T2, T1), aptX voice. Fallback order on failure: LC3 T2 -> LC3 T1 -> CVSD; mSBC T2 -> mSBC T1 -> CVSD. | [V] | bta_ag_sco.cc |
| "Android statically compiles WBS support": `get_wbs_supported() { return true; }`. Super-wideband only if `bluetooth.hfp.swb.supported` or `bluetooth.hfp.software_datapath.enabled` is true. | [V] | system/stack/btm/btm_sco_hfp_hal.cc. Check: `adb shell getprop bluetooth.hfp.swb.supported` |
| Audio bandwidth: CVSD is narrowband (8 kHz sampling, telephone band up to about 3.4 kHz); mSBC is wideband (16 kHz sampling, 50 Hz to 7 kHz); LC3-SWB is 32 kHz sampling, up to about 14 kHz. | [I] | HFP specification values, quoted from secondary sources only |
| The legacy javadoc still says SCO input "must be 8kHz". That text predates wideband speech. | [V] text / [I] outdated | javadoc of `startBluetoothSco` |

Meaning for speech recognition [I]:

- Common recognisers work on 16 kHz mono input. An mSBC link delivers exactly that. Request 16 kHz from
  `AudioRecord` and let the platform resample if the link is narrower.
- With CVSD the signal is telephone quality. Expect more errors on consonants and on short commands, which is
  what a menu uses. The recogniser should be configured or chosen for 8 kHz telephony audio in that case.
- Therefore: have the hearing-care professional or myPhonak set Bluetooth phone calls to
  "Enhanced sound quality (adaptive bandwidth)" and verify the negotiated codec on the device (test T3).
- The same link carries our prompts. For a listener with a high-frequency loss the difference between a
  3.4 kHz and a 7 kHz band is audible in consonant clarity. A2DP is better still, see section 4.

---

## 4. Output path for short spoken menu prompts: SCO or A2DP

### 4.1 Comparison

| Aspect | SCO (call path, HFP) | A2DP (media path) |
|---|---|---|
| Direction | two-way: prompt and microphone on one link [V] | one-way. A microphone needs a switch to SCO. [V] |
| Audio quality | mono, 8 or 16 kHz sampling (CVSD / mSBC), 32 kHz only with LC3-SWB on both ends [V codecs, U for Phonak] | stereo media codec, full band. Best speech clarity. [I] |
| How we select it | `setCommunicationDevice(scoDevice)`, then `USAGE_VOICE_COMMUNICATION` [V] | no selection call possible (A2DP is not a valid communication device). Just play with `USAGE_ASSISTANT` or `USAGE_MEDIA` while no SCO is active. [V] |
| Time to first audio from idle | SCO set-up, "can take several seconds" in the worst case [V]; typical value [U]. Paid once per session. | A2DP stream start plus hearing-aid program switch [U]. Paid again after every pause, see next row. |
| Behaviour between prompts | The link stays up as long as our route client is active (6 s rule, section 3.3). Prompts inside a session start without a new set-up. [V mechanism, I result] | The output thread goes to standby after `kDefaultStandbyTimeInNsecs = seconds(3)` without an active track; the code comment says the delay exists "to avoid truncating audio when going to standby". [V] The A2DP stream is then suspended by the Bluetooth audio HAL. [I] A menu with pauses longer than 3 s restarts the stream for every prompt. |
| Clipped first syllable | Possible at session start only. Guard: wait for the device-changed callback, then pre-roll. [I] | Possible at every stream start. [U] |
| Hearing-aid program | The aid sees a phone call (virtual call: dialing, alerting, active). One switch in, one switch out per session. [V Android side, I aid side] | The aid enters its streaming program when it "receive[s] an audio signal" and returns to "the program that was most recently used" afterwards. [V] One switch pair per stream start/stop, so potentially per prompt. |
| Tap gesture during output | A double tap is "end call": it sends hang-up, Android closes the virtual call. Usable as a cancel gesture, but also an accidental-abort risk. [V Android side, I aid side] | A double tap is "pause/resume streaming". [V user guide] It would then arrive as a media key at whichever `MediaSession` Android picks (input path C). [I] |
| Other media apps | While SCO is active, A2DP devices are removed from routing and media/assistant strategies are sent to the SCO device: a running podcast would continue on the call link in call quality. [V] Take `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` so players pause, and release it at session end so they resume. | Our prompt mixes into the same A2DP stream. Use `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` (music ducks) or `..._TRANSIENT` (music pauses). [I] |
| Phone calls | A real call takes the SCO link away. "NOT returned automatically when the call ends." [V] | A real call suspends A2DP. [I] |
| Radio-level privacy | uplink from the aid's microphones is on air for the whole session [I] | no uplink |
| Volume control | call volume, synchronised with the aid's speaker gain [V] | media volume |
| Android 17 background rules | playback hardening applies to every usage alike (section 5.2) [V] | same |

### 4.2 Why SCO for the whole session in v1

[I] The product is a dialogue: activation, a few prompts, button presses, sometimes speech input, a spoken answer.
Mixing the two paths inside one dialogue costs an A2DP suspend, a SCO set-up and two hearing-aid program switches
every time the microphone is needed, and the same again on the way back. A single SCO session has one set-up, one
program switch and a microphone that is ready the moment the user presses "record". The price is call-grade audio
for the prompts and the "this is a phone call" semantics on the aid.

Keep the decision reversible. Put the route behind one interface with two implementations
(`ScoSessionRoute`, `MediaRoute`) and decide per interaction type after the measurements in section 6. A sensible
second step is "output-only announcements (no input expected) over A2DP, dialogues over SCO".

### 4.3 Mitigations

| Mitigation | What to do | Status |
|---|---|---|
| Confirm before speaking | Do not write speech to the track before `OnCommunicationDeviceChangedListener` reports the SCO device (and, as a cross-check, `BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED` reports `STATE_AUDIO_CONNECTED`). | [V] APIs exist / [I] sufficient |
| Pre-roll | After confirmation, write 200 to 500 ms of very low-level noise or silence, then a short earcon, then speech. The earcon is the part that may be clipped. Tune the length with test T2. | [I] |
| Earcons as state signals | Fixed, cached PCM sounds for "listening", "thinking", "done", "error", "cancelled". They also work as a latency probe and survive a partially clipped start better than a word. | [I] |
| Keep the link warm inside a session | Keep one `AudioTrack` in the playing state and feed zeros between prompts. This satisfies the 6 s activity rule and keeps the aid in one program. End it deliberately after an idle timeout (for example 20 to 30 s without input). | [V] rule / [I] design |
| Do not keep anything warm outside a session | A permanently open stream would hold the aid in its streaming or call program all day, drain its battery and keep the uplink on air. | [I] |
| Pre-synthesise | `TextToSpeech.synthesizeToFile()` at set-up time for every fixed menu string; cache as 16 kHz mono PCM. Dynamic answers: synthesise to file or stream from the server, play through the same track. | [V] API / [I] design |
| Own track, not `TextToSpeech.speak()` | Reason in section 3.3 item 2. | [V] |
| Silence the phone's own sounds | Phonak: every system tone triggers the streaming program. Turn off touch, lock and notification sounds for the phone, or accept the switches. | [V] Phonak KB |
| Fast failure | If the route is not confirmed within about 5 s: `clearCommunicationDevice()`, one retry, then give up with a vibration pattern on the phone. There is no audible channel left in that case. | [I] |
| First-sound check | Exact-zero samples mean the link is not delivering audio yet; a quiet room never produces exact zeros. Signal "listening" only after the first non-zero buffer, and do not reopen the recorder while waiting. | [C] same guide, pitfalls 5 and 8 |
| Cue padding | The same guide pads cue files with leading silence because "the first fraction of a second of a cue played as the link switches modes is cut off". It used 2 s for LE Audio. | [C] pitfall 9 |
| Watch the route during recording | `AudioRecord.addOnRoutingChangedListener` and `registerAudioRecordingCallback` report a route change or silencing in mid-recording. | [V] APIs; pattern used in github.com/jooray/third-ear |

### 4.4 Open questions that only the device can answer

- Does the EON play its own "call" or "streaming" beep when SCO opens and closes? [U]
- How much ambient sound does the aid's call program leave audible, and can the fitter tune it? [U]
- Does the aid accept a virtual call when its Tap Control is set to "voice assistant"? What does a tap do then? [U]
- Is the A2DP start on the Pixel fast enough that A2DP prompts are a realistic alternative? [U]

---

## 5. Locked device, screen off, Doze

### 5.1 Routing does not depend on the lock state

Nothing in `AudioDeviceBroker`, `BtHelper`, `Engine.cpp` or `HeadsetService` consults the keyguard or the display
state when selecting devices or opening SCO. [I, from reading those files; absence of a check is not a proof]
The Bluetooth stack even takes a partial wake lock itself while it waits for the assistant after a headset voice
command (`mVoiceRecognitionWakeLock`, `PowerManager.PARTIAL_WAKE_LOCK`, held for `sStartVrTimeoutMs`). [V]

What does depend on the app's state is whether Android lets us **record** and **play** at all.

### 5.2 Android 17 background audio hardening (applies to the Pixel today)

Android 17 (API level 37) was released on 16 June 2026 "and making it available on most supported Pixel devices". [V]
https://developer.android.com/blog/posts/android-17-is-here . A Pixel 10 Pro that takes updates is therefore on
Android 17. [I] Check the real build in Phase 0.

Official rules [V], https://developer.android.com/about/versions/17/changes/bg-audio :

- "All apps running on Android 17 that have these background audio interactions must have a visible activity or
  must be running a foreground service that is not of type `SHORT_SERVICE`. This applies whether or not the app
  targets API level 37."
- "If an app targets Android 17 (API level 37), there is an additional restriction. If the app is running in the
  background, the app must be running a foreground service that has while-in-use (WIU) capabilities."
- Failure mode: "the audio playback and volume change APIs fail silently without throwing an exception or
  providing a failure message. The audio focus API fails with the result code `AUDIOFOCUS_REQUEST_FAILED`."
- Affected: `AudioTrack.write()`, AAudio, OpenSL ES, Media3/ExoPlayer/Oboe; `requestAudioFocus()`; the volume and
  ringer-mode setters.
- "For example, if your app starts a foreground service in response to `BOOT_COMPLETE` and attempts to interact
  with audio, it will be suppressed."
- Who gets WIU: "The primary exceptions that grant WIU are interactions involving explicit user intent for example,
  notification clicks, widget interactions, or media key events from an external device." and "Foreground services
  are granted WIU access if they are started by system-server delegation (for example, from the Telecom jetpack
  library), or by system bindings representing an elevated foreground state to perform dedicated functionality
  (such as for a `VoiceInteractionService`)."
- Debugging: the documentation gives `adb shell cmd audio set-enable-hardening <enable|disable|throw>`; "there will
  be an entry prefixed by `AudioHardening` with your package name. If the message contains `level: full`, your app
  is running a foreground service, but the service does not have while-in-use capability. If the message contains
  `level: partial`, your app is not running a foreground service at all."
- **Discrepancy:** the released source parses a different command name. `AudioManagerShellCommand.java`
  (android17-release) has `case "set-hardening":` with the help text `set-hardening <1|enable|0|disable|throw>` and
  `clear-hardening`; the string `set-enable-hardening` does not occur in that file. [V] On the device try
  `adb shell cmd audio set-hardening throw` first, then the documented spelling.

What the released source adds [V], `frameworks/av/services/audioflinger/Tracks.cpp`, `getHardeningDecision()`,
android17-release:

```cpp
const auto overrided = cb.getHardeningOverride();
if (overrided == ENABLE || overrided == THROW) { return {EnforcementLevel::FULL, NONE}; }
else if (overrided == DISABLE)                 { return {EnforcementLevel::NONE, OVERRIDE}; }
...
if (hardening_strict()) {
    ...
    if (pp.checkPermission(PermissionEnum::BLUETOOTH_CONNECT, uid).value_or(false)) {
        return {EnforcementLevel::PARTIAL, PRIVILEGED_APP};
    }
    if (pp.getHighestTargetSdkForUid(uid).value_or(1000) < 37) {
        return {EnforcementLevel::PARTIAL, TARGET_SDK};
    }
    return {EnforcementLevel::FULL, NONE};
}
```

and the matching branch for audio focus in `HardeningEnforcer.blockFocusMethod()`:
`else if (holdsPermission(Manifest.permission.BLUETOOTH_CONNECT, callingUid)) { yield new int[]{DENIED_IF_PARTIAL, ...PRIVILEGED_APP}; }`.

Reading [I]: in this release an app that holds `BLUETOOTH_CONNECT` is only held to the *partial* level (some
foreground service or visible activity), even when it targets API 37. This exemption is in the code but not in the
documentation. Treat it as a bonus that a quarterly release can remove, not as a foundation.

The two levels map to app-ops [V] (`AppOpsManager.java`, `AppOpsUidStateTrackerImpl.java`, android17-release):
`OP_CONTROL_AUDIO_PARTIAL` ("Similar to OP_CONTROL_AUDIO, but doesn't require capabilities", default
`MODE_FOREGROUND`), `OP_CONTROL_AUDIO` (needs `PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL`), `OP_TAKE_AUDIO_FOCUS`
(default `MODE_FOREGROUND`). A foreground service with while-in-use allowed grants the audio-control capability
regardless of its type (behind the feature flag `roForegroundAudioControl()`); the microphone capability additionally needs type `microphone`
(`OomAdjusterImpl.java`: `capabilityFromFGS |= PROCESS_CAPABILITY_FOREGROUND_AUDIO_CONTROL;` and
`(fgsType & FOREGROUND_SERVICE_TYPE_MICROPHONE) != 0 ? PROCESS_CAPABILITY_FOREGROUND_MICROPHONE : 0`).

Older rule that still applies [V]: "Apps that target Android 15 (API level 35) must be the top app or running a
foreground service in order to request audio focus."
https://developer.android.com/about/versions/15/behavior-changes-15

### 5.3 The microphone in the background

| Statement | Status | Source |
|---|---|---|
| `RECORD_AUDIO` is while-in-use only. "you cannot create a `microphone` foreground service while your app is in the background and you cannot launch a `microphone` foreground service from a `BOOT_COMPLETED` receiver, with a few exceptions". On Android 14+ the attempt throws `SecurityException`. | [V] | https://developer.android.com/develop/background-work/services/fgs/service-types (Microphone) and .../fgs/restrictions-bg-start |
| Exemptions that give a background-started service while-in-use access: started by a system component; widget; notification; `PendingIntent` from a visible app; device owner; "**The service starts by an app which provides the `VoiceInteractionService`**"; `START_ACTIVITIES_FROM_BACKGROUND`. | [V] | .../fgs/restrictions-bg-start#wiu-restrictions-exemptions |
| Mechanism behind the VoiceInteractionService exemption: the system binds the active service with `Context.BIND_AUTO_CREATE \| Context.BIND_FOREGROUND_SERVICE \| Context.BIND_INCLUDE_CAPABILITIES \| Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS`. `BIND_INCLUDE_CAPABILITIES` makes the bound process inherit the binder's capabilities (`capability \|= client.getCurCapability();`), and the while-in-use check accepts `BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS` ("The binding flag BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS is also allowed by the check here."). | [V] | VoiceInteractionManagerServiceImpl.java (both branches); OomAdjusterImpl.java; ActiveServices.java (android17-release) |
| So the process of the *currently selected* default assistant holds microphone and audio-control capability for as long as the system keeps it bound, with the screen off, without any activity. | [I] | follows from the row above; decisive device test T5 |
| Media-button presses put the receiving app on a temporary while-in-use allowlist: "used when MediaSessionService's bluetooth button or play/resume/stop commands are issued. The typical temp allowlist duration is 10 seconds." App-ops in `MODE_FOREGROUND` are allowed for a UID on that list (`isTempAllowlistedForFgsWhileInUse`). | [V] | ActiveServices.java; AppOpsUidStateTrackerImpl.evalModeInternal |
| A BLE scan result delivered by `PendingIntent` is **not** in either exemption list. Battery-optimisation exemption or a Companion Device association allow *starting* a foreground service from the background, but that service does not get while-in-use access. Logcat then shows "Foreground service started from background can not have location/camera/microphone access". | [V] | fgs/restrictions-bg-start (both lists) |
| A recording made without the capability is not an error for the app: it is delivered as silence and `isClientSilenced()` turns true. | [V] | AudioRecordingConfiguration.java; AudioPolicyService.cpp `updateUidStates_l()` ("Never allow capture if app op is denied") |
| A foreground service of type `microphone` started while the activity was visible keeps the capability for as long as it runs. If the system restarts it in the background, the capability is gone. | [I] | from the same rules. This is the fragile alternative to being the default assistant. |
| Field report for that alternative: "After a reboot or an app update the service can run but cannot record until the user opens the app once." | [C] | github.com/jooray/android-bluetooth-microphone, "Background recording" (Pixel 10 Pro Fold, Android 17). That project does not use the assistant role. |
| Concurrent-capture policy: with only our app recording and the app not idle, capture is allowed; a privacy-sensitive capture of another app, a phone call, or the microphone privacy toggle silences us. | [V] | AudioPolicyService.cpp, comment block and `silenceAllRecordings_l()` on sensor privacy |

Conclusion [I]: **input path B (BLE button) can only record from the screen-off state if the app is the default
assistant** (or uses one of the heavier alternatives below). The assistant role is therefore not just the entry
point for path A; it is what makes B and C able to use the microphone. This is the single most important
architecture fact in this report and it is unproven on the device.

Alternatives if the assistant role turns out to be unusable [I unless marked]:

1. A permanent `microphone` foreground service started from the visible activity after every boot. Simple, but one
   background restart silently breaks recording until the app is opened again.
2. A self-managed call through the Telecom library. The documentation names "system-server delegation (for example,
   from the Telecom jetpack library)" as a source of while-in-use access. [V] It would make every interaction a
   "call" for the whole system (call UI elements, car kits, call logs of accessories). Not examined further.
3. Owner-only switches on a sideloaded, single-user phone, for debugging rather than as a design:
   `adb shell cmd audio set-hardening disable` (playback/focus hardening off; see the naming discrepancy in 5.2;
   whether the override survives a reboot is [U]) [V command in source];
   `adb shell appops set <package> RECORD_AUDIO allow` (app-op evaluation returns the mode unchanged unless it is
   `MODE_FOREGROUND`: `if (mode != MODE_FOREGROUND) return mode;`) [V code] / persistence across reboots and
   permission re-syncs [U]; device-owner provisioning (`REASON_DEVICE_DPC` grants while-in-use) [V code] / practical
   on a phone with accounts [U].

### 5.4 Doze

| Statement | Status | Source |
|---|---|---|
| In Doze the system "Suspends network access" and ignores wake locks for ordinary background apps. | [V] | https://developer.android.com/training/monitoring-device-state/doze-standby |
| Partial wake locks keep working in device-idle mode for apps on the device-idle allowlist, on the temporary allowlist, or whose process state is at most `PROCESS_STATE_BOUND_FOREGROUND_SERVICE`. | [V] | PowerManagerService.setWakeLockDisabledStateLocked (android17-release) |
| A process bound by the system as VoiceInteractionService (`BIND_FOREGROUND_SERVICE`) or running its own foreground service is in that range. | [I] | from the two rows above |
| Active playback and recording hold their own wake locks through the audio server. Silence gaps and network waits do not. Hold an explicit `PARTIAL_WAKE_LOCK` with a timeout for the whole session, and declare the `WAKE_LOCK` permission (without it `acquire()` throws and kills the service). | [I] / [C] | not checked in source; the same guide reports "The audio HAL holds its own `AudioIn` wake lock for an active record client" and the missing-permission crash (pitfall 13) |
| Exempt the app from battery optimisation during set-up. "An app that is partially exempt can use the network and hold partial wake locks during Doze and App Standby." | [V] | same Doze page, section on the exemption list |
| First interaction after hours: the Bluetooth ACL link to the aid is in a low-power mode and the app process may have to be woken by the BLE scan. Both add latency before the SCO set-up even starts. | [U] | measure (test T6) |

### 5.5 `BluetoothHeadset.startVoiceRecognition`

| Statement | Status | Source |
|---|---|---|
| Public API. `@RequiresPermission(BLUETOOTH_CONNECT)` only. "This methods sends the voice recognition AT command to the headset and establishes the audio connection." Returns false if the headset "doesn't support voice recognition, or voice recognition is already started, or audio channel is occupied". | [V] | BluetoothHeadset.java (android17-release) |
| `isVoiceRecognitionSupported(device)` is public too. | [V] | same |
| Headset-initiated flow (`AT+BVRA=1`): `HeadsetStateMachine.processVrEvent()` -> `HeadsetService.startVoiceRecognitionByHeadset()` -> `HeadsetSystemInterface.activateVoiceRecognition()`: | [V] | packages/modules/Bluetooth, android/app/src/com/android/bluetooth/hfp/ |

```java
boolean activateVoiceRecognition(BluetoothDevice fromDevice) {
    Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);
    intent.putExtra(BluetoothDevice.EXTRA_DEVICE, fromDevice);
    intent.putExtra(BluetoothProfile.EXTRA_PROFILE, BluetoothProfile.HEADSET);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    ...
    mHeadsetService.startActivity(intent);
```

```java
// HeadsetService
@VisibleForTesting static int sStartVrTimeoutMs = 5000;
mVoiceRecognitionTimeoutEvent = new VoiceRecognitionTimeoutEvent(fromDevice);
mStateMachinesThreadHandler.postDelayed(mVoiceRecognitionTimeoutEvent, sStartVrTimeoutMs);
if (!mSystemInterface.getVoiceRecognitionWakeLock().isHeld()) {
    mSystemInterface.getVoiceRecognitionWakeLock().acquire(sStartVrTimeoutMs);
}
```

The javadoc of `activateVoiceRecognition` states the contract: "caller should wait for
`BluetoothHeadset#startVoiceRecognition(BluetoothDevice)` callback that will then trigger
`HeadsetService#startVoiceRecognition(BluetoothDevice)`". If nobody calls it within 5 s, the timeout sends
`VOICE_RECOGNITION_RESULT` 0 (fail) to the headset. [V]

What this means [I]:

- A hearing-aid "voice assistant" tap arrives in Android as an **activity start with the implicit intent
  `android.intent.action.VOICE_COMMAND`**, not as a callback into a `VoiceInteractionService`. The assistant role
  definition in `roles.xml` contains no preferred activity for that action. [V] Our app needs an exported activity
  with that intent filter, and it must win the resolution against the Google app without a chooser, because a
  chooser cannot be answered on a locked, dark phone. How to make it the fixed default, and whether the activity
  can run with the display off, belongs to the input-path-A investigation. [U]
- Inside that activity (or a service it starts) we must call `startVoiceRecognition(device)` within 5 s. That
  answers the aid with OK and opens SCO in voice-recognition mode. The audio service then sees an externally
  opened SCO link and routes to it (section 3.4). After that, the same `AudioTrack` / `AudioRecord` code as in 3.2
  applies. Calling `setCommunicationDevice()` as well is harmless on API 36: "Confirm SCO Audio connection to
  requesting app as it is already connected externally". [V comment in BtHelper.requestScoState]
- For paths B and C on API 37, `setCommunicationDevice()` always produces a *virtual call*, while path A produces a
  *voice-recognition* session. The aid may behave differently in the two (prompts, tap meaning). Calling
  `startVoiceRecognition()` directly for B and C too would make all three paths look the same to the aid. That is
  allowed by the API, but then our route client bookkeeping (6 s rule) is not involved and SCO stays open until we
  call `stopVoiceRecognition()` or the aid ends it. Compare both in test T4 before choosing. [I]
- The second tap ("stop voice assistant") arrives as `AT+BVRA=0` -> `stopVoiceRecognitionByHeadset()` -> broadcast
  `Intent.ACTION_STOP_VOICE_COMMAND` and SCO teardown. We see it as `ACTION_AUDIO_STATE_CHANGED` ->
  `STATE_AUDIO_DISCONNECTED`. Treat it as "cancel". [V flow, I use]

Existing apps that use exactly this entry point [C]:

- github.com/kerero/airdots-double-tap (MIT, last commit 2023-08): a `Theme.NoDisplay` activity with
  `<action android:name="android.intent.action.VOICE_COMMAND" />`, `android:showOnLockScreen="true"` and
  `launchMode="singleInstance"` catches the earbud "assistant" double tap. Its README describes the resolution
  step: "The device will ask you with which app to complete the action" -> "Select always". Tested by its author
  on Android 8.1 to 12. It does not call `startVoiceRecognition()`, so the earbud's request simply times out.
- github.com/DicioTeam/dicio-android (GPL-3.0): the assistant's main activity lists both
  `android.intent.action.ASSIST` and `android.intent.action.VOICE_COMMAND`.

Both confirm the shape of the trigger. Neither shows it working on a locked, dark Android 17 phone. [U]

---

## 6. Recommendation

### 6.1 Routing design for the first version

Everything here is [I]: a design derived from the verified mechanisms above. The device test in 6.2 decides
whether it stands.

**One-time set-up (screen on, in the app's set-up screen)**

1. Grant `RECORD_AUDIO`, `BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS`. Declare `MODIFY_AUDIO_SETTINGS`, `WAKE_LOCK`,
   `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`.
2. Make the app the default digital assistant. This is what gives the process microphone and audio-control
   capability in the background (5.3).
3. Exempt the app from battery optimisation.
4. Build with `targetSdk 36` for v1. On Android 17 this keeps playback and focus at the documented "any foreground
   service" level. Design as if the strict level applied anyway, so that a later move to 37 is not a rewrite.
5. Hearing aid: paired through myPhonak, "Bluetooth streaming" on, Bluetooth phone calls set to "Enhanced sound
   quality (adaptive bandwidth)", Tap Control assigned. Ask the hearing-care professional to confirm that
   hands-free calls are enabled in Target.
6. Phone: touch, lock and notification sounds off, so that only our app makes the aid switch programs.
7. Store the hearing aid's Bluetooth address. Never route to "whatever SCO device is there" (car kit, earbuds).

**One interaction = one SCO session**

```
IDLE --trigger A/B/C--> OPENING --route confirmed--> READY <--> PROMPTING / LISTENING / THINKING
                           |  timeout 5 s, 1 retry                |  idle 20-30 s, cancel, route lost
                           v                                      v
                        FAILED (vibrate)                       CLOSING --> IDLE
```

| Step | Action | Why |
|---|---|---|
| 1 | Take a `PARTIAL_WAKE_LOCK` with timeout. Start the session foreground service (type `microphone`). Log if the start is refused. | 5.3, 5.4 |
| 2 | `requestAudioFocus(AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)`. Log the result. `AUDIOFOCUS_REQUEST_FAILED` means the lifecycle is wrong; abort and record why. | 4.1, 5.2 |
| 3a | Triggered by the hearing aid (path A, `ACTION_VOICE_COMMAND`): call `BluetoothHeadset.startVoiceRecognition(device)` at once. Budget: 5 s including process start. | 5.5 |
| 3b | Triggered by button or media key (paths B, C): `setCommunicationDevice(scoDevice)`. | 3.2 |
| 4 | Wait for `OnCommunicationDeviceChangedListener` == our SCO device (cross-check `ACTION_AUDIO_STATE_CHANGED`). 5 s, one retry after `clearCommunicationDevice()`. | 3.2, 4.3 |
| 5 | Start the session `AudioTrack` (`USAGE_VOICE_COMMUNICATION`, 16 kHz mono) and keep it playing until the session ends. Pre-roll, then the "ready" earcon. | 3.3, 4.3 |
| 6 | Prompts: cached PCM written to the same track. | 3.3 item 2 |
| 7 | Recording on explicit user action only: `AudioRecord(VOICE_COMMUNICATION, 16 kHz mono)`, `setPreferredDevice(scoInput)`, start, then **verify** `routedDevice.type == TYPE_BLUETOOTH_SCO`, matching address, `isClientSilenced == false`, and keep watching with a routing listener. If not: stop, discard, say "microphone not available". No fallback to the phone microphone, ever. Play the "listening" earcon only after the first non-zero buffer. | 3.5, 4.3, requirement "never the phone mic" |
| 8 | End: "done" earcon, let it drain, release recorder and track, `clearCommunicationDevice()` (or `stopVoiceRecognition()` on path A), abandon focus, stop the foreground service, release the wake lock. | 3.2 |
| any | Communication device changes away from the aid, SCO disconnects (tap on the aid, real phone call, aid out of range): treat as cancel, go to CLOSING. | 3.3, 3.4 |

**Structure**

- `AudioRoute` interface with `ScoSessionRoute` (v1) and `MediaRoute` (A2DP, later). The state machine talks to
  the interface only.
- One event log with monotonic timestamps for every step above, kept in a ring buffer and dumpable over adb. The
  same events are the measurements of the test below.
- All device selection by `AudioDeviceInfo` type plus address, so that another headset can stand in for the
  hearing aid during development.

### 6.2 Minimal on-device test: "AudioRouteProbe"

Goal: prove which microphone was really used, measure the latencies, and find out which lifecycle states allow
recording and playback with the screen off. It uses only profile-level APIs, so **any ordinary HFP headset** can
stand in for the hearing aid. Run it with a headset first, then with the Phonak.

**App shape**

- One activity (permission prompts, a "start service" button, a case picker with a start delay so the phone can be
  locked and pocketed before the case runs).
- One foreground service `ProbeService` (types `microphone|connectedDevice`) that runs the cases. It registers a
  receiver so cases can be started over adb while the screen is off:
  `adb shell am broadcast -a <pkg>.RUN --es case T1 --ei delaySec 15`
- Optional second build flavour that also declares a stub `VoiceInteractionService` (plus the session and
  recognition services the role requires), to test the default-assistant lifecycle (T5d).
- Output: logcat tag `AudioProbe`, one JSON line per event, and WAV files in
  `/sdcard/Android/data/<pkg>/files/` (`adb pull`).

**Core of every case**

```kotlin
suspend fun runScoCase(openBy: OpenBy, source: Int, usage: Int, prerollMs: Int) {
    val t0 = SystemClock.elapsedRealtimeNanos()
    fun ev(name: String, vararg kv: Pair<String, Any?>) = Log.i("AudioProbe",
        JSONObject(mapOf("ev" to name, "ms" to (SystemClock.elapsedRealtimeNanos() - t0) / 1e6,
            "screenOn" to pm.isInteractive, "locked" to km.isKeyguardLocked,
            "idle" to pm.isDeviceIdleMode) + kv).toString())

    ev("inventory",
        "outputs" to am.getDevices(GET_DEVICES_OUTPUTS).map { "${it.type}/${it.address}" },
        "inputs" to am.getDevices(GET_DEVICES_INPUTS).map { "${it.type}/${it.address}/${it.sampleRates.toList()}" },
        "commDevices" to am.availableCommunicationDevices.map { "${it.type}/${it.address}" },
        "scoOffCall" to am.isBluetoothScoAvailableOffCall, "mode" to am.mode)

    val sco = am.availableCommunicationDevices.first { it.type == TYPE_BLUETOOTH_SCO }
    val confirmed = CompletableDeferred<Unit>()
    val l = AudioManager.OnCommunicationDeviceChangedListener { d ->
        ev("commDeviceChanged", "type" to d?.type, "addr" to d?.address)
        if (d?.id == sco.id) confirmed.complete(Unit)
    }
    am.addOnCommunicationDeviceChangedListener(exec, l)
    // also log BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED and AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED

    ev("focus", "result" to am.requestAudioFocus(focusReq))
    when (openBy) {
        OpenBy.COMM_DEVICE -> ev("setCommunicationDevice", "ok" to am.setCommunicationDevice(sco))
        OpenBy.VOICE_RECOGNITION -> ev("startVoiceRecognition", "ok" to headset.startVoiceRecognition(btDevice))
        OpenBy.LEGACY_SCO -> { @Suppress("DEPRECATION") am.startBluetoothSco(); ev("startBluetoothSco") }
    }
    val ok = withTimeoutOrNull(10_000) { confirmed.await() } != null
    ev("routeConfirmed", "ok" to ok, "comm" to am.communicationDevice?.type)

    val track = buildTrack(usage, 16_000); track.play()
    ev("trackStarted", "routed" to track.routedDevice?.type)
    track.write(silence(prerollMs)); track.write(countingPrompt)   // "one two three four five" + 1 kHz beep
    ev("promptWritten", "routed" to track.routedDevice?.type)

    val rec = buildRecord(source, 48_000)        // 48 kHz on purpose: shows the band limit of the link
    am.getDevices(GET_DEVICES_INPUTS).firstOrNull { it.type == TYPE_BLUETOOTH_SCO }?.let { rec.preferredDevice = it }
    rec.startRecording()
    var firstNonZero = false
    val pcm = readFor(rec, seconds = 8) { buf ->
        if (!firstNonZero && buf.any { it != 0.toShort() }) { firstNonZero = true; ev("firstNonZeroSample") }
    }
    ev("recRoute",
        "routedType" to rec.routedDevice?.type, "routedAddr" to rec.routedDevice?.address,
        "configs" to am.activeRecordingConfigurations.map { mapOf(
            "clientSource" to it.clientAudioSource, "devType" to it.audioDevice?.type,
            "devAddr" to it.audioDevice?.address, "silenced" to it.isClientSilenced,
            "deviceRate" to it.format.sampleRate, "clientRate" to it.clientFormat.sampleRate) })
    ev("recStats", "rms" to rms(pcm), "peak" to peak(pcm),
        "energyAbove4k" to bandEnergy(pcm, 4_000, 8_000), "energyAbove8k" to bandEnergy(pcm, 8_000, 16_000),
        "energyAbove16k" to bandEnergy(pcm, 16_000, 24_000))
    writeWav(pcm)
    rec.stop(); rec.release(); track.stop(); track.release()
    if (openBy == OpenBy.VOICE_RECOGNITION) headset.stopVoiceRecognition(btDevice) else am.clearCommunicationDevice()
    am.abandonAudioFocusRequest(focusReq); am.removeOnCommunicationDeviceChangedListener(l)
}
```

**Four independent proofs of the microphone**

| # | Proof | Pass criterion |
|---|---|---|
| P1 | API | `AudioRecord.getRoutedDevice().type == 7` (`TYPE_BLUETOOTH_SCO`) with the headset's address, and the matching `AudioRecordingConfiguration.getAudioDevice()`; `isClientSilenced == false`. Control case T9 must show `15` (`TYPE_BUILTIN_MIC`). |
| P2 | System | `adb shell dumpsys audio` (recording activity, communication route clients, SCO state), `adb shell dumpsys media.audio_policy` (active input and its device), `adb shell cmd audio get-current-input-device`, `adb shell dumpsys bluetooth_manager` (HFP audio state). |
| P3 | Physical | During the 8 s recording the prompt says "scratch the headset now" (seconds 2-4) and "scratch the phone now" (seconds 5-7). Scratch the microphone opening with a fingernail. Only the headset window may show a large peak. Repeat with the phone in another room: speech must still be recorded. |
| P4 | Spectral | Recorded at 48 kHz, a SCO signal has no energy above its codec band: nothing above about 4 kHz means CVSD, nothing above about 8 kHz means mSBC, content up to about 14 kHz means LC3-SWB. The phone's own microphone has energy well above 8 kHz. Cross-check with `deviceRate` (8000 / 16000 / 32000). |

**Cases**

| Case | What | Records |
|---|---|---|
| T0 | Inventory only. Also `adb shell getprop bluetooth.hfp.swb.supported`, `adb shell getprop \| grep -i -e sco -e hfp`, `BluetoothHeadset.isVoiceRecognitionSupported(device)`. | which device types and addresses exist; is `TYPE_BLUETOOTH_SCO` listed while idle |
| T1 | `setCommunicationDevice` + `VOICE_COMMUNICATION` + `USAGE_VOICE_COMMUNICATION`. 10 runs. | ms from request to `commDeviceChanged`, to `STATE_AUDIO_CONNECTED`, to first non-zero sample |
| T1b | Same with `VOICE_RECOGNITION`, `MIC` and `UNPROCESSED`, each with and without `setPreferredDevice`. | which sources really reach the SCO microphone on this build (settles the conflict in 3.5) |
| T2 | Clipping: T1 with pre-roll 0, 200, 500 ms. The listener notes the first number heard. Same for the A2DP variant (no communication device, `USAGE_ASSISTANT`), once from idle and once 5 s after a previous prompt. | minimum pre-roll per path; whether A2DP is usable for prompts |
| T3 | Codec: T1 with the aid set to "fixed" and to "adaptive" bandwidth. | P4 band limit, `deviceRate`, logcat lines of the codec negotiation |
| T4 | Opening method: `COMM_DEVICE` against `VOICE_RECOGNITION` against `LEGACY_SCO`. Listen for beeps and program change in the aid; double-tap during the session; note what the app sees. | behaviour of the aid in virtual-call and in voice-recognition mode; tap as cancel |
| T5a | T1 with the activity visible. | baseline |
| T5b | Service started from the visible activity, then screen off and locked, case started over adb after 1 min. | does a while-in-use service keep working locked |
| T5c | Service **started from the background** by an alarm or a BLE scan `PendingIntent`, app not the assistant. Expected: `SecurityException` for type `microphone`, or `silenced == true` and silent WAV; playback result per Android 17 rules. | confirms the restriction really applies |
| T5d | App set as default assistant (stub flavour), no activity, case started from the background. Expected: recording not silenced. | **the decisive test for input path B** |
| T5e | T5b and T5d with `adb shell cmd audio set-hardening throw` (released source) or `set-enable-hardening throw` (documentation). Then `adb shell dumpsys audio \| grep -i AudioHardening`. | whether playback and focus would be blocked at the strict level |
| T6 | T5b/T5d after 5 min, 30 min, 2 h of screen-off; once more after `adb shell dumpsys battery unplug; adb shell dumpsys deviceidle force-idle`. | first-interaction latency, Doze effects |
| T7 | Six-second rule: open the route, play nothing for 15 s, log `commDeviceChanged`. Repeat with a silent keep-alive track. | when the route is dropped; whether silence keeps it |
| T8 | `TextToSpeech.speak()` with `USAGE_VOICE_COMMUNICATION` against our own track. `adb shell dumpsys audio` shows which UID owns the player. | confirms 3.3 item 2 on the device |
| T9 | Control: no communication device, same recorder. | routed type must be 15; P4 must show full band |
| T10 | Robustness: `adb shell cmd audio hfp-audio-disconnect <REASON>` (android17 shell command) or switching the headset off in mid-session; an incoming phone call in mid-session. | the events the app receives |

**Log capture**

```
adb logcat -v threadtime AudioProbe:V AS.AudioService:V AS.AudioDeviceBroker:V AS.BtHelper:V \
    AS.HardeningEnforcer:V AF::AfPlaybackCommon:V HeadsetService:V HeadsetStateMachine:V \
    HeadsetSystemInterface:V ActivityManager:W *:S
```

The tags are the `TAG` constants of the classes quoted in this report. [V] More detail on route decisions:
`AudioService.DEBUG_COMM_RTE = Log.isLoggable(TAG + ".Comm", Log.DEBUG)` and `DEBUG_MODE` (`TAG + ".Mode"`). [V]
They are evaluated once at start-up, so `adb shell setprop persist.log.tag.AS.AudioService.Comm DEBUG` followed by a
reboot should switch them on. [I]

Pass criteria for the architecture:

1. T1: P1 to P4 all agree on the headset microphone. Median time to confirmed route recorded.
2. T5d: recording not silenced with the screen off and locked. If this fails, input path B cannot record and
   section 5.3 "alternatives" becomes the next topic.
3. T7: the keep-alive track holds the route.
4. T3: wideband confirmed with the Phonak, or the narrowband consequence accepted.

Note on the probe: with `OpenBy.VOICE_RECOGNITION` no route client of ours exists, so the communication-device
listener may or may not report the SCO device. [U] The probe must accept either that callback or
`BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED` = `STATE_AUDIO_CONNECTED` as confirmation and log which one came.

---

## 7. Corrections to the inherited planning notes

File: `<planning-notes>`

| Note says | Finding | Status |
|---|---|---|
| Phone "Pixel 11 Pro / Android 17" (section 1), elsewhere the project assumes API 36. | Android 17 is API level 37 and has been out for Pixels since 16 June 2026. A Pixel 10 Pro is very likely on it. Plan for the Android 17 rules, whatever the app targets. | [V] release / [I] this phone |
| Hearing aid "CROS EON". | Product name is "CROS EON R". The EON R and EON Sphere names are right. | [V] |
| Path A: "Phonak-Geste löst systemweiten Android-Sprachassistenten aus (`VoiceInteractionService` und zugehörige Session)". | A headset voice command over HFP is delivered by the Bluetooth stack as an activity start with `Intent.ACTION_VOICE_COMMAND`, and the receiving app must call `BluetoothHeadset.startVoiceRecognition()` within 5 s. `VoiceInteractionService` is not in that code path. The assistant role is still needed, for a different reason (background microphone capability). That the Phonak tap uses this HFP mechanism is itself an assumption. | [V] Android side / [I] Phonak side |
| "Bluetooth Classic / Freisprechen (typisch HFP) ... Direkter Android-Audioeingang des konkreten Hörgeräts muss geprüft werden." | Confirmed as far as documents go: EON lists HFP, and the guide says the voice "is picked up by the hearing aid's microphones and transmitted back to the phone". Device test still required. | [V] documents |
| "Auracast ... belegt keine Nachrüstung von bidirektionalem LE-Audio-Unicast via Firmware." | Consistent. EON lists only PBP (broadcast receive). Phonak states it stays on Classic "today". | [V] |
| "Phonak-Mikrofon ist nur während der Aufnahme aktiv". | True at app level (we only capture on demand). Not true at radio level if SCO is also used for prompts: the uplink runs for the whole SCO session. Decide whether that distinction matters for the privacy requirement. | [I] |
| "Android-Audio-Routing/Communication-Device-APIs und Headset-Profile existieren." | Correct, with two additions the notes do not have: the six-second activity rule (3.3), and Android 17 background audio hardening (5.2). | [V] |
| "eine dauerhafte Android-Foreground-Service-Benachrichtigung ist damit vereinbar". | A foreground service alone is not enough for the microphone when it is started from the background (BLE trigger). It needs while-in-use access, which the default-assistant role provides. | [V] rule / [I] consequence |
| Test plan Phase 2: "Anruf/Freisprechen als Basis prüfen". | Still a good first check. Add: fitter must not have disabled hands-free calls; set call bandwidth to "adaptive"; note the negotiated codec. | [V] settings exist |
| phonak-ha: "nur Audéo i90 Sphere und Samsung S26 getestet". | Matches the repository's own protocol notes (Sphere i90, Samsung S26). The repository is MIT-licensed and was pushed on 2026-11-23. Its notes mention a readable "active program id", which could time the aid's program switches. | [C] |

Not checked here (outside this task): the statements about Starkey, ReSound, the Shelly and Flic remotes, GPTWake,
BlindPilot and the Hermes clients.

---

## 8. What is still unknown, in order of risk

| # | Unknown | Why it matters | Test |
|---|---|---|---|
| 1 | Does a process that is the default assistant really record un-silenced with the screen off and no activity, on this Pixel build? | Without it, the BLE button cannot start a voice input. | T5d |
| 2 | Android 17 hardening in practice for our playback and focus (documented rule, plus the undocumented `BLUETOOTH_CONNECT` branch). | Silent failure of every prompt. | T5e |
| 3 | Time to first audible prompt after hours of idle (process wake, ACL wake, SCO set-up, aid program switch). | The whole interaction feel. | T1, T6 |
| 4 | How the EON reacts to an Android virtual call against a voice-recognition session: beeps, ring, ambient attenuation, tap meaning. | Decides `setCommunicationDevice` or `startVoiceRecognition` for paths B and C. | T4 |
| 5 | Negotiated HFP codec with the EON (CVSD, mSBC, LC3-SWB). | Recognition accuracy and prompt clarity. | T3 |
| 6 | Whether the Phonak tap really sends `AT+BVRA`, and whether our `ACTION_VOICE_COMMAND` activity can be the fixed handler and run with the display off. | Input path A. | separate investigation; logcat `startVoiceRecognitionByHeadset` |
| 7 | Quality of the behind-the-ear microphones for the recogniser in wind and noise. | Usability outdoors. | recordings from T1 in real conditions |
| 8 | A2DP start latency and clipping on this phone and aid. | Whether output-only announcements can use the better-sounding path. | T2 |
| 9 | Whether "SCO managed by audio" is enabled on the Pixel build and changes any of the timings. | Explains differences between builds. | `getprop`, dumpsys audio |
| 10 | Persistence of the owner-only overrides (`cmd audio set-hardening`, `appops set`). | Only for debugging convenience. | reboot test |

---

## 9. Sources

Vendor (Phonak / Sonova)

- Media release EON, 24 Aug 2026: https://www.sonova.com/wp-content/uploads/2026/08/Phonak_Media-Release_EON.pdf
- Product page: https://www.phonak.com/en-us/hearing-devices/hearing-aids/audeo-eon (also de-de: https://www.phonak.com/de-de/hoerloesungen/hoersysteme/audeo-eon)
- User guide Audéo E-R / E-Sphere (029-1462-03, 2026-03): https://www.phonak.com/content/dam/celum/phonak/master-assets/documents/hearing-instruments/eon/audeo-e-r/user-guides/ph-user-guide-audeo-e-92x125-029-1462-03-en-us.pdf
- User guide CROS E-R (029-1461-03): https://www.phonak.com/content/dam/celum/phonak/master-assets/documents/hearing-instruments/eon/cros-e-r/user-guides/ph-user-guide-cros-e-92x125-029-1461-03-en-us.pdf
- myPhonak 7.5 user guide: https://www.phonak.com/content/dam/celum/phonak/master-assets/en/documents/apps/myphonak/ph-user-guide-my-phonak-7.5-v13.00-en.pdf.coredownload.pdf
- Blog "Phonak EON: Connectivity excellence" (17 Sep 2026): https://audiologyblog.phonakpro.com/phonak-eon-connectivity-excellence/
- Blog "What to know about Bluetooth Classic, Bluetooth LE Audio, and Auracast broadcast, and why Phonak uses Bluetooth Classic today": https://audiologyblog.phonakpro.com/what-to-know-about-bluetooth-classic-bluetooth-le-audio-and-auracast-broadcast-and-why-phonak-uses-bluetooth-classic-today/
- Blog "Phonak Infinio Ultra: Connectivity that works, no matter what the device": https://audiologyblog.phonakpro.com/phonak-infinio-ultra-connectivity-that-works-no-matter-what-the-device/
- Knowledge base (base https://www.phonak.com/en-us/support/knowledge-base/articles/): `what-hearing-aid-should-be-selected-from-the-list-on-the-mobile-phone`, `connecting-phonak-hearing-aid-to-an-android-smartphone`, `how-to-manage-bluetooth-pairing-entries-on-your-android-smartphone-after-updating-your-infinio-hearing-aids-to-ultra`, `why-cant-i-take-a-call-by-pressing-the-button-on-my-phonak-hearing-aids`, `myphonak-app-how-to-change-the-bandwidth`, `poor-quality-of-bluetooth-audio`, `myphonak-and-tap-control`, `hearing-aids-switch-program-when-receiving-a-sound-notification`, `hearing-aids-switch-to-streaming-program-when-talkback-or-voiceover-is-activated`, `switch-between-talking-on-the-phone-and-watching-tv`, `what-do-i-need-to-use-auracast-broadcast`, `bluetooth-profiles` (en-uk)
- Expert Q&A about Phonak Bluetooth on AudiologyOnline (14 Jul 2021, secondary source): https://www.audiologyonline.com/ask-the-experts/phonak-troubleshoot-bluetooth-issues-27921
- Bluetooth SIG qualification search API: https://qualificationapi.bluetooth.com/api/Platform/Listings/Details/295886

Android documentation

- https://developer.android.com/blog/posts/android-17-is-here
- https://developer.android.com/about/versions/17/behavior-changes-all and .../behavior-changes-17
- https://developer.android.com/about/versions/17/changes/bg-audio
- https://developer.android.com/about/versions/15/behavior-changes-15 (audio focus)
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-manager
- https://developer.android.com/develop/connectivity/bluetooth/ble-audio/audio-recording
- https://developer.android.com/develop/connectivity/bluetooth/ble-audio/overview
- https://developer.android.com/training/monitoring-device-state/doze-standby

AOSP source (https://android.googlesource.com/<project>/+/refs/heads/<branch>/<path>; branches `android16-release`, `android17-release`)

- platform/frameworks/base: `media/java/android/media/AudioManager.java`, `AudioDeviceInfo.java`, `AudioRecord.java`, `AudioRecordingConfiguration.java`, `AudioAttributes.java`, `MediaRecorder.java`
- platform/frameworks/base: `services/core/java/com/android/server/audio/AudioService.java`, `AudioDeviceBroker.java`, `BtHelper.java`, `RouteSelectionStack.java`, `ActivityDebouncer.java`, `HardeningEnforcer.java`, `AudioManagerShellCommand.java`
- platform/frameworks/base: `services/core/java/com/android/server/am/ActiveServices.java`, `am/psc/OomAdjusterImpl.java`, `appop/AppOpsUidStateTrackerImpl.java`, `power/PowerManagerService.java`, `core/java/android/app/AppOpsManager.java`, `core/res/AndroidManifest.xml`
- platform/frameworks/base: `services/voiceinteraction/java/com/android/server/voiceinteraction/VoiceInteractionManagerServiceImpl.java`
- platform/frameworks/base: `core/java/android/speech/tts/BlockingAudioTrack.java`, `TextToSpeech.java`
- platform/frameworks/av: `services/audiopolicy/enginedefault/src/Engine.cpp`, `services/audiopolicy/service/AudioPolicyService.cpp`, `services/audioflinger/Tracks.cpp`, `services/audioflinger/Threads.cpp`
- platform/packages/modules/Bluetooth: `framework/java/android/bluetooth/BluetoothHeadset.java`, `android/app/src/com/android/bluetooth/hfp/HeadsetService.java`, `HeadsetStateMachine.java`, `HeadsetSystemInterface.java`, `system/bta/ag/bta_ag_sco.cc`, `system/stack/btm/btm_sco_hfp_hal.cc`
- platform/packages/modules/Permission: `PermissionController/res/xml/roles.xml`

Sample code

- https://github.com/android/platform-samples (Apache-2.0), `samples/connectivity/audio/src/main/java/com/example/platform/connectivity/audio/` : `AudioDeviceState.kt`, `AudioLoopSource.kt`, `AudioCommsSample.kt`

Community (hints only)

- https://github.com/jooray/android-bluetooth-microphone (MIT): written guide "Recording from a Bluetooth microphone on Android", measured on a Pixel 10 Pro Fold with Android 17
- https://github.com/jooray/third-ear (GPL-3.0): recorder app; `app/src/main/kotlin/app/thirdear/audio/AndroidSources.kt`, `BluetoothChoice.kt`
- https://github.com/thivyan-studios/hark-app (BSD-3-Clause): phone as remote microphone for hearing aids (output side: ASHA, A2DP, SCO, LE)
- https://github.com/kerero/airdots-double-tap (MIT): `VOICE_COMMAND` activity for an earbud assistant gesture
- https://github.com/DicioTeam/dicio-android (GPL-3.0): open-source assistant handling `ASSIST` and `VOICE_COMMAND`
- https://github.com/ijustlikeit/phonak-ha (MIT), `PROTOCOL.md`
- https://forum.hearingtracker.com/t/an-intermittent-bluetooth-streaming-issue-with-my-phonak-audeo-i90-sphere-hearing-aids/114825
