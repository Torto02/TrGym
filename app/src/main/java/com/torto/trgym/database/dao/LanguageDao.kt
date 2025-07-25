package com.torto.trgym.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.torto.trgym.database.entities.Language
import kotlinx.coroutines.flow.Flow

@Dao
interface LanguageDao {
    @Query("SELECT * FROM languages")
    fun getAllLanguages(): Flow<List<Language>>

    @Query("SELECT id FROM languages WHERE code = :code LIMIT 1")
    suspend fun getLanguageIdByCode(code: String): Int?
}