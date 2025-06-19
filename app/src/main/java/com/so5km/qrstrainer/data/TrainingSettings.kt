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
    val requiredCorrectToAdvance: Int = 10     // Required correct answers to advance level
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
                requiredCorrectToAdvance = prefs.getInt(KEY_REQUIRED_CORRECT, 10)
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
            apply()
        }
    }
} 