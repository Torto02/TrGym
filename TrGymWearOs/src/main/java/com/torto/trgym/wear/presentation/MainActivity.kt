package com.torto.trgym.wear.presentation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.torto.trgym.wear.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearHeartRateActivity : Activity(), SensorEventListener, MessageClient.OnMessageReceivedListener {
    private lateinit var sensorManager: SensorManager
    private var heartRateSensor: Sensor? = null
    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(this) }

    private var monitoring = false
    private val monitoringJob = Job()
    private val scope = CoroutineScope(Dispatchers.Main + monitoringJob)

    private lateinit var heartRateValue: TextView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_rate)

        heartRateValue = findViewById(R.id.heartRateValue)
        statusText = findViewById(R.id.statusText)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (heartRateSensor == null) {
            heartRateValue.text = "--"
            statusText.text = "Sensore non disponibile"
            Log.e(TAG, "Sensore battito cardiaco non disponibile sul dispositivo")
        } else {
            statusText.text = "Inizializzazione..."
            // Verifica e richiedi i permessi
            checkAndRequestSensorPermission()
        }

        // Registra listener per i messaggi in arrivo
        messageClient.addListener(this)
    }

    private fun checkAndRequestSensorPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BODY_SENSORS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Richiesta permesso BODY_SENSORS")
            // Richiesta permesso
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BODY_SENSORS),
                SENSOR_PERMISSION_REQUEST_CODE
            )
        } else {
            Log.d(TAG, "Permesso BODY_SENSORS già concesso")
            startMonitoring()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == SENSOR_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permesso BODY_SENSORS concesso")
                startMonitoring()
            } else {
                Log.e(TAG, "Permesso BODY_SENSORS negato")
                statusText.text = "Permesso sensore negato"
            }
        }
    }

    private fun startMonitoring() {
        if (heartRateSensor == null) {
            Log.e(TAG, "Sensore battito cardiaco non disponibile")
            statusText.text = "Sensore non disponibile"
            return
        }

        monitoring = true
        statusText.text = "Monitoraggio attivo"
        Log.d(TAG, "Avvio monitoraggio battito cardiaco")

        // Test UI update - verifica che l'UI si aggiorni correttamente
        heartRateValue.text = "--"

        sensorManager.registerListener(
            this,
            heartRateSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        // Invia messaggi periodici per indicare che è attivo
        scope.launch {
            while (monitoring) {
                sendAliveMessage()
                delay(5000) // Ogni 5 secondi
            }
        }
    }

    private fun stopMonitoring() {
        monitoring = false
        statusText.text = "Monitoraggio in pausa"
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Monitoraggio fermato")
    }

    private fun sendAliveMessage() {
        scope.launch {
            try {
                val nodes = Wearable.getNodeClient(this@WearHeartRateActivity).connectedNodes.await()
                for (node in nodes) {
                    messageClient.sendMessage(
                        node.id,
                        "/heart_rate_status",
                        "active".toByteArray()
                    ).await()
                    Log.d(TAG, "Messaggio status inviato a ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore nell'invio del messaggio", e)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_HEART_RATE) {
            val heartRate = event.values[0].toInt()
            Log.d(TAG, "Rilevato battito: $heartRate")

            // Aggiorna UI
            heartRateValue.text = heartRate.toString()

            // Invia il battito cardiaco all'app mobile
            scope.launch {
                try {
                    val nodes = Wearable.getNodeClient(this@WearHeartRateActivity).connectedNodes.await()
                    for (node in nodes) {
                        messageClient.sendMessage(
                            node.id,
                            "/heart_rate_data",
                            heartRate.toString().toByteArray()
                        ).await()
                        Log.d(TAG, "Battito $heartRate inviato a ${node.displayName}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Errore nell'invio del battito cardiaco", e)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val accuracyText = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Precisione bassa"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Precisione media"
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Precisione alta"
            else -> "Precisione sconosciuta"
        }

        Log.d(TAG, "Precisione sensore: $accuracyText")
        statusText.text = accuracyText
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Messaggio ricevuto: ${messageEvent.path}")
        when (messageEvent.path) {
            "/start_heart_rate_monitoring" -> {
                scope.launch { startMonitoring() }
            }
            "/stop_heart_rate_monitoring" -> {
                scope.launch { stopMonitoring() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        messageClient.addListener(this)
        if (!monitoring && heartRateSensor != null &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED) {
            startMonitoring()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        // Mantenere il monitoraggio anche in background
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        stopMonitoring()
        monitoringJob.cancel()
        messageClient.removeListener(this)
    }

    companion object {
        private const val TAG = "WearHeartRateActivity"
        private const val SENSOR_PERMISSION_REQUEST_CODE = 100
    }
}