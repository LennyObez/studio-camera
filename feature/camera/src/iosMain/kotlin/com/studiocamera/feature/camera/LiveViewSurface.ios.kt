package com.studiocamera.feature.camera.presentation.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import org.koin.compose.koinInject

@Composable
actual fun LiveViewSurface(
    streamUrl: String,
    accessToken: String?,
    modifier: Modifier
) {
    val httpClient: HttpClient = koinInject()
    val decoder = remember { MjpegStreamDecoder(httpClient) }
    val scope = rememberCoroutineScope()
    var currentFrame by remember { mutableStateOf<ImageBitmap?>(null) }
    var streamJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(streamUrl) {
        streamJob = scope.launch(Dispatchers.Default) {
            decoder.streamFrames(streamUrl, accessToken).collect { frameBytes ->
                try {
                    val skiaImage = Image.makeFromEncoded(frameBytes)
                    currentFrame = skiaImage.toComposeImageBitmap()
                } catch (e: Exception) {
                    // Skip invalid frame
                }
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
