package io.github.offshootworks.ampwright.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.offshootworks.ampwright.protocol.BasicInfo
import io.github.offshootworks.ampwright.protocol.ChargeState
import io.github.offshootworks.ampwright.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.math.roundToInt

object Format {
    fun packVolts(mv: Int) = "%.2f V".format(mv / 1000.0)
    fun cellVolts(mv: Int) = "%.3f V".format(mv / 1000.0)
    fun amps(ma: Int) = "%.2f A".format(abs(ma) / 1000.0)
    fun ampHours(mah: Int) = "%.1f Ah".format(mah / 1000.0)
    fun celsius(c: Double) = "%.1f °C".format(c)
    fun signedMv(mv: Int) = if (mv > 0) "+$mv mV" else "$mv mV"

    fun watts(w: Double) = if (w < 10) "%.1f W".format(w) else "${w.roundToInt()} W"

    fun duration(hours: Double): String {
        val totalMin = (hours * 60).roundToInt()
        return when {
            totalMin < 1 -> "under a minute"
            totalMin < 60 -> "$totalMin min"
            totalMin < 48 * 60 -> "${totalMin / 60} h ${totalMin % 60} min"
            else -> "${(hours / 24).roundToInt()} days"
        }
    }

    fun ago(millis: Long): String {
        val s = (millis / 1000).coerceAtLeast(0)
        return when {
            s < 3 -> "just now"
            s < 60 -> "${s}s ago"
            else -> "${s / 60} min ago"
        }
    }
}

/** Direction from the BMS's own status byte, falling back to the sign of the current. */
fun BasicInfo.direction(): ChargeState = when {
    chargeState != ChargeState.Unknown -> chargeState
    currentMa > 0 -> ChargeState.Charging
    currentMa < 0 -> ChargeState.Discharging
    else -> ChargeState.Idle
}

/** "Full in 2 h 10 min" / "Empty in 9 h 3 min", or null when the current is too small to estimate. */
fun BasicInfo.timeEstimate(): String? {
    val current = abs(currentMa)
    if (current < 200) return null
    return when (direction()) {
        ChargeState.Charging -> {
            val toFull = fullCapacityMah - remainingCapacityMah
            if (toFull <= 0) null else "Full in ${Format.duration(toFull.toDouble() / current)}"
        }
        ChargeState.Discharging -> "Empty in ${Format.duration(remainingCapacityMah.toDouble() / current)}"
        else -> null
    }
}

@Composable
fun socColor(percent: Int): Color = when {
    percent <= 15 -> AppTheme.status.bad
    percent <= 30 -> AppTheme.status.warn
    else -> AppTheme.status.good
}

@Composable
fun temperatureColor(c: Double): Color = when {
    c >= 60 -> AppTheme.status.bad
    c >= 45 -> AppTheme.status.warn
    c <= 0 -> AppTheme.status.cold
    else -> AppTheme.status.good
}

fun temperatureLabel(c: Double) = when {
    c >= 60 -> "Hot"
    c >= 45 -> "Warm"
    c <= 0 -> "Cold"
    else -> "Normal"
}

@Composable
fun spreadColor(mv: Int): Color = when {
    mv >= 100 -> AppTheme.status.bad
    mv >= 40 -> AppTheme.status.warn
    else -> AppTheme.status.good
}
