package com.studiocamera.feature.camera.presentation.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.studiocamera.feature.camera.presentation.CaptureMode

@Composable
fun ModeSwitcher(
    currentMode: CaptureMode,
    onModeChange: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val indicatorOffset by animateFloatAsState(
        targetValue = if (currentMode == CaptureMode.Photo) 0f else 1f,
        animationSpec = tween(200)
    )

    Column(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.4f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeLabel(
                text = "PHOTO",
                isSelected = currentMode == CaptureMode.Photo,
                onClick = { onModeChange(CaptureMode.Photo) }
            )
            ModeLabel(
                text = "VIDEO",
                isSelected = currentMode == CaptureMode.Video,
                onClick = { onModeChange(CaptureMode.Video) }
            )
        }

        // Animated underline indicator
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(100.dp)
                .height(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .offset(x = (indicatorOffset * 62).dp)
                    .width(38.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.Yellow)
            )
        }
    }
}

@Composable
private fun ModeLabel(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (isSelected) Color.Yellow else Color.White.copy(alpha = 0.6f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    )
}
