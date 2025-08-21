package com.torto.trgym.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "muscles",
    foreignKeys = [ForeignKey(
        entity = MuscleGroup::class,
        parentColumns = ["id"],
        childColumns = ["muscle_group_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("muscle_group_id")]
)
data class Muscle(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "muscle_group_id") val muscleGroupId: Int
)