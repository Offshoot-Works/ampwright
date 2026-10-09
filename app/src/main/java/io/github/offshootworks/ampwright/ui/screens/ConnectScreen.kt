package io.github.offshootworks.ampwright.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Battery5Bar
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SignalCellularAlt1Bar
import androidx.compose.material.icons.outlined.SignalCellularAlt2Bar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.offshootworks.ampwright.bms.BmsDevice
import io.github.offshootworks.ampwright.bms.ScannedDevice
import io.github.offshootworks.ampwright.ui.theme.AppTheme

/** Phone-side prerequisites for talking to the battery. */
data class BluetoothEnv(
    val permissionsGranted: Boolean,
    val bluetoothOn: Boolean,
    val locationNeeded: Boolean,
) {
    val ready get() = permissionsGranted && bluetoothOn && !locationNeeded
}

@Composable
fun ConnectScreen(
    env: BluetoothEnv,
    contentPadding: PaddingValues,
    scanning: Boolean,
    results: List<ScannedDevice>,
    lastDevice: BmsDevice?,
    failure: Pair<BmsDevice, String>?,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (BmsDevice) -> Unit,
    onForget: () -> Unit,
    onDismissFailure: () -> Unit,
    /** Null hides the demo button, as in release builds. */
    onDemo: (() -> Unit)?,
    onOpenAbout: () -> Unit,
) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val visible = if (showAll) results else results.filter { it.looksLikeBms }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header() }

        if (failure != null) {
            item {
                Notice(
                    icon = Icons.Outlined.ErrorOutline,
                    color = AppTheme.status.bad,
                    title = "Couldn't connect to ${failure.first.name}",
                    body = failure.second,
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onConnect(failure.first) }) { Text("Try again") }
                            TextButton(onClick = onOpenAbout) { Text("Get help") }
                        }
                    },
                    onDismiss = onDismissFailure,
                )
            }
        }

        when {
            !env.permissionsGranted -> item {
                Notice(
                    icon = Icons.Outlined.Bluetooth,
                    color = MaterialTheme.colorScheme.primary,
                    title = "Allow Bluetooth access",
                    body = "The app needs permission to find and connect to nearby Bluetooth devices. " +
                        "It's only used to talk to your battery.",
                    action = { Button(onClick = onRequestPermissions) { Text("Allow") } },
                )
            }
            !env.bluetoothOn -> item {
                Notice(
                    icon = Icons.Outlined.BluetoothDisabled,
                    color = AppTheme.status.warn,
                    title = "Bluetooth is off",
                    body = "Turn on Bluetooth to connect to your battery.",
                    action = { Button(onClick = onEnableBluetooth) { Text("Turn on") } },
                )
            }
            env.locationNeeded -> item {
                Notice(
                    icon = Icons.Outlined.LocationOff,
                    color = AppTheme.status.warn,
                    title = "Location is off",
                    body = "On this version of Android, Bluetooth scanning only works while Location is switched on.",
                    action = { Button(onClick = onOpenLocationSettings) { Text("Open settings") } },
                )
            }
        }

        if (lastDevice != null && env.ready) {
            item { RecentDevice(lastDevice, onConnect = { onConnect(lastDevice) }, onForget = onForget) }
        }

        if (env.ready) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Nearby batteries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f))
                    if (scanning) {
                        TextButton(onClick = onStopScan) { Text("Stop") }
                    } else {
                        FilledTonalButton(onClick = onScan) { Text(if (results.isEmpty()) "Scan" else "Scan again") }
                    }
                }
                if (scanning) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        when {
                            scanning -> "Looking for batteries… make sure the BMS is awake and within a few metres."
                            results.isNotEmpty() -> "No batteries found. Turn on \"Show all devices\" if yours has a different name."
                            else -> "Tap Scan to look for your battery."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
            } else {
                item {
                    Card(
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        Column {
                            visible.forEachIndexed { i, result ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                DeviceRow(result) { onConnect(result.device) }
                            }
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Text("Show all devices", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = showAll, onCheckedChange = { showAll = it })
                }
            }
        }

        if (onDemo != null) {
            item {
                OutlinedButton(onClick = onDemo, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Icon(Icons.Outlined.Science, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Try demo mode")
                }
            }
        }

        item {
            TextButton(onClick = onOpenAbout, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("About & diagnostics")
            }
        }
    }
}

@Composable
private fun Header() {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(72.dp)) {
            Icon(
                Icons.Outlined.Battery5Bar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(18.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text("Connect to your battery", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Monitor charge, cells and temperatures on your battery over Bluetooth.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Notice(
    icon: ImageVector,
    color: Color,
    title: String,
    body: String,
    action: @Composable () -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Dismiss")
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(14.dp))
            action()
        }
    }
}

@Composable
private fun RecentDevice(device: BmsDevice, onConnect: () -> Unit, onForget: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Recent battery", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(device.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(device.address, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConnect) { Text("Connect") }
                TextButton(onClick = onForget) { Text("Forget") }
            }
        }
    }
}

@Composable
private fun DeviceRow(result: ScannedDevice, onClick: () -> Unit) {
    val signal = when {
        result.rssi >= -65 -> Icons.Outlined.SignalCellularAlt
        result.rssi >= -80 -> Icons.Outlined.SignalCellularAlt2Bar
        else -> Icons.Outlined.SignalCellularAlt1Bar
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp),
        headlineContent = { Text(result.device.name, fontWeight = FontWeight.Medium) },
        supportingContent = { Text("${result.device.address} · ${result.rssi} dBm") },
        leadingContent = {
            Icon(
                if (result.looksLikeBms) Icons.Outlined.Battery5Bar else Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailingContent = { Icon(signal, contentDescription = "Signal ${result.rssi} dBm") },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
