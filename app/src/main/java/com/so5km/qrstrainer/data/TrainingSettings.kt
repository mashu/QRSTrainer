package com.so5km.qrstrainer.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Training settings for the Morse code trainer
 * Reorganized to separate training behavior from audio generation settings
 */
data class TrainingSettings(
    // === TRAINING BEHAVIOR SETTINGS ===
    val speedWpm: Int = 25,                    // Words per minute (affects training progression)
    val kochLevel: Int = 2,                    // Current Koch level (1-based)
    val isLevelLocked: Boolean = false,        // Whether to lock at current level
    val groupSizeMin: Int = 1,                 // Minimum group size
    val groupSizeMax: Int = 5,                 // Maximum group size
    val answerTimeoutSeconds: Int = 3,         // Timeout for answers
    val repeatCount: Int = 1,                  // How many times to repeat each sequence
    val repeatSpacingMs: Int = 0,              // Milliseconds between repeats
    val requiredCorrectToAdvance: Int = 3,     // Required correct answers to advance level
    val sequenceDelayMs: Int = 0,              // Delay between sequences after answering
    val mistakesToDropLevel: Int = 1,          // Number of mistakes to cause level drop (0 = disabled)
    val farnsworthWpm: Int = 0,                // Farnsworth timing WPM (0 = disabled) - affects character timing
    
    // === AUDIO GENERATION SETTINGS ===
    val toneFrequencyHz: Int = 600,            // Tone frequency in Hz (300-1000)
    val wordSpacingMs: Int = 0,                // Extra word spacing in ms (0 = default)
    val groupSpacingMs: Int = 0,               // Extra group spacing in ms (0 = default)
    val appVolumeLevel: Float = 0.7f,          // App-specific volume (0.0-1.0)
    val audioEnvelopeMs: Int = 5,              // Audio envelope rise/fall time (1-20ms)
    val keyingStyle: Int = 0,                  // Keying style: 0=Hard, 1=Soft, 2=Smooth
    
    // === CW FILTER SETTINGS ===
    val filterBandwidthHz: Int = 500,          // Primary filter bandwidth in Hz (100-2000)
    val secondaryFilterBandwidthHz: Int = 500, // Secondary filter bandwidth in Hz (100-2000)
    val filterQFactor: Float = 5.0f,           // Q factor for filter ringing (1.0-20.0)
    val backgroundNoiseLevel: Float = 0.1f,    // Background noise level (0.0-1.0)
    val filterRingingEnabled: Boolean = false,  // Enable/disable filter ringing effect (OFF by default)
    val primaryFilterOffset: Int = 0,          // Primary filter offset from tone freq (-200 to +200 Hz)
    val secondaryFilterOffset: Int = 0         // Secondary filter offset from tone freq (-200 to +200 Hz)
) {
    companion object {
        private const val PREFS_NAME = "morse_trainer_settings"
        
        // Training behavior settings keys
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
        private const val KEY_MISTAKES_TO_DROP = "mistakes_to_drop"
        private const val KEY_FARNSWORTH_WPM = "farnsworth_wpm"
        
        // Audio generation settings keys
        private const val KEY_TONE_FREQUENCY = "tone_frequency"
        private const val KEY_WORD_SPACING = "word_spacing"
        private const val KEY_GROUP_SPACING = "group_spacing"
        private const val KEY_APP_VOLUME = "app_volume"
        private const val KEY_AUDIO_ENVELOPE = "audio_envelope"
        private const val KEY_KEYING_STYLE = "keying_style"
        
        // CW Filter settings keys
        private const val KEY_FILTER_BANDWIDTH = "filter_bandwidth"
        private const val KEY_SECONDARY_FILTER_BANDWIDTH = "secondary_filter_bandwidth"
        private const val KEY_FILTER_Q_FACTOR = "filter_q_factor"
        private const val KEY_BACKGROUND_NOISE = "background_noise"
        private const val KEY_FILTER_RINGING = "filter_ringing"
        private const val KEY_PRIMARY_FILTER_OFFSET = "primary_filter_offset"
        private const val KEY_SECONDARY_FILTER_OFFSET = "secondary_filter_offset"
        
        /**
         * Load settings from SharedPreferences
         */
        fun load(context: Context): TrainingSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return TrainingSettings(
                // Training behavior settings
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
                mistakesToDropLevel = prefs.getInt(KEY_MISTAKES_TO_DROP, 1),
                farnsworthWpm = prefs.getInt(KEY_FARNSWORTH_WPM, 0),
                
                // Audio generation settings
                toneFrequencyHz = prefs.getInt(KEY_TONE_FREQUENCY, 600),
                wordSpacingMs = prefs.getInt(KEY_WORD_SPACING, 0),
                groupSpacingMs = prefs.getInt(KEY_GROUP_SPACING, 0),
                appVolumeLevel = prefs.getFloat(KEY_APP_VOLUME, 0.7f),
                audioEnvelopeMs = prefs.getInt(KEY_AUDIO_ENVELOPE, 5),
                keyingStyle = prefs.getInt(KEY_KEYING_STYLE, 0),
                
                // CW Filter settings
                filterBandwidthHz = prefs.getInt(KEY_FILTER_BANDWIDTH, 500),
                secondaryFilterBandwidthHz = prefs.getInt(KEY_SECONDARY_FILTER_BANDWIDTH, 500),
                filterQFactor = prefs.getFloat(KEY_FILTER_Q_FACTOR, 5.0f),
                backgroundNoiseLevel = prefs.getFloat(KEY_BACKGROUND_NOISE, 0.1f),
                filterRingingEnabled = prefs.getBoolean(KEY_FILTER_RINGING, false),
                primaryFilterOffset = prefs.getInt(KEY_PRIMARY_FILTER_OFFSET, 0),
                secondaryFilterOffset = prefs.getInt(KEY_SECONDARY_FILTER_OFFSET, 0)
            )
        }
    }
    
    /**
     * Save settings to SharedPreferences
     */
    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            // Training behavior settings
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
            putInt(KEY_MISTAKES_TO_DROP, mistakesToDropLevel)
            putInt(KEY_FARNSWORTH_WPM, farnsworthWpm)
            
            // Audio generation settings
            putInt(KEY_TONE_FREQUENCY, toneFrequencyHz)
            putInt(KEY_WORD_SPACING, wordSpacingMs)
            putInt(KEY_GROUP_SPACING, groupSpacingMs)
            putFloat(KEY_APP_VOLUME, appVolumeLevel)
            putInt(KEY_AUDIO_ENVELOPE, audioEnvelopeMs)
            putInt(KEY_KEYING_STYLE, keyingStyle)
            
            // CW Filter settings
            putInt(KEY_FILTER_BANDWIDTH, filterBandwidthHz)
            putInt(KEY_SECONDARY_FILTER_BANDWIDTH, secondaryFilterBandwidthHz)
            putFloat(KEY_FILTER_Q_FACTOR, filterQFactor)
            putFloat(KEY_BACKGROUND_NOISE, backgroundNoiseLevel)
            putBoolean(KEY_FILTER_RINGING, filterRingingEnabled)
            putInt(KEY_PRIMARY_FILTER_OFFSET, primaryFilterOffset)
            putInt(KEY_SECONDARY_FILTER_OFFSET, secondaryFilterOffset)
            apply()
        }
    }
} 