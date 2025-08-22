package com.torto.trgymwear.presentation

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.torto.trgymwear.R
import com.torto.trgymwear.service.HeartRateService

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private lateinit var heartRateText: TextView
    private lateinit var startServiceBtn: Button
    private lateinit var stopServiceBtn: Button
    private lateinit var heartIcon: ImageView
    private lateinit var requestPermissionBtn: Button

    private var isServiceRunning = false

    companion object {
        private const val HEART_RATE_PATH = "/heart_rate"
        private const val TAG = "WearableMainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupAnimations()
        setupPermissionHandling()
    }

    private fun initViews() {
        heartRateText = findViewById(R.id.heartRateText)
        startServiceBtn = findViewById(R.id.startServiceBtn)
        stopServiceBtn = findViewById(R.id.stopServiceBtn)
        heartIcon = findViewById(R.id.heartIcon)
        requestPermissionBtn = findViewById(R.id.requestPermissionBtn)
    }

    private fun setupAnimations() {
        val anim = AnimationUtils.loadAnimation(this, R.anim.heartbeat)
        heartIcon.startAnimation(anim)
    }

    private fun setupPermissionHandling() {
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            updatePermissionStatus(isGranted)
        }

        requestPermissionBtn.setOnClickListener {
            if (!hasHeartRatePermission() &&
                !ActivityCompat.shouldShowRequestPermissionRationale(this, "android.permission.health.READ_HEART_RATE")) {
                // Il permesso è stato negato definitivamente, apri le impostazioni
                openAppSettings()
            } else {
                // Prova ancora a richiedere il permesso
                requestPermissionLauncher.launch("android.permission.health.READ_HEART_RATE")
            }
        }

        startServiceBtn.setOnClickListener {
            if (hasHeartRatePermission()) {
                startHeartRateService()
            } else {
                requestPermissionLauncher.launch("android.permission.health.READ_HEART_RATE")
            }
        }

        stopServiceBtn.setOnClickListener {
            stopHeartRateService()
        }

        // Richiede automaticamente il permesso se non concesso
        checkAndRequestPermissionAutomatically(requestPermissionLauncher)
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        updateUI()

        // Controlla se l'utente ha concesso il permesso dalle impostazioni
        if (hasHeartRatePermission()) {
            updatePermissionStatus(true)
        }
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
    }

    private fun hasHeartRatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            "android.permission.health.READ_HEART_RATE"
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkAndRequestPermissionAutomatically(
        requestPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
    ) {
        if (hasHeartRatePermission()) {
            updatePermissionStatus(true)
        } else {
            // Richiede automaticamente il permesso senza mostrare bottoni
//            permissionStatusText.text = "Richiesta permesso battito cardiaco..."
            requestPermissionLauncher.launch("android.permission.health.READ_HEART_RATE")
        }
    }

    private fun updatePermissionStatus(isGranted: Boolean) {
        if (isGranted) {
            heartRateText.text = "Start heart rate monitoring"
            heartRateText.gravity = android.view.Gravity.CENTER
//            permissionStatusText.text = ""
            requestPermissionBtn.visibility = Button.GONE
            updateServiceButtons(true)
        } else {
            heartRateText.text = "Permission Required to Read Heart Rate"
            heartRateText.gravity = android.view.Gravity.CENTER

            // Controlla se il permesso è stato negato definitivamente
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, "android.permission.health.READ_HEART_RATE")) {
                requestPermissionBtn.text = "Enable in Settings"
                requestPermissionBtn.gravity = android.view.Gravity.CENTER
            } else {
                requestPermissionBtn.text = "Permission Request"
                requestPermissionBtn.gravity = android.view.Gravity.CENTER
            }

            requestPermissionBtn.visibility = Button.VISIBLE
            updateServiceButtons(false)
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun updateServiceButtons(permissionGranted: Boolean) {
        if (permissionGranted) {
            startServiceBtn.visibility = if (isServiceRunning) Button.GONE else Button.VISIBLE
            stopServiceBtn.visibility = if (isServiceRunning) Button.VISIBLE else Button.GONE
        } else {
            startServiceBtn.visibility = Button.GONE
            stopServiceBtn.visibility = Button.GONE
        }
    }

    private fun updateUI() {
        val permissionGranted = hasHeartRatePermission()
        updatePermissionStatus(permissionGranted)
    }

    private fun startHeartRateService() {
        try {
            HeartRateService.startService(this)
            isServiceRunning = true
            updateServiceButtons(true)
//            heartRateText.text = "Service started - waiting data..."
        } catch (e: Exception) {
            //
        }
    }

    private fun stopHeartRateService() {
        try {
            HeartRateService.stopService(this)
            isServiceRunning = false
            updateServiceButtons(true)
            heartRateText.text = "-- BPM"
        } catch (e: Exception) {
            Log.e(TAG, "Errore stop servizio", e)
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged ricevuto")

        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                if (item.uri.path?.compareTo(HEART_RATE_PATH) == 0) {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val heartRate = dataMap.getInt("heart_rate")

                    runOnUiThread {
                        heartRateText.text = "$heartRate BPM"
                    }
                }
            }
        }
    }
}