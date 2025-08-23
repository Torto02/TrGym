package com.torto.trgym.database.dao

import androidx.room.*
import com.torto.trgym.database.entities.BodyMeasurement
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMeasurementDao {

    // ==========================================
    // BASIC OPERATIONS
    // ==========================================

    @Query("SELECT * FROM body_measurements ORDER BY measurementDate DESC")
    fun getAllMeasurements(): Flow<List<BodyMeasurement>>

    @Query("SELECT DISTINCT name FROM body_measurements ORDER BY name ASC")
    fun getAllMeasurementNames(): Flow<List<String>>

    @Query("SELECT * FROM body_measurements WHERE name = :measurementName ORDER BY measurementDate DESC")
    fun getMeasurementsByName(measurementName: String): Flow<List<BodyMeasurement>>

    @Query("SELECT * FROM body_measurements WHERE name = :measurementName ORDER BY measurementDate DESC LIMIT 1")
    suspend fun getLatestMeasurement(measurementName: String): BodyMeasurement?

    @Query("SELECT * FROM body_measurements WHERE id = :measurementId")
    suspend fun getMeasurementById(measurementId: String): BodyMeasurement?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurement(measurement: BodyMeasurement): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeasurements(measurements: List<BodyMeasurement>)

    @Update
    suspend fun updateMeasurement(measurement: BodyMeasurement)

    @Delete
    suspend fun deleteMeasurement(measurement: BodyMeasurement)

    @Query("DELETE FROM body_measurements WHERE name = :measurementName")
    suspend fun deleteMeasurementsByName(measurementName: String)

    // ==========================================
    // STATISTICS
    // ==========================================

    @Query("SELECT COUNT(*) FROM body_measurements WHERE name = :measurementName")
    suspend fun getMeasurementCount(measurementName: String): Int

    @Query("SELECT MIN(value) FROM body_measurements WHERE name = :measurementName")
    suspend fun getMinValue(measurementName: String): Double?

    @Query("SELECT MAX(value) FROM body_measurements WHERE name = :measurementName")
    suspend fun getMaxValue(measurementName: String): Double?

    @Query("SELECT AVG(value) FROM body_measurements WHERE name = :measurementName")
    suspend fun getAverageValue(measurementName: String): Double?

    // ==========================================
    // SYNC OPERATIONS
    // ==========================================

    @Query("SELECT * FROM body_measurements WHERE pendingSync = 1")
    suspend fun getUnsyncedMeasurements(): List<BodyMeasurement>

    @Query("UPDATE body_measurements SET syncedWithServer = 1, pendingSync = 0 WHERE id = :measurementId")
    suspend fun markMeasurementAsSynced(measurementId: String)

    /**
     * Removes all user-specific body measurement data.
     */
    @Query("DELETE FROM body_measurements")
    suspend fun clearAllMeasurements()
}