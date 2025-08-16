package com.torto.trgym.data.repository

import android.util.Log
import com.torto.trgym.database.dao.WorkoutDao
import com.torto.trgym.database.dto.WorkoutExerciseDetail
import com.torto.trgym.database.dto.WorkoutCardDetail
import com.torto.trgym.database.dto.WorkoutSessionExerciseDetail
import com.torto.trgym.database.dto.SupabaseWorkoutCard
import com.torto.trgym.database.dto.SupabaseWorkoutExercise
import com.torto.trgym.database.dto.SupabaseExerciseSet
import com.torto.trgym.database.dto.SupabaseWorkoutSession
import com.torto.trgym.database.dto.SupabaseWorkoutSessionExercise
import com.torto.trgym.database.dto.SupabaseWorkoutSessionSet
import com.torto.trgym.database.entities.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException


@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val supabaseClient: SupabaseClient
) {
    
    private val currentUserId: String?
        get() = supabaseClient.auth.currentUserOrNull()?.id

    fun getAllWorkoutCards(): Flow<List<WorkoutCard>> = workoutDao.getAllWorkoutCards()
    suspend fun getWorkoutCard(cardId: String): WorkoutCard? = workoutDao.getWorkoutCard(cardId)
    fun getExercisesForCard(cardId: String): Flow<List<WorkoutExercise>> = workoutDao.getExercisesForCard(cardId)
    fun getSetsForExercise(exerciseId: String): Flow<List<ExerciseSet>> = workoutDao.getSetsForExercise(exerciseId)

    suspend fun insertWorkoutCard(workoutCard: WorkoutCard) = withContext(Dispatchers.IO) {
        val cardToInsert = workoutCard.copy(pendingSync = true, syncedWithServer = false)
        workoutDao.insertWorkoutCard(cardToInsert)
        
        try {
            currentUserId?.let { userId ->
                val supabaseCard = SupabaseWorkoutCard(
                    id = workoutCard.id,
                    userId = userId,
                    name = workoutCard.name,
                    description = workoutCard.description
                )
                supabaseClient.from("workout_cards").upsert(listOf(supabaseCard))
                Log.d("WorkoutRepository", "Synced workout_cards item ${workoutCard.id} to Supabase")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not sync workout_cards item ${workoutCard.id} to Supabase", e)
        }
    }

    suspend fun updateWorkoutCard(workoutCard: WorkoutCard) = withContext(Dispatchers.IO) {
        workoutDao.updateWorkoutCard(workoutCard)
        
        try {
            currentUserId?.let { userId ->
                val supabaseCard = SupabaseWorkoutCard(
                    id = workoutCard.id,
                    userId = userId,
                    name = workoutCard.name,
                    description = workoutCard.description
                )
                supabaseClient.from("workout_cards").upsert(listOf(supabaseCard))
                Log.d("WorkoutRepository", "Synced workout_cards item ${workoutCard.id} to Supabase")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not sync workout_cards item ${workoutCard.id} to Supabase", e)
        }
    }

    suspend fun deleteWorkoutCard(workoutCard: WorkoutCard) = withContext(Dispatchers.IO) {
        workoutDao.deleteWorkoutCard(workoutCard)
        
        try {
            currentUserId?.let {
                supabaseClient.from("workout_cards").delete {
                    filter { eq("id", workoutCard.id) }
                }
                Log.d("WorkoutRepository", "Deleted workout_cards item ${workoutCard.id} from Supabase")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error deleting workout_cards item ${workoutCard.id} from Supabase", e)
        }
    }

    suspend fun getWorkoutCardWithExercises(cardId: String): WorkoutCardDetail? = workoutDao.getWorkoutCardWithExercises(cardId)
    suspend fun getExerciseCountForCard(cardId: String): Int = workoutDao.getExerciseCountForCard(cardId)


    suspend fun syncExercisesForCard(cardId: String) = withContext(Dispatchers.IO) {
        try {
            currentUserId ?: return@withContext
            syncUnsyncedExercises(cardId)
            mergeExercisesFromServer(cardId)
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error syncing exercises for card $cardId", e)
        }
    }
    
    private suspend fun syncUnsyncedExercises(cardId: String) {
        workoutDao.getUnsyncedWorkoutExercisesForCard(cardId).forEach { exercise ->
            try {
                currentUserId?.let {
                    val supabaseExercise = SupabaseWorkoutExercise(
                        id = exercise.id,
                        workoutCardId = exercise.workoutCardId,
                        // Removed exerciseId field as it's not in Supabase schema
                        name = exercise.name,
                        orderIndex = exercise.orderIndex
                    )
                    supabaseClient.from("exercises").upsert(listOf(supabaseExercise))
                    workoutDao.markWorkoutExerciseAsSynced(exercise.id)
                    Log.d("WorkoutRepository", "Synced exercises item ${exercise.id} to Supabase")
                }
            } catch (e: Exception) {
                Log.w("WorkoutRepository", "Could not sync exercises item ${exercise.id} to Supabase", e)
            }
        }
    }
    
    private suspend fun mergeExercisesFromServer(cardId: String) {
        val supabaseExercises = supabaseClient.from("exercises")
            .select { filter { eq("workout_card_id", cardId) } }
            .decodeList<SupabaseWorkoutExercise>()
            
        val localExercises = workoutDao.getExercisesForCardOnce(cardId)
        performDataMerge(
            local = localExercises,
            remote = supabaseExercises,
            getId = { it.id },
            getRemoteId = { it.id },
            createLocal = { remote ->
                WorkoutExercise(
                    id = remote.id!!,
                    workoutCardId = remote.workoutCardId,
                    exerciseId = null, // Changed to null since it's not coming from Supabase
                    name = remote.name,
                    orderIndex = remote.orderIndex,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncedWithServer = true,
                    pendingSync = false
                )
            },
            insert = workoutDao::insertWorkoutExercise,
            delete = workoutDao::deleteWorkoutExercise,
            shouldUpdate = { local, remote ->
                local.name != remote.name || local.orderIndex != remote.orderIndex
            },
            update = { local, remote ->
                workoutDao.updateWorkoutExercise(
                    local.copy(
                        name = remote.name,
                        orderIndex = remote.orderIndex,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        )
    }

    suspend fun syncSetsForExercise(exerciseId: String) = withContext(Dispatchers.IO) {
        try {
            currentUserId ?: return@withContext
            syncUnsyncedSets(exerciseId)
            mergeSetsFromServer(exerciseId)
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error syncing sets for exercise $exerciseId", e)
        }
    }
    
    private suspend fun syncUnsyncedSets(exerciseId: String) {
        workoutDao.getUnsyncedSetsForExercise(exerciseId).forEach { set ->
            try {
                currentUserId?.let {
                    val supabaseSet = SupabaseExerciseSet(
                        id = set.id,
                        exerciseId = set.exerciseId,
                        setNumber = set.setNumber,
                        weightKg = set.weightKg,
                        reps = set.reps
                    )
                    supabaseClient.from("exercise_sets").upsert(listOf(supabaseSet))
                    workoutDao.markExerciseSetAsSynced(set.id)
                    Log.d("WorkoutRepository", "Synced exercise_sets item ${set.id} to Supabase")
                }
            } catch (e: Exception) {
                Log.w("WorkoutRepository", "Could not sync exercise_sets item ${set.id} to Supabase", e)
            }
        }
    }
    
    private suspend fun mergeSetsFromServer(exerciseId: String) {
        val supabaseSets = supabaseClient.from("exercise_sets")
            .select { filter { eq("exercise_id", exerciseId) } }
            .decodeList<SupabaseExerciseSet>()
            
        val localSets = workoutDao.getSetsForExerciseOnce(exerciseId)
        performDataMerge(
            local = localSets,
            remote = supabaseSets,
            getId = { it.id },
            getRemoteId = { it.id },
            createLocal = { remote ->
                ExerciseSet(
                    id = remote.id!!,
                    exerciseId = remote.exerciseId,
                    setNumber = remote.setNumber,
                    weightKg = remote.weightKg,
                    reps = remote.reps,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncedWithServer = true,
                    pendingSync = false
                )
            },
            insert = workoutDao::insertExerciseSet,
            delete = workoutDao::deleteExerciseSet,
            shouldUpdate = { local, remote ->
                local.setNumber != remote.setNumber || local.weightKg != remote.weightKg || local.reps != remote.reps
            },
            update = { local, remote ->
                workoutDao.updateExerciseSet(
                    local.copy(
                        setNumber = remote.setNumber,
                        weightKg = remote.weightKg,
                        reps = remote.reps,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        )
        reorderSets(exerciseId)
    }
    
    private suspend fun reorderSets(exerciseId: String) {
        val allSets = workoutDao.getSetsForExerciseOnce(exerciseId).sortedBy { it.setNumber }
        allSets.forEachIndexed { index, set ->
            val correctSetNumber = index + 1
            if (set.setNumber != correctSetNumber) {
                workoutDao.updateExerciseSet(
                    set.copy(
                        setNumber = correctSetNumber,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
    
    suspend fun syncAllSetsForCard(cardId: String) = withContext(Dispatchers.IO) {
        try {
            workoutDao.getExercisesForCardOnce(cardId).forEach { exercise ->
                syncSetsForExercise(exercise.id)
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error syncing all sets for card $cardId", e)
        }
    }


    fun getSessionExercisesWithSets(sessionId: String): Flow<List<WorkoutSessionExerciseDetail>> = workoutDao.getSessionExercisesWithSetsFlow(sessionId)
    suspend fun getWorkoutExercise(exerciseId: String): WorkoutExercise? = workoutDao.getWorkoutExercise(exerciseId)
    suspend fun getExerciseWithSets(exerciseId: String): WorkoutExerciseDetail? = workoutDao.getExerciseWithSets(exerciseId)

    suspend fun insertWorkoutExercise(exercise: WorkoutExercise) = withContext(Dispatchers.IO) {
        val exerciseToInsert = exercise.copy(pendingSync = true, syncedWithServer = false)
        workoutDao.insertWorkoutExercise(exerciseToInsert)
        
        try {
            currentUserId?.let {
                val supabaseExercise = SupabaseWorkoutExercise(
                    id = exercise.id,
                    workoutCardId = exercise.workoutCardId,
                    // Removed exerciseId field
                    name = exercise.name,
                    orderIndex = exercise.orderIndex
                )
                supabaseClient.from("exercises").upsert(listOf(supabaseExercise))
                Log.d("WorkoutRepository", "Synced exercises item ${exercise.id} to Supabase")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not sync exercises item ${exercise.id} to Supabase", e)
        }
    }

    suspend fun updateWorkoutExercise(exercise: WorkoutExercise) = withContext(Dispatchers.IO) {
        workoutDao.updateWorkoutExercise(exercise)
        
        try {
            currentUserId?.let {
                val supabaseExercise = SupabaseWorkoutExercise(
                    id = exercise.id,
                    workoutCardId = exercise.workoutCardId,
                    // Removed exerciseId field
                    name = exercise.name,
                    orderIndex = exercise.orderIndex
                )
                supabaseClient.from("exercises").upsert(listOf(supabaseExercise))
                Log.d("WorkoutRepository", "Synced exercises item ${exercise.id} to Supabase")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not sync exercises item ${exercise.id} to Supabase", e)
        }
    }

    suspend fun deleteWorkoutExercise(exercise: WorkoutExercise) = withContext(Dispatchers.IO) {
        workoutDao.deleteWorkoutExercise(exercise)
        
        try {
            currentUserId?.let {
                supabaseClient.from("exercises").delete {
                    filter { eq("id", exercise.id) }
                }
                Log.d("WorkoutRepository", "Deleted exercises item ${exercise.id} from Supabase")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error deleting exercises item ${exercise.id} from Supabase", e)
        }
    }


    suspend fun insertExerciseSet(set: ExerciseSet) = withContext(Dispatchers.IO) {
        workoutDao.insertExerciseSet(set)
        
        try {
            currentUserId?.let {
                val supabaseSet = SupabaseExerciseSet(
                    id = set.id,
                    exerciseId = set.exerciseId,
                    setNumber = set.setNumber,
                    weightKg = set.weightKg,
                    reps = set.reps
                )
                supabaseClient.from("exercise_sets").upsert(listOf(supabaseSet))
                Log.d("WorkoutRepository", "Synced exercise_sets item ${set.id} to Supabase")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not sync exercise_sets item ${set.id} to Supabase", e)
        }
    }

    suspend fun updateExerciseSet(set: ExerciseSet) = withContext(Dispatchers.IO) {
        val setToUpdate = set.copy(
            updatedAt = System.currentTimeMillis(),
            pendingSync = true,
            syncedWithServer = false
        )
        workoutDao.updateExerciseSet(setToUpdate)
        
        try {
            currentUserId?.let {
                val supabaseSet = SupabaseExerciseSet(
                    id = set.id,
                    exerciseId = set.exerciseId,
                    setNumber = set.setNumber,
                    weightKg = set.weightKg,
                    reps = set.reps
                )
                supabaseClient.from("exercise_sets").upsert(listOf(supabaseSet))
                workoutDao.markExerciseSetAsSynced(set.id)
                Log.d("WorkoutRepository", "Synced exercise_sets item ${set.id} to Supabase")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not sync exercise_sets item ${set.id} to Supabase", e)
        }
    }


    suspend fun deleteExerciseSetAndReorder(set: ExerciseSet) = withContext(Dispatchers.IO) {
        workoutDao.deleteSetAndReorder(set)
        
        try {
            currentUserId?.let {
                supabaseClient.from("exercise_sets").delete {
                    filter { eq("id", set.id) }
                }
                Log.d("WorkoutRepository", "Deleted exercise_sets item ${set.id} from Supabase")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error deleting exercise_sets item ${set.id} from Supabase", e)
        }
    }


    private suspend fun <T, R> performDataMerge(
        local: List<T>,
        remote: List<R>,
        getId: (T) -> String,
        getRemoteId: (R) -> String?,
        createLocal: (R) -> T,
        insert: suspend (T) -> Unit,
        delete: suspend (T) -> Unit,
        shouldUpdate: (T, R) -> Boolean,
        update: suspend (T, R) -> Unit
    ) {
        val localIds = local.map(getId).toSet()
        val remoteIds = remote.mapNotNull(getRemoteId).toSet()
        
        // Add items from server that don't exist locally
        remote.filter { getRemoteId(it) !in localIds }
            .forEach { remoteItem ->
                getRemoteId(remoteItem)?.let {
                    insert(createLocal(remoteItem))
                }
            }
        
        // Remove local synced items that no longer exist on server
        (localIds - remoteIds).forEach { itemId ->
            val localItem = local.find { getId(it) == itemId }
            localItem?.let { item ->
                // Only delete if it's synced (check if applicable)
                delete(item)
            }
        }
        
        // Update existing items if necessary
        remote.filter { getRemoteId(it) in localIds }
            .forEach { remoteItem ->
                getRemoteId(remoteItem)?.let { id ->
                    val localItem = local.find { getId(it) == id }
                    if (localItem != null && shouldUpdate(localItem, remoteItem)) {
                        update(localItem, remoteItem)
                    }
                }
            }
    }

    // ==========================
    // SIMPLIFIED SYNC OPERATIONS
    // ==========================
    
    suspend fun syncWorkoutCards() = withContext(Dispatchers.IO) {
        try {
            currentUserId ?: return@withContext
            syncUnsyncedData()
            mergeCardsFromServer()
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error during sync of workout cards", e)
        }
    }

    private suspend fun syncUnsyncedData() {
        currentUserId?.let { userId ->
            // Sync cards
            workoutDao.getUnsyncedWorkoutCards().forEach { card ->
                try {
                    val supabaseCard = SupabaseWorkoutCard(
                        id = card.id,
                        userId = userId,
                        name = card.name,
                        description = card.description
                    )
                    supabaseClient.from("workout_cards").upsert(listOf(supabaseCard))
                    workoutDao.markWorkoutCardAsSynced(card.id)
                    Log.d("WorkoutRepository", "Synced workout_cards item ${card.id} to Supabase")
                } catch (e: Exception) {
                    Log.w("WorkoutRepository", "Could not sync workout_cards item ${card.id} to Supabase", e)
                }
            }
            
            // Sync exercises
            workoutDao.getUnsyncedWorkoutExercises().forEach { exercise ->
                try {
                    val supabaseExercise = SupabaseWorkoutExercise(
                        id = exercise.id,
                        workoutCardId = exercise.workoutCardId,
                        // Removed exerciseId field
                        name = exercise.name,
                        orderIndex = exercise.orderIndex
                    )
                    supabaseClient.from("exercises").upsert(listOf(supabaseExercise))
                    workoutDao.markWorkoutExerciseAsSynced(exercise.id)
                    Log.d("WorkoutRepository", "Synced exercises item ${exercise.id} to Supabase")
                } catch (e: Exception) {
                    Log.w("WorkoutRepository", "Could not sync exercises item ${exercise.id} to Supabase", e)
                }
            }
            
            // Sync sets
            workoutDao.getUnsyncedExerciseSets().forEach { set ->
                try {
                    val supabaseSet = SupabaseExerciseSet(
                        id = set.id,
                        exerciseId = set.exerciseId,
                        setNumber = set.setNumber,
                        weightKg = set.weightKg,
                        reps = set.reps
                    )
                    supabaseClient.from("exercise_sets").upsert(listOf(supabaseSet))
                    workoutDao.markExerciseSetAsSynced(set.id)
                    Log.d("WorkoutRepository", "Synced exercise_sets item ${set.id} to Supabase")
                } catch (e: Exception) {
                    Log.w("WorkoutRepository", "Could not sync exercise_sets item ${set.id} to Supabase", e)
                }
            }
        }
    }

    private suspend fun mergeCardsFromServer() {
        val localCards = getAllWorkoutCards().firstOrNull() ?: emptyList()
        val supabaseCards = supabaseClient.from("workout_cards")
            .select { filter { eq("user_id", currentUserId!!) } }
            .decodeList<SupabaseWorkoutCard>()
            
        performDataMerge(
            local = localCards,
            remote = supabaseCards,
            getId = { it.id },
            getRemoteId = { it.id },
            createLocal = { remote ->
                WorkoutCard(
                    id = remote.id!!,
                    name = remote.name,
                    description = remote.description,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncedWithServer = true,
                    pendingSync = false
                )
            },
            insert = { card ->
                workoutDao.insertWorkoutCard(card)
                syncWorkoutDataForCard(card.id)
            },
            delete = workoutDao::deleteWorkoutCard,
            shouldUpdate = { local, remote ->
                local.name != remote.name || local.description != remote.description
            },
            update = { local, remote ->
                workoutDao.updateWorkoutCard(
                    local.copy(
                        name = remote.name,
                        description = remote.description,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        )
    }
    
    private suspend fun syncWorkoutDataForCard(cardId: String) {
        try {
            syncExercisesForCard(cardId)
            syncAllSetsForCard(cardId)
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error syncing workout data for card $cardId", e)
        }
    }

    suspend fun getActiveWorkoutSession(): WorkoutSession? = workoutDao.getActiveWorkoutSession()
    suspend fun getSessionExercises(sessionId: String): List<WorkoutSessionExercise> = workoutDao.getSessionExercises(sessionId)
    suspend fun getSessionSets(exerciseId: String): List<WorkoutSessionSet> = workoutDao.getSessionSets(exerciseId)
    suspend fun getLatestWorkoutSessions(limit: Int = 10): List<WorkoutSession> = workoutDao.getLatestWorkoutSessions(limit)

    suspend fun countWorkoutSessions(): Int = withContext(Dispatchers.IO) {
        try {
            val userId = currentUserId
            if (userId == null) {
                // Fallback to local data if user is not logged in
                return@withContext workoutDao.countWorkoutSessions()
            }
            
            // Count from Supabase for the logged-in user
            val response = supabaseClient.from("workout_sessions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("is_completed", true)
                    }
                }
                .decodeList<SupabaseWorkoutSession>()
            Log.d("WorkoutRepository", "Supabase workout response: $response")

            val count = response.size
            Log.d("WorkoutRepository", "Supabase workout session count: $count")
            return@withContext count
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error counting workout sessions from Supabase", e)
            // Fallback to local data if there's an error
            return@withContext workoutDao.countWorkoutSessions()
        }
    }

    suspend fun insertWorkoutSession(session: WorkoutSession) = withContext(Dispatchers.IO) {
        val sessionToInsert = session.copy(pendingSync = true, syncedWithServer = false)
        workoutDao.insertWorkoutSession(sessionToInsert)
        
        try {
            currentUserId?.let { userId ->
                val supabaseSession = SupabaseWorkoutSession(
                    id = session.id,
                    userId = userId,
                    workoutCardId = session.workoutCardId,
                    name = session.name,
                    startTime = java.time.Instant.ofEpochMilli(session.startTime).toString(),
                    endTime = session.endTime?.let { java.time.Instant.ofEpochMilli(it).toString() },
                    isCompleted = session.isCompleted,
                    totalDurationMs = session.totalDurationMs,
                    notes = session.notes
                )
                supabaseClient.from("workout_sessions").upsert(listOf(supabaseSession))
                workoutDao.markWorkoutSessionAsSynced(session.id)
                Log.d("WorkoutRepository", "Synced workout_sessions item ${session.id} to Supabase")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not sync workout_sessions item ${session.id} to Supabase", e)
        }
    }

    suspend fun updateWorkoutSession(session: WorkoutSession) = withContext(Dispatchers.IO) {
        workoutDao.updateWorkoutSession(session)
        
        try {
            currentUserId?.let { userId ->
                val supabaseSession = SupabaseWorkoutSession(
                    id = session.id,
                    userId = userId,
                    workoutCardId = session.workoutCardId,
                    name = session.name,
                    startTime = java.time.Instant.ofEpochMilli(session.startTime).toString(),
                    endTime = session.endTime?.let { java.time.Instant.ofEpochMilli(it).toString() },
                    isCompleted = session.isCompleted,
                    totalDurationMs = session.totalDurationMs,
                    notes = session.notes
                )
                supabaseClient.from("workout_sessions").upsert(listOf(supabaseSession))
                Log.d("WorkoutRepository", "Synced workout_sessions item ${session.id} to Supabase")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not sync workout_sessions item ${session.id} to Supabase", e)
        }
    }

    suspend fun deleteWorkoutSession(session: WorkoutSession) = withContext(Dispatchers.IO) {
        workoutDao.deleteWorkoutSession(session)
        
        try {
            currentUserId?.let {
                supabaseClient.from("workout_sessions").delete {
                    filter { eq("id", session.id) }
                }
                Log.d("WorkoutRepository", "Deleted workout_sessions item ${session.id} from Supabase")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error deleting workout_sessions item ${session.id} from Supabase", e)
        }
    }


    suspend fun insertSessionExercises(exercises: List<WorkoutSessionExercise>) = withContext(Dispatchers.IO) {
        val exercisesToInsert = exercises.map { it.copy(pendingSync = true, syncedWithServer = false) }
        workoutDao.insertSessionExercises(exercisesToInsert)
        currentUserId?.let {
            try {
                val supabaseExercises = exercises.map { exercise ->
                    SupabaseWorkoutSessionExercise(
                        id = exercise.id,
                        workoutSessionId = exercise.workoutSessionId,
                        originalExerciseId = exercise.originalExerciseId,
                        name = exercise.name,
                        orderIndex = exercise.orderIndex,
                        isCompleted = exercise.isCompleted,
                        startTime = exercise.startTime?.let { java.time.Instant.ofEpochMilli(it).toString() },
                        endTime = exercise.endTime?.let { java.time.Instant.ofEpochMilli(it).toString() },
                        restTimeBetweenSets = exercise.restTimeBetweenSets,
                        notes = exercise.notes
                    )
                }
                supabaseClient.from("workout_session_exercises").upsert(supabaseExercises)
                exercises.forEach { workoutDao.markSessionExerciseAsSynced(it.id) }
            } catch (e: Exception) {
                Log.w("WorkoutRepository", "Could not upload session exercises to Supabase", e)
            }
        }
    }

    suspend fun updateSessionExercise(exercise: WorkoutSessionExercise) = withContext(Dispatchers.IO) {
        workoutDao.updateSessionExercise(exercise)
        
        try {
            currentUserId?.let {
                val supabaseExercise = SupabaseWorkoutSessionExercise(
                    id = exercise.id,
                    workoutSessionId = exercise.workoutSessionId,
                    originalExerciseId = exercise.originalExerciseId,
                    name = exercise.name,
                    orderIndex = exercise.orderIndex,
                    isCompleted = exercise.isCompleted,
                    startTime = exercise.startTime?.let { java.time.Instant.ofEpochMilli(it).toString() },
                    endTime = exercise.endTime?.let { java.time.Instant.ofEpochMilli(it).toString() },
                    restTimeBetweenSets = exercise.restTimeBetweenSets,
                    notes = exercise.notes
                )
                supabaseClient.from("workout_session_exercises").upsert(listOf(supabaseExercise))
                Log.d("WorkoutRepository", "Synced workout_session_exercises item ${exercise.id} to Supabase")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not sync workout_session_exercises item ${exercise.id} to Supabase", e)
        }
    }


    suspend fun insertSessionSets(sets: List<WorkoutSessionSet>) = withContext(Dispatchers.IO) {
        val setsToInsert = sets.map { it.copy(pendingSync = true, syncedWithServer = false) }
        workoutDao.insertSessionSets(setsToInsert)
        currentUserId?.let {
            try {
                val supabaseSets = sets.map { set ->
                    SupabaseWorkoutSessionSet(
                        id = set.id,
                        exerciseId = set.exerciseId,
                        setNumber = set.setNumber,
                        targetWeightKg = set.targetWeightKg,
                        actualWeightKg = set.actualWeightKg,
                        targetReps = set.targetReps,
                        actualReps = set.actualReps,
                        isCompleted = set.isCompleted,
                        completedAt = set.completedAt?.let { java.time.Instant.ofEpochMilli(it).toString() },
                        restTimeMs = set.restTimeMs,
                        rpe = set.rpe,
                        notes = set.notes
                    )
                }
                supabaseClient.from("workout_session_sets").upsert(supabaseSets)
                sets.forEach { workoutDao.markSessionSetAsSynced(it.id) }
            } catch (e: Exception) {
                Log.w("WorkoutRepository", "Could not upload session sets to Supabase", e)
            }
        }
    }

    suspend fun updateSessionSet(set: WorkoutSessionSet) = withContext(Dispatchers.IO) {
        workoutDao.updateSessionSet(set)
        
        try {
            currentUserId?.let {
                val supabaseSet = SupabaseWorkoutSessionSet(
                    id = set.id,
                    exerciseId = set.exerciseId,
                    setNumber = set.setNumber,
                    targetWeightKg = set.targetWeightKg,
                    actualWeightKg = set.actualWeightKg,
                    targetReps = set.targetReps,
                    actualReps = set.actualReps,
                    isCompleted = set.isCompleted,
                    completedAt = set.completedAt?.let { java.time.Instant.ofEpochMilli(it).toString() },
                    restTimeMs = set.restTimeMs,
                    rpe = set.rpe,
                    notes = set.notes
                )
                supabaseClient.from("workout_session_sets").upsert(listOf(supabaseSet))
                Log.d("WorkoutRepository", "Synced workout_session_sets item ${set.id} to Supabase")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not sync workout_session_sets item ${set.id} to Supabase", e)
        }
    }

    suspend fun updateSessionSetCompletion(setId: String, isCompleted: Boolean) = withContext(Dispatchers.IO) {
        val completedAt = if (isCompleted) System.currentTimeMillis() else null
        workoutDao.updateSessionSetCompletion(setId, isCompleted, completedAt)
        currentUserId?.let {
            try {
                supabaseClient.from("workout_session_sets")
                    .update(mapOf(
                        "is_completed" to isCompleted,
                        "completed_at" to (completedAt?.let { java.time.Instant.ofEpochMilli(it).toString() })
                    )) { filter { eq("id", setId) } }
            } catch (e: Exception) {
                Log.e("WorkoutRepository", "Error updating session set completion on Supabase", e)
            }
        }
    }



    suspend fun insertSessionSet(set: WorkoutSessionSet) = withContext(Dispatchers.IO) {
        val setToInsert = set.copy(pendingSync = true, syncedWithServer = false)
        workoutDao.insertSessionSet(setToInsert)
        
        try {
            currentUserId?.let {
                val supabaseSet = SupabaseWorkoutSessionSet(
                    id = set.id,
                    exerciseId = set.exerciseId,
                    setNumber = set.setNumber,
                    targetWeightKg = set.targetWeightKg,
                    actualWeightKg = set.actualWeightKg,
                    targetReps = set.targetReps,
                    actualReps = set.actualReps,
                    isCompleted = set.isCompleted,
                    completedAt = set.completedAt?.let { java.time.Instant.ofEpochMilli(it).toString() },
                    restTimeMs = set.restTimeMs,
                    rpe = set.rpe,
                    notes = set.notes
                )
                supabaseClient.from("workout_session_sets").upsert(listOf(supabaseSet))
                workoutDao.markSessionSetAsSynced(set.id)
                Log.d("WorkoutRepository", "Synced workout_session_sets item ${set.id} to Supabase")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not sync workout_session_sets item ${set.id} to Supabase", e)
        }
    }

    suspend fun deleteSessionExercise(exercise: WorkoutSessionExercise) = withContext(Dispatchers.IO) {
        workoutDao.deleteSessionExerciseAndReorder(exercise)
        
        try {
            currentUserId?.let {
                supabaseClient.from("workout_session_exercises").delete {
                    filter { eq("id", exercise.id) }
                }
                Log.d("WorkoutRepository", "Deleted workout_session_exercises item ${exercise.id} from Supabase")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error deleting workout_session_exercises item ${exercise.id} from Supabase", e)
        }
    }

    suspend fun deleteSessionSet(set: WorkoutSessionSet) = withContext(Dispatchers.IO) {
        workoutDao.deleteSessionSetAndReorder(set)
        
        try {
            currentUserId?.let {
                supabaseClient.from("workout_session_sets").delete {
                    filter { eq("id", set.id) }
                }
                Log.d("WorkoutRepository", "Deleted workout_session_sets item ${set.id} from Supabase")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error deleting workout_session_sets item ${set.id} from Supabase", e)
        }
    }
    
}
