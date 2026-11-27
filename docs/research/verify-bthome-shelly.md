# Verification: cluster "bthome-shelly" (Shelly BLU RC Button 4 ZB as BTHome remote)

Verification date: 2026-11-27. Nothing below was measured on the target hardware; no Shelly device and no Pixel were available.
Every quote was fetched fresh today by me (not taken from the earlier report). Evidence copies:
`<research>/verify-bthome/` (pages, issue JSON, third-party files) and
`<research>/verify-scripts/vectors.py` (my own decoder used to check every test vector, incl. AES-CCM via the system `cryptography` 3.4.8 package).

Source versions used:

| Source | Version checked |
|---|---|
| bthome.io | `last-modified: Wed, 25 Nov 2026 13:27:18 GMT`; site repo head `2837ab9aae` (2026-11-25, "add progress (0x67)") |
| Bluetooth-Devices/bthome-ble | commit `14bdb2e164a6ea1a85e84ca3e7a07ef2a0de1ed0` (2026-11-26) = upstream `main`, release v3.27.0; local clone is a depth-1 clone of exactly that commit |
| Shelly API docs | pages fetched 2026-11-27 (footer "Copyright (c) 2026 Allterco Robotics") |
| Shelly KB page | `page-last-modified` 2026-10-13 |
| AOSP Bluetooth module | tag `android-17.0.0_r1` = commit `c77db469de80f86660bc053bec4dee0c5d4b947c` (local clone `repos/aosp-bluetooth-a17`, `git rev-parse android-17.0.0_r1^{commit}` equals HEAD) |
| theengs/decoder | branch `development` at `3c9eb2afb0` (2026-11-26) |
| ALLTERCO/fleet-management | `main`, pushed 2026-10-08, Apache-2.0 |

---

## 1. Verdicts at a glance

| # | Claim (short) | Verdict | One-line reason |
|---|---|---|---|
| 1 | SBBT-104CUS, id 0x16, fresh unit sends unencrypted BTHome v2 on 0xFCD2 per press with no setup | **partly** | Identity confirmed by three vendor sources. "Unencrypted without setup" follows from vendor text and is corroborated first-hand on a sibling of the same firmware line, but is not measured on this model. "On each press" is wrong: one burst per recognised *gesture*. |
| 2 | BTHome v2 layout, object lengths, button codes, ordering, unknown ids | **partly** | Byte layout and codes are right. Missing: event 0x81, Shelly legacy 0xFE, variable-length objects (0x53, 0x54, 0x3B), 2-byte 0x3C, the reference parser's undocumented bit-1 handling, and the exact parser rules (order is not enforced; unknown id stops parsing but keeps what was parsed). |
| 3 | Real capture `44 00 0e 01 64 3a 00 3a 01 3a 00 3a 00` = button 2 single press | **confirmed** | Found in ioBroker.shelly issue 1506; decoding re-computed. Nine further real vectors collected. |
| 4 | One advertisement burst "with duration 1500 ms" per event; beacon every 15 min in normal mode | **confirmed** (as vendor text) | Both strings are on the vendor page. Advertising interval inside the burst and press-to-first-packet delay are undocumented for the ZB model (see details). |
| 5 | 4 presses = BLE pairing, 5 presses = Zigbee joining, so reserved | **partly** | Confirmed, but the list is incomplete: "2 short + hold" starts Zigbee Find & Bind, two buttons held 10 s start BLE pairing, 30 s hold after battery insertion is factory reset, hold during battery insertion is bootloader. |
| 6 | Encryption: GATT, one-way, AES-128-CCM, 4-byte MIC, nonce layout, 4-byte counter | **partly** | Format confirmed and reproduced bit-exact. Correction that matters: on Shelly BLU devices the "counter" is the device clock in seconds, not a per-packet counter. Factory reset (the only way back) can also be written over GATT by a bonded peer. |
| 7 | Static address, MAC ScanFilter stays valid | **partly** | Strong indirect evidence for a public IEEE address (all observed units have Silicon Labs OUIs; vendor gateways key devices by MAC). No vendor sentence and no capture over a battery change. AOSP 17 matches the address regardless of address type. Bootloader/OTA mode uses a different address. |
| 8 | No HID / media-key profile | **confirmed** (documentation level) | Vendor interface description lists BTHome advertising plus the Shelly GATT service only; a GATT enumeration of a sibling on the same firmware line found only the Shelly service and Device Information. No GATT dump of this exact model was found. |

---

## 2. Claim by claim

### Claim 1 - identity and out-of-box behaviour

Evidence for:

- https://shelly-api-docs.shelly.cloud/docs-ble/Devices/BLU_ZB/button4_ZB/ : "Short device name: SBBT-104CUS", "Shelly Bluetooth Device Id: 0x16", "The device transmits BLE advertising packets when one or more buttons are pressed (1 or more times).", section heading "Advertising (BTHome)".
- https://kb.shelly.cloud/knowledge-base/shelly-blu-rc-button-4-zb : "Device model: SBBT-104CUS", "Device Bluetooth ID: 0x0016", MCU "EFR32MG27", "Encryption: Yes".
- Vendor code https://github.com/ALLTERCO/fleet-management/blob/main/backend/src/config/BTHomeData.ts : `'SBBT-104CUS': { productName: 'Shelly BLU RC Button 4 ZB', modelId: 22, isRemote: true, reportCadenceSec: 900 }`.
- Real unit seen by a Shelly gateway (https://github.com/bieniu/ha-shellies-discovery-gen2/pull/753): `"bthomedevice:200" ... "battery":100,"packet_id":21 ... "fw_ver":"v1.1.4" ... "addr":"28:68:47:f0:12:c7" ... "attrs":{"flags":0,"model_id":22}`.
- Printed user guide (PDF linked from the KB page; text copy in `research/ble-shelly/pdf_rc4zb_user_guide.txt`): "The Device comes ready to use with the battery installed.", "The Device works with any home automation platform that supports the BTHome protocol."
- Encryption is opt-in: https://shelly-api-docs.shelly.cloud/docs-ble/encryption/ "Encryption is activated by writting a 6 digit code to the Passkey charackteristic."
- UUID: bthome.io/format "The UUID has to be read reversed per byte, so the UUID is 0xFCD2."; the gateway script that produced the real capture reads `result.service_data['fcd2']` (`research/ble-shelly/iobroker_ble_gateway_script.ts`, line 56).
- First-hand report on a sibling of the same BLU+ZB firmware line (BLU Motion ZB `SBMO-103Z`, firmware `260511/v1.2.20@10419653`, 2026-11-14): https://github.com/hshimomura/home-metrics/blob/main/docs/shelly-blu-motion-zb-investigation.md - advertisement received without pairing ("BLE 広告を読むためのペアリングは不要だった"), unencrypted, service data `44 00 08 01 64 3a 01 64 01`, GATT read shows Beacon mode 0 and **Zigbee 0 (disabled)** on a unit that had never been joined.

Evidence against / limits:

- No vendor sentence says "unencrypted by default"; it is an inference from "encryption is activated by ...". The printed guide even says "The Device uses encrypted communication for enhanced security" (marketing wording).
- The one public capture of this model (claim 3) is from a unit whose setup history is unknown.
- "On each press" is not what the firmware does. Presses are classified first (single / double / triple / long ...), then one event is advertised; 4 and 5 presses switch modes instead (claim 5). Vendor KB: "Green light flashing when a button is pressed shortly. The number of flashes corresponds to the number of the button presses."

Design consequence: plan on unencrypted 0x44 packets from a fresh unit, but make "press one button, see `44 ...` in the log" the very first device test; identify the model by object 0xF0 = 0x0016 from the periodic beacon (it is *not* in button packets) or by manufacturer data block 0x0B, never by name.

### Claim 2 - BTHome v2 layout and parser rules

Confirmed quotes (https://bthome.io/format/):

- Device info: "bit 0: Encryption flag", "bit 1: Reserved for future use", "bit 2: Trigger based device flag ... at an irregular interval (bit 2 = 1), e.g. only when someone pushes a button", "bit 3-4: Reserved for future use", "bit 5-7: BTHome Version ... 2 is the latest version (bit 5-7 = 010)".
- `0x00 packet id uint8 (1 byte)`, example `0009` = 9; `0x01 battery uint8 (1 byte)`, example `0161` = 97 %; `0x3A button`, examples `3A00` None, `3A01` press, `3A02` double_press, `3A03` triple_press, `3A04` long_press, `3A05` long_double_press, `3A06` long_triple_press, `3A80` hold_press, **`3A81` hold_release**.
- "you can also send multiple events of the same type, by just adding them behind each other. The event `0x00` (None) is a special event ... An event 3A003A01 will send no event for the first button, and an event "press" for the second button."
- "Object ids have to be applied in numerical order (from low to high) in your advertisement. ... A BTHome receiver will stop parsing object ids as soon as it finds an object id that isn't supported."
- Packet id: "optional ... BTHome receivers should only process the advertisement if the packet id is different compared to the previous one. The packet id is a value between 0 (0x00) and 255 (0xFF), and should be increased on every change in data."

Reference parser (`src/bthome_ble/parser.py`, `const.py`, `event.py` at `14bdb2e`):

- `BUTTON_EVENTS = {0x00: None, 0x01: "press", 0x02: "double_press", 0x03: "triple_press", 0x04: "long_press", 0x05: "long_double_press", 0x06: "long_triple_press", 0x80: "hold_press", 0x81: "hold_release"}`. An event code not in the table yields `event_type = None` and no event is fired (`if event_type: self.fire_event(...)`).
- 0x3A has the default `data_length = 1`; 0x3C dimmer `data_length=2`; 0x3B has `data_format="command"`.
- Order is **not enforced**: `if prev_obj_meas_type > obj_meas_type: _LOGGER.warning("... not sending object ids in numerical order ...")` and parsing continues.
- Unknown id: `if obj_meas_type not in MEAS_TYPES: _LOGGER.debug("Invalid Object ID found in payload") ; break` - objects parsed before the unknown id are kept and processed.
- Variable length: `if obj_data_format in ["raw", "string", "command"]` -> a length byte follows the id; for command `args_length = payload[obj_start + 1] & 0x1F; obj_data_length = 1 + args_length`.
- Truncation: `if payload_length < next_obj_start: ... break`.
- Multi-button index: a postfix `_1.._n` is assigned in order of appearance among all objects of the same type, including the `0x00` None entries (`postfix_counter = postfix_dict.get(meas_format, 0) + 1`), so the n-th 0x3A object is button n.
- Device info: `_is_encrypted` bit 0, `_is_sleepy_device` bit 2, `_get_version` `(adv_info >> 5) & 7`, and - **not in the spec** - `_is_mac_included` bit 1: "If True, the first 6 bytes contain the mac address"; the payload then starts at offset 7 and the MAC is taken reversed from bytes 1..6.
- Version other than 2 under UUID FCD2 is rejected (`BTHomeVersion.INVALID`).
- Packet-id filter `_skip_old_or_duplicated_advertisement`: first packet accepted; "more than 4 seconds since last packet, don't discard packet"; otherwise accepted only if the new id is 1..63 ahead of the last one modulo 256, everything else (same id or "older") is dropped.

Object table: the 97 ids in `const.py` equal the ids on bthome.io today: 0x00-0x2F, 0x3A-0x67, 0xF0-0xF2. Lengths in bytes (value only, without the id byte):

```
1: 00 01 09 0F-11 15-2D 2E 2F 3A 46 57 58 59 60 64 65 67
2: 02 03 06 07 08 0C 0D 0E 12 13 14 3C 3D 3F 40 41 43 44 45 47 48 49 4A 51 52 56 5A 5D 5E 5F 61 66 F0
3: 04 05 0A 0B 42 4B F2
4: 3E 4C 4D 4E 4F 50 55 5B 5C 62 63 F1
length byte follows the id: 53 (text), 54 (raw); 3B (command): length byte, low 5 bits = number of argument bytes, then 1 opcode byte + arguments
```

The table is moving: bthome.io repo commits "add hold_release button event (0x81)" 2026-10-27, "add frequency (0x66)" 2026-11-24, "add progress (0x67)" 2026-11-25.

Shelly-specific additions:

- Legacy hold code 0xFE: https://shelly-api-docs.shelly.cloud/docs-ble/Devices/BLU/wall_us/ "0xFE* button hold ... for firmware prior to 1.0.20" and changelog "[1.0.22] Change button hold event to 0x80 as is in BTHome official documentation" (non-ZB firmware line).
- The ZB page lists per button "1x, 2x, 3x short press, 1x, 2x, 3x long press, hold, none" without numbers; the KB says "Each button supports single, double, triple and long push - which allows the user to make 16 different actions!"; a forum user reports "single press, double press, triple press, and hold press" over BTHome for the ZB unit (https://community.home-assistant.io/t/shelly-blu-rc-button-4-zigbee-mode/978579 post 2). Which of 0x04/0x05/0x06/0x80 the ZB firmware really emits for a long press and for a held button is not established.

Design consequence: table-driven parser with the lengths above; accept device info only if version == 2 and reserved bits 1, 3, 4 are 0 (0x40/0x44 plain, 0x41/0x45 encrypted); do not reject out-of-order ids; stop at the first unknown id and still use what was parsed (so a future object after the buttons cannot hide a press, and one before them would hide it - log this case loudly); map 0xFE to hold; treat an unknown event code as "no event" plus a log line; button number = running count of 0x3A objects.

### Claim 3 - the real capture

Located: https://github.com/iobroker-community-adapters/ioBroker.shelly/issues/1506 (opened 2026-05-23, firmware field `260310/v1.2.13@f43a1caa`):

```
... "srcBle":{"type":"SBBT-104CUS","mac":"f8:23:77:0f:c9:90","rssi":-70},"payload":"44000e01643a003a013a003a00"}
[processBleMessage] Received unpacked payload {"encryption":false,"BTHome_version":2,"pid":14,"battery":100,"button_1":0,"button_2":1,"button_3":0,"button_4":0}
```

(The reporter shows two different, evidently edited MACs in the same issue, `f8:44:77:0a:cd:90` and `f8:23:77:0f:c9:90`; do not use them as address evidence.)

My decoding: `44` = 0b0100_0100: bit 0 = 0 plain, bit 2 = 1 trigger based, bits 5-7 = 2. `00 0e` packet id 14. `01 64` battery 100 %. `3a 00` button 1 none, `3a 01` button 2 press, `3a 00`, `3a 00`. Matches the claim. Note what is absent: no 0xF0 device id and no firmware object in a button packet.

### Claim 4 - timing

Vendor text, ZB page: under "Beacon": "Periodic, with interval: beacon mode: 12s, normal mode: 15min" (objects 0x01, 0xF0, 0xF1, 0xF2); under "Button events": "Single, with duration 1500ms". The same notation on sibling pages: Button Tough 1 ZB and Wall Switch 4 ZB "Single, with duration 1500ms", Motion ZB "Single, with duration 1000ms", H&T ZB "Single, with duration 3000ms". Vendor code: `reportCadenceSec: 900 // normal-mode beacon (15 min)`.

Contradictions inside vendor material:

- Common page (https://shelly-api-docs.shelly.cloud/docs-ble/common/): identification objects 0x00, 0x01, 0xF0, 0xF1 - "The packets is emited at device power on / restart and every 6 hours." The ZB page says 15 min. The vendor's own fleet code resolves this as "6h universal keepalive" for BLU and 900 s for BLU ZB.
- Beacon mode: API page 12 s, printed guide "emits beacons every 8 seconds".
- "Beacon Mode: If active, the device will send single advertising packets more often." - a beacon is a *single packet*, not a 1.5 s burst. The vendor's own staleness logic tolerates three missed reports (`BLU_MISSED_REPORTS_TOLERANCE = 3`, "A healthy BLU device can drop a few lossy BLE adverts").

Advertising interval inside the burst: not documented for the ZB (EFR32MG27) firmware. Only the older BLU firmware changelog has numbers (https://shelly-api-docs.shelly.cloud/docs-ble/changelog/): "[1.0.7] Change advertising period and duration to 50mS/2Sec", "[1.0.9] Change advertising timing to 20mS/1Sec", "[1.0.9] Change beacon packets timing: 4 pulses, 150mSec delay", "[1.0.23] Reduce beacon interval for pairing to 25mS", "[1.0.23] Wall US (RC Button4) Inrease max beacon time to 60 seconds. Send button hold after multiclick delay time". No sniffer measurement of any BLU ZB button was found (searched GitHub issues/code, HA, openHAB, ioBroker, Homey forums). Indirect: the current ioBroker gateway script scans passively with `interval_ms: 240, window_ms: 80` and is the supported way to receive these buttons, so the burst survives a 33 % duty cycle.

Delay between press and first packet: not documented for ZB. It cannot be zero, because the firmware has to wait out the multi-click window before it knows a press was single. Non-ZB sibling: characteristic "Multiclick speed (0-5) [800, 550, 300, 150, 90, 55] miliseconds delay", default not stated; "Send button hold after multiclick delay time". The ZB page lists no such characteristic. ZB changelog 1.2.12: "Zigbee poll on single button click for faster event delivery" (only relevant when joined).

Design consequence: treat 1500 ms as nominal; measure interval, burst length and press-to-packet latency on the device before fixing the scan duty cycle and the UX timing; the 15-minute beacon is a single lossy packet, so use it only as a soft liveness hint (vendor uses 45 min).

### Claim 5 - reserved gestures

Confirmed: ZB API page "To enter BLE pairing, press any button 4 times or hold any two buttons simultaneously for 10 seconds. The BLE pairing will be indicated by blinking the LED in blue." (duration 1 min) and "To start the ZigBee steering process, press any button 5 times." (duration 1 min). KB and printed guide say the same ("Additional press sequences (4x, 5x) are used for pairing").

Missing from the claim (same page):

- "ZigBee Find & Bind ... Press the corresponding button 3 times, holding the third press (or 2 short clicks + hold). The device will start blinking in magenta, indicating F&B procedure running." Duration 1 min.
- Two buttons held 10 s = BLE pairing.
- "press and hold any button for 30 seconds within 3 minutes of powering on the device" = factory reset; holding on "the device will still perform the factory reset and then enter bootloader mode".
- "To enter bootloader mode, hold the button while the device starts (when inserting the battery)."
- ZB changelog 1.2.12: "5-click now also enables Zigbee if it was previously disabled".

Design consequence: the gesture vocabulary is single, double, triple, long (and whatever the device sends for hold) per button and two-button chords shorter than 10 s. Never ask for 4+ presses, never for "two short then hold", never for a two-button hold. Because quick repeated presses are merged by the firmware, "next, next, next, next" pressed quickly is a 4x gesture: it sends no navigation event and opens BLE pairing for a minute.

### Claim 6 - encryption

Confirmed against https://bthome.io/encryption/ : "AES encryption (CCM mode) which works with a pre-shared key ... 16 bytes long key"; "Counter, which is a 4 bytes long value (unsigned 32-bit little-endian integer), which should increase every advertisement"; nonce parts "MAC address 5448E68F80A5 / UUID D2FC / BTHome device data byte 41 / Counter as bytes in little endian 33221100 / nonce = 5448e68f80a5d2fc4133221100"; `cipher = AES.new(key, AES.MODE_CCM, nonce=nonce, mac_len=4)`; "In BTHome V2, this header is not used anymore" (no associated data); service data = `D2FC` + `41` + ciphertext + counter + MIC = `d2fc41e445f3c9962b332211006c7c4519`.
I reproduced the example bit-exact (key `231d39c1d7cc1ab1aee224cd096db932`, plaintext `02ca0903bf13`, counter 1122867 -> `41e445f3c9962b332211006c7c4519`); with the MAC bytes reversed the MIC check fails, so the MAC goes into the nonce in display order (as `BluetoothDevice.getAddress()` prints it). Flipping the device-info byte also breaks the MIC, because it is part of the nonce.
bthome-ble does the same: `get_counter()` = `service_data[-8:-4]`, `get_mic()` = `[-4:]`, nonce `b"".join([bthome_mac, b"\xd2\xfc" + bytes([adv_info]), counter])`, `AESCCM(bindkey, tag_length=4)`, associated data `None` for v2.

Shelly side (https://shelly-api-docs.shelly.cloud/docs-ble/encryption/ and /common/): "According to BTHome speciffications ... Shelly BLU devices use AES encryption (CCM mode). The encryption key is auto generated using a true hardware random generator. The pin code given by the user during encryption is used for a higher level of security pairing." "The encryption key can be obtained by reading it from the device (pairing is required)." "Encryption is activated by writting a 6 digit code to the Passkey charackteristic. Decryption is only possible with factory reset." "After device is encrypted, further connection to it will require the pin code." Characteristics in service `de8a5aac-a99b-c315-0c80-60d4cbb51225`: Passkey `0ffb7104-860c-49ae-8989-1f946d5f6c03` (4 bytes, "Write bonded ... (0-999999). Little endian encoded"), Encryption key `eb0fb41b-af4b-4724-a6f9-974f55aba81a` (16 bytes, "Read bonded", "randomly generated on each change of the current passkey"), Factory reset `b0a7e40f-2b87-49db-801c-eb3686a24bdb` ("Write bonded ... Write 1 to restore factory settings"), Unix time `d56a3410-115e-41d1-945b-3a7f189966a1` ("Read, Write bonded"). ZB firmware changelog BLU_FW 1.2.12 (2026-03-04): "Encryption key BLE characteristic now requires encrypted connection instead of bonding". Pairing: "The maximum number of paried devices is 4."

How the key is obtained in practice: with the vendor's "Shelly BLE Debug" app (Google Play `cloud.shelly.bledebug`, "engineering app for testing the communication with the Shelly BLE Devices, a.k.a. BTHome implementations", updated 2026-03-11, no account). Reports: "After enabling encryption the app showed "Device found" message with json dump of device parameters, including encryption key. On the lower level encryption key can be read from eb0fb41b-af4b-4724-a6f9-974f55aba81a UUID but this can only be done from a paired device" (https://community.home-assistant.io/t/617477 post 8); Play review 2024-12-06: "update firmware ... without setting up some kind of cloud account ... It even let me set up the encryption". Doing the same from our own app (4 presses, bond, write passkey, read key) is plausible from the documented characteristics and from third-party GATT scripts for BLU devices (e.g. https://github.com/mik3y/stargarden/blob/main/scripts/blu_config.py for settings), but nobody was found who did the encryption step on the ZB button without the vendor app.

Corrections that matter:

1. **The Shelly "counter" is a clock, not a packet counter.** Evidence:
   - https://github.com/home-assistant/core/issues/173146 (encrypted "Shelly Blu 4 (without zigbee)", June 2026): "The new encryption counter (1780672257) is not larger than the previous value (1780672257)" ... 1780672258 ... 59 ... 60 ... 61. 1780672257 = 2026-06-05T15:10:57Z, the wall-clock time of that log (17:1x CEST). A second user's unit: 29907339, 29907341, 29907343 logged at 20:45:46.7, :48.7, :50.8 - one count per second, value = 346 days, i.e. a clock that was never set.
   - Encrypted Shelly captures in theengs/decoder `tests/BLE/test_ble.cpp`: counters `b8f09364` = 2023-06-22T06:56:56Z (Button1), `ed16c164` = 2023-07-26 (Door/Window), `4bfbd366`, `aae9d466`, `e9e9d466` = 2024-09-01 (H&T).
   - An independent Shelly-focused decoder has a mode "counter is time: the counter in encrypted messages is checked again the actual time" and rejects packets more than 30 s old or 15 s ahead (https://github.com/m-schaeffler/node-red-my-nodes/tree/main/node-red-bthome).
   - Home Assistant's parser was made strict in 3.23.x (`<=`), broke Shelly BLU buttons ("It seems that the Shelly BLU buttons are showing issues as well", https://github.com/Bluetooth-Devices/bthome-ble/issues/386) and was reverted; today it rejects only `new < last and bindkey_verified and new >= 100`.
   Consequences: all repeats of a burst within the same second carry the same counter and identical bytes; a 1.5 s burst can span two or three counter values with the *same* packet id (new ciphertext, same event); two different events in the same second share a counter; after a battery change the value restarts low unless the clock is written again. All of this is observed on the non-ZB BLU line; the ZB firmware is undocumented here and must be measured.
2. "Irreversible except by factory reset" is the vendor's wording, but factory reset is also a GATT write for a bonded peer, so the vendor apps can "decrypt" a device without the 30-second battery procedure. Older BLU changelog: "Remove completely PIN bonding. Pin code during encryption and decryption is only used to authenticate decryption".
3. Replay protection is entirely the receiver's job (bthome.io: "The receiver must also check the message counter ... must increase compared against last time"; "Hackers can always send non-encrypted (or modified encrypted) data with your (copied) MAC address"). Shelly's gateway only got "BTHome: Implement encrypted advertisement replay attack protection" recently and reports `unencrypted_data` as an error for a device that has a key.

Design consequence: implement CCM exactly as above (13-byte nonce, 4-byte tag, no AAD, counter and MIC are the last 8 bytes). Acceptance rule for an encrypted remote: drop plaintext from that MAC; accept a packet iff MIC is valid and `counter >= lastCounter`; de-duplicate on packet id (not on bytes, not on counter); persist `(lastCounter, packet ids already accepted at lastCounter)` so that an old packet replayed later fails `counter >= lastCounter` and a replay of the newest packet fails the packet-id memory; provide a deliberate "re-enrol remote" action for the battery-change case instead of auto-accepting low counters. Optionally write the Unix time at enrolment and additionally require the counter to be near the phone clock (needs a drift measurement first).

### Claim 7 - static address

For:

- All BLU MAC prefixes found are IEEE-registered to Silicon Laboratories (checked against the IEEE `oui.csv` copy in `research/ble-shelly/`): ZB units `28:68:47` (BLE address `28:68:47:f0:12:c7` of an RC Button 4 ZB in the gateway dump above; Zigbee EUI-64 `0x286847fffeeff1a3` of another unit in https://github.com/Koenkk/zigbee2mqtt/issues/30640), `F8:44:77` (Zigbee EUI-64 `0xf84477fffe04a1d3`, `...047d4e`, `...34fbd2`, `...42ff34` of RC Button 4 ZB units in https://github.com/Koenkk/zigbee2mqtt/issues/30941; an EUI-64 of the form OUI-FFFE-xxxxxx is an expanded EUI-48, and the one BLE address we have uses the same OUI block), `FC:4D:6A` (BLE address of the Motion ZB first-hand report); non-ZB units `38:39:8F`, `BC:02:6E`, `3C:2E:F5`, `60:EF:AB`, `8C:6F:B9`. A registered OUI means a public device address; `28:...` could not even be a valid static random address (top two bits are not 11).
- The device repeats its MAC in the scan response: common page "0x0A | 6 | MAC address of the device"; real sample from the Motion ZB `01 01 00 0b 13 00 0a e3 53 26 6a 4d fc` = MAC `FC:4D:6A:26:53:E3` stored little-endian.
- Vendor gateways identify a BLU device permanently by address: https://shelly-api-docs.shelly.cloud/gen2/DynamicComponents/BTHome/BTHomeDevice "The BTHomeDevice component stands for an individual physical device identified by its MAC address"; `BTHome.AddDevice '{"config":{"addr":"3c:2e:f5:71:d5:2a"}}'`.
- BTHome decryption needs the advertiser address in the nonce.

Against / limits:

- No vendor statement of the address type and no capture across a battery change or factory reset. The author of the Motion ZB report also left it open ("MAC が安定していることだけで `isRandom=false` と判断しない").
- Common page: "Reconnection Address | 00002a03-... | Device MAC address in OTA mode (Bootloader active)" - in bootloader/OTA mode the device shows a different address.

Android side (AOSP `android-17.0.0_r1`):

- `framework/java/android/bluetooth/le/ScanFilter.java`: public `setDeviceAddress(String)` - "The @AddressType is defaulted to BluetoothDevice#ADDRESS_TYPE_PUBLIC"; the overloads with an address type are `@SystemApi`. Host-side match is a plain string comparison: `mDeviceAddress.equals(device.getAddress())` (so store the MAC in upper case).
- `system/gd/hci/le_scanning_manager_impl.cc`, `update_address_filter`: the controller filter is always created with `ApcfApplicationAddressType::NOT_APPLICABLE` ("specifying PUBLIC (0) will only return results with a public address ... For this, Addresses type not applicable (0x02) must be specified"). So the offloaded address filter matches public and random addresses alike.
- `framework/api/current.txt`: `method public int getAddressType();` on `BluetoothDevice` is public API, so the type can be logged on the device.

Design consequence: enrol by MAC and filter on it; log `getAddressType()` and the address over days, across a battery removal and after a factory reset; keep a recovery path "re-enrol by service-data filter + device id 0x16" in case the address ever differs; remember that a MAC filter identifies, it does not authenticate.

### Claim 8 - no HID

- Vendor interface description (ZB page): Bluetooth = "Advertising (BTHome)" plus a "Characteristics" table with exactly four entries (Find my device, Beacon mode, Factory reset, BTHome sample); common page: "All characteristics are contained in Shelly service." plus Device Information 180A. No HID service, no keyboard / consumer-control mode anywhere in docs-ble, KB or user guide (grep for hid / keyboard / media / consumer over all fetched vendor pages: no hit).
- KB "Supported Devices: Shelly Plus / Gen3 / Gen4 series, Shelly Pro series" (the gateways that listen to its advertisements).
- GATT enumeration of a sibling on the same firmware line (Motion ZB, link above): "GATT の Shelly service `de8a5aac-a99b-c315-0c80-60d4cbb51225` と Device Information service `0x180A` を確認した".
- Every integration found (Home Assistant, openHAB, ioBroker, Homey, Node-RED, Theengs, zigbee2mqtt, ZHA, deCONZ) uses BTHome advertisements or Zigbee; none uses HID.

Limit: nobody published a GATT service list of the SBBT-104CUS itself. Design consequence: the remote can only be received by scanning; Android's media-button path cannot see it. Close the gap with one service discovery in pairing mode during the first device test.

---

## 3. Test vectors for the Kotlin unit tests

Input = service data **as Android returns it** from `ScanRecord.getServiceData(ParcelUuid("0000fcd2-0000-1000-8000-00805f9b34fb"))`, i.e. without the two UUID bytes `D2 FC`. All rows were checked with my own decoder `verify-scripts/vectors.py` (Y26 and Y32 in a follow-up run with the same module). "Buttons" lists the 0x3A objects in order.

### 3.1 Real captures

| Id | Hex in | Expected | Origin |
|---|---|---|---|
| R1 | `44000e01643a003a013a003a00` | v2, plain, trigger; pid 14; battery 100; buttons [none, press, none, none] -> event (button 2, PRESS) | REAL, SBBT-104CUS fw 1.2.13, ioBroker.shelly issue 1506 |
| R2 | `44001d01643a013a003a003a00` | pid 29; battery 100; [press, none, none, none] -> (button 1, PRESS) | REAL-DERIVED, non-ZB RC Button 4 / Wall Switch 4, theengs/decoder `tests/BLE/test_ble.cpp` line 1177 (MAC anonymised) |
| R3 | `40001d01643a01` | device info 0x40 (not trigger flagged); pid 29; battery 100; [press] | REAL-DERIVED, BLU Button1 older firmware, theengs line 1175 |
| R4 | `4562511158bd25b8f093645b573115` | encrypted; ciphertext `62511158bd25`; counter 1687417016 (bytes `b8f09364`); MIC `5b573115`; without key -> "needs key", no event | REAL-DERIVED, encrypted BLU Button1, theengs line 1176 (key and true MAC unknown) |
| R5 | `44000801643a016401` | pid 8; battery 100; [press]; then object 0x64 = 1 (skipped by length) -> (button 1, PRESS) | REAL, BLU Motion ZB fw 1.2.20, hshimomura/home-metrics investigation |
| R6 | `440010016421006400` | pid 16; battery 100; 0x21 = 0; 0x64 = 0; no button object -> no event | REAL, same source (companion plan document) |
| R7 | `4400020153050000002d003f0000` | pid 2; battery 83; 0x05 (3 bytes) = 0; 0x2D = 0; 0x3F (2 bytes) = 0 -> no event | REAL, BLU Door/Window, bthome-ble PR 422 |
| R8 | `44005d016405fc21002d013f9601` | pid 93; battery 100; 0x05 = `fc2100`; 0x2D = 1; 0x3F = `9601` -> no event | REAL-DERIVED, theengs line 1178 |
| R9 | `4400ae01642e383afe45fa00` | pid 174; battery 100; 0x2E = 0x38; [0xFE = legacy hold]; 0x45 = `fa00` -> (button 1, HOLD) | REAL-DERIVED, BLU H&T old firmware, theengs line 1187 (MAC is a placeholder) |
| R10 | `44000d5a10ffa90b01` | pid 13; 0x5A (2 bytes) = `10ff`; **unknown id 0xA9 -> stop**; no button, no crash | REAL but not on-air: value of the "BTHome sample" GATT characteristic read from a Motion ZB in pairing mode (it runs into the manufacturer-data bytes `ff a9 0b`) |

### 3.2 Specification examples

| Id | Hex in | Expected | Origin |
|---|---|---|---|
| S1 | `4002c40903bf13` | plain, not trigger; 0x02 = `c409` (25.00 C), 0x03 = `bf13` (50.55 %); no event | bthome.io/format example |
| S2 | `403a003a01` | [none, press] -> (button 2, PRESS) | built from the spec sentence "3A003A01 ... press for the second button" |
| S3 | `41e445f3c9962b332211006c7c4519` with MAC `54:48:E6:8F:80:A5`, key `231d39c1d7cc1ab1aee224cd096db932` | nonce `5448e68f80a5d2fc4133221100`; counter 1122867; plaintext `02ca0903bf13`; MIC ok | bthome.io/encryption example, reproduced |
| S3b | same bytes, MAC `A5:80:8F:E6:48:54` | MIC failure | proves MAC byte order |

### 3.3 Synthetic (derived from spec + vendor layout `44 00 pid 01 batt 3A b1 3A b2 3A b3 3A b4`)

| Id | Hex in | Expected |
|---|---|---|
| Y1 | `44001001643a013a003a003a00` | (button 1, PRESS), pid 16 |
| Y2 | `44001001643a023a003a003a00` | (button 1, DOUBLE_PRESS) |
| Y3 | `44001001643a033a003a003a00` | (button 1, TRIPLE_PRESS) |
| Y4 | `44001001643a043a003a003a00` | (button 1, LONG_PRESS) |
| Y5 | `44001001643a053a003a003a00` | (button 1, LONG_DOUBLE_PRESS) |
| Y6 | `44001001643a063a003a003a00` | (button 1, LONG_TRIPLE_PRESS) |
| Y7 | `44001001643a803a003a003a00` | (button 1, HOLD_PRESS) |
| Y8 | `44001001643a813a003a003a00` | (button 1, HOLD_RELEASE) |
| Y9 | `44001001643afe3a003a003a00` | (button 1, HOLD_PRESS) via legacy code 0xFE |
| Y10 | `44001001643a073a003a003a00` | unknown event code 0x07 -> no event, logged; other slots still evaluated |
| Y11 | `44002a015f3a003a003a003a04` | pid 42; battery 95; (button 4, LONG_PRESS) |
| Y12 | `4400ff01073a003a003a023a00` | pid 255; battery 7; (button 3, DOUBLE_PRESS) |
| Y13 | `44001101643a013a003a013a00` | two events in one packet: (button 1, PRESS) and (button 3, PRESS) (vendor example for the non-ZB model) |
| Y14 | `44001201643a003a003a003a00` | all four none -> no event |
| Y15 | `4400130164f01600f1000d0201f20d0201` | beacon shape per vendor table: pid 19; battery 100; device type id 0x0016; firmware 1.2.13.0 (F1) and 1.2.13 (F2); no event. Whether the real beacon carries a pid and both firmware objects is unknown |
| Y16 | `440164f01600f1000d0201` | beacon without packet id: battery 100; device type 0x0016; firmware 1.2.13.0; no event; must not disturb packet-id state |
| Y17 | `44000501643a01a9013a02` | (button 1, PRESS) only; parsing stops at unknown id 0xA9, the later `3a 02` is ignored |
| Y18 | `44000701643a` | truncated 0x3A object -> pid 7 and battery 100 parsed, no event, no exception |
| Y19 | `44` | device info only -> no event, no exception |
| Y20 | `4400` | truncated packet id -> no event, no exception |
| Y21 | `24000701643a01` | version bits = 1 under UUID FCD2 -> reject |
| Y22 | `64000701643a01` | version bits = 3 -> reject |
| Y23 | `4400083a0153026869` | (button 1, PRESS); then text object 0x53 length 2 "hi" skipped by its length byte |
| Y24 | `4400093a013b010305` | (button 1, PRESS); then command object 0x3B: length byte 0x01 -> opcode 0x03 + 1 argument byte, skipped correctly |
| Y25 | `403a013c0103` | (button 1, PRESS); then dimmer 0x3C (2 bytes: event 1, 3 steps) |
| Y26 | `42a5808fe6485400073a01` | bit 1 set. Reference parser: MAC `54:48:E6:8F:80:A5` taken from bytes 1..6 reversed, then pid 7, (button 1, PRESS). Recommended for this app: reject, reserved bit set |
| Y27 | `4590c30a24367381a0d7927ebf01e7226a7af5fd6b` with MAC `F8:44:77:00:00:01`, key `000102030405060708090a0b0c0d0e0f` | encrypted, trigger; counter 1780672257 (bytes `01e7226a`); plaintext `000e01643a003a013a003a00` -> pid 14, battery 100, (button 2, PRESS) |
| Y28 | `4587794eea5f46236cc50a1f8f2ce7226a2c82c85c`, same MAC and key | counter 1780672300; plaintext `000f01643a003a003a003a04` -> pid 15, (button 4, LONG_PRESS) |
| Y29 | `4590c30a24367381a0d7927ebf01e7226a7af5fd00`, same MAC and key | last MIC byte altered -> MIC failure, no event |
| Y30 | Y27 bytes with key `ffffffffffffffffffffffffffffffff` | MIC failure |
| Y31 | Y27 bytes with MAC `F8:44:77:00:00:02` | MIC failure |
| Y32 | `4190c30a24367381a0d7927ebf01e7226a7af5fd6b` (Y27 with device info 0x41) | MIC failure (device info is part of the nonce) |
| Y33 | Y1 received from a MAC that has a stored key | plaintext from an encrypted remote -> dropped (downgrade) |

### 3.4 Sequence tests (synthetic, behaviour not bytes)

| Id | Sequence | Expected |
|---|---|---|
| Q1 | R1 twenty times within 1.5 s | exactly one event |
| Q2 | pid 255 press, then pid 0 press 3 s later | two events (wrap) |
| Q3 | pid 200 press, then pid 0 press 10 s later (device rebooted, counter restarted) | two events; an "older id" rule must not eat the second one |
| Q4 | pid 14 press, 30 s pause, pid 14 press again with identical bytes | bthome-ble would accept the second (4 s rule). Decide deliberately: for an unencrypted remote accept after the burst window; for an encrypted remote use counter + packet-id memory instead |
| Q5 | encrypted: (counter c, pid p) then (counter c+1, pid p) | one event (burst crossed a second boundary) |
| Q6 | encrypted: (c, p) then (c, p+1) | two events (two gestures within one second) |
| Q7 | encrypted: accept (c+5, p+1), then replay of (c, p) | replay dropped (counter went backwards) |
| Q8 | encrypted: accept (c, p), one hour later the same bytes again | dropped (same counter, packet id already accepted at that counter) |
| Q9 | Y16 between two button packets | beacon produces no event and does not reset de-duplication |

---

## 4. Surprises (not covered by the claims)

1. **Counter = clock** on Shelly BLU encrypted devices (claim 6, correction 1). It decides the whole replay-protection design and the battery-change story.
2. **Fast repeated presses are not separate events.** The firmware coalesces presses into double/triple events and treats four as "open BLE pairing for 1 minute", five as "start Zigbee steering for 1 minute and enable Zigbee". Spoken-menu navigation by tapping one button repeatedly will misfire unless the app maps DOUBLE/TRIPLE of a navigation button to 2/3 steps or the user is told to pace presses.
3. **An accidental 4x press is a security event.** While pairing mode is open the device accepts a connection; on a Motion ZB in pairing mode a third party read all settings with `Paired=0, Bonded=0`. Inferred from the documented characteristics (Passkey is "Write bonded", pairing is open in pairing mode, up to 4 peers), not tested: a stranger in range could bond and, on an unencrypted unit, write a passkey, which turns encryption on with a key we do not have; recovery needs a factory reset. Worth a line in the user notes and a "remote went encrypted unexpectedly" state in the app.
4. **A third reserved gesture**: two short presses followed by a hold starts Zigbee Find & Bind.
5. **Packet id restarts after a device reboot.** Observed on a BLU Door/Window: "The sensor restarts when the door is shut, its packet id counter starts again from 0, and the first advertisements after the restart are skipped as older packets" (bthome-ble PR 422). A coin cell under radio load can brown out, so the same can happen to a remote. De-duplicate on equality within the burst window; do not discard "older" ids outside it.
6. **The BTHome object table changes monthly** (0x81 on 2026-10-27, 0x66 on 2026-11-24, 0x67 on 2026-11-25). Keep the table as data and rely on stop-at-unknown; a table frozen today will meet unknown ids.
7. **Reference parser reads bit 1 as "MAC included"**, which the spec calls reserved (open discussion in bthome-ble PR 328). Decide explicitly; for a single known remote, rejecting reserved bits is the safer choice.
8. **Garbage around pairing mode.** ZB changelog 1.2.12: "Wall US Removed parasitic BTHome item that appeared during BLE pairing"; the "BTHome sample" characteristic of a 1.2.20 device returned `44000d5a10ffa90b01` (runs into the manufacturer data). The parser must survive this (vector R10); firmware older than 1.2.12 may emit such packets on air after a 4x press.
9. **The periodic beacon is one packet, and the vendor documents two different periods** (15 min on the ZB page, "every 6 hours" on the common page) and two beacon-mode periods (12 s vs 8 s). Heartbeat logic must tolerate several misses.
10. **Scan type matters for this button family.** Home Assistant users received "only every fifteenth or twentieth button press" of the non-ZB RC Button 4 with active scanning and all of them with passive scanning (forum thread 781849; again in core issue 173146: "only worked correctly ... if passive scanning ... was activated"). The ioBroker gateway script moved from `active: true` (v1.2) to `active: false, interval_ms: 240, window_ms: 80` (v1.4.0). `ScanSettings.SCAN_TYPE_PASSIVE` is public API in android-17.0.0_r1 (`field public static final int SCAN_TYPE_PASSIVE = 1`).
11. **Zigbee appears to ship disabled** on the BLU+ZB line (Motion ZB unit read `Zigbee 0: disabled`; changelog "5-click now also enables Zigbee if it was previously disabled"), which supports "never press 5 times" rather than "unjoin".
12. **Which event codes the ZB button emits is unsettled**: vendor API page lists seven gestures, the KB counts four per button, a user reports single / double / triple / hold. The app must not depend on telling LONG_PRESS (0x04) from HOLD_PRESS (0x80) until measured, and should not expect HOLD_RELEASE.
13. **Device id 0xF0 is not in button packets** (R1), only in the periodic beacon. During enrolment the app sees a 0xFCD2 packet with four 0x3A slots and nothing that says "0x16"; use the scan-response manufacturer data (company 0x0BA9, block 0x0B = `16 00`, block 0x0A = MAC little-endian) or wait for a beacon.
14. **MAC filter strings must be upper case** (`ScanFilter.matches` uses `String.equals`; `checkBluetoothAddress` accepts upper case only).
15. Non-ZB changelog 1.0.23-hotfix1 (2025-08-24): "Wall US (RC Button4) Fix device encrypted advertising" - encrypted advertising on the four-button family has had bugs; verify decryption on the actual firmware before relying on it.

---

## 5. Still needs a device test

1. Fresh unit, tab pulled, one press: is there a 0xFCD2 packet with device info 0x44, and no encryption? Record firmware from the beacon (0xF1/0xF2).
2. Raw bytes for every gesture on every button: 1x, 2x, 3x, long, 2x long, 3x long, held for 5 s (does 0x80 repeat? is there 0x81 or a trailing all-none packet?), two buttons at once.
3. What is sent for 4x, 5x and "2 short + hold" (any BTHome packet before the mode switch?).
4. Burst length, advertising interval, PDU type, and press-to-first-packet latency (LOW_LATENCY scan with `getTimestampNanos()`, better an nRF sniffer); whether a multiclick-speed characteristic `35c8a5e2-...` exists on the ZB model.
5. Packet id: +1 per gesture, constant inside a burst, behaviour after battery removal; content and period of the beacon (pid present? one packet or several?).
6. Address: `getAddressType()`, stability over days, battery change, factory reset; address in bootloader mode.
7. GATT service list in pairing mode (expect Shelly service + 0x180A, no 0x1812); which characteristics are readable without bonding.
8. Encryption end to end without the vendor app: bond, write passkey, read key; decrypt real packets with our CCM; counter origin (clock? value after battery change? after writing Unix time?), counter inside one burst, drift per day.
9. Passive versus active scan catch rate on the Pixel.
10. Whether Zigbee is disabled on the fresh unit (GATT setting, if exposed) and whether an unjoined unit's burst differs.

## 6. Evidence files (all under `.../scratchpad/research/verify-bthome/` unless noted)

- Vendor pages: `shelly-api-docs.shelly.cloud_docs-ble_Devices_BLU_ZB_button4_ZB_.{html,txt}`, `..._common.*`, `..._encryption.*`, `..._changelog.*`, `..._changelog_mg27.*`, `..._Devices_BLU_wall_us.*`, `v_docs-ble_Devices_BLU_ZB_{button1_ZB,button22_ZB,blu_remote,BluMotionZB,dw_ZB,ht_ZB}.txt`, `v_gen2_DynamicComponents_BTHome_BTHomeDevice.txt`, `kb.shelly.cloud_knowledge-base_shelly-blu-rc-button-4-zb.*`, `play_bledebug.txt`, `sitemap.xml`
- Spec: `bthome.io_format_.{html,txt}`, `bthome.io_encryption_.{html,txt}`
- Captures and reports: `iobroker_1506.json`, `gh_home-assistant_core_issues_173146*.json`, `gh_Bluetooth-Devices_bthome-ble_issues_386*.json`, `gh_bieniu_ha-shellies-discovery-gen2_issues_753.json`, `theengs_test_ble.cpp`, `hm_blu_motion_zb_investigation.md`, `hm_blu_motion_zb_cisco_plan.md`, `nodered_bthome.js`, `nodered_bthome_README.md`, `fm_BTHomeData.ts`, `stargarden_blu_config.py`, `ha_617477.json`
- Reference parser: `.../scratchpad/repos/bthome-ble` (commit `14bdb2e`)
- AOSP: `.../scratchpad/repos/aosp-bluetooth-a17` (tag `android-17.0.0_r1`)
- Vector checker: `.../scratchpad/research/verify-scripts/vectors.py`
