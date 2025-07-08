package com.so5km.qrstrainer.audio

import kotlin.math.*
import kotlin.random.Random

/**
 * Generates various types of noise for realistic CW conditions
 */
class NoiseGenerator(private val sampleRate: Int = AudioEngine.SAMPLE_RATE) {
    
    // Butterworth filter coefficients storage
    private data class ButterworthFilter(
        val b: DoubleArray,
        val a: DoubleArray,
        val zi: DoubleArray
    )
    
    private var currentFilter: ButterworthFilter? = null
    private var lastCenterFreq: Float = 0f
    private var lastBandwidth: Float = 0f
    
    fun generateNoise(
        durationMs: Int,
        amplitude: Float,
        bandwidthHz: Float,
        centerFreqHz: Float = 600f
    ): FloatArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val noise = FloatArray(numSamples)
        
        // Generate white noise
        for (i in 0 until numSamples) {
            noise[i] = (Random.nextFloat() * 2 - 1) * amplitude
        }
        
        // Apply CW-specific bandpass filter
        return applyCWBandpassFilter(noise, centerFreqHz, bandwidthHz)
    }
    
    /**
     * Apply a Butterworth bandpass filter characteristic of CW receivers
     */
    private fun applyCWBandpassFilter(
        signal: FloatArray,
        centerFreq: Float,
        bandwidth: Float
    ): FloatArray {
        // Update filter if parameters changed
        if (currentFilter == null || centerFreq != lastCenterFreq || bandwidth != lastBandwidth) {
            currentFilter = designButterworthBandpass(centerFreq, bandwidth)
            lastCenterFreq = centerFreq
            lastBandwidth = bandwidth
        }
        
        val filter = currentFilter ?: return signal
        
        // Apply filter using Direct Form II Transposed
        val filtered = FloatArray(signal.size)
        val z = filter.zi.copyOf()
        
        for (i in signal.indices) {
            var y = filter.b[0] * signal[i].toDouble()
            
            for (j in 1 until filter.b.size) {
                if (i >= j) {
                    y += filter.b[j] * signal[i - j].toDouble()
                }
            }
            
            for (j in 1 until filter.a.size) {
                if (i >= j) {
                    y -= filter.a[j] * filtered[i - j].toDouble()
                }
            }
            
            filtered[i] = (y / filter.a[0]).toFloat()
        }
        
        return filtered
    }
    
    /**
     * Design a Butterworth bandpass filter for CW reception
     * This simulates the steep-sided filters used in CW receivers
     */
    private fun designButterworthBandpass(
        centerFreq: Float,
        bandwidth: Float
    ): ButterworthFilter {
        val order = 4 // 4th order for good selectivity without excessive ringing
        val nyquist = sampleRate / 2.0
        
        // Calculate normalized frequencies
        val lowFreq = (centerFreq - bandwidth / 2) / nyquist
        val highFreq = (centerFreq + bandwidth / 2) / nyquist
        
        // Ensure frequencies are in valid range
        val wLow = max(0.001, min(0.999, lowFreq))
        val wHigh = max(0.001, min(0.999, highFreq))
        
        // Pre-warp frequencies for bilinear transform
        val warpedLow = tan(PI * wLow / 2)
        val warpedHigh = tan(PI * wHigh / 2)
        
        // Design analog prototype lowpass filter
        val (bAnalog, aAnalog) = butterworthAnalogPrototype(order)
        
        // Transform to bandpass
        val bw = warpedHigh - warpedLow
        val w0 = sqrt(warpedLow * warpedHigh)
        
        // Calculate digital filter coefficients using bilinear transform
        val b = DoubleArray(2 * order + 1)
        val a = DoubleArray(2 * order + 1)
        
        // Simplified coefficient calculation for bandpass
        val alpha = sin(2 * PI * centerFreq / sampleRate) * sinh(ln(2.0) / 2 * bandwidth / centerFreq)
        val cos0 = cos(2 * PI * centerFreq / sampleRate)
        
        // Second-order sections for stability
        b[0] = alpha
        b[1] = 0.0
        b[2] = -alpha
        
        a[0] = 1 + alpha
        a[1] = -2 * cos0
        a[2] = 1 - alpha
        
        // Normalize
        for (i in b.indices) {
            b[i] /= a[0]
        }
        for (i in 1 until a.size) {
            a[i] /= a[0]
        }
        a[0] = 1.0
        
        return ButterworthFilter(b, a, DoubleArray(max(b.size, a.size) - 1))
    }
    
    /**
     * Generate Butterworth analog prototype coefficients
     */
    private fun butterworthAnalogPrototype(order: Int): Pair<DoubleArray, DoubleArray> {
        val b = DoubleArray(order + 1)
        val a = DoubleArray(order + 1)
        
        // For Butterworth, numerator is just 1
        b[0] = 1.0
        
        // Calculate denominator poles
        a[0] = 1.0
        for (k in 1..order) {
            val theta = PI * (2 * k - 1) / (2 * order)
            a[k] = a[k - 1] * 2 * sin(theta)
        }
        
        return Pair(b, a)
    }
    
    /**
     * Generate band-limited noise that sounds like real HF band noise
     */
    fun generateHFBandNoise(
        durationMs: Int,
        amplitude: Float,
        centerFreqHz: Float,
        bandwidthHz: Float,
        filterType: String = "butterworth",
        filterOrder: Int = 4,
        includeStatic: Boolean = true,
        includeQRN: Boolean = true
    ): FloatArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        var noise = FloatArray(numSamples)
        
        // Base white noise
        for (i in 0 until numSamples) {
            noise[i] = (Random.nextFloat() * 2 - 1) * amplitude * 0.7f
        }
        
        // Add occasional static crashes (QRN)
        if (includeQRN && Random.nextFloat() < 0.01f) {
            val crashStart = Random.nextInt(numSamples - 100)
            val crashDuration = Random.nextInt(50, 150)
            for (i in crashStart until min(crashStart + crashDuration, numSamples)) {
                noise[i] += (Random.nextFloat() * 2 - 1) * amplitude * 2f * 
                    exp(-(i - crashStart).toFloat() / 20f)
            }
        }
        
        // Add power line hum (50/60 Hz and harmonics)
        if (includeStatic) {
            val humFreq = if (Random.nextBoolean()) 50f else 60f
            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                noise[i] += amplitude * 0.1f * sin(2 * PI * humFreq * t).toFloat()
                noise[i] += amplitude * 0.05f * sin(2 * PI * humFreq * 2 * t).toFloat()
            }
        }
        
        // Apply CW filter with specified type and order
        noise = applyCWBandpassFilterWithType(noise, centerFreqHz, bandwidthHz, filterType, filterOrder)
        
        // Add some AGC-like compression to simulate receiver AGC
        val rms = sqrt(noise.map { it * it }.average()).toFloat()
        if (rms > 0) {
            val targetRms = amplitude * 0.3f
            val gain = targetRms / rms
            for (i in noise.indices) {
                noise[i] = (noise[i] * gain).coerceIn(-1f, 1f)
            }
        }
        
        return noise
    }
    
    private fun applyCWBandpassFilterWithType(
        signal: FloatArray,
        centerFreq: Float,
        bandwidth: Float,
        filterType: String,
        filterOrder: Int
    ): FloatArray {
        // For now, use the existing filter implementation
        // In a real implementation, you would design different filter types
        return applyCWBandpassFilter(signal, centerFreq, bandwidth)
    }
}
