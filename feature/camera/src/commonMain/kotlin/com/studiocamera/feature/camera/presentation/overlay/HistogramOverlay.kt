package com.studiocamera.feature.camera.presentation.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp

@Composable
fun HistogramOverlay(
    redHistogram: IntArray,
    greenHistogram: IntArray,
    blueHistogram: IntArray,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(160.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height

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
        }
    }
}
