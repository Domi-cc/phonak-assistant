# Verification report: cluster "phonak-eon"

Date of verification: 2026-11-27. Every source below was fetched by me on that day (curl / WebFetch / gh api).
Local copies: `<research>/verify-eon2/`
(PDFs, extracted text, KB pages under `kb/`). Nothing was run on a device. Nothing here replaces the device test.

Rule applied: "confirmed" only where I opened a primary source (Phonak / Sonova document, Bluetooth SIG listing,
Apple / Google platform documentation, AOSP code) and can quote it. Forum posts and GitHub issues are marked [C] (community).

## Verdict table

| # | Claim | Verdict | One-line reason |
|---|---|---|---|
| 1 | EON family exists, launched Aug 2026, models Audéo EON R, Audéo EON Sphere, CROS EON R | confirmed | Sonova media release of 24 Aug 2026, user guides, Bluetooth SIG listing 295886 |
| 2 | Classic HFP + A2DP, Auracast receive (PBP); no LE Audio unicast, no ASHA | confirmed | Radio table in the user guide; product information maps calls/streaming to Classic and only Auracast to LE Audio. Note: the profile list is not exhaustive (no AVRCP listed although tap can pause/resume) |
| 3 | Hands-free calls use the aid's own microphones | confirmed | User guide chapter 14, verbatim |
| 4 | Tap Control with voice assistant / accept-end call / pause-resume | partly | Exists on ALL EON models and levels. But per ear it is a single choice (pause/resume OR voice assistant OR off), it was "off by default" in Target 11, and for EON the Classic-link side is not documented |
| 5 | Right-only fitting keeps streaming, calls and Tap Control | partly | Streaming and calls with one aid are documented. Tap Control with one aid is not stated anywhere, only implied by per-ear assignment |
| 6 | Voice-assistant tap = HFP voice-recognition request (AT+BVRA), Android as iOS | undetermined | No Phonak statement and no capture. Strong indirect evidence (Apple requires AT+BVRA; Google documents BVRA; community reports). My confidence it is BVRA: about 85-90 % |
| 7 | 8 paired / 2 connected; streaming-start signal; fitter can change call bandwidth / Bluetooth settings | partly | 8/2 confirmed for EON (1 connected with a CROS system). No tone documented for automatic Bluetooth streaming start. Target Bluetooth settings confirmed for Target 11.0; no Target 12 guide is public |

---

## Claim 1. Does "Audéo EON" exist? CONFIRMED

Primary sources:

- Sonova media release, PDF created 2026-08-24, https://www.sonova.com/wp-content/uploads/2026/08/Phonak_Media-Release_EON.pdf
  - "Stäfa, Switzerland – August 24, 2026 – Phonak today launched EON, its next-generation hearing aid platform."
  - "Phonak EON will be available to order by licensed hearing care professionals in the United States as of August 24, 2026, with additional markets, including Germany, France, the United Kingdom, Australia and others, to follow in September."
  - "The EON portfolio – Phonak Audéo EON Sphere (mild to profound hearing loss) – Phonak Audéo EON R (mild to profound hearing loss) – Phonak CROS EON R (unilateral hearing loss) – Two new charging solutions: ChargerGo RIC I E ... Charger RIC E ... – Enhancements to Phonak Target fitting software – Updates to the myPhonak and myPhonak Junior apps"
  - "combines Bluetooth Classic, Roger Direct™ and Auracast™ broadcast audio into one hearing aid"
- User guide "Phonak Audéo E", US edition 029-1462-03/V1.00/2026-03 and international edition 029-1462-02/V1.00/2026-05:
  https://www.phonak.com/content/dam/celum/phonak/master-assets/documents/hearing-instruments/eon/audeo-e-r/user-guides/ph-user-guide-audeo-e-92x125-029-1462-03-en-us.pdf
  https://www.phonak.com/content/dam/celum/phonak/master-assets/documents/hearing-instruments/eon/audeo-e-r/user-guides/ph-user-guide-audeo-e-92x125-029-1462-02-en.pdf.coredownload.pdf
  - "This user guide is valid for: Phonak Audéo E90-R, E70-R, E50-R, E30-R, Audéo E-R Trial, Audéo E90-Sphere, E70-Sphere, E50-Sphere, Audéo E-Sphere Trial"
  - A German edition exists: `.../ph-user-guide-audeo-e-92x125-029-1462-01-de.pdf` (linked from the de-de product page, not read).
- User guide "Phonak CROS E", 029-1461-03 (US) and 029-1461-02 (int.): model "Phonak CROS E-R".
- Bluetooth SIG qualification listing 295886 "Louis", Sonova AG, published 2026-08-24, "Phonak Hearing aid with Bluetooth 5.3":
  `https://qualificationapi.bluetooth.com/api/Platform/Listings/Details/295886` returns models E30-R, E50-R, E70-R, E90-R, E-R Trial, E-R Demo, E50/E70/E90-Sphere, E-Sphere Trial, E-Sphere Demo, CROS E-R, CROS E-R Demo. The ICS (profile detail) endpoints answer 401; not readable without a SIG login.

Details that matter:
- Marketing name "Audéo EON R / EON Sphere", regulatory name "Audéo E-R / E-Sphere". Levels: E-R 90/70/50/30, E-Sphere 90/70/50 (no E30 Sphere).
- "Trial" variants exist (performance level switchable by the fitter). A trial fitting may be done with these. See surprise S10.
- Fitting needs "Phonak Target 12.0 or higher" (product information 027-0742-02/V2.00/2026-07).

## Claim 2. Wireless profiles. CONFIRMED (with one caveat)

- User guide 029-1462-03 page 55 and 029-1462-02, "Radio information of your wireless hearing aid", verbatim:
  ```
  Modulation             GFSK, Pi/4-DQPSK, 8-DPSK, GMSK
  Radiated power         < 2.5 mW
  Bluetooth®
  Core version           Bluetooth® Core 5.3, Dual-Mode
  Profiles supported     HFP (Hands-free profile), A2DP,
                         PBP (Public Broadcast Profile)
  ```
- Product information "Phonak Audéo E", 027-0742-02/V2.00/2026-07,
  https://www.phonak.com/content/dam/celum/phonak/master-assets/documents/hearing-instruments/eon/audeo-e-r/product-information/ph-product-information-audeo-e-210x280-027-0742-02-en.pdf
  (not used by the first pass), section "Phonak Audéo E wireless technology":
  ```
  Direct calls / audio streaming           Bluetooth® Classic Audio
  Auracast™ broadcast                      Bluetooth® LE Audio
  ```
- KB "Does my phone need a Bluetooth Hearing Aid Profile (HAP)...": "No. Phonak hearing aids do not need the Hearing Aid Profile (HAP). Phonak hearing aids supports the widely availalbe standard Bluetooth Hands Free Profile (HFP) for phone calls."
  https://www.phonak.com/en-us/support/knowledge-base/articles/bluetooth-hearing-aid-profile-hap-and-phonak-hearing-aids
- Phonak Audiology Blog, 2026-01-13 (modified 2026-04-28): "At Phonak, we continue to use Bluetooth Classic because it provides the most universal and seamless connectivity experience for hearing aid users today." and "Bluetooth Classic ... is used for everyday tasks such as streaming media, taking phone calls, and using voice assistants."
- KB "What do I need to use Auracast Broadcast?": "you need myPhonak 7.5 or newer, and Phonak Audéo™ E, Audéo™ E-Sphere devices or newer."
- CROS E-R transmitter (guide 029-1461): "Core version Bluetooth® Core 5.3, LE Audio", modulation "GFSK, GMSK" only. That is the CROS device, which has no phone audio of its own.

Attack on the claim: the "Profiles supported" list is evidently NOT exhaustive. It omits AVRCP although Tap Control offers "pause/resume streaming", and it omits the LE GATT services used by myPhonak. So absence from that list alone would be weak proof of "no LE Audio unicast / no ASHA". What carries the negative is the product-information line that assigns calls and audio streaming to "Bluetooth Classic Audio" and limits "Bluetooth LE Audio" to Auracast, plus the HAP article and the blog. No Phonak source mentions ASHA at all. I found nothing that contradicts the claim.

Design consequence: the only microphone path into Android is Classic HFP (SCO/eSCO). Do not build on `TYPE_HEARING_AID`, `TYPE_BLE_HEADSET` or LE Audio APIs. Do not assume AVRCP key events arrive just because the tap can "pause/resume" (test it).

## Claim 3. Own microphones in hands-free calls. CONFIRMED

- User guide 029-1462-03, chapter 14: "Phone calls are “hands free”, this means your voice is picked up by the hearing aid’s microphones and transmitted back to the phone." and 14.1: "Your voice is picked up by the hearing aid microphones and transmitted to the phone."
- KB (Android): "During a phone call, your voice is picked up by the microphones of the Phonak hearing aid." ... "If you are in a very noisy environment- the own voice pickup through the hearing aids may be disturbed too much."
  https://www.phonak.com/en-us/support/knowledge-base/articles/why-cant-the-person-hear-me-when-using-phonak-hearing-aids-with-an-android-phone
- KB Siri: "As long as the hearing aid is connected to the phone, you can use Siri using the voice pick up of the hearing aid."

Two switches can remove this path completely (both verified):
- Fitter: "Disable Hands-free Calls" is listed as a feature on every EON level (product information, footnote "For high security environments, such as SCIF"). KB: "Hands-free Calls are disabled in Target ... Only your hearing care professional can enable it again".
- Wearer: "For Infinio Ultra and newer hearing aids, calls and media streaming can be disabled in the myPhonak app settings" (Devices > "Streaming via Bluetooth" > "Bluetooth streaming").

## Claim 4. Tap Control. PARTLY

What is confirmed for EON:
- User guide chapter 9: "If paired with a Bluetooth® enabled device, multiple functions can be accessed using Tap Control e.g. accept/end phone call, pause/resume streaming, start/stop voice assistant. This is indicated in your individual “Hearing aid instructions”. To use Tap Control, tap the top of your ear twice."
- Product information 027-0742-02, table "Performance levels" (checked on the rendered page, not only the text layer): rows "Tap control – Siri/Google Assistant", "– Pause/resume streaming", "– Accept/end phone call" carry a dot in all seven columns: E-Sphere Premium/Advanced/Standard (E90/E70/E50) and E-R Premium/Advanced/Standard/Essential (E90/E70/E50/E30).
  So Tap Control is on every EON model and level, including E30. (Paradise limited it to levels 90 and 70.)
- Same table: "Motion Sensor Hearing" has dots everywhere except E-R Essential (E30). The tap needs the accelerometer, so the sensor hardware must be in all models; only the hearing feature is missing on E30. [the hardware statement is my inference]
- CROS guide chapter 9: "If they are part of the CROS system, the Tap Control functions are available on the CROS device".

Corrections that matter for building:
1. **One function per ear, not three.** myPhonak user guide 4.0.4 (V1.03/2021-04, Phonak archive), page 17, screenshot "Tap control – Select features for double tap": section "Both hearing aids: Accept/end call or streaming [toggle]", section "Left hearing aid: Pause/resume streaming | Voice assistant | Off" (single choice), section "Right hearing aid: Pause/resume streaming ...".
   Phonak Fast Facts Tap Control (028-2160-02, 2020): "Functions can be deactivated or assigned to the preferred ear". Phonak blog (Paradise): "three pre-defined behaviors, two of which can be assigned to a specific hearing aid".
   This is documented for the Paradise generation. The current myPhonak 7.5 guide (V13.00/2026-06) no longer shows the screen, so for EON this is carried over, not proven. If it holds, a single right aid can have "voice assistant" OR "pause/resume streaming" on the tap, plus accept/end call.
2. **Off by default in Target.** Target 11.0 Step-by-step Guide (029-1387-33, 2025): "With Paradise rechargeable hearing aids and newer, Tap Control can be configured in [Manual controls]. Tap Control can be used to accept/end phone call, pause/resume streaming, and start/stop the smartphone’s voice assistant. Tap control is off by default for all clients. If you would like it to be on for all adult clients automatically, go to [Setup] > [Fitting session] > [Fitting] > [Enable “Tap control” per default]." (The 2020 Fast Facts said "On by default"; this changed.) "The client has the option to adjust the Tap Control configuration in the myPhonak app."
3. **Context dependent.** Same guide: "For an overview of the multifunction button and Tap Control behaviors in different listening situations, click on [Standard], [Streaming] or [Phone call]." The tap therefore does different things when idle, while streaming and during a call. The exact matrix is not published.
4. Sensitivity: "The default setting for sensitivity is “Normal”. The setting “Firm” means a strong double tap ... “Gentle” means a light double tap". KB: accidental taps "can trigger functions like hanging up calls, pausing streaming, or activating Voice assistant".

Which aid holds the Classic link:
- Up to Infinio without Ultra: one aid, selectable by the fitter. Target 11.0 guide: "Configuring the Bluetooth name, side and managing pairings can be found by clicking on [Bluetooth]." KB: "Only one hearing aid is set to connect via Bluetooth classic, known as the phone ear hearing aid." and "it can be set either as right (R) or left (L) by your hearing care professional."
- Infinio Ultra and newer (EON): KB "What hearing aid should be selected...": with two aids "you will see two hearing aid entries ... R-Phonak hearing aid ... L-Phonak hearing aid"; "If you wear one hearing aid, then you see only one of them in your Bluetooth menu. Either the L (left) or R (right)." EON blog (2026-09-17): "left-right diversity is designed to use the hearing aid with the stronger connection to support wireless stability." Whether Target 12 still offers a fixed "side" for EON is NOT documented (no Target 12 guide is published; the Target page lists guides up to 11.2 only). Phonak contradicts itself on the entry count (Ultra blog: "left and right hearing aids appear as a single entry").
- For a right-only fitting the question is moot: the one aid holds the link (see claim 5).

## Claim 5. Single right aid. PARTLY

Confirmed:
- KB "What hearing aid should be selected from the list on the mobile phone?": "Phonak hearing aids use ... Bluetooth Classic Audio for hands-free calls and streaming audio, and Bluetooth Low Energy for connecting to myPhonak. Depending on the model of your hearing aids and whether you are using one or two devices ..." and, for Infinio Ultra and newer, "If you wear one hearing aid, then you see only one of them in your Bluetooth menu. Either the L (left) or R (right)."
- The EON guide speaks in the singular: "Your hearing aid can be connected to up to two devices and paired to up to eight devices."
- Phonak documents one-aid use elsewhere too (RemoteControl KB: "if I only use a hearing aid in one ear ... The pairing process is identical").

Not confirmed: no Phonak text says that Tap Control works in a monaural fitting. It follows from the per-ear assignment (claim 4), nothing more. No statement on whether "Motion Sensor Hearing" or call microphone processing differ in a monaural fitting.

If the "single fitting" is really a CROS system (CROS E-R on the other ear), three things change, all verified in the CROS guide 029-1461: "CROS E is only compatible with Audéo E-R hearing aids" (not Sphere); "The CROS system can be connected to one device at a time and paired to up to eight devices" (no two-device multipoint); "During streaming of phone calls or music to the hearing aid, the signal from the CROS device is no longer transmitted".

## Claim 6. Tap = HFP voice recognition request. UNDETERMINED (confidence about 85-90 % that it is true)

No Phonak document names the mechanism, and I found no HCI capture of any Phonak aid. The SIG listing's ICS is behind a login.

Evidence for:
- Apple, Accessory Design Guidelines for Apple Devices R7 (2018-11-07), 15.3.1 (copy at https://www.bluetooth.com/wp-content/uploads/attachments/BluetoothDesignGuidelines.pdf): "To initiate a Siri session, the accessory must use the voice recognition command AT+BVRA defined in the Bluetooth Hands-Free Profile specification." A plain Classic HFP accessory has no other documented way to start Siri, and Phonak lists "Tap control – Siri/Google Assistant".
- Google, "Voice optimization" for Bluetooth accessories (https://developers.google.com/assistant/accessories/integrate/bluetooth/voice-optimization): "Google Assistant, via the Google Assistant app running on a paired mobile device, uses BVRA to initiate a Google Voice Assistant (GVA) session."
- Phonak's wording "start/stop voice assistant" mirrors AT+BVRA=1 / AT+BVRA=0. Phonak blog: Classic "is used for ... using voice assistants".
- AOSP android17-release, `packages/modules/Bluetooth/android/app/src/com/android/bluetooth/hfp/HeadsetSystemInterface.java` line 349 ff. (local checkout at commit c77db469, branch android17-release): the headset-initiated path does `new Intent(Intent.ACTION_VOICE_COMMAND)` ... `mHeadsetService.startActivity(intent)`.
- [C] Samsung users with Phonak Paradise: the tap "tries to launch the Bixby, even if it is uninstalled and Google Assistant is set as the default Assistant app"; fixed by disabling "Bixby Voice" and "Bixby Dictation" (forum.hearingtracker.com/t/phonak-paradise-tap-control-and-google-assistant/58895). That is the signature of an `ACTION_VOICE_COMMAND` activity resolution, which ignores the assistant role.
- [C] home-assistant/android issue 5045 (2025-02): Phonak "Audeo I70 Sphere", Android 15, "tap-control to enable the assist function (Double-Tap my ear/HearingAid)" starts the Home Assistant app. That app handles Bluetooth requests through an activity alias with `android.intent.action.VOICE_COMMAND` (manifest; issues 4126 "Assist support AT+BVRA command?" and 4457).

Evidence against: none found. Alternatives (HSP button `AT+CKPD`, an AVRCP key) would not open Siri and would redial or toggle media on Android.

Device test that settles it: `adb logcat | grep -i -e startVoiceRecognitionByHeadset -e VOICE_COMMAND` while tapping, or a Bluetooth HCI snoop log showing `AT+BVRA=1`.

## Claim 7. Pairing count, streaming-start signal, fitter settings. PARTLY

- Pairing: EON guide 13.2: "Your hearing aid can be connected to up to two devices and paired to up to eight devices." KB: "With Phonak EON, Infinio, Lumity, and Paradise hearing aids, you can pair up to eight Bluetooth® devices (such as smartphones), with up to two devices connected at the same time." CROS system: one at a time (see claim 5). KB: "A pair of Phonak hearing aids is configured as one system. Therefore the second hearing aid cannot be separately paired to another Bluetooth device."
- Streaming start, what is documented:
  - "When the hearing aids receive an audio signal from the smartphone a streaming program will be activated automatically. When the streaming is finished, the program that was most recently used will be activated." (KB hearing-aids-switch-program-when-receiving-a-sound-notification)
  - "they switch to the Bluetooth streaming program whenever there is sound played on your phone" including ringing, media, notification and system sounds (KB why-does-the-bluetooth-streaming-program-appear...).
  - Priority (KB myphonak-app-why-do-hearing-aids-unmute-automatically): "Bluetooth phone calls (HFP) have always the highest priority and a calling notification will be heard." "Hearing aid microphones will stay on by default while receiving streaming signals. The balance between hearing aid microphones and streaming signals is customizable."
  - Phonak's official tone library (YouTube channel "Phonak", published 2026-06-25): "Phonak Hearing Aid Tone – Streaming Activation: This tone is played when the streaming program is activated manually using the hearing aid controls or the myPhonak app." So the documented tone is for MANUAL activation. No tone is documented for automatic Bluetooth streaming or for an HFP audio link without a ring. "Ring Tone 1/2" are described as "used by certain older Phonak hearing aid models".
  - "Ringtones for an incoming call with Phonak hearing aids cannot be switched off."
  - Not published anywhere: delay from stream start to audible sound, clipping of the first syllable, any tone at SCO open/close.
- Fitter settings (Target 11.0 Step-by-step Guide, section Device options): "Configuring the Bluetooth name, side and managing pairings can be found by clicking on [Bluetooth]." "Selection of the adaptive versus fixed bandwidth applies to the quality of phone calls. It is recommended to choose adaptive bandwidth for optimal sound quality. Adaptive bandwidth offers a higher sampling rate ... Select fixed bandwidth if the client reports issues where the far end speaker cannot hear the hearing aid wearer. The client has the option to adjust the Bluetooth bandwidth setting in the myPhonak app." "the ‘hands-free calls’ protocol can be disabled".
  Current KB (poor-quality-of-bluetooth-audio): "Change the Bluetooth connection for phone calls from adaptive to fixed bandwidth. This can be done in the myPhonak app settings or by the hearing care professional in the fitting software."
  Gap: all of this is written for Target 11.x. For EON (Target 12.0) there is no public guide. Phonak names no codec; "adaptive = wideband mSBC" stays an inference, LC3-SWB is unknown.

---

## Surprises (not covered by the claims)

S1. **A headset voice request is routed by "preferred activity", not by the assistant role, and on a Pixel with Android 17 Google's handler wins by default.** [C] home-assistant/android issue 7834 (2026-11-08, Pixel, Android 17 build CP3A.261005.005): logcat `START u0 {act=android.intent.action.VOICE_COMMAND flg=0x10000000 cmp=com.google.android.googlequicksearchbox/com.google.android.voicesearch.handsfree.HandsFreeActivity} ... from uid 1002 (com.google.android.bluetooth)`, and Google then opened the "Default digital assistant app" settings because another app held the role. `dumpsys package` showed a preferred-activity entry for VOICE_COMMAND pointing at Google. After "Clear default preferences" for the Google app the system resolver appeared and the third-party handler worked. `pm disable-user` on Google's activity was refused ("Shell cannot change component state"). This matches the AOSP code (implicit `startActivity`). Our app needs its own exported activity for `android.intent.action.VOICE_COMMAND` + `CATEGORY_DEFAULT`, and the owner must make it the "Always" choice once, with the screen on, before the locked use case can work. Holding the assistant role is not enough for this path.
S2. AOSP android17-release `HeadsetService.startVoiceRecognitionByHeadset`: returns false when `!isAudioModeIdle()` ("audio mode not idle"), and the app has `sStartVrTimeoutMs = 5000` to answer. A voice-assistant tap during an already open call-type audio session does not start anything.
S3. Tap Control may be switched off after the fitting (Target 11: "off by default for all clients").
S4. One tap function per ear (claim 4). With one aid, "voice assistant" and "pause/resume streaming" compete for the same gesture.
S5. Two separate kill switches for the HFP path: "Disable Hands-free Calls" (fitter) and "Bluetooth streaming" toggle (myPhonak). The app should detect "no HFP device" and say why this can happen.
S6. Any phone sound moves the aid into the streaming program; HFP has top priority; ringtones cannot be disabled in the aid.
S7. Reconnection is not promised: "The connection will be maintained as long as the device remains ON and within range." "After the device goes out of range, you can trigger an automatic reconnection by pressing the multi-function button for less than 2 seconds." With two or more paired devices nearby the aid connects to those ("they will try to connect to them instead of pairing with a new one").
S8. Phonak's own sources disagree on how many Bluetooth entries a pair of Ultra/EON aids creates (one vs two). Identify the HFP device by connected profile, never by name or count. The fitter can rename the device ("your hearing care professional may have customized the names").
S9. Target 12.0 "Fitting Authorization: An additional security layer has been introduced with EON hearing aids." The reverse-engineered LE control protocol that the first report proposed as a timing probe was written against an Infinio i90 Sphere; it may not work on EON. [inference]
S10. Trial units: "A tone is played in the hearing aids 1x/hour for 6 days after the expiration time. Then, the tone will play 1x/minute." Finish audio tests before the trial expires.
S11. CROS variant: one connected device only, E-R only, CROS signal muted during streaming and calls.
S12. A new SIG listing "ERA 1.0 – Dual-mode Bluetooth® controller" (Sonova AG, listing 353929) was published on 2026-11-05. No functional conclusion can be drawn from it.

## Still needs a device test

1. Does the tap send `AT+BVRA=1` (logcat `startVoiceRecognitionByHeadset`, HCI snoop)? Does a second tap send `AT+BVRA=0`?
2. Which activity receives `ACTION_VOICE_COMMAND` on this Pixel; can our activity be made the fixed handler; does it start with the display off and the phone locked?
3. HFP codec actually negotiated with "adaptive bandwidth" (CVSD / mSBC / LC3-SWB).
4. What a tap does while our app holds SCO open (virtual call vs voice-recognition session): hang-up, BVRA=0, nothing?
5. Does "pause/resume streaming" arrive as an AVRCP media key in our MediaSession, and only while audio is streaming?
6. Tones and delay at SCO open/close and at A2DP start; first-syllable clipping; time to fall back to the hearing program.
7. Reconnect behaviour after out-of-range and after the charger; behaviour with a second connected device.
8. How the single aid enumerates (one dual-mode entry? name?) and whether `getAvailableCommunicationDevices()` lists it as `TYPE_BLUETOOTH_SCO` while idle.

## Questions for the audiologist at the trial fitting

Device
1. Exact model and level (Audéo E-R or E-Sphere; E30/E50/E70/E90)? Is it a "Trial" unit, and when does the trial period expire (expiry beeps)? Firmware version?
2. Is a CROS E-R part of the fitting? (Then only one phone/PC can be connected at a time, and CROS works only with E-R.)

Tap Control (Target > Device options > Manual controls)
3. Please enable Tap Control (it was off by default in Target 11). Is it available at my performance level in Target 12?
4. On my one aid, can "voice assistant" and "pause/resume streaming" both be active, or only one of them? Please set "voice assistant" on the right aid and leave "accept/end call & streaming" on.
5. Please show me the [Standard] / [Streaming] / [Phone call] overview: what does a double tap do when nothing is playing, while audio is streaming, and during a call?
6. Please run "Training & sensitivity" with me and set the sensitivity (Gentle / Normal / Firm). Can I change tap settings myself in myPhonak?
7. Can the multi-function button (short / long press) be given "voice assistant" or "pause/resume streaming" as an alternative trigger? What is the long press set to now (for example the mute function)?
8. Please print the "Hearing aid instructions" report.

Bluetooth (Target > Device options > Bluetooth)
9. Is "Disable hands-free calls (SCIF)" off? Is "Bluetooth streaming" enabled?
10. Phone-call bandwidth: please set "adaptive bandwidth". Does this setting exist for EON in Target 12? Does Phonak say which codec it is (wideband mSBC, LC3 super-wideband)?
11. Does Target 12 still have a "Bluetooth side" setting for EON, or is that gone since Infinio Ultra? What is the Bluetooth name of my aid, and can it stay at the default?
12. Which paired devices are stored in the aid (up to 8), and can old ones be deleted? With two connected devices, which one gets the voice-assistant tap and which one wins for audio?

Sound during streaming
13. In the programs "Phone call + mic" and "Media speech + mic": how much ambient sound is mixed in, and can the two be set differently? Which one is used when an app opens a call-type link without a real phone call?
14. Does the aid play any tone when Bluetooth audio or a hands-free link starts or ends? Can those signals be changed under "Signals & alerts"?
15. How long does the switch into and out of the streaming program take; is clipping of the first syllable a known effect? (Possibly a question for Phonak support.)

Questions to pass on to Phonak support
16. On Android, does "start/stop voice assistant" send the standard hands-free voice-recognition command (AT+BVRA), and does the aid play a tone or time out if the phone does not open audio?
17. After being out of range, does the aid reconnect to the phone by itself, or only after a button press?
18. Is LE Audio unicast (two-way) planned for EON by firmware? (Do not build on it either way.)
19. Is the Pixel 10 Pro with Android 17 listed in the Phonak compatibility checker for calls, streaming and myPhonak 7.5?

## Sources opened (all 2026-11-27)

Phonak / Sonova
- https://www.sonova.com/wp-content/uploads/2026/08/Phonak_Media-Release_EON.pdf
- User guides Audéo E (029-1462-03 en-us, 029-1462-02 en), CROS E (029-1461-03, 029-1461-02): URLs above and https://www.phonak.com/en-us/support/user-guides/eon , https://www.phonak.com/en-uk/support/user-guides/eon
- Product information 027-0742-02 and datasheet (acoustic data only): https://www.phonak.com/content/dam/celum/phonak/master-assets/documents/hearing-instruments/eon/audeo-e-r/datasheets/ph-datasheet-audeo-e-r-210x297-en.pdf
- https://www.phonak.com/en-us/hearing-devices/hearing-aids/audeo-eon/for-professionals , .../audeo-eon-sphere/features
- Phonak Target page (Target 12.0 notes): https://www.phonak.com/en-us/professionals/innovations/target
- Target 11.0 Step-by-step Guide: https://www.phonak.com/content/dam/celum/phonak/master-assets/en/documents/resources/phonak-target/target-11/ph-desktop-fitting-guide-target-11.0-step-by-step-210x297-029-1387-33-en-gb.pdf
- Target 11.2 User Guide: https://www.phonak.com/content/dam/celum/phonak/master-assets/en/documents/resources/phonak-target/target-11/ph-user-guide-target-11.2-92x125-en.pdf
- myPhonak guides 7.5, 7.2, 7.1, 7.0, 6.8, 6.5, 6.1, 4.0.4 (links on https://www.phonak.com/en-us/hearing-devices/apps/myphonak)
- Fast Facts Tap Control 028-2160-02 (phonakpro.com was down with HTTP 503; read from web.archive.org snapshot 2025-03-28)
- Connectivity instructions and tips (V1.00/2025-07): https://www.phonak.com/content/dam/phonak/en/documents/apps/PH_Guide_ConnectivityInstructionsAndTips_210x297_EN.pdf.coredownload.pdf
- Knowledge base, base https://www.phonak.com/en-us/support/knowledge-base/articles/ : myphonak-and-tap-control, why-do-my-phonak-hearing-aids-hang-up-calls-pause-streaming-or-activate-voice-assistant-unexpectedly (en-au), why-is-the-tap-control-sensitivity-not-visible, what-hearing-aid-should-be-selected-from-the-list-on-the-mobile-phone, three-hearing-aids-in-the-bluetooth-phone-list (en-au), why-are-my-phonak-hearing-aids-not-showing-in-the-bluetooth-list-of-my-phone, why-cant-i-take-a-call-by-pressing-the-button-on-my-phonak-hearing-aids, why-can-t-i-take-phones-calls-directly-on-my-virto-r-infinio-hearing-aids, how-can-i-disable-streaming-on-virto-r-infinio-hearing-aids, myphonak-app-how-to-change-the-bandwidth, poor-quality-of-bluetooth-audio, how-many-bluetooth-enabled-devices-e-g-smartphones-can-be-connected-to-phonak-hearing-aids, pairing-one-hearing-aid-to-one-phonedevice-and-the-other-hearing-aid-to-another-phonedevice, bluetooth-hearing-aid-profile-hap-and-phonak-hearing-aids, bluetooth-profiles (en-uk), bluetooth-compatibility, siri-and-phonak-hearing-aids, hearing-aids-switch-program-when-receiving-a-sound-notification, why-does-the-bluetooth-streaming-program-appear-in-the-myphonak-app-even-when-i-m-not-actively-streaming, myphonak-app-why-do-hearing-aids-unmute-automatically, deactivating-the-ringtone-in-phonak-hearing-aids, what-do-the-notification-sounds-i-hear-in-my-hearing-aid-mean, hearing-aids-play-a-beep-regularly, what-do-i-need-to-use-auracast-broadcast, connecting-phonak-hearing-aid-to-an-android-smartphone, how-to-manage-bluetooth-pairing-entries-on-your-android-smartphone-after-updating-your-infinio-hearing-aids-to-ultra, why-cant-the-person-hear-me-when-using-phonak-hearing-aids-with-an-android-phone, can-i-use-the-myphonak-app-to-adjust-the-volume-during-phone-calls
- Blogs: https://audiologyblog.phonakpro.com/phonak-eon-connectivity-excellence/ (2026-09-17), .../what-to-know-about-bluetooth-classic-bluetooth-le-audio-and-auracast-broadcast-and-why-phonak-uses-bluetooth-classic-today/ (2026-01-13), .../phonak-infinio-ultra-connectivity-that-works-no-matter-what-the-device/ (2025-11-17), .../novel-way-of-accessing-bluetooth-functionalities/ (Paradise Tap Control)
- Tone library: https://www.youtube.com/playlist?list=PLya8vOHQyOR9B4E6Qi_v8TvIQ1YtmksDQ (video au5eg5pSwT4 "Streaming Activation")

Other primary
- Bluetooth SIG: https://qualificationapi.bluetooth.com/api/Platform/Listings/Details/295886
- Apple Accessory Design Guidelines R7: https://www.bluetooth.com/wp-content/uploads/attachments/BluetoothDesignGuidelines.pdf
- Google: https://developers.google.com/assistant/accessories/integrate/bluetooth/voice-optimization
- AOSP platform/packages/modules/Bluetooth, branch android17-release (local checkout, commit c77db469de80f86660bc053bec4dee0c5d4b947c): `android/app/src/com/android/bluetooth/hfp/HeadsetSystemInterface.java`, `HeadsetService.java`

Community [C]
- https://forum.hearingtracker.com/t/phonak-paradise-tap-control-and-google-assistant/58895
- https://forum.hearingtracker.com/t/-/81786/3
- https://github.com/home-assistant/android/issues/5045 , /issues/7834 , /issues/4126 , /issues/4457
- https://github.com/librepods-org/librepods/issues/868
