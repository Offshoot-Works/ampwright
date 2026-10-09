package io.github.offshootworks.ampwright.protocol

enum class FlagSeverity {
    /** The BMS is warning that a limit is being approached. */
    Warning,

    /** The BMS has tripped a protection and switched a MOSFET off. */
    Protection,
}

enum class FlagSource { Alarm, Protection, TempProtection }

class StatusFlag(
    val source: FlagSource,
    val bit: Int,
    val title: String,
    val description: String,
) {
    val severity get() = if (source == FlagSource.Alarm) FlagSeverity.Warning else FlagSeverity.Protection

    fun isSet(info: BasicInfo): Boolean {
        val word = when (source) {
            FlagSource.Alarm -> info.alarmFlags
            FlagSource.Protection -> info.protectionFlags
            FlagSource.TempProtection -> info.tempProtectionFlags
        }
        return (word ushr bit) and 1 == 1
    }
}

object StatusFlags {
    private fun alarm(bit: Int, title: String, description: String) =
        StatusFlag(FlagSource.Alarm, bit, title, description)

    private fun protect(bit: Int, title: String, description: String) =
        StatusFlag(FlagSource.Protection, bit, title, description)

    private fun tempProtect(bit: Int, title: String, description: String) =
        StatusFlag(FlagSource.TempProtection, bit, title, description)

    val warnings = listOf(
        alarm(0, "Cell high voltage", "A cell is close to its maximum voltage."),
        alarm(1, "Cell low voltage", "A cell is close to its minimum voltage."),
        alarm(2, "Pack high voltage", "Total pack voltage is close to its maximum."),
        alarm(3, "Pack low voltage", "Total pack voltage is close to its minimum."),
        alarm(4, "High charge current", "Charge current is close to the limit."),
        alarm(5, "High discharge current", "Discharge current is close to the limit."),
        alarm(6, "Cell imbalance", "The voltage spread between cells is larger than normal."),
        alarm(7, "Low battery", "Remaining capacity is low."),
        alarm(16, "Hot while charging", "Cell temperature is high for charging."),
        alarm(17, "Cold while charging", "Cell temperature is low for charging."),
        alarm(18, "Hot while discharging", "Cell temperature is high for discharging."),
        alarm(19, "Cold while discharging", "Cell temperature is low for discharging."),
        alarm(20, "Ambient hot (charging)", "Surrounding air is hot for charging."),
        alarm(21, "Ambient cold (charging)", "Surrounding air is cold for charging."),
        alarm(22, "Ambient hot (discharging)", "Surrounding air is hot for discharging."),
        alarm(23, "Ambient cold (discharging)", "Surrounding air is cold for discharging."),
        alarm(24, "MOSFET hot", "The BMS power switches are running hot."),
    )

    val protections = listOf(
        protect(0, "Cell over-voltage", "Charging stopped: a cell exceeded its maximum voltage."),
        protect(1, "Cell under-voltage", "Discharging stopped: a cell fell below its minimum voltage."),
        protect(2, "Pack over-voltage", "Charging stopped: pack voltage too high."),
        protect(3, "Pack under-voltage", "Discharging stopped: pack voltage too low."),
        protect(4, "Charge over-current", "Charging stopped: charge current too high."),
        protect(5, "Discharge over-current", "Discharging stopped: load current too high."),
        protect(6, "Short circuit", "Output switched off after a short circuit was detected."),
        protect(7, "Open wire", "A cell sense wire appears to be disconnected."),
        protect(8, "Cell imbalance", "Stopped because the cell voltage spread is too large."),
        protect(9, "Short circuit lockout", "Repeated short circuits: output locked off."),
        protect(10, "Reverse connection", "The charger or load is connected with reversed polarity."),
        protect(11, "Charge timeout", "Charging took longer than the allowed time."),
        protect(12, "Over-current lockout", "Repeated over-current events: discharge locked off."),
        protect(13, "AFE failure", "The BMS measurement chip reported a fault."),
        protect(14, "Charge MOSFET failure", "The charge switch is not behaving as commanded."),
        protect(15, "Discharge MOSFET failure", "The discharge switch is not behaving as commanded."),
        tempProtect(0, "MOSFET over-temperature", "Power switches too hot: output cut."),
        tempProtect(1, "Charge over-temperature", "Charging stopped: cells too hot."),
        tempProtect(2, "Charge under-temperature", "Charging stopped: cells too cold."),
        tempProtect(3, "Discharge over-temperature", "Discharging stopped: cells too hot."),
        tempProtect(4, "Discharge under-temperature", "Discharging stopped: cells too cold."),
        tempProtect(5, "Ambient over-temp (charging)", "Charging stopped: surroundings too hot."),
        tempProtect(6, "Ambient under-temp (charging)", "Charging stopped: surroundings too cold."),
        tempProtect(7, "Ambient over-temp (discharging)", "Discharging stopped: surroundings too hot."),
        tempProtect(8, "Ambient under-temp (discharging)", "Discharging stopped: surroundings too cold."),
        tempProtect(9, "Temperature sensor fault", "An NTC sensor is open or shorted."),
    )

    fun active(info: BasicInfo?): List<StatusFlag> =
        if (info == null) emptyList() else (protections + warnings).filter { it.isSet(info) }
}
