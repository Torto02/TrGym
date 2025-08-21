//package com.torto.trgym.database.entities
//
//import androidx.room.ColumnInfo
//import androidx.room.Entity
//import androidx.room.PrimaryKey
//
//@Entity(tableName = "starred_exercises")
//data class StarredExercise(
//    @PrimaryKey
//    @ColumnInfo(name = "exercise_id")
//    val exerciseId: Int
//)

package com.torto.trgym.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entità che rappresenta un esercizio preferito dall'utente.
 * Ora include supporto per la gestione offline e sincronizzazione.
 */
@Entity(tableName = "starred_exercises")
data class StarredExercise(
    @PrimaryKey
    @ColumnInfo(name = "exercise_id")
    val exerciseId: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "synced_with_server")
    val syncedWithServer: Boolean = false
)