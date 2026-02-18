package com.studiocamera.core.common

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun currentEpochSeconds(): Long = NSDate().timeIntervalSince1970.toLong()
actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun getCurrentLinkAddress(): String? {
    // iOS implementation would use Network framework
    // For now, return null - can be implemented later if needed
    return null
}
