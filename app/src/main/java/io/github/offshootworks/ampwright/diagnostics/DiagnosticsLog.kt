package io.github.offshootworks.ampwright.diagnostics

/**
 * In-memory record of what happened on the Bluetooth link this session, for the diagnostics report
 * a user can share when something doesn't work. Nothing leaves the phone unless they share it.
 *
 * Written from the main thread and from GATT binder threads, so every access is synchronised.
 */
class DiagnosticsLog(
    private val maxEvents: Int = 150,
    private val maxNotifications: Int = 40,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    class Entry(val timeMillis: Long, val text: String)

    private val events = ArrayDeque<Entry>()
    private val notifications = ArrayDeque<Entry>()
    private val payloads = sortedMapOf<Int, String>()
    private var layout: List<String> = emptyList()
    private var module: String? = null

    @Synchronized
    fun event(text: String) = add(events, Entry(clock(), text), maxEvents)

    /** A raw BLE notification, before frames are reassembled. */
    @Synchronized
    fun notification(bytes: ByteArray) = add(notifications, Entry(clock(), hex(bytes)), maxNotifications)

    /** The latest decoded payload for [cmd], kept so every field the BMS sends can be inspected. */
    @Synchronized
    fun payload(cmd: Int, payload: IntArray) {
        payloads[cmd] = hex(payload)
    }

    /** The services and characteristics found on the last discovery, and the profile that matched. */
    @Synchronized
    fun gatt(layout: List<String>, module: String?) {
        this.layout = layout
        this.module = module
    }

    @Synchronized fun events(): List<Entry> = events.toList()
    @Synchronized fun notifications(): List<Entry> = notifications.toList()
    @Synchronized fun payloads(): Map<Int, String> = payloads.toMap()
    @Synchronized fun gattLayout(): List<String> = layout
    @Synchronized fun module(): String? = module

    private fun add(list: ArrayDeque<Entry>, entry: Entry, max: Int) {
        list.addLast(entry)
        while (list.size > max) list.removeFirst()
    }

    companion object {
        /** The app-wide log, shared by the BLE client, the scanner and the report. */
        val shared = DiagnosticsLog()

        private const val DIGITS = "0123456789ABCDEF"

        // Built by hand rather than with String.format, which can localise digits.
        fun hex(byte: Int): String = "${DIGITS[(byte shr 4) and 0xF]}${DIGITS[byte and 0xF]}"

        fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { hex(it.toInt() and 0xFF) }

        fun hex(values: IntArray): String = values.joinToString(" ") { hex(it and 0xFF) }

        /** Keeps the last two bytes, so reports can be told apart without publishing the whole address. */
        fun maskAddress(address: String): String {
            val parts = address.split(':')
            if (parts.size != 6) return address
            return (List(4) { "XX" } + parts.takeLast(2)).joinToString(":")
        }
    }
}
