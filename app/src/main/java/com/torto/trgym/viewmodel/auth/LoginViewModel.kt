package com.torto.trgym.viewmodel.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel per la gestione del login utente.
 * Utilizza Hilt per l'iniezione delle dipendenze.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository // Repository per le operazioni di autenticazione
) : ViewModel() {

    // Stato interno della UI, modificabile solo all'interno del ViewModel
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    // Stato pubblico della UI, esposto come StateFlow
    val uiState: StateFlow<LoginUiState> = _uiState

    /**
     * Metodo per effettuare il login con email e password.
     * Esegue validazioni sui campi e tenta il login tramite il repository.
     *
     * @param email Email dell'utente
     * @param password Password dell'utente
     */
    fun login(email: String, password: String) {
        // Validazione minima sui campi di input
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Compila tutti i campi")
            return
        }

        // Tentativo di login tramite coroutine
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                authRepository.signIn(email, password) // Chiamata al repository per il login
                _uiState.value = LoginUiState.Success
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Login fallito")
            }
        }
    }

    /**
     * Metodo per effettuare il login con Google.
     *
     * @param context Contesto dell'applicazione
     */
    fun signInWithGoogle(context: Context) {
        authRepository.signInWithGoogle(context) // Chiamata al repository per il login con Google
    }

    /**
     * Stato della UI per il login.
     * Può essere Idle, Loading, Success o Error.
     */
    sealed interface LoginUiState {
        object Idle    : LoginUiState // Stato iniziale
        object Loading : LoginUiState // Stato di caricamento
        object Success : LoginUiState // Stato di successo
        data class Error(val message: String) : LoginUiState // Stato di errore con messaggio
    }
}