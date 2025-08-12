
package com.torto.trgym.viewmodel.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.WorkoutRepository
import com.torto.trgym.database.entities.WorkoutCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
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

    fun updateWorkoutCard(workoutCard: WorkoutCard) {
        viewModelScope.launch {
            try {
                workoutRepository.updateWorkoutCard(workoutCard)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nell'aggiornamento della scheda")
            }
        }
    }

    fun duplicateWorkoutCard(workoutCard: WorkoutCard) {
        viewModelScope.launch {
            try {
                val duplicatedCard = workoutCard.copy(
                    id = UUID.randomUUID().toString(),
                    name = "${workoutCard.name} (Copia)",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                workoutRepository.insertWorkoutCard(duplicatedCard)

                // Duplica anche gli esercizi se esistono
                val originalExercises = workoutRepository.getExercisesForCard(workoutCard.id).first()
                originalExercises.forEach { originalExercise ->
                    val duplicatedExercise = originalExercise.copy(
                        id = UUID.randomUUID().toString(),
                        workoutCardId = duplicatedCard.id,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    workoutRepository.insertWorkoutExercise(duplicatedExercise)

                    // Duplica anche i set
                    val originalSets = workoutRepository.getSetsForExercise(originalExercise.id).first()
                    val duplicatedSets = originalSets.map { originalSet ->
                        originalSet.copy(
                            id = UUID.randomUUID().toString(),
                            exerciseId = duplicatedExercise.id,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    }
                    workoutRepository.insertExerciseSets(duplicatedSets)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Errore nella duplicazione della scheda")
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