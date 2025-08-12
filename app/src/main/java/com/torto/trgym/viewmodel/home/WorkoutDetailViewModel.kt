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
import java.lang.System
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

    // Lista degli esercizi con i relativi set - sempre aggiornata dal database
    val exercisesWithSets = workoutRepository.getExercisesForCard(workoutCardId)
        .flatMapLatest { exercises ->
            if (exercises.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    exercises.map { exercise ->
                        workoutRepository.getSetsForExercise(exercise.id)
                            .map { sets -> WorkoutExerciseDetail(exercise, sets) }
                    }
                ) { exerciseDetails ->
                    exerciseDetails.toList()
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadWorkoutCard()
        observeExercises()
    }

    private fun loadWorkoutCard() {
        viewModelScope.launch {
            try {
                val card = workoutRepository.getWorkoutCard(workoutCardId)
                _workoutCard.value = card

                if (card == null) {
                    _uiState.value = UiState.Error("Scheda non trovata")
                } else {
                    _uiState.value = UiState.Success
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nel caricamento")
            }
        }
    }

    private fun observeExercises() {
        viewModelScope.launch {
            exercisesWithSets.collect {
                // Gli esercizi sono sempre aggiornati automaticamente dal flow
                // Non serve fare nulla qui, ma possiamo aggiornare lo stato se necessario
                if (_uiState.value is UiState.Loading) {
                    _uiState.value = UiState.Success
                }
            }
        }
    }

    fun addExercise(exerciseName: String) {
        viewModelScope.launch {
            try {
                val currentExercises = exercisesWithSets.value
                val nextOrderIndex = currentExercises.size

                val newExercise = WorkoutExercise(
                    workoutCardId = workoutCardId,
                    name = exerciseName,
                    orderIndex = nextOrderIndex,
                    updatedAt = System.currentTimeMillis()
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

                // Non serve ricaricare, il flow si aggiorna automaticamente
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiunta dell'esercizio")
            }
        }
    }

    fun addMultipleExercises(exerciseNames: List<String>) {
        viewModelScope.launch {
            try {
                val currentExercises = exercisesWithSets.value
                var nextOrderIndex = currentExercises.size

                exerciseNames.forEach { exerciseName ->
                    val newExercise = WorkoutExercise(
                        workoutCardId = workoutCardId,
                        name = exerciseName,
                        orderIndex = nextOrderIndex++,
                        updatedAt = System.currentTimeMillis()

                    )

                    workoutRepository.insertWorkoutExercise(newExercise)
                }

                // Non serve ricaricare, il flow si aggiorna automaticamente
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiunta degli esercizi")
            }
        }
    }

    fun deleteExercise(exercise: WorkoutExercise) {
        viewModelScope.launch {
            try {
                workoutRepository.deleteWorkoutExercise(exercise)
                // Non serve ricaricare, il flow si aggiorna automaticamente
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'eliminazione dell'esercizio")
            }
        }
    }

    fun addSetToExercise(exerciseId: String) {
        viewModelScope.launch {
            try {
                // Ottiene i set correnti
                val currentSets = workoutRepository.getSetsForExercise(exerciseId).first()
                val nextSetNumber = (currentSets.maxOfOrNull { it.setNumber } ?: 0) + 1

                val newSet = ExerciseSet(
                    exerciseId = exerciseId,
                    setNumber = nextSetNumber,
                    weightKg = null,
                    reps = null
                )

                // Inserisce il nuovo set nel database
                workoutRepository.insertExerciseSet(newSet)

                // Non serve aggiornare manualmente, il flow si aggiorna automaticamente
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiunta del set")
            }
        }
    }

    fun updateExerciseSet(set: ExerciseSet) {
        viewModelScope.launch {
            try {
                // Aggiorna il set nel database
                workoutRepository.updateExerciseSet(set)

                // Non serve ricaricare, il flow si aggiorna automaticamente
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiornamento del set")
            }
        }
    }

    fun deleteExerciseSet(set: ExerciseSet) {
        viewModelScope.launch {
            try {
                workoutRepository.deleteExerciseSet(set)
                // Non serve ricaricare, il flow si aggiorna automaticamente
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