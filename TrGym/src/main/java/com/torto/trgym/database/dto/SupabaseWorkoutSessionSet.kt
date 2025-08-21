package com.torto.trgym.database.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseWorkoutSessionSet(
    val id: String? = null,
    @SerialName("exercise_id")
    val exerciseId: String,
    @SerialName("set_number")
    val setNumber: Int,
    @SerialName("target_weight_kg")
    val targetWeightKg: Double? = null,
    @SerialName("actual_weight_kg")
    val actualWeightKg: Double? = null,
    @SerialName("target_reps")
    val targetReps: Int? = null,
    @SerialName("actual_reps")
    val actualReps: Int? = null,
    @SerialName("is_completed")
    val isCompleted: Boolean = false,
    @SerialName("completed_at")
    val completedAt: String? = null,
    @SerialName("rest_time_ms")
    val restTimeMs: Long? = null,
    val rpe: Int? = null,
    val notes: String? = null
)