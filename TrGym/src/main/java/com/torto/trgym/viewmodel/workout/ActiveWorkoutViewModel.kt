package com.torto.trgym.viewmodel.workout

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.repository.WorkoutRepository
import com.torto.trgym.database.dto.WorkoutExerciseDetail
import com.torto.trgym.database.dto.WorkoutSessionExerciseDetail
import com.torto.trgym.database.entities.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    // Timer state
    private val _timerRunning = MutableStateFlow(false)

    private val _elapsedTime = MutableStateFlow(0L)

    // Track exercise completion state
    private val _completedSets = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val completedSets: StateFlow<Map<String, Boolean>> = _completedSets.asStateFlow()

    // Add a state to track set ID mapping (original to session)
    private val _setIdMapping = MutableStateFlow<Map<String, String>>(emptyMap())
    val setIdMapping: StateFlow<Map<String, String>> = _setIdMapping.asStateFlow()

    // List of session exercises and sets
    private val _sessionExercises = MutableStateFlow<List<WorkoutSessionExercise>>(emptyList())
    val sessionExercises: StateFlow<List<WorkoutSessionExercise>> = _sessionExercises.asStateFlow()

    private val _sessionSets = MutableStateFlow<Map<String, List<WorkoutSessionSet>>>(emptyMap())
    val sessionSets: StateFlow<Map<String, List<WorkoutSessionSet>>> = _sessionSets.asStateFlow()

    // Flag to track whether session data is loaded
    private val _sessionDataLoaded = MutableStateFlow(false)

    // Exercises with sets
    @OptIn(ExperimentalCoroutinesApi::class)
    val exercisesWithSets: StateFlow<List<WorkoutExerciseDetail>> = combine(
        _activeSession,
        _sessionDataLoaded
    ) { activeSession, sessionDataLoaded ->
        Pair(activeSession, sessionDataLoaded)
    }.flatMapLatest { (activeSession, sessionDataLoaded) ->
        if (activeSession != null && sessionDataLoaded) {
            // Active session with data loaded - load workout session exercises/sets
            workoutRepository.getSessionExercisesWithSets(activeSession.id)
                .map { sessionDetails ->
                    sessionDetails.map { detail ->
                        convertSessionToWorkoutDetail(detail)
                    }
                }
        } else {
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
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Helper function to convert session detail to workout detail
    private fun convertSessionToWorkoutDetail(detail: WorkoutSessionExerciseDetail): WorkoutExerciseDetail {
        val workoutExercise = WorkoutExercise(
            id = detail.exercise.id,
            workoutCardId = workoutCardId,
            exerciseId = detail.exercise.originalExerciseId.toIntOrNull(),
            name = detail.exercise.name,
            orderIndex = detail.exercise.orderIndex,
            updatedAt = System.currentTimeMillis(),
            imagePath = detail.exercise.imagePath,
        )

        val exerciseSets = detail.sets.map { sessionSet ->
            ExerciseSet(
                id = sessionSet.id,
                exerciseId = detail.exercise.id,
                setNumber = sessionSet.setNumber,
                weightKg = sessionSet.actualWeightKg ?: sessionSet.targetWeightKg,
                reps = sessionSet.actualReps ?: sessionSet.targetReps,
                updatedAt = System.currentTimeMillis()
            )
        }

        return WorkoutExerciseDetail(workoutExercise, exerciseSets)
    }

    init {
        viewModelScope.launch {
            loadWorkoutCard()
            checkForActiveSession()
        }
    }

    private suspend fun loadWorkoutCard() {
        try {
            Log.d("ActiveWorkoutViewModel", "Loading workout card for ID: $workoutCardId")
            val card = workoutRepository.getWorkoutCard(workoutCardId)
            _workoutCard.value = card

            if (card == null) {
                Log.e("ActiveWorkoutViewModel", "Workout card not found for ID: $workoutCardId")
                _uiState.value = UiState.Error("Scheda non trovata")
            } else {
                Log.d("ActiveWorkoutViewModel", "Workout card loaded: ${card.name}")
            }
        } catch (e: Exception) {
            Log.e("ActiveWorkoutViewModel", "Error loading workout card", e)
            _uiState.value = UiState.Error(e.message ?: "Errore nel caricamento")
        }
    }

    private suspend fun checkForActiveSession() {
        try {
            // Reset sessionDataLoaded flag
            _sessionDataLoaded.value = false

            Log.d("ActiveWorkoutViewModel", "Checking for active session...")
            val activeSession = workoutRepository.getActiveWorkoutSession()

            if (activeSession != null) {
                Log.d("ActiveWorkoutViewModel", "Found active session: ${activeSession.id}")
                // Set session before setting UI state
                _activeSession.value = activeSession

                // Load session exercises and sets before indicating data is ready
                loadSessionExercisesAndSets(activeSession.id)

                // Only after data is loaded, update UI state
                _uiState.value = UiState.WorkoutInProgress
            } else {
                Log.d("ActiveWorkoutViewModel", "No active session found, ready to start")
                _uiState.value = UiState.ReadyToStart
            }
        } catch (e: Exception) {
            Log.e("ActiveWorkoutViewModel", "Error checking for active session", e)
            _uiState.value = UiState.Error(e.message ?: "Errore nel controllo della sessione attiva")
        }
    }

    private suspend fun loadSessionExercisesAndSets(sessionId: String) {
        try {
            val exercises = workoutRepository.getSessionExercises(sessionId)

            val setsMap = mutableMapOf<String, List<WorkoutSessionSet>>()
            val completedSetsMap = mutableMapOf<String, Boolean>()
            val setIdMappingMap = mutableMapOf<String, String>()

            exercises.forEach { exercise ->
                val sets = workoutRepository.getSessionSets(exercise.id)
                setsMap[exercise.id] = sets

                // Track completed sets
                sets.forEach { set ->
                    completedSetsMap[set.id] = set.isCompleted
                    // Add mapping from original ID to session set ID if available
                    if (exercise.originalExerciseId.isNotEmpty()) {
                        setIdMappingMap[set.id] = set.id  // Self-mapping for existing sessions
                    }
                }
            }

            // Update all states at once to reduce UI flicker
            _sessionExercises.value = exercises
            _sessionSets.value = setsMap
            _completedSets.value = completedSetsMap
            _setIdMapping.value = setIdMappingMap

            // Finally indicate that session data is loaded
            _sessionDataLoaded.value = true
        } catch (e: Exception) {
            _uiState.value = UiState.Error(e.message ?: "Error loading session")
        }
    }

    private suspend fun initializeSessionExercisesAndSets(sessionId: String) {
        // Reset sessionDataLoaded flag
        _sessionDataLoaded.value = false

        val exercises = exercisesWithSets.value

        val sessionExercises = exercises.mapIndexed { index, exerciseDetail ->
            WorkoutSessionExercise(
                workoutSessionId = sessionId,
                originalExerciseId = exerciseDetail.exercise.id,
                name = exerciseDetail.exercise.name,
                orderIndex = index,
                startTime = System.currentTimeMillis(),
                imagePath = exerciseDetail.exercise.imagePath,
            )
        }

        workoutRepository.insertSessionExercises(sessionExercises)

        // Initialize session sets
        val sessionSetsMap = mutableMapOf<String, List<WorkoutSessionSet>>()
        val completedSetsMap = mutableMapOf<String, Boolean>()
        val setIdMappingMap = mutableMapOf<String, String>()

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
                // Create a mapping from original set ID to session set ID
                setIdMappingMap[set.id] = sessionSet.id
                sessionSet
            } ?: emptyList()

            workoutRepository.insertSessionSets(sessionSets)
            sessionSetsMap[sessionExercise.id] = sessionSets
        }

        // Update all states at once
        _sessionExercises.value = sessionExercises
        _sessionSets.value = sessionSetsMap
        _completedSets.value = completedSetsMap
        _setIdMapping.value = setIdMappingMap

        // Finally indicate that session data is loaded
        _sessionDataLoaded.value = true
    }

    fun startWorkout() {
        viewModelScope.launch {
            try {
                Log.d("ActiveWorkoutViewModel", "Starting workout...")
                val card = _workoutCard.value
                if (card == null) {
                    Log.e("ActiveWorkoutViewModel", "Cannot start workout: workout card is null")
                    _uiState.value = UiState.Error("Errore: scheda allenamento non trovata")
                    return@launch
                }

                Log.d("ActiveWorkoutViewModel", "Creating session for card: ${card.name}")
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

                Log.d("ActiveWorkoutViewModel", "Session created: ${session.id}, initializing exercises...")
                // Initialize session exercises and sets from the workout card
                initializeSessionExercisesAndSets(session.id)
                Log.d("ActiveWorkoutViewModel", "Workout started successfully")
            } catch (e: Exception) {
                Log.e("ActiveWorkoutViewModel", "Error starting workout", e)
                _uiState.value = UiState.Error(e.message ?: "Errore nell'avvio dell'allenamento")
            }
        }
    }

    fun toggleSetCompletion(setId: String, completed: Boolean) {
        val currentMap = _completedSets.value.toMutableMap()
        currentMap[setId] = completed
        _completedSets.value = currentMap

        viewModelScope.launch {
            try {
                workoutRepository.updateSessionSetCompletion(setId, completed)
            } catch (e: Exception) {

                currentMap[setId] = !completed
                _completedSets.value = currentMap
            }
        }
    }

    fun completeWorkout(averageHeartRate: Int? = null) {
        viewModelScope.launch {
            try {
                // Show loading state immediately
                _uiState.value = UiState.CompletingWorkout

                val session = _activeSession.value ?: return@launch

                Log.d("ActiveWorkoutViewModel", "Completing workout: $session")

                // Filter and keep only completed sets
                val completedSetsIds = _completedSets.value.filter { it.value }.keys
                val exercisesWithCompletedSets = mutableSetOf<String>()

                Log.d("ActiveWorkoutViewModel", "Completed sets: $completedSetsIds")
                Log.d("ActiveWorkoutViewModel", "Completed sets map: ${_completedSets.value}")

                // Delete uncompleted sets and track exercises with completed sets
                _sessionSets.value.forEach { (exerciseId, sets) ->
                    sets.forEach { set ->
                        if (set.id !in completedSetsIds) {
                            Log.d("ActiveWorkoutViewModel", "Deleting uncompleted set: $set")
                            // Delete uncompleted set
                            workoutRepository.deleteSessionSet(set)
                        } else {
                            // Track this exercise as having completed sets
                            exercisesWithCompletedSets.add(exerciseId)
                            Log.d("ActiveWorkoutViewModel", "Keeping completed set: $set")
                        }
                    }
                }

                // Delete exercises without any completed sets
                _sessionExercises.value.forEach { exercise ->
                    if (exercise.id !in exercisesWithCompletedSets) {
                        workoutRepository.deleteSessionExercise(exercise)
                    } else {
                        // Update session exercise with end time and completed status
                        val updatedExercise = exercise.copy(
                            endTime = System.currentTimeMillis(),
                            isCompleted = true
                        )
                        workoutRepository.updateSessionExercise(updatedExercise)
                    }
                }

                // Update session with end time, completed status and heart rate average
                val updatedSession = session.copy(
                    endTime = System.currentTimeMillis(),
                    totalDurationMs = _elapsedTime.value,
                    isCompleted = true,
                    averageHeartRate = averageHeartRate
                )

                Log.d("ActiveWorkoutViewModel", "Completed workout: $updatedSession")
                if (averageHeartRate != null) {
                    Log.d("ActiveWorkoutViewModel", "Saving average heart rate: $averageHeartRate BPM")
                }

                workoutRepository.updateWorkoutSession(updatedSession)

                _activeSession.value = null

                // Only set completed state after all operations are done
                _uiState.value = UiState.WorkoutCompleted

                Log.d("ActiveWorkoutViewModel", "Workout completed successfully")
            } catch (e: Exception) {
                Log.e("ActiveWorkoutViewModel", "Error completing workout", e)
                _uiState.value = UiState.Error(e.message ?: "Errore nel completamento dell'allenamento")
            }
        }
    }

    fun cancelWorkout() {
        viewModelScope.launch {
            try {
                // Show loading state immediately
                _uiState.value = UiState.CancellingWorkout

                val session = _activeSession.value ?: return@launch

                // Delete the session and all related data
                workoutRepository.deleteWorkoutSession(session)

                _activeSession.value = null

                // Only set cancelled state after all operations are done
                _uiState.value = UiState.WorkoutCancelled

                Log.d("ActiveWorkoutViewModel", "Workout cancelled successfully")

            } catch (e: Exception) {
                Log.e("ActiveWorkoutViewModel", "Error cancelling workout", e)
                _uiState.value = UiState.Error(e.message ?: "Errore nell'annullamento dell'allenamento")
            }
        }
    }

    sealed interface UiState {
        object Loading : UiState
        object ReadyToStart : UiState
        object WorkoutInProgress : UiState
        object CompletingWorkout : UiState
        object CancellingWorkout : UiState
        object WorkoutCompleted : UiState
        object WorkoutCancelled : UiState
        data class Error(val message: String) : UiState
    }
}