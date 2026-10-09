package io.github.offshootworks.ampwright.bms

import io.github.offshootworks.ampwright.protocol.BasicInfo
import io.github.offshootworks.ampwright.protocol.CellInfo
import io.github.offshootworks.ampwright.protocol.ChargeState
import io.github.offshootworks.ampwright.protocol.MosCommand
import io.github.offshootworks.ampwright.protocol.TempInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Simulated 4S 100 Ah LiFePO4 pack so the UI can be explored without a battery. */
class DemoBmsSource(private val scope: CoroutineScope) : BmsSource {

    private val _link = MutableStateFlow<LinkState>(LinkState.Disconnected)
    override val link = _link.asStateFlow()

    private val _snapshot = MutableStateFlow(BmsSnapshot())
    override val snapshot = _snapshot.asStateFlow()

    private var job: Job? = null
    private var chargeOn = true
    private var dischargeOn = true
    private var remainingMah = 72_400.0

    override fun connect(device: BmsDevice) {
        disconnect()
        _link.value = LinkState.Connecting(1, reconnecting = false)
        job = scope.launch {
            delay(900)
            _link.value = LinkState.Connected
            var t = 0
            while (isActive) {
                _snapshot.value = generate(t++)
                delay(1_000)
            }
        }
    }

    override fun disconnect() {
        job?.cancel()
        job = null
        _snapshot.value = BmsSnapshot()
        _link.value = LinkState.Disconnected
    }

    override fun send(command: MosCommand) {
        when (command) {
            MosCommand.ChargeOn -> chargeOn = true
            MosCommand.ChargeOff -> chargeOn = false
            MosCommand.DischargeOn -> dischargeOn = true
            MosCommand.DischargeOff -> dischargeOn = false
        }
    }

    private fun generate(t: Int): BmsSnapshot {
        // Alternate between a solar-charging phase and a discharging phase every 40 s.
        val solar = (t / 40) % 2 == 0
        val rawCurrent = if (solar) 9_000 + 2_500 * sin(t / 5.0) else -6_500 - 3_000 * sin(t / 3.0)
        val currentMa = when {
            rawCurrent > 0 && !chargeOn -> 0
            rawCurrent < 0 && !dischargeOn -> 0
            else -> (rawCurrent / 10).roundToInt() * 10
        }
        remainingMah = (remainingMah + currentMa / 3600.0).coerceIn(0.0, FULL_MAH.toDouble())
        val soc = (remainingMah * 100 / FULL_MAH).roundToInt()

        val base = 3_260 + soc * 1.1 + currentMa / 400.0
        val offsets = intArrayOf(4, -3, 11, -9)
        val cells = List(4) { (base + offsets[it] + Random.nextInt(-2, 3)).roundToInt() }
        val packMv = cells.sum()
        val balancing = if (currentMa > 0 && cells.max() - cells.min() > 15) 1 shl cells.indexOf(cells.max()) else 0
        val temp = 21.5 + kotlin.math.abs(currentMa) / 2_000.0

        val basic = BasicInfo(
            packVoltageMv = packMv / 10 * 10,
            currentMa = currentMa,
            socPercent = soc,
            cycles = 137,
            chargeState = when {
                currentMa > 0 -> ChargeState.Charging
                currentMa < 0 -> ChargeState.Discharging
                else -> ChargeState.Idle
            },
            fullCapacityMah = FULL_MAH,
            remainingCapacityMah = (remainingMah / 10).roundToInt() * 10,
            protectionFlags = 0,
            tempProtectionFlags = 0,
            alarmFlags = if (soc < 20) 1 shl 7 else 0,
            balanceFlags = balancing,
            firmwareVersion = 0x12,
            chargeFetOn = chargeOn,
            dischargeFetOn = dischargeOn,
            sohPercent = 96,
        )
        return BmsSnapshot(
            basic = basic,
            cells = CellInfo(cells),
            temps = TempInfo(
                sensorsC = listOf(temp, temp + 0.8),
                mosfetC = temp + 3.4,
                ambientC = 19.0,
            ),
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    companion object {
        const val ADDRESS = "DEMO"
        private const val FULL_MAH = 100_000
    }
}
