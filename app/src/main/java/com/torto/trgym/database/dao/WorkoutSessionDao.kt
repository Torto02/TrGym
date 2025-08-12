package com.torto.trgym.database.dao

import androidx.room.*
import com.torto.trgym.database.dto.CompleteWorkoutSession
import com.torto.trgym.database.dto.WorkoutSessionDetail
import com.torto.trgym.database.dto.WorkoutSessionExerciseDetail
import com.torto.trgym.database.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {

    // ========== WORKOUT SESSIONS ==========

    @Query("SELECT * FROM workout_sessions ORDER BY startTime DESC")
    fun getAllWorkoutSessions(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    suspend fun getWorkoutSession(sessionId: String): WorkoutSession?

    @Query("SELECT * FROM workout_sessions WHERE isCompleted = 0 ORDER BY startTime DESC LIMIT 1")
    suspend fun getCurrentActiveSession(): WorkoutSession?

    @Query("SELECT * FROM workout_sessions WHERE workoutCardId = :cardId ORDER BY startTime DESC")
    fun getSessionsForCard(cardId: String): Flow<List<WorkoutSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSession(session: WorkoutSession)

    @Update
    suspend fun updateWorkoutSession(session: WorkoutSession)

    @Delete
    suspend fun deleteWorkoutSession(session: WorkoutSession)

    @Query("UPDATE workout_sessions SET isCompleted = 1, endTime = :endTime, totalDurationMs = :duration, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun completeWorkoutSession(sessionId: String, endTime: Long, duration: Long, updatedAt: Long = System.currentTimeMillis())

    // ========== WORKOUT SESSION EXERCISES ==========

    @Query("SELECT * FROM workout_session_exercises WHERE workoutSessionId = :sessionId ORDER BY orderIndex ASC")
    fun getExercisesForSession(sessionId: String): Flow<List<WorkoutSessionExercise>>

    @Query("SELECT * FROM workout_session_exercises WHERE id = :exerciseId")
    suspend fun getWorkoutSessionExercise(exerciseId: String): WorkoutSessionExercise?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSessionExercise(exercise: WorkoutSessionExercise)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSessionExercises(exercises: List<WorkoutSessionExercise>)

    @Update
    suspend fun updateWorkoutSessionExercise(exercise: WorkoutSessionExercise)

    @Delete
    suspend fun deleteWorkoutSessionExercise(exercise: WorkoutSessionExercise)

    @Query("UPDATE workout_session_exercises SET isCompleted = 1, endTime = :endTime, updatedAt = :updatedAt WHERE id = :exerciseId")
    suspend fun completeWorkoutSessionExercise(exerciseId: String, endTime: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE workout_session_exercises SET orderIndex = :newIndex, updatedAt = :updatedAt WHERE id = :exerciseId")
    suspend fun updateExerciseOrder(exerciseId: String, newIndex: Int, updatedAt: Long = System.currentTimeMillis())

    // ========== WORKOUT SESSION SETS ==========

    @Query("SELECT * FROM workout_session_sets WHERE exerciseId = :exerciseId ORDER BY setNumber ASC")
    fun getSetsForSessionExercise(exerciseId: String): Flow<List<WorkoutSessionSet>>

    @Query("SELECT * FROM workout_session_sets WHERE id = :setId")
    suspend fun getWorkoutSessionSet(setId: String): WorkoutSessionSet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSessionSet(set: WorkoutSessionSet)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSessionSets(sets: List<WorkoutSessionSet>)

    @Update
    suspend fun updateWorkoutSessionSet(set: WorkoutSessionSet)

    @Delete
    suspend fun deleteWorkoutSessionSet(set: WorkoutSessionSet)

    @Query("UPDATE workout_session_sets SET isCompleted = 1, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :setId")
    suspend fun completeWorkoutSessionSet(setId: String, completedAt: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM workout_session_sets WHERE exerciseId = :exerciseId")
    suspend fun deleteSetsForSessionExercise(exerciseId: String)

    // ========== QUERIES COMPLESSE ==========

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    suspend fun getWorkoutSessionDetail(sessionId: String): WorkoutSessionDetail?

    @Transaction
    @Query("SELECT * FROM workout_session_exercises WHERE id = :exerciseId")
    suspend fun getWorkoutSessionExerciseDetail(exerciseId: String): WorkoutSessionExerciseDetail?

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :sessionId")
    suspend fun getCompleteWorkoutSession(sessionId: String): CompleteWorkoutSession?

    @Transaction
    @Query("SELECT * FROM workout_session_exercises WHERE workoutSessionId = :sessionId ORDER BY orderIndex ASC")
    fun getExercisesWithSetsForSession(sessionId: String): Flow<List<WorkoutSessionExerciseDetail>>

    // ========== STATISTICHE ==========

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE isCompleted = 1")
    suspend fun getTotalCompletedWorkouts(): Int

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE workoutCardId = :cardId AND isCompleted = 1")
    suspend fun getCompletedWorkoutsForCard(cardId: String): Int

    @Query("SELECT * FROM workout_sessions WHERE isCompleted = 1 ORDER BY startTime DESC LIMIT :limit")
    suspend fun getRecentCompletedWorkouts(limit: Int = 10): List<WorkoutSession>

    @Query("SELECT AVG(totalDurationMs) FROM workout_sessions WHERE isCompleted = 1 AND workoutCardId = :cardId")
    suspend fun getAverageWorkoutDuration(cardId: String): Long?

    // ========== SINCRONIZZAZIONE SUPABASE ==========

    @Query("SELECT * FROM workout_sessions WHERE pendingSync = 1")
    suspend fun getUnsyncedWorkoutSessions(): List<WorkoutSession>

    @Query("SELECT * FROM workout_session_exercises WHERE pendingSync = 1")
    suspend fun getUnsyncedWorkoutSessionExercises(): List<WorkoutSessionExercise>

    @Query("SELECT * FROM workout_session_sets WHERE pendingSync = 1")
    suspend fun getUnsyncedWorkoutSessionSets(): List<WorkoutSessionSet>

    @Query("UPDATE workout_sessions SET syncedWithServer = 1, pendingSync = 0 WHERE id = :sessionId")
    suspend fun markWorkoutSessionAsSynced(sessionId: String)

    @Query("UPDATE workout_session_exercises SET syncedWithServer = 1, pendingSync = 0 WHERE id = :exerciseId")
    suspend fun markWorkoutSessionExerciseAsSynced(exerciseId: String)

    @Query("UPDATE workout_session_sets SET syncedWithServer = 1, pendingSync = 0 WHERE id = :setId")
    suspend fun markWorkoutSessionSetAsSynced(setId: String)
}