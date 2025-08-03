package com.so5km.qrstrainer.ui.listen

import android.util.Log
import com.so5km.qrstrainer.data.TrainingSettings
import kotlinx.coroutines.*

/**
 * Coordinates the proper sequence of events in listen mode:
 * Audio Playback → TTS Delay → TTS Speech → Post-Reveal Delay → Next Sequence
 */
class ListenSequenceCoordinator(
    private val onStartNextSequence: () -> Unit,
    private val onStartTTS: (sequence: String, settings: TrainingSettings) -> Unit
) {
    private var currentJob: Job? = null
    private var ttsTimeoutJob: Job? = null
    private var isActive = false
    
    /**
     * Called when audio playback completes
     */
    fun onSequencePlaybackComplete(sequence: String, settings: TrainingSettings) {
        if (!isActive) return
        
        Log.d("ListenCoordinator", "Sequence playback completed: '$sequence'")
        
        currentJob?.cancel()
        ttsTimeoutJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                // Step 1: Wait for TTS delay (parametric)
                if (settings.ttsDelayMs > 0) {
                    Log.d("ListenCoordinator", "Waiting TTS delay: ${settings.ttsDelayMs}ms")
                    delay(settings.ttsDelayMs)
                }
                
                // Step 2: Start TTS if enabled
                if (settings.ttsSpeakInListenMode) {
                    Log.d("ListenCoordinator", "Starting TTS for sequence: '$sequence'")
                    onStartTTS(sequence, settings)
                    
                    // Set up a timeout for TTS in case it fails to complete
                    ttsTimeoutJob = CoroutineScope(Dispatchers.Main).launch {
                        delay(10000) // 10 second timeout for TTS
                        if (isActive) {
                            Log.w("ListenCoordinator", "TTS timeout - proceeding without TTS completion")
                            onTTSComplete(settings)
                        }
                    }
                    // TTS will call onTTSComplete when done (or timeout will trigger)
                } else {
                    Log.d("ListenCoordinator", "TTS disabled - proceeding to post-reveal delay")
                    onTTSComplete(settings)
                }
            } catch (e: CancellationException) {
                Log.d("ListenCoordinator", "Sequence coordination cancelled")
            }
        }
    }
    
    /**
     * Called when TTS completes speaking
     */
    fun onTTSComplete(settings: TrainingSettings) {
        if (!isActive) return
        
        Log.d("ListenCoordinator", "TTS completed")
        
        currentJob?.cancel()
        ttsTimeoutJob?.cancel() // Cancel timeout since TTS completed normally
        currentJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                // Step 3: Wait for post-reveal delay (parametric)
                if (settings.postRevealDelayMs > 0) {
                    Log.d("ListenCoordinator", "Waiting post-reveal delay: ${settings.postRevealDelayMs}ms")
                    delay(settings.postRevealDelayMs)
                }
                
                // Step 4: Start next sequence
                Log.d("ListenCoordinator", "Starting next sequence")
                onStartNextSequence()
            } catch (e: CancellationException) {
                Log.d("ListenCoordinator", "Post-reveal coordination cancelled")
            }
        }
    }
    
    /**
     * Start coordination (when listen mode becomes active)
     */
    fun start() {
        Log.d("ListenCoordinator", "Starting sequence coordination")
        isActive = true
    }
    
    /**
     * Stop coordination and cancel any pending events
     */
    fun stop() {
        Log.d("ListenCoordinator", "Stopping sequence coordination")
        isActive = false
        currentJob?.cancel()
        ttsTimeoutJob?.cancel()
        currentJob = null
        ttsTimeoutJob = null
    }
    
    /**
     * Cancel current coordination (e.g., user manually advances)
     */
    fun cancel() {
        Log.d("ListenCoordinator", "Cancelling current coordination")
        currentJob?.cancel()
        ttsTimeoutJob?.cancel()
        currentJob = null
        ttsTimeoutJob = null
    }
} 