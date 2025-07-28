package com.torto.trgym.data.repository

import android.content.Context
import com.torto.trgym.database.dao.ExerciseDao
import com.torto.trgym.database.dao.LanguageDao
import com.torto.trgym.database.dto.ExerciseDetail
import com.torto.trgym.database.dto.ExerciseWithImage
import com.torto.trgym.utils.LocaleManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val languageDao: LanguageDao,
    @ApplicationContext private val context: Context
) {
    fun allExercises(): Flow<List<ExerciseWithImage>> = flow {
        val languageCode = LocaleManager.getStoredLanguage(context)
        val languageId = getLanguageId(languageCode)
        emit(exerciseDao.getExercisesWithImage(languageId).firstOrNull() ?: emptyList())
    }

    fun getExerciseDetail(id: Int): Flow<ExerciseDetail> = flow {
        val languageCode = LocaleManager.getStoredLanguage(context)
        val languageId = getLanguageId(languageCode)
        emit(exerciseDao.getExerciseDetail(id, languageId).firstOrNull()!!)
    }

    fun filteredExercisesByMuscleGroup(muscleGroupId: Int): Flow<List<ExerciseWithImage>> = flow {
        val languageCode = LocaleManager.getStoredLanguage(context)
        val languageId = getLanguageId(languageCode)
        emit(exerciseDao.getExercisesFilteredByMuscleGroup(muscleGroupId, languageId).firstOrNull() ?: emptyList())
    }.flowOn(Dispatchers.IO)

    fun filteredExercisesByEquipment(equipmentId: Int): Flow<List<ExerciseWithImage>> = flow {
        val languageCode = LocaleManager.getStoredLanguage(context)
        val languageId = getLanguageId(languageCode)
        emit(exerciseDao.getExercisesFilteredByEquipment(equipmentId, languageId).firstOrNull() ?: emptyList())
    }.flowOn(Dispatchers.IO)

    private suspend fun getLanguageId(languageCode: String): Int {
        // Ottieni l'ID della lingua dal codice, usando l'inglese come fallback
        return languageDao.getLanguageIdByCode(languageCode)
            ?: languageDao.getLanguageIdByCode("en")
            ?: 1 // Fallback all'ID 1 se non viene trovato nulla
    }
}