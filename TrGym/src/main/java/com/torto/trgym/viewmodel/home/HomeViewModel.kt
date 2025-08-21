package com.torto.trgym.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.WorkoutRepository
import com.torto.trgym.database.entities.WorkoutCard
import com.torto.trgym.database.entities.WorkoutSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    // Carica le schede di allenamento
    var workoutCards = workoutRepository.getAllWorkoutCards()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun refreshHome(){
        workoutCards = workoutRepository.getAllWorkoutCards()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
        viewModelScope.launch {
            workoutCards.collect { cards ->
                _uiState.value = if (cards.isEmpty()) {
                    UiState.Empty
                } else {
                    UiState.Success(cards)
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            workoutCards.collect { cards ->
                _uiState.value = if (cards.isEmpty()) {
                    UiState.Empty
                } else {
                    UiState.Success(cards)
                }
            }
        }
    }

    fun createWorkoutCard(name: String, description: String? = null) {
        viewModelScope.launch {
            try {
                val workoutCard = WorkoutCard(
                    name = name,
                    description = description
                )
                workoutRepository.insertWorkoutCard(workoutCard)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Error in creation of the workout card")
            }
        }
    }

    fun deleteWorkoutCard(workoutCard: WorkoutCard) {
        viewModelScope.launch {
            try {
                //check if the workout card is active
                val activeSession = workoutRepository.getActiveWorkoutSession()
                if (activeSession != null && activeSession.workoutCardId == workoutCard.id) {
                    //Mostriamo un dialogo di messaggio all'utente
                    _uiState.value = UiState.Error("Unable to delete the card: it is currently in use..")
                    return@launch
                }

                workoutRepository.deleteWorkoutCard(workoutCard)

            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Error in deletion of the workout card")
            }
        }
    }

    suspend fun getActiveWorkoutSession(): WorkoutSession? {
        return workoutRepository.getActiveWorkoutSession()
    }

    sealed interface UiState {
        object Loading : UiState
        object Empty : UiState
        data class Success(val workoutCards: List<WorkoutCard>) : UiState
        data class Error(val message: String) : UiState
    }
}