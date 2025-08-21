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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel per la gestione degli esercizi.
 * Utilizza Hilt per l'iniezione delle dipendenze e gestisce lo stato dell'interfaccia utente
 * tramite flussi di dati reattivi.
 *
 * @property repo Repository per l'accesso ai dati degli esercizi.
 */
//@HiltViewModel
//class ExercisesViewModel @Inject constructor(
//    private val exerciseRepo: ExerciseRepository,
//    private val muscleRepo: MuscleRepository,
//    private val equipmentRepo: EquipmentRepository
//) : ViewModel() {
//
//    private val _muscleGroups = MutableStateFlow<List<MuscleGroupI18n>>(emptyList())
//    val muscleGroups: StateFlow<List<MuscleGroupI18n>> = _muscleGroups
//    private val _equipments = MutableStateFlow<List<EquipmentI18n>>(emptyList())
//    val equipments: StateFlow<List<EquipmentI18n>> = _equipments
//    private val _muscleFilterId = MutableStateFlow<Int?>(null)
//    private val _equipmentFilterId = MutableStateFlow<Int?>(null)
//    private val _starredFilter = MutableStateFlow(false)
//    val starredFilter: StateFlow<Boolean> = _starredFilter
//
//    private val _starredIds = MutableStateFlow<Set<Int>>(emptySet())
//    val starredIds: StateFlow<Set<Int>> = _starredIds
//
//    // Expose selected names for UI
//    val selectedMuscleName = combine(_muscleFilterId, _muscleGroups) { id, list ->
//        if (id == null) "All muscles"
//        else list.find { it.muscleGroupId == id }?.name ?: "All muscles"
//    }
//
//    val selectedEquipmentName = combine(_equipmentFilterId, _equipments) { id, list ->
//        if (id == null) "All equipment"
//        else list.find { it.equipment_id == id }?.name ?: "All equipment"
//    }
//    private val _uiState = MutableStateFlow<ExercisesUiState>(ExercisesUiState.Loading)
//    val uiState: StateFlow<ExercisesUiState> = _uiState
//
//    init {
//        // Sincronizza gli esercizi preferiti all'avvio
//        viewModelScope.launch {
//            exerciseRepo.syncStarredExercises()
//        }
//
//        viewModelScope.launch(Dispatchers.IO) {
//            exerciseRepo.getAllStarredExercises().collect { list ->
//                _uiState.value = ExercisesUiState.Data(list)
//            }
//        }
//        viewModelScope.launch {
//            exerciseRepo.allExercises().collect { list ->
//                _uiState.value = ExercisesUiState.Data(list)
//            }
//        }
//        viewModelScope.launch {
//            muscleRepo.allMuscleGroups()
//                .flowOn(Dispatchers.IO)
//                .collect { _muscleGroups.value = it }
//        }
//        viewModelScope.launch {
//            equipmentRepo.allEquipment()
//                .flowOn(Dispatchers.IO)
//                .collect { _equipments.value = it }
//        }
//    }
//
//    private fun loadExercisesBasedOnFilters() {
//        viewModelScope.launch {
//            when {
//                // starred + muscle + equipment
//                _starredFilter.value && _muscleFilterId.value != null && _equipmentFilterId.value != null -> {
//                    combine(
//                        exerciseRepo.getAllStarredExercises(),
//                        exerciseRepo.filteredExercisesByMuscleGroup(_muscleFilterId.value!!),
//                        exerciseRepo.filteredExercisesByEquipment(_equipmentFilterId.value!!)
//                    ) { starList, muscleList, equipList ->
//                        val muscleIds = muscleList.map { it.id }.toSet()
//                        val equipIds = equipList.map { it.id }.toSet()
//                        starList.filter { it.id in muscleIds && it.id in equipIds }
//                    }.collect { _uiState.value = ExercisesUiState.Data(it) }
//                }
//                // starred + muscle
//                _starredFilter.value && _muscleFilterId.value != null -> {
//                    combine(
//                        exerciseRepo.getAllStarredExercises(),
//                        exerciseRepo.filteredExercisesByMuscleGroup(_muscleFilterId.value!!)
//                    ) { starList, muscleList ->
//                        val muscleIds = muscleList.map { it.id }.toSet()
//                        starList.filter { it.id in muscleIds }
//                    }.collect { _uiState.value = ExercisesUiState.Data(it) }
//                }
//                // starred + equipment
//                _starredFilter.value && _equipmentFilterId.value != null -> {
//                    combine(
//                        exerciseRepo.getAllStarredExercises(),
//                        exerciseRepo.filteredExercisesByEquipment(_equipmentFilterId.value!!)
//                    ) { starList, equipList ->
//                        val equipIds = equipList.map { it.id }.toSet()
//                        starList.filter { it.id in equipIds }
//                    }.collect { _uiState.value = ExercisesUiState.Data(it) }
//                }
//                // starred only
//                _starredFilter.value -> {
//                    exerciseRepo.getAllStarredExercises().collect {
//                        _uiState.value = ExercisesUiState.Data(it)
//                    }
//                }
//                // muscle + equipment
//                _muscleFilterId.value != null && _equipmentFilterId.value != null -> {
//                    combine(
//                        exerciseRepo.filteredExercisesByMuscleGroup(_muscleFilterId.value!!),
//                        exerciseRepo.filteredExercisesByEquipment(_equipmentFilterId.value!!)
//                    ) { muscleList, equList ->
//                        val equIds = equList.map { it.id }.toSet()
//                        muscleList.filter { it.id in equIds }
//                    }.collect { _uiState.value = ExercisesUiState.Data(it) }
//                }
//                // muscle only
//                _muscleFilterId.value != null -> {
//                    exerciseRepo.filteredExercisesByMuscleGroup(_muscleFilterId.value!!).collect {
//                        _uiState.value = ExercisesUiState.Data(it)
//                    }
//                }
//                // equipment only
//                _equipmentFilterId.value != null -> {
//                    exerciseRepo.filteredExercisesByEquipment(_equipmentFilterId.value!!).collect {
//                        _uiState.value = ExercisesUiState.Data(it)
//                    }
//                }
//                // none: all exercises
//                else -> {
//                    exerciseRepo.allExercises().collect {
//                        _uiState.value = ExercisesUiState.Data(it)
//                    }
//                }
//            }
//        }
//    }
//    fun filterByMuscleGroup(muscleGroupId: Int) {
//        _muscleFilterId.value = muscleGroupId
//        loadExercisesBasedOnFilters()
//    }
//
//    fun filterByEquipment(equipmentId: Int) {
//        _equipmentFilterId.value = equipmentId
//        loadExercisesBasedOnFilters()
//    }
//
//    fun clearMuscleGroupFilter() {
//        _muscleFilterId.value = null
//        loadExercisesBasedOnFilters()
//    }
//
//    fun clearEquipmentFilter() {
//        _equipmentFilterId.value = null
//        loadExercisesBasedOnFilters()
//    }
//
//    fun starredExercise() {
//        _starredFilter.value = !_starredFilter.value
//        loadExercisesBasedOnFilters()
//    }
//
//    /**
//     * Rimuove tutti i filtri: equipment, muscle e starred.
//     */
//    fun clearAllFilters() {
//        _equipmentFilterId.value = null
//        _muscleFilterId.value = null
//        // Reset starred filter explicitly
//        _starredFilter.value = false
//        loadExercisesBasedOnFilters()
//    }
//
//
//    sealed interface ExercisesUiState {
//        object Loading : ExercisesUiState
//        data class Data(val exercises: List<ExerciseWithImage>) : ExercisesUiState
//    }
//}

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

    private val _cachedExercises = MutableStateFlow<List<ExerciseWithImage>>(emptyList())
    val cachedExercises: StateFlow<List<ExerciseWithImage>> = _cachedExercises

    fun cacheAllExercises(list: List<ExerciseWithImage>) {
        _cachedExercises.value = list
    }


    // Sorgenti dati "hot" e cache in memoria
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

    // Flussi dipendenti dai filtri (dinamici)
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

    // Azioni filtri (niente collect qui!)
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

    // Opzionale: tocca i flussi per forzare "warm up"
    fun warmUp() {
        allExercises.value
        starredExercises.value
        muscleGroups.value
        equipments.value
    }

    sealed interface ExercisesUiState {
        object Loading : ExercisesUiState
        data class Data(val exercises: List<ExerciseWithImage>) : ExercisesUiState
    }
}
