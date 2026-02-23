package com.studiocamera.core.data.camera

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class SsdpScanner actual constructor() {
    private val TAG = "SsdpScanner"

    actual suspend fun discoverLocationUrl(targetIp: String): String? = withContext(Dispatchers.IO) {
        try {
            val ssdpAddress = java.net.InetAddress.getByName("239.255.255.250")
            val ssdpPort = 1900
            val timeoutMs = 3000

            val searchMessage = "M-SEARCH * HTTP/1.1\r\n" +
                    "HOST: 239.255.255.250:1900\r\n" +
                    "MAN: \"ssdp:discover\"\r\n" +
                    "MX: 1\r\n" +
                    "ST: urn:schemas-sony-com:service:ScalarWebAPI:1\r\n\r\n"

            val sendData = searchMessage.toByteArray(Charsets.UTF_8)
            val socket = java.net.DatagramSocket()
            socket.broadcast = true
            socket.reuseAddress = true
            socket.soTimeout = timeoutMs

            try {
                val network = com.studiocamera.core.common.CameraNetworkBinding.boundNetwork
                if (network is android.net.Network) {
                    network.bindSocket(socket)
                    Logger.d(TAG) { "Bound SSDP DatagramSocket to camera network" }
                }
            } catch (e: Exception) {
                Logger.d(TAG) { "Network binding not available for SSDP: ${e.message}" }
            }

            val sendPacket = java.net.DatagramPacket(sendData, sendData.size, ssdpAddress, ssdpPort)
            socket.send(sendPacket)

            // Send a unicast burst as well directly to the IP to punch through Android AP isolation
            try {
                val unicastAddress = java.net.InetAddress.getByName(targetIp)
                val unicastPacket = java.net.DatagramPacket(sendData, sendData.size, unicastAddress, ssdpPort)
                socket.send(unicastPacket)
            } catch (e: Exception) {
                Logger.d(TAG) { "Failed to send unicast SSDP to $targetIp: ${e.message}" }
            }

            val receiveData = ByteArray(1024)
            val receivePacket = java.net.DatagramPacket(receiveData, receiveData.size)

            try {
                // Wait for up to timeoutMs for a response
                socket.receive(receivePacket)
                val response = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8)
                Logger.d(TAG) { "SSDP Response from ${receivePacket.address.hostAddress}:\n$response" }

                // Parse LOCATION header or endpoint if available in Sony response
                val locationMatch = Regex("LOCATION:\\s*(http://[\\w.:]+/[^\r\n]*)", RegexOption.IGNORE_CASE).find(response)
                return@withContext locationMatch?.groupValues?.get(1)?.trim()
            } catch (e: java.net.SocketTimeoutException) {
                Logger.d(TAG) { "SSDP discovery timed out" }
            } finally {
                socket.close()
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "SSDP discovery failed" }
        }
        null
    }
}
