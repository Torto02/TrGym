package com.torto.trgym.repository

import android.util.Log
import com.torto.trgym.database.dao.BodyMeasurementDao
import com.torto.trgym.database.dao.StarredExerciseDao
import com.torto.trgym.database.dao.WorkoutDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataCleanupRepository @Inject constructor(
    private val starredExerciseDao: StarredExerciseDao,
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val workoutDao: WorkoutDao
) {

    /**
     * Clears all user-specific data from Room database.
     * This should be called during logout to ensure user data is not left behind.
     */
    suspend fun clearAllUserData() = withContext(Dispatchers.IO) {
        try {
            Log.d("DataCleanupRepository", "Starting user data cleanup...")
            
            // Clear starred exercises
            starredExerciseDao.clearAllStarred()
            Log.d("DataCleanupRepository", "Cleared starred exercises")
            
            // Clear body measurements
            bodyMeasurementDao.clearAllMeasurements()
            Log.d("DataCleanupRepository", "Cleared body measurements")
            
            // Clear all workout-related data
            workoutDao.clearAllWorkoutSessionSets()
            workoutDao.clearAllWorkoutSessionExercises()
            workoutDao.clearAllWorkoutSessions()
            workoutDao.clearAllExerciseSets()
            workoutDao.clearAllWorkoutExercises()
            workoutDao.clearAllWorkoutCards()
            Log.d("DataCleanupRepository", "Cleared workout data")
            
            Log.d("DataCleanupRepository", "User data cleanup completed successfully")
            
        } catch (e: Exception) {
            Log.e("DataCleanupRepository", "Error during user data cleanup", e)
            throw e
        }
    }
}