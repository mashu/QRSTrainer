package com.so5km.qrstrainer.audio

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioFocusRequest
import android.media.AudioAttributes
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.MainActivity
import com.so5km.qrstrainer.data.TrainingSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Foreground service for background Morse code audio playback
 * Implements the same pattern as music apps for reliable background operation
 */
class MorseAudioService : Service() {
    
    companion object {
        private const val TAG = "MorseAudioService"
        const val NOTIFICATION_ID = 1001
        const val MEDIA_CHANNEL_ID = "morse_audio_channel"
        
        // Service actions
        const val ACTION_START_PLAYBACK = "start_playback"
        const val ACTION_STOP_PLAYBACK = "stop_playback"
        const val ACTION_PAUSE_PLAYBACK = "pause_playback"
        const val ACTION_RESUME_PLAYBACK = "resume_playback"
        
        // Intent extras
        const val EXTRA_SEQUENCE = "sequence"
        const val EXTRA_SETTINGS = "settings"
    }
    
    // Service binding
    private val binder = MorseAudioBinder()
    
    // Audio components
    private lateinit var audioEngine: AudioEngine
    private lateinit var morsePlayer: MorsePlayer
    private lateinit var morseEncoder: MorseEncoder
    private lateinit var signalGenerator: SignalGenerator
    private lateinit var noiseGenerator: NoiseGenerator
    
    // Media session and audio focus
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    
    // Wake lock for background operation
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Playback state
    private var isPlaying = false
    private var isPaused = false
    private var currentSequence = ""
    private var currentSettings: TrainingSettings? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    
    // Completion listener
    private var completionListener: AudioCompletionListener? = null
    
    // Persistent state storage for restart recovery
    private fun storePlaybackState(sequence: String, settings: TrainingSettings) {
        try {
            val prefs = getSharedPreferences("morse_playback_state", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("sequence", sequence)
                .putInt("wpm", settings.wpm)
                .putInt("effectiveWpm", settings.effectiveWpm)
                .putInt("frequency", settings.frequency)
                .putFloat("noiseVolume", settings.noiseVolume)
                .putBoolean("isActive", true)
                .apply()
            android.util.Log.d(TAG, "Stored playback state for recovery")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to store playback state: ${e.message}")
        }
    }
    
    private fun getStoredPlaybackState(): Pair<String, TrainingSettings>? {
        return try {
            val prefs = getSharedPreferences("morse_playback_state", Context.MODE_PRIVATE)
            if (prefs.getBoolean("isActive", false)) {
                val sequence = prefs.getString("sequence", "") ?: ""
                val settings = TrainingSettings(
                    wpm = prefs.getInt("wpm", 20),
                    effectiveWpm = prefs.getInt("effectiveWpm", 20),
                    frequency = prefs.getInt("frequency", 600),
                    noiseVolume = prefs.getFloat("noiseVolume", 0.3f)
                )
                if (sequence.isNotEmpty()) {
                    android.util.Log.d(TAG, "Retrieved stored playback state")
                    Pair(sequence, settings)
                } else null
            } else null
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to retrieve playback state: ${e.message}")
            null
        }
    }
    
    private fun clearPlaybackState() {
        try {
            val prefs = getSharedPreferences("morse_playback_state", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            android.util.Log.d(TAG, "Cleared stored playback state")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to clear playback state: ${e.message}")
        }
    }
    
    /**
     * Check if this service is actually running as a foreground service
     * Let Android be the source of truth
     */
    private fun isActuallyForegroundService(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
                val serviceInfo = runningServices.find { 
                    it.service.className == this::class.java.name 
                }
                serviceInfo?.foreground == true
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error checking foreground service state: ${e.message}")
                false
            }
        } else {
            // For older Android versions, assume we're foreground if we have notification
            try {
                val notificationManager = getSystemService(NotificationManager::class.java)
                // If we have an active notification, we're likely foreground
                true // This is a reasonable assumption for older versions
            } catch (e: Exception) {
                false
            }
        }
    }
    
    inner class MorseAudioBinder : Binder() {
        fun getService(): MorseAudioService = this@MorseAudioService
    }
    
    override fun onCreate() {
        super.onCreate()
        android.util.Log.d(TAG, "MorseAudioService created")
        
        initializeAudioComponents()
        initializeMediaSession()
        createNotificationChannel()
        initializeWakeLock()
        initializeAudioFocus()
    }
    
    private fun initializeAudioComponents() {
        audioEngine = AudioEngine()
        signalGenerator = SignalGenerator()
        morseEncoder = MorseEncoder()
        noiseGenerator = NoiseGenerator()
        morsePlayer = MorsePlayer(audioEngine, signalGenerator, morseEncoder, noiseGenerator)
    }
    
    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, TAG)
        mediaSession.setCallback(mediaSessionCallback)
        mediaSession.setFlags(
            MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
            MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        
        // Set initial metadata
        updateMediaMetadata()
        updatePlaybackState()
        
        mediaSession.isActive = true
    }
    
    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            android.util.Log.d(TAG, "MediaSession onPlay")
            resumePlayback()
        }
        
        override fun onPause() {
            android.util.Log.d(TAG, "MediaSession onPause")
            pausePlayback()
        }
        
        override fun onStop() {
            android.util.Log.d(TAG, "MediaSession onStop")
            stopPlayback()
        }
    }
    
    private fun initializeWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "QRSTrainer:MorseAudioService"
        ).apply {
            setReferenceCounted(false)
        }
    }
    
    private fun initializeAudioFocus() {
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
        }
    }
    
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        android.util.Log.d(TAG, "Audio focus changed: $focusChange")
        
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                android.util.Log.d(TAG, "Audio focus gained - resuming if paused")
                if (isPaused) {
                    resumePlayback()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                android.util.Log.d(TAG, "Audio focus lost permanently - pausing instead of stopping")
                // For background audio apps, pause instead of stopping completely
                // This allows the user to manually resume playback later
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                android.util.Log.d(TAG, "Audio focus lost temporarily - pausing")
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                android.util.Log.d(TAG, "Audio focus lost - can duck, continuing playback")
                // Continue playing - could lower volume here if needed
                // For now, just continue at normal volume
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d(TAG, "onStartCommand: ${intent?.action}, startId: $startId")
        
        when (intent?.action) {
            ACTION_START_PLAYBACK -> {
                val sequence = intent.getStringExtra(EXTRA_SEQUENCE) ?: ""
                val settings = intent.getParcelableExtra<TrainingSettings>(EXTRA_SETTINGS)
                if (settings != null) {
                    // Only start foreground if we're not already foreground
                    // This prevents Android's "not allowed" errors on subsequent sequences
                    if (!isActuallyForegroundService()) {
                        android.util.Log.d(TAG, "Starting as foreground service (first time)")
                        try {
                            startForeground(NOTIFICATION_ID, createNotification())
                            android.util.Log.d(TAG, "Started as foreground service")
                        } catch (e: Exception) {
                            android.util.Log.e(TAG, "Failed to start foreground: ${e.message}")
                            // If we can't be foreground, we'll still try to play but may be killed
                        }
                    } else {
                        android.util.Log.d(TAG, "Already foreground service - just playing new sequence")
                    }
                    
                    // Stop any existing playback first
                    if (isPlaying) {
                        android.util.Log.d(TAG, "Stopping existing playback before starting new")
                        playbackJob?.cancel()
                        morsePlayer.stopSequence()
                    }
                    
                    // Store state for restart recovery
                    storePlaybackState(sequence, settings)
                    
                    // Now start the actual playback
                    startMorsePlayback(sequence, settings)
                } else {
                    android.util.Log.e(TAG, "Missing settings for playback")
                }
            }
            ACTION_STOP_PLAYBACK -> {
                stopAndDestroyService()
            }
            ACTION_PAUSE_PLAYBACK -> {
                pausePlayback()
            }
            ACTION_RESUME_PLAYBACK -> {
                resumePlayback()
            }
            null -> {
                // Service restarted by Android - check if we should resume playback
                android.util.Log.d(TAG, "Service restarted by Android - checking for recovery")
                val storedState = getStoredPlaybackState()
                if (storedState != null) {
                    android.util.Log.d(TAG, "Recovering playback: ${storedState.first}")
                    try {
                        startForeground(NOTIFICATION_ID, createNotification())
                        startMorsePlayback(storedState.first, storedState.second)
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to recover playback: ${e.message}")
                    }
                }
            }
            else -> {
                android.util.Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        }
        
        // ALWAYS return START_STICKY to ensure Android restarts us if killed
        // This is crucial for background audio services
        return START_STICKY
    }
    
    fun startMorsePlayback(sequence: String, settings: TrainingSettings) {
        android.util.Log.d(TAG, "Starting Morse playback: $sequence")
        
        currentSequence = sequence
        currentSettings = settings
        
        if (!requestAudioFocus()) {
            android.util.Log.e(TAG, "Failed to gain audio focus")
            return
        }
        
        // Acquire wake lock - this works regardless of foreground status
        try {
            wakeLock?.acquire(8*60*60*1000L) // 8 hour timeout for continuous listening
            android.util.Log.d(TAG, "Wake lock acquired for background playback")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to acquire wake lock: ${e.message}")
            // Continue anyway - foreground service should still protect us
        }
        
        // Note: startForeground() is now called in onStartCommand() before this method
        // Update the notification to show current sequence
        updateNotification()
        
        // Start audio playback
        startAudioPlayback(sequence, settings)
    }
    
    private fun startAudioPlayback(sequence: String, settings: TrainingSettings) {
        // Start audio playback
        playbackJob?.cancel()
        playbackJob = scope.launch {
            try {
                isPlaying = true
                isPaused = false
                updatePlaybackState()
                updateNotification()
                
                // Set up completion listener
                morsePlayer.setCompletionListener(object : AudioCompletionListener {
                    override fun onSequenceCompleted() {
                        android.util.Log.d(TAG, "Sequence completed")
                        completionListener?.onSequenceCompleted()
                        // Don't stop service automatically - let ListenFragment control it
                    }
                    
                    override fun onPlaybackStopped() {
                        android.util.Log.d(TAG, "Playback stopped")
                        isPlaying = false
                        updatePlaybackState()
                        updateNotification()
                        completionListener?.onPlaybackStopped()
                    }
                    
                    override fun onPlaybackError(error: Exception) {
                        android.util.Log.e(TAG, "Playback error", error)
                        completionListener?.onPlaybackError(error)
                        stopPlayback()
                    }
                })
                
                morsePlayer.playSequence(sequence, settings)
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in playback", e)
                stopPlayback()
            }
        }
    }
    
    private fun pausePlayback() {
        android.util.Log.d(TAG, "Pausing playback")
        isPaused = true
        audioEngine.pause()
        updatePlaybackState()
        updateNotification()
    }
    
    private fun resumePlayback() {
        android.util.Log.d(TAG, "Resuming playback")
        if (isPaused && hasAudioFocus) {
            isPaused = false
            audioEngine.resume()
            updatePlaybackState()
            updateNotification()
        }
    }
    
    fun stopPlayback() {
        android.util.Log.d(TAG, "Stopping playback (keeping service foreground for next sequence)")
        
        playbackJob?.cancel()
        morsePlayer.stopSequence()
        audioEngine.stop()
        
        isPlaying = false
        isPaused = false
        
        updatePlaybackState()
        updateNotification()
        
        // Release audio focus
        releaseAudioFocus()
        
        // Release wake lock
        try {
            wakeLock?.release()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error releasing wake lock: ${e.message}")
        }
        
        // DON'T exit foreground service - stay foreground and ready for next sequence
        // This is the key to avoiding Android's "not allowed" errors
        android.util.Log.d(TAG, "Playback stopped, service stays foreground for continuous listening")
    }
    
    private fun stopAndDestroyService() {
        android.util.Log.d(TAG, "Stopping and destroying service completely")
        
        // First stop any playback
        stopPlayback()
        
        // Clear stored state
        clearPlaybackState()
        
        // Handle service stopping based on actual Android state
        stopServiceGracefully()
    }
    
    private fun stopServiceGracefully() {
        try {
            // Check if we're actually running as foreground and stop accordingly
            if (isActuallyForegroundService()) {
                android.util.Log.d(TAG, "Stopping foreground service")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(Service.STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } else {
                android.util.Log.d(TAG, "Stopping regular service")
                // Just remove notification for regular service
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.cancel(NOTIFICATION_ID)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error during service cleanup: ${e.message}")
            // Fallback: try to remove notification manually
            try {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.cancel(NOTIFICATION_ID)
            } catch (fallbackError: Exception) {
                android.util.Log.e(TAG, "Fallback notification cleanup failed: ${fallbackError.message}")
            }
        }
        
        // Stop the service itself
        try {
            stopSelf()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error stopping service: ${e.message}")
        }
    }
    
    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { request ->
                audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } ?: false
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }.also { hasAudioFocus = it }
    }
    
    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }
    
    fun setCompletionListener(listener: AudioCompletionListener?) {
        completionListener = listener
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MEDIA_CHANNEL_ID,
                "Morse Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows Morse code training progress"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val pauseIntent = Intent(this, MorseAudioService::class.java).apply {
            action = if (isPaused) ACTION_RESUME_PLAYBACK else ACTION_PAUSE_PLAYBACK
        }
        val pausePendingIntent = PendingIntent.getService(
            this, 1, pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = Intent(this, MorseAudioService::class.java).apply {
            action = ACTION_STOP_PLAYBACK
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, MEDIA_CHANNEL_ID)
            .setStyle(MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0, 1))
            .setSmallIcon(R.drawable.ic_morse_code_logo)
            .setContentTitle("QRS Trainer - Listen Mode")
            .setContentText(when {
                isPlaying && currentSequence.isNotEmpty() -> "Playing: $currentSequence"
                isPaused && currentSequence.isNotEmpty() -> "Paused: $currentSequence"
                else -> "Ready for continuous listening"
            })
            .setContentIntent(openAppPendingIntent)
            .addAction(
                if (isPaused) R.drawable.ic_play_arrow else R.drawable.ic_pause,
                if (isPaused) "Resume" else "Pause",
                pausePendingIntent
            )
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
    
    private fun updateNotification() {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, createNotification())
            android.util.Log.d(TAG, "Notification updated")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error updating notification: ${e.message}")
        }
    }
    
    private fun updateMediaMetadata() {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Morse Code Training")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "QRS Trainer")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Listen Mode")
            .build()
        
        mediaSession.setMetadata(metadata)
    }
    
    private fun updatePlaybackState() {
        val state = when {
            isPlaying && !isPaused -> PlaybackStateCompat.STATE_PLAYING
            isPaused -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        
        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_STOP
            )
            .build()
        
        mediaSession.setPlaybackState(playbackState)
    }
    
    override fun onDestroy() {
        android.util.Log.d(TAG, "Service destroyed - cleaning up all resources")
        
        // Ensure playback is stopped
        if (isPlaying) {
            playbackJob?.cancel()
            morsePlayer.stopSequence()
            audioEngine.stop()
        }
        
        // Release all resources
        mediaSession.release()
        releaseAudioFocus()
        
        // Release wake lock
        try {
            wakeLock?.release()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error releasing wake lock in onDestroy: ${e.message}")
        }
        
        // Release audio engine in coroutine since it's a suspend function
        scope.launch {
            try {
                audioEngine.release()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error releasing audio engine: ${e.message}")
            }
        }
        
        super.onDestroy()
    }
} 