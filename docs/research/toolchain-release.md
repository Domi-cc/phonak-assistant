# Toolchain, release and debugging set-up for "Lara im Ohr"

Research date: 2026-11-27. Scope: build toolchain, release pipeline, sideloading, debugging toolkit, language choice.
Nothing was installed, built or run. All repository and web content was read only.

**Status legend used on every statement**

- **[V] verified** – seen in a primary source during this session (URL, file, or AOSP path given).
- **[I] inferred** – reasoned from verified sources or from long-standing platform behaviour; not re-checked against a primary source today.
- **[U] unknown** – could not be established; needs a device test or another source.

Primary sources were fetched with curl from developer.android.com, source.android.com, dl.google.com (Maven metadata and the SDK repository index), services.gradle.org, repo1.maven.org, the GitHub API, and android.googlesource.com at tag `android-17.0.0_r1`. Later quarterly releases (QPR1, QPR2) may differ from that tag; where it matters this is called out.

---

## 0. The ten things that matter most

1. **[V] Android 17 (API 37) is released** (16 June 2026). Minor SDK releases 37.1 and 37.2 exist. The Pixel 10 Pro received the Android 17 QPR2 build `CP4A.261105.040.A1` in the November 2026 update (build number from two press sources, not from Google's own image list).
2. **[V] Current stable toolchain:** Android Studio Rabbit 2 | 2026.2.2 Patch 3, AGP 9.5.3, Gradle 9.9.2, Kotlin 2.4.21, KSP 2.3.14, JDK 17 minimum (21 recommended), Compose BOM 2026.11.01, Media3 1.11.1, kotlinx-coroutines 1.11.0, kotlinx-serialization 1.13.0, OkHttp 5.5.1.
3. **[V] Android 17 "background audio hardening" is the one targetSdk-37 change that can break this app.** With targetSdk 37, audio playback, audio focus and volume calls from the background fail **silently** unless a foreground service with while-in-use (WIU) capability is running. A foreground service started from a BLE broadcast does not normally get WIU.
4. **[V + I] The assistant role is the likely key to that problem.** Documentation lists "the service starts by an app which provides the VoiceInteractionService" as a WIU exemption, and AOSP binds the active VoiceInteractionService with `BIND_INCLUDE_CAPABILITIES`. Whether that also covers a service started by the BLE path on a Pixel 10 Pro is a device test.
5. **[V] Google's developer verification does not block sideloading in Germany today.** Enforcement since 30 September 2026 covers Brazil, Indonesia, Singapore and Thailand and only seven named app stores. Global expansion is announced for 2027. `adb install` is exempt for good.
6. **[V] Restricted settings (Enhanced Confirmation Mode) do not touch the assistant role, Bluetooth, microphone or battery exemption.** The AOSP list covers SMS permissions, device admin, accessibility, notification listener, overlay, usage stats and the dialer and SMS roles only.
7. **[I] Debug and release builds must share one applicationId and one signing key.** The assistant role, permissions, battery exemption and BLE registrations belong to the package; uninstalling to switch build type throws them away.
8. **[V] Never use `am force-stop` to simulate a process death.** Since Android 15 it cancels all the app's PendingIntents and puts the package in the stopped state. Use `am kill` or `am stop-app`.
9. **[V] `sdkmanager` is marked deprecated** in favour of Google's new `android` CLI (`android sdk install`). It still ships and still works; the GitHub runners still use it.
10. **Recommendation: Kotlin only, Compose for the small UI, pure-Kotlin core module for JVM unit tests.** No cross-platform framework, no NDK unless a concrete DSP need appears.

---

## 1. Versions on 2026-11-27

### 1.1 Toolchain and libraries

| Component | Current stable | Next / pre-release | Status | Source |
|---|---|---|---|---|
| Android Studio | **Rabbit 2 \| 2026.2.2 Patch 3** (build 2026.2.2.11) | – | [V] | https://developer.android.com/studio/releases – "Download Android Studio Rabbit 2 \| 2026.2.2 Patch 3", file `android-studio-rabbit2-patch3-linux.tar.gz`, 1.6 GB, SHA-256 `1c7ccf6dbb0ac6376e3b15e536096f1878621b4b5586d81e22510cd835f1438d` |
| Android Gradle Plugin | **9.5.3** (published 2026-11-25; 9.5.0 is "October 2026") | 9.6.0-alpha07 | [V] | https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml ; release notes https://developer.android.com/build/releases/gradle-plugin |
| Gradle | **9.9.2** (build time 2026-11-19) | no active RC | [V] | https://services.gradle.org/versions/current – `"version" : "9.9.2"`, distribution SHA-256 `971fcd7150ca4ba04852c0b2ca0d2d0ab9468e14b03473eab19528809914c76b` |
| Kotlin | **2.4.21** (2026-10-08) | 2.5.0-RC2 (2026-11-25) | [V] | GitHub releases of JetBrains/kotlin; Maven Central `kotlin-gradle-plugin` |
| KSP | **2.3.14** (2026-11-13) | – | [V] | GitHub releases of google/ksp; Maven Central `symbol-processing-api` |
| JDK | **17 minimum** for AGP 9.5; Gradle 9.9.2 runs on JVM 17 to 27. Recommended: **21 LTS** (Temurin `jdk-21.0.13.1+2`) | 25 LTS also supported | [V] minimum, [I] recommendation | AGP notes table "JDK 17 / 17"; https://docs.gradle.org/current/userguide/compatibility.html – "A JVM version between 17 and 27 is required to execute Gradle" |
| compileSdk / targetSdk | **37** (Android 17). Platform packages `platforms;android-37.0` r2, `android-37.1` r1, `android-37.2` r2 | canary platforms | [V] | https://dl.google.com/android/repository/repository2-3.xml ; https://developer.android.com/about/versions/17/setup-sdk – `compileSdk = 37`, `targetSdk = 37` |
| Compose BOM | **2026.11.01** (2026-11-19) → ui/foundation/runtime 1.13.0, material3 1.5.0 | – | [V] | https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml and the BOM POM |
| Media3 | **1.11.1** (2026-09-11) | 1.12.0-rc01 (2026-11-25) | [V] | https://dl.google.com/dl/android/maven2/androidx/media3/media3-session/maven-metadata.xml ; GitHub releases of androidx/media |
| kotlinx-coroutines | **1.11.0** (2026-05-08) | – | [V] | Maven Central `kotlinx-coroutines-core`; GitHub releases |
| kotlinx-serialization | **1.13.0** (2026-11-11) | – | [V] | Maven Central `kotlinx-serialization-json`; GitHub releases |
| OkHttp | **5.5.1** (Maven metadata updated 2026-10-22) | – | [V] | https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml |

Other SDK packages [V] (same repository index): platform-tools **37.0.3**, build-tools **37.0.0** (AGP 9.5 default is 36.0.0), command-line tools **23.0** (`cmdline-tools;latest`; the download page still offers build 15859902 = 22.0), NDK 30.0.16248370 (AGP default 28.2.13676358), `sources;android-37.0/37.1/37.2`.

Other AndroidX versions [V] (dl.google.com Maven metadata, latest non-pre-release): core-ktx 1.19.1, lifecycle 2.11.1, activity-compose 1.13.0, datastore-preferences 1.3.0, room 2.8.5, work-runtime 2.12.0, androidx.media 1.8.0, navigation3 1.2.2. `androidx.bluetooth` has **no stable release** (newest 1.0.0-alpha02) – do not build on it.

**Facts that differ from what an older source or model would say**

- **[V] OkHttp's repository moved.** `github.com/square/okhttp` now resolves to `github.com/lysine-dev/okhttp`, and the 5.5.1 POM says `<url>https://lysine.dev/okhttp/</url>` and `scm:git:https://github.com/lysine-dev/okhttp.git`. The Maven coordinates are unchanged: `com.squareup.okhttp3:okhttp`.
- **[V] AGP 9 has built-in Kotlin.** "Android Gradle plugin 9.0 introduces built-in Kotlin support and enables it by default … you no longer have to apply the `org.jetbrains.kotlin.android` plugin" (https://developer.android.com/build/releases/agp-9-0-0-release-notes). AGP 9.5.3 carries a runtime dependency on KGP **2.2.10** (seen in `gradle-9.5.3.pom`); a higher KGP must be put on the build classpath explicitly.
- **[V] KSP versions are no longer `kotlinVersion-kspVersion`.** The current line is plain `2.3.x`.
- **[V] The Compose compiler is a Kotlin plugin** (`org.jetbrains.kotlin.plugin.compose`), versioned with Kotlin. There is no separate compiler-version matrix.

**Compatibility caveats**

- [V] AGP 9.5 needs Gradle **9.6.0 or newer** and JDK 17 (AGP release notes table).
- [V] Kotlin's own table lists KGP 2.4.20–2.4.21 as *fully supported* with Gradle up to 9.7.0 and AGP up to 9.3.1, and adds: "You can also use Gradle and AGP versions up to the latest releases, but … you might encounter deprecation warnings" (https://kotlinlang.org/docs/gradle-configure-project.html). Gradle 9.9.2 embeds Kotlin 2.4.21 and says it is "tested with Android Gradle Plugin 9.0 through 9.5.0-alpha10".
- [I] So "everything newest" (Gradle 9.9.2 + AGP 9.5.3 + Kotlin 2.4.21) is a combination each vendor tests only partly. It is what Android Studio Rabbit 2 Patch 3 ships with (the Flathub release entry for 2026.2.2.11 reads "Android Gradle Plugin 9.5.3"). If it produces warnings, the conservative fallback is whatever the Studio "New Project" wizard generates.
- [V] Android Studio Rabbit 2 supports AGP 7.1–9.5. API 37.2 needs Studio Quail 4 and AGP 9.4.0 or newer; API 37.0 needs AGP 9.1.1 or newer (table on https://developer.android.com/studio/releases).
- [U] Which JetBrains Runtime version Studio Rabbit 2 bundles was not found on the release page. Check `android-studio/jbr/release` after unpacking.

### 1.2 Android 17 and the Pixel 10 Pro

- **[V] Android 17 has shipped.** "Android 17 is Here … 16 Jun 2026 … Today we're releasing Android 17 and making it available on most supported Pixel devices" (https://developer.android.com/blog/posts/android-17-is-here). AOSP lists `android-17.0.0_r1`, build `CP2A.260605.016`, "API level 37", quarterly release name 26Q2 (https://source.android.com/docs/setup/reference/build-numbers).
- **[V] Minor SDK levels exist:** 37.1 (QPR1) and 37.2 (QPR2), each with a stable platform package in the SDK repository index. The developer site maps "QPR1 Beta → API 37.1" and "QPR2 Beta → 37.2".
- **[V, secondary source] Pixel 10 Pro today:** the November 2026 Pixel update is "Android 17 QPR2 … rolling out a month early with the November 2026 Feature Drop", global build `CP4A.261105.040.A1` for Pixel 10 / 10 Pro / 10 Pro XL / 10 Pro Fold / 10a (https://9to5google.com/2026/11/10/android-17-qpr2-november-pixel-update/ and https://www.droid-life.com/2026/11/10/2026-november-pixel-update-download-install-release/). Google's own factory-image list sits behind a licence click-through and was not read.
- **[I] Therefore** `Build.VERSION.SDK_INT == 37` and the full SDK level is 37.2 on an updated Pixel 10 Pro.
- **[V] Android 17 QPR3 is in beta** (Beta 4, 12 November 2026, build `DP11.261030.003`). No Android 18 material was found on the pages read.
- **[V] The Pixel 10 Pro launched on Android 16:** Google's image page says that after the May 2026 update "you won't be able to flash and boot older Android 16 builds" on Pixel 10, 10 Pro, 10 Pro XL and 10 Pro Fold. Its AOSP codename is `blazer`.
- **[V] A Pixel 11 family exists** (AOSP codenames: Pixel 11 Pro = `grizzly`). The inherited notes say "Pixel 11 Pro"; the task says Pixel 10 Pro. Confirm on the device:

```bash
adb shell getprop ro.product.model          # expected "Pixel 10 Pro"
adb shell getprop ro.product.device         # expected "blazer"
adb shell getprop ro.build.id               # expected CP4A.261105.040.A1 or newer
adb shell getprop ro.build.version.release  # expected 17
adb shell getprop ro.build.version.sdk      # expected 37
adb shell getprop ro.build.version.security_patch
```

### 1.3 Recommended SDK levels

| Setting | Value | Why |
|---|---|---|
| `compileSdk` | **37** (add `minorApiLevel = 2` only when a 37.1 or 37.2 API is needed) | Newest stable API surface, including the Android 17 hearing-aid additions. |
| `targetSdk` | **37**, read from a Gradle property so a `-PtargetSdk=36` build is one command away | See the reasoning below. |
| `minSdk` | **36** | The phone shipped with 36 and runs 37. Nothing older will ever be tested. Lowering it later costs only the guards that lint flags. |

Minor-level syntax [V] (https://developer.android.com/about/versions/16/qpr2/setup-sdk, same DSL for 37.x):

```kotlin
android {
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
}
```

**Why target 37 instead of staying on 36** [I]

- The stricter rules describe where the platform is going. A design that only works because of a lower target is on borrowed time.
- Behaviour under an old target is a compatibility shim, and it is the less documented path.
- [V] Silent updates need a recent target anyway: on Android 17 an installer may skip the confirmation only if the app being installed targets "API 35 or higher on Android C (API 37)" (https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)).
- The escape hatch stays open: building once with `-PtargetSdk=36` is the fastest way to tell "my bug" from "target-37 hardening".

If the device tests in section 8 show that path B cannot get a WIU-capable service at all, targetSdk 36 is the documented fallback for speech output (not for the microphone).

### 1.4 Behaviour changes that touch Bluetooth, foreground services, microphone, audio or the assistant

**Android 17, apps targeting 37** (https://developer.android.com/about/versions/17/behavior-changes-17)

| Change | What it does | Relevance |
|---|---|---|
| **Background audio hardening** [V] | "If the app is running in the background, the app must be running a foreground service that has while-in-use (WIU) capabilities." Otherwise "audio playback and volume change APIs fail silently … The audio focus API fails with the result code `AUDIOFOCUS_REQUEST_FAILED`." (https://developer.android.com/about/versions/17/changes/bg-audio) | **Critical.** Every spoken menu is background audio. |
| **Local network permission** [V] | `ACCESS_LOCAL_NETWORK` is mandatory for LAN access. It is in the `NEARBY_DEVICES` group, so a user who granted Bluetooth permissions "aren't prompted again". Without it "TCP connections will typically result in a timeout error". | Relevant if Hermes is reached by a LAN address. A missing permission looks like a network timeout. Whether VPN addresses count as local: [U]. |
| Background activity launch hardening [V] | `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` is to be replaced by granular modes; "Developers should utilize strict mode and updated lint checks". | Only if the app ever starts an activity from the background. |
| RFCOMM `read()` returns -1 on close [V] | Instead of throwing. | Only if classic Bluetooth sockets are used. Not planned. |
| Certificate Transparency on by default, ECH [V] | TLS behaviour changes. | A self-hosted server with a private CA or a self-signed certificate needs a network security config. Exact CT effect on private CAs: [U]. |
| `static final` fields immutable, lock-free `MessageQueue` [V] | Reflection hacks break. | Only matters for libraries that reflect on internals. |

**Android 17, all apps regardless of target** (https://developer.android.com/about/versions/17/behavior-changes-all)

| Change | What it does | Relevance |
|---|---|---|
| Background audio hardening, base level [V] | "All apps running on Android 17 that have these background audio interactions must have a visible activity or must be running a foreground service that is not of type `SHORT_SERVICE`." | A foreground service is mandatory for speech output with the screen off, whatever the target. |
| App memory limits [V] | Kills show up as `REASON_OTHER` with description containing `"MemoryLimiter:AnonSwap"`. Test with `adb shell am memory-limiter …`. | Unlikely for a small app; the journal should record it. |
| Autonomous Bluetooth re-pairing [V] | `ACTION_KEY_MISSING` is "broadcast only if the autonomous re-pairing attempt fails"; new `EXTRA_PAIRING_CONTEXT`. | Affects bonded devices (the hearing aid), not BTHome advertisements. |
| `usesCleartextTraffic` deprecation plan [V] | Use a network security configuration file instead. | Applies if Hermes is ever reached over plain HTTP. |

**How WIU is obtained** [V] (bg-audio page, section "Understanding FGS with while-in-use capability"):

> Standard FGS: Services started while the app is visible or granted background activity launch capability are granted WIU access.
> Background-Started FGS (BFSL): Most do not grant WIU access. The primary exceptions that grant WIU are interactions involving explicit user intent for example, notification clicks, widget interactions, or **media key events from an external device**.
> System started FGS: Foreground services are granted WIU access if they are started by system-server delegation (for example, from the Telecom jetpack library), or by system bindings representing an elevated foreground state to perform dedicated functionality (such as for a **`VoiceInteractionService`**).

Mapped onto the three input paths:

| Path | Trigger | WIU for audio and microphone | Status |
|---|---|---|---|
| A | Hearing-aid tap → assistant | Granted by the system binding | [V] documented, [U] on this device |
| C | Bluetooth media button → MediaSession | "media key events from an external device" is a named exception | [V] documented, [U] on this device |
| B | BLE advertisement → PendingIntent → service | **Not granted by itself.** | [V] rule; see next paragraph |

**The assistant role probably rescues path B.** Two verified facts point that way:

- [V] The foreground-service documentation lists, under "Exemptions from restrictions on while-in-use permissions": "The service starts by an app which provides the `VoiceInteractionService`." (https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)
- [V] AOSP binds the active service with capability inheritance (`services/voiceinteraction/java/com/android/server/voiceinteraction/VoiceInteractionManagerServiceImpl.java`, tag `android-17.0.0_r1`, line 1322):

```java
mContext.bindServiceAsUser(intent, mConnection,
        Context.BIND_AUTO_CREATE
                | Context.BIND_FOREGROUND_SERVICE
                | Context.BIND_INCLUDE_CAPABILITIES
                | Context.BIND_ALLOW_BACKGROUND_ACTIVITY_STARTS,
        new UserHandle(mUser));
```

- [I] So while Lara holds the assistant role, its process is kept bound by `system_server` with inherited capabilities, and a service it starts from the BLE path should count as WIU-capable. **This is the single most important thing to prove on the phone** (test T1 in section 8).
- Cross-check: the sibling reports `android-bg-ble.md` (section 4.4) and `assistant-role.md` in the same research folder reach the same conclusion from `ActiveServices.shouldAllowFgsWhileInUsePermissionLocked`, and add that the BLE scan PendingIntent itself carries no foreground-service allowance. Read them for the code-level detail.

**How the enforcement is implemented** [V] (`services/core/java/com/android/server/audio/HardeningEnforcer.java`, tag `android-17.0.0_r1`):

- The gate is the target SDK, not a toggleable compat change: `boolean isPreCinnamonBun = targetSdk < Build.VERSION_CODES.CINNAMON_BUN;`. Apps below 37 are enforced at the "partial" level, apps at 37 at the "full" level.
- Log lines have the form `AudioHardening … ignored for <package> (<uid>), level: full|partial`, tag `AS.HardeningEnforcer`. `level: full` means "a foreground service runs but has no WIU"; `level: partial` means "no foreground service at all".
- In that tag, the audio-focus branch treats an app holding `BLUETOOTH_CONNECT` at the partial level even when it targets 37. This is an implementation detail of one tag, the playback path lives in native code that was not read, and it may have changed in QPR1 or QPR2. Do not design on it. [U] for the phone's build.
- Test switches [V]: `adb shell cmd audio set-enable-hardening enable|disable|throw`. `throw` turns silent failures into `IllegalStateException`.

**Foreground services – rules that already apply at target 34 to 36**

- [V] Apps targeting 31+ cannot start a foreground service from the background except in listed cases. The list includes "The user turns off battery optimizations for your app", the Companion Device Manager permissions, `ACTION_BOOT_COMPLETED`, exact alarms and others. **The current page does not list a Bluetooth-broadcast exemption.** The battery-optimisation exemption is therefore the dependable way to make a start from the BLE receiver legal.
- [V] A foreground service of type `microphone` cannot be created from the background at all without a WIU exemption: "the system sees that your app doesn't currently have the required permissions, and it throws a `SecurityException`". Logcat marker: `Foreground service started from background can not have location/camera/microphone access: service …`.
- [V] Android 15: `BOOT_COMPLETED` receivers may not launch `dataSync`, `camera`, `mediaPlayback`, `phoneCall`, `mediaProjection` or `microphone` services. `connectedDevice` is not on that list.
- [V] Android 15, target 35: an app "must be the top app or running a foreground service in order to request audio focus".
- [V] Android 15, all apps: "the system also cancels all pending intents when the app enters the stopped state". The app gets `ACTION_BOOT_COMPLETED` when the user takes it out of that state, and `ApplicationStartInfo.wasForceStopped()` tells it what happened. A BLE scan registered with a PendingIntent is presumably lost too: [I].
- [V] Android 16: new intents `ACTION_KEY_MISSING` and `ACTION_ENCRYPTION_CHANGE` for bond loss; `removeBond(int)` for target 36.

**Assistant role** [V] (`PermissionController/res/xml/roles.xml`, tag `android-17.0.0_r1`)

- `android.app.role.ASSISTANT` is `exclusive="true"`, `requestable="false"`, `showNone="true"`. The app cannot pop up a role request; the user picks it in Settings.
- It qualifies through a `VoiceInteractionService` (with `sessionService`, `recognitionService`, `supportsAssist = true` in the metadata) or an activity handling `android.intent.action.ASSIST`.
- The role grants, among others, a permission new in this release: `android.permission.MANAGE_ASSISTANT_AUDIO`, protection level `signature|role`, behind the feature flag `android.media.audio.manage_assistant_audio_permission`. Whether the flag is on in the Pixel's build and what the permission allows: [U] – worth a look by whoever researches audio routing.

**Hearing aids in Android 17** [V] (launch blog post): new `AudioDeviceInfo.TYPE_BLE_HEARING_AID` so an app "can distinguish hearing aids from regular headsets", and a user setting for "granular audio routing for hearing aids" that lets users send notifications, ringtones and alarms to the hearing aids or the phone speaker. The second point means the audio usage chosen for speech output decides where it plays. Which usage ends up in a Phonak device: [U], device test.

---

## 2. Command-line build on Linux Mint 21.2, then the IDE

### 2.1 What the machine has today [V] (read-only checks on the machine)

- OpenJDK 11.0.33 only (`/usr/lib/jvm/java-11-openjdk-amd64`). Too old: AGP 9.5 needs 17.
- No `adb`, no `sdkmanager`, no Gradle, no SDKMAN. `curl`, `wget`, `unzip`, `flatpak` present.
- glibc 2.35 (Android Studio needs 2.31 or later), 62 GB RAM, 147 GB free disk.
- The user is already in the `plugdev` group. No Android udev rules are installed.
- apt candidates: `openjdk-17-jdk-headless` 17.0.21, `openjdk-21-jdk-headless` 21.0.13, `openjdk-25-jdk-headless` 25.0.5, `android-sdk-platform-tools-common` 28.0.2+6 (udev rules only, 39 kB). The apt `adb` package is version `1:10.0.0+r36` – **do not use it**; current platform-tools is 37.0.3.

### 2.2 JDK 21

Pick one. All three leave OpenJDK 11 untouched.

**With sudo** [V candidate exists]

```bash
sudo apt install openjdk-21-jdk-headless
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

**Without sudo – Temurin tarball** [V download exists, from api.adoptium.net]

```bash
mkdir -p ~/.local/opt && cd ~/.local/opt
curl -fLO 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13.1%2B2/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13.1_2.tar.gz'
# compare with the checksum shown by:
#   curl -s 'https://api.adoptium.net/v3/assets/latest/21/hotspot?os=linux&architecture=x64&image_type=jdk'
sha256sum OpenJDK21U-jdk_x64_linux_hotspot_21.0.13.1_2.tar.gz
tar xzf OpenJDK21U-jdk_x64_linux_hotspot_21.0.13.1_2.tar.gz
export JAVA_HOME="$HOME/.local/opt/jdk-21.0.13.1+2"
```

**Without sudo – reuse the IDE's runtime** [I]: once Android Studio is unpacked, `export JAVA_HOME=~/.local/opt/android-studio/jbr`. One JDK for IDE and terminal, which the Android docs recommend for consistent results ("make sure you set your `JAVA_HOME` … to that same JDK", https://developer.android.com/build/jdks).

**Later refinement – let Gradle fetch its own JDK** [V feature, I that JDK 11 is enough to launch it]: Gradle's "Daemon JVM criteria" (`./gradlew updateDaemonJvm --jvm-version=21`) writes `gradle/gradle-daemon-jvm.properties`, which is checked in and lets Gradle auto-provision the daemon JDK. The compatibility page says the wrapper and client "are compatible with JVM 8", so the installed JDK 11 could start the wrapper. Good for contributors and CI parity; not needed on day one.

### 2.3 Android SDK command-line tools

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools" && cd /tmp
curl -fLO https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip
echo '4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583  commandlinetools-linux-15859902_latest.zip' | sha256sum -c
unzip -q commandlinetools-linux-15859902_latest.zip -d "$ANDROID_HOME/cmdline-tools"
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"

export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"

yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;36.0.0"
# optional, for stepping into framework code and for 37.2 APIs:
sdkmanager "sources;android-37.2" "platforms;android-37.2"
```

- [V] File name, size (181.8 MB) and SHA-256 are from https://developer.android.com/studio ("Command line tools only").
- [V] The `latest` sub-directory layout is the documented one (https://developer.android.com/tools/sdkmanager).
- [V] Package names are from the SDK repository index. `build-tools;36.0.0` is AGP 9.5's default.
- [V] Accepting licences matters beyond the first install: the `licenses` directory "is necessary for Gradle to auto-download missing packages" (https://developer.android.com/studio/intro/update). So the minimum is really `sdkmanager --licenses` plus `platform-tools`; Gradle fetches the rest on first build.
- [I] `sdkmanager` itself needs Java 17 or newer, so do 2.2 first.
- [V] **`sdkmanager` is deprecated:** "The sdkmanager tool is deprecated. Instead, use the Android CLI `android sdk` command." The new tool installs without root via `curl -fsSL https://dl.google.com/android/cli/latest/linux_x86_64/install.sh | bash` or from a Google apt repository (https://developer.android.com/tools/agents/android-cli/download). It collects usage metrics unless run with `--no-metrics`. It is optional; the classic route above is what the GitHub runner images still use.

**Environment variables** (append to `~/.profile` or `~/.bashrc`)

```bash
export JAVA_HOME="$HOME/.local/opt/jdk-21.0.13.1+2"        # or the apt path
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$JAVA_HOME/bin:$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
```

[V] `ANDROID_HOME` is the supported variable; "`ANDROID_SDK_ROOT` … is deprecated" (https://developer.android.com/tools/variables). `ANDROID_SERIAL` selects a device when several are attached.

**First build** [I]: the project needs a Gradle wrapper. Easiest is to let Android Studio's "New Project" wizard (or `android create`) generate the skeleton with wrapper, then commit it. After that: `./gradlew :app:assembleDebug` and `./gradlew :app:installDebug`.

### 2.4 adb over USB: udev

[V] Google's page says two things are needed on Ubuntu-family systems: membership of `plugdev` (already true here) and udev rules, for which "the `android-sdk-platform-tools-common` package contains a community-maintained default set" (https://developer.android.com/studio/run/device).

```bash
# with sudo, either the package (rules only, it does not pull in the old adb):
sudo apt install android-sdk-platform-tools-common
# or one hand-written rule for Google devices (vendor id 18d1):
echo 'SUBSYSTEM=="usb", ATTR{idVendor}=="18d1", MODE="0660", GROUP="plugdev", TAG+="uaccess"' \
  | sudo tee /etc/udev/rules.d/51-android.rules
sudo udevadm control --reload-rules && sudo udevadm trigger
```

Then: phone → Settings → About phone → tap "Build number" seven times → Developer options → USB debugging → plug in → accept the RSA fingerprint → `adb devices -l`.

[I] Without sudo there is no way to add udev rules. Use wireless debugging, which needs none.

### 2.5 Wireless debugging [V] (https://developer.android.com/tools/adb)

1. Phone and PC on the same Wi-Fi network (no client isolation).
2. Developer options → Wireless debugging → on → allow for this network.
3. "Pair device with pairing code" shows IP, port and a six-digit code.
4. `adb pair <ip>:<pairing-port>` and type the code. Pairing is needed once per workstation.
5. `adb connect <ip>:<port>` with the port from the main Wireless debugging screen (it differs from the pairing port), or wait for mDNS auto-connect. `adb devices -l` to confirm.
6. Trouble: `adb mdns track-services --proto-text` shows whether the network passes mDNS at all (documented). `adb mdns check`, `adb mdns services` and `adb kill-server` are the usual next steps [I].

Why this matters for this project: a USB cable charges the phone, and a charging phone does not enter Doze. For real screen-off tests use wireless adb, or fake the unplugged state with `adb shell dumpsys battery unplug` (undo with `dumpsys battery reset`).

[U] Whether wireless debugging stays on across reboots and network changes on Android 17, and whether an open wireless adb session itself delays deep Doze. Check both before trusting an overnight capture.

### 2.6 Full IDE: three ways to install

| Option | How | Sudo | Verdict |
|---|---|---|---|
| **Tarball from Google** | Download `android-studio-rabbit2-patch3-linux.tar.gz` (1.6 GB, SHA-256 above), unpack to `~/.local/opt/android-studio`, run `bin/studio`. Point it at the existing `$ANDROID_HOME`. | No | **Recommended.** Official build, no sandbox between IDE, adb and USB. Updates through the IDE. |
| **JetBrains Toolbox** | Toolbox App 3.9.2 (2026-11-24, `jetbrains-toolbox-3.9.2.89815.tar.gz`) installs and updates Android Studio per user and can keep stable and canary side by side with rollback. | No | Good if several IDE versions are wanted. One more updater in the chain. [V] Toolbox version from the JetBrains release feed; [I] that it lists Android Studio. |
| **Flatpak** | `com.google.AndroidStudio` on Flathub is at 2026.2.2.11. | No | **Not recommended here.** [V] Flathub reports the app as "Not verified", so it is a community repackaging, not Google's. [I] The sandbox complicates adb, udev, `JAVA_HOME` and the SDK path. |

[V] Google's install page still lists 32-bit libraries for Ubuntu (`libc6:i386 libncurses5:i386 libstdc++6:i386 lib32z1 libbz2-1.0:i386`). [U] whether anything in this project needs them; try without.

### 2.7 What the IDE adds for debugging

All of the build can be done from the terminal. The IDE earns its place for these:

- **Attach debugger to Android process** [V] – attach to the already running service over wireless adb, without restarting it. Essential for a process that was started by a broadcast.
- **Non-suspending breakpoints** ("Evaluate and log") [I] – add log output to a running debug build without rebuilding.
- **Stepping into framework code** [I] with `sources;android-37.x` installed. Framework sources match the SDK level, not necessarily the phone's exact monthly build.
- **Logcat window** [V] with query filters such as `package:mine level:warn tag:BtGatt`.
- **App Inspection** [I] – Background Task Inspector (jobs, alarms, wake locks), Network Inspector (OkHttp traffic), Database Inspector.
- **Profiler** [V for the power part] – CPU and system traces, heap dumps, and the Power Profiler which reads the On Device Power Rails Monitor, "available … on Pixel 6 and subsequent Pixel devices" (https://developer.android.com/studio/profile/power-profiler).
- **App Compatibility Changes** [V] – per-app toggles for target-gated changes; "You must use a debuggable build variant" (https://developer.android.com/guide/app-compatibility/test-debug).
- Device Explorer, APK Analyzer, lint with API-level checks, Compose preview [I].

One trap [I]: the IDE's Run button can install APKs flagged test-only, which behave differently on reinstall. For builds that must stay on the phone, install artefacts produced by `./gradlew` or CI.

---

## 3. Signed release on a version tag (GitHub Actions)

### 3.1 Why one stable key for both debug and release

- [V] "When the system is installing an update to an app, it compares the certificate(s) in the new version with those in the existing version. The system allows the update if the certificates match" (https://developer.android.com/studio/publish/app-signing). A different key means uninstall first.
- [I] For this app an uninstall is expensive: it drops the assistant role, every runtime permission, the battery-optimisation exemption, the notification channel settings, the BLE scan registration and the event journal. All of it would have to be redone by hand, on an unlocked screen.
- [I] The assistant role and the BLE scan belong to one package name. A second package (`…​.debug`) installed alongside would compete for the same role and scan the same button. So: **no `applicationIdSuffix`; debug and release share the applicationId and the key** and replace each other in place.
- [V] The default debug key is machine-local: Studio "automatically creates the debug keystore and certificate in `$HOME/.android/debug.keystore`" (30-year validity). A CI debug build and a laptop debug build therefore do not match unless the key is supplied explicitly.
- [V] Google's developer verification, when it reaches Germany, registers a package name together with "the SHA-256 certificate fingerprint of your signing key". Choose applicationId and key once.
- [I] `versionCode` must never go down for a non-debuggable installed build. `adb install -d` ("Allow version code downgrade") only helps when the installed build is debuggable. Deriving the code from the version triple, locally and in CI, keeps both in step.

### 3.2 Create the key, store it as secrets

```bash
# 1. Generate. No personal data in the DN – the certificate is public in every APK.
keytool -genkeypair -v -storetype PKCS12 \
  -keystore lara-release.p12 -alias lara \
  -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 36500 \
  -dname "CN=Lara im Ohr"

# 2. Note the fingerprint (goes into README, Obtainium, later the developer console).
keytool -list -v -keystore lara-release.p12 -alias lara | grep 'SHA256:'

# 3. Put it into GitHub (repository secrets, or secrets of an environment named "release").
base64 -w0 lara-release.p12 | gh secret set LARA_KEYSTORE_BASE64
gh secret set LARA_KEYSTORE_PASSWORD      # prompts
gh secret set LARA_KEY_ALIAS --body lara
gh secret set LARA_KEY_PASSWORD           # PKCS12: same value as the store password
gh variable set LARA_CERT_SHA256 --body '<hex fingerprint without colons, lower case>'

# 4. Back up lara-release.p12 and the password outside the repo (password manager). Never commit them.
```

[I] The commands are standard JDK `keytool` and `gh` usage; they were not executed here.

Local builds read the same four values from a git-ignored `keystore.properties` in the project root:

```properties
LARA_KEYSTORE_FILE=/home/<user>/.keys/lara-release.p12
LARA_KEYSTORE_PASSWORD=…
LARA_KEY_ALIAS=lara
LARA_KEY_PASSWORD=…
```

Hardening for a public repository [I]:

- Put the secrets in a GitHub *environment* (`release`) with required reviewers, so a pushed tag cannot use the key without an approval click.
- Protect `v*` tags with a tag ruleset.
- Secrets are not exposed to workflows triggered from forks; keep the release job on `push: tags` only.
- Pin actions to commit SHAs (listed in 3.5).

### 3.3 Gradle configuration (AGP 9.5, Kotlin DSL)

`gradle/libs.versions.toml`

```toml
[versions]
agp = "9.5.3"
kotlin = "2.4.21"
composeBom = "2026.11.01"
media3 = "1.11.1"
coroutines = "1.11.0"
serialization = "1.13.0"
okhttp = "5.5.1"
coreKtx = "1.19.1"
lifecycle = "2.11.1"
activityCompose = "1.13.0"
datastore = "1.3.0"
# ksp = "2.3.14"   # only when an annotation processor (Room, …) is added

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

Root `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false            // puts KGP 2.4.21 on the classpath
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

`app/build.gradle.kts`

```kotlin
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)      // built-in Kotlin: no kotlin-android plugin
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ---- version: from the tag in CI, from git locally ----------------------
fun latestTagVersion(): String? = runCatching {
    providers.exec {
        commandLine("git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().removePrefix("v").ifEmpty { null }
}.getOrNull()

fun versionCodeOf(name: String): Int {
    val (major, minor, patch) = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(name)?.destructured
        ?: error("versionName must start with MAJOR.MINOR.PATCH, got '$name'")
    require(minor.toInt() < 1000 && patch.toInt() < 1000)
    return major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()
}

val appVersionName: String =
    providers.gradleProperty("appVersionName").orNull ?: latestTagVersion() ?: "0.0.0"
val appVersionCode: Int =
    providers.gradleProperty("appVersionCode").orNull?.toInt() ?: versionCodeOf(appVersionName)

// ---- signing: environment first (CI), then keystore.properties (local) ---
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use(::load)
}
fun secret(name: String): String? =
    providers.environmentVariable(name).orNull ?: keystoreProps.getProperty(name)

android {
    namespace = "<your.namespace>"
    compileSdk = 37

    defaultConfig {
        applicationId = "<your.application.id>"      // choose once, never change
        minSdk = 36
        targetSdk = (providers.gradleProperty("targetSdk").orNull ?: "37").toInt()
        versionName = appVersionName
        versionCode = appVersionCode
    }

    signingConfigs {
        create("lara") {
            secret("LARA_KEYSTORE_FILE")?.let { path ->
                storeFile = file(path)
                storePassword = secret("LARA_KEYSTORE_PASSWORD")
                keyAlias = secret("LARA_KEY_ALIAS")
                keyPassword = secret("LARA_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        val lara = signingConfigs.getByName("lara").takeIf { it.storeFile != null }
        debug {
            // same applicationId, same key as release → in-place switch between the two
            signingConfig = lara ?: signingConfigs.getByName("debug")   // fallback for forks
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = lara          // null → "app-release-unsigned.apk"; CI fails on that
            isDebuggable = false
            isProfileable = true          // Perfetto, simpleperf and heap sampling on the release build
            isMinifyEnabled = false       // open source, tiny app: keep stack traces readable
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
}
```

Notes on that file:

- [V] `isProfileable`, `isDebuggable`, `isMinifyEnabled`, `signingConfig` exist on `ApplicationBuildType` in the AGP 9 DSL reference.
- [I] Declaring the Kotlin plugins at 2.4.21 in the root is expected to raise AGP's bundled KGP 2.2.10 to 2.4.21. The AGP notes document a `buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:…") } }` block for the same purpose; use that if `./gradlew buildEnvironment` still shows 2.2.10.
- [I] A pre-release tag such as `v0.4.0-rc1` gets the same versionCode as `v0.4.0`. Installing the final build over the candidate is a same-code reinstall, which is allowed.
- [I] `providers.exec` keeps the build compatible with Gradle's configuration cache. The whole file is a proposal and has not been compiled.

`.gitignore` additions: `keystore.properties`, `*.jks`, `*.p12`, `*.keystore`, `.kotlin/`, `local.properties`.

### 3.4 Workflow

`.github/workflows/release.yml`

```yaml
name: release

on:
  push:
    tags: ['v[0-9]+.[0-9]+.[0-9]+*']

permissions:
  contents: read

concurrency:
  group: release-${{ github.ref }}
  cancel-in-progress: false

jobs:
  apk:
    runs-on: ubuntu-24.04
    timeout-minutes: 30
    environment: release              # optional: environment secrets + required reviewer
    permissions:
      contents: write                 # create the release, upload assets
      id-token: write                 # build provenance attestation
      attestations: write
      artifact-metadata: write

    steps:
      - uses: actions/checkout@v7
        with:
          persist-credentials: false

      - name: Version from tag
        id: ver
        run: |
          name="${GITHUB_REF_NAME#v}"
          if [[ ! "$name" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-[0-9A-Za-z.]+)?$ ]]; then
            echo "::error::tag '$GITHUB_REF_NAME' is not vMAJOR.MINOR.PATCH[-suffix]"; exit 1
          fi
          code=$(( 10#${BASH_REMATCH[1]} * 1000000 + 10#${BASH_REMATCH[2]} * 1000 + 10#${BASH_REMATCH[3]} ))
          echo "name=$name" >> "$GITHUB_OUTPUT"
          echo "code=$code" >> "$GITHUB_OUTPUT"
          if [[ "$name" == *-* ]]; then echo "prerelease=true" >> "$GITHUB_OUTPUT"; else echo "prerelease=false" >> "$GITHUB_OUTPUT"; fi

      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '21'

      - uses: gradle/actions/setup-gradle@v6
        with:
          cache-disabled: true        # a signed release should not be built from a restored cache

      - name: Decode keystore
        env:
          LARA_KEYSTORE_BASE64: ${{ secrets.LARA_KEYSTORE_BASE64 }}
        run: |
          install -m 700 -d "$RUNNER_TEMP/ks"
          printf '%s' "$LARA_KEYSTORE_BASE64" | base64 --decode > "$RUNNER_TEMP/ks/lara.p12"
          chmod 600 "$RUNNER_TEMP/ks/lara.p12"

      - name: Build signed release APK
        env:
          LARA_KEYSTORE_FILE: ${{ runner.temp }}/ks/lara.p12
          LARA_KEYSTORE_PASSWORD: ${{ secrets.LARA_KEYSTORE_PASSWORD }}
          LARA_KEY_ALIAS: ${{ secrets.LARA_KEY_ALIAS }}
          LARA_KEY_PASSWORD: ${{ secrets.LARA_KEY_PASSWORD }}
          VERSION_NAME: ${{ steps.ver.outputs.name }}
          VERSION_CODE: ${{ steps.ver.outputs.code }}
        run: |
          ./gradlew --no-daemon --stacktrace :app:assembleRelease \
            -PappVersionName="$VERSION_NAME" -PappVersionCode="$VERSION_CODE"

      - name: Verify signature, rename, checksum
        id: pkg
        env:
          VERSION_NAME: ${{ steps.ver.outputs.name }}
          EXPECTED_CERT_SHA256: ${{ vars.LARA_CERT_SHA256 }}
        run: |
          apk=app/build/outputs/apk/release/app-release.apk
          if [[ ! -f "$apk" ]]; then
            echo "::error::signed APK missing – signing secrets not set?"; ls -R app/build/outputs/apk || true; exit 1
          fi
          bt="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
          "$bt/apksigner" verify --verbose --print-certs "$apk" | tee cert.txt
          cert="$(grep -m1 'certificate SHA-256 digest' cert.txt | awk '{print $NF}')"
          if [[ -n "$EXPECTED_CERT_SHA256" && "$cert" != "$EXPECTED_CERT_SHA256" ]]; then
            echo "::error::APK signed with unexpected certificate $cert"; exit 1
          fi
          mkdir dist
          cp "$apk" "dist/lara-im-ohr-${VERSION_NAME}.apk"
          ( cd dist && sha256sum ./*.apk > SHA256SUMS.txt && cat SHA256SUMS.txt )
          echo "cert=$cert" >> "$GITHUB_OUTPUT"

      - uses: actions/attest@v4
        with:
          subject-path: dist/*.apk

      - uses: softprops/action-gh-release@v3
        with:
          files: |
            dist/*.apk
            dist/SHA256SUMS.txt
          fail_on_unmatched_files: true
          generate_release_notes: true
          prerelease: ${{ steps.ver.outputs.prerelease }}
          body: |
            Signing certificate SHA-256: `${{ steps.pkg.outputs.cert }}`
            Check the download: `sha256sum -c SHA256SUMS.txt`
            Check where it was built: `gh attestation verify lara-im-ohr-${{ steps.ver.outputs.name }}.apk --repo ${{ github.repository }}`

      - name: Remove keystore
        if: always()
        run: rm -rf "$RUNNER_TEMP/ks"
```

What is verified about that workflow, and what is not:

- [V] Action major versions current in November 2026: `actions/checkout` v7.0.1, `actions/setup-java` v6.3.0, `gradle/actions` v6.4.1, `softprops/action-gh-release` v3.0.4, `actions/attest` v4.2.2 (GitHub releases API).
- [V] `actions/attest-build-provenance` v4 is now "simply a wrapper on top of `actions/attest`"; "new implementations should use `actions/attest` instead". Required permissions `id-token: write`, `attestations: write`, `artifact-metadata: write` are from its README. Attestations are available for public repositories.
- [V] `softprops/action-gh-release` needs `contents: write`; inputs `files`, `fail_on_unmatched_files`, `generate_release_notes`, `prerelease`, `body` exist in the v3 README.
- [V] `gradle/actions/setup-gradle@v6` validates wrapper JARs on every run and supports `cache-disabled: true`. Its default cache provider is a proprietary "Enhanced Caching" library that is free for public repositories; `cache-provider: basic` selects the MIT one. Disabling the cache avoids the question.
- [V] `ubuntu-24.04` runners ship JDK 17 (default), 21 and 25, `ANDROID_HOME=/usr/local/lib/android/sdk`, platform-tools 37.0.3, build-tools 36.0.0 and 37.0.0, platforms android-37.0, 37.1 and 37.2. No SDK set-up step is needed. `ubuntu-latest` already means Ubuntu 26.04 with JDK 25 as default, which is why the workflow names its image and its JDK.
- [I] The exact `apksigner` output line ("certificate SHA-256 digest") and the unsigned-file naming are long-standing behaviour, not re-checked today.
- **The YAML has not been run.** Expect one or two fixes on the first tag.

A second workflow for pull requests and `main` (no secrets) should run `./gradlew :core:test :app:lintDebug :app:assembleDebug`.

Optional [I]: attach a second asset `lara-im-ohr-<ver>-debug.apk`, built with the same key and versionCode. The owner can then swap to the debuggable build of the *same* version in the field with one `adb install -r`. Obtainium then needs an APK filter so it ignores `-debug`.

### 3.5 Pinned action references [V]

```
actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1            # v7.0.1
actions/setup-java@d1374f8ac74474200d16ef8ecee22c7ae7a51880          # v6.3.0
gradle/actions/setup-gradle@8da46b539cd8fe6ab27ca75128919a18d757604c # v6.4.1
actions/attest@1e69f48acb82d1966a394da916b4c1698aa569d6              # v4.2.2
softprops/action-gh-release@8a0cd651e5b2365bd640854a3fa5dfc70e4875f3 # v3.0.4
```

---

## 4. Getting the APK onto the phone

### 4.1 Three routes

| Route | Steps | Confirmation on the phone | Good for |
|---|---|---|---|
| **adb** | `adb install -r lara-im-ohr-<ver>.apk` | None | Development, first install, scripted set-up. Exempt from developer verification. |
| **Browser download** from the release page | Download in Chrome → allow "Install unknown apps" for Chrome once → install | Install dialog; possibly Play Protect's "App scan recommended" | Updating away from the PC. |
| **Obtainium** | Add the repository URL once; Obtainium checks GitHub Releases and installs updates | First install and possibly every update (see 4.2) | Automatic updates from a public repository. |

adb extras that save unlocked-screen time:

```bash
adb install -r -g lara.apk                               # -g: grant all runtime permissions in the manifest
adb shell pm grant <pkg> android.permission.RECORD_AUDIO  # or one by one
adb shell dumpsys deviceidle whitelist +<pkg>            # battery-optimisation exemption
adb shell cmd role add-role-holder android.app.role.ASSISTANT <pkg>
adb shell cmd role get-role-holders android.app.role.ASSISTANT
```

- [V] `-r`, `-g`, `-d`, `-t`, `-i installer_package_name` are documented `adb install` options.
- [V] `dumpsys deviceidle whitelist [package ...]` – "Add (prefix with +) or remove (prefix with -) packages" (AOSP `DeviceIdleController.java`, 17.0.0_r1).
- [V] `cmd role add-role-holder [--user USER_ID] ROLE PACKAGE [FLAGS]` and `get-role-holders` exist (AOSP `RoleShellCommand.java`, 17.0.0_r1). There is also `set-bypassing-role-qualification true|false` for experiments with an app that does not yet qualify.
- [U] Whether the shell user may assign the assistant role on a retail Pixel on Android 17 without a confirmation. If it works, the whole device set-up fits in one script.

### 4.2 Obtainium

- [V] Obtainium (`ImranR98/Obtainium`, GPL-3.0, v1.7.5 of 2026-11-23, about 22 000 stars) installs and updates apps "directly from their releases pages". GitHub is a first-class source.
- [V] It uses the GitHub API; unauthenticated use is rate-limited, and a personal access token can be added.
- [V] Options that matter here: "Filter APKs by Regular Expression" (when a release has more than one APK the user is asked to pick, and then "the app will not be updated silently in the background"), include or exclude pre-releases, "Verify Latest Tag", and "expected signing certificate hashes".
- [V] Deep links: `obtainium://add/<url>` opens the add-app screen with the repository pre-filled. `obtainium://app/<url-encoded JSON>` carries a full configuration and is what a "Get it on Obtainium" badge in a README uses (https://wiki.obtainium.imranr.dev/deep_links/).
- **Silent updates** [V for the rule]: Android lets an installer skip the confirmation only when the app targets API 35 or higher on Android 17 **and** the installer is the update owner or the installer of record of the existing install.
  - [I] An app first installed with `adb` has no such installer, so Obtainium's first update will ask. After that one confirmed update Obtainium should be the installer of record and later updates can be silent.
  - [I] A later `adb install` resets that again.
  - [I] `adb install -i dev.imranr.obtainium lara.apk` may set Obtainium as installer from the start. Untested.
  - Check with: `adb shell dumpsys package <pkg> | grep -i -E 'installerPackageName|initiatingPackageName|updateOwner|packageSource'`.
- [I] Updates replace the APK and restart the process. Do them at the desk, not mid-day in the pocket: the journal should record the `REASON_PACKAGE_UPDATED` exit and whether BLE scanning and the assistant binding came back by themselves.
- One release asset, one stable file-name pattern (`lara-im-ohr-<version>.apk`) keeps Obtainium configuration trivial.

### 4.3 Sideloading hurdles on Android 15, 16 and 17

**Restricted settings / Enhanced Confirmation Mode (ECM)** [V] (`packages/modules/Permission/service/java/com/android/ecm/EnhancedConfirmationService.java`, tag `android-17.0.0_r1`)

The complete protected list in that file:

```java
// Runtime permissions
SEND_SMS, RECEIVE_SMS, READ_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH, READ_CELL_BROADCASTS, permission_group.SMS
BIND_DEVICE_ADMIN
// App ops
OPSTR_BIND_ACCESSIBILITY_SERVICE, OPSTR_ACCESS_NOTIFICATIONS, OPSTR_SYSTEM_ALERT_WINDOW,
OPSTR_GET_USAGE_STATS, OPSTR_LOADER_USAGE_STATS
// Default application roles
RoleManager.ROLE_DIALER, RoleManager.ROLE_SMS
```

- Not on the list: `ROLE_ASSISTANT`, `RECORD_AUDIO`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS`, battery-optimisation exemption. **None of the app's planned needs is restricted.**
- An app counts as guarded when its package source is a local or downloaded file, or when its installer is not on the device's trusted-installer list. A browser download, Obtainium and probably adb all fall in that group on a Pixel. [I] for the Pixel's actual allowlist.
- It becomes relevant only if the app later adds a **NotificationListenerService**, an **AccessibilityService**, an overlay or usage-stats access. Then the user must tap App info → ⋮ → "Allow restricted settings" once. [I] The state is stored as the app-op `ACCESS_RESTRICTED_SETTINGS`, so `adb shell appops set <pkg> ACCESS_RESTRICTED_SETTINGS allow` should lift it too.
- The same file blocks granting "install unknown apps" and accessibility during a phone call with an unknown number (feature flag `unknownCallPackageInstallBlockingEnabled`).

**Play Protect** [V] (https://developers.google.com/android/play-protect/warning-dev-guidance)

- "App scan recommended": "Play Protect hasn't seen this app before. To protect your device and data, send some info about the app to Google for scanning before you install." [I] Every self-built APK is new to Google, so expect this prompt on browser and Obtainium installs. It can be declined.
- "App blocked to protect your device": installs from "Internet-sideloading sources" (browsers, messaging apps, file managers) are blocked automatically when the app declares `RECEIVE_SMS`, `READ_SMS`, `NOTIFICATION_LISTENER` or `ACCESSIBILITY`. "This protection is active in select markets." [U] whether Germany is one of them. Another reason to stay away from those four.
- Warnings for apps "built for older Android versions" do not apply at target 37.
- [U] Whether Play Protect's "Verify apps over USB" developer option is on by default and whether it interferes with `adb install`.

**Other platform rules**

- [V] Advanced Protection Mode (Android 16+) lists "Blocked app sideloading" among its protections (https://developer.android.com/privacy-and-security/advanced-protection-mode). If the owner ever enables it, only adb remains. [U] whether adb installs are also blocked.
- [V] Since Android 15, force-stopping the app cancels its PendingIntents (see 1.4).

**Developer verification – status for Germany** [V] (https://developer.android.com/developer-verification, its guides and FAQ, pages updated up to 10 November 2026)

- "Starting September 30, 2026, these new developer verification protections go live for users in Brazil, Indonesia, Singapore, and Thailand."
- In that phase "the system verifies app installations from the following app stores": Google Play, HONOR App Market, OPPO App Market, Galaxy Store, Palm Store, V-Appstore, GetApps. "If you distribute your app through other stores, or if users sideload your app directly, these new verification requirements won't apply to your app yet."
- "Verification capability will be expanded globally for all apps on certified Android devices in 2027." No date for Germany or the EU was found.
- It "will apply to all certified Android devices running Android 8 (API level 26) or higher" and is "delivered through Google Play services", so it does not depend on the Android version.
- **`adb` stays free:** "As a developer, you are free to install apps without verification with ADB." "ADB workflow and experience stays the same."

**So today in Germany nothing changes for this project.** When the global phase arrives there are four ways through, in order of effort:

| Path | Cost | What it gives |
|---|---|---|
| Keep installing with adb | nothing | Works for the owner. No help for other users of the public repository. |
| "Advanced flow" on the phone [V] | One-time: enable developer mode, confirm you are not being coached, restart, wait one day, confirm with biometrics or PIN. Then choose 7 days or indefinitely. | Installs from unverified developers keep working, with a warning and "Install Anyway". Needed once per phone, also for Obtainium. |
| Limited distribution account [V] | Free, no government ID. Needs a Google account with 2-step verification and a payments profile holding legal name and address. | Share with a fixed number of authorised devices. The FAQ and guides say "up to 20 devices"; the limited-distribution page says "up to 50". The official pages disagree. |
| Full verification [V] | 25 USD, government ID, register package name plus signing-key SHA-256. There is a dedicated guide for apps on open-source repositories. | Anyone can install the GitHub release with no extra step. |

Design consequence: fix the applicationId and the signing key now, because registration ties the two together.

### 4.4 Does any of this hurt the four things the app needs?

| Need | Affected by sideloading? | How it is granted |
|---|---|---|
| Assistant role | **No** [V: not in the ECM list; role XML has no installer condition] | User selects it in Settings (the role is not requestable from code); possibly `cmd role add-role-holder` [U]. |
| Bluetooth scanning (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`) | **No** [V] – protection level `dangerous`, normal runtime dialog | Dialog, `adb install -g` or `pm grant`. |
| Microphone (`RECORD_AUDIO`) | **No** [V] – `dangerous\|instant` | Same. The real limit is while-in-use capability (1.4), not installation. |
| Battery-optimisation exemption | **No** [V] – `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` is a normal permission; the Play policy against asking for it does not apply outside Play | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dialog or `dumpsys deviceidle whitelist +<pkg>`. |

Sideloading plus adb actually **widens** what the app may do. These permissions carry the `development` protection flag in the Android 17 framework manifest [V] (`core/res/AndroidManifest.xml`, 17.0.0_r1) and can therefore be granted with `adb shell pm grant` to any app that declares them:

| Permission | Protection level | Possible use |
|---|---|---|
| `READ_LOGS` | `signature\|privileged\|development` | Read system-wide logcat. Limited in practice, see 5.2. |
| `DUMP` | `signature\|privileged\|development` | Run `dumpsys`-style dumps from inside the app. |
| `PACKAGE_USAGE_STATS` | `signature\|privileged\|development\|appop\|retailDemo` | Standby bucket history, usage events. |
| `BATTERY_STATS` | `signature\|privileged\|development` | Battery statistics. |
| `WRITE_SECURE_SETTINGS` | `signature\|privileged\|development\|role\|installer` | Change secure and global settings. |
| `READ_DROPBOX_DATA`, `GET_APP_OPS_STATS` | `signature\|privileged\|development` | Crash and ANR drop-box entries, app-op history. |
| `SET_MEDIA_KEY_LISTENER`, `SET_VOLUME_KEY_LONG_PRESS_LISTENER` | `signature\|privileged\|development` | Guard hidden `MediaSessionManager` listener APIs that see media keys before normal session routing. Potentially a stronger path C. |

- [I] The APIs behind the last row are hidden, so they also need reflection and a hidden-API exemption. [V] `adb shell settings put global hidden_api_policy 1` disables hidden-API enforcement device-wide (https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces). Whether this still works for those two APIs on Android 17 is [U] and belongs to the media-button research.
- [V] Not reachable this way: `BLUETOOTH_PRIVILEGED` (`signature|privileged`), `MODIFY_AUDIO_ROUTING` and `CAPTURE_AUDIO_HOTWORD` (`signature|privileged|role`).
- [U] Shizuku (`RikkaApps/Shizuku`, Apache-2.0) offers shell-level API access from apps, but its last release is v13.6.0 from May 2025; compatibility with Android 17 is unknown.

---

## 5. Debugging toolkit for "screen off, hours later, in a pocket"

The failure being hunted has three properties: nobody is watching, the logcat ring buffer has long rolled over, and attaching a cable changes the power state. The toolkit therefore has three layers: what the app records about itself, what can be captured unattended, and what can be forced at the desk.

### 5.1 Layer 1: persistent in-app event journal

This is the most valuable single piece and should exist before the first feature.

**Format** – append-only JSON Lines, one event per line, written through one single-threaded writer:

```json
{"seq":4812,"wall":"2026-11-27T14:03:22.418+01:00","elapsed":98234112,"uptime":3511876,
 "boot":57,"proc":"a3f9","pid":21344,"src":"BLE","ev":"adv_received",
 "d":{"button":2,"click":"single","rssi":-71,"packetId":188},
 "state":{"interactive":false,"locked":true,"idle":"deep","bucket":"ACTIVE","charging":false,
          "fgs":false,"importance":125,"btOn":true,"audioOut":"BLE_HEARING_AID"}}
```

**Fields that make later analysis possible** [I]

- `elapsed` = `SystemClock.elapsedRealtime()` (counts sleep) and `uptime` = `SystemClock.uptimeMillis()` (does not). The difference between two lines shows how long the CPU slept in between.
- `boot` = `Settings.Global.BOOT_COUNT`, `proc` = random id chosen at process start. Together they show reboots and process restarts at a glance.
- State snapshot on every input event: `PowerManager.isInteractive`, `isDeviceIdleMode`, `isDeviceLightIdleMode`, `KeyguardManager.isDeviceLocked`, `UserManager.isUserUnlocked`, charging, `isIgnoringBatteryOptimizations`, `UsageStatsManager.getAppStandbyBucket()`, own process importance, Bluetooth adapter state, current audio output and communication device.
- One event per hop of the pipeline with a shared correlation id: advertisement received → parsed → de-duplicated → routed → state machine transition → service start requested → service started → audio focus result → first audio frame → network request → response. Latency per hop falls out for free.

**At every process start, record why the last one ended and why this one began** [V APIs]

- `ActivityManager.getHistoricalProcessExitReasons()` → `ApplicationExitInfo`: reason, importance, description, PSS/RSS, timestamp, `getTraceInputStream()` for ANRs and native crashes. Reasons present in the API 37 reference: `REASON_ANOMALY`, `ANR`, `CRASH`, `CRASH_NATIVE`, `DEPENDENCY_DIED`, `EXCESSIVE_RESOURCE_USAGE`, `EXIT_SELF`, `FREEZER`, `INITIALIZATION_FAILURE`, `LOW_MEMORY`, `MEMORY_LIMITER`, `OTHER`, `PACKAGE_STATE_CHANGE`, `PACKAGE_UPDATED`, `PERMISSION_CHANGE`, `SIGNALED`, `USER_REQUESTED`, `USER_STOPPED`.
- `ActivityManager.setProcessStateSummary(byte[])` – store the last journal sequence number and state-machine state; it comes back in the next process's exit record.
- `ApplicationStartInfo` (API 35): `START_REASON_BROADCAST`, `SERVICE`, `ALARM`, `BOOT_COMPLETE`, `PUSH`, `JOB`, `LAUNCHER`, …, start type cold/warm/hot, `wasForceStopped()`. This is the proof that a BLE PendingIntent cold-started the process.
- An uncaught-exception handler that writes one synchronous journal line before the default handler runs.

**Other details** [I]

- A heartbeat event every 15 minutes. Gaps without a matching exit record mean the process was frozen or the device slept, not killed.
- Rotation by size (for example 20 files of 5 MB). Storage in `filesDir/journal`. If the app is direct-boot aware, a small second journal in device-protected storage for events before the first unlock.
- Never log tokens, server addresses with credentials, or audio content. Log lengths and hashes instead.
- Mirror each line to logcat under one tag so a live adb session sees the same stream.

**Export – several ways, because the failure may also break one of them**

| Way | Works on release build | Needs |
|---|---|---|
| Share / "Save as" from the diagnostics screen (zip of journal + exit info + device facts) | yes | unlocked screen |
| `adb shell dumpsys activity service <pkg>/<ServiceClass> journal 500` | yes | [V] `Service.dump()` "gets invoked if you run `adb shell dumpsys activity service <yourservicename>`"; "the service must be running" |
| `adb exec-out run-as <pkg> tar c files/journal > journal.tar` | **debug build only** | [I] `run-as` requires a debuggable package |
| Upload over the existing authenticated Hermes connection | yes | the network path being alive |
| Spoken diagnosis: a menu entry that reads the last errors aloud | yes | fits the audio-only design; nothing else |

### 5.2 Layer 2: capture without touching the phone

**logcat**

- Enlarge the ring buffers: Developer options → "Logger buffer sizes" → 16M, or `adb logcat -G 16M`. [I]
- Live to the PC over wireless adb:
  `adb logcat -b main,system,crash,events -v threadtime,year,zone,uid > lara-$(date +%F-%H%M).log`
- Unattended on the phone, started once from the PC and left running after disconnect [I, verify that it survives]:
  `adb shell 'nohup logcat -b main,system,crash,events -v threadtime,year -f /data/local/tmp/lara.log -r 8192 -n 8 >/dev/null 2>&1 &'`
  then later `adb pull /data/local/tmp/` (rotates 8 files of 8 MB; ends at reboot).
- The `events` buffer is the fastest way to see process lifecycle: `am_proc_start` (with the reason and component), `am_proc_died`, `am_kill`, freeze and unfreeze events. [I]
- Useful tags and strings: `ActivityManager`, `BroadcastQueue`, `AS.HardeningEnforcer` / `AudioHardening` [V], `Foreground service started from background can not have location/camera/microphone access` [V], `ForegroundServiceStartNotAllowedException` [V], `DeviceIdleController`, `VoiceInteractionManager`, `MediaSessionService`, `BtGatt.*`, `bluetooth`.
- `adb shell setprop log.tag.<TAG> VERBOSE` raises verbosity for tags that check `Log.isLoggable`. [I]
- **What the app cannot do itself** [V]: reading its own lines from logcat needs no permission, but system-wide reading is approved only when the caller's process state is `PROCESS_STATE_TOP` and the user confirms a dialog (`LogcatManagerService.shouldShowConfirmationDialog`: `return procState == ActivityManager.PROCESS_STATE_TOP;`). A background service is declined even with `READ_LOGS` granted. System-wide capture has to come from the shell side or a bug report.

**Bug reports** [V] (https://developer.android.com/studio/debug/bug-report, AOSP shell help)

- `adb bugreport lara-bug.zip`, or on the phone via Developer options → "Bug report", or `adb shell am bug-report --progress`.
- Contains all `dumpsys` output, logcat buffers, ANR traces, drop-box entries, battery history and the Bluetooth snoop log. Take one as soon as possible after a failure; the buffers keep rolling.
- [V] Google's Battery Historian (`google/battery-historian`) is archived since 2019. Read the battery history through `dumpsys batterystats` or open the bug report in the Perfetto UI [I].

**Bluetooth HCI snoop log** [V] (https://source.android.com/docs/core/connect/bluetooth/verifying_debugging)

- Developer options → "Enable Bluetooth HCI snoop log" → "Restart Bluetooth for logging to take effect".
- "For most Android devices, the logs are stored in `data/misc/bluetooth/logs`"; on a retail phone they come out through the bug report. `btsnooz.py` extracts the always-on in-memory log, which "only logs non-personal information and events".
- Open in Wireshark. What it answers: **did the controller hand the Shelly advertisement to the host at all?** That separates "radio or controller filter never saw it" from "Bluetooth stack saw it, app did not get the PendingIntent" from "app got it and failed later".
- [I] With scan filters offloaded to the controller, non-matching advertisements never appear on HCI; that is expected.
- Cross-check with a second scanner (any phone with a BLE scanner app, or an ESP32) that the button really transmitted.

**Perfetto** [V] (https://perfetto.dev/docs)

- From the PC: `record_android_trace` (script in the Perfetto repository). On the phone: Developer options → System Tracing.
- Long traces: "to capture a long trace just set `write_into_file:true`, set a long `duration_ms` and use an in-memory buffer size of 32MB or more". Detached mode keeps tracing after adb disconnects.
- Use it for latency questions: scheduler, CPU idle and frequency, wake-ups, binder calls, `am`, `power`, `audio`, plus the app's own `android.os.Trace` sections (visible on a release build because of `isProfileable = true`).
- **Limit:** "The `android.log` data source … is supported only on Android userdebug builds." On a retail Pixel logcat is not merged into the trace; correlate by timestamp.
- [V] From inside the app: `ProfilingManager` (API 35, service name `profiling`) can request system traces, heap dumps and stack samples without adb. The Android 17 launch post shows `profilingManager.addProfilingTriggers(triggers)` and a `TRIGGER_TYPE_ANOMALY`. The app could attach a trace to a journal entry by itself. Rate limits: [U].

### 5.3 Layer 3: `dumpsys` services worth knowing

Service names are verified against `Context.java` constants in 17.0.0_r1 unless noted. The "what to look for" column is [I]: it describes what these dumps have contained in past releases and was not compared with Android 17 output.

| Command | What to look for |
|---|---|
| `dumpsys bluetooth_manager` [V name, AOSP Bluetooth docs] | Adapter state, registered LE scanners per app (is the PendingIntent scan still registered, which filters, results delivered), bonded and connected devices, profile states. |
| `dumpsys media_session` | Which session receives media buttons ("media button session"), its state and flags, last key events, audio-playback-based priority. The first stop for path C. |
| `dumpsys voiceinteraction` | Active voice interaction service component, bound state, session state, recogniser. The first stop for path A. |
| `dumpsys role` | Holder of `android.app.role.ASSISTANT`. |
| `dumpsys audio` | Audio focus stack, active playback and recording, communication device, volume state, **`AudioHardening` entries** [V]. |
| `dumpsys media.audio_policy`, `dumpsys media.audio_flinger` [I names] | Actual routing per stream and usage, open inputs and outputs, which device the microphone capture comes from. |
| `dumpsys deviceidle` | Doze state (light and deep), exemption lists, why it is or is not idle. |
| `dumpsys activity services <pkg>` | Foreground-service state, types, whether while-in-use was granted at start and why. |
| `dumpsys activity processes` / `dumpsys activity p <pkg>` | Process state, OOM adj, capabilities, frozen flag, bindings from `system_server`. |
| `dumpsys activity exit-info <pkg>` / `start-info <pkg>` | The same exit and start records the journal reads. |
| `dumpsys activity broadcasts` | Pending and historical broadcasts, delivery delays. |
| `dumpsys batterystats --charged <pkg>` and `--history` | Wake-ups, wake locks, Bluetooth scan time, foreground-service time attributed to the app. Reset with `--reset` before a test. |
| `dumpsys usagestats`, `am get-standby-bucket <pkg>` | Standby bucket and its history. |
| `dumpsys alarm`, `dumpsys jobscheduler`, `dumpsys power` | Pending alarms, jobs, held wake locks, wake-up reasons. |
| `dumpsys appops --package <pkg>` | Last use and rejection of `RECORD_AUDIO`, `BLUETOOTH_SCAN`, `CONTROL_AUDIO`, `TAKE_AUDIO_FOCUS`, restricted-settings state. |
| `dumpsys package <pkg>` | Granted permissions, installer of record, signing certificate digests, stopped state, target SDK. |
| `dumpsys netpolicy`, `dumpsys connectivity` | Whether the UID's network is blocked by Doze or standby. |
| `dumpsys companiondevice`, `dumpsys telecom`, `dumpsys notification` | Only if those features are used. |

Snapshot script idea [I]: one shell script that writes all of the above for the package into a time-stamped folder. Run it before and after each experiment and diff.

### 5.4 Forcing states from the desk

```bash
PKG=<your.application.id>

# --- power state (USB attached, so pretend it is not charging) -------------------
adb shell dumpsys battery unplug
adb shell input keyevent KEYCODE_SLEEP            # screen off
adb shell dumpsys deviceidle force-idle deep      # or: light
adb shell dumpsys deviceidle get deep             # expect IDLE
adb shell dumpsys deviceidle step deep            # walk the state machine one step at a time
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset                   # always undo

# --- app standby ------------------------------------------------------------------
adb shell am set-standby-bucket $PKG rare         # active|working_set|frequent|rare|restricted
adb shell am get-standby-bucket $PKG
adb shell am set-inactive $PKG true
adb shell am set-bg-restriction-level $PKG restricted_bucket   # …|background_restricted|hibernation

# --- process death, from gentle to brutal ----------------------------------------
adb shell am freeze --sticky $PKG                 # cached-app freezer;  am unfreeze $PKG
adb shell am kill $PKG                            # like the low-memory killer; keeps PendingIntents
adb shell am stop-app $PKG                        # stops services, keeps alarms and jobs
adb shell am crash $PKG                           # induce a VM crash
# adb shell am force-stop $PKG                    # stopped state + cancels PendingIntents: only to test recovery

# --- watch what the system thinks of the UID -------------------------------------
adb shell am watch-uids --oom $(adb shell cmd package list packages -U $PKG | sed 's/.*uid://')

# --- Android 17 specifics ----------------------------------------------------------
adb shell cmd audio set-enable-hardening throw    # enable | disable | throw
adb shell am memory-limiter status
adb shell am compat disable RESTRICT_LOCAL_NETWORK $PKG      # debuggable build only
adb shell am compat enable-all 37 $PKG                       # toggle everything gated by a target SDK

# --- reboot cases ---------------------------------------------------------------------
adb reboot                                        # then test BEFORE the first unlock, and after
```

- [V] All `am` sub-commands above are in the Android 17 shell help (`ActivityManagerShellCommand.java`): `set-standby-bucket … active|working_set|frequent|rare|restricted`, `set-bg-restriction-level … unrestricted|exempted|adaptive_bucket|restricted_bucket|background_restricted|hibernation`, `freeze [--sticky]`, `kill` ("Kill all background processes associated with the given application"), `stop-app` ("Unlike `force-stop` this does not cancel the app's scheduled alarms and jobs"), `watch-uids [--oom <uid>] [--mask <capabilities integer>]`, `compat enable|disable|reset|enable-all|disable-all`, `memory-limiter`.
- [V] `deviceidle` sub-commands `step`, `force-idle`, `unforce`, `get`, `whitelist`, `tempwhitelist` are in `DeviceIdleController.java`; the documented test recipe is at https://developer.android.com/training/monitoring-device-state/doze-standby.
- [V] `am compat` toggles need a debuggable build. The audio hardening is **not** a compat change (it reads the target SDK directly), so use `cmd audio set-enable-hardening` or a `-PtargetSdk=36` build for that.
- [I] Forced Doze is not identical to natural Doze after hours (sensors, alarms, modem and Bluetooth controller state differ). Use forced states to find bugs quickly and real overnight runs to accept a fix.

### 5.5 StrictMode and friends

- [I] In debug builds enable `StrictMode.ThreadPolicy` `detectAll()` and a `VmPolicy` with `detectLeakedClosableObjects()`, `detectLeakedRegistrationObjects()` (leaked receivers and service connections), `detectUnsafeIntentLaunch()`, `detectIncorrectContextUse()`. Use `penaltyListener` to write violations into the journal instead of crashing.
- [V] `detectNonSdkApiUsage()` reports hidden-API use (documented on the non-SDK restrictions page). Android 17's background-activity-launch hardening explicitly recommends "strict mode and updated lint checks".
- [I] LeakCanary (`square/leakcanary`, Apache-2.0) in debug builds for leaked services and binders. Android Studio Rabbit 2 advertises LeakCanary integration.
- [I] For coroutines: name them (`CoroutineName`) and enable the debug agent in debug builds so stack traces carry the coroutine that failed.

### 5.6 A debugging order that fits the three blockers

1. **"First click after hours did nothing."** Journal: is there an `adv_received`? No → HCI snoop: did the controller deliver it? → `dumpsys bluetooth_manager`: is the scan still registered? → exit info: was the process killed, and did re-registration run on restart or boot? Yes → follow the correlation id to the hop that stopped.
2. **"Lara started but stayed silent."** Logcat or `dumpsys audio` for `AudioHardening … level: full|partial`; `dumpsys activity services` for the while-in-use flag; `cmd audio set-enable-hardening disable` to confirm; then `-PtargetSdk=36` to see whether only the target-37 rule bites.
3. **"The microphone recorded silence or the wrong microphone."** Logcat for the foreground-service microphone message; `dumpsys appops` for `RECORD_AUDIO` rejections; `dumpsys media.audio_policy` and `dumpsys audio` for the active input device and communication device.
4. **"The tap opened something else or lit the screen."** `dumpsys voiceinteraction`, `dumpsys role`, events buffer for which activity was started.

---

## 6. Language and framework

### 6.1 What "no limits" really depends on

The limits on this app come from the platform: permissions, while-in-use capability, SELinux, the hidden-API list, Doze. **No language lifts any of them.** What a language can do is add or remove distance between the code and the platform APIs. Every component this app needs is a Java-level framework class that `system_server` instantiates by name from the manifest:

| Component | API | Who creates it |
|---|---|---|
| Path A | `VoiceInteractionService`, `VoiceInteractionSessionService`, `RecognitionService` | System binds it, possibly into a cold process with no UI |
| Path B | `BroadcastReceiver` for the `BluetoothLeScanner.startScan(…, PendingIntent)` result | System delivers a broadcast, possibly cold start |
| Path C | Media3 `MediaSessionService` / `MediaSession` | System and media button routing |
| Audio | `AudioManager`, `AudioTrack`, `AudioRecord`, `TextToSpeech`, communication-device routing | Called from the service |
| Lifetime | Foreground `Service` with types, `BOOT_COMPLETED` receiver | System |

### 6.2 Comparison for this app

| Option | Platform access | Debugging depth | Cost for this app | Verdict |
|---|---|---|---|---|
| **Kotlin** | Direct, first-class. Google's documentation, samples and Jetpack are Kotlin-first. Reflection on hidden APIs works as in Java. | Full: JDWP debugger, step into framework sources, StrictMode, Studio profilers, Perfetto. Coroutine stacks need a little care. | None extra. Coroutines and Flow fit an event-driven state machine; sealed types fit events and states; kotlinx.serialization fits the journal and the Hermes protocol. | **Choose this.** |
| **Java** | Identical to Kotlin (same bytecode, same APIs). | Identical; stack traces are slightly plainer. | More boilerplate, callbacks instead of structured concurrency, no Compose. Media3 and AndroidX work fine. | Fully viable, no advantage. Good to *read*, since AOSP is Java. |
| **C++ via NDK** | [V] The NDK's stable API list has C library, logging, trace, graphics, camera, media, native activity, binder, hardware buffers, **audio (AAudio, OpenSL ES)** and neural networks. There is **no Bluetooth, no role, no MediaSession, no VoiceInteraction API** (https://developer.android.com/ndk/guides/stable_apis). Everything else goes through JNI into the same Java classes. | Harder: two debuggers, JNI boundaries, native crashes as tombstones. | A JNI layer for no gain. | Only for a specific DSP block (resampling, voice-activity detection, a codec) if profiling ever demands it. |
| **Rust via NDK** | Same surface as C++; no official SDK bindings for the framework classes. | As C++, plus a second build system (cargo inside Gradle). | High. | Same niche as C++; not now. |
| **Kotlin Multiplatform** | Android side is ordinary Kotlin; nothing is lost. | Same as Kotlin for the Android target. | Extra Gradle complexity and plugin-compatibility lag for a second platform that does not exist. An iPhone cannot offer these integration points to third parties anyway. | Not now. Keep the door open by structure (6.3), not by plugin. |
| **Flutter** | Every component in the table above must still be written in Kotlin and bridged over platform channels. Dart only runs after a Flutter engine is started inside that cold, UI-less process. | Two runtimes and two debuggers; the hard bugs live in the Kotlin part and at the bridge. | High; start-up latency exactly where latency matters. A GitHub repository search for "VoiceInteractionService flutter" returned **0** results. | No. |
| **React Native** | Same story. [V] Background work is "Headless JS", which requires a native `HeadlessJsTaskService` subclass. | Same as Flutter, plus a JavaScript engine in the wake-up path. | High. A search for "VoiceInteractionService react-native" returned **0** results; the same search with `language:Kotlin` returned 5. | No. Existing React Native Hermes clients are useful as *protocol* references only. |

### 6.3 Recommendation

- **Kotlin for 100 % of the app and Kotlin DSL for the build.** It is the shortest path to every API in the table and the one with the most tooling.
- **Split into two Gradle modules from the first commit:**
  - `:core` – plain Kotlin/JVM library with **no `android.*` imports**: input-event model, input router, menu state machine, BTHome parser, Hermes protocol client interfaces, journal record format. Tested with `./gradlew :core:test` on the PC in seconds, no phone needed.
  - `:app` – the Android components, each a thin adapter that turns a platform callback into a `:core` event.
  - This is where most of the debuggability comes from: a state-machine bug is reproduced in a unit test from a journal excerpt instead of in a pocket. It also happens to be the structure Kotlin Multiplatform would need later.
- **Native code only on evidence**, behind a small interface, if a measured need appears.
- **Keep the option of hidden APIs and adb-granted permissions** in isolated, clearly named adapters with a public-API fallback, so a platform update can break at most an optional enhancement.

### 6.4 Compose or Views for the settings and diagnostics UI

**Compose (Material 3).**

- [V] "Jetpack Compose is Android's recommended modern toolkit for building native UI." The BOM 2026.11.01 is stable; the compiler ships with Kotlin.
- [I] The UI here is a handful of screens: set-up checklist with live status (role held? permissions? battery exemption? button seen?), settings, a scrolling journal viewer with filters, export. In Compose that is a `LazyColumn` over a `StateFlow`; in Views it is XML, adapters and view binding.
- [I] No navigation library is needed for three or four screens; a sealed `Screen` state is enough.
- Costs [I]: a few megabytes of APK and somewhat longer builds. Irrelevant for one sideloaded app.
- **The rule that matters more than the toolkit:** nothing on the audio path may depend on the UI. Services, receivers and the state machine must work when no activity has ever been created in the process. The UI only observes.

---

## 7. Corrections and gaps in the inherited planning notes (toolchain-related)

| Notes say | Finding |
|---|---|
| Phone "Pixel 11 Pro / Android 17" (section 1), to be checked | [V] Both a Pixel 10 Pro (`blazer`) and a Pixel 11 Pro (`grizzly`) exist. The task brief says Pixel 10 Pro. Android 17 is right for either. Confirm with `getprop` (1.2). |
| Nothing about build tooling, signing or distribution | Not an error, a gap. Covered here. |
| Section 6 treats "a correctly permitted foreground service" as the open question for background audio | [V] Android 17 adds a second condition the notes do not mention: for targetSdk 37 the service must have **while-in-use capability**, and failures are **silent**. |
| Section 8 mentions network access to Hermes only in terms of TLS/VPN | [V] With targetSdk 37 a LAN address additionally needs the `ACCESS_LOCAL_NETWORK` runtime permission; without it TCP connections time out. |
| Test plan item "App-Prozess beendet" | [V] How the process is ended matters: `am force-stop` cancels PendingIntents since Android 15 and is not a fair simulation of a system kill. |
| References "zuletzt in der Recherche geprüft: 17.11.2026" | [U] Cannot be confirmed. The Android reference URLs listed there are real pages; their content was not re-audited in this task. |

---

## 8. Load-bearing claims and how to test them on the real Pixel

| # | Claim | Status | Test |
|---|---|---|---|
| T1 | While Lara holds the assistant role, a foreground service started from the BLE receiver (screen off, locked) can play audio and record from the microphone with targetSdk 37. | inferred | Minimal app: `VoiceInteractionService` + BLE PendingIntent receiver that starts a `mediaPlayback\|microphone` service and plays a tone and records 2 s. Run with role held and with role removed. Read `dumpsys activity services` (while-in-use flag), `dumpsys audio` / logcat for `AudioHardening`, and the microphone log line. Repeat with `cmd audio set-enable-hardening throw`. |
| T2 | With targetSdk 36 the same service plays audio even without WIU. | verified in documentation and AOSP 17.0.0_r1, not on device | Same app built with `-PtargetSdk=36`, role removed. |
| T3 | Battery-optimisation exemption is sufficient to start a foreground service from the BLE receiver after hours of Doze. | documented exemption, untested | Exempt the app, force deep idle, press the button, look for `ForegroundServiceStartNotAllowedException` in logcat and the journal. Then a real overnight run. |
| T4 | Debug and release builds with one key and one applicationId replace each other without losing role, permissions and exemption. | inferred | Install release, set everything up, `adb install -r` the debug build, check `dumpsys role`, `dumpsys package`, `dumpsys deviceidle whitelist`. And back. |
| T5 | An update (adb or Obtainium) restores BLE scanning and the assistant binding without opening the app. | unknown | Update while idle, screen off. Journal must show `PACKAGE_UPDATED` exit, a restart reason, and a re-registered scan; then press the button. |
| T6 | Obtainium can update silently after its first confirmed update. | inferred | Watch for a confirmation dialog on the second update; check installer of record in `dumpsys package`. |
| T7 | `cmd role add-role-holder android.app.role.ASSISTANT <pkg>` works from adb on the retail build. | unknown | Run it; verify with `get-role-holders`. |
| T8 | The unattended on-device `logcat -f` survives adb disconnect and Doze. | inferred | Start it, disconnect, wait two hours, pull the files. |
| T9 | The Pixel runs API 37.2, build CP4A.261105.040.A1 or newer. | secondary source | `getprop` commands in 1.2. |
| T10 | Developer verification does not interfere with a browser or Obtainium install in Germany today. | verified in documentation | Install once via Chrome download; note every dialog shown. |
| T11 | The toolchain combination Gradle 9.9.2 + AGP 9.5.3 + Kotlin 2.4.21 builds cleanly with JDK 21. | inferred | First `./gradlew :app:assembleDebug`; read warnings; `./gradlew buildEnvironment` for the effective Kotlin plugin version. |

---

## 9. Material for the README

The owner asked for a proper README with links for other hearing-aid users. From this research area:

**Install section**

- Release APK from GitHub Releases, its SHA-256 file, the signing-certificate fingerprint, and the two verification commands (`sha256sum -c`, `gh attestation verify`).
- An Obtainium link (`obtainium://add/<repository URL>`) or badge.
- One honest sentence on scope: built for and tested on one Pixel 10 Pro with Android 17; minSdk 36.
- A note on Google's developer verification: not enforced in Germany as of November 2026; `adb install` always works.

**Build-from-source section** – the commands in 2.2 to 2.3 plus `./gradlew :app:assembleDebug`.

**Debugging section** – how to export the journal, the `dumpsys` table, the Doze commands.

**Tools worth linking**

| Project | What it is | Licence |
|---|---|---|
| Obtainium – github.com/ImranR98/Obtainium | Updates straight from GitHub Releases | GPL-3.0 (used as a tool; no code reuse) |
| Perfetto – github.com/google/perfetto, ui.perfetto.dev | System tracing and trace viewer | Apache-2.0 |
| scrcpy – github.com/Genymobile/scrcpy | Mirror and control the phone from the PC (v5.0.1, October 2026) | Apache-2.0 |
| LeakCanary – github.com/square/leakcanary | Leak detection in debug builds | Apache-2.0 |
| Wireshark | Reads `btsnoop_hci.log` | GPL (tool) |

**Related assistant projects found by a GitHub search for `VoiceInteractionService language:Kotlin`** – found, **not reviewed**; another research task should judge them:

| Repository | Description (as published) | Licence |
|---|---|---|
| yuga-hashimoto/openclaw-assistant (325 stars, last push 2026-08-29) | "OpenClaw voice assistant app for Android - Wake word activation & system assistant integration" | MIT |
| NiallBunting/AssistantVoiceRecorder (last push 2026-11-02) | "Use the Android's VoiceInteractionService to take the place of a digital assistant. This app just records so you can add your own processing pipeline." | GPL-3.0 – read only, do not copy into Apache-2.0 code |
| 352fihi-beep/siri-gemini (last push 2026-11-27) | "AirPods H2 triggered native Gemini assistant on Android/GrapheneOS. BLE Continuity parsing, VoiceInteractionService …" | none stated – no reuse rights |

---

## 10. Source index

**Versions**
- https://developer.android.com/studio/releases · https://developer.android.com/studio
- https://developer.android.com/build/releases/gradle-plugin · https://developer.android.com/build/releases/agp-9-0-0-release-notes
- https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml
- https://services.gradle.org/versions/current · https://docs.gradle.org/current/userguide/compatibility.html · https://docs.gradle.org/current/userguide/gradle_daemon.html
- https://kotlinlang.org/docs/gradle-configure-project.html
- GitHub API: releases of JetBrains/kotlin, google/ksp, Kotlin/kotlinx.coroutines, Kotlin/kotlinx.serialization, androidx/media, gradle/gradle
- https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/ (metadata and 5.5.1 POM)
- https://dl.google.com/android/repository/repository2-3.xml
- https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml and the 2026.11.01 POM

**Android 17 and the Pixel**
- https://developer.android.com/blog/posts/android-17-is-here
- https://developer.android.com/about/versions/17 · /release-notes · /behavior-changes-17 · /behavior-changes-all · /changes/bg-audio · /setup-sdk · /qpr1/release-notes · /qpr2/release-notes · /qpr3/release-notes
- https://developer.android.com/about/versions/16/behavior-changes-16 · /behavior-changes-all · /qpr2/setup-sdk
- https://developer.android.com/about/versions/15/behavior-changes-15 · /behavior-changes-all
- https://source.android.com/docs/setup/reference/build-numbers
- https://developers.google.com/android/images (public part only)
- https://9to5google.com/2026/11/10/android-17-qpr2-november-pixel-update/ · https://www.droid-life.com/2026/11/10/2026-november-pixel-update-download-install-release/ (secondary)
- https://developer.android.com/privacy-and-security/local-network-permission
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start · /service-types
- https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams

**AOSP at tag `android-17.0.0_r1`** (android.googlesource.com)
- platform/frameworks/base: `services/voiceinteraction/java/com/android/server/voiceinteraction/VoiceInteractionManagerServiceImpl.java`, `services/core/java/com/android/server/audio/HardeningEnforcer.java`, `services/core/java/com/android/server/am/ActivityManagerShellCommand.java`, `services/core/java/com/android/server/logcat/LogcatManagerService.java`, `apex/jobscheduler/service/java/com/android/server/DeviceIdleController.java`, `core/java/android/content/Context.java`, `core/res/AndroidManifest.xml`
- platform/packages/modules/Permission: `service/java/com/android/ecm/EnhancedConfirmationService.java`, `PermissionController/res/xml/roles.xml`, `service/java/com/android/role/RoleShellCommand.java`

**Local set-up**
- https://developer.android.com/tools/sdkmanager · /tools/variables · /tools/adb · /studio/run/device · /studio/install · /studio/intro/update · /build/jdks
- https://developer.android.com/tools/agents · /tools/android-cli-reference · /tools/agents/android-cli/download
- https://api.adoptium.net/v3/assets/latest/21/hotspot?os=linux&architecture=x64&image_type=jdk
- https://flathub.org/api/v2/appstream/com.google.AndroidStudio · https://flathub.org/api/v2/verification/com.google.AndroidStudio/status
- https://data.services.jetbrains.com/products/releases?code=TBA&latest=true&type=release

**Release pipeline**
- READMEs and releases of actions/checkout, actions/setup-java, gradle/actions (and docs/setup-gradle.md), softprops/action-gh-release, actions/attest, actions/attest-build-provenance
- github.com/actions/runner-images: `images/ubuntu/Ubuntu2404-Readme.md`, `Ubuntu2604-Readme.md`
- https://developer.android.com/studio/publish/app-signing
- https://developer.android.com/reference/tools/gradle-api/9.0/com/android/build/api/dsl/ApplicationBuildType

**Sideloading**
- https://developer.android.com/developer-verification · /guides · /guides/faq · /guides/limited-distribution · /guides/open-source-app-registration
- https://android-developers.googleblog.com/2026/03/android-developer-verification.html · https://android-developers.googleblog.com/2026/06/android-developer-verification.html
- https://developers.google.com/android/play-protect/warning-dev-guidance
- https://developer.android.com/privacy-and-security/advanced-protection-mode
- github.com/ImranR98/Obtainium (README) · https://wiki.obtainium.imranr.dev/ (sources, deep links)

**Debugging**
- https://developer.android.com/training/monitoring-device-state/doze-standby · https://developer.android.com/topic/performance/appstandby
- https://source.android.com/docs/core/connect/bluetooth/verifying_debugging
- https://developer.android.com/studio/debug/bug-report · /studio/debug · /studio/debug/logcat · /studio/profile/power-profiler
- https://developer.android.com/guide/app-compatibility/test-debug · /guide/app-compatibility/restrictions-non-sdk-interfaces
- https://developer.android.com/reference/android/app/ApplicationExitInfo · /ApplicationStartInfo · /Service
- https://perfetto.dev/docs/getting-started/system-tracing · /docs/concepts/config · /docs/data-sources/android-log

**Language**
- https://developer.android.com/ndk/guides/stable_apis · https://developer.android.com/compose
- https://reactnative.dev/docs/headless-js-android · https://docs.flutter.dev/platform-integration/platform-channels
- GitHub repository search API (three queries, results quoted in 6.2 and 9)
