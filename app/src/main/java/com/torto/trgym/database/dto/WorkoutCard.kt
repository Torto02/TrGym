package com.torto.trgym.database.dto

data class WorkoutCard(
    val id: String,
    val userId: String,
    val name: String,
    val description: String?,
    val exercises: List<WorkoutCardExercise> = emptyList()
)
