package com.torto.trgym.viewmodel.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel per la gestione della registrazione utente.
 * Utilizza Hilt per l'iniezione delle dipendenze.
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository // Repository per le operazioni di autenticazione
) : ViewModel() {

    // Stato interno della UI, modificabile solo all'interno del ViewModel
    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    // Stato pubblico della UI, esposto come StateFlow
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    /**
     * Metodo per registrare un nuovo utente.
     * Esegue validazioni sui campi e tenta la registrazione tramite il repository.
     *
     * @param email Email dell'utente
     * @param username Nome utente
     * @param password Password scelta
     * @param confirm Conferma della password
     */
    fun register(email: String, username: String, password: String, confirm: String) {
        // Validazioni di base sui campi di input
        if (email.isBlank() || username.isBlank() || password.isBlank() || confirm.isBlank()) {
            _uiState.value = RegisterUiState.Error("Compila tutti i campi")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = RegisterUiState.Error("Email non valida")
            return
        }
        if (password.length < 6) {
            _uiState.value = RegisterUiState.Error("La password deve avere ≥6 caratteri")
            return
        }
        if (password != confirm) {
            _uiState.value = RegisterUiState.Error("Le password non corrispondono")
            return
        }

        // Tentativo di registrazione tramite coroutine
        viewModelScope.launch {
            _uiState.value = RegisterUiState.Loading
            try {
                authRepository.signUp(email, password, username) // Chiamata al repository per la registrazione
                _uiState.value = RegisterUiState.Success
            } catch (e: Exception) {
                _uiState.value = RegisterUiState.Error(e.message ?: "Errore registrazione")
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
     * Stato della UI per la registrazione.
     * Può essere Idle, Loading, Success o Error.
     */
    sealed interface RegisterUiState {
        object Idle   : RegisterUiState // Stato iniziale
        object Loading: RegisterUiState // Stato di caricamento
        object Success: RegisterUiState // Stato di successo
        data class Error(val message: String): RegisterUiState // Stato di errore con messaggio
    }
}