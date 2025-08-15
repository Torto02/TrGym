package com.torto.trgym.viewmodel.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.AuthRepository
import com.torto.trgym.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        fetchUserProfile()
    }

    private fun fetchUserProfile() {
        viewModelScope.launch {
            try {
                val user = authRepository.getCurrentUser()

                if (user == null) {
                    _uiState.value = ProfileUiState.LoggedOut
                    return@launch
                }

                val totalWorkouts = workoutRepository.countWorkoutSessions()
                Log.d("ProfileViewModel", "Totale esercizi: $totalWorkouts")

                val totalCalories = calculateTotalCalories()

                val profileData = ProfileData(
                    email = user.email ?: "Ospite",
                    displayName = user.userMetadata?.get("full_name")?.toString() ?: "Utente",
                    totalWorkouts = totalWorkouts,
                    totalCalories = totalCalories
                )

                Log.d("ProfileViewModel", "Dati profilo: $profileData")

                _uiState.value = ProfileUiState.Data(profileData)

            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Errore nel caricamento del profilo")
            }
        }
    }

    fun refreshProfile() {
        fetchUserProfile()
    }


    private suspend fun calculateTotalCalories(): Int {
        // Per ora ritorna un valore mock
        // TODO: Implementare calcolo calorie quando avremo l'integrazione smartwatch
        return 2100
    }

    fun logout() {
        viewModelScope.launch {
            //! Mostriamo un dialog di conferma prima di effettuare il logout
            try {
                authRepository.signOut()
                _uiState.value = ProfileUiState.LoggedOut
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Errore durante il logout")
            }
        }
    }

    sealed interface ProfileUiState {
        object Loading : ProfileUiState
        data class Data(val profileData: ProfileData) : ProfileUiState
        data class Error(val message: String) : ProfileUiState
        object LoggedOut : ProfileUiState
    }

    data class ProfileData(
        val email: String,
        val displayName: String,
        val totalWorkouts: Int,
        val totalCalories: Int
    )


}