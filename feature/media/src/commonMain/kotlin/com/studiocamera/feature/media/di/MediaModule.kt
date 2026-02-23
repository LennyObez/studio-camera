package com.studiocamera.feature.media.di

import org.koin.dsl.module

val mediaModule = module {
    // MediaRepository is provided by DataModule (named "real") and MockModule (named "mock")
    // Bridged in AppModule
}
