package com.studiocamera.core.data.di

import com.studiocamera.core.domain.model.CameraBrand
import com.studiocamera.core.data.camera.BrandApiDiscovery
import com.studiocamera.core.data.repository.PairRepositoryImpl
import com.studiocamera.core.data.session.SessionManagerImpl
import com.studiocamera.core.domain.repository.PairRepository
import com.studiocamera.core.domain.session.SessionManager
import com.studiocamera.core.domain.repository.MediaRepository
import com.studiocamera.core.data.media.BrandMediaRepositoryRouter
import com.studiocamera.core.data.media.sony.SonyMediaRepository
import com.studiocamera.core.data.repository.MediaRepositoryImpl
import com.studiocamera.core.domain.repository.CameraRepository
import com.studiocamera.core.data.camera.BrandCameraRepositoryRouter
import com.studiocamera.core.data.camera.MjpegFrameExtractor
import com.studiocamera.core.data.camera.sony.SonyApiClient
import com.studiocamera.core.data.camera.sony.SonyLiveViewDecoder
import com.studiocamera.core.data.camera.sony.SonyCameraRepository
import com.studiocamera.core.data.camera.canon.CanonCameraRepository
import com.studiocamera.core.data.camera.panasonic.PanasonicCameraRepository
import com.studiocamera.core.data.camera.stub.StubCameraRepository
import com.studiocamera.core.domain.repository.DiscoveryRepository
import com.studiocamera.core.data.discovery.DiscoveryRepositoryImpl
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dataModule = module {
    single<DiscoveryRepository> { DiscoveryRepositoryImpl(mdnsEngine = get(), deviceStorage = get()) }
    single<PairRepository> { PairRepositoryImpl(httpClient = get(), deviceStorage = get()) }
    
    // Brand API discovery for camera-specific protocols
    single { BrandApiDiscovery(httpClient = get()) }
    
    // Session manager with brand API discovery support
    single<SessionManager>(named("real")) { 
        SessionManagerImpl(
            httpClient = get(),
            connectionStateManager = get(),
            deviceStorage = get(),
            brandApiDiscovery = get()
        )
    }

    // --- Camera Repositories ---

    single { MjpegFrameExtractor(httpClient = get(named("streaming"))) }
    single { SonyLiveViewDecoder(httpClient = get(named("streaming"))) }
    single {
        SonyApiClient(
            httpClient = get()
        )
    }

    single<CameraRepository>(named("sonyCamera")) {
        val sessionManager: SessionManager = get(named("real"))
        SonyCameraRepository(
            apiClient = get(),
            liveViewDecoder = get(),
            endpoint = { sessionManager.currentEndpoint() ?: "" }
        )
    }

    single<CameraRepository>(named("canonCamera")) {
        val sessionManager: SessionManager = get(named("real"))
        CanonCameraRepository(
            httpClient = get(),
            mjpegExtractor = get(),
            endpoint = { sessionManager.currentEndpoint() ?: "" }
        )
    }

    single<CameraRepository>(named("panasonicCamera")) {
        val sessionManager: SessionManager = get(named("real"))
        PanasonicCameraRepository(
            httpClient = get(),
            endpoint = { sessionManager.currentEndpoint() ?: "" }
        )
    }

    single<CameraRepository>(named("stubCamera")) { StubCameraRepository() }

    // Camera Router exposing as the real implementation
    single<CameraRepository>(named("real")) {
        BrandCameraRepositoryRouter(
            connectionStateManager = get(),
            brandRepositories = mapOf(
                CameraBrand.Sony to get(named("sonyCamera")),
                CameraBrand.Canon to get(named("canonCamera")),
                CameraBrand.Panasonic to get(named("panasonicCamera"))
            ),
            externalScope = get(named("appScope"))
        )
    }

    // --- Media Repositories ---

    // Generic Media Repository (used for legacy or standard REST devices)
    single<MediaRepository>(named("legacyMedia")) {
        val sessionManager: SessionManager = get(named("real"))
        MediaRepositoryImpl(
            httpClient = get(),
            endpoint = { sessionManager.currentEndpoint() ?: "" },
            accessToken = { sessionManager.currentAccessToken() },
            platformDownloader = getOrNull()
        )
    }

    // Sony Media Repository
    single<MediaRepository>(named("sonyMedia")) {
        val sessionManager: SessionManager = get(named("real"))
        SonyMediaRepository(
            apiClient = get(),
            httpClient = get(),
            endpoint = { sessionManager.currentEndpoint() ?: "" },
            platformDownloader = getOrNull()
        )
    }

    // Media Router exposing as the real implementation
    single<MediaRepository>(named("real")) {
        BrandMediaRepositoryRouter(
            connectionStateManager = get(),
            brandRepositories = mapOf(
                CameraBrand.Sony to get(named("sonyMedia"))
                // Add canon, panasonic later here
            ),
            defaultRepository = get(named("legacyMedia"))
        )
    }
}
