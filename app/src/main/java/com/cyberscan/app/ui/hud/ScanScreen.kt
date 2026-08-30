package com.cyberscan.app.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.cyberscan.app.domain.model.ScanPhase
import com.cyberscan.app.domain.model.ScanUiState
import com.cyberscan.app.ui.theme.AlertOrange
import com.cyberscan.app.ui.theme.GridBlue
import com.cyberscan.app.ui.theme.SignalCyan
import com.cyberscan.app.ui.theme.TextMuted
import com.cyberscan.app.ui.theme.VoidBlack

@Composable
fun ScanScreen(
    state: ScanUiState,
    permissionMessage: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onTargetSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(VoidBlack)
            val grid = 24.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(GridBlue.copy(alpha = .32f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), .5f)
                y += grid
            }
            drawCircle(SignalCyan.copy(alpha = .04f), radius = size.width * .7f, center = center, style = Stroke(1.dp.toPx()))
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("CYBER//SCAN", color = SignalCyan, style = MaterialTheme.typography.titleLarge)
            Text("ROOT RF RECONSOLE  v1.0", color = TextMuted, style = MaterialTheme.typography.labelSmall)
            StatusMeter(state.phase, state.adapterName, state.emf, Modifier.fillMaxWidth())
            DeviceList(
                devices = state.devices,
                selectedMac = state.selectedMac,
                onTargetSelected = onTargetSelected,
                modifier = Modifier.fillMaxWidth().weight(1f).heightIn(min = 170.dp),
            )
            val selected = state.devices.firstOrNull { it.bluetooth.macAddress == state.selectedMac }
            TargetDataPanel(selected, state.networkStatus, Modifier.fillMaxWidth())
            val failure = (state.phase as? ScanPhase.Failed)?.reason
            if (failure != null || permissionMessage != null) {
                Text(
                    text = "! ${failure ?: permissionMessage}",
                    color = AlertOrange,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            ActionBar(state.phase, onStart, onStop, onRetry, Modifier.fillMaxWidth())
        }
    }
}

