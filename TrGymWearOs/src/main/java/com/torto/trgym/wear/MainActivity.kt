package com.torto.trgym.wear

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class WearHeartRateActivity : Activity(), MessageClient.OnMessageReceivedListener {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var heartRateValue: TextView
    private lateinit var statusText: TextView
    private lateinit var connectionStatus: TextView
    private var monitoring = false
    private var simulating = false

    // Client per inviare messaggi
    private val messageClient: MessageClient by lazy {
        Wearable.getMessageClient(this)
    }

    private val nodeClient by lazy {
        Wearable.getNodeClient(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_heart_rate)

        heartRateValue = findViewById(R.id.heartRateValue)
        statusText = findViewById(R.id.statusText)

        // Aggiungi un TextView per lo stato della connessione (opzionale)
        // Se non c'è nel layout, commentalo
        // connectionStatus = findViewById(R.id.connectionStatus)

        // Registra il listener per i messaggi in arrivo
        messageClient.addListener(this)
        Log.d(TAG, "Activity creata, listener registrato")

        // Aggiungi bottoni per test manuale
        setupDebugButtons()

        // Avvia automaticamente la simulazione
        startSimulation()

        // Controlla se è stato richiesto di iniziare dal servizio
        if (intent.getBooleanExtra("START_MONITORING", false)) {
            Log.d(TAG, "Avvio richiesto dal servizio")
            startSimulation()
        }
    }

    private fun setupDebugButtons() {
        val debugLayout = findViewById<LinearLayout>(R.id.debugControlsLayout)
        if (debugLayout != null) {
            // Bottone per start/stop simulazione
            val toggleButton = Button(this)
            toggleButton.text = if (simulating) "Stop Simulazione" else "Avvia Simulazione"
            toggleButton.setOnClickListener {
                if (simulating) {
                    stopMonitoring()
                } else {
                    startSimulation()
                }
                toggleButton.text = if (simulating) "Stop Simulazione" else "Avvia Simulazione"
            }
            debugLayout.addView(toggleButton)

            // Bottone per test invio singolo
            val testButton = Button(this)
            testButton.text = "Test Invio 75 BPM"
            testButton.setOnClickListener {
                Log.d(TAG, "Test invio manuale")
                sendHeartRateData(75)
            }
            debugLayout.addView(testButton)

            // Bottone per verificare connessione
            val checkButton = Button(this)
            checkButton.text = "Verifica Connessione"
            checkButton.setOnClickListener {
                checkConnection()
            }
            debugLayout.addView(checkButton)
        }
    }

    private fun checkConnection() {
        scope.launch {
            try {
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) {
                    statusText.text = "Nessun telefono connesso"
                    Log.w(TAG, "Nessun nodo connesso")
                } else {
                    val nodeNames = nodes.joinToString(", ") { it.displayName }
                    statusText.text = "Connesso a: $nodeNames"
                    Log.d(TAG, "Connesso a: $nodeNames")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore verifica connessione", e)
                statusText.text = "Errore connessione"
            }
        }
    }

    private fun startSimulation() {
        if (simulating) {
            Log.d(TAG, "Simulazione già attiva")
            return
        }

        monitoring = true
        simulating = true
        statusText.text = "Simulazione attiva"
        Log.d(TAG, "Avvio simulazione battito cardiaco")

        scope.launch {
            // Prima verifica la connessione
            checkConnection()
            delay(500)

            var heartRate = 70
            var trend = 1

            while (monitoring && simulating) {
                // Genera battito variabile
                if (Random.nextFloat() < 0.1) trend *= -1
                heartRate += trend * Random.nextInt(0, 3)
                heartRate = heartRate.coerceIn(58, 95)

                // Aggiorna UI locale
                heartRateValue.text = heartRate.toString()
                statusText.text = "BPM: $heartRate - Invio..."

                // IMPORTANTE: Invia i dati al telefono!
                sendHeartRateData(heartRate)

                // Attendi prima del prossimo battito
                delay(1000)
            }
        }
    }

    private fun sendHeartRateData(heartRate: Int) {
        scope.launch {
            try {
                Log.d(TAG, "Tentativo invio battito: $heartRate BPM")

                // Ottieni i nodi connessi (telefoni/tablet)
                val nodes = nodeClient.connectedNodes.await()

                if (nodes.isEmpty()) {
                    Log.w(TAG, "❌ Nessun dispositivo connesso per inviare dati!")
                    statusText.text = "BPM: $heartRate - No phone"
                    return@launch
                }

                Log.d(TAG, "📱 Trovati ${nodes.size} dispositivi connessi")

                // Invia a tutti i dispositivi connessi
                for (node in nodes) {
                    Log.d(TAG, "📤 Invio a: ${node.displayName} (${node.id})")

                    try {
                        messageClient.sendMessage(
                            node.id,
                            "/heart_rate_data",  // IMPORTANTE: deve corrispondere al path nel ViewModel
                            heartRate.toString().toByteArray()
                        ).addOnSuccessListener {
                            Log.d(TAG, "✅ Battito $heartRate inviato con successo a ${node.displayName}")
                            runOnUiThread {
                                statusText.text = "BPM: $heartRate - Inviato ✓"
                            }
                        }.addOnFailureListener { e ->
                            Log.e(TAG, "❌ Errore invio a ${node.displayName}", e)
                            runOnUiThread {
                                statusText.text = "BPM: $heartRate - Errore invio"
                            }
                        }.await()
                    } catch (e: Exception) {
                        Log.e(TAG, "Errore invio messaggio a ${node.displayName}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore generale nell'invio del battito", e)
                runOnUiThread {
                    statusText.text = "Errore invio dati"
                }
            }
        }
    }

    private fun stopMonitoring() {
        Log.d(TAG, "Stop monitoraggio")
        monitoring = false
        simulating = false
        statusText.text = "Monitoraggio fermato"
        heartRateValue.text = "--"
    }

    // Ricevi messaggi dal telefono
    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "📨 Messaggio ricevuto: ${messageEvent.path}")

        when (messageEvent.path) {
            "/start_heart_rate_monitoring" -> {
                Log.d(TAG, "Ricevuto comando START dal telefono")
                runOnUiThread {
                    if (!simulating) {
                        startSimulation()
                    }
                }
            }
            "/stop_heart_rate_monitoring" -> {
                Log.d(TAG, "Ricevuto comando STOP dal telefono")
                runOnUiThread {
                    stopMonitoring()
                }
            }
            "/ping" -> {
                Log.d(TAG, "Ricevuto PING dal telefono")
                // Rispondi con pong
                scope.launch {
                    try {
                        messageClient.sendMessage(
                            messageEvent.sourceNodeId,
                            "/pong",
                            "alive".toByteArray()
                        ).await()
                        Log.d(TAG, "PONG inviato")
                    } catch (e: Exception) {
                        Log.e(TAG, "Errore invio PONG", e)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        messageClient.addListener(this)
        Log.d(TAG, "onResume - listener riattivato")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        monitoring = false
        simulating = false
        scope.cancel()
        messageClient.removeListener(this)
    }

    companion object {
        private const val TAG = "WearHeartRateActivity"
    }
}