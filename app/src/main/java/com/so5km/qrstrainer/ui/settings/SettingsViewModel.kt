package com.so5km.qrstrainer.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.so5km.qrstrainer.AppState
import com.so5km.qrstrainer.data.TrainingSettings
import kotlinx.coroutines.launch

/**
 * ViewModel for managing settings state across the application
 * This ensures settings are consistently applied and shared between fragments
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _settings = MutableLiveData<TrainingSettings>()
    val settings: LiveData<TrainingSettings> = _settings
    
    private val _settingsResetEvent = MutableLiveData<Boolean>()
    val settingsResetEvent: LiveData<Boolean> = _settingsResetEvent
    
    init {
        // Load settings when ViewModel is created
        loadSettings()
    }
    
    /**
     * Load settings from AppState or storage
     */
    fun loadSettings() {
        viewModelScope.launch {
            val loadedSettings = AppState.getSettings(getApplication())
            _settings.postValue(loadedSettings)
        }
    }
    
    /**
     * Save settings to storage and update AppState
     */
    fun saveSettings(settings: TrainingSettings) {
        viewModelScope.launch {
            AppState.updateSettings(getApplication(), settings)
            _settings.postValue(settings)
        }
    }
    
    /**
     * Update a specific setting and save
     */
    fun updateSetting(update: (TrainingSettings) -> TrainingSettings) {
        val currentSettings = _settings.value ?: AppState.getSettings(getApplication())
        val updatedSettings = update(currentSettings)
        saveSettings(updatedSettings)
    }
    
    /**
     * Reset audio settings to defaults while preserving training progress
     */
    fun resetAudioSettings() {
        viewModelScope.launch {
            val currentSettings = _settings.value ?: AppState.getSettings(getApplication())
            val resetSettings = currentSettings.resetAudioSettings()
            saveSettings(resetSettings)
            _settingsResetEvent.postValue(true)
        }
    }
    
    /**
     * Reset the settings reset event flag
     */
    fun resetEventHandled() {
        _settingsResetEvent.postValue(false)
    }
} 