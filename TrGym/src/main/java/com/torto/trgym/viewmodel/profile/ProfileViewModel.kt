package com.torto.trgym.viewmodel.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.AuthRepository
import com.torto.trgym.data.repository.BodyMeasurementRepository
import com.torto.trgym.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val workoutRepository: WorkoutRepository,
    private val bodyMeasurementRepository: BodyMeasurementRepository
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
//                val totalCalories = workoutRepository.getTotalHeartBeat()
                val totalCalories = "1547 kCal"
                val profileData = ProfileData(
                    email = user.email ?: "Ospite",
                    // Usa il display_name dalla tabella di autenticazione invece dei metadata
                    displayName = (user.userMetadata?.get("display_name")?.toString()
                        ?.removeSurrounding("\"")) ?: "Utente",
                    totalWorkouts = totalWorkouts,
                    totalCalories = totalCalories
                )

                _uiState.value = ProfileUiState.Data(profileData)

            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Loading profile error")
            }
        }
    }

    fun refreshProfile() {
        fetchUserProfile()
    }

    fun logout() {
        viewModelScope.launch {
            try {
                authRepository.signOut()
                _uiState.value = ProfileUiState.LoggedOut
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Error logging out")
            }
        }
    }

    // Nuova funzione per la sincronizzazione
    fun syncWithSupabase() {
        viewModelScope.launch {
            try {
                _uiState.value = ProfileUiState.Syncing

                workoutRepository.downloadWorkoutCardsInitial()
                bodyMeasurementRepository.syncUnsyncedMeasurements()
                bodyMeasurementRepository.downloadMeasurementsFromSupabase()

                // Dopo il sync, ottieni i dati aggiornati del profilo
                val user = authRepository.getCurrentUser()
                if (user == null) {
                    _uiState.value = ProfileUiState.LoggedOut
                    return@launch
                }

                val totalWorkouts = workoutRepository.countWorkoutSessions()

                val profileData = ProfileData(
                    email = user.email ?: "Ospite",
                    // Usa il display_name dalla tabella di autenticazione invece dei metadata
                    displayName = user.userMetadata?.get("display_name")?.toString() ?: "Utente",
                    totalWorkouts = totalWorkouts,
                    totalCalories = null
                )

                // Indica che la sincronizzazione è completata con i dati aggiornati
                _uiState.value = ProfileUiState.SyncCompleted(profileData)

            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Synchronization error ", e)
                _uiState.value =
                    ProfileUiState.Error("Synchronization error: ${e.message}")
            }
        }
    }

    sealed interface ProfileUiState {
        object Loading : ProfileUiState
        object Syncing : ProfileUiState
        data class Data(val profileData: ProfileData) : ProfileUiState
        data class SyncCompleted(val profileData: ProfileData) : ProfileUiState
        data class Error(val message: String) : ProfileUiState
        object LoggedOut : ProfileUiState
    }

    data class ProfileData(
        val email: String,
        val displayName: String,
        val totalWorkouts: Int,
        val totalCalories: String? = null
    )
}