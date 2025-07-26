package com.so5km.qrstrainer.audio

import kotlin.math.*
import kotlin.random.Random

// Extension function to generate Gaussian-distributed random numbers
private fun Random.nextGaussian(): Double {
    // Box-Muller transform
    var v1: Double
    var v2: Double
    var s: Double
    do {
        v1 = 2 * nextDouble() - 1
        v2 = 2 * nextDouble() - 1
        s = v1 * v1 + v2 * v2
    } while (s >= 1 || s == 0.0)
    
    val multiplier = sqrt(-2 * ln(s) / s)
    return v1 * multiplier
}

/**
 * Generates various types of noise for realistic CW conditions
 */
class NoiseGenerator(private val sampleRate: Int = AudioEngine.SAMPLE_RATE) {
    
    // Butterworth filter coefficients storage
    private data class ButterworthFilter(
        val sections: List<BiquadSection>,
        val gain: Double = 1.0
    )
    
    private data class BiquadSection(
        val b0: Double, val b1: Double, val b2: Double,
        val a0: Double, val a1: Double, val a2: Double,
        var z1: Double = 0.0, var z2: Double = 0.0  // State variables
    )
    
    private var currentFilter: ButterworthFilter? = null
    private var lastCenterFreq: Float = 0f
    private var lastBandwidth: Float = 0f
    private var lastOrder: Int = 0
    
    // State for pink noise generation
    private var pinkNoiseState = FloatArray(7)
    
    // Store last audio samples for spectrum analysis
    private var lastInputAudio: FloatArray? = null
    private var lastFilteredAudio: FloatArray? = null
    
    fun generateNoise(
        durationMs: Int,
        amplitude: Float,
        bandwidthHz: Float,
        centerFreqHz: Float = 600f
    ): FloatArray {
        // Always use realistic HF band noise for CW training
        return generateHFBandNoise(
            durationMs, 
            amplitude, 
            centerFreqHz, 
            bandwidthHz,
            filterType = "butterworth",
            filterOrder = 4,
            includeStatic = true,
            includeQRN = true
        )
    }
    
    /**
     * Generate pink noise (1/f noise) which is more characteristic of HF band noise
     */
    private fun generatePinkNoise(numSamples: Int, amplitude: Float): FloatArray {
        val noise = FloatArray(numSamples)
        
        for (i in 0 until numSamples) {
            var white = (Random.nextFloat() * 2 - 1)
            
            // Paul Kellet's pink noise filter
            pinkNoiseState[0] = 0.99886f * pinkNoiseState[0] + white * 0.0555179f
            pinkNoiseState[1] = 0.99332f * pinkNoiseState[1] + white * 0.0750759f
            pinkNoiseState[2] = 0.96900f * pinkNoiseState[2] + white * 0.1538520f
            pinkNoiseState[3] = 0.86650f * pinkNoiseState[3] + white * 0.3104856f
            pinkNoiseState[4] = 0.55000f * pinkNoiseState[4] + white * 0.5329522f
            pinkNoiseState[5] = -0.7616f * pinkNoiseState[5] + white * 0.0168980f
            
            val pink = (pinkNoiseState[0] + pinkNoiseState[1] + pinkNoiseState[2] + 
                       pinkNoiseState[3] + pinkNoiseState[4] + pinkNoiseState[5] + 
                       pinkNoiseState[6] + white * 0.5362f) * 0.11f
            
            pinkNoiseState[6] = white * 0.115926f
            
            noise[i] = pink * amplitude
        }
        
        return noise
    }
    
    /**
     * Generate atmospheric noise bursts (QRN)
     */
    private fun generateQRN(numSamples: Int, amplitude: Float): FloatArray {
        val qrn = FloatArray(numSamples)
        
        // Random static crashes
        val numCrashes = Random.nextInt(0, 3)
        for (crash in 0 until numCrashes) {
            val crashStart = Random.nextInt(numSamples - 200)
            val crashDuration = Random.nextInt(20, 100)
            val crashAmplitude = Random.nextFloat() * 2f + 1f
            
            for (i in crashStart until min(crashStart + crashDuration, numSamples)) {
                val t = (i - crashStart).toFloat() / crashDuration
                // Exponential decay envelope
                val envelope = exp(-t * 5f)
                // Add some oscillation to simulate lightning discharge
                val oscillation = sin(2 * PI * Random.nextFloat() * 1000f * t).toFloat()
                qrn[i] += (crashAmplitude * envelope * oscillation * amplitude).toFloat()
            }
        }
        
        return qrn
    }
    
    /**
     * Generate brown noise (1/f² noise) - Brownian motion
     */
    private fun generateBrownNoise(numSamples: Int, amplitude: Float): FloatArray {
        val noise = FloatArray(numSamples)
        var brown = 0f
        
        for (i in 0 until numSamples) {
            val white = (Random.nextFloat() * 2 - 1)
            brown += white * 0.02f // Integration with small step
            brown *= 0.997f // Slight decay to prevent drift
            noise[i] = brown * amplitude * 3f // Scale up as brown noise is quieter
        }
        
        return noise
    }
    
    /**
     * Apply a Butterworth bandpass filter characteristic of CW receivers
     */
    private fun applyCWBandpassFilter(
        signal: FloatArray,
        centerFreq: Float,
        bandwidth: Float,
        order: Int = 4
    ): FloatArray {
        // Store input for spectrum analysis
        lastInputAudio = signal.copyOf()
        
        // Update filter if parameters changed
        if (currentFilter == null || centerFreq != lastCenterFreq || 
            bandwidth != lastBandwidth || order != lastOrder) {
            currentFilter = designButterworthBandpass(centerFreq, bandwidth, order)
            lastCenterFreq = centerFreq
            lastBandwidth = bandwidth
            lastOrder = order
        }
        
        val filter = currentFilter ?: return signal
        
        // Apply cascaded biquad sections
        val filtered = signal.copyOf()
        
        for (section in filter.sections) {
            for (i in filtered.indices) {
                // Direct Form II implementation
                val input = filtered[i] * filter.gain
                val output = section.b0 * input + section.z1
                section.z1 = section.b1 * input - section.a1 * output + section.z2
                section.z2 = section.b2 * input - section.a2 * output
                filtered[i] = output.toFloat()
            }
        }
        
        // Store output for spectrum analysis
        lastFilteredAudio = filtered.copyOf()
        
        return filtered
    }
    
    /**
     * Design a Butterworth bandpass filter for CW reception
     * This simulates the steep-sided filters used in CW receivers
     */
    private fun designButterworthBandpass(
        centerFreq: Float,
        bandwidth: Float,
        order: Int = 4
    ): ButterworthFilter {
        // Ensure even order for bandpass (we need order/2 sections)
        val actualOrder = if (order % 2 == 1) order + 1 else order
        val numSections = actualOrder / 2
        
        val w0 = 2 * PI * centerFreq / sampleRate
        val bw = 2 * PI * bandwidth / sampleRate
        
        val sections = mutableListOf<BiquadSection>()
        
        // Design cascaded biquad sections for Butterworth response
        for (k in 0 until numSections) {
            // Butterworth pole angles
            val theta = PI * (2 * k + 1) / (2 * numSections)
            val poleReal = -sin(bw / 2) * sin(theta)
            val poleImag = sin(bw / 2) * cos(theta)
            
            // Bilinear transform coefficients
            val d = 1 - 2 * poleReal * cos(w0) + poleReal * poleReal + poleImag * poleImag
            
            // Bandpass biquad coefficients
            val b0 = sin(bw / 2) / d
            val b1 = 0.0
            val b2 = -sin(bw / 2) / d
            val a0 = 1.0
            val a1 = -2 * (poleReal * poleReal + poleImag * poleImag - 1) * cos(w0) / d
            val a2 = (1 + 2 * poleReal * cos(w0) + poleReal * poleReal + poleImag * poleImag) / d
            
            sections.add(BiquadSection(b0, b1, b2, a0, a1, a2))
        }
        
        // Calculate overall gain to normalize response
        val gain = 1.0 / sections.fold(1.0) { acc, section -> acc * section.b0 }
        
        return ButterworthFilter(sections, gain)
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
        includeQRN: Boolean = true,
        includeQSB: Boolean = false
    ): FloatArray {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        var noise = FloatArray(numSamples)
        
        // Start with pink noise (1/f) as base - more realistic than white noise
        val pinkNoise = generatePinkNoise(numSamples, amplitude * 0.7f)
        
        // Add some white noise for high-frequency hiss
        val whiteNoise = FloatArray(numSamples) { 
            (Random.nextFloat() * 2 - 1) * amplitude * 0.2f 
        }
        
        // Combine pink and white noise
        for (i in 0 until numSamples) {
            noise[i] = pinkNoise[i] + whiteNoise[i]
        }
        
        // Add atmospheric noise bursts (QRN)
        if (includeQRN) {
            val qrn = generateQRN(numSamples, amplitude)
            for (i in 0 until numSamples) {
                noise[i] += qrn[i]
            }
        }
        
        // Add weak carriers and heterodynes
        if (includeStatic) {
            // Weak carrier just outside passband
            val carrierOffset = bandwidthHz * 0.7f
            val carrierFreq = if (Random.nextBoolean()) {
                centerFreqHz + carrierOffset
            } else {
                centerFreqHz - carrierOffset
            }
            
            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                // Weak carrier with slight drift
                val drift = sin(2 * PI * 0.1f * t) * 2f
                noise[i] += amplitude * 0.05f * sin(2 * PI * (carrierFreq + drift) * t).toFloat()
            }
            
            // Add some 50/60 Hz hum (very weak)
            val humFreq = if (Random.nextBoolean()) 50f else 60f
            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                noise[i] += amplitude * 0.02f * sin(2 * PI * humFreq * t).toFloat()
            }
        }
        
        // Apply CW filter with specified type and order
        noise = applyCWBandpassFilterWithType(noise, centerFreqHz, bandwidthHz, filterType, filterOrder)
        
        // Add receiver thermal noise after filtering (Johnson-Nyquist noise)
        val thermalNoise = FloatArray(numSamples) {
            (Random.nextGaussian() * amplitude * 0.1f).toFloat()
        }
        for (i in 0 until numSamples) {
            noise[i] += thermalNoise[i]
        }
        
        // Apply QSB (fading) if enabled
        if (includeQSB) {
            val fadeRate = Random.nextFloat() * 0.5f + 0.1f // 0.1 to 0.6 Hz
            val fadeDepth = Random.nextFloat() * 0.7f + 0.3f // 30% to 100% depth
            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                val fade = (1f - fadeDepth * (sin(2 * PI * fadeRate * t).toFloat() + 1f) / 2f)
                noise[i] *= fade
            }
        }
        
        // Apply soft AGC-like compression to simulate receiver AGC
        // This creates the characteristic "breathing" sound
        val blockSize = sampleRate / 10 // 100ms blocks
        for (blockStart in 0 until numSamples step blockSize) {
            val blockEnd = min(blockStart + blockSize, numSamples)
            
            // Calculate RMS for this block
            var sum = 0f
            for (i in blockStart until blockEnd) {
                sum += noise[i] * noise[i]
            }
            val rms = sqrt(sum / (blockEnd - blockStart))
            
            if (rms > 0) {
                val targetRms = amplitude * 0.3f
                val gain = min(targetRms / rms, 2f) // Limit gain to prevent overamplification
                
                // Apply gain with soft knee compression
                for (i in blockStart until blockEnd) {
                    val sample = noise[i] * gain
                    // Soft clipping
                    noise[i] = if (abs(sample) > 0.9f) {
                        0.9f * sign(sample) * (1f - exp(-abs(sample)))
                    } else {
                        sample
                    }
                }
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
        // For now, we only have Butterworth implemented
        // In future, we could add Chebyshev and Elliptic implementations
        return when (filterType) {
            "butterworth" -> applyCWBandpassFilter(signal, centerFreq, bandwidth, filterOrder)
            "chebyshev" -> {
                // TODO: Implement Chebyshev filter
                // For now, fall back to Butterworth with adjusted order
                applyCWBandpassFilter(signal, centerFreq, bandwidth * 0.8f, filterOrder)
            }
            "elliptic" -> {
                // TODO: Implement Elliptic filter
                // For now, fall back to Butterworth with narrower bandwidth
                applyCWBandpassFilter(signal, centerFreq, bandwidth * 0.6f, filterOrder + 2)
            }
            else -> applyCWBandpassFilter(signal, centerFreq, bandwidth, filterOrder)
        }
    }

    /**
     * Get spectrum data for visualization
     * Returns a pair of (input spectrum, output spectrum)
     */
    fun getSpectrumData(fftSize: Int = 512): Pair<FloatArray, FloatArray>? {
        val input = lastInputAudio ?: return null
        val output = lastFilteredAudio ?: return null
        
        // Simple magnitude spectrum calculation
        // In a real implementation, you'd use a proper FFT library
        val inputSpectrum = calculateMagnitudeSpectrum(input, fftSize)
        val outputSpectrum = calculateMagnitudeSpectrum(output, fftSize)
        
        return Pair(inputSpectrum, outputSpectrum)
    }
    
    /**
     * Generate test spectrum data for visualization
     * This creates a simulated spectrum with CW tone and noise
     */
    fun generateTestSpectrum(
        centerFreq: Float,
        bandwidth: Float,
        filterOrder: Int,
        includeNoise: Boolean = true
    ): Pair<FloatArray, FloatArray> {
        val fftSize = 512
        val spectrum = FloatArray(fftSize / 2)
        
        // Create frequency axis
        val freqBinWidth = (sampleRate / 2f) / (fftSize / 2)
        
        // Generate input spectrum (CW tone + noise)
        for (i in spectrum.indices) {
            val freq = i * freqBinWidth
            
            // Add CW tone peak at center frequency
            val toneWidth = 50f // Hz
            val distFromTone = abs(freq - centerFreq)
            if (distFromTone < toneWidth) {
                // Gaussian-shaped CW signal
                spectrum[i] = exp(-(distFromTone * distFromTone) / (toneWidth * toneWidth)).toFloat()
            }
            
            // Add noise floor
            if (includeNoise) {
                // Pink noise spectrum (1/f characteristic)
                val noiseLevel = if (freq > 10) 0.1f / sqrt(freq / 1000f) else 0.1f
                spectrum[i] += noiseLevel * (0.8f + Random.nextFloat() * 0.4f)
            }
        }
        
        // Apply filter to create output spectrum
        val outputSpectrum = FloatArray(fftSize / 2)
        
        for (i in spectrum.indices) {
            val freq = i * freqBinWidth
            val response = calculateFilterResponse(freq, centerFreq, bandwidth, filterOrder)
            outputSpectrum[i] = spectrum[i] * response
        }
        
        return Pair(spectrum, outputSpectrum)
    }
    
    /**
     * Calculate frequency response of the filter at a given frequency
     */
    private fun calculateFilterResponse(
        freq: Float,
        centerFreq: Float,
        bandwidth: Float,
        order: Int
    ): Float {
        val normalizedFreq = 2 * (freq - centerFreq) / bandwidth
        
        // Butterworth bandpass response
        // Each biquad section contributes to the overall response
        val numSections = (if (order % 2 == 1) order + 1 else order) / 2
        var response = 1f
        
        // Approximate Butterworth response
        if (abs(normalizedFreq) > 1) {
            // Outside passband - rolloff rate depends on order
            response = 1f / (1f + normalizedFreq.pow(2 * numSections))
        }
        
        return response
    }
    
    /**
     * Simple DFT-based magnitude spectrum calculation
     * For production, use a proper FFT implementation
     */
    private fun calculateMagnitudeSpectrum(signal: FloatArray, fftSize: Int): FloatArray {
        val spectrum = FloatArray(fftSize / 2)
        val windowSize = min(signal.size, fftSize)
        
        // Apply Hamming window
        val windowed = FloatArray(windowSize)
        for (i in 0 until windowSize) {
            val window = 0.54f - 0.46f * cos(2 * PI * i / (windowSize - 1)).toFloat()
            windowed[i] = signal[i] * window
        }
        
        // Calculate magnitude for each frequency bin
        for (k in 0 until fftSize / 2) {
            var real = 0.0
            var imag = 0.0
            
            for (n in 0 until windowSize) {
                val angle = -2 * PI * k * n / fftSize
                real += windowed[n] * cos(angle)
                imag += windowed[n] * sin(angle)
            }
            
            spectrum[k] = sqrt(real * real + imag * imag).toFloat() / windowSize
        }
        
        return spectrum
    }
}
