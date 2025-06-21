package com.so5km.qrstrainer.audio

import kotlin.math.*

/**
 * Professional biquad filter implementation for realistic CW noise generation
 * Matches Web Audio API BiquadFilterNode behavior exactly
 */
class BiquadFilter(
    private var type: FilterType,
    private var frequency: Double,
    private var q: Double,
    private var gain: Double = 0.0,
    private val sampleRate: Int = 44100
) {
    enum class FilterType {
        LOWPASS, HIGHPASS, BANDPASS, NOTCH, ALLPASS, PEAKING
    }
    
    // Filter state variables
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0
    
    // Filter coefficients
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    
    init {
        updateCoefficients()
    }
    
    fun setFrequency(freq: Double) {
        frequency = freq.coerceIn(10.0, sampleRate / 2.0 - 1.0)
        updateCoefficients()
    }
    
    fun setQ(newQ: Double) {
        q = newQ.coerceIn(0.0001, 30.0)
        updateCoefficients()
    }
    
    fun setGain(newGain: Double) {
        gain = newGain.coerceIn(-40.0, 40.0)
        updateCoefficients()
    }
    
    private fun updateCoefficients() {
        val nyquist = sampleRate / 2.0
        val normalizedFreq = frequency / nyquist
        val omega = 2.0 * PI * normalizedFreq
        val cosOmega = cos(omega)
        val sinOmega = sin(omega)
        val alpha = sinOmega / (2.0 * q)
        val A = 10.0.pow(gain / 40.0)
        
        when (type) {
            FilterType.LOWPASS -> {
                b0 = (1.0 - cosOmega) / 2.0
                b1 = 1.0 - cosOmega
                b2 = (1.0 - cosOmega) / 2.0
                val a0 = 1.0 + alpha
                a1 = -2.0 * cosOmega
                a2 = 1.0 - alpha
                
                // Normalize
                b0 /= a0
                b1 /= a0
                b2 /= a0
                a1 /= a0
                a2 /= a0
            }
            FilterType.HIGHPASS -> {
                b0 = (1.0 + cosOmega) / 2.0
                b1 = -(1.0 + cosOmega)
                b2 = (1.0 + cosOmega) / 2.0
                val a0 = 1.0 + alpha
                a1 = -2.0 * cosOmega
                a2 = 1.0 - alpha
                
                // Normalize
                b0 /= a0
                b1 /= a0
                b2 /= a0
                a1 /= a0
                a2 /= a0
            }
            FilterType.BANDPASS -> {
                b0 = alpha
                b1 = 0.0
                b2 = -alpha
                val a0 = 1.0 + alpha
                a1 = -2.0 * cosOmega
                a2 = 1.0 - alpha
                
                // Normalize
                b0 /= a0
                b1 /= a0
                b2 /= a0
                a1 /= a0
                a2 /= a0
            }
            FilterType.NOTCH -> {
                b0 = 1.0
                b1 = -2.0 * cosOmega
                b2 = 1.0
                val a0 = 1.0 + alpha
                a1 = -2.0 * cosOmega
                a2 = 1.0 - alpha
                
                // Normalize
                b0 /= a0
                b1 /= a0
                b2 /= a0
                a1 /= a0
                a2 /= a0
            }
            FilterType.ALLPASS -> {
                b0 = 1.0 - alpha
                b1 = -2.0 * cosOmega
                b2 = 1.0 + alpha
                val a0 = 1.0 + alpha
                a1 = -2.0 * cosOmega
                a2 = 1.0 - alpha
                
                // Normalize
                b0 /= a0
                b1 /= a0
                b2 /= a0
                a1 /= a0
                a2 /= a0
            }
            FilterType.PEAKING -> {
                b0 = 1.0 + alpha * A
                b1 = -2.0 * cosOmega
                b2 = 1.0 - alpha * A
                val a0 = 1.0 + alpha / A
                a1 = -2.0 * cosOmega
                a2 = 1.0 - alpha / A
                
                // Normalize
                b0 /= a0
                b1 /= a0
                b2 /= a0
                a1 /= a0
                a2 /= a0
            }
        }
    }
    
    fun process(input: Double): Double {
        // Direct Form I implementation
        val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        
        // Update state
        x2 = x1
        x1 = input
        y2 = y1
        y1 = output
        
        return output
    }
    
    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }
} 