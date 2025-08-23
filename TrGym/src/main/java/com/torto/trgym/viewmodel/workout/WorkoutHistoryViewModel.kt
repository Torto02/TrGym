package com.torto.trgym.viewmodel.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.torto.trgym.database.dto.WorkoutSessionWithCalories

@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _workoutSessions = MutableStateFlow<List<WorkoutSessionWithCalories>>(emptyList())
    val workoutSessions: StateFlow<List<WorkoutSessionWithCalories>> = _workoutSessions.asStateFlow()

    init {
        loadWorkoutHistory()
    }

    private fun loadWorkoutHistory() {
        viewModelScope.launch {
            try {
                _uiState.value = UiState.Loading

                val sessions = workoutRepository.getLatestWorkoutSessions(20)
                    .filter { it.isCompleted } // Solo allenamenti completati
                    .sortedByDescending { it.startTime } // Ordine decrescente per data

                // Carica le calorie per ogni sessione
                val sessionsWithCalories = sessions.map { session ->
                    val calories = workoutRepository.getCaloriesForWorkout(session.id)
                    WorkoutSessionWithCalories(session, calories)
                }

                _workoutSessions.value = sessionsWithCalories
                _uiState.value = UiState.Success

            } catch (e: Exception) {
                _uiState.value = UiState.Error("History loading error: ${e.message}")
            }
        }
    }

    sealed interface UiState {
        object Loading : UiState
        object Success : UiState
        data class Error(val message: String) : UiState
    }
}