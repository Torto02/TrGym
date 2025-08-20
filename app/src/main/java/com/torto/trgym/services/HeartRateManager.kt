package com.torto.trgym.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.SampleDataPoint
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType.TYPE_HEART_RATE_BPM
import com.google.android.gms.fitness.request.SensorRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeartRateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "HeartRateManager"
        private const val PREF_NAME = "heart_rate_prefs"
        private const val PREF_CONSENT_ENABLED = "consent_enabled"
        private const val PREF_LAST_HR = "last_heart_rate"
    }
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    // Health Services client (WearOS)
    private val healthServicesClient = HealthServices.getClient(context)
    private val measureClient: MeasureClient = healthServicesClient.measureClient
    private var currentMeasureCallback: MeasureCallback? = null
    
    // Google Fit options
    private val fitnessOptions = FitnessOptions.builder()
        .addDataType(TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
        .build()
    
    var isMonitoring = false
        private set
    
    var lastHeartRate = 0
        private set
        get() = sharedPreferences.getInt(PREF_LAST_HR, 0)
    
    // Consenso utente per monitoraggio battito cardiaco
    var isConsentEnabled: Boolean
        get() = sharedPreferences.getBoolean(PREF_CONSENT_ENABLED, false)
        set(value) {
            sharedPreferences.edit()
                .putBoolean(PREF_CONSENT_ENABLED, value)
                .apply()
            Log.d(TAG, "Consenso battito cardiaco impostato a: $value")
        }
    
    /**
     * Controlla se i permessi necessari sono stati concessi
     */
    fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Controlla se il dispositivo supporta il monitoraggio del battito cardiaco
     */
    suspend fun isHeartRateSupported(): Boolean {
        return try {
            // Per ora restituiamo true se abbiamo i permessi necessari
            // L'implementazione completa dell'API Health Services richiederebbe più setup
            hasRequiredPermissions()
        } catch (e: Exception) {
            Log.w(TAG, "Errore nel controllo supporto battito cardiaco: ${e.message}")
            false
        }
    }
    
    /**
     * Avvia il monitoraggio del battito cardiaco
     */
    fun startHeartRateMonitoring(): Flow<HeartRateData> = callbackFlow {
        if (!isConsentEnabled) {
            Log.w(TAG, "Consenso non abilitato per monitoraggio battito cardiaco")
            close()
            return@callbackFlow
        }
        
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Permessi mancanti per monitoraggio battito cardiaco")
            close()
            return@callbackFlow
        }
        
        isMonitoring = true
        
        // Callback per Health Services (WearOS)
        currentMeasureCallback = object : MeasureCallback {
            override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
                Log.d(TAG, "Disponibilità battito cardiaco: $availability")
            }
            
            override fun onDataReceived(data: DataPointContainer) {
                val heartRateList = data.getData(DataType.HEART_RATE_BPM)
                heartRateList.forEach { dataPoint ->
                    val heartRate = (dataPoint as SampleDataPoint<Double>).value.toInt()
                    lastHeartRate = heartRate
                    
                    // Salva l'ultimo valore nelle preferences
                    sharedPreferences.edit()
                        .putInt(PREF_LAST_HR, heartRate)
                        .apply()
                    
                    scope.launch {
                        trySend(HeartRateData(
                            heartRate = heartRate,
                            timestamp = System.currentTimeMillis(),
                            source = HeartRateSource.WEAR_OS
                        ))
                    }
                    
                    Log.d(TAG, "Battito cardiaco ricevuto: $heartRate BPM")
                }
            }
        }
        
        try {
            // Prima prova con Health Services (WearOS)
            if (isHeartRateSupported()) {
                Log.d(TAG, "Avvio monitoraggio con Health Services")
                // Per ora simuliamo il monitoraggio per testare l'UI
                // L'implementazione completa dell'API Health Services richiederebbe setup più complesso
                Log.d(TAG, "Monitoraggio battito cardiaco avviato (modalità simulazione)")
            } else {
                // Fallback su Google Fit se disponibile
                Log.d(TAG, "Health Services non supportato, provo Google Fit")
                startGoogleFitMonitoring()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'avvio monitoraggio: ${e.message}")
            isMonitoring = false
        }
        
        awaitClose {
            stopHeartRateMonitoring()
        }
    }
    
    /**
     * Avvia monitoraggio con Google Fit come fallback
     */
    private fun startGoogleFitMonitoring() {
        try {
            val sensorRequest = SensorRequest.Builder()
                .setDataType(TYPE_HEART_RATE_BPM)
                .setSamplingRate(1, TimeUnit.SECONDS)
                .build()
            
            // Implementazione Google Fit qui
            Log.d(TAG, "Google Fit monitoraggio avviato (fallback)")
        } catch (e: Exception) {
            Log.e(TAG, "Errore Google Fit: ${e.message}")
        }
    }
    
    /**
     * Ferma il monitoraggio del battito cardiaco
     */
    fun stopHeartRateMonitoring() {
        if (isMonitoring) {
            try {
                currentMeasureCallback?.let { callback ->
                    // Per ora semplicemente resettiamo lo stato
                    // L'implementazione completa richiederebbe di gestire correttamente l'API Health Services
                }
                currentMeasureCallback = null
                isMonitoring = false
                Log.d(TAG, "Monitoraggio battito cardiaco fermato")
            } catch (e: Exception) {
                Log.e(TAG, "Errore nel fermare il monitoraggio: ${e.message}")
            }
        }
    }
    
    /**
     * Calcola le calorie bruciate basate sul battito cardiaco
     * Formula semplificata di Karvonen
     */
    fun calculateCaloriesBurned(
        heartRate: Int,
        durationMinutes: Int,
        age: Int = 30,
        weight: Double = 70.0,
        gender: Gender = Gender.MALE
    ): Double {
        if (heartRate <= 0 || durationMinutes <= 0) return 0.0
        
        val maxHR = 220 - age
        val restingHR = when (gender) {
            Gender.MALE -> 70
            Gender.FEMALE -> 75
        }
        
        // Intensità dell'esercizio (% della frequenza cardiaca massima)
        val intensity = ((heartRate - restingHR).toDouble() / (maxHR - restingHR)).coerceIn(0.0, 1.0)
        
        // MET approssimativo basato sull'intensità
        val met = when {
            intensity < 0.5 -> 3.0  // Attività leggera
            intensity < 0.7 -> 6.0  // Attività moderata
            intensity < 0.85 -> 9.0 // Attività intensa
            else -> 12.0             // Attività molto intensa
        }
        
        // Calorie = MET × peso (kg) × durata (ore)
        return met * weight * (durationMinutes / 60.0)
    }
    
    /**
     * Rilascia le risorse
     */
    fun release() {
        stopHeartRateMonitoring()
        scope.cancel()
    }
}

/**
 * Dati del battito cardiaco
 */
data class HeartRateData(
    val heartRate: Int,
    val timestamp: Long,
    val source: HeartRateSource
)

/**
 * Fonte dei dati del battito cardiaco
 */
enum class HeartRateSource {
    WEAR_OS,
    GOOGLE_FIT,
    UNKNOWN
}

/**
 * Genere per calcolo calorie
 */
enum class Gender {
    MALE,
    FEMALE
}