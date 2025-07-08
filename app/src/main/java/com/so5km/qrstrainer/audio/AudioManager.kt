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
    
    suspend fun playSequence(sequence: String, settings: TrainingSettings) {
        playbackJob?.cancel()
        
        playbackJob = scope.launch {
            store.dispatch(AppAction.SetAudioPlaying(true))
            
            try {
                morsePlayer.playSequence(sequence, settings)
            } catch (e: Exception) {
                // Log error but don't throw
                android.util.Log.e("AudioManager", "Error playing sequence", e)
            } finally {
                store.dispatch(AppAction.SetAudioPlaying(false))
            }
        }
        
        // Wait for the job to complete
        playbackJob?.join()
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
        audioEngine.release()
    }
    
    fun startContinuousNoise(settings: TrainingSettings) {
        if (!settings.noiseEnabled) return
        
        android.util.Log.d("AudioManager", "Starting continuous noise")
        store.dispatch(AppAction.SetNoiseRunning(true))
        
        audioEngine.startNoiseStream {
            if (store.state.value.audioState.isNoiseRunning) {
                val noise = noiseGenerator.generateHFBandNoise(
                    100, // Generate 100ms chunks
                    settings.noiseVolume,
                    settings.frequency.toFloat(), // Pass center frequency
                    settings.noiseBandwidthHz,
                    settings.filterType,
                    settings.filterOrder,
                    includeStatic = true,
                    includeQRN = true
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
