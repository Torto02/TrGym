package com.torto.trgym.database.dto

import com.torto.trgym.database.entities.WorkoutSessionExercise
import com.torto.trgym.database.entities.WorkoutSessionSet

data class WorkoutSessionExerciseDetail(
    @androidx.room.Embedded
    val exercise: WorkoutSessionExercise,
    @androidx.room.Relation(
        parentColumn = "id",
        entityColumn = "exerciseId"
    )
    val sets: List<WorkoutSessionSet>
)