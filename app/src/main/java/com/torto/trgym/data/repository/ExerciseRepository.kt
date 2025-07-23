package com.torto.trgym.data.repository

import com.torto.trgym.database.dao.ExerciseDao
import com.torto.trgym.database.dto.ExerciseDetail
import com.torto.trgym.database.dto.ExerciseWithImage
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@ViewModelScoped
class ExerciseRepository @Inject constructor(
    private val dao: ExerciseDao
) {
    fun allExercises(): Flow<List<ExerciseWithImage>> = dao.getExercisesWithImage()

    fun getExerciseDetail(id: Int): Flow<ExerciseDetail> =
        dao.getExerciseDetail(id)
}
