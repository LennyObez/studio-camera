package com.studiocamera.core.common

expect fun currentEpochSeconds(): Long
expect fun currentTimeMillis(): Long

/**
 * Gets the link address (IP assigned to this device) from the current network
 * Returns null if not available or not on a Wi-Fi Direct network
 */
expect fun getCurrentLinkAddress(): String?
