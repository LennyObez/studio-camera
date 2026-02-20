package com.studiocamera.core.data.di

import com.studiocamera.core.common.MockModeManager
import com.studiocamera.core.domain.repository.CameraRepository
import com.studiocamera.core.domain.repository.MediaRepository
import com.studiocamera.core.domain.session.SessionManager
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Bridge module: provides unqualified bindings that delegate to "real" or "mock"
 * based on MockModeManager state. Shared between Android and iOS.
 */
val bridgeModule = module {
    factory<SessionManager> {
        val mockMode: MockModeManager = get()
        if (mockMode.isMockActive.value) get(named("mock")) else get(named("real"))
    }
    factory<CameraRepository> {
        val mockMode: MockModeManager = get()
        if (mockMode.isMockActive.value) get(named("mock")) else get(named("real"))
    }
    factory<MediaRepository> {
        val mockMode: MockModeManager = get()
        if (mockMode.isMockActive.value) get(named("mock")) else get(named("real"))
    }
}
