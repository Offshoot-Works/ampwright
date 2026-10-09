package io.github.offshootworks.ampwright.ui

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.offshootworks.ampwright.BuildConfig
import io.github.offshootworks.ampwright.bms.BlePermissions
import io.github.offshootworks.ampwright.bms.BmsDevice
import io.github.offshootworks.ampwright.bms.BmsSnapshot
import io.github.offshootworks.ampwright.bms.LinkState
import io.github.offshootworks.ampwright.protocol.StatusFlags
import io.github.offshootworks.ampwright.ui.screens.AboutScreen
import io.github.offshootworks.ampwright.ui.screens.AlertsScreen
import io.github.offshootworks.ampwright.ui.screens.BluetoothEnv
import io.github.offshootworks.ampwright.ui.screens.CellsScreen
import io.github.offshootworks.ampwright.ui.screens.ConnectScreen
import io.github.offshootworks.ampwright.ui.screens.OverviewScreen
import io.github.offshootworks.ampwright.ui.screens.TemperatureScreen
import io.github.offshootworks.ampwright.ui.theme.AppTheme
import kotlinx.coroutines.delay

private fun readEnv(context: Context) = BluetoothEnv(
    permissionsGranted = BlePermissions.granted(context),
    bluetoothOn = BlePermissions.bluetoothEnabled(context),
    locationNeeded = BlePermissions.locationServicesNeeded(context),
)

@Composable
fun AmpWrightApp(vm: BmsViewModel = viewModel()) {
    val context = LocalContext.current
    var env by remember { mutableStateOf(readEnv(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { env = readEnv(context) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                env = readEnv(context)
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        env = readEnv(context)
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        env = readEnv(context)
    }

    val device by vm.device.collectAsStateWithLifecycle()
    val link by vm.link.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val lastDevice by vm.lastDevice.collectAsStateWithLifecycle()
    val scanning by vm.scanner.scanning.collectAsStateWithLifecycle()
    val results by vm.scanner.results.collectAsStateWithLifecycle()

    LaunchedEffect(env.ready) {
        if (env.ready) vm.autoConnect()
    }

    // Shown over whichever screen opened it; the connection carries on underneath.
    var showAbout by rememberSaveable { mutableStateOf(false) }
    if (showAbout) {
        BackHandler { showAbout = false }
        AboutScreen(buildReport = vm::diagnosticsReport, onBack = { showAbout = false })
        return
    }

    val current = device
    val failed = link as? LinkState.Failed
    if (current != null && failed == null) {
        Dashboard(
            device = current,
            isDemo = vm.isDemo,
            link = link,
            snapshot = snapshot,
            onDisconnect = vm::disconnect,
            onSetCharging = vm::setCharging,
            onSetDischarging = vm::setDischarging,
            onOpenAbout = { showAbout = true },
        )
    } else {
        LaunchedEffect(env.ready) {
            if (env.ready && lastDevice == null && failed == null) vm.scanner.start()
        }
        Scaffold { padding ->
            ConnectScreen(
                env = env,
                contentPadding = padding,
                scanning = scanning,
                results = results,
                lastDevice = lastDevice,
                failure = if (current != null && failed != null) current to failed.message else null,
                onRequestPermissions = { permissionLauncher.launch(BlePermissions.required) },
                onEnableBluetooth = {
                    try {
                        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    } catch (_: SecurityException) {
                        env = readEnv(context)
                    }
                },
                onOpenLocationSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                onScan = vm.scanner::start,
                onStopScan = vm.scanner::stop,
                onConnect = vm::connect,
                onForget = vm::forgetLastDevice,
                onDismissFailure = vm::disconnect,
                // A simulated battery helps development but would confuse people using the release.
                onDemo = if (BuildConfig.DEBUG) vm::startDemo else null,
                onOpenAbout = { showAbout = true },
            )
        }
    }
}

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Overview("Overview", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    Cells("Cells", Icons.Outlined.ViewWeek, Icons.Filled.ViewWeek),
    Temps("Temps", Icons.Outlined.Thermostat, Icons.Filled.Thermostat),
    Alerts("Alerts", Icons.Outlined.Notifications, Icons.Filled.Notifications),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    device: BmsDevice,
    isDemo: Boolean,
    link: LinkState,
    snapshot: BmsSnapshot,
    onDisconnect: () -> Unit,
    onSetCharging: (Boolean) -> Unit,
    onSetDischarging: (Boolean) -> Unit,
    onOpenAbout: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Overview) }
    var menuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = tab != Tab.Overview) { tab = Tab.Overview }
    val alertCount = StatusFlags.active(snapshot.basic).size

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (isDemo) "${device.name} (demo)" else device.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                            )
                            StatusLine(link, snapshot)
                        }
                    },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (isDemo) "Exit demo" else "Disconnect") },
                                leadingIcon = { Icon(Icons.Outlined.BluetoothDisabled, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onDisconnect()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("About & diagnostics") },
                                leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenAbout()
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                if (link is LinkState.Connecting || link == LinkState.Pairing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        label = { Text(t.label) },
                        icon = {
                            BadgedBox(badge = {
                                if (t == Tab.Alerts && alertCount > 0) Badge { Text("$alertCount") }
                            }) {
                                Icon(if (tab == t) t.selectedIcon else t.icon, contentDescription = null)
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        if (!snapshot.hasData) {
            ConnectingPlaceholder(device, link, padding, onDisconnect)
        } else {
            when (tab) {
                Tab.Overview -> OverviewScreen(
                    snapshot = snapshot,
                    device = device,
                    contentPadding = padding,
                    onOpenAlerts = { tab = Tab.Alerts },
                    onSetCharging = onSetCharging,
                    onSetDischarging = onSetDischarging,
                )
                Tab.Cells -> CellsScreen(snapshot, padding)
                Tab.Temps -> TemperatureScreen(snapshot, padding)
                Tab.Alerts -> AlertsScreen(snapshot, padding)
            }
        }
    }
}

@Composable
private fun StatusLine(link: LinkState, snapshot: BmsSnapshot) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val age = now - snapshot.updatedAtMillis
    val (text, color) = when (link) {
        is LinkState.Connecting ->
            if (link.reconnecting) "Reconnecting… (attempt ${link.attempt})" to AppTheme.status.warn
            else "Connecting…" to MaterialTheme.colorScheme.onSurfaceVariant
        LinkState.Pairing -> "Pairing…" to MaterialTheme.colorScheme.onSurfaceVariant
        LinkState.Connected ->
            if (snapshot.hasData && age > STALE_AFTER_MS) "No new data · ${Format.ago(age)}" to AppTheme.status.warn
            else "Live" to AppTheme.status.good
        else -> "Disconnected" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
private fun ConnectingPlaceholder(device: BmsDevice, link: LinkState, padding: PaddingValues, onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(24.dp))
        Text(
            when (link) {
                LinkState.Connected -> "Reading battery…"
                LinkState.Pairing -> "Pairing with ${device.name}…"
                else -> "Connecting to ${device.name}…"
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (link == LinkState.Pairing) {
                "Enter the battery's password when Android asks. If no box appears, check your notifications for a pairing request."
            } else {
                "Keep your phone within a few metres of the battery."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) { Text("Cancel") }
    }
}

private const val STALE_AFTER_MS = 6_000L
