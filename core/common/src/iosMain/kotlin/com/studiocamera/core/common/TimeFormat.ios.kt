package com.studiocamera.core.common

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter

actual fun formatLocalTime(epochMs: Long): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = "HH:mm:ss.SSS"
    }
    val date = NSDate(timeIntervalSince1970 = epochMs / 1000.0)
    return formatter.stringFromDate(date)
}
