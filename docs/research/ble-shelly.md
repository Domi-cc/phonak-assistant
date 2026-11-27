# Shelly BLU RC Button 4 ZB as a BLE remote, and what existing code teaches us

Research date: 2026-11-27. Nothing here was measured on a device. Every statement is tagged
**[verified]** (seen in a primary source, source given), **[inferred]** (reasoned from sources)
or **[unknown]**.

Local evidence files (fetched pages, cloned repositories) are under
`<research>/`
(pages, some in the sub-folder `ble-shelly/`) and
`<repos>/`.

---

## 0. Short answer

- The remote exists, is documented by the vendor and emits **unencrypted BTHome v2 service data
  (UUID 0xFCD2) on every button event out of the box**. No Shelly app, account, hub, Zigbee
  coordinator or pairing is needed to receive it.
- One real capture of a ZB unit is public: `44 00 0e 01 64 3a 00 3a 01 3a 00 3a 00`
  (device info 0x44, packet id, battery, four fixed button slots).
- Vendor documentation gives **one 1500 ms advertising burst per button event**, plus a periodic
  beacon every 15 min (12 s or 8 s in beacon mode; the two vendor documents disagree).
  The advertising interval inside the burst and the delay before the burst (multi-click
  decision time) are **not documented for the ZB model**.
- A 1.5 s burst is always caught by a 100 % or 25 % duty-cycle scan (LOW_LATENCY, BALANCED) and
  by LOW_POWER (140 ms / 1400 ms). It is caught only about **1 time in 5** by Android's
  screen-off low-power mode (512 ms window every 10.24 s). The app therefore depends on Android
  *not* demoting its scan to that mode. AOSP 17 contains a new, flag-gated "scan allowance"
  throttle that would do exactly that for apps without a visible activity.
- BTHome encryption (AES-128-CCM, 4-byte counter, 4-byte MIC) gives confidentiality and
  integrity. Replay protection exists only if **our receiver** enforces counter monotonicity and
  rejects plaintext from a device known to be encrypted. Android has no documented AES/CCM
  `Cipher`; CCM has to be built on AES/ECB or taken from Bouncy Castle.
- SaraButton is a four-month-old, single-author, zero-star, zero-issue Java app (branch names
  `codex/...`). Its structure is a useful checklist, not a reliability proof. A second,
  independent Kotlin app (altinojoao/bthome-bridge, MIT) solves the same "button in the pocket,
  screen off" problem with a different recipe and records field lessons in its comments.

---

## 1. The advertisement emitted on a button press

### 1.1 Identity

| Item | Value | Status and source |
|---|---|---|
| Product | Shelly BLU RC Button 4 ZB | [verified] https://shelly-api-docs.shelly.cloud/docs-ble/Devices/BLU_ZB/button4_ZB/ |
| Short device name / model | `SBBT-104CUS` | [verified] same page: "Short device name: SBBT-104CUS"; https://kb.shelly.cloud/knowledge-base/shelly-blu-rc-button-4-zb "Device model: SBBT-104CUS" |
| Shelly Bluetooth device id | `0x16` (22), sent as BTHome object `0xF0` (uint16 LE: `F0 16 00`) | [verified] API page "Shelly Bluetooth Device Id: 0x16"; KB "Device Bluetooth ID: 0x0016"; vendor code `ALLTERCO/fleet-management` `backend/src/config/BTHomeData.ts`: `'SBBT-104CUS': { productName: 'Shelly BLU RC Button 4 ZB', modelId: 22, isRemote: true, reportCadenceSec: 900 }` |
| MCU | Silicon Labs EFR32MG27, 768 kB flash | [verified] KB page, "Microcontroller unit" |
| Size / battery | 65 x 30 x 13 mm, 21 g, 1 x CR2032, "Up to 2 years" | [verified] KB page |
| Radio | Bluetooth 5 (LE) and 802.15.4 Zigbee, max RF power < 8 dBm, "Up to 10 m indoors and 30 m outdoors" | [verified] KB page |
| Non-ZB sibling | `SBBT-004CUS`, device id `0x0007` | [verified] https://shelly-api-docs.shelly.cloud/docs-ble/common "0x0007 Shelly BLU RC Button 4 SBBT-004CUS" |

The model table on the vendor's "Common features" page stops at `0x000C`; the ZB ids (0x15, 0x16,
0x17 ...) are only on the per-device pages. [verified]

### 1.2 BTHome framing

BTHome v2, service data AD type 0x16 with 16-bit UUID 0xFCD2 (bytes `D2 FC` on air).
[verified] https://bthome.io/format/ ("The UUID has to be read reversed per byte, so the UUID is
0xFCD2"; "Allterco Robotics, the manufacturer of Shelly devices, has sponsored this UUID").

Device-information byte (first byte after the UUID) [verified] bthome.io/format:

- bit 0: encryption flag
- bit 2: trigger-based device flag (irregular interval, "e.g. only when someone pushes a button")
- bits 5-7: BTHome version (2 = `010`)

Values seen for Shelly BLU buttons:

| Byte | Meaning | Where seen |
|---|---|---|
| `0x44` | v2, trigger based, not encrypted | [verified] real ZB capture below; Theengs test vectors for the four-button family |
| `0x40` | v2, not trigger based, not encrypted | [verified] Theengs test vectors for older BLU Button1 firmware (`40001d01643a01`); also all SaraButton and bthome-ble unit tests (synthetic) |
| `0x45` | v2, trigger based, encrypted | [verified] Theengs test vector for an encrypted BLU Button1 (`4562511158bd25b8f093645b573115`) |
| `0x41` | v2, encrypted, not trigger based | [verified] bthome.io/encryption example only |

A parser must therefore decide on `(byte >> 5) == 2` and `byte & 1`, never on equality with 0x40.

### 1.3 Objects and encodings

Vendor table for the ZB model (https://shelly-api-docs.shelly.cloud/docs-ble/Devices/BLU_ZB/button4_ZB/) [verified]:

```
Beacon   - "Periodic, with interval: beacon mode: 12s, normal mode: 15min"
  0x01 battery %          uint8
  0xF0 device type id     uint16
  0xF1 firmware version   uint32
  0xF2 firmware version   uint24
Button events - "Single, with duration 1500ms"
  0x3A button1 uint8   1x, 2x, 3x short press, 1x, 2x, 3x long press, hold, none
  0x3A button2 uint8   (same)
  0x3A button3 uint8   (same)
  0x3A button4 uint8   (same)
```

The ZB page does not list the numeric event values and does not list the packet id. They come from:

- BTHome specification, object 0x3A "button" [verified] https://bthome.io/format/ :
  `0x00` none, `0x01` press, `0x02` double_press, `0x03` triple_press, `0x04` long_press,
  `0x05` long_double_press, `0x06` long_triple_press, `0x80` hold_press, `0x81` hold_release.
- Non-ZB RC Button 4 page [verified] https://shelly-api-docs.shelly.cloud/docs-ble/Devices/BLU/wall_us :
  `0x00 packet id uint8 revolving counter`, `0x01 battery uint8`, `0x3A button`; events
  0x00 None, 0x01 press, 0x02 double_press, 0x03 triple_press, 0x04 long_press, 0x80 button hold,
  "0xFE* button hold ... for firmware prior to 1.0.20"; "The state of the 4 buttons of the device
  are transmitted with every packet and are at fixed position. Example ... 0x3A 0x01 0x3A 0x00
  0x3A 0x01 0x3A 0x00 - buttons 1 and 3 are short pressed, buttons 2 and 4 are not."
- BLU changelog [verified] https://shelly-api-docs.shelly.cloud/docs-ble/changelog :
  "[1.0.22] Change button hold event to 0x80 as is in BTHome official documentation".

**Real capture of an SBBT-104CUS** (firmware reported as `260310/v1.2.13@f43a1caa`), relayed by a
Shelly BLU Gateway script and pasted into a public issue
[verified as a third-party report] https://github.com/iobroker-community-adapters/ioBroker.shelly/issues/1506 :

```
"srcBle":{"type":"SBBT-104CUS","mac":"f8:..","rssi":-70},"payload":"44000e01643a003a013a003a00"
unpacked: {"encryption":false,"BTHome_version":2,"pid":14,"battery":100,
           "button_1":0,"button_2":1,"button_3":0,"button_4":0}
```

Decoded:

```
44        device info: v2, trigger based, unencrypted
00 0e     packet id = 14
01 64     battery = 100 %
3a 00     button 1: none
3a 01     button 2: press
3a 00     button 3: none
3a 00     button 4: none
```

Resulting table for the ZB model:

| Object | Bytes | Encoding | Status |
|---|---|---|---|
| packet id | `00 xx` | uint8, same value in all repeats of one event, changes on the next event, wraps at 255 | [verified] present in the real capture; "revolving counter" in vendor common page; repeat semantics [verified] bthome.io ("send multiple advertisements that are exactly the same ... only process the advertisement if the packet id is different") |
| battery | `01 xx` | uint8 percent | [verified] real capture |
| button 1..4 | four times `3A xx`, fixed order | uint8 event code | [verified] real capture (single press of button 2 = `01`) |
| single press | `01` | | [verified] real capture |
| double / triple press | `02` / `03` | | [inferred] BTHome spec + non-ZB vendor page + HA forum report that the ZB unit delivers "single press, double press, triple press, and hold press" over BTHome (https://community.home-assistant.io/t/shelly-blu-rc-button-4-zigbee-mode/978579 post 2) |
| long press (1x, 2x, 3x) | `04` / `05` / `06` | | [inferred] BTHome spec; vendor ZB page lists "1x, 2x, 3x long press" without numbers |
| hold | `80` (accept legacy `FE` too) | | [inferred] BTHome spec + BLU changelog 1.0.22; not confirmed by a ZB capture |
| hold release `81` | | | [unknown] not in the vendor event list |
| device type id | `F0 16 00` | uint16 LE | [verified] vendor page; only documented for the beacon packet |
| firmware version | `F1 ..` (4 bytes) / `F2 ..` (3 bytes) | | [verified] vendor page, beacon packet |

Notes:

- Several buttons can carry an event in the same packet ("simultaneous button presses", user
  guide; non-ZB example above). [verified for the product family]
- Whether the 15-minute beacon packet also contains the packet id and four `3A 00` slots is
  [unknown]. A receiver must treat a packet with no non-zero button slot as "no event".
- The vendor ZB changelog says for firmware BLU_FW 1.2.12 (2026-03-04): "Button1 Battery percentage
  included in button press BLE advertisements" and "Wall US Removed parasitic BTHome item that
  appeared during BLE pairing" (https://shelly-api-docs.shelly.cloud/docs-ble/changelog_mg27).
  "Wall US" is the vendor's internal name for the RC Button 4 ([verified] BLU changelog:
  "Wall US (RC Button4) ..."). So payload shape can change with firmware; the parser must be
  table driven and must not assume a fixed length of 13 bytes.

### 1.4 Other AD structures, local name, manufacturer data

- Flags `02 01 06` [verified for BTHome in general, bthome.io; inferred for this device].
- Scan response carries Allterco manufacturer data (company id 0x0BA9) with typed blocks:
  `0x01` flags (2 bytes), `0x0A` MAC (6 bytes), `0x0B` model id (2 bytes).
  [verified] https://shelly-api-docs.shelly.cloud/docs-ble/common "Manufacturer data in scan response".
  Example from the non-ZB RC Button 4 in the Theengs decoder tests:
  `a90b 01 0100 0b 0700 0a ffee718f3938` = company 0x0BA9, flags 0x0001, model 0x0007,
  MAC 38:39:8F:71:EE:FF. [verified] `theengs/decoder` `tests/BLE/test_ble.cpp` line 1177.
- Local name: the ZB unit was reported with `local_name` = `SBBT-104CUS` by a Shelly gateway
  running an **active** scan (ioBroker gateway script v1.2: `type: result.local_name`,
  `active: true`). [verified as third-party report]. The non-ZB RC Button 4 advertises a shortened
  name with MAC digits, e.g. `SBBT-USEEFF` ([verified] Theengs test vector; BLU changelog 1.0.19/1.0.20:
  "Short device name from advertising includes 4 digits of mac address").
  Whether the ZB name sits in the advertisement or only in the scan response is [unknown]; with
  20 bytes already used (flags 3 + service data 17) a full 11-character name cannot fit in the
  31-byte legacy advertisement, so the scan response is likely. [inferred]
  Consequence: never filter on the name; filter on service-data UUID and, once known, the MAC.
- PDU type (connectable/scannable ADV_IND or not) is [unknown]. The existence of a scan response
  and of GATT pairing suggests scannable and connectable advertising. [inferred]

### 1.5 Is the MAC address static?

[inferred, strong] Yes, a public IEEE address:

- Address prefixes in public reports of ZB units are `F8:44:77` (BLE MAC in the ioBroker issue,
  Zigbee EUI-64 `0xf84477fffe04a1d3` in https://github.com/Koenkk/zigbee2mqtt/issues/30941) and
  `28:68:47` (EUI-64 `0x286847fffeeff1a3` in zigbee2mqtt issue 30640). Both are registered to
  Silicon Laboratories in the IEEE registry ([verified] https://standards-oui.ieee.org/oui/oui.csv:
  `MA-L,F84477,Silicon Laboratories`, `MA-L,286847,Silicon Laboratories`).
- BTHome encryption uses the MAC inside the nonce, the scan response repeats the MAC, and Shelly
  gateways and Home Assistant key devices by MAC. A rotating address would break all three.

Not confirmed by capture over a battery change. Device test in section 9.

---

## 2. Timing

### 2.1 What the vendor documents

| Fact | Value | Status and source |
|---|---|---|
| Advertising per button event (ZB) | one burst, "Single, with duration 1500ms" | [verified] ZB API page. The same notation is used on sibling pages (Motion ZB "Single, with duration 1000ms", H&T ZB "3000ms"), so it means a one-shot burst of that length. |
| Periodic beacon, normal mode (ZB) | every 15 min (battery, device id, firmware) | [verified] ZB API page; vendor code `reportCadenceSec: 900` |
| Periodic beacon, beacon mode (ZB) | 12 s per API page; "emits beacons every 8 seconds" per printed user guide (PDF dated 2026-10-13) | [verified, sources disagree] API page vs `Shelly BLU RC Button 4 ZB multilingual printed user and safety guide.pdf` linked from the KB page |
| Beacon mode default | off (`0x00`), GATT characteristic `cb9e957e-952d-4761-a7e1-4416494a5bfa`, write needs a bonded peer; user guide: "enable from the Shelly Smart Control app" | [verified] ZB API page, common page, user guide |
| Advertising interval inside the burst (ZB) | not documented | [unknown] |
| Advertising interval and burst, older non-ZB BLU firmware line | "[1.0.7] Change advertising period and duration to 50mS/2Sec", "[1.0.9] Change advertising timing to 20mS/1Sec", "[1.0.9] Change beacon packets timing: 4 pulses, 150mSec delay" | [verified] https://shelly-api-docs.shelly.cloud/docs-ble/changelog . This is a different firmware line (BLU, not BLU+ZB on EFR32MG27); applying 20 ms to the ZB model is [inferred]. |
| Delay before the burst (single versus multi click) | not documented for ZB | [unknown] |
| Multi-click window, non-ZB RC Button 4 | GATT "Multiclick speed" `35c8a5e2-1cac-4b03-8386-eb6846a1776e`, "(0-5) [800, 550, 300, 150, 90, 55] miliseconds delay"; default not stated | [verified] non-ZB page. The ZB page lists only four characteristics (Find my device, Beacon mode, Factory reset, BTHome sample), so configurability on the ZB model is [unknown]. |
| Hold | non-ZB: "[1.0.23] Wall US (RC Button4) Inrease max beacon time to 60 seconds. Send button hold after multiclick delay time" | [verified] BLU changelog; ZB behaviour [unknown] |
| Device-level LED feedback | green flashes, count equals number of presses; yellow on long press | [verified] KB page. Shows the firmware classifies the gesture before signalling, so a decision delay exists. |
| BLE pairing mode | 4 presses or two buttons held 10 s; lasts 1 min | [verified] ZB API page. So "4x press" is reserved and cannot be an app gesture. |
| Zigbee steering | 5 presses; lasts 1 min | [verified] ZB API page. "5x press" is reserved too. |

Third-party observation for the single-button BLU Button1: "repete o mesmo pacote cerca de vinte
vezes ao longo de um ou dois segundos" (repeats the same packet about twenty times over one or two
seconds). [verified as the author's statement] `altinojoao/bthome-bridge` wiki `Como-funciona.md`.
Twenty receptions over 1 to 2 s is what an Android scanner sees, not necessarily what is sent.

No sniffer measurement of the ZB model was found in vendor docs, Home Assistant, ESPHome, Theengs,
zigbee2mqtt, openHAB or ioBroker threads. [verified absence in the sources searched]

### 2.2 Can a duty-cycled screen-off scan catch a single press?

Android scan duty cycles, AOSP `android-17.0.0_r1`
(`packages/modules/Bluetooth/android/app/src/com/android/bluetooth/le_scan/ScanUtil.kt`,
https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/tags/android-17.0.0_r1/android/app/src/com/android/bluetooth/le_scan/ScanUtil.kt ) [verified]:

```
SCAN_MODE_LOW_POWER_WINDOW_MS = 140      SCAN_MODE_LOW_POWER_INTERVAL_MS = 1400
SCAN_MODE_BALANCED_WINDOW_MS = 183       SCAN_MODE_BALANCED_INTERVAL_MS = 730
SCAN_MODE_LOW_LATENCY_WINDOW_MS = 100    SCAN_MODE_LOW_LATENCY_INTERVAL_MS = 100
SCAN_MODE_SCREEN_OFF_LOW_POWER_WINDOW = 512 ms    ..._INTERVAL = 10240 ms
SCAN_MODE_SCREEN_OFF_BALANCED_WINDOW = 183 ms     ..._INTERVAL = 730 ms
DEFAULT_SCAN_TIMEOUT = 10 minutes        DEFAULT_SCAN_QUOTA_COUNT = 5 per 30 s window
DEFAULT_SCAN_ALLOWANCE_SECONDS = 6 minutes (property bluetooth.ble.scan.scan_allowance_seconds.config)
```

Geometric catch probability for one burst of length D with advertising interval a, scan window W,
scan interval T: P = min(1, (D + W - a) / T). [inferred, simple model: any advertising event that
falls inside a scan window is received; real radios lose some to collisions and coexistence]

| Scan mode (W / T) | D = 1.5 s (ZB doc) | D = 1.0 s (older BLU firmware) |
|---|---|---|
| LOW_LATENCY 100 / 100 ms | 100 % | 100 % |
| BALANCED and SCREEN_OFF_BALANCED 183 / 730 ms | 100 % (guaranteed for D > 0.57 s) | 100 % |
| LOW_POWER 140 / 1400 ms | 100 % (guaranteed for D > 1.28 s) | about 80 % |
| SCREEN_OFF 512 / 10240 ms | about 19 % | about 15 % |

So the question is not "can a duty-cycled scan catch it" but "which duty cycle does Android leave
us with after hours of screen-off":

- [verified] An unfiltered scan is suspended when the screen is off:
  `requiresScreenOn(client) = !isOpportunisticScanClient(client) && !client.hasNonEmptyFilters`,
  log text "Cannot start unfiltered scan in screen-off." (`ScanManager.java`, `ScanUtil.kt`).
  bthome-bridge states the same from experience (comment in `GestorScan.kt`).
- [verified] With the classic logic, an app whose uid importance is at most
  `IMPORTANCE_FOREGROUND_SERVICE` keeps LOW_LATENCY when the screen goes off
  (`ScanThrottler.throttleScanMode`: `SCAN_MODE_LOW_LATENCY -> SCAN_MODE_LOW_LATENCY`,
  `LOW_POWER -> SCREEN_OFF`, `BALANCED -> SCREEN_OFF_BALANCED`). An app that is *not* in the
  foreground-service band is limited to `SCAN_MODE_SCREEN_OFF`.
- [verified] A regular (callback or ALL_MATCHES) scan that runs longer than the scan timeout
  (10 min in AOSP 17) is force-downgraded to LOW_POWER when filtered, or made opportunistic when
  unfiltered (`handleRegularScanTimeout`, `SCAN_MODE_FORCE_DOWNGRADED = SCAN_MODE_LOW_POWER`).
  With the screen off a force-downgraded client is then limited to `SCAN_MODE_SCREEN_OFF`
  (`throttleScanMode`: `isForceDowngradedScanClient` branch). Scans with
  `CALLBACK_TYPE_FIRST_MATCH` are exempt from the timeout (`isExemptFromScanTimeout`).
  This is the reason SaraButton restarts its scans every 4 min and bthome-bridge every 20 min.
- [verified in source, activation unknown] AOSP 17 adds flag `scan_allowance_throttling_enabled`
  ("The flag enables scan throttling using allowance", `flags/le_scanning.aconfig`). When on, the
  timeout logic is skipped and every app that has neither a privileged permission nor a visible
  UI (`isAppForegroundUi`, importance at most `IMPORTANCE_FOREGROUND`; a foreground service is
  not enough) gets an hourly allowance: 6 min of LOW_LATENCY, or 24 min of BALANCED (weight 25),
  after which the client is set to `SCAN_MODE_SCREEN_OFF` until the hourly refill
  (`ScanThrottler.applyAllowanceThrottling`, `ALLOWANCE_REFILL_WINDOW = 1.hours`).
  Whether this flag is enabled on the Pixel 10 Pro build is [unknown]. If it is, a screen-off app
  would sit at 512 ms / 10.24 s most of each hour and miss about four of five single presses.
- [verified] Android 16 QPR2 (API 36.1) added `ScanSettings.Builder.setScanType(SCAN_TYPE_PASSIVE)`
  ("scan results are delivered immediately upon receiving an advertising report, without waiting
  for scan responses"), https://developer.android.com/reference/android/bluetooth/le/ScanSettings.Builder .
  We only need the service data, so passive scanning is sufficient, avoids a SCAN_REQ/SCAN_RSP
  exchange per advertisement and saves the coin cell.
- [verified] In AOSP 17 the reassembler reports a scannable legacy advertisement immediately and
  again after the scan response (`system/gd/hci/le_scanning_reassembler.cc`: "Returns current
  result and save it for next scan response"). With an active scan, expect up to two results per
  advertising event; de-duplicate on packet id, not on the whole scan record.

Further timing risks:

- [inferred] During a spoken menu the phone streams audio to the hearing aid. The Bluetooth
  controller time-shares one radio between that link and LE scanning, so real scan windows are
  shorter than nominal exactly when menu navigation presses arrive. Must be measured.
- [verified as third-party reports] With BlueZ **active** scanning, Home Assistant users saw only
  "every fifteenth or twentieth button press" of the (non-ZB) RC Button 4 and Wall Switch 4;
  switching the adapter to passive scanning fixed it
  (https://community.home-assistant.io/t/shelly-blu-rc-button-4-connection-problems/781849, posts 1 and 8).
  The cause is in the receiver stack, but it is a warning that scan type matters for these
  short-burst devices.
- [inferred] End-to-end latency = firmware gesture decision delay (unknown, plausibly a few
  hundred ms) + wait for the next scan window (0 for LOW_LATENCY, up to 547 ms for BALANCED,
  up to 1.26 s for LOW_POWER) + Android delivery (a PendingIntent broadcast, plus process start
  if the process is dead).

---

## 3. Encryption

### 3.1 How BTHome v2 encryption works

[verified] https://bthome.io/encryption/ :

- AES-128 in CCM mode with a pre-shared 16-byte key ("bindkey"), 4-byte MIC (`mac_len=4`).
- Nonce (13 bytes) = MAC address (6) + UUID `D2 FC` (2) + device-info byte (1) + counter (4, little endian).
- Service data on air = `D2 FC` + device-info byte (bit 0 set) + ciphertext + counter (4) + MIC (4).
- No associated data in v2 ("In BTHome V2, this header is not used anymore").
- Worked example: key `231d39c1d7cc1ab1aee224cd096db932`, MAC `5448E68F80A5`, plaintext `02CA0903BF13`,
  counter `33221100` gives service data `d2fc41e445f3c9962b332211006c7c4519`. Use this as the unit-test vector.
- The spec itself warns: "this is not an automatic safety guarantee. The full safety guarantees of
  BTHome encryption can only be reached if the receiver implements its required checks ... Hackers
  can always send non-encrypted (or modified encrypted) data with your (copied) MAC address ...
  The receiver must also check the message counter inside the encrypted packet (must increase
  compared against last time). Otherwise, hackers can trivially record your valid message, and
  repeat it later".

Size for this remote: 1 + 12 (ciphertext of `00 pid 01 batt 3A x 3A x 3A x 3A x`) + 4 + 4 = 21 bytes
of service data, 25 bytes as an AD structure, 28 with flags. Fits a legacy advertisement. [inferred]

### 3.2 How Shelly enables it

[verified] https://shelly-api-docs.shelly.cloud/docs-ble/encryption and /docs-ble/common :

- "The encryption key is auto generated using a true hardware random generator. The pin code given
  by the user during encryption is used for a higher level of security pairing."
- "Encryption is activated by writting a 6 digit code to the Passkey charackteristic. Decryption is
  only possible with factory reset." "After device is encrypted, further connection to it will
  require the pin code."
- Shelly service `de8a5aac-a99b-c315-0c80-60d4cbb51225`:
  Passkey `0ffb7104-860c-49ae-8989-1f946d5f6c03` (4 bytes, little endian, 0-999999, write bonded);
  Encryption key `eb0fb41b-af4b-4724-a6f9-974f55aba81a` (16 bytes, "AES CCM encryption key (randomly
  generated on every change of the passkey)", read);
  Unix time `d56a3410-115e-41d1-945b-3a7f189966a1` (4 bytes, read / write bonded);
  Factory reset `b0a7e40f-2b87-49db-801c-eb3686a24bdb`;
  Sample BTHome data `d52246df-98ac-4d21-be1b-70d5f66a5ddb` ("Unencrypted BTHome data from the device", read).
- ZB firmware BLU_FW 1.2.12: "Encryption key BLE characteristic now requires encrypted connection
  instead of bonding" (changelog_mg27).
- Pairing: "The devices can be connected, controlled and modified only from a paired peer ... The
  maximum number of paried devices is 4."
- Tools: the vendor names two apps, "Shelly BLE Debug" (Google Play id `cloud.shelly.bledebug`,
  "an engineering app for testing the communication with the Shelly BLE Devices, a.k.a. BTHome
  implementations", last updated 2026-03-11) and Shelly Smart Control (cloud account; the user
  guide says it needs "at least one permanently powered Shelly device ... acting as a Bluetooth
  gateway"). Community reports say the Debug app can enable encryption and show the key; whether
  that app needs a login is [unknown].
- Because the procedure is plain GATT (enter pairing mode with 4 presses, bond, write passkey, read
  key), our own app could do it without any Shelly software. [inferred from the documented
  characteristics; not tested]

### 3.3 What replay protection it really gives

- Confidentiality and integrity against anyone without the key: yes, if we verify the MIC. [verified by spec]
- Replay protection: only what the receiver implements. Reference behaviour:
  - Home Assistant `bthome-ble` rejects a lower counter only when `new < last and bindkey_verified
    and new >= 100`; counters below 100 are always accepted to tolerate restarts
    (`src/bthome_ble/parser.py`, `_check_encryption_counter`). It also ignores plaintext once a
    bindkey is configured ("Received plaintext adv ... while bindkey is known, ignoring!",
    `_set_downgrade_detected`). [verified]
  - Shelly's own gateway firmware only got "BTHome: Implement encrypted advertisement replay attack
    protection" in Gen2+ firmware 2.0.0 (2026-07-13). [verified] https://shelly-api-docs.shelly.cloud/gen2/changelog
- Counter semantics on Shelly BLU devices are not documented. [unknown]
  One published encrypted BLU Button1 packet has counter bytes `b8 f0 93 64` = 0x6493F0B8 =
  1687417016 = 2023-06-22 06:56:56 UTC, five days before the test vector was committed
  (`theengs/decoder` `tests/BLE/test_ble.cpp` line 1176, commit history 2023-06-27). The device
  also exposes a writable "Unix time" characteristic. So the counter is very likely derived from
  the device clock. [inferred from one sample]
  Consequences if true: the counter is monotonic while powered, the same counter may cover all
  repeats of one burst, and it may jump backwards after a battery change until the clock is set
  again. A strict "must increase" rule then locks the remote out after a battery swap, and a
  lenient rule re-opens a replay window. Needs a device test before we choose a policy.
- Within the burst window an attacker can always relay a packet live (range extension). Counter
  checks do not stop that. [inferred]

### 3.4 What an unencrypted setup exposes

- Anyone within radio range learns the static MAC, battery level and every press with button and
  gesture, and can track the remote by its MAC and its 15-minute beacon. [inferred from the format]
- Anyone can transmit `D2 FC 44 00 <new pid> 01 64 3A 01 ...` with the remote's MAC as advertiser
  address (any nRF or ESP32 board can set its address) and our app cannot distinguish it from the
  real remote. A MAC filter is identification, not authentication. [inferred; stated in general by
  bthome.io]
- An Android phone cannot easily be the attacker, because apps cannot choose their advertising
  address ([verified] `falkorichter/open-bthome-button-app` `docs/feasibility.md`, citing
  `AdvertisingSetParameters.Builder.setOwnAddressType()` being `@SystemApi`), but that is no
  protection against dedicated hardware.

---

## 4. The ZB model: modes and first-time setup

- [verified] BLE advertising is the base behaviour: "The device transmits BLE advertising packets
  when one or more buttons are pressed (1 or more times). Issues ZigBee On/Off/Toggle, Level Step
  and Recall Scene commands on 4 endpoints." (ZB API page). There is no documented "mode switch"
  between Bluetooth and Zigbee. The "modes" on that page (On/Off mode, Toggle mode) are Zigbee
  endpoint behaviours.
- [verified] Zigbee is joined by pressing any button 5 times (1 min steering); BLE pairing by
  pressing 4 times or holding two buttons 10 s. ZB firmware 1.2.12: "Wall US Zigbee can now be
  enabled or disabled via BLE setting" and "5-click now also enables Zigbee if it was previously
  disabled". So Zigbee is the optional part.
- [verified as third-party reports] A unit joined to Zigbee still sends BTHome at the same time:
  "only one event occurs in the Zigbee device ... and a separate button press triggers in BTHome";
  "I paired it with HA via BTHome and now the 4-buttons are working perfectly ... single press,
  double press, triple press, and hold press" (HA forum thread 978579, January/February 2026).
- [verified] Out of the box: "The Device comes ready to use with the battery installed" (remove the
  insulating tab); "The Device works with any home automation platform that supports the BTHome
  protocol" (printed user guide). Shelly Smart Control is only needed for the Shelly cloud, for
  toggling beacon mode from the vendor UI and for firmware updates through the account.
- [verified] Firmware updates: "Firmware update is acomplished with the help of Shelly BLE Debug
  mobile application ... or from Shelly Smart Control App ... if the device is registered in the
  user's account" (common page).
- [verified] Factory reset: hold any button 30 s within 3 min after inserting the battery (ZB API
  page). It "erases all pairing keys" and is the only way to turn encryption off.
- [unknown] Whether an un-joined unit spends energy or time on Zigbee activity at each press, and
  whether disabling Zigbee through the BLE setting changes BLE timing.
- [inferred] Home Assistant's parser has no name mapping for `SBBT-104CUS` (it knows SBBT-002C,
  -102C, -004CEU, -004CUS ...), so there the device shows up as a generic "BTHome sensor". Not a
  problem for us; we identify by device id 0x16 and MAC.

---

## 5. OferTiber/SaraButton, read in depth

Clone: `.../scratchpad/repos/SaraButton`, commit `48975ae` (2026-08-07), version 2.4.2.
Licence MIT ("Copyright (c) 2026 Sara Button contributors"). Java, no Kotlin, no third-party
dependencies, `minSdk 23`, `targetSdk 35`. [verified]

Project health [verified via GitHub API]: created 2026-07-22, last push 2026-08-07, 0 stars,
0 issues ever, 11 pull requests all by the single author from `codex/...` branches. There is no
community evidence about screen-off reliability, only the author's README.

### 5.1 Supported models

`SBBT-004CUS` (id 0x0007, 4 buttons), `SBBT-104CUS` (id 0x0016, 4 buttons), `SBBT-102C` Button
Tough 1 ZB (id 0x0017, 1 button). One remote per type at a time. Identification order: BTHome
device id 0xF0, then name prefix, then number of 0x3A objects (`ShellyButtonDevice.java`). [verified]

### 5.2 Background scanning

Two scans run in parallel on API 26+ (`MonitoringService.java`, `BleScanManager.java`) [verified]:

| | Callback scan | PendingIntent scan |
|---|---|---|
| Started from | foreground service | foreground service, and as fallback from `BootReceiver` if the service cannot start |
| Scan mode | `SCAN_MODE_LOW_LATENCY` | `SCAN_MODE_LOW_LATENCY` |
| Callback type | `CALLBACK_TYPE_ALL_MATCHES` | `CALLBACK_TYPE_FIRST_MATCH or CALLBACK_TYPE_MATCH_LOST` |
| Match mode | `MATCH_MODE_AGGRESSIVE`, `MATCH_NUM_MAX_ADVERTISEMENT`, report delay 0 | same |
| Filters | `ScanFilter.setDeviceAddress(mac)` per configured remote; if none configured `setServiceData(0xFCD2, byte[0])` | same list |
| Delivery | `ScanCallback` inside the service | explicit broadcast to `BleScanReceiver` (`FLAG_UPDATE_CURRENT or FLAG_MUTABLE`), handled with `goAsync()` and a 2 s delayed `finish()` |

Source comments: "On Android 8.0 and newer, keep both paths active. The PendingIntent scan can wake
the app if a vendor pauses callbacks with the screen off. Packet fingerprinting prevents the same
advertisement from calling twice." and "Address filters handle vendor differences in how BTHome
service data is exposed and let both configured remotes wake the app."

Other mechanisms [verified]:

- Foreground service declared `connectedDevice|location`, started with
  `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` on API 31+, `START_STICKY`, `stopWithTask="false"`,
  `directBootAware="true"`.
- Scan refresh: both scans are stopped and restarted every 4 minutes
  (`SCAN_REFRESH_MILLIS = 4 * 60 * 1000`; comment: "Some Android Bluetooth stacks reduce
  long-running low-latency scans after about five minutes"). AOSP's actual timeout is 10 min in
  Android 17 (section 2.2). The refresh is driven by `Handler.postDelayed`.
- Wake lock: `PARTIAL_WAKE_LOCK` "SaraButton:Monitoring", acquired with a 5 min timeout and renewed
  at every refresh, i.e. **held permanently while monitoring**. The CPU never suspends.
- Retry after `onScanFailed` or a missing scanner: 10 s.
- `BluetoothAdapter.ACTION_STATE_CHANGED` receiver re-arms both scans on `STATE_ON`.
- Boot: `BootReceiver` for `LOCKED_BOOT_COMPLETED`, `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`;
  settings live in device-protected storage so monitoring works before the first unlock.
- Battery: onboarding step checks `PowerManager.isIgnoringBatteryOptimizations` and fires
  `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; a second step checks
  `PackageManager.isAutoRevokeWhitelisted()` (unused-app hibernation) and opens app details.
- Permissions: `BLUETOOTH_SCAN` with `neverForLocation`, `BLUETOOTH_CONNECT`,
  `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`,
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `POST_NOTIFICATIONS`; no `INTERNET`.

### 5.3 Parser and de-duplication

`BthomeParser.java` (278 lines) [verified]:

- Reads the device-info byte, requires version 2, returns "encrypted" without parsing if bit 0 is set.
- Knows exactly six objects: 0x00 (1 byte), 0x01 (1), 0x3A (1), 0xF0 (2), 0xF1 (4), 0xF2 (3).
  Any other object id stops parsing and marks the packet incomplete ("Stop instead of guessing a
  size and accidentally treating sensor data as a press").
- Button index = running count of 0x3A objects (positional, as the spec requires).
- Event constants 0x00-0x06, 0x80 and legacy 0xFE.
- Unit tests use synthetic packets with device info 0x40; none is a real capture.

`ButtonEventProcessor.java` [verified]:

- Accepts results only from the stored MAC(s).
- Fingerprint = `MAC + ":" + Arrays.hashCode(serviceData)`, stored in `SharedPreferences` per
  remote type and button; a result is dropped if it equals the last stored fingerprint, with **no
  time window**. Comment: "Keep ignoring that exact packet until a new packet ID/data hash arrives,
  even if the advertising burst lasts longer than a time window."
- A 10 s "call guard" after each trigger (domain specific).
- Encrypted packets only set a status text telling the user to factory-reset the remote.

### 5.4 What README and issues say about screen-off reliability

Only the README speaks [verified]: "Background execution policies still vary between manufacturers.
The app uses an ongoing `connectedDevice` foreground service, refreshes long-running scans, holds a
bounded and renewable partial wake lock, and registers boot, app-update, and Bluetooth-state
recovery. Users may still need to allow unrestricted battery use"; "BLE delivery, battery policy ...
and background process limits vary across Android implementations. Always perform end-to-end tests
on every intended device and Android version"; "Do not force-stop the app"; "not a medical device
or guaranteed emergency system". No measurements, no device list, no issue reports.

### 5.5 Port or not

Worth porting to Kotlin (ideas, the code is small enough to rewrite):

1. The two-path design: a callback scan owned by the foreground service plus a PendingIntent scan
   as the process-wake path, both with a non-empty `ScanFilter`.
2. MAC filter once the remote is enrolled, service-data filter only during enrolment.
3. Periodic scan restart below the platform timeout, and re-arming on Bluetooth `STATE_ON`,
   boot, locked boot and package replace.
4. `directBootAware` components plus device-protected storage for the remote's MAC and key, so the
   path works after a reboot while the phone is still locked.
5. Onboarding checks for battery-optimisation exemption and unused-app hibernation.
6. Strict "stop at unknown object" parsing and identification by device id 0xF0.
7. `goAsync()` in the scan receiver.

Not worth porting, or to be done differently:

1. The permanent partial wake lock. It defeats Doze-era power saving and should not be needed when
   the controller filters and wakes the host. Start without it and measure; add a short wake lock
   only around handling an event.
2. `Handler.postDelayed` as the refresh clock (only works because of that wake lock). Use the
   PendingIntent scan's own exemption from the timeout, the remote's 15-minute beacon as a
   liveness signal, and an `AlarmManager` allow-while-idle alarm as backstop.
3. The fingerprint de-duplication without expiry. A packet that is byte-identical to the previous
   one (packet id wrap after 256 events with no intermediate reception, counter restart after a
   battery change) is dropped forever. Use (MAC, packet id) with an expiry a little longer than the
   burst (Home Assistant uses 4 s) and monotonic time.
4. `FIRST_MATCH or MATCH_LOST` as the only wake path. On-found/on-lost tracking has its own
   timeouts (`ONLOST_ONFOUND_BASE_TIMEOUT_MS = 500` and factors in AOSP) and may merge two quick
   presses into one "found". Menu navigation needs every event, so the wake path should be
   `ALL_MATCHES` (as bthome-bridge does) or be backed by the callback scan.
5. Six-object parser table. Use the full BTHome object table so that known objects are skipped by
   length rather than aborting.
6. API 23-30 compatibility branches, location service type, Java.
7. No encryption support.

---

## 5b. Second reference found: altinojoao/bthome-bridge (Kotlin, MIT)

Clone: `.../scratchpad/repos/bthome-bridge` (commit `29ed7a5`, 2026-10-13) and its wiki
`.../scratchpad/repos/bthome-bridge.wiki`. "Android BTHome-to-HTTP bridge that runs with the screen
off - no Shelly BLU dongle required." Created 2026-08-19, 2 stars, 0 issues. `LICENSE` is MIT
("Copyright (c) 2026 Altino Natalino"); the README footer says "Todos os direitos reservados",
which contradicts it. [verified] Treat the licence as unclear until the author clarifies; reuse
ideas, ask before copying code.

Same use case as ours (a Shelly BLU button carried in the pocket, phone as receiver, screen off).
Recipe (`ble/GestorScan.kt`, `ble/ScanReceiver.kt`, `ble/ProcessadorClique.kt`) [verified]:

- **Only** a PendingIntent scan, `SCAN_MODE_LOW_LATENCY`, `CALLBACK_TYPE_ALL_MATCHES`, no report
  delay, filter `setServiceData(0xFCD2, byteArrayOf(), byteArrayOf())`.
- Comments record why: an unfiltered scan "is automatically stopped by the system when the screen
  turns off"; LOW_POWER "listens in short cycles (~0.5 s) with long radio-off pauses (several
  seconds) ... which explains variable delays of several seconds"; report delay batches results.
- "Android converts long-running BLE scans (about 30 min) into opportunistic scans ... some stray
  advertisements keep arriving, so the classic watchdog (60 s without ANY advertisement) never
  fires, and clicks are lost until the app is restarted." Countermeasures: preventive restart every
  20 min, restart when nothing at all was received for 60 s, restart when a known beacon has been
  silent for 120 s, and an `AlarmManager.setExactAndAllowWhileIdle` backstop "because the main
  thread Handler does not fire reliably when the process is suspended with the screen locked".
- De-duplication by packet id persisted in `SharedPreferences` per MAC, because "Android can kill
  the process between button presses".
- Foreground service `connectedDevice|location`, `START_STICKY`, boot receiver,
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- `ble/BTHomeCripto.kt`: BTHome v2 AES-CCM decryption written on top of `AES/ECB/NoPadding`
  following RFC 3610, checked against the bthome.io vector. Its comment that Android's `Cipher`
  list has no CCM matches the Android reference ([verified]
  https://developer.android.com/reference/javax/crypto/Cipher lists AES with CBC, CFB, CTR, CTS,
  ECB, OFB, GCM, GCM-SIV only). It does **not** check the counter.
- Wiki FAQ "A app perde cliques com o ecra apagado" attributes lost clicks to vendor battery
  management (Samsung, Xiaomi, OnePlus) and tells users to set the app to unrestricted. No Pixel
  data, no numbers.

---

## 6. 50ButtonsEach/flic2lib-android

Clone: `.../scratchpad/repos/flic2lib-android`, commit `d18733a` (2026-09-22), library version
2.1.1, `compileSdk 36`, 21 stars. [verified]

**Licence.** `License.txt`, "Copyright (c) 2019 Shortcut Labs AB": "Permission is hereby granted to
use, copy, modify, and distribute this software, or portions hereof, for any purpose, without fee,
subject to the following restrictions: 1. The origin of this software must not be misrepresented
... 2. Altered source versions must be plainly marked as such ... 3. This Copyright notice may not
be removed or altered from any source or altered source distribution." This is the wording of the
libpng/zlib family of permissive licences. GitHub reports it as "Other / NOASSERTION" because it
is not a recognised SPDX text. [verified]
Assessment (engineering reading, not legal advice): it can be consumed from a public Apache-2.0
repository, either as the JitPack dependency `com.github.50ButtonsEach:flic2lib-android:2.+` or
vendored, provided the notice stays with the source, modified files are marked, and our NOTICE
file names the origin. There is no copyleft, no field-of-use limit and no patent clause. The
licence covers the SDK only; the buttons' firmware and Flic's back end are proprietary. [inferred]
Two practical caveats [verified]: the library manifest adds `android.permission.INTERNET`, and
`Utils.firmwareCheck` posts button uuid, firmware version, the host app's package name and version
and the library version to `https://api.flic.io/api/v1/buttons/versions/firmware3` on a per-button
schedule (`nextFirmwareCheckTimestamp`).

**Background delivery.** Flic does not scan in the background. Scanning
(`ScanFilter.setServiceUuid`, `SCAN_MODE_LOW_LATENCY`) is used only to pair a new button. A paired
button is reached with `BluetoothDevice.connectGatt(context, true /* autoConnect */, cb,
TRANSPORT_LE)` (`Flic2Manager.java` line 603), i.e. a pending background connection that the
Bluetooth stack completes "whenever the button starts advertising"; the button stores presses made
while disconnected and delivers them, flagged `wasQueued`, after reconnecting (README; maintainer
in issue 7). The README's recipe to stay alive is a plain foreground service ("the service class
itself doesn't need to do anything. As long as it is running it will keep the rest of the process
... alive") plus `BOOT_COMPLETED` and `PACKAGE_REPLACED` receivers that merely start the process.
The sample declares no foreground-service type; issue 34 (open since 2024) reports Google Play
rejecting `connectedDevice` for this and the maintainer suggests adding a CompanionDeviceManager
association for the already-paired button. So Flic's model is "bonded GATT link with queued
events", which avoids the scan duty-cycle problem entirely at the cost of a proprietary protocol
and a maintained connection.

Side note [verified]: SDK 2.1.1 has `setHidMidiConfig(byte[] config, ...)` and `getHidMidiConfig`
("The button must have firmware version at least version 9"). The blob format is not documented
in the repository.

---

## 7. Other BTHome v2 parser implementations

| Project | Language | Licence | What it is | Use for us |
|---|---|---|---|---|
| https://github.com/Bluetooth-Devices/bthome-ble | Python | MIT | The parser behind Home Assistant's BTHome integration; 97 object definitions in `const.py`, events in `event.py`, v2 decryption, counter check, plaintext-downgrade guard, packet-id filter with 4 s window; pushed 2026-11-26 | Format reference of record. Port the object table and the three guards. Its Shelly tests are synthetic. |
| https://bthome.io/format/ and /encryption/ | spec | open | Object table, device-info bits, encryption example | Normative source and test vector |
| https://github.com/koenvervloesem/BTHome-Kaitai-Struct | Kaitai Struct (+Python) | MIT | Declarative description of the format; Kaitai can generate Java | Alternative to a hand-written table; last push 2024-06, check coverage of 0xF0-0xF2 and 0x3B |
| https://github.com/seime/openhab-bthome | Java | none stated | openHAB binding built on the Kaitai description, Bouncy Castle `CCMBlockCipher`, packet-id check | Read for structure; no licence, do not copy |
| https://github.com/ALLTERCO/shelly-script-examples (`ble/ble-shelly-blu.shelly.js`) | JavaScript | Apache-2.0 | Vendor's reference decoder for BLU devices; de-duplicates on `pid`; does not handle encryption | Vendor-blessed behaviour; same licence as ours |
| https://github.com/ALLTERCO/fleet-management (`backend/src/config/BTHomeData.ts`) | TypeScript | Apache-2.0 | Vendor table of BLU models (model strings, numeric ids, report cadence) | Source for model ids including 0x15, 0x16, 0x17 |
| https://github.com/theengs/decoder (`src/devices/SBBT_004CEU_US_json.h`, `tests/BLE/test_ble.cpp`) | C++/JSON | GPL-3.0 | Decoder definitions and raw test vectors for Shelly BLU buttons incl. encrypted sample and manufacturer data | Test vectors as facts only; do not copy code (GPL) |
| https://github.com/altinojoao/bthome-bridge | Kotlin | MIT file, README says all rights reserved | Minimal v2 parser (objects 0x00, 0x01, 0x3A), AES-CCM on AES/ECB, PendingIntent scanning | Closest to our use case; clarify licence first |
| https://github.com/wb8wka/BTHOME2_decoder | Kotlin | none | Scanner app with a broad object table and `AesCcm.kt` on AES/ECB | Read only |
| https://github.com/steanlab/BTHome-Logger | Kotlin | MIT | Foreground logger, unfiltered LOW_LATENCY scan, single activity | Small MIT Kotlin example; no background design |
| https://github.com/falkorichter/open-bthome-button-app | Kotlin | none | Phone acts as a BTHome button (1.5 s burst at 100 ms interval per press) and as receiver; notes on Android address rotation | Ready-made **test transmitter** on a second phone before the hardware arrives; address rotates, so MAC filtering cannot be tested with it |
| https://github.com/OferTiber/SaraButton | Java | MIT | Section 5 | Checklist and MIT-licensed snippets |

A BTHome v2 parser for our needs is about 150 lines of Kotlin plus a table. Writing it ourselves
from the spec, with bthome-ble as oracle and the vectors above as tests, is cheaper and cleaner
than adopting any of the Kotlin/Java projects.

---

## 8. Design implications for "Lara im Ohr"

1. **Input adapter contract.** One `RemoteEvent(sourceMac, button 1..4, gesture, packetId, battery,
   rssi, receivedAtElapsedRealtime, encrypted, counter)` per accepted packet. Gestures: PRESS,
   DOUBLE, TRIPLE, LONG, LONG_DOUBLE, LONG_TRIPLE, HOLD. Keep the raw service data in the debug log.
2. **Parser.** Table driven from the BTHome object list; decide on version bits and encryption bit,
   accept device info 0x40 and 0x44 (0x41/0x45 encrypted); positional 0x3A objects; ignore packets
   whose button slots are all zero (beacons); stop at unknown ids; accept hold as 0x80 or 0xFE.
3. **De-duplication.** Key (MAC, packet id), expiry about 5 s on `SystemClock.elapsedRealtime()`,
   last value persisted so a cold-started process does not replay an echo. Do not hash the whole
   scan record: with active scanning the same advertisement can arrive with and without scan
   response data.
4. **Scanning.** Non-empty filter always (MAC after enrolment). Foreground service of type
   `connectedDevice` holding a LOW_LATENCY callback scan, plus a PendingIntent scan as wake path.
   Prefer `SCAN_TYPE_PASSIVE` (API 36.1+). The foreground service is not optional: without it the
   uid is "background" and AOSP limits the scan to 512 ms / 10.24 s when the screen is off.
5. **Scan health.** Restart scans below the platform timeout; treat the remote's 15-minute beacon
   as a heartbeat ("no beacon for 35 min while Bluetooth is on" means restart and log); expose
   current scan mode from `dumpsys bluetooth_manager` in the debug screen or log.
6. **Gesture vocabulary.** 4x and 5x presses are reserved by the firmware for BLE pairing and Zigbee
   steering; a 30 s hold after battery insertion is a factory reset. Use single presses of the four
   buttons for navigation (fastest and least ambiguous), double/long for secondary actions, and
   never require 4 or more presses. Expect a fixed decision delay before each event.
7. **Security tiering.** Unencrypted mode is fine for "next / previous / select / repeat" in a
   spoken menu. Anything that sends, buys, deletes or opens the microphone on behalf of the user
   should need either the encrypted remote with our own counter and downgrade checks, or a second
   factor (hearing-aid tap, spoken confirmation). Once a key is stored for a MAC, drop plaintext
   from that MAC.
8. **Encryption plumbing.** Implement AES-CCM (13-byte nonce, 4-byte tag, no AAD) on
   `AES/ECB/NoPadding` or bundle Bouncy Castle; store the 16-byte key in device-protected storage
   wrapped by a Keystore key that is usable before first unlock, if the "works while locked after
   reboot" requirement applies to the remote path.
9. **Hardware abstraction.** Keep "BTHome advertisement remote" behind an interface. Flic's model
   (bonded GATT link, queued events, proprietary SDK that phones home) is a different adapter with
   different failure modes and should not shape the core.
10. **Do not join Zigbee.** It is not needed, and a joined unit sends Zigbee commands on every press
    in addition to BLE.

---

## 9. Load-bearing claims and how to test them on the Pixel

| # | Claim | Status | Test |
|---|---|---|---|
| 1 | Factory-fresh unit advertises unencrypted BTHome v2 on each press without any setup | verified in docs and one third-party capture | Pull the battery tab, press a button, watch with nRF Connect or our logger: service data UUID 0xFCD2, first byte 0x44 |
| 2 | Payload is `44 00 pid 01 batt 3A b1 3A b2 3A b3 3A b4` | verified by one capture of firmware 1.2.13 | Log raw service data for every button and gesture (1x, 2x, 3x, long, long 2x, long 3x, hold, two buttons at once); record firmware version from the 0xF1/0xF2 beacon |
| 3 | One burst of 1500 ms per event; interval about 20 ms | duration verified in vendor doc; interval inferred | Screen on, LOW_LATENCY callback scan, log `ScanResult.getTimestampNanos()` of every result for 50 presses: first-to-last spread and median gap. Better: nRF52840 dongle with nRF Sniffer |
| 4 | Delay between physical press and first advertisement is a few hundred ms | unknown | Record audio of the click plus a beep the app plays on first result; or film LED and phone at 240 fps |
| 5 | Packet id increments by one per event and is constant within a burst; beacon packets do not disturb it | inferred from spec and vendor script | Same log as 2; also log the 15-min beacon packets for an hour |
| 6 | MAC is a static public address | inferred (Silicon Labs OUI in reports) | Compare address over days and across a battery removal; check `ScanResult.getDevice().getAddressType()` |
| 7 | With a foreground service and a MAC filter, the scan stays at a duty cycle that catches every burst after hours of screen-off and Doze | unknown, the central risk | Phone locked, screen off, unplugged, lying still: one press every 20 to 40 min for 8 h, then 30 presses in a row; compare the app log with a written press list. Capture `adb shell dumpsys bluetooth_manager` (scan mode, "downgraded", allowance lines) before and after. Repeat with battery optimisation exempt and not exempt |
| 8 | `scan_allowance_throttling_enabled` is off on the Pixel build, or exempts us | unknown | Look for the flag in `adb shell dumpsys bluetooth_manager` and in the device's aconfig flag listing (exact command to be confirmed on the device, e.g. `adb shell aflags list`); `adb logcat -s BtScan.ScanThrottler` shows "Apply scan mode ... with available scan allowance" when the throttle is active (log tag and text verified in `ScanThrottler.kt`) |
| 9 | A PendingIntent scan alone (process dead) catches single presses with the screen off | inferred to fail about 4 of 5 times (background uid is limited to SCREEN_OFF mode) | Kill the process with `adb shell am kill`, keep the registration, press once every few minutes, count deliveries |
| 10 | Presses are received while audio is streaming to the hearing aid | unknown | Play TTS to the Phonak continuously and repeat the 30-press test |
| 11 | Passive scan type delivers the service data and does not reduce the catch rate | inferred | A/B test `SCAN_TYPE_PASSIVE` against active in test 7 |
| 12 | Encryption can be enabled and the key read over GATT without Shelly software | inferred from documented characteristics | 4 presses, bond from nRF Connect, write passkey, read `eb0fb41b-...`; else use Shelly BLE Debug |
| 13 | Encrypted packets use nonce = MAC + D2FC + info + counter, 4-byte MIC; counter behaviour allows a monotonic check | format verified by spec; counter behaviour unknown | Decrypt captured packets with our implementation; log counters over 100 presses, within one burst, after 24 h, and after a battery removal |
| 14 | Unjoined Zigbee does not delay or shorten the BLE burst | unknown | Compare test 3 results before and after disabling Zigbee through the BLE setting, if the setting is reachable |

---

## 10. Corrections and additions to the inherited planning notes

- The notes call SaraButton a good reference with "background and reboot mechanisms". True as far
  as it goes, but it has no users on record (0 stars, 0 issues, single author, about two weeks of
  commit history, AI-assistant branch names) and it holds a permanent wake lock. It proves nothing
  about a Pixel in Doze.
- The notes say SaraButton "supports specifically model SBBT-104CUS". It supports three models:
  SBBT-004CUS, SBBT-104CUS and SBBT-102C.
- The notes treat "BLE pairing and optional encryption" as a detail. Missing: encryption is
  one-way (only a factory reset removes it), the key is generated on the device and must be read
  over a bonded GATT connection, and replay protection is entirely the receiver's job.
- The notes do not mention the documented 1500 ms burst, the 15-minute beacon, the reserved 4x/5x
  press sequences, or that a Zigbee-joined unit keeps sending BTHome.
- The notes list `BluetoothLeScanner.startScan(..., PendingIntent)` as a way to wake the app.
  Correct, but in AOSP 17 a uid without a foreground service is limited to a 512 ms / 10.24 s scan
  when the screen is off, which cannot catch a 1.5 s burst reliably. The PendingIntent path is a
  recovery mechanism, not the primary one.
- The notes say Flic's "manufacturer shows a background foreground service". Correct. Addition:
  Flic's delivery relies on an auto-connecting bonded GATT link with event queueing, the SDK adds
  the INTERNET permission and contacts `api.flic.io` for firmware checks, and its licence is a
  permissive libpng/zlib-style text that GitHub cannot classify.
- Price, availability and purchase status of the remote were not checked in this task.

## 11. Open questions

- Advertising interval and PDU type of the ZB firmware; gesture decision delay; whether a
  multi-click speed setting exists on the ZB model.
- Behaviour while a button is held (repeat rate of 0x80, release event).
- Content of the 15-minute beacon packet and whether it carries a packet id.
- Counter origin and persistence in encrypted mode.
- Whether Shelly BLE Debug works without an account.
- State of `scan_allowance_throttling_enabled` on current Pixel builds.
