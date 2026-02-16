package com.studiocamera.core.data.di

import com.studiocamera.core.data.repository.PairRepositoryImpl
import com.studiocamera.core.domain.repository.PairRepository
import org.koin.dsl.module

val dataModule = module {
    single<PairRepository> { PairRepositoryImpl(httpClient = get(), deviceStorage = get()) }
}
