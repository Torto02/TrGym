package com.torto.trgym.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "exercise_equipment",
    primaryKeys = ["exercise_id","equipment_id"],
    foreignKeys = [
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exercise_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(entity = Equipment::class, parentColumns = ["id"], childColumns = ["equipment_id"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("equipment_id")]
)
data class ExerciseEquipment(
    @ColumnInfo(name = "exercise_id") val exerciseId: Int,
    @ColumnInfo(name = "equipment_id") val equipmentId: Int
)