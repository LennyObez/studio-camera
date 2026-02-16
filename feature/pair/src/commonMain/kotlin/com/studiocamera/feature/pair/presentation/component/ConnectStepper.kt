package com.studiocamera.feature.pair.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.studiocamera.core.designsystem.theme.StudioColors
import com.studiocamera.feature.pair.domain.PairProgress
import com.studiocamera.feature.pair.domain.StepStatus

@Composable
fun ConnectStepper(
    progress: PairProgress,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Connecting...",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            progress.steps.forEachIndexed { index, step ->
                StepRow(
                    name = step.name,
                    status = step.status,
                    elapsedMs = step.elapsedMs,
                    errorMessage = step.errorMessage,
                    isLast = index == progress.steps.lastIndex
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val hasFailed = progress.steps.any { it.status == StepStatus.Failed }
                if (hasFailed) {
                    Button(onClick = onRetry) {
                        Text("Retry")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    name: String,
    status: StepStatus,
    elapsedMs: Long,
    errorMessage: String?,
    isLast: Boolean
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Step indicator
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val color by animateColorAsState(
                targetValue = when (status) {
                    StepStatus.Pending -> StudioColors.Disconnected
                    StepStatus.InProgress -> StudioColors.Primary
                    StepStatus.Completed -> StudioColors.Connected
                    StepStatus.Failed -> StudioColors.Error
                }
            )

            when (status) {
                StepStatus.InProgress -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = color
                    )
                }
                StepStatus.Completed -> {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Completed",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                StepStatus.Failed -> {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Failed",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                }
                StepStatus.Pending -> {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.3f))
                    )
                }
            }

            // Connecting line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(24.dp)
                        .background(
                            if (status == StepStatus.Completed) {
                                StudioColors.Connected.copy(alpha = 0.5f)
                            } else {
                                StudioColors.Disconnected.copy(alpha = 0.3f)
                            }
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Step content
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (status) {
                        StepStatus.Pending -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                if (elapsedMs > 0) {
                    Text(
                        text = "${elapsedMs}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.Error
                )
            }
        }
    }
}
