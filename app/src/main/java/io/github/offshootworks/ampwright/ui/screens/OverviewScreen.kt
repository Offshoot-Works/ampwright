package io.github.offshootworks.ampwright.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Loop
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.offshootworks.ampwright.bms.BmsDevice
import io.github.offshootworks.ampwright.bms.BmsSnapshot
import io.github.offshootworks.ampwright.protocol.BasicInfo
import io.github.offshootworks.ampwright.protocol.ChargeState
import io.github.offshootworks.ampwright.protocol.FlagSeverity
import io.github.offshootworks.ampwright.protocol.StatusFlags
import io.github.offshootworks.ampwright.ui.Format
import io.github.offshootworks.ampwright.ui.components.MetricTile
import io.github.offshootworks.ampwright.ui.components.RingGauge
import io.github.offshootworks.ampwright.ui.components.SectionCard
import io.github.offshootworks.ampwright.ui.components.TileGrid
import io.github.offshootworks.ampwright.ui.direction
import io.github.offshootworks.ampwright.ui.socColor
import io.github.offshootworks.ampwright.ui.spreadColor
import io.github.offshootworks.ampwright.ui.temperatureColor
import io.github.offshootworks.ampwright.ui.theme.AppTheme
import io.github.offshootworks.ampwright.ui.timeEstimate
import kotlinx.coroutines.delay

private class Metric(val icon: ImageVector, val label: String, val value: String, val supporting: String?, val accent: Color?)

@Composable
fun OverviewScreen(
    snapshot: BmsSnapshot,
    device: BmsDevice,
    contentPadding: PaddingValues,
    onOpenAlerts: () -> Unit,
    onSetCharging: (Boolean) -> Unit,
    onSetDischarging: (Boolean) -> Unit,
) {
    val basic = snapshot.basic ?: return
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChargeHero(basic)
        AlertSummary(basic, onOpenAlerts)
        Metrics(snapshot, basic)
        PowerControls(basic, onSetCharging, onSetDischarging)
        BatteryDetails(basic, device)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ChargeHero(basic: BasicInfo) {
    val direction = basic.direction()
    val (stateIcon, stateText) = when (direction) {
        ChargeState.Charging -> Icons.Outlined.BatteryChargingFull to "Charging at ${Format.amps(basic.currentMa)}"
        ChargeState.Discharging -> Icons.Outlined.ElectricBolt to "Supplying ${Format.amps(basic.currentMa)}"
        else -> Icons.Outlined.Battery5Bar to "Idle"
    }
    SectionCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            RingGauge(
                fraction = basic.socPercent / 100f,
                color = socColor(basic.socPercent),
                modifier = Modifier.size(216.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${basic.socPercent}%",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        Format.ampHours(basic.remainingCapacityMah) + " left",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(stateIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stateText, style = MaterialTheme.typography.titleMedium)
            }
            basic.timeEstimate()?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AlertSummary(basic: BasicInfo, onOpenAlerts: () -> Unit) {
    val active = StatusFlags.active(basic)
    val protections = active.count { it.severity == FlagSeverity.Protection }
    val warnings = active.size - protections
    val (color, icon, title) = when {
        protections > 0 -> Triple(AppTheme.status.bad, Icons.Outlined.ErrorOutline,
            if (protections == 1) "Protection active: ${active.first().title}" else "$protections protections active")
        warnings > 0 -> Triple(AppTheme.status.warn, Icons.Outlined.WarningAmber,
            if (warnings == 1) "Warning: ${active.first().title}" else "$warnings warnings")
        else -> Triple(AppTheme.status.good, Icons.Outlined.CheckCircle, "No alarms or protections")
    }
    Card(
        onClick = onOpenAlerts,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open alerts")
        }
    }
}

@Composable
private fun Metrics(snapshot: BmsSnapshot, basic: BasicInfo) {
    val temps = snapshot.temps
    val cells = snapshot.cells
    val directionLabel = when (basic.direction()) {
        ChargeState.Charging -> "charging"
        ChargeState.Discharging -> "discharging"
        else -> "idle"
    }
    val metrics = buildList {
        add(Metric(Icons.Outlined.Bolt, "Voltage", Format.packVolts(basic.packVoltageMv), cells?.let { "${it.count} cells in series" }, null))
        add(Metric(Icons.Outlined.Speed, "Current", Format.amps(basic.currentMa), directionLabel, null))
        add(Metric(Icons.Outlined.ElectricBolt, "Power", Format.watts(basic.powerW), directionLabel, null))
        val avg = temps?.averageSensorC
        add(Metric(Icons.Outlined.Thermostat, "Temperature", avg?.let(Format::celsius) ?: "--",
            temps?.maxC?.let { "max ${Format.celsius(it)}" }, avg?.let { temperatureColor(temps.maxC ?: it) }))
        add(Metric(Icons.Outlined.Battery5Bar, "Capacity", Format.ampHours(basic.remainingCapacityMah),
            "of ${Format.ampHours(basic.fullCapacityMah)}", null))
        add(Metric(Icons.Outlined.ViewWeek, "Cell spread", cells?.let { "${it.deltaMv} mV" } ?: "--",
            cells?.let { "${Format.cellVolts(it.minMv)} – ${Format.cellVolts(it.maxMv)}" }, cells?.let { spreadColor(it.deltaMv) }))
        add(Metric(Icons.Outlined.Favorite, "Health", basic.sohPercent?.let { "$it%" } ?: "--", "state of health", null))
        add(Metric(Icons.Outlined.Loop, "Cycles", basic.cycles.toString(), "charge cycles", null))
    }
    TileGrid(metrics) { m, modifier ->
        MetricTile(
            icon = m.icon,
            label = m.label,
            value = m.value,
            supporting = m.supporting,
            accent = m.accent ?: MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )
    }
}

private enum class Switchable(val title: String, val onText: String, val offWarning: String) {
    Charge(
        "Charging",
        "The battery accepts charge from solar, alternator or mains.",
        "The battery will stop accepting charge until you switch charging back on.",
    ),
    Discharge(
        "Output",
        "The battery powers everything connected to it.",
        "Everything powered by this battery will lose power immediately, " +
            "including this phone's charger if it's on the same system.",
    ),
}

@Composable
private fun PowerControls(
    basic: BasicInfo,
    onSetCharging: (Boolean) -> Unit,
    onSetDischarging: (Boolean) -> Unit,
) {
    var confirmOff by remember { mutableStateOf<Switchable?>(null) }
    // Requested state while waiting for the BMS to report it back.
    var pendingCharge by remember { mutableStateOf<Boolean?>(null) }
    var pendingDischarge by remember { mutableStateOf<Boolean?>(null) }

    // Give up waiting after a few seconds; the switch then shows whatever the BMS reports.
    LaunchedEffect(pendingCharge) { if (pendingCharge != null) { delay(6_000); pendingCharge = null } }
    LaunchedEffect(pendingDischarge) { if (pendingDischarge != null) { delay(6_000); pendingDischarge = null } }
    val waitingCharge = pendingCharge?.takeIf { it != basic.chargeFetOn }
    val waitingDischarge = pendingDischarge?.takeIf { it != basic.dischargeFetOn }

    fun request(which: Switchable, on: Boolean) {
        when (which) {
            Switchable.Charge -> { pendingCharge = on; onSetCharging(on) }
            Switchable.Discharge -> { pendingDischarge = on; onSetDischarging(on) }
        }
    }

    SectionCard(title = "Power switches") {
        SwitchRow(Switchable.Charge, basic.chargeFetOn, waitingCharge) { on ->
            if (on) request(Switchable.Charge, true) else confirmOff = Switchable.Charge
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SwitchRow(Switchable.Discharge, basic.dischargeFetOn, waitingDischarge) { on ->
            if (on) request(Switchable.Discharge, true) else confirmOff = Switchable.Discharge
        }
    }

    confirmOff?.let { which ->
        AlertDialog(
            onDismissRequest = { confirmOff = null },
            icon = { Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null) },
            title = { Text("Turn off ${which.title.lowercase()}?") },
            text = { Text(which.offWarning) },
            confirmButton = {
                TextButton(onClick = {
                    request(which, false)
                    confirmOff = null
                }) { Text("Turn off") }
            },
            dismissButton = { TextButton(onClick = { confirmOff = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SwitchRow(which: Switchable, on: Boolean, pending: Boolean?, onChange: (Boolean) -> Unit) {
    val shown = pending ?: on
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = pending == null) { onChange(!shown) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(which.title, style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    pending != null -> "Sending to battery…"
                    on -> which.onText
                    else -> "Switched off"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (!on && pending == null) AppTheme.status.warn else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (pending != null) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
        }
        Switch(checked = shown, onCheckedChange = onChange, enabled = pending == null)
    }
}

@Composable
private fun BatteryDetails(basic: BasicInfo, device: BmsDevice) {
    SectionCard(title = "Battery details") {
        DetailRow("Bluetooth name", device.name)
        DetailRow("Address", device.address)
        DetailRow("BMS firmware", basic.firmwareLabel)
        DetailRow("Full charge capacity", Format.ampHours(basic.fullCapacityMah))
        // Only newer firmware sends these, so rows appear only when there is something to show.
        basic.bmsId?.let { DetailRow("BMS ID", it.toString()) }
        basic.deviceId?.let { DetailRow("Device ID", it) }
        basic.runtimeSeconds?.takeIf { it > 0 }?.let { DetailRow("Running time", Format.duration(it / 3600.0)) }
        if (basic.heaterOn) {
            DetailRow("Heater", basic.heaterCurrentMa?.takeIf { it > 0 }?.let { "On · ${Format.amps(it)}" } ?: "On")
        }
        basic.faultFlags?.takeIf { it != 0 }?.let { DetailRow("Fault code", "0x%08X".format(it)) }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
