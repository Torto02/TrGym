package com.torto.trgym.viewmodel.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.ExerciseRepository
import com.torto.trgym.database.dto.ExerciseDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
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
    
    init {
        viewModelScope.launch {
            repo.getExerciseDetail(exerciseId)
                .catch { _uiState.value = UiState.Error(it.message ?: "Errore") }
                .collect { detail ->
                    _uiState.value = UiState.Success(detail)
                }
        }
    }
    sealed interface UiState {
        object Loading : UiState
        data class Success(val detail: ExerciseDetail) : UiState
        data class Error(val msg: String) : UiState
    }
}