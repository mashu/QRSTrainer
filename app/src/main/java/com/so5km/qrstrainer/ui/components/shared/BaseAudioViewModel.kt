package com.so5km.qrstrainer.ui.components.shared

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.so5km.qrstrainer.audio.MorseCodeGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Base ViewModel for audio-related functionality
 */
abstract class BaseAudioViewModel(application: Application) : AndroidViewModel(application) {
    
    protected val morseGenerator = MorseCodeGenerator(application)
    
    private val _isAudioPlaying = MutableStateFlow(false)
    val isAudioPlaying: StateFlow<Boolean> = _isAudioPlaying
    
    protected fun setAudioPlaying(isPlaying: Boolean) {
        _isAudioPlaying.value = isPlaying
    }
    
    override fun onCleared() {
        super.onCleared()
        morseGenerator.release()
    }
} 