package com.so5km.qrstrainer.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Data class for progress export/import
 */
data class ProgressData(
    val currentLevel: Int,
    val characterStats: Map<Char, CharacterStatData>,
    val totalSessions: Int,
    val totalTime: Long,
    val bestStreak: Int
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("currentLevel", currentLevel)
        
        val statsArray = JSONArray()
        characterStats.forEach { (char, stat) ->
            val statObj = JSONObject()
            statObj.put("character", char.toString())
            statObj.put("attempts", stat.attempts)
            statObj.put("correct", stat.correct)
            statObj.put("averageResponseTime", stat.averageResponseTime)
            statsArray.put(statObj)
        }
        json.put("characterStats", statsArray)
        
        json.put("totalSessions", totalSessions)
        json.put("totalTime", totalTime)
        json.put("bestStreak", bestStreak)
        
        return json.toString(2)
    }
    
    companion object {
        fun fromJson(json: String): ProgressData {
            val obj = JSONObject(json)
            
            val stats = mutableMapOf<Char, CharacterStatData>()
            val statsArray = obj.getJSONArray("characterStats")
            for (i in 0 until statsArray.length()) {
                val statObj = statsArray.getJSONObject(i)
                val char = statObj.getString("character")[0]
                stats[char] = CharacterStatData(
                    attempts = statObj.getInt("attempts"),
                    correct = statObj.getInt("correct"),
                    averageResponseTime = statObj.getLong("averageResponseTime")
                )
            }
            
            return ProgressData(
                currentLevel = obj.getInt("currentLevel"),
                characterStats = stats,
                totalSessions = obj.getInt("totalSessions"),
                totalTime = obj.getLong("totalTime"),
                bestStreak = obj.getInt("bestStreak")
            )
        }
    }
}

data class CharacterStatData(
    val attempts: Int,
    val correct: Int,
    val averageResponseTime: Long
)

