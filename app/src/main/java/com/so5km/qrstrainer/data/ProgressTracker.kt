package com.so5km.qrstrainer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tracks progress and statistics for all characters
 */
class ProgressTracker(private val context: Context) {
    private val prefs = context.getSharedPreferences("morse_progress", Context.MODE_PRIVATE)
    private val _characterStats = mutableMapOf<Char, CharacterStats>()
    
    /**
     * Get all character statistics
     */
    val characterStats: Map<Char, CharacterStats> get() = _characterStats.toMap()
    
    /**
     * Current session stats
     */
    private var _sessionCorrect = 0
    private var _sessionIncorrect = 0
    private var _sessionStreak = 0
    private var _sessionBestStreak = 0
    private var _sessionTotalResponseTimeMs = 0L
    private var _sessionResponseCount = 0
    
    /**
     * Level drop tracking
     */
    private var _currentLevelMistakes = 0
    
    val sessionCorrect: Int get() = _sessionCorrect
    val sessionIncorrect: Int get() = _sessionIncorrect
    val sessionStreak: Int get() = _sessionStreak
    val sessionBestStreak: Int get() = _sessionBestStreak
    val sessionTotal: Int get() = _sessionCorrect + _sessionIncorrect
    val currentLevelMistakes: Int get() = _currentLevelMistakes
    val sessionAverageResponseTimeMs: Double get() = if (_sessionResponseCount > 0) {
        _sessionTotalResponseTimeMs.toDouble() / _sessionResponseCount
    } else 0.0
    
    init {
        loadProgress()
    }
    
    /**
     * Get statistics for a specific character
     */
    fun getCharacterStats(char: Char): CharacterStats {
        return _characterStats[char] ?: CharacterStats(char)
    }
    
    /**
     * Record a correct answer for a character
     */
    fun recordCorrect(char: Char) {
        val current = getCharacterStats(char)
        val updated = current.addCorrect()
        _characterStats[char] = updated
        
        _sessionCorrect++
        _sessionStreak++
        if (_sessionStreak > _sessionBestStreak) {
            _sessionBestStreak = _sessionStreak
        }
        
        // Reset level mistakes counter on correct answer
        _currentLevelMistakes = 0
        
        updateWeights()
        saveProgress()
        
        // Debug log
        android.util.Log.d("ProgressTracker", "Recorded correct for '$char': now ${updated.correctCount} correct, ${updated.incorrectCount} incorrect")
    }
    
    /**
     * Record an incorrect answer for a character
     */
    fun recordIncorrect(char: Char) {
        val current = getCharacterStats(char)
        _characterStats[char] = current.addIncorrect()
        
        _sessionIncorrect++
        _sessionStreak = 0
        
        // Increment level mistakes counter
        _currentLevelMistakes++
        
        updateWeights()
        saveProgress()
    }
    
    /**
     * Record response time for a character
     */
    fun recordResponseTime(char: Char, responseTimeMs: Long) {
        val current = getCharacterStats(char)
        _characterStats[char] = current.addResponseTime(responseTimeMs)
        
        _sessionTotalResponseTimeMs += responseTimeMs
        _sessionResponseCount++
        
        saveProgress()
    }
    
    /**
     * Get characters for the current level with their weights
     */
    fun getWeightedCharacters(level: Int, lettersOnly: Boolean = false): Array<Pair<Char, Double>> {
        val characters = MorseCode.getCharactersForLevel(level, lettersOnly)
        return characters.map { char ->
            val stats = getCharacterStats(char)
            char to stats.weight
        }.toTypedArray()
    }
    
    /**
     * Update character weights based on performance
     * Characters with lower accuracy get higher weights
     */
    private fun updateWeights() {
        _characterStats.forEach { (char, stats) ->
            if (stats.totalAttempts > 0) {
                // Higher weight for lower accuracy (more likely to be selected)
                val accuracyFactor = 1.0 - (stats.accuracy / 100.0)
                val newWeight = 1.0 + (accuracyFactor * 3.0) // Weight range: 1.0 to 4.0
                _characterStats[char] = stats.updateWeight(newWeight)
            }
        }
    }
    
    /**
     * Reset all progress
     */
    fun resetProgress() {
        _characterStats.clear()
        _sessionCorrect = 0
        _sessionIncorrect = 0
        _sessionStreak = 0
        _sessionBestStreak = 0
        _currentLevelMistakes = 0
        _sessionTotalResponseTimeMs = 0
        _sessionResponseCount = 0
        saveProgress()
    }
    
    /**
     * Reset session statistics
     */
    fun resetSession() {
        _sessionCorrect = 0
        _sessionIncorrect = 0
        _sessionStreak = 0
        _sessionBestStreak = 0
        _sessionTotalResponseTimeMs = 0
        _sessionResponseCount = 0
    }
    
    /**
     * Check if user has enough correct answers to advance to next level
     */
    fun canAdvanceLevel(currentLevel: Int, requiredCorrect: Int): Boolean {
        val characters = MorseCode.getCharactersForLevel(currentLevel)
        
        // Debug log to check character stats
        android.util.Log.d("ProgressTracker", "Checking level advancement for level $currentLevel, required: $requiredCorrect")
        characters.forEach { char ->
            val stats = getCharacterStats(char)
            android.util.Log.d("ProgressTracker", "Character '$char': correct=${stats.correctCount}, incorrect=${stats.incorrectCount}")
        }
        
        val canAdvance = characters.all { char ->
            getCharacterStats(char).correctCount >= requiredCorrect
        }
        
        android.util.Log.d("ProgressTracker", "Can advance level: $canAdvance")
        return canAdvance
    }
    
    /**
     * Get overall accuracy across all characters
     */
    fun getOverallAccuracy(): Double {
        val totalCorrect = _characterStats.values.sumOf { it.correctCount }
        val totalAttempts = _characterStats.values.sumOf { it.totalAttempts }
        
        return if (totalAttempts > 0) {
            (totalCorrect.toDouble() / totalAttempts) * 100.0
        } else 0.0
    }
    
    /**
     * Get overall average response time across all characters
     */
    fun getOverallAverageResponseTime(): Double {
        val totalResponseTime = _characterStats.values.sumOf { it.totalResponseTimeMs }
        val totalResponseCount = _characterStats.values.sumOf { it.responseCount }
        
        return if (totalResponseCount > 0) {
            totalResponseTime.toDouble() / totalResponseCount
        } else 0.0
    }
    
    /**
     * Save progress to SharedPreferences
     */
    private fun saveProgress() {
        val json = JSONObject()
        val statsArray = JSONArray()
        
        _characterStats.values.forEach { stats ->
            statsArray.put(stats.toJson())
        }
        
        json.put("characterStats", statsArray)
        json.put("sessionCorrect", _sessionCorrect)
        json.put("sessionIncorrect", _sessionIncorrect)
        json.put("sessionBestStreak", _sessionBestStreak)
        json.put("currentLevelMistakes", _currentLevelMistakes)
        json.put("sessionTotalResponseTimeMs", _sessionTotalResponseTimeMs)
        json.put("sessionResponseCount", _sessionResponseCount)
        
        prefs.edit().putString("progress_data", json.toString()).apply()
    }
    
    /**
     * Load progress from SharedPreferences
     */
    private fun loadProgress() {
        val jsonString = prefs.getString("progress_data", null) ?: return
        
        try {
            val json = JSONObject(jsonString)
            val statsArray = json.getJSONArray("characterStats")
            
            for (i in 0 until statsArray.length()) {
                val statsJson = statsArray.getJSONObject(i)
                val stats = CharacterStats.fromJson(statsJson)
                _characterStats[stats.character] = stats
            }
            
            _sessionCorrect = json.optInt("sessionCorrect", 0)
            _sessionIncorrect = json.optInt("sessionIncorrect", 0)
            _sessionBestStreak = json.optInt("sessionBestStreak", 0)
            _currentLevelMistakes = json.optInt("currentLevelMistakes", 0)
            _sessionTotalResponseTimeMs = json.optLong("sessionTotalResponseTimeMs", 0)
            _sessionResponseCount = json.optInt("sessionResponseCount", 0)
            
        } catch (e: Exception) {
            // If loading fails, start with clean slate
            _characterStats.clear()
        }
    }
    
    /**
     * Check if level should drop based on mistakes
     */
    fun shouldDropLevel(mistakesToDrop: Int): Boolean {
        return mistakesToDrop > 0 && _currentLevelMistakes >= mistakesToDrop
    }
    
    /**
     * Reset the level mistakes counter (called when level changes)
     */
    fun resetLevelMistakes() {
        _currentLevelMistakes = 0
        saveProgress()
    }
} 