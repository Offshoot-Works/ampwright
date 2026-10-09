package io.github.offshootworks.ampwright.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.offshootworks.ampwright.bms.BleBmsClient
import io.github.offshootworks.ampwright.bms.BleScanner
import io.github.offshootworks.ampwright.bms.BmsDevice
import io.github.offshootworks.ampwright.bms.BmsSnapshot
import io.github.offshootworks.ampwright.bms.BmsSource
import io.github.offshootworks.ampwright.bms.DemoBmsSource
import io.github.offshootworks.ampwright.bms.DevicePrefs
import io.github.offshootworks.ampwright.bms.LinkState
import io.github.offshootworks.ampwright.diagnostics.DiagnosticsReport
import io.github.offshootworks.ampwright.protocol.MosCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class BmsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = DevicePrefs(app)
    private val ble = BleBmsClient(app, viewModelScope)
    private val demo = DemoBmsSource(viewModelScope)
    val scanner = BleScanner(app, viewModelScope)

    private val source = MutableStateFlow<BmsSource>(ble)

    private val _device = MutableStateFlow<BmsDevice?>(null)
    /** The battery the user chose, or null when on the connect screen. */
    val device: StateFlow<BmsDevice?> = _device.asStateFlow()

    private val _lastDevice = MutableStateFlow(prefs.lastDevice)
    val lastDevice: StateFlow<BmsDevice?> = _lastDevice.asStateFlow()

    val link: StateFlow<LinkState> = source.flatMapLatest { it.link }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LinkState.Disconnected)

    val snapshot: StateFlow<BmsSnapshot> = source.flatMapLatest { it.snapshot }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BmsSnapshot())

    val isDemo get() = _device.value?.address == DemoBmsSource.ADDRESS

    private var autoConnectTried = false

    fun connect(device: BmsDevice) {
        scanner.stop()
        source.value.disconnect()
        source.value = ble
        _device.value = device
        prefs.lastDevice = device
        _lastDevice.value = device
        ble.connect(device)
    }

    fun startDemo() {
        scanner.stop()
        source.value.disconnect()
        source.value = demo
        val device = BmsDevice(DemoBmsSource.ADDRESS, "Demo battery")
        _device.value = device
        demo.connect(device)
    }

    fun disconnect() {
        source.value.disconnect()
        _device.value = null
    }

    fun retry() {
        val device = _device.value ?: return
        if (device.address == DemoBmsSource.ADDRESS) startDemo() else connect(device)
    }

    fun forgetLastDevice() {
        prefs.lastDevice = null
        _lastDevice.value = null
    }

    /** Reconnects to the remembered battery once per app launch. */
    fun autoConnect() {
        if (autoConnectTried) return
        autoConnectTried = true
        if (_device.value == null) prefs.lastDevice?.let(::connect)
    }

    fun setCharging(on: Boolean) = source.value.send(if (on) MosCommand.ChargeOn else MosCommand.ChargeOff)

    fun setDischarging(on: Boolean) = source.value.send(if (on) MosCommand.DischargeOn else MosCommand.DischargeOff)

    fun diagnosticsReport(): String = DiagnosticsReport.build(getApplication(), _device.value, link.value, snapshot.value)

    override fun onCleared() {
        scanner.stop()
        source.value.disconnect()
    }
}
