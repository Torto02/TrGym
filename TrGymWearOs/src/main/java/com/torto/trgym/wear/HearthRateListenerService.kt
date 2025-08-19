package com.torto.trgym.wear

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class HeartRateListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: ${messageEvent.path}")

        when (messageEvent.path) {
            "/start_heart_rate_monitoring" -> {
                // Start the activity if it's not running
                val intent = Intent(this, WearHeartRateActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            "/stop_heart_rate_monitoring" -> {
                // You could broadcast an intent to stop monitoring
                sendBroadcast(Intent("com.torto.trgym.STOP_MONITORING"))
            }
        }
    }

    companion object {
        private const val TAG = "HeartRateListenerService"
    }
}