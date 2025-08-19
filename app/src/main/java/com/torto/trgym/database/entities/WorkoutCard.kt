// WorkoutCard.kt
package com.torto.trgym.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "workout_cards")
data class WorkoutCard(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val orderIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncedWithServer: Boolean = false,
    val pendingSync: Boolean = false  // true when local changes need to be synced
)