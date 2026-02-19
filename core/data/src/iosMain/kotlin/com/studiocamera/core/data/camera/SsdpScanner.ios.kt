package com.studiocamera.core.data.camera

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.posix.AF_INET
import platform.posix.IPPROTO_UDP
import platform.posix.SOCK_DGRAM
import platform.posix.SO_BROADCAST
import platform.posix.SO_RCVTIMEO
import platform.posix.SOL_SOCKET
import platform.posix.close
import platform.posix.htons
import platform.posix.inet_addr
import platform.posix.recv
import platform.posix.sendto
import platform.posix.setsockopt
import platform.posix.socket
import platform.posix.sockaddr_in
import platform.posix.timeval

private const val TAG = "SsdpScanner"

/**
 * SSDP M-SEARCH scanner using BSD POSIX sockets on iOS.
 * No network binding needed — iOS routes through the active Wi-Fi.
 */
@OptIn(ExperimentalForeignApi::class)
actual class SsdpScanner actual constructor() {

    actual suspend fun discoverLocationUrl(targetIp: String): String? = withContext(Dispatchers.Default) {
        var fd = -1
        try {
            fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)
            if (fd < 0) {
                Logger.e(TAG) { "Failed to create UDP socket" }
                return@withContext null
            }

            memScoped {
                // Enable broadcast
                val broadcastFlag = alloc<platform.posix.int32_tVar>()
                broadcastFlag.value = 1
                setsockopt(fd, SOL_SOCKET, SO_BROADCAST, broadcastFlag.ptr, sizeOf<platform.posix.int32_tVar>().convert())

                // Set receive timeout (3 seconds)
                val timeout = alloc<timeval>()
                timeout.tv_sec = 3
                timeout.tv_usec = 0
                setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert())

                val searchMessage = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 1\r\n" +
                        "ST: urn:schemas-sony-com:service:ScalarWebAPI:1\r\n\r\n"

                val sendData = searchMessage.encodeToByteArray()

                // Send multicast M-SEARCH to 239.255.255.250:1900
                val multicastAddr = alloc<sockaddr_in>()
                multicastAddr.sin_family = AF_INET.convert()
                multicastAddr.sin_port = htons(1900u)
                multicastAddr.sin_addr.s_addr = inet_addr("239.255.255.250")

                sendData.usePinned { pinned ->
                    sendto(
                        fd,
                        pinned.addressOf(0),
                        sendData.size.convert(),
                        0,
                        multicastAddr.ptr.reinterpret(),
                        sizeOf<sockaddr_in>().convert()
                    )
                }

                // Also send unicast directly to the target IP
                val unicastAddr = alloc<sockaddr_in>()
                unicastAddr.sin_family = AF_INET.convert()
                unicastAddr.sin_port = htons(1900u)
                unicastAddr.sin_addr.s_addr = inet_addr(targetIp)

                sendData.usePinned { pinned ->
                    sendto(
                        fd,
                        pinned.addressOf(0),
                        sendData.size.convert(),
                        0,
                        unicastAddr.ptr.reinterpret(),
                        sizeOf<sockaddr_in>().convert()
                    )
                }

                Logger.d(TAG) { "Sent SSDP M-SEARCH (multicast + unicast to $targetIp)" }

                // Receive response
                val recvBuf = ByteArray(1024)
                val bytesRead = recvBuf.usePinned { pinned ->
                    recv(fd, pinned.addressOf(0), recvBuf.size.convert(), 0)
                }

                if (bytesRead > 0) {
                    val response = recvBuf.decodeToString(endIndex = bytesRead.toInt())
                    Logger.d(TAG) { "SSDP Response:\n$response" }

                    // Parse LOCATION header
                    val locationMatch = Regex(
                        "LOCATION:\\s*(http://[\\w.:]+/[^\r\n]*)",
                        RegexOption.IGNORE_CASE
                    ).find(response)
                    return@withContext locationMatch?.groupValues?.get(1)?.trim()
                } else {
                    Logger.d(TAG) { "SSDP discovery timed out" }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, e) { "SSDP discovery failed" }
        } finally {
            if (fd >= 0) close(fd)
        }
        null
    }
}
