package com.studiocamera.feature.pair.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studiocamera.core.domain.session.ConnectionStateManager
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.feature.pair.presentation.component.ConnectStepper
import com.studiocamera.feature.pair.presentation.component.ConnectionStatusCard
import com.studiocamera.feature.pair.presentation.component.ManualEntryForm
import com.studiocamera.feature.pair.presentation.component.ModePicker
import com.studiocamera.feature.pair.presentation.component.NetworkStatusCard
import com.studiocamera.feature.pair.presentation.component.QrScanner
import com.studiocamera.feature.pair.presentation.component.TrustDialog
import org.koin.compose.koinInject

@Composable
fun PairScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateToMedia: () -> Unit,
    onOpenWifiSettings: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onRequestCameraPermission: (onGranted: () -> Unit) -> Unit = { it() },
    modifier: Modifier = Modifier,
    viewModel: PairViewModel = koinInject()
) {
    val state by viewModel.state.collectAsState()
    val connectionStateManager: ConnectionStateManager = koinInject()
    val connectionState by connectionStateManager.state.collectAsState()
    val connectedDevice by connectionStateManager.connectedDevice.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                PairSideEffect.NavigateToCamera -> onNavigateToCamera()
                PairSideEffect.NavigateToMedia -> onNavigateToMedia()
                is PairSideEffect.ShowSnackbar -> { /* handled by snackbar host */ }
                PairSideEffect.OpenWifiSettings -> onOpenWifiSettings()
                PairSideEffect.OpenAppSettings -> onOpenAppSettings()
                PairSideEffect.RequestCameraPermission -> {
                    onRequestCameraPermission { viewModel.onCameraPermissionGranted() }
                }
                PairSideEffect.RequestLocationPermission -> { /* platform-specific */ }
            }
        }
    }

    // QR Scanner overlay
    if (state.isScanning) {
        QrScanner(
            onQrCodeScanned = { rawPayload ->
                viewModel.onEvent(PairEvent.QrCodeScanned(rawPayload))
            },
            onClose = {
                viewModel.onEvent(PairEvent.DismissScanner)
            }
        )
        return
    }

    // Trust confirmation dialog
    if (state.showTrustDialog) {
        TrustDialog(
            fingerprint = state.trustFingerprint,
            onAccept = { viewModel.onEvent(PairEvent.TrustConfirmed(true)) },
            onReject = { viewModel.onEvent(PairEvent.TrustConfirmed(false)) }
        )
    }

    // Mode picker bottom sheet
    if (state.showModePicker) {
        ModePicker(
            onSelectLiveControl = { viewModel.onEvent(PairEvent.SelectLiveControl) },
            onSelectMediaBrowser = { viewModel.onEvent(PairEvent.SelectMediaBrowser) },
            onDismiss = { viewModel.onEvent(PairEvent.DismissModePicker) }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Network Status
        NetworkStatusCard(
            ssidName = state.ssidName,
            unavailableReason = state.ssidUnavailableReason,
            wifiConnected = state.wifiConnected,
            onOpenWifiSettings = { viewModel.onEvent(PairEvent.OpenWifiSettings) },
            onOpenAppSettings = { viewModel.onEvent(PairEvent.OpenAppSettings) }
        )

        // 2. Connection Status (when connected or paired)
        if (connectionState != ConnectionState.Disconnected || connectedDevice != null) {
            ConnectionStatusCard(
                connectionState = connectionState,
                deviceName = connectedDevice?.deviceName,
                lastConnectedAt = connectedDevice?.lastConnectedAt,
                onReconnect = { viewModel.onEvent(PairEvent.Reconnect) },
                onDisconnect = { viewModel.onEvent(PairEvent.Disconnect) }
            )
        }

        // 3. Pairing Progress (when pairing)
        if (state.isPairing) {
            ConnectStepper(
                progress = state.pairProgress,
                onCancel = { viewModel.onEvent(PairEvent.CancelPairing) },
                onRetry = { viewModel.onEvent(PairEvent.RetryPairing) }
            )
        }

        // 4. Error display
        if (state.lastError != null) {
            ErrorCard(
                message = state.lastError!!,
                onDismiss = { viewModel.onEvent(PairEvent.DismissError) },
                onRetry = { viewModel.onEvent(PairEvent.RetryPairing) }
            )
        }

        // 5. Scan QR Code button (primary CTA)
        if (!state.isPairing && connectionState == ConnectionState.Disconnected) {
            Button(
                onClick = { viewModel.onEvent(PairEvent.ScanQrCode) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan QR Code")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 6. Manual Entry
            ManualEntryForm(
                endpoint = state.manualEndpoint,
                bindToken = state.manualBindToken,
                isTokenVisible = state.isTokenVisible,
                onEndpointChanged = { viewModel.onEvent(PairEvent.ManualEndpointChanged(it)) },
                onTokenChanged = { viewModel.onEvent(PairEvent.ManualTokenChanged(it)) },
                onToggleVisibility = { viewModel.onEvent(PairEvent.ToggleTokenVisibility) },
                onConnect = { viewModel.onEvent(PairEvent.ConnectManual) }
            )

            // 7. Troubleshooting
            TroubleshootingSection(
                onOpenWifiSettings = { viewModel.onEvent(PairEvent.OpenWifiSettings) }
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
                Button(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun TroubleshootingSection(
    onOpenWifiSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (expanded) "Hide Troubleshooting" else "Troubleshooting Tips")
        }

        if (expanded) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "• Make sure your Studio Camera Box is powered on",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• Ensure your phone is on the same Wi-Fi network",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• The QR code expires after a few minutes — generate a new one if needed",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• Try moving closer to the device",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(onClick = onOpenWifiSettings) {
                    Text("Open Wi-Fi Settings")
                }
            }
        }
    }
}
