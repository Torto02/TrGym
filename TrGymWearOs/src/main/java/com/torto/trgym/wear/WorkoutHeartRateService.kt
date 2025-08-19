package com.torto.trgym.wear

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Servizio che riceve messaggi anche quando l'app non è in primo piano.
 * Questo è importante per permettere all'app mobile di avviare
 * il monitoraggio del battito cardiaco.
 */
class WearHeartRateService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Messaggio ricevuto nel servizio: ${messageEvent.path}")
        Log.d(TAG, "Da nodo: ${messageEvent.sourceNodeId}")

        when (messageEvent.path) {
            "/start_heart_rate_monitoring" -> {
                Log.d(TAG, "Ricevuto comando START monitoraggio")
                // Avvia l'activity se non è già in esecuzione
                val startIntent = Intent(this, WearHeartRateActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    // Passa un extra per indicare che deve iniziare il monitoraggio
                    putExtra("START_MONITORING", true)
                }
                startActivity(startIntent)
            }
            "/stop_heart_rate_monitoring" -> {
                Log.d(TAG, "Ricevuto comando STOP monitoraggio")
                // L'activity gestirà il messaggio se è in esecuzione
                // Potremmo anche inviare un broadcast se necessario
                val stopIntent = Intent("com.torto.trgym.wear.STOP_MONITORING")
                sendBroadcast(stopIntent)
            }
            "/ping" -> {
                Log.d(TAG, "Ricevuto PING dal telefono")
                // Rispondi con un pong per confermare la connessione
                sendPongResponse(messageEvent.sourceNodeId)
            }
        }
    }

    private fun sendPongResponse(nodeId: String) {
        try {
            val messageClient = com.google.android.gms.wearable.Wearable.getMessageClient(this)
            messageClient.sendMessage(
                nodeId,
                "/pong",
                "alive".toByteArray()
            ).addOnSuccessListener {
                Log.d(TAG, "PONG inviato con successo")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Errore nell'invio del PONG", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore nel sendPongResponse", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Servizio creato")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Servizio distrutto")
    }

    companion object {
        private const val TAG = "WearHeartRateService"
    }
}