package com.torto.trgym.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "exercise_tips",
    primaryKeys = ["exercise_id","language_id","tip_order"],
    indices = [Index("language_id")]
)
data class ExerciseTip(
    @ColumnInfo(name = "exercise_id") val exerciseId: Int,
    @ColumnInfo(name = "language_id") val languageId: Int,
    @ColumnInfo(name = "tip_order") val tipOrder: Int,
    val description: String
)
