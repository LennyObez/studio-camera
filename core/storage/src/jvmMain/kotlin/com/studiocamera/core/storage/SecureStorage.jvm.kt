package com.studiocamera.core.storage

actual class SecureStorage {
    private val store = mutableMapOf<String, String>()

    actual fun putString(key: String, value: String) {
        store[key] = value
    }

    actual fun getString(key: String): String? {
        return store[key]
    }

    actual fun remove(key: String) {
        store.remove(key)
    }

    actual fun contains(key: String): Boolean {
        return store.containsKey(key)
    }

    actual fun clear() {
        store.clear()
    }
}
