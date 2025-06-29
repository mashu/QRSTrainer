package com.so5km.qrstrainer.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.so5km.qrstrainer.MainActivity
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.data.TrainingSettings

/**
 * A foreground service for audio playback that can distinguish between
 * screen off (blanked) and app minimized states.
 */
class AudioForegroundService : Service() {

    companion object {
        private const val TAG = "AudioForegroundService"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "qrstrainer_audio_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "QRS Trainer Audio"

        const val ACTION_START = "com.so5km.qrstrainer.action.START"
        const val ACTION_STOP = "com.so5km.qrstrainer.action.STOP"
        const val ACTION_PAUSE = "com.so5km.qrstrainer.action.PAUSE"
        const val ACTION_RESUME = "com.so5km.qrstrainer.action.RESUME"
        
        // Track if the service is running
        private var isServiceRunning = false
        
        // Helper to check if service is running
        fun isRunning(): Boolean = isServiceRunning
    }

    private var morseGenerator: MorseCodeGenerator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Track if we're playing audio
    private var isPlaying = false
    
    // Track if the screen is on or off
    private var isScreenOn = true
    
    // Screen state receiver
    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen turned ON")
                    isScreenOn = true
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen turned OFF")
                    isScreenOn = false
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Initialize the morse code generator
        morseGenerator = MorseCodeGenerator(applicationContext)
        
        // Create a wake lock to keep CPU running during playback
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QRSTrainer::AudioWakeLock"
        )
        
        // Register screen state receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification())
                startPlayback()
                isServiceRunning = true
            }
            ACTION_STOP -> {
                stopPlayback()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isServiceRunning = false
            }
            ACTION_PAUSE -> {
                pausePlayback()
            }
            ACTION_RESUME -> {
                resumePlayback()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        stopPlayback()
        
        // Unregister screen state receiver
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Screen state receiver not registered", e)
        }
        
        isServiceRunning = false
        super.onDestroy()
    }
    
    /**
     * Start audio playback and acquire wake lock
     */
    private fun startPlayback() {
        if (!isPlaying) {
            Log.d(TAG, "Starting playback")
            wakeLock?.acquire(10*60*1000L) // 10 minutes
            
            // Start the headphone keep-alive tone to prevent disconnections
            morseGenerator?.startHeadphoneKeepAlive()
            
            // Load settings
            val settings = TrainingSettings.load(applicationContext)
            
            // Start continuous background noise if enabled
            if (settings.filterRingingEnabled && settings.backgroundNoiseLevel > 0) {
                morseGenerator?.startContinuousNoise()
            }
            
            isPlaying = true
        }
    }
    
    /**
     * Stop audio playback and release wake lock
     */
    private fun stopPlayback() {
        if (isPlaying) {
            Log.d(TAG, "Stopping playback")
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            
            // Stop all audio playback
            morseGenerator?.stop()
            morseGenerator?.stopContinuousNoise()
            morseGenerator?.stopHeadphoneKeepAlive()
            
            // Release resources
            morseGenerator?.release()
            
            isPlaying = false
        }
    }
    
    /**
     * Pause audio playback
     */
    private fun pausePlayback() {
        if (isPlaying) {
            Log.d(TAG, "Pausing playback")
            morseGenerator?.pause()
        }
    }
    
    /**
     * Resume audio playback
     */
    private fun resumePlayback() {
        if (isPlaying) {
            Log.d(TAG, "Resuming playback")
            morseGenerator?.resume()
        }
    }
    
    /**
     * Create notification for foreground service
     */
    private fun createNotification(): Notification {
        createNotificationChannel()
        
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, 
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, AudioForegroundService::class.java)
        stopIntent.action = ACTION_STOP
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, 
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("QRS Trainer")
            .setContentText("Audio playback in progress")
            .setSmallIcon(R.drawable.ic_menu_camera) // Replace with appropriate icon
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_menu_camera, "Stop", stopPendingIntent) // Replace with appropriate icon
            .build()
    }
    
    /**
     * Create notification channel for Android O and above
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "QRS Trainer audio playback channel"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
} 