package com.studiocamera.core.data.camera

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Extracts JPEG frames from an MJPEG HTTP stream.
 * Shared by Canon CCAPI and any other brand using MJPEG-style streaming.
 */
class MjpegFrameExtractor(
    private val httpClient: HttpClient
) {
    companion object {
        private const val TAG = "MjpegExtractor"
        private const val JPEG_START_1: Int = 0xFF
        private const val JPEG_START_2: Int = 0xD8
        private const val JPEG_END_1: Int = 0xFF
        private const val JPEG_END_2: Int = 0xD9
        private const val INITIAL_BUFFER_SIZE = 65536
    }

    fun streamFrames(url: String): Flow<ByteArray> = flow {
        try {
            Logger.d(TAG) { "Starting MJPEG stream from $url" }

            val response = httpClient.get(url)
            val channel = response.bodyAsChannel()
            val readBuffer = ByteArray(INITIAL_BUFFER_SIZE)
            var frameBuffer = ByteArray(INITIAL_BUFFER_SIZE)
            var frameSize = 0
            var inFrame = false
            var prevByte: Int = -1

            while (!channel.isClosedForRead) {
                val bytesRead = channel.readAvailable(readBuffer)
                if (bytesRead <= 0) continue

                for (i in 0 until bytesRead) {
                    val byte = readBuffer[i].toInt() and 0xFF

                    if (!inFrame) {
                        if (prevByte == JPEG_START_1 && byte == JPEG_START_2) {
                            frameSize = 0
                            frameBuffer = ensureCapacity(frameBuffer, frameSize + 2)
                            frameBuffer[frameSize++] = JPEG_START_1.toByte()
                            frameBuffer[frameSize++] = JPEG_START_2.toByte()
                            inFrame = true
                        }
                        prevByte = byte
                    } else {
                        frameBuffer = ensureCapacity(frameBuffer, frameSize + 1)
                        frameBuffer[frameSize++] = byte.toByte()

                        if (prevByte == JPEG_END_1 && byte == JPEG_END_2) {
                            emit(frameBuffer.copyOf(frameSize))
                            frameSize = 0
                            inFrame = false
                            prevByte = -1
                            continue
                        }
                        prevByte = byte
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

    private fun ensureCapacity(buffer: ByteArray, needed: Int): ByteArray {
        return if (needed <= buffer.size) buffer
        else buffer.copyOf(maxOf(buffer.size * 2, needed))
    }
}
