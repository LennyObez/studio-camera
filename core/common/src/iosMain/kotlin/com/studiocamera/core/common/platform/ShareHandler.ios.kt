package com.studiocamera.core.common.platform

import co.touchlab.kermit.Logger

actual class ShareHandler {
    actual fun share(filePath: String, mimeType: String) {
        // iOS: UIActivityViewController — full implementation requires macOS build
        Logger.w("ShareHandler") { "iOS share not yet implemented for $filePath" }
    }
}
