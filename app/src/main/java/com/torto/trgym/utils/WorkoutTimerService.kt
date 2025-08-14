package com.torto.trgym.utils


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
import androidx.core.app.NotificationCompat
import com.torto.trgym.MainActivity
import com.torto.trgym.R

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@AndroidEntryPoint
class WorkoutTimerService : Service() {



    private val binder = WorkoutTimerBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var timerJob: Job? = null
    private var currentSessionId: String? = null

    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private var startTime: Long = 0
    private var pausedDuration: Long = 0
    private var pauseStartTime: Long = 0

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "workout_timer_channel"
        const val ACTION_START_TIMER = "start_timer"
        const val ACTION_PAUSE_TIMER = "pause_timer"
        const val ACTION_RESUME_TIMER = "resume_timer"
        const val ACTION_STOP_TIMER = "stop_timer"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_START_TIME = "start_time"
    }

    inner class WorkoutTimerBinder : Binder() {
        fun getService(): WorkoutTimerService = this@WorkoutTimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TIMER -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                val sessionStartTime = intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis())
                startTimer(sessionId, sessionStartTime)
            }
            ACTION_PAUSE_TIMER -> pauseTimer()
            ACTION_RESUME_TIMER -> resumeTimer()
            ACTION_STOP_TIMER -> stopTimer()
        }
        return START_STICKY
    }

    private fun startTimer(sessionId: String?, sessionStartTime: Long) {
        if (sessionId == null) return

        currentSessionId = sessionId
        startTime = sessionStartTime
        pausedDuration = 0
        _isRunning.value = true
        _isPaused.value = false

        startForeground(NOTIFICATION_ID, createNotification())

        timerJob = serviceScope.launch {
            while (_isRunning.value) {
                if (!_isPaused.value) {
                    val currentTime = System.currentTimeMillis()
                    _elapsedTime.value = currentTime - startTime - pausedDuration
                }
                delay(1000) // Aggiorna ogni secondo
            }
        }
    }

    private fun pauseTimer() {
        if (_isRunning.value && !_isPaused.value) {
            _isPaused.value = true
            pauseStartTime = System.currentTimeMillis()
            updateNotification()
        }
    }

    private fun resumeTimer() {
        if (_isRunning.value && _isPaused.value) {
            pausedDuration += System.currentTimeMillis() - pauseStartTime
            _isPaused.value = false
            updateNotification()
        }
    }

    private fun stopTimer() {
        _isRunning.value = false
        _isPaused.value = false
        _elapsedTime.value = 0
        currentSessionId = null

        timerJob?.cancel()
        timerJob = null

        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Workout Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Timer per allenamenti in corso"
                setShowBadge(false)
                setSound(null, null)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, WorkoutTimerService::class.java).apply {
            action = if (_isPaused.value) ACTION_RESUME_TIMER else ACTION_PAUSE_TIMER
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this, 1, pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WorkoutTimerService::class.java).apply {
            action = ACTION_STOP_TIMER
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val elapsedTimeFormatted = formatElapsedTime(_elapsedTime.value)
        val statusText = if (_isPaused.value) "In pausa" else "In corso"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Allenamento $statusText")
            .setContentText("Tempo trascorso: $elapsedTimeFormatted")
            .setSmallIcon(R.drawable.ic_fitness_center)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(
                if (_isPaused.value) R.drawable.ic_play else R.drawable.ic_pause,
                if (_isPaused.value) "Riprendi" else "Pausa",
                pauseResumePendingIntent
            )
            .addAction(
                R.drawable.ic_pause,
                "Termina",
                stopPendingIntent
            )
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Tempo trascorso: $elapsedTimeFormatted\nTocca per aprire l'app"))
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun formatElapsedTime(milliseconds: Long): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        val hours = (milliseconds / (1000 * 60 * 60)) % 24

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        timerJob?.cancel()
    }
}

// Utility per gestire il servizio
object WorkoutTimerServiceManager {

    fun startWorkoutTimer(context: Context, sessionId: String, startTime: Long) {
        val intent = Intent(context, WorkoutTimerService::class.java).apply {
            action = WorkoutTimerService.ACTION_START_TIMER
            putExtra(WorkoutTimerService.EXTRA_SESSION_ID, sessionId)
            putExtra(WorkoutTimerService.EXTRA_START_TIME, startTime)
        }
        context.startForegroundService(intent)
    }

    fun pauseWorkoutTimer(context: Context) {
        val intent = Intent(context, WorkoutTimerService::class.java).apply {
            action = WorkoutTimerService.ACTION_PAUSE_TIMER
        }
        context.startService(intent)
    }

    fun resumeWorkoutTimer(context: Context) {
        val intent = Intent(context, WorkoutTimerService::class.java).apply {
            action = WorkoutTimerService.ACTION_RESUME_TIMER
        }
        context.startService(intent)
    }

    fun stopWorkoutTimer(context: Context) {
        val intent = Intent(context, WorkoutTimerService::class.java).apply {
            action = WorkoutTimerService.ACTION_STOP_TIMER
        }
        context.startService(intent)
    }
}