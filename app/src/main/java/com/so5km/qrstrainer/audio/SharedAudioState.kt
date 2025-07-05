package com.so5km.qrstrainer.audio

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.so5km.qrstrainer.AppState

/**
 * Singleton to manage shared audio state between different fragments
 * This ensures that audio settings are consistent and prevents multiple audio streams
 */
object SharedAudioState {
    
    // Audio playback state
    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying
    
    // Current audio source (fragment identifier)
    private val _currentAudioSource = MutableLiveData<String>()
    val currentAudioSource: LiveData<String> = _currentAudioSource
    
    // Last active sequence
    private val _lastSequence = MutableLiveData<String>()
    val lastSequence: LiveData<String> = _lastSequence
    
    /**
     * Start audio playback from a specific source
     * @param source Identifier for the source (e.g. "trainer", "listen")
     * @param sequence The sequence being played
     */
    fun startPlayback(source: String, sequence: String) {
        _currentAudioSource.postValue(source)
        _lastSequence.postValue(sequence)
        _isPlaying.postValue(true)
        AppState.setAudioPlaying(true)
    }
    
    /**
     * Stop audio playback
     */
    fun stopPlayback() {
        _isPlaying.postValue(false)
        AppState.setAudioPlaying(false)
    }
    
    /**
     * Check if audio is playing from a specific source
     */
    fun isPlayingFrom(source: String): Boolean {
        return _isPlaying.value == true && _currentAudioSource.value == source
    }
    
    /**
     * Register a sequence played from a specific source
     */
    fun registerSequence(source: String, sequence: String) {
        _currentAudioSource.postValue(source)
        _lastSequence.postValue(sequence)
    }
} 