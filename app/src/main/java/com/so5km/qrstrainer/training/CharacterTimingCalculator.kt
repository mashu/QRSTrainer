package com.so5km.qrstrainer.training

import com.so5km.qrstrainer.data.MorseCode
import com.so5km.qrstrainer.data.TrainingSettings

/**
 * Calculates the timing of each character in a Morse code sequence
 * Used to track accurate per-character response times
 */
class CharacterTimingCalculator {
    
    /**
     * Represents the timing information for a character in a sequence
     */
    data class CharacterTiming(
        val char: Char,
        val startTimeMs: Int,
        val durationMs: Int
    ) {
        val endTimeMs: Int get() = startTimeMs + durationMs
    }
    
    /**
     * Calculate the timing for each character in a sequence
     * @param sequence The Morse code sequence
     * @param settings The training settings that affect timing
     * @return A list of character timing information
     */
    fun calculateCharacterTimings(sequence: String, settings: TrainingSettings): List<CharacterTiming> {
        val dotDuration = calculateDotDuration(settings.speedWpm)
        val dashDuration = dotDuration * 3
        val symbolSpacing = dotDuration
        val charSpacing = calculateCharacterSpacing(settings)
        val wordSpacing = calculateWordSpacing(settings)
        
        val timings = mutableListOf<CharacterTiming>()
        var currentTimeMs = 0
        
        for (i in sequence.indices) {
            val char = sequence[i]
            val pattern = MorseCode.getPattern(char)
            
            if (pattern != null) {
                val charStartTimeMs = currentTimeMs
                var charDurationMs = 0
                
                // Calculate pattern duration
                for (symbol in pattern) {
                    when (symbol) {
                        '.' -> charDurationMs += dotDuration
                        '-' -> charDurationMs += dashDuration
                    }
                }
                
                // Add symbol spacing within pattern
                if (pattern.length > 1) {
                    charDurationMs += symbolSpacing * (pattern.length - 1)
                }
                
                // Add this character to timings
                timings.add(CharacterTiming(char, charStartTimeMs, charDurationMs))
                
                // Update current time for next character
                currentTimeMs += charDurationMs
                
                // Add character spacing (except after last character)
                if (i < sequence.length - 1) {
                    currentTimeMs += if (char == ' ') wordSpacing else charSpacing
                }
            }
        }
        
        return timings
    }
    
    /**
     * Calculate dot duration in milliseconds for given WPM
     * Standard: PARIS = 50 dot units, so dot = 1200ms / WPM
     */
    private fun calculateDotDuration(wpm: Int): Int {
        return (1200.0 / wpm).toInt()
    }
    
    /**
     * Calculate character spacing with Farnsworth timing
     */
    private fun calculateCharacterSpacing(settings: TrainingSettings): Int {
        val dotDuration = calculateDotDuration(settings.speedWpm)
        val baseDuration = dotDuration * 3 // Normal character spacing is 3 dots
        
        return if (settings.farnsworthWpm > 0 && settings.farnsworthWpm < settings.speedWpm) {
            // Farnsworth timing: slow down spacing but keep character speed
            val farnsworthRatio = settings.farnsworthWpm.toDouble() / settings.speedWpm
            (baseDuration / farnsworthRatio).toInt() + settings.wordSpacingMs
        } else {
            baseDuration + settings.wordSpacingMs
        }
    }
    
    /**
     * Calculate word spacing with Farnsworth timing
     */
    private fun calculateWordSpacing(settings: TrainingSettings): Int {
        val dotDuration = calculateDotDuration(settings.speedWpm)
        val baseDuration = dotDuration * 7 // Normal word spacing is 7 dots
        
        return if (settings.farnsworthWpm > 0 && settings.farnsworthWpm < settings.speedWpm) {
            // Farnsworth timing: slow down spacing but keep character speed
            val farnsworthRatio = settings.farnsworthWpm.toDouble() / settings.speedWpm
            (baseDuration / farnsworthRatio).toInt() + settings.wordSpacingMs
        } else {
            baseDuration + settings.wordSpacingMs
        }
    }
    
    /**
     * Find which character is being played at a specific time
     * @param timings The list of character timings
     * @param timeMs The current time in milliseconds
     * @return The index of the character being played, or -1 if no character is playing
     */
    fun findCharacterAtTime(timings: List<CharacterTiming>, timeMs: Int): Int {
        for (i in timings.indices) {
            val timing = timings[i]
            if (timeMs >= timing.startTimeMs && timeMs < timing.endTimeMs) {
                return i
            }
        }
        return -1
    }
    
    /**
     * Calculate the response time for a specific character
     * @param timings The list of character timings
     * @param charIndex The index of the character in the sequence
     * @param responseTimeMs The total response time from sequence start
     * @return The response time for the specific character
     */
    fun calculateCharacterResponseTime(
        timings: List<CharacterTiming>, 
        charIndex: Int, 
        responseTimeMs: Long
    ): Long {
        if (charIndex < 0 || charIndex >= timings.size) return 0
        
        val timing = timings[charIndex]
        return responseTimeMs - timing.startTimeMs
    }
} 