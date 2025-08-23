package com.torto.trgym.viewmodel.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.repository.EquipmentRepository
import com.torto.trgym.repository.ExerciseRepository
import com.torto.trgym.repository.MuscleRepository
import com.torto.trgym.database.dto.ExerciseWithImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ExercisesViewModel @Inject constructor(
    private val exerciseRepo: ExerciseRepository,
    private val muscleRepo: MuscleRepository,
    private val equipmentRepo: EquipmentRepository
) : ViewModel() {

    // Filtri
    private val _muscleFilterId = MutableStateFlow<Int?>(null)
    private val _equipmentFilterId = MutableStateFlow<Int?>(null)
    private val _starredFilter = MutableStateFlow(false)
    val starredFilter: StateFlow<Boolean> = _starredFilter

    private val allExercises = exerciseRepo.allExercises()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val starredExercises = exerciseRepo.getAllStarredExercises()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val muscleGroups = muscleRepo.allMuscleGroups()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val equipments = equipmentRepo.allEquipment()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val byMuscle = _muscleFilterId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else exerciseRepo.filteredExercisesByMuscleGroup(id)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val byEquipment = _equipmentFilterId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList())
        else exerciseRepo.filteredExercisesByEquipment(id)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Selettori etichette
    val selectedMuscleName: StateFlow<String> =
        combine(_muscleFilterId, muscleGroups) { id, list ->
            if (id == null) "All muscles"
            else list.find { it.muscleGroupId == id }?.name ?: "All muscles"
        }.stateIn(viewModelScope, SharingStarted.Eagerly, "All muscles")

    val selectedEquipmentName: StateFlow<String> =
        combine(_equipmentFilterId, equipments) { id, list ->
            if (id == null) "All equipment"
            else list.find { it.equipment_id == id }?.name ?: "All equipment"
        }.stateIn(viewModelScope, SharingStarted.Eagerly, "All equipment")

    // Lista finale filtrata (intersezione)
    private val filteredExercises = combine(
        allExercises, starredExercises, byMuscle, byEquipment, _starredFilter
    ) { all, starred, muscleList, equipList, onlyStarred ->
        var base = if (onlyStarred) starred else all
        if (muscleList.isNotEmpty()) {
            val ids = muscleList.map { it.id }.toSet()
            base = base.filter { it.id in ids }
        }
        if (equipList.isNotEmpty()) {
            val ids = equipList.map { it.id }.toSet()
            base = base.filter { it.id in ids }
        }
        base
    }

    val uiState: StateFlow<ExercisesUiState> = filteredExercises
        .map< List<ExerciseWithImage>, ExercisesUiState > { ExercisesUiState.Data(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ExercisesUiState.Loading)

    fun filterByMuscleGroup(muscleGroupId: Int) { _muscleFilterId.value = muscleGroupId }
    fun filterByEquipment(equipmentId: Int)     { _equipmentFilterId.value = equipmentId }
    fun clearMuscleGroupFilter()                { _muscleFilterId.value = null }
    fun clearEquipmentFilter()                  { _equipmentFilterId.value = null }
    fun starredExercise()                       { _starredFilter.value = !_starredFilter.value }
    fun clearAllFilters() {
        _equipmentFilterId.value = null
        _muscleFilterId.value = null
        _starredFilter.value = false
    }
    sealed interface ExercisesUiState {
        object Loading : ExercisesUiState
        data class Data(val exercises: List<ExerciseWithImage>) : ExercisesUiState
    }
}
