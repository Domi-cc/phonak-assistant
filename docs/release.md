# Releasing

A release is a git tag. Pushing a tag such as `v0.2.0` starts `.github/workflows/release.yml`. It builds the app, signs it with the project key and attaches exactly two files to a GitHub Release:

- `lara-im-ohr.apk`
- `lara-im-ohr.apk.sha256`

The names are the same for every version, so `https://github.com/<owner>/<repo>/releases/latest/download/lara-im-ohr.apk` always is the newest stable APK.

## Why the key matters

Local builds and releases carry the same signing key and the same application id. Only then does every build replace the installed one in place. An APK with any other key can only be installed after an uninstall, and an uninstall costs the assistant role, every permission, the battery exemption and the "Always" choice for the hearing-aid tap.

The key lives outside the repository under `.local/keystore/` (git-ignored). GitHub never shows a stored secret again, so keep your own backup of the keystore file and its passwords.

The release job refuses to build when a secret is missing. After the build it compares the certificate inside the APK with the one in the keystore.

## Once: store the key as repository secrets

| Secret | Content |
|---|---|
| `LARA_KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `LARA_STOREPASSWORD` | `storePassword` from `.local/keystore/keystore.properties` |
| `LARA_KEYALIAS` | `keyAlias` from the same file |
| `LARA_KEYPASSWORD` | `keyPassword` from the same file |

Run this in the repository root, with the [GitHub CLI](https://cli.github.com) logged in:

```sh
props=.local/keystore/keystore.properties
value() { sed -n "s/^$1=//p" "$props"; }

base64 -w0 "$(value storeFile)"      | gh secret set LARA_KEYSTORE_BASE64
printf '%s' "$(value storePassword)" | gh secret set LARA_STOREPASSWORD
printf '%s' "$(value keyAlias)"      | gh secret set LARA_KEYALIAS
printf '%s' "$(value keyPassword)"   | gh secret set LARA_KEYPASSWORD

gh secret list
```

Nothing is printed and no value lands in the shell history. The helper expects plain `name=value` lines. If the file has blanks around `=` or escaped characters, run `gh secret set <NAME>` without a pipe and paste the value at the prompt.

GitHub replaces every secret value in its logs with `***`, the key alias included. With an alias such as `lara` the build log shows `***-im-ohr.apk`; the files themselves are named correctly.

## Cut a release

1. Push `main` and wait for its `ci` run to pass.
2. Choose the version (rules below), then tag and push:

   ```sh
   git tag -a v0.2.0 -m "Lara im Ohr 0.2.0"
   git push origin v0.2.0
   ```

3. Follow the run with `gh run watch` or in the Actions tab.
4. Install:

   ```sh
   gh release download v0.2.0
   sha256sum -c lara-im-ohr.apk.sha256
   adb install -r lara-im-ohr.apk
   ```

Never move a published tag. If a release is bad, fix it and tag the next patch version.

## Version rules

- The tag is `vMAJOR.MINOR.PATCH`. `versionName` is the tag without the `v`.
- `versionCode` is `MAJOR * 10000 + MINOR * 100 + PATCH`, so `v0.2.0` gives 200. MINOR and PATCH go up to 99; the job rejects anything else.
- A suffix such as `v0.2.0-rc1` makes a pre-release with the same `versionCode` as `v0.2.0`. Pre-releases do not count as "latest".
- Android does not install a lower `versionCode` over an installed release build (`adb install -d` only helps while the installed build is debuggable). Local builds default to `versionCode` 1, so after installing a release build them with at least its code: `./gradlew :app:assembleDebug -Plara.versionCode=200`.

## When the job fails

Each failure names its reason as an error annotation on the run.

| Annotation | What to do |
|---|---|
| Tag is not a version | Delete the tag (`git push origin :refs/tags/<tag>`) and push a valid one. |
| Signing secret missing | Set the named secret as shown above, then re-run the job. |
| Keystore secret unusable | The keystore, its password or the alias do not fit together. Set all four secrets again from `keystore.properties`. |
| Assistant metadata incomplete | `tools/check-assistant-metadata.sh` found the manifest or `voice_interaction_service.xml` incomplete. Such a build would silently lose the assistant role on the phone. Run the script locally and fix what it lists. |
| Wrong signing key, Wrong version in APK | `app/build.gradle.kts` no longer reads the `LARA_*` variables or the `lara.versionName` and `lara.versionCode` properties the workflow passes. |
| Unexpected build output | The release build did not leave exactly one APK in `app/build/outputs/apk/release`. Something in `app/build.gradle.kts` changed what is built (a flavour, an ABI split). |
| No build-tools | The runner image has no `apksigner`. Check that the job still runs on `ubuntu-24.04`, which ships the Android SDK. |
| Signature does not verify | `apksigner verify` rejects the APK. Re-run the job once; if it stays, the build produced a damaged or unsigned APK and `app/build.gradle.kts` is the place to look. |

A failing Gradle step is not on this list: the unit tests run again in this job, and its log names the test that failed.

If the job stops with "a release with the same tag name already exists", delete that release (`gh release delete <tag>`, the tag stays) and re-run the job.

## Updating the actions

Both workflows name the GitHub actions they use by commit, with the release as a comment, because a tag such as `v6` can be moved to other code and the release job holds the signing key. To move to a newer release of an action, look up the commit of its tag and change both workflow files:

```sh
git ls-remote https://github.com/gradle/actions 'refs/tags/v6.4.1^{}' 'refs/tags/v6.4.1'
```

For a tag that is an object of its own the line ending in `^{}` is the commit to use; otherwise there is only one line.

