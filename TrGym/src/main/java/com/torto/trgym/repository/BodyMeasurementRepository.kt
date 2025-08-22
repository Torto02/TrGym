package com.torto.trgym.repository

import android.util.Log
import com.torto.trgym.database.dao.BodyMeasurementDao
import com.torto.trgym.database.dto.MeasurementStats
import com.torto.trgym.database.dto.SupabaseBodyMeasurement
import com.torto.trgym.database.entities.BodyMeasurement
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class BodyMeasurementRepository @Inject constructor(
    private val bodyMeasurementDao: BodyMeasurementDao,
    private val supabaseClient: SupabaseClient
) {

    private val currentUserId: String?
        get() = supabaseClient.auth.currentUserOrNull()?.id

    // ==========================================
    // PUBLIC API
    // ==========================================

    fun getAllMeasurements(): Flow<List<BodyMeasurement>> = bodyMeasurementDao.getAllMeasurements()

    fun getAllMeasurementNames(): Flow<List<String>> = bodyMeasurementDao.getAllMeasurementNames()

    fun getMeasurementsByName(measurementName: String): Flow<List<BodyMeasurement>> =
        bodyMeasurementDao.getMeasurementsByName(measurementName)

    suspend fun getLatestMeasurement(measurementName: String): BodyMeasurement? =
        bodyMeasurementDao.getLatestMeasurement(measurementName)

    suspend fun insertMeasurement(measurement: BodyMeasurement) = withContext(Dispatchers.IO) {
        val measurementToInsert = measurement.copy(pendingSync = true, syncedWithServer = false)
        bodyMeasurementDao.insertMeasurement(measurementToInsert)
        Log.d("BodyMeasurementRepo", "Inserted measurement locally: ${measurement.id}")

        // Sync to Supabase
        try {
            val userId = currentUserId
            Log.d("BodyMeasurementRepo", "Current user ID: $userId")

            if (userId != null) {
                val supabaseMeasurement = SupabaseBodyMeasurement(
                    id = measurement.id,
                    user_id = userId,
                    name = measurement.name,
                    value = measurement.value,
                    unit = measurement.unit,
                    measurement_date = Instant.ofEpochMilli(measurement.measurementDate).toString(),
                    notes = measurement.notes,
                    created_at = Instant.ofEpochMilli(measurement.createdAt).toString(),
                    updated_at = Instant.ofEpochMilli(measurement.updatedAt).toString()
                )

                Log.d("BodyMeasurementRepo", "Attempting to sync to Supabase: $supabaseMeasurement")
                supabaseClient.from("body_measurements").upsert(listOf(supabaseMeasurement))
                bodyMeasurementDao.markMeasurementAsSynced(measurement.id)
                Log.d("BodyMeasurementRepo", "Successfully synced measurement ${measurement.id} to Supabase")
            } else {
                Log.w("BodyMeasurementRepo", "No user ID available for sync")
            }
        } catch (e: Exception) {
            Log.e("BodyMeasurementRepo", "Could not sync measurement ${measurement.id} to Supabase", e)
        }
    }

    suspend fun updateMeasurement(measurement: BodyMeasurement) = withContext(Dispatchers.IO) {
        val measurementToUpdate = measurement.copy(
            updatedAt = System.currentTimeMillis(),
            pendingSync = true,
            syncedWithServer = false
        )
        bodyMeasurementDao.updateMeasurement(measurementToUpdate)

        // Sync to Supabase
        try {
            currentUserId?.let { userId ->
                val supabaseMeasurement = SupabaseBodyMeasurement(
                    id = measurement.id,
                    user_id = userId,
                    name = measurement.name,
                    value = measurement.value,
                    unit = measurement.unit,
                    measurement_date = Instant.ofEpochMilli(measurement.measurementDate).toString(),
                    notes = measurement.notes,
                    created_at = Instant.ofEpochMilli(measurement.createdAt).toString(),
                    updated_at = Instant.ofEpochMilli(measurementToUpdate.updatedAt).toString()
                )
                supabaseClient.from("body_measurements").upsert(listOf(supabaseMeasurement))
                bodyMeasurementDao.markMeasurementAsSynced(measurement.id)
                Log.d("BodyMeasurementRepo", "Updated measurement ${measurement.id} on Supabase")
            }
        } catch (e: Exception) {
            Log.w("BodyMeasurementRepo", "Could not update measurement ${measurement.id} on Supabase", e)
        }
    }

    suspend fun deleteMeasurement(measurement: BodyMeasurement) = withContext(Dispatchers.IO) {
        bodyMeasurementDao.deleteMeasurement(measurement)

        // Delete from Supabase
        try {
            currentUserId?.let {
                supabaseClient.from("body_measurements").delete {
                    filter { eq("id", measurement.id) }
                }
                Log.d("BodyMeasurementRepo", "Deleted measurement ${measurement.id} from Supabase")
            }
        } catch (e: Exception) {
            Log.e("BodyMeasurementRepo", "Error deleting measurement ${measurement.id} from Supabase", e)
        }
    }

    suspend fun deleteMeasurementsByName(measurementName: String) = withContext(Dispatchers.IO) {
        bodyMeasurementDao.deleteMeasurementsByName(measurementName)

        // Delete from Supabase
        try {
            currentUserId?.let { userId ->
                supabaseClient.from("body_measurements").delete {
                    filter {
                        eq("user_id", userId)
                        eq("name", measurementName)
                    }
                }
                Log.d("BodyMeasurementRepo", "Deleted all measurements for '$measurementName' from Supabase")
            }
        } catch (e: Exception) {
            Log.e("BodyMeasurementRepo", "Error deleting measurements for '$measurementName' from Supabase", e)
        }
    }

    // ==========================================
    // STATISTICS
    // ==========================================

    suspend fun getMeasurementStats(measurementName: String): MeasurementStats? = withContext(Dispatchers.IO) {
        try {
            val count = bodyMeasurementDao.getMeasurementCount(measurementName)
            if (count == 0) return@withContext null

            val min = bodyMeasurementDao.getMinValue(measurementName) ?: 0.0
            val max = bodyMeasurementDao.getMaxValue(measurementName) ?: 0.0
            val avg = bodyMeasurementDao.getAverageValue(measurementName) ?: 0.0
            val latest = bodyMeasurementDao.getLatestMeasurement(measurementName)

            MeasurementStats(
                count = count,
                minValue = min,
                maxValue = max,
                averageValue = avg,
                latestValue = latest?.value ?: 0.0,
                latestUnit = latest?.unit ?: ""
            )
        } catch (e: Exception) {
            Log.e("BodyMeasurementRepo", "Error getting stats for $measurementName", e)
            null
        }
    }

    // ==========================================
    // SYNC OPERATIONS
    // ==========================================

    suspend fun syncUnsyncedMeasurements() = withContext(Dispatchers.IO) {
        try {
            currentUserId?.let { userId ->
                bodyMeasurementDao.getUnsyncedMeasurements().forEach { measurement ->
                    try {
                        val supabaseMeasurement = SupabaseBodyMeasurement(
                            id = measurement.id,
                            user_id = userId,
                            name = measurement.name,
                            value = measurement.value,
                            unit = measurement.unit,
                            measurement_date = Instant.ofEpochMilli(measurement.measurementDate).toString(),
                            notes = measurement.notes,
                            created_at = Instant.ofEpochMilli(measurement.createdAt).toString(),
                            updated_at = Instant.ofEpochMilli(measurement.updatedAt).toString()
                        )
                        supabaseClient.from("body_measurements").upsert(listOf(supabaseMeasurement))
                        bodyMeasurementDao.markMeasurementAsSynced(measurement.id)
                        Log.d("BodyMeasurementRepo", "Synced measurement ${measurement.id} to Supabase")
                    } catch (e: Exception) {
                        Log.w("BodyMeasurementRepo", "Could not sync measurement ${measurement.id} to Supabase", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BodyMeasurementRepo", "Error syncing unsynced measurements", e)
        }
    }

    suspend fun downloadMeasurementsFromSupabase() = withContext(Dispatchers.IO) {
        try {
            currentUserId?.let { userId ->
                val supabaseMeasurements = supabaseClient.from("body_measurements")
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<SupabaseBodyMeasurement>()

                val localMeasurements = supabaseMeasurements.map { remote ->
                    BodyMeasurement(
                        id = remote.id!!,
                        name = remote.name,
                        value = remote.value,
                        unit = remote.unit,
                        measurementDate = Instant.parse(remote.measurement_date).toEpochMilli(),
                        notes = remote.notes,
                        createdAt = remote.created_at?.let { Instant.parse(it).toEpochMilli() } ?: System.currentTimeMillis(),
                        updatedAt = remote.updated_at?.let { Instant.parse(it).toEpochMilli() } ?: System.currentTimeMillis(),
                        syncedWithServer = true,
                        pendingSync = false
                    )
                }

                bodyMeasurementDao.insertMeasurements(localMeasurements)
                Log.d("BodyMeasurementRepo", "Downloaded ${localMeasurements.size} measurements from Supabase")
            }
        } catch (e: Exception) {
            Log.e("BodyMeasurementRepo", "Error downloading measurements from Supabase", e)
        }
    }
}

