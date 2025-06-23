package com.so5km.qrstrainer.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.so5km.qrstrainer.data.MorseCode
import com.so5km.qrstrainer.data.TrainingSettings
import kotlin.math.*

// DSP components moved to separate files for better code organization

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
    
    // Professional CW noise generator
    private val cwNoiseGenerator = CWNoiseGenerator(SAMPLE_RATE)
    
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
        startNoise: Boolean = true,
        onComplete: (() -> Unit)? = null
    ) {
        // Stop any current playback before starting new one
        stopSequence()
        
        currentSettings = settings
        isPlaying = true
        isPaused = false
        shouldStop = false
        shouldPause = false
        
        // Start continuous background noise ONLY if checkbox is enabled AND noise level > 0
        // AND startNoise parameter is true (for session-based noise control)
        if (startNoise && settings.filterRingingEnabled && settings.backgroundNoiseLevel > 0 && !isNoisePlayingContinuously) {
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
        // Higher frequencies get sharper envelopes (shorter effective envelope time)
        val baseEnvelopeMs = currentSettings.audioEnvelopeMs
        val frequencyFactor = when {
            toneFrequency >= 800 -> 0.7f // 30% shorter envelope for sharp high frequencies
            toneFrequency >= 600 -> 1.0f // Normal envelope
            toneFrequency >= 400 -> 1.3f // 30% longer envelope for gentle mid frequencies
            else -> 1.6f // 60% longer envelope for very gentle low frequencies
        }
        val effectiveEnvelopeMs = (baseEnvelopeMs * frequencyFactor).toInt()
        val envelopeSamples = maxOf(1, minOf(samples / 10, SAMPLE_RATE * effectiveEnvelopeMs / 1000))
        
        for (i in startIndex until endIndex) {
            val sampleIndex = i - startIndex
            val angle = 2.0 * PI * sampleIndex * toneFrequency / SAMPLE_RATE
            var amplitude = sin(angle) * currentSettings.appVolumeLevel // Use app volume setting
            
            // Apply subtle filter warmth to signal if enabled (minimal processing)
            // This does NOT affect timing - just subtle tone coloring
            if (currentSettings.filterRingingEnabled) {
                amplitude = applySignalWarmth(amplitude, toneFrequency)
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
        
        // CRITICAL: Always return the exact calculated endIndex
        // Do NOT add any ringing tails or extensions that would affect timing
        // The letters must play for their exact intended duration
        return endIndex
    }
    
    /**
     * Add silence to the buffer for proper spacing
     */
    private fun addSilenceToBuffer(
        buffer: ShortArray,
        startIndex: Int,
        durationMs: Int
    ): Int {
        if (durationMs <= 0) return startIndex
        
        val samples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val endIndex = minOf(startIndex + samples, buffer.size)
        
        // Fill with clean silence - no ringing preservation needed
        // This ensures proper timing between letters and words
        for (i in startIndex until endIndex) {
            buffer[i] = 0
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
                android.util.Log.d("MorseCodeGenerator", "Audio resumed")
            } catch (e: Exception) {
                android.util.Log.e("MorseCodeGenerator", "Error resuming AudioTrack: ${e.message}")
            }
        }
    }
    
    /**
     * Stop only the current sequence playback (not the background noise)
     * Used for session-based training where noise continues between sequences
     */
    fun stopSequence() {
        shouldStop = true
        shouldPause = false
        
        try {
            audioTrack?.stop()
        } catch (e: Exception) {
            android.util.Log.e("MorseCodeGenerator", "Error stopping AudioTrack: ${e.message}")
        }
        
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
     * Stop all audio playback including background noise
     * Used when completely stopping training
     */
    fun stop() {
        stopSequence()
        stopContinuousNoise()
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
     * Check if background noise is currently playing
     */
    fun isNoiseActive(): Boolean = isNoisePlayingContinuously
    
    /**
     * Start continuous background noise for session-based training
     * This keeps noise playing continuously throughout the session
     */
    fun startContinuousNoise() {
        if (isNoisePlayingContinuously) return
        
        android.util.Log.d("MorseCodeGenerator", "Starting continuous session noise - Noise level: ${(currentSettings.backgroundNoiseLevel * 100).toInt()}%")
        
        isNoisePlayingContinuously = true
        shouldStopNoise = false
        
        noiseThread = Thread {
            try {
                noiseAudioTrack?.play()
                
                val chunkSize = 1024
                val noiseBuffer = ShortArray(chunkSize)
                
                while (!shouldStopNoise && isNoisePlayingContinuously) {
                    // Generate CW-filtered noise chunk using professional CW noise generator
                    cwNoiseGenerator.generateRealisticCWNoise(noiseBuffer, currentSettings)
                    
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
                android.util.Log.d("MorseCodeGenerator", "Continuous session noise stopped")
            }
        }
        
        noiseThread?.start()
    }
    
    /**
     * Stop continuous background noise
     */
    fun stopContinuousNoise() {
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
     * Apply subtle warmth to the signal based on filter settings
     * This gives the signal character without heavy processing
     */
    private fun applySignalWarmth(amplitude: Double, toneFrequency: Int): Double {
        val centerFreq = currentSettings.toneFrequencyHz.toDouble()
        val primaryBandwidth = currentSettings.filterBandwidthHz.toDouble()
        
        // Apply very subtle frequency response for warmth
        val freqDiff = abs(toneFrequency - centerFreq)
        val warmthFactor = when {
            primaryBandwidth < 200 -> 0.95 + 0.05 * cos(PI * freqDiff / 100.0) // Narrow = warmer
            primaryBandwidth > 1000 -> 1.0 // Wide = neutral
            else -> 0.98 + 0.02 * cos(PI * freqDiff / 200.0) // Medium = slight warmth
        }
        
        return amplitude * warmthFactor
    }
    
    /**
     * Start continuous noise for testing CW filter parameters
     * This is independent of training mode and can be used for real-time parameter adjustment
     */
    fun startTestNoise(settings: TrainingSettings) {
        if (isNoisePlayingContinuously) {
            stopTestNoise()
        }
        
        currentSettings = settings
        android.util.Log.d("MorseCodeGenerator", "Starting CW test noise - Level: ${(settings.backgroundNoiseLevel * 100).toInt()}%, LFO1: ${settings.lfo1FrequencyHz}Hz, LFO2: ${settings.lfo2FrequencyHz}Hz")
        startContinuousNoise()
    }
    
    /**
     * Stop continuous test noise
     */
    fun stopTestNoise() {
        android.util.Log.d("MorseCodeGenerator", "Stopping CW test noise")
        stopContinuousNoise()
    }
    
    /**
     * Update settings for continuous noise testing in real-time
     */
    fun updateNoiseSettings(settings: TrainingSettings) {
        if (isNoisePlayingContinuously) {
            currentSettings = settings
            android.util.Log.d("MorseCodeGenerator", "Updated CW test noise settings - Level: ${(settings.backgroundNoiseLevel * 100).toInt()}%, Q: ${settings.filterQFactor}, LFO1: ${settings.lfo1FrequencyHz}Hz, LFO2: ${settings.lfo2FrequencyHz}Hz")
        }
    }
    
    /**
     * Reset the noise generator to ensure parameter changes take immediate effect
     * This is particularly useful for Q factor changes which might not be immediately applied
     */
    fun resetNoiseGenerator() {
        cwNoiseGenerator.reset()
        android.util.Log.d("MorseCodeGenerator", "Reset noise generator to ensure parameter changes take effect")
        
        // If noise is currently playing, briefly stop and restart it to apply changes
        if (isNoisePlayingContinuously) {
            val currentSettings = this.currentSettings
            stopTestNoise()
            Thread.sleep(50) // Brief pause
            startTestNoise(currentSettings)
        }
    }
    
    /**
     * Check if test noise is currently playing
     */
    fun isTestNoiseActive(): Boolean = isNoisePlayingContinuously

} 