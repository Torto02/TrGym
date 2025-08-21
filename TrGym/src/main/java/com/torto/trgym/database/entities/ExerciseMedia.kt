package com.torto.trgym.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "exercise_media",
    primaryKeys = ["exercise_id","media_order"]
)
data class ExerciseMedia(
    @ColumnInfo(name = "exercise_id") val exerciseId: Int,
    @ColumnInfo(name = "media_order") val mediaOrder: Int,
    val path: String,
    val type: String
)