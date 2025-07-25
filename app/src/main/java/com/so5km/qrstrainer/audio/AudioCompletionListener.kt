package com.so5km.qrstrainer.audio

/**
 * Interface for receiving audio playback completion events
 * Replaces CPU-intensive polling with event-driven callbacks
 */
interface AudioCompletionListener {
    /**
     * Called when a sequence has finished playing
     */
    fun onSequenceCompleted()
    
    /**
     * Called when playback is cancelled or stopped
     */
    fun onPlaybackStopped()
    
    /**
     * Called when an error occurs during playback
     */
    fun onPlaybackError(error: Exception)
} 