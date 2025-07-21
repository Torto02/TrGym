package com.torto.trgym.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "muscle_groups_i18n",
    primaryKeys = ["muscle_group_id","language_id"],
    indices = [Index("language_id")]
)
data class MuscleGroupI18n(
    @ColumnInfo(name = "muscle_group_id") val muscleGroupId: Int,
    @ColumnInfo(name = "language_id") val languageId: Int,
    val name: String
)