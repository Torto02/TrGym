package com.torto.trgym.database.dto

import com.torto.trgym.database.entities.WorkoutExercise
import com.torto.trgym.database.entities.ExerciseSet
import androidx.room.Embedded
import androidx.room.Relation


data class WorkoutExerciseDetail(
    @Embedded
    val exercise: WorkoutExercise,
    @Relation(
        parentColumn = "id",
        entityColumn = "exerciseId"
    )
    val sets: List<ExerciseSet>
)