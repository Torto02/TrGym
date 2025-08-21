
package com.torto.trgymwear.presentation

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.torto.trgymwear.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private lateinit var permissionStatusText: TextView
    private lateinit var heartRateText: TextView
    private lateinit var requestPermissionBtn: Button
    private lateinit var heartIcon: ImageView

    companion object {
        private const val HEART_RATE_PATH = "/heart_rate"
        private const val TAG = "WearableMainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Collega le view
        permissionStatusText = findViewById(R.id.permissionStatusText)
        heartRateText = findViewById(R.id.heartRateText)
        requestPermissionBtn = findViewById(R.id.requestPermissionBtn)
        heartIcon = findViewById(R.id.heartIcon)

        // Avvia animazione cuore
        val anim = AnimationUtils.loadAnimation(this, R.anim.heartbeat)
        heartIcon.startAnimation(anim)

        // Launcher per richiedere il permesso
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            updatePermissionStatus(isGranted)
        }

        requestPermissionBtn.setOnClickListener {
            requestPermissionLauncher.launch("android.permission.health.READ_HEART_RATE")
        }

        // Verifica subito il permesso
        checkAndRequestPermission(requestPermissionLauncher)
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)

        // Ricontrolla permessi
        if (ContextCompat.checkSelfPermission(
                this,
                "android.permission.health.READ_HEART_RATE"
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            updatePermissionStatus(true)
        }
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    private fun checkAndRequestPermission(
        requestPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
    ) {
        when {
            ContextCompat.checkSelfPermission(
                this,
                "android.permission.health.READ_HEART_RATE"
            ) == PackageManager.PERMISSION_GRANTED -> {
                updatePermissionStatus(true)
            }
            else -> {
                permissionStatusText.text = "Permesso non concesso."
                requestPermissionBtn.visibility = Button.VISIBLE
                requestPermissionLauncher.launch("android.permission.health.READ_HEART_RATE")
            }
        }
    }

    private fun updatePermissionStatus(isGranted: Boolean) {
        if (isGranted) {
            permissionStatusText.text = "Permesso BODY_SENSORS concesso!"
            requestPermissionBtn.visibility = Button.GONE
            startHeartRateSensor()
        } else {
            permissionStatusText.text = "Permesso BODY_SENSORS negato."
            heartRateText.text = "Sensore non disponibile"
            requestPermissionBtn.visibility = Button.VISIBLE
        }
    }

    private fun startHeartRateSensor() {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (heartRateSensor != null) {
            sensorManager.registerListener(
                object : SensorEventListener {
                    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                        event?.let {
                            if (it.sensor.type == Sensor.TYPE_HEART_RATE) {
                                val heartRate = it.values[0].toInt()
                                heartRateText.text = "$heartRate BPM"
                                sendHeartRateToPhone(heartRate)
                            }
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                },
                heartRateSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        } else {
            heartRateText.text = "Sensore non disponibile"
        }
    }

    private fun sendHeartRateToPhone(heartRate: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val putDataMapReq = PutDataMapRequest.create(HEART_RATE_PATH)
                putDataMapReq.dataMap.putInt("heart_rate", heartRate)
                putDataMapReq.dataMap.putLong("timestamp", System.currentTimeMillis())

                val putDataReq = putDataMapReq.asPutDataRequest()
                putDataReq.setUrgent()

                Wearable.getDataClient(this@MainActivity).putDataItem(putDataReq)
                    .addOnSuccessListener {
                        Log.d(TAG, "Dati inviati: $heartRate BPM")
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Errore invio: ${exception.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Eccezione invio", e)
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged ricevuto")
    }
}
