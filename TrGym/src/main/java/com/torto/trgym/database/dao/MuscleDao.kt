package com.torto.trgym.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.torto.trgym.database.entities.MuscleGroupI18n
import kotlinx.coroutines.flow.Flow

@Dao
interface MuscleDao {
    @Query("SELECT * FROM muscle_groups_i18n mg WHERE mg.language_id = :languageId ORDER BY mg.name")
    fun getMuscleGroups(languageId: Int): List<MuscleGroupI18n>
}