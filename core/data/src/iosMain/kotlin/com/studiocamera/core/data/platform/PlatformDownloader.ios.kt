package com.studiocamera.core.data.platform

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.Photos.PHAssetChangeRequest
import platform.Photos.PHPhotoLibrary
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "PlatformDownloader"

/**
 * Saves media files to the iOS Photos library via [PHPhotoLibrary].
 * Requires `NSPhotoLibraryAddUsageDescription` in Info.plist.
 */
actual class PlatformDownloader {

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun save(filename: String, mediaType: String, data: ByteArray): String {
        val isVideo = mediaType.startsWith("video") || filename.endsWith(".mp4")
        val tempDir = NSTemporaryDirectory()
        val tempPath = tempDir + filename
        val tempUrl = NSURL.fileURLWithPath(tempPath)

        // Write bytes to temp file
        val nsData = data.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = data.size.toULong())
        }
        nsData.writeToURL(tempUrl, atomically = true)

        // Save to Photos library
        val localId = suspendCancellableCoroutine { continuation ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                if (isVideo) {
                    PHAssetChangeRequest.creationRequestForAssetFromVideoAtFileURL(tempUrl)
                } else {
                    PHAssetChangeRequest.creationRequestForAssetFromImageAtFileURL(tempUrl)
                }
            }) { success, error ->
                if (success) {
                    Logger.d(TAG) { "Saved $filename to Photos library" }
                    if (continuation.isActive) {
                        continuation.resume(filename)
                    }
                } else {
                    val msg = "Failed to save $filename: ${error?.localizedDescription}"
                    Logger.e(TAG) { msg }
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException(msg))
                    }
                }
            }
        }

        // Clean up temp file
        NSFileManager.defaultManager.removeItemAtPath(tempPath, error = null)

        return localId
    }
}
