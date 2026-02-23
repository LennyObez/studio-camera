package com.studiocamera.feature.camera.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.model.ImageFormat
import com.studiocamera.feature.camera.presentation.CaptureMode

@Composable
fun SideControlsLeft(
    cameraState: CameraState,
    captureMode: CaptureMode,
    onFormatClick: () -> Unit,
    onPhotoResolutionClick: () -> Unit,
    onVideoResolutionClick: () -> Unit,
    onFpsClick: () -> Unit,
    onHdrToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (captureMode == CaptureMode.Photo) {
            // Format pill
            SidePill(
                text = when (cameraState.imageFormat) {
                    ImageFormat.JPEG -> "JPEG"
                    ImageFormat.RAW -> "RAW"
                    ImageFormat.JPEG_RAW -> "J+R"
                },
                onClick = onFormatClick
            )
            // Resolution pill
            SidePill(
                text = cameraState.photoResolution,
                onClick = onPhotoResolutionClick
            )
        } else {
            // Video mode
            SidePill(
                text = if (cameraState.hdrEnabled) "HDR" else "SDR",
                isActive = cameraState.hdrEnabled,
                onClick = onHdrToggle
            )
            SidePill(
                text = cameraState.videoResolution,
                onClick = onVideoResolutionClick
            )
            SidePill(
                text = "${cameraState.videoFps}fps",
                onClick = onFpsClick
            )
        }
    }
}

@Composable
private fun SidePill(
    text: String,
    isActive: Boolean = false,
    onClick: () -> Unit = {}
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = if (isActive) Color.Yellow else Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
