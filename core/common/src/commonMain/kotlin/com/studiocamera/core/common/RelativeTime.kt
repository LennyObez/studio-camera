package com.studiocamera.core.common

fun formatRelativeTime(epochSeconds: Long): String {
    val now = currentEpochSeconds()
    val diff = now - epochSeconds

    if (diff < 0 || epochSeconds == 0L) return ""

    return when {
        diff < 60 -> "Just now"
        diff < 3600 -> {
            val minutes = diff / 60
            if (minutes == 1L) "1 minute ago" else "$minutes minutes ago"
        }
        diff < 86400 -> {
            val hours = diff / 3600
            if (hours == 1L) "1 hour ago" else "$hours hours ago"
        }
        diff < 172800 -> "Yesterday"
        diff < 604800 -> {
            val days = diff / 86400
            "$days days ago"
        }
        diff < 2_592_000 -> {
            val weeks = diff / 604800
            if (weeks == 1L) "1 week ago" else "$weeks weeks ago"
        }
        diff < 31_536_000 -> {
            val months = diff / 2_592_000
            if (months == 1L) "1 month ago" else "$months months ago"
        }
        else -> {
            val years = diff / 31_536_000
            if (years == 1L) "1 year ago" else "$years years ago"
        }
    }
}
