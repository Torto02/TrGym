package com.torto.trgym.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.*

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutCard::class,
            parentColumns = ["id"],
            childColumns = ["workoutCardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["workoutCardId"]),
        Index(value = ["exerciseId"])  // Nuovo indice per performance
    ]
)
data class WorkoutExercise(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val workoutCardId: String,
    val exerciseId: Int? = null,  // Nuovo campo - riferimento all'Exercise originale
    val name: String,
    val orderIndex: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val imagePath: String? = null,
    val syncedWithServer: Boolean = false,
    val pendingSync: Boolean = false,
    val updatedAt: Long
)