package com.torto.trgym.viewmodel.heartrate

import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class HeartRateViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application), MessageClient.OnMessageReceivedListener, SensorEventListener {

    private val _heartRateState = MutableStateFlow<HeartRateState>(HeartRateState.Idle)
    val heartRateState: StateFlow<HeartRateState> = _heartRateState

    private var monitoring = false
    private var startTime: Instant? = null
    private var currentHeartRate = 0
    private var userWeight = 70f // Default
    private var userHeight = 170f // Default
    private var lastCalorieCalculationTime = 0L
    private var totalCaloriesBurned = 0f

    private val sensorManager by lazy {
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private val heartRateSensor by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    }

    private val messageClient by lazy {
        Wearable.getMessageClient(application)
    }

    private val nodeClient by lazy {
        Wearable.getNodeClient(application)
    }

    private val bluetoothAdapter by lazy {
        (application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    companion object {
        private const val TAG = "HeartRateViewModel"
        private const val HEART_RATE_PATH = "/heart_rate_data"
        private const val START_MONITORING_PATH = "/start_heart_rate_monitoring"
        private const val STOP_MONITORING_PATH = "/stop_heart_rate_monitoring"
        private const val STATUS_PATH = "/heart_rate_status"
    }

    init {
        // Registra il listener all'inizializzazione
        messageClient.addListener(this)
        Log.d(TAG, "ViewModel inizializzato, listener registrato")
    }

    fun startMonitoring(weight: Float, height: Float) {
        viewModelScope.launch {
            testConnection()
            try {
                userWeight = weight
                userHeight = height
                monitoring = true
                startTime = Instant.now()
                totalCaloriesBurned = 0f
                lastCalorieCalculationTime = System.currentTimeMillis()

                _heartRateState.value = HeartRateState.Connecting

                // Prima prova con il sensore locale (se disponibile)
                if (heartRateSensor != null) {
                    Log.d(TAG, "Sensore locale disponibile, tentativo di registrazione...")
                    sensorManager.registerListener(
                        this@HeartRateViewModel,
                        heartRateSensor,
                        SensorManager.SENSOR_DELAY_NORMAL
                    )

                    // Aspetta un po' per vedere se ricevi dati dal sensore locale
                    kotlinx.coroutines.delay(2000)

                    // Se non hai ancora ricevuto dati, prova con Wear OS
                    if (currentHeartRate == 0) {
                        Log.d(TAG, "Nessun dato dal sensore locale, connessione a Wear OS...")
                        connectToWearOS()
                    }
                } else {
                    // Nessun sensore locale, connetti direttamente a Wear OS
                    Log.d(TAG, "Nessun sensore locale, connessione diretta a Wear OS...")
                    connectToWearOS()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore durante l'avvio del monitoraggio", e)
                _heartRateState.value = HeartRateState.Error("Impossibile avviare il monitoraggio: ${e.message}")
                monitoring = false
            }
        }
    }

    private suspend fun connectToWearOS() {
        withContext(Dispatchers.IO) {
            try {
                // Verifica Bluetooth
                if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                    withContext(Dispatchers.Main) {
                        _heartRateState.value = HeartRateState.Error("Bluetooth non disponibile o disabilitato")
                    }
                    monitoring = false
                    return@withContext
                }

                // Assicurati che il listener sia registrato
                messageClient.removeListener(this@HeartRateViewModel) // Rimuovi per sicurezza
                messageClient.addListener(this@HeartRateViewModel) // Ri-aggiungi

                Log.d(TAG, "Listener MessageClient registrato")

                // Ottieni i nodi connessi
                Log.d(TAG, "Cercando dispositivi Wear OS connessi...")
                val nodes = nodeClient.connectedNodes.await()

                if (nodes.isEmpty()) {
                    Log.e(TAG, "Nessun dispositivo Wear OS connesso")

                    // Se siamo in emulatore, mostra un messaggio più utile
                    val message = if (android.os.Build.FINGERPRINT.contains("generic") ||
                        android.os.Build.MODEL.contains("Emulator")) {
                        "Nessun Wear OS connesso. Assicurati che entrambi gli emulatori siano avviati e accoppiati tramite Wear OS app."
                    } else {
                        "Nessun dispositivo Wear OS connesso. Verifica l'accoppiamento."
                    }

                    withContext(Dispatchers.Main) {
                        _heartRateState.value = HeartRateState.Error(message)
                    }
                    monitoring = false
                    return@withContext
                }

                Log.d(TAG, "Trovati ${nodes.size} dispositivi Wear OS:")
                nodes.forEach { node ->
                    Log.d(TAG, "  - ${node.displayName} (${node.id})")
                }

                // Invia messaggio di start a tutti i nodi
                for (node in nodes) {
                    try {
                        Log.d(TAG, "Invio START_MONITORING a ${node.displayName}...")

                        messageClient.sendMessage(
                            node.id,
                            START_MONITORING_PATH,
                            "start".toByteArray()
                        ).addOnSuccessListener {
                            Log.d(TAG, "✓ Messaggio START inviato con successo a ${node.displayName}")
                        }.addOnFailureListener { e ->
                            Log.e(TAG, "✗ Errore invio START a ${node.displayName}", e)
                        }.await()

                    } catch (e: Exception) {
                        Log.e(TAG, "Errore nell'invio messaggio a ${node.displayName}", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    _heartRateState.value = HeartRateState.Monitoring(
                        heartRate = 0,
                        calories = 0,
                        duration = "00:00"
                    )
                }

                Log.d(TAG, "Connessione Wear OS completata, in attesa di dati...")

            } catch (e: Exception) {
                Log.e(TAG, "Errore durante la connessione a Wear OS", e)
                withContext(Dispatchers.Main) {
                    _heartRateState.value = HeartRateState.Error("Errore di connessione: ${e.message}")
                }
                monitoring = false
            }
        }
    }

    fun stopMonitoring() {
        Log.d(TAG, "Stop monitoraggio richiesto")
        monitoring = false

        // Invia stop al Wear
        viewModelScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                for (node in nodes) {
                    messageClient.sendMessage(
                        node.id,
                        STOP_MONITORING_PATH,
                        "stop".toByteArray()
                    ).await()
                    Log.d(TAG, "Messaggio STOP inviato a ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore nell'invio del messaggio STOP", e)
            }
        }

        // Annulla la registrazione al sensore locale se presente
        if (heartRateSensor != null) {
            sensorManager.unregisterListener(this)
        }

        // NON rimuovere il listener qui per permettere riconnessioni
        // messageClient.removeListener(this)

        _heartRateState.value = HeartRateState.Idle
        startTime = null
        currentHeartRate = 0
        totalCaloriesBurned = 0f
    }

    // Listener per i messaggi da Wear OS
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "📨 Messaggio ricevuto: path=${messageEvent.path}, data=${String(messageEvent.data)}")

        when (messageEvent.path) {
            HEART_RATE_PATH -> {
                val heartRateStr = String(messageEvent.data)
                val heartRate = heartRateStr.toIntOrNull() ?: 0
                Log.d(TAG, "💓 Battito ricevuto: $heartRate BPM")

                // Aggiorna UI nel main thread
                viewModelScope.launch(Dispatchers.Main) {
                    updateHeartRate(heartRate)
                }
            }
            STATUS_PATH -> {
                val status = String(messageEvent.data)
                Log.d(TAG, "📊 Status ricevuto: $status")
            }
            "/pong" -> {
                Log.d(TAG, "🏓 PONG ricevuto dal Wear")
            }
            else -> {
                Log.d(TAG, "❓ Messaggio sconosciuto: ${messageEvent.path}")
            }
        }
    }

    // Listener per sensore locale
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_HEART_RATE && monitoring) {
            val heartRate = event.values[0].toInt()
            Log.d(TAG, "Sensore locale - Battito: $heartRate")
            updateHeartRate(heartRate)
        }
    }

    private fun testConnection() {
        viewModelScope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                for (node in nodes) {
                    Log.d(TAG, "🏓 Invio PING a ${node.displayName}")
                    messageClient.sendMessage(
                        node.id,
                        "/ping",
                        "test".toByteArray()
                    ).await()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore test ping", e)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "Accuratezza sensore cambiata: $accuracy")
    }

    private fun updateHeartRate(heartRate: Int) {
        if (!monitoring) {
            Log.d(TAG, "Update ignorato - monitoraggio non attivo")
            return
        }

        currentHeartRate = heartRate
        Log.d(TAG, "Aggiornamento battito: $heartRate BPM")

        val startTimeVal = startTime ?: run {
            Log.w(TAG, "startTime è null")
            return
        }

        // Calcola la durata
        val now = Instant.now()
        val duration = Duration.between(startTimeVal, now)
        val totalMinutes = duration.toMinutes()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        val durationFormatted = String.format("%02d:%02d", hours, minutes)

        // Calcola le calorie solo se è passato almeno 1 secondo dall'ultimo calcolo
        val currentTime = System.currentTimeMillis()
        if (heartRate > 0 && (currentTime - lastCalorieCalculationTime) >= 1000) {
            val secondsPassed = (currentTime - lastCalorieCalculationTime) / 1000f

            // Formula semplificata per le calorie
            // MET basato sul battito cardiaco
            val met = when {
                heartRate < 90 -> 2.0f
                heartRate < 110 -> 3.5f
                heartRate < 130 -> 5.0f
                heartRate < 150 -> 7.0f
                heartRate < 170 -> 9.0f
                else -> 11.0f
            }

            // Calorie = MET * peso(kg) * tempo(ore)
            val caloriesInPeriod = met * userWeight * (secondsPassed / 3600f)
            totalCaloriesBurned += caloriesInPeriod

            lastCalorieCalculationTime = currentTime
        }

        // Aggiorna lo stato
        _heartRateState.value = HeartRateState.Monitoring(
            heartRate = heartRate,
            calories = totalCaloriesBurned.toInt(),
            duration = durationFormatted
        )

        Log.d(TAG, "UI aggiornata: $heartRate BPM, ${totalCaloriesBurned.toInt()} cal, $durationFormatted")
    }

    fun isMonitoring(): Boolean = monitoring

    fun saveUserMeasurements(weight: Float, height: Float) {
        val sharedPrefs = getApplication<Application>().getSharedPreferences(
            "user_data",
            Context.MODE_PRIVATE
        )
        sharedPrefs.edit().apply {
            putFloat("weight", weight)
            putFloat("height", height)
            apply()
        }
    }

    fun getUserMeasurements(): UserMeasurements? {
        val sharedPrefs = getApplication<Application>().getSharedPreferences(
            "user_data",
            Context.MODE_PRIVATE
        )
        val weight = sharedPrefs.getFloat("weight", 0f)
        val height = sharedPrefs.getFloat("height", 0f)

        return if (weight > 0 && height > 0) {
            UserMeasurements(weight, height)
        } else null
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, rimuovo listener")
        messageClient.removeListener(this)
        if (heartRateSensor != null) {
            sensorManager.unregisterListener(this)
        }
    }

    data class UserMeasurements(val weight: Float, val height: Float)

    sealed class HeartRateState {
        object Idle : HeartRateState()
        object Connecting : HeartRateState()
        data class Monitoring(val heartRate: Int, val calories: Int, val duration: String) : HeartRateState()
        data class Error(val message: String) : HeartRateState()
    }
}