package com.so5km.qrstrainer.ui.listen

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.so5km.qrstrainer.data.ProgressTracker
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.training.SequenceGenerator
import com.so5km.qrstrainer.ui.components.shared.BaseAudioViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ListenViewModel(application: Application) : BaseAudioViewModel(application) {
    
    private val progressTracker = ProgressTracker(application)
    private val sequenceGenerator = SequenceGenerator(progressTracker)
    
    private val _delayProgress = MutableStateFlow(0f)
    val delayProgress: StateFlow<Float> = _delayProgress
    
    private val _isSpeakEnabled = MutableStateFlow(false)
    val isSpeakEnabled: StateFlow<Boolean> = _isSpeakEnabled
    
    private val _isAutoRevealEnabled = MutableStateFlow(true)
    val isAutoRevealEnabled: StateFlow<Boolean> = _isAutoRevealEnabled
    
    private var currentSequence = ""
    
    fun startListening() {
        val sequence = generateNewSequence()
        currentSequence = sequence
        playSequence(sequence)
    }
    
    fun revealSequence() {
        // Reveal the current sequence
        // This would typically update UI state
    }
    
    fun nextSequence() {
        // Generate next sequence
        viewModelScope.launch {
            delay(500)
            startListening()
        }
    }
    
    fun replaySequence() {
        playSequence(currentSequence)
    }
    
    fun setAutoReveal(enabled: Boolean) {
        _isAutoRevealEnabled.value = enabled
    }
    
    fun setSpeakEnabled(enabled: Boolean) {
        _isSpeakEnabled.value = enabled
    }
    
    private fun generateNewSequence(): String {
        val settings = TrainingSettings.default()
        return sequenceGenerator.generateSequence(
            settings.sequenceLength,
            settings.currentLevel
        )
    }
    
    private fun playSequence(sequence: String) {
        viewModelScope.launch {
            setAudioPlaying(true)
            val settings = TrainingSettings.default()
            try {
                morseGenerator.playSequence(sequence, settings)
            } catch (e: Exception) {
                // Handle audio playback error
            }
            setAudioPlaying(false)
            
            // Auto-reveal after delay if enabled
            if (_isAutoRevealEnabled.value) {
                startAutoRevealCountdown()
            }
        }
    }
    
    private fun startAutoRevealCountdown() {
        viewModelScope.launch {
            val delayMs = 500L // Could be configurable
            val steps = 50
            val stepDelay = delayMs / steps
            
            for (i in 0..steps) {
                _delayProgress.value = 1f - (i.toFloat() / steps)
                delay(stepDelay)
            }
            _delayProgress.value = 0f
        }
    }
}
