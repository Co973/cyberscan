package com.cyberscan.app.ui.hud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyberscan.app.domain.model.MergedDevice
import com.cyberscan.app.domain.model.NetworkStatus
import com.cyberscan.app.ui.theme.AlertOrange
import com.cyberscan.app.ui.theme.DataGreen
import com.cyberscan.app.ui.theme.SignalCyan
import com.cyberscan.app.ui.theme.TextMuted

@Composable
fun TargetDataPanel(
    target: MergedDevice?,
    networkStatus: NetworkStatus,
    modifier: Modifier = Modifier,
) {
    CornerBracketPanel(modifier = modifier, accent = if (target == null) TextMuted else AlertOrange) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("TARGET DATA", color = AlertOrange)
            if (target == null) {
                Text("Select a detected signal", color = TextMuted)
            } else {
                DataLine("IDENT", target.bluetooth.name ?: "UNKNOWN")
                DataLine("CLASS", target.bluetooth.deviceClass.name)
                DataLine("BT MAC", target.bluetooth.macAddress)
                DataLine("IPV4", target.correlatedNetwork?.ipAddress ?: "UNRESOLVED")
                DataLine("HOST", target.correlatedNetwork?.hostname ?: "--")
                DataLine("VENDOR", target.correlatedNetwork?.vendorOui ?: "--")
                Text(target.confidenceReason, color = SignalCyan)
            }
            val netColor = if (networkStatus == NetworkStatus.Available) DataGreen else TextMuted
            Text("NMAP // ${networkStatus.name.uppercase()}", color = netColor)
        }
    }
}

@Composable
private fun DataLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextMuted)
        Text(value, color = SignalCyan)
    }
}

