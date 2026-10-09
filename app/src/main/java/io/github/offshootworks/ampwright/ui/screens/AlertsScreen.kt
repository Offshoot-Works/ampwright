package io.github.offshootworks.ampwright.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.offshootworks.ampwright.bms.BmsSnapshot
import io.github.offshootworks.ampwright.protocol.BasicInfo
import io.github.offshootworks.ampwright.protocol.FlagSeverity
import io.github.offshootworks.ampwright.protocol.StatusFlag
import io.github.offshootworks.ampwright.protocol.StatusFlags
import io.github.offshootworks.ampwright.ui.components.EmptyState
import io.github.offshootworks.ampwright.ui.components.SectionCard
import io.github.offshootworks.ampwright.ui.components.StatusDot
import io.github.offshootworks.ampwright.ui.theme.AppTheme

@Composable
fun AlertsScreen(snapshot: BmsSnapshot, contentPadding: PaddingValues) {
    val basic = snapshot.basic
    if (basic == null) {
        Column(Modifier.padding(contentPadding)) {
            EmptyState(Icons.Outlined.HourglassEmpty, "Waiting for status", "Alarm states appear after the next reading.")
        }
        return
    }
    val active = StatusFlags.active(basic)
    val protections = active.filter { it.severity == FlagSeverity.Protection }
    val warnings = active.filter { it.severity == FlagSeverity.Warning }
    var showAll by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (active.isEmpty()) {
            SectionCard {
                EmptyState(
                    Icons.Outlined.VerifiedUser,
                    "All clear",
                    "The BMS isn't reporting any warnings or protection events.",
                )
            }
        }
        if (protections.isNotEmpty()) {
            Group(
                "Protections active",
                "The BMS has switched off charging or output to protect the battery.",
                protections,
                AppTheme.status.bad,
            )
        }
        if (warnings.isNotEmpty()) {
            Group("Warnings", "Limits are being approached. No action has been taken yet.", warnings, AppTheme.status.warn)
        }

        TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
            Icon(if (showAll) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (showAll) "Hide monitored conditions" else "Show all monitored conditions")
        }
        if (showAll) {
            AllConditions("Protections", StatusFlags.protections, basic)
            AllConditions("Warnings", StatusFlags.warnings, basic)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Group(title: String, subtitle: String, flags: List<StatusFlag>, color: Color) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (color == AppTheme.status.bad) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = color,
                )
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            flags.forEach { flag ->
                Row(Modifier.padding(vertical = 8.dp)) {
                    StatusDot(color, Modifier.padding(top = 6.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(flag.title, style = MaterialTheme.typography.titleSmall)
                        Text(flag.description, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AllConditions(title: String, flags: List<StatusFlag>, basic: BasicInfo) {
    SectionCard(title = title) {
        flags.forEach { flag ->
            val set = flag.isSet(basic)
            val color = when {
                !set -> AppTheme.status.good
                flag.severity == FlagSeverity.Protection -> AppTheme.status.bad
                else -> AppTheme.status.warn
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusDot(color)
                Spacer(Modifier.width(12.dp))
                Text(flag.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(if (set) "Active" else "OK", style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
    }
}
