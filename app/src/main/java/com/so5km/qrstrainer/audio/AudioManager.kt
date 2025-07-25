package com.so5km.qrstrainer.audio

import android.content.Context
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.state.AppAction
import com.so5km.qrstrainer.state.AppStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * High-level audio manager that coordinates all audio components
 */
class AudioManager(private val context: Context) {
    
    private val audioEngine = AudioEngine()
    private val signalGenerator = SignalGenerator()
    private val morseEncoder = MorseEncoder()
    private val noiseGenerator = NoiseGenerator()
    private val morsePlayer = MorsePlayer(
        audioEngine,
        signalGenerator,
        morseEncoder,
        null // Remove noise generator from MorsePlayer
    )
    
    private val store = AppStore.getInstance()
    private val scope = CoroutineScope(Dispatchers.Default)
    private var playbackJob: Job? = null
    
    private var externalCompletionListener: AudioCompletionListener? = null
    
    fun setAudioCompletionListener(listener: AudioCompletionListener?) {
        externalCompletionListener = listener
    }
    
    suspend fun playSequence(sequence: String, settings: TrainingSettings) {
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
            } catch (e: Exception) {
                // Log error and notify via callback
                android.util.Log.e("AudioManager", "Error playing sequence", e)
                completionListener.onPlaybackError(e)
            }
        }
        
        // Return immediately - completion handled via callbacks
        // This allows typing during playback without blocking
    }
    
    fun stopPlayback() {
        playbackJob?.cancel()
        morsePlayer.stopSequence()
        audioEngine.stop()
        store.dispatch(AppAction.SetAudioPlaying(false))
    }
    
    fun pause() {
        audioEngine.pause()
    }
    
    fun resume() {
        audioEngine.resume()
    }
    
    fun release() {
        stopPlayback()
        stopContinuousNoise()
        
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
}
