package com.studiocamera.feature.camera.presentation.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun SafeZoneOverlay(
    modifier: Modifier = Modifier,
    titleSafeColor: Color = Color.Yellow.copy(alpha = 0.4f),
    actionSafeColor: Color = Color.Cyan.copy(alpha = 0.3f)
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val strokePx = 1.dp.toPx()

        // Title safe (80%)
        val titleMarginX = w * 0.1f
        val titleMarginY = h * 0.1f
        drawRect(
            color = titleSafeColor,
            topLeft = Offset(titleMarginX, titleMarginY),
            size = Size(w - 2 * titleMarginX, h - 2 * titleMarginY),
            style = Stroke(width = strokePx)
        )

        // Action safe (90%)
        val actionMarginX = w * 0.05f
        val actionMarginY = h * 0.05f
        drawRect(
            color = actionSafeColor,
            topLeft = Offset(actionMarginX, actionMarginY),
            size = Size(w - 2 * actionMarginX, h - 2 * actionMarginY),
            style = Stroke(width = strokePx)
        )
    }
}

@Composable
fun SafeZone916Overlay(
    modifier: Modifier = Modifier,
    borderColor: Color = Color.Magenta.copy(alpha = 0.7f),
    shadingColor: Color = Color.Black.copy(alpha = 0.25f)
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val strokePx = 1.5f.dp.toPx()
        val targetRatio = 9f / 16f

        val currentRatio = w / h
        if (currentRatio > targetRatio) {
            // Pillarbox — 9:16 is narrower than the view
            val guideWidth = h * targetRatio
            val left = (w - guideWidth) / 2f
            // Left shading
            drawRect(shadingColor, Offset(0f, 0f), Size(left, h))
            // Right shading
            drawRect(shadingColor, Offset(left + guideWidth, 0f), Size(w - left - guideWidth, h))
            // Border
            drawRect(borderColor, Offset(left, 0f), Size(guideWidth, h), style = Stroke(strokePx))
        } else {
            // Letterbox — 9:16 is taller than the view
            val guideHeight = w / targetRatio
            val top = (h - guideHeight) / 2f
            // Top shading
            drawRect(shadingColor, Offset(0f, 0f), Size(w, top))
            // Bottom shading
            drawRect(shadingColor, Offset(0f, top + guideHeight), Size(w, h - top - guideHeight))
            // Border
            drawRect(borderColor, Offset(0f, top), Size(w, guideHeight), style = Stroke(strokePx))
        }
    }
}
