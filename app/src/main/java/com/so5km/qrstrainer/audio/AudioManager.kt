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
        noiseGenerator
    )
    
    private val store = AppStore.getInstance()
    private val scope = CoroutineScope(Dispatchers.Default)
    private var playbackJob: Job? = null
    
    fun playSequence(sequence: String, settings: TrainingSettings) {
        playbackJob?.cancel()
        
        playbackJob = scope.launch {
            store.dispatch(AppAction.SetAudioPlaying(true))
            
            try {
                morsePlayer.playSequence(sequence, settings)
            } finally {
                store.dispatch(AppAction.SetAudioPlaying(false))
            }
        }
    }
    
    fun stopPlayback() {
        playbackJob?.cancel()
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
        audioEngine.release()
    }
    
    fun startContinuousNoise(settings: TrainingSettings) {
        if (!settings.noiseEnabled) return
        
        scope.launch {
            store.dispatch(AppAction.SetNoiseRunning(true))
            
            audioEngine.playStream {
                if (store.state.value.audioState.isNoiseRunning) {
                    val noise = noiseGenerator.generateNoise(
                        100, // Generate 100ms chunks
                        settings.noiseVolume,
                        settings.noiseBandwidthHz
                    )
                    floatToShortArray(noise)
                } else {
                    null
                }
            }
            
            store.dispatch(AppAction.SetNoiseRunning(false))
        }
    }
    
    fun stopContinuousNoise() {
        store.dispatch(AppAction.SetNoiseRunning(false))
    }
    
    private fun floatToShortArray(floatArray: FloatArray): ShortArray {
        return ShortArray(floatArray.size) { i ->
            (floatArray[i] * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }
}
