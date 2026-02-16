package com.studiocamera.feature.pair.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.dispatch.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun QrScanner(
    onQrCodeScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    val captureSession = remember { AVCaptureSession() }

    DisposableEffect(Unit) {
        setupCamera(captureSession, onQrCodeScanned)
        captureSession.startRunning()
        onDispose {
            captureSession.stopRunning()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        UIKitView(
            factory = {
                val view = UIView(frame = CGRectMake(0.0, 0.0, 400.0, 800.0))
                val previewLayer = AVCaptureVideoPreviewLayer(session = captureSession)
                previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
                previewLayer.frame = view.bounds
                view.layer.addSublayer(previewLayer)
                view
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        // Close button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close scanner",
                tint = Color.White
            )
        }
    }
}

private fun setupCamera(
    session: AVCaptureSession,
    onScanned: (String) -> Unit
) {
    val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) ?: return
    val input = try {
        AVCaptureDeviceInput(device = device, error = null)
    } catch (e: Exception) {
        return
    }

    if (session.canAddInput(input)) {
        session.addInput(input)
    }

    val metadataOutput = AVCaptureMetadataOutput()
    if (session.canAddOutput(metadataOutput)) {
        session.addOutput(metadataOutput)
        metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)

        val delegate = QrDelegate(onScanned)
        metadataOutput.setMetadataObjectsDelegate(delegate, queue = dispatch_get_main_queue())
    }
}

private class QrDelegate(
    private val onScanned: (String) -> Unit
) : NSObject(), platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol {
    private var hasScanned = false

    override fun captureOutput(
        output: platform.AVFoundation.AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: platform.AVFoundation.AVCaptureConnection
    ) {
        if (hasScanned) return
        for (obj in didOutputMetadataObjects) {
            val metadata = obj as? AVMetadataMachineReadableCodeObject ?: continue
            val value = metadata.stringValue ?: continue
            hasScanned = true
            onScanned(value)
            break
        }
    }
}
