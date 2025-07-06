package com.so5km.qrstrainer.data

import org.json.JSONObject

data class TrainingSettings(
    val wpm: Int = 20,
    val effectiveWpm: Int = 20,
    val sequenceLength: Int = 5,
    val sequenceDelayMs: Long = 1000,
    val frequency: Int = 600,
    val volume: Float = 0.8f,
    val riseTimeMs: Double = 5.0,
    val noiseEnabled: Boolean = false,
    val noiseVolume: Float = 0.3f,
    val noiseBandwidthHz: Float = 1000f,
    val currentLevel: Int = 1,
    val useProsigns: Boolean = false
) {
    companion object {
        fun default() = TrainingSettings()
        
        // Add any validation or utility methods here
        fun validate(settings: TrainingSettings): TrainingSettings {
            return settings.copy(
                wpm = settings.wpm.coerceIn(1, 50),
                effectiveWpm = settings.effectiveWpm.coerceIn(1, 50),
                sequenceLength = settings.sequenceLength.coerceIn(1, 20),
                sequenceDelayMs = settings.sequenceDelayMs.coerceIn(100, 5000),
                frequency = settings.frequency.coerceIn(300, 1000),
                volume = settings.volume.coerceIn(0f, 1f),
                riseTimeMs = settings.riseTimeMs.coerceIn(1.0, 50.0),
                noiseVolume = settings.noiseVolume.coerceIn(0f, 1f),
                noiseBandwidthHz = settings.noiseBandwidthHz.coerceIn(100f, 5000f),
                currentLevel = settings.currentLevel.coerceIn(1, 10)
            )
        }
    }
}

// Extension functions for JSON serialization
fun TrainingSettings.toJson(): String {
    val json = JSONObject()
    json.put("wpm", wpm)
    json.put("effectiveWpm", effectiveWpm)
    json.put("frequency", frequency)
    json.put("volume", volume.toDouble())
    json.put("currentLevel", currentLevel)
    json.put("sequenceLength", sequenceLength)
    json.put("sequenceDelayMs", sequenceDelayMs)
    json.put("riseTimeMs", riseTimeMs)
    json.put("useProsigns", useProsigns)
    json.put("noiseEnabled", noiseEnabled)
    json.put("noiseVolume", noiseVolume.toDouble())
    json.put("noiseBandwidthHz", noiseBandwidthHz.toDouble())
    return json.toString(2)
}

fun TrainingSettings.Companion.fromJson(json: String): TrainingSettings {
    val obj = JSONObject(json)
    return TrainingSettings(
        wpm = obj.getInt("wpm"),
        effectiveWpm = obj.getInt("effectiveWpm"),
        frequency = obj.getInt("frequency"),
        volume = obj.getDouble("volume").toFloat(),
        currentLevel = obj.getInt("currentLevel"),
        sequenceLength = obj.getInt("sequenceLength"),
        sequenceDelayMs = obj.getLong("sequenceDelayMs"),
        riseTimeMs = obj.getDouble("riseTimeMs"),
        useProsigns = obj.getBoolean("useProsigns"),
        noiseEnabled = obj.getBoolean("noiseEnabled"),
        noiseVolume = obj.getDouble("noiseVolume").toFloat(),
        noiseBandwidthHz = obj.getDouble("noiseBandwidthHz").toFloat()
    )
}
