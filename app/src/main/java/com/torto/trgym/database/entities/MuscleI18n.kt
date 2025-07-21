package com.torto.trgym.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "muscles_i18n",
    primaryKeys = ["muscle_id","language_id"],
    indices = [Index("language_id")]
)
data class MuscleI18n(
    @ColumnInfo(name = "muscle_id") val muscleId: Int,
    @ColumnInfo(name = "language_id") val languageId: Int,
    val name: String
)