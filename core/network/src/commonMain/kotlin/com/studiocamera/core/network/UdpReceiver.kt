package com.studiocamera.core.network

/**
 * Cross-platform UDP datagram receiver.
 *
 * Binds to the specified [port] and blocks on [receive] until data arrives
 * or the socket timeout (5 s) elapses. Used for Panasonic live view UDP
 * streaming on port 49152.
 */
expect class UdpReceiver(port: Int) {
    /** Blocking receive into [buffer]. Returns the number of bytes read, or 0 on timeout. */
    fun receive(buffer: ByteArray): Int

    /** Closes the underlying socket. Safe to call multiple times. */
    fun close()
}
