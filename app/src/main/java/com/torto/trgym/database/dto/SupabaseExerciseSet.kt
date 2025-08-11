package com.torto.trgym.database.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseExerciseSet(
    val id: String? = null,
    @SerialName("exercise_id")
    val exerciseId: String,
    @SerialName("set_number")
    val setNumber: Int,
    @SerialName("weight_kg")
    val weightKg: Double? = null,
    val reps: Int? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)