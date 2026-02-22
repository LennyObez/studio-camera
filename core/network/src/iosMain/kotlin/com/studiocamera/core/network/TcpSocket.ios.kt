package com.studiocamera.core.network

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.posix.AF_INET
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.SO_SNDTIMEO
import platform.posix.close
import platform.posix.connect
import platform.posix.inet_addr
import platform.posix.htons
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.socket
import platform.posix.sockaddr_in
import platform.posix.timeval

@OptIn(ExperimentalForeignApi::class)
actual class TcpSocket actual constructor(host: String, port: Int, timeoutMs: Int) {

    private val fd: Int
    private var closed = false

    init {
        fd = socket(AF_INET, SOCK_STREAM, 0)
        check(fd >= 0) { "Failed to create TCP socket" }

        memScoped {
            val tv = alloc<timeval>()
            tv.tv_sec = (timeoutMs / 1000).toLong()
            tv.tv_usec = ((timeoutMs % 1000) * 1000).toInt()

            setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, tv.ptr, sizeOf<timeval>().convert())
            setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, tv.ptr, sizeOf<timeval>().convert())

            val addr = alloc<sockaddr_in>()
            addr.sin_family = AF_INET.convert()
            addr.sin_port = htons(port.toUShort())
            addr.sin_addr.s_addr = inet_addr(host)

            val result = connect(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
            if (result != 0) {
                close(fd)
                error("Failed to connect TCP socket to $host:$port")
            }
        }
    }

    actual fun send(data: ByteArray) {
        data.usePinned { pinned ->
            var sent = 0
            while (sent < data.size) {
                val n = send(fd, pinned.addressOf(sent), (data.size - sent).convert(), 0)
                if (n <= 0) error("TCP send failed")
                sent += n.toInt()
            }
        }
    }

    actual fun receive(buffer: ByteArray): Int {
        return buffer.usePinned { pinned ->
            val n = recv(fd, pinned.addressOf(0), buffer.size.convert(), 0)
            if (n <= 0) -1 else n.toInt()
        }
    }

    actual fun close() {
        if (!closed) {
            closed = true
            close(fd)
        }
    }

    actual val isConnected: Boolean get() = !closed
}
