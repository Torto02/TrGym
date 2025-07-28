package com.torto.trgym.data.repository

import android.content.Context
import com.torto.trgym.database.dao.LanguageDao
import com.torto.trgym.database.dao.MuscleDao
import com.torto.trgym.database.entities.MuscleGroupI18n
import com.torto.trgym.utils.LocaleManager
import dagger.hilt.android.qualifiers.ApplicationContext

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class MuscleRepository @Inject constructor(
    private val languageDao: LanguageDao,
    private val muscleDao: MuscleDao,
    @ApplicationContext private val context: Context
){

    fun allMuscleGroups(): Flow<List<MuscleGroupI18n>> = flow {
        val langCode = getLangCode()
        emit(muscleDao.getMuscleGroups(langCode))
    }.flowOn(Dispatchers.IO)
    suspend fun getLangCode(): Int{
        val languageCode = LocaleManager.getStoredLanguage(context)
        val languageId = getLanguageId(languageCode)
        return languageId
    }

    private suspend fun getLanguageId(languageCode: String): Int {
        // Ottieni l'ID della lingua dal codice, usando l'inglese come fallback
        return languageDao.getLanguageIdByCode(languageCode)
            ?: languageDao.getLanguageIdByCode("en")
            ?: 1 // Fallback all'ID 1 se non viene trovato nulla
    }
}