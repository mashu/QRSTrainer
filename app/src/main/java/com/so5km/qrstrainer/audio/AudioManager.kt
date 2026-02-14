package com.so5km.qrstrainer.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.so5km.qrstrainer.state.AppStore
import com.so5km.qrstrainer.state.AppAction
import com.so5km.qrstrainer.data.TrainingSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * High-level audio manager that coordinates between MorsePlayer and AudioEngine
 * Now uses MorseAudioService for background playback like music apps
 */
class AudioManager(private val context: Context) {
    
    private val store = AppStore.getInstance()
    private val scope = CoroutineScope(Dispatchers.Main)
    
    // For non-background playback (trainer mode, etc.)
    private val audioEngine = AudioEngine()
    private val signalGenerator = SignalGenerator()
    private val morseEncoder = MorseEncoder()
    private val noiseGenerator = NoiseGenerator()
    private val morsePlayer = MorsePlayer(audioEngine, signalGenerator, morseEncoder, noiseGenerator)
    
    private var playbackJob: Job? = null
    private var externalCompletionListener: AudioCompletionListener? = null
    
    // Service connection for background playback
    private var morseAudioService: MorseAudioService? = null
    private var isServiceBound = false
    private var isUsingBackgroundService = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            android.util.Log.d("AudioManager", "Service connected")
            val binder = service as MorseAudioService.MorseAudioBinder
            morseAudioService = binder.getService()
            isServiceBound = true
            
            // Set up completion listener
            morseAudioService?.setCompletionListener(externalCompletionListener)
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            android.util.Log.d("AudioManager", "Service disconnected")
            morseAudioService = null
            isServiceBound = false
        }
    }
    
    fun setAudioCompletionListener(listener: AudioCompletionListener?) {
        externalCompletionListener = listener
        morseAudioService?.setCompletionListener(listener)
    }
    
    /**
     * Start background-enabled playback using foreground service
     * Use this for Listen mode to enable hands-free operation
     */
    suspend fun playSequenceInBackground(sequence: String, settings: TrainingSettings) {
        android.util.Log.d("AudioManager", "Starting background playback with foreground service")
        
        // DON'T stop current playback - just send new sequence to running service
        // This prevents Android from blocking foreground service restarts
        
        isUsingBackgroundService = true
        
        // Bind to service if not already bound
        if (!isServiceBound) {
            val intent = Intent(context, MorseAudioService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        
        // Send new sequence to already-running service (or start if first time)
        val intent = Intent(context, MorseAudioService::class.java).apply {
            action = MorseAudioService.ACTION_START_PLAYBACK
            putExtra(MorseAudioService.EXTRA_SEQUENCE, sequence)
            putExtra(MorseAudioService.EXTRA_SETTINGS, settings)
        }
        
        // Always use startForegroundService on API 26+
        // Service will handle if it's already foreground
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        
        store.dispatch(AppAction.SetAudioPlaying(true))
        android.util.Log.d("AudioManager", "New sequence sent to foreground service")
    }
    
    /**
     * Regular playback without background service
     * Use this for Trainer mode and other non-background scenarios
     */
    suspend fun playSequence(sequence: String, settings: TrainingSettings) {
        android.util.Log.d("AudioManager", "Starting regular playback")
        isUsingBackgroundService = false
        
        // Stop any background service playback
        stopBackgroundPlayback()
        
        playbackJob?.cancel()
        
        // Set up completion listener for event-driven completion
        val completionListener = object : AudioCompletionListener {
            override fun onSequenceCompleted() {
                store.dispatch(AppAction.SetAudioPlaying(false))
                android.util.Log.d("AudioManager", "Sequence completed via callback")
                
                // Notify external listener (e.g., ListenFragment)
                externalCompletionListener?.onSequenceCompleted()
            }
            
            override fun onPlaybackStopped() {
                store.dispatch(AppAction.SetAudioPlaying(false))
                android.util.Log.d("AudioManager", "Playback stopped via callback")
                
                // Notify external listener
                externalCompletionListener?.onPlaybackStopped()
            }
            
            override fun onPlaybackError(error: Exception) {
                store.dispatch(AppAction.SetAudioPlaying(false))
                android.util.Log.e("AudioManager", "Playback error via callback", error)
                
                // Notify external listener
                externalCompletionListener?.onPlaybackError(error)
            }
        }
        
        morsePlayer.setCompletionListener(completionListener)
        
        playbackJob = scope.launch {
            store.dispatch(AppAction.SetAudioPlaying(true))
            
            try {
                morsePlayer.playSequence(sequence, settings)
            } catch (e: CancellationException) {
                // Expected when stopPlayback() or next playSequence() cancels this job
                android.util.Log.d("AudioManager", "Playback cancelled")
                completionListener.onPlaybackStopped()
            } catch (e: Exception) {
                android.util.Log.e("AudioManager", "Error playing sequence", e)
                completionListener.onPlaybackError(e)
            }
        }
        
        // Return immediately - completion handled via callbacks
        // This allows typing during playback without blocking
    }
    
    fun stopPlayback() {
        if (isUsingBackgroundService) {
            stopBackgroundPlayback()
        } else {
            // Stop regular playback
            playbackJob?.cancel()
            morsePlayer.stopSequence()
            audioEngine.stop()
            store.dispatch(AppAction.SetAudioPlaying(false))
        }
    }
    
    private fun stopBackgroundPlayback() {
        android.util.Log.d("AudioManager", "Stopping background playback completely")
        if (isServiceBound && morseAudioService != null) {
            val intent = Intent(context, MorseAudioService::class.java).apply {
                action = MorseAudioService.ACTION_STOP_PLAYBACK
            }
            context.startService(intent)
        }
        isUsingBackgroundService = false
        store.dispatch(AppAction.SetAudioPlaying(false))
        android.util.Log.d("AudioManager", "Background service stopped")
    }
    
    fun pause() {
        if (isUsingBackgroundService) {
            val intent = Intent(context, MorseAudioService::class.java).apply {
                action = MorseAudioService.ACTION_PAUSE_PLAYBACK
            }
            context.startService(intent)
        } else {
            audioEngine.pause()
        }
    }
    
    fun resume() {
        if (isUsingBackgroundService) {
            val intent = Intent(context, MorseAudioService::class.java).apply {
                action = MorseAudioService.ACTION_RESUME_PLAYBACK
            }
            context.startService(intent)
        } else {
            audioEngine.resume()
        }
    }
    
    fun release() {
        stopPlayback()
        stopContinuousNoise()
        
        // Unbind from service if bound
        if (isServiceBound) {
            context.unbindService(serviceConnection)
            isServiceBound = false
        }
        
        // Launch coroutine to handle suspend release
        scope.launch {
            try {
                audioEngine.release()
            } catch (e: Exception) {
                android.util.Log.e("AudioManager", "Error releasing audio engine", e)
            }
        }
    }
    
    fun startContinuousNoise(settings: TrainingSettings) {
        if (!settings.noiseEnabled) return
        
        android.util.Log.d("AudioManager", "Starting continuous noise")
        store.dispatch(AppAction.SetNoiseRunning(true))
        
        audioEngine.startNoiseStream {
            if (store.state.value.audioState.isNoiseRunning) {
                // Always use realistic HF band noise for CW training
                val noise = noiseGenerator.generateHFBandNoise(
                    100, // Generate 100ms chunks
                    settings.noiseVolume,
                    settings.frequency.toFloat(),
                    settings.noiseBandwidthHz,
                    settings.filterType,
                    settings.filterOrder,
                    includeStatic = true,
                    includeQRN = settings.qrmEnabled
                )
                floatToShortArray(noise)
            } else {
                null
            }
        }
    }
    
    fun stopContinuousNoise() {
        android.util.Log.d("AudioManager", "Stopping continuous noise")
        store.dispatch(AppAction.SetNoiseRunning(false))
        audioEngine.stopNoise()
    }
    
    private fun floatToShortArray(floatArray: FloatArray): ShortArray {
        return ShortArray(floatArray.size) { i ->
            (floatArray[i] * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }
    
    // Legacy methods for backward compatibility - these now do nothing
    // since background playback is handled by the service
    @Deprecated("Use playSequenceInBackground for Listen mode")
    fun acquireWakeLock() {
        android.util.Log.d("AudioManager", "acquireWakeLock called - use playSequenceInBackground for Listen mode")
    }
    
    @Deprecated("Background playback is handled by the service")
    fun releaseWakeLock() {
        android.util.Log.d("AudioManager", "releaseWakeLock called - background playback handled by service")
    }
}
