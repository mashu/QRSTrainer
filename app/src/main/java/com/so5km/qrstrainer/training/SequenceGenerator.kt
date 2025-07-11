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
        maxGroupSize: Int,
        lettersOnly: Boolean = false
    ): String {
        val groupSize = Random.nextInt(minGroupSize, maxGroupSize + 1)
        val weightedCharacters = progressTracker.getWeightedCharacters(level, lettersOnly)
        
        if (weightedCharacters.isEmpty()) {
            android.util.Log.e("SequenceGenerator", "No characters available for level $level, using fallback")
            return "K" // Fallback to first Koch character
        }
        
        val sequence = buildString {
            repeat(groupSize) {
                val selectedChar = selectWeightedCharacter(weightedCharacters)
                append(selectedChar)
            }
        }
        
        android.util.Log.d("SequenceGenerator", "Generated sequence '$sequence' for level $level (group size: $groupSize)")
        return sequence
    }
    
    /**
     * Generate a random sequence with specific parameters
     * This is an alternative method that takes parameters in a different order
     */
    fun generateRandomSequence(
        minGroupSize: Int,
        maxGroupSize: Int,
        lettersOnly: Boolean = false,
        level: Int
    ): String {
        return generateSequence(level, minGroupSize, maxGroupSize, lettersOnly)
    }
    
    /**
     * Generate a single character based on weights
     */
    fun generateSingleCharacter(level: Int, lettersOnly: Boolean = false): Char {
        val weightedCharacters = progressTracker.getWeightedCharacters(level, lettersOnly)
        
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