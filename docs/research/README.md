# Research notes

These are the working notes behind the design. They were written on 27 November 2026 by AI research agents, in two passes:

1. **First pass**: ten reports that read documentation, the Android 17 source and other projects.
2. **Verification pass**: nine reports whose only job was to try to refute the claims of the first pass against primary sources.

Where the two disagree, **the verification report wins**. Where a report and a measurement on the real phone disagree, **the measurement wins**; see [`../measurements/`](../measurements/) and the "What Android taught us" section of the top-level README.

> [!WARNING]
> Nothing in these reports was run on a device by its author. Most Shelly research describes the
> ZB model (`SBBT-104CUS`); the remote actually used is the model without Zigbee (`SBBT-004CUS`).
> The public Android source is version 17.0; the phone runs a newer build. Treat every statement
> as a lead to check, not as a fact.

Local paths in the reports were replaced by placeholders such as `<research>/` and `<repos>/`. Files that the reports mention next to themselves (downloaded pages, source copies) are not part of this repository.

## First pass

| Report | Topic |
|---|---|
| [ble-shelly.md](ble-shelly.md) | Shelly BLU button remotes: BTHome format, timing, encryption, existing Android readers |
| [android-bg-ble.md](android-bg-ble.md) | Receiving BLE advertisements with the screen off: scan modes, throttling, foreground services |
| [assistant-role.md](assistant-role.md) | Becoming the default assistant; what a headset voice button really triggers |
| [bt-audio.md](bt-audio.md) | Two-way audio with a Bluetooth Classic hearing aid: routing, SCO, the Phonak EON |
| [speech.md](speech.md) | Speech-to-text, text-to-speech and microphone rules on a locked phone |
| [mediasession.md](mediasession.md) | Media buttons as a trigger: routing rules and the media-key listener |
| [hermes.md](hermes.md) | Talking to a self-hosted Hermes Agent from Android |
| [toolchain-release.md](toolchain-release.md) | Build tools, signing, releases, sideloading, debugging toolkit |
| [related-hearing.md](related-hearing.md) | Catalogue: projects for people who wear hearing aids |
| [related-adjacent.md](related-adjacent.md) | Catalogue: adjacent building blocks (assistants, remotes, speech, tools) |

## Verification pass

| Report | What it checked |
|---|---|
| [verify-hfp-voice-command.md](verify-hfp-voice-command.md) | The hands-free voice-recognition path and the VOICE_COMMAND intent |
| [verify-assistant-privileges.md](verify-assistant-privileges.md) | What the assistant binding grants, and what it does not |
| [verify-audio-hardening.md](verify-audio-hardening.md) | Android 17 background audio hardening and the communication route rules |
| [verify-scan-modes.md](verify-scan-modes.md) | BLE scan parameters, the screen-off mode and the scan allowance |
| [verify-media-key-listener.md](verify-media-key-listener.md) | The adb-grantable media-key listener |
| [verify-bthome-shelly.md](verify-bthome-shelly.md) | BTHome format and Shelly behaviour, with test vectors |
| [verify-phonak-eon.md](verify-phonak-eon.md) | Phonak Audeo EON: profiles, Tap Control, single-aid fitting |
| [verify-hermes-api.md](verify-hermes-api.md) | The Hermes API server at release v0.23.4 |
| [verify-versions.md](verify-versions.md) | Tool and library versions, SDK package names, CI action versions |

## Later checks

Written after the two passes. The warning above holds for these too.

| Report | Question |
|---|---|
| [hearing-function.md](hearing-function.md) | 28 November 2026: does a sound channel from the phone get in the way of the hearing aid's own job? What the aid does while streaming, how long Android keeps a stream open, every audio path of the app, and the tests on the ear. Three reports, each re-checked, in one document |
