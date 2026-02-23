package com.studiocamera.core.storage

import com.studiocamera.core.domain.model.AppSettings
import com.studiocamera.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsRepositoryImpl(
    private val secureStorage: SecureStorage
) : SettingsRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val updateMutex = Mutex()

    companion object {
        private const val KEY_SETTINGS = "app_settings"
    }

    private val _settings = MutableStateFlow(loadSettings())
    override val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    override suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        updateMutex.withLock {
            val updated = transform(_settings.value)
            _settings.value = updated
            saveSettings(updated)
        }
    }

    override suspend fun resetToDefaults() {
        updateMutex.withLock {
            val defaults = AppSettings()
            _settings.value = defaults
            saveSettings(defaults)
        }
    }

    private fun loadSettings(): AppSettings {
        val raw = secureStorage.getString(KEY_SETTINGS) ?: return AppSettings()
        return try {
            json.decodeFromString<AppSettings>(raw)
        } catch (_: Exception) {
            AppSettings()
        }
    }

    private fun saveSettings(settings: AppSettings) {
        secureStorage.putString(KEY_SETTINGS, json.encodeToString(settings))
    }
}
