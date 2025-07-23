package com.torto.trgym.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
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

/**
 * Repository per la gestione dell'autenticazione.
 * Utilizza Supabase per le operazioni di login, registrazione e gestione delle sessioni.
 * È annotato con @Singleton per garantire un'unica istanza tramite Hilt.
 *
 * @param client Istanza di SupabaseClient iniettata tramite Hilt.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val client: SupabaseClient
) {

    // Riferimento al modulo di autenticazione di Supabase
    private val auth = client.auth

    /**
     * Osserva lo stato della sessione di autenticazione.
     *
     * @return Stato della sessione come Flow.
     */
    fun observeAuthStatus() = auth.sessionStatus

    /**
     * Effettua la registrazione di un nuovo utente con email e password.
     * Può includere un nome utente opzionale.
     *
     * @param email Email dell'utente.
     * @param password Password dell'utente.
     * @param username Nome utente opzionale.
     * @return Risultato della registrazione.
     * @throws Exception Se si verifica un errore durante la registrazione.
     */
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
                result
            } catch (e: Exception) {
                // Gestisce errori specifici di Supabase per email già registrate
                val errorMessage = e.message ?: ""
                if (errorMessage.contains("already") && errorMessage.contains("email")) {
                    Log.e("AuthRepository", "Email già registrata", e)
                    throw Exception("Questa email è già associata ad un account. Prova ad accedere con password o Google.")
                } else {
                    Log.e("AuthRepository", "Signup error", e)
                    throw e
                }
            }
        }

    /**
     * Effettua il login di un utente con email e password.
     *
     * @param email Email dell'utente.
     * @param password Password dell'utente.
     * @return Sessione di autenticazione.
     */
    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /**
     * Avvia il processo di login con Google utilizzando un Custom Tab.
     *
     * @param context Contesto dell'applicazione.
     */
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

    /**
     * Gestisce i deep link per l'autenticazione.
     *
     * @param intent Intent ricevuto dall'app.
     */
    fun handleDeeplinks(intent: Intent) {
        client.handleDeeplinks(intent)
    }

    /**
     * Recupera l'utente attualmente autenticato.
     *
     * @return Utente autenticato o null se non c'è una sessione attiva.
     */
    fun getCurrentUser() = auth.currentSessionOrNull()?.user

    /**
     * Effettua il logout dell'utente corrente.
     */
    suspend fun signOut() = auth.signOut()
}