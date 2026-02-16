package com.studiocamera.feature.camera.presentation.component

import android.graphics.BitmapFactory
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    var streamJob: Job? = null

    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = modifier,
        update = { imageView ->
            streamJob?.cancel()
            streamJob = scope.launch(Dispatchers.IO) {
                decoder.streamFrames(streamUrl, accessToken).collect { frameBytes ->
                    val bitmap = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size)
                    if (bitmap != null) {
                        imageView.post { imageView.setImageBitmap(bitmap) }
                    }
                }
            }
        }
    )

    DisposableEffect(streamUrl) {
        onDispose {
            streamJob?.cancel()
        }
    }
}
