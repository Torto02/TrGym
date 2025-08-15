package com.torto.trgym.database.dao

import androidx.room.*
import com.torto.trgym.database.dto.WorkoutCardDetail
import com.torto.trgym.database.dto.WorkoutExerciseDetail
import com.torto.trgym.database.dto.WorkoutSessionExerciseDetail
import com.torto.trgym.database.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {

    // WorkoutCard queries
    @Query("SELECT * FROM workout_cards ORDER BY updatedAt DESC")
    fun getAllWorkoutCards(): Flow<List<WorkoutCard>>

    @Query("SELECT * FROM workout_cards WHERE id = :cardId")
    suspend fun getWorkoutCard(cardId: String): WorkoutCard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutCard(workoutCard: WorkoutCard): Long

    @Update
    suspend fun updateWorkoutCard(workoutCard: WorkoutCard)

    @Delete
    suspend fun deleteWorkoutCard(workoutCard: WorkoutCard)

    @Query("DELETE FROM workout_cards")
    suspend fun deleteAllWorkoutCards()

    // WorkoutExercise queries
    @Query("SELECT * FROM workout_exercises WHERE workoutCardId = :cardId ORDER BY orderIndex ASC")
    fun getExercisesForCard(cardId: String): Flow<List<WorkoutExercise>>

    @Query("SELECT * FROM workout_exercises WHERE id = :exerciseId")
    suspend fun getWorkoutExercise(exerciseId: String): WorkoutExercise?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercise(exercise: WorkoutExercise): Long

    @Update
    suspend fun updateWorkoutExercise(exercise: WorkoutExercise)

    @Delete
    suspend fun deleteWorkoutExercise(exercise: WorkoutExercise)

    @Query("DELETE FROM workout_exercises WHERE workoutCardId = :cardId")
    suspend fun deleteExercisesForCard(cardId: String)

    // ExerciseSet queries
    @Query("SELECT * FROM exercise_sets WHERE exerciseId = :exerciseId ORDER BY setNumber ASC")
    fun getSetsForExercise(exerciseId: String): Flow<List<ExerciseSet>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseSet(set: ExerciseSet): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseSets(sets: List<ExerciseSet>)

    @Update
    suspend fun updateExerciseSet(set: ExerciseSet)

    @Delete
    suspend fun deleteExerciseSet(set: ExerciseSet)

    @Query("DELETE FROM exercise_sets WHERE exerciseId = :exerciseId")
    suspend fun deleteSetsForExercise(exerciseId: String)

    // WorkoutSession queries
    @Query("SELECT * FROM workout_sessions WHERE isCompleted = 0 LIMIT 1")
    suspend fun getActiveWorkoutSession(): WorkoutSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSession(session: WorkoutSession): Long

    @Update
    suspend fun updateWorkoutSession(session: WorkoutSession)

    @Delete
    suspend fun deleteWorkoutSession(session: WorkoutSession)

    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC LIMIT :limit")
    suspend fun getLatestWorkoutSessions(limit: Int): List<WorkoutSession>

    // WorkoutSessionExercise queries
    @Query("SELECT * FROM workout_session_exercises WHERE workoutSessionId = :sessionId ORDER BY orderIndex ASC")
    suspend fun getSessionExercises(sessionId: String): List<WorkoutSessionExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionExercises(exercises: List<WorkoutSessionExercise>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionExercise(exercise: WorkoutSessionExercise): Long

    @Update
    suspend fun updateSessionExercise(exercise: WorkoutSessionExercise)

    // WorkoutSessionSet queries
    @Query("SELECT * FROM workout_session_sets WHERE exerciseId = :exerciseId ORDER BY setNumber ASC")
    suspend fun getSessionSets(exerciseId: String): List<WorkoutSessionSet>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionSets(sets: List<WorkoutSessionSet>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionSet(set: WorkoutSessionSet): Long

    @Update
    suspend fun updateSessionSet(set: WorkoutSessionSet)

    @Query("UPDATE workout_session_sets SET isCompleted = :isCompleted, completedAt = :completedAt WHERE id = :setId")
    suspend fun updateSessionSetCompletion(setId: String, isCompleted: Boolean, completedAt: Long?)

    @Delete
    suspend fun deleteSessionExercise(exercise: WorkoutSessionExercise)

    @Delete
    suspend fun deleteSessionSet(set: WorkoutSessionSet)

    @Query("UPDATE workout_session_exercises SET orderIndex = orderIndex - 1 WHERE workoutSessionId = :sessionId AND orderIndex > :deletedOrderIndex")
    suspend fun updateSessionExercisesOrderAfterDeletion(sessionId: String, deletedOrderIndex: Int)

    @Query("UPDATE workout_session_sets SET setNumber = setNumber - 1 WHERE exerciseId = :exerciseId AND setNumber > :deletedSetNumber")
    suspend fun decrementSessionSetNumbersAfterDeletion(exerciseId: String, deletedSetNumber: Int)

    @Transaction
    suspend fun deleteSessionSetAndReorder(set: WorkoutSessionSet) {
        deleteSessionSet(set)
        decrementSessionSetNumbersAfterDeletion(set.exerciseId, set.setNumber)
    }

    @Transaction
    suspend fun deleteSessionExerciseAndReorder(exercise: WorkoutSessionExercise) {
        deleteSessionExercise(exercise)
        updateSessionExercisesOrderAfterDeletion(exercise.workoutSessionId, exercise.orderIndex)
    }

    // Complex queries
    @Transaction
    @Query("SELECT * FROM workout_cards WHERE id = :cardId")
    suspend fun getWorkoutCardWithExercises(cardId: String): WorkoutCardDetail?

    @Transaction
    @Query("SELECT * FROM workout_exercises WHERE id = :exerciseId")
    suspend fun getExerciseWithSets(exerciseId: String): WorkoutExerciseDetail?

    @Transaction
    @Query("SELECT * FROM workout_session_exercises WHERE workoutSessionId = :sessionId ORDER BY orderIndex ASC")
    suspend fun getSessionExercisesWithSets(sessionId: String): List<WorkoutSessionExerciseDetail>

    @Transaction
    @Query("SELECT * FROM workout_session_exercises WHERE workoutSessionId = :sessionId ORDER BY orderIndex ASC")
    fun getSessionExercisesWithSetsFlow(sessionId: String): Flow<List<WorkoutSessionExerciseDetail>>

    @Query("SELECT COUNT(*) FROM workout_exercises WHERE workoutCardId = :cardId")
    fun getExerciseCountForCard(cardId: String): Int


    @Query("SELECT * FROM workout_cards WHERE pendingSync = 1")
    suspend fun getUnsyncedWorkoutCards(): List<WorkoutCard>

    @Query("SELECT * FROM workout_exercises WHERE pendingSync = 1")
    suspend fun getUnsyncedWorkoutExercises(): List<WorkoutExercise>

    @Query("SELECT * FROM exercise_sets WHERE pending_sync = 1")
    suspend fun getUnsyncedExerciseSets(): List<ExerciseSet>

    @Query("UPDATE workout_cards SET syncedWithServer = 1, pendingSync = 0 WHERE id = :cardId")
    suspend fun markWorkoutCardAsSynced(cardId: String)

    @Query("UPDATE workout_exercises SET syncedWithServer = 1, pendingSync = 0 WHERE id = :exerciseId")
    suspend fun markWorkoutExerciseAsSynced(exerciseId: String)

    @Query("UPDATE exercise_sets SET synced_with_server = 1, pending_sync = 0 WHERE id = :setId")
    suspend fun markExerciseSetAsSynced(setId: String)

    @Query("UPDATE workout_sessions SET syncedWithServer = 1, pendingSync = 0 WHERE id = :sessionId")
    suspend fun markWorkoutSessionAsSynced(sessionId: String)

    @Query("UPDATE workout_session_exercises SET syncedWithServer = 1, pendingSync = 0 WHERE id = :exerciseId")
    suspend fun markSessionExerciseAsSynced(exerciseId: String)

    @Query("UPDATE workout_session_sets SET syncedWithServer = 1, pendingSync = 0 WHERE id = :setId")
    suspend fun markSessionSetAsSynced(setId: String)

    @Query("UPDATE exercise_sets SET setNumber = setNumber - 1 WHERE exerciseId = :exerciseId AND setNumber > :deletedSetNumber")
    suspend fun decrementSetNumbersAfterDeletion(exerciseId: String, deletedSetNumber: Int)

    @Transaction
    suspend fun deleteSetAndReorder(set: ExerciseSet) {
        deleteExerciseSet(set)
        decrementSetNumbersAfterDeletion(set.exerciseId, set.setNumber)
    }

    @Transaction
    suspend fun deleteExerciseAndReorder(exercise: WorkoutExercise) {
        deleteWorkoutExercise(exercise)
        updateExercisesOrderAfterDeletion(exercise.workoutCardId, exercise.orderIndex)
    }

    @Query("UPDATE workout_exercises SET orderIndex = orderIndex - 1, updatedAt = :timestamp WHERE workoutCardId = :cardId AND orderIndex > :deletedOrderIndex")
    suspend fun updateExercisesOrderAfterDeletion(cardId: String, deletedOrderIndex: Int, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM workout_exercises WHERE workoutCardId = :cardId AND orderIndex > :orderIndex ORDER BY orderIndex ASC")
    suspend fun getExercisesWithHigherOrderIndex(cardId: String, orderIndex: Int): List<WorkoutExercise>

    @Query("SELECT COUNT(*) workoutCardId FROM workout_sessions WHERE isCompleted = 1")
    suspend fun countWorkoutSessions():Int
}