package com.torto.trgym.data.repository

import com.torto.trgym.database.dao.WorkoutDao
import com.torto.trgym.database.dto.WorkoutExerciseDetail
import com.torto.trgym.database.dto.WorkoutCardDetail
import com.torto.trgym.database.entities.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao
) {

    // WorkoutCard operations
    fun getAllWorkoutCards(): Flow<List<WorkoutCard>> = workoutDao.getAllWorkoutCards()

    suspend fun getWorkoutCard(cardId: String): WorkoutCard? = workoutDao.getWorkoutCard(cardId)

    suspend fun insertWorkoutCard(workoutCard: WorkoutCard) = workoutDao.insertWorkoutCard(workoutCard)

    suspend fun updateWorkoutCard(workoutCard: WorkoutCard) = workoutDao.updateWorkoutCard(workoutCard)

    suspend fun deleteWorkoutCard(workoutCard: WorkoutCard) = workoutDao.deleteWorkoutCard(workoutCard)

    suspend fun getWorkoutCardWithExercises(cardId: String): WorkoutCardDetail? =
        workoutDao.getWorkoutCardWithExercises(cardId)

    suspend fun getExerciseCountForCard(cardId: String): Int =
        workoutDao.getExerciseCountForCard(cardId)

    // WorkoutExercise operations
    fun getExercisesForCard(cardId: String): Flow<List<WorkoutExercise>> =
        workoutDao.getExercisesForCard(cardId)

    suspend fun getWorkoutExercise(exerciseId: String): WorkoutExercise? =
        workoutDao.getWorkoutExercise(exerciseId)

    suspend fun insertWorkoutExercise(exercise: WorkoutExercise) =
        workoutDao.insertWorkoutExercise(exercise)

    suspend fun updateWorkoutExercise(exercise: WorkoutExercise) =
        workoutDao.updateWorkoutExercise(exercise)

    suspend fun deleteWorkoutExercise(exercise: WorkoutExercise) =
        workoutDao.deleteWorkoutExercise(exercise)

    suspend fun getExerciseWithSets(exerciseId: String): WorkoutExerciseDetail? =
        workoutDao.getExerciseWithSets(exerciseId)

    // ExerciseSet operations
    fun getSetsForExercise(exerciseId: String): Flow<List<ExerciseSet>> =
        workoutDao.getSetsForExercise(exerciseId)

    suspend fun insertExerciseSet(set: ExerciseSet) = workoutDao.insertExerciseSet(set)

    suspend fun insertExerciseSets(sets: List<ExerciseSet>) = workoutDao.insertExerciseSets(sets)

    suspend fun updateExerciseSet(set: ExerciseSet) = workoutDao.updateExerciseSet(set)

    suspend fun deleteExerciseSet(set: ExerciseSet) = workoutDao.deleteExerciseSet(set)

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
}