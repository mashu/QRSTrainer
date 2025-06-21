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
    val levelChangeDelayMs: Int = 1500,        // Delay when level changes (advancement/drop)
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
    val filterBandwidthHz: Int = 250,          // Primary filter bandwidth in Hz (100-2000)
    val secondaryFilterBandwidthHz: Int = 300, // Secondary filter bandwidth in Hz (100-2000)
    val filterQFactor: Float = 15.0f,           // Q factor for filter ringing (1.0-20.0) - Moderate Q for pleasant sound
    val backgroundNoiseLevel: Float = 0.3f,    // Background noise level (0.0-1.0) - DEPRECATED, use noiseVolume instead  
    val noiseVolume: Float = 0.3f,             // Independent noise volume (0.0-1.0) - Moderate level for pleasant background
    val filterRingingEnabled: Boolean = false,  // Enable/disable filter ringing effect (OFF by default)
    val primaryFilterOffset: Int = 0,          // Primary filter offset from tone freq (-200 to +200 Hz)
    val secondaryFilterOffset: Int = 30,        // Secondary filter offset from tone freq (-200 to +200 Hz)
    
    // === CW LFO SETTINGS ===
    val lfo1FrequencyHz: Float = 0.1f,         // Primary LFO frequency in Hz (0.05-0.5)
    val lfo2FrequencyHz: Float = 0.17f,        // Secondary LFO frequency in Hz (0.05-0.5)
    val continuousNoiseEnabled: Boolean = false, // Enable continuous noise playback for testing
    
    // === CW ATMOSPHERIC SETTINGS ===
    val atmosphericIntensity: Float = 2.0f,    // Atmospheric noise intensity (0.5-5.0) - Moderate, pleasant default
    val crackleIntensity: Float = 0.05f,       // Random pops and crackles (0.01-0.2) - Light, subtle interference
    val resonanceJumpRate: Float = 0.3f,       // Resonance jump frequency (0.1-2.0) - Gentle CW pings
    val driftSpeed: Float = 0.4f,              // Frequency drift speed (0.1-2.0) - Slow, natural drift
    
    // === UI STATE SETTINGS ===
    val lastExpandedSettingsTab: String = "audio",  // Remember which settings tab was last expanded
    val warmth: Float = 8.0f // matches reference Warmth setting
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
        private const val KEY_LEVEL_CHANGE_DELAY = "level_change_delay"
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
        private const val KEY_NOISE_VOLUME = "noise_volume"
        private const val KEY_FILTER_RINGING = "filter_ringing"
        private const val KEY_PRIMARY_FILTER_OFFSET = "primary_filter_offset"
        private const val KEY_SECONDARY_FILTER_OFFSET = "secondary_filter_offset"
        
        // CW LFO settings keys
        private const val KEY_LFO1_FREQUENCY = "lfo1_frequency"
        private const val KEY_LFO2_FREQUENCY = "lfo2_frequency"
        private const val KEY_CONTINUOUS_NOISE = "continuous_noise"
        
        // CW Atmospheric settings keys
        private const val KEY_ATMOSPHERIC_INTENSITY = "atmospheric_intensity"
        private const val KEY_CRACKLE_INTENSITY = "crackle_intensity"
        private const val KEY_RESONANCE_JUMP_RATE = "resonance_jump_rate"
        private const val KEY_DRIFT_SPEED = "drift_speed"
        private const val KEY_WARMTH = "warmth"
        
        // UI State settings keys
        private const val KEY_LAST_EXPANDED_SETTINGS_TAB = "last_expanded_settings_tab"
        
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
                levelChangeDelayMs = prefs.getInt(KEY_LEVEL_CHANGE_DELAY, 1500),
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
                filterBandwidthHz = prefs.getInt(KEY_FILTER_BANDWIDTH, 250),
                secondaryFilterBandwidthHz = prefs.getInt(KEY_SECONDARY_FILTER_BANDWIDTH, 300),
                filterQFactor = prefs.getFloat(KEY_FILTER_Q_FACTOR, 15.0f),
                backgroundNoiseLevel = prefs.getFloat(KEY_BACKGROUND_NOISE, 0.3f),
                noiseVolume = prefs.getFloat(KEY_NOISE_VOLUME, 0.3f),
                filterRingingEnabled = prefs.getBoolean(KEY_FILTER_RINGING, false),
                primaryFilterOffset = prefs.getInt(KEY_PRIMARY_FILTER_OFFSET, 0),
                secondaryFilterOffset = prefs.getInt(KEY_SECONDARY_FILTER_OFFSET, 30),
                
                // CW LFO settings
                lfo1FrequencyHz = prefs.getFloat(KEY_LFO1_FREQUENCY, 0.1f),
                lfo2FrequencyHz = prefs.getFloat(KEY_LFO2_FREQUENCY, 0.17f),
                continuousNoiseEnabled = prefs.getBoolean(KEY_CONTINUOUS_NOISE, false),
                
                // CW Atmospheric settings
                atmosphericIntensity = prefs.getFloat(KEY_ATMOSPHERIC_INTENSITY, 2.0f),
                crackleIntensity = prefs.getFloat(KEY_CRACKLE_INTENSITY, 0.05f),
                resonanceJumpRate = prefs.getFloat(KEY_RESONANCE_JUMP_RATE, 0.3f),
                driftSpeed = prefs.getFloat(KEY_DRIFT_SPEED, 0.4f),
                
                // UI State settings
                lastExpandedSettingsTab = prefs.getString(KEY_LAST_EXPANDED_SETTINGS_TAB, "audio") ?: "audio",
                warmth = prefs.getFloat(KEY_WARMTH, 8.0f)
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
            putInt(KEY_LEVEL_CHANGE_DELAY, levelChangeDelayMs)
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
            putFloat(KEY_NOISE_VOLUME, noiseVolume)
            putBoolean(KEY_FILTER_RINGING, filterRingingEnabled)
            putInt(KEY_PRIMARY_FILTER_OFFSET, primaryFilterOffset)
            putInt(KEY_SECONDARY_FILTER_OFFSET, secondaryFilterOffset)
            
            // CW LFO settings
            putFloat(KEY_LFO1_FREQUENCY, lfo1FrequencyHz)
            putFloat(KEY_LFO2_FREQUENCY, lfo2FrequencyHz)
            putBoolean(KEY_CONTINUOUS_NOISE, continuousNoiseEnabled)
            
            // CW Atmospheric settings
            putFloat(KEY_ATMOSPHERIC_INTENSITY, atmosphericIntensity)
            putFloat(KEY_CRACKLE_INTENSITY, crackleIntensity)
            putFloat(KEY_RESONANCE_JUMP_RATE, resonanceJumpRate)
            putFloat(KEY_DRIFT_SPEED, driftSpeed)
            
            // UI State settings
            putString(KEY_LAST_EXPANDED_SETTINGS_TAB, lastExpandedSettingsTab)
            putFloat(KEY_WARMTH, warmth)
            apply()
        }
    }
    
    /**
     * Reset only audio-related settings to defaults while preserving training behavior and progress
     */
    fun resetAudioSettings(): TrainingSettings {
        val defaults = TrainingSettings()
        return this.copy(
            // Reset all audio generation settings to defaults
            toneFrequencyHz = defaults.toneFrequencyHz,
            wordSpacingMs = defaults.wordSpacingMs,
            groupSpacingMs = defaults.groupSpacingMs,
            appVolumeLevel = defaults.appVolumeLevel,
            audioEnvelopeMs = defaults.audioEnvelopeMs,
            keyingStyle = defaults.keyingStyle,
            
            // Reset all CW filter settings to defaults
            filterBandwidthHz = defaults.filterBandwidthHz,
            secondaryFilterBandwidthHz = defaults.secondaryFilterBandwidthHz,
            filterQFactor = defaults.filterQFactor,
            backgroundNoiseLevel = defaults.backgroundNoiseLevel,
            noiseVolume = defaults.noiseVolume,
            filterRingingEnabled = defaults.filterRingingEnabled,
            primaryFilterOffset = defaults.primaryFilterOffset,
            secondaryFilterOffset = defaults.secondaryFilterOffset,
            
            // Reset all CW LFO settings to defaults
            lfo1FrequencyHz = defaults.lfo1FrequencyHz,
            lfo2FrequencyHz = defaults.lfo2FrequencyHz,
            continuousNoiseEnabled = defaults.continuousNoiseEnabled,
            
            // Reset all CW atmospheric settings to defaults
            atmosphericIntensity = defaults.atmosphericIntensity,
            crackleIntensity = defaults.crackleIntensity,
            resonanceJumpRate = defaults.resonanceJumpRate,
            driftSpeed = defaults.driftSpeed,
            warmth = defaults.warmth
            
            // Note: Training behavior settings (speedWpm, kochLevel, etc.) are NOT reset
            // Note: UI state settings (lastExpandedSettingsTab) are NOT reset
        )
    }
} 