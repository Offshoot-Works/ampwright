package io.github.offshootworks.ampwright.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.offshootworks.ampwright.bms.BmsSnapshot
import io.github.offshootworks.ampwright.ui.Format
import io.github.offshootworks.ampwright.ui.components.EmptyState
import io.github.offshootworks.ampwright.ui.temperatureColor
import io.github.offshootworks.ampwright.ui.temperatureLabel

private class Sensor(val icon: ImageVector, val name: String, val description: String, val celsius: Double)

@Composable
fun TemperatureScreen(snapshot: BmsSnapshot, contentPadding: PaddingValues) {
    val temps = snapshot.temps
    if (temps == null) {
        Column(Modifier.padding(contentPadding)) {
            EmptyState(Icons.Outlined.HourglassEmpty, "Waiting for temperatures", "Readings appear after the next update.")
        }
        return
    }
    val sensors = buildList {
        temps.sensorsC.forEachIndexed { i, c ->
            add(Sensor(Icons.Outlined.BatteryStd, "Cell sensor ${i + 1}", "Probe on the cell pack", c))
        }
        temps.mosfetC?.let { add(Sensor(Icons.Outlined.DeveloperBoard, "BMS power switches", "MOSFET temperature", it)) }
        temps.ambientC?.let { add(Sensor(Icons.Outlined.Air, "Ambient", "Air around the BMS", it)) }
    }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        sensors.forEach { SensorCard(it) }
        Text(
            "Colours: blue at or below 0 °C, amber from 45 °C, red from 60 °C. " +
                "The battery's own protection limits are set inside the BMS.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SensorCard(sensor: Sensor) {
    val color = temperatureColor(sensor.celsius)
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = color.copy(alpha = 0.15f), modifier = Modifier.size(44.dp)) {
                    Icon(sensor.icon, contentDescription = null, tint = color, modifier = Modifier.padding(10.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(sensor.name, style = MaterialTheme.typography.titleSmall)
                    Text(sensor.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Format.celsius(sensor.celsius), style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text(temperatureLabel(sensor.celsius), style = MaterialTheme.typography.labelMedium, color = color)
                }
            }
            Spacer(Modifier.height(14.dp))
            // Scale runs from -20 °C to 80 °C.
            LinearProgressIndicator(
                progress = { ((sensor.celsius + 20) / 100).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}
