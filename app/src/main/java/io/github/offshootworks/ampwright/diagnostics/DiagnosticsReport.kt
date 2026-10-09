package io.github.offshootworks.ampwright.diagnostics

import android.content.Context
import android.os.Build
import io.github.offshootworks.ampwright.BuildConfig
import io.github.offshootworks.ampwright.bms.BmsDevice
import io.github.offshootworks.ampwright.bms.BmsSnapshot
import io.github.offshootworks.ampwright.bms.DemoBmsSource
import io.github.offshootworks.ampwright.bms.LinkState
import io.github.offshootworks.ampwright.diagnostics.DiagnosticsLog.Companion.hex
import io.github.offshootworks.ampwright.diagnostics.DiagnosticsLog.Companion.maskAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Plain-text report for pasting into a GitHub issue. */
object DiagnosticsReport {

    fun build(
        context: Context,
        device: BmsDevice?,
        link: LinkState,
        snapshot: BmsSnapshot,
        log: DiagnosticsLog = DiagnosticsLog.shared,
    ): String = buildString {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        fun line(text: String = "") = appendLine(text)

        line("AmpWright diagnostics")
        line("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        line("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        line("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")

        line()
        line("## Battery")
        if (device == null) {
            line("Not connected")
        } else {
            line("Name: ${device.name}")
            line("Address: ${if (device.address == DemoBmsSource.ADDRESS) "demo mode" else maskAddress(device.address)}")
        }
        line("Link: ${describe(link)}")
        line("Bluetooth module: ${log.module() ?: "not identified"}")
        snapshot.basic?.let { b ->
            line("Firmware: ${b.firmwareLabel}${b.deviceId?.let { ", device ID $it" } ?: ""}")
            line("Charge ${b.socPercent} %, ${b.packVoltageMv} mV, ${b.currentMa} mA, ${b.cycles} cycles")
            line("Switches: charge ${onOff(b.chargeFetOn)}, output ${onOff(b.dischargeFetOn)}")
            line(
                "Flags: protection ${hex16(b.protectionFlags)}, temperature ${hex16(b.tempProtectionFlags)}, " +
                    "alarm ${hex32(b.alarmFlags)}, fault ${b.faultFlags?.let(::hex32) ?: "n/a"}",
            )
        }
        snapshot.cells?.let { line("Cells (mV): ${it.cellMv.joinToString(", ")}") }
        snapshot.temps?.let { t ->
            line("Temperatures (°C): ${t.sensorsC.joinToString(", ")}; MOSFET ${t.mosfetC ?: "n/a"}, ambient ${t.ambientC ?: "n/a"}")
        }

        val payloads = log.payloads()
        if (payloads.isNotEmpty()) {
            line()
            line("## Latest payload per command")
            payloads.forEach { (cmd, bytes) ->
                val size = if (bytes.isEmpty()) 0 else bytes.count { it == ' ' } + 1
                line("0x${hex(cmd)} ($size bytes): $bytes")
            }
        }

        val layout = log.gattLayout()
        if (layout.isNotEmpty()) {
            line()
            line("## Bluetooth services (last discovery)")
            layout.forEach(::line)
        }

        line()
        line("## Events")
        val events = log.events()
        if (events.isEmpty()) line("None yet")
        events.forEach { line("${time.format(Date(it.timeMillis))}  ${it.text}") }

        val notifications = log.notifications()
        if (notifications.isNotEmpty()) {
            line()
            line("## Raw notifications (newest last)")
            notifications.forEach { line("${time.format(Date(it.timeMillis))}  ${it.text}") }
        }

        CrashLog.read(context)?.let {
            line()
            line("## Last crash")
            line(it.trimEnd())
        }
    }

    private fun describe(link: LinkState) = when (link) {
        LinkState.Disconnected -> "disconnected"
        is LinkState.Connecting -> "${if (link.reconnecting) "reconnecting" else "connecting"}, attempt ${link.attempt}"
        LinkState.Pairing -> "pairing"
        LinkState.Connected -> "connected"
        is LinkState.Failed -> "failed: ${link.message}"
    }

    private fun onOff(on: Boolean) = if (on) "on" else "off"

    private fun hex16(value: Int) = "0x${hex(value shr 8)}${hex(value)}"

    private fun hex32(value: Int) = "0x${hex(value ushr 24)}${hex(value shr 16)}${hex(value shr 8)}${hex(value)}"
}
