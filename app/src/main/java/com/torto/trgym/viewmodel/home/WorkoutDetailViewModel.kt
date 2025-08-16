package com.torto.trgym.viewmodel.home

import android.util.Log
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)

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

    private val pendingSetChanges = mutableMapOf<String, ExerciseSet>()

    val exercisesWithSets = workoutRepository.getExercisesForCard(workoutCardId)
        .flatMapLatest { exercises ->
            if (exercises.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    exercises.map { exercise ->
                        workoutRepository.getSetsForExercise(exercise.id)
                            .map { sets ->
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

    fun refreshWorkoutCard() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            loadWorkoutCard()
            syncData()
        }
    }

    private suspend fun syncData() {
        try {
            workoutRepository.syncExercisesForCard(workoutCardId)
            workoutRepository.syncAllSetsForCard(workoutCardId)
        } catch (e: Exception) {
            Log.e("WorkoutDetailViewModel", "Error syncing data", e)
        }
    }


    init {
        loadWorkoutCard()
    }

    private fun loadWorkoutCard() {
        viewModelScope.launch {
            try {
                val card = workoutRepository.getWorkoutCard(workoutCardId)
                _workoutCard.value = card
                _uiState.value = if (card != null) UiState.Success else UiState.Error("Scheda non trovata")
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nel caricamento")
            }
        }
    }

    fun toggleEditMode() {
        viewModelScope.launch {
            if (_isEditMode.value) {
                savePendingChanges()
            } else {
                pendingSetChanges.clear()
            }
            _isEditMode.value = !_isEditMode.value
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
        executeWithErrorHandling("Errore nell'aggiunta dell'esercizio") {
            val newExercise = WorkoutExercise(
                workoutCardId = workoutCardId,
                name = exerciseName.trim(),
                orderIndex = exercisesWithSets.value.size,
                updatedAt = System.currentTimeMillis()
            )
            workoutRepository.insertWorkoutExercise(newExercise)
        }
    }

    fun addMultipleExercises(selectedExercises: List<ExerciseWithImage>) {
        if (!_isEditMode.value) return
        executeWithErrorHandling("Errore nell'aggiunta degli esercizi") {
            var nextOrderIndex = exercisesWithSets.value.size
            selectedExercises.forEach { exercise ->
                val newExercise = WorkoutExercise(
                    workoutCardId = workoutCardId,
                    exerciseId = exercise.id,
                    name = exercise.title.trim(),
                    orderIndex = nextOrderIndex++,
                    updatedAt = System.currentTimeMillis()
                )
                workoutRepository.insertWorkoutExercise(newExercise)
            }
        }
    }

    fun deleteExercise(exercise: WorkoutExercise) {
        if (!_isEditMode.value) return
        executeWithErrorHandling("Errore nell'eliminazione dell'esercizio") {
            workoutRepository.deleteWorkoutExercise(exercise)
            pendingSetChanges.keys.removeAll { setId ->
                exercisesWithSets.value.any { detail ->
                    detail.exercise.id == exercise.id && detail.sets.any { it.id == setId }
                }
            }
        }
    }

    fun addSetToExercise(exerciseId: String) {
        if (!_isEditMode.value) return
        executeWithErrorHandling("Errore nell'aggiunta del set") {
            val currentSets = workoutRepository.getSetsForExercise(exerciseId).first()
            val newSet = ExerciseSet(
                exerciseId = exerciseId,
                setNumber = (currentSets.maxOfOrNull { it.setNumber } ?: 0) + 1,
                weightKg = null,
                reps = null,
                updatedAt = System.currentTimeMillis()
            )
            workoutRepository.insertExerciseSet(newSet)
        }
    }

    fun updateExerciseSet(set: ExerciseSet) {
        if (!_isEditMode.value) return
        
        val existingPendingSet = pendingSetChanges[set.id]
        val mergedSet = existingPendingSet?.let {
            set.copy(
                weightKg = set.weightKg ?: it.weightKg,
                reps = set.reps ?: it.reps,
                updatedAt = System.currentTimeMillis()
            )
        } ?: set.copy(updatedAt = System.currentTimeMillis())
        
        pendingSetChanges[set.id] = mergedSet
    }

    fun deleteExerciseSet(set: ExerciseSet) {
        if (!_isEditMode.value) return
        executeWithErrorHandling("Errore nell'eliminazione del set") {
            workoutRepository.deleteExerciseSetAndReorder(set)
            pendingSetChanges.remove(set.id)
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
        executeWithErrorHandling("Errore nel riordino degli esercizi") {
            val exercises = exercisesWithSets.value.toMutableList()
            if (fromPosition in exercises.indices && toPosition in exercises.indices) {
                val movedExercise = exercises.removeAt(fromPosition)
                exercises.add(toPosition, movedExercise)
                
                exercises.forEachIndexed { index, exerciseDetail ->
                    val updatedExercise = exerciseDetail.exercise.copy(
                        orderIndex = index,
                        updatedAt = System.currentTimeMillis()
                    )
                    workoutRepository.updateWorkoutExercise(updatedExercise)
                }
            }
        }
    }

    suspend fun checkForActiveSession(): WorkoutSession? = workoutRepository.getActiveWorkoutSession()
    
    suspend fun cancelActiveSession(session: WorkoutSession) = workoutRepository.deleteWorkoutSession(session)
    
    private fun executeWithErrorHandling(errorMessage: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: errorMessage)
            }
        }
    }

    sealed interface UiState {
        object Loading : UiState
        object Success : UiState
        data class Error(val message: String) : UiState
    }
}
