package com.torto.trgym.database.dao

import androidx.room.*
import com.torto.trgym.database.dto.WorkoutCardDetail
import com.torto.trgym.database.dto.WorkoutExerciseDetail
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

    // Complex queries
    @Transaction
    @Query("SELECT * FROM workout_cards WHERE id = :cardId")
    suspend fun getWorkoutCardWithExercises(cardId: String): WorkoutCardDetail?

    @Transaction
    @Query("SELECT * FROM workout_exercises WHERE id = :exerciseId")
    suspend fun getExerciseWithSets(exerciseId: String): WorkoutExerciseDetail?

    @Query("SELECT COUNT(*) FROM workout_exercises WHERE workoutCardId = :cardId")
    suspend fun getExerciseCountForCard(cardId: String): Int

    // Sync management methods
    @Query("UPDATE workout_cards SET syncedWithServer = 1, pendingSync = 0 WHERE id = :cardId")
    suspend fun markWorkoutCardAsSynced(cardId: String)

    @Query("UPDATE workout_exercises SET syncedWithServer = 1, pendingSync = 0 WHERE id = :exerciseId")
    suspend fun markWorkoutExerciseAsSynced(exerciseId: String)

    @Query("UPDATE exercise_sets SET syncedWithServer = 1, pendingSync = 0 WHERE id = :setId")
    suspend fun markExerciseSetAsSynced(setId: String)

    @Query("SELECT * FROM workout_cards WHERE pendingSync = 1 OR syncedWithServer = 0")
    suspend fun getUnsyncedWorkoutCards(): List<WorkoutCard>

    @Query("SELECT * FROM workout_exercises WHERE pendingSync = 1 OR syncedWithServer = 0")
    suspend fun getUnsyncedWorkoutExercises(): List<WorkoutExercise>

    @Query("SELECT * FROM exercise_sets WHERE pendingSync = 1 OR syncedWithServer = 0")
    suspend fun getUnsyncedExerciseSets(): List<ExerciseSet>
}