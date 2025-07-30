package com.torto.trgym.viewmodel.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.WorkoutRepository
import com.torto.trgym.database.dto.WorkoutExerciseDetail
import com.torto.trgym.database.entities.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val workoutCardId: String = checkNotNull(savedStateHandle["workoutCardId"])

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    private val _workoutCard = MutableStateFlow<WorkoutCard?>(null)
    val workoutCard: StateFlow<WorkoutCard?> = _workoutCard.asStateFlow()

    // Lista degli esercizi con i relativi set
    private val _exercisesWithSets = MutableStateFlow<List<WorkoutExerciseDetail>>(emptyList())
    val exercisesWithSets: StateFlow<List<WorkoutExerciseDetail>> = _exercisesWithSets.asStateFlow()

    init {
        loadWorkoutDetail()
    }

    private fun loadWorkoutDetail() {
        viewModelScope.launch {
            try {
                // Carica la scheda di allenamento
                val card = workoutRepository.getWorkoutCard(workoutCardId)
                _workoutCard.value = card

                if (card == null) {
                    _uiState.value = UiState.Error("Scheda non trovata")
                    return@launch
                }

                // Carica gli esercizi
                workoutRepository.getExercisesForCard(workoutCardId)
                    .collect { exercises ->
                        val exercisesWithSetsData = exercises.map { exercise ->
                            val sets = workoutRepository.getSetsForExercise(exercise.id)
                                .first() // Get current value
                            WorkoutExerciseDetail(exercise, sets)
                        }

                        _exercisesWithSets.value = exercisesWithSetsData
                        _uiState.value = UiState.Success
                    }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nel caricamento")
            }
        }
    }

    fun addExercise(exerciseName: String) {
        viewModelScope.launch {
            try {
                val currentExercises = _exercisesWithSets.value
                val nextOrderIndex = currentExercises.size

                val newExercise = WorkoutExercise(
                    workoutCardId = workoutCardId,
                    name = exerciseName,
                    orderIndex = nextOrderIndex
                )

                workoutRepository.insertWorkoutExercise(newExercise)

                // Aggiungi 3 set vuoti di default
                val defaultSets = (1..3).map { setNumber ->
                    ExerciseSet(
                        exerciseId = newExercise.id,
                        setNumber = setNumber
                    )
                }
                workoutRepository.insertExerciseSets(defaultSets)

                // Ricarica i dati
                loadWorkoutDetail()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiunta dell'esercizio")
            }
        }
    }

    fun deleteExercise(exercise: WorkoutExercise) {
        viewModelScope.launch {
            try {
                workoutRepository.deleteWorkoutExercise(exercise)
                loadWorkoutDetail()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'eliminazione dell'esercizio")
            }
        }
    }

    fun addSetToExercise(exerciseId: String) {
        viewModelScope.launch {
            try {
                val currentSets = workoutRepository.getSetsForExercise(exerciseId).first()
                val nextSetNumber = (currentSets.maxOfOrNull { it.setNumber } ?: 0) + 1

                val newSet = ExerciseSet(
                    exerciseId = exerciseId,
                    setNumber = nextSetNumber
                )

                workoutRepository.insertExerciseSet(newSet)
                loadWorkoutDetail()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiunta del set")
            }
        }
    }

    fun updateExerciseSet(set: ExerciseSet) {
        viewModelScope.launch {
            try {
                workoutRepository.updateExerciseSet(set)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiornamento del set")
            }
        }
    }

    fun deleteExerciseSet(set: ExerciseSet) {
        viewModelScope.launch {
            try {
                workoutRepository.deleteExerciseSet(set)
                loadWorkoutDetail()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'eliminazione del set")
            }
        }
    }

    sealed interface UiState {
        object Loading : UiState
        object Success : UiState
        data class Error(val message: String) : UiState
    }
}