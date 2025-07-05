package com.so5km.qrstrainer

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.so5km.qrstrainer.data.TrainingSettings

/**
 * Singleton to manage global app state between components
 * This provides centralized state management to prevent state inconsistencies
 */
object AppState {
    // Track if app is in foreground
    private val _isAppInForeground = MutableLiveData(true)
    val isAppInForeground: LiveData<Boolean> = _isAppInForeground
    
    // Track if audio is currently playing
    private val _isAudioPlaying = MutableLiveData(false)
    val isAudioPlaying: LiveData<Boolean> = _isAudioPlaying
    
    // Track if continuous noise is running
    private val _isNoiseRunning = MutableLiveData(false)
    val isNoiseRunning: LiveData<Boolean> = _isNoiseRunning
    
    // Track current settings
    private var _settings: TrainingSettings? = null
    
    // Track if foreground service is running
    private val _isForegroundServiceRunning = MutableLiveData(false)
    val isForegroundServiceRunning: LiveData<Boolean> = _isForegroundServiceRunning
    
    // Methods to update state
    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.postValue(inForeground)
    }
    
    fun setAudioPlaying(isPlaying: Boolean) {
        _isAudioPlaying.postValue(isPlaying)
    }
    
    fun setNoiseRunning(isRunning: Boolean) {
        _isNoiseRunning.postValue(isRunning)
    }
    
    fun setForegroundServiceRunning(isRunning: Boolean) {
        _isForegroundServiceRunning.postValue(isRunning)
    }
    
    /**
     * Get current settings, loading from storage if necessary
     */
    fun getSettings(context: Context): TrainingSettings {
        if (_settings == null) {
            _settings = TrainingSettings.load(context)
        }
        return _settings!!
    }
    
    /**
     * Update settings and save to storage
     */
    fun updateSettings(context: Context, settings: TrainingSettings) {
        _settings = settings
        settings.save(context)
    }
    
    /**
     * Reload settings from storage
     */
    fun reloadSettings(context: Context): TrainingSettings {
        _settings = TrainingSettings.load(context)
        return _settings!!
    }
} 