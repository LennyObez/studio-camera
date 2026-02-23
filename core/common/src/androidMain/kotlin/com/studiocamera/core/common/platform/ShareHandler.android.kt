package com.studiocamera.core.common.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider

actual class ShareHandler(
    private val context: Context
) {
    actual fun share(filePath: String, mimeType: String) {
        val uri = if (filePath.startsWith("content://")) {
            Uri.parse(filePath)
        } else {
            val file = java.io.File(filePath)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(
            Intent.createChooser(shareIntent, "Share via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}
