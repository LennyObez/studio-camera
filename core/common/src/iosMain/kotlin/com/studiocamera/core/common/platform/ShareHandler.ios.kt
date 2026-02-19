package com.studiocamera.core.common.platform

import co.touchlab.kermit.Logger
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

private const val TAG = "ShareHandler"

/**
 * Presents the iOS share sheet via [UIActivityViewController].
 */
actual class ShareHandler {

    actual fun share(filePath: String, mimeType: String) {
        val fileUrl = NSURL.fileURLWithPath(filePath)

        dispatch_async(dispatch_get_main_queue()) {
            @Suppress("DEPRECATION")
            val rootViewController = UIApplication.sharedApplication
                .keyWindow
                ?.rootViewController

            if (rootViewController == null) {
                Logger.w(TAG) { "No root view controller available for share sheet" }
                return@dispatch_async
            }

            val activityVC = UIActivityViewController(
                activityItems = listOf(fileUrl),
                applicationActivities = null
            )

            rootViewController.presentViewController(
                activityVC,
                animated = true,
                completion = null
            )
        }
    }
}
