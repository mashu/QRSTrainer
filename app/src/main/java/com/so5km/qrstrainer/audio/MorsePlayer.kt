package com.so5km.qrstrainer.audio

import com.so5km.qrstrainer.data.TrainingSettings
import kotlinx.coroutines.delay

/**
 * High-level Morse code player that coordinates audio generation and playback
 */
class MorsePlayer(
    private val audioEngine: AudioEngine,
    private val signalGenerator: SignalGenerator,
    private val morseEncoder: MorseEncoder,
    private val noiseGenerator: NoiseGenerator? = null
) {
    
    private var isSequencePlaying = false
    
    suspend fun playSequence(
        sequence: String,
        settings: TrainingSettings
    ) {
        // Prevent overlapping sequences
        if (isSequencePlaying) {
            android.util.Log.w("MorsePlayer", "Sequence already playing, ignoring new request")
            return
        }
        
        android.util.Log.d("MorsePlayer", "Playing sequence: $sequence")
        val symbols = morseEncoder.encodeSequence(sequence, settings)
        android.util.Log.d("MorsePlayer", "Encoded ${symbols.size} symbols")
        
        isSequencePlaying = true
        
        for ((index, symbol) in symbols.withIndex()) {
            // Allow cancellation mid-sequence
            if (!isSequencePlaying) {
                android.util.Log.d("MorsePlayer", "Sequence cancelled at symbol $index")
                break
            }
            
            android.util.Log.d("MorsePlayer", "Processing symbol $index: ${symbol.type}")
            
            when (symbol.type) {
                MorseEncoder.SymbolType.DIT,
                MorseEncoder.SymbolType.DAH -> {
                    android.util.Log.d("MorsePlayer", "Playing ${symbol.type} for ${symbol.durationMs}ms")
                    playTone(settings.frequency, symbol.durationMs, settings)
                }
                MorseEncoder.SymbolType.ELEMENT_SPACE,
                MorseEncoder.SymbolType.CHARACTER_SPACE,
                MorseEncoder.SymbolType.WORD_SPACE -> {
                    android.util.Log.d("MorsePlayer", "Playing silence for ${symbol.durationMs}ms")
                    delay(symbol.durationMs.toLong())
                }
            }
        }
        
        isSequencePlaying = false
        android.util.Log.d("MorsePlayer", "Finished playing sequence")
    }
    
    fun stopSequence() {
        android.util.Log.d("MorsePlayer", "Stopping sequence playback")
        isSequencePlaying = false
        audioEngine.stop()
    }
    
    private suspend fun playTone(
        frequency: Int,
        durationMs: Int,
        settings: TrainingSettings
    ) {
        android.util.Log.d("MorsePlayer", "playTone: freq=$frequency, duration=$durationMs, volume=${settings.volume}")
        
        // Generate tone
        var signal = signalGenerator.generateSineWave(
            frequency,
            durationMs,
            settings.volume
        )
        android.util.Log.d("MorsePlayer", "Generated signal with ${signal.size} samples")
        
        // Apply envelope
        signal = signalGenerator.applyEnvelope(
            signal,
            settings.riseTimeMs,
            settings.riseTimeMs
        )
        android.util.Log.d("MorsePlayer", "Applied envelope")
        
        // Convert to audio format and play (no noise mixing - noise runs separately)
        val audioData = floatToShortArray(signal)
        android.util.Log.d("MorsePlayer", "Converted to ${audioData.size} audio samples")
        
        // Play audio synchronously
        audioEngine.play(audioData)
        android.util.Log.d("MorsePlayer", "Called audioEngine.play()")
        
        // Add a small delay to ensure timing is correct
        delay(10)
    }
    
    private fun floatToShortArray(floatArray: FloatArray): ShortArray {
        return ShortArray(floatArray.size) { i ->
            (floatArray[i] * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }
}
