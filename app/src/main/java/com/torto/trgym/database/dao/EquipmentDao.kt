package com.torto.trgym.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.torto.trgym.database.entities.EquipmentI18n

@Dao
interface EquipmentDao {

    @Query("""
        SELECT * FROM equipment_i18n
        WHERE language_id = :languageId
        UNION
        SELECT * FROM equipment_i18n
        WHERE language_id = 1
        AND equipment_id NOT IN (
            SELECT equipment_id FROM equipment_i18n WHERE language_id = :languageId
        )
        ORDER BY name
    """)
    fun getEquipment(languageId: Int): List<EquipmentI18n>
}