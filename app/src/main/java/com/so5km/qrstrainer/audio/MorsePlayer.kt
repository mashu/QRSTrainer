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
    
    suspend fun playSequence(
        sequence: String,
        settings: TrainingSettings
    ) {
        val symbols = morseEncoder.encodeSequence(sequence, settings)
        
        for (symbol in symbols) {
            if (!audioEngine.isPlaying.value) break
            
            when (symbol.type) {
                MorseEncoder.SymbolType.DIT,
                MorseEncoder.SymbolType.DAH -> {
                    playTone(settings.frequency, symbol.durationMs, settings)
                }
                MorseEncoder.SymbolType.ELEMENT_SPACE,
                MorseEncoder.SymbolType.CHARACTER_SPACE,
                MorseEncoder.SymbolType.WORD_SPACE -> {
                    delay(symbol.durationMs.toLong())
                }
            }
        }
    }
    
    private fun playTone(
        frequency: Int,
        durationMs: Int,
        settings: TrainingSettings
    ) {
        // Generate tone
        var signal = signalGenerator.generateSineWave(
            frequency,
            durationMs,
            settings.volume
        )
        
        // Apply envelope
        signal = signalGenerator.applyEnvelope(
            signal,
            settings.riseTimeMs,
            settings.riseTimeMs
        )
        
        // Mix with noise if enabled
        if (settings.noiseEnabled && noiseGenerator != null) {
            val noise = noiseGenerator.generateNoise(
                durationMs,
                settings.noiseVolume,
                settings.noiseBandwidthHz
            )
            signal = signalGenerator.mixSignals(signal, noise)
        }
        
        // Convert to audio format and play
        val audioData = floatToShortArray(signal)
        audioEngine.play(audioData)
    }
    
    private fun floatToShortArray(floatArray: FloatArray): ShortArray {
        return ShortArray(floatArray.size) { i ->
            (floatArray[i] * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
