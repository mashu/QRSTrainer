package com.so5km.qrstrainer.audio

import android.content.Context
import com.so5km.qrstrainer.data.TrainingSettings

/**
 * Simplified MorseCodeGenerator that delegates to AudioManager
 * Maintains compatibility with existing code
 */
class MorseCodeGenerator(context: Context) {
    
    private val audioManager = AudioManager(context)
    
    fun playSequence(sequence: String, settings: TrainingSettings) {
        audioManager.playSequence(sequence, settings)
    }
    
    fun stopPlayback() {
        audioManager.stopPlayback()
    }
    
    fun pause() {
        audioManager.pause()
    }
    
    fun resume() {
        audioManager.resume()
    }
    
    fun release() {
        audioManager.release()
    }
    
    fun startContinuousNoise(settings: TrainingSettings) {
        audioManager.startContinuousNoise(settings)
    }
    
    fun stopContinuousNoise() {
        audioManager.stopContinuousNoise()
    }
}
