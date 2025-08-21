package com.torto.trgym.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

// WorkoutSessionSet.kt - Serie eseguita in una sessione
@Entity(
    tableName = "workout_session_sets",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = WorkoutSessionExercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class WorkoutSessionSet(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val exerciseId: String, // Riferimento all'esercizio della sessione
    val setNumber: Int, // Numero della serie (1, 2, 3, ...)
    val targetWeightKg: Double? = null, // Peso pianificato
    val actualWeightKg: Double? = null, // Peso effettivamente utilizzato
    val targetReps: Int? = null, // Ripetizioni pianificate
    val actualReps: Int? = null, // Ripetizioni effettivamente eseguite
    val isCompleted: Boolean = false, // Se la serie è stata completata
    val completedAt: Long? = null, // Timestamp completamento serie
    val restTimeMs: Long? = null, // Tempo di riposo dopo questa serie
    val rpe: Int? = null, // Rate of Perceived Exertion (1-10)
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Campi per sincronizzazione Supabase
    val syncedWithServer: Boolean = false,
    val pendingSync: Boolean = false
)