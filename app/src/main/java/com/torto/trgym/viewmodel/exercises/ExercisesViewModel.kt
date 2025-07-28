package com.torto.trgym.viewmodel.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.EquipmentRepository
import com.torto.trgym.data.repository.ExerciseRepository
import com.torto.trgym.data.repository.MuscleRepository
import com.torto.trgym.database.dto.ExerciseWithImage
import com.torto.trgym.database.entities.MuscleGroupI18n
import com.torto.trgym.database.entities.EquipmentI18n
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
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
    private val repo: ExerciseRepository,
    private val muscleRepo: MuscleRepository,
    private val equipmentRepo: EquipmentRepository
) : ViewModel() {

    private val _muscleGroups = MutableStateFlow<List<MuscleGroupI18n>>(emptyList())
    val muscleGroups: StateFlow<List<MuscleGroupI18n>> = _muscleGroups
    private val _equipments = MutableStateFlow<List<EquipmentI18n>>(emptyList())
    val equipments: StateFlow<List<EquipmentI18n>> = _equipments

    private val _muscleFilterId = MutableStateFlow<Int?>(null)
    private val _equipmentFilterId = MutableStateFlow<Int?>(null)

    // Expose selected names for UI
    val selectedMuscleName = combine(_muscleFilterId, _muscleGroups) { id, list ->
        if (id == null) "All muscles"
        else list.find { it.muscleGroupId == id }?.name ?: "All muscles"
    }

    val selectedEquipmentName = combine(_equipmentFilterId, _equipments) { id, list ->
        if (id == null) "All equipment"
        else list.find { it.equipment_id == id }?.name ?: "All equipment"
    }
    private val _uiState = MutableStateFlow<ExercisesUiState>(ExercisesUiState.Loading)
    val uiState: StateFlow<ExercisesUiState> = _uiState

    init {
        viewModelScope.launch {
            repo.allExercises().collect { list ->
                _uiState.value = ExercisesUiState.Data(list)
            }
        }
        viewModelScope.launch {
            muscleRepo.allMuscleGroups()
                .flowOn(Dispatchers.IO)
                .collect { _muscleGroups.value = it }
        }
        viewModelScope.launch {
            equipmentRepo.allEquipment()
                .flowOn(Dispatchers.IO)
                .collect { _equipments.value = it }
        }
    }

    private fun loadExercisesBasedOnFilters() {
        viewModelScope.launch {
            when {
                _muscleFilterId.value != null && _equipmentFilterId.value != null -> {
                    combine(
                        repo.filteredExercisesByMuscleGroup(_muscleFilterId.value!!),
                        repo.filteredExercisesByEquipment(_equipmentFilterId.value!!)
                    ) { muscleList, equipmentList ->
                        val equipmentIds = equipmentList.map { it.id }.toSet()
                        muscleList.filter { it.id in equipmentIds }
                    }.collect { list ->
                        _uiState.value = ExercisesUiState.Data(list)
                    }
                }
                _muscleFilterId.value != null -> {
                    repo.filteredExercisesByMuscleGroup(_muscleFilterId.value!!).collect { list ->
                        _uiState.value = ExercisesUiState.Data(list)
                    }
                }
                _equipmentFilterId.value != null -> {
                    repo.filteredExercisesByEquipment(_equipmentFilterId.value!!).collect { list ->
                        _uiState.value = ExercisesUiState.Data(list)
                    }
                }
                else -> {
                    repo.allExercises().collect { list ->
                        _uiState.value = ExercisesUiState.Data(list)
                    }
                }
            }
        }
    }
    fun filterByMuscleGroup(muscleGroupId: Int) {
        _muscleFilterId.value = muscleGroupId
        loadExercisesBasedOnFilters()
    }

    fun filterByEquipment(equipmentId: Int) {
        _equipmentFilterId.value = equipmentId
        loadExercisesBasedOnFilters()
    }

    fun clearMuscleGroupFilter() {
        _muscleFilterId.value = null
        loadExercisesBasedOnFilters()
    }

    fun clearEquipmentFilter() {
        _equipmentFilterId.value = null
        loadExercisesBasedOnFilters()
    }

    sealed interface ExercisesUiState {
        object Loading : ExercisesUiState
        data class Data(val exercises: List<ExerciseWithImage>) : ExercisesUiState
    }
}