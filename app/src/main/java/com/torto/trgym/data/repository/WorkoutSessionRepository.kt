package com.torto.trgym.data.repository

import android.util.Log
import com.torto.trgym.database.dao.WorkoutSessionDao
import com.torto.trgym.database.dto.CompleteWorkoutSession
import com.torto.trgym.database.dto.WorkoutSessionExerciseDetail
import com.torto.trgym.database.entities.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutSessionRepository @Inject constructor(
    val workoutSessionDao: WorkoutSessionDao,
    private val supabaseClient: SupabaseClient
) {

    // ========== WORKOUT SESSION OPERATIONS ==========

    fun getAllWorkoutSessions(): Flow<List<WorkoutSession>> =
        workoutSessionDao.getAllWorkoutSessions()

    suspend fun getWorkoutSession(sessionId: String): WorkoutSession? =
        workoutSessionDao.getWorkoutSession(sessionId)

    suspend fun getCurrentActiveSession(): WorkoutSession? =
        workoutSessionDao.getCurrentActiveSession()

    fun getSessionsForCard(cardId: String): Flow<List<WorkoutSession>> =
        workoutSessionDao.getSessionsForCard(cardId)

    /**
     * Crea una nuova sessione di allenamento a partire da una WorkoutCard
     */
    suspend fun createWorkoutSessionFromCard(
        workoutCard: WorkoutCard,
        workoutRepository: WorkoutRepository
    ): String = withContext(Dispatchers.IO) {

        // Crea la sessione principale
        val session = WorkoutSession(
            workoutCardId = workoutCard.id,
            name = workoutCard.name,
            startTime = System.currentTimeMillis(),
            pendingSync = true
        )

        insertWorkoutSession(session)

        // Ottieni gli esercizi dalla scheda originale
        val originalExercises = workoutRepository.getExercisesForCard(workoutCard.id).first()

        // Crea gli esercizi della sessione
        originalExercises.forEachIndexed { index, originalExercise ->
            val sessionExercise = WorkoutSessionExercise(
                workoutSessionId = session.id,
                originalExerciseId = originalExercise.id,
                name = originalExercise.name,
                orderIndex = index,
                pendingSync = true
            )

            insertWorkoutSessionExercise(sessionExercise)

            // Ottieni i set originali e crea i set della sessione
            val originalSets = workoutRepository.getSetsForExercise(originalExercise.id).first()

            originalSets.forEach { originalSet ->
                val sessionSet = WorkoutSessionSet(
                    exerciseId = sessionExercise.id,
                    setNumber = originalSet.setNumber,
                    targetWeightKg = originalSet.weightKg,
                    targetReps = originalSet.reps,
                    pendingSync = true
                )

                insertWorkoutSessionSet(sessionSet)
            }
        }

        session.id
    }

    suspend fun insertWorkoutSession(session: WorkoutSession) = withContext(Dispatchers.IO) {
        workoutSessionDao.insertWorkoutSession(session)

        // TODO: Sync to Supabase (implementeremo in seguito)
        try {
            val userId = supabaseClient.auth.currentUserOrNull()?.id
            if (userId != null) {
                Log.d("WorkoutSessionRepository", "Session created, will sync to Supabase later: ${session.id}")
            }
        } catch (e: Exception) {
            Log.w("WorkoutSessionRepository", "Could not upload session to Supabase", e)
        }
    }

    suspend fun updateWorkoutSession(session: WorkoutSession) = withContext(Dispatchers.IO) {
        val updatedSession = session.copy(
            updatedAt = System.currentTimeMillis(),
            pendingSync = true
        )
        workoutSessionDao.updateWorkoutSession(updatedSession)
        // TODO: Sync to Supabase (implementeremo in seguito)
    }

    suspend fun completeWorkoutSession(sessionId: String) = withContext(Dispatchers.IO) {
        val endTime = System.currentTimeMillis()
        val session = getWorkoutSession(sessionId)

        session?.let {
            val duration = endTime - it.startTime
            workoutSessionDao.completeWorkoutSession(sessionId, endTime, duration)
            // TODO: Sync to Supabase (implementeremo in seguito)
        }
    }

    suspend fun deleteWorkoutSession(session: WorkoutSession) = withContext(Dispatchers.IO) {
        workoutSessionDao.deleteWorkoutSession(session)
        // TODO: Delete from Supabase (implementeremo in seguito)
    }

    // ========== WORKOUT SESSION EXERCISE OPERATIONS ==========

    fun getExercisesForSession(sessionId: String): Flow<List<WorkoutSessionExercise>> =
        workoutSessionDao.getExercisesForSession(sessionId)

    fun getExercisesWithSetsForSession(sessionId: String): Flow<List<WorkoutSessionExerciseDetail>> =
        workoutSessionDao.getExercisesWithSetsForSession(sessionId)

    suspend fun insertWorkoutSessionExercise(exercise: WorkoutSessionExercise) = withContext(Dispatchers.IO) {
        workoutSessionDao.insertWorkoutSessionExercise(exercise)
        // TODO: Sync to Supabase (implementeremo in seguito)
    }

    suspend fun updateWorkoutSessionExercise(exercise: WorkoutSessionExercise) = withContext(Dispatchers.IO) {
        val updatedExercise = exercise.copy(
            updatedAt = System.currentTimeMillis(),
            pendingSync = true
        )
        workoutSessionDao.updateWorkoutSessionExercise(updatedExercise)
        // TODO: Sync to Supabase (implementeremo in seguito)
    }

    suspend fun completeWorkoutSessionExercise(exerciseId: String) = withContext(Dispatchers.IO) {
        val endTime = System.currentTimeMillis()
        workoutSessionDao.completeWorkoutSessionExercise(exerciseId, endTime)
        // TODO: Sync to Supabase (implementeremo in seguito)
    }

    suspend fun deleteWorkoutSessionExercise(exercise: WorkoutSessionExercise) = withContext(Dispatchers.IO) {
        workoutSessionDao.deleteWorkoutSessionExercise(exercise)
        // TODO: Delete from Supabase (implementeremo in seguito)
    }

    suspend fun reorderExercises(sessionId: String, exerciseIds: List<String>) = withContext(Dispatchers.IO) {
        exerciseIds.forEachIndexed { index, exerciseId ->
            workoutSessionDao.updateExerciseOrder(exerciseId, index)
        }
        // TODO: Sync to Supabase (implementeremo in seguito)
    }

    suspend fun addExerciseToSession(sessionId: String, exerciseName: String) = withContext(Dispatchers.IO) {
        // Ottieni l'ordine corrente più alto
        val currentExercises = getExercisesForSession(sessionId).first()
        val nextOrderIndex = currentExercises.size

        val newExercise = WorkoutSessionExercise(
            workoutSessionId = sessionId,
            originalExerciseId = "", // Nuovo esercizio senza originale
            name = exerciseName,
            orderIndex = nextOrderIndex,
            pendingSync = true
        )

        insertWorkoutSessionExercise(newExercise)

        // Non creare set di default durante l'allenamento - l'utente li aggiungerà manualmente
        // Questo permette più flessibilità durante l'allenamento attivo

        newExercise.id
    }

    // ========== WORKOUT SESSION SET OPERATIONS ==========

    fun getSetsForSessionExercise(exerciseId: String): Flow<List<WorkoutSessionSet>> =
        workoutSessionDao.getSetsForSessionExercise(exerciseId)

    suspend fun insertWorkoutSessionSet(set: WorkoutSessionSet) = withContext(Dispatchers.IO) {
        workoutSessionDao.insertWorkoutSessionSet(set)
        // TODO: Sync to Supabase (implementeremo in seguito)
    }

    suspend fun updateWorkoutSessionSet(set: WorkoutSessionSet) = withContext(Dispatchers.IO) {
        val updatedSet = set.copy(
            updatedAt = System.currentTimeMillis(),
            pendingSync = true
        )
        workoutSessionDao.updateWorkoutSessionSet(updatedSet)
        // TODO: Sync to Supabase (implementeremo in seguito)
    }

    suspend fun completeWorkoutSessionSet(setId: String) = withContext(Dispatchers.IO) {
        val completedAt = System.currentTimeMillis()

        // Marca il set come completato
        workoutSessionDao.completeWorkoutSessionSet(setId, completedAt)

        // Controlla se tutti i set dell'esercizio sono completati
        val set = workoutSessionDao.getWorkoutSessionSet(setId)
        set?.let {
            checkAndCompleteExerciseIfAllSetsCompleted(it.exerciseId)
        }
    }

    suspend fun deleteWorkoutSessionSet(set: WorkoutSessionSet) = withContext(Dispatchers.IO) {
        workoutSessionDao.deleteWorkoutSessionSet(set)
        // TODO: Delete from Supabase (implementeremo in seguito)
    }

    suspend fun addSetToSessionExercise(exerciseId: String): String = withContext(Dispatchers.IO) {
        val currentSets = getSetsForSessionExercise(exerciseId).first()
        val nextSetNumber = (currentSets.maxOfOrNull { it.setNumber } ?: 0) + 1

        val newSet = WorkoutSessionSet(
            exerciseId = exerciseId,
            setNumber = nextSetNumber,
            pendingSync = true
        )

        insertWorkoutSessionSet(newSet)
        newSet.id
    }

    // ========== HELPER METHODS ==========

    private suspend fun checkAndCompleteExerciseIfAllSetsCompleted(exerciseId: String) {
        val sets = getSetsForSessionExercise(exerciseId).first()
        val allSetsCompleted = sets.isNotEmpty() && sets.all { it.isCompleted }

        if (allSetsCompleted) {
            val exercise = workoutSessionDao.getWorkoutSessionExercise(exerciseId)
            exercise?.let {
                val completedExercise = it.copy(
                    isCompleted = true,
                    endTime = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    pendingSync = true
                )
                workoutSessionDao.updateWorkoutSessionExercise(completedExercise)
                Log.d("WorkoutSessionRepository", "Exercise ${it.name} marked as completed")
            }
        }
    }

    suspend fun getCompleteWorkoutSession(sessionId: String): CompleteWorkoutSession? =
        workoutSessionDao.getCompleteWorkoutSession(sessionId)

    // This method should be moved to WorkoutRepository or receive workoutRepository as parameter
    // For now, removing it as it's not used and causes compilation error

    suspend fun getTotalCompletedWorkouts(): Int =
        workoutSessionDao.getTotalCompletedWorkouts()

    suspend fun getCompletedWorkoutsForCard(cardId: String): Int =
        workoutSessionDao.getCompletedWorkoutsForCard(cardId)

    suspend fun getRecentCompletedWorkouts(limit: Int = 10): List<WorkoutSession> =
        workoutSessionDao.getRecentCompletedWorkouts(limit)

    suspend fun getAverageWorkoutDuration(cardId: String): Long? =
        workoutSessionDao.getAverageWorkoutDuration(cardId)

    // ========== TIMER FUNCTIONALITY ==========

    /**
     * Gestisce il timer dell'allenamento
     */
    class WorkoutTimer {
        private var startTime: Long = 0
        private var pausedDuration: Long = 0
        private var isPaused: Boolean = false
        private var pauseStartTime: Long = 0

        fun start() {
            startTime = System.currentTimeMillis()
            isPaused = false
            pausedDuration = 0
        }

        fun startFromTime(sessionStartTime: Long) {
            startTime = sessionStartTime
            isPaused = false
            pausedDuration = 0
        }

        fun pause() {
            if (!isPaused) {
                isPaused = true
                pauseStartTime = System.currentTimeMillis()
            }
        }

        fun resume() {
            if (isPaused) {
                pausedDuration += System.currentTimeMillis() - pauseStartTime
                isPaused = false
            }
        }

        fun getElapsedTime(): Long {
            return if (isPaused) {
                pauseStartTime - startTime - pausedDuration
            } else {
                System.currentTimeMillis() - startTime - pausedDuration
            }
        }

        fun formatTime(milliseconds: Long): String {
            val seconds = (milliseconds / 1000) % 60
            val minutes = (milliseconds / (1000 * 60)) % 60
            val hours = (milliseconds / (1000 * 60 * 60)) % 24

            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

        fun isPaused(): Boolean = isPaused
    }
}