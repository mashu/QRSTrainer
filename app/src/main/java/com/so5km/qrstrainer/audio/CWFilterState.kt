package com.so5km.qrstrainer.audio

/**
 * Enhanced CW Filter State with proper DSP implementation
 * Manages all state variables for realistic CW noise effects
 */
data class CWFilterState(
    // Brownian noise state
    var brownianState: Double = 0.0,
    var atmosphericState: Double = 0.0,
    var atmosphericPhase: Double = 0.0,
    
    // Filter chain
    var filterChain: CWFilterChain? = null,
    
    // Resonance jumps
    var resonanceJumpState: Double = 0.0,
    var resonanceDecay: Double = 0.95,
    var lastResonanceJump: Long = 0L,
    
    // Frequency drift
    var frequencyDrift: Double = 0.0,
    var lastDriftUpdate: Long = 0L,
    
    // Deep fading
    var fadingState: Double = 0.0,
    var fadingDecay: Double = 0.9998,
    var lastFadingUpdate: Long = 0L,
    
    // Amplitude modulation
    var amPhase: Double = 0.0,
    var amFrequency: Double = 0.1,
    var amDepth: Double = 0.0,
    
    // Chunk counter for timing
    var chunkCounter: Long = 0L
) 