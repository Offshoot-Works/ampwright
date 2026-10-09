package io.github.offshootworks.ampwright.bms

import io.github.offshootworks.ampwright.protocol.BasicInfo
import io.github.offshootworks.ampwright.protocol.CellInfo
import io.github.offshootworks.ampwright.protocol.MosCommand
import io.github.offshootworks.ampwright.protocol.TempInfo
import kotlinx.coroutines.flow.StateFlow

data class BmsDevice(val address: String, val name: String)

sealed interface LinkState {
    data object Disconnected : LinkState

    /** [reconnecting] is true when an established link dropped and is being restored. */
    data class Connecting(val attempt: Int, val reconnecting: Boolean) : LinkState

    /** Waiting for the user to enter the battery's password in Android's pairing dialog. */
    data object Pairing : LinkState

    data object Connected : LinkState

    data class Failed(val message: String) : LinkState
}

data class BmsSnapshot(
    val basic: BasicInfo? = null,
    val cells: CellInfo? = null,
    val temps: TempInfo? = null,
    val updatedAtMillis: Long = 0L,
) {
    val hasData get() = basic != null
}

/** Something that produces BMS readings: the real battery over BLE, or the demo simulator. */
interface BmsSource {
    val link: StateFlow<LinkState>
    val snapshot: StateFlow<BmsSnapshot>
    fun connect(device: BmsDevice)
    fun disconnect()
    fun send(command: MosCommand)
}
