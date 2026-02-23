package com.studiocamera.feature.camera.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.studiocamera.core.domain.model.CameraState
import com.studiocamera.core.domain.model.FlashMode
import com.studiocamera.core.domain.model.ImageFormat
import com.studiocamera.feature.camera.presentation.CaptureMode
import com.studiocamera.feature.camera.presentation.CameraScreenState
import com.studiocamera.feature.camera.presentation.SelectorType
import com.studiocamera.feature.camera.presentation.overlay.GridType
import com.studiocamera.feature.camera.presentation.overlay.OverlayConfig
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValueSelector(
    selectorType: SelectorType,
    cameraState: CameraState,
    screenState: CameraScreenState,
    onDismiss: () -> Unit,
    onUpdateSetting: (
        iso: Int?, shutterSpeed: String?, aperture: Float?, ev: Float?,
        flashMode: FlashMode?, imageFormat: ImageFormat?,
        videoResolution: String?, videoFps: Int?
    ) -> Unit,
    onUpdateScreenState: (CameraScreenState) -> Unit,
    onUpdateWhiteBalance: (String) -> Unit = {},
    onUpdateExposureMode: (String) -> Unit = {},
    onUpdatePhotoResolution: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black.copy(alpha = 0.85f),
        contentColor = Color.White,
        scrimColor = Color.Black.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            when (selectorType) {
                SelectorType.ISO -> IsoSelector(cameraState) { iso ->
                    onUpdateSetting(iso, null, null, null, null, null, null, null)
                }
                SelectorType.ShutterSpeed -> ShutterSpeedSelector(cameraState) { ss ->
                    onUpdateSetting(null, ss, null, null, null, null, null, null)
                }
                SelectorType.Aperture -> ApertureSelector(cameraState) { av ->
                    onUpdateSetting(null, null, av, null, null, null, null, null)
                }
                SelectorType.EV -> EvSelector(cameraState) { ev ->
                    onUpdateSetting(null, null, null, ev, null, null, null, null)
                }
                SelectorType.WhiteBalance -> OptionListSelector(
                    title = "White balance",
                    options = cameraState.availableWhiteBalance.ifEmpty { listOf("Auto", "Daylight", "Cloudy", "Tungsten", "Fluorescent", "Shade", "Flash") },
                    selected = cameraState.whiteBalance
                ) { selected -> onUpdateWhiteBalance(selected) }
                SelectorType.Flash -> OptionListSelector(
                    title = "Flash",
                    options = cameraState.availableFlashModes.ifEmpty { FlashMode.entries.map { it.name } },
                    selected = cameraState.flashMode.name
                ) { selected ->
                    val mode = FlashMode.entries.find { it.name == selected } ?: FlashMode.Off
                    onUpdateSetting(null, null, null, null, mode, null, null, null)
                }
                SelectorType.Format -> OptionListSelector(
                    title = "Image format",
                    options = cameraState.availableImageFormats.ifEmpty { listOf("JPEG", "RAW", "JPEG+RAW") },
                    selected = when (cameraState.imageFormat) {
                        ImageFormat.JPEG -> "JPEG"
                        ImageFormat.RAW -> "RAW"
                        ImageFormat.JPEG_RAW -> "JPEG+RAW"
                    }
                ) { selected ->
                    val fmt = when (selected) {
                        "RAW" -> ImageFormat.RAW
                        "JPEG+RAW" -> ImageFormat.JPEG_RAW
                        else -> ImageFormat.JPEG
                    }
                    onUpdateSetting(null, null, null, null, null, fmt, null, null)
                }
                SelectorType.Grid -> OptionListSelector(
                    title = "Grid overlay",
                    options = GridType.entries.map { it.label },
                    selected = screenState.overlayConfig.gridType.label
                ) { selected ->
                    val gridType = GridType.entries.find { it.label == selected } ?: GridType.None
                    onUpdateScreenState(
                        screenState.copy(overlayConfig = screenState.overlayConfig.copy(gridType = gridType))
                    )
                }
                SelectorType.ExposureMode -> OptionListSelector(
                    title = "Exposure mode",
                    options = cameraState.availableExposureMode.ifEmpty { listOf("P", "A", "S", "M") },
                    selected = cameraState.exposureMode
                ) { selected -> onUpdateExposureMode(selected) }
                SelectorType.Timer -> OptionListSelector(
                    title = "Self timer",
                    options = listOf("Off", "3s", "5s", "10s"),
                    selected = when (screenState.timerSeconds) {
                        3 -> "3s"; 5 -> "5s"; 10 -> "10s"; else -> "Off"
                    }
                ) { selected ->
                    val seconds = when (selected) {
                        "3s" -> 3; "5s" -> 5; "10s" -> 10; else -> 0
                    }
                    onUpdateScreenState(screenState.copy(timerSeconds = seconds))
                }
                SelectorType.AspectRatio -> OptionListSelector(
                    title = "Aspect ratio guide",
                    options = listOf("Off", "16:9", "9:16", "4:3", "3:2"),
                    selected = when (screenState.overlayConfig.gridType) {
                        GridType.Ratio16x9 -> "16:9"
                        GridType.Ratio9x16 -> "9:16"
                        GridType.Ratio4x3 -> "4:3"
                        GridType.Ratio3x2 -> "3:2"
                        else -> "Off"
                    }
                ) { selected ->
                    val gridType = when (selected) {
                        "16:9" -> GridType.Ratio16x9
                        "9:16" -> GridType.Ratio9x16
                        "4:3" -> GridType.Ratio4x3
                        "3:2" -> GridType.Ratio3x2
                        else -> GridType.None
                    }
                    onUpdateScreenState(
                        screenState.copy(overlayConfig = screenState.overlayConfig.copy(gridType = gridType))
                    )
                }
                SelectorType.VideoResolution -> OptionListSelector(
                    title = "Video resolution",
                    options = cameraState.availableVideoResolutions.ifEmpty { listOf("HD", "FHD", "4K") },
                    selected = cameraState.videoResolution
                ) { selected ->
                    onUpdateSetting(null, null, null, null, null, null, selected, null)
                }
                SelectorType.VideoFps -> OptionListSelector(
                    title = "Frame rate",
                    options = cameraState.availableVideoFps.map { it.toString() }.ifEmpty { listOf("24", "25", "30", "50", "60", "120") },
                    selected = cameraState.videoFps.toString()
                ) { selected ->
                    val fps = selected.toIntOrNull() ?: 30
                    onUpdateSetting(null, null, null, null, null, null, null, fps)
                }
                SelectorType.PhotoResolution -> OptionListSelector(
                    title = "Photo resolution",
                    options = cameraState.availablePhotoResolutions.ifEmpty { listOf("L", "M", "S") },
                    selected = cameraState.photoResolution
                ) { selected -> onUpdatePhotoResolution(selected) }
            }
        }
    }
}

@Composable
private fun IsoSelector(cameraState: CameraState, onUpdate: (Int) -> Unit) {
    val isoSteps = cameraState.availableIso.ifEmpty {
        listOf(
            50, 100, 125, 160, 200, 250, 320, 400, 500, 640, 800,
            1000, 1250, 1600, 2000, 2500, 3200, 4000, 5000, 6400,
            8000, 10000, 12800, 16000, 20000, 25600, 32000, 40000,
            51200, 64000, 80000, 102400, 204800
        )
    }
    val currentIndex = isoSteps.indexOf(cameraState.iso).coerceAtLeast(0)
    var sliderValue by remember { mutableStateOf(currentIndex.toFloat()) }

    Text("ISO", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
    Text(
        "ISO ${isoSteps[sliderValue.roundToInt()]}",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 16.dp)
    )
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onUpdate(isoSteps[sliderValue.roundToInt()]) },
        valueRange = 0f..(isoSteps.size - 1).toFloat(),
        steps = isoSteps.size - 2
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(isoSteps.first().toString(), style = MaterialTheme.typography.labelSmall)
        Text(isoSteps.last().toString(), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ShutterSpeedSelector(cameraState: CameraState, onUpdate: (String) -> Unit) {
    val speeds = cameraState.availableShutterSpeed.ifEmpty {
        listOf(
            "30\"", "25\"", "20\"", "15\"", "13\"", "10\"", "8\"", "6\"",
            "5\"", "4\"", "3.2\"", "2.5\"", "2\"", "1.6\"", "1.3\"", "1\"",
            "0.8\"", "0.6\"", "0.5\"", "1/3", "1/4", "1/5", "1/6", "1/8",
            "1/10", "1/13", "1/15", "1/20", "1/25", "1/30", "1/40", "1/50",
            "1/60", "1/80", "1/100", "1/125", "1/160", "1/200", "1/250",
            "1/320", "1/400", "1/500", "1/640", "1/800", "1/1000", "1/1250",
            "1/1600", "1/2000", "1/2500", "1/3200", "1/4000", "1/5000",
            "1/6400", "1/8000"
        )
    }.map { if (!it.contains("/") && !it.endsWith("\"")) "$it\"" else it }
    val currentSs = cameraState.shutterSpeed.let { if (!it.contains("/") && !it.endsWith("\"")) "$it\"" else it }
    val currentIndex = speeds.indexOf(currentSs).coerceAtLeast(0)
    var sliderValue by remember { mutableStateOf(currentIndex.toFloat()) }

    Text("Shutter speed", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
    Text(
        speeds[sliderValue.roundToInt()],
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 16.dp)
    )
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { 
            // Sony API expects "2", not "2\""
            val apiSpeed = speeds[sliderValue.roundToInt()].removeSuffix("\"")
            onUpdate(apiSpeed) 
        },
        valueRange = 0f..(speeds.size - 1).toFloat(),
        steps = speeds.size - 2
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(speeds.first(), style = MaterialTheme.typography.labelSmall)
        Text(speeds.last(), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ApertureSelector(cameraState: CameraState, onUpdate: (Float) -> Unit) {
    val apertures = cameraState.availableAperture.ifEmpty {
        listOf(1.4f, 2.0f, 2.8f, 4.0f, 5.6f, 8.0f, 11.0f, 16.0f)
    }
    val currentIndex = apertures.indexOfFirst {
        (it - cameraState.aperture) < 0.1f && (it - cameraState.aperture) > -0.1f
    }.coerceAtLeast(0)
    var sliderValue by remember { mutableStateOf(currentIndex.toFloat()) }

    Text("Aperture", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
    Text(
        "f/%.1f".format(apertures[sliderValue.roundToInt()]),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 16.dp)
    )
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onUpdate(apertures[sliderValue.roundToInt()]) },
        valueRange = 0f..(apertures.size - 1).toFloat(),
        steps = apertures.size - 2
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("f/${apertures.first()}", style = MaterialTheme.typography.labelSmall)
        Text("f/${apertures.last()}", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun EvSelector(cameraState: CameraState, onUpdate: (Float) -> Unit) {
    var sliderValue by remember { mutableStateOf(cameraState.ev) }

    Text("Exposure compensation", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
    Text(
        "%+.1f EV".format(sliderValue),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 16.dp)
    )
    Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onUpdate(sliderValue) },
        valueRange = -3f..3f
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("-3.0", style = MaterialTheme.typography.labelSmall)
        Text("0", style = MaterialTheme.typography.labelSmall)
        Text("+3.0", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OptionListSelector(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
    LazyColumn {
        items(options) { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(option) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = option == selected,
                    onClick = { onSelect(option) }
                )
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
