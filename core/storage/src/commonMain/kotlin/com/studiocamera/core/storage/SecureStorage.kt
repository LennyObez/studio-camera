package com.studiocamera.core.storage

/**
 * Platform-agnostic interface for secure key-value storage.
 * Android: EncryptedSharedPreferences backed by Android Keystore
 * iOS: Keychain Services
 */
expect class SecureStorage {
    fun putString(key: String, value: String)
    fun getString(key: String): String?
    fun remove(key: String)
    fun contains(key: String): Boolean
    fun clear()
}
