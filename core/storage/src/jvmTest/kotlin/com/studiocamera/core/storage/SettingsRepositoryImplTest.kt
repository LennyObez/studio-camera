package com.studiocamera.core.storage

import com.studiocamera.core.domain.model.AppSettings
import com.studiocamera.core.domain.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryImplTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialSettings_returnsDefaults() {
        val repo = SettingsRepositoryImpl(SecureStorage())
        assertEquals(AppSettings(), repo.settings.value)
    }

    @Test
    fun updateSettings_persistsAndEmits() = runTest {
        val repo = SettingsRepositoryImpl(SecureStorage())
        repo.updateSettings { it.copy(themeMode = ThemeMode.Dark) }
        assertEquals(ThemeMode.Dark, repo.settings.value.themeMode)
    }

    @Test
    fun updateSettings_persistsAcrossInstances() = runTest {
        val storage = SecureStorage()
        val repo1 = SettingsRepositoryImpl(storage)
        repo1.updateSettings { it.copy(autoReconnect = false) }

        val repo2 = SettingsRepositoryImpl(storage)
        assertEquals(false, repo2.settings.value.autoReconnect)
    }

    @Test
    fun resetToDefaults_restoresDefaultValues() = runTest {
        val repo = SettingsRepositoryImpl(SecureStorage())
        repo.updateSettings { it.copy(themeMode = ThemeMode.Light, autoReconnect = false) }
        repo.resetToDefaults()

        assertEquals(AppSettings(), repo.settings.value)
    }
}
