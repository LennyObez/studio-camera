package com.studiocamera.android

import android.app.Application
import co.touchlab.kermit.Logger
import com.studiocamera.android.di.appModule
import com.studiocamera.core.common.SanitizingLogWriter
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class StudioCameraApp : Application() {

    override fun onCreate() {
        super.onCreate()

        initLogging()
        initKoin()
    }

    private fun initLogging() {
        val isRelease = !BuildConfig.DEBUG
        Logger.setLogWriters(
            SanitizingLogWriter(
                delegate = co.touchlab.kermit.platformLogWriter(),
                isRelease = isRelease
            )
        )
        Logger.setTag("StudioCamera")
        Logger.i { "StudioCamera app started (debug=${BuildConfig.DEBUG})" }
    }

    private fun initKoin() {
        startKoin {
            androidLogger()
            androidContext(this@StudioCameraApp)
            modules(appModule())
        }
    }
}
