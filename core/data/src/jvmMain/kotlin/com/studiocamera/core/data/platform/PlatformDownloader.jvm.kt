package com.studiocamera.core.data.platform

actual class PlatformDownloader {
    actual suspend fun save(filename: String, mediaType: String, data: ByteArray): String {
        // JVM stub — return a fake path for testing
        return "/tmp/$filename"
    }
}
