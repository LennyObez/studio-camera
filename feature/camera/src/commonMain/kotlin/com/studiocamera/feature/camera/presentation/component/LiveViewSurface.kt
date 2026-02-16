package com.studiocamera.feature.camera.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific live view surface.
 * Uses native video rendering (NOT Compose Canvas) to avoid recomposition jank.
 *
 * Android: SurfaceView/TextureView via AndroidView interop
 * iOS: UIView via UIKitView interop
 */
@Composable
expect fun LiveViewSurface(
    streamUrl: String,
    accessToken: String?,
    modifier: Modifier = Modifier
)
