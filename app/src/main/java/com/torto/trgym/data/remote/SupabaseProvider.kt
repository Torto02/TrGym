//package com.torto.trgym.data.remote
//
//// SupabaseProvider.kt
//
//import io.github.jan.supabase.SupabaseClient
//import io.github.jan.supabase.annotations.SupabaseInternal
//import io.github.jan.supabase.createSupabaseClient
//import io.github.jan.supabase.auth.Auth
//import io.github.jan.supabase.auth.ExternalAuthAction
//import io.github.jan.supabase.postgrest.Postgrest
//import io.github.jan.supabase.realtime.Realtime
//
//
//object SupabaseProvider {
//
//    /** Singleton del client: una sola istanza per l’intera app */
//    @OptIn(SupabaseInternal::class)
//    val client: SupabaseClient by lazy {
//        createSupabaseClient(
//            supabaseUrl = "https://ebmmlujvowrumcafksrw.supabase.co",
//            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVibW1sdWp2b3dydW1jYWZrc3J3Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDcwMzQ0ODYsImV4cCI6MjA2MjYxMDQ4Nn0.P4BvBrWSIrOr6zfIgVVdeyesp6WorZso0MEmyfzNsTQ"
//        ) {
//            install(Auth) {
//                alwaysAutoRefresh = true
//                autoLoadFromStorage = true
//                //  ───── CONFIGURAZIONE DEEPLINK ─────
//                // host e scheme devono corrispondere al tuo intent-filter
//                host = "login-callback"
//                scheme = "com.torto.trgym"
//                // apri il flusso OAuth in CustomTabs anziché browser esterno
//                defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
//            }
//            install(Postgrest)
//            install(Realtime)
//        }
//    }
//}
//
//
