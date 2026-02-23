package com.studiocamera.core.data.camera

expect class SsdpScanner() {
    suspend fun discoverLocationUrl(targetIp: String): String?
}
