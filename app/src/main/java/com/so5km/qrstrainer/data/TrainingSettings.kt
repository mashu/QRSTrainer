package com.so5km.qrstrainer.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Training settings for the Morse code trainer
 */
data class TrainingSettings(
    val speedWpm: Int = 20,                    // Words per minute
    val kochLevel: Int = 2,                    // Current Koch level (1-based)
    val isLevelLocked: Boolean = false,        // Whether to lock at current level
    val groupSizeMin: Int = 1,                 // Minimum group size
    val groupSizeMax: Int = 5,                 // Maximum group size
    val answerTimeoutSeconds: Int = 10,        // Timeout for answers
    val repeatCount: Int = 2,                  // How many times to repeat
    val repeatSpacingMs: Int = 2000,           // Milliseconds between repeats
    val requiredCorrectToAdvance: Int = 10,    // Required correct answers to advance level
    
    // New audio settings
    val toneFrequencyHz: Int = 600,            // Tone frequency in Hz (300-1000)
    val farnsworthWpm: Int = 0,                // Farnsworth timing WPM (0 = disabled)
    val wordSpacingMs: Int = 0,                // Extra word spacing in ms (0 = default)
    val groupSpacingMs: Int = 0                // Extra group spacing in ms (0 = default)
) {
    companion object {
        private const val PREFS_NAME = "morse_trainer_settings"
        private const val KEY_SPEED_WPM = "speed_wpm"
        private const val KEY_KOCH_LEVEL = "koch_level"
        private const val KEY_LEVEL_LOCKED = "level_locked"
        private const val KEY_GROUP_SIZE_MIN = "group_size_min"
        private const val KEY_GROUP_SIZE_MAX = "group_size_max"
        private const val KEY_ANSWER_TIMEOUT = "answer_timeout"
        private const val KEY_REPEAT_COUNT = "repeat_count"
        private const val KEY_REPEAT_SPACING = "repeat_spacing"
        private const val KEY_REQUIRED_CORRECT = "required_correct"
        
        // New keys for audio settings
        private const val KEY_TONE_FREQUENCY = "tone_frequency"
        private const val KEY_FARNSWORTH_WPM = "farnsworth_wpm"
        private const val KEY_WORD_SPACING = "word_spacing"
        private const val KEY_GROUP_SPACING = "group_spacing"
        
        /**
         * Load settings from SharedPreferences
         */
        fun load(context: Context): TrainingSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return TrainingSettings(
                speedWpm = prefs.getInt(KEY_SPEED_WPM, 20),
                kochLevel = prefs.getInt(KEY_KOCH_LEVEL, 2),
                isLevelLocked = prefs.getBoolean(KEY_LEVEL_LOCKED, false),
                groupSizeMin = prefs.getInt(KEY_GROUP_SIZE_MIN, 1),
                groupSizeMax = prefs.getInt(KEY_GROUP_SIZE_MAX, 5),
                answerTimeoutSeconds = prefs.getInt(KEY_ANSWER_TIMEOUT, 10),
                repeatCount = prefs.getInt(KEY_REPEAT_COUNT, 2),
                repeatSpacingMs = prefs.getInt(KEY_REPEAT_SPACING, 2000),
                requiredCorrectToAdvance = prefs.getInt(KEY_REQUIRED_CORRECT, 10),
                
                // New audio settings
                toneFrequencyHz = prefs.getInt(KEY_TONE_FREQUENCY, 600),
                farnsworthWpm = prefs.getInt(KEY_FARNSWORTH_WPM, 0),
                wordSpacingMs = prefs.getInt(KEY_WORD_SPACING, 0),
                groupSpacingMs = prefs.getInt(KEY_GROUP_SPACING, 0)
            )
        }
    }
    
    /**
     * Save settings to SharedPreferences
     */
    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_SPEED_WPM, speedWpm)
            putInt(KEY_KOCH_LEVEL, kochLevel)
            putBoolean(KEY_LEVEL_LOCKED, isLevelLocked)
            putInt(KEY_GROUP_SIZE_MIN, groupSizeMin)
            putInt(KEY_GROUP_SIZE_MAX, groupSizeMax)
            putInt(KEY_ANSWER_TIMEOUT, answerTimeoutSeconds)
            putInt(KEY_REPEAT_COUNT, repeatCount)
            putInt(KEY_REPEAT_SPACING, repeatSpacingMs)
            putInt(KEY_REQUIRED_CORRECT, requiredCorrectToAdvance)
            
            // New audio settings
            putInt(KEY_TONE_FREQUENCY, toneFrequencyHz)
            putInt(KEY_FARNSWORTH_WPM, farnsworthWpm)
            putInt(KEY_WORD_SPACING, wordSpacingMs)
            putInt(KEY_GROUP_SPACING, groupSpacingMs)
            apply()
        }
    }
} 