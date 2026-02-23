package com.studiocamera.feature.camera.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.Flow

@Composable
actual fun LiveViewSurface(
    frameFlow: Flow<ByteArray>,
    modifier: Modifier,
    onAspectRatioChange: (Float) -> Unit
) {
    // JVM stub — live view not available on desktop
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text("Live View not available on desktop", color = Color.White)
    }
}
