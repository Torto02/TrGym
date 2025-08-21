package com.torto.trgym.database.dto

data class WorkoutCardExercise(
    val id: String,
    val workoutCardId: String,
    val name: String,
    val orderIndex: Int,
    val sets: List<ExerciseSet> = emptyList()
)
