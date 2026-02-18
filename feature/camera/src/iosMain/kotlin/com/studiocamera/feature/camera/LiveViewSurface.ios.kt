package com.studiocamera.feature.camera.presentation.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image

private const val MIN_FRAME_INTERVAL_MS = 33L // ~30fps cap

@Composable
actual fun LiveViewSurface(
    frameFlow: Flow<ByteArray>,
    modifier: Modifier,
    onAspectRatioChange: (Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var streamJob by remember { mutableStateOf<Job?>(null) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    if (streamError != null) {
        Box(
            modifier = modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VideocamOff,
                    contentDescription = "Stream unavailable",
                    modifier = Modifier.size(48.dp),
                    tint = Color.White.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Stream unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = {
                    streamError = null
                    retryTrigger++
                }) {
                    Text("Retry")
                }
            }
        }
        return
    }

    DisposableEffect(frameFlow, retryTrigger) {
        streamJob = scope.launch(Dispatchers.Default) {
            try {
                var lastFrameTime = 0L
                frameFlow.collect { frameBytes ->
                    val now = com.studiocamera.core.common.currentTimeMillis()
                    if (now - lastFrameTime < MIN_FRAME_INTERVAL_MS) return@collect
                    lastFrameTime = now
                    try {
                        val skiaImage = Image.makeFromEncoded(frameBytes)
                        onAspectRatioChange(skiaImage.width.toFloat() / skiaImage.height.toFloat())
                        currentFrame = skiaImage.toComposeImageBitmap()
                    } catch (_: Exception) {
                        // Skip invalid frame
                    }
                }
            } catch (_: CancellationException) {
                // Normal cancellation
            } catch (e: Exception) {
                streamError = e.message ?: "Stream failed"
            }
        }

        onDispose {
            streamJob?.cancel()
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        currentFrame?.let { frame ->
            Image(
                bitmap = frame,
                contentDescription = "Live view",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
