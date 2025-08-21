package com.torto.trgym.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val workoutCardId: String, // Riferimento alla scheda utilizzata
    val name: String, // Nome della sessione (di solito uguale alla scheda)
    val startTime: Long, // Timestamp inizio allenamento
    val endTime: Long? = null, // Timestamp fine allenamento (null se in corso)
    val isCompleted: Boolean = false, // Se l'allenamento è stato completato
    val totalDurationMs: Long = 0, // Durata totale in millisecondi
    val averageHeartRate: Int? = null, // Media battiti cardiaci durante allenamento
    val notes: String? = null, // Note dell'allenamento
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Campi per sincronizzazione Supabase
    val syncedWithServer: Boolean = false,
    val pendingSync: Boolean = false
)