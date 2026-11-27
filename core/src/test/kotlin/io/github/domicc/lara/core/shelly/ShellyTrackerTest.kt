package io.github.domicc.lara.core.shelly

import io.github.domicc.lara.core.bthome.BthomePacket
import io.github.domicc.lara.core.bthome.BthomeParser
import io.github.domicc.lara.core.bthome.BthomeVectors
import io.github.domicc.lara.core.bthome.ButtonEvent
import io.github.domicc.lara.core.input.Gesture
import io.github.domicc.lara.core.input.Input
import io.github.domicc.lara.core.input.InputPath
import io.github.domicc.lara.core.input.InputSource
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Q1 to Q9 are the sequence tests of the BTHome verification report (see [BthomeVectors]). */
class ShellyTrackerTest {

    private val tracker = ShellyTracker()
    private val window = ShellyTracker.DEFAULT_WINDOW_MS

    private fun vector(id: String): BthomePacket = assertNotNull(BthomeParser.parse(BthomeVectors.byId(id).bytes))

    /** A packet as the four-button remote sends it; [codes] are the event codes of buttons 1 to 4. */
    private fun remotePacket(packetId: Int, vararg codes: Int): BthomePacket {
        val bytes = ArrayList<Byte>(listOf(0x44, 0x00, packetId.toByte(), 0x01, 0x64))
        for (button in 0 until 4) {
            bytes += 0x3A
            bytes += (codes.getOrNull(button) ?: 0).toByte()
        }
        return assertNotNull(BthomeParser.parse(bytes.toByteArray()))
    }

    /** Button 1 pressed once. */
    private fun press(packetId: Int): BthomePacket = remotePacket(packetId, 0x01)

    private fun feed(packet: BthomePacket, atMs: Long, address: String = REMOTE): ShellyEvent? =
        tracker.onPacket(address, packet, atMs).event

    @Test
    fun `Q1 twenty repeats of one packet are one event`() {
        val events = (0 until 20).mapNotNull { feed(vector("R1"), 1_000 + it * 75L) }
        assertEquals(
            listOf(ShellyEvent(REMOTE, 14, listOf(ShellyPress(2, Gesture.PRESS)), IdStep.UNKNOWN, 0, 1_000)),
            events,
        )
    }

    @Test
    fun `the burst statistics follow when the burst is over`() {
        val event = feed(vector("R2"), 1_000)
        for (i in 1 until 50) assertNull(feed(vector("R2"), 1_000 + i * 20L))
        val lastPacket = 1_000 + 49 * 20L
        assertTrue(tracker.takeFinished(lastPacket + window - 1).isEmpty())
        assertEquals(listOf(ShellyBurst(assertNotNull(event), packets = 50, spanMs = 980)), tracker.takeFinished(lastPacket + window))
        assertTrue(tracker.takeFinished(lastPacket + 10 * window).isEmpty(), "a burst is handed out once")
    }

    @Test
    fun `a throttled scan shows as few packets in a short span`() {
        // the scan heard 120 ms of the one-second burst
        for (i in 0 until 6) feed(press(7), 5_000 + i * 20L)
        val burst = tracker.takeFinished(60_000).single()
        assertEquals(6, burst.packets)
        assertEquals(100, burst.spanMs)
    }

    @Test
    fun `a single packet is a burst of one with no span`() {
        feed(press(7), 5_000)
        val event = ShellyEvent(REMOTE, 7, listOf(ShellyPress(1, Gesture.PRESS)), IdStep.UNKNOWN, 0, 5_000)
        assertEquals(ShellyBurst(event, packets = 1, spanMs = 0), tracker.takeFinished(9_000).single())
    }

    @Test
    fun `a finished burst is also handed out by the next packet`() {
        val first = assertNotNull(feed(press(7), 1_000))
        feed(press(7), 1_500)
        val update = tracker.onPacket(REMOTE, press(8), 30_000)
        assertEquals(listOf(ShellyBurst(first, packets = 2, spanMs = 500)), update.finished)
        assertEquals(IdStep.NEXT, update.event?.idStep)
        assertTrue(tracker.takeFinished(30_000).isEmpty())
    }

    @Test
    fun `a repeat inside the window is a duplicate and one at its end is a new event`() {
        assertNotNull(feed(press(14), 0))
        assertNull(feed(press(14), window - 1))
        // the window slides: it is measured from the last repeat
        assertNull(feed(press(14), 2 * window - 2))
        val again = assertNotNull(feed(press(14), 3 * window - 2))
        assertEquals(IdStep.SAME, again.idStep)
    }

    @Test
    fun `a held button that keeps sending stays one event`() {
        val events = (0..600).mapNotNull { feed(remotePacket(3, 0x80), it * 100L) }
        assertEquals(1, events.size)
        assertEquals(listOf(ShellyPress(1, Gesture.HOLD)), events.single().presses)
        val burst = tracker.takeFinished(60_000 + window).single()
        assertEquals(601, burst.packets)
        assertEquals(60_000, burst.spanMs)
    }

    @Test
    fun `Q4 the same id after a long pause is a new event marked as the same id`() {
        val first = assertNotNull(feed(vector("R1"), 0))
        val second = assertNotNull(feed(vector("R1"), 30_000))
        assertEquals(IdStep.UNKNOWN, first.idStep)
        assertEquals(IdStep.SAME, second.idStep)
        assertEquals(0, second.missedBefore)
        assertTrue(second.repeatsPrevious)
        assertFalse(first.repeatsPrevious)
    }

    @Test
    fun `duplicates are not remembered after the window`() {
        feed(press(14), 0)
        assertEquals(1, tracker.takeFinished(window).size)
        // the very same packet a moment later: nothing is left that could swallow it
        assertNotNull(feed(press(14), window + 1))
    }

    @Test
    fun `consecutive ids lose nothing`() {
        val steps = (10..15).map { assertNotNull(feed(press(it), it * 2_000L)) }
        assertEquals(listOf(IdStep.UNKNOWN) + List(5) { IdStep.NEXT }, steps.map { it.idStep })
        assertEquals(0, steps.sumOf { it.missedBefore })
    }

    @Test
    fun `Q2 the id wraps from 255 to 0 without a loss`() {
        feed(press(255), 0)
        val event = assertNotNull(feed(press(0), 3_000))
        assertEquals(IdStep.NEXT, event.idStep)
        assertEquals(0, event.missedBefore)
    }

    @Test
    fun `a jump of the id reports how many events were missed`() {
        feed(press(10), 0)
        val event = assertNotNull(feed(press(13), 5_000))
        assertEquals(IdStep.GAP, event.idStep)
        assertEquals(2, event.missedBefore)
    }

    @Test
    fun `a jump across the wrap is counted modulo 256`() {
        feed(press(254), 0)
        val event = assertNotNull(feed(press(1), 5_000))
        assertEquals(IdStep.GAP, event.idStep)
        assertEquals(2, event.missedBefore)
    }

    @Test
    fun `Q3 a counter that starts again is an event and not a loss`() {
        feed(press(200), 0)
        val event = assertNotNull(feed(press(0), 10_000))
        assertEquals(IdStep.RESTART, event.idStep)
        assertEquals(0, event.missedBefore)
        assertEquals(listOf(ShellyPress(1, Gesture.PRESS)), event.presses)
        // counting goes on from the new value
        assertEquals(IdStep.NEXT, feed(press(1), 20_000)?.idStep)
    }

    @Test
    fun `a backwards step is a restart`() {
        feed(press(10), 0)
        val steps = listOf(feed(press(9), 10_000), feed(press(3), 20_000)).map { assertNotNull(it) }
        assertEquals(listOf(IdStep.RESTART, IdStep.RESTART), steps.map { it.idStep })
        assertEquals(0, steps.sumOf { it.missedBefore })
    }

    @Test
    fun `the largest believable gap is a parameter`() {
        val max = ShellyTracker.DEFAULT_MAX_GAP
        feed(press(0), 0)
        val believable = assertNotNull(feed(press(max + 1), 10_000))
        assertEquals(IdStep.GAP, believable.idStep)
        assertEquals(max, believable.missedBefore)
        val tooFar = assertNotNull(feed(press(2 * max + 3), 20_000))
        assertEquals(IdStep.RESTART, tooFar.idStep)
        assertEquals(0, tooFar.missedBefore)

        val strict = ShellyTracker(maxGap = 1)
        strict.onPacket(REMOTE, press(0), 0)
        assertEquals(IdStep.GAP, strict.onPacket(REMOTE, press(2), 10_000).event?.idStep)
        assertEquals(IdStep.RESTART, strict.onPacket(REMOTE, press(5), 20_000).event?.idStep)
    }

    @Test
    fun `the window is a parameter`() {
        val short = ShellyTracker(windowMs = 500)
        assertNotNull(short.onPacket(REMOTE, press(1), 0).event)
        assertNull(short.onPacket(REMOTE, press(1), 499).event)
        assertNotNull(short.onPacket(REMOTE, press(1), 999).event)
        assertEquals(500, short.windowMs)
    }

    @Test
    fun `two buttons in one packet are one event with two presses`() {
        val event = assertNotNull(feed(vector("Y13"), 1_000))
        assertEquals(listOf(ShellyPress(1, Gesture.PRESS), ShellyPress(3, Gesture.PRESS)), event.presses)
        assertEquals(
            listOf(
                Input(InputSource.SHELLY, 1_000, button = 1, gesture = Gesture.PRESS, path = InputPath.BLE_CALLBACK),
                Input(InputSource.SHELLY, 1_000, button = 3, gesture = Gesture.PRESS, path = InputPath.BLE_CALLBACK),
            ),
            event.toInputs(InputPath.BLE_CALLBACK),
        )
    }

    @Test
    fun `a loss is reported with the first input of an event only`() {
        feed(press(10), 0)
        // packet id 13 after 10: two events lost; buttons 1 and 3 pressed together
        val event = assertNotNull(feed(remotePacket(13, 0x01, 0x00, 0x01), 10_000))
        assertEquals(2, event.missedBefore)
        assertEquals(listOf(2, 0), event.toInputs(InputPath.BLE_RESCUE).map { it.missedBefore })
        assertEquals(listOf("ble_rescue", "ble_rescue"), event.toInputs(InputPath.BLE_RESCUE).map { it.origin })
    }

    @Test
    fun `every gesture of the remote is passed on`() {
        val expected = listOf(
            "Y1" to Gesture.PRESS,
            "Y2" to Gesture.DOUBLE,
            "Y3" to Gesture.TRIPLE,
            "Y4" to Gesture.LONG,
            "Y5" to Gesture.LONG_DOUBLE,
            "Y6" to Gesture.LONG_TRIPLE,
            "Y7" to Gesture.HOLD,
            "Y8" to Gesture.HOLD_RELEASE,
            "Y9" to Gesture.HOLD,
            // a code the app does not know is still reported; the router refuses to act on it
            "Y10" to Gesture.UNKNOWN,
        )
        for ((id, gesture) in expected) {
            val event = assertNotNull(ShellyTracker().onPacket(REMOTE, vector(id), 0).event, id)
            assertEquals(listOf(ShellyPress(1, gesture)), event.presses, id)
        }
        assertEquals(listOf(ShellyPress(4, Gesture.LONG)), feed(vector("Y11"), 0)?.presses)
        assertEquals(listOf(ShellyPress(3, Gesture.DOUBLE)), feed(vector("Y12"), 10_000)?.presses)
    }

    @Test
    fun `a packet without a press is an event without presses and keeps the count of ids right`() {
        feed(remotePacket(17, 0x01), 0)
        // Y14: all four buttons "none", packet id 18
        val idle = assertNotNull(feed(vector("Y14"), 10_000))
        assertTrue(idle.presses.isEmpty())
        assertTrue(idle.toInputs(InputPath.BLE_CALLBACK).isEmpty())
        assertEquals(IdStep.NEXT, idle.idStep)
        // Y15: beacon with packet id 19, repeated
        val beacon = assertNotNull(feed(vector("Y15"), 20_000))
        assertNull(feed(vector("Y15"), 20_150))
        assertTrue(beacon.presses.isEmpty())
        val next = assertNotNull(feed(remotePacket(20, 0x01), 30_000))
        assertEquals(IdStep.NEXT, next.idStep)
    }

    @Test
    fun `Q9 a beacon without packet id between repeats disturbs nothing`() {
        assertNotNull(feed(vector("R1"), 0))
        // Y16 has neither packet id nor press
        val beacon = assertNotNull(feed(vector("Y16"), 100))
        assertNull(beacon.packetId)
        assertEquals(IdStep.UNKNOWN, beacon.idStep)
        assertTrue(beacon.presses.isEmpty())
        // still a repeat of packet id 14
        assertNull(feed(vector("R1"), 200))
        // packet id 15 follows 14, not the beacon
        assertEquals(IdStep.NEXT, feed(remotePacket(15, 0, 0x01), 5_000)?.idStep)
    }

    @Test
    fun `presses without packet id are told apart by content`() {
        // S2: no packet id, button 2 pressed
        val first = assertNotNull(feed(vector("S2"), 0))
        assertEquals(listOf(ShellyPress(2, Gesture.PRESS)), first.presses)
        assertNull(first.packetId)
        assertEquals(IdStep.UNKNOWN, first.idStep)
        assertNull(feed(vector("S2"), 500))
        // other content inside the window is something new
        assertEquals(listOf(ShellyPress(1, Gesture.PRESS)), feed(vector("Y25"), 600)?.presses)
        // the same content after the window as well
        assertNotNull(feed(vector("S2"), 500 + window))
    }

    @Test
    fun `encrypted packets are ignored`() {
        feed(press(5), 0)
        val update = tracker.onPacket(REMOTE, vector("R4"), 100)
        assertNull(update.event)
        assertTrue(update.finished.isEmpty())
        assertEquals(IdStep.NEXT, feed(press(6), 5_000)?.idStep)
    }

    @Test
    fun `an encrypted packet still hands out finished bursts`() {
        val event = assertNotNull(feed(press(5), 0))
        assertEquals(listOf(ShellyBurst(event, 1, 0)), tracker.onPacket(REMOTE, vector("R4"), window).finished)
    }

    @Test
    fun `two remotes are tracked separately`() {
        assertNotNull(feed(press(5), 0, REMOTE))
        assertNotNull(feed(press(5), 10, OTHER_REMOTE))
        assertNull(feed(press(5), 20, REMOTE))
        assertNull(feed(press(5), 30, OTHER_REMOTE))
        val bursts = tracker.takeFinished(30 + window)
        assertEquals(listOf(REMOTE to 2, OTHER_REMOTE to 2), bursts.map { it.event.address to it.packets })
        assertEquals(IdStep.NEXT, feed(press(6), 5_000, REMOTE)?.idStep)
        assertEquals(IdStep.GAP, feed(press(9), 5_000, OTHER_REMOTE)?.idStep)
    }

    @Test
    fun `an older id that arrives late on a second path is still a duplicate`() {
        assertNotNull(feed(press(5), 0))
        assertNotNull(feed(press(6), 500))
        // the rescue scan delivers id 5 after the callback scan has delivered id 6 already
        assertNull(feed(press(5), 600))
        assertNull(feed(press(6), 700))
        val bursts = tracker.takeFinished(60_000)
        assertEquals(listOf(5 to 2, 6 to 2), bursts.map { it.event.packetId to it.packets })
        assertEquals(listOf(600L, 200L), bursts.map { it.spanMs })
        assertEquals(IdStep.NEXT, feed(press(7), 60_000)?.idStep)
    }

    @Test
    fun `packets that are not in order of time are still one burst`() {
        assertNotNull(feed(press(5), 1_000))
        assertNull(feed(press(5), 900))
        assertNull(feed(press(5), 1_100))
        val burst = tracker.takeFinished(1_100 + window).single()
        assertEquals(3, burst.packets)
        assertEquals(200, burst.spanMs)
        assertEquals(1_000, burst.event.elapsedMs)
    }

    @Test
    fun `a repeat that is delivered after its burst was closed starts nothing`() {
        assertNotNull(feed(press(5), 0))
        assertNull(feed(press(5), 900))
        // the next press arrives and closes the burst of id 5
        val closing = tracker.onPacket(REMOTE, press(6), 6_000)
        assertEquals(listOf(5), closing.finished.map { it.event.packetId })
        // now the rescue scan hands over what it received of id 5, seconds late
        val late = tracker.onPacket(REMOTE, press(5), 400)
        assertNull(late.event)
        assertTrue(late.late)
        assertTrue(tracker.onPacket(REMOTE, press(5), 950).late, "anything within the window of the burst's last packet")
        // and the count of ids is where it was: 7 follows 6, nothing was lost
        val next = assertNotNull(feed(press(7), 12_000))
        assertEquals(IdStep.NEXT, next.idStep)
        assertEquals(0, next.missedBefore)
    }

    @Test
    fun `a packet that is on time is never taken for a late one`() {
        val update = tracker.onPacket(REMOTE, press(5), 0)
        assertFalse(update.late)
        assertFalse(tracker.onPacket(REMOTE, press(5), 20).late, "a repeat of an open burst is in its statistics")
        tracker.takeFinished(window + 20)
        // the same id received a window after the burst is a new event, as before
        val again = tracker.onPacket(REMOTE, press(5), window + 20)
        assertFalse(again.late)
        assertEquals(IdStep.SAME, again.event?.idStep)
    }

    @Test
    fun `the id is back where it was after 256 events and still starts a new event`() {
        for (i in 0..256) assertNotNull(feed(press(i and 0xFF), i * 10_000L), "event $i")
    }

    @Test
    fun `a packet whose buttons could not be read does not hide the press that follows`() {
        // Y18: packet id 7, cut off inside the first button object
        val cut = tracker.onPacket(REMOTE, vector("Y18"), 1_000)
        assertNull(cut.event)
        assertFalse(cut.late)
        // the complete packet of the same burst is the press
        val event = assertNotNull(feed(press(7), 1_020))
        assertEquals(listOf(ShellyPress(1, Gesture.PRESS)), event.presses)
        // from now on the cut packet is one more repeat of that burst
        assertNull(feed(vector("Y18"), 1_040))
        // the cut packet that came first is in no burst
        assertEquals(2, tracker.takeFinished(60_000).single().packets)
    }

    @Test
    fun `an unreadable packet does not move the count of ids`() {
        feed(press(4), 0)
        // R10: packet id 13, then an object this app cannot step over, no button read
        assertNull(feed(vector("R10"), 10_000))
        val next = assertNotNull(feed(press(5), 20_000))
        assertEquals(IdStep.NEXT, next.idStep)
    }

    @Test
    fun `a press in front of an unknown object is still an event`() {
        // Y17: press of button 1, then unknown object 0xA9
        assertEquals(listOf(ShellyPress(1, Gesture.PRESS)), feed(vector("Y17"), 0)?.presses)
    }

    @Test
    fun `the last id of an earlier process makes a loss across the restart count`() {
        // the process that received id 40 is gone; ids 41 to 47 were sent meanwhile
        tracker.seed(REMOTE, 40)
        val event = assertNotNull(feed(press(48), 5_000))
        assertEquals(IdStep.GAP, event.idStep)
        assertEquals(7, event.missedBefore)
    }

    @Test
    fun `without a last id the first event of a process has nothing to compare with`() {
        val event = assertNotNull(feed(press(48), 5_000))
        assertEquals(IdStep.UNKNOWN, event.idStep)
        assertEquals(0, event.missedBefore)
    }

    @Test
    fun `after a restart the next id is no loss and the same id is a repeat`() {
        tracker.seed(REMOTE, 255)
        assertEquals(IdStep.NEXT, feed(press(0), 1_000)?.idStep)

        // the process died in the middle of a burst and is started again by a packet of it
        val other = ShellyTracker()
        other.seed(REMOTE, 12)
        val again = assertNotNull(other.onPacket(REMOTE, press(12), 1_000).event)
        assertTrue(again.repeatsPrevious)
    }

    @Test
    fun `a last id that comes too late changes nothing`() {
        feed(press(10), 0)
        tracker.seed(REMOTE, 3)
        assertEquals(IdStep.NEXT, feed(press(11), 5_000)?.idStep)
        // and it is kept per sender
        tracker.seed(OTHER_REMOTE, 3)
        assertEquals(IdStep.GAP, feed(press(5), 6_000, OTHER_REMOTE)?.idStep)
    }

    @Test
    fun `random streams are fully accounted for`() {
        val random = Random(1127)
        // without the packets whose buttons could not be read: whether one of them joins a
        // burst depends on what is open, which the tests above pin down
        val packets = BthomeVectors.all.mapNotNull { it.expected }
            .filterNot { it.stoppedEarly && it.buttons.all { button -> button == ButtonEvent.NONE } }
        repeat(200) {
            val tracker = ShellyTracker()
            var now = 0L
            var inBursts = 0
            var late = 0
            var plainPackets = 0
            repeat(300) {
                // mostly close together, sometimes a long pause, sometimes a step back in time
                now += when (random.nextInt(10)) {
                    0 -> random.nextLong(window, 10 * window)
                    1 -> -random.nextLong(0, 50)
                    else -> random.nextLong(0, 300)
                }
                val packet = if (random.nextBoolean()) packets.random(random) else press(random.nextInt(256))
                val update = tracker.onPacket(if (random.nextInt(4) == 0) OTHER_REMOTE else REMOTE, packet, now)
                if (!packet.encrypted) plainPackets++
                update.event?.let { event ->
                    assertFalse(update.late)
                    assertEquals(event.idStep == IdStep.GAP, event.missedBefore > 0)
                    assertTrue(event.missedBefore <= ShellyTracker.DEFAULT_MAX_GAP)
                    assertTrue(event.presses.all { it.button in 1..4 })
                }
                if (update.late) late++
                inBursts += update.finished.sumOf { it.packets }
                assertTrue(update.finished.all { it.spanMs >= 0 && it.packets >= 1 })
            }
            inBursts += tracker.takeFinished(Long.MAX_VALUE).sumOf { it.packets }
            assertTrue(tracker.takeFinished(Long.MAX_VALUE).isEmpty())
            // every packet belongs to exactly one burst or is a late repeat of a closed one
            assertEquals(plainPackets, inBursts + late)
        }
    }

    private companion object {
        const val REMOTE = "AA:BB:CC:DD:EE:FF"
        const val OTHER_REMOTE = "AA:BB:CC:DD:EE:01"
    }
}
