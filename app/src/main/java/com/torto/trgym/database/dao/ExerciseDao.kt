package com.torto.trgym.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.torto.trgym.database.entities.Exercise

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises")
    fun getAllExercises(): List<Exercise>
}