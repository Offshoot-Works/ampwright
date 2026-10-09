package io.github.offshootworks.ampwright.bms

import java.util.UUID

/**
 * A Bluetooth module an LTW BMS can be fitted with. Every module carries the same EE AA frames;
 * only the GATT service and characteristics differ. The list matches the modules the current LTW
 * app (Play Store build) recognises.
 */
class GattProfile(
    val name: String,
    val service: UUID,
    val write: UUID,
    val notify: UUID,
    /** The original LTW module is paired before use; generic modules often can't pair at all. */
    val needsBond: Boolean = false,
) {
    companion object {
        /** Checked in order; the first profile whose service has both characteristics is used. */
        val all = listOf(
            GattProfile("LTW", uuid16(0xFE60), uuid16(0xFE61), uuid16(0xFE62), needsBond = true),
            GattProfile(
                "Nordic UART",
                UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
                UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e"),
                UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e"),
            ),
            GattProfile(
                "2760",
                UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e1001"),
                UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e0001"),
                UUID.fromString("00002760-08c2-11e1-9073-0e8ac72e0002"),
            ),
            GattProfile("FEE7", uuid16(0xFEE7), uuid16(0xFEC1), uuid16(0xFEC1)),
            GattProfile("FEE7 36F5", uuid16(0xFEE7), uuid16(0x36F5), uuid16(0x36F6)),
            GattProfile("FFE0", uuid16(0xFFE0), uuid16(0xFFE1), uuid16(0xFFE1)),
            GattProfile(
                "Telink",
                UUID.fromString("00010203-0405-0607-0809-0a0b0c0dffe0"),
                UUID.fromString("00010203-0405-0607-0809-0a0b0c0dffe2"),
                UUID.fromString("00010203-0405-0607-0809-0a0b0c0dffe1"),
            ),
            GattProfile("AE30", uuid16(0xAE30), uuid16(0xAE01), uuid16(0xAE02)),
            GattProfile("FFF0", uuid16(0xFFF0), uuid16(0xFFF2), uuid16(0xFFF1)),
        )

        private fun uuid16(id: Int): UUID = UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(id))
    }
}
