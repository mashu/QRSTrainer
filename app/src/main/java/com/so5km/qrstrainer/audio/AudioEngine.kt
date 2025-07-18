package com.so5km.qrstrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Core audio engine responsible for low-level audio playback with continuous streaming
 */
class AudioEngine {
    
    companion object {
        const val SAMPLE_RATE = 44100
        private const val TAG = "AudioEngine"
        private const val BUFFER_SIZE_MULTIPLIER = 4
    }
    
    private var morseAudioTrack: AudioTrack? = null
    private var noiseAudioTrack: AudioTrack? = null
    private var isNoiseRunning = false
    
    // Streaming support
    private val audioQueue = LinkedBlockingQueue<ShortArray>()
    private val isStreaming = AtomicBoolean(false)
    private val streamingMutex = Mutex()
    private var streamingThread: Thread? = null
    
    init {
        android.util.Log.d(TAG, "AudioEngine initializing...")
        initializeAudioTracks()
    }
    
    private fun initializeAudioTracks() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * BUFFER_SIZE_MULTIPLIER
        
        android.util.Log.d(TAG, "Min buffer size: $bufferSize")
        
        // Create morse code audio track
        morseAudioTrack = createAudioTrack(bufferSize)
        
        // Create noise audio track
        noiseAudioTrack = createAudioTrack(bufferSize)
        
        morseAudioTrack?.let { track ->
            android.util.Log.d(TAG, "Morse AudioTrack state: ${track.state}, playState: ${track.playState}")
        } ?: android.util.Log.e(TAG, "Morse AudioTrack is null after initialization!")
        
        noiseAudioTrack?.let { track ->
            android.util.Log.d(TAG, "Noise AudioTrack state: ${track.state}, playState: ${track.playState}")
        } ?: android.util.Log.e(TAG, "Noise AudioTrack is null after initialization!")
    }
    
    private fun createAudioTrack(bufferSize: Int): AudioTrack? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.util.Log.d(TAG, "Creating AudioTrack using new API")
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } else {
            android.util.Log.d(TAG, "Creating AudioTrack using legacy API")
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_MUSIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
        }
    }
    
    /**
     * Start continuous streaming mode for smooth playback
     */
    suspend fun startStreaming() {
        streamingMutex.withLock {
            if (isStreaming.get()) {
                android.util.Log.d(TAG, "Already streaming")
                return
            }
            
            morseAudioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    android.util.Log.d(TAG, "Starting streaming mode")
                    isStreaming.set(true)
                    
                    // Clear any existing audio data
                    audioQueue.clear()
                    
                    // Start the AudioTrack
                    track.play()
                    
                    // Start streaming thread
                    streamingThread = Thread {
                        streamAudio()
                    }.apply {
                        name = "AudioStreamingThread"
                        start()
                    }
                } else {
                    android.util.Log.e(TAG, "AudioTrack not initialized for streaming! State: ${track.state}")
                }
            } ?: android.util.Log.e(TAG, "AudioTrack is null for streaming!")
        }
    }
    
    /**
     * Stop streaming mode
     */
    suspend fun stopStreaming() {
        streamingMutex.withLock {
            if (!isStreaming.get()) {
                android.util.Log.d(TAG, "Already stopped streaming")
                return
            }
            
            android.util.Log.d(TAG, "Stopping streaming mode")
            isStreaming.set(false)
            
            // Wake up the streaming thread if it's waiting
            audioQueue.offer(ShortArray(0)) // Empty array as stop signal
            
            // Wait for streaming thread to finish
            streamingThread?.join(1000) // Wait max 1 second
            streamingThread = null
            
            // Stop the AudioTrack
            morseAudioTrack?.stop()
            
            // Clear remaining audio data
            audioQueue.clear()
            
            android.util.Log.d(TAG, "Streaming stopped")
        }
    }
    
    /**
     * Queue audio data for streaming playback
     */
    fun queueAudio(audioData: ShortArray) {
        if (isStreaming.get() && audioData.isNotEmpty()) {
            audioQueue.offer(audioData)
            android.util.Log.d(TAG, "Queued ${audioData.size} audio samples")
        }
    }
    
    /**
     * Streaming thread that continuously writes audio data
     */
    private fun streamAudio() {
        android.util.Log.d(TAG, "Audio streaming thread started")
        
        while (isStreaming.get()) {
            try {
                // Wait for audio data (blocking)
                val audioData = audioQueue.take()
                
                // Check if this is a stop signal
                if (audioData.isEmpty()) {
                    android.util.Log.d(TAG, "Received stop signal in streaming thread")
                    break
                }
                
                // Write to AudioTrack
                morseAudioTrack?.let { track ->
                    val written = track.write(audioData, 0, audioData.size)
                    android.util.Log.d(TAG, "Streamed ${written}/${audioData.size} samples")
                    
                    if (written < 0) {
                        android.util.Log.e(TAG, "Error writing to AudioTrack: $written")
                    }
                }
                
            } catch (e: InterruptedException) {
                android.util.Log.d(TAG, "Streaming thread interrupted")
                break
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error in streaming thread", e)
            }
        }
        
        android.util.Log.d(TAG, "Audio streaming thread finished")
    }
    
    /**
     * Legacy play method for backward compatibility
     */
    fun play(audioData: ShortArray) {
        if (isStreaming.get()) {
            // If streaming is active, queue the audio
            queueAudio(audioData)
        } else {
            // Legacy synchronous playback
            playLegacy(audioData)
        }
    }
    
    /**
     * Legacy synchronous playback method
     */
    private fun playLegacy(audioData: ShortArray) {
        morseAudioTrack?.let { track ->
            android.util.Log.d(TAG, "Legacy play called with ${audioData.size} samples")
            if (audioData.isEmpty()) {
                android.util.Log.e(TAG, "Audio data is empty!")
                return
            }
            
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                android.util.Log.d(TAG, "AudioTrack is initialized, starting playback")
                track.play()
                android.util.Log.d(TAG, "AudioTrack.play() called, playState: ${track.playState}")
                
                val written = track.write(audioData, 0, audioData.size)
                android.util.Log.d(TAG, "Written $written samples to AudioTrack")
                
                // Wait for the audio to finish playing
                val durationMs = (audioData.size * 1000L) / SAMPLE_RATE
                android.util.Log.d(TAG, "Waiting ${durationMs}ms for audio to complete")
                Thread.sleep(durationMs)
                
                track.stop()
                android.util.Log.d(TAG, "AudioTrack stopped")
            } else {
                android.util.Log.e(TAG, "AudioTrack not initialized! State: ${track.state}")
            }
        } ?: android.util.Log.e(TAG, "AudioTrack is null!")
    }
    
    fun startNoiseStream(audioGenerator: () -> ShortArray?) {
        noiseAudioTrack?.let { track ->
            if (track.state == AudioTrack.STATE_INITIALIZED && !isNoiseRunning) {
                android.util.Log.d(TAG, "Starting noise stream")
                isNoiseRunning = true
                track.play()
                
                // Run noise in a separate thread
                Thread {
                    while (isNoiseRunning) {
                        val data = audioGenerator()
                        if (data != null && isNoiseRunning) {
                            track.write(data, 0, data.size)
                        } else {
                            break
                        }
                    }
                    track.stop()
                    android.util.Log.d(TAG, "Noise stream stopped")
                }.start()
            }
        }
    }
    
    fun stopNoise() {
        android.util.Log.d(TAG, "Stopping noise")
        isNoiseRunning = false
        noiseAudioTrack?.stop()
    }
    
    fun stop() {
        android.util.Log.d(TAG, "Stopping all audio")
        morseAudioTrack?.stop()
        stopNoise()
    }
    
    fun pause() {
        morseAudioTrack?.pause()
        noiseAudioTrack?.pause()
    }
    
    fun resume() {
        morseAudioTrack?.play()
        if (isNoiseRunning) {
            noiseAudioTrack?.play()
        }
    }
    
    suspend fun release() {
        stopStreaming()
        stop()
        morseAudioTrack?.release()
        noiseAudioTrack?.release()
        morseAudioTrack = null
        noiseAudioTrack = null
    }
}
