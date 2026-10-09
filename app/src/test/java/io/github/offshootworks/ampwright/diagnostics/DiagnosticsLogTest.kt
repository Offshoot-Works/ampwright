package io.github.offshootworks.ampwright.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsLogTest {

    @Test
    fun keepsOnlyTheNewestEvents() {
        val log = DiagnosticsLog(maxEvents = 3)
        repeat(5) { log.event("event $it") }
        assertEquals(listOf("event 2", "event 3", "event 4"), log.events().map { it.text })
    }

    @Test
    fun keepsOnlyTheNewestNotifications() {
        val log = DiagnosticsLog(maxNotifications = 2)
        log.notification(byteArrayOf(1))
        log.notification(byteArrayOf(2))
        log.notification(byteArrayOf(3))
        assertEquals(listOf("02", "03"), log.notifications().map { it.text })
    }

    @Test
    fun keepsTheLatestPayloadPerCommand() {
        val log = DiagnosticsLog()
        log.payload(0x11, intArrayOf(4, 0xE8))
        log.payload(0x10, intArrayOf(0x0A))
        log.payload(0x11, intArrayOf(2))
        assertEquals(mapOf(0x10 to "0A", 0x11 to "02"), log.payloads())
    }

    @Test
    fun formatsHexAsUnsignedUppercase() {
        assertEquals("EE AA 00 7F 80 FF", DiagnosticsLog.hex(byteArrayOf(0xEE.toByte(), 0xAA.toByte(), 0, 0x7F, 0x80.toByte(), -1)))
        assertEquals("0D 0A", DiagnosticsLog.hex(intArrayOf(0x0D, 0x0A)))
    }

    @Test
    fun masksAllButTheLastTwoAddressBytes() {
        assertEquals("XX:XX:XX:XX:3A:9F", DiagnosticsLog.maskAddress("C4:7F:51:02:3A:9F"))
    }

    @Test
    fun leavesNonMacAddressesAlone() {
        assertEquals("DEMO", DiagnosticsLog.maskAddress("DEMO"))
    }
}
