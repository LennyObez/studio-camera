package com.studiocamera.feature.pair.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studiocamera.core.designsystem.theme.StudioColors
import com.studiocamera.core.domain.model.ConnectionState

@Composable
fun ConnectionStatusCard(
    connectionState: ConnectionState,
    deviceName: String?,
    lastConnectedAt: Long?,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (connectionState) {
        ConnectionState.Connected -> StudioColors.Connected
        ConnectionState.Connecting,
        ConnectionState.Authenticating,
        ConnectionState.Binding -> StudioColors.Connecting
        ConnectionState.Reconnecting -> StudioColors.Reconnecting
        ConnectionState.Failed -> StudioColors.Error
        ConnectionState.Disconnected -> StudioColors.Disconnected
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = deviceName ?: "Unknown Device",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when (connectionState) {
                    ConnectionState.Connected -> "Connected"
                    ConnectionState.Connecting -> "Connecting..."
                    ConnectionState.Authenticating -> "Authenticating..."
                    ConnectionState.Binding -> "Binding..."
                    ConnectionState.Reconnecting -> "Reconnecting..."
                    ConnectionState.Failed -> "Connection failed"
                    ConnectionState.Disconnected -> "Disconnected"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (connectionState) {
                    ConnectionState.Connected -> {
                        OutlinedButton(onClick = onDisconnect) {
                            Text("Disconnect")
                        }
                    }
                    ConnectionState.Failed,
                    ConnectionState.Disconnected -> {
                        Button(onClick = onReconnect) {
                            Text("Reconnect")
                        }
                    }
                    else -> {} // Connecting states - no action
                }
            }
        }
    }
}
