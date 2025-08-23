package com.torto.trgym.database.dto

import com.torto.trgym.database.entities.WorkoutSession

data class WorkoutSessionWithCalories(
    val workoutSession: WorkoutSession,
    val calories: Int
)

