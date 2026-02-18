package com.studiocamera.feature.camera.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.model.ConnectionState

@Composable
fun CameraTopBar(
    cameraState: CameraState,
    connectionState: ConnectionState,
    recordingSeconds: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Connection status dot
        val dotColor = when (connectionState) {
            ConnectionState.Connected -> Color(0xFF4CAF50)
            ConnectionState.Connecting, ConnectionState.Binding -> Color(0xFFFFC107)
            else -> Color(0xFFF44336)
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape)
        )

        // Recording indicator
        if (cameraState.isRecording) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.Red, CircleShape)
            )
            val mins = recordingSeconds / 60
            val secs = recordingSeconds % 60
            Text(
                "%02d:%02d".format(mins, secs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Red
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        // Battery
        if (cameraState.batteryPercent >= 0) {
            Icon(
                imageVector = Icons.Default.BatteryFull,
                contentDescription = "Battery",
                modifier = Modifier.size(14.dp),
                tint = Color.White
            )
            Text(
                "${cameraState.batteryPercent}%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}
