package io.github.offshootworks.ampwright.bms

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import io.github.offshootworks.ampwright.protocol.BmsFrame
import io.github.offshootworks.ampwright.protocol.BmsParser
import io.github.offshootworks.ampwright.protocol.BmsProtocol
import io.github.offshootworks.ampwright.protocol.FrameAssembler
import io.github.offshootworks.ampwright.protocol.MosCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Talks to the BMS over BLE GATT. The service and characteristics depend on which Bluetooth module
 * the battery has (see [GattProfile]); the LTW module uses service 0xFE60, notifications on 0xFE62
 * and writes to 0xFE61.
 *
 * Like the original app, it polls basic info -> cell voltages -> temperatures in a loop, one
 * request every 500 ms, and treats 5 unanswered requests as a lost link. Unlike the original it
 * reconnects automatically after a dropped link.
 *
 * The LTW module only talks to bonded phones (the original app paired before connecting), so an
 * unbonded battery with that module is paired over the open LE link once its services are known,
 * and Android asks the user for its password. Other modules are used without pairing.
 *
 * Connection state is only mutated on [scope]'s dispatcher (main); GATT callbacks hop onto it.
 */
@SuppressLint("MissingPermission") // Callers check permissions before connecting.
class BleBmsClient(
    private val context: Context,
    private val scope: CoroutineScope,
) : BmsSource {

    private val _link = MutableStateFlow<LinkState>(LinkState.Disconnected)
    override val link = _link.asStateFlow()

    private val _snapshot = MutableStateFlow(BmsSnapshot())
    override val snapshot = _snapshot.asStateFlow()

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val assembler = FrameAssembler()
    private val pendingCommand = AtomicReference<MosCommand?>(null)
    private val responded = AtomicBoolean(true)

    @Volatile private var nextRead = BmsProtocol.CMD_BASIC_INFO
    @Volatile private var gatt: BluetoothGatt? = null

    private var target: BmsDevice? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var attempt = 0
    private var everConnected = false
    private var connectionJob: Job? = null
    private var pollJob: Job? = null
    private var bondReceiver: BroadcastReceiver? = null

    override fun connect(device: BmsDevice) {
        disconnect()
        target = device
        attempt = 0
        everConnected = false
        pendingCommand.set(null)
        _snapshot.value = BmsSnapshot()
        openGatt()
    }

    override fun disconnect() {
        target = null
        teardown()
        _link.value = LinkState.Disconnected
    }

    override fun send(command: MosCommand) {
        pendingCommand.set(command)
    }

    private fun openGatt() {
        val device = target ?: return
        attempt++
        _link.value = LinkState.Connecting(attempt, everConnected)
        if (adapter == null || !adapter.isEnabled) {
            onAttemptFailed("Bluetooth is turned off.")
            return
        }
        assembler.reset()
        try {
            val remote = adapter.getRemoteDevice(device.address)
            gatt = remote.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            fail("Bluetooth permission was denied.")
            return
        } catch (e: IllegalArgumentException) {
            fail("Invalid Bluetooth address.")
            return
        }
        startConnectTimeout()
    }

    /** Covers connect + service discovery + first response, like the original's 15 s limit. */
    private fun startConnectTimeout() {
        connectionJob?.cancel()
        connectionJob = scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            onAttemptFailed("The battery didn't respond in time.")
        }
    }

    /** Gives up without retrying. */
    private fun fail(reason: String) {
        target = null
        teardown()
        _link.value = LinkState.Failed(reason)
    }

    private fun discoverServices(g: BluetoothGatt) {
        if (!g.discoverServices()) onAttemptFailed("Service discovery failed.")
    }

    /**
     * Bonds over the open LE link. Android shows its own password dialog; the connect timeout is
     * replaced by a longer one so the user has time to type. A wrong or cancelled password fails
     * without retrying, so the dialog doesn't keep popping up.
     */
    private fun pair(g: BluetoothGatt) {
        _link.value = LinkState.Pairing
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                if (g !== gatt || device?.address != g.device.address) return
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                    BluetoothDevice.BOND_BONDED -> onPaired(g)
                    BluetoothDevice.BOND_NONE -> fail("Pairing failed. Check the battery's password and try again.")
                }
            }
        }
        bondReceiver = receiver
        // The bond broadcast comes from the Bluetooth process, so the receiver must be exported.
        // ACTION_BOND_STATE_CHANGED is a protected broadcast, so other apps can't fake it.
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED,
        )
        val started = when (g.device.bondState) {
            BluetoothDevice.BOND_BONDED -> return onPaired(g)
            BluetoothDevice.BOND_BONDING -> true // Android already started it.
            else -> g.device.createBond()
        }
        if (!started) return fail("Couldn't start pairing with the battery.")
        connectionJob?.cancel()
        connectionJob = scope.launch {
            delay(PAIRING_TIMEOUT_MS)
            fail("Pairing timed out. Enter the battery's password when Android asks for it.")
        }
    }

    private fun onPaired(g: BluetoothGatt) {
        stopWatchingBond()
        _link.value = LinkState.Connecting(attempt, everConnected)
        startConnectTimeout()
        discoverServices(g)
    }

    private fun stopWatchingBond() {
        bondReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
            }
        }
        bondReceiver = null
    }

    private fun onAttemptFailed(reason: String) {
        teardown()
        if (target == null) return
        if (!everConnected && attempt >= MAX_INITIAL_ATTEMPTS) {
            target = null
            _link.value = LinkState.Failed(reason)
            return
        }
        // An established link that drops keeps retrying until the user disconnects.
        _link.value = LinkState.Connecting(attempt + 1, everConnected)
        val wait = minOf(MAX_RETRY_DELAY_MS, RETRY_DELAY_MS * attempt)
        connectionJob = scope.launch {
            delay(wait)
            openGatt()
        }
    }

    private fun teardown() {
        connectionJob?.cancel()
        connectionJob = null
        pollJob?.cancel()
        pollJob = null
        stopWatchingBond()
        gatt?.let {
            try {
                it.disconnect()
                it.close()
            } catch (_: SecurityException) {
            }
        }
        gatt = null
        writeChar = null
    }

    private fun startPolling() {
        if (pollJob != null) return
        nextRead = BmsProtocol.CMD_BASIC_INFO
        responded.set(true)
        pollJob = scope.launch {
            var missed = 0
            while (isActive) {
                if (responded.getAndSet(false)) {
                    missed = 0
                } else if (++missed >= MAX_MISSED_POLLS) {
                    onAttemptFailed(if (everConnected) "The battery stopped responding." else "No response from the battery.")
                    break
                }
                val command = pendingCommand.getAndSet(null)
                write(
                    if (command != null) BmsProtocol.writeRequest(BmsProtocol.CMD_MOS_CONTROL, command.value)
                    else BmsProtocol.readRequest(nextRead)
                )
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun write(bytes: ByteArray) {
        val g = gatt ?: return
        val c = writeChar ?: return
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(c, bytes, c.writeType)
            } else {
                @Suppress("DEPRECATION")
                c.value = bytes
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
        } catch (_: SecurityException) {
        }
    }

    private fun findProfile(g: BluetoothGatt): Triple<GattProfile, BluetoothGattCharacteristic, BluetoothGattCharacteristic>? {
        for (profile in GattProfile.all) {
            val service = g.getService(profile.service) ?: continue
            val write = service.getCharacteristic(profile.write) ?: continue
            val notify = service.getCharacteristic(profile.notify) ?: continue
            return Triple(profile, write, notify)
        }
        return null
    }

    /** Returns true if a CCCD write was started; polling then begins in onDescriptorWrite. */
    private fun enableNotifications(g: BluetoothGatt, c: BluetoothGattCharacteristic): Boolean {
        if (!g.setCharacteristicNotification(c, true)) return false
        val cccd = c.getDescriptor(CCCD_UUID) ?: return false
        val value = if (c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        return if (Build.VERSION.SDK_INT >= 33) {
            g.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            cccd.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
    }

    /** Runs on a binder thread. */
    private fun onNotification(g: BluetoothGatt, value: ByteArray) {
        if (g !== gatt) return
        val frames = assembler.feed(value)
        if (frames.isEmpty()) return
        frames.forEach(::apply)
        responded.set(true)
        if (_link.value != LinkState.Connected) {
            scope.launch {
                if (g !== gatt) return@launch
                connectionJob?.cancel()
                connectionJob = null
                everConnected = true
                attempt = 0
                _link.value = LinkState.Connected
            }
        }
    }

    private fun apply(frame: BmsFrame) {
        val now = System.currentTimeMillis()
        when (frame.cmd) {
            BmsProtocol.CMD_BASIC_INFO -> {
                BmsParser.parseBasic(frame.payload)?.let { b -> _snapshot.update { it.copy(basic = b, updatedAtMillis = now) } }
                nextRead = BmsProtocol.CMD_CELL_VOLTAGES
            }
            BmsProtocol.CMD_CELL_VOLTAGES -> {
                BmsParser.parseCells(frame.payload)?.let { c -> _snapshot.update { it.copy(cells = c, updatedAtMillis = now) } }
                nextRead = BmsProtocol.CMD_TEMPERATURES
            }
            BmsProtocol.CMD_TEMPERATURES -> {
                BmsParser.parseTemps(frame.payload)?.let { t -> _snapshot.update { it.copy(temps = t, updatedAtMillis = now) } }
                nextRead = BmsProtocol.CMD_BASIC_INFO
            }
            else -> nextRead = BmsProtocol.CMD_BASIC_INFO
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            scope.launch {
                if (g !== gatt) return@launch
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    delay(SERVICE_DISCOVERY_DELAY_MS)
                    if (g !== gatt) return@launch
                    discoverServices(g)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                    onAttemptFailed(if (everConnected) "Connection lost." else "Couldn't connect (Bluetooth error $status).")
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            scope.launch {
                if (g !== gatt) return@launch
                val found = if (status == BluetoothGatt.GATT_SUCCESS) findProfile(g) else null
                if (found == null) {
                    fail("This device doesn't look like an LTW BMS.")
                    return@launch
                }
                val (profile, write, notify) = found
                if (profile.needsBond && g.device.bondState != BluetoothDevice.BOND_BONDED) {
                    pair(g) // Services are discovered again once bonded.
                    return@launch
                }
                writeChar = write
                if (!enableNotifications(g, notify)) startPolling()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            scope.launch { if (g === gatt) startPolling() }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            onNotification(g, value)
        }

        @Deprecated("Used on Android 12L and below")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            onNotification(g, c.value ?: return)
        }
    }

    companion object {
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val POLL_INTERVAL_MS = 500L
        private const val MAX_MISSED_POLLS = 5
        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val PAIRING_TIMEOUT_MS = 60_000L
        private const val SERVICE_DISCOVERY_DELAY_MS = 300L
        private const val MAX_INITIAL_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1_500L
        private const val MAX_RETRY_DELAY_MS = 10_000L
    }
}
