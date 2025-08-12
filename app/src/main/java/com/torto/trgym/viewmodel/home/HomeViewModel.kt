package com.torto.trgym.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.WorkoutRepository
import com.torto.trgym.database.entities.WorkoutCard
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
    val workoutCards = workoutRepository.getAllWorkoutCards()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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
                _uiState.value = UiState.Error(e.message ?: "Errore nella creazione della scheda")
            }
        }
    }

    fun deleteWorkoutCard(workoutCard: WorkoutCard) {
        viewModelScope.launch {
            try {
                workoutRepository.deleteWorkoutCard(workoutCard)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'eliminazione della scheda")
            }
        }
    }

    sealed interface UiState {
        object Loading : UiState
        object Empty : UiState
        data class Success(val workoutCards: List<WorkoutCard>) : UiState
        data class Error(val message: String) : UiState
    }
}