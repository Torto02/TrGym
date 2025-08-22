package com.torto.trgym.repository

import android.content.Context
import com.torto.trgym.database.dao.EquipmentDao
import com.torto.trgym.database.entities.EquipmentI18n
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class EquipmentRepository @Inject constructor(
    private val equipmentDao: EquipmentDao,
    @ApplicationContext private val context: Context){


    fun allEquipment(): Flow<List<EquipmentI18n>> = flow {
        emit(equipmentDao.getEquipment(1))
    }.flowOn(Dispatchers.IO)
}