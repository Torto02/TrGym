package com.torto.trgym.viewmodel.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.repository.WorkoutRepository
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
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading

                val sessions = workoutRepository.getLatestWorkoutSessions(1000)
                val session = sessions.find { it.id == sessionId }

                if (session == null) {
                    _uiState.value = UiState.Error("Workout session not found")
                    return@launch
                }
                _workoutSession.value = session
                val exercises = workoutRepository.getSessionExercises(sessionId)

                // Per ogni esercizio, carica i suoi set
                val exercisesWithSets = mutableListOf<WorkoutSessionExerciseDetail>()
                for (exercise in exercises) {
                    val sets = workoutRepository.getSessionSets(exercise.id)
                    exercisesWithSets.add(WorkoutSessionExerciseDetail(exercise, sets))
                }

                _exercisesWithSets.value = exercisesWithSets.sortedBy { it.exercise.orderIndex }
                _uiState.value = UiState.Success

            } catch (e: Exception) {
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