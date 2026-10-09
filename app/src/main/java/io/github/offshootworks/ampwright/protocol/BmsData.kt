package io.github.offshootworks.ampwright.protocol

import kotlin.math.abs

enum class ChargeState { Idle, Discharging, Charging, Unknown }

/** Response to command 0x10. */
data class BasicInfo(
    val packVoltageMv: Int,
    /** Signed; the BMS reports in 10 mA steps. */
    val currentMa: Int,
    val socPercent: Int,
    val cycles: Int,
    val chargeState: ChargeState,
    val fullCapacityMah: Int,
    val remainingCapacityMah: Int,
    val protectionFlags: Int,
    val tempProtectionFlags: Int,
    val alarmFlags: Int,
    val balanceFlags: Int,
    val firmwareVersion: Int,
    val chargeFetOn: Boolean,
    val dischargeFetOn: Boolean,
    val sohPercent: Int?,
    // The fields below are only sent by newer firmware (payloads longer than 27 bytes).
    val heaterOn: Boolean = false,
    val heaterCurrentMa: Int? = null,
    val bmsId: Long? = null,
    val runtimeSeconds: Long? = null,
    val faultFlags: Int? = null,
    /** Big-endian major.minor, e.g. 0x0203 -> "V2.3". */
    val appVersion: Int? = null,
    val deviceId: String? = null,
) {
    val powerW: Double get() = packVoltageMv / 1000.0 * abs(currentMa) / 1000.0

    /**
     * The newer two-byte version when the BMS sends one, otherwise the firmware byte rendered the
     * way the original app did: 0x12 -> "V1.2", 0x5 -> "V0.5".
     */
    val firmwareLabel: String
        get() {
            appVersion?.let { return "V${it shr 8}.${it and 0xFF}" }
            val hex = Integer.toHexString(firmwareVersion and 0xFF)
            return if (hex.length < 2) "V0.$hex" else "V${hex[0]}.${hex[1]}"
        }

    fun isCellBalancing(index: Int) = index in 0..31 && (balanceFlags ushr index) and 1 == 1
}

/** Response to command 0x11. */
data class CellInfo(val cellMv: List<Int>) {
    val count get() = cellMv.size
    val maxMv get() = cellMv.maxOrNull() ?: 0
    val minMv get() = cellMv.minOrNull() ?: 0
    val maxIndex get() = cellMv.indices.maxByOrNull { cellMv[it] } ?: -1
    val minIndex get() = cellMv.indices.minByOrNull { cellMv[it] } ?: -1
    val deltaMv get() = if (cellMv.isEmpty()) 0 else maxMv - minMv
    val averageMv get() = if (cellMv.isEmpty()) 0 else cellMv.sum() / cellMv.size
}

/** Response to command 0x12. Temperatures in degrees Celsius. */
data class TempInfo(
    val sensorsC: List<Double>,
    val mosfetC: Double?,
    val ambientC: Double?,
) {
    val averageSensorC: Double? get() = sensorsC.takeIf { it.isNotEmpty() }?.average()
    val maxC: Double? get() = (sensorsC + listOfNotNull(mosfetC, ambientC)).maxOrNull()
}

object BmsParser {
    fun parseBasic(p: IntArray): BasicInfo? {
        if (p.size < 26) return null
        val rawCurrent = p.u16(2)
        val fet = p[25]
        // High-current packs report current in coarser steps and flag the scale in the FET byte.
        val currentScale = when {
            fet and 0x08 != 0 -> 4
            fet and 0x04 != 0 -> 2
            else -> 1
        }
        return BasicInfo(
            packVoltageMv = p.u16(0) * 10,
            currentMa = (if (rawCurrent >= 0x8000) rawCurrent - 0x10000 else rawCurrent) * 10 * currentScale,
            socPercent = p[4],
            cycles = p.u16(5),
            chargeState = when (p[7]) {
                0 -> ChargeState.Idle
                1 -> ChargeState.Discharging
                2 -> ChargeState.Charging
                else -> ChargeState.Unknown
            },
            fullCapacityMah = p.u16(8) * 10,
            remainingCapacityMah = p.u16(10) * 10,
            protectionFlags = p.u16(12),
            tempProtectionFlags = p.u16(14),
            alarmFlags = p.u32(16),
            balanceFlags = p.u32(20),
            firmwareVersion = p[24],
            chargeFetOn = fet and 0x01 != 0,
            dischargeFetOn = fet and 0x02 != 0,
            sohPercent = p.getOrNull(26),
            heaterOn = fet and 0x10 != 0,
            // Bytes 27 and 28 are the multi-pack mode and BMS type, which only matter for linked packs.
            bmsId = p.u32OrNull(29)?.toLong()?.and(0xFFFFFFFFL)?.takeUnless { it == 0L || it == 0xFFFFL || it == 0xFFFFFFFFL },
            runtimeSeconds = p.u32OrNull(33)?.toLong()?.and(0xFFFFFFFFL),
            heaterCurrentMa = p.u16OrNull(37)?.let { it * 10 },
            // Bytes 39-40 are a battery status word with no documented meaning.
            faultFlags = p.u32OrNull(41),
            appVersion = if (p.size >= 47) ((p[45] shl 8) or p[46]).takeIf { it != 0 } else null,
            deviceId = p.asciiOrNull(47, 20),
        )
    }

    fun parseCells(p: IntArray): CellInfo? {
        if (p.isEmpty()) return null
        val count = minOf(p[0], (p.size - 1) / 2, 32)
        return CellInfo(List(count) { p.u16(1 + it * 2) })
    }

    fun parseTemps(p: IntArray): TempInfo? {
        if (p.isEmpty()) return null
        val count = minOf(p[0], (p.size - 1) / 2, 10)
        val sensors = List(count) { deciKelvinToC(p.u16(1 + it * 2)) }
        val next = 1 + count * 2
        return TempInfo(
            sensorsC = sensors,
            mosfetC = if (p.size >= next + 2) deciKelvinToC(p.u16(next)) else null,
            ambientC = if (p.size >= next + 4) deciKelvinToC(p.u16(next + 2)) else null,
        )
    }

    private fun deciKelvinToC(raw: Int) = (raw - 2731) / 10.0

    private fun IntArray.u16(i: Int) = this[i] or (this[i + 1] shl 8)
    private fun IntArray.u32(i: Int) = u16(i) or (u16(i + 2) shl 16)
    private fun IntArray.u16OrNull(i: Int) = if (size >= i + 2) u16(i) else null
    private fun IntArray.u32OrNull(i: Int) = if (size >= i + 4) u32(i) else null

    /** Printable ASCII up to the first NUL, or null if missing or blank. */
    private fun IntArray.asciiOrNull(start: Int, length: Int): String? {
        if (size < start + length) return null
        val chars = (start until start + length).map { this[it] }.takeWhile { it != 0 }
        if (chars.any { it !in 0x20..0x7E }) return null
        return chars.map { it.toChar() }.joinToString("").trim().ifEmpty { null }
    }
}
