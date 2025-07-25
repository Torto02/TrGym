package com.torto.trgym.database.dto

data class ExerciseSet(
    val id: String,
    val exerciseId: String,
    val setNumber: Int,
    val weightKg: Double?,
    val reps: Int?
)
