package com.torto.trgym.database.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseWorkoutCard(
    val id: String? = null,
    @SerialName("user_id")
    val userId: String? = null,
    val name: String,
    val description: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null
)