package com.studiocamera.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun formatLocalTime(epochMs: Long): String {
    val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    return formatter.format(Date(epochMs))
}
