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
    indices = [Index(value = ["workoutCardId"])]
)
data class WorkoutExercise(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val workoutCardId: String,
    val name: String,
    val orderIndex: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val syncedWithServer: Boolean = false,
    val pendingSync: Boolean = false,
    val updatedAt: Long
)