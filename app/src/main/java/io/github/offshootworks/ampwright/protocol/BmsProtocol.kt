package io.github.offshootworks.ampwright.protocol

/**
 * Wire format used by the LTW BMS Bluetooth module (reverse-engineered from LTW BMS V1.1).
 *
 * ```
 * EE AA | dir | cmd | len | data[len] | chk_lo chk_hi | 0D 0A
 * ```
 * - `dir` is A5 for a read request and 5A for a write request.
 * - `chk` = 0x10000 - (cmd + len + sum(data)), little-endian.
 * - All multi-byte payload values are little-endian.
 */
object BmsProtocol {
    const val CMD_BASIC_INFO = 0x10
    const val CMD_CELL_VOLTAGES = 0x11
    const val CMD_TEMPERATURES = 0x12
    const val CMD_MOS_CONTROL = 0x30

    private const val HEADER_1 = 0xEE
    private const val HEADER_2 = 0xAA
    private const val DIR_READ = 0xA5
    private const val DIR_WRITE = 0x5A
    private const val TAIL_1 = 0x0D
    private const val TAIL_2 = 0x0A

    /** Bytes surrounding the payload: header(2) + dir + cmd + len + checksum(2) + tail(2). */
    const val FRAME_OVERHEAD = 9

    fun readRequest(cmd: Int): ByteArray = frame(DIR_READ, cmd, IntArray(0))

    fun writeRequest(cmd: Int, value: Int): ByteArray =
        frame(DIR_WRITE, cmd, intArrayOf(value and 0xFF, (value shr 8) and 0xFF))

    fun checksum(cmd: Int, payload: IntArray): Int {
        val sum = cmd + payload.size + payload.sum()
        return (0x10000 - (sum and 0xFFFF)) and 0xFFFF
    }

    private fun frame(dir: Int, cmd: Int, payload: IntArray): ByteArray {
        val chk = checksum(cmd, payload)
        val out = IntArray(payload.size + FRAME_OVERHEAD)
        out[0] = HEADER_1
        out[1] = HEADER_2
        out[2] = dir
        out[3] = cmd
        out[4] = payload.size
        payload.copyInto(out, 5)
        out[5 + payload.size] = chk and 0xFF
        out[6 + payload.size] = chk shr 8
        out[7 + payload.size] = TAIL_1
        out[8 + payload.size] = TAIL_2
        return ByteArray(out.size) { out[it].toByte() }
    }

    internal fun isHeader(b1: Int, b2: Int) = b1 == HEADER_1 && b2 == HEADER_2
    internal fun isTail(b1: Int, b2: Int) = b1 == TAIL_1 && b2 == TAIL_2
    internal const val HEADER_FIRST = HEADER_1
}

/** A validated response frame. [payload] holds unsigned byte values. */
class BmsFrame(val cmd: Int, val payload: IntArray)

/** The four switch commands understood by command 0x30. */
enum class MosCommand(val value: Int) {
    ChargeOn(0xFF01),
    ChargeOff(0xFF00),
    DischargeOn(0x01FF),
    DischargeOff(0x00FF),
}

/**
 * Reassembles response frames from BLE notifications, which can split or merge frames.
 * Frames are delimited using the length byte, so payloads that happen to contain 0D 0A are handled.
 */
class FrameAssembler(private val maxBuffered: Int = 2048) {
    private var buffer = IntArray(256)
    private var size = 0

    @Synchronized
    fun feed(bytes: ByteArray): List<BmsFrame> {
        if (size + bytes.size > maxBuffered) size = 0
        if (size + bytes.size > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + bytes.size))
        for (b in bytes) buffer[size++] = b.toInt() and 0xFF

        val frames = mutableListOf<BmsFrame>()
        var start = 0
        while (true) {
            start = findHeader(start)
            if (start < 0) {
                // Keep a trailing 0xEE in case the next notification starts with 0xAA.
                start = if (size > 0 && buffer[size - 1] == BmsProtocol.HEADER_FIRST) size - 1 else size
                break
            }
            if (size - start < 5) break
            val len = buffer[start + 4]
            val total = len + BmsProtocol.FRAME_OVERHEAD
            if (size - start < total) break

            val frame = tryDecode(start, len)
            if (frame != null) {
                frames += frame
                start += total
            } else {
                start += 1 // Corrupt frame or false header: resync on the next byte.
            }
        }
        discard(start)
        return frames
    }

    @Synchronized
    fun reset() {
        size = 0
    }

    private fun findHeader(from: Int): Int {
        for (i in from until size - 1) {
            if (BmsProtocol.isHeader(buffer[i], buffer[i + 1])) return i
        }
        return -1
    }

    private fun tryDecode(start: Int, len: Int): BmsFrame? {
        val cmd = buffer[start + 3]
        val payload = buffer.copyOfRange(start + 5, start + 5 + len)
        val chkPos = start + 5 + len
        val received = buffer[chkPos] or (buffer[chkPos + 1] shl 8)
        if (received != BmsProtocol.checksum(cmd, payload)) return null
        if (!BmsProtocol.isTail(buffer[chkPos + 2], buffer[chkPos + 3])) return null
        return BmsFrame(cmd, payload)
    }

    private fun discard(count: Int) {
        if (count <= 0) return
        buffer.copyInto(buffer, 0, count, size)
        size -= count
    }
}
