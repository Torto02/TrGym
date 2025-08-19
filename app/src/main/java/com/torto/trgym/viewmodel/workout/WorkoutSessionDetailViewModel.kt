package com.torto.trgym.viewmodel.workout

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.WorkoutRepository
import com.torto.trgym.database.dto.WorkoutSessionExerciseDetail
import com.torto.trgym.database.entities.WorkoutSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutSessionDetailViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _workoutSession = MutableStateFlow<WorkoutSession?>(null)
    val workoutSession: StateFlow<WorkoutSession?> = _workoutSession.asStateFlow()

    private val _exercisesWithSets = MutableStateFlow<List<WorkoutSessionExerciseDetail>>(emptyList())
    val exercisesWithSets: StateFlow<List<WorkoutSessionExerciseDetail>> = _exercisesWithSets.asStateFlow()

    fun loadWorkoutSession(sessionId: String) {
        Log.d("WorkoutSessionDetailViewModel", "Inizio caricamento sessione: $sessionId")
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading

                // Carica i dati della sessione
                Log.d("WorkoutSessionDetailViewModel", "Caricamento dati sessione...")
                val sessions = workoutRepository.getLatestWorkoutSessions(1000)
                val session = sessions.find { it.id == sessionId }

                if (session == null) {
                    Log.d("WorkoutSessionDetailViewModel", "Sessione non trovata con ID: $sessionId")
                    _uiState.value = UiState.Error("Sessione di allenamento non trovata")
                    return@launch
                }

                Log.d("WorkoutSessionDetailViewModel", "Sessione trovata: ${session.name}")
                _workoutSession.value = session

                // Carica gli esercizi con i set usando il metodo corretto
                Log.d("WorkoutSessionDetailViewModel", "Caricamento esercizi per sessione: $sessionId")
                val exercises = workoutRepository.getSessionExercises(sessionId)
                Log.d("WorkoutSessionDetailViewModel", "Esercizi trovati: ${exercises.size}")

                // Per ogni esercizio, carica i suoi set
                val exercisesWithSets = mutableListOf<WorkoutSessionExerciseDetail>()
                for (exercise in exercises) {
                    val sets = workoutRepository.getSessionSets(exercise.id)
                    Log.d("WorkoutSessionDetailViewModel", "Set per esercizio ${exercise.name}: ${sets.size}")
                    exercisesWithSets.add(WorkoutSessionExerciseDetail(exercise, sets))
                }

                _exercisesWithSets.value = exercisesWithSets.sortedBy { it.exercise.orderIndex }
                Log.d("WorkoutSessionDetailViewModel", "Caricamento completato. Esercizi totali: ${exercisesWithSets.size}")

                _uiState.value = UiState.Success

            } catch (e: Exception) {
                Log.e("WorkoutSessionDetailViewModel", "Errore nel caricamento", e)
                _uiState.value = UiState.Error("Errore nel caricamento: ${e.message}")
            }
        }
    }


    sealed interface UiState {
        object Loading : UiState
        object Success : UiState
        data class Error(val message: String) : UiState
    }
}