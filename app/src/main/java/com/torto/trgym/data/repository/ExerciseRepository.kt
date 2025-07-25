//package com.torto.trgym.data.repository
//
//import com.torto.trgym.database.dao.ExerciseDao
//import com.torto.trgym.database.dto.ExerciseDetail
//import com.torto.trgym.database.dto.ExerciseWithImage
//import dagger.hilt.android.scopes.ViewModelScoped
//import kotlinx.coroutines.flow.Flow
//import javax.inject.Inject
//import javax.inject.Singleton
//
//@Singleton
//class ExerciseRepository @Inject constructor(
//    private val dao: ExerciseDao
//) {
//    fun allExercises(): Flow<List<ExerciseWithImage>> = dao.getExercisesWithImage()
//
//    fun getExerciseDetail(id: Int): Flow<ExerciseDetail> =
//        dao.getExerciseDetail(id)
//}


package com.torto.trgym.data.repository

import android.content.Context
import com.torto.trgym.database.dao.ExerciseDao
import com.torto.trgym.database.dao.LanguageDao
import com.torto.trgym.database.dto.ExerciseDetail
import com.torto.trgym.database.dto.ExerciseWithImage
import com.torto.trgym.utils.LocaleManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val dao: ExerciseDao,
    private val languageDao: LanguageDao,
    @ApplicationContext private val context: Context
) {
    fun allExercises(): Flow<List<ExerciseWithImage>> = flow {
        val languageCode = LocaleManager.getStoredLanguage(context)
        val languageId = getLanguageId(languageCode)
        emit(dao.getExercisesWithImage(languageId).firstOrNull() ?: emptyList())
    }

    fun getExerciseDetail(id: Int): Flow<ExerciseDetail> = flow {
        val languageCode = LocaleManager.getStoredLanguage(context)
        val languageId = getLanguageId(languageCode)
        emit(dao.getExerciseDetail(id, languageId).firstOrNull()!!)
    }

    private suspend fun getLanguageId(languageCode: String): Int {
        // Ottieni l'ID della lingua dal codice, usando l'inglese come fallback
        return languageDao.getLanguageIdByCode(languageCode)
            ?: languageDao.getLanguageIdByCode("en")
            ?: 1 // Fallback all'ID 1 se non viene trovato nulla
    }
}