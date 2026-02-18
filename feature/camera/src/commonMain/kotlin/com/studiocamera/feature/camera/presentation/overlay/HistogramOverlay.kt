package com.studiocamera.feature.camera.presentation.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp

/**
 * Displays an RGB histogram overlay.
 *
 * When real histogram data is provided (non-null arrays), renders actual channel distributions.
 * When no data is available (null arrays), renders a placeholder bell-curve visualization
 * to indicate where the histogram will appear once frame analysis is implemented.
 */
@Composable
fun HistogramOverlay(
    redHistogram: IntArray? = null,
    greenHistogram: IntArray? = null,
    blueHistogram: IntArray? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

            if (redHistogram != null && greenHistogram != null && blueHistogram != null) {
                // Real histogram data
                fun drawChannel(histogram: IntArray, color: Color) {
                    if (histogram.isEmpty()) return
                    val maxVal = histogram.max().coerceAtLeast(1)
                    val path = Path()
                    path.moveTo(0f, h)

                    for (i in histogram.indices) {
                        val x = (i.toFloat() / histogram.size) * w
                        val y = h - (histogram[i].toFloat() / maxVal) * h
                        path.lineTo(x, y)
                    }

                    path.lineTo(w, h)
                    path.close()
                    drawPath(path, color.copy(alpha = 0.4f), style = Fill)
                }

                drawChannel(redHistogram, Color.Red)
                drawChannel(greenHistogram, Color.Green)
                drawChannel(blueHistogram, Color.Blue)
            } else {
                // Placeholder bell-curve visualization
                val channels = listOf(
                    Color.Red.copy(alpha = 0.5f),
                    Color.Green.copy(alpha = 0.5f),
                    Color.Blue.copy(alpha = 0.5f)
                )
                val offsets = listOf(0.4f, 0.5f, 0.6f)

                channels.forEachIndexed { idx, color ->
                    val path = Path()
                    val steps = 64
                    val center = offsets[idx]

                    path.moveTo(0f, h)
                    for (i in 0..steps) {
                        val x = w * i / steps
                        val t = i.toFloat() / steps
                        val dist = (t - center) * 3f
                        val y = h - h * 0.8f *
                            kotlin.math.exp((-dist * dist).toDouble()).toFloat()
                        path.lineTo(x, y)
                    }
                    path.lineTo(w, h)
                    path.close()

                    drawPath(path, color, style = Fill)
                }
            }
        }
    }
}
