package com.torto.trgym.service

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class HeartRateDataListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "HeartRateDataListener"
        private const val HEART_RATE_PATH = "/heart_rate"
        const val ACTION_HEART_RATE_RECEIVED = "com.torto.trgym.HEART_RATE_RECEIVED"
        const val EXTRA_HEART_RATE = "heart_rate"
        const val EXTRA_TIMESTAMP = "timestamp"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "onDataChanged called with ${dataEvents.count} events")
        
        for (event in dataEvents) {
            Log.d(TAG, "Processing event type: ${event.type}, path: ${event.dataItem.uri.path}")
            
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                Log.d(TAG, "Data item path: ${dataItem.uri.path}, expected: $HEART_RATE_PATH")
                
                if (dataItem.uri.path == HEART_RATE_PATH) {
                    Log.d(TAG, "Path matches! Processing heart rate data...")
                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                    processHeartRateData(dataMap)
                } else {
                    Log.w(TAG, "Path doesn't match. Received: ${dataItem.uri.path}, expected: $HEART_RATE_PATH")
                }
            } else {
                Log.d(TAG, "Event type is not TYPE_CHANGED: ${event.type}")
            }
        }
    }

    private fun processHeartRateData(dataMap: DataMap) {
        try {
            Log.d(TAG, "Processing heart rate data from DataMap")
            Log.d(TAG, "DataMap keys: ${dataMap.keySet()}")
            
            val heartRate = dataMap.getInt("heart_rate", -1)
            val timestamp = dataMap.getLong("timestamp", -1)
            
            Log.d(TAG, "Extracted data - Heart rate: $heartRate BPM, timestamp: $timestamp")
            
            if (heartRate > 0) {
                Log.d(TAG, "Valid heart rate data received: $heartRate BPM")
                
                // Invia i dati tramite LocalBroadcast all'app
                val intent = Intent(ACTION_HEART_RATE_RECEIVED).apply {
                    putExtra(EXTRA_HEART_RATE, heartRate)
                    putExtra(EXTRA_TIMESTAMP, timestamp)
                }
                
                Log.d(TAG, "Sending LocalBroadcast with action: $ACTION_HEART_RATE_RECEIVED")
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                Log.d(TAG, "LocalBroadcast sent successfully")
            } else {
                Log.e(TAG, "Invalid heart rate data: $heartRate")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing heart rate data", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HeartRateDataListenerService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "HeartRateDataListenerService destroyed")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "HeartRateDataListenerService onStartCommand called")
        return super.onStartCommand(intent, flags, startId)
    }
}