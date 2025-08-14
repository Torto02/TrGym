//package com.torto.trgym.viewmodel.profile
//
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.torto.trgym.data.repository.AuthRepository
//// TODO: Implementare in versioni future
//// import com.torto.trgym.data.repository.WorkoutRepository
//// import com.torto.trgym.data.repository.UserStatsRepository
//// import com.torto.trgym.data.repository.WearableRepository
//import dagger.hilt.android.lifecycle.HiltViewModel
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//// import kotlinx.coroutines.flow.combine
//import kotlinx.coroutines.launch
//import javax.inject.Inject
//
//@HiltViewModel
//class ProfileViewModel @Inject constructor(
//    private val authRepository: AuthRepository
//    // TODO: Aggiungere questi repository quando implementati
//    // private val workoutRepository: WorkoutRepository,
//    // private val userStatsRepository: UserStatsRepository,
//    // private val wearableRepository: WearableRepository
//) : ViewModel() {
//
//    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
//    val uiState: StateFlow<ProfileUiState> = _uiState
//
//    init {
//        fetchUserProfile()
//    }
//
//    private fun fetchUserProfile() {
//        viewModelScope.launch {
//            try {
//                val user = authRepository.getCurrentUser()
//                if (user == null) {
//                    _uiState.value = ProfileUiState.LoggedOut
//                    return@launch
//                }
//
//                // Per ora usiamo dati mock - TODO: implementare repository reali
//                val profileData = ProfileData(
//                    email = user.email ?: "Ospite",
//                    displayName =  "Utente",
//                    totalWorkouts = 42,  // Mock data
//                    currentStreak = 7,   // Mock data
//                    totalCalories = 2100 // Mock data
//                )
//
//                _uiState.value = ProfileUiState.Data(profileData)
//
//                /* TODO: Implementare quando i repository saranno disponibili
//                combine(
//                    userStatsRepository.getTotalWorkouts(),
//                    userStatsRepository.getCurrentStreak(),
//                    userStatsRepository.getTotalCaloriesBurned()
//                ) { totalWorkouts, currentStreak, totalCalories ->
//                    ProfileData(
//                        email = user.email ?: "Ospite",
//                        displayName = user.displayName ?: "Utente",
//                        totalWorkouts = totalWorkouts,
//                        currentStreak = currentStreak,
//                        totalCalories = totalCalories
//                    )
//                }.collect { profileData ->
//                    _uiState.value = ProfileUiState.Data(profileData)
//                }
//                */
//            } catch (e: Exception) {
//                _uiState.value = ProfileUiState.Error("Errore nel caricamento del profilo")
//            }
//        }
//    }
//
//    fun logout() {
//        viewModelScope.launch {
//            try {
//                authRepository.signOut()
//                _uiState.value = ProfileUiState.LoggedOut
//            } catch (e: Exception) {
//                _uiState.value = ProfileUiState.Error("Errore durante il logout")
//            }
//        }
//    }
//
//    // TODO: Implementare funzioni smartwatch in versioni future
//    /*
//    fun connectWearable() {
//        viewModelScope.launch {
//            try {
//                wearableRepository.connectDevice()
//                fetchUserProfile()
//            } catch (e: Exception) {
//                _uiState.value = ProfileUiState.Error("Errore nella connessione del dispositivo")
//            }
//        }
//    }
//
//    fun disconnectWearable() {
//        viewModelScope.launch {
//            try {
//                wearableRepository.disconnectDevice()
//                fetchUserProfile()
//            } catch (e: Exception) {
//                _uiState.value = ProfileUiState.Error("Errore nella disconnessione del dispositivo")
//            }
//        }
//    }
//    */
//
//    sealed interface ProfileUiState {
//        object Loading : ProfileUiState
//        data class Data(val profileData: ProfileData) : ProfileUiState
//        data class Error(val message: String) : ProfileUiState
//        object LoggedOut : ProfileUiState
//    }
//
//    data class ProfileData(
//        val email: String,
//        val displayName: String,
//        val totalWorkouts: Int,
//        val currentStreak: Int,
//        val totalCalories: Int
//        // TODO: Aggiungere campi smartwatch in futuro
//        // val isWatchConnected: Boolean,
//        // val watchDeviceName: String?
//    )
//
//    // TODO: Implementare in versioni future
//    /*
//    data class WatchConnectionStatus(
//        val isConnected: Boolean,
//        val deviceName: String? = null,
//        val batteryLevel: Int? = null
//    )
//    */
//}

package com.torto.trgym.viewmodel.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.AuthRepository
//import com.torto.trgym.data.repository.WorkoutSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
//    private val workoutSessionRepository: WorkoutSessionRepository
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

                // Ottieni statistiche reali dal database
//                val totalWorkouts = workoutSessionRepository.getTotalCompletedWorkouts()
                val currentStreak = calculateCurrentStreak()
                val totalCalories = calculateTotalCalories()

                val profileData = ProfileData(
                    email = user.email ?: "Ospite",
                    displayName = "Utente",
                    totalWorkouts = 0,
                    currentStreak = currentStreak,
                    totalCalories = totalCalories
                )

                _uiState.value = ProfileUiState.Data(profileData)

            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Errore nel caricamento del profilo")
            }
        }
    }

    private suspend fun calculateCurrentStreak(): Int {
        // Calcola i giorni consecutivi di allenamento
//        val recentWorkouts = workoutSessionRepository.getRecentCompletedWorkouts(30)

//        if (recentWorkouts.isEmpty()) return 0

        var streak = 0
        var currentDate = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L

        // Raggruppa allenamenti per giorno
//        val workoutsByDay = recentWorkouts.groupBy { workout ->
//            (workout.startTime / oneDayMs) * oneDayMs
//        }

        // Conta i giorni consecutivi dall'oggi
//        while (workoutsByDay.containsKey((currentDate / oneDayMs) * oneDayMs)) {
//            streak++
//            currentDate -= oneDayMs
//        }

        return streak
    }

    private suspend fun calculateTotalCalories(): Int {
        // Per ora ritorna un valore mock
        // TODO: Implementare calcolo calorie quando avremo l'integrazione smartwatch
        return 2100
    }

    fun logout() {
        viewModelScope.launch {
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
        val currentStreak: Int,
        val totalCalories: Int
    )
}