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

/**
 * ViewModel per la gestione dei dettagli di un esercizio.
 * Utilizza Hilt per l'iniezione delle dipendenze e gestisce lo stato dell'interfaccia utente
 * tramite flussi di dati reattivi.
 *
 * @property repo Repository per l'accesso ai dati degli esercizi.
 * @property savedStateHandle Gestisce lo stato salvato del ViewModel, fornito da Hilt.
 */
@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val repo: ExerciseRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // ID dell'esercizio, recuperato dallo stato salvato.
    private val exerciseId: Int = checkNotNull(savedStateHandle["exerciseId"])

    // Stato interno dell'interfaccia utente, inizialmente impostato su "Loading".
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)

    // Stato pubblico dell'interfaccia utente, esposto come flusso immutabile.
    val uiState: StateFlow<UiState> = _uiState

    /**
     * Inizializza il ViewModel raccogliendo i dettagli dell'esercizio dal repository
     * e aggiornando lo stato dell'interfaccia utente.
     */
    init {
        viewModelScope.launch {
            repo.getExerciseDetail(exerciseId)
                // Gestisce gli errori durante la raccolta dei dati.
                .catch { _uiState.value = UiState.Error(it.message ?: "Errore") }
                // Aggiorna lo stato con i dettagli dell'esercizio.
                .collect { detail ->
                    _uiState.value = UiState.Success(detail)
                }
        }
    }

    /**
     * Interfaccia sigillata per rappresentare i diversi stati dell'interfaccia utente.
     */
    sealed interface UiState {
        /**
         * Stato di caricamento, utilizzato quando i dati sono in fase di recupero.
         */
        object Loading : UiState

        /**
         * Stato contenente i dettagli dell'esercizio.
         *
         * @property detail Dettagli dell'esercizio.
         */
        data class Success(val detail: ExerciseDetail) : UiState

        /**
         * Stato di errore, utilizzato quando si verifica un problema durante il recupero dei dati.
         *
         * @property msg Messaggio di errore.
         */
        data class Error(val msg: String) : UiState
    }
}