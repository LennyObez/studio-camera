package com.studiocamera.core.data.platform

/**
 * Platform-specific media downloader.
 * Android: Saves to MediaStore (scoped storage)
 * iOS: Saves to Photos via PHPhotoLibrary
 */
expect class PlatformDownloader {
    suspend fun save(filename: String, mediaType: String, data: ByteArray): String
}
