package com.torto.trgym.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.torto.trgym.utils.PreferencesManager
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
    private val client: SupabaseClient,
    private val preferencesManager: PreferencesManager
) {

    private val auth = client.auth

    fun observeAuthStatus() = auth.sessionStatus

    suspend fun signUp(email: String, password: String, username: String? = null) =
        withContext(Dispatchers.IO) {
            try {
                val result = auth.signUpWith(Email, redirectUrl = "com.torto.trgym://login-callback") {
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
                Log.d("AuthRepository", "Signup result: $result")
            } catch (e: Exception) {
                // Gestisce errori specifici di Supabase per email già registrate
                val errorMessage = e.message ?: ""
                Log.d("AuthRepository", "Signup error message: $errorMessage")
                if (errorMessage.contains("already") && errorMessage.contains("user")) {
                    Log.e("AuthRepository", "Email already registered", e)
                    throw Exception("This email is already in use.")
                } else {
                    Log.e("AuthRepository", "Signup error", e)
                    throw e
                }
            }
        }

    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        try {
            val result = auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            
            // Save login state to SharedPreferences
            preferencesManager.setLoggedIn(true)
            preferencesManager.setUserEmail(email)
            auth.currentSessionOrNull()?.user?.id?.let { userId ->
                preferencesManager.setUserId(userId)
            }
            
            Log.d("AuthRepository", "User login state saved to SharedPreferences")
            result
        }catch (e: Exception) {
            Log.e("AuthRepository", "SignIn error", e)
            val errorMessage = e.message ?: ""
            if (errorMessage.contains("invalid") && errorMessage.contains("credentials")) {
                throw Exception("Invalid Credentials.")
            } else {
                Log.e("AuthRepository", "Sign in error", e)
                throw e
            }
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

    suspend fun signOut() {
        // Clear login state from SharedPreferences
        preferencesManager.clearUserData()
        Log.d("AuthRepository", "User login state cleared from SharedPreferences")
        
        auth.signOut()
    }
}