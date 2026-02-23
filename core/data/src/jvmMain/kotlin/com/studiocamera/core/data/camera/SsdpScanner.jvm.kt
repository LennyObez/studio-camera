package com.studiocamera.core.data.camera

import co.touchlab.kermit.Logger

actual class SsdpScanner actual constructor() {
    actual suspend fun discoverLocationUrl(targetIp: String): String? {
        Logger.d("SsdpScanner") { "SSDP Discovery not supported yet on JVM desktop" }
        return null
    }
}
