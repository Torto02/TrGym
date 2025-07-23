package com.torto.trgym.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.torto.trgym.database.dto.ExerciseDetail
import com.torto.trgym.database.dto.ExerciseWithImage
import com.torto.trgym.database.entities.Exercise
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name")
    fun getAllExercises(): Flow<List<Exercise>>

    @Transaction
    @Query("""
        SELECT e.id, e.name, e.type,
               m.path AS imagePath
        FROM exercises e
        LEFT JOIN exercise_media m
          ON e.id = m.exercise_id
          AND m.media_order = 1
        ORDER BY e.name
    """)
    fun getExercisesWithImage(): Flow<List<ExerciseWithImage>>

    @Transaction
    @Query("SELECT * FROM exercises WHERE id = :id")
    fun getExerciseDetail(id: Int): Flow<ExerciseDetail>
}
