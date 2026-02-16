package com.studiocamera.feature.mock.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.PairedDevice
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.domain.session.SessionManager
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

enum class MockMode {
    OfflineSimulator,
    MockServer
}

@Composable
fun MockScreen(
    modifier: Modifier = Modifier
) {
    val connectionStateManager: ConnectionStateManager = koinInject()
    val sessionManager: SessionManager = koinInject()
    val connectionState by connectionStateManager.state.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedMode by remember { mutableStateOf(MockMode.OfflineSimulator) }
    var mockServerUrl by remember { mutableStateOf("http://localhost:8080") }
    var isActivating by remember { mutableStateOf(false) }

    val isConnected = connectionState == ConnectionState.Connected

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Mock Mode",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Test the app without a real device",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Offline Simulator
        MockModeCard(
            icon = Icons.Default.PhoneAndroid,
            title = "Offline Simulator",
            description = "Simulates a connected device with sample data. Works in airplane mode.",
            isSelected = selectedMode == MockMode.OfflineSimulator,
            enabled = !isConnected,
            onClick = { selectedMode = MockMode.OfflineSimulator }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Mock Server
        MockModeCard(
            icon = Icons.Default.Cloud,
            title = "Mock Server",
            description = "Connect to a mock server implementing the Studio Camera protocol.",
            isSelected = selectedMode == MockMode.MockServer,
            enabled = !isConnected,
            onClick = { selectedMode = MockMode.MockServer }
        )

        if (selectedMode == MockMode.MockServer && !isConnected) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = mockServerUrl,
                onValueChange = { mockServerUrl = it },
                label = { Text("Mock Server URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Activate / Disconnect button
        if (isConnected) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        sessionManager.disconnect()
                        connectionStateManager.disconnect()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Disconnect Mock")
            }
        } else {
            Button(
                onClick = {
                    isActivating = true
                    scope.launch {
                        val mockDevice = PairedDevice(
                            deviceId = "mock-device-001",
                            deviceName = "Mock Studio Camera",
                            endpoint = if (selectedMode == MockMode.MockServer) mockServerUrl else "mock://localhost",
                            fingerprint = "MOCK",
                            lastConnectedAt = com.studiocamera.core.common.currentTimeMillis()
                        )
                        connectionStateManager.setConnectedDevice(mockDevice)
                        sessionManager.connect(mockDevice)
                        connectionStateManager.updateState(ConnectionState.Connected)
                        isActivating = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isActivating
            ) {
                if (isActivating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connecting...")
                } else {
                    Text("Activate Mock Mode")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isConnected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isConnected) {
                        "Mock mode active. Camera and Media tabs are now available."
                    } else if (selectedMode == MockMode.OfflineSimulator) {
                        "Tap 'Activate Mock Mode' to simulate a connected device with sample data."
                    } else {
                        "Enter mock server URL and tap 'Activate Mock Mode' to test the full protocol stack."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isConnected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun MockModeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = if (enabled) onClick else null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
