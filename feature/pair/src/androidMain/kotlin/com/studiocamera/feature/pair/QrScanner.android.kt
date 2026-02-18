package com.studiocamera.feature.pair.presentation.component

import android.annotation.SuppressLint
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("UnsafeOptInUsageError")
@Composable
actual fun QrScanner(
    onQrCodeScanned: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val scanned = remember { AtomicBoolean(false) }
    val cameraProviderRef = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    // Release camera when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            cameraProviderRef[0]?.unbindAll()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProviderRef[0] = cameraProvider
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val barcodeScanner = BarcodeScanning.getClient()

                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(1920, 1080),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null && !scanned.get()) {
                                    val inputImage = InputImage.fromMediaImage(
                                        mediaImage,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    barcodeScanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                if (barcode.format == Barcode.FORMAT_QR_CODE) {
                                                    val rawValue = barcode.rawValue
                                                    if (rawValue != null && scanned.compareAndSet(false, true)) {
                                                        onQrCodeScanned(rawValue)
                                                    }
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            ctx as androidx.lifecycle.LifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (e: Exception) {
                        co.touchlab.kermit.Logger.e("QrScanner") { "Camera bind failed: ${e.message}" }
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Viewfinder overlay with cutout
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cutoutSize = size.minDimension * 0.65f
            val cutoutLeft = (size.width - cutoutSize) / 2
            val cutoutTop = (size.height - cutoutSize) / 2
            val cornerRadius = 16.dp.toPx()

            // Semi-transparent overlay with cutout
            val cutoutPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(cutoutLeft, cutoutTop, cutoutLeft + cutoutSize, cutoutTop + cutoutSize),
                        cornerRadius = CornerRadius(cornerRadius)
                    )
                )
            }
            clipPath(cutoutPath, clipOp = ClipOp.Difference) {
                drawRect(Color.Black.copy(alpha = 0.6f))
            }

            // Corner brackets
            val bracketLength = cutoutSize * 0.15f
            val bracketStroke = 3.dp.toPx()
            val bracketColor = Color.White

            // Top-left corner
            drawLine(bracketColor, Offset(cutoutLeft, cutoutTop + cornerRadius), Offset(cutoutLeft, cutoutTop + bracketLength), Stroke(bracketStroke).width.let { bracketStroke })
            drawLine(bracketColor, Offset(cutoutLeft + cornerRadius, cutoutTop), Offset(cutoutLeft + bracketLength, cutoutTop), bracketStroke)

            // Top-right corner
            drawLine(bracketColor, Offset(cutoutLeft + cutoutSize, cutoutTop + cornerRadius), Offset(cutoutLeft + cutoutSize, cutoutTop + bracketLength), bracketStroke)
            drawLine(bracketColor, Offset(cutoutLeft + cutoutSize - cornerRadius, cutoutTop), Offset(cutoutLeft + cutoutSize - bracketLength, cutoutTop), bracketStroke)

            // Bottom-left corner
            drawLine(bracketColor, Offset(cutoutLeft, cutoutTop + cutoutSize - cornerRadius), Offset(cutoutLeft, cutoutTop + cutoutSize - bracketLength), bracketStroke)
            drawLine(bracketColor, Offset(cutoutLeft + cornerRadius, cutoutTop + cutoutSize), Offset(cutoutLeft + bracketLength, cutoutTop + cutoutSize), bracketStroke)

            // Bottom-right corner
            drawLine(bracketColor, Offset(cutoutLeft + cutoutSize, cutoutTop + cutoutSize - cornerRadius), Offset(cutoutLeft + cutoutSize, cutoutTop + cutoutSize - bracketLength), bracketStroke)
            drawLine(bracketColor, Offset(cutoutLeft + cutoutSize - cornerRadius, cutoutTop + cutoutSize), Offset(cutoutLeft + cutoutSize - bracketLength, cutoutTop + cutoutSize), bracketStroke)
        }

        // Instruction text below viewfinder
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Point at your camera's QR code",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Found on your camera's screen or in Wi-Fi settings",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        // Close button
        IconButton(
            onClick = {
                cameraProviderRef[0]?.unbindAll()
                onClose()
            },
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
