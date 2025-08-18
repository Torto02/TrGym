package com.torto.trgym.wear.presentation

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.torto.trgym.wear.presentation.WearHeartRateActivity

class WearHeartRateService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            "/start_heart_rate_monitoring" -> {
                Log.d(TAG, "Ricevuto messaggio per avviare il monitoraggio")
                // Avvia l'activity se non è già in esecuzione
                val startIntent = Intent(this, WearHeartRateActivity::class.java)
                startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(startIntent)
            }
            "/stop_heart_rate_monitoring" -> {
                Log.d(TAG, "Ricevuto messaggio per fermare il monitoraggio")
                // L'activity gestirà il messaggio se è in esecuzione
            }
        }
    }

    companion object {
        private const val TAG = "WearHeartRateService"
    }
}