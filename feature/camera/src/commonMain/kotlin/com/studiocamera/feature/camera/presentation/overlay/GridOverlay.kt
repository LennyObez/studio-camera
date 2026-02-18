package com.studiocamera.feature.camera.presentation.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun GridOverlay(
    gridType: GridType,
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.5f)
) {
    if (gridType == GridType.None) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val strokePx = 1.dp.toPx()

        when (gridType) {
            GridType.RuleOfThirds -> {
                drawLine(color, Offset(w / 3f, 0f), Offset(w / 3f, h), strokePx)
                drawLine(color, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokePx)
                drawLine(color, Offset(0f, h / 3f), Offset(w, h / 3f), strokePx)
                drawLine(color, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokePx)
            }
            GridType.Grid2x2 -> {
                drawLine(color, Offset(w / 2f, 0f), Offset(w / 2f, h), strokePx)
                drawLine(color, Offset(0f, h / 2f), Offset(w, h / 2f), strokePx)
            }
            GridType.Grid6x4 -> {
                for (i in 1..5) {
                    val x = w * i / 6f
                    drawLine(color, Offset(x, 0f), Offset(x, h), strokePx)
                }
                for (i in 1..3) {
                    val y = h * i / 4f
                    drawLine(color, Offset(0f, y), Offset(w, y), strokePx)
                }
            }
            GridType.CenterCross -> {
                val crossSize = minOf(w, h) * 0.05f
                val cx = w / 2f
                val cy = h / 2f
                drawLine(color, Offset(cx - crossSize, cy), Offset(cx + crossSize, cy), strokePx)
                drawLine(color, Offset(cx, cy - crossSize), Offset(cx, cy + crossSize), strokePx)
                val faintColor = color.copy(alpha = 0.2f)
                drawLine(faintColor, Offset(cx, 0f), Offset(cx, h), strokePx)
                drawLine(faintColor, Offset(0f, cy), Offset(w, cy), strokePx)
            }
            GridType.GoldenRatio -> {
                val phi = 1.618f
                val x1 = w / (1f + phi)
                val x2 = w - x1
                val y1 = h / (1f + phi)
                val y2 = h - y1
                drawLine(color, Offset(x1, 0f), Offset(x1, h), strokePx)
                drawLine(color, Offset(x2, 0f), Offset(x2, h), strokePx)
                drawLine(color, Offset(0f, y1), Offset(w, y1), strokePx)
                drawLine(color, Offset(0f, y2), Offset(w, y2), strokePx)
            }
            GridType.Diagonal -> {
                drawLine(color, Offset(0f, 0f), Offset(w, h), strokePx)
                drawLine(color, Offset(w, 0f), Offset(0f, h), strokePx)
            }
            GridType.Spiral -> drawGoldenSpiral(color, strokePx)
            GridType.Ratio16x9 -> drawAspectRatioGuide(16f / 9f, color, strokePx)
            GridType.Ratio9x16 -> drawAspectRatioGuide(9f / 16f, color, strokePx)
            GridType.Ratio4x3 -> drawAspectRatioGuide(4f / 3f, color, strokePx)
            GridType.Ratio3x2 -> drawAspectRatioGuide(3f / 2f, color, strokePx)
            else -> { /* GridType.None handled by early return above */ }
        }
    }
}

private fun DrawScope.drawAspectRatioGuide(
    targetRatio: Float,
    color: Color,
    strokeWidth: Float
) {
    val w = size.width
    val h = size.height
    val currentRatio = w / h

    val guideRect: Rect = if (currentRatio > targetRatio) {
        // Current is wider than target — pillarbox (vertical bars on sides)
        val guideWidth = h * targetRatio
        val left = (w - guideWidth) / 2f
        Rect(left, 0f, left + guideWidth, h)
    } else {
        // Current is taller than target — letterbox (horizontal bars top/bottom)
        val guideHeight = w / targetRatio
        val top = (h - guideHeight) / 2f
        Rect(0f, top, w, top + guideHeight)
    }

    // Draw semi-transparent shading outside the guide area
    val shadingColor = Color.Black.copy(alpha = 0.3f)
    // Top bar
    if (guideRect.top > 0f) {
        drawRect(shadingColor, Offset(0f, 0f), Size(w, guideRect.top))
    }
    // Bottom bar
    if (guideRect.bottom < h) {
        drawRect(shadingColor, Offset(0f, guideRect.bottom), Size(w, h - guideRect.bottom))
    }
    // Left bar
    if (guideRect.left > 0f) {
        drawRect(shadingColor, Offset(0f, guideRect.top), Size(guideRect.left, guideRect.height))
    }
    // Right bar
    if (guideRect.right < w) {
        drawRect(shadingColor, Offset(guideRect.right, guideRect.top), Size(w - guideRect.right, guideRect.height))
    }

    // Draw the guide rectangle border
    drawRect(
        color = color,
        topLeft = Offset(guideRect.left, guideRect.top),
        size = Size(guideRect.width, guideRect.height),
        style = Stroke(width = strokeWidth * 2f)
    )
}

private fun DrawScope.drawGoldenSpiral(color: Color, strokeWidth: Float) {
    val w = size.width
    val h = size.height
    val phi = 1.618f
    val path = Path()

    // Draw Fibonacci rectangles and quarter-arc spiral
    // Start with the full rectangle and subdivide by golden ratio
    var left = 0f
    var top = 0f
    var rw = w
    var rh = h

    // Draw guide lines at golden ratio divisions
    val lineColor = color.copy(alpha = 0.3f)
    val x1 = w / phi
    val x2 = w - x1
    val y1 = h / phi
    val y2 = h - y1
    drawLine(lineColor, Offset(x1, 0f), Offset(x1, h), strokeWidth)
    drawLine(lineColor, Offset(x2, 0f), Offset(x2, h), strokeWidth)
    drawLine(lineColor, Offset(0f, y1), Offset(w, y1), strokeWidth)
    drawLine(lineColor, Offset(0f, y2), Offset(w, y2), strokeWidth)

    // Draw approximate spiral using quarter arcs
    val spiralPath = Path()
    var cx: Float
    var cy: Float
    var radius: Float

    // 6 iterations of golden spiral quarter-arcs
    var sl = 0f
    var st = 0f
    var sw = w
    var sh = h

    for (i in 0 until 6) {
        when (i % 4) {
            0 -> { // Right side
                val newW = sw / phi
                cx = sl + sw - newW
                cy = st + sh
                radius = sh
                spiralPath.arcTo(
                    rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius),
                    startAngleDegrees = 270f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = i == 0
                )
                sw = newW
            }
            1 -> { // Bottom
                val newH = sh / phi
                cy = st + sh - newH
                cx = sl
                radius = sw
                spiralPath.arcTo(
                    rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                sh = newH
            }
            2 -> { // Left side
                val newW = sw / phi
                cx = sl + newW
                cy = st
                radius = sh
                spiralPath.arcTo(
                    rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius),
                    startAngleDegrees = 90f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                sl += newW
                sw -= newW
            }
            3 -> { // Top
                val newH = sh / phi
                cx = sl + sw
                cy = st + newH
                radius = sw
                spiralPath.arcTo(
                    rect = Rect(cx - radius, cy - radius, cx + radius, cy + radius),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 90f,
                    forceMoveTo = false
                )
                st += newH
                sh -= newH
            }
        }
    }

    drawPath(spiralPath, color, style = Stroke(width = strokeWidth * 1.5f))
}
