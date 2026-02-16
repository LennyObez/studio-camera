package com.studiocamera.feature.camera.presentation.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

@Composable
fun SafeZoneOverlay(
    modifier: Modifier = Modifier,
    titleSafeColor: Color = Color.Yellow.copy(alpha = 0.4f),
    actionSafeColor: Color = Color.Cyan.copy(alpha = 0.3f),
    strokeWidth: Float = 1f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Title safe (80%)
        val titleMarginX = w * 0.1f
        val titleMarginY = h * 0.1f
        drawRect(
            color = titleSafeColor,
            topLeft = Offset(titleMarginX, titleMarginY),
            size = Size(w - 2 * titleMarginX, h - 2 * titleMarginY),
            style = Stroke(width = strokeWidth)
        )

        // Action safe (90%)
        val actionMarginX = w * 0.05f
        val actionMarginY = h * 0.05f
        drawRect(
            color = actionSafeColor,
            topLeft = Offset(actionMarginX, actionMarginY),
            size = Size(w - 2 * actionMarginX, h - 2 * actionMarginY),
            style = Stroke(width = strokeWidth)
        )
    }
}
