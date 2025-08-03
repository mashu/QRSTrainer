package com.so5km.qrstrainer.ui.trainer

import android.util.Log
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.state.AppAction
import com.so5km.qrstrainer.state.AppState
import com.so5km.qrstrainer.state.StoreViewModel
import com.so5km.qrstrainer.state.TrainingState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Reactive coordinator for training flow events
 * Eliminates manual state coordination by using reactive flows
 */
class TrainingCoordinator(
    private val storeViewModel: StoreViewModel,
    private val scope: CoroutineScope
) {
    private companion object {
        const val TAG = "TrainingCoordinator"
    }
    
    // Event callbacks
    var onStartSequence: ((String, TrainingSettings) -> Unit)? = null
    var onStopTraining: (() -> Unit)? = null
    var onAdvanceToNext: (() -> Unit)? = null
    var onShowMessage: ((String) -> Unit)? = null
    
    private var coordinationJob: Job? = null
    private var pendingAdvanceJob: Job? = null
    
    /**
     * Start reactive coordination of training events
     */
    fun start() {
        stop() // Stop any existing coordination
        
        coordinationJob = scope.launch {
            // Reactive coordination of training state changes
            observeTrainingFlow()
        }
        
        Log.d(TAG, "Training coordination started")
    }
    
    /**
     * Stop reactive coordination
     */
    fun stop() {
        coordinationJob?.cancel()
        pendingAdvanceJob?.cancel()
        coordinationJob = null
        pendingAdvanceJob = null
        
        Log.d(TAG, "Training coordination stopped")
    }
    
    /**
     * Observe training flow and react to state changes
     */
    private suspend fun observeTrainingFlow() {
        // Combine training state, audio state, and settings for reactive coordination
        combine(
            storeViewModel.state.map { it.trainingState },
            storeViewModel.state.map { it.audioState },
            storeViewModel.state.map { it.settings }
        ) { trainingState, audioState, settings ->
            TrainingFlowState(trainingState, audioState, settings)
        }
        .collect { flowState ->
            handleTrainingFlowChange(flowState)
        }
    }
    
    /**
     * Handle training flow state changes reactively
     */
    private suspend fun handleTrainingFlowChange(flowState: TrainingFlowState) {
        val trainingState = flowState.trainingState
        val audioState = flowState.audioState
        val settings = flowState.settings
        
        Log.d(TAG, "Training flow change: ${trainingState.state}, audio playing: ${audioState.isPlaying}")
        
        when {
            // When training is active and audio completes, advance automatically
            trainingState.state == TrainingState.WAITING && 
            !audioState.isPlaying && 
            trainingState.previousWasCorrect != null -> {
                
                Log.d(TAG, "Audio completed, scheduling automatic advancement")
                scheduleAutomaticAdvancement(settings)
            }
            
            // When training stops, clean up any pending advancement
            trainingState.state == TrainingState.READY -> {
                cancelAutomaticAdvancement()
            }
            
            // When audio starts playing, ensure UI is in correct state
            trainingState.state == TrainingState.PLAYING && audioState.isPlaying -> {
                Log.d(TAG, "Audio playback active during training")
                // UI should already be in correct state via normal state observation
            }
        }
    }
    
    /**
     * Schedule automatic advancement to next sequence
     */
    private fun scheduleAutomaticAdvancement(settings: TrainingSettings) {
        // Cancel any existing advancement
        pendingAdvanceJob?.cancel()
        
        pendingAdvanceJob = scope.launch {
            try {
                val delayMs = settings.sequenceDelayMs
                Log.d(TAG, "Scheduling advancement in ${delayMs}ms")
                
                if (delayMs > 0) {
                    delay(delayMs)
                }
                
                // Check if training is still active before advancing
                val currentState = storeViewModel.state.value.trainingState
                if (currentState.isActive && currentState.state == TrainingState.WAITING) {
                    Log.d(TAG, "Auto-advancing to next sequence")
                    onAdvanceToNext?.invoke()
                } else {
                    Log.d(TAG, "Training state changed, canceling auto-advancement")
                }
                
            } catch (e: CancellationException) {
                Log.d(TAG, "Auto-advancement cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error during auto-advancement", e)
                onShowMessage?.invoke("Error advancing to next sequence: ${e.message}")
            }
        }
    }
    
    /**
     * Cancel automatic advancement
     */
    private fun cancelAutomaticAdvancement() {
        pendingAdvanceJob?.cancel()
        pendingAdvanceJob = null
        Log.d(TAG, "Automatic advancement cancelled")
    }
    
    /**
     * Handle training completion (answer submitted)
     */
    fun onTrainingCompleted(isCorrect: Boolean, settings: TrainingSettings) {
        scope.launch {
            // Let the state management handle the submission
            // The reactive flow will automatically handle advancement
            Log.d(TAG, "Training completed: ${if (isCorrect) "correct" else "incorrect"}")
        }
    }
    
    /**
     * Request immediate advancement (user clicked next/skip)
     */
    fun requestImmediateAdvancement() {
        Log.d(TAG, "Immediate advancement requested")
        cancelAutomaticAdvancement()
        onAdvanceToNext?.invoke()
    }
    
    /**
     * Data class for training flow state
     */
    private data class TrainingFlowState(
        val trainingState: com.so5km.qrstrainer.state.TrainingStateData,
        val audioState: com.so5km.qrstrainer.state.AudioStateData,
        val settings: TrainingSettings
    )
} 