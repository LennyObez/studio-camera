package com.studiocamera.feature.camera.presentation.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Highlights in-focus areas with colored edge outlines.
 * Input: 2D boolean array indicating high-contrast (in-focus) pixels (downscaled).
 * Performance: Input should be aggressively downscaled (e.g., 160x120).
 */
@Composable
fun FocusPeakingOverlay(
    focusMap: Array<BooleanArray>?,
    modifier: Modifier = Modifier,
    peakingColor: Color = Color.Red
) {
    if (focusMap == null || focusMap.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val mapH = focusMap.size
        val mapW = focusMap[0].size
        val scaleX = size.width / mapW
        val scaleY = size.height / mapH

        for (y in focusMap.indices) {
            for (x in focusMap[y].indices) {
                if (focusMap[y][x]) {
                    val px = x * scaleX + scaleX / 2
                    val py = y * scaleY + scaleY / 2
                    drawCircle(
                        color = peakingColor.copy(alpha = 0.7f),
                        radius = scaleX / 2,
                        center = Offset(px, py)
                    )
                }
            }
        }
    }
}
