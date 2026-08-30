package com.cyberscan.app.ui.hud

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.cyberscan.app.domain.model.ScanPhase
import com.cyberscan.app.ui.theme.AlertOrange
import com.cyberscan.app.ui.theme.SignalCyan
import com.cyberscan.app.ui.theme.VoidBlack

@Composable
fun ActionBar(
    phase: ScanPhase,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = phase == ScanPhase.Calibrating || phase == ScanPhase.Scanning
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (phase is ScanPhase.Failed) {
            Button(
                onClick = onRetry,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = AlertOrange, contentColor = VoidBlack),
            ) { Text("RETRY LINK") }
        } else {
            Button(
                onClick = onStart,
                enabled = !active,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(containerColor = SignalCyan, contentColor = VoidBlack),
            ) { Text("INITIATE SCAN") }
        }
        OutlinedButton(
            onClick = onStop,
            enabled = active,
            modifier = Modifier.weight(.65f).heightIn(min = 52.dp),
            shape = RectangleShape,
            border = BorderStroke(1.dp, AlertOrange),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertOrange),
        ) { Text("ABORT") }
    }
}

