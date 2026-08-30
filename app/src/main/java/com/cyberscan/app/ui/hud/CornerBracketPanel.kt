package com.cyberscan.app.ui.hud

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.cyberscan.app.ui.theme.PanelBlack
import com.cyberscan.app.ui.theme.SignalCyanDim

@Composable
fun CornerBracketPanel(
    modifier: Modifier = Modifier,
    accent: Color = SignalCyanDim,
    padding: PaddingValues = PaddingValues(12.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.background(PanelBlack)) {
        Canvas(Modifier.matchParentSize()) {
            val corner = 16.dp.toPx()
            val width = 1.5.dp.toPx()
            val maxX = size.width
            val maxY = size.height
            listOf(
                Pair(Offset(0f, corner), Offset(0f, 0f)), Pair(Offset(0f, 0f), Offset(corner, 0f)),
                Pair(Offset(maxX - corner, 0f), Offset(maxX, 0f)), Pair(Offset(maxX, 0f), Offset(maxX, corner)),
                Pair(Offset(maxX, maxY - corner), Offset(maxX, maxY)), Pair(Offset(maxX, maxY), Offset(maxX - corner, maxY)),
                Pair(Offset(corner, maxY), Offset(0f, maxY)), Pair(Offset(0f, maxY), Offset(0f, maxY - corner)),
            ).forEach { (start, end) -> drawLine(accent, start, end, width) }
            drawRect(accent.copy(alpha = 0.18f), style = Stroke(0.5.dp.toPx()))
        }
        Box(Modifier.padding(padding), content = content)
    }
}

