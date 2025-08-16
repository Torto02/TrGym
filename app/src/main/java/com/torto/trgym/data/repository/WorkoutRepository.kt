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

    fun getAllWorkoutCards(): Flow<List<WorkoutCard>> = workoutDao.getAllWorkoutCards()

    suspend fun getWorkoutCard(cardId: String): WorkoutCard? = workoutDao.getWorkoutCard(cardId)

    suspend fun insertWorkoutCard(workoutCard: WorkoutCard) = withContext(Dispatchers.IO) {
        // Insert locally first with pendingSync flag
        val cardToInsert = workoutCard.copy(pendingSync = true, syncedWithServer = false)
        workoutDao.insertWorkoutCard(cardToInsert)

        // Upload to Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                val supabaseCard = SupabaseWorkoutCard(
                    id = workoutCard.id,
                    userId = userId,
                    name = workoutCard.name,
                    description = workoutCard.description
                )
                supabaseClient.from("workout_cards").upsert(supabaseCard)
                // Mark as synced if successful
                workoutDao.markWorkoutCardAsSynced(workoutCard.id)
                Log.d("WorkoutRepository", "Uploaded new workout card to Supabase: ${workoutCard.id}")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not upload workout card to Supabase (offline?), will sync later", e)
            // Keep pendingSync = true for later synchronization
        }
    }

    suspend fun updateWorkoutCard(workoutCard: WorkoutCard) = withContext(Dispatchers.IO) {
        // Update locally first
        workoutDao.updateWorkoutCard(workoutCard)

        // Upload to Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                val supabaseCard = SupabaseWorkoutCard(
                    id = workoutCard.id,
                    userId = userId,
                    name = workoutCard.name,
                    description = workoutCard.description
                )
                supabaseClient.from("workout_cards").upsert(supabaseCard)
                Log.d("WorkoutRepository", "Updated workout card on Supabase: ${workoutCard.id}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error updating workout card on Supabase", e)
            // Don't fail the local operation if Supabase upload fails
        }
    }

    suspend fun deleteWorkoutCard(workoutCard: WorkoutCard) = withContext(Dispatchers.IO) {
        // Delete locally first
        workoutDao.deleteWorkoutCard(workoutCard)

        // Delete from Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                supabaseClient.from("workout_cards").delete {
                    filter {
                        eq("id", workoutCard.id)
                    }
                }
                Log.d("WorkoutRepository", "Deleted workout card from Supabase: ${workoutCard.id}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error deleting workout card from Supabase", e)
            // Don't fail the local operation if Supabase delete fails
        }
    }

    suspend fun getWorkoutCardWithExercises(cardId: String): WorkoutCardDetail? =
        workoutDao.getWorkoutCardWithExercises(cardId)

    suspend fun getExerciseCountForCard(cardId: String): Int =
        workoutDao.getExerciseCountForCard(cardId)

    // WorkoutExercise operations
    fun getExercisesForCard(cardId: String): Flow<List<WorkoutExercise>> =
        workoutDao.getExercisesForCard(cardId)


    // Aggiungi questi metodi al WorkoutRepository

    /**
     * Sincronizza gli esercizi per una specifica workout card
     */
    suspend fun syncExercisesForCard(cardId: String) = withContext(Dispatchers.IO) {
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext

            // 1. Upload degli esercizi locali non sincronizzati
            val unsyncedExercises = workoutDao.getUnsyncedWorkoutExercisesForCard(cardId)
            unsyncedExercises.forEach { exercise ->
                try {
                    val supabaseExercise = SupabaseWorkoutExercise(
                        id = exercise.id,
                        workoutCardId = exercise.workoutCardId,
                        exerciseId = exercise.exerciseId,
                        name = exercise.name,
                        orderIndex = exercise.orderIndex
                    )
                    supabaseClient.from("exercises").upsert(supabaseExercise)
                    workoutDao.markWorkoutExerciseAsSynced(exercise.id)
                } catch (e: Exception) {
                    Log.w("WorkoutRepository", "Could not sync exercise ${exercise.id}", e)
                }
            }

            // 2. Download degli esercizi da Supabase
            val supabaseExercises = supabaseClient
                .from("exercises")
                .select {
                    filter {
                        eq("workout_card_id", cardId)
                    }
                }
                .decodeList<SupabaseWorkoutExercise>()

            // 3. Merge intelligente
            val localExercises = workoutDao.getExercisesForCardOnce(cardId)
            performExercisesMerge(localExercises, supabaseExercises, cardId)

            Log.d("WorkoutRepository", "Synced exercises for card: $cardId")
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error syncing exercises for card $cardId", e)
        }
    }

    private suspend fun performExercisesMerge(
        localExercises: List<WorkoutExercise>,
        supabaseExercises: List<SupabaseWorkoutExercise>,
        cardId: String
    ) {
        val localIds = localExercises.map { it.id }.toSet()
        val supabaseIds = supabaseExercises.mapNotNull { it.id }.toSet()

        // Aggiungi esercizi da Supabase che non esistono localmente
        val toAddLocally = supabaseExercises.filter { it.id !in localIds }
        toAddLocally.forEach { supabaseExercise ->
            supabaseExercise.id?.let { id ->
                val localExercise = WorkoutExercise(
                    id = id,
                    workoutCardId = supabaseExercise.workoutCardId,
                    exerciseId = supabaseExercise.exerciseId,
                    name = supabaseExercise.name,
                    orderIndex = supabaseExercise.orderIndex,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncedWithServer = true,
                    pendingSync = false
                )
                workoutDao.insertWorkoutExercise(localExercise)

                // Sincronizza anche i set per questo esercizio
                syncExerciseSetsForExercise(id)
            }
        }

        // Rimuovi esercizi locali sincronizzati che non esistono più su server
        val toRemoveLocally = localIds - supabaseIds
        toRemoveLocally.forEach { exerciseId ->
            val localExercise = localExercises.find { it.id == exerciseId }
            if (localExercise?.syncedWithServer == true && localExercise.pendingSync == false) {
                workoutDao.deleteWorkoutExercise(localExercise)
                Log.d("WorkoutRepository", "Removed synced exercise $exerciseId that was deleted on server")
            }
        }

        // Aggiorna esercizi esistenti se necessario (controlla timestamp o altri campi)
        val toUpdate = supabaseExercises.filter { it.id in localIds }
        toUpdate.forEach { supabaseExercise ->
            supabaseExercise.id?.let { id ->
                val localExercise = localExercises.find { it.id == id }
                if (localExercise != null && localExercise.syncedWithServer && !localExercise.pendingSync) {
                    // Aggiorna solo se i dati sono diversi
                    if (localExercise.name != supabaseExercise.name ||
                        localExercise.orderIndex != supabaseExercise.orderIndex) {
                        val updatedExercise = localExercise.copy(
                            name = supabaseExercise.name,
                            orderIndex = supabaseExercise.orderIndex,
                            updatedAt = System.currentTimeMillis()
                        )
                        workoutDao.updateWorkoutExercise(updatedExercise)
                    }
                }
            }
        }
    }

    /**
     * Metodo per ottenere esercizi con sincronizzazione automatica
     */
    fun getExercisesForCardWithSync(cardId: String): Flow<List<WorkoutExercise>> = flow {
        // Prima sincronizza
        syncExercisesForCard(cardId)
        // Poi emetti il Flow dal database locale aggiornato
        emitAll(workoutDao.getExercisesForCard(cardId))
    }

    // Versione ottimizzata che sincronizza solo quando necessario

    /**
     * Metodo per ottenere set con sincronizzazione condizionale
     */
    fun getSetsForExerciseWithSmartSync(exerciseId: String): Flow<List<ExerciseSet>> = flow {
        try {
            // Controlla se ci sono modifiche non sincronizzate localmente
            val hasUnsyncedChanges = workoutDao.getUnsyncedSetsForExercise(exerciseId).isNotEmpty()

            // Controlla l'ultima sincronizzazione (potresti aggiungere un timestamp nel database)
            val lastSyncTime = getLastSyncTimeForExercise(exerciseId)
            val needsSync = hasUnsyncedChanges ||
                    (System.currentTimeMillis() - lastSyncTime) > SYNC_INTERVAL_MS

            if (needsSync) {
                syncSetsForExercise(exerciseId)
                updateLastSyncTimeForExercise(exerciseId)
            }

            // Emetti il Flow dal database locale
            emitAll(workoutDao.getSetsForExercise(exerciseId))
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error in smart sync for exercise $exerciseId", e)
            // In caso di errore, restituisci comunque i dati locali
            emitAll(workoutDao.getSetsForExercise(exerciseId))
        }
    }

    private suspend fun getLastSyncTimeForExercise(exerciseId: String): Long {
        // Implementa il recupero del timestamp dell'ultima sincronizzazione
        // Potresti aggiungere una tabella o campo per tracciare questo
        return 0L // Placeholder
    }

    private suspend fun updateLastSyncTimeForExercise(exerciseId: String) {
        // Implementa l'aggiornamento del timestamp dell'ultima sincronizzazione
    }

    companion object {
        private const val SYNC_INTERVAL_MS = 30_000L // 30 secondi
    }

    // Aggiungi questi metodi al WorkoutRepository

    /**
     * Sincronizza i set per un specifico esercizio
     */
    suspend fun syncSetsForExercise(exerciseId: String) = withContext(Dispatchers.IO) {
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext

            // 1. Upload dei set locali non sincronizzati
            val unsyncedSets = workoutDao.getUnsyncedSetsForExercise(exerciseId)
            unsyncedSets.forEach { set ->
                try {
                    val supabaseSet = SupabaseExerciseSet(
                        id = set.id,
                        exerciseId = set.exerciseId,
                        setNumber = set.setNumber,
                        weightKg = set.weightKg,
                        reps = set.reps
                    )
                    supabaseClient.from("exercise_sets").upsert(supabaseSet)
                    workoutDao.markExerciseSetAsSynced(set.id)
                } catch (e: Exception) {
                    Log.w("WorkoutRepository", "Could not sync set ${set.id}", e)
                }
            }

            // 2. Download dei set da Supabase
            val supabaseSets = supabaseClient
                .from("exercise_sets")
                .select {
                    filter {
                        eq("exercise_id", exerciseId)
                    }
                }
                .decodeList<SupabaseExerciseSet>()

            // 3. Merge intelligente
            val localSets = workoutDao.getSetsForExerciseOnce(exerciseId)
            performSetsMerge(localSets, supabaseSets, exerciseId)

            Log.d("WorkoutRepository", "Synced sets for exercise: $exerciseId")
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error syncing sets for exercise $exerciseId", e)
        }
    }

    private suspend fun performSetsMerge(
        localSets: List<ExerciseSet>,
        supabaseSets: List<SupabaseExerciseSet>,
        exerciseId: String
    ) {
        val localIds = localSets.map { it.id }.toSet()
        val supabaseIds = supabaseSets.mapNotNull { it.id }.toSet()

        // Aggiungi set da Supabase che non esistono localmente
        val toAddLocally = supabaseSets.filter { it.id !in localIds }
        toAddLocally.forEach { supabaseSet ->
            supabaseSet.id?.let { id ->
                val localSet = ExerciseSet(
                    id = id,
                    exerciseId = supabaseSet.exerciseId,
                    setNumber = supabaseSet.setNumber,
                    weightKg = supabaseSet.weightKg,
                    reps = supabaseSet.reps,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncedWithServer = true,
                    pendingSync = false
                )
                workoutDao.insertExerciseSet(localSet)
            }
        }

        // Rimuovi set locali sincronizzati che non esistono più su server
        val toRemoveLocally = localIds - supabaseIds
        toRemoveLocally.forEach { setId ->
            val localSet = localSets.find { it.id == setId }
            if (localSet?.syncedWithServer == true && localSet.pendingSync == false) {
                workoutDao.deleteExerciseSet(localSet)
                Log.d("WorkoutRepository", "Removed synced set $setId that was deleted on server")
            }
        }

        // Aggiorna set esistenti se necessario
        val toUpdate = supabaseSets.filter { it.id in localIds }
        toUpdate.forEach { supabaseSet ->
            supabaseSet.id?.let { id ->
                val localSet = localSets.find { it.id == id }
                if (localSet != null && localSet.syncedWithServer && !localSet.pendingSync) {
                    // Aggiorna solo se i dati sono diversi
                    if (localSet.setNumber != supabaseSet.setNumber ||
                        localSet.weightKg != supabaseSet.weightKg ||
                        localSet.reps != supabaseSet.reps) {
                        val updatedSet = localSet.copy(
                            setNumber = supabaseSet.setNumber,
                            weightKg = supabaseSet.weightKg,
                            reps = supabaseSet.reps,
                            updatedAt = System.currentTimeMillis()
                        )
                        workoutDao.updateExerciseSet(updatedSet)
                    }
                }
            }
        }

        // Riordina i set per setNumber dopo il merge
        val allLocalSets = workoutDao.getSetsForExerciseOnce(exerciseId)
        val sortedSets = allLocalSets.sortedBy { it.setNumber }
        sortedSets.forEachIndexed { index, set ->
            val correctSetNumber = index + 1
            if (set.setNumber != correctSetNumber) {
                val correctedSet = set.copy(
                    setNumber = correctSetNumber,
                    updatedAt = System.currentTimeMillis()
                )
                workoutDao.updateExerciseSet(correctedSet)
            }
        }
    }

    /**
     * Metodo per ottenere set con sincronizzazione automatica
     */
    fun getSetsForExerciseWithSync(exerciseId: String): Flow<List<ExerciseSet>> = flow {
        // Prima sincronizza
        syncSetsForExercise(exerciseId)
        // Poi emetti il Flow dal database locale aggiornato
        emitAll(workoutDao.getSetsForExercise(exerciseId))
    }

    /**
     * Sincronizza tutti i set per tutti gli esercizi di una workout card
     */
    suspend fun syncAllSetsForCard(cardId: String) = withContext(Dispatchers.IO) {
        try {
            val exercises = workoutDao.getExercisesForCardOnce(cardId)
            exercises.forEach { exercise ->
                syncSetsForExercise(exercise.id)
            }
            Log.d("WorkoutRepository", "Synced all sets for card: $cardId")
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error syncing all sets for card $cardId", e)
        }
    }


    fun getSessionExercisesWithSets(sessionId: String): Flow<List<WorkoutSessionExerciseDetail>> =
        workoutDao.getSessionExercisesWithSetsFlow(sessionId)

    suspend fun getWorkoutExercise(exerciseId: String): WorkoutExercise? =
        workoutDao.getWorkoutExercise(exerciseId)

    suspend fun insertWorkoutExercise(exercise: WorkoutExercise) = withContext(Dispatchers.IO) {
        // Insert locally first with pendingSync flag
        val exerciseToInsert = exercise.copy(pendingSync = true, syncedWithServer = false)
        workoutDao.insertWorkoutExercise(exerciseToInsert)

        // Upload to Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                val supabaseExercise = SupabaseWorkoutExercise(
                    id = exercise.id,
                    workoutCardId = exercise.workoutCardId,
                    name = exercise.name,
                    orderIndex = exercise.orderIndex
                )
                supabaseClient.from("exercises").upsert(supabaseExercise)
                workoutDao.markWorkoutExerciseAsSynced(exercise.id)
                Log.d("WorkoutRepository", "Uploaded new exercise to Supabase: ${exercise.id}")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not upload exercise to Supabase (offline?), will sync later", e)
        }
    }

    suspend fun updateWorkoutExercise(exercise: WorkoutExercise) = withContext(Dispatchers.IO) {
        // Update locally first
        workoutDao.updateWorkoutExercise(exercise)

        // Upload to Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                val supabaseExercise = SupabaseWorkoutExercise(
                    id = exercise.id,
                    workoutCardId = exercise.workoutCardId,
                    name = exercise.name,
                    orderIndex = exercise.orderIndex
                )
                supabaseClient.from("exercises").upsert(supabaseExercise)
                Log.d("WorkoutRepository", "Updated exercise on Supabase: ${exercise.id}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error updating exercise on Supabase", e)
        }
    }

    suspend fun deleteWorkoutExercise(exercise: WorkoutExercise) = withContext(Dispatchers.IO) {
        // Delete locally first
        workoutDao.deleteWorkoutExercise(exercise)

        // Delete from Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                supabaseClient.from("exercises").delete {
                    filter {
                        eq("id", exercise.id)
                    }
                }
                Log.d("WorkoutRepository", "Deleted exercise from Supabase: ${exercise.id}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error deleting exercise from Supabase", e)
        }
    }

    suspend fun getExerciseWithSets(exerciseId: String): WorkoutExerciseDetail? =
        workoutDao.getExerciseWithSets(exerciseId)

    // ExerciseSet operations
    fun getSetsForExercise(exerciseId: String): Flow<List<ExerciseSet>> =
        workoutDao.getSetsForExercise(exerciseId)

    suspend fun insertExerciseSet(set: ExerciseSet) = withContext(Dispatchers.IO) {
        // Insert locally first
        workoutDao.insertExerciseSet(set)

        // Upload to Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                val supabaseSet = SupabaseExerciseSet(
                    id = set.id,
                    exerciseId = set.exerciseId,
                    setNumber = set.setNumber,
                    weightKg = set.weightKg,
                    reps = set.reps
                )
                supabaseClient.from("exercise_sets").upsert(supabaseSet)
                Log.d("WorkoutRepository", "Uploaded new exercise set to Supabase: ${set.id}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error uploading exercise set to Supabase", e)
        }
    }

    suspend fun insertExerciseSets(sets: List<ExerciseSet>) = withContext(Dispatchers.IO) {
        // Insert locally first
        workoutDao.insertExerciseSets(sets)

        // Upload to Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                val supabaseSets = sets.map { set ->
                    SupabaseExerciseSet(
                        id = set.id,
                        exerciseId = set.exerciseId,
                        setNumber = set.setNumber,
                        weightKg = set.weightKg,
                        reps = set.reps
                    )
                }
                supabaseClient.from("exercise_sets").upsert(supabaseSets)
                Log.d("WorkoutRepository", "Uploaded ${sets.size} exercise sets to Supabase")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error uploading exercise sets to Supabase", e)
        }
    }

    suspend fun updateExerciseSet(set: ExerciseSet) = withContext(Dispatchers.IO) {
        // Update locally first with pendingSync flag and updated timestamp
        val setToUpdate = set.copy(
            updatedAt = System.currentTimeMillis(),
            pendingSync = true,
            syncedWithServer = false
        )
        workoutDao.updateExerciseSet(setToUpdate)

        // Upload to Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                val supabaseSet = SupabaseExerciseSet(
                    id = set.id,
                    exerciseId = set.exerciseId,
                    setNumber = set.setNumber,
                    weightKg = set.weightKg,
                    reps = set.reps
                )
                supabaseClient.from("exercise_sets").upsert(supabaseSet)
                workoutDao.markExerciseSetAsSynced(set.id)
                Log.d("WorkoutRepository", "Updated exercise set on Supabase: ${set.id}")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not update exercise set on Supabase (offline?), will sync later", e)
        }
    }

    suspend fun deleteExerciseSet(set: ExerciseSet) = withContext(Dispatchers.IO) {
        // Delete locally first
        workoutDao.deleteExerciseSet(set)

        // Delete from Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                supabaseClient.from("exercise_sets").delete {
                    filter {
                        eq("id", set.id)
                    }
                }
                Log.d("WorkoutRepository", "Deleted exercise set from Supabase: ${set.id}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error deleting exercise set from Supabase", e)
        }
    }

    suspend fun deleteSetsForExercise(exerciseId: String) = workoutDao.deleteSetsForExercise(exerciseId)

    // Helper methods
    suspend fun createWorkoutCardWithExercises(
        name: String,
        description: String?,
        exercises: List<Pair<String, Int>>
    ): String {
        val workoutCard = WorkoutCard(
            name = name,
            description = description
        )

        insertWorkoutCard(workoutCard)

        exercises.forEachIndexed { index, (exerciseName, sets) ->
            val exercise = WorkoutExercise(
                workoutCardId = workoutCard.id,
                name = exerciseName,
                orderIndex = index,
                updatedAt = System.currentTimeMillis()
            )
            insertWorkoutExercise(exercise)

            // Create default empty sets
            val exerciseSets = (1..sets).map { setNumber ->
                ExerciseSet(
                    exerciseId = exercise.id,
                    setNumber = setNumber
                )
            }
            insertExerciseSets(exerciseSets)
        }

        return workoutCard.id
    }

    // ==========================
    // SUPABASE SYNC OPERATIONS
    // ==========================

    /**
     * Synchronizes workout cards with Supabase using intelligent merge strategy
     */
    suspend fun syncWorkoutCards() = withContext(Dispatchers.IO) {
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext

            // Step 1: Upload unsynced local changes to Supabase
            syncUnsyncedData()

            // Step 2: Get data from both sources
            val localCards = getAllWorkoutCards().firstOrNull() ?: emptyList()
            val supabaseCards = supabaseClient
                .from("workout_cards")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<SupabaseWorkoutCard>()

            // Step 3: Perform intelligent merge
            performIntelligentMerge(localCards, supabaseCards)

            Log.d("WorkoutRepository", "Smart sync completed")
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error during smart sync of workout cards", e)
        }
    }

    private suspend fun syncUnsyncedData() {
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return

            // Sync workout cards
            val unsyncedCards = workoutDao.getUnsyncedWorkoutCards()
            unsyncedCards.forEach { card ->
                try {
                    val supabaseCard = SupabaseWorkoutCard(
                        id = card.id,
                        userId = userId,
                        name = card.name,
                        description = card.description
                    )
                    supabaseClient.from("workout_cards").upsert(supabaseCard)
                    workoutDao.markWorkoutCardAsSynced(card.id)
                    Log.d("WorkoutRepository", "Synced local workout card ${card.id} to Supabase")
                } catch (e: Exception) {
                    Log.w("WorkoutRepository", "Could not sync workout card ${card.id}", e)
                }
            }

            // Sync workout exercises
            val unsyncedExercises = workoutDao.getUnsyncedWorkoutExercises()
            unsyncedExercises.forEach { exercise ->
                try {
                    val supabaseExercise = SupabaseWorkoutExercise(
                        id = exercise.id,
                        workoutCardId = exercise.workoutCardId,
                        name = exercise.name,
                        orderIndex = exercise.orderIndex
                    )
                    supabaseClient.from("exercises").upsert(supabaseExercise)
                    workoutDao.markWorkoutExerciseAsSynced(exercise.id)
                    Log.d("WorkoutRepository", "Synced local exercise ${exercise.id} to Supabase")
                } catch (e: Exception) {
                    Log.w("WorkoutRepository", "Could not sync exercise ${exercise.id}", e)
                }
            }

            // Sync exercise sets
            val unsyncedSets = workoutDao.getUnsyncedExerciseSets()
            unsyncedSets.forEach { set ->
                try {
                    val supabaseSet = SupabaseExerciseSet(
                        id = set.id,
                        exerciseId = set.exerciseId,
                        setNumber = set.setNumber,
                        weightKg = set.weightKg,
                        reps = set.reps
                    )
                    supabaseClient.from("exercise_sets").upsert(supabaseSet)
                    workoutDao.markExerciseSetAsSynced(set.id)
                    Log.d("WorkoutRepository", "Synced local exercise set ${set.id} to Supabase")
                } catch (e: Exception) {
                    Log.w("WorkoutRepository", "Could not sync exercise set ${set.id}", e)
                }
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error syncing unsynced data", e)
        }
    }

    private suspend fun performIntelligentMerge(localCards: List<WorkoutCard>, supabaseCards: List<SupabaseWorkoutCard>) {
        val localIds = localCards.map { it.id }.toSet()
        val supabaseIds = supabaseCards.mapNotNull { it.id }.toSet()

        // Add cards from Supabase that don't exist locally
        val toAddLocally = supabaseCards.filter { it.id !in localIds }
        toAddLocally.forEach { supabaseCard ->
            supabaseCard.id?.let { id ->
                val localCard = WorkoutCard(
                    id = id,
                    name = supabaseCard.name,
                    description = supabaseCard.description,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    syncedWithServer = true,
                    pendingSync = false
                )
                workoutDao.insertWorkoutCard(localCard)

                // Sync exercises and sets for this card
                syncWorkoutExercisesForCard(id)
            }
        }

        // Remove local cards that are synced but no longer exist on server
        val toRemoveLocally = localIds - supabaseIds
        toRemoveLocally.forEach { cardId ->
            val localCard = localCards.find { it.id == cardId }
            if (localCard?.syncedWithServer == true && localCard.pendingSync == false) {
                workoutDao.deleteWorkoutCard(localCard)
                Log.d("WorkoutRepository", "Removed synced workout card $cardId that was deleted on server")
            }
        }

        Log.d("WorkoutRepository", "Merge completed: added ${toAddLocally.size}, removed candidates: ${toRemoveLocally.size}")
    }

    private suspend fun syncWorkoutExercisesForCard(workoutCardId: String) {
        try {
            // Get exercises from Supabase for this workout card
            val supabaseExercises = supabaseClient
                .from("exercises")
                .select {
                    filter {
                        eq("workout_card_id", workoutCardId)
                    }
                }
                .decodeList<SupabaseWorkoutExercise>()

            // Convert and insert locally
            val localExercises = supabaseExercises.mapNotNull { supabaseExercise ->
                supabaseExercise.id?.let { id ->
                    WorkoutExercise(
                        id = id,
                        workoutCardId = supabaseExercise.workoutCardId,
                        name = supabaseExercise.name,
                        orderIndex = supabaseExercise.orderIndex,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }

            localExercises.forEach { exercise ->
                // Use direct DAO insert to avoid triggering Supabase upload again
                workoutDao.insertWorkoutExercise(exercise)
                // Sync exercise sets for each exercise
                syncExerciseSetsForExercise(exercise.id)
            }

        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error syncing exercises for card $workoutCardId", e)
        }
    }

    private suspend fun syncExerciseSetsForExercise(exerciseId: String) {
        try {
            // Get exercise sets from Supabase
            val supabaseSets = supabaseClient
                .from("exercise_sets")
                .select {
                    filter {
                        eq("exercise_id", exerciseId)
                    }
                }
                .decodeList<SupabaseExerciseSet>()

            // Convert and insert locally
            val localSets = supabaseSets.mapNotNull { supabaseSet ->
                supabaseSet.id?.let { id ->
                    ExerciseSet(
                        id = id,
                        exerciseId = supabaseSet.exerciseId,
                        setNumber = supabaseSet.setNumber,
                        weightKg = supabaseSet.weightKg,
                        reps = supabaseSet.reps
                    )
                }
            }

            // Use direct DAO insert to avoid triggering Supabase upload again
            workoutDao.insertExerciseSets(localSets)

        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error syncing sets for exercise $exerciseId", e)
        }
    }

    /**
     * Uploads local workout cards to Supabase
     */
    suspend fun uploadWorkoutCardsToSupabase() = withContext(Dispatchers.IO) {
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext

            val localCards = getAllWorkoutCards().firstOrNull() ?: return@withContext

            localCards.forEach { card ->
                uploadWorkoutCardToSupabase(card, userId)
            }

            Log.d("WorkoutRepository", "Uploaded ${localCards.size} workout cards to Supabase")
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error uploading workout cards", e)
        }
    }

    private suspend fun uploadWorkoutCardToSupabase(card: WorkoutCard, userId: String) {
        try {
            // Upload workout card
            val supabaseCard = SupabaseWorkoutCard(
                id = card.id,
                userId = userId,
                name = card.name,
                description = card.description
            )

            supabaseClient
                .from("workout_cards")
                .upsert(supabaseCard)

            // Upload exercises for this card
            val exercises = getExercisesForCard(card.id).firstOrNull() ?: emptyList()
            exercises.forEach { exercise ->
                uploadWorkoutExerciseToSupabase(exercise)
            }

        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error uploading workout card ${card.id}", e)
        }
    }


    private suspend fun uploadWorkoutExerciseToSupabase(exercise: WorkoutExercise) {
        try {
            val supabaseExercise = SupabaseWorkoutExercise(
                id = exercise.id,
                workoutCardId = exercise.workoutCardId,
                exerciseId = exercise.exerciseId,  // Includi exerciseId
                name = exercise.name,
                orderIndex = exercise.orderIndex
            )

            supabaseClient
                .from("exercises")
                .upsert(supabaseExercise)

            // Upload sets for this exercise
            val sets = getSetsForExercise(exercise.id).firstOrNull() ?: emptyList()
            sets.forEach { set ->
                uploadExerciseSetToSupabase(set)
            }

        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error uploading exercise ${exercise.id}", e)
        }
    }

    private suspend fun uploadExerciseSetToSupabase(set: ExerciseSet) {
        try {
            val supabaseSet = SupabaseExerciseSet(
                id = set.id,
                exerciseId = set.exerciseId,
                setNumber = set.setNumber,
                weightKg = set.weightKg,
                reps = set.reps
            )

            supabaseClient
                .from("exercise_sets")
                .upsert(supabaseSet)

        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error uploading exercise set ${set.id}", e)
        }
    }

    /**
     * Deletes a workout card from both local database and Supabase
     */
    suspend fun deleteWorkoutCardWithSync(workoutCard: WorkoutCard) = withContext(Dispatchers.IO) {
        try {
            // Delete from local database
            deleteWorkoutCard(workoutCard)

            // Delete from Supabase
            supabaseClient
                .from("workout_cards")
                .delete {
                    filter {
                        eq("id", workoutCard.id)
                    }
                }

            Log.d("WorkoutRepository", "Deleted workout card ${workoutCard.id} from both local and Supabase")
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error deleting workout card with sync", e)
        }
    }

    /**
     * Updates workout card locally and syncs with Supabase
     */
    suspend fun updateWorkoutCardWithSync(workoutCard: WorkoutCard) = withContext(Dispatchers.IO) {
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@withContext

            // Update locally
            updateWorkoutCard(workoutCard)

            // Update in Supabase
            val supabaseCard = SupabaseWorkoutCard(
                id = workoutCard.id,
                userId = userId,
                name = workoutCard.name,
                description = workoutCard.description
            )

            supabaseClient
                .from("workout_cards")
                .upsert(supabaseCard)

            Log.d("WorkoutRepository", "Updated workout card ${workoutCard.id} with sync")
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error updating workout card with sync", e)
        }
    }


    suspend fun deleteExerciseSetAndReorder(set: ExerciseSet) = withContext(Dispatchers.IO) {
        // Local transactional delete + reorder
        workoutDao.deleteSetAndReorder(set)
        // Remote delete only for the removed set (no need to sync reordered numbers immediately)
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                supabaseClient.from("exercise_sets").delete {
                    filter { eq("id", set.id) }
                }
                Log.d("WorkoutRepository", "Deleted exercise set (with reorder) from Supabase: ${set.id}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error deleting exercise set from Supabase", e)
        }
    }

    // Workout Session methods
    suspend fun getActiveWorkoutSession(): WorkoutSession? = withContext(Dispatchers.IO) {
        workoutDao.getActiveWorkoutSession()
    }

    suspend fun insertWorkoutSession(session: WorkoutSession) = withContext(Dispatchers.IO) {
        // Insert locally first with pendingSync flag
        val sessionToInsert = session.copy(pendingSync = true, syncedWithServer = false)
        workoutDao.insertWorkoutSession(sessionToInsert)

        // Upload to Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
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
                supabaseClient.from("workout_sessions").upsert(supabaseSession)
                // Mark as synced if successful
                workoutDao.markWorkoutSessionAsSynced(session.id)
                Log.d("WorkoutRepository", "Uploaded new workout session to Supabase: ${session.id}")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not upload workout session to Supabase (offline?), will sync later", e)
            // Keep pendingSync = true for later synchronization
        }
    }

    suspend fun updateWorkoutSession(session: WorkoutSession) = withContext(Dispatchers.IO) {
        // Update locally first
        workoutDao.updateWorkoutSession(session)

        // Upload to Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
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
                supabaseClient.from("workout_sessions").upsert(supabaseSession)
                Log.d("WorkoutRepository", "Updated workout session on Supabase: ${session.id}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error updating workout session on Supabase", e)
            // Don't fail the local operation if Supabase upload fails
        }
    }

    suspend fun deleteWorkoutSession(session: WorkoutSession) = withContext(Dispatchers.IO) {
        // Delete locally first
        workoutDao.deleteWorkoutSession(session)

        // Delete from Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                supabaseClient.from("workout_sessions").delete {
                    filter {
                        eq("id", session.id)
                    }
                }
                Log.d("WorkoutRepository", "Deleted workout session from Supabase: ${session.id}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error deleting workout session from Supabase", e)
            // Don't fail the local operation if Supabase delete fails
        }
    }

    suspend fun getSessionExercises(sessionId: String): List<WorkoutSessionExercise> = withContext(Dispatchers.IO) {
        workoutDao.getSessionExercises(sessionId)
    }

    suspend fun insertSessionExercises(exercises: List<WorkoutSessionExercise>) = withContext(Dispatchers.IO) {
        // Insert locally first with pendingSync flag
        val exercisesToInsert = exercises.map { it.copy(pendingSync = true, syncedWithServer = false) }
        workoutDao.insertSessionExercises(exercisesToInsert)

        // Upload to Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
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
                // Mark all as synced if successful
                exercises.forEach { workoutDao.markSessionExerciseAsSynced(it.id) }
                Log.d("WorkoutRepository", "Uploaded ${exercises.size} session exercises to Supabase")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not upload session exercises to Supabase (offline?), will sync later", e)
            // Keep pendingSync = true for later synchronization
        }
    }

    suspend fun updateSessionExercise(exercise: WorkoutSessionExercise) = withContext(Dispatchers.IO) {
        // Update locally first
        workoutDao.updateSessionExercise(exercise)

        // Upload to Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
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
                supabaseClient.from("workout_session_exercises").upsert(supabaseExercise)
                Log.d("WorkoutRepository", "Updated session exercise on Supabase: ${exercise.id}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error updating session exercise on Supabase", e)
            // Don't fail the local operation if Supabase upload fails
        }
    }

    suspend fun getSessionSets(exerciseId: String): List<WorkoutSessionSet> = withContext(Dispatchers.IO) {
        workoutDao.getSessionSets(exerciseId)
    }

    suspend fun insertSessionSets(sets: List<WorkoutSessionSet>) = withContext(Dispatchers.IO) {
        // Insert locally first with pendingSync flag
        val setsToInsert = sets.map { it.copy(pendingSync = true, syncedWithServer = false) }
        workoutDao.insertSessionSets(setsToInsert)

        // Upload to Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
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
                // Mark all as synced if successful
                sets.forEach { workoutDao.markSessionSetAsSynced(it.id) }
                Log.d("WorkoutRepository", "Uploaded ${sets.size} session sets to Supabase")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not upload session sets to Supabase (offline?), will sync later", e)
            // Keep pendingSync = true for later synchronization
        }
    }

    suspend fun updateSessionSet(set: WorkoutSessionSet) = withContext(Dispatchers.IO) {
        // Update locally first
        workoutDao.updateSessionSet(set)

        // Upload to Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
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
                supabaseClient.from("workout_session_sets").upsert(supabaseSet)
                Log.d("WorkoutRepository", "Updated session set on Supabase: ${set.id}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error updating session set on Supabase", e)
            // Don't fail the local operation if Supabase upload fails
        }
    }

    suspend fun updateSessionSetCompletion(setId: String, isCompleted: Boolean) = withContext(Dispatchers.IO) {
        val completedAt = if (isCompleted) System.currentTimeMillis() else null
        workoutDao.updateSessionSetCompletion(setId, isCompleted, completedAt)

        // Upload to Supabase if user is authenticated
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                supabaseClient.from("workout_session_sets")
                    .update(mapOf(
                        "is_completed" to isCompleted,
                        "completed_at" to (completedAt?.let { java.time.Instant.ofEpochMilli(it).toString() })
                    )) {
                        filter {
                            eq("id", setId)
                        }
                    }
                Log.d("WorkoutRepository", "Updated session set completion on Supabase: $setId")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error updating session set completion on Supabase", e)
            // Don't fail the local operation if Supabase upload fails
        }
    }

    suspend fun getLatestWorkoutSessions(limit: Int = 10): List<WorkoutSession> = withContext(Dispatchers.IO) {
        workoutDao.getLatestWorkoutSessions(limit)
    }


    suspend fun insertSessionSet(set: WorkoutSessionSet) = withContext(Dispatchers.IO) {
        val setToInsert = set.copy(pendingSync = true, syncedWithServer = false)
        workoutDao.insertSessionSet(setToInsert)

        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
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
                supabaseClient.from("workout_session_sets").upsert(supabaseSet)
                workoutDao.markSessionSetAsSynced(set.id)
                Log.d("WorkoutRepository", "Uploaded session set to Supabase: ${set.id}")
            }
        } catch (e: Exception) {
            Log.w("WorkoutRepository", "Could not upload session set to Supabase (offline?), will sync later", e)
        }
    }

    suspend fun deleteSessionExercise(exercise: WorkoutSessionExercise) = withContext(Dispatchers.IO) {
        workoutDao.deleteSessionExerciseAndReorder(exercise)

        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                supabaseClient.from("workout_session_exercises").delete {
                    filter {
                        eq("id", exercise.id)
                    }
                }
                Log.d("WorkoutRepository", "Deleted session exercise from Supabase: ${exercise.id}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error deleting session exercise from Supabase", e)
        }
    }

    suspend fun deleteSessionSet(set: WorkoutSessionSet) = withContext(Dispatchers.IO) {
        workoutDao.deleteSessionSetAndReorder(set)

        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                supabaseClient.from("workout_session_sets").delete {
                    filter {
                        eq("id", set.id)
                    }
                }
                Log.d("WorkoutRepository", "Deleted session set from Supabase: ${set.id}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepository", "Error deleting session set from Supabase", e)
        }
    }

    suspend fun countWorkoutSessions(): Int = withContext(Dispatchers.IO) {
        workoutDao.countWorkoutSessions()
    }

}
