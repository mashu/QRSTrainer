package com.so5km.qrstrainer.state

import com.so5km.qrstrainer.data.TrainingSettings

data class AppState(
    val isAppInForeground: Boolean = true,
    val isForegroundServiceRunning: Boolean = false,
    val settings: TrainingSettings = TrainingSettings(),
    val trainingState: TrainingStateData = TrainingStateData(),
    val listenState: ListenStateData = ListenStateData(),
    val audioState: AudioStateData = AudioStateData(),
    val progressState: ProgressStateData = ProgressStateData()
)

data class TrainingStateData(
    val isActive: Boolean = false,
    val currentSequence: String = "",
    val userInput: String = "",
    val previousSequence: String = "",
    val previousUserInput: String = "",
    val previousWasCorrect: Boolean = false,
    val state: TrainingState = TrainingState.READY
)

data class ListenStateData(
    val isActive: Boolean = false,
    val currentSequence: String = "",
    val isRevealed: Boolean = false,
    val sequenceCount: Int = 0,
    val state: ListeningState = ListeningState.READY
)

data class AudioStateData(
    val isPlaying: Boolean = false,
    val isNoiseRunning: Boolean = false,
    val currentSource: String = "",
    val lastSequence: String = ""
)

data class ProgressStateData(
    val currentLevel: Int = 0,
    val correctAnswers: Int = 0,
    val totalAttempts: Int = 0,
    val characterStats: Map<Char, CharacterStats> = emptyMap()
)

data class CharacterStats(
    val attempts: Int = 0,
    val correct: Int = 0,
    val averageResponseTime: Long = 0
)

enum class TrainingState {
    READY, PLAYING, PAUSED, WAITING, FINISHED
}

enum class ListeningState {
    READY, PLAYING, PAUSED, WAITING, REVEALED
}