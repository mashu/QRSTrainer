package com.so5km.qrstrainer.training

import com.so5km.qrstrainer.data.ProgressTracker
import com.so5km.qrstrainer.data.MorseCode
import kotlin.random.Random

/**
 * Generates training sequences based on user progress and level
 */
class SequenceGenerator(private val progressTracker: ProgressTracker) {
    
    /**
     * Generate a training sequence for the given length and level
     */
    fun generateSequence(length: Int, level: Int): String {
        val availableChars = progressTracker.getCharactersForLevel(level)
        val weightsBasedOnProgress = getCharacterWeights(availableChars)
        
        return (1..length).map {
            selectWeightedRandomCharacter(availableChars, weightsBasedOnProgress)
        }.joinToString("")
    }
    
    /**
     * Generate a sequence with specific characters for focused training
     */
    fun generateFocusedSequence(length: Int, focusChars: List<Char>): String {
        if (focusChars.isEmpty()) {
            return generateSequence(length, progressTracker.getCurrentLevel())
        }
        
        val weights = getCharacterWeights(focusChars)
        return (1..length).map {
            selectWeightedRandomCharacter(focusChars, weights)
        }.joinToString("")
    }
    
    /**
     * Generate a sequence with more difficult characters based on user performance
     */
    fun generateAdaptiveSequence(length: Int): String {
        val level = progressTracker.getCurrentLevel()
        val availableChars = progressTracker.getCharactersForLevel(level)
        val stats = progressTracker.getAllCharacterStats()
        
        // Prefer characters with lower accuracy
        val difficultChars = availableChars.filter { char ->
            val charStats = stats[char]
            charStats == null || charStats.accuracy < 0.8f
        }
        
        val charsToUse = if (difficultChars.isEmpty()) availableChars else difficultChars
        val weights = getCharacterWeights(charsToUse)
        
        return (1..length).map {
            selectWeightedRandomCharacter(charsToUse, weights)
        }.joinToString("")
    }
    
    /**
     * Generate a sequence for review of previously learned characters
     */
    fun generateReviewSequence(length: Int): String {
        val currentLevel = progressTracker.getCurrentLevel()
        val allLearnedChars = mutableListOf<Char>()
        
        // Include characters from all levels up to current
        for (level in 1..currentLevel) {
            allLearnedChars.addAll(progressTracker.getCharactersForLevel(level))
        }
        
        val weights = getCharacterWeights(allLearnedChars)
        return (1..length).map {
            selectWeightedRandomCharacter(allLearnedChars, weights)
        }.joinToString("")
    }
    
    /**
     * Calculate weights for characters based on their performance
     * Characters with lower accuracy get higher weights (more likely to be selected)
     */
    private fun getCharacterWeights(chars: List<Char>): Map<Char, Float> {
        val stats = progressTracker.getAllCharacterStats()
        val weights = mutableMapOf<Char, Float>()
        
        chars.forEach { char ->
            val charStats = stats[char]
            weights[char] = when {
                charStats == null -> 3.0f // New characters get high weight
                charStats.accuracy < 0.5f -> 4.0f // Very poor accuracy
                charStats.accuracy < 0.7f -> 3.0f // Poor accuracy
                charStats.accuracy < 0.85f -> 2.0f // Below average
                charStats.accuracy < 0.95f -> 1.0f // Good accuracy
                else -> 0.5f // Excellent accuracy, less practice needed
            }
        }
        
        return weights
    }
    
    /**
     * Select a random character based on weights
     */
    private fun selectWeightedRandomCharacter(chars: List<Char>, weights: Map<Char, Float>): Char {
        if (chars.isEmpty()) return 'E' // Fallback
        
        val totalWeight = weights.values.sum()
        val randomValue = Random.nextFloat() * totalWeight
        
        var currentWeight = 0f
        for (char in chars) {
            currentWeight += weights[char] ?: 1.0f
            if (randomValue <= currentWeight) {
                return char
            }
        }
        
        return chars.random() // Fallback
    }
    
    /**
     * Generate a sequence with specific pattern (e.g., alternating characters)
     */
    fun generatePatternSequence(length: Int, pattern: SequencePattern): String {
        val level = progressTracker.getCurrentLevel()
        val availableChars = progressTracker.getCharactersForLevel(level)
        
        return when (pattern) {
            SequencePattern.ALTERNATING -> generateAlternatingSequence(length, availableChars)
            SequencePattern.SIMILAR_SOUNDING -> generateSimilarSoundingSequence(length)
            SequencePattern.RANDOM -> generateSequence(length, level)
        }
    }
    
    private fun generateAlternatingSequence(length: Int, chars: List<Char>): String {
        if (chars.size < 2) return generateSequence(length, progressTracker.getCurrentLevel())
        
        val char1 = chars[0]
        val char2 = chars[1]
        
        return (1..length).map { index ->
            if (index % 2 == 1) char1 else char2
        }.joinToString("")
    }
    
    private fun generateSimilarSoundingSequence(length: Int): String {
        // Groups of similar-sounding morse patterns
        val similarGroups = listOf(
            listOf('E', 'I', 'S', 'H'), // Short sounds
            listOf('T', 'M', 'O'), // Long sounds
            listOf('A', 'N', 'D', 'K'), // Mixed patterns
            listOf('U', 'F', 'R', 'L') // Similar rhythm
        )
        
        val selectedGroup = similarGroups.random()
        val weights = getCharacterWeights(selectedGroup)
        
        return (1..length).map {
            selectWeightedRandomCharacter(selectedGroup, weights)
        }.joinToString("")
    }
    
    enum class SequencePattern {
        ALTERNATING,
        SIMILAR_SOUNDING,
        RANDOM
    }
} 