package com.so5km.qrstrainer.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Training settings for the Morse code trainer
 */
data class TrainingSettings(
    val speedWpm: Int = 25,                    // Words per minute (updated default for new range)
    val kochLevel: Int = 2,                    // Current Koch level (1-based)
    val isLevelLocked: Boolean = false,        // Whether to lock at current level
    val groupSizeMin: Int = 1,                 // Minimum group size
    val groupSizeMax: Int = 5,                 // Maximum group size
    val answerTimeoutSeconds: Int = 3,         // Timeout for answers (much shorter default)
    val repeatCount: Int = 1,                  // How many times to repeat (faster default)
    val repeatSpacingMs: Int = 0,              // Milliseconds between repeats (no delay default)
    val requiredCorrectToAdvance: Int = 3,     // Required correct answers to advance level (faster progression)
    val sequenceDelayMs: Int = 0,              // Delay between sequences after answering (immediate default)
    
    // Audio settings
    val toneFrequencyHz: Int = 600,            // Tone frequency in Hz (300-1000)
    val farnsworthWpm: Int = 0,                // Farnsworth timing WPM (0 = disabled)
    val wordSpacingMs: Int = 0,                // Extra word spacing in ms (0 = default)
    val groupSpacingMs: Int = 0,               // Extra group spacing in ms (0 = default)
    
    // CW Filter settings
    val filterBandwidthHz: Int = 500,          // Filter bandwidth in Hz (100-2000)
    val filterQFactor: Float = 5.0f,           // Q factor for filter ringing (1.0-20.0)
    val backgroundNoiseLevel: Float = 0.1f,    // Background noise level (0.0-1.0)
    val filterRingingEnabled: Boolean = true   // Enable/disable filter ringing effect
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
        private const val KEY_SEQUENCE_DELAY = "sequence_delay"
        
        // Audio settings keys
        private const val KEY_TONE_FREQUENCY = "tone_frequency"
        private const val KEY_FARNSWORTH_WPM = "farnsworth_wpm"
        private const val KEY_WORD_SPACING = "word_spacing"
        private const val KEY_GROUP_SPACING = "group_spacing"
        
        // CW Filter settings keys
        private const val KEY_FILTER_BANDWIDTH = "filter_bandwidth"
        private const val KEY_FILTER_Q_FACTOR = "filter_q_factor"
        private const val KEY_BACKGROUND_NOISE = "background_noise"
        private const val KEY_FILTER_RINGING = "filter_ringing"
        
        /**
         * Load settings from SharedPreferences
         */
        fun load(context: Context): TrainingSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return TrainingSettings(
                speedWpm = prefs.getInt(KEY_SPEED_WPM, 25),
                kochLevel = prefs.getInt(KEY_KOCH_LEVEL, 2),
                isLevelLocked = prefs.getBoolean(KEY_LEVEL_LOCKED, false),
                groupSizeMin = prefs.getInt(KEY_GROUP_SIZE_MIN, 1),
                groupSizeMax = prefs.getInt(KEY_GROUP_SIZE_MAX, 5),
                answerTimeoutSeconds = prefs.getInt(KEY_ANSWER_TIMEOUT, 3),
                repeatCount = prefs.getInt(KEY_REPEAT_COUNT, 1),
                repeatSpacingMs = prefs.getInt(KEY_REPEAT_SPACING, 0),
                requiredCorrectToAdvance = prefs.getInt(KEY_REQUIRED_CORRECT, 3),
                sequenceDelayMs = prefs.getInt(KEY_SEQUENCE_DELAY, 0),
                
                // Audio settings
                toneFrequencyHz = prefs.getInt(KEY_TONE_FREQUENCY, 600),
                farnsworthWpm = prefs.getInt(KEY_FARNSWORTH_WPM, 0),
                wordSpacingMs = prefs.getInt(KEY_WORD_SPACING, 0),
                groupSpacingMs = prefs.getInt(KEY_GROUP_SPACING, 0),
                
                // CW Filter settings
                filterBandwidthHz = prefs.getInt(KEY_FILTER_BANDWIDTH, 500),
                filterQFactor = prefs.getFloat(KEY_FILTER_Q_FACTOR, 5.0f),
                backgroundNoiseLevel = prefs.getFloat(KEY_BACKGROUND_NOISE, 0.1f),
                filterRingingEnabled = prefs.getBoolean(KEY_FILTER_RINGING, true)
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
            putInt(KEY_SEQUENCE_DELAY, sequenceDelayMs)
            
            // Audio settings
            putInt(KEY_TONE_FREQUENCY, toneFrequencyHz)
            putInt(KEY_FARNSWORTH_WPM, farnsworthWpm)
            putInt(KEY_WORD_SPACING, wordSpacingMs)
            putInt(KEY_GROUP_SPACING, groupSpacingMs)
            
            // CW Filter settings
            putInt(KEY_FILTER_BANDWIDTH, filterBandwidthHz)
            putFloat(KEY_FILTER_Q_FACTOR, filterQFactor)
            putFloat(KEY_BACKGROUND_NOISE, backgroundNoiseLevel)
            putBoolean(KEY_FILTER_RINGING, filterRingingEnabled)
            apply()
        }
    }
} 