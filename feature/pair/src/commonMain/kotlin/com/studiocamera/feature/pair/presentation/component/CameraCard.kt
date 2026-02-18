package com.studiocamera.feature.pair.presentation.component

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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.studiocamera.core.common.formatRelativeTime
import com.studiocamera.core.designsystem.theme.StudioColors
import com.studiocamera.core.domain.model.CameraBrand
import com.studiocamera.core.domain.model.ConnectionState
import com.studiocamera.core.domain.model.PairedDevice

@Composable
fun CameraCard(
    device: PairedDevice,
    isConnected: Boolean,
    connectionState: ConnectionState,
    onReconnect: () -> Unit,
    onNavigateCamera: () -> Unit,
    onNavigateMedia: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        isConnected && connectionState == ConnectionState.Connected -> StudioColors.Connected
        connectionState == ConnectionState.Connecting ||
            connectionState == ConnectionState.Authenticating ||
            connectionState == ConnectionState.Binding -> StudioColors.Connecting
        connectionState == ConnectionState.Reconnecting -> StudioColors.Reconnecting
        else -> StudioColors.Disconnected
    }

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: brand icon, name, status dot, overflow menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand icon
                Surface(
                    shape = CircleShape,
                    color = brandColor(device.cameraBrand).copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "${device.cameraBrand.name} camera",
                        tint = brandColor(device.cameraBrand),
                        modifier = Modifier.padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = device.displayName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Status dot
                        Surface(
                            shape = CircleShape,
                            color = statusColor,
                            modifier = Modifier.size(8.dp)
                        ) {}
                    }
                    Text(
                        text = brandSubtitle(device),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (device.lastConnectedAt > 0L) {
                        val relativeTime = formatRelativeTime(device.lastConnectedAt)
                        if (relativeTime.isNotBlank()) {
                            Text(
                                text = if (isConnected) "Connected" else relativeTime,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isConnected) StudioColors.Connected else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Overflow menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Remove",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                onRemove()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNavigateCamera,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remote control")
                }

                OutlinedButton(
                    onClick = onNavigateMedia,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Import photos")
                }

                if (!isConnected) {
                    IconButton(onClick = onReconnect) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reconnect",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    // Rename dialog
    if (showRenameDialog) {
        RenameDeviceDialog(
            currentName = device.displayName,
            onConfirm = { newName ->
                showRenameDialog = false
                onRename(newName)
            },
            onDismiss = { showRenameDialog = false }
        )
    }
}

@Composable
private fun brandColor(brand: CameraBrand) = when (brand) {
    CameraBrand.Sony -> StudioColors.Primary
    CameraBrand.Canon -> StudioColors.Error
    CameraBrand.Nikon -> StudioColors.Connecting
    CameraBrand.Fujifilm -> StudioColors.Connected
    CameraBrand.Panasonic -> StudioColors.Primary
    CameraBrand.OmSystem -> StudioColors.Secondary
    CameraBrand.Unknown -> StudioColors.Disconnected
}

private fun brandSubtitle(device: PairedDevice): String {
    val brandName = when (device.cameraBrand) {
        CameraBrand.Sony -> "Sony"
        CameraBrand.Canon -> "Canon"
        CameraBrand.Nikon -> "Nikon"
        CameraBrand.Fujifilm -> "Fujifilm"
        CameraBrand.Panasonic -> "Panasonic/Lumix"
        CameraBrand.OmSystem -> "OM System"
        CameraBrand.Unknown -> "Camera"
    }
    return if (device.customName != null) {
        "$brandName - ${device.deviceName}"
    } else {
        brandName
    }
}
