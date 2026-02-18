package com.studiocamera.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.studiocamera.core.designsystem.theme.StudioColors
import com.studiocamera.core.domain.model.ConnectionState

@Composable
fun ConnectionChip(
    state: ConnectionState,
    deviceName: String?,
    onRetryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (state) {
        ConnectionState.Disconnected -> StudioColors.Disconnected to "Not connected"
        ConnectionState.Connecting -> StudioColors.Connecting to "Connecting..."
        ConnectionState.Authenticating -> StudioColors.Connecting to "Authenticating..."
        ConnectionState.Binding -> StudioColors.Connecting to "Binding..."
        ConnectionState.Connected -> StudioColors.Connected to "Connected to ${deviceName ?: "device"}"
        ConnectionState.Reconnecting -> StudioColors.Reconnecting to "Reconnecting..."
        ConnectionState.Failed -> StudioColors.Error to "Connection failed"
    }

    val animatedColor by animateColorAsState(targetValue = color)

    val isPulsing = state == ConnectionState.Connecting ||
            state == ConnectionState.Authenticating ||
            state == ConnectionState.Binding

    val isSpinning = state == ConnectionState.Reconnecting

    val chipModifier = if (state == ConnectionState.Failed && onRetryClick != null) {
        modifier.clickable { onRetryClick() }
    } else {
        modifier
    }

    Row(
        modifier = chipModifier
            .clip(RoundedCornerShape(16.dp))
            .background(animatedColor.copy(alpha = 0.15f))
            .semantics { contentDescription = text }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isSpinning) {
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                color = animatedColor,
                strokeWidth = 2.dp
            )
        } else {
            val dotAlpha = if (isPulsing) {
                val transition = rememberInfiniteTransition()
                val alpha by transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                alpha
            } else {
                1f
            }

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(dotAlpha)
                    .background(animatedColor, CircleShape)
            )
        }

        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = animatedColor
        )
    }
}
