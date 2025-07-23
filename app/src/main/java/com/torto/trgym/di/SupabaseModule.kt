package com.torto.trgym.di

import android.app.Application
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

/**
 * Modulo Dagger per la configurazione del client Supabase.
 * Fornisce le dipendenze necessarie per l'interazione con Supabase.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    /**
     * Fornisce un'istanza singleton di [SupabaseClient].
     * Configura il client con URL e chiave di accesso, e installa i moduli necessari.
     *
     * @param app L'istanza dell'applicazione, fornita da Hilt.
     * @return Un'istanza configurata di [SupabaseClient].
     */
    @Provides
    @Singleton
    @OptIn(SupabaseInternal::class)
    fun provideSupabaseClient(app: Application): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = "https://ebmmlujvowrumcafksrw.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVibW1sdWp2b3dydW1jYWZrc3J3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDcwMzQ0ODYsImV4cCI6MjA2MjYxMDQ4Nn0.P4BvBrWSIrOr6zfIgVVdeyesp6WorZso0MEmyfzNsTQ"
        ) {
            // Configura il modulo di autenticazione.
            install(Auth) {
                alwaysAutoRefresh = true // Abilita il refresh automatico del token.
                autoLoadFromStorage = true // Carica automaticamente i dati di autenticazione dalla memoria.
                host = "login-callback" // Specifica l'host per il callback di login.
                scheme = "com.torto.trgym" // Specifica lo schema per il callback di login.
                defaultExternalAuthAction = ExternalAuthAction.CustomTabs() // Configura l'azione di autenticazione esterna.
            }
            // Installa il modulo Postgrest per l'accesso ai dati.
            install(Postgrest)
            // Installa il modulo Realtime per la gestione delle notifiche in tempo reale.
            install(Realtime)
        }
    }
}