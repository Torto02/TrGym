package com.torto.trgym.repository

import android.content.Context
import com.torto.trgym.database.dao.MuscleDao
import com.torto.trgym.database.entities.MuscleGroupI18n
import dagger.hilt.android.qualifiers.ApplicationContext

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class MuscleRepository @Inject constructor(
    private val muscleDao: MuscleDao,
    @ApplicationContext private val context: Context
){

    fun allMuscleGroups(): Flow<List<MuscleGroupI18n>> = flow {
        emit(muscleDao.getMuscleGroups(1))
    }.flowOn(Dispatchers.IO)
}