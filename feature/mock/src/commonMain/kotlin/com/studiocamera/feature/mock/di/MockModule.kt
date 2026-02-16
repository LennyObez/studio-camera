package com.studiocamera.feature.mock.di

import com.studiocamera.core.domain.repository.CameraRepository
import com.studiocamera.core.domain.repository.MediaRepository
import com.studiocamera.core.domain.session.SessionManager
import com.studiocamera.feature.mock.data.MockCameraRepository
import com.studiocamera.feature.mock.data.MockMediaRepository
import com.studiocamera.feature.mock.data.MockSessionManager
import org.koin.dsl.module

val mockModule = module {
    single<SessionManager> { MockSessionManager() }
    single<CameraRepository> { MockCameraRepository() }
    single<MediaRepository> { MockMediaRepository() }
}
