package com.so5km.qrstrainer.audio

import com.so5km.qrstrainer.data.TrainingSettings
import kotlin.math.*

/**
 * Professional CW Noise Generator
 * Handles realistic CW background noise generation using proper DSP techniques
 */
class CWNoiseGenerator(private val sampleRate: Int = 44100) {
    
    private lateinit var cwFilterState: CWFilterState
    
    /**
     * Professional CW-style background noise generation using proper DSP
     * Uses professional biquad filters for realistic CW receiver simulation
     */
    fun generateRealisticCWNoise(buffer: ShortArray, settings: TrainingSettings) {
        // Apply safety constraints to prevent harsh artifacts
        val safeSettings = applySafetyConstraints(settings)
        
        // CRITICAL: Use independent noise volume, not coupled to signal volume
        val absoluteNoiseLevel = safeSettings.noiseVolume.toDouble()  // This is the independent noise volume (0.0-1.0)
        
        if (absoluteNoiseLevel <= 0.0) {
            buffer.fill(0)
            return
        }
        
        val centerFreq = safeSettings.toneFrequencyHz.toDouble()
        val qFactor = safeSettings.filterQFactor.toDouble()
        val atmosphericIntensity = safeSettings.atmosphericIntensity.toDouble()
        val crackleIntensity = safeSettings.crackleIntensity.toDouble()
        val warmth = safeSettings.warmth.toDouble()
        val resonanceJumpRate = safeSettings.resonanceJumpRate.toDouble()
        val driftSpeed = safeSettings.driftSpeed.toDouble()
        val bandwidth = safeSettings.filterBandwidthHz.toDouble() // Use actual bandwidth setting
        
        // Initialize state if needed
        if (!::cwFilterState.isInitialized) {
            cwFilterState = CWFilterState()
        }
        
        // Initialize filter chain if needed
        if (cwFilterState.filterChain == null) {
            cwFilterState.filterChain = CWFilterChain(sampleRate)
        }
        
        // Update filter parameters
        cwFilterState.filterChain!!.updateParameters(
            centerFreq = centerFreq,
            resonance = qFactor,
            bandwidth = bandwidth,
            warmth = warmth,
            atmosphericIntensity = atmosphericIntensity
        )
        
        // Debug logging (less frequent)
        if (cwFilterState.chunkCounter % 2000L == 0L) {
            android.util.Log.d("CWNoiseGenerator", "Professional CW Filter: centerFreq=${centerFreq.toInt()}Hz, Q=${qFactor.toString().take(4)}, noiseVol=${(absoluteNoiseLevel*100).toInt()}%, atmospheric=$atmosphericIntensity")
        }
        
        // Generate realistic CW atmospheric noise using professional DSP
        for (i in buffer.indices) {
            val sampleTime = (cwFilterState.chunkCounter * 1024 + i).toDouble() / sampleRate
            
            // STEP 1: Generate Brownian noise carrier (matching reference)
            val brownNoise = (Math.random() * 2 - 1) * 0.02 * minOf(3.0, 0.5 + atmosphericIntensity)
            cwFilterState.brownianState = (cwFilterState.brownianState + brownNoise) / 1.02
            
            // STEP 2: Add slow atmospheric modulation (matching reference phase increment)
            cwFilterState.atmosphericPhase += 0.0001
            val slowVar = sin(cwFilterState.atmosphericPhase) * 0.05 * atmosphericIntensity * 3.0
            
            // STEP 3: Add crackles and pops (matching reference probability calculation)
            val popProbability = 0.9995 - (atmosphericIntensity * 0.0002)
            val crackle = if (Math.random() > popProbability) {
                (Math.random() * 2 - 1) * crackleIntensity * minOf(3.0, 0.5 + atmosphericIntensity)
            } else 0.0
            
            // Combine base noise (matching reference)
            cwFilterState.atmosphericState = (cwFilterState.atmosphericState + cwFilterState.brownianState) / 2 + slowVar + crackle
            var baseNoise = cwFilterState.atmosphericState * 4.0 // Increased amplitude like reference
            
            // STEP 4: Apply professional DSP filter chain
            var filteredNoise = cwFilterState.filterChain!!.process(baseNoise, atmosphericIntensity)
            
            // STEP 5: Apply subtle resonance jumps (reduced intensity and better decay)
            val currentTime = System.currentTimeMillis()
            if (currentTime - cwFilterState.lastResonanceJump > (2000 / (0.00015 * resonanceJumpRate * sampleRate)).toLong()) {
                if (Math.random() < 0.08 * resonanceJumpRate) { // Reduced chance to 8%
                    val jumpAmount = (Math.random() * 4 - 2) * resonanceJumpRate // Much smaller jumps
                    cwFilterState.resonanceJumpState = jumpAmount
                    cwFilterState.resonanceDecay = 0.92 // Faster decay
                    cwFilterState.lastResonanceJump = currentTime
                }
            }
            
            // Apply current resonance jump (reduced amplitude)
            if (abs(cwFilterState.resonanceJumpState) > 0.05) {
                val resonanceTone = sin(2.0 * PI * centerFreq * sampleTime) * cwFilterState.resonanceJumpState * 0.03 // Much lower volume
                filteredNoise += resonanceTone
                cwFilterState.resonanceJumpState *= cwFilterState.resonanceDecay
            }
            
            // Advanced effects (notch filter singing, phasing) now handled by professional filter chain
            
            // STEP 6: Add subtle frequency drift (reduced range and effect)
            if (currentTime - cwFilterState.lastDriftUpdate > (3000 / maxOf(0.1, driftSpeed)).toLong()) {
                val driftRange = 15 + (atmosphericIntensity * 20) // Much smaller drift range
                cwFilterState.frequencyDrift = (Math.random() * driftRange - driftRange/2)
                cwFilterState.lastDriftUpdate = currentTime
            }
            
            // Apply current drift (reduced effect)
            if (abs(cwFilterState.frequencyDrift) > 1.0) {
                val driftedFreq = centerFreq + cwFilterState.frequencyDrift
                val driftEffect = sin(2.0 * PI * driftedFreq * sampleTime) * 0.02 // Reduced amplitude
                filteredNoise += driftEffect
                cwFilterState.frequencyDrift *= 0.999 // Faster decay
            }
            
            // STEP 7: Add deep fading for extreme atmospheric conditions (matching reference)
            if (atmosphericIntensity > 5.0) {
                if (currentTime - cwFilterState.lastFadingUpdate > 5000 && Math.random() < 0.15) { // 15% chance every 5 seconds
                    val fadeDepth = 0.6 + (Math.random() * 0.3) // 60-90% reduction
                    cwFilterState.fadingState = fadeDepth
                    cwFilterState.fadingDecay = 0.9998 // Very slow recovery like reference
                    cwFilterState.lastFadingUpdate = currentTime
                }
            }
            
            // Apply current fading
            if (cwFilterState.fadingState > 0.02) {
                filteredNoise *= (1.0 - cwFilterState.fadingState)
                cwFilterState.fadingState *= cwFilterState.fadingDecay
            }
            
            // STEP 8: Add amplitude modulation for very high atmospheric settings (matching reference)
            if (atmosphericIntensity > 3.0) {
                // Update AM parameters
                if (cwFilterState.amDepth == 0.0) {
                    cwFilterState.amFrequency = 0.05 + (Math.random() * 0.1 * (atmosphericIntensity - 3.0))
                    cwFilterState.amDepth = minOf(0.8, (atmosphericIntensity - 3.0) * 0.15)
                }
                
                // Apply AM
                cwFilterState.amPhase += 2.0 * PI * cwFilterState.amFrequency / sampleRate
                if (cwFilterState.amPhase > 2.0 * PI) cwFilterState.amPhase -= 2.0 * PI
                
                val amModulation = sin(cwFilterState.amPhase) * cwFilterState.amDepth
                filteredNoise *= (1.0 - amModulation)
            }
            
            // FINAL: Apply volume and clamp (CRITICAL: Use independent absolute noise volume)
            buffer[i] = (filteredNoise * absoluteNoiseLevel * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        
        // Update chunk counter
        cwFilterState.chunkCounter++
    }
    
    /**
     * Reset the noise generator state
     */
    fun reset() {
        if (::cwFilterState.isInitialized) {
            cwFilterState.filterChain?.reset()
            cwFilterState = CWFilterState()
        }
    }
    
    /**
     * Debug function to verify settings are being applied
     */
    fun debugSettings(settings: TrainingSettings): String {
        val safeSettings = applySafetyConstraints(settings)
        return """
            CW Noise Settings Debug:
            - Noise Volume: ${safeSettings.noiseVolume} (${(safeSettings.noiseVolume * 100).toInt()}%)
            - Tone Frequency: ${safeSettings.toneFrequencyHz} Hz
            - Atmospheric Intensity: ${safeSettings.atmosphericIntensity}
            - Resonance Jump Rate: ${safeSettings.resonanceJumpRate}
            - Drift Speed: ${safeSettings.driftSpeed}
            - Crackle Intensity: ${safeSettings.crackleIntensity}
            - Warmth: ${safeSettings.warmth} dB
            - Filter Q: ${safeSettings.filterQFactor}
            - Filter Bandwidth: ${safeSettings.filterBandwidthHz} Hz
        """.trimIndent()
    }
    
    /**
     * Get recommended default settings for good CW noise quality
     */
    fun getRecommendedDefaults(): Map<String, Any> {
        return mapOf(
            "atmosphericIntensity" to 2.5f,      // Moderate atmospheric effects
            "crackleIntensity" to 0.05f,         // Light crackles
            "resonanceJumpRate" to 0.3f,         // Subtle resonance jumps
            "driftSpeed" to 0.4f,                // Slow, natural drift
            "warmth" to 6.0f,                    // Pleasant warmth
            "noiseVolume" to 0.3f,               // Moderate noise level
            "filterQFactor" to 15.0f,            // Good filter ringing
            "toneFrequencyHz" to 600,            // Standard CW frequency
            "filterBandwidthHz" to 250           // Good selectivity
        )
    }
    
    /**
     * Apply safety constraints to prevent harsh artifacts
     */
    private fun applySafetyConstraints(settings: TrainingSettings): TrainingSettings {
        return settings.copy(
            atmosphericIntensity = settings.atmosphericIntensity.coerceIn(0.5f, 4.0f),
            crackleIntensity = settings.crackleIntensity.coerceIn(0.01f, 0.1f),
            resonanceJumpRate = settings.resonanceJumpRate.coerceIn(0.1f, 1.0f),
            driftSpeed = settings.driftSpeed.coerceIn(0.1f, 1.0f),
            warmth = settings.warmth.coerceIn(0.0f, 12.0f),
            noiseVolume = settings.noiseVolume.coerceIn(0.0f, 1.0f),
            filterQFactor = settings.filterQFactor.coerceIn(1.0f, 25.0f)
        )
    }
} 