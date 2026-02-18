package com.studiocamera.feature.camera.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.studiocamera.core.domain.model.FlashMode

@Composable
fun SideControlsRight(
    flashMode: FlashMode,
    timerSeconds: Int,
    onFlashClick: () -> Unit,
    onTimerClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SideButton(
            icon = when (flashMode) {
                FlashMode.Off -> Icons.Default.FlashOff
                FlashMode.Auto -> Icons.Default.FlashAuto
                FlashMode.On -> Icons.Default.FlashOn
                FlashMode.RedEye -> Icons.Default.FlashOn
            },
            label = when (flashMode) {
                FlashMode.Off -> null
                FlashMode.Auto -> "A"
                FlashMode.On -> null
                FlashMode.RedEye -> "RE"
            },
            isActive = flashMode != FlashMode.Off,
            onClick = onFlashClick
        )

        SideButton(
            icon = Icons.Default.Timer,
            label = if (timerSeconds > 0) "${timerSeconds}s" else null,
            isActive = timerSeconds > 0,
            onClick = onTimerClick
        )

        SideButton(
            icon = Icons.Default.AspectRatio,
            onClick = onAspectRatioClick
        )
    }
}

@Composable
private fun SideButton(
    icon: ImageVector,
    label: String? = null,
    isActive: Boolean = false,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.4f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) Color.Yellow else Color.White,
            modifier = Modifier.size(22.dp)
        )
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) Color.Yellow else Color.White
            )
        }
    }
}
