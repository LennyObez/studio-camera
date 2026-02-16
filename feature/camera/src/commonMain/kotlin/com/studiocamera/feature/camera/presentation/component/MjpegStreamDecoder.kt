package com.studiocamera.feature.camera.presentation.component

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Decodes an MJPEG stream from the device's HTTP endpoint.
 * Emits raw JPEG frame bytes as they arrive.
 */
class MjpegStreamDecoder(
    private val httpClient: HttpClient
) {
    companion object {
        private const val TAG = "MjpegDecoder"
        private const val JPEG_START_MARKER_1: Byte = 0xFF.toByte()
        private const val JPEG_START_MARKER_2: Byte = 0xD8.toByte()
        private const val JPEG_END_MARKER_1: Byte = 0xFF.toByte()
        private const val JPEG_END_MARKER_2: Byte = 0xD9.toByte()
    }

    fun streamFrames(
        url: String,
        accessToken: String? = null
    ): Flow<ByteArray> = flow {
        try {
            Logger.d(TAG) { "Starting MJPEG stream from $url" }

            val response = httpClient.get(url) {
                accessToken?.let { bearerAuth(it) }
            }

            val channel = response.bodyAsChannel()
            val buffer = ByteArray(65536)
            val frameBuffer = ArrayList<Byte>(65536)
            var inFrame = false

            while (!channel.isClosedForRead) {
                val bytesRead = channel.readAvailable(buffer)
                if (bytesRead <= 0) continue

                for (i in 0 until bytesRead) {
                    val byte = buffer[i]

                    if (!inFrame) {
                        // Look for JPEG start marker (0xFF 0xD8)
                        if (frameBuffer.size == 1 && frameBuffer[0] == JPEG_START_MARKER_1 && byte == JPEG_START_MARKER_2) {
                            frameBuffer.add(byte)
                            inFrame = true
                        } else if (byte == JPEG_START_MARKER_1) {
                            frameBuffer.clear()
                            frameBuffer.add(byte)
                        } else {
                            frameBuffer.clear()
                        }
                    } else {
                        frameBuffer.add(byte)

                        // Look for JPEG end marker (0xFF 0xD9)
                        if (frameBuffer.size >= 4 &&
                            frameBuffer[frameBuffer.size - 2] == JPEG_END_MARKER_1 &&
                            byte == JPEG_END_MARKER_2
                        ) {
                            // Complete frame
                            emit(frameBuffer.toByteArray())
                            frameBuffer.clear()
                            inFrame = false
                        }
                    }
                }
            }

            Logger.d(TAG) { "MJPEG stream ended" }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "MJPEG stream error" }
            throw e
        }
    }
}
