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
import com.google.android.gms.wearable.DataClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.tasks.await



@HiltViewModel
class HeartRateViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application), DataClient.OnDataChangedListener, MessageClient.OnMessageReceivedListener, SensorEventListener {

    private val _heartRateState = MutableStateFlow<HeartRateState>(HeartRateState.Idle)
    val heartRateState: StateFlow<HeartRateState> = _heartRateState

    private var monitoring = false
    private var startTime: Instant? = null
    private var currentHeartRate = 0
    private var userWeight = 70f // Default
    private var userHeight = 170f // Default
    private var totalCaloriesBurned = 0f

    private val sensorManager by lazy {
        application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    private val heartRateSensor by lazy {
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
    }

    private val dataClient by lazy {
        Wearable.getDataClient(application)
    }

    private val messageClient by lazy {
        Wearable.getMessageClient(application)
    }

    private val bluetoothAdapter by lazy {
        (application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    // Metodo per avviare il monitoraggio
    fun startMonitoring(weight: Float, height: Float) {
        viewModelScope.launch {
            try {
                userWeight = weight
                userHeight = height
                monitoring = true
                startTime = Instant.now()
                totalCaloriesBurned = 0f

                _heartRateState.value = HeartRateState.Connecting

                // Tentativo di utilizzo del sensore locale se disponibile
                if (heartRateSensor != null) {
                    sensorManager.registerListener(
                        this@HeartRateViewModel,
                        heartRateSensor,
                        SensorManager.SENSOR_DELAY_NORMAL
                    )
                    Log.d(TAG, "Sensore battito cardiaco locale registrato")
                } else {
                    // Connessione a Wear OS
                    connectToWearOS()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore durante l'avvio del monitoraggio", e)
                _heartRateState.value =
                    HeartRateState.Error("Impossibile avviare il monitoraggio: ${e.message}")
                monitoring = false
            }
        }
    }

    // Metodo per fermare il monitoraggio
    fun stopMonitoring() {
        monitoring = false

        // Annulla la registrazione al sensore locale
        if (heartRateSensor != null) {
            sensorManager.unregisterListener(this)
        }

        // Disconnetti da Wear OS
        dataClient.removeListener(this)
        messageClient.removeListener(this)

        _heartRateState.value = HeartRateState.Idle
        startTime = null
    }

    suspend fun connectToWearOS() {
        withContext(Dispatchers.IO) {
            try {
                if (!bluetoothAdapter.isEnabled) {
                    _heartRateState.value =
                        HeartRateState.Error("Bluetooth non abilitato. Attiva il Bluetooth nelle impostazioni.")
                    monitoring = false
                    return@withContext
                }

                dataClient.addListener(this@HeartRateViewModel)
                messageClient.addListener(this@HeartRateViewModel)

                // Ottieni i nodi Wear OS connessi
                Log.d(TAG, "Cercando dispositivi Wear OS connessi...")
                val nodes = Wearable.getNodeClient(getApplication()).connectedNodes.await()

                if (nodes.isEmpty()) {
                    Log.e(TAG, "Nessun dispositivo Wear OS connesso")
                    _heartRateState.value =
                        HeartRateState.Error("Nessun dispositivo Wear OS connesso. Assicurati che l'orologio sia associato correttamente.")
                    monitoring = false
                    return@withContext
                }

                Log.d(
                    TAG,
                    "Trovati ${nodes.size} dispositivi: ${nodes.joinToString { it.displayName }}"
                )

                for (node in nodes) {
                    Log.d(TAG, "Invio richiesta monitoraggio a ${node.displayName}")
                    messageClient.sendMessage(
                        node.id,
                        "/start_heart_rate_monitoring",
                        null
                    ).await()
                    Log.d(TAG, "Messaggio inviato a ${node.displayName}")
                }

                _heartRateState.value = HeartRateState.Monitoring(
                    heartRate = 0,
                    calories = 0,
                    duration = "00:00"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Errore durante la connessione a Wear OS", e)
                _heartRateState.value = HeartRateState.Error("Errore di connessione: ${e.message}")
                monitoring = false
            }
        }
    }

    // Aggiungi questo nel companion object
    companion object {
        private const val TAG = "HeartRateViewModel"
    }



        // Implementazione di SensorEventListener per sensori locali
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_HEART_RATE && monitoring) {
                val heartRate = event.values[0].toInt()
                updateHeartRate(heartRate)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Non serve implementazione
        }

        // Implementazione di DataClient.OnDataChangedListener
        override fun onDataChanged(dataEvents: DataEventBuffer) {
            for (event in dataEvents) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (item.uri.path == "/heart_rate") {
                        DataMapItem.fromDataItem(item).dataMap.apply {
                            if (containsKey("heart_rate")) {
                                val heartRate = getInt("heart_rate")
                                updateHeartRate(heartRate)
                            }
                        }
                    }
                }
            }
        }

        // Implementazione di MessageClient.OnMessageReceivedListener
        override fun onMessageReceived(messageEvent: MessageEvent) {
            if (messageEvent.path == "/heart_rate_data") {
                val heartRate = String(messageEvent.data).toIntOrNull() ?: 0
                updateHeartRate(heartRate)
            }
        }

        private fun updateHeartRate(heartRate: Int) {
            if (!monitoring) return

            currentHeartRate = heartRate

            // Calcola le calorie bruciate utilizzando la formula
            // Formula: ((-55.0969 + (0.6309 x HR) + (0.1988 x W) + (0.2017 x A)) / 4.184) x 60 x T
            // HR = Heart Rate, W = Weight (kg), A = Age (anni), T = durata in ore

            // Per semplicità usiamo una formula più basica:
            // Calories = (HR x 0.035 x Weight (kg)) / 200 * time_in_minutes

            val startTimeVal = startTime ?: return
            val durationMinutes =
                Duration.between(startTimeVal, Instant.now()).toMinutes().toFloat() / 60f

            if (heartRate > 0) {
                // Calorie bruciate nella frazione di tempo corrente
                val caloriesPerMinute = (heartRate * 0.035f * userWeight) / 200f

                // Aggiorna il totale
                totalCaloriesBurned += caloriesPerMinute

                // Durata formattata
                val hours = durationMinutes.toInt()
                val minutes = ((durationMinutes - hours) * 60).toInt()
                val durationFormatted = String.format("%02d:%02d", hours, minutes)

                _heartRateState.value = HeartRateState.Monitoring(
                    heartRate = heartRate,
                    calories = totalCaloriesBurned.toInt(),
                    duration = durationFormatted
                )
            }
        }

        fun isMonitoring(): Boolean = monitoring

        // Metodi per salvare e recuperare i dati dell'utente
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

        data class UserMeasurements(val weight: Float, val height: Float)

        sealed class HeartRateState {
            object Idle : HeartRateState()
            object Connecting : HeartRateState()
            data class Monitoring(val heartRate: Int, val calories: Int, val duration: String) :
                HeartRateState()

            data class Error(val message: String) : HeartRateState()
        }


    }
