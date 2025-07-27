package com.so5km.qrstrainer.state

import android.content.Context
import android.content.SharedPreferences
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.data.fromJson
import com.so5km.qrstrainer.data.toJson
import com.so5km.qrstrainer.data.MorseCode
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
        loadSettings() // Load settings synchronously during initialization
        loadProgress() // Load progress synchronously during initialization
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
        
        // Save progress whenever it changes
        if (action is AppAction.RecordCharacterAttempt ||
            action is AppAction.RecordSequenceAttempt ||
            action is AppAction.ResetProgress) {
            saveProgress(newState.progressState)
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
                        previousWasCorrect = isCorrect
                        // Don't automatically set to FINISHED - let UI manage the transition
                    ),
                    progressState = state.progressState
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
            
            // Progress Actions
            is AppAction.RecordCharacterAttempt -> {
                val currentStats = state.progressState.characterStats[action.character] ?: CharacterStats()
                val updatedStats = currentStats.copy(
                    attempts = currentStats.attempts + 1,
                    correct = currentStats.correct + if (action.wasCorrect) 1 else 0,
                    totalResponseTime = currentStats.totalResponseTime + action.responseTimeMs
                )
                state.copy(
                    progressState = state.progressState.copy(
                        characterStats = state.progressState.characterStats + (action.character to updatedStats)
                    )
                )
            }
            is AppAction.RecordSequenceAttempt -> {
                val currentStreak = state.progressState.currentStreak
                val newStreak = if (action.wasCorrect) {
                    if (currentStreak < 0) 1 else currentStreak + 1
                } else {
                    if (currentStreak > 0) -1 else currentStreak - 1
                }
                val newBestStreak = if (newStreak > state.progressState.bestStreak) newStreak else state.progressState.bestStreak
                
                // Check for level progression
                val newLevel = calculateLevelProgression(state.settings, newStreak)
                val updatedSettings = if (newLevel != state.settings.currentLevel) {
                    state.settings.copy(currentLevel = newLevel)
                } else {
                    state.settings
                }
                
                state.copy(
                    settings = updatedSettings,
                    progressState = state.progressState.copy(
                        currentStreak = if (newLevel != state.settings.currentLevel) 0 else newStreak, // Reset streak on level change
                        bestStreak = newBestStreak,
                        totalSequenceAttempts = state.progressState.totalSequenceAttempts + 1,
                        totalSequenceCorrect = state.progressState.totalSequenceCorrect + if (action.wasCorrect) 1 else 0,
                        totalSequenceResponseTime = state.progressState.totalSequenceResponseTime + action.responseTimeMs
                    )
                )
            }
            is AppAction.ResetProgress -> state.copy(
                progressState = ProgressStateData(),
                settings = state.settings.copy(currentLevel = 1)
            )
        }
    }
    
    /**
     * Calculate level progression based on streak and settings
     */
    private fun calculateLevelProgression(settings: TrainingSettings, currentStreak: Int): Int {
        if (settings.lockLevel) return settings.currentLevel
        
        val currentLevel = settings.currentLevel
        
        // Level up if positive streak meets requirement
        if (currentStreak >= settings.correctAnswersToLevelUp && currentLevel < settings.maxLevel) {
            return currentLevel + 1
        }
        // Level down if negative streak meets requirement
        else if (currentStreak <= -settings.incorrectAnswersToDropLevel && currentLevel > 1) {
            return currentLevel - 1
        }
        
        return currentLevel
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
    
    /**
     * Save progress to SharedPreferences
     */
    private fun saveProgress(progressState: ProgressStateData) {
        try {
            sharedPreferences?.edit()?.apply {
                putInt("current_streak", progressState.currentStreak)
                putInt("best_streak", progressState.bestStreak)
                putInt("total_sequence_attempts", progressState.totalSequenceAttempts)
                putInt("total_sequence_correct", progressState.totalSequenceCorrect)
                putLong("total_sequence_response_time", progressState.totalSequenceResponseTime)
                
                // Save character stats
                progressState.characterStats.forEach { (char, stats) ->
                    putInt("${char}_attempts", stats.attempts)
                    putInt("${char}_correct", stats.correct)
                    putLong("${char}_total_time", stats.totalResponseTime)
                }
                
                apply()
            }
            android.util.Log.d("AppStore", "Progress saved successfully")
        } catch (e: Exception) {
            android.util.Log.e("AppStore", "Failed to save progress", e)
        }
    }
    
    /**
     * Load progress from SharedPreferences
     */
    private fun loadProgress() {
        try {
            val currentStreak = sharedPreferences?.getInt("current_streak", 0) ?: 0
            val bestStreak = sharedPreferences?.getInt("best_streak", 0) ?: 0
            val totalSequenceAttempts = sharedPreferences?.getInt("total_sequence_attempts", 0) ?: 0
            val totalSequenceCorrect = sharedPreferences?.getInt("total_sequence_correct", 0) ?: 0
            val totalSequenceResponseTime = sharedPreferences?.getLong("total_sequence_response_time", 0L) ?: 0L
            
            // Load character stats
            val characterStats = mutableMapOf<Char, CharacterStats>()
            for (char in MorseCode.MORSE_MAP.keys) {
                val attempts = sharedPreferences?.getInt("${char}_attempts", 0) ?: 0
                if (attempts > 0) {
                    characterStats[char] = CharacterStats(
                        attempts = attempts,
                        correct = sharedPreferences?.getInt("${char}_correct", 0) ?: 0,
                        totalResponseTime = sharedPreferences?.getLong("${char}_total_time", 0L) ?: 0L
                    )
                }
            }
            
            _state.update { currentState ->
                currentState.copy(
                    progressState = ProgressStateData(
                        currentStreak = currentStreak,
                        bestStreak = bestStreak,
                        totalSequenceAttempts = totalSequenceAttempts,
                        totalSequenceCorrect = totalSequenceCorrect,
                        totalSequenceResponseTime = totalSequenceResponseTime,
                        characterStats = characterStats
                    )
                )
            }
            android.util.Log.d("AppStore", "Progress loaded successfully")
        } catch (e: Exception) {
            android.util.Log.e("AppStore", "Failed to load progress, using defaults", e)
        }
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