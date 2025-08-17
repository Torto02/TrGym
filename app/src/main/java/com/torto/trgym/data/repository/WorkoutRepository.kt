package com.torto.trgym.data.repository

import android.util.Log
import com.torto.trgym.database.dao.WorkoutDao
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton


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

    /**
     * Sincronizza gli esercizi per una card - SOLO PUSH (upload)
     */
    suspend fun syncExercisesForCard(cardId: String) = withContext(Dispatchers.IO) {
        try {
            currentUserId ?: return@withContext
            
            // SOLO upload: Carica le modifiche locali non sincronizzate su Supabase
            syncUnsyncedExercises(cardId)
            
            Log.d("WorkoutRepository", "Push-only sync completed for exercises in card $cardId")
            
            // DISABILITATO: Download e merge da Supabase per evitare flash/reload della UI
            // mergeExercisesFromServer(cardId)
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error during push-only sync of exercises for card $cardId", e)
        }
    }

    // MODIFICA 1: Funzione syncUnsyncedExercises
    private suspend fun syncUnsyncedExercises(cardId: String) {
        workoutDao.getUnsyncedWorkoutExercisesForCard(cardId).forEach { exercise ->
            try {
                currentUserId?.let {
                    val supabaseExercise = SupabaseWorkoutExercise(
                        id = exercise.id,
                        workoutCardId = exercise.workoutCardId,
                        exerciseId = exercise.exerciseId, // AGGIUNTO: Include exerciseId
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

    // MODIFICA 2: Funzione mergeExercisesFromServer
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
                    exerciseId = remote.exerciseId, // AGGIUNTO: Include exerciseId dal server
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
                local.name != remote.name ||
                        local.orderIndex != remote.orderIndex ||
                        local.exerciseId != remote.exerciseId // AGGIUNTO: Controlla anche exerciseId
            },
            update = { local, remote ->
                workoutDao.updateWorkoutExercise(
                    local.copy(
                        name = remote.name,
                        orderIndex = remote.orderIndex,
                        exerciseId = remote.exerciseId, // AGGIUNTO: Aggiorna exerciseId
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        )
    }

    /**
     * Sincronizza i set per un esercizio - SOLO PUSH (upload)
     */
    suspend fun syncSetsForExercise(exerciseId: String) = withContext(Dispatchers.IO) {
        try {
            currentUserId ?: return@withContext
            
            // SOLO upload: Carica le modifiche locali non sincronizzate su Supabase
            syncUnsyncedSets(exerciseId)
            
            Log.d("WorkoutRepository", "Push-only sync completed for sets in exercise $exerciseId")
            
            // DISABILITATO: Download e merge da Supabase per evitare flash/reload della UI
            // mergeSetsFromServer(exerciseId)
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error during push-only sync of sets for exercise $exerciseId", e)
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

    // MODIFICA 3: Funzione insertWorkoutExercise
    suspend fun insertWorkoutExercise(exercise: WorkoutExercise) = withContext(Dispatchers.IO) {
        val exerciseToInsert = exercise.copy(pendingSync = true, syncedWithServer = false)
        workoutDao.insertWorkoutExercise(exerciseToInsert)

        try {
            currentUserId?.let {
                val supabaseExercise = SupabaseWorkoutExercise(
                    id = exercise.id,
                    workoutCardId = exercise.workoutCardId,
                    exerciseId = exercise.exerciseId, // AGGIUNTO: Include exerciseId
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
    
    /**
     * Sincronizza le workout cards - SOLO PUSH (upload)
     * Download iniziale viene fatto solo al primo accesso dell'app
     */
    suspend fun syncWorkoutCards() = withContext(Dispatchers.IO) {
        try {
            currentUserId ?: return@withContext
            
            // SOLO upload: Carica le modifiche locali non sincronizzate su Supabase
            syncUnsyncedData()
            
            Log.d("WorkoutRepository", "Push-only sync completed for workout cards")
            
            // DISABILITATO: Download e merge da Supabase per evitare flash/reload della UI
            // Il download viene fatto solo al primo accesso dell'app in MainActivity
            // mergeCardsFromServer()
            // mergeWorkoutSessionsFromServer()
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error during push-only sync of workout cards", e)
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
                        exerciseId = exercise.exerciseId, // AGGIUNTO: Include exerciseId
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

            // Sync workout sessions
            workoutDao.getUnsyncedWorkoutSessions().forEach { session ->
                try {
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
                } catch (e: Exception) {
                    Log.w("WorkoutRepository", "Could not sync workout_sessions item ${session.id} to Supabase", e)
                }
            }

            // Sync workout session exercises
            workoutDao.getUnsyncedWorkoutSessionExercises().forEach { exercise ->
                try {
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
                    workoutDao.markSessionExerciseAsSynced(exercise.id)
                    Log.d("WorkoutRepository", "Synced workout_session_exercises item ${exercise.id} to Supabase")
                } catch (e: Exception) {
                    Log.w("WorkoutRepository", "Could not sync workout_session_exercises item ${exercise.id} to Supabase", e)
                }
            }

            // Sync workout session sets
            workoutDao.getUnsyncedWorkoutSessionSets().forEach { set ->
                try {
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
                } catch (e: Exception) {
                    Log.w("WorkoutRepository", "Could not sync workout_session_sets item ${set.id} to Supabase", e)
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
            // DISABILITATO: Sincronizzazione completa che include download
            // syncExercisesForCard(cardId)
            // syncAllSetsForCard(cardId)
            
            // Ora viene fatto solo upload tramite syncUnsyncedData()
            Log.d("WorkoutRepository", "Sync workout data for card $cardId - only upload enabled")
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error syncing workout data for card $cardId", e)
        }
    }

    suspend fun getActiveWorkoutSession(): WorkoutSession? = workoutDao.getActiveWorkoutSession()
    suspend fun getSessionExercises(sessionId: String): List<WorkoutSessionExercise> = workoutDao.getSessionExercises(sessionId)
    suspend fun getSessionSets(exerciseId: String): List<WorkoutSessionSet> = workoutDao.getSessionSets(exerciseId)

    /**
     * Conta le sessioni di workout completate - USA SOLO DATI LOCALI
     */
    suspend fun countWorkoutSessions(): Int = withContext(Dispatchers.IO) {
        try {
            // DISABILITATO: Count da Supabase per evitare chiamate inutili
            // Ora usa solo dati locali che vengono aggiornati al primo accesso dell'app
            val count = workoutDao.countWorkoutSessions()
            Log.d("WorkoutRepository", "Local workout session count: $count")
            return@withContext count
            
            /*
            // DISABILITATO: Count from Supabase
            val response = supabaseClient.from("workout_sessions")
                .select {
                    filter {
                        eq("is_completed", true)
                    }
                }
                .decodeList<SupabaseWorkoutSession>()
            Log.d("WorkoutRepository", "Supabase workout response: $response")

            val count = response.size
            Log.d("WorkoutRepository", "Supabase workout session count: $count")
            return@withContext count
            */
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error counting workout sessions", e)
            return@withContext 0
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

    private suspend fun mergeWorkoutSessionsFromServer() {
        try {
            currentUserId?.let { userId ->
                // Merge workout sessions
                val localSessions = workoutDao.getLatestWorkoutSessions(1000) // Get all sessions
                val supabaseSessions = supabaseClient.from("workout_sessions")
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<SupabaseWorkoutSession>()

                performDataMerge(
                    local = localSessions,
                    remote = supabaseSessions,
                    getId = { it.id },
                    getRemoteId = { it.id },
                    createLocal = { remote ->
                        WorkoutSession(
                            id = remote.id!!,
                            workoutCardId = remote.workoutCardId ?: "",
                            name = remote.name,
                            startTime = java.time.Instant.parse(remote.startTime).toEpochMilli(),
                            endTime = remote.endTime?.let { java.time.Instant.parse(it).toEpochMilli() },
                            isCompleted = remote.isCompleted,
                            totalDurationMs = remote.totalDurationMs,
                            notes = remote.notes,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            syncedWithServer = true,
                            pendingSync = false
                        )
                    },
                    insert = { session ->
                        workoutDao.insertWorkoutSession(session)
                        // After inserting session, sync its exercises and sets
                        mergeWorkoutSessionDataFromServer(session.id)
                    },
                    delete = workoutDao::deleteWorkoutSession,
                    shouldUpdate = { local, remote ->
                        local.name != remote.name || 
                        local.isCompleted != remote.isCompleted ||
                        local.totalDurationMs != remote.totalDurationMs ||
                        local.notes != remote.notes
                    },
                    update = { local, remote ->
                        workoutDao.updateWorkoutSession(
                            local.copy(
                                name = remote.name,
                                isCompleted = remote.isCompleted,
                                totalDurationMs = remote.totalDurationMs,
                                notes = remote.notes,
                                endTime = remote.endTime?.let { java.time.Instant.parse(it).toEpochMilli() },
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error merging workout sessions from server", e)
        }
    }

    private suspend fun mergeWorkoutSessionDataFromServer(sessionId: String) {
        try {
            // Merge workout session exercises
            val localExercises = workoutDao.getSessionExercises(sessionId)
            val supabaseExercises = supabaseClient.from("workout_session_exercises")
                .select { filter { eq("workout_session_id", sessionId) } }
                .decodeList<SupabaseWorkoutSessionExercise>()

            performDataMerge(
                local = localExercises,
                remote = supabaseExercises,
                getId = { it.id },
                getRemoteId = { it.id },
                createLocal = { remote ->
                    WorkoutSessionExercise(
                        id = remote.id!!,
                        workoutSessionId = remote.workoutSessionId!!,
                        originalExerciseId = remote.originalExerciseId,
                        name = remote.name,
                        orderIndex = remote.orderIndex,
                        isCompleted = remote.isCompleted,
                        startTime = remote.startTime?.let { java.time.Instant.parse(it).toEpochMilli() },
                        endTime = remote.endTime?.let { java.time.Instant.parse(it).toEpochMilli() },
                        restTimeBetweenSets = remote.restTimeBetweenSets,
                        notes = remote.notes,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        syncedWithServer = true,
                        pendingSync = false
                    )
                },
                insert = { exercise ->
                    workoutDao.insertSessionExercise(exercise)
                    // After inserting exercise, sync its sets
                    mergeWorkoutSessionSetsFromServer(exercise.id)
                },
                delete = workoutDao::deleteSessionExercise,
                shouldUpdate = { local, remote ->
                    local.name != remote.name || 
                    local.orderIndex != remote.orderIndex ||
                    local.isCompleted != remote.isCompleted ||
                    local.notes != remote.notes
                },
                update = { local, remote ->
                    workoutDao.updateSessionExercise(
                        local.copy(
                            name = remote.name,
                            orderIndex = remote.orderIndex,
                            isCompleted = remote.isCompleted,
                            startTime = remote.startTime?.let { java.time.Instant.parse(it).toEpochMilli() },
                            endTime = remote.endTime?.let { java.time.Instant.parse(it).toEpochMilli() },
                            restTimeBetweenSets = remote.restTimeBetweenSets,
                            notes = remote.notes,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            )
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error merging workout session exercises from server for session $sessionId", e)
        }
    }

    private suspend fun mergeWorkoutSessionSetsFromServer(exerciseId: String) {
        try {
            val localSets = workoutDao.getSessionSets(exerciseId)
            val supabaseSets = supabaseClient.from("workout_session_sets")
                .select { filter { eq("exercise_id", exerciseId) } }
                .decodeList<SupabaseWorkoutSessionSet>()

            performDataMerge(
                local = localSets,
                remote = supabaseSets,
                getId = { it.id },
                getRemoteId = { it.id },
                createLocal = { remote ->
                    WorkoutSessionSet(
                        id = remote.id!!,
                        exerciseId = remote.exerciseId!!,
                        setNumber = remote.setNumber,
                        targetWeightKg = remote.targetWeightKg,
                        actualWeightKg = remote.actualWeightKg,
                        targetReps = remote.targetReps,
                        actualReps = remote.actualReps,
                        isCompleted = remote.isCompleted,
                        completedAt = remote.completedAt?.let { java.time.Instant.parse(it).toEpochMilli() },
                        restTimeMs = remote.restTimeMs,
                        rpe = remote.rpe,
                        notes = remote.notes,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        syncedWithServer = true,
                        pendingSync = false
                    )
                },
                insert = workoutDao::insertSessionSet,
                delete = workoutDao::deleteSessionSet,
                shouldUpdate = { local, remote ->
                    local.setNumber != remote.setNumber ||
                    local.targetWeightKg != remote.targetWeightKg ||
                    local.actualWeightKg != remote.actualWeightKg ||
                    local.targetReps != remote.targetReps ||
                    local.actualReps != remote.actualReps ||
                    local.isCompleted != remote.isCompleted ||
                    local.rpe != remote.rpe ||
                    local.notes != remote.notes
                },
                update = { local, remote ->
                    workoutDao.updateSessionSet(
                        local.copy(
                            setNumber = remote.setNumber,
                            targetWeightKg = remote.targetWeightKg,
                            actualWeightKg = remote.actualWeightKg,
                            targetReps = remote.targetReps,
                            actualReps = remote.actualReps,
                            isCompleted = remote.isCompleted,
                            completedAt = remote.completedAt?.let { java.time.Instant.parse(it).toEpochMilli() },
                            restTimeMs = remote.restTimeMs,
                            rpe = remote.rpe,
                            notes = remote.notes,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            )
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error merging workout session sets from server for exercise $exerciseId", e)
        }
    }

    // ==========================
    // METODI PER DOWNLOAD INIZIALE
    // ==========================
    
    /**
     * Download iniziale delle workout cards da Supabase - chiamato solo al primo accesso
     */
    suspend fun downloadWorkoutCardsInitial() = withContext(Dispatchers.IO) {
        try {
            currentUserId ?: return@withContext
            
            Log.d("WorkoutRepository", "Starting initial download of workout data from Supabase")
            
            // Prima carica le modifiche locali pendenti
            syncUnsyncedData()
            
            // Poi scarica e mergia i dati da Supabase
            mergeCardsFromServerInitial()
            
            // Scarica esercizi e set per tutte le workout cards esistenti
            val allCards = getAllWorkoutCards().firstOrNull() ?: emptyList()
            allCards.forEach { card ->
                downloadWorkoutDataForCardInitial(card.id)
            }
            
            // Infine scarica le sessioni di workout
            mergeWorkoutSessionsFromServer()
            
            Log.d("WorkoutRepository", "Initial download of workout data completed")
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error during initial download of workout data", e)
        }
    }

    /**
     * Download iniziale degli esercizi per una card - chiamato solo al primo accesso
     */
    private suspend fun downloadExercisesForCardInitial(cardId: String) {
        try {
            currentUserId ?: return
            
            // Upload pending local changes first
            syncUnsyncedExercises(cardId)
            
            // Download and merge from server
            mergeExercisesFromServer(cardId)
            
            Log.d("WorkoutRepository", "Initial download completed for exercises in card $cardId")
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error during initial download of exercises for card $cardId", e)
        }
    }

    /**
     * Download iniziale dei set per un esercizio - chiamato solo al primo accesso
     */
    private suspend fun downloadSetsForExerciseInitial(exerciseId: String) {
        try {
            currentUserId ?: return
            
            // Upload pending local changes first
            syncUnsyncedSets(exerciseId)
            
            // Download and merge from server
            mergeSetsFromServer(exerciseId)
            
            Log.d("WorkoutRepository", "Initial download completed for sets in exercise $exerciseId")
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error during initial download of sets for exercise $exerciseId", e)
        }
    }

    /**
     * Versione di mergeCardsFromServer per il download iniziale che scarica anche esercizi e set
     */
    private suspend fun mergeCardsFromServerInitial() {
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
                // DOWNLOAD INIZIALE: Scarica anche esercizi e set per la nuova card
                downloadWorkoutDataForCardInitial(card.id)
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

    /**
     * Download iniziale di esercizi e set per una workout card - chiamato solo al primo accesso
     */
    private suspend fun downloadWorkoutDataForCardInitial(cardId: String) {
        try {
            downloadExercisesForCardInitial(cardId)
            downloadAllSetsForCardInitial(cardId)
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error downloading workout data for card $cardId during initial sync", e)
        }
    }

    /**
     * Download iniziale di tutti i set per una workout card - chiamato solo al primo accesso
     */
    private suspend fun downloadAllSetsForCardInitial(cardId: String) {
        try {
            workoutDao.getExercisesForCardOnce(cardId).forEach { exercise ->
                downloadSetsForExerciseInitial(exercise.id)
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error downloading all sets for card $cardId during initial sync", e)
        }
    }
    
}
