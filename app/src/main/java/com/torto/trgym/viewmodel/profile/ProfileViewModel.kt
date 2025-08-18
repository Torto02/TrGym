////package com.torto.trgym.viewmodel.profile
////
////import android.util.Log
////import androidx.lifecycle.ViewModel
////import androidx.lifecycle.viewModelScope
////import com.torto.trgym.data.repository.AuthRepository
////import com.torto.trgym.data.repository.WorkoutRepository
////import dagger.hilt.android.lifecycle.HiltViewModel
////import kotlinx.coroutines.flow.MutableStateFlow
////import kotlinx.coroutines.flow.StateFlow
////import kotlinx.coroutines.flow.count
////import kotlinx.coroutines.launch
////import javax.inject.Inject
////
////@HiltViewModel
////class ProfileViewModel @Inject constructor(
////    private val authRepository: AuthRepository,
////    private val workoutRepository: WorkoutRepository
////) : ViewModel() {
////
////    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
////    val uiState: StateFlow<ProfileUiState> = _uiState
////
////    init {
////        fetchUserProfile()
////    }
////
////    private fun fetchUserProfile() {
////        viewModelScope.launch {
////            try {
////                val user = authRepository.getCurrentUser()
////
////                if (user == null) {
////                    _uiState.value = ProfileUiState.LoggedOut
////                    return@launch
////                }
////
////                val totalWorkouts = workoutRepository.countWorkoutSessions()
////                Log.d("ProfileViewModel", "Totale esercizi: $totalWorkouts")
////
////                val totalCalories = calculateTotalCalories()
////
////                val profileData = ProfileData(
////                    email = user.email ?: "Ospite",
////                    displayName = user.userMetadata?.get("full_name")?.toString() ?: "Utente",
////                    totalWorkouts = totalWorkouts,
////                    totalCalories = totalCalories
////                )
////
////                Log.d("ProfileViewModel", "Dati profilo: $profileData")
////
////                _uiState.value = ProfileUiState.Data(profileData)
////
////            } catch (e: Exception) {
////                _uiState.value = ProfileUiState.Error("Errore nel caricamento del profilo")
////            }
////        }
////    }
////
////    fun refreshProfile() {
////        fetchUserProfile()
////    }
////
////
////    private suspend fun calculateTotalCalories(): Int {
////        // Per ora ritorna un valore mock
////        // TODO: Implementare calcolo calorie quando avremo l'integrazione smartwatch
////        return 2100
////    }
////
////    fun logout() {
////        viewModelScope.launch {
////            //! Mostriamo un dialog di conferma prima di effettuare il logout
////            try {
////                authRepository.signOut()
////                _uiState.value = ProfileUiState.LoggedOut
////            } catch (e: Exception) {
////                _uiState.value = ProfileUiState.Error("Errore durante il logout")
////            }
////        }
////    }
////
////    sealed interface ProfileUiState {
////        object Loading : ProfileUiState
////        data class Data(val profileData: ProfileData) : ProfileUiState
////        data class Error(val message: String) : ProfileUiState
////        object LoggedOut : ProfileUiState
////    }
////
////    data class ProfileData(
////        val email: String,
////        val displayName: String,
////        val totalWorkouts: Int,
////        val totalCalories: Int
////    )
////
////
////}
//package com.torto.trgym.viewmodel.profile
//
//import android.util.Log
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.torto.trgym.data.repository.AuthRepository
//import com.torto.trgym.data.repository.WorkoutRepository
//import dagger.hilt.android.lifecycle.HiltViewModel
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.launch
//import javax.inject.Inject
//
//@HiltViewModel
//class ProfileViewModel @Inject constructor(
//    private val authRepository: AuthRepository,
//    private val workoutRepository: WorkoutRepository
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
//
//                if (user == null) {
//                    _uiState.value = ProfileUiState.LoggedOut
//                    return@launch
//                }
//
//                val totalWorkouts = workoutRepository.countWorkoutSessions()
//                Log.d("ProfileViewModel", "Totale esercizi: $totalWorkouts")
//
//                val totalCalories = calculateTotalCalories()
//
//                val profileData = ProfileData(
//                    email = user.email ?: "Ospite",
//                    displayName = user.userMetadata?.get("full_name")?.toString() ?: "Utente",
//                    totalWorkouts = totalWorkouts,
//                    totalCalories = totalCalories
//                )
//
//                Log.d("ProfileViewModel", "Dati profilo: $profileData")
//
//                _uiState.value = ProfileUiState.Data(profileData)
//
//            } catch (e: Exception) {
//                _uiState.value = ProfileUiState.Error("Errore nel caricamento del profilo")
//            }
//        }
//    }
//
//    fun refreshProfile() {
//        fetchUserProfile()
//    }
//
//    private suspend fun calculateTotalCalories(): Int {
//        // Per ora ritorna un valore mock
//        // TODO: Implementare calcolo calorie quando avremo l'integrazione smartwatch
//        return 2100
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
//    // Nuova funzione per la sincronizzazione
//    fun syncWithSupabase() {
//        viewModelScope.launch {
//            try {
//                _uiState.value = ProfileUiState.Syncing
//
//                // Effettua il pull completo da Supabase come all'avvio dell'app
//                // Usa la funzione downloadWorkoutCardsInitial() che fa:
//                // 1. Prima carica le modifiche locali pendenti (push)
//                // 2. Poi scarica e mergia i dati da Supabase (pull)
//                // 3. Include workout cards, esercizi, set e sessioni
//                workoutRepository.downloadWorkoutCardsInitial()
//
//                Log.d("ProfileViewModel", "Sincronizzazione dati completata con successo")
//
//                // Dopo il sync, ottieni i dati aggiornati del profilo
//                val user = authRepository.getCurrentUser()
//                if (user == null) {
//                    _uiState.value = ProfileUiState.LoggedOut
//                    return@launch
//                }
//
//                val totalWorkouts = workoutRepository.countWorkoutSessions()
//                val totalCalories = calculateTotalCalories()
//
//                val profileData = ProfileData(
//                    email = user.email ?: "Ospite",
//                    displayName = user.userMetadata?.get("full_name")?.toString() ?: "Utente",
//                    totalWorkouts = totalWorkouts,
//                    totalCalories = totalCalories
//                )
//
//                Log.d(
//                    "ProfileViewModel",
//                    "Sincronizzazione completata. Nuovi dati profilo: $profileData"
//                )
//
//                // Indica che la sincronizzazione è completata con i dati aggiornati
//                _uiState.value = ProfileUiState.SyncCompleted(profileData)
//
//            } catch (e: Exception) {
//                Log.e("ProfileViewModel", "Errore durante la sincronizzazione", e)
//                _uiState.value =
//                    ProfileUiState.Error("Errore durante la sincronizzazione: ${e.message}")
//            }
//        }
//    }
//
//                    sealed interface ProfileUiState {
//                    object Loading : ProfileUiState
//                    object Syncing : ProfileUiState
//                    data class Data(val profileData: ProfileData) : ProfileUiState
//                    data class SyncCompleted(val profileData: ProfileData) : ProfileUiState
//                    data class Error(val message: String) : ProfileUiState
//                    object LoggedOut : ProfileUiState
//                }
//
//                data class ProfileData(
//                    val email: String,
//                    val displayName: String,
//                    val totalWorkouts: Int,
//                    val totalCalories: Int
//                )
//            }

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
            try {
                authRepository.signOut()
                _uiState.value = ProfileUiState.LoggedOut
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error("Errore durante il logout")
            }
        }
    }

    // Nuova funzione per la sincronizzazione
    fun syncWithSupabase() {
        viewModelScope.launch {
            try {
                _uiState.value = ProfileUiState.Syncing

                // Effettua il pull completo da Supabase come all'avvio dell'app
                // Usa la funzione downloadWorkoutCardsInitial() che fa:
                // 1. Prima carica le modifiche locali pendenti (push)
                // 2. Poi scarica e mergia i dati da Supabase (pull)
                // 3. Include workout cards, esercizi, set e sessioni
                workoutRepository.downloadWorkoutCardsInitial()

                // Sincronizza anche le body measurements
                bodyMeasurementRepository.syncUnsyncedMeasurements()
                bodyMeasurementRepository.downloadMeasurementsFromSupabase()

                Log.d("ProfileViewModel", "Sincronizzazione dati completata con successo")

                // Dopo il sync, ottieni i dati aggiornati del profilo
                val user = authRepository.getCurrentUser()
                if (user == null) {
                    _uiState.value = ProfileUiState.LoggedOut
                    return@launch
                }

                val totalWorkouts = workoutRepository.countWorkoutSessions()
                val totalCalories = calculateTotalCalories()

                val profileData = ProfileData(
                    email = user.email ?: "Ospite",
                    displayName = user.userMetadata?.get("full_name")?.toString() ?: "Utente",
                    totalWorkouts = totalWorkouts,
                    totalCalories = totalCalories
                )

                Log.d(
                    "ProfileViewModel",
                    "Sincronizzazione completata. Nuovi dati profilo: $profileData"
                )

                // Indica che la sincronizzazione è completata con i dati aggiornati
                _uiState.value = ProfileUiState.SyncCompleted(profileData)

            } catch (e: Exception) {
                Log.e("ProfileViewModel", "Errore durante la sincronizzazione", e)
                _uiState.value =
                    ProfileUiState.Error("Errore durante la sincronizzazione: ${e.message}")
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