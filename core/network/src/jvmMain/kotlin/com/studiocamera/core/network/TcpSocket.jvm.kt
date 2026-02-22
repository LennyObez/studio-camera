package com.studiocamera.core.network

import java.net.InetSocketAddress
import java.net.Socket

actual class TcpSocket actual constructor(host: String, port: Int, timeoutMs: Int) {

    private val socket = Socket().apply {
        soTimeout = timeoutMs
        connect(InetSocketAddress(host, port), timeoutMs)
    }

    private val output = socket.getOutputStream()
    private val input = socket.getInputStream()

    actual fun send(data: ByteArray) {
        output.write(data)
        output.flush()
    }

    actual fun receive(buffer: ByteArray): Int {
        val n = input.read(buffer)
        return if (n < 0) -1 else n
    }

    actual fun close() {
        try { socket.close() } catch (_: Exception) {}
    }

    actual val isConnected: Boolean get() = socket.isConnected && !socket.isClosed
}
