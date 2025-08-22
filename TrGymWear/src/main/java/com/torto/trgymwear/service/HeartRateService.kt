//package com.torto.trgymwear.service
//
//import android.app.Notification
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.hardware.Sensor
//import android.hardware.SensorEvent
//import android.hardware.SensorEventListener
//import android.hardware.SensorManager
//import android.os.IBinder
//import android.util.Log
//import androidx.core.app.NotificationCompat
//import com.google.android.gms.wearable.PutDataMapRequest
//import com.google.android.gms.wearable.Wearable
//import com.torto.trgymwear.R
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//
//class HeartRateService : Service(), SensorEventListener {
//
//    private lateinit var sensorManager: SensorManager
//    private var heartRateSensor: Sensor? = null
//    private var currentHeartRate = 0
//
//    companion object {
//        private const val CHANNEL_ID = "heart_rate_channel"
//        private const val NOTIFICATION_ID = 1
//        private const val HEART_RATE_PATH = "/heart_rate"
//        private const val TAG = "HeartRateService"
//
//        fun startService(context: Context) {
//            val intent = Intent(context, HeartRateService::class.java)
//            context.startForegroundService(intent)
//        }
//
//        fun stopService(context: Context) {
//            val intent = Intent(context, HeartRateService::class.java)
//            context.stopService(intent)
//        }
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        createNotificationChannel()
//        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
//        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        startForeground(NOTIFICATION_ID, createNotification())
//        startHeartRateMonitoring()
//        return START_STICKY // Il servizio si riavvia se viene terminato dal sistema
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        stopHeartRateMonitoring()
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//
//    private fun createNotificationChannel() {
//        val channel = NotificationChannel(
//            CHANNEL_ID,
//            "Heart Rate Monitor",
//            NotificationManager.IMPORTANCE_LOW
//        ).apply {
//            description = "Monitoraggio continuo del battito cardiaco"
//            setShowBadge(false)
//        }
//
//        val notificationManager = getSystemService(NotificationManager::class.java)
//        notificationManager.createNotificationChannel(channel)
//    }
//
//    private fun createNotification(): Notification {
//        return NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle("TR Gym Wear")
//            .setContentText("Monitoraggio battito: ${if (currentHeartRate > 0) "$currentHeartRate BPM" else "In attesa..."}")
//            .setSmallIcon(R.drawable.heart_pulse) // Assicurati di avere questa icona
//            .setOngoing(true)
//            .setSilent(true)
//            .build()
//    }
//
//    private fun updateNotification() {
//        val notification = createNotification()
//        val notificationManager = getSystemService(NotificationManager::class.java)
//        notificationManager.notify(NOTIFICATION_ID, notification)
//    }
//
//    private fun startHeartRateMonitoring() {
//        heartRateSensor?.let { sensor ->
//            val registered = sensorManager.registerListener(
//                this,
//                sensor,
//                SensorManager.SENSOR_DELAY_NORMAL
//            )
//            Log.d(TAG, "Sensore registrato: $registered")
//        } ?: run {
//            Log.e(TAG, "Sensore battito cardiaco non disponibile")
//        }
//    }
//
//    private fun stopHeartRateMonitoring() {
//        sensorManager.unregisterListener(this)
//        Log.d(TAG, "Sensore disregistrato")
//    }
//
//    override fun onSensorChanged(event: SensorEvent?) {
//        event?.let {
//            if (it.sensor.type == Sensor.TYPE_HEART_RATE && it.values.isNotEmpty()) {
//                currentHeartRate = it.values[0].toInt()
//                Log.d(TAG, "Nuovo battito: $currentHeartRate BPM")
//
//                // Aggiorna la notifica
//                updateNotification()
//
//                // Invia al telefono
//                sendHeartRateToPhone(currentHeartRate)
//            }
//        }
//    }
//
//    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
//        Log.d(TAG, "Accuratezza sensore cambiata: $accuracy")
//    }
//
//    private fun sendHeartRateToPhone(heartRate: Int) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val putDataMapReq = PutDataMapRequest.create(HEART_RATE_PATH)
//                putDataMapReq.dataMap.putInt("heart_rate", heartRate)
//                putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())
//
//                val putDataReq = putDataMapReq.asPutDataRequest()
//                putDataReq.setUrgent()
//
//                Wearable.getDataClient(this@HeartRateService).putDataItem(putDataReq)
//                    .addOnSuccessListener {
//                        Log.d(TAG, "Dati inviati al telefono: $heartRate BPM")
//                    }
//                    .addOnFailureListener { exception ->
//                        Log.e(TAG, "Errore invio dati: ${exception.message}")
//                    }
//            } catch (e: Exception) {
//                Log.e(TAG, "Eccezione durante l'invio", e)
//            }
//        }
//    }
//}


package com.torto.trgymwear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.torto.trgymwear.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

class HeartRateService : Service() {

    private var currentHeartRate = 0
    private var simulationHandler: Handler? = null
    private var simulationRunnable: Runnable? = null
    private var isSimulating = false

    companion object {
        private const val CHANNEL_ID = "heart_rate_channel"
        private const val NOTIFICATION_ID = 1
        private const val HEART_RATE_PATH = "/heart_rate"
        private const val TAG = "HeartRateService"

        // Parametri per la simulazione
        private const val SIMULATION_INTERVAL = 1000L // Aggiorna ogni 2 secondi
        private const val BASE_HEART_RATE = 70 // BPM base
        private const val HEART_RATE_VARIATION = 35 // Variazione massima (+/-)

        fun startService(context: Context) {
            val intent = Intent(context, HeartRateService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, HeartRateService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupSimulation()
        Log.d(TAG, "HeartRateService creato - modalità simulazione")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startHeartRateSimulation()
        return START_STICKY // Il servizio si riavvia se viene terminato dal sistema
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHeartRateSimulation()
        Log.d(TAG, "HeartRateService distrutto")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Heart Rate Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monitoraggio continuo del battito cardiaco (simulato)"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val statusText = if (currentHeartRate > 0) {
            "Battito: $currentHeartRate BPM (simulato)"
        } else {
            "Avvio simulazione..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TR Gym Wear")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.heart_pulse)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun setupSimulation() {
        simulationHandler = Handler(Looper.getMainLooper())
        simulationRunnable = object : Runnable {
            override fun run() {
                if (isSimulating) {
                    generateSimulatedHeartRate()
                    simulationHandler?.postDelayed(this, SIMULATION_INTERVAL)
                }
            }
        }
    }

    private fun startHeartRateSimulation() {
        if (!isSimulating) {
            isSimulating = true
            Log.d(TAG, "Avvio simulazione battito cardiaco")

            // Genera il primo valore dopo un piccolo delay
            simulationHandler?.postDelayed(simulationRunnable!!, 1000L)
        }
    }

    private fun stopHeartRateSimulation() {
        isSimulating = false
        simulationHandler?.removeCallbacks(simulationRunnable!!)
        Log.d(TAG, "Simulazione battito cardiaco fermata")
    }

    private fun generateSimulatedHeartRate() {
        // Genera un battito realistico con variazioni graduali
        val variation = Random.nextInt(-HEART_RATE_VARIATION, HEART_RATE_VARIATION + 1)
        val newHeartRate = BASE_HEART_RATE + variation

        // Assicurati che il valore sia in un range realistico (50-120 BPM)
        currentHeartRate = newHeartRate.coerceIn(60, 120)

        Log.d(TAG, "Battito simulato generato: $currentHeartRate BPM")

        // Aggiorna la notifica
        updateNotification()

        // Invia al telefono
        sendHeartRateToPhone(currentHeartRate)
    }

    private fun sendHeartRateToPhone(heartRate: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val putDataMapReq = PutDataMapRequest.create(HEART_RATE_PATH)
                putDataMapReq.dataMap.putInt("heart_rate", heartRate)
                putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())

                val putDataReq = putDataMapReq.asPutDataRequest()
                putDataReq.setUrgent()

                Wearable.getDataClient(this@HeartRateService).putDataItem(putDataReq)
                    .addOnSuccessListener {
                        Log.d(TAG, "Dati simulati inviati al telefono: $heartRate BPM")
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Errore invio dati simulati: ${exception.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Eccezione durante l'invio dati simulati", e)
            }
        }
    }
}