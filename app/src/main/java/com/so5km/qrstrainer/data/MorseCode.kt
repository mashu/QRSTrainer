package com.so5km.qrstrainer.data

/**
 * Morse code mappings and utilities
 */
object MorseCode {
    
    /**
     * Standard International Morse Code mappings
     */
    val MORSE_MAP = mapOf(
        // Letters
        'A' to ".-",
        'B' to "-...",
        'C' to "-.-.",
        'D' to "-..",
        'E' to ".",
        'F' to "..-.",
        'G' to "--.",
        'H' to "....",
        'I' to "..",
        'J' to ".---",
        'K' to "-.-",
        'L' to ".-..",
        'M' to "--",
        'N' to "-.",
        'O' to "---",
        'P' to ".--.",
        'Q' to "--.-",
        'R' to ".-.",
        'S' to "...",
        'T' to "-",
        'U' to "..-",
        'V' to "...-",
        'W' to ".--",
        'X' to "-..-",
        'Y' to "-.--",
        'Z' to "--..",
        
        // Numbers
        '0' to "-----",
        '1' to ".----",
        '2' to "..---",
        '3' to "...--",
        '4' to "....-",
        '5' to ".....",
        '6' to "-....",
        '7' to "--...",
        '8' to "---..",
        '9' to "----.",
        
        // Punctuation
        '.' to ".-.-.-",
        ',' to "--..--",
        '?' to "..--..",
        '\'' to ".----.",
        '!' to "-.-.--",
        '/' to "-..-.",
        '(' to "-.--.",
        ')' to "-.--.-",
        '&' to ".-...",
        ':' to "---...",
        ';' to "-.-.-.",
        '=' to "-...-",
        '+' to ".-.-.",
        '-' to "-....-",
        '_' to "..--.-",
        '"' to ".-..-.",
        '$' to "...-..-",
        '@' to ".--.-."
    )
    
    /**
     * Koch method character order - optimized for learning
     */
    val KOCH_ORDER = listOf(
        'K', 'M', 'U', 'R', 'E', 'S', 'N', 'A', 'P', 'T',
        'L', 'W', 'I', 'J', 'Z', 'F', 'O', 'Y', 'V', 'K',
        'H', 'G', 'D', 'B', 'X', 'C', 'Q', '5', '4', '3',
        '2', '1', '6', '7', '8', '9', '0', '/', '=', '?',
        '.', ',', ';', ':', '!', '(', ')', '"', '-', '_',
        '$', '@', '&'
    )
    
    /**
     * Prosigns (procedural signals) - separate from character map
     */
    val PROSIGNS = mapOf(
        "AR" to ".-.-.",  // End of message
        "AS" to ".-...",  // Wait
        "BT" to "-...-",  // Break/pause
        "CT" to "-.-.-",  // Start copying
        "SK" to "...-.-", // End of work
        "SN" to "...-.",  // Understood
        "SOS" to "...---..." // SOS emergency
    )
    
    /**
     * Reverse mapping for decoding
     */
    val REVERSE_MORSE_MAP = MORSE_MAP.entries.associate { (k, v) -> v to k }
    
    /**
     * Check if a character has a morse code representation
     */
    fun hasMapping(char: Char): Boolean = MORSE_MAP.containsKey(char.uppercaseChar())
    
    /**
     * Get morse pattern for a character
     */
    fun getMorsePattern(char: Char): String? = MORSE_MAP[char.uppercaseChar()]
    
    /**
     * Get character from morse pattern
     */
    fun getCharFromPattern(pattern: String): Char? = REVERSE_MORSE_MAP[pattern]
    
    /**
     * Get prosign from morse pattern
     */
    fun getProsignFromPattern(pattern: String): String? = 
        PROSIGNS.entries.find { it.value == pattern }?.key
    
    /**
     * Generate a random sequence of characters for training
     */
    fun generateRandomSequence(length: Int, includeNumbers: Boolean = true, includePunctuation: Boolean = false): String {
        val availableChars = mutableListOf<Char>()
        
        // Add letters
        availableChars.addAll('A'..'Z')
        
        // Add numbers if requested
        if (includeNumbers) {
            availableChars.addAll('0'..'9')
        }
        
        // Add some punctuation if requested
        if (includePunctuation) {
            availableChars.addAll(listOf('.', ',', '?', '/', '='))
        }
        
        return (1..length).map { availableChars.random() }.joinToString("")
    }
    
    /**
     * Validate that all characters in a string can be encoded
     */
    fun canEncode(text: String): Boolean {
        return text.uppercase().all { it == ' ' || hasMapping(it) }
    }
} 