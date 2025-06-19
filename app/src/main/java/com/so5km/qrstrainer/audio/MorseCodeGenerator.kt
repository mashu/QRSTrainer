package com.so5km.qrstrainer.audio

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.so5km.qrstrainer.data.MorseCode
import kotlin.math.*

/**
 * Generates and plays Morse code audio
 */
class MorseCodeGenerator {
    
    companion object {
        private const val SAMPLE_RATE = 44100
        private const val TONE_FREQUENCY = 600.0 // Hz
        private const val FARNSWORTH_RATIO = 0.6 // Ratio for character spacing in Farnsworth timing
    }
    
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null
    private var isPlaying = false
    private var shouldStop = false
    
    /**
     * Generate a sequence of characters and play as Morse code
     */
    fun playSequence(sequence: String, wpm: Int, repeatCount: Int = 1, onComplete: (() -> Unit)? = null) {
        // Stop any current playback before starting new one
        stop()
        
        isPlaying = true
        shouldStop = false
        
        playbackThread = Thread {
            try {
                for (i in 0 until repeatCount) {
                    if (shouldStop) break
                    
                    playSequenceOnce(sequence, wpm)
                    
                    if (i < repeatCount - 1 && !shouldStop) {
                        Thread.sleep(calculateCharacterSpacing(wpm).toLong())
                    }
                }
            } catch (e: InterruptedException) {
                // Thread was interrupted, which is expected when stopping
            } catch (e: Exception) {
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
        
        // Stop current audio track
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        
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
                playPattern(pattern, dotDuration, dashDuration, symbolSpacing)
                
                // Add character spacing (except after last character)
                if (i < sequence.length - 1 && !shouldStop) {
                    if (char == ' ') {
                        safeSleep(wordSpacing.toLong())
                    } else {
                        safeSleep(charSpacing.toLong())
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
                '.' -> playTone(dotDuration)
                '-' -> playTone(dashDuration)
            }
            
            // Add symbol spacing (except after last symbol)
            if (i < pattern.length - 1 && !shouldStop) {
                safeSleep(symbolSpacing.toLong())
            }
        }
    }
    
    /**
     * Play a tone for the specified duration
     */
    private fun playTone(durationMs: Int) {
        if (shouldStop) return
        
        val samples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val buffer = ShortArray(samples)
        
        // Generate sine wave
        for (i in buffer.indices) {
            val sample = sin(2.0 * PI * i * TONE_FREQUENCY / SAMPLE_RATE)
            buffer[i] = (sample * Short.MAX_VALUE * 0.5).toInt().toShort()
        }
        
        // Apply envelope to avoid clicks
        applyEnvelope(buffer)
        
        if (shouldStop) return
        
        // Create and play audio track
        val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buffer.size * 2,
            AudioTrack.MODE_STATIC
        )
        
        this.audioTrack = audioTrack
        
        audioTrack.write(buffer, 0, buffer.size)
        audioTrack.play()
        
        // Wait for playback to complete, but check for stop signal
        safeSleep(durationMs.toLong())
        
        audioTrack.stop()
        audioTrack.release()
        this.audioTrack = null
    }
    
    /**
     * Safe sleep that can be interrupted
     */
    private fun safeSleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            shouldStop = true
        }
    }
    
    /**
     * Apply envelope to prevent audio clicks
     */
    private fun applyEnvelope(buffer: ShortArray) {
        val rampSamples = minOf(buffer.size / 10, SAMPLE_RATE / 100) // 10ms ramp or 10% of buffer
        
        // Fade in
        for (i in 0 until rampSamples) {
            val factor = i.toDouble() / rampSamples
            buffer[i] = (buffer[i] * factor).toInt().toShort()
        }
        
        // Fade out
        for (i in 0 until rampSamples) {
            val idx = buffer.size - 1 - i
            val factor = i.toDouble() / rampSamples
            buffer[idx] = (buffer[idx] * factor).toInt().toShort()
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