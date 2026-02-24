package com.studiocamera.core.data.camera

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MjpegFrameExtractorTest {

    /** Build a minimal JPEG frame: SOI marker + payload + EOI marker. */
    private fun fakeJpegFrame(payload: ByteArray = byteArrayOf(0x01, 0x02, 0x03)): ByteArray {
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte()) +
            payload +
            byteArrayOf(0xFF.toByte(), 0xD9.toByte())
    }

    /** Build a mock MJPEG stream from a list of JPEG frames with optional garbage between them. */
    private fun buildMjpegStream(frames: List<ByteArray>, garbageBetween: ByteArray = byteArrayOf()): ByteArray {
        val result = mutableListOf<Byte>()
        for ((i, frame) in frames.withIndex()) {
            if (i > 0) result.addAll(garbageBetween.toList())
            result.addAll(frame.toList())
        }
        return result.toByteArray()
    }

    private fun createExtractorWithStream(streamBytes: ByteArray): MjpegFrameExtractor {
        val mockEngine = MockEngine { _ ->
            val channel = ByteChannel(autoFlush = true)
            channel.writeFully(streamBytes)
            channel.close()
            respond(
                content = channel,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "multipart/x-mixed-replace")
            )
        }
        return MjpegFrameExtractor(HttpClient(mockEngine))
    }

    @Test
    fun extractsSingleJpegFrame() = runTest {
        val jpegData = fakeJpegFrame(byteArrayOf(0x10, 0x20, 0x30))
        val extractor = createExtractorWithStream(jpegData)

        val frames = extractor.streamFrames("http://fake/stream").toList()

        assertEquals(1, frames.size)
        assertTrue(frames[0].contentEquals(jpegData))
    }

    @Test
    fun extractsMultipleFrames() = runTest {
        val frame1 = fakeJpegFrame(byteArrayOf(0xAA.toByte()))
        val frame2 = fakeJpegFrame(byteArrayOf(0xBB.toByte()))
        val frame3 = fakeJpegFrame(byteArrayOf(0xCC.toByte()))
        val stream = buildMjpegStream(listOf(frame1, frame2, frame3))
        val extractor = createExtractorWithStream(stream)

        val frames = extractor.streamFrames("http://fake/stream").toList()

        assertEquals(3, frames.size)
        assertTrue(frames[0].contentEquals(frame1))
        assertTrue(frames[1].contentEquals(frame2))
        assertTrue(frames[2].contentEquals(frame3))
    }

    @Test
    fun skipsGarbageBetweenFrames() = runTest {
        val frame1 = fakeJpegFrame(byteArrayOf(0x01))
        val frame2 = fakeJpegFrame(byteArrayOf(0x02))
        val garbage = byteArrayOf(0x00, 0x00, 0x7F, 0x33)
        val stream = buildMjpegStream(listOf(frame1, frame2), garbageBetween = garbage)
        val extractor = createExtractorWithStream(stream)

        val frames = extractor.streamFrames("http://fake/stream").toList()

        assertEquals(2, frames.size)
        assertTrue(frames[0].contentEquals(frame1))
        assertTrue(frames[1].contentEquals(frame2))
    }

    @Test
    fun handlesEmptyStream() = runTest {
        val extractor = createExtractorWithStream(byteArrayOf())

        val frames = extractor.streamFrames("http://fake/stream").toList()

        assertEquals(0, frames.size)
    }

    @Test
    fun handlesStreamWithNoValidFrames() = runTest {
        val garbage = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04)
        val extractor = createExtractorWithStream(garbage)

        val frames = extractor.streamFrames("http://fake/stream").toList()

        assertEquals(0, frames.size)
    }

    @Test
    fun handlesLargeFrame() = runTest {
        val largePayload = ByteArray(100_000) { (it % 256).toByte() }
        val frame = fakeJpegFrame(largePayload)
        val extractor = createExtractorWithStream(frame)

        val frames = extractor.streamFrames("http://fake/stream").toList()

        assertEquals(1, frames.size)
        assertEquals(frame.size, frames[0].size)
    }
}
