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
    
    private var audioTrack: AudioTrack? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    init {
        initializeAudioTrack()
    }
    
    private fun initializeAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ) * 8
        
        audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Use the newer AudioTrack constructor for API 23+
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
            // Fallback for older versions
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
        audioTrack?.let { track ->
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.play()
                _isPlaying.value = true
                track.write(audioData, 0, audioData.size)
                track.stop()
                _isPlaying.value = false
            }
        }
    }
    
    fun playStream(audioGenerator: () -> ShortArray?) {
        audioTrack?.let { track ->
            if (track.state == AudioTrack.STATE_INITIALIZED) {
                track.play()
                _isPlaying.value = true
                
                while (_isPlaying.value) {
                    val data = audioGenerator()
                    if (data != null) {
                        track.write(data, 0, data.size)
                    } else {
                        break
                    }
                }
                
                track.stop()
                _isPlaying.value = false
            }
        }
    }
    
    fun stop() {
        _isPlaying.value = false
        audioTrack?.stop()
    }
    
    fun pause() {
        audioTrack?.pause()
    }
    
    fun resume() {
        audioTrack?.play()
    }
    
    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
    }
}
