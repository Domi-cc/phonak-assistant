package io.github.domicc.lara.core.bthome

import io.github.domicc.lara.core.bthome.ButtonEvent.DOUBLE
import io.github.domicc.lara.core.bthome.ButtonEvent.HOLD
import io.github.domicc.lara.core.bthome.ButtonEvent.HOLD_RELEASE
import io.github.domicc.lara.core.bthome.ButtonEvent.LONG
import io.github.domicc.lara.core.bthome.ButtonEvent.LONG_DOUBLE
import io.github.domicc.lara.core.bthome.ButtonEvent.LONG_TRIPLE
import io.github.domicc.lara.core.bthome.ButtonEvent.NONE
import io.github.domicc.lara.core.bthome.ButtonEvent.PRESS
import io.github.domicc.lara.core.bthome.ButtonEvent.TRIPLE
import io.github.domicc.lara.core.bthome.ButtonEvent.UNKNOWN

/**
 * The test vectors of the BTHome verification report of 27 November 2026 (research notes, kept
 * outside the repository), with the ids used there. Input is the service data as Android
 * returns it for UUID 0xFCD2.
 *
 * R = from real devices, S = examples of the BTHome specification, Y = built from the
 * specification and the vendor's packet layout `44 00 pid 01 battery 3A b1 3A b2 3A b3 3A b4`.
 * Not every R vector was seen on the air by somebody who says so: R2, R3, R4, R8 and R9 are
 * taken from the test file of another decoder, and R10 was read from a device, not received as
 * an advertisement.
 *
 * C = advertisements of the remote this app is built for (RC Button 4 without Zigbee,
 * SBBT-004CUS), received by this app on the owner's phone on 27 November 2026. They are the
 * only vectors here that are known to be what this remote really sends.
 *
 * Four vectors of the report are left out because they cannot be told from others without
 * decryption or a key store, which version 0.1 does not have: S3b (S3 checked against a
 * reversed sender address), Y30 and Y31 (Y27 with a wrong key and a wrong address) and Y33 (Y1
 * from a remote that has a key).
 *
 * @param expected null if the parser has to reject the data
 */
class BthomeVector(val id: String, val hex: String, val expected: BthomePacket?) {
    val bytes: ByteArray get() = hex.hexToByteArray()
}

object BthomeVectors {

    /** Device information 0x44: version 2, sent on a trigger, not encrypted. */
    private fun triggered(
        packetId: Int? = null,
        battery: Int? = null,
        buttons: List<ButtonEvent> = emptyList(),
        stop: BthomeStop? = null,
    ) = BthomePacket(0x44, encrypted = false, triggerBased = true, packetId, battery, buttons, stop)

    /** Device information 0x40: version 2, sent at regular intervals, not encrypted. */
    private fun regular(packetId: Int? = null, battery: Int? = null, buttons: List<ButtonEvent> = emptyList()) =
        BthomePacket(0x40, encrypted = false, triggerBased = false, packetId, battery, buttons)

    /** Encrypted packets carry nothing: this app has no key. */
    private fun encrypted(deviceInfo: Int, triggerBased: Boolean) =
        BthomePacket(deviceInfo, encrypted = true, triggerBased = triggerBased)

    private fun button1(event: ButtonEvent) = listOf(event, NONE, NONE, NONE)

    val real = listOf(
        // RC Button 4 ZB, firmware 1.2.13: button 2 pressed
        BthomeVector("R1", "44000e01643a003a013a003a00", triggered(14, 100, listOf(NONE, PRESS, NONE, NONE))),
        // said to be an RC Button 4 without Zigbee, from another decoder's tests: button 1 pressed
        BthomeVector("R2", "44001d01643a013a003a003a00", triggered(29, 100, button1(PRESS))),
        // BLU Button1 with older firmware, from another decoder's tests: one button object,
        // trigger flag not set
        BthomeVector("R3", "40001d01643a01", regular(29, 100, listOf(PRESS))),
        // encrypted BLU Button1, from another decoder's tests: cipher text, counter, check value
        BthomeVector("R4", "4562511158bd25b8f093645b573115", encrypted(0x45, triggerBased = true)),
        // BLU Motion ZB: a press followed by object 0x64, which is stepped over
        BthomeVector("R5", "44000801643a016401", triggered(8, 100, listOf(PRESS))),
        // the same device without a button object
        BthomeVector("R6", "440010016421006400", triggered(16, 100)),
        // BLU Door/Window: 0x53 here is the battery value 83, not the text object
        BthomeVector("R7", "4400020153050000002d003f0000", triggered(2, 83)),
        // from another decoder's tests, like R9
        BthomeVector("R8", "44005d016405fc21002d013f9601", triggered(93, 100)),
        // BLU H&T with old firmware: hold sent as 0xFE
        BthomeVector("R9", "4400ae01642e383afe45fa00", triggered(174, 100, listOf(HOLD))),
        // not an advertisement: read from a device in pairing mode over a connection. It runs
        // into foreign bytes, unknown object 0xA9.
        BthomeVector("R10", "44000d5a10ffa90b01", triggered(13, stop = BthomeStop.UNKNOWN_OBJECT)),
    )

    val captured = listOf(
        BthomeVector("C1", "44005d01643a013a003a003a00", triggered(93, 100, button1(PRESS))),
        // a held button: "hold" while it is down ...
        BthomeVector("C2", "44006201643a803a003a003a00", triggered(98, 100, button1(HOLD))),
        // ... and "long" under the next packet id when it is released
        BthomeVector("C3", "44006301643a043a003a003a00", triggered(99, 100, button1(LONG))),
        BthomeVector("C4", "44006401643a003a023a003a00", triggered(100, 100, listOf(NONE, DOUBLE, NONE, NONE))),
        BthomeVector("C5", "44006601643a003a003a003a03", triggered(102, 100, listOf(NONE, NONE, NONE, TRIPLE))),
    )

    val specification = listOf(
        // temperature and humidity
        BthomeVector("S1", "4002c40903bf13", regular()),
        // "3A003A01: no event for the first button, press for the second"
        BthomeVector("S2", "403a003a01", regular(buttons = listOf(NONE, PRESS))),
        // the encryption example; this app has no key, so it stays closed
        BthomeVector("S3", "41e445f3c9962b332211006c7c4519", encrypted(0x41, triggerBased = false)),
    )

    val synthetic = listOf(
        BthomeVector("Y1", "44001001643a013a003a003a00", triggered(16, 100, button1(PRESS))),
        BthomeVector("Y2", "44001001643a023a003a003a00", triggered(16, 100, button1(DOUBLE))),
        BthomeVector("Y3", "44001001643a033a003a003a00", triggered(16, 100, button1(TRIPLE))),
        BthomeVector("Y4", "44001001643a043a003a003a00", triggered(16, 100, button1(LONG))),
        BthomeVector("Y5", "44001001643a053a003a003a00", triggered(16, 100, button1(LONG_DOUBLE))),
        BthomeVector("Y6", "44001001643a063a003a003a00", triggered(16, 100, button1(LONG_TRIPLE))),
        BthomeVector("Y7", "44001001643a803a003a003a00", triggered(16, 100, button1(HOLD))),
        BthomeVector("Y8", "44001001643a813a003a003a00", triggered(16, 100, button1(HOLD_RELEASE))),
        // legacy hold code of Shelly firmware before 1.0.22
        BthomeVector("Y9", "44001001643afe3a003a003a00", triggered(16, 100, button1(HOLD))),
        // event code 0x07 does not exist; the other three slots are still read
        BthomeVector("Y10", "44001001643a073a003a003a00", triggered(16, 100, button1(UNKNOWN))),
        BthomeVector("Y11", "44002a015f3a003a003a003a04", triggered(42, 95, listOf(NONE, NONE, NONE, LONG))),
        BthomeVector("Y12", "4400ff01073a003a003a023a00", triggered(255, 7, listOf(NONE, NONE, DOUBLE, NONE))),
        // vendor example for the model without Zigbee: buttons 1 and 3 pressed together
        BthomeVector("Y13", "44001101643a013a003a013a00", triggered(17, 100, listOf(PRESS, NONE, PRESS, NONE))),
        BthomeVector("Y14", "44001201643a003a003a003a00", triggered(18, 100, listOf(NONE, NONE, NONE, NONE))),
        // beacon: device type 0x0016 and two firmware objects, no button
        BthomeVector("Y15", "4400130164f01600f1000d0201f20d0201", triggered(19, 100)),
        // beacon without packet id
        BthomeVector("Y16", "440164f01600f1000d0201", triggered(battery = 100)),
        // unknown object 0xA9 after the first button: the later 3A 02 must not be read
        BthomeVector("Y17", "44000501643a01a9013a02", triggered(5, 100, listOf(PRESS), BthomeStop.UNKNOWN_OBJECT)),
        // cut off inside the button object
        BthomeVector("Y18", "44000701643a", triggered(7, 100, stop = BthomeStop.TRUNCATED)),
        BthomeVector("Y19", "44", triggered()),
        BthomeVector("Y20", "4400", triggered(stop = BthomeStop.TRUNCATED)),
        // version 1 and version 3 under the version-2 UUID
        BthomeVector("Y21", "24000701643a01", null),
        BthomeVector("Y22", "64000701643a01", null),
        // text object 0x53 "hi" and command object 0x3B behind the button, stepped over by their length byte
        BthomeVector("Y23", "4400083a0153026869", triggered(8, buttons = listOf(PRESS))),
        BthomeVector("Y24", "4400093a013b010305", triggered(9, buttons = listOf(PRESS))),
        // dimmer object 0x3C (two bytes) behind the button
        BthomeVector("Y25", "403a013c0103", regular(buttons = listOf(PRESS))),
        // reserved bit 1 set; the reference parser would read six address bytes first
        BthomeVector("Y26", "42a5808fe6485400073a01", null),
        // Encrypted packets of a four-button remote. Y29 has a damaged check value, Y32 is Y27
        // with another device-information byte. Without decryption all of them just say
        // "encrypted".
        BthomeVector("Y27", "4590c30a24367381a0d7927ebf01e7226a7af5fd6b", encrypted(0x45, triggerBased = true)),
        BthomeVector("Y28", "4587794eea5f46236cc50a1f8f2ce7226a2c82c85c", encrypted(0x45, triggerBased = true)),
        BthomeVector("Y29", "4590c30a24367381a0d7927ebf01e7226a7af5fd00", encrypted(0x45, triggerBased = true)),
        BthomeVector("Y32", "4190c30a24367381a0d7927ebf01e7226a7af5fd6b", encrypted(0x41, triggerBased = false)),
    )

    val all = real + captured + specification + synthetic

    fun byId(id: String): BthomeVector = all.first { it.id == id }
}
