//package com.torto.trgym.viewmodel.home
//
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.torto.trgym.data.repository.WorkoutRepository
//import com.torto.trgym.data.repository.WorkoutSessionRepository
//import com.torto.trgym.database.dto.WorkoutSessionExerciseDetail
//import com.torto.trgym.database.entities.*
//import dagger.hilt.android.lifecycle.HiltViewModel
//import kotlinx.coroutines.Job
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.flow.*
//import kotlinx.coroutines.launch
//import javax.inject.Inject
//
//@HiltViewModel
//class ActiveWorkoutViewModel @Inject constructor(
//    private val workoutRepository: WorkoutRepository,
//    private val workoutSessionRepository: WorkoutSessionRepository
//) : ViewModel() {
//
//    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
//    val uiState: StateFlow<UiState> = _uiState
//
//    private val _currentSession = MutableStateFlow<WorkoutSession?>(null)
//    val currentSession: StateFlow<WorkoutSession?> = _currentSession
//
//    private val _workoutTimer = MutableStateFlow(WorkoutSessionRepository.WorkoutTimer())
//    val workoutTimer: StateFlow<WorkoutSessionRepository.WorkoutTimer> = _workoutTimer
//
//    private val _elapsedTime = MutableStateFlow(0L)
//    val elapsedTime: StateFlow<Long> = _elapsedTime
//
//    private val _isWorkoutActive = MutableStateFlow(false)
//    val isWorkoutActive: StateFlow<Boolean> = _isWorkoutActive
//
//    private val _isEditMode = MutableStateFlow(false)
//    val isEditMode: StateFlow<Boolean> = _isEditMode
//
//    // Flow degli esercizi con set per la sessione corrente
//    private val _exercisesWithSets = MutableStateFlow<List<WorkoutSessionExerciseDetail>>(emptyList())
//    val exercisesWithSets: StateFlow<List<WorkoutSessionExerciseDetail>> = _exercisesWithSets
//
//    private var timerJob: Job? = null
//    private var restTimerJob: Job? = null
//
//    private val _currentRestTimer = MutableStateFlow<RestTimer?>(null)
//    val currentRestTimer: StateFlow<RestTimer?> = _currentRestTimer
//
//    init {
//        // Controlla se c'è una sessione attiva all'avvio
//        checkForActiveSession()
//    }
//
//    private fun checkForActiveSession() {
//        viewModelScope.launch {
//            try {
//                val activeSession = workoutSessionRepository.getCurrentActiveSession()
//                if (activeSession != null) {
//                    _currentSession.value = activeSession
//                    _isWorkoutActive.value = true
//
//                    // Riprendi il timer dal tempo salvato
//                    _workoutTimer.value.apply {
//                        // TODO: Calcola il tempo trascorso dalla startTime della sessione
//                        // e imposta il timer di conseguenza
//                    }
//
//                    loadSessionExercises(activeSession.id)
//                    startTimer()
//                    _uiState.value = UiState.WorkoutInProgress
//                } else {
//                    _uiState.value = UiState.Ready
//                }
//            } catch (e: Exception) {
//                _uiState.value = UiState.Error(e.message ?: "Errore nel caricamento")
//            }
//        }
//    }
//
//    fun startWorkout(workoutCard: com.torto.trgym.database.entities.WorkoutCard) {
//        viewModelScope.launch {
//            try {
//                _uiState.value = UiState.Loading
//
//                // Crea una nuova sessione di allenamento
//                val sessionId = workoutSessionRepository.createWorkoutSessionFromCard(
//                    workoutCard,
//                    workoutRepository
//                )
//
//                val session = workoutSessionRepository.getWorkoutSession(sessionId)
//                _currentSession.value = session
//                _isWorkoutActive.value = true
//
//                // Avvia il timer
//                _workoutTimer.value.start()
//                startTimer()
//
//                // Carica gli esercizi della sessione
//                loadSessionExercises(sessionId)
//
//                _uiState.value = UiState.WorkoutInProgress
//            } catch (e: Exception) {
//                _uiState.value = UiState.Error(e.message ?: "Errore nell'avvio dell'allenamento")
//            }
//        }
//    }
//
//    private fun loadSessionExercises(sessionId: String) {
//        viewModelScope.launch {
//            workoutSessionRepository.getExercisesWithSetsForSession(sessionId)
//                .collect { exercises ->
//                    _exercisesWithSets.value = exercises
//                }
//        }
//    }
//
//    fun pauseWorkout() {
//        _workoutTimer.value.pause()
//        timerJob?.cancel()
//    }
//
//    fun resumeWorkout() {
//        _workoutTimer.value.resume()
//        startTimer()
//    }
//
//    fun completeWorkout() {
//        viewModelScope.launch {
//            try {
//                val session = _currentSession.value
//                if (session != null) {
//                    workoutSessionRepository.completeWorkoutSession(session.id)
//                    _isWorkoutActive.value = false
//                    _currentSession.value = null
//                    stopTimer()
//                    _uiState.value = UiState.WorkoutCompleted(session)
//                }
//            } catch (e: Exception) {
//                _uiState.value = UiState.Error(e.message ?: "Errore nel completamento")
//            }
//        }
//    }
//
//    private fun startTimer() {
//        timerJob = viewModelScope.launch {
//            while (_isWorkoutActive.value) {
//                _elapsedTime.value = _workoutTimer.value.getElapsedTime()
//                delay(1000) // Aggiorna ogni secondo
//            }
//        }
//    }
//
//    private fun stopTimer() {
//        timerJob?.cancel()
//        timerJob = null
//    }
//
//    // ========== EXERCISE OPERATIONS ==========
//
//    fun completeSet(setId: String) {
//        viewModelScope.launch {
//            try {
//                workoutSessionRepository.completeWorkoutSessionSet(setId)
//
//                // Avvia timer di riposo se configurato
//                val set = workoutSessionRepository.workoutSessionDao.getWorkoutSessionSet(setId)
//                set?.let {
//                    val exercise = workoutSessionRepository.workoutSessionDao
//                        .getWorkoutSessionExercise(it.exerciseId)
//                    exercise?.let { ex ->
//                        startRestTimer(ex.restTimeBetweenSets)
//                    }
//                }
//            } catch (e: Exception) {
//                _uiState.value = UiState.Error(e.message ?: "Errore nel completamento della serie")
//            }
//        }
//    }
//
//    fun updateSet(set: WorkoutSessionSet) {
//        viewModelScope.launch {
//            try {
//                workoutSessionRepository.updateWorkoutSessionSet(set)
//            } catch (e: Exception) {
//                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiornamento della serie")
//            }
//        }
//    }
//
//    fun addSetToExercise(exerciseId: String) {
//        viewModelScope.launch {
//            try {
//                workoutSessionRepository.addSetToSessionExercise(exerciseId)
//            } catch (e: Exception) {
//                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiunta della serie")
//            }
//        }
//    }
//
//    fun deleteSet(set: WorkoutSessionSet) {
//        viewModelScope.launch {
//            try {
//                workoutSessionRepository.deleteWorkoutSessionSet(set)
//            } catch (e: Exception) {
//                _uiState.value = UiState.Error(e.message ?: "Errore nell'eliminazione della serie")
//            }
//        }
//    }
//
//    // ========== EXERCISE REORDERING ==========
//
//    fun toggleEditMode() {
//        _isEditMode.value = !_isEditMode.value
//    }
//
//    fun reorderExercises(exerciseIds: List<String>) {
//        viewModelScope.launch {
//            try {
//                val sessionId = _currentSession.value?.id
//                if (sessionId != null) {
//                    workoutSessionRepository.reorderExercises(sessionId, exerciseIds)
//                }
//            } catch (e: Exception) {
//                _uiState.value = UiState.Error(e.message ?: "Errore nel riordinamento")
//            }
//        }
//    }
//
//    // ========== REST TIMER ==========
//
//    private fun startRestTimer(durationMs: Long) {
//        _currentRestTimer.value = RestTimer(durationMs)
//
//        restTimerJob?.cancel()
//        restTimerJob = viewModelScope.launch {
//            val restTimer = _currentRestTimer.value ?: return@launch
//
//            while (restTimer.remainingTime > 0) {
//                delay(1000)
//                restTimer.tick()
//                _currentRestTimer.value = restTimer.copy()
//            }
//
//            // Timer finito
//            _currentRestTimer.value = null
//        }
//    }
//
//    fun skipRestTimer() {
//        restTimerJob?.cancel()
//        _currentRestTimer.value = null
//    }
//
//    // ========== UI STATE ==========
//
//    sealed interface UiState {
//        object Loading : UiState
//        object Ready : UiState
//        object WorkoutInProgress : UiState
//        data class WorkoutCompleted(val session: WorkoutSession) : UiState
//        data class Error(val message: String) : UiState
//    }
//
//    data class RestTimer(
//        val totalDuration: Long,
//        var remainingTime: Long = totalDuration
//    ) {
//        fun tick() {
//            if (remainingTime > 0) {
//                remainingTime -= 1000
//            }
//        }
//
//        fun formatTime(): String {
//            val minutes = (remainingTime / 1000) / 60
//            val seconds = (remainingTime / 1000) % 60
//            return String.format("%02d:%02d", minutes, seconds)
//        }
//    }
//
//    override fun onCleared() {
//        super.onCleared()
//        stopTimer()
//        restTimerJob?.cancel()
//    }
//}