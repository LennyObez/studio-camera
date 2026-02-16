package com.studiocamera.feature.pair.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studiocamera.core.designsystem.theme.StudioColors

@Composable
fun NetworkStatusCard(
    ssidName: String?,
    unavailableReason: String?,
    wifiConnected: Boolean,
    onOpenWifiSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (wifiConnected) {
                StudioColors.Connected.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (wifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = if (wifiConnected) "Wi-Fi connected" else "Wi-Fi disconnected",
                    modifier = Modifier.size(24.dp),
                    tint = if (wifiConnected) StudioColors.Connected else StudioColors.Disconnected
                )

                Column {
                    if (wifiConnected) {
                        if (ssidName != null) {
                            Text(
                                text = "Connected to $ssidName",
                                style = MaterialTheme.typography.titleMedium
                            )
                        } else {
                            Text(
                                text = "Connected to Wi-Fi (name unavailable)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (unavailableReason != null) {
                                Text(
                                    text = unavailableReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Wi-Fi Not Connected",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = unavailableReason ?: "Connect to the same network as your device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!wifiConnected || (unavailableReason != null && ssidName == null)) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!wifiConnected) {
                        TextButton(onClick = onOpenWifiSettings) {
                            Text("Open Wi-Fi Settings")
                        }
                    }
                    if (unavailableReason != null && ssidName == null && wifiConnected) {
                        TextButton(onClick = onOpenAppSettings) {
                            Text("Grant Permission")
                        }
                    }
                }
            }
        }
    }
}
