package com.studiocamera.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import co.touchlab.kermit.Logger

actual class SecureStorage(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "studio_camera_secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Logger.w("SecureStorage") { "EncryptedSharedPreferences failed, using fallback: ${e.message}" }
            context.getSharedPreferences("studio_camera_prefs_fallback", Context.MODE_PRIVATE)
        }
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
