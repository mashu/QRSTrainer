package com.so5km.qrstrainer.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * Broadcast receiver to detect screen on/off events
 */
class ScreenStateReceiver(private val callback: ScreenStateCallback) : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenStateReceiver"
    }

    /**
     * Interface for screen state change callbacks
     */
    interface ScreenStateCallback {
        fun onScreenOn()
        fun onScreenOff()
    }

    /**
     * Register this receiver to listen for screen on/off events
     */
    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(this, filter)
        Log.d(TAG, "Screen state receiver registered")
    }

    /**
     * Unregister this receiver
     */
    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(this)
            Log.d(TAG, "Screen state receiver unregistered")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Receiver not registered", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen turned ON")
                callback.onScreenOn()
            }
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen turned OFF")
                callback.onScreenOff()
            }
        }
    }
} 