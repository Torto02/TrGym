package com.torto.trgym.viewmodel.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.repository.AuthRepository
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
    private val authRepository: AuthRepository
) : ViewModel() {

    // Stato interno della UI, modificabile solo all'interno del ViewModel
    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)

    // Stato pubblico della UI, esposto come StateFlow
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun register(email: String, username: String, password: String, confirm: String) {
        if (email.isBlank() || username.isBlank() || password.isBlank() || confirm.isBlank()) {
            _uiState.value = RegisterUiState.Error("Fill in all the fields")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = RegisterUiState.Error("Invalid email")
            return
        }
        if (password.length < 6) {
            _uiState.value = RegisterUiState.Error("Password must be at least 6 characters long")
            return
        }
        if (password != confirm) {
            _uiState.value = RegisterUiState.Error("Mismatched passwords")
            return
        }

        viewModelScope.launch {
            _uiState.value = RegisterUiState.Loading
            try {
                authRepository.signUp(email, password, username)
                _uiState.value = RegisterUiState.Success
            } catch (e: Exception) {
                _uiState.value = RegisterUiState.Error(e.message ?: "Registration failed")
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        authRepository.signInWithGoogle(context) // Chiamata al repository per il login con Google
    }

    sealed interface RegisterUiState {
        object Idle   : RegisterUiState // Stato iniziale, nessuna azione in corso
        object Loading: RegisterUiState // Stato di caricamento
        object Success: RegisterUiState // Stato di successo
        data class Error(val message: String): RegisterUiState // Stato di errore con messaggio
    }
}