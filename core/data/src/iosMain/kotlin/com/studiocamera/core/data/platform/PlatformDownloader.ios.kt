package com.studiocamera.core.data.platform

import co.touchlab.kermit.Logger

actual class PlatformDownloader {
    actual suspend fun save(filename: String, mediaType: String, data: ByteArray): String {
        // iOS: Save via PHPhotoLibrary — full implementation requires macOS build
        Logger.w("PlatformDownloader") { "iOS save not yet implemented, returning stub path" }
        return "/photos/$filename"
    }
}
