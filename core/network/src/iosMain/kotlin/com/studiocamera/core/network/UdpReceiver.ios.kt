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
import platform.posix.INADDR_ANY
import platform.posix.SOCK_DGRAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.SO_REUSEADDR
import platform.posix.bind
import platform.posix.close
import platform.posix.htons
import platform.posix.recv
import platform.posix.setsockopt
import platform.posix.socket
import platform.posix.sockaddr_in
import platform.posix.timeval

@OptIn(ExperimentalForeignApi::class)
actual class UdpReceiver actual constructor(port: Int) {

    private val fd: Int
    private var closed = false

    init {
        fd = socket(AF_INET, SOCK_DGRAM, 0)
        check(fd >= 0) { "Failed to create UDP socket" }

        memScoped {
            // SO_REUSEADDR
            val reuseVal = alloc<platform.posix.int32_tVar>()
            reuseVal.value = 1
            setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, reuseVal.ptr, sizeOf<platform.posix.int32_tVar>().convert())

            // SO_RCVTIMEO = 5 seconds
            val tv = alloc<timeval>()
            tv.tv_sec = 5
            tv.tv_usec = 0
            setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, tv.ptr, sizeOf<timeval>().convert())

            // Bind to port
            val addr = alloc<sockaddr_in>()
            addr.sin_family = AF_INET.convert()
            addr.sin_port = htons(port.toUShort())
            addr.sin_addr.s_addr = INADDR_ANY
            val bindResult = bind(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert())
            check(bindResult == 0) { "Failed to bind UDP socket to port $port" }
        }
    }

    actual fun receive(buffer: ByteArray): Int {
        return buffer.usePinned { pinned ->
            val n = recv(fd, pinned.addressOf(0), buffer.size.convert(), 0)
            if (n < 0) 0 else n.toInt()
        }
    }

    actual fun close() {
        if (!closed) {
            closed = true
            close(fd)
        }
    }
}
