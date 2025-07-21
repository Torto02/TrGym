//package com.torto.trgym.data.repository
//
//import androidx.browser.customtabs.CustomTabsIntent
//import com.torto.trgym.data.remote.SupabaseProvider
//import io.github.jan.supabase.auth.auth
//import io.github.jan.supabase.auth.providers.Google
//import io.github.jan.supabase.auth.providers.builtin.Email
//import android.content.Context
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import kotlinx.serialization.json.JsonObject
//import kotlinx.serialization.json.JsonPrimitive
//import androidx.core.net.toUri
//
//object AuthRepository {
//
//    private val auth = SupabaseProvider.client.auth
//
//    fun observeAuthStatus() = auth.sessionStatus
//
//    suspend fun signUp(email: String, password: String, username: String? = null) =
//        withContext(Dispatchers.IO) {
//            auth.signUpWith(Email, redirectUrl = "com.torto.trgym://login-callback") {
//                this.email = email
//                this.password = password
//
//
//                username?.let {
//                    this.data = JsonObject(
//                        mapOf(
//                            "username" to JsonPrimitive(it),
//                            "display_name" to JsonPrimitive(it)
//                        )
//                    )
//                }
//            }
//        }
//
//
//    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
//        auth.signInWith(Email) {
//            this.email = email
//            this.password = password
//        }
//    }
//
//    fun signInWithGoogle(context: Context) {
//        // 1) Genera l’URL OAuth e aggiungi prompt=select_account
//        val baseUrl = auth.getOAuthUrl(
//            Google,
//            redirectUrl = "com.torto.trgym://login-callback"
//        )
//        val urlWithPrompt = "$baseUrl&prompt=select_account"
//
//        // 2) Apri in Custom Tab
//        CustomTabsIntent.Builder()
//            .build()
//            .launchUrl(context, urlWithPrompt.toUri())
//        //Uri.parse(urlWithPrompt) se il nuovo non funziona
//    }
//
//    fun getCurrentUser() = auth.currentSessionOrNull()?.user
//
//    suspend fun signOut() = auth.signOut()
//
//
//}

package com.torto.trgym.data.repository

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
//! import com.torto.trgym.BuildConfig DA FARE
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val client: SupabaseClient
) {

    private val auth = client.auth

    fun observeAuthStatus() = auth.sessionStatus

    suspend fun signUp(email: String, password: String, username: String? = null) =
        withContext(Dispatchers.IO) {
            auth.signUpWith(Email, redirectUrl = "com.torto.trgym://login-callback") {
                this.email = email
                this.password = password

                username?.let {
                    this.data = JsonObject(
                        mapOf(
                            "username" to JsonPrimitive(it),
                            "display_name" to JsonPrimitive(it)
                        )
                    )
                }
            }
        }

    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    fun signInWithGoogle(context: Context) {
        val baseUrl = auth.getOAuthUrl(
            Google,
            redirectUrl = "com.torto.trgym://login-callback"
        )
        val urlWithPrompt = "$baseUrl&prompt=select_account"

        CustomTabsIntent.Builder()
            .build()
            .launchUrl(context, urlWithPrompt.toUri())
    }

    fun handleDeeplinks(intent: Intent) {
        client.handleDeeplinks(intent)
    }

    fun getCurrentUser() = auth.currentSessionOrNull()?.user

    suspend fun signOut() = auth.signOut()
}
