package com.studiocamera.core.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException

actual class UdpReceiver actual constructor(port: Int) {

    private val socket = DatagramSocket(port).apply {
        soTimeout = 5_000
        reuseAddress = true
    }

    actual fun receive(buffer: ByteArray): Int {
        val packet = DatagramPacket(buffer, buffer.size)
        return try {
            socket.receive(packet)
            packet.length
        } catch (_: SocketTimeoutException) {
            0
        }
    }

    actual fun close() {
        try { socket.close() } catch (_: Exception) {}
    }
}
