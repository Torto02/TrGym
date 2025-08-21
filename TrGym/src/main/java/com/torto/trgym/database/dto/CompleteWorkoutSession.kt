package com.torto.trgym.database.dto

import com.torto.trgym.database.entities.WorkoutSession
import com.torto.trgym.database.entities.WorkoutSessionExercise

data class CompleteWorkoutSession(
    @androidx.room.Embedded
    val session: WorkoutSession,
    @androidx.room.Relation(
        entity = WorkoutSessionExercise::class,
        parentColumn = "id",
        entityColumn = "workoutSessionId"
    )
    val exercisesWithSets: List<WorkoutSessionExerciseDetail>
)
