package com.studiocamera.core.network

import android.net.Network
import co.touchlab.kermit.Logger
import com.studiocamera.core.common.CameraNetworkBinding
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * A [SocketFactory] that binds sockets to the camera Wi-Fi network via
 * [Network.bindSocket].
 *
 * OkHttp calls the no-arg [createSocket] to obtain an unconnected socket,
 * then connects it itself. We intercept that call to bind the socket to
 * the camera network before OkHttp connects it, ensuring traffic routes
 * through the camera even when the home Wi-Fi uses an overlapping subnet.
 *
 * The remaining overloads return already-connected sockets where
 * [Network.bindSocket] cannot be applied; they delegate straight to
 * [SocketFactory.getDefault].
 */
class BoundNetworkSocketFactory : SocketFactory() {

    override fun createSocket(): Socket {
        val socket = getDefault().createSocket()
        val network = CameraNetworkBinding.boundNetwork
        if (network is Network && !socket.isConnected) {
            try {
                network.bindSocket(socket)
            } catch (e: Exception) {
                Logger.w("BoundNetworkSocketFactory") { "bindSocket failed: ${e.message}" }
            }
        }
        return socket
    }

    override fun createSocket(host: String, port: Int): Socket =
        getDefault().createSocket(host, port)

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        getDefault().createSocket(host, port, localHost, localPort)

    override fun createSocket(host: InetAddress, port: Int): Socket =
        getDefault().createSocket(host, port)

    override fun createSocket(host: InetAddress, port: Int, localHost: InetAddress, localPort: Int): Socket =
        getDefault().createSocket(host, port, localHost, localPort)
}
