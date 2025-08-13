package com.torto.trgym.viewmodel.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.ExerciseRepository
import com.torto.trgym.data.repository.WorkoutRepository
import com.torto.trgym.database.dto.ExerciseDetail
import com.torto.trgym.database.dto.ExerciseWithImage
import com.torto.trgym.database.dto.WorkoutExerciseDetail
import com.torto.trgym.database.entities.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val workoutCardId: String = checkNotNull(savedStateHandle["workoutCardId"])

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _workoutCard = MutableStateFlow<WorkoutCard?>(null)
    val workoutCard: StateFlow<WorkoutCard?> = _workoutCard.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    // Temporary changes map for batch saving
    private val pendingSetChanges = mutableMapOf<String, ExerciseSet>()

    // Lista degli esercizi con i relativi set - sempre aggiornata dal database
    val exercisesWithSets = workoutRepository.getExercisesForCard(workoutCardId)
        .flatMapLatest { exercises ->
            if (exercises.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    exercises.map { exercise ->
                        workoutRepository.getSetsForExercise(exercise.id)
                            .map { sets ->
                                // Apply pending changes to display
                                val updatedSets = sets.map { set ->
                                    pendingSetChanges[set.id] ?: set
                                }
                                WorkoutExerciseDetail(exercise, updatedSets)
                            }
                    }
                ) { exerciseDetails ->
                    exerciseDetails.toList().sortedBy { it.exercise.orderIndex }
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
                if (_uiState.value is UiState.Loading) {
                    _uiState.value = UiState.Success
                }
            }
        }
    }

    fun toggleEditMode() {
        viewModelScope.launch {
            if (_isEditMode.value) {
                // Saving mode - persist all pending changes
                savePendingChanges()
                _isEditMode.value = false
            } else {
                // Entering edit mode
                pendingSetChanges.clear()
                _isEditMode.value = true
            }
        }
    }

    private suspend fun savePendingChanges() {
        try {
            pendingSetChanges.values.forEach { set ->
                workoutRepository.updateExerciseSet(set)
            }
            pendingSetChanges.clear()
        } catch (e: Exception) {
            _uiState.value = UiState.Error(e.message ?: "Errore nel salvataggio delle modifiche")
        }
    }

    fun addExercise(exerciseName: String) {
        if (!_isEditMode.value) return

        viewModelScope.launch {
            try {
                val currentExercises = exercisesWithSets.value
                val nextOrderIndex = currentExercises.size

                val newExercise = WorkoutExercise(
                    workoutCardId = workoutCardId,
                    name = exerciseName.trim(),
                    orderIndex = nextOrderIndex,
                    updatedAt = System.currentTimeMillis()
                )

                workoutRepository.insertWorkoutExercise(newExercise)

            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiunta dell'esercizio")
            }
        }
    }

    fun addMultipleExercises(selectedExercises: List<ExerciseWithImage>) {
        if (!_isEditMode.value) return

        viewModelScope.launch {
            try {
                val currentExercises = exercisesWithSets.value
                var nextOrderIndex = currentExercises.size

                selectedExercises.forEach { exercise ->
                    val newExercise = WorkoutExercise(
                        workoutCardId = workoutCardId,
                        exerciseId = exercise.id,  // Mantieni il riferimento all'ID originale
                        name = exercise.title.trim(),
                        orderIndex = nextOrderIndex++,
                        updatedAt = System.currentTimeMillis()
                    )
                    workoutRepository.insertWorkoutExercise(newExercise)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiunta degli esercizi")
            }
        }
    }

    // Aggiungi un nuovo metodo per ottenere i dettagli dell'esercizio originale
    fun getOriginalExerciseDetails(workoutExercise: WorkoutExercise): Flow<ExerciseDetail?> {
        return if (workoutExercise.exerciseId != null) {
            exerciseRepository.getExerciseDetail(workoutExercise.exerciseId)
        } else {
            flowOf(null)
        }
    }

    fun deleteExercise(exercise: WorkoutExercise) {
        if (!_isEditMode.value) return

        viewModelScope.launch {
            try {
                workoutRepository.deleteWorkoutExercise(exercise)
                // Remove any pending changes for sets of this exercise
                pendingSetChanges.keys.removeAll { setId ->
                    exercisesWithSets.value.any { detail ->
                        detail.exercise.id == exercise.id && detail.sets.any { it.id == setId }
                    }
                }
                // Riordina gli esercizi rimanenti
                reorderExercisesAfterDeletion(exercise.orderIndex)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'eliminazione dell'esercizio")
            }
        }
    }

    private suspend fun reorderExercisesAfterDeletion(deletedOrderIndex: Int) {
        val currentExercises = exercisesWithSets.value
        currentExercises
            .filter { it.exercise.orderIndex > deletedOrderIndex }
            .forEach { exerciseDetail ->
                val updatedExercise = exerciseDetail.exercise.copy(
                    orderIndex = exerciseDetail.exercise.orderIndex - 1,
                    updatedAt = System.currentTimeMillis()
                )
                workoutRepository.updateWorkoutExercise(updatedExercise)
            }
    }

    fun addSetToExercise(exerciseId: String) {
        if (!_isEditMode.value) return

        viewModelScope.launch {
            try {
                val currentSets = workoutRepository.getSetsForExercise(exerciseId).first()
                val nextSetNumber = (currentSets.maxOfOrNull { it.setNumber } ?: 0) + 1

                val newSet = ExerciseSet(
                    exerciseId = exerciseId,
                    setNumber = nextSetNumber,
                    weightKg = null,
                    reps = null,
                    updatedAt = System.currentTimeMillis()
                )

                workoutRepository.insertExerciseSet(newSet)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiunta del set")
            }
        }
    }

    fun updateExerciseSet(set: ExerciseSet) {
        if (!_isEditMode.value) return

        // Store changes temporarily - merge with existing pending changes to preserve both weight and reps
        val existingPendingSet = pendingSetChanges[set.id]
        val mergedSet = if (existingPendingSet != null) {
            // Merge changes: keep non-null values from both sets
            set.copy(
                weightKg = set.weightKg ?: existingPendingSet.weightKg,
                reps = set.reps ?: existingPendingSet.reps,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            set.copy(updatedAt = System.currentTimeMillis())
        }

        pendingSetChanges[set.id] = mergedSet
    }

    fun deleteExerciseSet(set: ExerciseSet) {
        if (!_isEditMode.value) return

        viewModelScope.launch {
            try {
                workoutRepository.deleteExerciseSetAndReorder(set)
                pendingSetChanges.remove(set.id)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'eliminazione del set")
            }
        }
    }

    fun updateWorkoutCard(name: String, description: String?) {
        if (!_isEditMode.value) return

        viewModelScope.launch {
            try {
                val currentCard = _workoutCard.value ?: return@launch
                val updatedCard = currentCard.copy(
                    name = name.trim(),
                    description = description?.trim(),
                    updatedAt = System.currentTimeMillis()
                )
                workoutRepository.updateWorkoutCard(updatedCard)
                _workoutCard.value = updatedCard
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiornamento della scheda")
            }
        }
    }

    fun moveExercise(fromPosition: Int, toPosition: Int) {
        viewModelScope.launch {
            try {
                val exercises = exercisesWithSets.value.toMutableList()
                if (fromPosition in exercises.indices && toPosition in exercises.indices) {
                    val movedExercise = exercises.removeAt(fromPosition)
                    exercises.add(toPosition, movedExercise)

                    // Aggiorna gli orderIndex
                    exercises.forEachIndexed { index, exerciseDetail ->
                        val updatedExercise = exerciseDetail.exercise.copy(
                            orderIndex = index,
                            updatedAt = System.currentTimeMillis()
                        )
                        workoutRepository.updateWorkoutExercise(updatedExercise)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nel riordino degli esercizi")
            }
        }
    }

    sealed interface UiState {
        object Loading : UiState
        object Success : UiState
        data class Error(val message: String) : UiState
    }
}