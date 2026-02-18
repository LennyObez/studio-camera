package com.studiocamera.feature.camera.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.model.ImageFormat
import com.studiocamera.feature.camera.presentation.CaptureMode
import com.studiocamera.feature.camera.presentation.SelectorType
import com.studiocamera.feature.camera.presentation.overlay.GridType
import com.studiocamera.feature.camera.presentation.overlay.OverlayConfig

data class ControlStripItem(
    val selectorType: SelectorType,
    val label: String,
    val value: String,
    val isEnabled: Boolean = true
)

@Composable
fun ControlStrip(
    cameraState: CameraState,
    captureMode: CaptureMode,
    overlayConfig: OverlayConfig,
    isFlipped: Boolean,
    isInverted: Boolean,
    activeSelector: SelectorType?,
    onItemClick: (SelectorType) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = buildControlStripItems(cameraState, captureMode, overlayConfig, isFlipped, isInverted)

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(items, key = { it.selectorType.name }) { item ->
            ControlStripChip(
                label = item.label,
                value = item.value,
                isActive = activeSelector == item.selectorType,
                isEnabled = item.isEnabled,
                onClick = { if (item.isEnabled) onItemClick(item.selectorType) }
            )
        }
    }
}

@Composable
private fun ControlStripChip(
    label: String,
    value: String,
    isActive: Boolean,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val alpha = if (isEnabled) 1.0f else 0.4f
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.labelSmall,
        color = (if (isActive) Color.Black else Color.White).copy(alpha = alpha),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isActive) Color.Yellow.copy(alpha = alpha)
                else Color.White.copy(alpha = (if (isEnabled) 0.15f else 0.05f))
            )
            .border(
                width = 1.dp,
                color = if (isActive) Color.Transparent else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

private fun buildControlStripItems(
    state: CameraState,
    mode: CaptureMode,
    overlayConfig: OverlayConfig,
    isFlipped: Boolean,
    isInverted: Boolean
): List<ControlStripItem> {
    val items = mutableListOf<ControlStripItem>()

    items += ControlStripItem(SelectorType.WhiteBalance, "WB", state.whiteBalance)

    if (mode == CaptureMode.Photo) {
        items += ControlStripItem(
            SelectorType.Format, "FMT",
            when (state.imageFormat) {
                ImageFormat.JPEG -> "JPEG"
                ImageFormat.RAW -> "RAW"
                ImageFormat.JPEG_RAW -> "J+R"
            }
        )
    } else {
        items += ControlStripItem(
            SelectorType.VideoResolution, "RES", state.videoResolution
        )
        items += ControlStripItem(
            SelectorType.VideoFps, "FPS", "${state.videoFps}"
        )
    }

    items += ControlStripItem(
        SelectorType.Grid, "Grid",
        if (overlayConfig.gridType == GridType.None) "Off" else overlayConfig.gridType.label
    )

    items += ControlStripItem(SelectorType.ExposureMode, "Mode", state.exposureMode)
    items += ControlStripItem(SelectorType.Aperture, "f/", "%.1f".format(state.aperture))
    
    val formattedSs = state.shutterSpeed.let { if (!it.contains("/") && !it.endsWith("\"")) "$it\"" else it }
    items += ControlStripItem(SelectorType.ShutterSpeed, "SS", formattedSs)
    items += ControlStripItem(SelectorType.ISO, "ISO", state.iso.toString())
    
    val evEnabled = state.exposureMode != "M"
    items += ControlStripItem(SelectorType.EV, "EV", "%+.1f".format(state.ev), evEnabled)

    return items
}
