package com.studiocamera.core.network

/**
 * Cross-platform blocking TCP socket for PTP/IP communication.
 *
 * Connects to [host]:[port] on construction with the given [timeoutMs].
 * Used by Nikon (port 15740) and Fujifilm (ports 55740-55742) PTP/IP.
 */
expect class TcpSocket(host: String, port: Int, timeoutMs: Int = 10_000) {
    /** Sends [data] over the connection. Throws on failure. */
    fun send(data: ByteArray)

    /** Blocking receive into [buffer]. Returns bytes read, or -1 on EOF. */
    fun receive(buffer: ByteArray): Int

    /** Closes the socket. Safe to call multiple times. */
    fun close()

    /** Whether the socket is currently connected. */
    val isConnected: Boolean
}
