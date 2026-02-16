package com.studiocamera.feature.camera.presentation.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun GridOverlay(
    gridType: GridType,
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.5f),
    strokeWidth: Float = 1f
) {
    if (gridType == GridType.None) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (gridType) {
            GridType.RuleOfThirds -> {
                // Vertical lines at 1/3 and 2/3
                drawLine(color, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth)
                drawLine(color, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth)
                // Horizontal lines at 1/3 and 2/3
                drawLine(color, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth)
                drawLine(color, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth)
            }
            GridType.CenterCross -> {
                val crossSize = minOf(w, h) * 0.05f
                val cx = w / 2f
                val cy = h / 2f
                // Horizontal crosshair
                drawLine(color, Offset(cx - crossSize, cy), Offset(cx + crossSize, cy), strokeWidth)
                // Vertical crosshair
                drawLine(color, Offset(cx, cy - crossSize), Offset(cx, cy + crossSize), strokeWidth)
                // Full center lines (faint)
                val faintColor = color.copy(alpha = 0.2f)
                drawLine(faintColor, Offset(cx, 0f), Offset(cx, h), strokeWidth)
                drawLine(faintColor, Offset(0f, cy), Offset(w, cy), strokeWidth)
            }
            GridType.GoldenRatio -> {
                val phi = 1.618f
                val x1 = w / (1f + phi)
                val x2 = w - x1
                val y1 = h / (1f + phi)
                val y2 = h - y1
                drawLine(color, Offset(x1, 0f), Offset(x1, h), strokeWidth)
                drawLine(color, Offset(x2, 0f), Offset(x2, h), strokeWidth)
                drawLine(color, Offset(0f, y1), Offset(w, y1), strokeWidth)
                drawLine(color, Offset(0f, y2), Offset(w, y2), strokeWidth)
            }
            GridType.None -> {}
        }
    }
}
