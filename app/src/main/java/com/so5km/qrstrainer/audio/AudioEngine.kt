package com.so5km.qrstrainer.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Core audio engine responsible for low-level audio playback
 */
class AudioEngine {
    
    companion object {
        const val SAMPLE_RATE = 44100
        private const val TAG = "AudioEngine"
    }
    
    private var morseAudioTrack: AudioTrack? = null
    private var noiseAudioTrack: AudioTrack? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private var isNoiseRunning = false
    
    init {
        android.util.Log.d(TAG, "AudioEngine initializing...")
        initializeAudioTracks()
    }
    
    private fun initializeAudioTracks() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 4 // Increase buffer size for smoother playback
        
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
    
    fun play(audioData: ShortArray) {
        morseAudioTrack?.let { track ->
            android.util.Log.d(TAG, "AudioEngine.play called with ${audioData.size} samples")
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
        _isPlaying.value = false
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
    
    fun release() {
        stop()
        morseAudioTrack?.release()
        noiseAudioTrack?.release()
        morseAudioTrack = null
        noiseAudioTrack = null
    }
}
