
package com.so5km.qrstrainer.audio

import kotlin.math.PI
import kotlin.math.sin

/**
 * Generates audio signals (sine waves) with configurable parameters
 */
class SignalGenerator(private val sampleRate: Int = AudioEngine.SAMPLE_RATE) {
    
    fun generateSineWave(
        frequency: Int,
        durationMs: Int,
        amplitude: Float = 1.0f
    ): FloatArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val samples = FloatArray(numSamples)
        val angularFreq = 2.0 * PI * frequency / sampleRate
        
        for (i in 0 until numSamples) {
            samples[i] = (amplitude * sin(angularFreq * i)).toFloat()
        }
        
        return samples
    }
    
    fun applyEnvelope(
        signal: FloatArray,
        riseTimeMs: Double,
        fallTimeMs: Double = riseTimeMs
    ): FloatArray {
        val riseSamples = (sampleRate * riseTimeMs / 1000.0).toInt()
        val fallSamples = (sampleRate * fallTimeMs / 1000.0).toInt()
        val result = signal.copyOf()
        
        // Apply rise envelope
        for (i in 0 until riseSamples.coerceAtMost(result.size)) {
            val factor = i.toFloat() / riseSamples
            result[i] *= factor
        }
        
        // Apply fall envelope
        val startFall = result.size - fallSamples
        for (i in startFall until result.size) {
            val factor = (result.size - i).toFloat() / fallSamples
            result[i] *= factor
        }
        
        return result
    }
    
    fun mixSignals(vararg signals: FloatArray): FloatArray {
        val maxLength = signals.maxOf { it.size }
        val mixed = FloatArray(maxLength)
        
        for (i in 0 until maxLength) {
            var sum = 0f
            var count = 0
            
            for (signal in signals) {
                if (i < signal.size) {
                    sum += signal[i]
                    count++
                }
            }
            
            mixed[i] = if (count > 0) sum / count else 0f
        }
        
        return mixed
    }
}
