package com.so5km.qrstrainer.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.so5km.qrstrainer.data.MorseCode
import kotlin.math.*

/**
 * Generates and plays Morse code audio using efficient streaming AudioTrack
 */
class MorseCodeGenerator(private val context: Context) {
    
    companion object {
        private const val SAMPLE_RATE = 44100
        private const val TONE_FREQUENCY = 600.0 // Hz - proper CW tone
        private const val FARNSWORTH_RATIO = 0.6 // Ratio for character spacing in Farnsworth timing
    }
    
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    private var isPlaying = false
    @Volatile
    private var shouldStop = false
    
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
        wpm: Int, 
        repeatCount: Int = 1, 
        repeatSpacingMs: Int = 2000,
        onComplete: (() -> Unit)? = null
    ) {
        // Stop any current playback before starting new one
        stop()
        
        isPlaying = true
        shouldStop = false
        
        playbackThread = Thread {
            try {
                android.util.Log.d("MorseCodeGenerator", "Starting playback of sequence: '$sequence' at ${wpm}wpm")
                
                audioTrack?.play()
                
                for (i in 0 until repeatCount) {
                    if (shouldStop) break
                    
                    playSequenceOnce(sequence, wpm)
                    
                    if (i < repeatCount - 1 && !shouldStop) {
                        playSilence(repeatSpacingMs)
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
                onComplete?.invoke()
            }
        }
        
        playbackThread?.start()
    }
    
    /**
     * Stop current playback
     */
    fun stop() {
        shouldStop = true
        
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
    }
    
    /**
     * Check if audio is currently playing
     */
    fun isPlaying(): Boolean = isPlaying
    
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
    private fun playSequenceOnce(sequence: String, wpm: Int) {
        val dotDuration = calculateDotDuration(wpm)
        val dashDuration = dotDuration * 3
        val symbolSpacing = dotDuration
        val charSpacing = calculateCharacterSpacing(wpm)
        val wordSpacing = calculateWordSpacing(wpm)
        
        for (i in sequence.indices) {
            if (shouldStop) break
            
            val char = sequence[i]
            val pattern = MorseCode.getPattern(char)
            
            if (pattern != null) {
                android.util.Log.d("MorseCodeGenerator", "Playing character '$char' with pattern '$pattern'")
                playPattern(pattern, dotDuration, dashDuration, symbolSpacing)
                
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
    private fun playPattern(pattern: String, dotDuration: Int, dashDuration: Int, symbolSpacing: Int) {
        for (i in pattern.indices) {
            if (shouldStop) break
            
            when (pattern[i]) {
                '.' -> playToneWithEnvelope(dotDuration)
                '-' -> playToneWithEnvelope(dashDuration)
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
    private fun playToneWithEnvelope(durationMs: Int) {
        if (shouldStop || audioTrack == null) return
        
        val samples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val buffer = ShortArray(samples)
        
        // Generate sine wave with envelope
        val envelopeLength = minOf(samples / 10, SAMPLE_RATE / 100) // 10ms envelope
        
        for (i in buffer.indices) {
            val angle = 2.0 * PI * i * TONE_FREQUENCY / SAMPLE_RATE
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
        if (shouldStop || audioTrack == null) return
        
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
     * Calculate character spacing (Farnsworth timing)
     */
    private fun calculateCharacterSpacing(wpm: Int): Int {
        val baseDuration = calculateDotDuration(wpm) * 3 // Normal character spacing is 3 dots
        return (baseDuration / FARNSWORTH_RATIO).toInt()
    }
    
    /**
     * Calculate word spacing
     */
    private fun calculateWordSpacing(wpm: Int): Int {
        val baseDuration = calculateDotDuration(wpm) * 7 // Normal word spacing is 7 dots
        return (baseDuration / FARNSWORTH_RATIO).toInt()
    }
} 