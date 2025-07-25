//package com.torto.trgym.database.dao
//
//import androidx.room.Dao
//import androidx.room.Query
//import androidx.room.Transaction
//import com.torto.trgym.database.dto.ExerciseDetail
//import com.torto.trgym.database.dto.ExerciseWithImage
//import com.torto.trgym.database.entities.Exercise
//import kotlinx.coroutines.flow.Flow
//
//@Dao
//interface ExerciseDao {
//    @Query("SELECT * FROM exercises ORDER BY name")
//    fun getAllExercises(): Flow<List<Exercise>>
//
//    @Transaction
//    @Query("""
//        SELECT e.id, e.name, e.type,
//               m.path AS imagePath
//        FROM exercises e
//        LEFT JOIN exercise_media m
//          ON e.id = m.exercise_id
//          AND m.media_order = 1
//        ORDER BY e.name
//    """)
//    fun getExercisesWithImage(): Flow<List<ExerciseWithImage>>
//
//    @Transaction
//    @Query("SELECT * FROM exercises WHERE id = :id")
//    fun getExerciseDetail(id: Int): Flow<ExerciseDetail>
//}


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
        SELECT e.id,
               e.name AS name,
               COALESCE(ed.title, ee.title, e.name) AS title,
               e.type,
               m.path AS imagePath
        FROM exercises e
        LEFT JOIN exercise_i18n ed
          ON e.id = ed.exercise_id
          AND ed.language_id = :languageId
        LEFT JOIN exercise_i18n ee
          ON e.id = ee.exercise_id
          AND ee.language_id = (
              SELECT id FROM languages WHERE code = 'en' LIMIT 1
          )
        LEFT JOIN exercise_media m
          ON e.id = m.exercise_id
          AND m.media_order = 1
        ORDER BY name
    """)
    fun getExercisesWithImage(languageId: Int): Flow<List<ExerciseWithImage>>

    @Transaction
    @Query("""
        SELECT e.*,
               COALESCE(ei.title, ee.title, e.name) AS localizedName,
               COALESCE(ei.primer, ee.primer, '') AS localizedPrimer
        FROM exercises e
        LEFT JOIN exercise_i18n ei
          ON e.id = ei.exercise_id
          AND ei.language_id = :languageId
        LEFT JOIN exercise_i18n ee
          ON e.id = ee.exercise_id
          AND ee.language_id = (
              SELECT id FROM languages WHERE code = 'en' LIMIT 1
          )
        WHERE e.id = :id
    """)
    fun getExerciseDetail(id: Int, languageId: Int): Flow<ExerciseDetail>
}