package com.torto.trgym.database.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseWorkoutSessionExercise(
    val id: String? = null,
    @SerialName("workout_session_id")
    val workoutSessionId: String,
    @SerialName("original_exercise_id")
    val originalExerciseId: String,
    val name: String,
    @SerialName("order_index")
    val orderIndex: Int,
    @SerialName("is_completed")
    val isCompleted: Boolean = false,
    @SerialName("start_time")
    val startTime: String? = null,
    @SerialName("end_time")
    val endTime: String? = null,
    @SerialName("rest_time_between_sets")
    val restTimeBetweenSets: Long = 60000,
    val notes: String? = null
)