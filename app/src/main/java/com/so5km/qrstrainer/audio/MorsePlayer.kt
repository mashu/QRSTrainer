package com.so5km.qrstrainer.audio

import com.so5km.qrstrainer.data.TrainingSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * High-level Morse code player that coordinates audio generation and playback
 */
class MorsePlayer(
    private val audioEngine: AudioEngine,
    private val signalGenerator: SignalGenerator,
    private val morseEncoder: MorseEncoder,
    private val noiseGenerator: NoiseGenerator? = null
) {
    
    private var isSequencePlaying = false
    private var completionListener: AudioCompletionListener? = null
    private var completionHandler: Handler = Handler(Looper.getMainLooper())
    private var completionRunnable: Runnable? = null
    
    fun setCompletionListener(listener: AudioCompletionListener?) {
        completionListener = listener
    }
    
    suspend fun playSequence(
        sequence: String,
        settings: TrainingSettings
    ) {
        // Prevent overlapping sequences
        if (isSequencePlaying) {
            android.util.Log.w("MorsePlayer", "Sequence already playing, ignoring new request")
            return
        }
        
        android.util.Log.d("MorsePlayer", "Playing sequence: $sequence with ${settings.numberOfRepeats} repeat(s)")
        val symbols = morseEncoder.encodeSequence(sequence, settings)
        android.util.Log.d("MorsePlayer", "Encoded ${symbols.size} symbols")
        
        if (symbols.isEmpty()) {
            android.util.Log.w("MorsePlayer", "No symbols to play")
            return
        }
        
        isSequencePlaying = true
        
        try {
            // Start streaming mode for smooth playback
            audioEngine.startStreaming()
            
            // Play the sequence the specified number of times
            for (repeatNum in 1..settings.numberOfRepeats) {
                if (!isSequencePlaying) {
                    android.util.Log.d("MorsePlayer", "Sequence cancelled during repeat $repeatNum")
                    break
                }
                
                android.util.Log.d("MorsePlayer", "Playing repeat $repeatNum of ${settings.numberOfRepeats}")
                
                // Generate and queue all audio data for this repeat
                generateAndQueueSequence(symbols, settings)
                
                // Add buffer silence at the end to ensure last character plays completely
                val endBufferMs = (settings.riseTimeMs * 40).toInt() // Use parametric buffer based on rise time
                val endBufferData = generateSilence(endBufferMs)
                audioEngine.queueAudio(endBufferData)
                
                // Wait for actual audio completion using callback
                suspendCancellableCoroutine<Unit> { continuation ->
                    audioEngine.setStreamCompletionCallback {
                        android.util.Log.d("MorsePlayer", "Audio completion callback received for repeat $repeatNum")
                        if (continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }
                }
                
                // Add delay between repeats (except after the last repeat)
                if (repeatNum < settings.numberOfRepeats && isSequencePlaying && settings.repeatDelayMs > 0) {
                    android.util.Log.d("MorsePlayer", "Adding repeat delay of ${settings.repeatDelayMs}ms")
                    
                    // Generate silence for the repeat delay
                    val silenceData = generateSilence(settings.repeatDelayMs.toInt())
                    audioEngine.queueAudio(silenceData)
                    
                    // Wait for delay completion using callback
                    suspendCancellableCoroutine<Unit> { continuation ->
                        audioEngine.setStreamCompletionCallback {
                            android.util.Log.d("MorsePlayer", "Repeat delay completion callback received")
                            if (continuation.isActive) {
                                continuation.resume(Unit)
                            }
                        }
                    }
                }
            }
            
        } finally {
            // Always stop streaming when done
            audioEngine.stopStreaming()
            isSequencePlaying = false
            android.util.Log.d("MorsePlayer", "Finished playing sequence")
            
            // Cancel any pending completion callbacks
            completionRunnable?.let { completionHandler.removeCallbacks(it) }
            audioEngine.setStreamCompletionCallback(null)
            
            // Notify completion
            completionListener?.onSequenceCompleted()
        }
    }
    
    private fun generateAndQueueSequence(
        symbols: List<MorseEncoder.MorseSymbol>,
        settings: TrainingSettings
    ) {
        android.util.Log.d("MorsePlayer", "Generating continuous audio stream for ${symbols.size} symbols")
        
        for ((index, symbol) in symbols.withIndex()) {
            // Check if sequence was cancelled
            if (!isSequencePlaying) {
                android.util.Log.d("MorsePlayer", "Sequence cancelled at symbol $index")
                break
            }
            
            android.util.Log.d("MorsePlayer", "Generating symbol $index: ${symbol.type} (${symbol.durationMs}ms)")
            
            when (symbol.type) {
                MorseEncoder.SymbolType.DIT,
                MorseEncoder.SymbolType.DAH -> {
                    // Generate tone
                    val audioData = generateTone(settings.frequency, symbol.durationMs, settings)
                    audioEngine.queueAudio(audioData)
                }
                MorseEncoder.SymbolType.ELEMENT_SPACE,
                MorseEncoder.SymbolType.CHARACTER_SPACE,
                MorseEncoder.SymbolType.WORD_SPACE -> {
                    // Generate silence
                    val audioData = generateSilence(symbol.durationMs)
                    audioEngine.queueAudio(audioData)
                }
            }
        }
        
        android.util.Log.d("MorsePlayer", "Finished generating audio stream")
    }
    
    private fun generateTone(
        frequency: Int,
        durationMs: Int,
        settings: TrainingSettings
    ): ShortArray {
        android.util.Log.d("MorsePlayer", "Generating tone: freq=$frequency, duration=$durationMs, volume=${settings.volume}")
        
        // Generate sine wave
        var signal = signalGenerator.generateSineWave(
            frequency,
            durationMs,
            settings.volume
        )
        android.util.Log.d("MorsePlayer", "Generated signal with ${signal.size} samples")
        
        // Apply envelope to prevent clicks
        signal = signalGenerator.applyEnvelope(
            signal,
            settings.riseTimeMs,
            settings.riseTimeMs
        )
        android.util.Log.d("MorsePlayer", "Applied envelope with ${settings.riseTimeMs}ms rise/fall time")
        
        // Convert to audio format
        val audioData = floatToShortArray(signal)
        android.util.Log.d("MorsePlayer", "Converted to ${audioData.size} audio samples")
        
        return audioData
    }
    
    private fun generateSilence(durationMs: Int): ShortArray {
        val numSamples = (AudioEngine.SAMPLE_RATE * durationMs / 1000.0).toInt()
        val audioData = ShortArray(numSamples) { 0 }
        android.util.Log.d("MorsePlayer", "Generated ${audioData.size} silence samples for ${durationMs}ms")
        return audioData
    }
    
    fun stopSequence() {
        android.util.Log.d("MorsePlayer", "Stopping sequence playback")
        isSequencePlaying = false
        
        // Cancel any pending completion callbacks
        completionRunnable?.let { completionHandler.removeCallbacks(it) }
        audioEngine.setStreamCompletionCallback(null)
        
        // Notify that playback was stopped
        completionListener?.onPlaybackStopped()
        
        // Note: AudioEngine streaming will be stopped by the playSequence method
    }
    
    private fun floatToShortArray(floatArray: FloatArray): ShortArray {
        return ShortArray(floatArray.size) { i ->
            (floatArray[i] * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
    }
}

