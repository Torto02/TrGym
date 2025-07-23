package com.torto.trgym.viewmodel.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.ExerciseRepository
import com.torto.trgym.database.dto.ExerciseWithImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel per la gestione degli esercizi.
 * Utilizza Hilt per l'iniezione delle dipendenze e gestisce lo stato dell'interfaccia utente
 * tramite flussi di dati reattivi.
 *
 * @property repo Repository per l'accesso ai dati degli esercizi.
 */
@HiltViewModel
class ExercisesViewModel @Inject constructor(
    private val repo: ExerciseRepository
) : ViewModel() {

    // Stato interno dell'interfaccia utente, inizialmente impostato su "Loading".
    private val _uiState = MutableStateFlow<ExercisesUiState>(ExercisesUiState.Loading)

    // Stato pubblico dell'interfaccia utente, esposto come flusso immutabile.
    val uiState: StateFlow<ExercisesUiState> = _uiState

    /**
     * Inizializza il ViewModel raccogliendo la lista degli esercizi dal repository
     * e aggiornando lo stato dell'interfaccia utente.
     */
    init {
        viewModelScope.launch {
            repo.allExercises().collect { list ->
                _uiState.value = ExercisesUiState.Data(list)
            }
        }
    }

    /**
     * Interfaccia sigillata per rappresentare i diversi stati dell'interfaccia utente.
     */
    sealed interface ExercisesUiState {
        /**
         * Stato di caricamento, utilizzato quando i dati sono in fase di recupero.
         */
        object Loading : ExercisesUiState

        /**
         * Stato contenente i dati degli esercizi.
         *
         * @property exercises Lista di esercizi con immagini.
         */
        data class Data(val exercises: List<ExerciseWithImage>) : ExercisesUiState
    }
}