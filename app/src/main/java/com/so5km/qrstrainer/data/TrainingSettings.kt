package com.so5km.qrstrainer.data

import org.json.JSONObject

data class TrainingSettings(
    // Audio Settings
    val wpm: Int = 20,
    val effectiveWpm: Int = 20,
    val frequency: Int = 600,
    val volume: Float = 0.8f,
    val riseTimeMs: Double = 5.0,
    
    // Group Settings
    val minGroupSize: Int = 1,
    val maxGroupSize: Int = 5,
    val sequenceLength: Int = 5, // number of groups per sequence
    
    // Timing & Repeats
    val sequenceDelayMs: Long = 1000, // delay between sequences
    val repeatDelayMs: Long = 500,    // delay between repeats of same sequence
    val groupDelayMs: Long = 2000,    // delay between character groups
    val numberOfRepeats: Int = 2,     // how many times to repeat each sequence
    
    // Level Management
    val currentLevel: Int = 1,
    val maxLevel: Int = 40,
    val lockLevel: Boolean = false,           // prevents automatic level changes
    val correctAnswersToLevelUp: Int = 10,    // consecutive correct needed to advance
    val incorrectAnswersToDropLevel: Int = 3, // consecutive wrong to drop level
    
    // Training Dynamics
    val adaptiveSpeed: Boolean = false,       // automatically adjust WPM based on performance
    val adaptiveGroupSize: Boolean = false,   // automatically adjust group size
    val requirePerfectCopy: Boolean = false,  // must get every character right
    val allowPartialCredit: Boolean = true,   // partial points for partially correct
    val dynamicSpacing: Boolean = true,       // adjust spacing based on performance
    
    // Character Selection
    val useProsigns: Boolean = false,
    val useNumbers: Boolean = false,
    val usePunctuation: Boolean = false,
    val customCharacterSet: String = "",      // custom characters to practice
    
    // Noise Settings
    val noiseEnabled: Boolean = false,
    val noiseVolume: Float = 0.3f,
    val noiseBandwidthHz: Float = 1000f,
    val filterType: String = "butterworth",    // butterworth, chebyshev, elliptic
    val filterOrder: Int = 4,                  // 2, 4, 6, 8 - higher = steeper but more ringing
    val qrmEnabled: Boolean = true,           // QRM (interference) simulation - default true for realism
    val qrmVolume: Float = 0.2f,
    val qsbEnabled: Boolean = false,          // QSB (fading) simulation
    val qsbRate: Float = 0.1f,                // fading rate
    
    // Advanced Audio
    val clicksEnabled: Boolean = false,       // key clicks
    val clickVolume: Float = 0.1f,
    val bandwidthHz: Float = 50f,             // signal bandwidth
    val shapingEnabled: Boolean = true,       // waveform shaping
    
    // Text-to-Speech Settings
    val ttsVolume: Float = 0.8f,             // TTS volume (0.0 to 1.0)
    val ttsSpeechRate: Float = 1.0f,         // TTS speech rate (0.1 to 3.0)
    val ttsPitch: Float = 1.0f,              // TTS pitch (0.1 to 2.0)
    val ttsDelayMs: Long = 1000L,            // Delay before speaking (0 to 5000ms)
    val ttsSpeakInListenMode: Boolean = true, // Enable speaking in listen mode
    
    // Auto-Reveal Settings
    val autoRevealEnabled: Boolean = true,   // Enable auto-reveal in listen mode
    val autoRevealDelayMs: Long = 3000L,     // Auto-reveal countdown delay (0 to 10000ms)
    val postRevealDelayMs: Long = 2000L,     // Delay after reveal before auto-advance (0 to 5000ms)
    
    // Listen Mode Settings (separate from trainer)
    val listenWpm: Int = 20,                 // Listen mode character speed (5-60)
    val listenEffectiveWpm: Int = 20,        // Listen mode effective speed/Farnsworth (5-60)
    val listenMinGroupSize: Int = 1,         // Minimum characters per group (1-10)
    val listenMaxGroupSize: Int = 5,         // Maximum characters per group (1-10)
    val listenSequenceLength: Int = 5,       // Number of groups per sequence (1-15) - kept for compatibility
    val listenMinSequenceLength: Int = 3,    // Minimum number of groups per sequence (1-15)
    val listenMaxSequenceLength: Int = 7,    // Maximum number of groups per sequence (1-15)
    val listenNumberOfRepeats: Int = 1,      // How many times to repeat each sequence (1-5)
    val listenSequenceDelayMs: Long = 500,   // Delay after sequence playback completes (0-3000ms)
    val listenRepeatDelayMs: Long = 300,     // Delay between repeats of same sequence (0-2000ms)
    val listenGroupDelayMs: Long = 1000,     // Delay between character groups (0-5000ms)
    val listenNextDelayMs: Long = 300,       // Delay before auto-starting next sequence (0-2000ms)
    
    // Appearance
    val themeMode: String = "light",          // "light", "dark", or "system"
    
    // Progress Tracking
    val enableStatistics: Boolean = true,
    val saveProgress: Boolean = true,
    val sessionTimeMinutes: Int = 15,         // target session length
    val breakReminderMinutes: Int = 60        // remind for breaks
) {
    companion object {
        /**
         * Calculate the maximum valid level based on character settings
         * Each level adds 1 character from the available character set
         * Traditional Koch: Level 1 = 2 chars, Level 2 = 3 chars, etc.
         * Max level = total available characters - 1 (since level + 1 = character count)
         */
        fun calculateMaxLevel(
            useNumbers: Boolean = false,
            usePunctuation: Boolean = false,
            useProsigns: Boolean = false,
            customCharacterSet: String = ""
        ): Int {
            // Count total available characters
            var totalChars = 26 // Base alphabet (A-Z)
            
            if (useNumbers) totalChars += 10 // 0-9
            if (usePunctuation) totalChars += 7 // . , ? / = + -
            if (useProsigns) totalChars += 3 // < > @
            totalChars += customCharacterSet.length
            
            // Max level = total characters - 1 (since level + 1 = character count)
            // This ensures we never exceed the available character set
            return totalChars - 1
        }
        
        /**
         * Validate and adjust settings to ensure consistency
         * This is the centralized place for all validation logic
         */
        fun validate(settings: TrainingSettings): TrainingSettings {
            // Calculate the actual maximum level for current character settings
            val actualMaxLevel = calculateMaxLevel(
                useNumbers = settings.useNumbers,
                usePunctuation = settings.usePunctuation,
                useProsigns = settings.useProsigns,
                customCharacterSet = settings.customCharacterSet
            )
            
            // Ensure current level doesn't exceed what's available
            val validCurrentLevel = settings.currentLevel.coerceIn(1, actualMaxLevel)
            
            // Ensure maxLevel setting reflects the actual maximum
            val validMaxLevel = actualMaxLevel
            
            // Ensure group sizes are valid
            val validMinGroupSize = settings.minGroupSize.coerceIn(1, 10)
            val validMaxGroupSize = settings.maxGroupSize.coerceIn(validMinGroupSize, 20)
            
            // Ensure listen sequence lengths are valid
            val validListenMinSequenceLength = settings.listenMinSequenceLength.coerceIn(1, 15)
            val validListenMaxSequenceLength = settings.listenMaxSequenceLength.coerceIn(validListenMinSequenceLength, 15)
            
            // Ensure WPM values are valid
            val validWpm = settings.wpm.coerceIn(5, 60)
            val validEffectiveWpm = settings.effectiveWpm.coerceIn(5, validWpm)
            
            // Ensure other values are within reasonable bounds
            val validFrequency = settings.frequency.coerceIn(300, 1000)
            val validVolume = settings.volume.coerceIn(0f, 1f)
            val validNoiseVolume = settings.noiseVolume.coerceIn(0f, 1f)
            
            // Ensure TTS values are within reasonable bounds
            val validTtsVolume = settings.ttsVolume.coerceIn(0f, 1f)
            val validTtsSpeechRate = settings.ttsSpeechRate.coerceIn(0.1f, 3.0f)
            val validTtsPitch = settings.ttsPitch.coerceIn(0.1f, 2.0f)
            val validTtsDelayMs = settings.ttsDelayMs.coerceIn(0L, 5000L)
            
            // Ensure auto-reveal delay is within reasonable bounds
            val validAutoRevealDelayMs = settings.autoRevealDelayMs.coerceIn(1000L, 10000L)
            
            // Ensure level progression values are reasonable
            val validCorrectAnswersToLevelUp = settings.correctAnswersToLevelUp.coerceIn(3, 20)
            val validIncorrectAnswersToDropLevel = settings.incorrectAnswersToDropLevel.coerceIn(2, 10)
            
            // Ensure theme mode is valid
            val validThemeMode = if (settings.themeMode in listOf("light", "dark", "system")) {
                settings.themeMode
            } else {
                "light"
            }
            
            return settings.copy(
                currentLevel = validCurrentLevel,
                maxLevel = validMaxLevel,
                minGroupSize = validMinGroupSize,
                maxGroupSize = validMaxGroupSize,
                listenMinSequenceLength = validListenMinSequenceLength,
                listenMaxSequenceLength = validListenMaxSequenceLength,
                wpm = validWpm,
                effectiveWpm = validEffectiveWpm,
                frequency = validFrequency,
                volume = validVolume,
                noiseVolume = validNoiseVolume,
                correctAnswersToLevelUp = validCorrectAnswersToLevelUp,
                incorrectAnswersToDropLevel = validIncorrectAnswersToDropLevel,
                ttsVolume = validTtsVolume,
                ttsSpeechRate = validTtsSpeechRate,
                ttsPitch = validTtsPitch,
                ttsDelayMs = validTtsDelayMs,
                autoRevealDelayMs = validAutoRevealDelayMs,
                themeMode = validThemeMode
            )
        }
        
        fun default(): TrainingSettings {
            val defaultSettings = TrainingSettings()
            return validate(defaultSettings) // Ensure even defaults are validated
        }
    }
}

// Extension functions for JSON serialization
fun TrainingSettings.toJson(): String {
    val json = JSONObject()
    // Audio Settings
    json.put("wpm", wpm)
    json.put("effectiveWpm", effectiveWpm)
    json.put("frequency", frequency)
    json.put("volume", volume.toDouble())
    json.put("riseTimeMs", riseTimeMs)
    
    // Group Settings
    json.put("minGroupSize", minGroupSize)
    json.put("maxGroupSize", maxGroupSize)
    json.put("sequenceLength", sequenceLength)
    
    // Timing & Repeats
    json.put("sequenceDelayMs", sequenceDelayMs)
    json.put("repeatDelayMs", repeatDelayMs)
    json.put("groupDelayMs", groupDelayMs)
    json.put("numberOfRepeats", numberOfRepeats)
    
    // Level Management
    json.put("currentLevel", currentLevel)
    json.put("maxLevel", maxLevel)
    json.put("lockLevel", lockLevel)
    json.put("correctAnswersToLevelUp", correctAnswersToLevelUp)
    json.put("incorrectAnswersToDropLevel", incorrectAnswersToDropLevel)
    
    // Training Dynamics
    json.put("adaptiveSpeed", adaptiveSpeed)
    json.put("adaptiveGroupSize", adaptiveGroupSize)
    json.put("requirePerfectCopy", requirePerfectCopy)
    json.put("allowPartialCredit", allowPartialCredit)
    json.put("dynamicSpacing", dynamicSpacing)
    
    // Character Selection
    json.put("useProsigns", useProsigns)
    json.put("useNumbers", useNumbers)
    json.put("usePunctuation", usePunctuation)
    json.put("customCharacterSet", customCharacterSet)
    
    // Noise Settings
    json.put("noiseEnabled", noiseEnabled)
    json.put("noiseVolume", noiseVolume.toDouble())
    json.put("noiseBandwidthHz", noiseBandwidthHz.toDouble())
    json.put("filterType", filterType)
    json.put("filterOrder", filterOrder)
    json.put("qrmEnabled", qrmEnabled)
    json.put("qrmVolume", qrmVolume.toDouble())
    json.put("qsbEnabled", qsbEnabled)
    json.put("qsbRate", qsbRate.toDouble())
    
    // Advanced Audio
    json.put("clicksEnabled", clicksEnabled)
    json.put("clickVolume", clickVolume.toDouble())
    json.put("bandwidthHz", bandwidthHz.toDouble())
    json.put("shapingEnabled", shapingEnabled)
    
    // Text-to-Speech Settings
    json.put("ttsVolume", ttsVolume.toDouble())
    json.put("ttsSpeechRate", ttsSpeechRate.toDouble())
    json.put("ttsPitch", ttsPitch.toDouble())
    json.put("ttsDelayMs", ttsDelayMs)
    json.put("ttsSpeakInListenMode", ttsSpeakInListenMode)
    
    // Auto-Reveal Settings
    json.put("autoRevealEnabled", autoRevealEnabled)
    json.put("autoRevealDelayMs", autoRevealDelayMs)
    json.put("postRevealDelayMs", postRevealDelayMs)
    
    // Listen Mode Settings
    json.put("listenWpm", listenWpm)
    json.put("listenEffectiveWpm", listenEffectiveWpm)
    json.put("listenMinGroupSize", listenMinGroupSize)
    json.put("listenMaxGroupSize", listenMaxGroupSize)
    json.put("listenSequenceLength", listenSequenceLength)
    json.put("listenMinSequenceLength", listenMinSequenceLength)
    json.put("listenMaxSequenceLength", listenMaxSequenceLength)
    json.put("listenNumberOfRepeats", listenNumberOfRepeats)
    json.put("listenSequenceDelayMs", listenSequenceDelayMs)
    json.put("listenRepeatDelayMs", listenRepeatDelayMs)
    json.put("listenGroupDelayMs", listenGroupDelayMs)
    json.put("listenNextDelayMs", listenNextDelayMs)
    
    // Appearance
    json.put("themeMode", themeMode)
    
    // Progress Tracking
    json.put("enableStatistics", enableStatistics)
    json.put("saveProgress", saveProgress)
    json.put("sessionTimeMinutes", sessionTimeMinutes)
    json.put("breakReminderMinutes", breakReminderMinutes)
    
    return json.toString(2)
}

fun TrainingSettings.Companion.fromJson(json: String): TrainingSettings {
    val obj = JSONObject(json)
    
    // Helper function to safely get values with defaults
    fun getIntOrDefault(key: String, default: Int) = if (obj.has(key)) obj.getInt(key) else default
    fun getLongOrDefault(key: String, default: Long) = if (obj.has(key)) obj.getLong(key) else default
    fun getFloatOrDefault(key: String, default: Float) = if (obj.has(key)) obj.getDouble(key).toFloat() else default
    fun getDoubleOrDefault(key: String, default: Double) = if (obj.has(key)) obj.getDouble(key) else default
    fun getBooleanOrDefault(key: String, default: Boolean) = if (obj.has(key)) obj.getBoolean(key) else default
    fun getStringOrDefault(key: String, default: String) = if (obj.has(key)) obj.getString(key) else default
    
    return TrainingSettings(
        // Audio Settings
        wpm = getIntOrDefault("wpm", 20),
        effectiveWpm = getIntOrDefault("effectiveWpm", 20),
        frequency = getIntOrDefault("frequency", 600),
        volume = getFloatOrDefault("volume", 0.8f),
        riseTimeMs = getDoubleOrDefault("riseTimeMs", 5.0),
        
        // Group Settings
        minGroupSize = getIntOrDefault("minGroupSize", 1),
        maxGroupSize = getIntOrDefault("maxGroupSize", 5),
        sequenceLength = getIntOrDefault("sequenceLength", 5),
        
        // Timing & Repeats
        sequenceDelayMs = getLongOrDefault("sequenceDelayMs", 1000),
        repeatDelayMs = getLongOrDefault("repeatDelayMs", 500),
        groupDelayMs = getLongOrDefault("groupDelayMs", 2000),
        numberOfRepeats = getIntOrDefault("numberOfRepeats", 2),
        
        // Level Management
        currentLevel = getIntOrDefault("currentLevel", 1),
        maxLevel = getIntOrDefault("maxLevel", 40),
        lockLevel = getBooleanOrDefault("lockLevel", false),
        correctAnswersToLevelUp = getIntOrDefault("correctAnswersToLevelUp", 10),
        incorrectAnswersToDropLevel = getIntOrDefault("incorrectAnswersToDropLevel", 3),
        
        // Training Dynamics
        adaptiveSpeed = getBooleanOrDefault("adaptiveSpeed", false),
        adaptiveGroupSize = getBooleanOrDefault("adaptiveGroupSize", false),
        requirePerfectCopy = getBooleanOrDefault("requirePerfectCopy", false),
        allowPartialCredit = getBooleanOrDefault("allowPartialCredit", true),
        dynamicSpacing = getBooleanOrDefault("dynamicSpacing", true),
        
        // Character Selection
        useProsigns = getBooleanOrDefault("useProsigns", false),
        useNumbers = getBooleanOrDefault("useNumbers", false),
        usePunctuation = getBooleanOrDefault("usePunctuation", false),
        customCharacterSet = getStringOrDefault("customCharacterSet", ""),
        
        // Noise Settings
        noiseEnabled = getBooleanOrDefault("noiseEnabled", false),
        noiseVolume = getFloatOrDefault("noiseVolume", 0.3f),
        noiseBandwidthHz = getFloatOrDefault("noiseBandwidthHz", 1000f),
        filterType = getStringOrDefault("filterType", "butterworth"),
        filterOrder = getIntOrDefault("filterOrder", 4),
        qrmEnabled = getBooleanOrDefault("qrmEnabled", true),
        qrmVolume = getFloatOrDefault("qrmVolume", 0.2f),
        qsbEnabled = getBooleanOrDefault("qsbEnabled", false),
        qsbRate = getFloatOrDefault("qsbRate", 0.1f),
        
        // Advanced Audio
        clicksEnabled = getBooleanOrDefault("clicksEnabled", false),
        clickVolume = getFloatOrDefault("clickVolume", 0.1f),
        bandwidthHz = getFloatOrDefault("bandwidthHz", 50f),
        shapingEnabled = getBooleanOrDefault("shapingEnabled", true),
        
        // Text-to-Speech Settings
        ttsVolume = getFloatOrDefault("ttsVolume", 0.8f),
        ttsSpeechRate = getFloatOrDefault("ttsSpeechRate", 1.0f),
        ttsPitch = getFloatOrDefault("ttsPitch", 1.0f),
        ttsDelayMs = getLongOrDefault("ttsDelayMs", 1000L),
        ttsSpeakInListenMode = getBooleanOrDefault("ttsSpeakInListenMode", true),
        
        // Auto-Reveal Settings
        autoRevealEnabled = getBooleanOrDefault("autoRevealEnabled", true),
        autoRevealDelayMs = getLongOrDefault("autoRevealDelayMs", 3000L),
        postRevealDelayMs = getLongOrDefault("postRevealDelayMs", 2000L),
        
        // Listen Mode Settings
        listenWpm = getIntOrDefault("listenWpm", 20),
        listenEffectiveWpm = getIntOrDefault("listenEffectiveWpm", 20),
        listenMinGroupSize = getIntOrDefault("listenMinGroupSize", 1),
        listenMaxGroupSize = getIntOrDefault("listenMaxGroupSize", 5),
        listenSequenceLength = getIntOrDefault("listenSequenceLength", 5),
        listenMinSequenceLength = getIntOrDefault("listenMinSequenceLength", 3),
        listenMaxSequenceLength = getIntOrDefault("listenMaxSequenceLength", 7),
        listenNumberOfRepeats = getIntOrDefault("listenNumberOfRepeats", 1),
        listenSequenceDelayMs = getLongOrDefault("listenSequenceDelayMs", 500L),
        listenRepeatDelayMs = getLongOrDefault("listenRepeatDelayMs", 300L),
        listenGroupDelayMs = getLongOrDefault("listenGroupDelayMs", 1000L),
        listenNextDelayMs = getLongOrDefault("listenNextDelayMs", 300L),
        
        // Appearance
        themeMode = getStringOrDefault("themeMode", "light"),
        
        // Progress Tracking
        enableStatistics = getBooleanOrDefault("enableStatistics", true),
        saveProgress = getBooleanOrDefault("saveProgress", true),
        sessionTimeMinutes = getIntOrDefault("sessionTimeMinutes", 15),
        breakReminderMinutes = getIntOrDefault("breakReminderMinutes", 60)
    )
}
