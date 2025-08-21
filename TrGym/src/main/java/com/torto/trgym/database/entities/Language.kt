package com.torto.trgym.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "languages")
data class Language(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String
)