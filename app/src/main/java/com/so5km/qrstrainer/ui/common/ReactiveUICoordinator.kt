package com.so5km.qrstrainer.ui.common

import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Reactive UI coordinator that handles UI state transitions using events
 * Eliminates manual UI state coordination by using reactive patterns
 */
class ReactiveUICoordinator(
    private val scope: LifecycleCoroutineScope
) {
    private companion object {
        const val TAG = "ReactiveUICoordinator"
    }
    
    private val activeObservers = mutableListOf<Job>()
    
    /**
     * Observe a state property and react to changes
     */
    fun <T> observeState(
        stateFlow: StateFlow<T>,
        onStateChange: (T) -> Unit
    ) {
        val job = scope.launch {
            stateFlow.collect { state ->
                try {
                    onStateChange(state)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in state observer", e)
                }
            }
        }
        activeObservers.add(job)
    }
    
    /**
     * Observe a derived state property and react to changes
     */
    fun <T, R> observeDerivedState(
        stateFlow: StateFlow<T>,
        selector: (T) -> R,
        onStateChange: (R) -> Unit
    ) {
        val job = scope.launch {
            stateFlow
                .map(selector)
                .distinctUntilChanged()
                .collect { derivedState ->
                    try {
                        onStateChange(derivedState)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in derived state observer", e)
                    }
                }
        }
        activeObservers.add(job)
    }
    
    /**
     * Observe multiple states and react when any changes
     */
    fun <T1, T2> observeCombinedState(
        state1: StateFlow<T1>,
        state2: StateFlow<T2>,
        onStateChange: (T1, T2) -> Unit
    ) {
        val job = scope.launch {
            kotlinx.coroutines.flow.combine(state1, state2) { s1, s2 -> s1 to s2 }
                .collect { (s1, s2) ->
                    try {
                        onStateChange(s1, s2)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in combined state observer", e)
                    }
                }
        }
        activeObservers.add(job)
    }
    
    /**
     * Observe three states and react when any changes
     */
    fun <T1, T2, T3> observeCombinedState(
        state1: StateFlow<T1>,
        state2: StateFlow<T2>,
        state3: StateFlow<T3>,
        onStateChange: (T1, T2, T3) -> Unit
    ) {
        val job = scope.launch {
            kotlinx.coroutines.flow.combine(state1, state2, state3) { s1, s2, s3 -> Triple(s1, s2, s3) }
                .collect { (s1, s2, s3) ->
                    try {
                        onStateChange(s1, s2, s3)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in triple state observer", e)
                    }
                }
        }
        activeObservers.add(job)
    }
    
    /**
     * Schedule a delayed UI action
     */
    fun scheduleDelayedAction(
        delayMs: Long,
        action: () -> Unit
    ): Job {
        val job = scope.launch {
            try {
                if (delayMs > 0) {
                    delay(delayMs)
                }
                action()
            } catch (e: CancellationException) {
                // Expected when cancelled
            } catch (e: Exception) {
                Log.e(TAG, "Error in delayed action", e)
            }
        }
        return job
    }
    
    /**
     * Execute UI action safely on main thread
     */
    fun executeUIAction(action: () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (e: Exception) {
                Log.e(TAG, "Error executing UI action", e)
            }
        }
    }
    
    /**
     * Clean up all observers
     */
    fun cleanup() {
        activeObservers.forEach { it.cancel() }
        activeObservers.clear()
        Log.d(TAG, "Reactive UI coordinator cleaned up")
    }
    
    /**
     * Get the number of active observers (for debugging)
     */
    fun getActiveObserverCount(): Int = activeObservers.count { it.isActive }
} 