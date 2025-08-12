package com.torto.trgym.database.dto

import com.torto.trgym.database.entities.WorkoutSession
import com.torto.trgym.database.entities.WorkoutSessionExercise

data class WorkoutSessionDetail(
    @androidx.room.Embedded
    val session: WorkoutSession,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "workoutSessionId"
    )
    val exercises: List<WorkoutSessionExercise>
)
