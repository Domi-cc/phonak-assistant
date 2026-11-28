# Control over adb

The app can be driven from a computer, so that a series of measurements can be run and repeated without touching the phone: start a session, change a scan setting, set how announcements are played and how loud, write a marker into the journal, pretend that a button of the remote was pressed, ask for a new plan of the calendar reminders, play a reminder as a test.

**State: built, not yet tried on the device.** The commands below are what the code expects. Whether Android 17 lets the adb shell deliver them as described is one of the things to check first (see "First check").

## How it works

The app has one broadcast receiver for this, `io.github.domicc.lara/.control.AdbReceiver`. Android delivers a broadcast to it only if the sender holds `android.permission.DUMP`. The adb shell holds that permission. An ordinary app cannot get it: it is granted to system apps only, or by hand over adb to an app that asks for it in its manifest. So nothing that is merely installed on the phone can send these commands.

The one exception is of your own making: an app to which you have granted `DUMP` over adb (some diagnostic tools ask for that) can send these commands too, which includes starting a session with the microphone. `adb shell dumpsys package <package>` lists the permissions an app was granted.

Every command is written to the journal as `adb_command` before it does anything. A command the app cannot carry out exactly as given is journaled as `adb_rejected`, and nothing is changed: a value of the wrong kind, a number outside its range and a setting name the app does not know all reject the whole command.

A session started from here is a session like every other: it goes to the selected hearing aid or nowhere, and a failure before the link is up makes the phone vibrate.

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
Broadcast completed: result=0, data="ok idle=low_power session=balanced passive=true rescue=false refresh=0 wake=true tap=remote channel=media level=off leadin=450 ..."
```

`data` starts with `ok` or with `error:` followed by the reason. A line without `data` means that the broadcast did not reach the app; see "If nothing happens".

Text with blanks has to be quoted twice, once for your shell and once for the shell on the phone:

```sh
lara -a lara.MARK --es text "'ab jetzt 2 h Ruhe'"
```

## Commands

| Command | Parameters | What it does |
|---|---|---|
| `lara.REMOTE` | none | Starts a remote session, like "Shelly-Sitzung" on the screen: every press is announced in words ("Taste 1 kurz"). |
| `lara.ECHO` | none | Starts the echo test, like "Echo-Test jetzt" on the screen. During a remote session or a count session the echo test runs inside that session; if it fails there, the journal says so (`session_remote_echo_failed`, `session_count_echo_failed`) and the session goes on. |
| `lara.COUNT` | none | Starts a count session, like "Zählsitzung starten" on the screen: every press gets its running number. |
| `lara.SET` | any of the settings below | Changes the settings that are named and leaves the others as they are. Answers with all settings. |
| `lara.MARK` | `--es text "..."` | Writes a `marker` line with this text into the journal. |
| `lara.PRESS` | `--ei button 1..4` (default 1), `--es gesture <word>` (default `press`), `--ei missed 0..99` (default 0) | Simulates a press of the remote. See below. |
| `lara.STATUS` | none | Changes nothing. Answers with the settings and the state of the app. |
| `lara.CALENDAR` | none | Plans the calendar reminders anew, as a change in the calendar would, and answers with the numbers of that plan. See below. |
| `lara.REHEARSE` | none | Plays the reminder for the next appointment as it sounds ten minutes before it, like "Erinnerung testen" on the screen. See below. |

`lara.REMOTE`, `lara.ECHO` and `lara.COUNT` answer `ok` as soon as the session is asked for. Whether it opened is in the journal: `session_link_up`, `session_end`, or `session_refused` and `session_busy` if it did not start. A session is busy until `session_link_down`, a moment after its `session_end`: wait for that line, or a second, before you ask for the next one.

Every session ends the same way: after 30 s without a press, on a long press of button 4, or on a tap on the hearing aid; none lasts longer than five minutes. A normal end says "Beende".

### Settings for `lara.SET`

| Extra | Values | Setting on the screen |
|---|---|---|
| `--es tap` | `remote`, `echo`, `count` | "Hörgeräte-Tap startet": what a double tap on the hearing aid starts. The default is `remote`; while no remote is enrolled, a tap with that setting runs the echo test. |
| `--ez wake` | `true`, `false` | "Shelly weckt auf": a press outside a session opens a remote session. `false` is the silent mode in which presses outside a session are only journaled and counted. |
| `--es idle` | `low_power`, `balanced`, `low_latency` | "Scan ohne Sitzung" (spar, mittel, schnell) |
| `--es session` | `low_power`, `balanced`, `low_latency` | "Scan in der Sitzung": the mode while a remote session or a count session is open. An echo test on its own does not change the scan. |
| `--ez passive` | `true`, `false` | "Passive Scan-Art"; `false` is Android's default scan type |
| `--ez rescue` | `true`, `false` | "Rettungs-Scan" |
| `--ei refresh` | minutes, 0 to 1440 | How often the scans are registered anew as a precaution; 0, the default, switches that off. Not on the screen. See the warning below. |
| `--es channel` | `media`, `assistant` | "Lautstärke-Kanal": which of Android's volumes an announcement is played under. The default is `media`. See "How loud announcements are" below. |
| `--es level` | `off`, or a step from 1 to the phone's highest media step (`--ei level 18` works too) | "Eigene Lautstärke nur für Ansagen". A number switches it on with that step; `off` switches it off and keeps the step. Never 0. Only applied in the channel `media`: with `channel=assistant` the step is stored and shown, and the app sets nothing. |
| `--ei leadin` | `450`, `1500`, `3000` (milliseconds) | "Vorlauf vor der Ansage": the silence in front of the chime of an announcement |

```sh
lara -a lara.SET --es idle low_power --es session low_latency
lara -a lara.SET --ez rescue true --ez wake false
lara -a lara.SET --es tap count
lara -a lara.SET --es level 18 --ei leadin 1500
lara -a lara.SET --es channel assistant
```

**`refresh` changes what you measure.** Registering anew means stopping the running scan and starting another. In the Android 17.0 source a scan books its use of the hourly scan allowance only when a delayed job fires (after 6 minutes in `low_latency`, after 24 minutes in `balanced`), and stopping the scan cancels that job. With `refresh` below those times a scan is never throttled, and a catch rate measured that way says nothing about Android's limits. The alarm behind `refresh` also wakes a dozing phone. So: keep `refresh` at 0 for measurements, or treat it as one more thing you vary, and compare catch rates only between runs with the same value. Every `scan_started` line carries it as `refreshMinutes`. Without the refresh the scans are still registered anew when Bluetooth comes back, when the process starts and when the app is updated.

Every change is journaled (`scan_settings`, `shelly_wakes`, `tap_setting`, `announce_setting`), and a running listener applies a changed scan setting at once (`scan_started`). Android allows an app only a few scan starts per half minute. If you change modes faster than that, the app waits and journals `scan_deferred`; leave a few seconds between two changes. Opening a session that listens to the remote costs one scan start (`reason=session_open`). The way back costs another one, 15 s after the session has closed (`reason=session_closed`); a session that opens within those 15 s finds the scan still in session mode and costs none. Lines of the remote from those 15 s carry the session mode in `mode` and `session=false`.

### Simulated presses

```sh
lara -a lara.PRESS
lara -a lara.PRESS --ei button 2 --es gesture double
lara -a lara.PRESS --ei button 4 --es gesture long
lara -a lara.PRESS --ei button 1 --ei missed 1
```

Gestures: `press`, `double`, `triple`, `long`, `long_double`, `long_triple`, `hold`, `hold_release`, `unknown`.

A simulated press takes the same way through the app as a real one from the point where a real one has been recognised: input bus, router, session. It needs no enrolled remote.

- **Outside a session**, with "Shelly weckt auf" switched on, it opens a remote session and is the first press that session announces. Not while the phone is in a call or ringing: then it is given up (`input_dropped` with `reason=phone_call`) and the phone vibrates.
- **In a remote session** it is announced in words, whatever the gesture ("Taste 2 doppelt", "Taste 1 gehalten"), and does nothing else. A long press of button 4 ends the session.
- **In a count session** buttons 1, 2 and 4 are counted aloud and button 3 runs the echo test. `hold` and `hold_release` get no number and start no echo test (`session_count_held`): the remote sends `long` or `press` when a held button is released, and that is what counts. A long press of button 4 ends the session and gets no number.

`unknown` does nothing anywhere (`input_routed` with `route=none`).

`--ei missed 1` makes the press claim that one event was lost before it, so that you hear "einer fehlt" (in a remote session "mehrere fehlen" for more than one, in a count session the number: "drei fehlen"). Not with the first press of a session: a loss before it is not spoken, because it lies before the session. Send an ordinary press first.

It tests nothing of the radio path. To keep the measurements clean, a simulated press

- is journaled as `sim_press`, never as `shelly_press`,
- carries `origin=simulated` in every line it causes (`input_routed`, `session_remote_press`, `session_count`, and as `trigger` in all `session_*` lines of a session it opened),
- is not counted under "Shelly-Ereignisse" on the screen and does not change "letzter Druck".

A session that a simulated press opens is a real session with the hearing aid and counts under "Sitzungen".

### The answer of `lara.STATUS`

```text
ok idle=low_power session=balanced passive=true rescue=false refresh=0 wake=true tap=remote channel=media level=off leadin=450 enrolled=true scan=low_power/passive listener=true sessionBusy=false sessionOpen=false assistantBound=true hearingAid=connected prompts=139/139 calendar=off mediaStep=15/25 mediaMuted=false mediaToAid=true
```

Match the fields by name, not by position.

- `scan` is what is registered right now: mode and scan type of the listener's scan, or `off` if there is none, followed by `+rescue` if the rescue scan is registered. So `low_power/passive`, `balanced/active+rescue`, `off` and `off+rescue` are all possible.
- `sessionBusy` is true from the moment a session is accepted until its link is closed and the hands-free audio is down. `sessionOpen` is true while that session listens to the remote (remote session or count session); `scan` keeps the session mode for 15 s longer.
- `hearingAid` is `connected`, `not_connected` or `not_selected`. Without a selected hearing aid the app stays silent.
- `prompts` is how many spoken phrases are ready; while some are missing the app plays tones instead.
- `calendar` is the state of the spoken calendar reminders ("Termine ansagen"): `off`, `on`, or `no_permission` if they are switched on but the app may not read the calendar.
- `channel`, `level` and `leadin` are the settings of the section "Ansagen", as `lara.SET` takes them. `level` is `off` or the step the app sets for an announcement in the channel `media`; with `channel=assistant` a step is shown here too, but the app sets no volume in that channel.
- `mediaStep` is the phone's media volume right now, as step/highest step. It is the hearing aid's only while `mediaToAid=true`, which means that Android would send media to the selected hearing aid and to nothing else; with another output the step is that output's. `mediaMuted=true` means that media are muted, by hand or by Do Not Disturb: announcements in the channel `media` are then silent.

### How loud announcements are

Since Android 17, sound played as assistant speech has a volume of its own that an app cannot set and that the media volume does not move. So announcements are played as media by default (`channel=media`) and follow the phone's media volume for the hearing aid. `channel=assistant` plays them as the builds before did. The background, and what of it is untested: [`CLAUDE.md`](../CLAUDE.md), "How loud an announcement is", and [`docs/measurements/lautstaerke.md`](measurements/lautstaerke.md) (German, step by step).

- `level=off`: announcements play at the phone's media volume as it is, and the app writes nothing. Over adb that volume can be set with Android's own command, for example `adb shell cmd media_session volume --stream 3 --set 18` (not tried on this phone); it acts on whatever is the media output at that moment.
- `level=18`: before each announcement the app sets step 18 of the media volume, and puts the phone's value back half a second after the last word (at once if the announcement was cut off; not at all, for the time being, if the hearing aid is gone or going: then it is put back when the aid is the output again). Only while the hearing aid alone is the media output, and not while the stream is muted, during a call, or while other media play. `lara.SET` only stores the step; nothing is written until an announcement plays.
- A step beyond the phone's scale is rejected (`level must be off or a step from 1 to 25`).

What happened is in the journal, once per announcement: `announce_volume` (`action` is `kept`, `set`, `already`, `skipped` or `refused`, with `why`; `mBefore`, `mWanted`, `mGot`, `mMax` are steps of the media volume, the hearing aid's only while `mRoute=aid`; `governing` is the stream Android puts the announcement under, 3 for media and 11 for the assistant volume), `announce_volume_mid` half a second into the speech (none for a speech shorter than that), and, if the app set or looked after the volume, `announce_volume_restore` (`restored`, `adopted` if the volume keys were pressed during the announcement, `left_muted`, `deferred_route`, `deferred_going_away`, `nothing_to_restore`, `restore_refused`). `volume_state` is written at the start of the app and whenever the app looks at a value it has not put back for good: two seconds after every restore (`trigger=restore_check`, expected `leftover=confirmed`), and when the hearing aid may be the output again. All words: [`CLAUDE.md`](../CLAUDE.md), "The instrument".

There is no command for the spoken sample of the screen's slider. `lara.REHEARSE` plays an announcement of the length of a real reminder and takes the same way.

### Rehearsing a reminder: `lara.REHEARSE`

```sh
lara -a lara.REHEARSE
```

Plays "In 10 Minuten: <title of the next appointment>" into the hearing aid, through the same code as a real reminder: the reminder exactly as it will sound ten minutes before that appointment. If there is no appointment in the next 24 hours, if the reminders are switched off, if the permission is missing or the calendar cannot be read, it says "In 10 Minuten: Beispieltermin" instead, so that there is always something to hear. With the reminders off it does not touch the calendar. It changes nothing about what is planned or remembered.

The answer is `ok rehearsal asked for`, at once. What became of it is in the journal: `calendar_test` with `kind=rehearsal`, `events`, `sample` (true if the sample title was used, with `sampleWhy`) and `textLength`, then the lines of the announcement with `reason=calendar_rehearsal`. During a session or a phone call nothing is played (`calendar_test_skipped`). The title is in neither the answer nor the journal.

### Calendar reminders: `lara.CALENDAR`

Ten minutes before an appointment the app says "In 10 Minuten: Zahnarzt" into the hearing aid, if "Termine ansagen" is switched on in the section "Kalender". The rules are in [`docs/design/kalender-ansagen.md`](design/kalender-ansagen.md) (German). The switch and the choice of calendars are on the screen only; over adb you can ask for a new plan and read its numbers:

```sh
lara -a lara.CALENDAR
```

```text
ok enabled=true permission=true readable=true calendars=2 events=3 wake=13:50 kind=reminder
```

- `calendars` is how many calendars are selected. `events` is how many appointments with a reminder in the next 24 hours could be announced at all (not begun, not all-day, not cancelled, not declined, calendar selected), whether or not they have been.
- `wake` is when the app plans next, as a time of day, and `kind` says why: `reminder` (an announcement falls due), `retry` (an announcement waits for the end of a session or of a phone call and is tried again every 20 s), `look_again` (the app reads the calendar again; it never lets more than six hours pass without doing so, whether or not an announcement is planned) or `read_again` (the calendar could not be read; a new try, a minute after the first failure and less often after each further one). Both are `none` while the reminders are off or the permission is missing.
- `readable=false` means the phone's calendar storage could not be read; the journal says more (`calendar_provider_error`). An alarm that was planned before stays where it was: `wake` is then that time, or the next try to read if that comes first.

The answer comes when the calendar has been read. That is expected to take a fraction of a second; it has not been measured. A plan that finds an announcement waiting for a session which has just ended holds it back for another second. If the plan takes longer than 8 s the answer is an error, the journal has `adb_rejected`, and the plan may still follow. A calendar storage that does not answer is given 3 s per question (`calendar_provider_error` with `why=timeout`).

A plan that finds an appointment due announces it, like every other plan. So this command can make the phone speak, into the selected hearing aid or nowhere.

**The answer and the journal never contain what an appointment is called.** Titles, names of calendars and accounts stay out of both; the journal has counts, the numbers Android gives calendars and events, times and lengths (`calendar_plan`, `calendar_alarm`, `calendar_announce`, `calendar_missed`, and `announce_start` with `textLength` in place of the text). What each line means is in the design document, "Was im Protokoll steht".

## Following along

```sh
adb logcat -s LaraJournal
```

Every journal line is also written to logcat. That includes the `shelly_packet` lines (one per advertisement of the remote), which the app's screen leaves out. Lines that come from an input say where it came from, in `origin` (or `trigger` for `session_*` lines):

| Word | Meaning |
|---|---|
| `tap` | The hearing-aid tap (Android started the app with `VOICE_COMMAND`) |
| `ble_callback` | A press of the remote, delivered by the scan of the listener service |
| `ble_rescue` | A press of the remote, delivered by the rescue scan |
| `screen` | A button on the app's screen |
| `adb` | A command from this page |
| `simulated` | A press made up by `lara.PRESS` |
| `assist` | Android's assist gesture |

What became of an input is in `input_routed` (`route` is one of `start_remote`, `start_echo`, `start_count`, `wake_remote`, `hold`, `to_session`, `none`, `too_late`). A press that was given up is journaled as `input_dropped` with the reason: `session_did_not_open`, `session_closed`, `phone_call`, `too_old` for a press that reached the app more than 20 s after it was made, and `no_consumer` or `bus_full` if it never got as far as the router. A press made while a session was saying "Beende" opens the next session as soon as the old one is completely over (`input_retry`).

## An example series

Twenty simulated presses in a count session, then a change of the session scan mode for the next run with the real remote:

```sh
lara() { adb shell am broadcast -n io.github.domicc.lara/.control.AdbReceiver "$@"; }

lara -a lara.MARK --es text "'Reihe 1: 20 simulierte Druecke'"
lara -a lara.COUNT
sleep 4                                   # the voice link needs about a second
for i in $(seq 1 20); do
    lara -a lara.PRESS --ei button 1
    sleep 2
done
lara -a lara.PRESS --ei button 4 --es gesture long   # ends the session: "Beende"
                                          # you should have heard "eins" to "zwanzig"

lara -a lara.SET --es session low_latency
lara -a lara.MARK --es text "'Reihe 2: echter Shelly, Sitzung schnell'"
```

## Things to know before measuring

- **USB keeps the phone awake.** On a cable the phone charges and does not go into deep sleep, so nothing measured that way says anything about standby. Use wireless debugging, or run `adb shell dumpsys battery unplug` before and `adb shell dumpsys battery reset` after.
- **Every command wakes the phone for a moment.** A command sent in the middle of a standby measurement disturbs it. Set everything up, write the marker, then leave the phone alone.
- **A session needs the assistant role.** From the background Android lets the app start its microphone service only while the app is the digital assistant and its service is bound (`assistantBound=true`). Otherwise the journal shows `session_refused` and the phone vibrates.
- **The journal contains Bluetooth addresses and device names.** Remove them before you post a log.

## If nothing happens

- No `data` in the answer and no `adb_command` in the journal: the broadcast did not reach the app. Android delivers no broadcasts to an app that was never opened since it was installed or that was force-stopped; open the app once. Check the component name, including the dot before `control`.
- `data="error: ..."`: the command reached the app and was refused; the text says why.
- `data="ok ..."` but no session: look for `session_refused` or `session_busy` in the journal.

## First check

This path has not run on the phone yet. Before relying on it:

1. `lara -a lara.STATUS` must answer with `data="ok ..."`, and `adb_command` must appear in the journal.
2. `lara -a lara.MARK --es text "'test'"` must produce a `marker` line with `origin=adb`.
3. With the phone locked and the display off, `lara -a lara.REMOTE` followed by `lara -a lara.PRESS` must be heard in the hearing aid as the start tones and "Taste 1 kurz".
4. `lara -a lara.CALENDAR` must answer with its numbers after the plan has run, not with an empty `data`: the answer is given from another thread, a moment after the receiver has returned.
5. `lara -a lara.REHEARSE` must be heard in the hearing aid as a chime and "In 10 Minuten: …", and the journal must show `announce_volume` in front of `announce_start`.
6. `lara -a lara.SET --es level 20` followed by `lara -a lara.REHEARSE`, with the phone locked and the display off: `announce_volume` must read `action=set` with `mGot` equal to `mWanted` (or `already`), `announce_volume_restore` must read `result=restored`, and two seconds later `volume_state` must read `trigger=restore_check leftover=confirmed`. An `action=refused` here means that Android does not take a volume from this app in the background.
