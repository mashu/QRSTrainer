package com.so5km.qrstrainer.state

import android.content.Context
import android.content.SharedPreferences
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.data.fromJson
import com.so5km.qrstrainer.data.toJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Central state store for the entire application
 * Implements a Redux-like pattern with actions and reducers
 */
class AppStore private constructor() {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    
    private var sharedPreferences: SharedPreferences? = null
    
    /**
     * Initialize the store with context for persistence
     */
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        loadSettings()
    }
    
    fun dispatch(action: AppAction) {
        _state.update { currentState ->
            val newState = reduce(currentState, action)
            
            // Save settings whenever they change
            if (action is AppAction.UpdateSettings || 
                action is AppAction.UpdateWpm || 
                action is AppAction.UpdateEffectiveWpm ||
                action is AppAction.ResetSettings) {
                saveSettings(newState.settings)
            }
            
            newState
        }
    }
    
    private fun reduce(state: AppState, action: AppAction): AppState {
        return when (action) {
            // Training Actions
            is AppAction.StartTraining -> state.copy(
                trainingState = state.trainingState.copy(
                    isActive = true,
                    currentSequence = action.sequence,
                    userInput = "",
                    state = TrainingState.PLAYING
                )
            )
            is AppAction.StopTraining -> state.copy(
                trainingState = state.trainingState.copy(
                    isActive = false,
                    state = TrainingState.READY
                )
            )
            is AppAction.UpdateUserInput -> state.copy(
                trainingState = state.trainingState.copy(
                    userInput = action.input
                )
            )
            is AppAction.SubmitAnswer -> {
                val isCorrect = state.trainingState.currentSequence == action.answer
                state.copy(
                    trainingState = state.trainingState.copy(
                        previousSequence = state.trainingState.currentSequence,
                        previousUserInput = action.answer,
                        previousWasCorrect = isCorrect,
                        state = TrainingState.FINISHED
                    ),
                    progressState = if (isCorrect) {
                        state.progressState.copy(
                            correctAnswers = state.progressState.correctAnswers + 1
                        )
                    } else state.progressState
                )
            }
            
            // Audio Actions
            is AppAction.SetAudioPlaying -> state.copy(
                audioState = state.audioState.copy(isPlaying = action.isPlaying)
            )
            is AppAction.SetNoiseRunning -> state.copy(
                audioState = state.audioState.copy(isNoiseRunning = action.isRunning)
            )
            is AppAction.UpdateAudioSource -> state.copy(
                audioState = state.audioState.copy(currentSource = action.source)
            )
            is AppAction.AudioSequenceCompleted -> state.copy(
                audioState = state.audioState.copy(isPlaying = false),
                trainingState = if (state.trainingState.state == TrainingState.PLAYING) {
                    state.trainingState.copy(state = TrainingState.WAITING)
                } else {
                    state.trainingState
                }
            )
            
            // Settings Actions
            is AppAction.UpdateSettings -> state.copy(
                settings = TrainingSettings.validate(action.settings)
            )
            is AppAction.UpdateWpm -> state.copy(
                settings = TrainingSettings.validate(state.settings.copy(wpm = action.wpm))
            )
            is AppAction.UpdateEffectiveWpm -> state.copy(
                settings = TrainingSettings.validate(state.settings.copy(effectiveWpm = action.effectiveWpm))
            )
            is AppAction.ResetSettings -> state.copy(
                settings = TrainingSettings.default()
            )
            
            // App Lifecycle Actions
            is AppAction.SetAppInForeground -> state.copy(
                isAppInForeground = action.inForeground
            )
            is AppAction.SetForegroundServiceRunning -> state.copy(
                isForegroundServiceRunning = action.isRunning
            )
            
            // Listen Mode Actions
            is AppAction.StartListening -> state.copy(
                listenState = state.listenState.copy(
                    isActive = true,
                    currentSequence = action.sequence,
                    isRevealed = false,
                    state = ListeningState.PLAYING
                )
            )
            is AppAction.SetListeningWaiting -> state.copy(
                listenState = state.listenState.copy(
                    state = ListeningState.WAITING
                )
            )
            is AppAction.RevealSequence -> state.copy(
                listenState = state.listenState.copy(
                    isRevealed = true,
                    state = ListeningState.REVEALED
                )
            )
            is AppAction.NextSequence -> state.copy(
                listenState = state.listenState.copy(
                    sequenceCount = state.listenState.sequenceCount + 1,
                    isRevealed = false,
                    state = ListeningState.READY
                )
            )
        }
    }
    
    /**
     * Save settings to SharedPreferences
     */
    private fun saveSettings(settings: TrainingSettings) {
        try {
            sharedPreferences?.edit()?.apply {
                putString("training_settings", settings.toJson())
                apply()
            }
            android.util.Log.d("AppStore", "Settings saved successfully")
        } catch (e: Exception) {
            android.util.Log.e("AppStore", "Failed to save settings", e)
        }
    }
    
    /**
     * Load settings from SharedPreferences
     */
    private fun loadSettings() {
        try {
            val settingsJson = sharedPreferences?.getString("training_settings", null)
            if (settingsJson != null) {
                val loadedSettings = TrainingSettings.fromJson(settingsJson)
                _state.update { currentState ->
                    currentState.copy(settings = TrainingSettings.validate(loadedSettings))
                }
                android.util.Log.d("AppStore", "Settings loaded successfully")
            } else {
                android.util.Log.d("AppStore", "No saved settings found, using defaults")
            }
        } catch (e: Exception) {
            android.util.Log.e("AppStore", "Failed to load settings, using defaults", e)
            _state.update { currentState ->
                currentState.copy(settings = TrainingSettings.default())
            }
        }
    }
    
    /**
     * Reset all settings to defaults
     */
    fun resetToDefaults() {
        val defaultSettings = TrainingSettings.default()
        _state.update { currentState ->
            currentState.copy(settings = defaultSettings)
        }
        saveSettings(defaultSettings)
        android.util.Log.d("AppStore", "Settings reset to defaults")
    }
    
    companion object {
        @Volatile
        private var INSTANCE: AppStore? = null
        
        fun getInstance(): AppStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppStore().also { INSTANCE = it }
            }
        }
    }
}