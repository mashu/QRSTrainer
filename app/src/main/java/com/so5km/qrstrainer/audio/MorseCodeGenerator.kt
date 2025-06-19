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
        private const val ENVELOPE_MS = 5 // 5ms envelope for smooth transitions
    }
    
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    private var isPlaying = false
    private var isPaused = false
    @Volatile
    private var shouldStop = false
    @Volatile
    private var shouldPause = false
    
    // Current settings
    private var currentSettings: TrainingSettings = TrainingSettings()
    
    init {
        initializeAudioTrack()
    }
    
    private fun initializeAudioTrack() {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ) * 4 // Larger buffer for smooth playback
            
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
        
        playbackThread = Thread {
            try {
                android.util.Log.d("MorseCodeGenerator", "Starting playback of sequence: '$sequence' at ${settings.speedWpm}wpm, tone: ${settings.toneFrequencyHz}Hz")
                
                // Generate the entire sequence as one continuous audio buffer
                val audioData = generateSequenceAudio(sequence, settings)
                
                if (audioData.isNotEmpty() && !shouldStop) {
                    audioTrack?.play()
                    playAudioBuffer(audioData)
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
        val envelopeSamples = maxOf(1, minOf(samples / 10, SAMPLE_RATE * ENVELOPE_MS / 1000))
        
        for (i in startIndex until endIndex) {
            val sampleIndex = i - startIndex
            val angle = 2.0 * PI * sampleIndex * toneFrequency / SAMPLE_RATE
            var amplitude = sin(angle) * 0.7 // 70% volume
            
            // Apply CW filter effects if enabled
            if (currentSettings.filterRingingEnabled) {
                amplitude = applyCWFilterEffects(amplitude, sampleIndex, toneFrequency)
            }
            
            // Add background noise
            if (currentSettings.backgroundNoiseLevel > 0) {
                val noise = (Math.random() - 0.5) * 2.0 * currentSettings.backgroundNoiseLevel * 0.1
                amplitude += noise
            }
            
            // Apply smooth envelope
            when {
                sampleIndex < envelopeSamples -> {
                    // Smooth fade in using cosine curve
                    val fadeRatio = sampleIndex.toDouble() / envelopeSamples
                    amplitude *= 0.5 * (1.0 - cos(PI * fadeRatio))
                }
                sampleIndex >= samples - envelopeSamples -> {
                    // Smooth fade out using cosine curve
                    val fadeIndex = samples - 1 - sampleIndex
                    val fadeRatio = fadeIndex.toDouble() / envelopeSamples
                    amplitude *= 0.5 * (1.0 - cos(PI * fadeRatio))
                }
            }
            
            buffer[i] = (amplitude * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        
        return endIndex
    }
    
    /**
     * Add silence to the buffer
     */
    private fun addSilenceToBuffer(
        buffer: ShortArray,
        startIndex: Int,
        durationMs: Int
    ): Int {
        if (durationMs <= 0) return startIndex
        
        val samples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val endIndex = minOf(startIndex + samples, buffer.size)
        
        // Fill with zeros (silence)
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
        
        val envelopeSamples = SAMPLE_RATE * ENVELOPE_MS * 2 / 1000 // 10ms envelope
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
    private fun playAudioBuffer(buffer: ShortArray) {
        var offset = 0
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
     */
    private fun applyCWFilterEffects(amplitude: Double, sampleIndex: Int, toneFrequency: Int): Double {
        var filteredAmplitude = amplitude
        
        // Simulate bandpass filter effect
        val centerFreq = currentSettings.toneFrequencyHz.toDouble()
        val bandwidth = currentSettings.filterBandwidthHz.toDouble()
        val qFactor = currentSettings.filterQFactor.toDouble()
        
        // Calculate frequency response (simplified bandpass)
        val freqDiff = abs(toneFrequency - centerFreq)
        val normalizedFreq = freqDiff / (bandwidth / 2.0)
        val filterResponse = 1.0 / sqrt(1.0 + (qFactor * normalizedFreq).pow(2))
        
        // Apply filter response
        filteredAmplitude *= filterResponse
        
        // Add ringing effect for high Q filters
        if (qFactor > 5.0) {
            val ringingFreq = centerFreq * (1.0 + 0.1 / qFactor)  // Slight frequency shift for ringing
            val ringingPhase = 2.0 * PI * sampleIndex * ringingFreq / SAMPLE_RATE
            val ringingAmplitude = 0.05 * (qFactor / 20.0)  // Ringing amplitude proportional to Q
            val ringing = sin(ringingPhase) * ringingAmplitude * exp(-sampleIndex * 0.001)  // Decaying ringing
            filteredAmplitude += ringing
        }
        
        // Simulate filter group delay effects (phase distortion)
        if (qFactor > 10.0) {
            val phaseShift = (qFactor - 10.0) / 10.0 * PI / 4  // Up to 45 degree phase shift
            val phaseDistortion = sin(2.0 * PI * sampleIndex * toneFrequency / SAMPLE_RATE + phaseShift)
            filteredAmplitude = filteredAmplitude * 0.8 + phaseDistortion * 0.2 * (qFactor / 20.0)
        }
        
        return filteredAmplitude
    }
} 