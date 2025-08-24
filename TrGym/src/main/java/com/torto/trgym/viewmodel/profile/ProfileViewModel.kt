package com.torto.trgym.viewmodel.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.repository.AuthRepository
import com.torto.trgym.repository.BodyMeasurementRepository
import com.torto.trgym.repository.DataCleanupRepository
import com.torto.trgym.repository.ExerciseRepository
import com.torto.trgym.repository.WorkoutRepository
import com.torto.trgym.utils.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val workoutRepository: WorkoutRepository,
    private val bodyMeasurementRepository: BodyMeasurementRepository,
    private val exerciseRepository: ExerciseRepository,
    private val dataCleanupRepository: DataCleanupRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState: StateFlow<ProfileUiState> = _uiState


    init {
        fetchUserProfile()
    }

    private fun fetchUserProfile() {
        viewModelScope.launch {
            try {
                val totalWorkouts = workoutRepository.countWorkoutSessions()
                val totalCalories = workoutRepository.getTotalCalories()

                val profileData =

                    ProfileData(
                        email = preferencesManager.getUserEmail() ?: "Ospite",
                        displayName = preferencesManager.getDisplayName() ?: "Utente",
                        totalWorkouts = totalWorkouts,
                        totalCalories = totalCalories
                    )

                _uiState.value = ProfileUiState.Data(profileData)

            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error loading profile", e)
                // Fallback to offline mode on error
                val totalWorkouts = workoutRepository.countWorkoutSessions()
                val totalCalories = workoutRepository.getTotalCalories()
                val profileData = ProfileData(
                    email = preferencesManager.getUserEmail() ?: "Ospite",
                    displayName = preferencesManager.getDisplayName() ?: "Utente",
                    totalWorkouts = totalWorkouts,
                    totalCalories = totalCalories
                )
                _uiState.value = ProfileUiState.Data(profileData)
            }
        }
    }


    fun logout() {
        viewModelScope.launch {
            try {
                // First clear all user data from Room
                dataCleanupRepository.clearAllUserData()
                Log.d("ProfileViewModel", "User data cleared from Room")
                
                // Then sign out from Supabase
                authRepository.signOut()
                Log.d("ProfileViewModel", "Signed out from Supabase")
                
                _uiState.value = ProfileUiState.LoggedOut
            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Error during logout", e)
                _uiState.value = ProfileUiState.Error("Error logging out: ${e.message}")
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
                exerciseRepository.syncStarredExercises()

                // Dopo il sync, ottieni i dati aggiornati del profilo
                val user = authRepository.getCurrentUser()
                if (user == null) {
                    _uiState.value = ProfileUiState.LoggedOut
                    return@launch
                }

                val totalWorkouts = workoutRepository.countWorkoutSessions()
                val totalCalories = workoutRepository.getTotalCalories()

                // Try different ways to get display_name for sync
                var displayName = user.userMetadata?.get("display_name")?.toString()?.removeSurrounding("\"")
                if (displayName.isNullOrEmpty()) {
                    displayName = user.userMetadata?.get("username")?.toString()?.removeSurrounding("\"")
                }
                if (displayName.isNullOrEmpty()) {
                    displayName = user.userMetadata?.get("name")?.toString()?.removeSurrounding("\"")
                }
                if (displayName.isNullOrEmpty()) {
                    displayName = "Utente"
                }
                
                // Save display_name to preferences for offline use
                preferencesManager.setDisplayName(displayName)

                val profileData = ProfileData(
                    email = user.email ?: "Ospite",
                    displayName = displayName,
                    totalWorkouts = totalWorkouts,
                    totalCalories = totalCalories
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
        val totalCalories: Int
    )
}