package com.so5km.qrstrainer.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Statistics for a single character
 */
data class CharacterStats(
    val character: Char,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val weight: Double = 1.0  // Weight for sampling probability
) {
    /**
     * Total attempts for this character
     */
    val totalAttempts: Int get() = correctCount + incorrectCount
    
    /**
     * Accuracy percentage for this character
     */
    val accuracy: Double get() = if (totalAttempts > 0) {
        (correctCount.toDouble() / totalAttempts) * 100.0
    } else 0.0
    
    /**
     * Create a new CharacterStats with an added correct answer
     */
    fun addCorrect(): CharacterStats = copy(correctCount = correctCount + 1)
    
    /**
     * Create a new CharacterStats with an added incorrect answer
     */
    fun addIncorrect(): CharacterStats = copy(incorrectCount = incorrectCount + 1)
    
    /**
     * Create a new CharacterStats with updated weight
     */
    fun updateWeight(newWeight: Double): CharacterStats = copy(weight = newWeight)
    
    /**
     * Convert to JSON for persistence
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("character", character.toString())
            put("correctCount", correctCount)
            put("incorrectCount", incorrectCount)
            put("weight", weight)
        }
    }
    
    companion object {
        /**
         * Create CharacterStats from JSON
         */
        fun fromJson(json: JSONObject): CharacterStats {
            return CharacterStats(
                character = json.getString("character").first(),
                correctCount = json.getInt("correctCount"),
                incorrectCount = json.getInt("incorrectCount"),
                weight = json.getDouble("weight")
            )
        }
    }
} 