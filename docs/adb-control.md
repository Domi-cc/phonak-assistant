# Control over adb

The app can be driven from a computer, so that a series of measurements can be run and repeated without touching the phone: start a session, change a scan setting, write a marker into the journal, pretend that a button of the remote was pressed.

**State: built, not yet tried on the device.** The commands below are what the code expects. Whether Android 17 lets the adb shell deliver them as described is one of the things to check first (see "First check").

## How it works

The app has one broadcast receiver for this, `io.github.domicc.lara/.control.AdbReceiver`. Android delivers a broadcast to it only if the sender holds `android.permission.DUMP`. The adb shell holds that permission. An ordinary app cannot get it: it is granted to system apps only, or by hand over adb to an app that asks for it in its manifest. So nothing that is merely installed on the phone can send these commands.

Every command is written to the journal as `adb_command` before it does anything. A command the app cannot carry out is journaled as `adb_rejected`, and nothing is changed.

## Sending a command

```sh
adb shell am broadcast -n io.github.domicc.lara/.control.AdbReceiver -a lara.STATUS
```

`-n` names the receiver and is required: the receiver has no intent filter. `-a` is the command. Parameters follow as extras: `--es name text`, `--ez name true|false`, `--ei name number`.

A shell function saves typing. The examples below use it.

```sh
lara() { adb shell am broadcast -n io.github.domicc.lara/.control.AdbReceiver "$@"; }
lara -a lara.STATUS
```

The answer is the last line that `am` prints:

```text
Broadcast completed: result=0, data="ok idle=low_power session=balanced passive=true rescue=false refresh=15 wake=true ..."
```

`data` starts with `ok` or with `error:` followed by the reason. A line without `data` means that the broadcast did not reach the app; see "If nothing happens".

Text with blanks has to be quoted twice, once for your shell and once for the shell on the phone:

```sh
lara -a lara.MARK --es text "'ab jetzt 2 h Ruhe'"
```

## Commands

| Command | Parameters | What it does |
|---|---|---|
| `lara.ECHO` | none | Starts the echo test, like "Echo-Test jetzt" on the screen. During a count session the echo test runs inside that session. |
| `lara.COUNT` | none | Starts a count session, like "Zählsitzung starten" on the screen. |
| `lara.SET` | any of the settings below | Changes the settings that are named and leaves the others as they are. Answers with all settings. |
| `lara.MARK` | `--es text "..."` | Writes a `marker` line with this text into the journal. |
| `lara.PRESS` | `--ei button 1..4` (default 1), `--es gesture <word>` (default `press`), `--ei missed 0..99` (default 0) | Simulates a press of the remote. See below. |
| `lara.STATUS` | none | Changes nothing. Answers with the settings and the state of the app. |

`lara.ECHO` and `lara.COUNT` answer `ok` as soon as the session is asked for. Whether it opened is in the journal: `session_link_up`, `session_end`, or `session_refused` and `session_busy` if it did not start.

### Settings for `lara.SET`

| Extra | Values | Setting on the screen |
|---|---|---|
| `--es idle` | `low_power`, `balanced`, `low_latency` | "Scan ohne Sitzung" (spar, mittel, schnell) |
| `--es session` | `low_power`, `balanced`, `low_latency` | "Scan in der Sitzung" |
| `--ez passive` | `true`, `false` | "Passive Scan-Art"; `false` is Android's default scan type |
| `--ez rescue` | `true`, `false` | "Rettungs-Scan" |
| `--ez wake` | `true`, `false` | "Shelly weckt auf"; `false` is the silent mode in which presses outside a session are only journaled and counted |
| `--ei refresh` | minutes, 0 to 1440 | How often the scans are registered anew as a precaution; 0 switches that off. Not on the screen. |

```sh
lara -a lara.SET --es idle low_power --es session low_latency
lara -a lara.SET --ez rescue true --ez wake false
```

A running listener applies a change at once and journals it (`scan_settings`, `shelly_wakes`, then `scan_started`). Android allows an app only a few scan starts per half minute. If you change modes faster than that, the app waits and journals `scan_deferred`; leave a few seconds between two changes.

### Simulated presses

```sh
lara -a lara.PRESS
lara -a lara.PRESS --ei button 2 --es gesture double
lara -a lara.PRESS --ei button 3
lara -a lara.PRESS --ei button 1 --ei missed 1
```

Gestures: `press`, `double`, `triple`, `long`, `long_double`, `long_triple`, `hold`, `hold_release`, `unknown`.

A simulated press takes the same way through the app as a real one from the point where a real one has been recognised: input bus, router, session. Buttons 1, 2 and 4 are counted aloud, button 3 runs the echo test, and with "Shelly weckt auf" switched on a press outside a session opens one. `--ei missed 1` makes the press claim that one event was lost before it, so that you hear "einer fehlt".

It tests nothing of the radio path. To keep the measurements clean, a simulated press

- is journaled as `sim_press`, never as `shelly_press`,
- carries `origin=simulated` in every line it causes (`input_routed`, `session_count`, and as `trigger` in all `session_*` lines of a session it opened),
- is not counted under "Shelly-Ereignisse" on the screen and does not change "letzter Druck".

A session that a simulated press opens is a real session with the hearing aid and counts under "Sitzungen".

### The answer of `lara.STATUS`

```text
ok idle=low_power session=balanced passive=true rescue=false refresh=15 wake=true enrolled=true scan=low_power/passive listener=true sessionOpen=false assistantBound=true prompts=101/101
```

`scan` is what is registered right now (`off` if nothing is). `sessionOpen` is true while a count session runs. `prompts` is how many spoken prompts are ready; while some are missing the app plays tones instead.

## Following along

```sh
adb logcat -s LaraJournal
```

Every journal line is also written to logcat. Lines that come from an input say where it came from, in `origin` (or `trigger` for `session_*` lines):

| Word | Meaning |
|---|---|
| `tap` | The hearing-aid tap (Android started the app with `VOICE_COMMAND`) |
| `ble_callback` | A press of the remote, delivered by the scan of the listener service |
| `ble_rescue` | A press of the remote, delivered by the rescue scan |
| `screen` | A button on the app's screen |
| `adb` | A command from this page |
| `simulated` | A press made up by `lara.PRESS` |
| `assist` | Android's assist gesture |

## An example series

Twenty simulated presses in a session, then a change of the session scan mode for the next run with the real remote:

```sh
lara() { adb shell am broadcast -n io.github.domicc.lara/.control.AdbReceiver "$@"; }

lara -a lara.MARK --es text "'Reihe 1: 20 simulierte Druecke'"
lara -a lara.COUNT
sleep 4                                   # the voice link needs about a second
for i in $(seq 1 20); do
    lara -a lara.PRESS --ei button 1
    sleep 2
done
sleep 25                                  # the session closes after 20 s without input

lara -a lara.SET --es session low_latency
lara -a lara.MARK --es text "'Reihe 2: echter Shelly, Sitzung schnell'"
```

## Things to know before measuring

- **USB keeps the phone awake.** On a cable the phone charges and does not go into deep sleep, so nothing measured that way says anything about standby. Use wireless debugging, or run `adb shell dumpsys battery unplug` before and `adb shell dumpsys battery reset` after.
- **Every command wakes the phone for a moment.** A command sent in the middle of a standby measurement disturbs it. Set everything up, write the marker, then leave the phone alone.
- **A session needs the assistant role.** From the background Android lets the app start its microphone service only while the app is the digital assistant and its service is bound (`assistantBound=true`). Otherwise the journal shows `session_refused`.
- **The journal contains Bluetooth addresses and device names.** Remove them before you post a log.

## If nothing happens

- No `data` in the answer and no `adb_command` in the journal: the broadcast did not reach the app. Android delivers no broadcasts to an app that was never opened since it was installed or that was force-stopped; open the app once. Check the component name, including the dot before `control`.
- `data="error: ..."`: the command reached the app and was refused; the text says why.
- `data="ok ..."` but no session: look for `session_refused` or `session_busy` in the journal.

## First check

This path has not run on the phone yet. Before relying on it:

1. `lara -a lara.STATUS` must answer with `data="ok ..."`, and `adb_command` must appear in the journal.
2. `lara -a lara.MARK --es text "'test'"` must produce a `marker` line with `origin=adb`.
3. With the phone locked and the display off, `lara -a lara.COUNT` followed by `lara -a lara.PRESS` must be heard in the hearing aid as the start tones and "eins".
