package com.studiocamera.feature.camera.presentation.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.studiocamera.feature.camera.presentation.CaptureMode

@Composable
fun CaptureButton(
    captureMode: CaptureMode,
    isRecording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val innerSize by animateDpAsState(
        targetValue = if (captureMode == CaptureMode.Video && isRecording) 24.dp else 62.dp,
        animationSpec = tween(200)
    )
    val innerCorner by animateDpAsState(
        targetValue = if (captureMode == CaptureMode.Video && isRecording) 6.dp else 31.dp,
        animationSpec = tween(200)
    )

    val description = when {
        captureMode == CaptureMode.Photo -> "Capture photo"
        isRecording -> "Stop recording"
        else -> "Start recording"
    }

    Box(
        modifier = modifier
            .size(74.dp)
            .background(Color.White.copy(alpha = 0.2f), CircleShape)
            .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        val innerColor = when {
            captureMode == CaptureMode.Photo -> Color.White
            else -> Color.Red
        }

        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(innerCorner))
                .background(innerColor)
        )
    }
}
