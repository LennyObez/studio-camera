package com.studiocamera.core.storage

import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFTypeRefVar
import platform.Foundation.NSMutableDictionary

@OptIn(ExperimentalForeignApi::class)
actual class SecureStorage {

    private val serviceName = "com.studiocamera.secure"

    actual fun putString(key: String, value: String) {
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        val query = baseQuery(key)

        // Delete existing item first
        SecItemDelete(query)

        // Add new item
        query[kSecValueData] = data
        query[kSecAttrAccessible] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        SecItemAdd(query, null)
    }

    actual fun getString(key: String): String? {
        val query = baseQuery(key)
        query[kSecReturnData] = true
        query[kSecMatchLimit] = kSecMatchLimitOne

        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status == errSecSuccess) {
                val data = result.value as? NSData ?: return null
                return NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
            }
        }
        return null
    }

    actual fun remove(key: String) {
        val query = baseQuery(key)
        SecItemDelete(query)
    }

    actual fun contains(key: String): Boolean {
        val query = baseQuery(key)
        query[kSecMatchLimit] = kSecMatchLimitOne
        return SecItemCopyMatching(query, null) == errSecSuccess
    }

    actual fun clear() {
        val query = NSMutableDictionary().apply {
            this[kSecClass] = kSecClassGenericPassword
            this[kSecAttrService] = serviceName
        }
        SecItemDelete(query)
    }

    private fun baseQuery(key: String): NSMutableDictionary {
        return NSMutableDictionary().apply {
            this[kSecClass] = kSecClassGenericPassword
            this[kSecAttrService] = serviceName
            this[kSecAttrAccount] = key
        }
    }
}
