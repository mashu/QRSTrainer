package com.so5km.qrstrainer.state

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Helper class to observe state changes in a lifecycle-aware manner
 */
class StateObserver(
    private val lifecycleOwner: LifecycleOwner,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : DefaultLifecycleObserver {
    
    private val jobs = mutableListOf<Job>()
    
    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }
    
    fun <T> observe(
        flow: StateFlow<T>,
        action: (T) -> Unit
    ) {
        val job = scope.launch {
            flow.collect { value ->
                action(value)
            }
        }
        jobs.add(job)
    }
    
    fun <T, R> observeDistinct(
        flow: StateFlow<T>,
        selector: (T) -> R,
        action: (R) -> Unit
    ) {
        val job = scope.launch {
            flow.map(selector)
                .distinctUntilChanged()
                .collect { value ->
                    action(value)
                }
        }
        jobs.add(job)
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        jobs.forEach { it.cancel() }
        jobs.clear()
        super.onDestroy(owner)
    }
}