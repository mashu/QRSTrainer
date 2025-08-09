package com.so5km.qrstrainer.training

import com.so5km.qrstrainer.data.ProgressTracker
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.data.MorseCode
import kotlin.random.Random

/**
 * Generates training sequences based on user progress and level
 */
class SequenceGenerator(private val progressTracker: ProgressTracker) {
    
    companion object {
        /**
         * Get available characters based on settings and level
         * This now uses the corrected progressive Koch method
         */
        fun getAvailableCharacters(settings: TrainingSettings, progressTracker: ProgressTracker): List<Char> {
            progressTracker.updateSettings(settings)
            return progressTracker.getCharactersForLevel(settings.currentLevel)
        }
    }
    
    /**
     * Determine the number of groups for a sequence based on settings
     * Uses min/max range if available, falls back to fixed sequenceLength for backward compatibility
     */
    private fun determineSequenceLength(settings: TrainingSettings): Int {
        return if (settings.minSequenceLength != 0 && settings.maxSequenceLength != 0) {
            // Use variable sequence length
            if (settings.minSequenceLength == settings.maxSequenceLength) {
                settings.minSequenceLength
            } else {
                Random.nextInt(settings.minSequenceLength, settings.maxSequenceLength + 1)
            }
        } else {
            // Fall back to fixed sequence length for backward compatibility
            settings.sequenceLength
        }
    }

    /**
     * Generate a training sequence with groups based on settings
     * @param settings Training settings including group sizes and sequence length
     * @return A string with groups separated by spaces
     */
    fun generateGroupSequence(settings: TrainingSettings): String {
        val availableChars = getAvailableCharacters(settings, progressTracker)
        val weightsBasedOnProgress = getCharacterWeights(availableChars)
        
        val groups = mutableListOf<String>()
        
        // Always use the current settings values (trainer or listen mode should prepare settings appropriately)
        val sequenceLength = determineSequenceLength(settings)
        
        android.util.Log.d("SequenceGenerator", "generateGroupSequence - minGroupSize: ${settings.minGroupSize}, maxGroupSize: ${settings.maxGroupSize}")
        android.util.Log.d("SequenceGenerator", "generateGroupSequence - sequenceLength: $sequenceLength (range: ${settings.minSequenceLength}-${settings.maxSequenceLength})")
        
        // Generate the determined number of groups
        repeat(sequenceLength) {
            val groupSize = if (settings.minGroupSize == settings.maxGroupSize) {
                settings.minGroupSize
            } else {
                Random.nextInt(settings.minGroupSize, settings.maxGroupSize + 1)
            }
            
            android.util.Log.d("SequenceGenerator", "Generated group size: $groupSize (should be between ${settings.minGroupSize} and ${settings.maxGroupSize})")
            
            val group = (1..groupSize).map {
                selectWeightedRandomCharacter(availableChars, weightsBasedOnProgress)
            }.joinToString("")
            
            android.util.Log.d("SequenceGenerator", "Generated group: '$group' (length: ${group.length})")
            groups.add(group)
        }
        
        val finalSequence = groups.joinToString(" ")
        android.util.Log.d("SequenceGenerator", "Final sequence: '$finalSequence' with ${groups.size} groups")
        android.util.Log.d("SequenceGenerator", "Group breakdown: ${groups.joinToString(", ") { "'$it' (${it.length})" }}")
        
        return finalSequence
    }
    
    /**
     * Get available characters based on settings (instance method for backward compatibility)
     */
    private fun getAvailableCharacters(settings: TrainingSettings): List<Char> {
        return getAvailableCharacters(settings, progressTracker)
    }
    
    /**
     * Generate a training sequence for the given length and level (legacy method)
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
    fun generateFocusedSequence(length: Int, focusChars: List<Char>, level: Int = 1): String {
        if (focusChars.isEmpty()) {
            return generateSequence(length, level)
        }
        
        val weights = getCharacterWeights(focusChars)
        return (1..length).map {
            selectWeightedRandomCharacter(focusChars, weights)
        }.joinToString("")
    }
    
    /**
     * Generate a sequence with more difficult characters based on user performance
     */
    fun generateAdaptiveSequence(settings: TrainingSettings): String {
        android.util.Log.d("SequenceGenerator", "generateAdaptiveSequence called - minGroupSize: ${settings.minGroupSize}, maxGroupSize: ${settings.maxGroupSize}")
        
        val availableChars = getAvailableCharacters(settings)
        val stats = progressTracker.getAllCharacterStats()
        
        // Prefer characters with lower accuracy
        val difficultChars = availableChars.filter { char ->
            val charStats = stats[char]
            charStats == null || charStats.accuracy < 0.8f
        }
        
        val charsToUse = if (difficultChars.isEmpty()) availableChars else difficultChars
        val weightsBasedOnProgress = getCharacterWeights(charsToUse)
        
        val groups = mutableListOf<String>()
        
        // Use variable sequence length
        val sequenceLength = determineSequenceLength(settings)
        android.util.Log.d("SequenceGenerator", "generateAdaptiveSequence - sequenceLength: $sequenceLength (range: ${settings.minSequenceLength}-${settings.maxSequenceLength})")
        
        // Generate adaptive groups
        repeat(sequenceLength) {
            val groupSize = if (settings.adaptiveGroupSize) {
                // Smaller groups for difficult characters
                val avgDifficulty = charsToUse.mapNotNull { char ->
                    stats[char]?.accuracy
                }.average()
                
                val adaptiveSize = when {
                    avgDifficulty < 0.5 -> settings.minGroupSize
                    avgDifficulty < 0.7 -> (settings.minGroupSize + settings.maxGroupSize) / 2
                    else -> settings.maxGroupSize
                }
                
                android.util.Log.d("SequenceGenerator", "Adaptive group size: $adaptiveSize (avgDifficulty: $avgDifficulty)")
                adaptiveSize
            } else {
                val randomSize = Random.nextInt(settings.minGroupSize, settings.maxGroupSize + 1)
                android.util.Log.d("SequenceGenerator", "Random group size: $randomSize")
                randomSize
            }
            
            val group = (1..groupSize).map {
                selectWeightedRandomCharacter(charsToUse, weightsBasedOnProgress)
            }.joinToString("")
            
            groups.add(group)
        }
        
        return groups.joinToString(" ")
    }
    
    /**
     * Generate a sequence for review of previously learned characters
     */
    fun generateReviewSequence(settings: TrainingSettings): String {
        val currentLevel = settings.currentLevel
        val allLearnedChars = mutableListOf<Char>()
        
        // Include characters from all levels up to current
        for (level in 1..currentLevel) {
            allLearnedChars.addAll(progressTracker.getCharactersForLevel(level))
        }
        
        // Add enabled character sets
        if (settings.useNumbers) allLearnedChars.addAll('0'..'9')
        if (settings.usePunctuation) allLearnedChars.addAll(listOf('.', ',', '?', '/', '=', '+', '-'))
        if (settings.useProsigns) allLearnedChars.addAll(listOf('<', '>', '@'))
        if (settings.customCharacterSet.isNotEmpty()) allLearnedChars.addAll(settings.customCharacterSet.toList())
        
        val distinctChars = allLearnedChars.distinct()
        val weights = getCharacterWeights(distinctChars)
        
        val groups = mutableListOf<String>()
        
        // Use variable sequence length
        val sequenceLength = determineSequenceLength(settings)
        android.util.Log.d("SequenceGenerator", "generateReviewSequence - sequenceLength: $sequenceLength (range: ${settings.minSequenceLength}-${settings.maxSequenceLength})")
        
        repeat(sequenceLength) {
            val groupSize = Random.nextInt(settings.minGroupSize, settings.maxGroupSize + 1)
            val group = (1..groupSize).map {
                selectWeightedRandomCharacter(distinctChars, weights)
            }.joinToString("")
            
            groups.add(group)
        }
        
        return groups.joinToString(" ")
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
    fun generatePatternSequence(settings: TrainingSettings, pattern: SequencePattern): String {
        val availableChars = getAvailableCharacters(settings)
        
        return when (pattern) {
            SequencePattern.ALTERNATING -> generateAlternatingSequence(settings, availableChars)
            SequencePattern.SIMILAR_SOUNDING -> generateSimilarSoundingSequence(settings)
            SequencePattern.RANDOM -> generateGroupSequence(settings)
        }
    }
    
    private fun generateAlternatingSequence(settings: TrainingSettings, chars: List<Char>): String {
        if (chars.size < 2) return generateGroupSequence(settings)
        
        val char1 = chars.random()
        val char2 = chars.filter { it != char1 }.random()
        
        val groups = mutableListOf<String>()
        
        // Use variable sequence length
        val sequenceLength = determineSequenceLength(settings)
        
        repeat(sequenceLength) { groupIndex ->
            val groupSize = Random.nextInt(settings.minGroupSize, settings.maxGroupSize + 1)
            val group = (1..groupSize).map { charIndex ->
                if ((groupIndex + charIndex) % 2 == 0) char1 else char2
            }.joinToString("")
            
            groups.add(group)
        }
        
        return groups.joinToString(" ")
    }
    
    private fun generateSimilarSoundingSequence(settings: TrainingSettings): String {
        // Groups of similar-sounding morse patterns
        val similarGroups = listOf(
            listOf('E', 'I', 'S', 'H'), // Short sounds
            listOf('T', 'M', 'O'), // Long sounds
            listOf('A', 'N', 'D', 'K'), // Mixed patterns
            listOf('U', 'F', 'R', 'L') // Similar rhythm
        )
        
        val selectedGroup = similarGroups.random()
        val weights = getCharacterWeights(selectedGroup)
        
        val groups = mutableListOf<String>()
        
        // Use variable sequence length
        val sequenceLength = determineSequenceLength(settings)
        
        repeat(sequenceLength) {
            val groupSize = Random.nextInt(settings.minGroupSize, settings.maxGroupSize + 1)
            val group = (1..groupSize).map {
                selectWeightedRandomCharacter(selectedGroup, weights)
            }.joinToString("")
            
            groups.add(group)
        }
        
        return groups.joinToString(" ")
    }
    
    enum class SequencePattern {
        ALTERNATING,
        SIMILAR_SOUNDING,
        RANDOM
    }
} 