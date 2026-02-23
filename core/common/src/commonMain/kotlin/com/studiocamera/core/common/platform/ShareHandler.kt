package com.studiocamera.core.common.platform

/**
 * Platform-specific share handler.
 * Android: Intent.ACTION_SEND with FileProvider
 * iOS: UIActivityViewController
 */
expect class ShareHandler {
    fun share(filePath: String, mimeType: String)
}
