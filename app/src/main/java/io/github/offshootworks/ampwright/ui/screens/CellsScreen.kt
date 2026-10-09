package io.github.offshootworks.ampwright.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.offshootworks.ampwright.bms.BmsSnapshot
import io.github.offshootworks.ampwright.protocol.CellInfo
import io.github.offshootworks.ampwright.ui.Format
import io.github.offshootworks.ampwright.ui.components.EmptyState
import io.github.offshootworks.ampwright.ui.components.SectionCard
import io.github.offshootworks.ampwright.ui.components.TileGrid
import io.github.offshootworks.ampwright.ui.spreadColor
import io.github.offshootworks.ampwright.ui.theme.AppTheme

@Composable
fun CellsScreen(snapshot: BmsSnapshot, contentPadding: PaddingValues) {
    val cells = snapshot.cells
    if (cells == null || cells.count == 0) {
        Column(Modifier.padding(contentPadding)) {
            EmptyState(Icons.Outlined.HourglassEmpty, "Waiting for cell data", "Cell voltages appear after the next reading.")
        }
        return
    }
    val basic = snapshot.basic
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Summary(cells)
        SectionCard(title = "Cell voltages") {
            CellChart(cells, Modifier.fillMaxWidth().height(200.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                "Bars are zoomed to the voltage range so small differences are visible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val anyBalancing = basic != null && cells.cellMv.indices.any(basic::isCellBalancing)
        if (anyBalancing) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Icon(Icons.Outlined.SyncAlt, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text("Balancing is active: the BMS is bleeding charge from the highest cells.",
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        TileGrid(cells.cellMv.indices.toList(), columns = 2) { i, modifier ->
            CellTile(
                index = i,
                mv = cells.cellMv[i],
                averageMv = cells.averageMv,
                isMax = i == cells.maxIndex && cells.deltaMv > 0,
                isMin = i == cells.minIndex && cells.deltaMv > 0,
                balancing = basic?.isCellBalancing(i) == true,
                modifier = modifier,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Summary(cells: CellInfo) {
    SectionCard {
        Row(Modifier.fillMaxWidth()) {
            SummaryItem("Spread", "${cells.deltaMv} mV", spreadColor(cells.deltaMv), Modifier.weight(1f))
            SummaryItem("Highest", Format.cellVolts(cells.maxMv), null, Modifier.weight(1f), "cell ${cells.maxIndex + 1}")
            SummaryItem("Lowest", Format.cellVolts(cells.minMv), null, Modifier.weight(1f), "cell ${cells.minIndex + 1}")
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "${cells.count} cells · average ${Format.cellVolts(cells.averageMv)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SummaryItem(label: String, value: String, color: Color?, modifier: Modifier, supporting: String? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            color = color ?: MaterialTheme.colorScheme.onSurface)
        if (supporting != null) {
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CellChart(cells: CellInfo, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val barColor = MaterialTheme.colorScheme.primary
    val maxColor = AppTheme.status.warn
    val minColor = AppTheme.status.cold
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)

    // Zoom the y-axis to the data, with at least a 40 mV window.
    val pad = maxOf(10, (40 - cells.deltaMv) / 2)
    val floor = cells.minMv - pad
    val ceiling = cells.maxMv + pad
    val range = (ceiling - floor).toFloat()

    Canvas(modifier) {
        val labelHeight = 18.dp.toPx()
        val chartHeight = size.height - labelHeight
        val slot = size.width / cells.count
        val barWidth = (slot * 0.62f).coerceAtMost(36.dp.toPx())

        val avgY = chartHeight * (1 - (cells.averageMv - floor) / range)
        drawLine(gridColor, Offset(0f, avgY), Offset(size.width, avgY), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        drawText(measurer, "avg", Offset(0f, (avgY - 16.dp.toPx()).coerceAtLeast(0f)), style = labelStyle)

        val labelEvery = if (cells.count > 16) 2 else 1
        cells.cellMv.forEachIndexed { i, mv ->
            val h = chartHeight * ((mv - floor) / range).coerceIn(0.02f, 1f)
            val x = slot * i + (slot - barWidth) / 2
            val color = when {
                cells.deltaMv == 0 -> barColor
                i == cells.maxIndex -> maxColor
                i == cells.minIndex -> minColor
                else -> barColor
            }
            drawRoundRect(color, Offset(x, chartHeight - h), Size(barWidth, h), CornerRadius(4.dp.toPx()))
            if (i % labelEvery == 0) {
                val text = measurer.measure("${i + 1}", labelStyle)
                drawText(text, topLeft = Offset(slot * i + (slot - text.size.width) / 2, chartHeight + 4.dp.toPx()))
            }
        }
    }
}

@Composable
private fun CellTile(
    index: Int,
    mv: Int,
    averageMv: Int,
    isMax: Boolean,
    isMin: Boolean,
    balancing: Boolean,
    modifier: Modifier,
) {
    val accent = when {
        isMax -> AppTheme.status.warn
        isMin -> AppTheme.status.cold
        else -> null
    }
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = accent?.let { BorderStroke(1.5.dp, it) },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cell ${index + 1}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (balancing) {
                    Icon(Icons.Outlined.SyncAlt, contentDescription = "Balancing", modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary)
                }
            }
            Text(Format.cellVolts(mv), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    isMax -> "Highest · ${Format.signedMv(mv - averageMv)}"
                    isMin -> "Lowest · ${Format.signedMv(mv - averageMv)}"
                    else -> Format.signedMv(mv - averageMv) + " vs avg"
                },
                style = MaterialTheme.typography.bodySmall,
                color = accent ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
