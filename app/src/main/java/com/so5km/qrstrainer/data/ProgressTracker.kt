package com.so5km.qrstrainer.data

import android.content.Context
import android.content.SharedPreferences
import com.so5km.qrstrainer.data.ProgressData
import com.so5km.qrstrainer.state.AppStore
import com.so5km.qrstrainer.state.AppAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracks user progress through morse code training
 */
class ProgressTracker(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("progress", Context.MODE_PRIVATE)
    private val store = AppStore.getInstance()
    
    private val _currentStreak = MutableStateFlow(0)
    val currentStreak: StateFlow<Int> = _currentStreak
    
    private val characterStats = mutableMapOf<Char, CharacterStats>()
    
    // Current training settings - can be updated
    private var currentSettings: TrainingSettings = TrainingSettings.default()
    
    init {
        loadProgress()
    }
    
    data class CharacterStats(
        var attempts: Int = 0,
        var correct: Int = 0,
        var totalResponseTime: Long = 0L
    ) {
        val accuracy: Float get() = if (attempts > 0) correct.toFloat() / attempts else 0f
        val averageResponseTime: Long get() = if (attempts > 0) totalResponseTime / attempts else 0L
    }
    
    /**
     * Update training settings used for level advancement
     */
    fun updateSettings(settings: TrainingSettings) {
        currentSettings = settings
    }
    
    /**
     * Record a character attempt
     */
    fun recordAttempt(character: Char, wasCorrect: Boolean, responseTimeMs: Long) {
        val stats = characterStats.getOrPut(character.uppercaseChar()) { CharacterStats() }
        
        stats.attempts++
        stats.totalResponseTime += responseTimeMs
        
        if (wasCorrect) {
            stats.correct++
            // Positive streak for correct answers
            if (_currentStreak.value < 0) {
                _currentStreak.value = 1  // Reset to 1 if coming from negative streak
            } else {
                _currentStreak.value = _currentStreak.value + 1
            }
        } else {
            // Negative streak for wrong answers
            if (_currentStreak.value > 0) {
                _currentStreak.value = -1  // Reset to -1 if coming from positive streak
            } else {
                _currentStreak.value = _currentStreak.value - 1
            }
        }
        
        // Check for level progression using current settings
        checkLevelProgression()
        saveProgress()
    }
    
    /**
     * Get current training level from AppStore
     */
    fun getCurrentLevel(): Int = store.state.value.settings.currentLevel
    
    /**
     * Get current streak
     */
    fun getCurrentStreak(): Int = _currentStreak.value
    
    /**
     * Get best streak achieved
     */
    fun getBestStreak(): Int = prefs.getInt("best_streak", 0)
    
    /**
     * Get all character statistics
     */
    fun getAllCharacterStats(): Map<Char, CharacterStats> = characterStats.toMap()
    
    /**
     * Get statistics for a specific character
     */
    fun getCharacterStats(character: Char): CharacterStats? = 
        characterStats[character.uppercaseChar()]
    
    /**
     * Get current level progress (0.0 to 1.0)
     */
    fun getCurrentLevelProgress(): Float {
        val requiredForNext = getRequiredForNextLevel()
        val currentStreak = maxOf(0, getCurrentStreak()) // Only count positive streak toward progress
        return if (requiredForNext > 0) {
            minOf(1.0f, currentStreak.toFloat() / requiredForNext)
        } else 1f
    }
    
    /**
     * Get number of correct answers required for next level
     */
    fun getRequiredForNextLevel(): Int {
        // Use configurable settings instead of hardcoded values
        return currentSettings.correctAnswersToLevelUp
    }
    
    /**
     * Get characters for current level using Koch method
     */
    fun getCharactersForLevel(level: Int): List<Char> {
        return when (level) {
            1 -> listOf('K', 'M')
            2 -> listOf('K', 'M', 'U', 'R')
            3 -> listOf('K', 'M', 'U', 'R', 'E', 'S')
            4 -> listOf('K', 'M', 'U', 'R', 'E', 'S', 'N', 'A')
            5 -> listOf('K', 'M', 'U', 'R', 'E', 'S', 'N', 'A', 'P', 'T')
            6 -> listOf('K', 'M', 'U', 'R', 'E', 'S', 'N', 'A', 'P', 'T', 'L', 'W')
            7 -> listOf('K', 'M', 'U', 'R', 'E', 'S', 'N', 'A', 'P', 'T', 'L', 'W', 'I', 'J')
            8 -> listOf('K', 'M', 'U', 'R', 'E', 'S', 'N', 'A', 'P', 'T', 'L', 'W', 'I', 'J', 'Z', 'F')
            9 -> listOf('K', 'M', 'U', 'R', 'E', 'S', 'N', 'A', 'P', 'T', 'L', 'W', 'I', 'J', 'Z', 'F', 'O', 'Y')
            10 -> listOf('K', 'M', 'U', 'R', 'E', 'S', 'N', 'A', 'P', 'T', 'L', 'W', 'I', 'J', 'Z', 'F', 'O', 'Y', 'V', 'G')
            else -> MorseCode.MORSE_MAP.keys.toList()
        }
    }
    
    /**
     * Reset all progress
     */
    fun resetProgress() {
        characterStats.clear()
        _currentStreak.value = 0
        prefs.edit().clear().apply()
        
        // Reset level in AppStore
        store.dispatch(AppAction.UpdateSettings(
            store.state.value.settings.copy(currentLevel = 1)
        ))
    }
    
    /**
     * Export progress data
     */
    fun exportProgress(): ProgressData {
        val statsData = characterStats.mapValues { (_, stats) ->
            CharacterStatData(
                attempts = stats.attempts,
                correct = stats.correct,
                averageResponseTime = stats.averageResponseTime
            )
        }
        
        return ProgressData(
            currentLevel = getCurrentLevel(),
            characterStats = statsData,
            totalSessions = prefs.getInt("total_sessions", 0),
            totalTime = prefs.getLong("total_time", 0L),
            bestStreak = getBestStreak()
        )
    }
    
    /**
     * Import progress data
     */
    fun importProgress(progressData: ProgressData) {
        characterStats.clear()
        progressData.characterStats.forEach { (char, statData) ->
            characterStats[char] = CharacterStats(
                attempts = statData.attempts,
                correct = statData.correct,
                totalResponseTime = statData.averageResponseTime * statData.attempts
            )
        }
        
        prefs.edit()
            .putInt("total_sessions", progressData.totalSessions)
            .putLong("total_time", progressData.totalTime)
            .putInt("best_streak", progressData.bestStreak)
            .apply()
        
        // Update level in AppStore
        store.dispatch(AppAction.UpdateSettings(
            store.state.value.settings.copy(currentLevel = progressData.currentLevel)
        ))
        
        saveProgress()
    }
    
    private fun getCurrentLevelCorrectCount(): Int {
        val levelChars = getCharactersForLevel(getCurrentLevel())
        return levelChars.sumOf { char ->
            characterStats[char]?.correct ?: 0
        }
    }
    
    private fun checkLevelProgression() {
        // Skip level changes if level is locked
        if (currentSettings.lockLevel) {
            return
        }
        
        val currentLevel = getCurrentLevel()
        val currentStreak = getCurrentStreak()
        
        // Check for level up
        if (currentStreak >= currentSettings.correctAnswersToLevelUp && currentLevel < currentSettings.maxLevel) {
            val newLevel = currentLevel + 1
            _currentStreak.value = 0 // Reset streak after level up
            
            // Update level in AppStore
            store.dispatch(AppAction.UpdateSettings(
                store.state.value.settings.copy(currentLevel = newLevel)
            ))
            
            android.util.Log.d("ProgressTracker", "Level up! Now at level $newLevel")
        }
        // Check for level down (only if streak is negative, meaning consecutive wrong answers)
        else if (currentStreak <= -currentSettings.incorrectAnswersToDropLevel && currentLevel > 1) {
            val newLevel = currentLevel - 1
            _currentStreak.value = 0 // Reset streak after level down
            
            // Update level in AppStore
            store.dispatch(AppAction.UpdateSettings(
                store.state.value.settings.copy(currentLevel = newLevel)
            ))
            
            android.util.Log.d("ProgressTracker", "Level down. Now at level $newLevel")
        }
        
        // Update best streak (only for positive streaks)
        val positiveStreak = maxOf(0, currentStreak)
        if (positiveStreak > getBestStreak()) {
            prefs.edit().putInt("best_streak", positiveStreak).apply()
        }
    }
    
    private fun saveProgress() {
        val editor = prefs.edit()
        editor.putInt("current_streak", getCurrentStreak())
        
        // Save character stats
        characterStats.forEach { (char, stats) ->
            editor.putInt("${char}_attempts", stats.attempts)
            editor.putInt("${char}_correct", stats.correct)
            editor.putLong("${char}_total_time", stats.totalResponseTime)
        }
        
        editor.apply()
    }
    
    private fun loadProgress() {
        _currentStreak.value = prefs.getInt("current_streak", 0)
        
        // Load character stats
        MorseCode.MORSE_MAP.keys.forEach { char ->
            val attempts = prefs.getInt("${char}_attempts", 0)
            if (attempts > 0) {
                characterStats[char] = CharacterStats(
                    attempts = attempts,
                    correct = prefs.getInt("${char}_correct", 0),
                    totalResponseTime = prefs.getLong("${char}_total_time", 0L)
                )
            }
        }
        
        // Remove level loading since it's now managed by AppStore
        // The level state is now the single source of truth in AppStore
    }
}
