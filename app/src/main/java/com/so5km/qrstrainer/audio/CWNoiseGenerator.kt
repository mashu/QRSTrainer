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
        
        // Check if primary settings have changed
        val settingsChanged = !::cwFilterState.isInitialized || 
                              cwFilterState.lastCenterFreq != centerFreq ||
                              cwFilterState.lastQFactor != qFactor ||
                              cwFilterState.lastBandwidth != bandwidth ||
                              cwFilterState.lastAtmosphericIntensity != atmosphericIntensity
        
        // Initialize state if needed
        if (!::cwFilterState.isInitialized) {
            cwFilterState = CWFilterState()
        }
        
        // Update tracking parameters
        cwFilterState.lastCenterFreq = centerFreq
        cwFilterState.lastQFactor = qFactor
        cwFilterState.lastBandwidth = bandwidth
        cwFilterState.lastAtmosphericIntensity = atmosphericIntensity
        
        // Initialize filter chain if needed
        if (cwFilterState.filterChain == null) {
            cwFilterState.filterChain = CWFilterChain(sampleRate)
        }
        
        // Update filter parameters - log changes for debugging
        if (settingsChanged) {
            android.util.Log.d("CWNoiseGenerator", "Settings changed: centerFreq=${centerFreq.toInt()}Hz, Q=${qFactor.toString().take(4)}, BW=${bandwidth.toInt()}Hz")
            cwFilterState.filterChain!!.updateParameters(
                centerFreq = centerFreq,
                resonance = qFactor,
                bandwidth = bandwidth,
                warmth = warmth,
                atmosphericIntensity = atmosphericIntensity
            )
        }
        
        // Debug logging (less frequent)
        if (cwFilterState.chunkCounter % 1000L == 0L) {
            android.util.Log.d("CWNoiseGenerator", "Professional CW Filter: centerFreq=${centerFreq.toInt()}Hz, Q=${qFactor.toString().take(4)}, noiseVol=${(absoluteNoiseLevel*100).toInt()}%, atmospheric=$atmosphericIntensity")
        }
        
        // Generate realistic CW atmospheric noise using professional DSP
        for (i in buffer.indices) {
            val sampleTime = (cwFilterState.chunkCounter * 1024 + i).toDouble() / sampleRate
            
            // STEP 1: Generate Brownian noise carrier with increased amplitude
            val brownNoise = (Math.random() * 2 - 1) * 0.04 * minOf(5.0, 0.8 + atmosphericIntensity)
            cwFilterState.brownianState = (cwFilterState.brownianState + brownNoise) / 1.01  // Slower decay for more low-frequency content
            
            // STEP 2: Add slow atmospheric modulation with increased effect
            cwFilterState.atmosphericPhase += 0.0001
            val slowVar = sin(cwFilterState.atmosphericPhase) * 0.08 * atmosphericIntensity * 3.0  // Increased amplitude
            
            // STEP 3: Add crackles and pops with increased intensity
            val popProbability = 0.9995 - (atmosphericIntensity * 0.0003)  // More frequent pops
            val crackle = if (Math.random() > popProbability) {
                (Math.random() * 2 - 1) * crackleIntensity * minOf(5.0, 0.8 + atmosphericIntensity) * 1.5  // Increased amplitude
            } else 0.0
            
            // Combine base noise with increased amplitude
            cwFilterState.atmosphericState = (cwFilterState.atmosphericState + cwFilterState.brownianState) / 2 + slowVar + crackle
            var baseNoise = cwFilterState.atmosphericState * 5.0 // Significantly increased amplitude
            
            // STEP 4: Apply professional DSP filter chain with increased effect
            var filteredNoise = cwFilterState.filterChain!!.process(baseNoise, atmosphericIntensity)
            
            // STEP 5: Apply resonance jumps with increased effect
            val currentTime = System.currentTimeMillis()
            if (currentTime - cwFilterState.lastResonanceJump > (1500 / (0.0002 * resonanceJumpRate * sampleRate)).toLong()) {
                if (Math.random() < 0.12 * resonanceJumpRate) { // Increased chance to 12%
                    val jumpAmount = (Math.random() * 6 - 3) * resonanceJumpRate * 1.5 // Larger jumps
                    cwFilterState.resonanceJumpState = jumpAmount
                    cwFilterState.resonanceDecay = 0.94 // Slower decay for more noticeable effect
                    cwFilterState.lastResonanceJump = currentTime
                }
            }
            
            // Apply current resonance jump with increased amplitude
            if (abs(cwFilterState.resonanceJumpState) > 0.05) {
                val resonanceTone = sin(2.0 * PI * centerFreq * sampleTime) * cwFilterState.resonanceJumpState * 0.06 // Higher volume
                filteredNoise += resonanceTone
                cwFilterState.resonanceJumpState *= cwFilterState.resonanceDecay
            }
            
            // STEP 6: Add frequency drift with increased effect
            if (currentTime - cwFilterState.lastDriftUpdate > (2500 / maxOf(0.1, driftSpeed)).toLong()) {
                val driftRange = 25 + (atmosphericIntensity * 30) // Larger drift range
                cwFilterState.frequencyDrift = (Math.random() * driftRange - driftRange/2)
                cwFilterState.lastDriftUpdate = currentTime
            }
            
            // Apply current drift with increased effect
            if (abs(cwFilterState.frequencyDrift) > 1.0) {
                val driftedFreq = centerFreq + cwFilterState.frequencyDrift
                val driftEffect = sin(2.0 * PI * driftedFreq * sampleTime) * 0.04 // Increased amplitude
                filteredNoise += driftEffect
                cwFilterState.frequencyDrift *= 0.998 // Slower decay for more noticeable effect
            }
            
            // STEP 7: Add deep fading for atmospheric conditions with increased effect
            if (atmosphericIntensity > 3.0) {
                if (currentTime - cwFilterState.lastFadingUpdate > 4000 && Math.random() < 0.2) { // 20% chance every 4 seconds
                    val fadeDepth = 0.7 + (Math.random() * 0.25) // 70-95% reduction
                    cwFilterState.fadingState = fadeDepth
                    cwFilterState.fadingDecay = 0.9997 // Very slow recovery
                    cwFilterState.lastFadingUpdate = currentTime
                }
            }
            
            // Apply current fading with increased effect
            if (cwFilterState.fadingState > 0.02) {
                filteredNoise *= (1.0 - cwFilterState.fadingState)
                cwFilterState.fadingState *= cwFilterState.fadingDecay
            }
            
            // STEP 8: Add amplitude modulation with increased effect
            if (atmosphericIntensity > 2.0) {  // Lower threshold for AM effect
                // Update AM parameters
                if (cwFilterState.amDepth == 0.0) {
                    cwFilterState.amFrequency = 0.08 + (Math.random() * 0.15 * (atmosphericIntensity - 2.0))
                    cwFilterState.amDepth = minOf(0.9, (atmosphericIntensity - 2.0) * 0.2)  // Increased depth
                }
                
                // Apply AM with increased effect
                cwFilterState.amPhase += 2.0 * PI * cwFilterState.amFrequency / sampleRate
                if (cwFilterState.amPhase > 2.0 * PI) cwFilterState.amPhase -= 2.0 * PI
                
                val amModulation = sin(cwFilterState.amPhase) * cwFilterState.amDepth
                filteredNoise *= (1.0 - amModulation)
            }
            
            // FINAL: Apply volume and clamp with slight boost
            buffer[i] = (filteredNoise * absoluteNoiseLevel * 1.2 * Short.MAX_VALUE).toInt()
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