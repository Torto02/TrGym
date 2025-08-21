package com.torto.trgym.database.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseStarredExercise(
    val id: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("exercise_id")
    val exerciseId: Int
)