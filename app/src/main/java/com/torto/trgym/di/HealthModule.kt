package com.torto.trgym.di

import android.content.Context
import com.torto.trgym.services.HeartRateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HealthModule {

    @Provides
    @Singleton
    fun provideHeartRateManager(
        @ApplicationContext context: Context
    ): HeartRateManager {
        return HeartRateManager(context)
    }
}