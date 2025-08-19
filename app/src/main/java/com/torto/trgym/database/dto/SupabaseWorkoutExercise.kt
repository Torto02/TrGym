package com.torto.trgym.database.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SupabaseWorkoutExercise(
    val id: String? = null,
    @SerialName("workout_card_id")
    val workoutCardId: String,
    @SerialName("exercise_id")
    val exerciseId: Int? = null,  // Aggiunta della colonna exerciseId
    val name: String,
    @SerialName("order_index")
    val orderIndex: Int,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("image_path")
    val imagePath: String? = null,
)