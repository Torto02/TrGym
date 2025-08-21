package com.torto.trgym.database.dto

import com.torto.trgym.database.entities.WorkoutCard
import com.torto.trgym.database.entities.WorkoutExercise
import androidx.room.Embedded
import androidx.room.Relation

// DTOs per le query
data class WorkoutCardDetail(
    @Embedded
    val workoutCard: WorkoutCard,
    @Relation(
        parentColumn = "id",
        entityColumn = "workoutCardId"
    )
    val exercises: List<WorkoutExercise>
)