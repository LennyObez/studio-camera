package com.studiocamera.feature.camera.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.studiocamera.core.designsystem.component.ConnectionGate
import com.studiocamera.core.designsystem.theme.StudioColors
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.repository.CameraRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun CameraScreen(
    connectionState: ConnectionState,
    onNavigateToPair: () -> Unit,
    modifier: Modifier = Modifier
) {
    ConnectionGate(
        connectionState = connectionState,
        featureName = "Camera",
        onNavigateToPair = onNavigateToPair,
        modifier = modifier
    ) {
        CameraContent()
    }
}

@Composable
private fun CameraContent() {
    val cameraRepository: CameraRepository = koinInject()
    val cameraState by cameraRepository.cameraState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Recording timer
    var recordingSeconds = remember { androidx.compose.runtime.mutableIntStateOf(0) }

    LaunchedEffect(cameraState.isRecording) {
        if (cameraState.isRecording) {
            recordingSeconds.intValue = 0
            while (isActive) {
                delay(1000)
                recordingSeconds.intValue++
            }
        } else {
            recordingSeconds.intValue = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioColors.Surface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Battery
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatteryFull,
                        contentDescription = "Battery",
                        modifier = Modifier.size(16.dp),
                        tint = StudioColors.Connected
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("100%", style = MaterialTheme.typography.labelSmall)
                }

                // Recording indicator
                if (cameraState.isRecording) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(StudioColors.RecordingRed, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val mins = recordingSeconds.intValue / 60
                        val secs = recordingSeconds.intValue % 60
                        Text(
                            "REC %02d:%02d".format(mins, secs),
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioColors.RecordingRed
                        )
                    }
                }

                // Storage
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SdStorage,
                        contentDescription = "Storage",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("0.0 / 64 GB", style = MaterialTheme.typography.labelSmall)
                }
            }

            // Live view area (placeholder - will be replaced with native surface)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Live View",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.Gray
                    )
                    Text(
                        text = "(Mock Mode - No video feed)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.DarkGray
                    )
                }
            }

            // Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioColors.Surface)
                    .padding(16.dp)
            ) {
                // Exposure controls from camera state
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ExposureChip("ISO", cameraState.iso.toString())
                    ExposureChip("SS", cameraState.shutterSpeed)
                    ExposureChip("f/", "%.1f".format(cameraState.aperture))
                    ExposureChip("EV", "%+.1f".format(cameraState.ev))
                    ExposureChip(
                        "AF",
                        if (cameraState.isAutoFocus) "ON" else "MF"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Capture/Record buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Record button
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                if (cameraState.isRecording) {
                                    val result = cameraRepository.stopRecording()
                                    if (result is ApiResult.Success) {
                                        snackbarHostState.showSnackbar("Recording saved: ${result.data}")
                                    }
                                } else {
                                    cameraRepository.startRecording()
                                }
                            }
                        },
                        containerColor = if (cameraState.isRecording) StudioColors.RecordingRed else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (cameraState.isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = if (cameraState.isRecording) "Stop recording" else "Start recording",
                            tint = if (cameraState.isRecording) Color.White else StudioColors.RecordingRed
                        )
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    // Capture button
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                val result = cameraRepository.capturePhoto()
                                if (result is ApiResult.Success) {
                                    snackbarHostState.showSnackbar("Photo captured: ${result.data}")
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Capture photo",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ExposureChip(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
