package com.torto.trgym.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "workout_session_exercises",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = WorkoutSession::class,
            parentColumns = ["id"],
            childColumns = ["workoutSessionId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class WorkoutSessionExercise(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val workoutSessionId: String, // Riferimento alla sessione
    val originalExerciseId: String, // Riferimento all'esercizio originale della scheda
    val name: String, // Nome dell'esercizio (snapshot)
    val orderIndex: Int, // Ordine nell'allenamento
    val isCompleted: Boolean = false, // Se tutti i set sono stati completati
    val startTime: Long? = null, // Quando è iniziato questo esercizio
    val endTime: Long? = null, // Quando è finito questo esercizio
    val restTimeBetweenSets: Long = 60000, // Tempo di riposo tra le serie (default 60s)
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val imagePath: String? = null,
    val syncedWithServer: Boolean = false,
    val pendingSync: Boolean = false
)