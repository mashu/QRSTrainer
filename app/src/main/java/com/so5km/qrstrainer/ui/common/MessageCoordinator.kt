package com.so5km.qrstrainer.ui.common

import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.so5km.qrstrainer.R
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Event-driven message coordinator that handles all UI messages reactively
 * Eliminates manual message management and provides consistent messaging UX
 */
class MessageCoordinator(
    private val rootView: View,
    private val scope: CoroutineScope
) {
    private companion object {
        const val TAG = "MessageCoordinator"
    }
    
    // Message events channel
    private val messageChannel = Channel<MessageEvent>(Channel.UNLIMITED)
    private var processingJob: Job? = null
    
    /**
     * Different types of messages
     */
    sealed class MessageType {
        object Info : MessageType()
        object Success : MessageType()
        object Warning : MessageType()
        object Error : MessageType()
        data class Progress(val isCorrect: Boolean, val progressDelayMs: Long) : MessageType()
    }
    
    /**
     * Message event data
     */
    data class MessageEvent(
        val message: String,
        val type: MessageType,
        val duration: Long = 3000L,
        val action: (() -> Unit)? = null,
        val actionText: String? = null
    )
    
    /**
     * Start processing message events
     */
    fun start() {
        stop() // Stop any existing processing
        
        processingJob = scope.launch {
            messageChannel.receiveAsFlow().collect { messageEvent ->
                try {
                    showMessage(messageEvent)
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "Error showing message", e)
                }
            }
        }
    }
    
    /**
     * Stop processing message events
     */
    fun stop() {
        processingJob?.cancel()
        processingJob = null
    }
    
    /**
     * Send a message event
     */
    fun sendMessage(
        message: String,
        type: MessageType = MessageType.Info,
        duration: Long = 3000L,
        action: (() -> Unit)? = null,
        actionText: String? = null
    ) {
        val messageEvent = MessageEvent(message, type, duration, action, actionText)
        messageChannel.trySend(messageEvent)
    }
    
    /**
     * Convenience methods for different message types
     */
    fun showInfo(message: String, duration: Long = 3000L) {
        sendMessage(message, MessageType.Info, duration)
    }
    
    fun showSuccess(message: String, duration: Long = 3000L) {
        sendMessage(message, MessageType.Success, duration)
    }
    
    fun showWarning(message: String, duration: Long = 3000L) {
        sendMessage(message, MessageType.Warning, duration)
    }
    
    fun showError(message: String, duration: Long = 5000L) {
        sendMessage(message, MessageType.Error, duration)
    }
    
    fun showProgress(message: String, isCorrect: Boolean, progressDelayMs: Long) {
        sendMessage(message, MessageType.Progress(isCorrect, progressDelayMs))
    }
    
    /**
     * Show message with appropriate styling based on type
     */
    private suspend fun showMessage(messageEvent: MessageEvent) {
        when (messageEvent.type) {
            is MessageType.Progress -> showProgressMessage(messageEvent)
            else -> showStandardMessage(messageEvent)
        }
    }
    
    /**
     * Show standard snackbar message
     */
    private fun showStandardMessage(messageEvent: MessageEvent) {
        val snackbar = if (messageEvent.action != null && messageEvent.actionText != null) {
            Snackbar.make(rootView, messageEvent.message, Snackbar.LENGTH_INDEFINITE)
                .setAction(messageEvent.actionText) { messageEvent.action.invoke() }
        } else {
            val duration = when (messageEvent.duration) {
                in 0..2000 -> Snackbar.LENGTH_SHORT
                in 2001..4000 -> Snackbar.LENGTH_LONG
                else -> Snackbar.LENGTH_INDEFINITE
            }
            Snackbar.make(rootView, messageEvent.message, duration)
        }
        
        // Style based on message type
        val context = rootView.context
        when (messageEvent.type) {
            MessageType.Success -> {
                snackbar.setBackgroundTint(ContextCompat.getColor(context, R.color.md_theme_light_primary))
                snackbar.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onPrimary))
            }
            MessageType.Error -> {
                snackbar.setBackgroundTint(ContextCompat.getColor(context, R.color.md_theme_light_error))
                snackbar.setTextColor(ContextCompat.getColor(context, R.color.md_theme_light_onError))
            }
            MessageType.Warning -> {
                snackbar.setBackgroundTint(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
                snackbar.setTextColor(ContextCompat.getColor(context, android.R.color.white))
            }
            else -> {
                // Default styling for info messages
            }
        }
        
        snackbar.show()
    }
    
    /**
     * Show progress message with countdown bar
     */
    private suspend fun showProgressMessage(messageEvent: MessageEvent) {
        val progressType = messageEvent.type as MessageType.Progress
        val context = rootView.context
        
        val snackbar = Snackbar.make(rootView, messageEvent.message, Snackbar.LENGTH_INDEFINITE)
        
        // Style based on correctness
        val backgroundColor = if (progressType.isCorrect) {
            ContextCompat.getColor(context, R.color.md_theme_light_primary)
        } else {
            ContextCompat.getColor(context, R.color.md_theme_light_error)
        }
        
        val textColor = if (progressType.isCorrect) {
            ContextCompat.getColor(context, R.color.md_theme_light_onPrimary)
        } else {
            ContextCompat.getColor(context, R.color.md_theme_light_onError)
        }
        
        snackbar.setBackgroundTint(backgroundColor)
        snackbar.setTextColor(textColor)
        
        // Add progress bar
        val progressBar = com.google.android.material.progressindicator.LinearProgressIndicator(context)
        progressBar.isIndeterminate = false
        progressBar.max = 100
        progressBar.progress = 100
        progressBar.setIndicatorColor(textColor)
        progressBar.trackColor = backgroundColor
        
        val snackbarView = snackbar.view as Snackbar.SnackbarLayout
        val progressParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            8 // 8dp height
        )
        snackbarView.addView(progressBar, progressParams)
        
        snackbar.show()
        
        // Animate progress countdown
        if (progressType.progressDelayMs > 0) {
            val animator = ValueAnimator.ofInt(100, 0)
            animator.duration = progressType.progressDelayMs
            animator.addUpdateListener { animation ->
                val progress = animation.animatedValue as Int
                progressBar.progress = progress
            }
            
            withContext(Dispatchers.Main) {
                animator.start()
            }
            
            // Auto-dismiss after delay
            delay(progressType.progressDelayMs)
            snackbar.dismiss()
        } else {
            // No delay, dismiss immediately
            snackbar.dismiss()
        }
    }
    
    /**
     * Clear all pending messages
     */
    fun clearMessages() {
        // Cancel current messages by recreating the channel
        // This is a simple way to clear pending messages
        scope.launch {
            while (!messageChannel.isEmpty) {
                messageChannel.tryReceive()
            }
        }
    }
} 