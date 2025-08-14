package com.torto.trgym.viewmodel.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.WorkoutRepository
import com.torto.trgym.database.dto.WorkoutExerciseDetail
import com.torto.trgym.database.entities.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    
    // Timer state
    private val _timerRunning = MutableStateFlow(false)
    val timerRunning: StateFlow<Boolean> = _timerRunning.asStateFlow()
    
    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()
    
    // Track exercise completion state
    private val _completedSets = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val completedSets: StateFlow<Map<String, Boolean>> = _completedSets.asStateFlow()
    
    // List of session exercises and sets
    private val _sessionExercises = MutableStateFlow<List<WorkoutSessionExercise>>(emptyList())
    private val _sessionSets = MutableStateFlow<Map<String, List<WorkoutSessionSet>>>(emptyMap())
    
    // Exercises with sets - similar to WorkoutDetailViewModel
    val exercisesWithSets = workoutRepository.getExercisesForCard(workoutCardId)
        .flatMapLatest { exercises ->
            if (exercises.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    exercises.map { exercise ->
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
        .stateIn(
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
            val activeSession = workoutRepository.getActiveWorkoutSession()
            
            if (activeSession != null) {
                // Resume the active session
                _activeSession.value = activeSession
                _uiState.value = UiState.WorkoutInProgress
                
                // Load session exercises and sets
                loadSessionExercisesAndSets(activeSession.id)
            } else {
                _uiState.value = UiState.ReadyToStart
            }
        }
    }
    
    private fun loadSessionExercisesAndSets(sessionId: String) {
        viewModelScope.launch {
            try {
                val exercises = workoutRepository.getSessionExercises(sessionId)
                _sessionExercises.value = exercises
                
                val setsMap = mutableMapOf<String, List<WorkoutSessionSet>>()
                val completedSetsMap = mutableMapOf<String, Boolean>()
                
                exercises.forEach { exercise ->
                    val sets = workoutRepository.getSessionSets(exercise.id)
                    setsMap[exercise.id] = sets
                    
                    // Track completed sets
                    sets.forEach { set ->
                        completedSetsMap[set.id] = set.isCompleted
                    }
                }
                
                _sessionSets.value = setsMap
                _completedSets.value = completedSetsMap
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nel caricamento della sessione")
            }
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
                _timerRunning.value = true
                
                // Initialize session exercises and sets from the workout card
                initializeSessionExercisesAndSets(session.id)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'avvio dell'allenamento")
            }
        }
    }
    
    private suspend fun initializeSessionExercisesAndSets(sessionId: String) {
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
        _sessionExercises.value = sessionExercises
        
        // Initialize session sets
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
        
        _sessionSets.value = sessionSetsMap
        _completedSets.value = completedSetsMap
    }
    
    fun pauseTimer() {
        _timerRunning.value = false
    }
    
    fun resumeTimer() {
        _timerRunning.value = true
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
    
    sealed interface UiState {
        object Loading : UiState
        object ReadyToStart : UiState
        object WorkoutInProgress : UiState
        object WorkoutCompleted : UiState
        object WorkoutCancelled : UiState
        data class Error(val message: String) : UiState
    }
}
