package com.so5km.qrstrainer.data

/**
 * Morse code data and utilities for the Koch method training
 */
object MorseCode {
    
    // Koch method character sequence (standard order)
    val KOCH_SEQUENCE = arrayOf(
        'K', 'M', 'U', 'R', 'E', 'S', 'N', 'A', 'P', 'T', 'L', 'W', 'I', '.',
        'J', 'Z', '=', 'F', 'O', 'Y', ',', 'V', 'G', '5', '/', 'Q', '9', '2',
        'H', '3', '8', 'B', '?', '4', '7', 'C', '1', 'D', '6', '0', 'X'
    )
    
    // Morse code patterns for each character
    val MORSE_PATTERNS = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--",
        '4' to "....-", '5' to ".....", '6' to "-....", '7' to "--...",
        '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '/' to "-..-.",
        '=' to "-...-"
    )
    
    /**
     * Get characters available at a given Koch level (1-based)
     */
    fun getCharactersForLevel(level: Int): Array<Char> {
        val adjustedLevel = maxOf(1, minOf(level, KOCH_SEQUENCE.size))
        return KOCH_SEQUENCE.sliceArray(0 until adjustedLevel)
    }
    
    /**
     * Get the Morse pattern for a character
     */
    fun getPattern(char: Char): String? {
        return MORSE_PATTERNS[char.uppercaseChar()]
    }
    
    /**
     * Get the maximum Koch level
     */
    fun getMaxLevel(): Int = KOCH_SEQUENCE.size
    
    /**
     * Get the character at a specific position in the Koch sequence
     */
    fun getCharacterAtPosition(position: Int): Char? {
        return if (position in 0 until KOCH_SEQUENCE.size) {
            KOCH_SEQUENCE[position]
        } else null
    }
} 