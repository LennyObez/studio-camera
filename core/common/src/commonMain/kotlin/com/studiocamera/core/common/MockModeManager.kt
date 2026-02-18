package com.studiocamera.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MockModeManager {
    private val _isMockActive = MutableStateFlow(false)
    val isMockActive: StateFlow<Boolean> = _isMockActive.asStateFlow()

    fun setMockActive(active: Boolean) {
        _isMockActive.value = active
    }
}
