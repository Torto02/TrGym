package com.torto.trgym.database.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class SupabaseWorkoutSession(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("workout_card_id")
    val workoutCardId: String? = null,
    val name: String,
    @SerialName("start_time")
    val startTime: String, // ISO timestamp
    @SerialName("end_time")
    val endTime: String? = null,
    @SerialName("is_completed")
    val isCompleted: Boolean = false,
    @SerialName("total_duration_ms")
    val totalDurationMs: Long = 0,
    val notes: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("heart_rate_avg")
    val heartRateAvg: Int? = null
)