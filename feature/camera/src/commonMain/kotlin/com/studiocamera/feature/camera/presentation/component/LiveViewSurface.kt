package com.studiocamera.feature.camera.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow

/**
 * Platform-specific live view surface.
 * Uses native video rendering (NOT Compose Canvas) to avoid recomposition jank.
 *
 * Android: ImageView via AndroidView interop
 * iOS: UIKitView interop
 * JVM: Compose Image (Skia)
 */
@Composable
expect fun LiveViewSurface(
    frameFlow: Flow<ByteArray>,
    modifier: Modifier = Modifier,
    onAspectRatioChange: (Float) -> Unit = {}
)
