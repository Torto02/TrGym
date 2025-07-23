package com.torto.trgym.database.dto

data class ExerciseWithImage(
    val id: Int,
    val name: String,
    val type: String,
    val imagePath: String?
)