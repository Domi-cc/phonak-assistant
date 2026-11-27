# Path C: Bluetooth media buttons as a trigger while the phone is locked with the screen off

Research date: 2026-11-27. Target: Pixel 10 Pro, Android 16 QPR / Android 17, sideloaded APK.
Nothing in this file has been run on a device. Every statement is tagged
**[V]** verified in a primary source (URL or file + quote/line), **[I]** inferred from sources, **[U]** unknown.

AOSP sources were fetched from `android.googlesource.com` (`?format=TEXT`), branches
`android17-release` and `android16-qpr2-release` (plus `android13/14/15-release` for spot checks).
Local copies: `<research>/aosp/<branch>/`.
Line numbers below refer to the `android17-release` copies unless noted.
URL pattern: `https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android17-release/<path>`.

---

## 0. Short answer

1. **[V] Android has exactly one "media button session" per user.** It is the session of *the app whose UID most
   recently started local audio playback and owns a MediaSession* (`MediaSessionStack.updateMediaButtonSessionIfNeeded`).
   Playback state and audio focus do not decide between apps. With the screen off, media keys are still routed
   and do **not** wake the display.
2. **[V] Non-media HID keys (arrows, enter, etc.) from an external Bluetooth keyboard/remote wake the screen** and are not
   delivered to any app while the device is non-interactive (`KeyboardInputMapper.cpp`, `PhoneWindowManager`).
   A HID "presentation/keyboard mode" remote therefore violates the display-off requirement unless a shell-uid
   process grabs the evdev node (Key Mapper "Expert Mode" technique).
3. **[V] There is an adb-grantable way to see every media key first without being the media-button session:**
   `android.permission.SET_MEDIA_KEY_LISTENER` has `protectionLevel="signature|privileged|development"`
   (Android 13, 14, 16 QPR2, 17 manifests), so `adb shell pm grant <pkg> android.permission.SET_MEDIA_KEY_LISTENER`
   works for a sideloaded app that declares it. `MediaSessionManager.setOnMediaKeyListener()` (hidden `@SystemApi`,
   flagged `sdk,system-api,test-api` in the Android 16 hidden-API list, i.e. not blocked) then receives each media key
   *before any session*; returning `false` (or not answering within 1000 ms, or dying) lets the system route the key
   normally. An open-source app (MIT) already ships exactly this.
   This is the only design that is both always available and does not hijack play/pause.
4. **[V] A Media3 `MediaSessionService` cannot "remain" the target.** Media3 1.11.1 (current stable; 1.12.0-rc01 exists)
   leaves the foreground after max. 10 minutes without playback, Android 16+ itself demotes a `mediaPlayback`
   FGS 10 minutes after the session stops playing, and any other session-owning app that starts playback takes the
   media-button role. Keeping the role with silent audio is the classic hack; it would keep the hearing aid in
   streaming mode [I] and hijacks "resume my podcast".
5. **[V]/[U] Phonak:** the official user guide lists only "HFP (Hands-free profile), A2DP" as supported profiles, while
   Tap Control offers "pause/resume streaming". Whether that reaches Android as AVRCP PLAY/PAUSE is **unknown**;
   a one-minute device test (section 4.3) settles it.
6. **[V] Android 17 "background audio hardening"** is a new platform restriction that affects the whole project (TTS
   playback, focus requests), not only this path: background audio needs a foreground service, and for
   targetSdk 37 one with while-in-use capability. Media key events through a session and being the
   `VoiceInteractionService` provider are documented exemptions.

---

## 1. How Android routes media keys (API 33 to 37)

### 1.1 Two entry points, one dispatcher

| Source | Path into the framework | Evidence |
|---|---|---|
| **HID consumer control** (BLE HOGP or classic HID media remote, wired headset) | kernel evdev -> `InputReader` (`KeyboardInputMapper`) -> `InputDispatcher` -> `PhoneWindowManager.interceptKeyBeforeQueueing` -> `MediaSessionLegacyHelper.sendMediaButtonEvent(event, true)` -> `MediaSessionManager.dispatchMediaKeyEventAsSystemService` -> `MediaSessionService.dispatchMediaKeyEvent(asSystemService=true, needWakeLock=true)` | [V] `PhoneWindowManager.java` 4753-4783, 5141-5188 |
| **AVRCP passthrough** (headset, car kit, hearing aid if it implements AVRCP CT) | Bluetooth app `AvrcpTargetService.sendMediaKeyEvent(device, key, pushed)` -> `mAudioManager.dispatchMediaKeyEvent(event)` -> `MediaSessionService.dispatchMediaKeyEvent(asSystemService=false, needWakeLock=false)` | [V] `packages/modules/Bluetooth/android/app/src/com/android/bluetooth/avrcp/AvrcpTargetService.java` 480-552 |
| **LE Audio MCP** (Media Control Profile) | `audio_util/MediaPlayerList.sendMediaKeyEvent` -> `mMediaSessionManager.dispatchMediaKeyEvent(event, false)`; comment: "This is used only by MCP, AVRCP uses AvrcpTargetService." | [V] `.../audio_util/MediaPlayerList.java` 290-303 |
| **adb** | `adb shell cmd media_session dispatch play|pause|play-pause|headsethook|stop|next|previous|rewind|record|fast-forward` -> `mSessionService.dispatchMediaKeyEvent(pkg, asSystemService=false, event, needWakeLock=false)` | [V] `MediaShellCommand.java` 110-137, 170-200 |

AVRCP-specific filters in the Bluetooth app [V] (`AvrcpTargetService.sendMediaKeyEvent`):

- PLAY and STOP are **dropped while a `USAGE_VOICE_COMMUNICATION` playback is active**
  ("Received ... event while call is active, not sending it to AudioManager"). If the assistant dialog runs over
  SCO/HFP with that usage, AVRCP PLAY/STOP are dead during the dialog; PAUSE/NEXT/PREVIOUS still pass.
- Keys are dropped if the A2DP connection policy of that device is forbidden ("Ignore passthrough key as media profiles disabled").
- AVRCP PLAY may be sent "directly to addressed player" (`addressedPlayer.playCurrent()`) when the addressed player differs from the active one.
- AVRCP key events are created as `new KeyEvent(action, keyCode)`: no repeat count, no `FLAG_LONG_PRESS`, `downTime` 0.
  Long-press detection in `MediaSessionService` therefore never fires for AVRCP keys. [V] code, [V] the
  like-current-song project notes "Bluetooth AVRCP keys arrive with `downTime` 0".
- Every AVRCP key is logged in a ring buffer titled **"BTAudio Media Key Events"** (size 20), visible in
  `adb shell dumpsys bluetooth_manager`. [V] `AvrcpTargetService.java` 66-69, 486-498.

### 1.2 Order of handling inside `MediaSessionService.dispatchMediaKeyEvent` [V]

`services/core/java/com/android/server/media/MediaSessionService.java` 1980-2041:

1. Not a media session key (`KeyEvent.isMediaSessionKey`: PLAY, PAUSE, PLAY_PAUSE, HEADSETHOOK, STOP, NEXT, PREVIOUS, REWIND, RECORD, FAST_FORWARD) -> ignored.
2. User setup not complete -> ignored.
3. **Global priority session active** (Telecom during ringing/calls, `FLAG_EXCLUSIVE_GLOBAL_PRIORITY`) -> it gets every key; nothing else does.
4. Otherwise, **if an `OnMediaKeyListener` is registered** (`mCurrentFullUserRecord.mOnMediaKeyListener != null`) the key goes
   to that listener first with a `MediaKeyListenerResultReceiver`. `RESULT_MEDIA_KEY_HANDLED` consumes the key; any
   other result, or `MEDIA_KEY_LISTENER_TIMEOUT = 1000` ms without a reply, continues normal dispatch with the
   *original caller identity* (lines 125, 2998-3051).
5. `KeyEventHandler.handleMediaKeyEventLocked`: long-press / multi-tap tracking for the "voice keys"
   (`isVoiceKey`: `KEYCODE_HEADSETHOOK`, or `KEYCODE_MEDIA_PLAY_PAUSE` on non-leanback devices, line 2967-2970).
   A first long press (`FLAG_LONG_PRESS` and `repeatCount == 1`) of a voice key calls `startVoiceInput()`, which starts
   an activity with `RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE` (plus `EXTRA_SECURE` when locked) when the device
   is locked or the screen is off (2929-2965, 3352-3357).
6. `dispatchMediaKeyEventLocked` (2840-2927):
   - `session = getMediaButtonSessionLocked()`; if non-null -> `session.sendMediaButton(...)` and (if `needWakeLock`)
     a `PARTIAL_WAKE_LOCK` "handleMediaEvent" is held until the session acknowledges or `WAKELOCK_TIMEOUT = 5000` ms.
   - else if `mLastMediaButtonReceiverHolder != null` -> `MediaButtonReceiverHolder.send(...)`.
   - else **nothing happens** (the key is silently dropped).

### 1.3 Which session is "the media button session" [V]

`MediaSessionStack.java` 190-229 (identical in android13-release, android16-qpr2-release and android17-release):

```java
 * <p>The media button session is the session that will receive the media button events.
 * <p>We send the media button events to the lastly played app. If the app has the media
 * session, the session will receive the media button events.
public void updateMediaButtonSessionIfNeeded() {
    List<Integer> audioPlaybackUids = mAudioPlayerStateMonitor.getSortedAudioPlaybackClientUids();
    for (int i = 0; i < audioPlaybackUids.size(); i++) {
        int audioPlaybackUid = audioPlaybackUids.get(i);
        MediaSessionRecordImpl mediaButtonSession = findMediaButtonSession(audioPlaybackUid);
        if (mediaButtonSession == null) {
            // Ignore if the lastly played app isn't a media app (i.e. has no media session)
            continue;
        }
        ...
        if (!ignoreButtonSession) {
            mAudioPlayerStateMonitor.cleanUpAudioPlaybackUids(mediaButtonSession.getUid());
            if (mediaButtonSession != mMediaButtonSession) { updateMediaButtonSession(mediaButtonSession); }
            return;
        }
    }
}
```

Facts that follow directly from the code:

- **[V] The key is the UID of the audio player**, tracked by `AudioPlayerStateMonitor` through
  `AudioManager.registerAudioPlaybackCallback`. Sorting: "The UID whose audio is currently playing comes first, then the UID
  whose audio playback becomes active at the last comes next" (`AudioPlayerStateMonitor.java` 183-196, 287-342).
- **[V] Re-evaluation happens** on every audio player active-state change (`MediaSessionService.java` 284-302; `SoundPool`
  players, `PLAYER_TYPE_JAM_SOUNDPOOL`, are ignored) and when a session is added (`addSession`: "This can happen if an app
  starts audio playback before creating media session").
- **[V] Usages that do not count** (when flag `com.android.media.mediasession.flags.filter_session_audio_playback_by_usage`
  is enabled): `USAGE_NOTIFICATION`, `USAGE_NOTIFICATION_RINGTONE`, `USAGE_ALARM`, `USAGE_NOTIFICATION_EVENT`
  (`MediaSessionService.java` 148-158, 279-283). `USAGE_MEDIA`, `USAGE_ASSISTANT`, `USAGE_ASSISTANCE_ACCESSIBILITY`,
  `USAGE_VOICE_COMMUNICATION` count. Whether the flag is on in the Pixel build: **[U]** (check with `adb shell aflags list | grep -i filter_session_audio`).
- **[V] An app without a MediaSession is skipped**, and skipping does not disturb the previous owner. An app that
  plays audio but owns no session never takes the media buttons.
- **[V] Taking over evicts the previous owner from the history**: `cleanUpAudioPlaybackUids` removes inactive UIDs that
  played before the new owner (`AudioPlayerStateMonitor.java` 230-256). After app X took the role, app Y gets it back
  only by *starting playback again*; nothing hands it back automatically.
- **[V] When the owning session is released or its process dies** the role becomes `null` and the system deliberately does
  not look for another session (`removeSession`, lines 103-117). Keys then go to the remembered receiver (1.4).
- **[V] Playback state is only a tie-breaker inside one app**: "In most cases, playback state isn't needed for finding
  media button session, but we only use it as a hint if an app has multiple local media sessions" (162-165).
  `findMediaButtonSession` does not check `isActive()`.
- **[V] Audio focus is not consulted** anywhere in `MediaSessionStack`/`AudioPlayerStateMonitor`. [I] Focus matters only
  indirectly: if another app pauses on transient focus loss and auto-resumes afterwards, its resumed player is a "new active
  playback" and it gets the role back; if it only ducks, it stays active and moves to the front when the assistant stops.
- **[V] `TextToSpeech.speak()` audio is not "our" playback.** The `AudioTrack` is created inside the TTS engine's service
  process (`android.speech.tts.BlockingAudioTrack`, line 226: `new AudioTrack(mAudioParams.mAudioAttributes, ...)`, driven by
  `TextToSpeechService.mAudioPlaybackHandler`). [I] The playback UID is the engine's, so speaking through the platform TTS
  engine never makes our session the media-button session; only audio played from our own process does.
- Public documentation says the same in prose [V]: "If you are running Android 8.0 (API level 26) or later, the system tries to
  find the last app with a MediaSession that played audio locally. If the session is still active Android sends the event
  directly to it. Otherwise, if the session is not active and it has a mediabutton receiver, Android sends the event to the
  receiver, which will restart the session ... If the session does not have a media button receiver the system discards the
  media button event and nothing will happen." (https://developer.android.com/media/legacy/media-buttons)

Public API to observe the current target [V] (flag `public-api` in the Android 16 hidden-API list; needs an enabled notification
listener or `MEDIA_CONTENT_CONTROL`, `enforceMediaPermissions`): `MediaSessionManager.getMediaKeyEventSession()`,
`getMediaKeyEventSessionPackageName()`, `addOnMediaKeyEventSessionChangedListener()`.

### 1.4 Dead process, reboot, playback resumption [V]

- When a session becomes the media-button session its receiver is remembered and persisted:
  `Settings.Secure "media_button_receiver"` = `component,userId,componentType`
  (`MediaSessionService.java` 128-130, 1472-1485; `MediaButtonReceiverHolder.flattenToString`). It is restored at boot:
  "Restore the remembered media button receiver before the boot" (1361-1366).
- `MediaButtonReceiverHolder.send()` (198-275): builds `Intent.ACTION_MEDIA_BUTTON` with `FLAG_RECEIVER_FOREGROUND` and
  `EXTRA_KEY_EVENT`; uses the live `PendingIntent` if it still exists, otherwise the restored component:
  activity -> `startActivityAsUser`, service -> `startForegroundService`, else `sendBroadcastAsUser`.
  *All* media session keys are forwarded, not only PLAY.
- Before sending (both to a live session and to a receiver) the target package is temporarily allow-listed
  (`tempAllowlistTargetPkgIfPossible`, 891-929): FGS start from background for
  `DEFAULT_MEDIA_SESSION_CALLBACK_FGS_ALLOWLIST_DURATION_MS = 10_000` and while-in-use permissions in that FGS for
  `DEFAULT_MEDIA_SESSION_CALLBACK_FGS_WHILE_IN_USE_TEMP_ALLOW_DURATION_MS = 10_000` (`MediaSessionDeviceConfig.java` 32-48),
  *if the caller itself is allowed to* (`canAllowWhileInUsePermissionInFgs` / `canStartForegroundService`). Log line to look
  for: `tempAllowlistTargetPkgIfPossible callingPackage:... targetPackage:... reason:... [WIU] [FGS]`.
  Whether the Bluetooth app as AVRCP caller qualifies for `[WIU]` on the Pixel: **[U]** (read the log line).
- API details [V] (`MediaSession.java` 364-396): `setMediaButtonReceiver(PendingIntent)` is deprecated ("@throws
  IllegalArgumentException if the pending intent targets an activity on apps targeting Android V and above");
  `setMediaButtonBroadcastReceiver(ComponentName)` must name a manifest-declared receiver of the same package.
- No session and no remembered receiver -> key dropped.
- A force-stopped app receives nothing [I] (standard broadcast rule).

### 1.5 Screen off: what wakes, what is delivered [V]

`frameworks/native/services/inputflinger/reader/mapper/KeyboardInputMapper.cpp` (android17-release 351-361, same in android16-qpr2-release 347-355):

```cpp
// Any key down on an external keyboard or internal alphanumeric keyboard should wake the
// device. We don't do this for non-alphanumeric internal keyboards to prevent them from
// waking up in your pocket.
// For internal keyboards and devices for which the default wake behavior is explicitly
// prevented (e.g. TV remotes), the key layout file should specify the policy flags for each
// wake key individually.
if (down && !mParameters.doNotWakeByDefault &&
    (getDeviceContext().isExternal() || wakeOnAlphabeticKeyboard(keyboardType)) &&
    !(keyboardType != KeyboardType::ALPHABETIC && isMediaKey(keyCode))) {
    policyFlags |= POLICY_FLAG_WAKE;
}
```

`isMediaKey` (66-90): MEDIA_PLAY, MEDIA_PAUSE, MEDIA_PLAY_PAUSE, MUTE, HEADSETHOOK, MEDIA_STOP, MEDIA_NEXT, MEDIA_PREVIOUS,
MEDIA_REWIND, MEDIA_RECORD, MEDIA_FAST_FORWARD, MEDIA_SKIP_FORWARD/BACKWARD, MEDIA_STEP_FORWARD/BACKWARD, MEDIA_AUDIO_TRACK,
VOLUME_UP, VOLUME_DOWN, VOLUME_MUTE, TV_AUDIO_DESCRIPTION*.

Consequences:

- **[V] Media and volume keys from an external, non-alphabetic device do not get the wake flag.** In
  `PhoneWindowManager.interceptKeyBeforeQueueing` (4443-4479) a non-interactive, non-injected key gets `result = 0`
  (not passed to the user); the media-key case (4753-4783) then takes `mBroadcastWakeLock` and posts
  `MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK`. While the key is held, a synthetic repeat with `FLAG_LONG_PRESS` is sent after
  `ViewConfiguration.getKeyRepeatTimeout()` (5158-5182), so HID long presses are detectable, unlike AVRCP.
- **[V] Every other key from an external device gets `POLICY_FLAG_WAKE`.** With the display off the key is not delivered
  ("When the screen is off and the key is not injected, determine whether to wake the device but don't pass the key to the
  application", 4468-4479) and `wakeUpFromWakeKey(event)` runs (4894-4896). DPAD keys additionally depend on
  `mWakeOnDpadKeyPress`, BACK on `mWakeOnBackKeyPress`, ASSIST on `mWakeOnAssistKeyPress` (`isWakeKeyWhenScreenOff`, 5020-5043);
  the Pixel overlay values are **[U]**.
- **[V] Caveat: if the remote is classified as an ALPHABETIC keyboard, its media keys wake too** (the `!(... != ALPHABETIC && isMediaKey)`
  term). Classification (`frameworks/native/libs/input/rust/keyboard_classifier.rs`): a device with Keyboard and AlphabeticKey
  classes starts as Alphabetic; a NonAlphabetic device becomes Alphabetic permanently once it sends an alphabetic key.
  Combo remotes with a "keyboard/presentation mode" are at risk; a pure consumer-control remote is not.
- **[V] `KEYCODE_VOICE_ASSIST`** (HID "Voice Command") is handled in `PhoneWindowManager` (4816-4827, 5190-5206): on key up it starts
  `ACTION_VOICE_SEARCH_HANDS_FREE` with `EXTRA_SECURE=true` when the keyguard is on and calls `DeviceIdleManager.endIdle("voice-search")`.
  It is not in `isMediaKey`, so from an external device it also wakes the screen [I].
- **[V] Accessibility services do not see screen-off keys**: `AccessibilityInputFilter.onInputEvent` returns early when
  `(policyFlags & WindowManagerPolicy.FLAG_PASS_TO_USER) == 0` ("Not processing event", line 399-405).
- [I] A connected BT HID link wakes the SoC from suspend for an incoming report (input fds are wake-capable), so Doze does not
  block delivery. Many remotes drop the link when idle and reconnect on the first press; whether that first press is
  delivered, delayed or lost is **[U]** per remote (test T6).

---

## 2. What a Media3 `MediaSessionService` must do, and what breaks it

**Version [V]:** `androidx.media3:media3-session` latest stable **1.11.1** (GitHub release 2026-09-11; Google Maven
`maven-metadata.xml`), release candidate 1.12.0-rc01 (2026-11-25). Source read at tag `1.11.1`
(`<repos>/androidx-media/libraries/session/src/main/java/androidx/media3/session/`).

### 2.1 Requirements to *be* the target

| Aspect | Requirement | Evidence |
|---|---|---|
| Session | A platform session must exist. Media3's `MediaSession` wraps a `MediaSessionCompat`/platform session (`MediaSessionLegacyStub`). | [V] `MediaSessionLegacyStub.java` 252-262 |
| Audio | Our own UID must be the most recent one that started non-ignored local playback (1.3). Platform TTS does not count. | [V]/[I] |
| Raw key access | `MediaSession.Callback.onMediaButtonEvent(session, controllerInfo, intent)`: return `true` to consume the raw `KeyEvent`. Otherwise Media3 maps keys to player commands and turns a play/pause double tap into NEXT after `ViewConfiguration.getDoubleTapTimeout()`. | [V] `MediaSessionImpl.java` 1613-1700 |
| Manifest | `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; service with `android:foregroundServiceType="mediaPlayback"` and action `androidx.media3.session.MediaSessionService`. | [V] https://developer.android.com/media/media3/session/background-playback |
| Foreground / notification | Media3 starts the FGS with `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` and a MediaStyle notification only while its player is considered playing; "The notification is created as soon as the Player has MediaItem instances in its playlist." | [V] `MediaNotificationManager.java` 532-539; docs |
| Playback state | Not needed for routing between apps (1.3). Needed only so Media3/SystemUI consider the session "engaged". | [V] |
| Audio focus | Not needed for routing. On Android 17 a focus request from the background fails without FGS (2.3). | [V] |
| Cold start | On API 31+ Media3 registers a media button receiver only if the manifest declares exactly one receiver for `ACTION_MEDIA_BUTTON` (`setMediaButtonBroadcastReceiver`); Media3's own `MediaButtonReceiver` ignores everything except PLAY / PLAY_PAUSE / HEADSETHOOK on API 26+ and then *must* start playback ("or the system will crash the service with a ForegroundServiceDidNotStartInTimeException"). | [V] `MediaSessionLegacyStub.java` 203-265, `MediaButtonReceiver.java` 147-170, 205-213 |

### 2.2 Why it cannot *remain* the target

- **[V] Another player starts**: any session-owning app that starts playback becomes the owner and evicts us from the history (1.3).
- **[V] 10-minute limits, twice.**
  - Media3: `DEFAULT_FOREGROUND_SERVICE_TIMEOUT_MS = 600_000`, "The default and maximum value" (`MediaSessionService.java` 186, 652-666).
    Docs: "If the player is paused, stopped or failed for more than 10 minutes without further user interactions, the service is
    automatically transitioned out of the foreground service state so it can be destroyed by the system."
  - Platform (Android 16+ code, behind flag `enable_notifying_activity_manager_with_media_session_status_change`):
    a session that leaves an active playback state is "temporarily engaged" for
    `DEFAULT_MEDIA_SESSION_TEMP_USER_ENGAGED_DURATION_MS = 600_000`, then `notifyInactiveMediaForegroundService` makes
    `ActiveServices` call `setServiceForegroundInnerLocked(sr, 0, null, 0, 0, ...)`, i.e. the system itself takes the service out
    of the foreground. Conditions: FGS type is exactly `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`, the notification is linked to
    the session, and compat change `MEDIA_FGS_STATE_TRANSITION` (`@EnabledSince(targetSdkVersion = UPSIDE_DOWN_CAKE)`) applies
    (`MediaSessionRecord.java` 1606-1645, `MediaSessionService.java` 716-862, `ActiveServices.java` 415-422, 10262-10309).
    Test shortcut: `adb shell cmd media_session expire-temp-engaged-sessions`.
  - [I] Losing FGS state does not remove the session. The session and the media-button role survive as long as the *process*
    lives. So process lifetime is the real issue; an always-bound `VoiceInteractionService` solves it without any FGS
    ("The current VoiceInteractionService that has been selected by the user is kept always running by the system",
    https://developer.android.com/reference/android/service/voice/VoiceInteractionService) [V quote, I application].
- **[V] Process death**: role -> `null`; keys go to the remembered receiver if there is one, else nowhere (1.4).
- **[I] Doze**: no routing effect. Delivery to a live, non-cached process works; a cached/frozen process is a risk [U].
- **[V] Reboot**: all sessions gone; only the persisted `media_button_receiver` component remains.
- **[V] Calls**: Telecom's global priority session takes all media keys while active.
- **[V] Android 17 background audio hardening** (2.3) silently mutes playback that would be needed to (re)claim the role.

### 2.3 Android 17 background audio hardening [V]

https://developer.android.com/about/versions/17/changes/bg-audio :

- "All apps running on Android 17 that have these background audio interactions must have a visible activity or must be running a
  foreground service that is not of type SHORT_SERVICE. This applies whether or not the app targets API level 37."
- "If an app targets Android 17 (API level 37) ... the app must be running a foreground service that has while-in-use (WIU) capabilities."
- Failure mode: "the audio playback and volume change APIs fail silently ... The audio focus API fails with the result code AUDIOFOCUS_REQUEST_FAILED."
- "Background-Started FGS (BFSL): Most do not grant WIU access. The primary exceptions that grant WIU are interactions involving
  explicit user intent for example, notification clicks, widget interactions, or media key events from an external device."
- "System started FGS: Foreground services are granted WIU access if they are started by ... system bindings representing an elevated
  foreground state to perform dedicated functionality (such as for a VoiceInteractionService)."
- "if your app starts a foreground service in response to BOOT_COMPLETE and attempts to interact with audio, it will be suppressed."
- Debug switch: `adb shell cmd audio set-enable-hardening <enable|disable|throw>`; failures appear as `AudioHardening` entries in
  `adb shell dumpsys audio` / logcat (`level: full` = FGS without WIU, `level: partial` = no FGS).

https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start : while-in-use exemptions include
"The service starts by an app which provides the VoiceInteractionService"; FGS-from-background exemptions include
"The user turns off battery optimizations for your app".

Design consequences [I]: (a) targetSdk 36 on Android 17 only needs *some* non-short FGS for background speech, targetSdk 37 needs WIU;
(b) being the default assistant is the cleanest source of WIU; (c) the media-key *listener* path (section 5) does **not** call
`tempAllowlistTargetPkgIfPossible`, so it brings no FGS/WIU allowance by itself, whereas the session/receiver path does.

---

## 3. How established apps get a hardware button with the screen off

| Technique | Who uses it | Screen off on unrooted Android 16/17? | Evidence |
|---|---|---|---|
| **Accessibility key filtering** (`FLAG_REQUEST_FILTER_KEY_EVENTS`, `onKeyEvent`) | Key Mapper normal mode [V]; Button Mapper, Tasker AutoInput, MacroDroid key triggers [I, closed source] | **No.** Keys without `FLAG_PASS_TO_USER` are not processed. Key Mapper: "This key map cannot be detected when the screen is off because the trigger was not recorded with Expert Mode." | [V] `AccessibilityInputFilter.java` 399-405; [V] Key Mapper `base/src/main/res/values/strings.xml` 545 |
| **Be the media-button session** (own session + own playback, often a silent clip or silent loop) | Tasker "Media Button" event / "Grab", MacroDroid "Media Button Pressed", many DIY apps | **Yes for media keys**, but only while we are the last playing session owner. Tasker's own help: "any app can grab the Media Button at any time, Tasker cannot 'keep hold' of it." | [V] section 1.3; [V] https://tasker.joaoapps.com/userguide/en/help/ah_grab_media_button.html |
| **Media button receiver / playback resumption** | every media app; Media3 `MediaButtonReceiver` | Yes, wakes a dead process, but only for the last media-button owner; persisted across reboot | [V] 1.4 |
| **`OnMediaKeyListener` with adb-granted `SET_MEDIA_KEY_LISTENER`** | `Osasuwu/like-current-song` (MIT, pushed 2026-11-07): triple-tap mode, "works with the screen off and the phone locked" | **Yes [V code path, I on device].** First look at every media key, pass-through by returning false | [V] AOSP manifest + `MediaSessionService.java` 2268-2322; [V] `TripleTapListener.kt`, `MediaKeyCapture.kt` |
| **Observe other apps' sessions** (NotificationListener + `getActiveSessions` + `MediaController.Callback`, react to pause->play patterns) | like-current-song (default mode: "It reacts to the pause/play state of whatever is playing"), MacroDroid "Media Button V2 (Beta)" ("uses notification access", per search snippet [I]) | Yes, but only while some player is active, and the user's music really pauses/resumes | [V] README of like-current-song |
| **`VolumeProvider` / remote playback session** for volume keys | cast-style apps; Home Assistant `HaMediaSessionService` (Media3 session for remote `media_player` entities, FGS `mediaPlayback`) | Phone volume keys only; irrelevant when the phone is never touched. A remote-playback session is not chosen as media-button session unless the app also plays local audio [I from 1.3] | [V] HA `AndroidManifest.xml` 345-355, `HaMediaSessionService.kt` |
| **`SET_VOLUME_KEY_LONG_PRESS_LISTENER`** (`signature|privileged|development`, adb grant) | MacroDroid "Volume Button Long Press" ("uses a private Android API", search snippet [I]) | Yes, phone volume keys only | [V] AOSP `core/res/AndroidManifest.xml` 7638-7643 |
| **Shell-uid evdev reader with `EVIOCGRAB`** started through in-app ADB client over Wireless Debugging, Shizuku, or root | Key Mapper 4.x "Expert Mode" (system bridge, uid 2000 or 0; Rust evdev module; `evdev.grab(GrabMode::Grab)` + uinput clone) | **Yes, any key of any input device, without waking** (the framework never sees grabbed events). Cost: helper must be restarted after each boot; without root that needs Wi-Fi, Wireless Debugging, `WRITE_SECURE_SETTINGS`, and on Android 17 the local-network permission | [V] Key Mapper `sysbridge/src/main/cpp/starter.cpp` 192-195, `evdev/.../grabbed_device.rs` 31-32, strings 2060, 2085-2087, 2114-2121; `CHANGELOG.md` (#1939, #2227, #2294) |
| **adb-started getevent script** | Button Mapper ("does not work when the screen is off unless your device is rooted or you run an adb command"; resets on reboot) | Yes until reboot | [I] search snippets only; vendor site unreachable from here |
| **Root / custom key layout or idc (`keyboard.doNotWakeByDefault`)** | custom ROM users | Not on an unrooted Pixel | [V] property name in `KeyboardInputMapper.cpp` 222 |
| **Default assistant** (`VoiceInteractionService`, `ACTION_VOICE_COMMAND`, `ACTION_VOICE_SEARCH_HANDS_FREE`) | Home Assistant (`AssistVoiceInteractionService`, FGS type `microphone`; `VOICE_COMMAND` activity alias), Google app | Path A of the project; also the long-press route of 1.2 step 5 | [V] HA `AndroidManifest.xml` 570-601 |

Status of helpers [V]: Key Mapper v4.7.2 (2026-11-25, GPL-3.0, active). Upstream `RikkaApps/Shizuku` last release v13.6.0
(2025-05-25: "Support Android16 QPR1", "auto start without root on Android13+ when connected to a trusted WiFi"), last push 2025-06-18;
compatibility with Android 17 is **[U]**. Key Mapper no longer depends on Shizuku (own ADB client).

---

## 4. Phonak hearing aids and AVRCP

### 4.1 What the vendor documents [V]

- User guide "Phonak Audéo I-Sphere" (029-1356-02, 2024), technical data: "Bluetooth 5.3 Dual-Mode", "Profiles supported:
  HFP (Hands-free profile), A2DP". AVRCP is not listed.
  (https://intl.phonak.com/en-id/wp-content/uploads/sites/6/2024/10/PH_UserGuide_Audeo-I-Sphere_92x125_EN_029-1356-02.pdf)
- Same guide, chapter 9: "If paired with a Bluetooth enabled device, multiple functions can be accessed using Tap Control e.g.
  accept/end phone call, pause/resume streaming, start/stop voice assistant. This is indicated in your individual 'Hearing aid
  instructions'. To use Tap Control tap the top of your ear twice."
- Multi-function button: "a short press on the upper or the lower part of the button will accept an incoming call and a long
  press will reject an incoming call". No media function is documented for the button.
- Knowledge base "myPhonak and Tap Control": functions "accept/end call for streaming", "pause/resume streaming from a connected
  Bluetooth device", "access smartphones' voice assistant"; available "only in specific hearing aid models".
  (https://www.phonak.com/en-int/support/knowledge-base/articles/myphonak-and-tap-control)
- Phonak pro blog (Paradise launch), two fragments as returned by the page summary: "Tap Control is easily programmed in
  Target 7.0, with three pre-defined behaviors" / "two of which can be assigned to a specific hearing aid." (https://audiologyblog.phonakpro.com/novel-way-of-accessing-bluetooth-functionalities/)
- Knowledge base "Bluetooth profiles": "Phonak hearing aids support Bluetooth classic, the Hands Free Profile (HFP)" and
  "the Advanced Audio Distribution Profile (A2DP)".

### 4.2 What can be concluded

- **[U] Whether "pause/resume streaming" is an AVRCP passthrough PLAY/PAUSE** (which Android would dispatch as a media key),
  an AVDTP suspend (which Android would not turn into a key event), or something local. No primary source states it.
- **[I] Phonak devices do implement some AVRCP**: AOSP's interop database still carries a Phonak AG address range under
  `[INTEROP_DISABLE_ABSOLUTE_VOLUME]` (`00:0f:59:50:00:00-00:0f:59:6f:ff:ff`, `system/conf/interop_database.conf` line 107
  in android17-release; older trees comment it "Phonak AG - volume level not change"). Absolute volume is an AVRCP feature,
  so at least those devices negotiate AVRCP. It does not prove that taps send PLAY/PAUSE.
- **[I] Limited usefulness even if it works**: a single right aid gets a small number of tap functions; "voice assistant" (path A)
  and "pause/resume streaming" compete for them, the fitting software decides, and pause/resume may only be active while a
  stream is running. AVRCP events also carry no long-press information.
- **[I] Do not use silent audio to keep a media role with a hearing aid**: an open A2DP stream keeps the aids in their streaming
  programme (a 2020 forum report on an Audéo Marvel says streaming "is separate program that's added", https://forum.hearingtracker.com/t/how-do-i-pause-audio-streaming-on-phonak-audeo-m50/53089), changing what the
  wearer hears from the microphones and costing battery. Behaviour on current models: [U].

### 4.3 Device test for the aid (no app needed)

1. Pair the aid, play any music through it, `adb shell dumpsys bluetooth_manager | grep -A 25 "BTAudio Media Key Events"`.
2. Double-tap (with Tap Control set to pause/resume streaming) and repeat the dump. An entry
   `sendMediaKeyEvent: device=... key=KEYCODE_MEDIA_PAUSE pushed=true ...` proves AVRCP. No entry while the music still
   stops means a non-AVRCP mechanism.
3. Also watch `adb logcat -s MediaSessionService:D AvrcpTargetService:D` ("Sending KeyEvent ... to <session>", `DEBUG_KEY_EVENT = true`).
4. Repeat with nothing playing, with the screen off, and for the multi-function button (short/long).

---

## 5. Recommendation

### 5.1 Design (minimal and robust)

**C1 (primary): media-key listener, fail-open.**

- Manifest: `<uses-permission android:name="android.permission.SET_MEDIA_KEY_LISTENER" tools:ignore="ProtectedPermissions" />`.
- One-time setup: `adb shell pm grant <applicationId> android.permission.SET_MEDIA_KEY_LISTENER`.
  [V] development permissions are granted through the normal runtime-grant path (`PermissionService.kt` 966, 1037:
  `permission.isDevelopment -> {}`); [I] the grant persists across reboots and same-signature updates, and is lost on uninstall.
- Register in the process that the system keeps alive (the `VoiceInteractionService`), from a `HandlerThread`:

```kotlin
// Hidden @SystemApi, flagged "sdk,system-api,test-api" in Android 16 hiddenapi-flags.csv -> plain reflection.
val listenerCls = Class.forName("android.media.session.MediaSessionManager\$OnMediaKeyListener")
val set = MediaSessionManager::class.java.getMethod("setOnMediaKeyListener", listenerCls, Handler::class.java)
val proxy = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(listenerCls)) { p, m, a ->
    when (m.name) {
        "onMediaKey" -> router.onMediaKey(a!![0] as KeyEvent)   // Boolean: true = consume
        "hashCode" -> System.identityHashCode(p); "equals" -> p === a?.get(0); else -> "LaraMediaKeyListener"
    }
}
set.invoke(getSystemService(MediaSessionManager::class.java), proxy, handler)
```

  Contract [V] (`MediaSessionManager.java` 1056-1071, 1338-1351): "the listener gets the media key before any other media sessions
  but after the global priority session"; "System can only have a single media key listener"; "If the listener consumes the initial
  down event (i.e. ACTION_DOWN with repeat count zero), it must also comsume all following key events"; "If it takes more than 1s to
  return, the key event will be sent to other media sessions."
  Service side [V]: "The media key listener cannot be reset by another app" (a second registrant is ignored with only a log line);
  binder death clears the listener.
- Decision rule, taken on the first DOWN and repeated for that press (AVRCP `downTime` is 0, so pair by a flag, not by downTime):
  - Lara dialog/menu **active** (opened through A or B, or through the wake key): consume everything.
    NEXT = menu down, PREVIOUS = menu up, PLAY / PAUSE / PLAY_PAUSE / HEADSETHOOK = select, STOP = cancel.
  - Lara **idle**: return `false` for everything except the configured wake key. Safest choice is a key the music app does not
    need on that remote (STOP, RECORD, REWIND, FAST_FORWARD). Optional rule for AVRCP sources: PLAY/PLAY_PAUSE is a wake key
    only if no session is in `STATE_PLAYING`/recently paused (needs notification-listener access; unknown state = pass).
  - Answer in microseconds from cached state, take a `PARTIAL_WAKE_LOCK`, post the real work elsewhere.
- Self-check at start (copy the idea from like-current-song): dispatch a synthetic `KEYCODE_MEDIA_RECORD` DOWN/UP with a unique
  `eventTime` through the public `AudioManager.dispatchMediaKeyEvent` and see whether the listener receives it; if not, the slot
  is taken or the API changed. Announce the result in the spoken diagnostics menu.
- After a trigger, start the dialog through the assistant machinery of path A (VoiceInteractionService -> session), because the
  listener path grants no FGS/WIU allowance by itself (2.3).

**C2 (fallback, public API only): no session while idle, optional session during a dialog.**

- Rule 1 [V basis 1.3]: while idle and for unsolicited announcements Lara owns **no** MediaSession. Then Lara never becomes the
  media-button session and the user's player keeps play/pause, no matter what Lara plays.
- Rule 2: only if media-key menu navigation is wanted without C1, create a plain platform `android.media.session.MediaSession`
  (Media3 adds nothing here) when a dialog starts, play the dialog audio from the app's own process (not the TTS engine process),
  consume keys in `onMediaButtonEvent`, release the session when the dialog ends, set no media button receiver.
  Known cost: the previous player has lost the media-button role until it plays again (1.3). Offer a spoken "resume music"
  action (`MediaController.transportControls.play()` on the remembered session, needs notification access) as mitigation.
- A wake-from-idle trigger through a remembered media button receiver is possible (1.4) but is exactly the hijack; keep it
  behind an explicit opt-in for users who run no other media app.

**Not recommended:** Media3 `MediaSessionService` with a fake player, silent audio loops, HID keyboard/presentation keys without an
evdev grab.

**Optional C3 (power-user, for arrow/enter remotes):** shell-uid evdev grab in the style of Key Mapper Expert Mode. It makes any BT
HID remote a full screen-off menu controller, but depends on a helper that must be restarted after each reboot.

### 5.2 Honest failure modes

| # | Failure | Effect | Detection / mitigation |
|---|---|---|---|
| F1 | `setOnMediaKeyListener` is hidden API; signature, flag list or permission level may change in a later Android | C1 silently unavailable | reflection errors -> spoken/visible status; fall back to C2; `adb shell settings put global hidden_api_policy 1` as documented escape hatch for blocked hidden APIs |
| F2 | Another app already holds the single listener slot (Pixel system apps: [U]) | registration ignored | slot probe; `adb shell dumpsys media_session` shows "Media key listener package" |
| F3 | Process dead or listener blocked > 1000 ms | key goes to normal target (fail-open) | keep listener in VIS process; never block |
| F4 | Call ringing/active (global priority session) | listener and sessions bypassed | by design |
| F5 | AVRCP PLAY/STOP dropped during `USAGE_VOICE_COMMUNICATION` playback | no select/cancel via PLAY/STOP in an SCO dialog | map select to PAUSE/NEXT as well, or use `USAGE_ASSISTANT` over A2DP for prompts; test T8 |
| F6 | HID remote link asleep; first press lost or 1-3 s late | missed trigger | measure (T6); prefer path B for wake |
| F7 | Remote classified as alphabetic keyboard, or sends non-media keys | screen wakes | pick a pure consumer-control remote; verify with T7 |
| F8 | Wake key also meaningful to the music app, or accidental press in the pocket | unwanted Lara start or lost music command | dedicated key, earcon on start, short auto-timeout |
| F9 | Grant missing after reinstall/new device | C1 off | setup check + exact adb command in the diagnostics output |
| F10 | Android 17 audio hardening mutes Lara's reply or fails focus | silent assistant | FGS with WIU via VoiceInteractionService; watch `AudioHardening` in `dumpsys audio`; `cmd audio set-enable-hardening throw` during development |
| F11 | (C2 only) Lara took the media-button role | headset "play" no longer resumes the podcast | Rule 1; "resume music" action |
| F12 | Aid in call/SCO or A2DP policy forbidden for the remote | AVRCP keys dropped by the Bluetooth app | check "BTAudio Media Key Events" |

### 5.3 How hijacking is avoided (summary)

1. Never own a MediaSession while idle (C2 rule 1) and never loop silent audio.
2. With C1, pass through by returning `false` on DOWN so the system dispatches with the original caller identity and its
   FGS allow-listing for the real player; do not "consume and re-inject" (re-injection through `AudioManager.dispatchMediaKeyEvent`
   carries our identity; whether the target then gets its FGS allowance depends on our own state).
3. Consume only (a) a dedicated wake key, (b) all keys during an explicit Lara dialog.
4. Show/speak the current media-key target (`getMediaKeyEventSessionPackageName()`) in diagnostics.

### 5.4 Device test (any Bluetooth headset or media remote)

Probe app (debug build, a few hundred lines): platform `MediaSession` with `onMediaButtonEvent` logging; manifest
`BroadcastReceiver` for `ACTION_MEDIA_BUTTON`; optional C1 listener; a button/intent to play a 300 ms tone from its own
`AudioTrack` (`USAGE_ASSISTANT`, alternatively `USAGE_MEDIA`); every event is appended to a file and logcat with: source
(listener/session/receiver), keyCode, action, repeatCount, flags, deviceId, `PowerManager.isInteractive`,
`isDeviceIdleMode`, `KeyguardManager.isKeyguardLocked`, elapsedRealtime. A short earcon confirms each received key so the
test can be done blind. Use adb over Wi-Fi (USB charging prevents Doze), or `adb shell dumpsys battery unplug`.

Read-outs:

```
adb shell dumpsys media_session        # "Media button session is ...", "Last MediaButtonReceiver: ...",
                                       # "Media key listener package: ...", "Audio playback (lastly played comes first)"
adb logcat -s MediaSessionService:* MediaSessionStack:* PendingIntentHolder:* AvrcpTargetService:*
adb shell dumpsys bluetooth_manager | grep -A 25 "BTAudio Media Key Events"
adb shell dumpsys power | grep -E "mWakefulness=|mHoldingDisplaySuspendBlocker"
adb shell dumpsys input | grep -B2 -A14 -i "<remote name>"      # look for the keyboard type, the external flag and the sources
adb shell cmd media_session dispatch play-pause                  # hardware-free simulation of an AVRCP-style key
adb shell cmd media_session expire-temp-engaged-sessions         # skip the 10 min engaged timer
adb shell dumpsys deviceidle force-idle ; adb shell dumpsys deviceidle unforce
adb shell aflags list | grep -i -E "filter_session_audio|notifying_activity_manager"
adb shell dumpsys audio | grep -i AudioHardening
```

| Test | Steps | Pass criterion | Claim tested |
|---|---|---|---|
| T1 baseline | Probe plays tone with session; lock, screen off; press play/pause, next, previous | each key logged via session; `mWakefulness=Asleep`/Dozing throughout | routing + no wake (1.3, 1.5) |
| T2 take-over | Start a podcast app, pause it; press play | podcast resumes; probe sees nothing; dumpsys shows podcast as button session | "last played wins" |
| T3 no session | Probe without session plays tone after T2; press play | podcast still resumes | C2 rule 1 |
| T4 dead process | Make probe the owner, `adb shell am kill <pkg>` (or wait hours); press any key | manifest receiver gets `ACTION_MEDIA_BUTTON`, process restarts; log shows `[FGS]`/`[WIU]` allow-list line | 1.4 |
| T5 reboot | Reboot, unlock once, lock, screen off, press key | receiver fires from persisted `media_button_receiver` | 1.4 |
| T6 long idle + Doze | Leave locked 2 h or `deviceidle force-idle`; single press | first press delivered; latency recorded | Doze / remote reconnect |
| T7 non-media keys | Remote in keyboard/presenter mode: arrow, enter | expected: screen wakes, no key delivered | 1.5 |
| T8 C1 listener | Grant permission, register; podcast paused; press dedicated key, then play | dedicated key reaches listener only; play resumes podcast; `dumpsys media_session` shows our package as listener | C1 |
| T9 C1 fail-open | `am kill` probe while listener registered; press play | podcast resumes | F3 |
| T10 SCO dialog | Open SCO / play `USAGE_VOICE_COMMUNICATION`; press play, pause, next on an AVRCP headset | PLAY/STOP missing, PAUSE/NEXT arrive | F5 |
| T11 Android 17 audio | From T8 trigger, speak a reply in background with and without FGS/WIU | reply audible only in the allowed configuration; `AudioHardening` log otherwise | 2.3 |
| T12 Phonak | section 4.3 | AVRCP entries yes/no | section 4 |

---

## 6. Corrections and additions to the inherited planning notes

- The notes treat path C as "MediaSession, best effort, system decides routing". Correct but incomplete: the adb-grantable
  `SET_MEDIA_KEY_LISTENER` + `setOnMediaKeyListener` route is missing; it changes C from "hijack or miss" to "first look, fail-open".
- "Keine Annahme, dass ein Bluetooth-Keyboard/Gamepad bei vollständig ausgeschaltetem Bildschirm immer steuerbar bleibt" is too
  weak: by AOSP code such keys are never delivered with the display off and they **wake the display** (except media/volume keys
  on non-alphabetic devices).
- "MediaSession übernimmt zuverlässig einen Tastendruck nach Stunden Inaktivität" is framed as a time problem. The session role has
  no timeout; what expires is foreground-service state (10 min) and therefore possibly the process, and the role is lost
  whenever another session-owning app plays.
- The notes do not mention that platform `TextToSpeech` audio is attributed to the TTS engine's UID, nor Android 17 background
  audio hardening; both affect the audio design.
- The Satechi row ("HID-Präsentationsmodus löst Bildschirm-aus-Problem nicht") is consistent with the code. Additional risk:
  a combo device classified as alphabetic keyboard makes even its media keys wake the screen.
- The 8BitDo row ("keine Lösung für zuverlässig ausgeschaltetes Display") holds for public APIs only; a shell-uid evdev grab
  (Key Mapper Expert Mode) does solve it at the price of a boot-time helper.
- The link https://developer.android.com/media/legacy/media-buttons exists and says what the notes claim.

---

## 7. Projects worth linking in the README / reusing

| Project | License | Why it is interesting here |
|---|---|---|
| https://github.com/Osasuwu/like-current-song | MIT | Working reference for `setOnMediaKeyListener` by reflection, adb grant flow, slot probe, AVRCP quirks, screen-off use with earbuds |
| https://github.com/keymapperorg/KeyMapper | GPL-3.0 (ideas only, no code copy into Apache-2.0) | Expert Mode: in-app ADB client over Wireless Debugging, shell-uid evdev grab, autostart handling, Android 17 fixes |
| https://github.com/home-assistant/android | Apache-2.0 | Production `VoiceInteractionService` assistant (`assist/service/`), Media3 `MediaSessionService` without local playback (`mediacontrols/`), `VOICE_COMMAND` alias |
| https://github.com/androidx/media | Apache-2.0 | Media3 session sources: `MediaButtonReceiver`, `MediaSessionImpl.onMediaButtonEvent` |
| https://github.com/RikkaApps/Shizuku | Apache-2.0 | adb-privilege bridge; upstream quiet since mid-2025, check before depending on it |
| https://github.com/LSPosed/AndroidHiddenApiBypass | Apache-2.0 | Only needed if a required hidden API becomes blocked |
| AOSP `frameworks/base` media session service | Apache-2.0 | Ground truth for routing; `cmd media_session` for tests |

---

## 8. Source index

AOSP (`https://android.googlesource.com/<project>/+/refs/heads/android17-release/<path>`):

- `platform/frameworks/base`: `services/core/java/com/android/server/media/{MediaSessionService,MediaSessionStack,AudioPlayerStateMonitor,MediaButtonReceiverHolder,MediaSessionRecord,MediaSessionDeviceConfig,MediaShellCommand}.java`;
  `services/core/java/com/android/server/policy/PhoneWindowManager.java`; `services/core/java/com/android/server/am/ActiveServices.java`;
  `services/accessibility/java/com/android/server/accessibility/AccessibilityInputFilter.java`;
  `services/permission/java/com/android/server/permission/access/permission/PermissionService.kt`;
  `media/java/android/media/session/{MediaSessionManager,MediaSession,MediaSessionLegacyHelper}.java`;
  `core/java/android/view/KeyEvent.java`; `core/java/android/speech/tts/{BlockingAudioTrack,TextToSpeechService}.java`;
  `core/res/AndroidManifest.xml` (7645-7651: SET_MEDIA_KEY_LISTENER `signature|privileged|development`; same level in android13-release 5100, android14-release 5937, android16-qpr2-release 7059).
- `platform/frameworks/native`: `services/inputflinger/reader/mapper/KeyboardInputMapper.cpp`; `libs/input/rust/keyboard_classifier.rs`.
- `platform/packages/modules/Bluetooth`: `android/app/src/com/android/bluetooth/avrcp/AvrcpTargetService.java`;
  `android/app/src/com/android/bluetooth/audio_util/MediaPlayerList.java`; `system/conf/interop_database.conf`.
- Hidden API list Android 16: https://dl.google.com/developers/android/baklava/non-sdk/hiddenapi-flags.csv , lines
  `Landroid/media/session/MediaSessionManager;->setOnMediaKeyListener(...)V,sdk,system-api,test-api`,
  `Landroid/media/session/MediaSessionManager$OnMediaKeyListener;->onMediaKey(Landroid/view/KeyEvent;)Z,sdk,system-api,test-api`,
  `...;->getMediaKeyEventSession()...,public-api,sdk,system-api,test-api`
  (extract: `<research>/hiddenapi_a16_mediakeys.txt`).
  The Android 17 list was not checked [U].

Android developer docs: media-buttons (legacy), media3/session/background-playback, about/versions/17/behavior-changes-all,
about/versions/17/changes/bg-audio, develop/background-work/services/fgs/restrictions-bg-start,
guide/app-compatibility/restrictions-non-sdk-interfaces, reference/android/service/voice/VoiceInteractionService.

Media3: https://github.com/androidx/media tag 1.11.1; https://dl.google.com/android/maven2/androidx/media3/media3-session/maven-metadata.xml .

Apps: Key Mapper commit 6ebb9c6 (2026-11-25); Home Assistant Android default branch on 2026-11-27; like-current-song default branch on 2026-11-27;
Tasker user guide `ah_grab_media_button.html`.

Phonak: user guide PDF above (text extract: `<research>/phonak_sphere_userguide.txt`), knowledge base articles, phonakpro blog.

Not reachable / not verified: Bluetooth SIG qualification listing for Sonova devices (would state AVRCP roles), flar2.com (Button Mapper FAQ),
macrodroidforum.com wiki (HTTP 403), aconfig flag values of the shipping Pixel build, Pixel resource overlays for wake-on-key configs.
