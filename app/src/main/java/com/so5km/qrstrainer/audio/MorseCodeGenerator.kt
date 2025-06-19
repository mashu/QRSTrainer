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
    
    /**
     * Generate a sequence of characters and play as Morse code
     */
    fun playSequence(sequence: String, wpm: Int, repeatCount: Int = 1, onComplete: (() -> Unit)? = null) {
        Thread {
            try {
                for (i in 0 until repeatCount) {
                    playSequenceOnce(sequence, wpm)
                    if (i < repeatCount - 1) {
                        Thread.sleep(calculateCharacterSpacing(wpm).toLong())
                    }
                }
                onComplete?.invoke()
            } catch (e: Exception) {
                e.printStackTrace()
                onComplete?.invoke()
            }
        }.start()
    }
    
    /**
     * Stop current playback
     */
    fun stop() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
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
            val char = sequence[i]
            val pattern = MorseCode.getPattern(char)
            
            if (pattern != null) {
                playPattern(pattern, dotDuration, dashDuration, symbolSpacing)
                
                // Add character spacing (except after last character)
                if (i < sequence.length - 1) {
                    if (char == ' ') {
                        Thread.sleep(wordSpacing.toLong())
                    } else {
                        Thread.sleep(charSpacing.toLong())
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
            when (pattern[i]) {
                '.' -> playTone(dotDuration)
                '-' -> playTone(dashDuration)
            }
            
            // Add symbol spacing (except after last symbol)
            if (i < pattern.length - 1) {
                Thread.sleep(symbolSpacing.toLong())
            }
        }
    }
    
    /**
     * Play a tone for the specified duration
     */
    private fun playTone(durationMs: Int) {
        val samples = (SAMPLE_RATE * durationMs / 1000.0).toInt()
        val buffer = ShortArray(samples)
        
        // Generate sine wave
        for (i in buffer.indices) {
            val sample = sin(2.0 * PI * i * TONE_FREQUENCY / SAMPLE_RATE)
            buffer[i] = (sample * Short.MAX_VALUE * 0.5).toInt().toShort()
        }
        
        // Apply envelope to avoid clicks
        applyEnvelope(buffer)
        
        // Create and play audio track
        val audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            buffer.size * 2,
            AudioTrack.MODE_STATIC
        )
        
        audioTrack.write(buffer, 0, buffer.size)
        audioTrack.play()
        
        // Wait for playback to complete
        Thread.sleep(durationMs.toLong())
        
        audioTrack.stop()
        audioTrack.release()
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