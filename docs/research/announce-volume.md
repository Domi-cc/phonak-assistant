# What decides how loud an announcement is in the hearing aid, and what may the app set?

Date: 2026-11-28. Nothing in this report was run on a device, and nobody listened with the aid on the ear. Every statement is a lead until the checks in section 11 have been done.

The owner (one aid, right ear, the phone locked in his pocket) reported: "Terminerinnerung viel zu leise, wir brauchen die Möglichkeit diese Durchsagen von der Lautstärke anzupassen" (the appointment reminder is much too quiet; the loudness of these announcements has to be adjustable). This report asks what sets that loudness on Android 17 with this aid, what an app is allowed to change, and what goes wrong when it does.

How this report was made: three reports written on that day, each then re-checked by a second agent ("the skeptic") whose only job was to refute it against the sources, and after them a design critique that held the change planned at that time against all six results.

- **The volume path:** which volume a sound with the announcer's audio attributes is under, what `AudioManager.setStreamVolume` does and when Android drops it, absolute volume. Android 17 source.
- **Limits and memory:** safe volume and sound dose, where volumes are stored, what "raise, speak, put back" leaves behind, timing, how an app notices changes. Android 17 source and captures from the phone.
- **The hearing aid and boost:** what Phonak and wearers say about the loudness of a stream, fade-in, the level of the speech engine, audio effects. Phonak documents, forum reports, Android 17 source.

This document is the condensed result. Where a skeptic corrected a report, only the corrected statement is given; section 10 lists all of those places.

"Captures from the phone, 2026-11-27" are logcat with the event log, the dump of the Bluetooth service, the Bluetooth configuration flags and the app's own journal, taken on that day for other questions. They are not part of this repository, because they contain addresses and names.

Status marks on every statement:

- **[V]** read in a primary source: the Android 17 source at the tag, developer.android.com, a Phonak document, the app's own code. The source is given.
- **[P]** seen in the captures from the phone, 2026-11-27. True for that day and that build.
- **[C]** community report (forum, issue tracker). A hint, not a fact. Almost all of them are about older Phonak generations.
- **[I]** inferred from the above, stated by none of them.
- **[U]** unknown: no source found. Needs the phone or the ear.

After the mark comes the skeptic's verdict: *confirmed*; *corrected* (what stands here is the corrected statement); *refuted* (what stands here is what replaced the claim); *could not check*; *added by the skeptic* (a fact the report had missed); *critique* (from the design critique, which nobody re-checked); or *not re-checked*. Statement numbers: L for the loudness path, S for what an app may set, M for memory, T for timing, K for the volume keys, B for boost, Ph for the hearing aid. H and A numbers refer to [`hearing-function.md`](hearing-function.md).

Words used here:

- **Usage:** what an app declares a sound to be (`AudioAttributes`), for example media or assistant. Android derives from it where the sound goes and which volume it is under.
- **Stream, index:** a volume as the user knows it (media, call, alarm), with a position between 0 and a maximum. `STREAM_MUSIC` is the media volume (constant 3), `STREAM_ASSISTANT` the assistant volume (constant 11).
- **Volume group, curve:** in the audio server every track belongs to a volume group. A curve turns the group's index into a gain in dB for the kind of output; the aid on the media channel counts as a "headset".
- **Absolute volume:** a Bluetooth (AVRCP) feature. With it the phone sends audio at full level and tells the device which volume to apply. Without it the phone turns the audio down itself.
- **Assistant role, role permission:** the app is the phone's selected assistant. A few permissions are granted to the holder of a role and to nobody else outside the system.
- **Media channel, voice link, streaming program, fitting:** as in [`hearing-function.md`](hearing-function.md): Bluetooth A2DP for announcements, the hands-free link for sessions, the program the aid runs while it receives sound, the settings an audiologist stored in the aid.
- **The announcer:** the part of the app that plays an unprompted announcement: 450 ms of silence, a chime, then speech from the phone's speech engine.

---

## 0. Short answer

| # | Question | Answer | Basis |
|---|---|---|---|
| 1 | Which volume is an announcement under? | With the usage the app had used until now (`USAGE_ASSISTANT`): Android 17's separate assistant volume, stream 11, range 0 to 15, default 5. Not the media volume. | [V] source and Google's release notes; [P] |
| 2 | Why "much too quiet"? | Most probably that index. It read 5, then 13, then 8 of 15 on 2026-11-27. By AOSP's curve 5 is 28.5 dB under full and 8 is 18.5 dB under full, and the value in effect on the media channel may have been 5 all along. | [P], [I], section 2.4 |
| 3 | Does turning up the media volume help? | Not for a sound played with the assistant usage. On this phone with this aid the media index has no effect on it, not even as an upper limit. | [V], [P] |
| 4 | Does the aid have a volume the phone can set? | No. The aid takes no absolute volume. Every volume on the phone is a digital gain in the phone's mixer. The full index is 0 dB, the loudest the phone can send. | [P], [V] |
| 5 | May an app set the assistant volume? | Only with a permission that goes to the holder of the assistant role. Whether a sideloaded holder gets it is untested. Without it the call is dropped without a word. | [V], [U] |
| 6 | May an app set the media volume? | Yes, without a permission, for the output media is routed to at that moment. Android drops the call silently in four situations; reading the value back tells. | [V] |
| 7 | What goes wrong with "raise, speak, put back"? | The raised value can stay: when the app dies or the aid drops out in between, it remains in the system setting and in the Bluetooth stack's memory for the aid, which hands it back at the next connection. A restore after a route change lands on another output. | [V] |
| 8 | Do the volume keys reach an announcement from the pocket? | With the assistant usage: no. While a track of the media volume group plays: yes, they change the media volume, also with the display off. | [V] in part, [I]; untried |
| 9 | Can anything be louder than the full index? | Not through a volume: the gains of a track and of the speech engine end at 1.0. The chime's own amplitude (0.25 today) has 12 dB of room. Beyond that only compression, with risks. | [V] |
| 10 | Does the aid fade a stream in? | Unknown for the EON. Wearers of earlier generations report 2 to 3 s for the switch into the streaming program; today's announcement is over within that time. | [U], [C], [P] |
| 11 | What does the app do with this? | Announcements move to the media volume group, and a slider sets the phone's media volume for the aid. Section 9. | decision |

In one paragraph: the announcements were not under the volume everybody assumed. Android 17 gave the assistant usage a volume of its own, an ordinary app cannot set it, the owner's volume keys reach it only while such a sound plays with the display on, and the aid's own volume reports rewrite it during assistant conversations. Because the aid takes no absolute volume, that index is nothing but a digital attenuation in the phone: 28.5 dB at the default and 18.5 dB at the last value seen. The media volume is the one volume an app may set for certain, and the one the owner can change with the keys from his pocket, so the announcements move there. What no volume setting changes: the ceiling at the full index, Do Not Disturb, and whatever the aid does with a short stream.

How to read this: sections 0, 9 and 11 are enough for the decision and for what has to be tried. Sections 2 to 8 are the evidence, statement by statement, for whoever wants to check or change something.

## 1. The question, and what the sources can and cannot tell

How loud an announcement is in the ear has four parts, and each has a different owner:

1. **The level of the samples.** The chime's amplitude is a constant in the app. The level of the speech comes from Google's speech engine, which is closed.
2. **The gain the phone applies.** Stream, index, curve, mixer. Readable in the source; the values are the phone's. Sections 2 to 6.
3. **What the aid does with what it receives.** The gain of its streaming program, the fitting, its own compression, a possible fade-in, the surroundings. Not readable from outside. Section 8.
4. **How that sounds.** Only on the ear. Section 11.

Limits of the sources, in short (section 12 has the detail):

- **Android:** the only public Android 17 platform source is the first release, tag `android-17.0.0_r1` (build `CP2A.260605.016`). The phone runs a later build (`CP3A.261005.005`, SDK 37.1, by [`../measurements/2026-11-27.md`](../measurements/2026-11-27.md)) without public source. Flag values are those of AOSP's published release configuration `cp2a`; Google's own values for the Pixel build cannot be seen from outside. The Bluetooth stack and the permission controller are updatable modules. No device tree and no audio policy configuration of the Pixel 10 is public, and the Pixel's audio HAL is closed.
- **The phone:** captures of 2026-11-27. None of them contains a line about the app's own announcement track. An update of the phone or of the aid's firmware since then would need a new read.
- **Phonak** publishes no level, no default gain and no switching time for streaming, for any generation.
- **Nothing was run.** No call named in this document was made on the phone.

---

## 2. What decides the loudness on the phone's side

Read from the Android 17 source at tag `android-17.0.0_r1` and from developer.android.com. `AudioService.java:3029-3038` means lines 3029 to 3038 of that file at the tag. Base URLs:

- `frameworks/base`: https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-17.0.0_r1/
- `frameworks/av`: https://android.googlesource.com/platform/frameworks/av/+/refs/tags/android-17.0.0_r1/
- `packages/modules/Bluetooth`: https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/tags/android-17.0.0_r1/
- `packages/modules/Permission`: https://android.googlesource.com/platform/packages/modules/Permission/+/refs/tags/android-17.0.0_r1/
- `build/release` (flag values): https://android.googlesource.com/platform/build/release/+/refs/tags/android-17.0.0_r1/
- Pixel 9 overlay, the newest public Pixel tree: https://android.googlesource.com/device/google/zumapro/+/refs/tags/android-15.0.0_r1/

Where the files cited by name live:

| Repository | Directory | Files |
|---|---|---|
| `frameworks/base` | `services/core/java/com/android/server/audio/` | `AudioService.java`, `HardeningEnforcer.java`, `SoundDoseHelper.java`, `BtHelper.java`, `AudioDeviceInventory.java`, `AudioDeviceBroker.java` |
| `frameworks/base` | `media/java/android/media/` | `AudioManager.java`, `AudioAttributes.java`, `AudioSystem.java`, `AudioTrack.java`, `IAudioService.aidl`, `audiopolicy/AudioProductStrategy.java`, `audiofx/LoudnessEnhancer.java` |
| `frameworks/base` | `core/java/android/speech/tts/` | `TextToSpeech.java`, `TextToSpeechService.java`, `BlockingAudioTrack.java`, `SynthesisPlaybackQueueItem.java`, `SilencePlaybackQueueItem.java` |
| `frameworks/base` | `services/core/java/com/android/server/media/` | `MediaSessionService.java`, `MediaSessionStack.java`, `AudioManagerRouteController.java` |
| `frameworks/base` | other | `core/res/AndroidManifest.xml`, `core/res/res/values/config.xml`, `services/core/java/com/android/server/EventLogTags.logtags`, `packages/SystemUI/src/com/android/systemui/volume/VolumeDialogControllerImpl.java`, `packages/SettingsProvider/src/com/android/providers/settings/SettingsProvider.java` |
| `frameworks/av` | `services/audiopolicy/` | `managerdefault/AudioPolicyManager.cpp`, `engine/common/src/EngineDefaultConfig.h`, `engine/common/src/VolumeCurve.cpp`, `config/default_volume_tables.xml`, `config/audio_policy_volumes.xml`, `common/include/Volume.h` |
| `frameworks/av` | `services/audioflinger/` | `Threads.cpp`, `AudioFlinger.cpp`, `Effects.cpp`, `MelReporter.cpp`, `sounddose/SoundDoseManager.cpp` |
| `frameworks/av` | `media/` | `libeffects/loudness/EffectLoudnessEnhancer.cpp`, `libeffects/loudness/dsp/core/dynamic_range_compression.h` and `.cpp`, `libeffects/dynamicsproc/EffectDynamicsProcessing.cpp`, `libeffects/dynamicsproc/dsp/DPFrequency.cpp`, `libaudioclient/AudioTrack.cpp`, `audio/aconfig/audio.aconfig` |
| `packages/modules/Bluetooth` | `android/app/src/com/android/bluetooth/` | `avrcp/AvrcpVolumeManager.java`, `avrcp/AvrcpTargetService.java`, `a2dp/A2dpService.java`, `storage/BluetoothStorageManager.kt` |
| `packages/modules/Bluetooth` | `system/` | `profile/avrcp/connection_handler.cc`, `profile/avrcp/device.cc`, `conf/interop_database.conf`, `audio_hal_interface/aidl/a2dp/a2dp_aidl_transport.cc` |
| `packages/modules/Permission` | `PermissionController/res/xml/` | `roles.xml` |

Named in the reports without a path, to be found by name in the repository given: `HeadsetStateMachine.java` (Bluetooth), `RoleControllerServiceImpl.java` (Permission), `AudioPolicyInterfaceImpl.cpp` (`frameworks/av`), `Activity.java`, `PhoneWindow.java` and `PhoneWindowManager.java` (`frameworks/base`).

The skeptics downloaded the files again from the tag. The skeptic of the volume path fetched 19 files and found them byte-identical to the report's copies. References such as `Announcer.kt:355` are to the app at commit `ad4c26d` (build 21), before the change this report led to.

### 2.1 Which volume an announcement is under

- **L1.1** [V] *confirmed*. A sound played with `USAGE_ASSISTANT` belongs to `STREAM_ASSISTANT` (constant 11), not to `STREAM_MUSIC`. `AudioAttributes.getVolumeControlStream()` asks the audio policy engine first and then falls back to a table of usages; both give the assistant stream. The content type plays no part. In the audio server the track gets the volume group of the assistant strategy. Range 0 to 15, default 5.
  - `AudioAttributes.java:1872-1950` (case `USAGE_ASSISTANT` at 1912-1913), `AudioProductStrategy.java:309-317`, `EngineDefaultConfig.h:94-104`, `AudioService.java:703-732` (maximum 15), `AudioSystem.java:2837-2850` (default 5).
- **L1.2** [V] *confirmed*. Since Android 17 that stream is no longer tied to media. `AudioService.updateStreamVolumeAlias()` reads the old resource `config_useAssistantVolume` (false in AOSP, which meant: copy the media index) and then overrides it: with the build flag `com.android.media.audio.stream_assistant_not_aliased_to_music` on, and the device neither single-volume nor a watch, the assistant stream is its own. The flag is `ENABLED` and `READ_ONLY` in `cp2a`. What follows: the stream has its own stored setting (`volume_assistant`, per device type; for the media channel to the aid `volume_assistant_bt_a2dp`), starts at 5 of 15, and `setStreamVolume(STREAM_MUSIC)` does not touch it any more.
  - Google: "Android 17 introduces a dedicated Assistant volume stream for Assistant apps, for playback with USAGE_ASSISTANT. This change decouples Assistant audio from the standard media stream". https://developer.android.com/about/versions/17/features
  - `AudioService.java:3029-3038`, `3079`, `744-757`, `2905-2916`, `11136-11160`; `core/res/res/values/config.xml:6413-6414`; `audio.aconfig:209-214`; `build/release` `aconfig/cp2a/com.android.media.audio/stream_assistant_not_aliased_to_music_flag_values.textproto`.
  - The project's own earlier note was out of date on this point and carries a correction since 28 November 2026: the table in [`bt-audio.md`](bt-audio.md) named "media volume" as the volume control of the media channel. That was so up to Android 16.
- **L1.3** [V] *confirmed*. The speech engine's track is under the same volume as the chime. `TextToSpeech.setAudioAttributes` stores the attributes in the request parameters; in the engine's process the framework builds the track with them. That the track belongs to the engine's UID does not matter for the stream volume. Google's engine is closed; this is the framework code that every engine's playback goes through.
  - `TextToSpeech.java:1522-1531`, `2024-2045`; `TextToSpeechService.java:738-767`; `BlockingAudioTrack.java:215-240`. The announcer's attributes: `Announcer.kt:63-66`.
- **L1.4** [P] *corrected*. On the owner's phone the assistant stream is separate from media, its maximum is 15, and it stood at the default 5 until volume reports over the voice link moved it. Event log of 2026-11-27: `volume_changed: [11,5,13,15,com.google.android.bluetooth]`, later `[11,13,8,15,...]` (fields: stream, previous level, level, maximum, caller; `EventLogTags.logtags:286`), and `stream_devices_changed: [11,128,32]`. The system UI logged the assistant stream as the active stream.
  - In the tag source AudioService writes these two events only for a stream that is not tied to another (`AudioService.java:11203-11210`, `10911-10913`), and the system UI is told the stream after the tie has been resolved (`6255`).
- **L1.5** [U] *could not check*. That the Pixel's own audio policy table puts the app's assistant-usage track into the assistant volume group. Nothing public shows the Pixel 10's engine configuration. The captures show a focus request of Google's assistant with the assistant usage, followed by a re-evaluation of the routing "for strategy 11" (the assistant strategy), and AudioService logging "absolute volume driving streams 11" in conversation mode. A separate strategy does not prove a separate volume group. This has to be decided on the phone: section 11, check 1.
- **L1.6** [V] *added by the skeptic*. Other usages follow the media volume: `USAGE_MEDIA`, and in AOSP's default table also `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` and `USAGE_UNKNOWN` (`EngineDefaultConfig.h:105-121`). The Pixel's own table is not public.

### 2.2 No absolute volume: every volume is a digital gain in the phone

- **L2.1** [P] *corrected*. The aid takes no absolute volume, as of the captures. The Bluetooth dump lists it in "Device Absolute Volume Map" with `false`; the native part says "Absolute Volume Enabled: true" for the phone as a whole and, for the aid, "Current Volume: Absolute Volume not supported" with no registered notifications. The system server logged "setAvrcpAbsoluteVolumeIndex: abs vol not supported" at every volume change and "onUpdateContextualVolumes: ... avrcp supported: false". The Bluetooth volume log holds only `storeVolumeForDevice` lines, which is the path for devices without absolute volume.
  - Source for the wording: "Absolute Volume not supported" is the state of a device that never answered the registration for volume changes (`device.cc:1938-1946`). Android uses absolute volume only if the device's AVRCP record is version 1.4 or newer with category 2, and neither the developer option nor the interoperability table forbids it (`connection_handler.cc:63-75`, `553-568`).
- **L2.2** [V] *confirmed*. AOSP carries a Phonak entry in the table of devices for which absolute volume is switched off: `interop_database.conf:107`, an address range under `[INTEROP_DISABLE_ABSOLUTE_VOLUME]`. It goes back to a Google commit of 2021-11-12, "Disable absolute volume control for Phonak": "Phonak does not adjust volume with the level included in set absolute volume command. Disable absolute volume control for Phonak to avoid volume keep at max."
  - Commit `e075f1eaede19984f0bfb07f1d9b86f95c5a08f5`, read through the mirror https://github.com/LineageOS/android_packages_modules_Bluetooth/commit/e075f1eaede19984f0bfb07f1d9b86f95c5a08f5 and by the skeptic at android.googlesource.com.
  - [U] Whether the EON R is off because of this table or because it does not announce the feature at all. The result is the same either way. The developer option "Disable absolute volume" changes nothing here.
- **L2.3** [V] *confirmed*. For such a device AudioService treats the media channel as an ordinary output with a variable volume: the index becomes a gain by the curve, and AudioFlinger's mixer applies it per track before the audio is encoded and sent. Nothing is sent to the aid about volume (`BtHelper.java:788-811`, early return at 796-800). The aid plays what it receives at its own streaming gain.
  - `Threads.cpp:6082-6096`, `6149-6150` (the mixer multiplies the track by its volume).
  - Consequence: every dB below the maximum is taken off the digital signal in the phone. The loudest possible announcement is the full index with a full-scale signal.
- **L2.4** [V] *corrected*. Without an absolute-volume entry a track gets the curve of its own volume group at that group's index, and nothing else: `AudioPolicyManager.cpp`, `adjustDeviceAttenuationForAbsVolume` 9013-9053 and `computeVolume` 9055-9066. Both switches are known for this phone (the assistant stream is not tied, L1.4; no absolute volume, L2.1). So the media index has no effect on a sound played with the assistant usage, not even as an upper limit. It would be an upper limit only with a device that takes absolute volume.
  - Only for such a device, should the app ever meet one: the correction of the assistant group against the media index is computed when the track starts (`AudioPolicyManager.cpp:2783-2788`) and not again when the media index changes while it plays.
- **L2.5** [V] *refuted*. Setting the media volume sends nothing to this aid. A command over the air, at once and also while nothing plays, exists only for a device with absolute volume. For the aid the change is an index in Android plus the Bluetooth stack's memory for the aid (M1.2).
- **L2.6** [U] *could not check*. Whether the Pixel's closed audio HAL applies a gain of its own behind the mixer on this route, and how long the phone-side output path is. The phone's log shows the route as primary playback to the Bluetooth media device with A2DP offload enabled. Only listening, or a recording at the aid, can settle it (T1.4).

### 2.3 The curves, and the numbers for this phone

- **L3.1** [V] *confirmed*. The media channel to the aid counts as a headset-class output (`Volume.h:117-149`). AOSP's assistant curve for that class has the points 1: -50 dB, 20: -35 dB, 60: -15 dB, 100: 0 dB (`default_volume_tables.xml:129-142`, referenced for the assistant stream in `audio_policy_volumes.xml:184-185`). The media curve has 1: -58 dB, 20: -40 dB, 60: -17 dB, 100: 0 dB (`default_volume_tables.xml:35-41`). `VolumeCurve::volIndexToDb` (`VolumeCurve.cpp:26-63`) scales the index to that 0 to 100 axis and interpolates in dB.
- **L3.2** [V] *corrected*. The Pixel's curves are not entirely closed. The vendor file of the Pixel 10 Pro XL is public in a third-party mirror, and the branch that carries the phone's build references the headset assistant curve for the assistant stream; an earlier branch still referenced the media curve. Not verified: that the phone ships the same `default_volume_tables.xml` as AOSP.
  - https://github.com/TheMuppets/proprietary_vendor_google_mustang/blob/lineage-24.0/proprietary/vendor/etc/audio/config/audio_policy_volumes.xml
- **L3.3** [I] *corrected*. The numbers, computed with that formula:

  | Stream and index | Gain | Seen on the phone |
  |---|---|---|
  | assistant, 5 of 15 | -28.5 dB | the default; the value until 15:30 on 2026-11-27 |
  | assistant, 8 of 15 | -18.5 dB | the last value seen |
  | assistant, 10 of 15 | -12.75 dB | |
  | assistant, 13 of 15 | -5.25 dB | seen twice |
  | assistant, 15 of 15 | 0 dB | |
  | media, 15 of 25 | -17 dB | the value stored for the aid in the dump |
  | media, 25 of 25 | 0 dB | |

  - A second curve gives slightly lower figures for the assistant stream: AOSP's example engine configuration assigns the media curve to the assistant group on headsets, and with it 5 is -32.5 dB, 8 is -21 dB and 13 is -6 dB. Each set was confirmed by its skeptic for its curve. The vendor file of L3.2 points to the first set.
  - At the default and at the last value seen, a full index would have been 28.5 and 18.5 dB louder. That fits "much too quiet".
- **L3.4** [P] *corrected*. The media range on this phone is not AOSP's 15 steps: the Bluetooth stack's stored values for the aid reach 25. Read `getStreamMaxVolume(STREAM_MUSIC)` instead of assuming a number.
- **L3.5** [V] *added by the skeptic*. An app can read the phone's real curve: `AudioManager.getStreamVolumeDb(stream, index, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)`, public since API 28, answered by the audio policy (`AudioManager.java:1456-1474`). For stream 11 it throws `IllegalArgumentException` if the build does not treat that stream as public, so it belongs in try and catch.

### 2.4 Who rewrites the assistant index

- **L4.1** [P] with [V] *corrected*. The values seen were written by the Bluetooth app, not by the owner. The aid reports a volume over the voice link (`HeadsetStateMachine.processVolumeEvent`, 1954-1973), and while the audio mode `MODE_ASSISTANT_CONVERSATION` is active AudioService directs such a report to the assistant stream (`AudioService.java:5943-5953`). The phone logged the very line: "MODE_ASSISTANT_CONVERSATION active, directing volume event to STREAM_ASSISTANT with new index 8". That mode was in use by Google's assistant on that day. The sequence: 5 to 13 at 15:30, 13 to 8 half a minute later, 8 to 13 and back to 8 at 15:41.
  - So there is a mechanism that sets the announcement volume low again, and it is the aid itself, each time an assistant conversation runs over the voice link.
- **L4.2** [V] *corrected*. Those were changes of the index of the voice link's device type, not of the media channel's. AudioService copies every change of the assistant stream between the two device types, but only in its own index map (`setIndex`, `AudioService.java:11179-11195`, a plain `mIndexMap.put`). The audio server and the stored setting get the changed device alone (`setStreamVolumeInt` 6299-6317, `setDeviceVolume` 11715-11748, `persistVolume` 11783-11803). By the source the copied value for the media channel is neither passed to the audio policy nor written to `volume_assistant_bt_a2dp`. It reaches the mixer only when all volumes are applied again (a change of mute, a restart of the audio server), and it is gone after a reboot.
- **L4.3** [I] *corrected*. The gain in effect for an announcement with the assistant usage is the one last applied for the media channel's device type. If nobody ever set the assistant volume while the aid was the media output, that is the default, 5 of 15. `getStreamVolume(11)` can report 8 at the same time. "Much too quiet" may therefore have been the lowest row of the table in L3.3 all along.
- **L4.4** [V] *added by the skeptic*. A set to the index AudioService already holds is not passed on: `setStreamVolumeInt` posts the message to the audio server only if `setIndex` reports a change, and the caller passes `force=false`. Together with L4.2 this means that a value read back as wanted is no proof that the mixer uses it. The native state shows in `dumpsys media.audio_policy` (the volume group of the assistant strategy, "Index Cur", device `0080`); `dumpsys audio` shows only the framework's map.
- **L4.5** [U] *added by the skeptic*. The assistant index for the media channel today, and whether build `CP3A` copies between the device types exactly as the tag does.

### 2.5 The app's own levels

- **L5.1** [V] *confirmed*. The chime is generated at 0.25 of full scale: a peak of -12 dBFS and, for a sine, about -15 dBFS RMS. `Announcer.kt:355`, `Tones.kt:16-27`. It is independent of every Android volume.
- **L5.2** [U] *confirmed*. How loud the speech of Google's engine is, in peak and RMS, is not published. For the Assistant on smart speakers Google names "-16 LUFS, matching Google Assistant TTS output" and recommends -19 LUFS for mono content; whether the engine on the phone follows that is stated nowhere. https://developers.google.com/assistant/tools/audio-loudness . The app can measure it: it already synthesises into files (B1.3).
- **L5.3** [V] with [I] *added by the skeptic*. The master balance of the accessibility settings is applied on every mixer output, the media channel included (`Threads.cpp:4389-4390`, `4473-4474`). With one aid on the right ear, a balance slider moved to the left would lower the channel the aid may be playing. That this matters for this owner is an inference; it is a ten-second look.

---

## 3. What an app may set, and what Android drops without a word

### 3.1 The media volume

- **S1.1** [V] *confirmed*. `AudioManager.setStreamVolume(STREAM_MUSIC, index, 0)` needs no permission. The index is clamped to the stream's range. The call returns nothing and throws nothing, whether Android applied it or dropped it.
  - `AudioService.java:5385-5397`, `5921-6037`.
- **S1.2** [V] *corrected*. There is no call for a named device. AudioService resolves the device at the moment of the call (`setStreamVolumeWithAttributionInt` 5450-5462, `getDeviceAttributesForStream` 9017-9036), and for the media stream that is the device the media strategy is routed to (`getDeviceSetForStreamDirect` 9203-9209). The index is kept per device type, not per Bluetooth address. If the aid is not the media output at that moment, the same call changes the media volume of the phone's speaker or of whatever else is.
  - The only call with a device argument, `AudioDeviceVolumeManager.setDeviceVolume`, is a system API.
- **S1.3** [V] *corrected*. The media route and the route of a sound with the assistant usage can differ. Choosing "This phone" in the system's output switcher sets a preferred device for the media strategy only (`AudioManagerRouteController.java:468-502`). A route check made with the assistant attributes therefore says nothing about where a media volume call lands: the check before a write and before a restore must use media attributes.
  - *Added by the skeptic*: an announcement that is itself played with the media usage follows the output switcher's media preference (`AudioManagerRouteController.java:497-499`). With "This phone" chosen there, the announcer's check finds the speaker as the route and, by the app's rule that sound goes to the selected aid only, skips every announcement. [I] In return the announcer's route check and the volume call then ask the same strategy.
- **S1.4** [V] *confirmed*. Whether a call was accepted can be read at once. The call is a blocking binder call (`IAudioService.aidl:123`, not `oneway`); inside it the index map is written, and that write clears the cache `getStreamVolume` reads from (`AudioService.java:10687-10697`). `getStreamVolume` right afterwards returns the new index if the call was accepted and the old one if it was dropped. It is the only way to learn it.
- **S1.5** [V] *added by the skeptic*. `getStreamVolume` is answered from a cache in the app's process, keyed by the stream alone (`AudioManager.java:1310-1326`, `1405-1407`). AudioService clears it at every index write and change of mute, and after a change of route only when it handles the routing update (`AudioService.java:2151-2160`), a moment later. Right after the output changed, the value can still be the old device's. Whether the aid is still the output is asked with `getAudioDevicesForAttributes`, first, and never judged from the volume value.
- **S1.6** [V] *confirmed*. With `flags = 0` nothing shows and nothing sounds, and the screen stays off. The volume panel needs `FLAG_SHOW_UI` and an awake, interactive device (`VolumeDialogControllerImpl.java:575-581`); a click needs `FLAG_PLAY_SOUND`; the path of `setStreamVolume` contains no wake-up of the screen. What does happen: the change is broadcast to all apps (S6.1), and the media slider shows the new value the next time it is opened. Read in AOSP's system UI; the Pixel's is a closed variant.

### 3.2 The silent refusals

None of these throws or returns anything. They hold for the media stream and for the assistant stream alike.

- **S2.1** [V] *confirmed*. **Background hardening.** `HardeningEnforcer.blockVolumeMethod` (275-392, called at `AudioService.java:5389-5394`) drops a volume call from a UID that is below foreground-service state. Because the app holds `USE_EXACT_ALARM` it is judged at the lenient "partial" level whatever its target SDK: the call passes while any foreground service of the app runs or one of its activities is visible. From an alarm receiver alone it is dropped and logged as "AudioHardening volume control for api 100 ignored for" the package, "level: partial".
  - [V] from the earlier report, not from a device: as the selected assistant the app's UID sits at bound-foreground-service state for as long as the system keeps the binding, which passes. [`verify-audio-hardening.md`](verify-audio-hardening.md), claim 2.
  - Documentation and code differ: the Android 17 page limits the waiver for exact-alarm apps to streams with the alarm usage, the code applies the lenient level to every volume call of such an app. The code may be tightened later. https://developer.android.com/about/versions/17/changes/bg-audio ("Volume and ringer mode APIs: ... method call is silently ignored").
- **S2.2** [V] *confirmed*, with an addition by the skeptic. **Do Not Disturb.** If Do Not Disturb currently mutes media (total silence, or priority mode with the media category not allowed), `volumeAdjustmentAllowedByDnd` drops the call (`AudioService.java:6080-6092`). `updateZenModeAffectedStreams` (8553-8600) adds the assistant stream to the muted streams together with media. So Do Not Disturb silences the announcement itself, on either volume, and no setting in the app can change that. The journal should name it as the reason.
- **S2.3** [V] *confirmed*. **A user restriction** on changing the volume: the app-op `OP_AUDIO_MEDIA_VOLUME` is denied. The same op guards the assistant stream (`STREAM_VOLUME_OPS`, `AudioService.java:826-840`).
- **S2.4** [V] *confirmed*. **A fixed-volume device** (`isVolumeFixed()`, `AudioService.java:9496-9512`; false in AOSP's phone configuration).

### 3.3 Mute

- **S3.1** [V] *confirmed*. `getStreamVolume` returns 0 for a muted stream (`AudioService.java:6511-6519`). A set with an index above 0 unmutes the stream and the streams tied to it; index 0 mutes it (`onSetStreamVolume` 4885-4890).
  - The trap, added by the skeptics: an app that remembers 0, sets a level and puts 0 back leaves the stream muted and the owner's former index overwritten. And a set alone unmutes what the owner or Do Not Disturb had muted. Ask `isStreamMute` first and leave a muted stream alone.

### 3.4 The assistant volume and the role permission

- **S4.1** [V] *confirmed*. An ordinary app cannot set the assistant stream. `setStreamVolume` and `adjustStreamVolume` for stream 11 return early, with a log line only ("Trying to call setStreamVolume() for STREAM_ASSISTANT without ..."), unless the caller holds `MODIFY_AUDIO_ROUTING`, `MODIFY_AUDIO_SETTINGS_PRIVILEGED` or `MANAGE_ASSISTANT_AUDIO` (`AudioService.java:5436-5448`, `4362-4379`). The first two are for system apps. Reading the index needs no permission.
- **S4.2** [V] *confirmed*. `MANAGE_ASSISTANT_AUDIO` has the protection level `signature|role` (`core/res/AndroidManifest.xml:7554-7561`), and `roles.xml:164-165` lists it among the permissions of the assistant role. Both sit behind the flag `android.media.audio.manage_assistant_audio_permission`, `ENABLED` in `cp2a`. The app holds the role (its journal says so); at commit `ad4c26d` its manifest does not declare the permission.
  - *Critique*: AudioService checks this permission in three places only (set, adjust, and `setMode` for the assistant conversation mode), so declaring it changes nothing else.
- **S4.3** [U] *could not check*. Whether the phone grants it to a sideloaded holder of the role. The role controller is part of the updatable permission module, whose version on the phone cannot be seen, and neither can the flag's state in build `CP3A`. One supporting fact [P]: the assistant conversation mode that this permission gates was live on the phone on 2026-11-27, used by Google's app.
  - [V] *added by the skeptic*: by the source an existing holder gets a newly declared permission without choosing the assistant again, but not at once. `RoleControllerServiceImpl.onGrantDefaultRoles` redoes the grant for current holders on its next pass after the package changed (149-164). Until then a set of stream 11 is dropped silently.
  - The hardening check of S2.1 runs first for this call as well; this permission is not on its list of privileged ones (`HardeningEnforcer.java:552-557`).
- **S4.4** [V] *corrected*. Writing the assistant index has side effects of its own. (1) The device is resolved at the moment of the call here too, so a write while the aid is not the output changes the assistant volume of the speaker. (2) The index is one value per device type: the same for every Bluetooth media device, and copied into the entries of the voice-link type (L4.2). The Bluetooth stack keeps no memory per device for it (`AvrcpTargetService.java:225-228` returns for every stream but media). The owner's other Bluetooth media devices would play Google's assistant voice with the value chosen for the aid. (3) A set to the value already in the map is not passed on (L4.4), and volume reports over the voice link keep overwriting the map entry. The value would have to be applied at the start of every announcement, with a set to a neighbouring index first whenever the value read already equals the wanted one (*critique*).
- **S4.5** [V] *critique*. A later platform version documents the way to ask for this access: `AudioManager.canManageAssistantAudio()` and `Settings.ACTION_MANAGE_ASSISTANT_AUDIO`, "Added in version 37.2". The phone is on 37.1 and the tag has neither. https://developer.android.com/reference/android/media/AudioManager
- **S4.6** [I] *not re-checked*. With the permission an app could also call `AudioManager.setMode(MODE_ASSISTANT_CONVERSATION)`. Its effects on routing and on the voice link were not examined. Nothing here uses it.

### 3.5 Safe volume and sound dose

- **S5.1** [V] *confirmed*. **The index-based safe volume** (when sound-dose measurement is off) refuses a set above the safe index instead of capping it: the index stays, the request is kept as pending and a warning is posted; only when the user confirms is it applied. The check is true only if the state is active, the stream is tied to media, and the device has a safe index. Only wired headsets, wired headphones and USB headsets get one. The Bluetooth media channel is on the list of safe-volume devices but has no safe index, so the check never bites there. A separate assistant stream is never checked. The enabling resource is true by default and false only for the mobile country codes 310 to 316; Germany has no override. Safe index: 10.
  - `AudioService.java:6012-6021`; `SoundDoseHelper.java:344-359`, `667-672`, `674-706`, `1134-1147`; `core/res/res/values/config.xml:3498`, `3502`, `3584`, `3592`.
- **S5.2** [V] *confirmed*. **With sound-dose measurement on** there is no index limit: the old state is set to disabled (`SoundDoseHelper.java:1014-1054`), every index is applied and no safe-volume dialog appears. Instead Android measures the dose of what is played and can lower the media volume itself: at five times the dose at once, and at the full dose if the warning is not acknowledged within 5 s (`1332-1357`). For Bluetooth media devices the dose is computed only if the device's audio category is "headphones"; hearing aid, speaker, car kit, other and unknown are not measured. The category comes from the device's metadata; without it the category is unknown and the user may choose it in the device details. The framework switches the computation on per output only while a track with the media or game usage plays; the assistant usage does not start it.
  - `SoundDoseHelper.java:572-607`, `1056-1087`; `BtHelper.java:1660-1709`; `AudioDeviceInventory.java:171-210`, `280-292`; `SoundDoseManager.cpp:238-245`, `546-587`; `MelReporter.cpp:83-118`, `125-168`.
- **S5.3** [I] *confirmed*. On this phone sound-dose measurement is on, so there is no index cap and no safe-volume dialog. Evidence: the Pixel 9's public overlay enables it, also for the US country codes (`device/google/zumapro` `overlay/frameworks/base/core/res/res/values/config.xml:65`); and the phone logs a line of `SoundDoseManager` for the Bluetooth media device type that the source reaches only with the measurement enabled. The captures contain no line that a stream was added for the dose, which fits the aid not being counted as headphones: the Bluetooth stack's database has no device-type metadata for the aid. Not read: the values in `dumpsys audio` (`mEnableCsd`).
- **S5.4** [V] *confirmed*. The warnings are system dialogs. AudioService forces the flag that shows them even when the caller passed 0, but neither the old safe-volume dialog nor the dose dialogs turn the screen on or play a sound; they wait on top of the lock screen. When Android lowers the volume by itself it also posts a notification, whose sound and pop-up behaviour were not read.
- **S5.5** [I] *confirmed*. What this means for announcements played with the media usage: they count toward the dose only if the aid's device type is ever set to "Headphones" in the phone's Bluetooth settings. Then Android may lower the media volume on its own, and an app that puts "its" old index back afterwards would undo that.
- **S5.6** [V] with [P] *added by the skeptic*. The Bluetooth stack can cap the media volume it remembers per device: `AvrcpVolumeManager.storeVolumeForDevice` limits the stored value to the system property `bluetooth.avrcp.target.safe_media_volume.config` when that is set. The stored values for the aid reach 25, the maximum, so no lower cap is active on this phone.

### 3.6 What an app can read and observe

- **S6.1** [V] *confirmed*. The broadcast `android.media.VOLUME_CHANGED_ACTION` is hidden but can be received with a receiver registered at runtime under the literal string. It is sent without a receiver permission, only when the index really changed and only for a change on the device the stream is routed to at that time. Its extras are the stream, the new and the previous value; nothing says who changed it, so the app's own sets arrive too. Older undelivered broadcasts for the same stream are dropped in favour of the newest, and delivery waits while the receiving process is cached. A change of the assistant stream is broadcast the same way, with stream type 11. Usable as a hint and for the journal, not as a basis for decisions.
  - `AudioManager.java:210-239`, `302-332`; `AudioService.java:10737-10746`, `11214-11263`, `6178-6191`.
- **S6.2** [V] *confirmed*. There is no public volume listener for other apps (`registerVolumeGroupCallback` is a system API, `AudioManager.java:9403-9434`). The settings keys `volume_music_bt_a2dp` and `volume_assistant_bt_a2dp` can be read and observed by any app, because the settings provider hides only keys that are declared as constants, and these composed keys are not (`SettingsProvider.java:2484-2495`, `2541-2593`). They lag by half a second and exist per device type, which lets an app read the value for the media channel while the aid is not connected. `VolumeProvider` is not a listener: it replaces the volume handling of a media session.
- **S6.3** [V] *added by the skeptic*. Three questions an app can answer without adb. (1) `AudioManager.isMusicActive()` about half a second into its own announcement, with nothing else playing: true means the announcement is in the media volume group on this phone, false means it is not (`AudioService.java:4047-4059`). The value the announcer journals at `announce_start` is taken before any sound and says nothing about this. (2) `getStreamVolumeDb` for the real curve (L3.5). (3) `checkSelfPermission` for the role permission (S4.3).
  - `getVolumeGroupIdForAttributes` (public since API 34) would be the direct answer, but at the tag it goes through a call that enforces system permissions; by the source it throws for this app.
- **S6.4** [V] *critique*. `AudioAttributes.getVolumeControlStream()` is public and asks the phone's policy engine first (`AudioAttributes.java:1872-1950`); the native lookup has no permission check (`AudioPolicyInterfaceImpl.cpp`, `getStreamTypeForAttributes`, 2482-2499). It names the stream that governs a set of attributes on this build: 3 for media, 11 for a separate assistant volume.

---

## 4. Where volumes are remembered, and what goes wrong at a restore

### 4.1 Three stores for the media volume

- **M1.1** [V] *confirmed*. AudioService keeps one index per stream and device type in memory (key `0x80` for the Bluetooth media type, shared by all such devices) and writes it to `Settings.System` under `volume_music_bt_a2dp` 500 ms after each change.
  - `AudioService.java:404` (the delay), `10927-10936`, `10961-11014`, `11715-11749`, `11783-11813`; `AudioSystem.java:1621`.
- **M1.2** [V] *corrected*. The Bluetooth stack keeps one more value per device address, also for a device without absolute volume. A receiver in `AvrcpTargetService` (218-236) stores every change of the media stream for the active media device; `BluetoothStorageManager.setAvrcpVolume` holds it (`BluetoothStorageManager.kt:663-672`). When a device becomes the active media device, `A2dpService` (546-559) hands the remembered value to AudioService, which writes it into the index (`AudioDeviceInventory.java:1033-1046`). A device with no stored value gets half of the maximum (`AvrcpVolumeManager.java:172`). So the value per address wins at every connection.
  - [P] The phone's Bluetooth dump shows exactly this for the aid: a stored volume of 15, and one `storeVolumeForDevice` line for every volume step. On 2026-11-27 the value moved about 45 times between 14 and 25.
  - The receiver stores the value for whichever media device is active, without looking at which output the change was for.
- **M1.3** [V] *confirmed*. The media volume therefore does not reset at a reconnection: a low value comes back low every time. No wearer report of Android resetting the volume of a Phonak aid at each connection was found.
- **M1.4** [V] *corrected*. All of M1.2 is about the media stream only. The assistant index is stored by AudioService alone (`volume_assistant_bt_a2dp`, per device type), has no memory per device, and has the rewriting mechanism of L4.1.

### 4.2 Raise, speak, put back

- **M2.1** [V] *confirmed*. Each of the two sets changes the index in memory at once, queues the change for the audio server, queues a settings write 500 ms later that writes whatever the index is then, and sends the broadcast on which the Bluetooth stack stores the value for the aid. After the restore all stores hold the old value again, provided both calls were accepted and the app lived to make the second.
- **M2.2** [V] *confirmed*. If the app dies, is killed or loses its foreground state between the two, the raised value stays: persisted for the device type, remembered for the aid's address, and applied again at every reconnection. *Added by the skeptic*: the next music in the ear then starts at the raised level. For someone who wears a hearing aid that is a safety point, not a cosmetic one.
- **M2.3** [V] *confirmed*. If the aid disconnects between the two, the restore hits another output (S1.2): normally the phone's speaker, or a car kit or headset if one is connected, whose media index is overwritten with the aid's old value. The aid's raised value survives twice: in AudioService's index for the Bluetooth media type and, because the Bluetooth stack stored it when the raise was broadcast, per address. At the restore the Bluetooth receiver stores nothing, because no media device is active. When the aid comes back, the raised value is applied. Neither step produces an error.
  - `AudioService.java:5451-5463`, `9012-9036`; `AvrcpTargetService.java:230-234`; `A2dpService.java:546-559`.
- **M2.4** [V] *added by the skeptic*. A repair after the reconnection can itself be overwritten. `AudioDeviceInventory` posts the Bluetooth stack's remembered volume before it makes the media device available, on another thread than the app's call; a repair made from the app's device callback can land before or after it. Read the value again about a second after the connection. And if another Bluetooth media device is active at the time of a late restore, the aid's old value goes into that device's index and, through the receiver of M1.2, into that device's memory.
- **M2.5** [I] *critique*. While the raise lasts it is the level of every app's media in the aid. Music or a podcast that Android only ducks for the announcement is lifted to the raised level for those seconds.
- **M2.6** [I] What follows for code that raises and restores, from the report (*confirmed*) and the critique:
  1. Before the first write, put a note on disk: the old index and the raised one. Clear it only after a restore that was read back.
  2. Ask the route with media attributes immediately before every write and every compare (S1.3, S1.5). A gap between check and call remains and cannot be closed from an app.
  3. Before the restore, read the value: if it is no longer the one the app set, or the stream is muted, somebody else changed it; leave it.
  4. Never take a read of 0 as "the old index" without `isStreamMute` (S3.1).
  5. If the aid is gone at the restore, keep the note and repair only when the aid is the media output again.
  6. Read back after every set (S1.4). One attempt per announcement, no loop.
  7. `flags = 0`, always (S1.6).

---

## 5. Timing

- **T1.1** [V] *confirmed*. The index changes inside the call, the effect follows a moment later: the audio server gets the new index through a queued message. So a set belongs before the announcer's 450 ms of silence, not right before the first audible sample and not behind the start of the track.
  - `AudioService.java:6299-6320`, `11715-11749`; `AudioPolicyManager.cpp:4042-4047`.
- **T1.2** [V] *confirmed*. For this aid a volume change acts only on audio that is mixed after it, never on audio already on its way, because the gain is applied per track in the mixer (L2.3). The mixer ramps a change over one mix buffer (`Threads.cpp:6053-6071`).
  - [I] For a device with absolute volume it would be different: the command acts in the device at once, on audio that left the phone 200 to 300 ms earlier.
- **T1.3** [V] *confirmed*. The speech engine's `onDone` arrives after the engine's track has handed its last frame to the mixer and has been released: nothing is left to mix, everything behind the mixer is still playing. An utterance shorter than the track's buffer (at least 8192 bytes, about 170 ms) takes another path: stop, then a sleep of the estimated length.
  - `SynthesisPlaybackQueueItem.java:96-149`; `BlockingAudioTrack.java:131-171`, `242-321`; `TextToSpeech.java:2231-2234`.
- **T1.4** [P] with [I] *confirmed*. The aid reports its own delay: in the Bluetooth dump the state machine of the media channel shows a delay report of 2000, in tenths of a millisecond (`a2dp_aidl_transport.cc:138`, `169`), so 200 ms. The phone-side part is not measured. For this aid that tail keeps the loudness it was mixed with, so by the source a restore at `onDone` plus about 200 ms does not touch the last word. *Critique*: keep 500 ms until it has been heard, because a gain stage behind the mixer in the Pixel's closed HAL could not be excluded (L2.6).
- **T1.5** [P] *confirmed*. Today's announcement: the journal shows `announce_speaking` 1044, 1069 and 1133 ms after `announce_start` (three announcements) and `announce_end` 2587 ms after that. The stream is opened by the app's own track: 450 ms of silence, 160 ms of chime, 120 ms of silence (`Announcer.kt:355`, `410`).
  - Skeptic: `announce_speaking` is the engine's start callback, not the first audible sample; the sound comes later by the start of the track and the Bluetooth latency.
- **T1.6** [V] *confirmed*. A raised value is in the stored setting 500 ms after the call (M1.1) and in the Bluetooth stack's memory as soon as the broadcast has been handled (M1.2). For an announcement of about three seconds a temporary value is never private to the app.
- **T1.7** [V] *confirmed* there. Unchanged by any of this: Android keeps the media stream open for 3 s after the last sound (A1.a to A1.c in [`hearing-function.md`](hearing-function.md)).

---

## 6. The volume keys

- **K1.1** [V] *corrected*. While a track with the assistant usage is active, or the assistant conversation mode is on, the phone's volume keys change the assistant stream; at all other times they change media (`AudioService.getActiveStreamType`, 8724-8745 and 8778-8785). But only when the key event reaches a window: with the display on, or dozing with the always-on display and the lock screen showing.
- **K1.2** [V] with [I] *corrected*. With the display off the key is not passed to a window and is sent on as "music only". `MediaSessionService.dispatchAdjustVolumeLocked` then returns early unless the media stream is active (`MediaSessionService.java:2784-2790`): "if (musicOnly && !AudioSystem.isStreamActive(AudioManager.STREAM_MUSIC, 0)) ... return". A track with the assistant usage in its own volume group does not make the media stream active. So in the pocket the keys do nothing during an announcement played with the assistant usage.
  - The critique re-read the lines in `MediaSessionService`. That keys arrive as music-only with the display off is the skeptic's reading of `PhoneWindowManager`, not read a second time.
- **K1.3** [I] *corrected*. The other side of the same lines: while a track of the media volume group plays, the media stream is active, and the keys change the media volume, also with the display off. An announcement played with the media usage can be turned up or down from the pocket while it plays, and the change is Android's own value: it stays, and the Bluetooth stack remembers it for the aid (M1.2). Untried on the phone.
- **K1.4** [V] *added by the skeptic*. Outside those moments the owner cannot reach the assistant volume in normal use: before and after a three-second announcement the keys change something else.
- **K1.5** [V] *critique*, untried. An app can point the keys at a stream while one of its activities is in front: `Activity.setVolumeControlStream` (`Activity.java`, 8267-8285; `PhoneWindow.onKeyDown`, 2058-2070; `MediaSessionService.dispatchAdjustVolumeLocked`, 2756-2828). The keys then change that stream for the current output with the system's identity, so the app needs no permission, also for stream 11. It works only while no other app's media session is playing (`MediaSessionStack.java:329-339`) and, being tied to a screen of the app, only with the display on.

---

## 7. Louder than the full index: what cannot be boosted and what could

### 7.1 What cannot

- **B1.1** [V] *confirmed*. No public gain goes above 1.0. `AudioTrack`: `GAIN_MAX = 1.0f` (`AudioTrack.java:119`), clamped in `clampGainOrLevel` (2867-2877); the native `AudioTrack::setVolume` rejects values above unity (`AudioTrack.cpp:1071-1077`); the mixer clamps track volumes to unity once more (`Threads.cpp:6108-6117`). Speech engine: `KEY_PARAM_VOLUME` is "a float ranging from 0 to 1 ... 1 is the maximum volume (the default behavior)" (`TextToSpeech.java:600-608`) and is clipped in `BlockingAudioTrack.setupVolume` (323-338). Both only turn down from what the stream volume allows.
- **B1.2** [V] *corrected*. Whatever the app plays, the ceiling is the index of the stream its usage belongs to, with a full-scale signal. Moving the announcements to the media usage changes which index that is, not the ceiling: 0 dB at the full index either way.
- **B1.3** [V] *corrected*. Playing the speech from the app itself does not lift that ceiling either. The parts exist: `SpeechEngine.synthesize` writes the engine's speech to a file and `PromptCache.convert` reads it; today they serve the voice sessions only. What it would give: peak and RMS become known numbers, silence, chime and speech can be one track, and the speech runs on the app's own track, pinned to the aid, instead of in the engine's process. What it costs: each announcement is synthesised first, and how long that takes for the title of an appointment on this phone is not measured.

### 7.2 What could

- **B2.1** [V] *confirmed*. **The chime's amplitude.** 0.25 today (L5.1). Raising it toward 1.0 gives up to 12 dB with no effect on anything else.
- **B2.2** [V] *corrected*. **LoudnessEnhancer** works behind the stream volume. It has no volume flag (`EffectLoudnessEnhancer.cpp:41-50`), so the mixer attenuates first and the effect works on the attenuated signal (`Effects.cpp:2703-2780`). It is a compressor made for speech: knee at -8 dBFS, falling to -10 dBFS at a five-fold gain, ratio 7 to 1, attack 1 ms, release 15 ms (`dynamic_range_compression.h:80-95`, `121-161`; `dynamic_range_compression.cpp:26-33`).
  - It does not reach full scale. Above the knee the output is the knee plus one seventh of the excess, so a loud signal ends between about -9 and -5 dBFS; the hard clamp at full scale catches only the first millisecond.
  - Switched on with a gain of 0, a signal that is already hot comes out about 7 dB lower in its peaks.
  - At a full index the most it can add to speech with an RMS near -20 dBFS is roughly 10 to 12 dB.
  - At a low index (5 of 15, -28.5 dB) a gain of 2000 mB is still linear, so it does undo a low index.
- **B2.3** [V] *confirmed*, untested on the phone. An effect may be attached to a session id that the app passes to the speech engine with `KEY_PARAM_SESSION_ID`: "can be used to associate one of the AudioEffect objects with the synthesis (or earcon) output" (`TextToSpeech.java:656-665`). `AudioFlinger::createEffect` checks a permission only for the global session, and says of the rest: "TODO: should we check if the callingUid (limited to pid) is in mAudioSessionRefs to prevent creating an effect when one doesn't actually have track with that session?" (`AudioFlinger.cpp:4270-4321`). The effect is parked until a track with that session appears (`4436-4452`, `1136-1202`).
  - Skeptic: a software effect is rejected when the session has a track that asked for low latency (`Threads.cpp:1478-1517`). The Pixel 9's public tree lists the library of this effect; the Pixel 10's is not public. This way rests on the missing ownership check that the source itself marks with a TODO.
  - On the app's own track (B1.3) the same effect is the ordinary documented use (`LoudnessEnhancer.java:27-37`) and needs none of that.
- **B2.4** [V] *corrected*. **DynamicsProcessing** follows the index but can offset it. It declares itself the volume controller of its chain and applies the volume as its output gain (`EffectDynamicsProcessing.cpp:552-593`). Its input gain is a plain factor (`DPFrequency.cpp:220-243`), and nothing clamps between that and the last stage (647-655). An input gain of +15 dB at an index worth -15 dB gives the level of the full index, without compression if the limiter is off. The ceiling is full scale at the mixer's output, where it clips hard. Its limiter works on the energy of blocks and lets peaks pass (590-621). In the Pixel 9's tree this effect is a proxy with a hardware variant, so what it does on the phone needs a test.

### 7.3 The risks

- **B3.1** [V] *added by the skeptic*. A boost travels with the session, not with the aid. When the output to the aid closes, AudioFlinger moves every effect chain of that thread to the first output that remains (`AudioFlinger.cpp:3250-3266`), normally the phone's speaker. If the aid drops out in the middle of an announcement, the rest of the sentence comes out of the speaker with the gain still on it, until the app's guard stops it (it polls every 200 ms). An effect must be released in the same step that stops the speech.
- **B3.2** [I] *not re-checked*. A hearing aid compresses again: its streaming program applies less gain the higher the input level, so 10 dB more from the phone arrive as clearly less than 10 dB in the ear.
- **B3.3** [C] with [I] *not re-checked*. Distortion at the top: a Lumity wearer describes hot streams as "overdriven, compressed, a little clipped" (https://forum.hearingtracker.com/t/-/88136). A signal limited exactly at full scale can overshoot after the Bluetooth codec, so a ceiling belongs a few dB below full scale.
- **B3.4** [I] *not re-checked*. Compressed speech loses the difference between the loud and the soft parts of syllables. It can sound louder and be harder to understand. Raise in steps and judge by understanding, not by loudness.
- **B3.5** [I] *not re-checked*. A level that no longer follows the index is a full-level signal even when the owner has turned the volume down. That is acceptable only because the app plays to the selected aid and nowhere else, so that guard stays in front of any boost.

### 7.4 The order, if the full index is still too quiet

*Critique*, with the corrected first step of the boost report's skeptic. First make sure it is the ceiling and not the lever: the index reads back as the maximum, the stream is not muted, Do Not Disturb is off, and lower steps were audibly quieter. Then:

1. The chime's amplitude (B2.1).
2. Measure the speech: peak and RMS of what the engine synthesises (L5.2). If the peaks are well under full scale, normalising helps; if they are near it, only compression does.
3. The lead-in test of section 11: if the aid fades a stream in, a longer lead-in does more than gain.
4. A digital boost: own playback with a limiter a few dB under full scale, or a LoudnessEnhancer on the app's own session, in a few fixed steps.
5. The aid's side, which no app can reach (Ph1.3).

- **B4.1** [C] *confirmed*. One switch outside the app: Settings, Text-to-speech output, the gear next to Google's engine, "Amplify speech volume" (German "Ausgabelautstärke anheben"). Known from two user posts of 2023 and absent from Google's help page; not confirmed for this phone. Worth one try, nothing to build on.
  - https://forum.kurviger.de/t/fixed-increase-the-voice-volume-losung-sprachausgabe-verstarken/12229 , https://forum.myrouteapp.com/topic/9686/navigation-volume-too-low-the-solution-for-android , https://support.google.com/accessibility/android/answer/6006983?hl=en

---

## 8. The hearing aid's side

Sources are Phonak's own unless marked [C]. "KB" is the Phonak knowledge base under `https://www.phonak.com/en-us/support/knowledge-base/articles/`. Pages and threads were fetched on 2026-11-28. Nothing in this section is specific to the EON unless it says so.

### 8.1 What Phonak says, and what can be changed on the aid

- **Ph1.1** [V] *confirmed*. Phonak has no knowledge-base article about a stream that is too quiet; about 170 article titles were listed. What it does say:
  - The phone's volume keys are a control for calls: "Using the volume buttons on your mobile phone" and "increase the volume of the call using the buttons on the phone". KB `changing-the-volume-of-a-phone-call-with-phonak-hearing-aids`: https://www.phonak.com/en-us/support/knowledge-base/articles/changing-the-volume-of-a-phone-call-with-phonak-hearing-aids and KB `when-i-receive-a-call-on-my-android-phone--why-do-i-have-problems-hearing-the-caller-through-my-phonak--hearing-aids`: https://www.phonak.com/en-us/support/knowledge-base/articles/when-i-receive-a-call-on-my-android-phone--why-do-i-have-problems-hearing-the-caller-through-my-phonak--hearing-aids
  - For Auracast it says the opposite: "The volume of an Auracast broadcast stream is controlled at the transmitter and can not be adjusted via the volume buttons of your phone". https://www.phonak.com/en-us/support/knowledge-base/articles/why-are-my-volume-buttons-on-the-phone-not-working-when-streaming-auracast-broadcast-audio-to-my-hearing-aids . That implies, and does not state, that for ordinary Bluetooth streaming the phone's keys are the intended control.
  - The general article on "not loud enough or distorted" names a volume set too low, a low battery, a blocked speaker or wax filter, and changed hearing. https://www.phonak.com/en-us/support/knowledge-base/articles/why-is-the-sound-from-my-phonak-hearing-aids-not-loud-enough-or-distorted
  - Skeptic: the quotes are in the pages word for word; the Auracast article was fetched a second time.
- **Ph1.2** [V] *could not check* by this report's skeptic; verified in [`hearing-function.md`](hearing-function.md). On the EON the media streaming programs are called "Media speech + mic" and "Media music + mic" (H1.2). The wearer's control in myPhonak while a stream runs is the Ambient balance, in the German app "Umgebungsbalance" (H1.6). Adjustments made during a stream are lost after it unless saved as a program, and the balance is not kept (H3.2, H3.3). For an announcement of three seconds these controls are of no practical use.
  - myPhonak 7.5 user guide, pages 9 and 12: https://www.phonak.com/content/dam/celum/phonak/master-assets/en/documents/apps/myphonak/ph-user-guide-my-phonak-7.5-v13.00-en.pdf
- **Ph1.3** [V] that it exists, [C] for the detail; *could not check* by this report's skeptic. The lasting remedy on the aid's side is the fitter. The streaming programs can be tuned apart from the acoustic ones in Phonak's fitting software (H3.4). By forum posts, fitters and self-fitters change the loudness of a stream there under Fine tuning, "AutoSense OS (streaming)", with the gain handles of both media programs. One poster notes: "Phonak Target does allow you to adjust streaming gain but this is not the same as a separate media volume slider". One calls -6 dB the factory setting of the microphone attenuation.
  - Threads read for this: https://forum.hearingtracker.com/t/-/67572 , https://forum.hearingtracker.com/t/-/78993 , https://forum.hearingtracker.com/t/-/105645 , https://forum.hearingtracker.com/t/-/134894 . The skeptic found the two fitter quotes in the copies of the threads and did not read the guides again.
  - [U] Any default streaming gain, or a streaming offset of its own, for the EON. The EON needs Target 12, and no Target 12 guide is public.
- **Ph1.4** [P] *added by the skeptic*. The aid has a volume of its own for the voice link and pushes it to the phone. As a voice link opened on 2026-11-27 the event log shows the call volume going from 15 to 8 of 15, written by the Bluetooth app, just after the app's session journal had recorded 15 of 15. That is a second place where "too quiet" can arise, for the spoken dialogue of a session. It is separate from the announcements and not part of this build.

### 8.2 What wearers report about loudness

- **Ph2.1** [C] *confirmed*. With Phonak's Bluetooth Classic aids the usual complaint is the opposite: too loud at the lowest phone volume. Paradise P90, 2022: "the audio starts out too loud". Lumity L90, 2024: "adequate music volume with iPhone turned all the way down and it sounds overdriven, compressed, a little clipped" (https://forum.hearingtracker.com/t/-/88136). Infinio Sphere, 2025 and 2026: "very loud even when the phone volume is at the lowest level" (https://forum.hearingtracker.com/t/-/108638) and "Even on 0 it was louder than I'd want".
- **Ph2.2** [C] *confirmed*. The reports of "too quiet" each have a specific cause:
  - Surrounding noise, Lumity, 2024: "when environment noise increases ... music volume decreases"; and with the microphones muted "music volume remains still very low, the lower when external sound is higher". What helped: the balance control in myPhonak, switching NoiseBlock off in the fitting software for two wearers, and most of all switching the microphones off in the streaming programs. https://forum.hearingtracker.com/t/-/92874
  - Wind while cycling, Paradise P90: "the faster I peddled the quieter the hearing aids adjusted". https://forum.hearingtracker.com/t/-/58298
  - A recording through a Marvel, 2020: "the audio starts really compressed and washed out then suddenly gets irritably loud and clear", explained in the thread by the aid changing between its speech and its music streaming program. https://forum.hearingtracker.com/t/-/55257
  - Wearers confirm that they set the loudness of a stream with the phone: "I just adjust the volume up on my phone".
  - Skeptic: the quotes exist. Several thread numbers were swapped in the report; the links above are the skeptic's assignment, and a quote without a link was not assigned again. All of these reports are about long music streams. None is about a short prompt on Android 17, so they say nothing against a cause on the phone's side.
  - Limits of the search: reddit could not be reached, no wearer community run by Phonak was searched, and long forum threads were read on their first page only. Also read: https://forum.hearingtracker.com/t/-/51098
- **Ph2.3** [I] *confirmed*. In noise the aid itself holds a stream down, and no gain on the phone removes an effect that the aid produces from its own microphones. Reminders arrive while the owner is out and about. One question to him settles which case this is: is the announcement also much too quiet in a silent room, or mainly outdoors?

### 8.3 Fade-in and lead-in

- **Ph3.1** [U] *confirmed*. Whether the EON fades a stream in when it switches into its streaming program is unknown. Phonak publishes no time for the switch and no word about a ramp (H5.1), and no Sonova document or patent describing one for this product was found. It has to be heard.
- **Ph3.2** [C] *confirmed*. What wearers report, none of them about the EON. They describe a lost or faded beginning of 0.5 to 2 s and a switch of 2 to 3 s, not a measured loudness curve:
  - A Phonak ComPilot II streamer, 2018: "I never hear the first one or two seconds of the message. It seems like the phone starts sending the message, but the sound slowly fades in on my hearing aids"; the same from a ReSound and a Signia wearer in that thread. https://forum.hearingtracker.com/t/phonak-compilot-ii-issue-with-driving-directions/34763
  - An iPhone, 2026, the aid not named, answered by a Phonak wearer: "the first half second of the instructions from the GPS App are clipped off"; the answer: "Whenever the Phonak switches programs, there is a delay." https://forum.hearingtracker.com/t/-/113103
  - Paradise, 2022: "any tone, no matter how short, will switch the HA to streaming mode for about 2-3 sec". https://forum.hearingtracker.com/t/phonak-target-7-3-1-volume-behavior/68448?page=2
  - Lumity, 2023: the aid goes into the streaming program "for a second or two" at each phone sound. https://forum.hearingtracker.com/t/-/77842
  - Another kind of Bluetooth hearing aid (ASHA) and two headsets: "the initial 500-1000ms are eaten up before connection resumes". https://github.com/osmandapp/OsmAnd/issues/19906 (not read a second time by the skeptic)
- **Ph3.3** [V] *added by the skeptic*. The phone's side has no fade: the mixer applies no ramp to the first buffer of a track (`Threads.cpp`, `prepareTracks_l`: "no ramp for the first volume setting"). What the Pixel's closed audio layer does at the start of a stream is not known.
- **Ph3.4** [I] *confirmed*. Today's whole announcement lies inside the 2 to 3 s that Paradise wearers give for the switch: the first word comes about 1.1 s after the stream opens and everything is over about 2.6 s later (T1.5). A fade-in, if the EON has one, would cover the entire announcement. The two streaming programs can also differ in loudness (the Marvel recording in Ph2.2), and a tone followed by speech may be sorted differently from a long stream of speech.
- **Ph3.5** [C] with [V] *confirmed*. What others do about a clipped beginning. A navigation app offers a delay and its developer proposes 1500 ms. The wearer of the ASHA aids found that a delay without sound does not help ("it also just shifts the cutoff point") and uses "sounds that are long enough or start with a long enough silence" instead. [V] That app's delay is built on `TextToSpeech.playSilentUtterance`, and that sends no audio at all: `SilencePlaybackQueueItem.run()` only waits (lines 32-44). It cannot serve as a lead-in. The announcer plays a real track of zeros, which does open the stream (A1.e in [`hearing-function.md`](hearing-function.md)).
- **Ph3.6** [U] *confirmed*. Whether the EON starts its switch at the start of the stream or only at the first sound in it. The app's silence test exists for that question (J9 in [`hearing-function.md`](hearing-function.md)). If the aid reacts only to sound, a lead-in would have to carry a very quiet signal instead of zeros.
- **Ph3.7** [I] *not re-checked*. A longer lead-in is not free: it keeps the aid in its streaming program for longer, which is the cost [`hearing-function.md`](hearing-function.md) is about. Whether it is worth that is the owner's to decide after the test.

---

## 9. What the app does with this

This section names the decisions, so that the document matches the code. How each is done is in the code and in [`../../CLAUDE.md`](../../CLAUDE.md).

1. **Announcements play in the media volume group by default.** Chime and speech carry `USAGE_MEDIA`. The assistant channel (`USAGE_ASSISTANT`, as before) stays as a fallback setting.
2. **The slider sets the phone's media volume for the aid.** That is Android's own value: the one the volume keys change (K1.3) and the Bluetooth stack remembers for the aid (M1.2).
3. **Optionally a step of the app's own.** The app sets it for the length of an announcement and puts the former value back. Section 4.2 lists what that has to survive.
4. **The app never writes the assistant stream.**
5. **`MANAGE_ASSISTANT_AUDIO` is declared in the manifest only to learn whether the phone grants it** to a sideloaded holder of the assistant role (S4.3). Nothing depends on it.

**What the critique recommended, and why the choice went the other way.** The design critique advised keeping the assistant usage and writing the assistant stream through the role permission. Its reasons: no other app's audio is touched, the Bluetooth stack keeps no memory per device for that stream, and a missed restore is harmless by comparison. It put the media variant under "not now", for the hazards of section 4.2 and for the change of route (S1.3). The decision went to media for these reasons:

- The role permission is untested for a sideloaded assistant (S4.3). Without it the app could set nothing at all.
- The media volume is certain to be settable: any app may set it without a permission (S1.1), and whether a call was accepted can be read at once (S1.4).
- The aid does not rewrite it: the mechanism of L4.1 is about the assistant stream only.
- The owner can change it with the volume keys from his pocket while an announcement plays (K1.3). With the assistant usage he cannot (K1.2).

**What the choice costs, knowingly.**

- The media volume for the aid is the level of every app's media in the aid. The slider changes it for good; that is what it is for. The own step changes it for seconds, and can leave it raised after a process death or a lost connection (M2.2, M2.3).
- With the own step, other media playing in the aid would be lifted for the length of the announcement (M2.5). The rule against that: the app leaves the volume alone while other media play.
- The announcement follows the media route. With "This phone" chosen in the output switcher it is skipped (S1.3).
- The ceiling stays where it was: 0 dB at the full index (B1.2). Nothing here boosts.
- If the aid's device type is ever set to "Headphones", the announcements count toward the sound dose (S5.5).
- [`hearing-function.md`](hearing-function.md) said that no code of the app sets a volume, and carries a correction since 28 November 2026. The sentence described commit `cfaccde`. From this build on the app sets one, the phone's media volume, and still nothing on the aid.

**What holds whatever the setting.** Do Not Disturb that mutes media silences the announcement (S2.2). On the fallback channel the loudness is Android's assistant volume as it stands (section 2.4): the app cannot change it, and the keys reach it only with the display on (K1.1).

The owner also asked for a button that simulates the next appointment announcement ("ein Testbutton um die nächste Eventansage zu simulieren"). The checks of section 11 are written to be triggered from the app's screen.

---

## 10. Where a skeptic corrected a report

Each line is the corrected statement in short, with the place where it stands in full. The confirmed statements are not repeated here.

**The volume path** (10 of 17 claims confirmed):

1. Whether this phone ties the assistant volume to media is not unknown: the captures show it separate, with a maximum of 15 and the default 5. L1.4.
2. On this phone the media index has no effect on a sound played with the assistant usage, not even as an upper limit. L2.4.
3. A media volume call acts on the device of the media strategy, which can differ from the route of the announcement. S1.2, S1.3.
4. The Bluetooth stack remembers the media volume per aid also without absolute volume. M1.2.
5. The Pixel's media range is not 15 steps. L3.4.
6. The media stream is the control for the media volume group only. It governs announcements only if they are played with a usage of that group. L2.4, L1.6.
7. The aid does not use absolute volume. L2.1.
8. Nothing is sent to the aid when the media volume is set. L2.5.
9. Not checkable from outside: whether the phone grants the role permission to a sideloaded holder. S4.3.

**Limits and memory** (18 of 23 claims confirmed):

10. The volume keys reach an assistant-usage announcement only when the key event is passed to a window. In the pocket they do nothing for it; for a track of the media group they would. K1.1 to K1.3.
11. The values 5, 13 and 8 are the index of the voice link's device type. The copy for the media channel exists in AudioService's map only; by the source it is not applied and not stored. L4.2, L4.3.
12. Writing the assistant index once and leaving it is not free of problems: it has three of its own. S4.4.
13. Not checkable from outside: that the Pixel governs the app's announcement track by the assistant index (L1.5), and that its audio HAL adds no gain of its own (L2.6).

**The hearing aid and boost** (8 of 16 claims confirmed):

14. What sets the loudness of an announcement on the phone's side is the assistant volume and the level of the samples, not the media index. L1.1, L1.2, L1.4.
15. Memory per device and the hand-back at every connection hold for the media stream only. The assistant stream has one index per device type and a mechanism that sets it low again. M1.4, L4.1.
16. The attenuation has to be computed with the assistant index: 28.5 dB at 5, 18.5 dB at 8. And the Pixel's curve table is public in a third-party mirror. L3.2, L3.3.
17. LoudnessEnhancer does not reach full scale; its compressor holds a loud signal between about -9 and -5 dBFS. B2.2.
18. DynamicsProcessing follows the index but can offset it. B2.4.
19. The ceiling of the app's own playback is the index of the stream its usage belongs to. B1.2, B1.3.
20. Any order of remedies starts with finding out which volume the announcement is under; a gain setting capped at 0 dB would have appeared to do nothing. Section 7.4.
21. Not read a second time by this skeptic: the myPhonak guide and the fitting material. Ph1.2, Ph1.3.

**Precisions inside confirmed statements:**

22. Several forum thread numbers were swapped. Ph2.2.
23. `announce_speaking` is the engine's start callback, not the first audible sample. T1.5.
24. A software effect is rejected on a session with a low-latency track. B2.3.
25. The mixer applies no ramp to the first buffer of a track, so the phone itself does not fade in. Ph3.3.

**Between the reports:**

26. Two reports computed the attenuation with two different curves. Both sets are in L3.3; the vendor file points to the first.
27. One skeptic named the safe-volume rules as a cost of moving to the media usage. The limits report, confirmed by its own skeptic, shows that the index-based check has no safe index for the Bluetooth media channel and is switched off on this phone anyway. What remains is the sound dose of S5.2 and S5.5.

---

## 11. Open device checks

No phone and no aid were at hand for this report. Nothing below has been done. The checks name the calls to read; which line of the app's journal carries them is the code's business.

### 11.1 Read by the app, on its screen or in its journal

1. **Which stream governs each channel on this build.** `getVolumeControlStream()` of the media attributes (expected 3) and of the assistant attributes (expected 11); and `isMusicActive()` about half a second into an announcement with nothing else playing (expected true on the media channel, false on the fallback channel). If the fallback channel answers 3 and true, this build ties the two after all and section 2.4 does not apply to it. Settles L1.5.
2. **Range and values with the aid as the output.** Maximum and index of the media stream (a maximum of 25 is expected from the stored values only) and of stream 11 (expected 15; the index read 5, 13 and 8 on 2026-11-27).
3. **The phone's real curves.** `getStreamVolumeDb` for every step of both streams (L3.5). Expected for media if the build uses AOSP's curve: -17 dB at 15 of 25. Does the call throw for stream 11?
4. **Is a media set accepted in every situation.** From the screen; at the hourly announcement with the display off; at a calendar reminder; with the phone in Doze. The value read back must be the wanted one. After a refusal logcat has "AudioHardening volume control for api" (S2.1).
5. **Is the role permission granted.** At the first start of the build that declares it, after a minute, after a reboot (`checkSelfPermission`). Only if it still is not: after choosing another assistant and then Lara again. Do that last, because it touches the role, and look at the tap's "Always" choice afterwards. Settles S4.3.
6. **Does an announcement with the media usage play from the pocket at all:** locked, display off, in Doze, as the one with the assistant usage did on 2026-11-27. [I] By the source it should: the background playback rule looks at the usage only for system usages and alarms ([`verify-audio-hardening.md`](verify-audio-hardening.md), claim 1).
7. **Route.** With "This phone" chosen in the output switcher: the announcement is skipped and journaled, and nothing comes from the speaker (S1.3).
8. **Do Not Disturb** with media not allowed, and total silence: is `isStreamMute` true, is the announcement silent, is a set dropped (S2.2)?
9. **The own step, when things go wrong.** Android's value before and after an announcement. Then: the app killed in the middle of one, and the next announcement. Then: the aid switched off in the middle of one. Which value does the aid get at the reconnection, what does a second read a second later say (M2.4), and what happened to the speaker's media volume (M2.3)?
10. **Who rewrites stream 11,** for the fallback channel: after a voice session with the aid, and after any use of Google's assistant, did `getStreamVolume(11)` change (L4.1, L4.5)?
11. **The level of the speech engine:** peak and RMS of a synthesised phrase (L5.2).

### 11.2 By ear

12. **The slider is the lever.** The same phrase at several media steps, for example 10, 15, 20 and 25 of 25. Each must be clearly louder than the one before.
13. **Keys from the pocket.** Display off, and volume-up pressed twice while an announcement plays: has the media value changed, and is the next announcement louder (K1.3)? With the display on: which slider does the Pixel show?
14. **The full step.** Loud enough in a quiet room? Outdoors, in traffic? This decides whether section 7 is needed at all, and answers the question of Ph2.3.
15. **Chime against speech** at the full step. The chime is 12 dB under full scale by the app's own constant (L5.1).
16. **The restore of the own step.** Is the last word as loud as the rest (T1.4)?
17. **Other media.** A podcast playing in the aid during an announcement with the own step: does the podcast keep its level, as it should when the app leaves the volume alone then (M2.5)?
18. **Fade-in of the aid.** One long announcement, counting from 1 to 20, at a fixed step: does it get louder after the first seconds? Then the same short phrase with a lead-in of 0.45 s, 1.5 s and 3 s (Ph3.1, Ph3.4).
19. **Default against fallback.** The same phrase on both channels, to hear what the assistant volume does today.
20. **Audio balance.** Settings, Accessibility: is the slider in the middle (L5.3)?
21. **The aid's device type** in the phone's Bluetooth settings: not "Headphones" (S5.5).
22. **Once:** the speech engine's own switch (B4.1).

### 11.3 Once, over Wi-Fi debugging

These settle the unknowns of sections 2 and 3 without the ear. None was run.

- `adb shell dumpsys audio`: the flag line `streamAssistantNotAliasedToMusic`; the block "Stream volumes" (a tie would show as "aliased to"); `mAvrcpAbsVolSupported`; the block "## SoundDoseHelper" with `mEnableCsd`; the lines "AudioHardening".
- `adb shell dumpsys media.audio_policy`: the strategy table (L1.5) and, for the volume group of the assistant strategy, "Index Cur" for device `0080`, to hold against the map in `dumpsys audio` (L4.4).
- `adb shell settings list system | grep volume_`: the stored values of M1.1.
- `adb shell dumpsys package io.github.domicc.lara | grep MANAGE_ASSISTANT_AUDIO` (S4.3).
- `adb shell getprop bluetooth.avrcp.target.safe_media_volume.config` (S5.6).
- `adb shell dumpsys media.audio_flinger`, only if an effect is ever tried: its chain must appear under the output thread of the aid's device (B2.3).
- The dump of the Bluetooth service shows the absolute-volume state and the stored volumes (L2.1, M1.2). It contains addresses and device names: it stays out of this repository.

---

## 12. How the sources were read

- **Android source:** tag `android-17.0.0_r1`, fetched from android.googlesource.com by each report and again by the skeptics. The skeptic of the volume path compared 19 files byte for byte and read these from earlier copies only: `Volume.h`, `VolumeCurve.cpp`, the native `AudioTrack.cpp`, `A2dpService.java`, `AvrcpTargetService.java`, `connection_handler.cc`, the app-ops files, `roles.xml`, the core manifest, the policy engine and the system UI. The limits report read the two media-session files of section 6 at the head of the branch `android17-release`; the critique gives its lines for the tag. Flag values are those of AOSP's release configuration `cp2a`.
- **What the tag cannot tell:** the phone runs build `CP3A.261005.005`, a later quarterly release without public source. The copying of the assistant index between device types (L4.2), the rule about a set to the same value (L4.4), the handling of the keys (section 6) and the role permission (S4.3) are the parts most likely to differ. The log strings in the captures match the tag.
- **The Pixel:** no device tree, no audio policy configuration and no audio HAL of the Pixel 10 is public. Overlay facts come from the Pixel 9 at `android-15.0.0_r1`. The vendor volume table comes from a third-party mirror of the phone's vendor image, not from Google.
- **developer.android.com:** the Android 17 features page, the page on background audio and the `AudioManager` reference, fetched on 2026-11-28.
- **The AOSP history pages** need a sign-in; the Phonak commit was read through a public mirror with the same hash, and by the skeptic at android.googlesource.com.
- **The phone:** captures of 2026-11-27, read on 2026-11-28. Nothing was run on the phone and adb was not used.
- **Phonak:** knowledge-base pages fetched on 2026-11-28; the Auracast article a second time by the skeptic. The myPhonak guide and the fitting material were taken from [`hearing-function.md`](hearing-function.md), where they were read and re-checked.
- **Forum threads** (forum.hearingtracker.com): fetched on 2026-11-28, long threads on their first page only. The skeptic fetched threads 92874, 108638, 34763 and 113103 again. The issue in the OsmAnd tracker and the two posts about the speech engine's switch were read once. reddit could not be reached.
- **The app:** commit `ad4c26d` (build 21). The measurement notes ([`../measurements/2026-11-27.md`](../measurements/2026-11-27.md)) and the earlier reports ([`bt-audio.md`](bt-audio.md), [`verify-audio-hardening.md`](verify-audio-hardening.md), [`hearing-function.md`](hearing-function.md)) were used as leads.
- **The design critique** was written after the skeptics, from the six results and the app's code. Nobody re-checked it. Its recommendation was not followed; section 9 says why.
- **Not public:** the source of the phone's build; a Pixel 10 device tree; the Pixel's audio HAL; a Target 12 guide; Google's speech engine.
