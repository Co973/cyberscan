package com.cyberscan.app.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cyberscan.app.domain.model.EmfReading
import com.cyberscan.app.domain.model.ScanPhase
import com.cyberscan.app.ui.theme.AlertOrange
import com.cyberscan.app.ui.theme.DataGreen
import com.cyberscan.app.ui.theme.GridBlue
import com.cyberscan.app.ui.theme.SignalCyan
import com.cyberscan.app.ui.theme.TextMuted
import kotlin.math.roundToInt

@Composable
fun StatusMeter(
    phase: ScanPhase,
    adapterName: String?,
    emf: EmfReading?,
    modifier: Modifier = Modifier,
) {
    val phaseLabel = when (phase) {
        ScanPhase.Idle -> "STANDBY"
        ScanPhase.Calibrating -> "CALIBRATING"
        ScanPhase.Scanning -> "SCANNING"
        ScanPhase.Complete -> "COMPLETE"
        is ScanPhase.Failed -> "FAULT"
    }
    val phaseColor = if (phase is ScanPhase.Failed) AlertOrange else DataGreen
    val anomaly = emf?.anomalyMicroTesla ?: 0f
    val normalized = (anomaly / 25f).coerceIn(0f, 1f)

    CornerBracketPanel(modifier = modifier, accent = phaseColor) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SYS // $phaseLabel", color = phaseColor)
                Text(adapterName ?: "HCI --", color = TextMuted)
            }
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .semantics {
                        contentDescription = "EMF anomaly ${anomaly.roundToInt()} microtesla"
                    },
            ) {
                val gap = 3.dp.toPx()
                val segments = 18
                val segmentWidth = (size.width - gap * (segments - 1)) / segments
                repeat(segments) { index ->
                    val active = index < (normalized * segments).roundToInt()
                    val progress = index.toFloat() / segments
                    val color = when {
                        !active -> GridBlue
                        progress > .72f -> AlertOrange
                        else -> SignalCyan
                    }
                    drawRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(index * (segmentWidth + gap), 0f),
                        size = androidx.compose.ui.geometry.Size(segmentWidth, size.height),
                    )
                }
            }
            Text(
                text = "EMF Δ ${"%.1f".format(anomaly)} µT  /  BASE ${"%.1f".format(emf?.baselineMicroTesla ?: 0f)}",
                color = if (normalized > .72f) AlertOrange else Color.White,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
    }
}

