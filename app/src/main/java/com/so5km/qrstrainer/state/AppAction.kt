package com.so5km.qrstrainer.state

import com.so5km.qrstrainer.data.TrainingSettings

sealed class AppAction {
    // Training Actions
    data class StartTraining(val sequence: String) : AppAction()
    object StopTraining : AppAction()
    data class UpdateUserInput(val input: String) : AppAction()
    data class SubmitAnswer(val answer: String) : AppAction()
    
    // Audio Actions
    data class SetAudioPlaying(val isPlaying: Boolean) : AppAction()
    data class SetNoiseRunning(val isRunning: Boolean) : AppAction()
    data class UpdateAudioSource(val source: String) : AppAction()
    
    // Settings Actions
    data class UpdateSettings(val settings: TrainingSettings) : AppAction()
    data class UpdateWpm(val wpm: Int) : AppAction()
    data class UpdateEffectiveWpm(val effectiveWpm: Int) : AppAction()
    
    // App Lifecycle Actions
    data class SetAppInForeground(val inForeground: Boolean) : AppAction()
    data class SetForegroundServiceRunning(val isRunning: Boolean) : AppAction()
    
    // Listen Mode Actions
    data class StartListening(val sequence: String) : AppAction()
    object RevealSequence : AppAction()
    object NextSequence : AppAction()
}