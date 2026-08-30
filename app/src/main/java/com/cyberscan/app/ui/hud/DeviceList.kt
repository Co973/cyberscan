package com.cyberscan.app.ui.hud

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cyberscan.app.domain.model.Confidence
import com.cyberscan.app.domain.model.MergedDevice
import com.cyberscan.app.ui.theme.AlertOrange
import com.cyberscan.app.ui.theme.GridBlue
import com.cyberscan.app.ui.theme.SignalCyan
import com.cyberscan.app.ui.theme.TextMuted

@Composable
fun DeviceList(
    devices: List<MergedDevice>,
    selectedMac: String?,
    onTargetSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CornerBracketPanel(modifier = modifier) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("DETECTED SIGNALS", color = SignalCyan)
                Text(devices.size.toString().padStart(2, '0'), color = TextMuted)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = GridBlue)
            if (devices.isEmpty()) {
                Text(
                    "No Bluetooth signals acquired",
                    color = TextMuted,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn {
                    items(devices, key = { it.bluetooth.macAddress }) { device ->
                        DeviceRow(
                            device = device,
                            selected = device.bluetooth.macAddress == selectedMac,
                            onClick = { onTargetSelected(device.bluetooth.macAddress) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: MergedDevice, selected: Boolean, onClick: () -> Unit) {
    val accent = if (selected) AlertOrange else SignalCyan
    val confidence = when (device.confidence) {
        Confidence.HIGH -> "◆◆◆"
        Confidence.MAYBE -> "◆◆◇"
        Confidence.NONE -> "◇◇◇"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(device.bluetooth.name ?: "UNKNOWN DEVICE", color = accent)
            Text(confidence, color = accent)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(device.bluetooth.macAddress, color = TextMuted)
            Text("${device.bluetooth.rssi ?: "--"} dBm", color = Color.White)
        }
    }
    HorizontalDivider(color = GridBlue)
}
