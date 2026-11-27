# Verification of the "versions" cluster (claims 1-7)

Checked on 2026-11-27 (UTC afternoon). Method: every claim was attacked against its primary feed (Maven metadata, Gradle version service, SDK repository XML, GitHub releases API, AOSP gitiles at tag, developer.android.com, Google support pages, the local apt cache). Nothing was built or run. Raw downloads are in
`<research>/vv/`.

Verdict summary

| # | Claim | Verdict |
|---|---|---|
| 1 | Current stable versions | confirmed (all ten) |
| 2 | They work together / catalogue | partly: consistent on paper, but the first pass's `compileSdk = 37` breaks the build with Compose BOM 2026.11.01 |
| 3 | compileSdk 37 platform + build-tools package names | partly: there is no `platforms;android-37`; the names are minor-versioned |
| 4 | Android 17 = API 37, stable June 2026, on Pixel 10 Pro | confirmed (the label "QPR2" for the November build is an inference) |
| 5 | GitHub Actions majors | confirmed, upload-artifact added |
| 6 | openjdk-21-jdk on Mint 21.2, cmdline-tools zip | confirmed |
| 7 | Developer verification not enforced in Germany, adb exempt | confirmed |

---

## Claim 1 - current stable versions: CONFIRMED

| Component | Claimed | Found | Primary evidence |
|---|---|---|---|
| AGP | 9.5.3 | 9.5.3 | `https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml`: versions end `9.5.0 9.5.1 9.5.2 9.5.3 9.6.0-alpha01 ... 9.6.0-alpha07`; `<release>` is `9.6.0-alpha07` (pre-release). POM `gradle-9.5.3.pom` has `last-modified: Wed, 25 Nov 2026 15:59:58 GMT`. |
| Gradle | 9.9.2 | 9.9.2 | `https://services.gradle.org/versions/current`: `"version" : "9.9.2"`, `"buildTime" : "20261119195207+0000"`, `"checksum" : "971fcd7150ca4ba04852c0b2ca0d2d0ab9468e14b03473eab19528809914c76b"`, `"wrapperChecksum" : "621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8"`. No active RC (`/versions/release-candidate` returns `{ }`). |
| Kotlin | 2.4.21 | 2.4.21 | Maven Central `kotlin-gradle-plugin/maven-metadata.xml`: `... 2.4.20 2.4.21-RC 2.4.21 2.5.0-Beta1 2.5.0-Beta2 2.5.0-RC 2.5.0-RC2`. GitHub `JetBrains/kotlin` releases/latest: `v2.4.21 2026-10-08`. `v2.5.0-RC2` published 2026-11-25. |
| KSP | 2.3.14 | 2.3.14 | Maven Central `com/google/devtools/ksp/symbol-processing-api/maven-metadata.xml`: `<release>2.3.14</release>`, lastUpdated 20261113. GitHub `google/ksp` 2.3.14 published 2026-11-13. |
| Compose BOM | 2026.11.01 | 2026.11.01 | Google Maven `androidx/compose/compose-bom/maven-metadata.xml`: `<release>2026.11.01</release>`. BOM POM maps ui/foundation/runtime/animation to 1.13.0, material3 to 1.5.0. developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler: "Always use the latest Compose BOM version: 2026.11.01." |
| Media3 | 1.11.1 | 1.11.1 | Google Maven `androidx/media3/media3-session/maven-metadata.xml`: `... 1.11.0 1.11.1 1.12.0-alpha01 1.12.0-alpha02 1.12.0-beta01 1.12.0-rc01`. |
| kotlinx-coroutines | 1.11.0 | 1.11.0 | Maven Central `kotlinx-coroutines-core`: `<release>1.11.0</release>`, lastUpdated 20260508. |
| kotlinx-serialization | 1.13.0 | 1.13.0 | Maven Central `kotlinx-serialization-json`: `<release>1.13.0</release>`, lastUpdated 20261111. Release note: "This release is based on Kotlin 2.4.10". |
| OkHttp | 5.5.1 | 5.5.1 | Maven Central `com/squareup/okhttp3/okhttp/maven-metadata.xml`: `<release>5.5.1</release>`, lastUpdated 20261022. |
| Android Studio | Rabbit 2 \| 2026.2.2 Patch 3 | same | developer.android.com/studio: "Download Android Studio Rabbit 2 \| 2026.2.2 Patch 3", `android-studio-rabbit2-patch3-linux.tar.gz`, SHA-256 `1c7ccf6dbb0ac6376e3b15e536096f1878621b4b5586d81e22510cd835f1438d`, URL `.../ide-zips/2026.2.2.11/...`. JetBrains feed (jb.gg/android-studio-releases-list.xml): `Android Studio Rabbit 2 | 2026.2.2 Patch 3 | 2026.2.2.11 | Patch | November 25, 2026`. |

Things that will move soon (all pre-release today): Kotlin 2.5.0 (RC2, 25 Nov), Media3 1.12.0 (rc01, 25 Nov), Android Studio Rabbit 3 (RC 2, 24 Nov), AGP 9.6 (alpha07). AGP 9.5.3 is two days old and Gradle 9.9.2 eight days old.

Trap: the GitHub API marks `androidx/media` `1.12.0-rc01` as the "latest" non-prerelease. Take stable versions from Maven metadata, not from GitHub "latest".

## Claim 2 - the versions work together: PARTLY (one build-breaking correction)

What holds, with source:

- **Gradle range and JDK for AGP 9.5.** developer.android.com/build/releases/agp-9-5-0-release-notes: table "Gradle | 9.6.0 (minimum) | 9.6.0 (default)", "SDK Build Tools | 36.0.0 | 36.0.0", "NDK | N/A | 28.2.13676358", "JDK | 17 | 17", and "The maximum API level that Android Gradle plugin 9.5 supports is API level 37." The Gradle module file `gradle-9.5.3.module` says `"createdBy": {"gradle": {"version": "9.6.0"}}` and `org.gradle.jvm.version: 17`.
- **Gradle 9.9.2 itself** (docs.gradle.org/current/userguide/compatibility.html): "A JVM version between 17 and 27 is required to execute Gradle." "Gradle is tested with Kotlin 2.0.0 through 2.5.0-Beta1." "Gradle is tested with Android Gradle Plugin 9.0 through 9.5.0-alpha10." Embedded Kotlin from 9.9.0 on: 2.4.21.
- **Built-in Kotlin.** developer.android.com/build/releases/agp-9-0-0-release-notes: "Android Gradle plugin 9.0 introduces built-in Kotlin support and enables it by default. That means you no longer have to apply the org.jetbrains.kotlin.android (or kotlin-android) plugin". AGP source at tag `studio-2026.2.2`, `BooleanOption.kt`: `BUILT_IN_KOTLIN(propertyName = "android.builtInKotlin", defaultValue = true, stage = FeatureStage.SoftlyEnforced(VERSION_10_0))`. Applying `org.jetbrains.kotlin.android` fails with "The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0." `kotlin-kapt` is incompatible; use KSP or `com.android.legacy-kapt`.
- **Which KGP is used.** `gradle-9.5.3.pom` carries a runtime dependency `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10`. developer.android.com/build/migrate-to-built-in-kotlin, "Choose a Kotlin Gradle plugin version": "AGP 9.0 through 9.5: AGP has a runtime dependency on KGP 2.2.10, which acts as both the default and minimum version for built-in Kotlin." and "If your top-level plugins block already declares another Kotlin Gradle plugin - such as org.jetbrains.kotlin.plugin.compose, org.jetbrains.kotlin.plugin.serialization, org.jetbrains.kotlin.jvm ... - with a version of 2.2.10 or higher, Gradle aligns KGP to that version automatically." Otherwise: `buildscript { dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:KGP_VERSION") } }`. So declaring the Compose or serialization plugin at 2.4.21 in the root `plugins {}` block (with `apply false`) is the documented way to get KGP 2.4.21.
- **Compose compiler.** It is the Kotlin plugin `org.jetbrains.kotlin.plugin.compose`, versioned with Kotlin (marker artifact 2.4.21 exists on Maven Central and the Gradle Plugin Portal). `buildFeatures { compose = true }` is still documented.
- **KSP.** AGP 9.5.3 publishes a dependency constraint `com.google.devtools.ksp:symbol-processing-gradle-plugin` `requires 2.2.10-2.0.2`, reason "KSP version needs to match KGP version" (module file and POM `dependencyManagement`). KSP 2.3.14 is higher, so it wins. KSP at tag 2.3.14: `aa-kotlin-base = "2.4.20-dev-6138"` (embedded Analysis API from the Kotlin 2.4.20 line), `kotlinBaseVersion=2.3.20`, `agpBaseVersion=9.3.0-alpha01`. KSP 2.3.10 notes: "Sanitize ':' in internal-name module suffix so KSP works with Kotlin 2.4.0 default module names" and "Fix R-class resolution in KSP when AGP 9 built-in Kotlin is enabled". No statement names Kotlin 2.4.21 explicitly; compatibility is by line, not by a published matrix.
- **Library Kotlin requirements** (stdlib version in each POM): Compose 1.13.0 -> 2.2.20, material3 1.5.0 -> 2.2.20, core-ktx 1.19.1 -> 2.1.20, media3 1.11.1 -> 2.2.10, coroutines 1.11.0 -> 2.2.20, serialization 1.13.0 -> 2.4.10, OkHttp 5.5.1 -> 2.1.21. All at or below 2.4.21.
- All plugin markers and library POMs in the catalogue below return HTTP 200.

What does not hold:

1. **`compileSdk = 37` fails with Compose BOM 2026.11.01.** AAR metadata inside `ui-android-1.13.0.aar`, `foundation-android-1.13.0.aar`, `animation-android-1.13.0.aar`, `ui-tooling-android-1.13.0.aar`:
   `minCompileSdk=37`, `minCompileMinorSdk=1`, `minAndroidGradlePluginVersion=9.1.0`.
   Compose UI release notes (developer.android.com/jetpack/androidx/releases/compose-ui, 1.13.0-alpha03): "compileSdk for Compose libraries updated to 37.1. This will require transitively updating compileSdk for all apps and libraries using Compose."
   AGP `CheckAarMetadataTask.kt` (tag `studio-2026.2.2`): `val isCompatible = minCompileSdkInt < compileSdkVersionInt || (minCompileSdkInt == compileSdkVersionInt && (minCompileSdkMinor == null || minCompileSdkMinor <= compileSdkVersionMinor))`; on failure: "Dependency '...' requires libraries and applications that depend on it to compile against version 37.1 or later of the Android APIs."
   `compileSdk = 37` resolves to 37.0 (see claim 3), so the first pass's build file would stop at `:app:checkDebugAarMetadata`. The app must compile against 37.1 or 37.2.
2. **KGP 2.4.21 is outside JetBrains' "fully supported" range for this Gradle and AGP.** kotlinlang.org/docs/gradle-configure-project.html: row `2.4.20-2.4.21 | Gradle 7.6.3-9.7.0 | AGP 8.5.2-9.3.1`, and "You can also use Gradle and AGP versions up to the latest releases, but if you do, keep in mind that you might encounter deprecation warnings or some new features might not work." KGP 2.4.21 ships Gradle-API variants up to 9.6. Do not run CI with `--warning-mode=fail`.
3. Nothing was compiled. "Resolves" below means: every coordinate exists and every published constraint I could read is satisfied.

Other AAR minimums that matter: core/core-ktx 1.19.1 and material3 1.5.0 need compileSdk 37.0 and AGP 9.1.0; activity 1.13.0 and media3 1.11.1 need 36; lifecycle 2.11.1, datastore 1.3.0, compose-runtime 1.13.0 need 34. All libraries have minSdk 23 or 24.

### Version catalogue (believed to resolve; not executed)

`gradle/wrapper/gradle-wrapper.properties`

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.9.2-bin.zip
distributionSha256Sum=971fcd7150ca4ba04852c0b2ca0d2d0ab9468e14b03473eab19528809914c76b
```

`gradle/libs.versions.toml`

```toml
[versions]
agp = "9.5.3"
kotlin = "2.4.21"
ksp = "2.3.14"                 # only if an annotation processor is added
composeBom = "2026.11.01"
media3 = "1.11.1"
coroutines = "1.11.0"
serialization = "1.13.0"
okhttp = "5.5.1"
coreKtx = "1.19.1"
lifecycle = "2.11.1"
activityCompose = "1.13.0"
datastore = "1.3.0"

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
# no org.jetbrains.kotlin.android: AGP 9 has built-in Kotlin and rejects that plugin

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-service = { module = "androidx.lifecycle:lifecycle-service", version.ref = "lifecycle" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
media3-session = { module = "androidx.media3:media3-session", version.ref = "media3" }
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
```

Root `build.gradle.kts`: `alias(...) apply false` for `android.application`, `kotlin.jvm`, `kotlin.compose`, `kotlin.serialization` (the Kotlin aliases are what lift KGP from 2.2.10 to 2.4.21). Check once with `./gradlew buildEnvironment | grep kotlin-gradle-plugin`.

`app/build.gradle.kts`, the lines that differ from the first pass:

```kotlin
android {
    compileSdk {
        version = release(37) {
            minorApiLevel = 2          // required: Compose 1.13 needs 37.1 or higher
        }
    }
    defaultConfig { minSdk = 36; targetSdk = 37 }
}
```

The DSL is documented for 36.1 on developer.android.com/about/versions/16/qpr2/setup-sdk (`version = release(36) { minorApiLevel = 1 }`) and implemented in `CompileSdkSpecImpl.kt` at tag `studio-2026.2.2`. AGP 9.5 accepts up to 37.2 (`SdkConstants.MAX_SUPPORTED_ANDROID_PLATFORM_VERSION = new AndroidVersion(37, 2)`); the Studio table gives minimum AGP 9.4.0 for API 37.2.

JDK: run Gradle on JDK 21 (allowed range 17-27), keep `sourceCompatibility`/`targetCompatibility` at 17. Conservative fallback if the newest pair misbehaves: Gradle 9.8.2 and AGP 9.5.2 (both exist; still above AGP's 9.6.0 minimum).

## Claim 3 - SDK package names: PARTLY

Source: `https://dl.google.com/android/repository/repository2-3.xml` (last-modified 27 Nov 2026 05:34 GMT) and its newer-schema sibling `repository2-4.xml` (namespace `.../repository2/04`, also HTTP 200; `repository2-5.xml` is 404). Both list the same packages and revisions. `AndroidSdkHandler.kt` builds the URL as `"${getBaseUrl(progress)}repository2-$version.xml"` from the number of schema versions the tool knows and falls back to the previous one, so newer tools read `-4`, older ones `-3`.

- **There is no `platforms;android-37`.** The stable platform packages are:
  - `platforms;android-37.0` rev 2, api-level 37.0, archive `platform-37.0_r02.zip` (sha1 `ed8ebf7f8822a4de5686d427f237d2fa30ff7410`)
  - `platforms;android-37.1` rev 1, api-level 37.1, archive `platform-37.1_r01.zip` (sha1 `62aeba1ec09c2b1fdf888e79549daf678fa0d75f`)
  - `platforms;android-37.2` rev 2, api-level 37.2, archive `platform-37.2_r02.zip` (sha1 `d52b3062af96f7c4dfdca5a479cd1db33ae83762`)
  - also present and not wanted: `platforms;android-37.2-beta1..3`, `platforms;android-canary-2026xxxx`, `platforms;android-CANARY`. Every package is in `channel-0`, so the channel does not separate stable from beta; the name and an empty `codename` do.
- **`compileSdk = 37` means 37.0.** `CompileSdkSpecImpl.kt`, tag `studio-2026.2.2`: "since api level 37, minor version is included in platform hash even for .0 (e.g. $SDK/platforms/android-37.0)" / `if (apiLevel >= 37 && minorApiLevel == null) { compileSdkString += ".0" }`. Studio table: API 37.0 needs AGP 9.1.1, 37.1 needs 9.3.1, 37.2 needs 9.4.0.
- **Build-tools:** `build-tools;37.0.0` rev 37.0.0 exists (`build-tools_r37_linux.zip`, sha1 `70954e99f4c3d9d46ee70fa32624672fe7cd6ebe`), plus `37.0.0-rc1/rc2`, `36.1.0`, `36.0.0`. AGP 9.5 uses `build-tools;36.0.0` unless told otherwise (`SdkConstants.CURRENT_BUILD_TOOLS_VERSION = "36.0.0"`).
- **Other:** `platform-tools` 37.0.3; `cmdline-tools;latest` = 23.0 (`commandlinetools-linux-16111833_latest.zip`); `sources;android-37.0` r2, `37.1` r1, `37.2` r1. All use `android-sdk-license`.
- The stale official set-up page (developer.android.com/about/versions/17/setup-sdk) still says `compileSdk = 37`, "Android Studio Meerkat", "AGP 8.9.0-rc01" and "Android SDK Build-Tools 37"; it predates the Compose requirement and should not be followed literally.

Install line for this project: `sdkmanager "platform-tools" "platforms;android-37.2" "build-tools;36.0.0"` (add `"sources;android-37.2"` for the IDE).

## Claim 4 - Android 17, API 37, June 2026, Pixel 10 Pro: CONFIRMED

- API level: AOSP `frameworks/base` tag `android-17.0.0_r1`, `core/java/android/os/Build.java` line 1318: `public static final int CINNAMON_BUN = 37;`. source.android.com/docs/setup/reference/build-numbers: "Android17 | 17 | API level 37 | 17 | 26Q2"; "CP2A.260605.016 | android-17.0.0_r1 | Android17 | 2026-06-05".
- Stable date: developer.android.com/blog/posts/android-17-is-here, dated "16 Jun 2026": "Today we're releasing Android 17 and making it available on most supported Pixel devices." Pixel bulletin index: "June 2026 | June 16, 2026 | 2026-06-05".
- Pixel 10 Pro: Google Pixel Community announcement "Google Pixel Update - November 2026" (support.google.com/pixelphone/thread/477214134), section "Software versions / Global": "Pixel 10 Pro: CP4A.261105.040.A1". Verizon's Pixel 10 Pro page: "System Update 18 ... Release date: 11/10/2026 ... Software Version: CP4A.261105.040.A1". Pixel Update Bulletin November 2026: "Published November 10, 2026", patch level 2026-11-05. The Android 17 beta image page lists "Pixel 10 Pro | blazer_beta-...", which confirms the codename `blazer`.
- Minor levels: developer.android.com/reference/android/os/Build.VERSION_CODES_FULL: `CINNAMON_BUN_1` "Added in version 37.1 ... Constant Value: 3700001", `CINNAMON_BUN_2` "Added in version 37.2 ... 3700002".
- Not from a Google page: that build CP4A is "Android 17 QPR2 / 37.2". Google's announcement does not use the term. It follows from the build-ID scheme (AOSP table: BP4A.251205.006 = "Android16 QPR2 ... 25Q4"; the QPR2 betas were CP41.*; QPR2 Beta 7.1 was 16 Oct 2026) and from press reports. Settle it on the phone: `adb shell getprop ro.build.version.sdk_full` (the property behind `Build.VERSION.SDK_INT_FULL`, per Build.java line 464) should print 37.2; `ro.build.version.sdk` prints 37.
- Google's factory-image list (developers.google.com/android/images) sits behind a terms click-through and was not opened.

Consequence found on the way: **AOSP has no source for what the phone runs.** `platform/manifest` and `frameworks/base` have only `refs/tags/android-17.0.0_r1` and `refs/heads/android17-release` (last commit 21 May 2026, "26Q2-rel"); there is no android17 QPR branch, and `android-latest-release` points to `android17-release`. source.android.com: "Effective in 2026 ... we will publish source code to AOSP in Q2 and Q4." The Q4 drop is not there yet. Every AOSP-based statement in the other reports describes 17.0 (June), not the November build. The SDK package `sources;android-37.2` contains client-side framework sources (`android.*`, `com.android.internal.*`) but no `com.android.server.*`, and its `Build.java` lacks `CINNAMON_BUN_1/_2`, so it is not an exact copy of the final 37.2 either.

## Claim 5 - GitHub Actions majors: CONFIRMED (one addition)

GitHub releases API, today:

| Action | Latest | Major | SHA of the tag |
|---|---|---|---|
| actions/checkout | v7.0.1 (2026-07-20) | v7 | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| actions/setup-java | v6.3.0 (2026-11-14) | v6 | `d1374f8ac74474200d16ef8ecee22c7ae7a51880` |
| gradle/actions (setup-gradle) | v6.4.1 (2026-10-28) | v6 | `8da46b539cd8fe6ab27ca75128919a18d757604c` |
| softprops/action-gh-release | v3.0.4 (2026-11-05) | v3 | `8a0cd651e5b2365bd640854a3fa5dfc70e4875f3` |
| actions/attest-build-provenance | v4.2.2 (2026-08-06) | v4 | `4d101475d8b20a2381f78447822ac1eab6504dd8` |
| actions/attest | v4.2.2 (2026-08-04) | v4 | `1e69f48acb82d1966a394da916b4c1698aa569d6` |
| actions/upload-artifact | v7.0.2 (2026-10-07) | v7 | `cf430e030ddbb5b0abf93d22962f4752f3646cd9` |

The five SHAs the first pass pinned match. upload-artifact was missing from it.

Breaking or behaviour changes that touch a "build signed APK on tag, attach to release" workflow:

- checkout v7: "checkout now refuses to check out fork pull request code by default when the workflow is triggered by pull_request_target or workflow_run" (opt-in `allow-unsafe-pr-checkout: true`). No effect on `push: tags`. v6: credentials persisted in a file under `$RUNNER_TEMP`. Runs on node24 (runner 2.327.1 or newer; hosted runners are fine). Default `fetch-depth` is still 1, so `git describe --tags` in the build script finds nothing in CI; pass the version in from the tag.
- setup-java v6.3.0: "setup-java no longer registers the Java problem matcher by default" (annotations only). v6.0.0: `jdkFile` renamed `jdk-file`.
- gradle/actions v6: "By upgrading to v6, you accept the Terms of Use for the gradle-actions-caching component"; caching is now a proprietary library, "used only when caching is enabled". `cache-disabled: true` or `cache-provider: basic` avoids it. v6.4.1 is a security fix release.
- softprops/action-gh-release v3: "moves the action runtime from Node 20 to Node 24". README: "GitHub immutable releases lock assets after publication ... prereleases stay published by default ... On an immutable-release repository, use draft: true for prereleases that upload assets". So `prerelease: true` plus immutable releases would fail to attach the APK. Needs `contents: write`.
- actions/attest-build-provenance v4: "simply a wrapper on top of actions/attest ... new implementations should use actions/attest instead". Permissions from the actions/attest README: `id-token: write`, `attestations: write`, `artifact-metadata: write`. "Artifact attestations are available in public repositories for all current GitHub plans."
- upload-artifact v7: new `archive: false` uploads a single file unzipped (handy for a debug APK from pull-request builds); v6 moved to node24.
- `gh` alternative: GitHub CLI 2.105.0 is preinstalled on `ubuntu-24.04`; `gh release create "$GITHUB_REF_NAME" dist/* --generate-notes` with `GH_TOKEN` and `contents: write` replaces softprops.
- Runner images (actions/runner-images README): `ubuntu-latest` is now Ubuntu 26.04 with JDK 25 as default; `ubuntu-24.04` has JDK 17 default plus 21 and 25, `ANDROID_HOME=/usr/local/lib/android/sdk`, platforms `android-37.0`, `37.1`, `37.2`, build-tools 36.0.0 and 37.0.0, Gradle 9.9.2. Ubuntu 22.04 images are being retired.

## Claim 6 - JDK 21 on Linux Mint 21.2 and the command-line tools: CONFIRMED

- This machine: `/etc/os-release` "Linux Mint 21.2 (Victoria)", upstream `DISTRIB_CODENAME=jammy`. `apt-cache policy openjdk-21-jdk`: "Candidate: 21.0.13.1+2-1~22.04.1 ... http://archive.ubuntu.com/ubuntu jammy-updates/universe amd64 Packages" and "jammy-security/universe". The pool lists `openjdk-21-jdk_21.0.13.1+2-1~22.04.1_amd64.deb`. Package lists are from today. It comes from `universe` in the updates and security pockets, not from the original `jammy` pocket. `openjdk-17-jdk` (17.0.21.1) and `openjdk-25-jdk` (25.0.5.1) are there as well. Installed today: OpenJDK 11.0.33 only.
- developer.android.com/studio, "Command line tools only": `commandlinetools-linux-15859902_latest.zip`, 181.8 MB, SHA-256 `4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583`. Downloaded without login from `https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip` (HTTP 200, 181 833 628 bytes); computed SHA-256 matches the page, SHA-1 `040d3996a65543d22ec4bf73e4c37aa37a8d4af4` matches `cmdline-tools;22.0` in the repository XML; `source.properties` says `Pkg.Revision=22.0`.
- Checksum sources: SHA-256 on the download page; SHA-1 in `repository2-3.xml`/`-4.xml`.
- Details the claim does not state: the repository already has 23.0 (`commandlinetools-linux-16111833_latest.zip`, sha1 `e025545c62a8e64c7559119566a569fb1dec5f60`, also downloadable without login); `sdkmanager --install "cmdline-tools;latest"` gets it. The classes in the 22.0 zip are class-file version 61, so **sdkmanager needs Java 17 or newer** and will not start on the installed JDK 11: install the JDK first. developer.android.com/tools/sdkmanager: "The sdkmanager tool is deprecated. Instead, use the Android CLI android sdk command."

## Claim 7 - developer verification in Germany, adb: CONFIRMED

- developer.android.com/developer-verification, timeline: "September 30, 2026 | Protections begin for all users who install apps from participating stores in Brazil, Indonesia, Singapore, and Thailand on certified devices running Android 8 (API level 26) or higher." "2027 and beyond | Verification capability will be expanded globally for all apps on certified Android devices in 2027."
- FAQ (page last updated 2026-11-17): "The September 30, 2026 deadline only applies to the specific participating stores. If you distribute your app through other stores, or if users sideload your app directly, these new verification requirements won't apply to your app yet."
- adb: "Will Android Debug Bridge (ADB) install work without registration? As a developer, you are free to install apps without verification with ADB." and "there are no changes to how ADB works. You will be able to install applications using ADB as usual. The waiting period does not apply to ADB installs."
- A search for a newer announcement naming Germany, the EU or a 2027 month found none.
- Caveats: "2027" has no month; the adb exemption is stated policy, not a promise ("for good" in the first pass is too strong); enforcement is "delivered through Google Play services", so it can arrive without an OS update. The two official pages still disagree on the limited-distribution cap ("up to 20 devices" on the main page, guides and FAQ; "up to 50 devices" on the limited-distribution guide).

---

## Surprises (not covered by the claims)

1. Compose 1.13 (BOM 2026.11.01) requires compileSdk 37.1 or higher; `compileSdk = 37` is 37.0 and fails the AAR metadata check.
2. No AOSP source exists yet for Android 17 QPR1/QPR2; only `android-17.0.0_r1` (26Q2). Source-based findings elsewhere describe June's 17.0, not the phone's November build.
3. sdkmanager needs Java 17+, is officially deprecated, and the download page offers 22.0 while the repository has 23.0.
4. gradle/actions v6 makes its default cache a proprietary component whose terms are accepted by using it.
5. softprops/action-gh-release with `prerelease: true` cannot attach assets on a repository with immutable releases.
6. `ubuntu-latest` is Ubuntu 26.04 with JDK 25 as default; pin the image and the JDK.
7. OkHttp moved from `square/okhttp` to `lysine-dev/okhttp` (Commonhaus) and, per its 5.5.0 changelog, "OkHttp artifacts are now signed with our new signing key". Coordinates are unchanged. Relevant only if Gradle dependency verification with PGP keys is switched on.
8. KGP 2.4.21 is "fully supported" by JetBrains only up to Gradle 9.7.0 and AGP 9.3.1; expect deprecation warnings.
9. Kotlin 2.5.0, Media3 1.12.0, Studio Rabbit 3 and AGP 9.6 are all at RC or alpha this week. AGP 9.6 changes the KGP default to 2.4.20 and requires Gradle 9.8.2.
10. GitHub marks `androidx/media` 1.12.0-rc01 as "latest"; do not automate on that flag.
11. Minor SDK releases carry no target-gated behaviour changes (Build.java: "no changes gated by targetSdkVersion ... compared to major releases"), so compiling against 37.2 does not change runtime behaviour; `targetSdk` stays 37.

## Still to test

- First real build with the catalogue: `./gradlew :app:assembleDebug` and `./gradlew buildEnvironment` (KGP must show 2.4.21). Nothing was compiled here.
- On the phone: `adb shell getprop ro.product.device` (blazer), `ro.build.id` (CP4A.261105.040.A1 or newer), `ro.build.version.sdk` (37), `ro.build.version.sdk_full` (37.2).
- `adb install` and a browser or Obtainium install on the phone in Germany: no developer-verification dialog expected; note what Play Protect shows.
- First tag build on GitHub: attestation step, release upload, and whether the repository has immutable releases enabled.
