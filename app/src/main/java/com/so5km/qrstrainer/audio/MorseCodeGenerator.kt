package com.so5km.qrstrainer.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.so5km.qrstrainer.AppState
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
        private const val TAG = "MorseCodeGenerator"
    }
    
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    @Volatile
    private var shouldStop = false
    @Volatile
    private var shouldPause = false
    
    // Background noise management
    private var noiseAudioTrack: AudioTrack? = null
    private var noiseThread: Thread? = null
    @Volatile
    private var shouldStopNoise = false
    
    // Professional CW noise generator
    private val cwNoiseGenerator = CWNoiseGenerator(SAMPLE_RATE)
    
    // Current settings
    private var currentSettings: TrainingSettings = TrainingSettings()
    
    // Headphone keep-alive tone management
    private var keepAliveAudioTrack: AudioTrack? = null
    private var keepAliveThread: Thread? = null
    @Volatile
    private var shouldStopKeepAlive = false
    
    init {
        initializeAudioTrack()
        initializeNoiseAudioTrack()
        initializeKeepAliveAudioTrack()
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
            
            android.util.Log.d(TAG, "AudioTrack initialized in streaming mode, buffer size: $bufferSize")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to initialize AudioTrack: ${e.message}")
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
            
            android.util.Log.d(TAG, "Noise AudioTrack initialized, buffer size: $bufferSize")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to initialize noise AudioTrack: ${e.message}")
        }
    }
    
    private fun initializeKeepAliveAudioTrack() {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            
            keepAliveAudioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
            
            android.util.Log.d(TAG, "Keep-alive AudioTrack initialized, buffer size: $bufferSize")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to initialize keep-alive AudioTrack: ${e.message}")
        }
    }
    
    /**
     * Generate a sequence of characters and play as Morse code
     * Now generates the entire sequence as one continuous audio buffer
     */
    fun playSequence(
        sequence: String, 
        settings: TrainingSettings,
        source: String = "default",
        startNoise: Boolean = true,
        onComplete: (() -> Unit)? = null
    ) {
        // Stop any current playback before starting new one
        stopSequence()
        
        // Always update current settings and audio parameters
        currentSettings = settings
        updateAudioParameters()
        
        shouldStop = false
        shouldPause = false
        
        // Register the sequence with SharedAudioState
        SharedAudioState.startPlayback(source, sequence)
        
        // Start continuous background noise ONLY if checkbox is enabled AND noise level > 0
        // AND startNoise parameter is true (for session-based noise control)
        if (startNoise && settings.filterRingingEnabled && settings.backgroundNoiseLevel > 0 && !AppState.isNoiseRunning.value!!) {
            startContinuousNoise()
        }
        
        playbackThread = Thread {
            try {
                android.util.Log.d(TAG, "Starting playback of sequence: '$sequence' at ${settings.speedWpm}wpm, tone: ${settings.toneFrequencyHz}Hz")
                
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
                android.util.Log.d(TAG, "Playback interrupted")
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error during playback: ${e.message}")
                e.printStackTrace()
            } finally {
                // Update shared state
                SharedAudioState.stopPlayback()
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
        
        android.util.Log.d(TAG, "Generating audio for sequence: '$sequence' with settings: speedWpm=${settings.speedWpm}, tone=${settings.toneFrequencyHz}Hz")
        
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
        
        android.util.Log.d(TAG, "Generated audio buffer of ${audioBuffer.size} samples for sequence '$sequence'")
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
                android.util.Log.d(TAG, "Generating character '$char' with pattern '$pattern'")
                
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
                android.util.Log.e(TAG, "Error writing to AudioTrack: ${e.message}")
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
     * Check if audio is currently playing
     */
    fun isPlaying(): Boolean {
        return SharedAudioState.isPlaying.value == true
    }
    
    /**
     * Stop current playback
     */
    fun stop() {
        shouldStop = true
        playbackThread?.let {
            if (it.isAlive) {
                try {
                    audioTrack?.pause()
                    audioTrack?.flush()
                    audioTrack?.stop()
                    it.interrupt()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error stopping playback: ${e.message}")
                }
            }
        }
        SharedAudioState.stopPlayback()
    }
    
    /**
     * Pause playback
     */
    fun pause() {
        shouldPause = true
        audioTrack?.pause()
    }
    
    /**
     * Resume playback
     */
    fun resume() {
        shouldPause = false
        audioTrack?.play()
    }
    
    /**
     * Release resources
     */
    fun release() {
        stop()
        stopContinuousNoise()
        stopHeadphoneKeepAlive()
        
        try {
            audioTrack?.release()
            noiseAudioTrack?.release()
            keepAliveAudioTrack?.release()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error releasing resources: ${e.message}")
        }
        
        audioTrack = null
        noiseAudioTrack = null
        keepAliveAudioTrack = null
    }
    
    /**
     * Start continuous background noise
     */
    fun startContinuousNoise() {
        if (AppState.isNoiseRunning.value == true) {
            return
        }
        
        shouldStopNoise = false
        AppState.setNoiseRunning(true)
        
        noiseThread = Thread {
            try {
                val bufferSize = 4096
                val noiseBuffer = ShortArray(bufferSize)
                
                noiseAudioTrack?.play()
                
                while (!shouldStopNoise) {
                    // Generate noise using the CW noise generator
                    cwNoiseGenerator.generateRealisticCWNoise(noiseBuffer, currentSettings)
                    
                    // Write to audio track
                    if (!shouldStopNoise) {
                        noiseAudioTrack?.write(noiseBuffer, 0, bufferSize)
                    }
                }
                
                noiseAudioTrack?.pause()
                noiseAudioTrack?.flush()
                noiseAudioTrack?.stop()
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in noise thread: ${e.message}")
            }
        }
        
        noiseThread?.start()
    }
    
    /**
     * Stop continuous background noise
     */
    fun stopContinuousNoise() {
        shouldStopNoise = true
        noiseThread?.let {
            if (it.isAlive) {
                try {
                    it.interrupt()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error stopping noise thread: ${e.message}")
                }
            }
        }
        
        try {
            noiseAudioTrack?.pause()
            noiseAudioTrack?.flush()
            noiseAudioTrack?.stop()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error stopping noise audio track: ${e.message}")
        }
        
        AppState.setNoiseRunning(false)
    }
    
    /**
     * Start headphone keep-alive tone
     * This plays an extremely quiet tone to prevent Bluetooth headphones
     * from disconnecting due to inactivity
     */
    fun startHeadphoneKeepAlive() {
        if (keepAliveThread != null && keepAliveThread!!.isAlive) {
            return
        }
        
        shouldStopKeepAlive = false
        
        keepAliveThread = Thread {
            try {
                val bufferSize = 8192
                val keepAliveBuffer = ShortArray(bufferSize)
                val frequency = 19000.0 // Very high frequency, barely audible
                val amplitude = 0.001 // Extremely quiet
                
                keepAliveAudioTrack?.play()
                
                while (!shouldStopKeepAlive) {
                    // Generate a very quiet high-frequency tone
                    for (i in 0 until bufferSize) {
                        val angle = 2.0 * PI * i * frequency / SAMPLE_RATE
                        keepAliveBuffer[i] = (sin(angle) * amplitude * Short.MAX_VALUE).toInt().toShort()
                    }
                    
                    // Write to audio track
                    if (!shouldStopKeepAlive) {
                        keepAliveAudioTrack?.write(keepAliveBuffer, 0, bufferSize)
                    }
                }
                
                keepAliveAudioTrack?.pause()
                keepAliveAudioTrack?.flush()
                keepAliveAudioTrack?.stop()
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in keep-alive thread: ${e.message}")
            }
        }
        
        keepAliveThread?.start()
    }
    
    /**
     * Stop headphone keep-alive tone
     */
    fun stopHeadphoneKeepAlive() {
        shouldStopKeepAlive = true
        keepAliveThread?.let {
            if (it.isAlive) {
                try {
                    it.interrupt()
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error stopping keep-alive thread: ${e.message}")
                }
            }
        }
        
        try {
            keepAliveAudioTrack?.pause()
            keepAliveAudioTrack?.flush()
            keepAliveAudioTrack?.stop()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error stopping keep-alive audio track: ${e.message}")
        }
    }
    
    /**
     * Stop current sequence playback
     */
    fun stopSequence() {
        stop()
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
        if (AppState.isNoiseRunning.value == true) {
            stopTestNoise()
        }
        
        currentSettings = settings
        android.util.Log.d(TAG, "Starting CW test noise - Level: ${(settings.backgroundNoiseLevel * 100).toInt()}%, LFO1: ${settings.lfo1FrequencyHz}Hz, LFO2: ${settings.lfo2FrequencyHz}Hz")
        startContinuousNoise()
    }
    
    /**
     * Stop continuous test noise
     */
    fun stopTestNoise() {
        android.util.Log.d(TAG, "Stopping CW test noise")
        stopContinuousNoise()
    }
    
    /**
     * Update settings for continuous noise testing in real-time
     */
    fun updateNoiseSettings(settings: TrainingSettings) {
        if (AppState.isNoiseRunning.value == true) {
            currentSettings = settings
            android.util.Log.d(TAG, "Updated CW test noise settings - Level: ${(settings.backgroundNoiseLevel * 100).toInt()}%, Q: ${settings.filterQFactor}, LFO1: ${settings.lfo1FrequencyHz}Hz, LFO2: ${settings.lfo2FrequencyHz}Hz")
        }
    }
    
    /**
     * Reset the noise generator to ensure parameter changes take immediate effect
     * This is particularly useful for Q factor changes which might not be immediately applied
     */
    fun resetNoiseGenerator() {
        cwNoiseGenerator.reset()
        android.util.Log.d(TAG, "Reset noise generator to ensure parameter changes take effect")
        
        // If noise is currently playing, briefly stop and restart it to apply changes
        if (AppState.isNoiseRunning.value == true) {
            val currentSettings = this.currentSettings
            stopTestNoise()
            Thread.sleep(50) // Brief pause
            startTestNoise(currentSettings)
        }
    }
    
    /**
     * Check if test noise is currently playing
     */
    fun isTestNoiseActive(): Boolean = AppState.isNoiseRunning.value == true
    
    /**
     * Play a single character (useful for testing tone settings)
     */
    fun playSingleCharacter(character: Char, settings: TrainingSettings? = null) {
        // Use provided settings or get from AppState
        val actualSettings = settings ?: AppState.getSettings(context)
        
        // Always stop any existing playback first
        stop()
        
        // Generate the sequence for a single character
        val morsePattern = MorseCode.getPattern(character.uppercaseChar()) ?: ""
        
        // Play the sequence
        playMorseSequence(morsePattern, actualSettings)
    }

    /**
     * Play a Morse code sequence (dots and dashes)
     */
    fun playMorseSequence(sequence: String, settings: TrainingSettings? = null) {
        // Use provided settings or get from AppState
        currentSettings = settings ?: AppState.getSettings(context)
        
        // Always update audio parameters from current settings
        updateAudioParameters()
        
        // Stop any existing playback
        stop()
        
        // Register the sequence with SharedAudioState
        SharedAudioState.registerSequence("morse_generator", sequence)
        
        // Start the playback thread
        playbackThread = Thread {
            try {
                val dotDuration = calculateDotDuration(currentSettings.speedWpm)
                val dashDuration = dotDuration * 3
                val symbolSpacing = dotDuration
                val charSpacing = calculateCharacterSpacing(currentSettings)
                
                // Start audio track
                audioTrack?.play()
                
                // Signal that playback has started
                AppState.setAudioPlaying(true)
                SharedAudioState.startPlayback("morse_generator", sequence)
                
                // Generate audio for each symbol in the sequence
                for (i in sequence.indices) {
                    if (shouldStop) {
                        break
                    }
                    
                    val symbol = sequence[i]
                    
                    when (symbol) {
                        '.' -> {
                            generateDotTone(dotDuration)
                            Thread.sleep(symbolSpacing.toLong())
                        }
                        '-' -> {
                            generateDashTone(dashDuration)
                            Thread.sleep(symbolSpacing.toLong())
                        }
                        ' ' -> {
                            Thread.sleep(charSpacing.toLong())
                        }
                        '/' -> {
                            Thread.sleep(calculateWordSpacing(currentSettings).toLong())
                        }
                    }
                }
                
                // Signal that playback has finished
                AppState.setAudioPlaying(false)
                SharedAudioState.stopPlayback()
                
                // Stop audio track
                audioTrack?.stop()
                audioTrack?.flush()
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in playback thread: ${e.message}")
            }
        }
        
        playbackThread?.start()
    }
    
    /**
     * Generate a dot tone with the current settings
     */
    private fun generateDotTone(durationMs: Int) {
        val bufferSize = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val buffer = ShortArray(bufferSize)
        
        // Generate sine wave with envelope
        for (i in 0 until bufferSize) {
            val time = i.toDouble() / SAMPLE_RATE
            val angle = 2.0 * PI * toneFrequency * time
            
            // Apply envelope
            val envelopeFactor = calculateEnvelope(i, bufferSize)
            
            // Generate sample
            buffer[i] = (sin(angle) * envelopeFactor * volume * Short.MAX_VALUE).toInt().toShort()
        }
        
        // Write to audio track
        audioTrack?.write(buffer, 0, bufferSize)
    }
    
    /**
     * Generate a dash tone with the current settings
     */
    private fun generateDashTone(durationMs: Int) {
        val bufferSize = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val buffer = ShortArray(bufferSize)
        
        // Generate sine wave with envelope
        for (i in 0 until bufferSize) {
            val time = i.toDouble() / SAMPLE_RATE
            val angle = 2.0 * PI * toneFrequency * time
            
            // Apply envelope
            val envelopeFactor = calculateEnvelope(i, bufferSize)
            
            // Generate sample
            buffer[i] = (sin(angle) * envelopeFactor * volume * Short.MAX_VALUE).toInt().toShort()
        }
        
        // Write to audio track
        audioTrack?.write(buffer, 0, bufferSize)
    }
    
    /**
     * Calculate envelope factor based on position in buffer
     */
    private fun calculateEnvelope(position: Int, bufferSize: Int): Double {
        val envelopeSamples = (envelopeMs * SAMPLE_RATE / 1000.0).toInt().coerceAtLeast(1)
        
        return when {
            position < envelopeSamples -> {
                // Attack phase
                when (keyingStyle) {
                    0 -> position.toDouble() / envelopeSamples // Hard keying - linear
                    1 -> 0.5 * (1.0 - cos(PI * position / envelopeSamples)) // Soft keying - cosine
                    2 -> sin(PI * position / (2 * envelopeSamples)) // Smooth keying - sine
                    else -> position.toDouble() / envelopeSamples
                }
            }
            position > bufferSize - envelopeSamples -> {
                // Release phase
                val releasePosition = bufferSize - position
                when (keyingStyle) {
                    0 -> releasePosition.toDouble() / envelopeSamples // Hard keying - linear
                    1 -> 0.5 * (1.0 - cos(PI * releasePosition / envelopeSamples)) // Soft keying - cosine
                    2 -> sin(PI * releasePosition / (2 * envelopeSamples)) // Smooth keying - sine
                    else -> releasePosition.toDouble() / envelopeSamples
                }
            }
            else -> 1.0 // Sustain phase
        }
    }
    
    // Audio parameters
    private var toneFrequency = 600.0
    private var volume = 0.7f
    private var envelopeMs = 5
    private var keyingStyle = 1 // 0=Hard, 1=Soft, 2=Smooth
    
    /**
     * Update audio parameters from current settings
     * This ensures that any changes to settings are applied immediately
     */
    private fun updateAudioParameters() {
        // Update tone frequency and volume
        toneFrequency = currentSettings.toneFrequencyHz.toDouble()
        volume = currentSettings.appVolumeLevel
        
        // Update envelope parameters
        envelopeMs = currentSettings.audioEnvelopeMs
        keyingStyle = currentSettings.keyingStyle
        
        // Update filter parameters for noise generator
        cwNoiseGenerator.updateFilterParameters(
            centerFreq = currentSettings.toneFrequencyHz.toDouble(),
            qFactor = currentSettings.filterQFactor.toDouble(),
            bandwidth = currentSettings.filterBandwidthHz.toDouble(),
            warmth = currentSettings.warmth.toDouble(),
            atmosphericIntensity = currentSettings.atmosphericIntensity.toDouble()
        )
        
        // Log parameter updates
        android.util.Log.d(TAG, "Updated audio parameters: freq=$toneFrequency, vol=$volume, env=$envelopeMs, keying=$keyingStyle")
    }
} 