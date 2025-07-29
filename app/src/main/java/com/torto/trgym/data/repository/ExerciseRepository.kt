package com.torto.trgym.data.repository

import android.content.Context
import android.util.Log
import com.torto.trgym.database.dao.ExerciseDao
import com.torto.trgym.database.dao.LanguageDao
import com.torto.trgym.database.dto.ExerciseDetail
import com.torto.trgym.database.dto.ExerciseWithImage
import com.torto.trgym.database.dao.StarredExerciseDao
import com.torto.trgym.database.entities.StarredExercise
import com.torto.trgym.database.dto.SupabaseStarredExercise
import com.torto.trgym.utils.LocaleManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val languageDao: LanguageDao,
    private val starredExerciseDao: StarredExerciseDao,
    private val supabaseClient: SupabaseClient,
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

    fun isExerciseStarred(exerciseId: Int): Flow<Boolean> {
        return starredExerciseDao.isExerciseStarred(exerciseId)
    }

    private suspend fun addToSupabaseStarred(exerciseId: Int) {
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return

            supabaseClient
                .from("starred_exercises")
                .insert(
                    SupabaseStarredExercise(
                        userId = userId,
                        exerciseId = exerciseId
                    )
                )

            Log.d("ExerciseRepository", "Added exercise $exerciseId to Supabase starred")
        } catch (e: Exception) {
            Log.e("ExerciseRepository", "Error adding to Supabase starred", e)
        }
    }

    private suspend fun removeFromSupabaseStarred(exerciseId: Int) {
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return

            supabaseClient
                .from("starred_exercises")
                .delete {
                    filter {
                        and {
                            eq("user_id", userId)
                            eq("exercise_id", exerciseId)
                        }
                    }
                }

            Log.d("ExerciseRepository", "Removed exercise $exerciseId from Supabase starred")
        } catch (e: Exception) {
            Log.e("ExerciseRepository", "Error removing from Supabase starred", e)
        }
    }

    /**
     * Sincronizza gli esercizi preferiti tra Room e Supabase
     */
    suspend fun syncStarredExercises() = withContext(Dispatchers.IO) {
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
            val supabaseStarred = supabaseClient
                .from("starred_exercises")
                .select{ filter {
                    eq("user_id", userId)
                }
                }
                .decodeList<SupabaseStarredExercise>()


            // Cancella i dati locali e inserisce quelli da Supabase
            starredExerciseDao.clearAllStarred()
            val localStarred = supabaseStarred.map { StarredExercise(it.exerciseId) }
            Log.d("ExerciseRepository", "Synchronized $localStarred starred exercises from Supabase")
            starredExerciseDao.insertStarredExercises(localStarred)

            Log.d("ExerciseRepository", "Synchronized ${localStarred.count()} starred exercises from Supabase")
        } catch (e: Exception) {
            Log.e("ExerciseRepository", "Error syncing starred exercises", e)
        }
    }

    suspend fun toggleStarredExercise(exerciseId: Int, isCurrentlyStarred: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                if (isCurrentlyStarred) {
                    // Rimuovi dall'locale
                    starredExerciseDao.removeFromStarred(exerciseId)

                    // Rimuovi da Supabase
                    removeFromSupabaseStarred(exerciseId)
                } else {
                    // Aggiungi al locale
                    starredExerciseDao.addToStarred(StarredExercise(exerciseId))

                    // Aggiungi a Supabase
                    addToSupabaseStarred(exerciseId)
                }
            } catch (e: Exception) {
                Log.e("ExerciseRepository", "Error toggling starred exercise", e)
            }
        }
    }

    fun getAllStarredExercises(): Flow<List<ExerciseWithImage>> = flow {
        val languageCode = LocaleManager.getStoredLanguage(context)
        val languageId = getLanguageId(languageCode)

        // Otteniamo prima tutti gli ID degli esercizi preferiti
        val starredIds = starredExerciseDao.getAllStarredExerciseIds().firstOrNull() ?: emptyList()

        // Se abbiamo degli ID, recuperiamo i dettagli completi degli esercizi
        if (starredIds.isNotEmpty()) {
            // Nota: potrebbe essere necessario implementare questa funzione in ExerciseDao
            val starredExercises = exerciseDao.getExercisesByIds(starredIds, languageId).firstOrNull() ?: emptyList()
            emit(starredExercises)
        } else {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
}