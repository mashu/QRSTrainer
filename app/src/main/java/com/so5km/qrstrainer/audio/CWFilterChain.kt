package com.so5km.qrstrainer.audio

import kotlin.math.*

/**
 * Professional CW Filter Chain - matches Web Audio API implementation exactly
 * Handles the complete filter chain for realistic CW receiver simulation
 */
class CWFilterChain(private val sampleRate: Int = 44100) {
    // Primary filter chain
    private val bandpass1 = BiquadFilter(BiquadFilter.FilterType.BANDPASS, 600.0, 25.0, sampleRate = sampleRate)
    private val bandpass2 = BiquadFilter(BiquadFilter.FilterType.BANDPASS, 630.0, 20.0, sampleRate = sampleRate)
    private val peaking1 = BiquadFilter(BiquadFilter.FilterType.PEAKING, 550.0, 2.0, 8.0, sampleRate)
    private val compressor = BiquadFilter(BiquadFilter.FilterType.LOWPASS, 3000.0, 0.707, sampleRate = sampleRate)
    
    // Optional advanced filters
    private var notchFilter: BiquadFilter? = null
    private var allpassFilter: BiquadFilter? = null
    
    // Track center frequency for modulation
    private var frequency: Double = 600.0
    
    // LFO oscillators for modulation
    private var lfo1Phase = 0.0
    private var lfo2Phase = 0.0
    private var notchLfoPhase = 0.0
    private var phaserLfoPhase = 0.0
    
    // Modulation parameters
    private var lfo1Freq = 0.1
    private var lfo2Freq = 0.17
    private var notchLfoFreq = 0.05
    private var phaserLfoFreq = 0.03
    
    // Track filter parameters for state consistency
    private var currentCenterFreq = 600.0
    private var currentResonance = 15.0
    private var currentBandwidth = 250.0
    private var currentWarmth = 8.0
    private var currentAtmosphericIntensity = 2.0
    
    fun updateParameters(
        centerFreq: Double, 
        resonance: Double, 
        bandwidth: Double, 
        warmth: Double,
        atmosphericIntensity: Double
    ) {
        // Store current parameters for state tracking
        val paramsChanged = centerFreq != currentCenterFreq || 
                           resonance != currentResonance || 
                           bandwidth != currentBandwidth || 
                           warmth != currentWarmth || 
                           atmosphericIntensity != currentAtmosphericIntensity
        
        // Update stored parameters
        currentCenterFreq = centerFreq
        currentResonance = resonance
        currentBandwidth = bandwidth
        currentWarmth = warmth
        currentAtmosphericIntensity = atmosphericIntensity
        
        // Only update if parameters actually changed
        if (!paramsChanged) {
            return
        }
        
        // Log parameter changes for debugging
        android.util.Log.d("CWFilterChain", "Updating filter params: centerFreq=$centerFreq, Q=$resonance, BW=$bandwidth, warmth=$warmth, atmos=$atmosphericIntensity")
        
        // Store center frequency for modulation
        frequency = centerFreq
        
        // Update primary bandpass filters - make bandwidth more noticeable
        val effectiveQ1 = if (bandwidth > 0) {
            (centerFreq / bandwidth).coerceIn(1.0, 50.0)  // Use bandwidth to set Q
        } else {
            resonance
        }
        val effectiveQ2 = effectiveQ1 * 0.8
        
        // Apply Q factor directly to ensure consistent behavior
        bandpass1.setFrequency(centerFreq)
        bandpass1.setQ(effectiveQ1)
        
        bandpass2.setFrequency(centerFreq + 30.0)
        bandpass2.setQ(effectiveQ2)
        
        // Make bandwidth affect the overall filter character more dramatically
        val bandwidthFactor = when {
            bandwidth < 200 -> 1.5  // Narrow = more focused, intense 
            bandwidth > 800 -> 0.6  // Wide = more diffuse, gentler
            else -> 1.0
        }
        
        // Update warmth (peaking filter) - let bandwidth affect warmth too
        val effectiveWarmth = warmth * bandwidthFactor
        peaking1.setFrequency(550.0)  // Reset frequency to ensure consistency
        peaking1.setQ(2.0)  // Reset Q to ensure consistency
        peaking1.setGain(effectiveWarmth)
        
        // Setup advanced filters based on atmospheric intensity AND bandwidth
        if (atmosphericIntensity > 2.0) {
            if (notchFilter == null) {
                notchFilter = BiquadFilter(BiquadFilter.FilterType.NOTCH, frequency * 1.1, 8.0, sampleRate = sampleRate)
            }
            // Make bandwidth affect notch filter frequency spread
            val notchSpread = 1.1 + (bandwidth / 1000.0) * 0.2  // Wider bandwidth = wider notch spread
            notchFilter!!.setFrequency(frequency * notchSpread)
            notchFilter!!.setQ(8.0)  // Reset Q to ensure consistency
            notchLfoFreq = 0.05 + (atmosphericIntensity * 0.01)
        }
        
        if (atmosphericIntensity > 4.0) {
            if (allpassFilter == null) {
                allpassFilter = BiquadFilter(BiquadFilter.FilterType.ALLPASS, frequency * 0.98, 5.0, sampleRate = sampleRate)
            }
            // Let bandwidth affect allpass character too
            val allpassOffset = 0.98 - (bandwidth / 2000.0) * 0.1  // Wider bandwidth = lower allpass freq
            allpassFilter!!.setFrequency(frequency * allpassOffset)
            allpassFilter!!.setQ(5.0)  // Reset Q to ensure consistency
        }
        
        // Update LFO frequencies based on atmospheric intensity AND bandwidth
        val bandwidthLfoFactor = (bandwidth / 500.0).coerceIn(0.5, 2.0)  // Bandwidth affects LFO speed
        lfo1Freq = 0.1 * (1.0 + atmosphericIntensity * 0.3) * bandwidthLfoFactor
        lfo2Freq = 0.17 * (1.0 + atmosphericIntensity * 0.2) * bandwidthLfoFactor
        
        // Reset compressor to ensure consistent behavior
        compressor.setFrequency(3000.0)
        compressor.setQ(0.707)
    }
    
    fun process(input: Double, atmosphericIntensity: Double): Double {
        val dt = 1.0 / sampleRate
        
        // Update LFO phases
        lfo1Phase += 2.0 * PI * lfo1Freq * dt
        lfo2Phase += 2.0 * PI * lfo2Freq * dt
        notchLfoPhase += 2.0 * PI * notchLfoFreq * dt
        phaserLfoPhase += 2.0 * PI * phaserLfoFreq * dt
        
        // Keep phases in range
        if (lfo1Phase > 2.0 * PI) lfo1Phase -= 2.0 * PI
        if (lfo2Phase > 2.0 * PI) lfo2Phase -= 2.0 * PI
        if (notchLfoPhase > 2.0 * PI) notchLfoPhase -= 2.0 * PI
        if (phaserLfoPhase > 2.0 * PI) phaserLfoPhase -= 2.0 * PI
        
        // Apply more pronounced LFO modulation to filter frequencies
        val lfo1 = sin(lfo1Phase)
        val lfo2 = sin(lfo2Phase)
        
        // Increase modulation depth significantly to make effects more noticeable
        val modDepth1 = 15.0 + atmosphericIntensity * 5.0  // Much larger modulation
        val modDepth2 = 12.0 + atmosphericIntensity * 4.0  // Much larger modulation
        
        // Apply LFO modulation to filter frequencies (update actual filter frequencies)
        bandpass1.setFrequency(frequency + (lfo1 * modDepth1))
        bandpass2.setFrequency(frequency + 30.0 + (lfo2 * modDepth2))
        
        // Debug logging (occasional)
        if (Math.random() < 0.0001) {
            android.util.Log.d("CWFilterChain", "Filter1: ${(frequency + (lfo1 * modDepth1)).toInt()}Hz, Filter2: ${(frequency + 30.0 + (lfo2 * modDepth2)).toInt()}Hz")
        }
        
        // Apply modulation with more pronounced effect
        var output = input
        
        // Primary filter chain - apply with stronger effect
        output = bandpass1.process(output) * 1.2  // Boost filtered output
        output = bandpass2.process(output) * 1.1  // Boost filtered output
        output = peaking1.process(output)
        
        // Advanced filters for higher atmospheric settings with increased effect
        if (atmosphericIntensity > 2.0 && notchFilter != null) {
            val notchLfo = sin(notchLfoPhase)
            val notchModDepth = 20.0 + atmosphericIntensity * 10.0  // Much larger modulation range
            
            // Update notch frequency with LFO - more pronounced effect
            val baseNotchFreq = frequency * 1.1
            notchFilter!!.setFrequency(baseNotchFreq + (notchLfo * notchModDepth))
            val notchOutput = notchFilter!!.process(output)
            
            // More pronounced notch effect
            output = output * 0.7 + notchOutput * 0.3  // 30% notch effect instead of 10%
        }
        
        if (atmosphericIntensity > 4.0 && allpassFilter != null) {
            val phaserLfo = sin(phaserLfoPhase)
            val phaserModDepth = 30.0 + atmosphericIntensity * 15.0  // Much larger modulation range
            
            val baseAllpassFreq = frequency * 0.98
            allpassFilter!!.setFrequency(baseAllpassFreq + (phaserLfo * phaserModDepth))
            val phasedOutput = allpassFilter!!.process(output)
            
            // More pronounced phasing effect
            output = output * 0.8 + phasedOutput * 0.2  // 20% phasing effect instead of 5%
        }
        
        // Final compression/limiting with slight boost
        output = compressor.process(output) * 1.1
        
        return output
    }
    
    fun reset() {
        bandpass1.reset()
        bandpass2.reset()
        peaking1.reset()
        compressor.reset()
        notchFilter?.reset()
        allpassFilter?.reset()
        
        // Reset LFO phases
        lfo1Phase = 0.0
        lfo2Phase = 0.0
        notchLfoPhase = 0.0
        phaserLfoPhase = 0.0
    }
} 