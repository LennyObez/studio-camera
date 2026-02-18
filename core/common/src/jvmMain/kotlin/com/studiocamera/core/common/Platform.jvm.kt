package com.studiocamera.core.common

actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000
actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun getCurrentLinkAddress(): String? = null
