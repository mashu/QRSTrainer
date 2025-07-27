package com.so5km.qrstrainer.audio

import com.so5km.qrstrainer.data.MorseCode
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.utils.ObjectPool
import com.so5km.qrstrainer.utils.Poolable

/**
 * Encodes text to Morse code timing sequences
 */
class MorseEncoder {
    
    class MorseSymbol(
        var type: SymbolType = SymbolType.DIT,
        var durationMs: Int = 0
    ) : Poolable {
        override fun reset() {
            type = SymbolType.DIT
            durationMs = 0
        }
        
        fun set(type: SymbolType, durationMs: Int): MorseSymbol {
            this.type = type
            this.durationMs = durationMs
            return this
        }
    }
    
    private val symbolPool = ObjectPool(
        factory = { MorseSymbol() },
        reset = { it.reset() },
        maxSize = 100
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
            when (char) {
                ' ' -> {
                    // Handle space as word separator
                    if (index < sequence.length - 1) {
                        symbols.add(symbolPool.acquire().set(SymbolType.WORD_SPACE, timings.wordSpaceMs))
                    }
                }
                else -> {
                    val morsePattern = MorseCode.MORSE_MAP[char] 
                    if (morsePattern != null) {
                        // Add morse pattern for character
                        morsePattern.forEachIndexed { patternIndex, element ->
                            when (element) {
                                '.' -> symbols.add(symbolPool.acquire().set(SymbolType.DIT, timings.ditMs))
                                '-' -> symbols.add(symbolPool.acquire().set(SymbolType.DAH, timings.dahMs))
                            }
                            
                            // Add element space except after last element
                            if (patternIndex < morsePattern.length - 1) {
                                symbols.add(symbolPool.acquire().set(SymbolType.ELEMENT_SPACE, timings.elementSpaceMs))
                            }
                        }
                        
                        // Add character space except after last character and before spaces
                        if (index < sequence.length - 1 && sequence[index + 1] != ' ') {
                            symbols.add(symbolPool.acquire().set(SymbolType.CHARACTER_SPACE, timings.charSpaceMs))
                        }
                    }
                    // If character not in MORSE_MAP, skip it (don't add anything)
                }
            }
        }
        
        return symbols
    }
    
    /**
     * Release symbols back to pool when no longer needed
     */
    fun releaseSymbols(symbols: List<MorseSymbol>) {
        symbols.forEach { symbol ->
            symbolPool.release(symbol)
        }
    }
    
    /**
     * Clear the symbol pool to free memory
     */
    fun clearPool() {
        symbolPool.clear()
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

