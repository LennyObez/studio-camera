package com.studiocamera.feature.camera.presentation.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val MIN_FRAME_INTERVAL_MS = 33L // ~30fps cap

@Composable
actual fun LiveViewSurface(
    frameFlow: Flow<ByteArray>,
    modifier: Modifier,
    onAspectRatioChange: (Float) -> Unit
) {
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

    var imageView by remember { mutableStateOf<ImageView?>(null) }

    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.BLACK)
            }.also { imageView = it }
        },
        modifier = modifier
    )

    LaunchedEffect(frameFlow, retryTrigger) {
        // Wait for AndroidView to be created before starting collection
        var view: ImageView? = imageView
        if (view == null) {
            kotlinx.coroutines.delay(50)
            view = imageView
        }
        if (view == null) return@LaunchedEffect

        var previousBitmap: Bitmap? = null
        try {
            withContext(Dispatchers.Default) {
                var lastFrameTime = 0L
                val options = BitmapFactory.Options()
                var receivedFrames = false

                frameFlow.collect { frameBytes ->
                    receivedFrames = true
                    val now = System.currentTimeMillis()
                    if (now - lastFrameTime < MIN_FRAME_INTERVAL_MS) return@collect
                    lastFrameTime = now

                    val bitmap = try {
                        BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size, options)
                    } catch (_: IllegalArgumentException) {
                        null
                    }

                    if (bitmap != null) {
                        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                        // Ensure callback is made on UI thread
                        view.post {
                            onAspectRatioChange(ratio)
                        }
                        
                        val old = previousBitmap
                        previousBitmap = bitmap
                        view.post {
                            view.setImageBitmap(bitmap)
                            old?.recycle()
                        }
                    }
                }

                // Flow completed normally without emitting — brand not supported or stream ended
                if (!receivedFrames) {
                    streamError = "Live view not available for this camera"
                }
            }
        } catch (e: CancellationException) {
            previousBitmap?.recycle()
            throw e
        } catch (e: Exception) {
            previousBitmap?.recycle()
            streamError = e.message ?: "Stream failed"
        }
    }
}
