package com.studiocamera.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import co.touchlab.kermit.Logger

actual class SecureStorage(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            Logger.e("SecureStorage") { "EncryptedSharedPreferences failed: ${e.message}" }
            // Corrupted prefs file — delete and retry before falling back
            try {
                context.deleteSharedPreferences("studio_camera_secure_prefs")
                Logger.w("SecureStorage") { "Deleted corrupted prefs, retrying encryption" }
                createEncryptedPrefs()
            } catch (retryException: Exception) {
                Logger.e("SecureStorage") { "Retry failed, using UNENCRYPTED fallback: ${retryException.message}" }
                context.getSharedPreferences("studio_camera_prefs_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "studio_camera_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    actual fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun getString(key: String): String? {
        return prefs.getString(key, null)
    }

    actual fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    actual fun contains(key: String): Boolean {
        return prefs.contains(key)
    }

    actual fun clear() {
        prefs.edit().clear().apply()
    }
}
