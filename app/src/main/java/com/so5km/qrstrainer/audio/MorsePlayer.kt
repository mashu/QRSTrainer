package com.so5km.qrstrainer.audio

import com.so5km.qrstrainer.data.TrainingSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

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
        
        if (symbols.isEmpty()) {
            android.util.Log.w("MorsePlayer", "No symbols to play")
            return
        }
        
        isSequencePlaying = true
        
        try {
            // Start streaming mode for smooth playback
            audioEngine.startStreaming()
            
            // Generate and queue all audio data
            generateAndQueueSequence(symbols, settings)
            
            // Calculate total duration and wait for completion
            val totalDurationMs = symbols.sumOf { it.durationMs }
            android.util.Log.d("MorsePlayer", "Total sequence duration: ${totalDurationMs}ms")
            
            // Wait for sequence to complete with small chunks to allow cancellation
            var remainingMs = totalDurationMs
            val checkIntervalMs = 100L
            
            while (remainingMs > 0 && isSequencePlaying) {
                val sleepTime = minOf(checkIntervalMs, remainingMs.toLong())
                delay(sleepTime)
                remainingMs -= sleepTime.toInt()
                
                // Allow other coroutines to run
                yield()
            }
            
        } finally {
            // Always stop streaming when done
            audioEngine.stopStreaming()
            isSequencePlaying = false
            android.util.Log.d("MorsePlayer", "Finished playing sequence")
        }
    }
    
    private fun generateAndQueueSequence(
        symbols: List<MorseEncoder.MorseSymbol>,
        settings: TrainingSettings
    ) {
        android.util.Log.d("MorsePlayer", "Generating continuous audio stream for ${symbols.size} symbols")
        
        for ((index, symbol) in symbols.withIndex()) {
            // Check if sequence was cancelled
            if (!isSequencePlaying) {
                android.util.Log.d("MorsePlayer", "Sequence cancelled at symbol $index")
                break
            }
            
            android.util.Log.d("MorsePlayer", "Generating symbol $index: ${symbol.type} (${symbol.durationMs}ms)")
            
            when (symbol.type) {
                MorseEncoder.SymbolType.DIT,
                MorseEncoder.SymbolType.DAH -> {
                    // Generate tone
                    val audioData = generateTone(settings.frequency, symbol.durationMs, settings)
                    audioEngine.queueAudio(audioData)
                }
                MorseEncoder.SymbolType.ELEMENT_SPACE,
                MorseEncoder.SymbolType.CHARACTER_SPACE,
                MorseEncoder.SymbolType.WORD_SPACE -> {
                    // Generate silence
                    val audioData = generateSilence(symbol.durationMs)
                    audioEngine.queueAudio(audioData)
                }
            }
        }
        
        android.util.Log.d("MorsePlayer", "Finished generating audio stream")
    }
    
    private fun generateTone(
        frequency: Int,
        durationMs: Int,
        settings: TrainingSettings
    ): ShortArray {
        android.util.Log.d("MorsePlayer", "Generating tone: freq=$frequency, duration=$durationMs, volume=${settings.volume}")
        
        // Generate sine wave
        var signal = signalGenerator.generateSineWave(
            frequency,
            durationMs,
            settings.volume
        )
        android.util.Log.d("MorsePlayer", "Generated signal with ${signal.size} samples")
        
        // Apply envelope to prevent clicks
        signal = signalGenerator.applyEnvelope(
            signal,
            settings.riseTimeMs,
            settings.riseTimeMs
        )
        android.util.Log.d("MorsePlayer", "Applied envelope with ${settings.riseTimeMs}ms rise/fall time")
        
        // Convert to audio format
        val audioData = floatToShortArray(signal)
        android.util.Log.d("MorsePlayer", "Converted to ${audioData.size} audio samples")
        
        return audioData
    }
    
    private fun generateSilence(durationMs: Int): ShortArray {
        val numSamples = (AudioEngine.SAMPLE_RATE * durationMs / 1000.0).toInt()
        val audioData = ShortArray(numSamples) { 0 }
        android.util.Log.d("MorsePlayer", "Generated ${audioData.size} silence samples for ${durationMs}ms")
        return audioData
    }
    
    fun stopSequence() {
        android.util.Log.d("MorsePlayer", "Stopping sequence playback")
        isSequencePlaying = false
        // Note: AudioEngine streaming will be stopped by the playSequence method
    }
    
    private fun floatToShortArray(floatArray: FloatArray): ShortArray {
        return ShortArray(floatArray.size) { i ->
            (floatArray[i] * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }
}

