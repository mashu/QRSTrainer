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
 */
class MorseCodeGenerator(private val context: Context) {
    
    companion object {
        private const val SAMPLE_RATE = 44100
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
                
                audioTrack?.play()
                
                for (i in 0 until settings.repeatCount) {
                    if (shouldStop) break
                    
                    playSequenceOnce(sequence, settings)
                    
                    if (i < settings.repeatCount - 1 && !shouldStop) {
                        val spacing = settings.repeatSpacingMs + settings.groupSpacingMs
                        playSilence(spacing)
                    }
                }
                
                audioTrack?.stop()
                
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
     * Play a sequence once
     */
    private fun playSequenceOnce(sequence: String, settings: TrainingSettings) {
        val dotDuration = calculateDotDuration(settings.speedWpm)
        val dashDuration = dotDuration * 3
        val symbolSpacing = dotDuration
        val charSpacing = calculateCharacterSpacing(settings)
        val wordSpacing = calculateWordSpacing(settings)
        
        for (i in sequence.indices) {
            if (shouldStop) break
            
            // Handle pause
            while (shouldPause && !shouldStop) {
                Thread.sleep(50)
            }
            
            val char = sequence[i]
            val pattern = MorseCode.getPattern(char)
            
            if (pattern != null) {
                android.util.Log.d("MorseCodeGenerator", "Playing character '$char' with pattern '$pattern'")
                playPattern(pattern, dotDuration, dashDuration, symbolSpacing, settings.toneFrequencyHz)
                
                // Add character spacing (except after last character)
                if (i < sequence.length - 1 && !shouldStop) {
                    if (char == ' ') {
                        playSilence(wordSpacing)
                    } else {
                        playSilence(charSpacing)
                    }
                }
            }
        }
    }
    
    /**
     * Play a single Morse pattern (e.g., ".-")
     */
    private fun playPattern(pattern: String, dotDuration: Int, dashDuration: Int, symbolSpacing: Int, toneFrequency: Int) {
        for (i in pattern.indices) {
            if (shouldStop) break
            
            // Handle pause
            while (shouldPause && !shouldStop) {
                Thread.sleep(50)
            }
            
            when (pattern[i]) {
                '.' -> playToneWithEnvelope(dotDuration, toneFrequency)
                '-' -> playToneWithEnvelope(dashDuration, toneFrequency)
            }
            
            // Add symbol spacing (except after last symbol)
            if (i < pattern.length - 1 && !shouldStop) {
                playSilence(symbolSpacing)
            }
        }
    }
    
    /**
     * Play a tone with proper envelope to prevent clicks
     */
    private fun playToneWithEnvelope(durationMs: Int, toneFrequency: Int) {
        if (shouldStop || audioTrack == null) return
        
        val samples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val buffer = ShortArray(samples)
        
        // Generate sine wave with envelope
        val envelopeLength = minOf(samples / 10, SAMPLE_RATE / 100) // 10ms envelope
        
        for (i in buffer.indices) {
            val angle = 2.0 * PI * i * toneFrequency / SAMPLE_RATE
            var amplitude = sin(angle) * 0.7 // 70% volume
            
            // Apply envelope
            when {
                i < envelopeLength -> {
                    // Fade in
                    amplitude *= (i.toDouble() / envelopeLength)
                }
                i >= samples - envelopeLength -> {
                    // Fade out
                    val fadeIndex = samples - 1 - i
                    amplitude *= (fadeIndex.toDouble() / envelopeLength)
                }
            }
            
            buffer[i] = (amplitude * Short.MAX_VALUE).toInt().toShort()
        }
        
        // Write to AudioTrack in streaming mode
        writeToAudioTrack(buffer)
    }
    
    /**
     * Play silence for the specified duration
     */
    private fun playSilence(durationMs: Int) {
        if (shouldStop || audioTrack == null || durationMs <= 0) return
        
        val samples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val buffer = ShortArray(samples) // All zeros = silence
        
        writeToAudioTrack(buffer)
    }
    
    /**
     * Write buffer to AudioTrack in streaming mode
     */
    private fun writeToAudioTrack(buffer: ShortArray) {
        if (shouldStop || audioTrack == null) return
        
        try {
            var offset = 0
            while (offset < buffer.size && !shouldStop) {
                // Handle pause during writing
                while (shouldPause && !shouldStop) {
                    Thread.sleep(50)
                }
                
                if (shouldStop) break
                
                val bytesWritten = audioTrack!!.write(buffer, offset, buffer.size - offset)
                if (bytesWritten > 0) {
                    offset += bytesWritten
                } else {
                    // If write fails, wait a bit and try again
                    Thread.sleep(1)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MorseCodeGenerator", "Error writing to AudioTrack: ${e.message}")
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
} 