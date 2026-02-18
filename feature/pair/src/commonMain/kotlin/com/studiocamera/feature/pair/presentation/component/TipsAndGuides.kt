package com.studiocamera.feature.pair.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.studiocamera.core.domain.model.CameraBrand

@Composable
fun TipsAndGuidesSection(
    pairedBrands: Set<CameraBrand>,
    onOpenWifiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Tips & guides",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        ConnectivityTipsCard(onOpenWifiSettings = onOpenWifiSettings)
        FocusGuideCard()
        BulbExposureGuideCard()
        PhotoQualityNotesCard(pairedBrands = pairedBrands)
    }
}

@Composable
private fun ExpandableGuideCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ConnectivityTipsCard(onOpenWifiSettings: () -> Unit) {
    ExpandableGuideCard(
        title = "Connectivity tips",
        icon = Icons.Default.Wifi
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TipItem(
                title = "Can't connect?",
                body = "Enable Wi-Fi on your camera, check the SSID matches, and ensure you're within close range."
            )
            TipItem(
                title = "Connection freezes?",
                body = "Restart Wi-Fi on your camera, then reconnect from the app."
            )
            TipItem(
                title = "Camera disconnects?",
                body = "Check your camera's auto-sleep settings. Keep the camera awake while using remote control."
            )
            TipItem(
                title = "Slow performance?",
                body = "Disable Bluetooth on your phone for better Wi-Fi throughput. Bluetooth and Wi-Fi share the same radio and can interfere.",
                highlight = true
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(onClick = onOpenWifiSettings) {
                Text("Open Wi-Fi settings")
            }
        }
    }
}

@Composable
private fun FocusGuideCard() {
    ExpandableGuideCard(
        title = "Focus guide",
        icon = Icons.Default.CameraAlt
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TipItem(
                title = "Manual focus",
                body = "Use live view zoom (2x/4x) for precise focus. Pinch to zoom in the live view."
            )
            TipItem(
                title = "AF modes",
                body = "Single-shot AF for stills, Continuous AF for moving subjects. Switch via the AF/MF button in camera controls."
            )
            TipItem(
                title = "Focus peaking",
                body = "Enable on your camera for edge highlighting. Toggle the focus peaking overlay in live view for on-screen assistance."
            )
            TipItem(
                title = "Touch AF",
                body = "Tap the live view to set focus point (brand-dependent). Works best with Single-shot AF."
            )
        }
    }
}

@Composable
private fun BulbExposureGuideCard() {
    ExpandableGuideCard(
        title = "Bulb exposure guide",
        icon = Icons.Default.Timer
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TipItem(
                title = "What is bulb mode?",
                body = "The shutter stays open as long as the button is held. Great for long exposures — star trails, light painting, night photography."
            )
            TipItem(
                title = "How to use",
                body = "Set your camera to BULB mode, then use the app's capture button: press to start the exposure, press again to stop."
            )
            TipItem(
                title = "Tips for best results",
                body = "Use a tripod to avoid camera shake. A remote trigger (this app!) helps avoid vibration from touching the camera. Use an ND filter for daylight long exposures."
            )
            TipItem(
                title = "Note",
                body = "Duration display varies by camera brand — some cameras show elapsed time on their screen during bulb exposure."
            )
        }
    }
}

@Composable
private fun PhotoQualityNotesCard(pairedBrands: Set<CameraBrand>) {
    ExpandableGuideCard(
        title = "Photo quality notes",
        icon = Icons.Default.HighQuality
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (CameraBrand.Sony in pairedBrands) {
                TipItem(
                    title = "Sony A7 series",
                    body = "No RAW download via Wi-Fi Direct. In RAW+JPEG mode the camera sends high-quality JPEG. In RAW-only mode it sends a 2MP JPEG thumbnail. Recommendation: shoot RAW+JPEG for best wireless transfer quality.",
                    highlight = true
                )
            }
            if (CameraBrand.Canon in pairedBrands) {
                TipItem(
                    title = "Canon",
                    body = "CCAPI supports JPEG and HEIF transfer. RAW transfer may be slow depending on model."
                )
            }
            if (CameraBrand.Panasonic in pairedBrands) {
                TipItem(
                    title = "Panasonic/Lumix",
                    body = "JPEG transfer works well. RAW transfer support varies by model and firmware version."
                )
            }
            TipItem(
                title = "General",
                body = "Wi-Fi transfer speed is limited (~5-15 MB/s). For bulk RAW transfer, use a card reader for fastest results."
            )
        }
    }
}

@Composable
private fun TipItem(
    title: String,
    body: String,
    highlight: Boolean = false
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
