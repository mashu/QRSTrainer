package com.so5km.qrstrainer.audio

import com.so5km.qrstrainer.data.MorseCode
import com.so5km.qrstrainer.data.TrainingSettings

/**
 * Encodes text to Morse code timing sequences
 */
class MorseEncoder {
    
    data class MorseSymbol(
        val type: SymbolType,
        val durationMs: Int
    )
    
    enum class SymbolType {
        DIT, DAH, ELEMENT_SPACE, CHARACTER_SPACE, WORD_SPACE
    }
    
    fun encodeSequence(
        sequence: String,
        settings: TrainingSettings
    ): List<MorseSymbol> {
        val symbols = mutableListOf<MorseSymbol>()
        val timings = calculateTimings(settings)
        
        sequence.forEachIndexed { index, char ->
            val morsePattern = MorseCode.MORSE_MAP[char] ?: return@forEachIndexed
            
            // Add morse pattern for character
            morsePattern.forEachIndexed { patternIndex, element ->
                when (element) {
                    '.' -> symbols.add(MorseSymbol(SymbolType.DIT, timings.ditMs))
                    '-' -> symbols.add(MorseSymbol(SymbolType.DAH, timings.dahMs))
                }
                
                // Add element space except after last element
                if (patternIndex < morsePattern.length - 1) {
                    symbols.add(MorseSymbol(SymbolType.ELEMENT_SPACE, timings.elementSpaceMs))
                }
            }
            
            // Add character or word space
            if (index < sequence.length - 1) {
                symbols.add(
                    if (char == ' ') {
                        MorseSymbol(SymbolType.WORD_SPACE, timings.wordSpaceMs)
                    } else {
                        MorseSymbol(SymbolType.CHARACTER_SPACE, timings.charSpaceMs)
                    }
                )
            }
        }
        
        return symbols
    }
    
    data class MorseTimings(
        val ditMs: Int,
        val dahMs: Int,
        val elementSpaceMs: Int,
        val charSpaceMs: Int,
        val wordSpaceMs: Int
    )
    
    private fun calculateTimings(settings: TrainingSettings): MorseTimings {
        // Calculate base unit time for character speed
        val baseUnitMs = 1200.0 / settings.wpm
        
        // Calculate Farnsworth spacing
        val effectiveUnitMs = if (settings.effectiveWpm < settings.wpm) {
            // Farnsworth timing calculation
            val charTime = 50 * baseUnitMs  // Average character time
            val effectiveCharTime = 50 * (1200.0 / settings.effectiveWpm)
            val extraSpacing = (effectiveCharTime - charTime) / 9  // Distribute extra time
            baseUnitMs + extraSpacing
        } else {
            baseUnitMs
        }
        
        return MorseTimings(
            ditMs = baseUnitMs.toInt(),
            dahMs = (baseUnitMs * 3).toInt(),
            elementSpaceMs = baseUnitMs.toInt(),
            charSpaceMs = (effectiveUnitMs * 3).toInt(),
            wordSpaceMs = (effectiveUnitMs * 7).toInt()
        )
    }
}

