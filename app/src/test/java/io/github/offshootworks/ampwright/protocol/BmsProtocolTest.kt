package io.github.offshootworks.ampwright.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BmsProtocolTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    private fun hex(b: ByteArray) = b.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    /** Builds a response frame the way the BMS sends it. */
    private fun response(cmd: Int, payload: IntArray): ByteArray {
        val chk = BmsProtocol.checksum(cmd, payload)
        return bytes(0xEE, 0xAA, 0xA5, cmd, payload.size, *payload, chk and 0xFF, chk shr 8, 0x0D, 0x0A)
    }

    @Test
    fun readRequestMatchesOriginalApp() {
        // Same bytes as ReadCombinecodeV1with1((byte) 16) in the original globaldata.java.
        assertEquals("EE AA A5 10 00 F0 FF 0D 0A", hex(BmsProtocol.readRequest(0x10)))
        assertEquals("EE AA A5 11 00 EF FF 0D 0A", hex(BmsProtocol.readRequest(0x11)))
        assertEquals("EE AA A5 12 00 EE FF 0D 0A", hex(BmsProtocol.readRequest(0x12)))
    }

    @Test
    fun mosCommandsMatchOriginalApp() {
        // WriteCombinecodeV1with1((byte) 48, 65281): sum = 0x30 + 2 + 0x01 + 0xFF = 306 -> 0xFECE.
        assertEquals("EE AA 5A 30 02 01 FF CE FE 0D 0A", hex(BmsProtocol.writeRequest(0x30, MosCommand.ChargeOn.value)))
        assertEquals("EE AA 5A 30 02 00 FF CF FE 0D 0A", hex(BmsProtocol.writeRequest(0x30, MosCommand.ChargeOff.value)))
        assertEquals("EE AA 5A 30 02 FF 01 CE FE 0D 0A", hex(BmsProtocol.writeRequest(0x30, MosCommand.DischargeOn.value)))
        assertEquals("EE AA 5A 30 02 FF 00 CF FE 0D 0A", hex(BmsProtocol.writeRequest(0x30, MosCommand.DischargeOff.value)))
    }

    @Test
    fun assemblerHandlesSplitFramesAndNoise() {
        val frame = response(0x11, intArrayOf(2, 0xE4, 0x0C, 0xEE, 0x0C))
        val stream = bytes(0x00, 0x13, 0xEE) + frame + response(0x12, intArrayOf(0))
        val assembler = FrameAssembler()
        val out = stream.toList().chunked(5).flatMap { assembler.feed(it.toByteArray()) }
        assertEquals(listOf(0x11, 0x12), out.map { it.cmd })
        assertEquals(listOf(2, 0xE4, 0x0C, 0xEE, 0x0C), out[0].payload.toList())
    }

    @Test
    fun assemblerUsesLengthNotTerminatorSearch() {
        // Payload contains 0D 0A, which broke the original app's string search.
        val frame = response(0x11, intArrayOf(1, 0x0D, 0x0A))
        val out = FrameAssembler().feed(frame)
        assertEquals(1, out.size)
        assertEquals(listOf(1, 0x0D, 0x0A), out[0].payload.toList())
    }

    @Test
    fun assemblerDropsCorruptFrameAndRecovers() {
        val bad = response(0x10, IntArray(27)).also { it[8] = 0x55 }
        val good = response(0x12, intArrayOf(0))
        val out = FrameAssembler().feed(bad + good)
        assertEquals(listOf(0x12), out.map { it.cmd })
    }

    @Test
    fun parsesBasicInfo() {
        val p = IntArray(27)
        fun put16(i: Int, v: Int) { p[i] = v and 0xFF; p[i + 1] = (v shr 8) and 0xFF }
        put16(0, 1328) // 13.28 V
        put16(2, 0x10000 - 845) // -8.45 A
        p[4] = 72
        put16(5, 137)
        p[7] = 1 // discharging
        put16(8, 10000) // 100 Ah
        put16(10, 7240) // 72.4 Ah
        put16(12, 1 shl 6) // short circuit
        put16(14, 1 shl 9) // NTC fault
        p[16] = 0x80 // low battery alarm
        p[18] = 0x01 // alarm bit 16: hot while charging
        p[20] = 0x05 // balancing cells 1 and 3
        p[24] = 0x12
        p[25] = 0x02 // discharge FET only
        p[26] = 96

        val b = BmsParser.parseBasic(p)!!
        assertEquals(13_280, b.packVoltageMv)
        assertEquals(-8_450, b.currentMa)
        assertEquals(72, b.socPercent)
        assertEquals(137, b.cycles)
        assertEquals(ChargeState.Discharging, b.chargeState)
        assertEquals(100_000, b.fullCapacityMah)
        assertEquals(72_400, b.remainingCapacityMah)
        assertEquals("V1.2", b.firmwareLabel)
        assertFalse(b.chargeFetOn)
        assertTrue(b.dischargeFetOn)
        assertEquals(96, b.sohPercent)
        assertTrue(b.isCellBalancing(0))
        assertFalse(b.isCellBalancing(1))
        assertTrue(b.isCellBalancing(2))

        val active = StatusFlags.active(b).map { it.title }
        assertEquals(
            listOf("Short circuit", "Temperature sensor fault", "Low battery", "Hot while charging"),
            active,
        )
    }

    @Test
    fun basicInfoWithoutSohIsAccepted() {
        assertNull(BmsParser.parseBasic(IntArray(26))!!.sohPercent)
        assertNull(BmsParser.parseBasic(IntArray(25)))
    }

    @Test
    fun currentIsScaledByFetByte() {
        // Raw -100 (x10 mA). FET bit 2 doubles it, bit 3 quadruples it, as in the Play Store app.
        fun currentWithFet(fet: Int): Int {
            val p = IntArray(27)
            p[2] = 0x9C; p[3] = 0xFF
            p[25] = fet
            return BmsParser.parseBasic(p)!!.currentMa
        }
        assertEquals(-1_000, currentWithFet(0x03))
        assertEquals(-2_000, currentWithFet(0x07))
        assertEquals(-4_000, currentWithFet(0x0B))
        assertEquals(-4_000, currentWithFet(0x0F))
    }

    @Test
    fun parsesNewerFirmwareFields() {
        val p = IntArray(67)
        p[24] = 0x12
        p[25] = 0x13 // both FETs on, heater on
        p[29] = 0x39; p[30] = 0x30 // BMS ID 12345
        p[33] = 0x10; p[34] = 0x0E // running 3600 s
        p[37] = 0x78 // heater 120 x 10 mA
        p[41] = 0x04 // fault bit 2
        p[45] = 0x02; p[46] = 0x03 // app version 2.3, big-endian
        "LTW-48V-100".forEachIndexed { i, c -> p[47 + i] = c.code }

        val b = BmsParser.parseBasic(p)!!
        assertTrue(b.heaterOn)
        assertEquals(1_200, b.heaterCurrentMa)
        assertEquals(12_345L, b.bmsId)
        assertEquals(3_600L, b.runtimeSeconds)
        assertEquals(4, b.faultFlags)
        assertEquals("V2.3", b.firmwareLabel)
        assertEquals("LTW-48V-100", b.deviceId)
    }

    @Test
    fun newerFieldsAreAbsentOnOlderFirmware() {
        val b = BmsParser.parseBasic(IntArray(27).also { it[24] = 0x12 })!!
        assertFalse(b.heaterOn)
        assertNull(b.bmsId)
        assertNull(b.runtimeSeconds)
        assertNull(b.faultFlags)
        assertNull(b.deviceId)
        assertEquals("V1.2", b.firmwareLabel)

        // Blank IDs and a zero app version fall back the same way.
        val blank = BmsParser.parseBasic(IntArray(67).also { it[24] = 0x12; it[29] = 0xFF; it[30] = 0xFF })!!
        assertNull(blank.bmsId)
        assertNull(blank.deviceId)
        assertEquals("V1.2", blank.firmwareLabel)
    }

    @Test
    fun parsesCellsAndTemperatures() {
        val cells = BmsParser.parseCells(intArrayOf(3, 0xE4, 0x0C, 0xEE, 0x0C, 0xDA, 0x0C))!!
        assertEquals(listOf(3300, 3310, 3290), cells.cellMv)
        assertEquals(20, cells.deltaMv)
        assertEquals(1, cells.maxIndex)
        assertEquals(2, cells.minIndex)

        // 2981 = 25.0 C, 2731 = 0.0 C, 2631 = -10.0 C (0.1 K units)
        val temps = BmsParser.parseTemps(intArrayOf(2, 0xA5, 0x0B, 0xAB, 0x0A, 0x47, 0x0A, 0xA5, 0x0B))!!
        assertEquals(listOf(25.0, 0.0), temps.sensorsC)
        assertEquals(-10.0, temps.mosfetC!!, 0.001)
        assertEquals(25.0, temps.ambientC!!, 0.001)
    }

    @Test
    fun cellCountIsClampedToPayload() {
        // Claims 8 cells but only carries 1.
        assertEquals(listOf(3300), BmsParser.parseCells(intArrayOf(8, 0xE4, 0x0C))!!.cellMv)
    }
}
