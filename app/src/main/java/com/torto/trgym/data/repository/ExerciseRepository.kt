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
     * Sincronizza gli esercizi preferiti tra Room e Supabase usando merge intelligente
     */
    suspend fun syncStarredExercises() = withContext(Dispatchers.IO) {
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
            
            // Step 1: Carica le modifiche locali non sincronizzate su Supabase
            val unsyncedLocal = starredExerciseDao.getUnsyncedStarredExercises()
            unsyncedLocal.forEach { localStarred ->
                try {
                    addToSupabaseStarred(localStarred.exerciseId)
                    starredExerciseDao.markAsSynced(localStarred.exerciseId)
                    Log.d("ExerciseRepository", "Synced local starred exercise ${localStarred.exerciseId} to Supabase")
                } catch (e: Exception) {
                    Log.w("ExerciseRepository", "Could not sync local starred exercise ${localStarred.exerciseId}", e)
                }
            }

            // Step 2: Scarica i dati da Supabase
            val supabaseStarred = supabaseClient
                .from("starred_exercises")
                .select{ filter {
                    eq("user_id", userId)
                }}
                .decodeList<SupabaseStarredExercise>()

            // Step 3: Ottieni i dati locali attuali
            val localStarred = starredExerciseDao.getAllLocalStarredExercises()
            val localIds = localStarred.map { it.exerciseId }.toSet()
            val supabaseIds = supabaseStarred.map { it.exerciseId }.toSet()

            // Step 4: Merge intelligente
            // Aggiungi elementi da Supabase che non sono presenti localmente
            val toAddLocally = supabaseIds - localIds
            if (toAddLocally.isNotEmpty()) {
                val newLocalStarred = toAddLocally.map { exerciseId ->
                    StarredExercise(
                        exerciseId = exerciseId,
                        syncedWithServer = true
                    )
                }
                starredExerciseDao.insertStarredExercises(newLocalStarred)
                Log.d("ExerciseRepository", "Added ${toAddLocally.size} starred exercises from Supabase")
            }

            // Step 5: Rimuovi elementi locali che non sono più su Supabase (solo se erano sincronizzati)
            val toRemoveLocally = localIds - supabaseIds
            toRemoveLocally.forEach { exerciseId ->
                val localExercise = localStarred.find { it.exerciseId == exerciseId }
                // Rimuovi solo se era già sincronizzato (evita di cancellare modifiche offline pendenti)
                if (localExercise?.syncedWithServer == true) {
                    starredExerciseDao.removeFromStarred(exerciseId)
                    Log.d("ExerciseRepository", "Removed synced starred exercise $exerciseId that was deleted on server")
                }
            }

            Log.d("ExerciseRepository", "Smart sync completed: ${supabaseStarred.size} on server, ${localStarred.size} local")
        } catch (e: Exception) {
            Log.e("ExerciseRepository", "Error during smart sync of starred exercises", e)
        }
    }

    suspend fun toggleStarredExercise(exerciseId: Int, isCurrentlyStarred: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                if (isCurrentlyStarred) {
                    // Rimuovi dall'locale
                    starredExerciseDao.removeFromStarred(exerciseId)

                    // Prova a rimuovere da Supabase (se online)
                    try {
                        removeFromSupabaseStarred(exerciseId)
                    } catch (e: Exception) {
                        Log.w("ExerciseRepository", "Could not remove from Supabase (offline?), will sync later", e)
                        // L'operazione locale è già stata fatta, sync avverrà quando tornerà online
                    }
                } else {
                    // Aggiungi al locale con flag pendingSync se fallisce l'upload
                    val starredExercise = StarredExercise(
                        exerciseId = exerciseId,
                        syncedWithServer = false
                    )
                    starredExerciseDao.addToStarred(starredExercise)

                    // Prova ad aggiungere a Supabase (se online)
                    try {
                        addToSupabaseStarred(exerciseId)
                        // Se riuscito, aggiorna il flag sync
                        starredExerciseDao.markAsSynced(exerciseId)
                    } catch (e: Exception) {
                        Log.w("ExerciseRepository", "Could not add to Supabase (offline?), will sync later", e)
                        // Mantieni syncedWithServer = false per sincronizzare in seguito
                    }
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