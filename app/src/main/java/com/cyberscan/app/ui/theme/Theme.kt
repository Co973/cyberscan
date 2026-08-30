package com.cyberscan.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CyberColorScheme = darkColorScheme(
    primary = SignalCyan,
    secondary = DataGreen,
    error = AlertOrange,
    background = VoidBlack,
    surface = PanelBlack,
    onPrimary = VoidBlack,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun CyberScanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CyberColorScheme,
        typography = CyberTypography,
        content = content,
    )
}

