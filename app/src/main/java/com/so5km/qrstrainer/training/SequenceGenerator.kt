package com.so5km.qrstrainer.training

import com.so5km.qrstrainer.data.MorseCode
import com.so5km.qrstrainer.data.ProgressTracker
import kotlin.random.Random

/**
 * Generates random character sequences for training
 */
class SequenceGenerator(private val progressTracker: ProgressTracker) {
    
    /**
     * Generate a random character sequence based on current level and settings
     */
    fun generateSequence(
        level: Int,
        minGroupSize: Int,
        maxGroupSize: Int
    ): String {
        val groupSize = Random.nextInt(minGroupSize, maxGroupSize + 1)
        val weightedCharacters = progressTracker.getWeightedCharacters(level)
        
        if (weightedCharacters.isEmpty()) {
            return "K" // Fallback to first Koch character
        }
        
        return buildString {
            repeat(groupSize) {
                val selectedChar = selectWeightedCharacter(weightedCharacters)
                append(selectedChar)
            }
        }
    }
    
    /**
     * Generate a single character based on weights
     */
    fun generateSingleCharacter(level: Int): Char {
        val weightedCharacters = progressTracker.getWeightedCharacters(level)
        
        if (weightedCharacters.isEmpty()) {
            return 'K' // Fallback to first Koch character
        }
        
        return selectWeightedCharacter(weightedCharacters)
    }
    
    /**
     * Select a character using weighted random selection
     */
    private fun selectWeightedCharacter(weightedCharacters: Array<Pair<Char, Double>>): Char {
        val totalWeight = weightedCharacters.sumOf { it.second }
        val randomValue = Random.nextDouble() * totalWeight
        
        var currentWeight = 0.0
        for ((char, weight) in weightedCharacters) {
            currentWeight += weight
            if (randomValue <= currentWeight) {
                return char
            }
        }
        
        // Fallback (should not happen with correct weights)
        return weightedCharacters.first().first
    }
    
    /**
     * Generate a test sequence with specific character distribution
     * Useful for testing specific character combinations
     */
    fun generateTestSequence(
        characters: Array<Char>,
        groupSize: Int,
        characterWeights: Map<Char, Double>? = null
    ): String {
        if (characters.isEmpty()) return ""
        
        return buildString {
            repeat(groupSize) {
                val char = if (characterWeights != null) {
                    val weightedChars = characters.map { char ->
                        char to (characterWeights[char] ?: 1.0)
                    }.toTypedArray()
                    selectWeightedCharacter(weightedChars)
                } else {
                    characters.random()
                }
                append(char)
            }
        }
    }
    
    /**
     * Get statistics about character distribution for current level
     */
    fun getCharacterDistribution(level: Int): Map<Char, Double> {
        val weightedCharacters = progressTracker.getWeightedCharacters(level)
        val totalWeight = weightedCharacters.sumOf { it.second }
        
        return weightedCharacters.associate { (char, weight) ->
            char to (weight / totalWeight * 100.0)
        }
    }
} 