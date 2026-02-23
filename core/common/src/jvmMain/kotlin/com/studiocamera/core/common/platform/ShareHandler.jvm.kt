package com.studiocamera.core.common.platform

actual class ShareHandler {
    actual fun share(filePath: String, mimeType: String) {
        // No-op on JVM (desktop) — sharing is platform-specific
    }
}
