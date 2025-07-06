package com.so5km.qrstrainer.audio

import kotlin.random.Random

/**
 * Generates various types of noise for realistic CW conditions
 */
class NoiseGenerator(private val sampleRate: Int = AudioEngine.SAMPLE_RATE) {
    
    fun generateNoise(
        durationMs: Int,
        amplitude: Float,
        bandwidthHz: Float
    ): FloatArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val noise = FloatArray(numSamples)
        
        // Generate white noise
        for (i in 0 until numSamples) {
            noise[i] = (Random.nextFloat() * 2 - 1) * amplitude
        }
        
        // Apply bandpass filter for realistic HF noise
        return applyBandpassFilter(noise, bandwidthHz)
    }
    
    private fun applyBandpassFilter(
        signal: FloatArray,
        bandwidth: Float
    ): FloatArray {
        // Simple IIR filter implementation
        // In production, use a proper DSP library
        val filtered = FloatArray(signal.size)
        val alpha = bandwidth / sampleRate
        
        var prev = 0f
        for (i in signal.indices) {
            filtered[i] = signal[i] * alpha + prev * (1 - alpha)
            prev = filtered[i]
        }
        
        return filtered
    }
}
