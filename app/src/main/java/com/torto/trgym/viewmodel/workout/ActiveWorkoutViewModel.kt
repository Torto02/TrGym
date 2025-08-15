package com.torto.trgym.viewmodel.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.WorkoutRepository
import com.torto.trgym.database.dto.WorkoutExerciseDetail
import com.torto.trgym.database.dto.WorkoutSessionExerciseDetail
import com.torto.trgym.database.entities.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ActiveWorkoutViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val workoutCardId: String = checkNotNull(savedStateHandle["workoutCardId"])

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _workoutCard = MutableStateFlow<WorkoutCard?>(null)
    val workoutCard: StateFlow<WorkoutCard?> = _workoutCard.asStateFlow()

    // Active workout session
    private val _activeSession = MutableStateFlow<WorkoutSession?>(null)
    val activeSession: StateFlow<WorkoutSession?> = _activeSession.asStateFlow()

    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()

    // Edit mode state
    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    // Temporary changes map for batch saving during edit mode
    private val pendingSetChanges = mutableMapOf<String, WorkoutSessionSet>()

    // Track exercise completion state
    private val _completedSets = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val completedSets: StateFlow<Map<String, Boolean>> = _completedSets.asStateFlow()

    // List of session exercises and sets
    private val _sessionExercises = MutableStateFlow<List<WorkoutSessionExercise>>(emptyList())
    val sessionExercises: StateFlow<List<WorkoutSessionExercise>> = _sessionExercises.asStateFlow()
    
    private val _sessionSets = MutableStateFlow<Map<String, List<WorkoutSessionSet>>>(emptyMap())
    val sessionSets: StateFlow<Map<String, List<WorkoutSessionSet>>> = _sessionSets.asStateFlow()

    private val _sessionDataLoaded = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val exercisesWithSets = combine(
        _activeSession, 
        _sessionDataLoaded,
        workoutRepository.getExercisesForCard(workoutCardId)
    ) { activeSession, sessionDataLoaded, originalExercises ->
        if (activeSession != null && sessionDataLoaded) {
            workoutRepository.getSessionExercisesWithSets(activeSession.id)
        } else if (activeSession != null) {
            emptyFlow<List<WorkoutSessionExerciseDetail>>()
        } else {
            if (originalExercises.isEmpty()) {
                flowOf(emptyList<WorkoutExerciseDetail>())
            } else {
                combine(
                    originalExercises.map { exercise ->
                        workoutRepository.getSetsForExercise(exercise.id)
                            .map { sets ->
                                WorkoutExerciseDetail(exercise, sets)
                            }
                    }
                ) { exerciseDetails ->
                    exerciseDetails.toList().sortedBy { it.exercise.orderIndex }
                }
            }
        }
    }.flatMapLatest { flow ->
        when (flow) {
            else -> {
                if (_activeSession.value != null && _sessionDataLoaded.value) {
                    @Suppress("UNCHECKED_CAST")
                    (flow as Flow<List<WorkoutSessionExerciseDetail>>).map { sessionExercises ->
                        sessionExercises.map { sessionExerciseDetail ->
                            val workoutExercise = WorkoutExercise(
                                id = sessionExerciseDetail.exercise.id,
                                workoutCardId = workoutCardId,
                                exerciseId = sessionExerciseDetail.exercise.originalExerciseId.toIntOrNull()
                                    ?: 0,
                                name = sessionExerciseDetail.exercise.name,
                                orderIndex = sessionExerciseDetail.exercise.orderIndex,
                                updatedAt = System.currentTimeMillis()
                            )

                            // Convert WorkoutSessionSet to ExerciseSet format
                            val exerciseSets = sessionExerciseDetail.sets.map { sessionSet ->
                                ExerciseSet(
                                    id = sessionSet.id,
                                    exerciseId = sessionExerciseDetail.exercise.id,
                                    setNumber = sessionSet.setNumber,
                                    weightKg = sessionSet.actualWeightKg
                                        ?: sessionSet.targetWeightKg,
                                    reps = sessionSet.actualReps ?: sessionSet.targetReps,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis(),
                                    pendingSync = false,
                                    syncedWithServer = false
                                )
                            }

                            WorkoutExerciseDetail(workoutExercise, exerciseSets)
                        }
                    }
                } else {
                    @Suppress("UNCHECKED_CAST")
                    flow as Flow<List<WorkoutExerciseDetail>>
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        loadWorkoutCard()
        checkForActiveSession()
    }

    private fun loadWorkoutCard() {
        viewModelScope.launch {
            try {
                val card = workoutRepository.getWorkoutCard(workoutCardId)
                _workoutCard.value = card

                if (card == null) {
                    _uiState.value = UiState.Error("Scheda non trovata")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nel caricamento")
            }
        }
    }

    private fun checkForActiveSession() {
        viewModelScope.launch {
            try {
                _sessionDataLoaded.value = false
                val activeSession = workoutRepository.getActiveWorkoutSession()

                if (activeSession != null) {
                    _activeSession.value = activeSession

                    loadSessionExercisesAndSets(activeSession.id)

                    _uiState.value = UiState.WorkoutInProgress
                } else {
                    _uiState.value = UiState.ReadyToStart
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nel controllo della sessione attiva")
            }
        }
    }

    private suspend fun loadSessionExercisesAndSets(sessionId: String) {
        try {
            val exercises = workoutRepository.getSessionExercises(sessionId)
            
            val setsMap = mutableMapOf<String, List<WorkoutSessionSet>>()
            val completedSetsMap = mutableMapOf<String, Boolean>()

            exercises.forEach { exercise ->
                val sets = workoutRepository.getSessionSets(exercise.id)
                setsMap[exercise.id] = sets

                sets.forEach { set ->
                    completedSetsMap[set.id] = set.isCompleted
                }
            }

            _sessionExercises.value = exercises
            _sessionSets.value = setsMap
            _completedSets.value = completedSetsMap
            _sessionDataLoaded.value = true
        } catch (e: Exception) {
            _uiState.value = UiState.Error(e.message ?: "Errore nel caricamento della sessione")
        }
    }

    fun startWorkout() {
        viewModelScope.launch {
            try {
                val card = _workoutCard.value ?: return@launch

                // Create a new workout session
                val session = WorkoutSession(
                    workoutCardId = card.id,
                    name = card.name,
                    startTime = System.currentTimeMillis()
                )

                workoutRepository.insertWorkoutSession(session)
                _activeSession.value = session
                _uiState.value = UiState.WorkoutInProgress

                // Initialize session exercises and sets from the workout card
                initializeSessionExercisesAndSets(session.id)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'avvio dell'allenamento")
            }
        }
    }

    private suspend fun initializeSessionExercisesAndSets(sessionId: String) {
        _sessionDataLoaded.value = false
        
        val exercises = exercisesWithSets.value

        val sessionExercises = exercises.mapIndexed { index, exerciseDetail ->
            WorkoutSessionExercise(
                workoutSessionId = sessionId,
                originalExerciseId = exerciseDetail.exercise.id,
                name = exerciseDetail.exercise.name,
                orderIndex = index,
                startTime = System.currentTimeMillis()
            )
        }

        workoutRepository.insertSessionExercises(sessionExercises)

        val sessionSetsMap = mutableMapOf<String, List<WorkoutSessionSet>>()
        val completedSetsMap = mutableMapOf<String, Boolean>()

        sessionExercises.forEach { sessionExercise ->
            val originalExercise = exercises.find { it.exercise.id == sessionExercise.originalExerciseId }

            val sessionSets = originalExercise?.sets?.mapIndexed { _, set ->
                val sessionSet = WorkoutSessionSet(
                    exerciseId = sessionExercise.id,
                    setNumber = set.setNumber,
                    targetWeightKg = set.weightKg,
                    targetReps = set.reps,
                    isCompleted = false
                )
                completedSetsMap[sessionSet.id] = false
                sessionSet
            } ?: emptyList()

            workoutRepository.insertSessionSets(sessionSets)
            sessionSetsMap[sessionExercise.id] = sessionSets
        }
        _sessionExercises.value = sessionExercises
        _sessionSets.value = sessionSetsMap
        _completedSets.value = completedSetsMap
        _sessionDataLoaded.value = true
    }

    fun updateElapsedTime(time: Long) {
        _elapsedTime.value = time
    }

    fun toggleSetCompletion(setId: String, completed: Boolean) {
        val currentMap = _completedSets.value.toMutableMap()
        currentMap[setId] = completed
        _completedSets.value = currentMap

        viewModelScope.launch {
            try {
                workoutRepository.updateSessionSetCompletion(setId, completed)
            } catch (e: Exception) {
                // Revert UI state if update fails
                currentMap[setId] = !completed
                _completedSets.value = currentMap
            }
        }
    }

    fun updateSessionSet(set: WorkoutSessionSet) {
        viewModelScope.launch {
            try {
                workoutRepository.updateSessionSet(set)

                // Update the local session sets map
                val exerciseId = set.exerciseId
                val currentSets = _sessionSets.value[exerciseId]?.toMutableList() ?: mutableListOf()
                val setIndex = currentSets.indexOfFirst { it.id == set.id }

                if (setIndex >= 0) {
                    currentSets[setIndex] = set
                    val updatedMap = _sessionSets.value.toMutableMap()
                    updatedMap[exerciseId] = currentSets
                    _sessionSets.value = updatedMap
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiornamento del set")
            }
        }
    }

    fun completeWorkout() {
        viewModelScope.launch {
            try {
                val session = _activeSession.value ?: return@launch

                // Update session with end time and completed status
                val updatedSession = session.copy(
                    endTime = System.currentTimeMillis(),
                    totalDurationMs = _elapsedTime.value,
                    isCompleted = true
                )

                workoutRepository.updateWorkoutSession(updatedSession)

                // Update session exercises with end time
                _sessionExercises.value.forEach { exercise ->
                    val updatedExercise = exercise.copy(
                        endTime = System.currentTimeMillis(),
                        isCompleted = true
                    )
                    workoutRepository.updateSessionExercise(updatedExercise)
                }

                _activeSession.value = null
                _uiState.value = UiState.WorkoutCompleted
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nel completamento dell'allenamento")
            }
        }
    }

    fun cancelWorkout() {
        viewModelScope.launch {
            try {
                val session = _activeSession.value ?: return@launch

                // Delete the session and all related data
                workoutRepository.deleteWorkoutSession(session)

                _activeSession.value = null
                _uiState.value = UiState.WorkoutCancelled

            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'annullamento dell'allenamento")
            }
        }
    }

    // Toggle edit mode function
    fun toggleEditMode() {
        viewModelScope.launch {
            if (_isEditMode.value) {
                // Exiting edit mode - save all pending changes
                savePendingChanges()
                _isEditMode.value = false
            } else {
                // Entering edit mode - clear any pending changes
                pendingSetChanges.clear()
                _isEditMode.value = true
            }
        }
    }

    private suspend fun savePendingChanges() {
        try {
            pendingSetChanges.values.forEach { set ->
                workoutRepository.updateSessionSet(set)
            }
            pendingSetChanges.clear()
        } catch (e: Exception) {
            _uiState.value = UiState.Error(e.message ?: "Errore nel salvataggio delle modifiche")
        }
    }

    // Aggiungi esercizi selezionati alla sessione di allenamento attiva
    fun addExercisesToSession(selectedExercises: List<com.torto.trgym.database.dto.ExerciseWithImage>) {
        viewModelScope.launch {
            try {
                val activeSession = _activeSession.value ?: return@launch
                val currentExercises = _sessionExercises.value
                var nextOrderIndex = currentExercises.size

                // Crea una lista di nuovi esercizi per la sessione
                val newSessionExercises = selectedExercises.map { exercise ->
                    WorkoutSessionExercise(
                        workoutSessionId = activeSession.id,
                        originalExerciseId = exercise.id.toString(), // Convertito a String
                        name = exercise.title.trim(),
                        orderIndex = nextOrderIndex++,
                        startTime = System.currentTimeMillis()
                    )
                }

                // Inserisci gli esercizi nel database
                workoutRepository.insertSessionExercises(newSessionExercises)

                // Aggiorna la lista di esercizi della sessione
                val updatedExercises = currentExercises + newSessionExercises
                _sessionExercises.value = updatedExercises

                // Crea set predefiniti per ogni nuovo esercizio
                val sessionSetsMap = _sessionSets.value.toMutableMap()
                val completedSetsMap = _completedSets.value.toMutableMap()

                newSessionExercises.forEach { sessionExercise ->
                    // Crea un set predefinito per l'esercizio (puoi personalizzare questa logica)
                    val sessionSet = WorkoutSessionSet(
                        exerciseId = sessionExercise.id,
                        setNumber = 1,
                        targetReps = 10,
                        isCompleted = false
                    )

                    completedSetsMap[sessionSet.id] = false
                    val setsList = listOf(sessionSet)
                    sessionSetsMap[sessionExercise.id] = setsList
                    workoutRepository.insertSessionSets(setsList)
                }

                _sessionSets.value = sessionSetsMap
                _completedSets.value = completedSetsMap

            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiunta degli esercizi")
            }
        }
    }

    // Delete exercise from session
    fun deleteSessionExercise(sessionExercise: WorkoutSessionExercise) {
        if (!_isEditMode.value) return

        viewModelScope.launch {
            try {
                workoutRepository.deleteSessionExercise(sessionExercise)
                
                // Remove any pending changes for sets of this exercise
                val setsToRemove = pendingSetChanges.keys.filter { setId ->
                    _sessionSets.value[sessionExercise.id]?.any { it.id == setId } == true
                }
                setsToRemove.forEach { pendingSetChanges.remove(it) }
                
                // Update local state
                val updatedExercises = _sessionExercises.value.filter { it.id != sessionExercise.id }
                _sessionExercises.value = updatedExercises
                
                val updatedSetsMap = _sessionSets.value.toMutableMap()
                updatedSetsMap.remove(sessionExercise.id)
                _sessionSets.value = updatedSetsMap
                
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'eliminazione dell'esercizio")
            }
        }
    }

    // Add set to session exercise
    fun addSetToSessionExercise(sessionExerciseId: String) {
        if (!_isEditMode.value) return

        viewModelScope.launch {
            try {
                val currentSets = _sessionSets.value[sessionExerciseId] ?: emptyList()
                val nextSetNumber = (currentSets.maxOfOrNull { it.setNumber } ?: 0) + 1

                val newSet = WorkoutSessionSet(
                    exerciseId = sessionExerciseId,
                    setNumber = nextSetNumber,
                    isCompleted = false
                )

                workoutRepository.insertSessionSet(newSet)
                
                // Update local state
                val updatedSets = currentSets + newSet
                val updatedSetsMap = _sessionSets.value.toMutableMap()
                updatedSetsMap[sessionExerciseId] = updatedSets
                _sessionSets.value = updatedSetsMap
                
                // Update completed sets map
                val updatedCompletedSets = _completedSets.value.toMutableMap()
                updatedCompletedSets[newSet.id] = false
                _completedSets.value = updatedCompletedSets
                
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiunta del set")
            }
        }
    }

    // Delete set from session exercise
    fun deleteSessionSet(sessionSet: WorkoutSessionSet) {
        if (!_isEditMode.value) return

        viewModelScope.launch {
            try {
                workoutRepository.deleteSessionSet(sessionSet)
                
                // Remove from pending changes
                pendingSetChanges.remove(sessionSet.id)
                
                // Update local state
                val exerciseId = sessionSet.exerciseId
                val currentSets = _sessionSets.value[exerciseId] ?: emptyList()
                val updatedSets = currentSets.filter { it.id != sessionSet.id }
                val updatedSetsMap = _sessionSets.value.toMutableMap()
                updatedSetsMap[exerciseId] = updatedSets
                _sessionSets.value = updatedSetsMap
                
                // Update completed sets map
                val updatedCompletedSets = _completedSets.value.toMutableMap()
                updatedCompletedSets.remove(sessionSet.id)
                _completedSets.value = updatedCompletedSets
                
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'eliminazione del set")
            }
        }
    }

    // Update session set values (temporarily until edit mode ends)
    fun updateSessionSetValues(sessionSet: WorkoutSessionSet) {
        if (!_isEditMode.value) return

        // Store changes temporarily - merge with existing pending changes
        val existingPendingSet = pendingSetChanges[sessionSet.id]
        val mergedSet = if (existingPendingSet != null) {
            // Merge changes: keep non-null values from both sets
            sessionSet.copy(
                actualWeightKg = sessionSet.actualWeightKg ?: existingPendingSet.actualWeightKg,
                actualReps = sessionSet.actualReps ?: existingPendingSet.actualReps
            )
        } else {
            sessionSet
        }

        pendingSetChanges[sessionSet.id] = mergedSet
    }

    // Update session set values from weight and reps (for adapter compatibility)
    fun updateSessionSetFromValues(setId: String, weight: Double?, reps: Int?) {
        if (!_isEditMode.value) return

        // Find the original session set
        val originalSet = _sessionSets.value.values.flatten().find { it.id == setId }
        if (originalSet != null) {
            val updatedSet = originalSet.copy(
                actualWeightKg = weight,
                actualReps = reps
            )
            updateSessionSetValues(updatedSet)
        }
    }

    sealed interface UiState {
        object Loading : UiState
        object ReadyToStart : UiState
        object WorkoutInProgress : UiState
        object WorkoutCompleted : UiState
        object WorkoutCancelled : UiState
        data class Error(val message: String) : UiState
    }
}
