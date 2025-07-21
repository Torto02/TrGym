package com.torto.trgym.di

import android.app.Application
//import com.torto.trgym.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.ExternalAuthAction
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    @OptIn(SupabaseInternal::class)
    fun provideSupabaseClient(app: Application): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = "https://ebmmlujvowrumcafksrw.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVibW1sdWp2b3dydW1jYWZrc3J3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDcwMzQ0ODYsImV4cCI6MjA2MjYxMDQ4Nn0.P4BvBrWSIrOr6zfIgVVdeyesp6WorZso0MEmyfzNsTQ"
        ) {
            install(Auth) {
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
                host = "login-callback"
                scheme = "com.torto.trgym"
                defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
            }
            install(Postgrest)
            install(Realtime)
        }
    }
}
