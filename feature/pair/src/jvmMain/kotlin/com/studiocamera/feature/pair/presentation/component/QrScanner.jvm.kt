package com.studiocamera.feature.pair.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
actual fun QrScanner(
    onQrCodeScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    // JVM stub — QR scanning not available on desktop
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text("QR Scanner not available on desktop", color = Color.White)
    }
}
