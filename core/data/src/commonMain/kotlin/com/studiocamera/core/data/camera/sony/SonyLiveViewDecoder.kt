package com.studiocamera.core.data.camera.sony

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Decodes Sony Camera Remote API live view stream.
 *
 * Sony live view format (per frame):
 * 1. Common header (8 bytes):
 *    - byte 0: start byte (0xFF)
 *    - byte 1: payload type (0x01 = JPEG image, 0x02 = frame info)
 *    - bytes 2-3: sequence number
 *    - bytes 4-7: timestamp
 *
 * 2. Payload header (128 bytes):
 *    - bytes 0-3: start code (0x24, 0x35, 0x68, 0x79)
 *    - bytes 4-6: JPEG data size (3 bytes, big-endian)
 *    - byte 7: padding size
 *    - bytes 8-127: reserved
 *
 * 3. JPEG payload (variable length)
 * 4. Padding (variable length, aligns to boundary)
 */
class SonyLiveViewDecoder(
    private val httpClient: HttpClient
) {
    companion object {
        private const val TAG = "SonyLiveView"
        private const val COMMON_HEADER_SIZE = 8
        private const val PAYLOAD_HEADER_SIZE = 128
        private const val START_BYTE: Byte = 0xFF.toByte()
        private const val PAYLOAD_TYPE_JPEG: Byte = 0x01
    }

    fun streamFrames(liveViewUrl: String): Flow<ByteArray> = flow {
        try {
            Logger.i(TAG) { "Connecting to Sony live view stream at $liveViewUrl" }

            httpClient.prepareGet(liveViewUrl).execute { response ->
                Logger.i(TAG) { "Live view HTTP response: ${response.status}" }
                val channel = response.bodyAsChannel()

                val commonHeader = ByteArray(COMMON_HEADER_SIZE)
                val payloadHeader = ByteArray(PAYLOAD_HEADER_SIZE)

                while (!channel.isClosedForRead) {
                    // Read common header (8 bytes)
                    try {
                        channel.readFully(commonHeader, 0, COMMON_HEADER_SIZE)
                    } catch (e: Exception) {
                        break // Stream ended
                    }

                    // Verify start byte — resync if invalid
                    if (commonHeader[0] != START_BYTE) {
                        Logger.w(TAG) { "Invalid start byte: ${commonHeader[0]}, scanning for resync..." }
                        var found = false
                        val scanBuf = ByteArray(1)
                        while (!channel.isClosedForRead) {
                            try {
                                channel.readFully(scanBuf, 0, 1)
                                if (scanBuf[0] == START_BYTE) {
                                    commonHeader[0] = START_BYTE
                                    channel.readFully(commonHeader, 1, COMMON_HEADER_SIZE - 1)
                                    found = true
                                    break
                                }
                            } catch (e: Exception) {
                                break
                            }
                        }
                        if (!found) break
                    }

                    val payloadType = commonHeader[1]

                    // Read payload header (128 bytes)
                    channel.readFully(payloadHeader, 0, PAYLOAD_HEADER_SIZE)

                    // Extract JPEG data size (bytes 4-6, big-endian, 3 bytes)
                    val jpegSize = ((payloadHeader[4].toInt() and 0xFF) shl 16) or
                        ((payloadHeader[5].toInt() and 0xFF) shl 8) or
                        (payloadHeader[6].toInt() and 0xFF)

                    // Extract padding size
                    val paddingSize = payloadHeader[7].toInt() and 0xFF

                    if (jpegSize <= 0 || jpegSize > 5_000_000) {
                        // Skip obviously invalid frames
                        Logger.w(TAG) { "Invalid JPEG size: $jpegSize" }
                        continue
                    }

                    // Read JPEG payload
                    val jpegData = ByteArray(jpegSize)
                    channel.readFully(jpegData, 0, jpegSize)

                    // Read and discard padding
                    if (paddingSize > 0) {
                        val padding = ByteArray(paddingSize)
                        channel.readFully(padding, 0, paddingSize)
                    }

                    // Only emit JPEG image payloads
                    if (payloadType == PAYLOAD_TYPE_JPEG) {
                        emit(jpegData)
                    }
                }

                Logger.d(TAG) { "Sony live view stream ended" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "Sony live view stream error" }
            throw e
        }
    }
}
