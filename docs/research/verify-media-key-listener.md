# Verification: media-key listener cluster (claims 1 to 7)

Date: 2026-11-27. Nothing here was run on a device. Verifier's own fetches are in
`<research>/verify-mkl/`
(`a17/` = files fetched by me at tag `android-17.0.0_r1`, `main/` = `refs/heads/main`, `media3/`, `privapp/`, `lcs/`).

## 0. Which source is "Android 17"

- `git ls-remote https://android.googlesource.com/platform/frameworks/base`:
  `refs/tags/android-17.0.0_r1^{}` = `94b4c163b7df...` = `refs/heads/android17-release`. Tag head committed 2026-05-21.
  Build id of that tag per the build-numbers page: `CP2A.260605.016`.
- `refs/heads/main` is stale: frameworks/base main head is dated 2025-03-26, Bluetooth main head 2025-03-17.
  Anything quoted from `main` is older than Android 16. All quotes below are from the tag.
- The platform manifest repo has only `android-17.0.0_r1` and the branches `android17-release`, `android17-security-release`.
  There is no QPR source. The Pixel 10 Pro vendor-blob repo (`TheMuppets/proprietary_vendor_google_blazer`, branch `lineage-24.0`)
  was last updated to build `CP3A.261005.005` (commit 2026-10-15), so the phone most likely runs a newer build than
  the newest public source. Everything "confirmed" below is confirmed for AOSP `android-17.0.0_r1`, not for the Pixel build.
- ART, Bluetooth and Media are Mainline modules and may be newer on the device than the tag.
- The earlier researcher's local copies under `research/aosp/android17-release/` are byte-identical to the tag
  (cmp on AndroidManifest.xml, MediaSessionManager, MediaSessionService, PhoneWindowManager, MediaSessionStack,
  KeyboardInputMapper.cpp, AvrcpTargetService).

URL pattern: `https://android.googlesource.com/<project>/+/refs/tags/android-17.0.0_r1/<path>`.

---

## Claim 1: SET_MEDIA_KEY_LISTENER is signature|privileged|development, adb grant works and persists

**Verdict: confirmed (AOSP 17.0.0_r1).**

- `platform/frameworks/base` `core/res/AndroidManifest.xml` 7645-7651:
  ```
  <!-- @SystemApi @hide Allows an application to set media key event listener. ...
       <p>Not for use by third-party applications</p> -->
  <permission android:name="android.permission.SET_MEDIA_KEY_LISTENER"
      android:protectionLevel="signature|privileged|development" />
  ```
  Same level on `android17-security-release` (line 7650).
- The legacy `PermissionManagerServiceImpl.java` no longer exists at the tag (404); the only implementation is
  `services/permission/java/com/android/server/permission/access/permission/PermissionService.kt`.
  `pm grant` -> `PackageManagerShellCommand.runGrantRevokePermission` (2759: `mPermissionManager.grantRuntimePermission(...)`)
  -> `PermissionService.setRuntimePermissionGranted`:
  - caller needs `GRANT_RUNTIME_PERMISSIONS` (`packages/Shell/AndroidManifest.xml` line 220: shell has it);
  - 1036-1037: `when { permission.isDevelopment -> {}` (no further condition: no debuggable, no testOnly, no privileged check);
  - 1134: `val newFlags = PermissionFlags.updateRuntimePermissionGranted(oldFlags, isGranted)` (= `flags or RUNTIME_GRANTED`).
- Granted state: `PermissionFlags.isPermissionGranted` ends with `return flags.hasBits(RUNTIME_GRANTED)` (350-370).
- Survives re-evaluation (boot, package update, OTA): `AppIdPermissionPolicy.kt` 1170-1177:
  ```kotlin
  // Different from the old implementation, which seemingly allows granting an
  // unallowlisted privileged permission via development or role but revokes it upon next
  // reconciliation, we now properly allows that because the privileged protection flag
  // should only affect the other static flags, but not dynamic flags like development or role.
  if (permission.isDevelopment) {
      newFlags = newFlags or (oldFlags and PermissionFlags.RUNTIME_GRANTED)
  }
  ```
- Survives reboot: `AppIdPermissionPersistence.kt` 220-230 serialises the flags; `RUNTIME_GRANTED` is stripped only for `ONE_TIME`.
- Update with the same signing key: `onPackageAdded` (152-167) calls `trimPermissionStates` (709-727), which clears only
  permissions that are no longer requested, then `evaluateAllPermissionStatesForPackage`. Uninstall removes the state.
  "Reset app preferences" (`resetRuntimePermissions`, 287-300) skips non-runtime permissions.
- Third-party device evidence (Android 16, not Pixel): PR 302 of `Osasuwu/like-current-song`, merged 2026-11-05:
  "Device: Xiaomi, HyperOS, SDK 36. Signed release APK, installed with `adb install -r` ... Grant and register: dumpsys lists
  our proxy as the media-key listener".

Details that matter:

- **`pm grant` is silent when the manifest does not declare the permission.** `PermissionService.kt` 1079-1088:
  `if (permissionName !in androidPackage.requestedPermissions && oldFlags == 0) { if (reportError) { Slog.e(LOG_TAG,
  "Permission $permissionName isn't requested by package $packageName") } return }`. No exception, exit code 0.
  Verify with `checkSelfPermission` or `adb shell dumpsys package <pkg> | grep SET_MEDIA_KEY_LISTENER`.
- The shell itself holds the permission (`packages/Shell/AndroidManifest.xml` line 80; `data/etc/privapp-permissions-platform.xml`
  line 591 inside `<privapp-permissions package="com.android.shell">`). In AOSP nobody else is allow-listed.

## Claim 2: setOnMediaKeyListener is @SystemApi and not blocked by hidden-API enforcement

**Verdict: confirmed by derivation from the Android 17 tag; no official Android 17 list exists to read it from.**

- No published Android 17 `hiddenapi-flags.csv`: https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces
  ("Last updated 2026-10-28 UTC") links lists only up to `.../android/baklava/non-sdk/hiddenapi-flags.csv` (Android 16).
  Guessed Android 17 URLs return 404.
- Official Android 16 list (downloaded and grepped by me):
  `Landroid/media/session/MediaSessionManager;->setOnMediaKeyListener(Landroid/media/session/MediaSessionManager$OnMediaKeyListener;Landroid/os/Handler;)V,sdk,system-api,test-api`
  and `Landroid/media/session/MediaSessionManager$OnMediaKeyListener;->onMediaKey(Landroid/view/KeyEvent;)Z,sdk,system-api,test-api`.
- Android 17 tag, `frameworks/base/core/api/system-current.txt` 9275 and 9283-9285:
  `method @RequiresPermission(android.Manifest.permission.SET_MEDIA_KEY_LISTENER) public void setOnMediaKeyListener(android.media.session.MediaSessionManager.OnMediaKeyListener, @Nullable android.os.Handler);`
  `public static interface MediaSessionManager.OnMediaKeyListener { method public boolean onMediaKey(android.view.KeyEvent); }`
  Not in `core/api/current.txt` (so not public API). Source: `MediaSessionManager.java` 1069-1071 `@SystemApi @RequiresPermission(...)`.
- Build rule that turns "system API" into the `sdk` list: `platform/build/soong` `scripts/hiddenapi/generate_hiddenapi_lists.py` 310-311:
  `if (FLAG_PUBLIC_API in flags) or (FLAG_SYSTEM_API in flags): flags.append(FLAG_SDK)`.
- ART at the tag:
  - `libartbase/base/hiddenapi_flags.h` 315-319: "Ignore flags which correspond to the stubs from where the api originates
    (i.e. system-api, test-api, public-api), as they are not relevant at runtime".
  - `runtime/hidden_api.h` 321-322: `if (api_list.Contains(ApiList::Sdk())) { runtime_flags |= kAccPublicApi; }`
  - `runtime/hidden_api.cc` 1077-1081: `// Exit early if member is public API. ... if ((runtime_flags & kAccPublicApi) != 0) { return false; }`
  The early exit comes before any look at the caller domain, target SDK or debuggable state. So an `sdk`-listed system API
  is treated exactly like a public API for a non-system, non-debuggable app, for reflection and for direct linking.
- Third-party device evidence: the release APK in the PR above registered through plain reflection on SDK 36.

Caveats: ART is a Mainline module; the dex flags of the Pixel's framework.jar cannot be read from here. One reflective call at
start-up tells (NoSuchMethodException = blocked or removed).

## Claim 3: only that permission is enforced; listener sees every media key first; false / 1000 ms / death pass the key on

**Verdict: partly. The mechanism is exactly as claimed; "every media key" is wrong in several building-relevant cases.**

Confirmed parts (`services/core/java/com/android/server/media/MediaSessionService.java`):

- 2269-2279: the only check is `mContext.checkPermission(android.Manifest.permission.SET_MEDIA_KEY_LISTENER, pid, uid)`,
  else `throw new SecurityException("Must hold the SET_MEDIA_KEY_LISTENER permission.")`. (Plus "Only the full user can set the media key listener".)
- 2014-2028 in `dispatchMediaKeyEvent`: `if (!isGlobalPriorityActive) { if (mCurrentFullUserRecord.mOnMediaKeyListener != null) { ...
  mCurrentFullUserRecord.mOnMediaKeyListener.onMediaKey(keyEvent, new MediaKeyListenerResultReceiver(...)); return; } catch (RemoteException e) {
  Log.w(TAG, "Failed to send " + keyEvent + " to the media key listener"); } }` (a dead binder falls through to normal dispatch).
- 125: `private static final int MEDIA_KEY_LISTENER_TIMEOUT = 1000;` 3010: `mHandler.postDelayed(this, MEDIA_KEY_LISTENER_TIMEOUT);`
  3024-3051: timeout or any result other than `RESULT_MEDIA_KEY_HANDLED` -> `handleMediaKeyEventLocked(mPackageName, mPid, mUid, ...)`
  with the original caller's identity.
- 2302-2312: `linkToDeath` -> `user.mOnMediaKeyListener = null`.
- Client: `MediaSessionManager.java` 1594-1622 posts to the given Handler and replies `RESULT_MEDIA_KEY_HANDLED` / `NOT_HANDLED`;
  1341-1351 doc: "If it takes more than 1s to return, the key event will be sent to other media sessions."
  `IOnMediaKeyListener.aidl`: `oneway interface`.
- Both sources converge on that method:
  - AVRCP: `AvrcpTargetService.sendMediaKeyEvent` 551 `mAudioManager.dispatchMediaKeyEvent(event)` -> `AudioManager` 1029-1031
    -> `MediaSessionLegacyHelper.sendMediaButtonEvent(keyEvent, false)` 177 `mCommunicationManager.dispatchMediaKeyEvent(keyEvent, needWakeLock)`
    -> `MediaCommunicationManager` 278-280 `if (!asSystemService) { getMediaSessionManager().dispatchMediaKeyEvent(keyEvent, false); return; }`
    -> `ISessionManager.dispatchMediaKeyEvent`. Independent of the display.
  - HID, display OFF: `PhoneWindowManager.interceptKeyBeforeQueueing` 4468-4479 ("When the screen is off and the key is not injected,
    determine whether to wake the device but don't pass the key to the application. result = 0;"), media-key case 4753-4783
    (`mBroadcastWakeLock.acquire(); ... MSG_DISPATCH_MEDIA_KEY_WITH_WAKE_LOCK`), 5184-5187
    `MediaSessionLegacyHelper.getHelper(mContext).sendMediaButtonEvent(event, true)` -> `MediaCommunicationService` 408-424
    (`mSessionManager.dispatchMediaKeyEvent(keyEvent, asSystemService)` after `clearCallingIdentity`) -> same service method.
  - HID, always-on display (display state DOZE, keyguard showing): `shouldDispatchInputWhenNonInteractive` 5065-5100 returns true
    ("Send events to keyguard while the screen is on and it's showing"), so the key goes to the keyguard window first and reaches
    MediaSessionService through SystemUI / `PhoneFallbackEventHandler.handleMediaKeyEvent` (321-322
    `dispatchMediaKeyEventAsSystemService`). Different path, same end point by code; worth one device test in both display modes.
- `static final boolean DEBUG_KEY_EVENT = true;` (122): logcat shows "Send KeyEvent ... to the media key listener".
- `adb shell cmd media_session dispatch play-pause` goes through the same method (`MediaShellCommand.java` 131-134), so the listener
  can be tested without hardware.

Where "every media key is offered first" fails:

1. **Global priority session.** Telecom's session bypasses the listener (`if (!isGlobalPriorityActive)`).
   `packages/services/Telecomm` `HeadsetMediaButton.java` 128 `FLAG_EXCLUSIVE_GLOBAL_PRIORITY`, 231-242: activated for every
   non-external call added (`if (call.isExternalCall()) return; handleCallAddition();`), with no self-managed exception.
   If the assistant dialog is modelled as a self-managed Telecom call, the listener sees no keys during the dialog.
2. **AVRCP keys are filtered before they become key events.**
   - Native `system/profile/avrcp/device.cc` 978-1010: a pushed PLAY from a device that is not the active A2DP peer makes it the active
     device and is dropped if music is playing ("Skipping sendKeyEvent since music is already playing"); all other passthrough
     commands are forwarded only `if (IsActive())`.
   - `AvrcpTargetService.sendMediaKeyEvent` 500-552: PLAY/STOP dropped during voice-communication playback (claim 7); PLAY may go
     "directly to addressed player"; dropped if A2DP policy is forbidden; VOLUME_UP/DOWN handled as volume, never as keys.
3. **Only `KeyEvent.isMediaSessionKey`** (KeyEvent.java 2889-2904): PLAY, PAUSE, PLAY_PAUSE, HEADSETHOOK, STOP, NEXT, PREVIOUS, REWIND,
   RECORD, FAST_FORWARD. Not: volume, MUTE, SKIP_*, VOICE_ASSIST.
4. **Display on:** a foreground activity that consumes the key, or one that called `setMediaController`
   (`dispatchMediaKeyEventToSessionAsSystemService`, 2057-2080, no listener check), bypasses the listener.
5. User setup not complete (1997-2003).

Other facts that change the build:

- **AVRCP events are anonymous.** `new KeyEvent(action, keyCode)` (AvrcpTargetService 549-550) = `downTime 0, eventTime 0,
  deviceId KeyCharacterMap.VIRTUAL_KEYBOARD, source 0` (KeyEvent.java 2361-2365). The listener cannot tell the hearing aid from a car kit;
  the only attribution is "current active A2DP device". HID events keep their real `deviceId`.
- **No wake lock and no FGS / while-in-use allowance on the listener path.** `needWakeLock` is only stored in the result receiver;
  `tempAllowlistTargetPkgIfPossible` is reached only when a session or receiver gets the key.
- **Frozen process = 1 s delay on every media key for every app.** The system sends the oneway call and waits; a frozen process cannot answer.
  https://source.android.com/docs/core/perf/cached-apps-freezer : "App processes in the cached state are frozen 10 seconds after entering
  the cached state." / "When an app process is frozen, all of its threads are suspended" / "When a server app process receives asynchronous
  (oneway) binder transactions while frozen, the transactions are buffered". MediaSessionService does not track the frozen state.
- Registration lives only in system_server memory: gone after reboot and after the app process dies; nothing restarts the app.
- `setOnMediaKeyListener(null)` needs the same permission; after `pm revoke` the old registration stays until the process dies
  (code: permission check first; observed on a device in PR 302).

## Claim 4: one slot per user, possibly taken on a Pixel; second registration

**Verdict: confirmed (slot and second-registration behaviour from code; "may be taken" supported by the Pixel 10 Pro allow-list; whether it
is actually taken is undetermined).**

- One field per full user: 1353-1354 `private IOnMediaKeyListener mOnMediaKeyListener; private int mOnMediaKeyListenerUid;` in `FullUserRecord`;
  dispatch reads `mCurrentFullUserRecord`. API doc 1056-1063: "System can only have a single media key listener."
- Second registration, 2289-2297:
  ```java
  if (user.mOnMediaKeyListener != null && user.mOnMediaKeyListenerUid != uid) {
      Log.w(TAG, "The media key listener cannot be reset by another app. "
              + ", mOnMediaKeyListenerUid=" + user.mOnMediaKeyListenerUid + ", uid=" + uid);
      return;
  }
  user.mOnMediaKeyListener = listener;
  user.mOnMediaKeyListenerUid = uid;
  ```
  Another UID: ignored, only a log line, no exception. Same UID: replaced. First come, first served; no priority for system apps.
  The slot frees only when the holder passes null or its process dies.
- Who can hold it:
  - AOSP: only `com.android.shell` is allow-listed; SystemUI and Bluetooth manifests at the tag do not request it.
  - Pixel 10 Pro: `TheMuppets/proprietary_vendor_google_blazer` (branch `lineage-24.0`, extracted from build `CP3A.261005.005`),
    `proprietary/product/etc/permissions/privapp-permissions-google-p-lineage.xml` line 783
    `<privapp-permissions package="com.google.android.googlequicksearchbox">`, line 801
    `<permission name="android.permission.SET_MEDIA_KEY_LISTENER"/>` (also `SET_VOLUME_KEY_LONG_PRESS_LISTENER`, `MEDIA_CONTENT_CONTROL`).
    Same package in `MindTheGapps/vendor_gapps` branch `cinnamonbun`. This is a third-party extraction of the Pixel image, not a Google
    publication. No other package in the two blazer privapp files has the permission.
  - Whether the Google app registers a listener at run time, and when: not found in any source.
- Read-out: `adb shell dumpsys media_session` prints "Media key listener: ..." and "Media key listener package: ..." (1435-1437).

## Claim 5: media keys from an external non-alphabetic device do not wake; other external keys wake and are not delivered

**Verdict: confirmed.**

- `platform/frameworks/native` `services/inputflinger/reader/mapper/KeyboardInputMapper.cpp` 351-361:
  ```cpp
  // Any key down on an external keyboard or internal alphanumeric keyboard should wake the
  // device. ...
  if (down && !mParameters.doNotWakeByDefault &&
      (getDeviceContext().isExternal() || wakeOnAlphabeticKeyboard(keyboardType)) &&
      !(keyboardType != KeyboardType::ALPHABETIC && isMediaKey(keyCode))) {
      policyFlags |= POLICY_FLAG_WAKE;
  }
  ```
  `isMediaKey` 66-90 includes the media keys, MUTE, HEADSETHOOK and VOLUME_UP/DOWN/MUTE.
- `PhoneWindowManager.java` 4468-4479 (display off: `result = 0`, key not passed), 4894-4896 `if (isWakeKey) { wakeUpFromWakeKey(event); }`,
  5020-5043 `isWakeKeyWhenScreenOff` (DPAD depends on `mWakeOnDpadKeyPress`; AOSP `config.xml` 6479-6481: all three wake configs `true`).
- Generic.kl at the tag has no WAKE flag on any media key (only `key 143 WAKEUP`).

Caveats: (a) a device classified ALPHABETIC wakes on media keys too (`libs/input/rust/keyboard_classifier.rs`: Keyboard + AlphabeticKey
classes = Alphabetic; a NonAlphabetic device becomes Alphabetic once it sends an alphabetic key); (b) with the always-on display the
non-media key is passed to the keyguard window and still wakes; (c) VOICE_ASSIST is not in `isMediaKey`, so it wakes; (d) Pixel overlays
for the wake configs and device-specific key layouts are not visible from here.

## Claim 6: default routing and the 10-minute limits

**Verdict: partly.**

Confirmed:

- `MediaSessionStack.java` 190-229: "We send the media button events to the lastly played app. If the app has the media session,
  the session will receive the media button events."; 210 "Ignore if the lastly played app isn't a media app (i.e. has no media session)".
  `AudioPlayerStateMonitor.java` 183-186: "The UID whose audio is currently playing comes first, then the UID whose audio playback becomes
  active at the last comes next."
- Media3 1.11.1 (latest stable, 2026-09-11; 1.12.0-rc01 2026-11-25) `MediaSessionService.java` 186
  `DEFAULT_FOREGROUND_SERVICE_TIMEOUT_MS = 600_000`; 643-653 "Sets the timeout for a session to stay in a foreground service state after it
  paused, stopped, failed or ended. ... The default and maximum value is DEFAULT_FOREGROUND_SERVICE_TIMEOUT_MS." Same in 1.12.0-rc01 (line 201).
- Platform: `MediaSessionDeviceConfig.java` 56 `DEFAULT_MEDIA_SESSION_TEMP_USER_ENGAGED_DURATION_MS = 600_000`;
  `MediaSessionRecord.java` 1606-1645; `MediaSessionService.java` 812-862 -> `ActiveServices.notifyInactiveMediaForegroundServiceLocked`
  10262-10300 (`setServiceForegroundInnerLocked(sr, 0, null, 0, 0, 0, true)`), only if the FGS type is exactly
  `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`, `sr.foregroundId == notificationId`, and `MEDIA_FGS_STATE_TRANSITION`
  (`@EnabledSince(targetSdkVersion = UPSIDE_DOWN_CAKE)`, 419-422).

Corrections:

- **Not "Android 16+" but "AOSP from Android 16 QPR2".** Flag `com.android.media.flags.enable_notifying_activity_manager_with_media_session_status_change`:
  `platform/build/release` at the tag has a value file only under `aconfig/bp4a/` (`state: ENABLED, permission: READ_ONLY`) and
  `trunk_staging`; none under `bp1a`, `bp2a`, `bp3a`. `release_configs/cp2a.textproto` inherits `cp1a`, which inherits `bp4a`, so it is
  enabled in AOSP Android 17. Pixel builds set their own flag values: check `adb shell aflags list | grep notifying_activity_manager`.
- **Both timers key off the reported playback state, not real audio.** Platform: `if (mPlaybackState != null && mPlaybackState.isActive())
  { newUserEngagedState = USER_PERMANENTLY_ENGAGED; }` (1613). Media3: `(controller.getPlayWhenReady() || startInForegroundWhenPaused) &&
  (STATE_READY || STATE_BUFFERING)` (`MediaNotificationManager.java` 288-296). A fake player that keeps reporting "playing" is not demoted by
  either timer. It is still not the media-button target unless its own UID has an active, unmuted player.
- "Active" excludes muted players: `AudioPlaybackConfiguration.isActive()` 803-815 returns `!isMuteAffectingActiveState()` for a started
  player (client volume, stream volume, app-op mute, ...). A volume-zero track does not count.

## Claim 7: AVRCP PLAY and STOP are not delivered during communication audio

**Verdict: partly (true, but the trigger is narrower than "a communication audio session").**

- `packages/modules/Bluetooth` `android/app/src/com/android/bluetooth/avrcp/AvrcpTargetService.java` 500-512:
  ```java
  // Some devices will send a play event upon SCO disconnection and some will also send a
  // stop event upon SCO connection resulting in music starting even if the call is still
  // ongoing. As this is a BT specific issue we handle it here.
  if (voiceCommunicationActive
          && (KeyEvent.KEYCODE_MEDIA_PLAY == keyCode || KeyEvent.KEYCODE_MEDIA_STOP == keyCode)) {
      Log.w(TAG, "Received " + ... + " event while call is active, not sending it to AudioManager");
      return;
  }
  ```
  635-646 `isVoiceCommunicationActive()`: any `mAudioManager.getActivePlaybackConfigurations()` entry with
  `getUsage() == AudioAttributes.USAGE_VOICE_COMMUNICATION && audioConfig.isActive()`.
- So the condition is an **active, unmuted player with usage VOICE_COMMUNICATION** (any app). Audio mode, an open SCO link or a running
  microphone capture alone do not trigger it. PAUSE, NEXT, PREVIOUS, REWIND, FAST_FORWARD still pass.
- The check is not in `refs/heads/main` (stale 2025 code), it is in the Android 17 tag. Bluetooth is a Mainline module; the device may differ.
- Every AVRCP key is logged with `voice active=` in the "BTAudio Media Key Events" buffer of `adb shell dumpsys bluetooth_manager`.

---

## Registration snippet (claims 1 and 2 hold, claim 3 holds for the mechanism)

```kotlin
// Manifest: <uses-permission android:name="android.permission.SET_MEDIA_KEY_LISTENER" tools:ignore="ProtectedPermissions"/>
// Once:     adb shell pm grant <applicationId> android.permission.SET_MEDIA_KEY_LISTENER
private const val PERM = "android.permission.SET_MEDIA_KEY_LISTENER"

/**
 * Returns null on success, else the cause (SecurityException = grant missing,
 * ReflectiveOperationException = hidden API blocked or changed).
 * Success does not prove the slot is ours: a registration while another UID holds it is ignored without an error.
 * onKey runs on [thread], must answer from cached state well below 1000 ms; true = consume, false = pass on.
 */
fun registerMediaKeyListener(ctx: Context, thread: HandlerThread, onKey: (KeyEvent) -> Boolean): Throwable? {
    if (ctx.checkSelfPermission(PERM) != PackageManager.PERMISSION_GRANTED) {
        return SecurityException("adb shell pm grant ${ctx.packageName} $PERM")
    }
    return try {
        val listenerCls = Class.forName("android.media.session.MediaSessionManager\$OnMediaKeyListener")
        val proxy = Proxy.newProxyInstance(ctx.classLoader, arrayOf(listenerCls)) { self, method, args ->
            when (method.name) {
                "onMediaKey" -> try { onKey(args!![0] as KeyEvent) } catch (t: Throwable) { false }
                "hashCode" -> System.identityHashCode(self)
                "equals" -> self === args?.get(0)
                else -> "MediaKeyListener"
            }
        }
        MediaSessionManager::class.java
            .getMethod("setOnMediaKeyListener", listenerCls, Handler::class.java)
            .invoke(ctx.getSystemService(MediaSessionManager::class.java), proxy, Handler(thread.looper))
        null
    } catch (e: InvocationTargetException) {
        e.targetException
    } catch (e: ReflectiveOperationException) {
        e
    }
}
```

Not compiled or run. Always pass a Handler (`handler == null` makes the framework call `new Handler()` on the calling thread).
Register from one process only, from a component the system keeps out of the cached state, again after every process start.

## Design consequences in one list

1. Setup check: `checkSelfPermission`, then register, then a slot probe (send a synthetic `KEYCODE_MEDIA_RECORD` pair with a unique
   `eventTime` through `AudioManager.dispatchMediaKeyEvent` and see whether the own listener gets it), and show the adb command and
   the `dumpsys media_session` line in diagnostics.
2. Keep the process out of the cached/frozen state at all times; otherwise every media key on the phone is delayed by 1 s, and a dead
   process means the trigger is silently gone.
3. Decide on the first DOWN from cached state, take a partial wake lock there, do the work elsewhere. Pair DOWN and UP by a flag
   (AVRCP `downTime` is 0).
4. Do not model the dialog as a Telecom call if media keys are needed during the dialog.
5. Do not rely on AVRCP PLAY/STOP while the app plays with `USAGE_VOICE_COMMUNICATION`; use PAUSE/NEXT/PREVIOUS or a HID key.
6. AVRCP keys reach Android only from the active A2DP device, and cannot be attributed to a device in the event.
7. The listener trigger brings no right to start a microphone foreground service from the background; that has to come from elsewhere.

## Not verifiable from here (device tests)

See the structured result; the main ones are the Pixel build's permission level and hidden-API flag, the slot owner on the Pixel,
AVRCP and HID delivery with display off / always-on display / Doze, and whether the hearing aid sends AVRCP at all.

## Source index (all opened by me)

- AOSP tag `android-17.0.0_r1`: frameworks/base `core/res/AndroidManifest.xml`, `core/api/system-current.txt`, `core/api/current.txt`,
  `media/java/android/media/session/{MediaSessionManager.java,MediaSessionLegacyHelper.java,IOnMediaKeyListener.aidl}`,
  `media/java/android/media/{AudioManager.java,AudioPlaybackConfiguration.java}`, `media/java/android/media/flags/media_better_together.aconfig`,
  `services/core/java/com/android/server/media/{MediaSessionService,MediaSessionStack,MediaSessionRecord,MediaSessionDeviceConfig,AudioPlayerStateMonitor,MediaShellCommand}.java`,
  `services/core/java/com/android/server/policy/{PhoneWindowManager,WindowWakeUpPolicy}.java`, `services/core/java/com/android/server/am/ActiveServices.java`,
  `services/core/java/com/android/server/pm/PackageManagerShellCommand.java`, `services/core/java/com/android/server/pm/permission/PermissionManagerService.java`,
  `services/permission/java/com/android/server/permission/access/permission/{PermissionService,AppIdPermissionPolicy,AppIdPermissionPersistence,PermissionFlags,Permission}.kt`,
  `packages/Shell/AndroidManifest.xml`, `packages/SystemUI/AndroidManifest.xml`, `packages/SystemUI/.../KeyguardKeyEventInteractor.kt`,
  `core/java/com/android/internal/policy/PhoneFallbackEventHandler.java`, `core/java/android/view/KeyEvent.java`,
  `data/etc/privapp-permissions-platform.xml`, `data/keyboards/Generic.kl`, `core/res/res/values/config.xml`;
  frameworks/native `services/inputflinger/reader/mapper/KeyboardInputMapper.cpp`, `libs/input/rust/keyboard_classifier.rs`;
  packages/modules/Bluetooth `android/app/src/com/android/bluetooth/avrcp/AvrcpTargetService.java`, `system/profile/avrcp/device.cc`, `android/app/AndroidManifest.xml`;
  packages/modules/Media `apex/framework/java/android/media/MediaCommunicationManager.java`, `apex/service/java/com/android/server/media/MediaCommunicationService.java`;
  packages/services/Telecomm `src/com/android/server/telecom/HeadsetMediaButton.java`;
  art `runtime/hidden_api.{h,cc}`, `libartbase/base/hiddenapi_flags.h`; build/soong `scripts/hiddenapi/generate_hiddenapi_lists.py`;
  build/release `release_configs/{cp2a,cp1a,bp4a}.textproto`, `aconfig/bp4a/com.android.media.flags/enable_notifying_activity_manager_with_media_session_status_change_flag_values.textproto`.
- https://dl.google.com/developers/android/baklava/non-sdk/hiddenapi-flags.csv (Android 16).
- https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces
- https://source.android.com/docs/core/perf/cached-apps-freezer
- https://github.com/androidx/media tags 1.11.1 and 1.12.0-rc01; https://dl.google.com/android/maven2/androidx/media3/media3-session/maven-metadata.xml
- https://github.com/TheMuppets/proprietary_vendor_google_blazer (lineage-24.0); https://github.com/MindTheGapps/vendor_gapps (cinnamonbun)
- https://github.com/Osasuwu/like-current-song (`TripleTapListener.kt`, `MediaKeyCapture.kt`), https://github.com/Osasuwu/like-current-song/pull/302
