package com.torto.trgym.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.torto.trgym.R
import com.torto.trgym.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class WorkoutTimerService : Service() {
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "workout_timer_channel"
        private const val CHANNEL_NAME = "Workout Timer"
        
        // Actions
        const val ACTION_START = "com.torto.trgym.action.START_TIMER"
        const val ACTION_PAUSE = "com.torto.trgym.action.PAUSE_TIMER"
        const val ACTION_RESUME = "com.torto.trgym.action.RESUME_TIMER"
        const val ACTION_STOP = "com.torto.trgym.action.STOP_TIMER"
        
        // Broadcast actions
        const val ACTION_TIME_UPDATE = "com.torto.trgym.action.TIME_UPDATE"
        const val EXTRA_ELAPSED_TIME = "com.torto.trgym.extra.ELAPSED_TIME"
        const val EXTRA_IS_RUNNING = "com.torto.trgym.extra.IS_RUNNING"
    }
    
    private val binder = LocalBinder()
    private var timerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    
    // Timer state
    private var startTime = 0L
    private var elapsedTime = 0L
    private var isRunning = false
    private var workoutSessionId: String? = null
    
    // StateFlow for reactive updates
    private val _timerState = MutableStateFlow(TimerState(0L, false))
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                workoutSessionId = intent.getStringExtra("sessionId")
                startTimer()
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimerAndService()
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    private fun startTimer() {
        if (!isRunning) {
            startTime = SystemClock.elapsedRealtime() - elapsedTime
            isRunning = true
            startForeground(NOTIFICATION_ID, createNotification())
            startTimerJob()
        }
    }
    
    private fun pauseTimer() {
        if (isRunning) {
            isRunning = false
            elapsedTime = SystemClock.elapsedRealtime() - startTime
            timerJob?.cancel()
            updateTimerState()
            updateNotification()
        }
    }
    
    private fun resumeTimer() {
        if (!isRunning) {
            startTime = SystemClock.elapsedRealtime() - elapsedTime
            isRunning = true
            startTimerJob()
            updateNotification()
        }
    }
    
    private fun stopTimerAndService() {
        timerJob?.cancel()
        stopForeground(true)
        stopSelf()
    }
    
    private fun startTimerJob() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isRunning) {
                elapsedTime = SystemClock.elapsedRealtime() - startTime
                updateTimerState()
                updateNotification()
                delay(1000) // Update every second
            }
        }
    }
    
    private fun updateTimerState() {
        _timerState.value = TimerState(elapsedTime, isRunning)
        // Broadcast time update
        Intent(ACTION_TIME_UPDATE).apply {
            putExtra(EXTRA_ELAPSED_TIME, elapsedTime)
            putExtra(EXTRA_IS_RUNNING, isRunning)
            sendBroadcast(this)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainActivityIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val pauseResumeAction = if (isRunning) {
            NotificationCompat.Action.Builder(
                R.drawable.ic_pause,
                "Pausa",
                PendingIntent.getService(
                    this, 1,
                    Intent(this, WorkoutTimerService::class.java).setAction(ACTION_PAUSE),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                R.drawable.ic_play,
                "Riprendi",
                PendingIntent.getService(
                    this, 2,
                    Intent(this, WorkoutTimerService::class.java).setAction(ACTION_RESUME),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            ).build()
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Allenamento in corso")
            .setContentText(formatElapsedTime(elapsedTime))
            .setSmallIcon(R.drawable.ic_timer)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(pauseResumeAction)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }
    
    private fun formatElapsedTime(elapsedMillis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(elapsedMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMillis) % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): WorkoutTimerService = this@WorkoutTimerService
    }
    
    data class TimerState(val elapsedTime: Long, val isRunning: Boolean)
    
    override fun onDestroy() {
        timerJob?.cancel()
        super.onDestroy()
    }
    
    // Helper methods for clients
    fun getElapsedTime(): Long = elapsedTime
    
    fun isTimerRunning(): Boolean = isRunning
    
    fun getFormattedTime(): String = formatElapsedTime(elapsedTime)
}
