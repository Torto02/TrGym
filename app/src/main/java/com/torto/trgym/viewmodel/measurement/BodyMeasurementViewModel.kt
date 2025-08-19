package com.torto.trgym.viewmodel.measurement

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.torto.trgym.data.repository.BodyMeasurementRepository
import com.torto.trgym.database.dto.MeasurementStats
import com.torto.trgym.database.entities.BodyMeasurement
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BodyMeasurementViewModel @Inject constructor(
    private val repository: BodyMeasurementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BodyMeasurementUiState>(BodyMeasurementUiState.Loading)
    val uiState: StateFlow<BodyMeasurementUiState> = _uiState

    private val _selectedMeasurementName = MutableStateFlow<String?>(null)
    val selectedMeasurementName: StateFlow<String?> = _selectedMeasurementName

    init {
        loadMeasurementCategories()
    }

    private fun loadMeasurementCategories() {
        viewModelScope.launch {
            try {
                // Combina la lista dei nomi delle misurazioni con le statistiche per ciascuna
                repository.getAllMeasurementNames().combine(
                    repository.getAllMeasurements()
                ) { names, allMeasurements ->
                    val categoriesWithStats = names.map { name ->
                        val measurements = allMeasurements.filter { it.name == name }
                        val latest = measurements.firstOrNull() // già ordinati per data DESC
                        MeasurementCategory(
                            name = name,
                            unit = latest?.unit ?: "",
                            latestValue = latest?.value,
                            latestDate = latest?.measurementDate,
                            totalCount = measurements.size
                        )
                    }
                    categoriesWithStats
                }.collect { categories ->
                    _uiState.value = BodyMeasurementUiState.Success(categories)
                }
            } catch (e: Exception) {
                Log.e("BodyMeasurementVM", "Error loading measurement categories", e)
                _uiState.value = BodyMeasurementUiState.Error("Errore nel caricamento delle misurazioni")
            }
        }
    }

    fun addNewMeasurement(name: String, value: Double, unit: String, notes: String? = null) {
        viewModelScope.launch {
            try {
                val measurement = BodyMeasurement(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    value = value,
                    unit = unit.trim(),
                    notes = notes?.takeIf { it.isNotBlank() }
                )

                repository.insertMeasurement(measurement)
                Log.d("BodyMeasurementVM", "Added new measurement: $name = $value $unit")
            } catch (e: Exception) {
                Log.e("BodyMeasurementVM", "Error adding measurement", e)
                _uiState.value = BodyMeasurementUiState.Error("Errore nell'aggiunta della misurazione")
            }
        }
    }

    fun updateMeasurement(measurement: BodyMeasurement, newValue: Double, newNotes: String? = null) {
        viewModelScope.launch {
            try {
                val updatedMeasurement = measurement.copy(
                    value = newValue,
                    notes = newNotes?.takeIf { it.isNotBlank() },
                    updatedAt = System.currentTimeMillis()
                )

                repository.updateMeasurement(updatedMeasurement)
                Log.d("BodyMeasurementVM", "Updated measurement: ${measurement.name}")
            } catch (e: Exception) {
                Log.e("BodyMeasurementVM", "Error updating measurement", e)
                _uiState.value = BodyMeasurementUiState.Error("Errore nell'aggiornamento della misurazione")
            }
        }
    }

    fun deleteMeasurement(measurement: BodyMeasurement) {
        viewModelScope.launch {
            try {
                repository.deleteMeasurement(measurement)
                Log.d("BodyMeasurementVM", "Deleted measurement: ${measurement.id}")
            } catch (e: Exception) {
                Log.e("BodyMeasurementVM", "Error deleting measurement", e)
                _uiState.value = BodyMeasurementUiState.Error("Errore nell'eliminazione della misurazione")
            }
        }
    }

    fun deleteMeasurementCategory(categoryName: String) {
        viewModelScope.launch {
            try {
                repository.deleteMeasurementsByName(categoryName)
                Log.d("BodyMeasurementVM", "Deleted all measurements for category: $categoryName")
            } catch (e: Exception) {
                Log.e("BodyMeasurementVM", "Error deleting measurement category", e)
                _uiState.value = BodyMeasurementUiState.Error("Errore nell'eliminazione della categoria")
            }
        }
    }

    fun selectMeasurementCategory(categoryName: String) {
        _selectedMeasurementName.value = categoryName
    }

    fun clearSelection() {
        _selectedMeasurementName.value = null
    }

    fun getMeasurementsForCategory(categoryName: String) = repository.getMeasurementsByName(categoryName)

    suspend fun getMeasurementStats(categoryName: String): MeasurementStats? {
        return try {
            repository.getMeasurementStats(categoryName)
        } catch (e: Exception) {
            Log.e("BodyMeasurementVM", "Error getting stats for $categoryName", e)
            null
        }
    }

    sealed interface BodyMeasurementUiState {
        object Loading : BodyMeasurementUiState
        data class Success(val categories: List<MeasurementCategory>) : BodyMeasurementUiState
        data class Error(val message: String) : BodyMeasurementUiState
    }

    data class MeasurementCategory(
        val name: String,
        val unit: String,
        val latestValue: Double?,
        val latestDate: Long?,
        val totalCount: Int
    )
}