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
    val minGroupSize: Int = 3,
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
    val qrmEnabled: Boolean = false,          // QRM (interference) simulation
    val qrmVolume: Float = 0.2f,
    val qsbEnabled: Boolean = false,          // QSB (fading) simulation
    val qsbRate: Float = 0.1f,                // fading rate
    
    // Advanced Audio
    val clicksEnabled: Boolean = false,       // key clicks
    val clickVolume: Float = 0.1f,
    val bandwidthHz: Float = 50f,             // signal bandwidth
    val shapingEnabled: Boolean = true,       // waveform shaping
    
    // Progress Tracking
    val enableStatistics: Boolean = true,
    val saveProgress: Boolean = true,
    val sessionTimeMinutes: Int = 15,         // target session length
    val breakReminderMinutes: Int = 60        // remind for breaks
) {
    companion object {
        fun default() = TrainingSettings()
        
        fun validate(settings: TrainingSettings): TrainingSettings {
            return settings.copy(
                // Audio validation
                wpm = settings.wpm.coerceIn(1, 60),
                effectiveWpm = settings.effectiveWpm.coerceIn(1, settings.wpm),
                frequency = settings.frequency.coerceIn(200, 2000),
                volume = settings.volume.coerceIn(0f, 1f),
                riseTimeMs = settings.riseTimeMs.coerceIn(1.0, 50.0),
                
                // Group validation
                minGroupSize = settings.minGroupSize.coerceIn(1, 10),
                maxGroupSize = settings.maxGroupSize.coerceIn(settings.minGroupSize, 20),
                sequenceLength = settings.sequenceLength.coerceIn(1, 50),
                
                // Timing validation
                sequenceDelayMs = settings.sequenceDelayMs.coerceIn(100, 10000),
                repeatDelayMs = settings.repeatDelayMs.coerceIn(100, 5000),
                groupDelayMs = settings.groupDelayMs.coerceIn(500, 10000),
                numberOfRepeats = settings.numberOfRepeats.coerceIn(1, 10),
                
                // Level validation
                currentLevel = settings.currentLevel.coerceIn(1, settings.maxLevel),
                maxLevel = settings.maxLevel.coerceIn(1, 100),
                correctAnswersToLevelUp = settings.correctAnswersToLevelUp.coerceIn(1, 50),
                incorrectAnswersToDropLevel = settings.incorrectAnswersToDropLevel.coerceIn(1, 20),
                
                // Noise validation
                noiseVolume = settings.noiseVolume.coerceIn(0f, 1f),
                noiseBandwidthHz = settings.noiseBandwidthHz.coerceIn(100f, 5000f),
                qrmVolume = settings.qrmVolume.coerceIn(0f, 1f),
                qsbRate = settings.qsbRate.coerceIn(0.01f, 2f),
                
                // Advanced audio validation
                clickVolume = settings.clickVolume.coerceIn(0f, 0.5f),
                bandwidthHz = settings.bandwidthHz.coerceIn(10f, 500f),
                
                // Progress validation
                sessionTimeMinutes = settings.sessionTimeMinutes.coerceIn(1, 240),
                breakReminderMinutes = settings.breakReminderMinutes.coerceIn(10, 300)
            )
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
    json.put("qrmEnabled", qrmEnabled)
    json.put("qrmVolume", qrmVolume.toDouble())
    json.put("qsbEnabled", qsbEnabled)
    json.put("qsbRate", qsbRate.toDouble())
    
    // Advanced Audio
    json.put("clicksEnabled", clicksEnabled)
    json.put("clickVolume", clickVolume.toDouble())
    json.put("bandwidthHz", bandwidthHz.toDouble())
    json.put("shapingEnabled", shapingEnabled)
    
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
        minGroupSize = getIntOrDefault("minGroupSize", 3),
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
        qrmEnabled = getBooleanOrDefault("qrmEnabled", false),
        qrmVolume = getFloatOrDefault("qrmVolume", 0.2f),
        qsbEnabled = getBooleanOrDefault("qsbEnabled", false),
        qsbRate = getFloatOrDefault("qsbRate", 0.1f),
        
        // Advanced Audio
        clicksEnabled = getBooleanOrDefault("clicksEnabled", false),
        clickVolume = getFloatOrDefault("clickVolume", 0.1f),
        bandwidthHz = getFloatOrDefault("bandwidthHz", 50f),
        shapingEnabled = getBooleanOrDefault("shapingEnabled", true),
        
        // Progress Tracking
        enableStatistics = getBooleanOrDefault("enableStatistics", true),
        saveProgress = getBooleanOrDefault("saveProgress", true),
        sessionTimeMinutes = getIntOrDefault("sessionTimeMinutes", 15),
        breakReminderMinutes = getIntOrDefault("breakReminderMinutes", 60)
    )
}
