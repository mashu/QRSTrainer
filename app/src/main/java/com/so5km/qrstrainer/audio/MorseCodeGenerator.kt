package com.so5km.qrstrainer.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.so5km.qrstrainer.data.MorseCode
import com.so5km.qrstrainer.data.TrainingSettings
import kotlin.math.*

/**
 * Generates and plays Morse code audio with configurable settings
 * Improved version with continuous sequence generation and better envelope handling
 */
class MorseCodeGenerator(private val context: Context) {
    
    companion object {
        private const val SAMPLE_RATE = 44100
        // ENVELOPE_MS now configurable via settings
    }
    
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    private var isPlaying = false
    private var isPaused = false
    @Volatile
    private var shouldStop = false
    @Volatile
    private var shouldPause = false
    
    // Background noise management
    private var noiseAudioTrack: AudioTrack? = null
    private var noiseThread: Thread? = null
    private var isNoisePlayingContinuously = false
    @Volatile
    private var shouldStopNoise = false
    
    // Current settings
    private var currentSettings: TrainingSettings = TrainingSettings()
    
    init {
        initializeAudioTrack()
        initializeNoiseAudioTrack()
    }
    
    private fun initializeAudioTrack() {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 8 // Larger buffer for smooth playback and prevent audio cutting
            
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
            
            android.util.Log.d("MorseCodeGenerator", "AudioTrack initialized in streaming mode, buffer size: $bufferSize")
        } catch (e: Exception) {
            android.util.Log.e("MorseCodeGenerator", "Failed to initialize AudioTrack: ${e.message}")
        }
    }
    
    private fun initializeNoiseAudioTrack() {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 2 // Smaller buffer for noise
            
            noiseAudioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
            
            android.util.Log.d("MorseCodeGenerator", "Noise AudioTrack initialized, buffer size: $bufferSize")
        } catch (e: Exception) {
            android.util.Log.e("MorseCodeGenerator", "Failed to initialize noise AudioTrack: ${e.message}")
        }
    }
    
    /**
     * Generate a sequence of characters and play as Morse code
     * Now generates the entire sequence as one continuous audio buffer
     */
    fun playSequence(
        sequence: String, 
        settings: TrainingSettings,
        onComplete: (() -> Unit)? = null
    ) {
        // Stop any current playback before starting new one
        stop()
        
        currentSettings = settings
        isPlaying = true
        isPaused = false
        shouldStop = false
        shouldPause = false
        
        // Start continuous background noise if enabled
        if (settings.backgroundNoiseLevel > 0 && !isNoisePlayingContinuously) {
            startContinuousNoise()
        }
        
        playbackThread = Thread {
            try {
                android.util.Log.d("MorseCodeGenerator", "Starting playback of sequence: '$sequence' at ${settings.speedWpm}wpm, tone: ${settings.toneFrequencyHz}Hz")
                
                // Generate the entire sequence as one continuous audio buffer
                val audioData = generateSequenceAudio(sequence, settings)
                
                if (audioData.isNotEmpty() && !shouldStop) {
                    // Pre-fill the AudioTrack buffer with initial data before starting playback
                    // This prevents audio cutting at the beginning
                    val initialChunkSize = minOf(4096, audioData.size)
                    val bytesWritten = audioTrack!!.write(audioData, 0, initialChunkSize)
                    
                    if (bytesWritten > 0) {
                        // Now start playback - the buffer already has data
                        audioTrack?.play()
                        
                        // Write the remaining data if any
                        if (initialChunkSize < audioData.size) {
                            playAudioBuffer(audioData, initialChunkSize)
                        }
                    }
                    
                    audioTrack?.stop()
                }
                
            } catch (e: InterruptedException) {
                android.util.Log.d("MorseCodeGenerator", "Playback interrupted")
            } catch (e: Exception) {
                android.util.Log.e("MorseCodeGenerator", "Error during playback: ${e.message}")
                e.printStackTrace()
            } finally {
                isPlaying = false
                isPaused = false
                onComplete?.invoke()
            }
        }
        
        playbackThread?.start()
    }
    
    /**
     * Generate the entire sequence as one continuous audio buffer
     * This eliminates clicks between characters and provides smooth playback
     */
    private fun generateSequenceAudio(sequence: String, settings: TrainingSettings): ShortArray {
        val dotDuration = calculateDotDuration(settings.speedWpm)
        val dashDuration = dotDuration * 3
        val symbolSpacing = dotDuration
        val charSpacing = calculateCharacterSpacing(settings)
        val wordSpacing = calculateWordSpacing(settings)
        
        // Calculate total duration for the entire sequence including repeats
        val singleSequenceDuration = calculateSequenceDuration(sequence, settings)
        val totalDuration = singleSequenceDuration * settings.repeatCount + 
                           (settings.repeatSpacingMs + settings.groupSpacingMs) * (settings.repeatCount - 1)
        
        val totalSamples = (SAMPLE_RATE * totalDuration / 1000.0).toInt()
        val audioBuffer = ShortArray(totalSamples)
        
        var bufferIndex = 0
        
        // Generate audio for each repeat
        for (repeatIndex in 0 until settings.repeatCount) {
            if (shouldStop) break
            
            // Generate audio for this sequence
            bufferIndex = generateSequenceIntoBuffer(
                audioBuffer, 
                bufferIndex, 
                sequence, 
                dotDuration, 
                dashDuration, 
                symbolSpacing, 
                charSpacing, 
                wordSpacing, 
                settings.toneFrequencyHz
            )
            
            // Add repeat spacing if not the last repeat
            if (repeatIndex < settings.repeatCount - 1) {
                val spacingDuration = settings.repeatSpacingMs + settings.groupSpacingMs
                bufferIndex = addSilenceToBuffer(audioBuffer, bufferIndex, spacingDuration)
            }
        }
        
        // Apply overall envelope to prevent any clicks at sequence start/end
        applyOverallEnvelope(audioBuffer)
        
        return audioBuffer
    }
    
    /**
     * Generate audio for a single sequence into the buffer
     */
    private fun generateSequenceIntoBuffer(
        buffer: ShortArray,
        startIndex: Int,
        sequence: String,
        dotDuration: Int,
        dashDuration: Int,
        symbolSpacing: Int,
        charSpacing: Int,
        wordSpacing: Int,
        toneFrequency: Int
    ): Int {
        var bufferIndex = startIndex
        
        for (i in sequence.indices) {
            if (shouldStop || bufferIndex >= buffer.size) break
            
            val char = sequence[i]
            val pattern = MorseCode.getPattern(char)
            
            if (pattern != null) {
                android.util.Log.d("MorseCodeGenerator", "Generating character '$char' with pattern '$pattern'")
                
                // Generate pattern audio
                bufferIndex = generatePatternIntoBuffer(
                    buffer, 
                    bufferIndex, 
                    pattern, 
                    dotDuration, 
                    dashDuration, 
                    symbolSpacing, 
                    toneFrequency
                )
                
                // Add character spacing (except after last character)
                if (i < sequence.length - 1) {
                    val spacing = if (char == ' ') wordSpacing else charSpacing
                    bufferIndex = addSilenceToBuffer(buffer, bufferIndex, spacing)
                }
            }
        }
        
        return bufferIndex
    }
    
    /**
     * Generate audio for a single Morse pattern into the buffer
     */
    private fun generatePatternIntoBuffer(
        buffer: ShortArray,
        startIndex: Int,
        pattern: String,
        dotDuration: Int,
        dashDuration: Int,
        symbolSpacing: Int,
        toneFrequency: Int
    ): Int {
        var bufferIndex = startIndex
        
        for (i in pattern.indices) {
            if (shouldStop || bufferIndex >= buffer.size) break
            
            val duration = when (pattern[i]) {
                '.' -> dotDuration
                '-' -> dashDuration
                else -> continue
            }
            
            // Generate tone with envelope
            bufferIndex = generateToneIntoBuffer(
                buffer, 
                bufferIndex, 
                duration, 
                toneFrequency
            )
            
            // Add symbol spacing (except after last symbol)
            if (i < pattern.length - 1) {
                bufferIndex = addSilenceToBuffer(buffer, bufferIndex, symbolSpacing)
            }
        }
        
        return bufferIndex
    }
    
    /**
     * Generate a tone with proper envelope into the buffer
     */
    private fun generateToneIntoBuffer(
        buffer: ShortArray,
        startIndex: Int,
        durationMs: Int,
        toneFrequency: Int
    ): Int {
        val samples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val endIndex = minOf(startIndex + samples, buffer.size)
        
        // Calculate envelope samples (minimum 1 sample, maximum 10% of duration)
        val envelopeMs = currentSettings.audioEnvelopeMs
        val envelopeSamples = maxOf(1, minOf(samples / 10, SAMPLE_RATE * envelopeMs / 1000))
        
        for (i in startIndex until endIndex) {
            val sampleIndex = i - startIndex
            val angle = 2.0 * PI * sampleIndex * toneFrequency / SAMPLE_RATE
            var amplitude = sin(angle) * currentSettings.appVolumeLevel // Use app volume setting
            
            // Apply CW filter effects if enabled
            if (currentSettings.filterRingingEnabled) {
                amplitude = applyCWFilterEffects(amplitude, sampleIndex, toneFrequency)
            }
            
            // Apply envelope based on keying style
            when (currentSettings.keyingStyle) {
                0 -> { // Hard keying - minimal envelope
                    val hardEnvelopeSamples = maxOf(1, envelopeSamples / 3)
                    when {
                        sampleIndex < hardEnvelopeSamples -> {
                            val fadeRatio = sampleIndex.toDouble() / hardEnvelopeSamples
                            amplitude *= fadeRatio
                        }
                        sampleIndex >= samples - hardEnvelopeSamples -> {
                            val fadeIndex = samples - 1 - sampleIndex
                            val fadeRatio = fadeIndex.toDouble() / hardEnvelopeSamples
                            amplitude *= fadeRatio
                        }
                    }
                }
                1 -> { // Soft keying - normal envelope
                    when {
                        sampleIndex < envelopeSamples -> {
                            val fadeRatio = sampleIndex.toDouble() / envelopeSamples
                            amplitude *= 0.5 * (1.0 - cos(PI * fadeRatio))
                        }
                        sampleIndex >= samples - envelopeSamples -> {
                            val fadeIndex = samples - 1 - sampleIndex
                            val fadeRatio = fadeIndex.toDouble() / envelopeSamples
                            amplitude *= 0.5 * (1.0 - cos(PI * fadeRatio))
                        }
                    }
                }
                2 -> { // Smooth keying - extended envelope
                    val smoothEnvelopeSamples = envelopeSamples * 2
                    when {
                        sampleIndex < smoothEnvelopeSamples -> {
                            val fadeRatio = sampleIndex.toDouble() / smoothEnvelopeSamples
                            amplitude *= sin(PI * fadeRatio / 2.0).pow(2)
                        }
                        sampleIndex >= samples - smoothEnvelopeSamples -> {
                            val fadeIndex = samples - 1 - sampleIndex
                            val fadeRatio = fadeIndex.toDouble() / smoothEnvelopeSamples
                            amplitude *= sin(PI * fadeRatio / 2.0).pow(2)
                        }
                    }
                }
            }
            
            buffer[i] = (amplitude * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        
        // Add ringing tail after the tone if filter ringing is enabled
        if (currentSettings.filterRingingEnabled && currentSettings.filterQFactor > 3.0) {
            val ringingTailDuration = minOf(50, durationMs / 2) // Up to 50ms ringing tail
            val ringingTailSamples = (SAMPLE_RATE * ringingTailDuration / 1000.0).toInt()
            val ringingEndIndex = minOf(endIndex + ringingTailSamples, buffer.size)
            
            for (i in endIndex until ringingEndIndex) {
                val ringingIndex = i - endIndex
                val ringingPhase = 2.0 * PI * ringingIndex * currentSettings.toneFrequencyHz / SAMPLE_RATE
                val ringingDecay = exp(-ringingIndex * 0.0008) // Exponential decay
                val ringingAmplitude = 0.3 * (currentSettings.filterQFactor / 20.0) * ringingDecay
                val ringing = sin(ringingPhase) * ringingAmplitude
                
                buffer[i] = (ringing * Short.MAX_VALUE).toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            
            return ringingEndIndex
        }
        
        return endIndex
    }
    
    /**
     * Add silence to the buffer, but preserve any existing ringing
     */
    private fun addSilenceToBuffer(
        buffer: ShortArray,
        startIndex: Int,
        durationMs: Int
    ): Int {
        if (durationMs <= 0) return startIndex
        
        val samples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val endIndex = minOf(startIndex + samples, buffer.size)
        
        // Fill with zeros (silence), but only if there's no existing ringing
        for (i in startIndex until endIndex) {
            // If there's already audio content (ringing), mix it with silence rather than overwrite
            if (buffer[i] != 0.toShort()) {
                // Allow existing ringing to continue, just reduce amplitude slightly
                buffer[i] = (buffer[i] * 0.8).toInt().toShort()
            } else {
                buffer[i] = 0
            }
        }
        
        return endIndex
    }
    
    /**
     * Apply overall envelope to the entire sequence to prevent any clicks
     */
    private fun applyOverallEnvelope(buffer: ShortArray) {
        if (buffer.isEmpty()) return
        
        val envelopeMs = currentSettings.audioEnvelopeMs * 2 // Double for overall envelope
        val envelopeSamples = SAMPLE_RATE * envelopeMs / 1000
        val actualEnvelopeSamples = minOf(envelopeSamples, buffer.size / 10)
        
        // Fade in at the very beginning
        for (i in 0 until minOf(actualEnvelopeSamples, buffer.size)) {
            val fadeRatio = i.toDouble() / actualEnvelopeSamples
            val envelope = 0.5 * (1.0 - cos(PI * fadeRatio))
            buffer[i] = (buffer[i] * envelope).toInt().toShort()
        }
        
        // Fade out at the very end
        for (i in maxOf(0, buffer.size - actualEnvelopeSamples) until buffer.size) {
            val fadeIndex = buffer.size - 1 - i
            val fadeRatio = fadeIndex.toDouble() / actualEnvelopeSamples
            val envelope = 0.5 * (1.0 - cos(PI * fadeRatio))
            buffer[i] = (buffer[i] * envelope).toInt().toShort()
        }
    }
    
    /**
     * Play the generated audio buffer
     */
    private fun playAudioBuffer(buffer: ShortArray, startIndex: Int = 0) {
        var offset = startIndex
        val chunkSize = 4096 // Write in chunks for smooth playback
        
        while (offset < buffer.size && !shouldStop) {
            // Handle pause
            while (shouldPause && !shouldStop) {
                Thread.sleep(50)
            }
            
            if (shouldStop) break
            
            val remainingSamples = buffer.size - offset
            val samplesToWrite = minOf(chunkSize, remainingSamples)
            
            try {
                val bytesWritten = audioTrack!!.write(buffer, offset, samplesToWrite)
                if (bytesWritten > 0) {
                    offset += bytesWritten
                } else {
                    // If write fails, wait a bit and try again
                    Thread.sleep(1)
                }
            } catch (e: Exception) {
                android.util.Log.e("MorseCodeGenerator", "Error writing to AudioTrack: ${e.message}")
                break
            }
        }
    }
    
    /**
     * Calculate the total duration of a sequence in milliseconds
     */
    private fun calculateSequenceDuration(sequence: String, settings: TrainingSettings): Int {
        val dotDuration = calculateDotDuration(settings.speedWpm)
        val dashDuration = dotDuration * 3
        val symbolSpacing = dotDuration
        val charSpacing = calculateCharacterSpacing(settings)
        val wordSpacing = calculateWordSpacing(settings)
        
        var totalDuration = 0
        
        for (i in sequence.indices) {
            val char = sequence[i]
            val pattern = MorseCode.getPattern(char)
            
            if (pattern != null) {
                // Add pattern duration
                for (symbol in pattern) {
                    when (symbol) {
                        '.' -> totalDuration += dotDuration
                        '-' -> totalDuration += dashDuration
                    }
                }
                
                // Add symbol spacing within pattern
                if (pattern.length > 1) {
                    totalDuration += symbolSpacing * (pattern.length - 1)
                }
                
                // Add character spacing (except after last character)
                if (i < sequence.length - 1) {
                    totalDuration += if (char == ' ') wordSpacing else charSpacing
                }
            }
        }
        
        return totalDuration
    }
    
    /**
     * Pause current playback
     */
    fun pause() {
        if (isPlaying && !isPaused) {
            shouldPause = true
            isPaused = true
            try {
                audioTrack?.pause()
                noiseAudioTrack?.pause()
                android.util.Log.d("MorseCodeGenerator", "Audio paused")
            } catch (e: Exception) {
                android.util.Log.e("MorseCodeGenerator", "Error pausing AudioTrack: ${e.message}")
            }
        }
    }
    
    /**
     * Resume paused playback
     */
    fun resume() {
        if (isPlaying && isPaused) {
            shouldPause = false
            isPaused = false
            try {
                audioTrack?.play()
                if (isNoisePlayingContinuously) {
                    noiseAudioTrack?.play()
                }
                android.util.Log.d("MorseCodeGenerator", "Audio resumed")
            } catch (e: Exception) {
                android.util.Log.e("MorseCodeGenerator", "Error resuming AudioTrack: ${e.message}")
            }
        }
    }
    
    /**
     * Stop current playback
     */
    fun stop() {
        shouldStop = true
        shouldPause = false
        
        try {
            audioTrack?.stop()
        } catch (e: Exception) {
            android.util.Log.e("MorseCodeGenerator", "Error stopping AudioTrack: ${e.message}")
        }
        
        // Stop continuous background noise
        stopContinuousNoise()
        
        // Interrupt and wait for playback thread to finish
        playbackThread?.interrupt()
        try {
            playbackThread?.join(1000) // Wait up to 1 second
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        playbackThread = null
        
        isPlaying = false
        isPaused = false
    }
    
    /**
     * Check if audio is currently playing
     */
    fun isPlaying(): Boolean = isPlaying
    
    /**
     * Check if audio is currently paused
     */
    fun isPaused(): Boolean = isPaused
    
    /**
     * Clean up resources
     */
    fun release() {
        stop()
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            android.util.Log.e("MorseCodeGenerator", "Error releasing AudioTrack: ${e.message}")
        }
        
        try {
            noiseAudioTrack?.release()
            noiseAudioTrack = null
        } catch (e: Exception) {
            android.util.Log.e("MorseCodeGenerator", "Error releasing noise AudioTrack: ${e.message}")
        }
    }
    
    /**
     * Calculate dot duration in milliseconds for given WPM
     * Standard: PARIS = 50 dot units, so dot = 1200ms / WPM
     */
    private fun calculateDotDuration(wpm: Int): Int {
        return (1200.0 / wpm).toInt()
    }
    
    /**
     * Calculate character spacing with Farnsworth timing
     */
    private fun calculateCharacterSpacing(settings: TrainingSettings): Int {
        val baseDuration = calculateDotDuration(settings.speedWpm) * 3 // Normal character spacing is 3 dots
        
        return if (settings.farnsworthWpm > 0 && settings.farnsworthWpm < settings.speedWpm) {
            // Farnsworth timing: slow down spacing but keep character speed
            val farnsworthRatio = settings.farnsworthWpm.toDouble() / settings.speedWpm
            (baseDuration / farnsworthRatio).toInt() + settings.wordSpacingMs
        } else {
            baseDuration + settings.wordSpacingMs
        }
    }
    
    /**
     * Calculate word spacing with Farnsworth timing
     */
    private fun calculateWordSpacing(settings: TrainingSettings): Int {
        val baseDuration = calculateDotDuration(settings.speedWpm) * 7 // Normal word spacing is 7 dots
        
        return if (settings.farnsworthWpm > 0 && settings.farnsworthWpm < settings.speedWpm) {
            // Farnsworth timing: slow down spacing but keep character speed
            val farnsworthRatio = settings.farnsworthWpm.toDouble() / settings.speedWpm
            (baseDuration / farnsworthRatio).toInt() + settings.wordSpacingMs
        } else {
            baseDuration + settings.wordSpacingMs
        }
    }
    
    /**
     * Apply CW filter effects including ringing and bandwidth filtering
     * Improved to reduce audio chopping and provide more realistic effects
     */
    private fun applyCWFilterEffects(amplitude: Double, sampleIndex: Int, toneFrequency: Int): Double {
        var filteredAmplitude = amplitude
        
        val centerFreq = currentSettings.toneFrequencyHz.toDouble()
        val primaryOffset = currentSettings.primaryFilterOffset.toDouble()
        val secondaryOffset = currentSettings.secondaryFilterOffset.toDouble()
        val qFactor = currentSettings.filterQFactor.toDouble()
        
        // Calculate effective filter positions
        val primaryFilterFreq = centerFreq + primaryOffset
        val secondaryFilterFreq = centerFreq + secondaryOffset
        
        // Calculate minimum bandwidth to prevent zero bandwidth
        val minBandwidth = 50.0 // Minimum 50Hz bandwidth
        val primaryBandwidth = maxOf(minBandwidth, currentSettings.filterBandwidthHz.toDouble())
        val secondaryBandwidth = maxOf(minBandwidth, currentSettings.secondaryFilterBandwidthHz.toDouble())
        
        // Apply dual filter response (more realistic CW filtering)
        val primaryFreqDiff = abs(toneFrequency - primaryFilterFreq)
        val secondaryFreqDiff = abs(toneFrequency - secondaryFilterFreq)
        
        val primaryResponse = 1.0 / (1.0 + (primaryFreqDiff / (primaryBandwidth / 2.0)).pow(2))
        val secondaryResponse = 1.0 / (1.0 + (secondaryFreqDiff / (secondaryBandwidth / 2.0)).pow(2))
        
        // Combine filter responses (additive for overlapping, multiplicative for cascaded)
        val combinedResponse = if (abs(primaryOffset - secondaryOffset) < minBandwidth) {
            // Overlapping filters - additive response
            (primaryResponse + secondaryResponse) / 2.0
        } else {
            // Separate filters - cascaded response
            sqrt(primaryResponse * secondaryResponse)
        }
        
        filteredAmplitude *= combinedResponse
        
        // Add realistic CW filter ringing only if enabled and Q is reasonable
        if (qFactor > 2.0) {
            // Much gentler ringing that doesn't dominate the signal
            val ringingDecay = exp(-sampleIndex * 0.001) // Faster decay to prevent chopping
            val ringingAmplitude = 0.05 * (qFactor / 10.0) // Much lower amplitude
            
            // Primary filter ringing
            val primaryRingingPhase = 2.0 * PI * sampleIndex * primaryFilterFreq / SAMPLE_RATE
            val primaryRinging = sin(primaryRingingPhase) * ringingAmplitude * ringingDecay
            
            // Secondary filter ringing (only if filters are separated)
            val secondaryRinging = if (abs(primaryOffset - secondaryOffset) > minBandwidth) {
                val secondaryRingingPhase = 2.0 * PI * sampleIndex * secondaryFilterFreq / SAMPLE_RATE
                sin(secondaryRingingPhase) * ringingAmplitude * 0.5 * ringingDecay
            } else {
                0.0
            }
            
            // Add subtle ringing - much less aggressive
            filteredAmplitude += (primaryRinging + secondaryRinging) * 0.3
        }
        
        return filteredAmplitude.coerceIn(-1.0, 1.0) // Prevent clipping
    }
    
    /**
     * Start continuous background noise that plays throughout the session
     */
    private fun startContinuousNoise() {
        if (isNoisePlayingContinuously) return
        
        isNoisePlayingContinuously = true
        shouldStopNoise = false
        
        noiseThread = Thread {
            try {
                android.util.Log.d("MorseCodeGenerator", "Starting continuous background noise at ${(currentSettings.backgroundNoiseLevel * 100).toInt()}%")
                
                noiseAudioTrack?.play()
                
                val chunkSize = 1024
                val noiseBuffer = ShortArray(chunkSize)
                
                while (!shouldStopNoise && isNoisePlayingContinuously) {
                    // Generate filtered noise chunk
                    generateFilteredNoiseChunk(noiseBuffer)
                    
                    try {
                        val bytesWritten = noiseAudioTrack!!.write(noiseBuffer, 0, chunkSize)
                        if (bytesWritten <= 0) {
                            Thread.sleep(1) // Brief pause if write fails
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MorseCodeGenerator", "Error writing noise: ${e.message}")
                        break
                    }
                }
                
                noiseAudioTrack?.stop()
                
            } catch (e: Exception) {
                android.util.Log.e("MorseCodeGenerator", "Error in continuous noise playback: ${e.message}")
            } finally {
                isNoisePlayingContinuously = false
                android.util.Log.d("MorseCodeGenerator", "Continuous background noise stopped")
            }
        }
        
        noiseThread?.start()
    }
    
    /**
     * Stop continuous background noise
     */
    private fun stopContinuousNoise() {
        shouldStopNoise = true
        
        try {
            noiseAudioTrack?.stop()
        } catch (e: Exception) {
            android.util.Log.e("MorseCodeGenerator", "Error stopping noise AudioTrack: ${e.message}")
        }
        
        noiseThread?.interrupt()
        try {
            noiseThread?.join(500) // Wait up to 500ms
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        noiseThread = null
        
        isNoisePlayingContinuously = false
    }
    
    /**
     * Generate a chunk of filtered background noise
     * Enhanced to properly simulate filter effects on noise spectrum
     */
    private fun generateFilteredNoiseChunk(buffer: ShortArray) {
        val centerFreq = currentSettings.toneFrequencyHz.toDouble()
        val primaryBandwidth = currentSettings.filterBandwidthHz.toDouble()
        val secondaryBandwidth = currentSettings.secondaryFilterBandwidthHz.toDouble()
        val primaryOffset = currentSettings.primaryFilterOffset.toDouble()
        val secondaryOffset = currentSettings.secondaryFilterOffset.toDouble()
        val qFactor = currentSettings.filterQFactor.toDouble()
        val noiseLevel = currentSettings.backgroundNoiseLevel * currentSettings.appVolumeLevel
        
        for (i in buffer.indices) {
            // Generate white noise
            var noise = (Math.random() - 0.5) * 2.0 * noiseLevel * 0.2 // Background level
            
            // Apply realistic bandpass filtering to the noise
            if (currentSettings.filterRingingEnabled) {
                // Calculate effective filter positions
                val primaryFilterFreq = centerFreq + primaryOffset
                val secondaryFilterFreq = centerFreq + secondaryOffset
                
                // Generate noise components at different frequencies and filter them
                var filteredNoise = 0.0
                
                // Sample multiple frequency components to simulate filtered noise spectrum
                for (freqSample in 0 until 10) {
                    val testFreq = centerFreq + (freqSample - 5) * 100 // Sample ±500Hz around center
                    
                    // Calculate filter response for this frequency
                    val primaryFreqDiff = abs(testFreq - primaryFilterFreq)
                    val secondaryFreqDiff = abs(testFreq - secondaryFilterFreq)
                    
                    val primaryResponse = 1.0 / (1.0 + (primaryFreqDiff / (primaryBandwidth / 2.0)).pow(qFactor / 5.0))
                    val secondaryResponse = 1.0 / (1.0 + (secondaryFreqDiff / (secondaryBandwidth / 2.0)).pow(qFactor / 5.0))
                    
                    // Combine filter responses
                    val combinedResponse = if (abs(primaryOffset - secondaryOffset) < 100) {
                        // Overlapping filters - additive response
                        (primaryResponse + secondaryResponse) / 2.0
                    } else {
                        // Separate filters - cascaded response
                        sqrt(primaryResponse * secondaryResponse)
                    }
                    
                    // Add filtered noise component
                    val freqNoise = (Math.random() - 0.5) * 2.0 * combinedResponse
                    filteredNoise += freqNoise
                }
                
                noise = filteredNoise / 10.0 * noiseLevel * 0.3
            }
            
            buffer[i] = (noise * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
    
    /**
     * Calculate a simple filter response for noise filtering
     */
    private fun calculateSimpleFilterResponse(centerFreq: Double, bandwidth: Double): Double {
        // This is a simplified version - in reality the noise would have spectral content
        // across all frequencies and would be filtered accordingly
        // For our purposes, we'll use a constant that represents the average filter response
        return when {
            bandwidth < 300 -> 0.3  // Narrow filter - significant noise reduction
            bandwidth < 600 -> 0.5  // Medium filter - moderate noise reduction  
            bandwidth < 1200 -> 0.7 // Wide filter - less noise reduction
            else -> 0.9            // Very wide filter - minimal noise reduction
        }
    }
} 