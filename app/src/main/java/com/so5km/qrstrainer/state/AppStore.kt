package com.so5km.qrstrainer.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Central state store for the entire application
 * Implements a Redux-like pattern with actions and reducers
 */
class AppStore {
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()
    
    fun dispatch(action: AppAction) {
        _state.update { currentState ->
            reduce(currentState, action)
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
            
            // Settings Actions
            is AppAction.UpdateSettings -> state.copy(
                settings = action.settings
            )
            is AppAction.UpdateWpm -> state.copy(
                settings = state.settings.copy(wpm = action.wpm)
            )
            is AppAction.UpdateEffectiveWpm -> state.copy(
                settings = state.settings.copy(effectiveWpm = action.effectiveWpm)
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