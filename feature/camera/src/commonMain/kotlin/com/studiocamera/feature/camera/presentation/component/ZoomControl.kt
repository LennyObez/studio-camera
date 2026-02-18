package com.studiocamera.feature.camera.presentation.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ZoomControl(
    zoomLevel: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSlider by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Slider (shown on long press / drag)
        if (showSlider) {
            Slider(
                value = zoomLevel,
                onValueChange = onZoomChange,
                onValueChangeFinished = { showSlider = false },
                valueRange = 1f..10f,
                modifier = Modifier
                    .width(200.dp)
                    .height(24.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Yellow,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )
        }

        // Preset buttons capsule
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .animateContentSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { showSlider = true },
                        onDragEnd = {},
                        onDragCancel = {},
                        onHorizontalDrag = { _, dragAmount ->
                            val delta = dragAmount / 200f
                            onZoomChange((zoomLevel + delta).coerceIn(1f, 10f))
                        }
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZoomPreset("0.5", 0.5f, zoomLevel, onZoomChange)
            ZoomPreset("1x", 1f, zoomLevel, onZoomChange)
            ZoomPreset("2", 2f, zoomLevel, onZoomChange)
            ZoomPreset("5", 5f, zoomLevel, onZoomChange)
        }
    }
}

@Composable
private fun ZoomPreset(
    label: String,
    targetZoom: Float,
    currentZoom: Float,
    onZoomChange: (Float) -> Unit
) {
    val isSelected = kotlin.math.abs(currentZoom - targetZoom) < 0.1f
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (isSelected) Color.Yellow else Color.Transparent)
            .clickable { onZoomChange(targetZoom) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = if (isSelected) Color.Black else Color.White
        )
    }
}
