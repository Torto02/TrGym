package com.torto.trgym.repository

import android.content.Context
import android.util.Log
import com.torto.trgym.database.dao.ExerciseDao
import com.torto.trgym.database.dao.LanguageDao
import com.torto.trgym.database.dto.ExerciseDetail
import com.torto.trgym.database.dto.ExerciseWithImage
import com.torto.trgym.database.dao.StarredExerciseDao
import com.torto.trgym.database.entities.StarredExercise
import com.torto.trgym.database.dto.SupabaseStarredExercise
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
    private val starredExerciseDao: StarredExerciseDao,
    private val supabaseClient: SupabaseClient,
    @ApplicationContext private val context: Context
) {
    fun allExercises(): Flow<List<ExerciseWithImage>> = flow {
        emit(exerciseDao.getExercisesWithImage(1).firstOrNull() ?: emptyList())
    }

    fun getExerciseDetail(id: Int): Flow<ExerciseDetail> = flow {
        emit(exerciseDao.getExerciseDetail(id, 1).firstOrNull()!!)
    }

    fun filteredExercisesByMuscleGroup(muscleGroupId: Int): Flow<List<ExerciseWithImage>> = flow {
        emit(exerciseDao.getExercisesFilteredByMuscleGroup(muscleGroupId, 1).firstOrNull() ?: emptyList())
    }.flowOn(Dispatchers.IO)

    fun filteredExercisesByEquipment(equipmentId: Int): Flow<List<ExerciseWithImage>> = flow {
        emit(exerciseDao.getExercisesFilteredByEquipment(equipmentId, 1).firstOrNull() ?: emptyList())
    }.flowOn(Dispatchers.IO)


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
     * Sincronizza gli esercizi preferiti tra Room e Supabase - SOLO PUSH (upload)
     * Download iniziale viene fatto solo al primo accesso dell'app
     */
    suspend fun syncStarredExercises() = withContext(Dispatchers.IO) {
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
            
            // SOLO upload: Carica le modifiche locali non sincronizzate su Supabase
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

            Log.d("ExerciseRepository", "Push-only sync completed: uploaded ${unsyncedLocal.size} local changes to Supabase")

        } catch (e: Exception) {
            Log.e("ExerciseRepository", "Error during push-only sync of starred exercises", e)
        }
    }

    /**
     * Download iniziale degli esercizi preferiti da Supabase - chiamato solo al primo accesso
     */
    suspend fun downloadStarredExercisesInitial() = withContext(Dispatchers.IO) {
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext
            
            // Download i dati da Supabase
            val supabaseStarred = supabaseClient
                .from("starred_exercises")
                .select{ filter {
                    eq("user_id", userId)
                }}
                .decodeList<SupabaseStarredExercise>()

            // Ottieni i dati locali attuali
            val localStarred = starredExerciseDao.getAllLocalStarredExercises()
            val localIds = localStarred.map { it.exerciseId }.toSet()
            val supabaseIds = supabaseStarred.map { it.exerciseId }.toSet()

            // Merge intelligente
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
                Log.d("ExerciseRepository", "Initial download: Added ${toAddLocally.size} starred exercises from Supabase")
            }

            // Rimuovi elementi locali che non sono più su Supabase (solo se erano sincronizzati)
            val toRemoveLocally = localIds - supabaseIds
            toRemoveLocally.forEach { exerciseId ->
                val localExercise = localStarred.find { it.exerciseId == exerciseId }
                // Rimuovi solo se era già sincronizzato (evita di cancellare modifiche offline pendenti)
                if (localExercise?.syncedWithServer == true) {
                    starredExerciseDao.removeFromStarred(exerciseId)
                    Log.d("ExerciseRepository", "Initial download: Removed synced starred exercise $exerciseId that was deleted on server")
                }
            }

            Log.d("ExerciseRepository", "Initial download completed: ${supabaseStarred.size} starred exercises from server")
        } catch (e: Exception) {
            Log.e("ExerciseRepository", "Error during initial download of starred exercises", e)
        }
    }

    suspend fun toggleStarredExercise(exerciseId: Int, isCurrentlyStarred: Boolean) {
        withContext(Dispatchers.IO) {
            try {
                if (isCurrentlyStarred) {
                    starredExerciseDao.removeFromStarred(exerciseId)
                    try {
                        removeFromSupabaseStarred(exerciseId)
                    } catch (e: Exception) {
                        Log.w("ExerciseRepository", "Could not remove from Supabase", e)
                    }
                } else {
                    val starredExercise = StarredExercise(
                        exerciseId = exerciseId,
                        syncedWithServer = false
                    )
                    starredExerciseDao.addToStarred(starredExercise)
                    try {
                        addToSupabaseStarred(exerciseId)
                        starredExerciseDao.markAsSynced(exerciseId)
                    } catch (e: Exception) {
                        Log.w("ExerciseRepository", "Could not add to Supabase", e)
                    }
                }
                // Forza l'aggiornamento del flusso
                getAllStarredExercises().collect { /* Trigger update */ }
            } catch (e: Exception) {
                Log.e("ExerciseRepository", "Error toggling starred exercise", e)
            }
        }
    }

    fun getAllStarredExercises(): Flow<List<ExerciseWithImage>> = flow {
        val starredIds = starredExerciseDao.getAllStarredExerciseIds().firstOrNull() ?: emptyList()

        if (starredIds.isNotEmpty()) {
            val starredExercises = exerciseDao.getExercisesByIds(starredIds, 1).firstOrNull() ?: emptyList()
            emit(starredExercises)
        } else {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)
}