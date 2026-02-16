package com.studiocamera.feature.camera.presentation.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Draws diagonal stripe pattern over overexposed pixels.
 * Input: 2D boolean array indicating overexposed pixels (downscaled).
 * Performance: Input should be aggressively downscaled (e.g., 160x120).
 */
@Composable
fun ZebraOverlay(
    overexposedMap: Array<BooleanArray>?,
    modifier: Modifier = Modifier,
    stripeColor: Color = Color.Red.copy(alpha = 0.6f),
    stripeSpacing: Float = 8f
) {
    if (overexposedMap == null || overexposedMap.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val mapH = overexposedMap.size
        val mapW = overexposedMap[0].size
        val scaleX = size.width / mapW
        val scaleY = size.height / mapH

        for (y in overexposedMap.indices) {
            for (x in overexposedMap[y].indices) {
                if (overexposedMap[y][x]) {
                    // Draw diagonal stripes in this cell
                    val cellX = x * scaleX
                    val cellY = y * scaleY
                    // Simple diagonal line per cell
                    drawLine(
                        color = stripeColor,
                        start = Offset(cellX, cellY),
                        end = Offset(cellX + scaleX, cellY + scaleY),
                        strokeWidth = 1.5f
                    )
                }
            }
        }
    }
}
