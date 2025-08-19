package com.torto.trgym.viewmodel.workout

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.WorkoutRepository
import com.torto.trgym.database.dto.ExerciseWithImage
import com.torto.trgym.database.dto.WorkoutExerciseDetail
import com.torto.trgym.database.entities.ExerciseSet
import com.torto.trgym.database.entities.WorkoutCard
import com.torto.trgym.database.entities.WorkoutExercise
import com.torto.trgym.database.entities.WorkoutSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val workoutCardId: String = checkNotNull(savedStateHandle["workoutCardId"])

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _workoutCard = MutableStateFlow<WorkoutCard?>(null)
    val workoutCard: StateFlow<WorkoutCard?> = _workoutCard.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    // Tracking collections for pending changes
    private val pendingSetChanges = mutableMapOf<String, ExerciseSet>()
    private val pendingAddedExercises = mutableListOf<WorkoutExercise>()
    private val pendingDeletedExercises = mutableListOf<WorkoutExercise>()
    private val pendingAddedSets = mutableListOf<ExerciseSet>()
    private val pendingDeletedSets = mutableListOf<ExerciseSet>()
    private val pendingExerciseUpdates = mutableMapOf<String, WorkoutExercise>()
    private var pendingWorkoutCardUpdate: WorkoutCard? = null

    // Local cache of current exercises and sets (for UI updates)
    private val _localExercisesWithSets = MutableStateFlow<List<WorkoutExerciseDetail>>(emptyList())

    // Fixed version of the exercisesWithSets flow
    val exercisesWithSets = _isEditMode
        .flatMapLatest { isEditMode ->
            if (isEditMode) {
                // In edit mode, use our locally managed list that includes pending changes
                _localExercisesWithSets
            } else {
                // Not in edit mode, use database data directly
                workoutRepository.getExercisesForCard(workoutCardId)
                    .flatMapLatest { exercises ->
                        if (exercises.isEmpty()) {
                            flowOf(emptyList())
                        } else {
                            // Create a flow of WorkoutExerciseDetail for each exercise
                            val exerciseFlows = exercises.map { exercise ->
                                workoutRepository.getSetsForExercise(exercise.id)
                                    .map { sets -> WorkoutExerciseDetail(exercise, sets) }
                            }

                            // Combine all the flows into a single list flow
                            combine(exerciseFlows) { exerciseDetails ->
                                exerciseDetails.toList().sortedBy { it.exercise.orderIndex }
                            }
                        }
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadWorkoutCard()
        // Initialize local cache with DB data
        viewModelScope.launch {
            initializeLocalCache()
        }
    }

    // ==========================================
    // INITIALIZATION AND DATA LOADING
    // ==========================================

    private fun loadWorkoutCard() {
        viewModelScope.launch {
            try {
                val card = workoutRepository.getWorkoutCard(workoutCardId)
                _workoutCard.value = card
                _uiState.value = if (card != null) UiState.Success else UiState.Error("Scheda non trovata")
            } catch (e: Exception) {
                Log.e("WorkoutDetailViewModel", "Error loading workout card", e)
                _uiState.value = UiState.Error(e.message ?: "Errore nel caricamento")
            }
        }
    }

    private suspend fun initializeLocalCache() {
        try {
            val dbExercises = workoutRepository.getExercisesForCard(workoutCardId).first()
            val exerciseDetails = dbExercises.map { exercise ->
                val sets = workoutRepository.getSetsForExercise(exercise.id).first()
                WorkoutExerciseDetail(exercise, sets)
            }.sortedBy { it.exercise.orderIndex }

            _localExercisesWithSets.value = exerciseDetails
            Log.d("WorkoutDetailViewModel", "Local cache initialized with ${exerciseDetails.size} exercises")
        } catch (e: Exception) {
            Log.e("WorkoutDetailViewModel", "Error initializing local cache", e)
        }
    }

    fun refreshWorkoutCard() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                // Solo se NON siamo in modalità modifica, ricarica i dati
                if (!_isEditMode.value) {
                    syncData()
                    // Reinizializza la cache locale con i dati aggiornati dal database
                    initializeLocalCache()
                }
                _uiState.value = UiState.Success
            } catch (e: Exception) {
                Log.e("WorkoutDetailViewModel", "Error refreshing workout card", e)
                _uiState.value = UiState.Error("Errore nell'aggiornamento dei dati")
            }
        }
    }

    private suspend fun syncData() {
        workoutRepository.syncExercisesForCard(workoutCardId)
        workoutRepository.syncAllSetsForCard(workoutCardId)
    }

    // ==========================================
    // EDIT MODE MANAGEMENT
    // ==========================================

    fun toggleEditMode() {
        viewModelScope.launch {
            if (_isEditMode.value) {
                // Exiting edit mode - commit all pending changes
                try {
                    Log.d("WorkoutDetailViewModel", "Saving pending changes...")
                    savePendingChanges()
                    _isEditMode.value = false
                    // Attendi che le modifiche siano salvate prima di reinizializzare
                    delay(100)
                    initializeLocalCache()
                    Log.d("WorkoutDetailViewModel", "Edit mode exited successfully")
                } catch (e: Exception) {
                    Log.e("WorkoutDetailViewModel", "Error exiting edit mode", e)
                    _uiState.value = UiState.Error("Errore nel salvataggio: ${e.message}")
                }
            } else {
                // Entering edit mode - initialize empty pending changes
                clearPendingChanges()
                initializeLocalCache() // Refresh local cache from DB
                _isEditMode.value = true
                Log.d("WorkoutDetailViewModel", "Edit mode entered")
            }
        }
    }

    fun cancelEditMode() {
        viewModelScope.launch {
            if (_isEditMode.value) {
                clearPendingChanges()
                initializeLocalCache() // Reset local cache to DB state
                _isEditMode.value = false
                Log.d("WorkoutDetailViewModel", "Edit mode cancelled")
            }
        }
    }

    private fun clearPendingChanges() {
        pendingSetChanges.clear()
        pendingAddedExercises.clear()
        pendingDeletedExercises.clear()
        pendingAddedSets.clear()
        pendingDeletedSets.clear()
        pendingExerciseUpdates.clear()
        pendingWorkoutCardUpdate = null
        Log.d("WorkoutDetailViewModel", "Pending changes cleared")
    }

    private suspend fun savePendingChanges() {
        try {
            Log.d("WorkoutDetailViewModel", "Saving changes: " +
                    "exercises=${pendingAddedExercises.size}, " +
                    "updates=${pendingExerciseUpdates.size}, " +
                    "sets=${pendingAddedSets.size}, " +
                    "setChanges=${pendingSetChanges.size}")

            // 1. Update workout card if changed
            pendingWorkoutCardUpdate?.let {
                workoutRepository.updateWorkoutCard(it)
                _workoutCard.value = it
                Log.d("WorkoutDetailViewModel", "Workout card updated")
            }

            // 2. Delete exercises (and their sets will cascade delete)
            pendingDeletedExercises.forEach { exercise ->
                workoutRepository.deleteWorkoutExercise(exercise)
                Log.d("WorkoutDetailViewModel", "Deleted exercise: ${exercise.name}")
            }

            // 3. Add new exercises
            pendingAddedExercises.forEach { exercise ->
                workoutRepository.insertWorkoutExercise(exercise)
                Log.d("WorkoutDetailViewModel", "Added exercise: ${exercise.name}")
            }

            // 4. Update existing exercises (per il riordino)
            pendingExerciseUpdates.values.forEach { exercise ->
                workoutRepository.updateWorkoutExercise(exercise)
                Log.d("WorkoutDetailViewModel", "Updated exercise: ${exercise.name} (order: ${exercise.orderIndex})")
            }

            // 5. Delete individual sets
            pendingDeletedSets.forEach { set ->
                workoutRepository.deleteExerciseSetAndReorder(set)
                Log.d("WorkoutDetailViewModel", "Deleted set: ${set.id}")
            }

            // 6. Add new sets
            pendingAddedSets.forEach { set ->
                workoutRepository.insertExerciseSet(set)
                Log.d("WorkoutDetailViewModel", "Added set: ${set.id}")
            }

            // 7. Update existing sets
            pendingSetChanges.values.forEach { set ->
                workoutRepository.updateExerciseSet(set)
                Log.d("WorkoutDetailViewModel", "Updated set: ${set.id}")
            }

            // Clear all pending changes
            clearPendingChanges()
            Log.d("WorkoutDetailViewModel", "All changes saved successfully")

        } catch (e: Exception) {
            Log.e("WorkoutDetailViewModel", "Error saving pending changes", e)
            _uiState.value = UiState.Error(e.message ?: "Errore nel salvataggio delle modifiche")
        }
    }

    // ==========================================
    // EXERCISE MANAGEMENT
    // ==========================================

    fun addMultipleExercises(selectedExercises: List<ExerciseWithImage>) {
        if (!_isEditMode.value) return

        var nextOrderIndex = _localExercisesWithSets.value.size
        val newExercises = selectedExercises.map { exercise ->
            WorkoutExercise(
                workoutCardId = workoutCardId,
                exerciseId = exercise.id,
                name = exercise.title.trim(),
                orderIndex = nextOrderIndex++,
                updatedAt = System.currentTimeMillis()
            )
        }

        // Add to pending changes
        pendingAddedExercises.addAll(newExercises)

        // Update local cache for UI
        val newDetails = newExercises.map { WorkoutExerciseDetail(it, emptyList()) }
        _localExercisesWithSets.value = _localExercisesWithSets.value + newDetails

        Log.d("WorkoutDetailViewModel", "Added ${newExercises.size} exercises")
    }

    fun deleteExercise(exercise: WorkoutExercise) {
        if (!_isEditMode.value) return

        // If this is a newly added exercise (not yet in DB), just remove it from pendingAddedExercises
        val isPendingAdd = pendingAddedExercises.removeIf { it.id == exercise.id }

        if (!isPendingAdd) {
            // Add to pending deletes if it's an existing exercise
            pendingDeletedExercises.add(exercise)
        }

        // Remove any pending changes for this exercise
        pendingExerciseUpdates.remove(exercise.id)

        // Remove any pending set changes for this exercise
        val exerciseDetail = _localExercisesWithSets.value.find { it.exercise.id == exercise.id }
        exerciseDetail?.sets?.forEach { set ->
            pendingSetChanges.remove(set.id)
            pendingAddedSets.removeIf { it.id == set.id }
        }

        // Update local cache for UI
        _localExercisesWithSets.value = _localExercisesWithSets.value.filter { it.exercise.id != exercise.id }

        Log.d("WorkoutDetailViewModel", "Deleted exercise: ${exercise.name}")
    }

    fun moveExercise(fromPosition: Int, toPosition: Int) {
        if (!_isEditMode.value) return
        if (fromPosition == toPosition) return

        val exercises = _localExercisesWithSets.value.toMutableList()
        if (fromPosition in exercises.indices && toPosition in exercises.indices) {
            val movedExercise = exercises.removeAt(fromPosition)
            exercises.add(toPosition, movedExercise)

            // Update order indices and add to pending changes
            exercises.forEachIndexed { index, exerciseDetail ->
                val originalExercise = exerciseDetail.exercise
                if (originalExercise.orderIndex != index) {
                    val updatedExercise = originalExercise.copy(
                        orderIndex = index,
                        updatedAt = System.currentTimeMillis()
                    )

                    // Gestisci correttamente il riordino senza duplicare gli esercizi
                    val isPendingAdd = pendingAddedExercises.indexOfFirst { it.id == originalExercise.id }
                    if (isPendingAdd != -1) {
                        // Se è un esercizio appena aggiunto, aggiorna direttamente nella lista pending
                        pendingAddedExercises[isPendingAdd] = updatedExercise
                    } else {
                        // Se è un esercizio esistente, usa la mappa separata per gli aggiornamenti
                        pendingExerciseUpdates[originalExercise.id] = updatedExercise
                    }

                    // Update local cache
                    exercises[index] = exerciseDetail.copy(exercise = updatedExercise)
                }
            }

            _localExercisesWithSets.value = exercises
            Log.d("WorkoutDetailViewModel", "Moved exercise from $fromPosition to $toPosition")
        }
    }

    // ==========================================
    // SET MANAGEMENT
    // ==========================================

    fun addSetToExercise(exerciseId: String) {
        if (!_isEditMode.value) return

        // Find the exercise in our local cache
        val exerciseIndex = _localExercisesWithSets.value.indexOfFirst { it.exercise.id == exerciseId }
        if (exerciseIndex == -1) return

        val exerciseDetail = _localExercisesWithSets.value[exerciseIndex]
        val sets = exerciseDetail.sets.toMutableList()

        // Create a new set
        val newSet = ExerciseSet(
            exerciseId = exerciseId,
            setNumber = (sets.maxOfOrNull { it.setNumber } ?: 0) + 1,
            weightKg = null,
            reps = null,
            updatedAt = System.currentTimeMillis()
        )

        // Add to pending changes
        pendingAddedSets.add(newSet)

        // Update local cache for UI
        sets.add(newSet)
        val updatedExerciseDetail = exerciseDetail.copy(sets = sets)
        val updatedList = _localExercisesWithSets.value.toMutableList()
        updatedList[exerciseIndex] = updatedExerciseDetail
        _localExercisesWithSets.value = updatedList

        Log.d("WorkoutDetailViewModel", "Added set to exercise: $exerciseId")
    }

    fun updateExerciseSet(set: ExerciseSet) {
        if (!_isEditMode.value) return

        // Verifica se il set è realmente cambiato
        val currentExerciseDetail = _localExercisesWithSets.value.find {
            it.exercise.id == set.exerciseId
        }
        val currentSet = currentExerciseDetail?.sets?.find { it.id == set.id }

        // Se il set non è cambiato, non fare nulla
        if (currentSet?.weightKg == set.weightKg && currentSet?.reps == set.reps) {
            return
        }

        // For newly added sets, update the pending add
        val isPendingAdd = pendingAddedSets.indexOfFirst { it.id == set.id }
        if (isPendingAdd != -1) {
            pendingAddedSets[isPendingAdd] = set
        } else {
            // For existing sets, add/update in pendingSetChanges
            pendingSetChanges[set.id] = set.copy(updatedAt = System.currentTimeMillis())
        }

        // Update local cache for UI only if the values are actually different
        val exerciseIndex = _localExercisesWithSets.value.indexOfFirst { it.exercise.id == set.exerciseId }
        if (exerciseIndex != -1) {
            val exerciseDetail = _localExercisesWithSets.value[exerciseIndex]
            val setIndex = exerciseDetail.sets.indexOfFirst { it.id == set.id }

            if (setIndex != -1) {
                val updatedSets = exerciseDetail.sets.toMutableList()
                updatedSets[setIndex] = set

                val updatedExerciseDetail = exerciseDetail.copy(sets = updatedSets)
                val updatedList = _localExercisesWithSets.value.toMutableList()
                updatedList[exerciseIndex] = updatedExerciseDetail

                // Solo se i dati sono effettivamente cambiati
                if (_localExercisesWithSets.value != updatedList) {
                    _localExercisesWithSets.value = updatedList
                }
            }
        }

        Log.d("WorkoutDetailViewModel", "Updated set: ${set.id}")
    }

    fun deleteExerciseSet(set: ExerciseSet) {
        if (!_isEditMode.value) return

        // If this is a newly added set, just remove it from pendingAddedSets
        val isPendingAdd = pendingAddedSets.removeIf { it.id == set.id }

        if (!isPendingAdd) {
            // Add to pending deletes if it's an existing set
            pendingDeletedSets.add(set)
        }

        // Remove from pending changes if present
        pendingSetChanges.remove(set.id)

        // Update local cache for UI
        val exerciseIndex = _localExercisesWithSets.value.indexOfFirst { it.exercise.id == set.exerciseId }
        if (exerciseIndex != -1) {
            val exerciseDetail = _localExercisesWithSets.value[exerciseIndex]
            val updatedSets = exerciseDetail.sets.filter { it.id != set.id }

            // Reorder set numbers
            val reorderedSets = updatedSets.mapIndexed { index, existingSet ->
                if (existingSet.setNumber != index + 1) {
                    val updatedSet = existingSet.copy(setNumber = index + 1)
                    // Update in pending changes
                    if (!pendingAddedSets.removeIf { it.id == existingSet.id }) {
                        pendingSetChanges[existingSet.id] = updatedSet
                    } else {
                        pendingAddedSets.add(updatedSet)
                    }
                    updatedSet
                } else {
                    existingSet
                }
            }

            val updatedExerciseDetail = exerciseDetail.copy(sets = reorderedSets)
            val updatedList = _localExercisesWithSets.value.toMutableList()
            updatedList[exerciseIndex] = updatedExerciseDetail
            _localExercisesWithSets.value = updatedList
        }

        Log.d("WorkoutDetailViewModel", "Deleted set: ${set.id}")
    }

    // ==========================================
    // WORKOUT CARD MANAGEMENT
    // ==========================================

    fun updateWorkoutCard(name: String, description: String?) {
        if (!_isEditMode.value) return

        val currentCard = _workoutCard.value ?: return
        val updatedCard = currentCard.copy(
            name = name.trim(),
            description = description?.trim(),
            updatedAt = System.currentTimeMillis()
        )

        // Store in pending changes
        pendingWorkoutCardUpdate = updatedCard

        // Update UI immediately
        _workoutCard.value = updatedCard

        Log.d("WorkoutDetailViewModel", "Updated workout card: $name")
    }

    // ==========================================
    // WORKOUT SESSION MANAGEMENT
    // ==========================================

    suspend fun checkForActiveSession(): WorkoutSession? = workoutRepository.getActiveWorkoutSession()

    suspend fun cancelActiveSession(session: WorkoutSession) = workoutRepository.deleteWorkoutSession(session)

    // ==========================================
    // UI STATE
    // ==========================================

    sealed interface UiState {
        object Loading : UiState
        object Success : UiState
        data class Error(val message: String) : UiState
    }
}