package com.so5km.qrstrainer.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.so5km.qrstrainer.AppState
import com.so5km.qrstrainer.audio.AudioForegroundService
import com.so5km.qrstrainer.audio.MorseCodeGenerator
import com.so5km.qrstrainer.audio.ScreenStateReceiver
import com.so5km.qrstrainer.audio.SharedAudioState
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.ui.settings.SettingsViewModel

/**
 * Base fragment class for fragments that use audio functionality
 * Provides common audio management and state handling
 */
abstract class BaseAudioFragment : Fragment(), ScreenStateReceiver.ScreenStateCallback {
    
    protected open lateinit var morseGenerator: MorseCodeGenerator
    protected lateinit var settingsViewModel: SettingsViewModel
    protected lateinit var screenStateReceiver: ScreenStateReceiver
    
    // Lifecycle state tracking
    protected open var wasPlayingWhenPaused = false
    
    // Session state tracking
    protected open var isSessionActive = false
    
    companion object {
        private const val TAG = "BaseAudioFragment"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize the MorseCodeGenerator
        morseGenerator = MorseCodeGenerator(requireContext())
        
        // Initialize the SettingsViewModel
        settingsViewModel = ViewModelProvider(requireActivity())[SettingsViewModel::class.java]
        
        // Initialize screen state receiver
        screenStateReceiver = ScreenStateReceiver(this)
    }
    
    override fun onResume() {
        super.onResume()
        
        // Register screen state receiver
        screenStateReceiver.register(requireContext())
        
        // Start the headphone keep-alive tone
        morseGenerator.startHeadphoneKeepAlive()
        
        // Resume audio when app comes back to foreground
        if (wasPlayingWhenPaused && AppState.isAppInForeground.value == true) {
            resumePlayback()
        }
        
        // Get latest settings
        val settings = AppState.getSettings(requireContext())
        
        // Start foreground service if needed
        if (AppState.isAudioPlaying.value == true) {
            startForegroundService()
        }
        
        // Observe settings changes
        settingsViewModel.settings.observe(viewLifecycleOwner) { newSettings ->
            onSettingsChanged(newSettings)
        }
        
        // Observe shared audio state
        SharedAudioState.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            onSharedAudioStateChanged(isPlaying)
        }
    }
    
    override fun onPause() {
        super.onPause()
        
        // Unregister screen state receiver
        screenStateReceiver.unregister(requireContext())
        
        // Only stop audio if app is minimized, not if screen is just turned off
        if (AppState.isAudioPlaying.value == true && !AudioForegroundService.isRunning()) {
            wasPlayingWhenPaused = true
            morseGenerator.stop()
            AppState.setAudioPlaying(false)
        }
        
        // Stop the headphone keep-alive tone when in background to save resources
        morseGenerator.stopHeadphoneKeepAlive()
        
        // Stop foreground service if needed
        if (!AppState.isAudioPlaying.value!!) {
            stopForegroundService()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Stop and clean up audio resources
        morseGenerator.stop()
        morseGenerator.stopHeadphoneKeepAlive()
        morseGenerator.release()
        
        // Ensure session is stopped
        isSessionActive = false
        stopContinuousNoise()
        
        // Stop foreground service if it's running
        if (AudioForegroundService.isRunning()) {
            val intent = Intent(requireContext(), AudioForegroundService::class.java)
            intent.action = AudioForegroundService.ACTION_STOP
            requireContext().startService(intent)
        }
    }
    
    /**
     * Start continuous background noise
     */
    protected open fun startContinuousNoise() {
        if (!AppState.isNoiseRunning.value!!) {
            val settings = settingsViewModel.settings.value ?: AppState.getSettings(requireContext())
            
            if (settings.filterRingingEnabled && settings.backgroundNoiseLevel > 0) {
                morseGenerator.startContinuousNoise()
                AppState.setNoiseRunning(true)
                Log.d(TAG, "Continuous noise started")
            }
        }
    }
    
    /**
     * Stop continuous background noise
     */
    protected open fun stopContinuousNoise() {
        if (AppState.isNoiseRunning.value == true) {
            morseGenerator.stopContinuousNoise()
            AppState.setNoiseRunning(false)
            Log.d(TAG, "Continuous noise stopped")
        }
    }
    
    /**
     * Start foreground service for audio playback
     */
    protected fun startForegroundService() {
        val serviceIntent = Intent(requireContext(), AudioForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent)
        } else {
            requireContext().startService(serviceIntent)
        }
    }
    
    /**
     * Stop foreground service
     */
    protected fun stopForegroundService() {
        val serviceIntent = Intent(requireContext(), AudioForegroundService::class.java)
        requireContext().stopService(serviceIntent)
    }
    
    /**
     * Pause audio playback
     */
    protected open fun pausePlayback() {
        morseGenerator.pause()
        AppState.setAudioPlaying(false)
    }
    
    /**
     * Resume audio playback
     */
    protected open fun resumePlayback() {
        morseGenerator.resume()
        AppState.setAudioPlaying(true)
    }
    
    /**
     * Get current settings from ViewModel or AppState
     */
    protected fun getSettings(): TrainingSettings {
        return settingsViewModel.settings.value ?: AppState.getSettings(requireContext())
    }
    
    /**
     * Called when screen turns on
     */
    override fun onScreenOn() {
        Log.d(TAG, "Screen turned ON")
    }
    
    /**
     * Called when screen turns off
     */
    override fun onScreenOff() {
        Log.d(TAG, "Screen turned OFF")
        // Start foreground service to keep audio playing when screen is off
        if (AppState.isAudioPlaying.value == true && !AudioForegroundService.isRunning()) {
            startForegroundService()
        }
    }
    
    /**
     * Called when settings change
     * Override to handle specific settings changes
     */
    open fun onSettingsChanged(newSettings: TrainingSettings) {
        // Default implementation does nothing
        // Subclasses should override this method if they need to respond to settings changes
    }
    
    /**
     * Called when shared audio state changes
     * Override to handle specific audio state changes
     */
    open fun onSharedAudioStateChanged(isPlaying: Boolean) {
        // Default implementation does nothing
        // Subclasses should override this method if they need to respond to audio state changes
    }
} 