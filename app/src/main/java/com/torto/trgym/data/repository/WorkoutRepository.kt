package com.torto.trgym.data.repository

import android.util.Log
import com.torto.trgym.database.dao.WorkoutDao
import com.torto.trgym.database.dto.WorkoutExerciseDetail
import com.torto.trgym.database.dto.WorkoutCardDetail
import com.torto.trgym.database.dto.SupabaseWorkoutCard
import com.torto.trgym.database.dto.SupabaseWorkoutExercise
import com.torto.trgym.database.dto.SupabaseExerciseSet
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

    // WorkoutCard operations
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
                orderIndex = index
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
                        orderIndex = supabaseExercise.orderIndex
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
            // Upload exercise
            val supabaseExercise = SupabaseWorkoutExercise(
                id = exercise.id,
                workoutCardId = exercise.workoutCardId,
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
}