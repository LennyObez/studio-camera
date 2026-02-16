package com.studiocamera.feature.pair.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific QR code scanner overlay.
 *
 * Android: CameraX PreviewView + ML Kit BarcodeScanner
 * iOS: AVCaptureSession + AVCaptureMetadataOutput
 *
 * UI: semi-transparent overlay with viewfinder cutout, corner brackets, close button.
 */
@Composable
expect fun QrScanner(
    onQrCodeScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
)
