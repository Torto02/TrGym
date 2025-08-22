package com.torto.trgym.viewmodel.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.repository.ExerciseRepository
import com.torto.trgym.database.dto.ExerciseDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val repo: ExerciseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val exerciseId: Int = checkNotNull(savedStateHandle["exerciseId"])
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    // Stato per il preferito
    private val _isStarred = MutableStateFlow(false)
    val isStarred: StateFlow<Boolean> = _isStarred.asStateFlow()

    init {
        // Carica i dettagli dell'esercizio
        viewModelScope.launch {
            repo.getExerciseDetail(exerciseId)
                .catch { _uiState.value = UiState.Error(it.message ?: "Errore") }
                .collect { detail ->
                    _uiState.value = UiState.Success(detail)
                }
        }

        // Monitora lo stato del preferito
        viewModelScope.launch {
            repo.isExerciseStarred(exerciseId)
                .collect { starred ->
                    _isStarred.value = starred
                }
        }
    }

    fun toggleStarred() {
        viewModelScope.launch {
            repo.toggleStarredExercise(exerciseId, _isStarred.value)
        }
    }

    sealed interface UiState {
        object Loading : UiState
        data class Success(val detail: ExerciseDetail) : UiState
        data class Error(val msg: String) : UiState
    }
}