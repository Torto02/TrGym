package com.torto.trgym.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "body_measurements")
data class BodyMeasurement(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String, // es: "Peso", "Circonferenza braccio", "Circonferenza torace"
    val value: Double, // valore numerico della misurazione
    val unit: String, // es: "kg", "cm", "mm"
    val measurementDate: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Sync fields
    val syncedWithServer: Boolean = false,
    val pendingSync: Boolean = false
)