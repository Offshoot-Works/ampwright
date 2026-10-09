package io.github.offshootworks.ampwright.bms

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScannedDevice(
    val device: BmsDevice,
    val rssi: Int,
    /** Name matches a prefix the LTW apps accept (LTW, ltw, BLE, SP-, AD). */
    val looksLikeBms: Boolean,
)

object BlePermissions {
    val required: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun granted(context: Context) = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    fun bluetoothEnabled(context: Context) =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    /** Android 11 and below only return scan results while location services are on. */
    fun locationServicesNeeded(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return false
        val lm = context.getSystemService(LocationManager::class.java) ?: return false
        return !LocationManagerCompat.isLocationEnabled(lm)
    }
}

@SuppressLint("MissingPermission") // Callers check permissions before scanning.
class BleScanner(context: Context, private val scope: CoroutineScope) {

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _results = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val results = _results.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()

    private var stopJob: Job? = null

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull()
            val address = result.device.address
            val found = ScannedDevice(
                device = BmsDevice(address, name ?: address),
                rssi = result.rssi,
                looksLikeBms = name != null && name.length > 3 && BMS_PREFIXES.any { name.startsWith(it) },
            )
            _results.update { list ->
                val others = list.filterNot { it.device.address == address }
                (others + found).sortedWith(compareByDescending<ScannedDevice> { it.looksLikeBms }.thenByDescending { it.rssi })
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _scanning.value = false
        }
    }

    fun start() {
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: return
        stop()
        _results.value = emptyList()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            scanner.startScan(null, settings, callback)
        } catch (_: SecurityException) {
            return
        }
        _scanning.value = true
        stopJob = scope.launch {
            delay(SCAN_DURATION_MS)
            stop()
        }
    }

    fun stop() {
        stopJob?.cancel()
        stopJob = null
        if (_scanning.value) {
            try {
                adapter?.bluetoothLeScanner?.stopScan(callback)
            } catch (_: SecurityException) {
            } catch (_: IllegalStateException) {
                // Adapter was switched off mid-scan.
            }
        }
        _scanning.value = false
    }

    companion object {
        /** LTW, ltw and BLE from the original app; SP- and AD from the current Play Store app. */
        private val BMS_PREFIXES = listOf("LTW", "ltw", "BLE", "SP-", "AD")
        private const val SCAN_DURATION_MS = 12_000L
    }
}
