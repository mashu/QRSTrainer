package com.so5km.qrstrainer.ui.listen

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.speech.tts.TextToSpeech
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.so5km.qrstrainer.AppState
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.audio.AudioForegroundService
import com.so5km.qrstrainer.audio.MorseCodeGenerator
import com.so5km.qrstrainer.audio.ScreenStateReceiver
import com.so5km.qrstrainer.data.MorseCode
import com.so5km.qrstrainer.data.ProgressTracker
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.databinding.FragmentListenBinding
import com.so5km.qrstrainer.training.SequenceGenerator
import java.util.Locale

class ListenFragment : Fragment(), TextToSpeech.OnInitListener, ScreenStateReceiver.ScreenStateCallback {

    private var _binding: FragmentListenBinding? = null
    private val binding get() = _binding!!

    private lateinit var sequenceGenerator: SequenceGenerator
    private lateinit var morseGenerator: MorseCodeGenerator
    private lateinit var settings: TrainingSettings
    private lateinit var textToSpeech: TextToSpeech
    
    private var currentSequence: String = ""
    private var isAudioPlaying = false
    private var isPaused = false
    private var isSequenceRevealed = false
    private var sequenceCount = 0
    private var isAutoRevealEnabled = true
    private var autoRevealDelayMs = 500L // Default delay before auto-reveal, will be overridden by settings
    
    // Lifecycle state tracking
    private var wasPlayingWhenPaused = false
    
    // Session state tracking
    private var isSessionActive = false
    private var isNoiseRunning = false
    
    // Delay progress tracking
    private var delayHandler: Handler? = null
    private var delayRunnable: Runnable? = null
    private var delayStartTime: Long = 0
    private var totalDelayTime: Long = 0

    // Screen state receiver
    private lateinit var screenStateReceiver: ScreenStateReceiver
    
    // Flag to track if app is in foreground
    private var isAppInForeground = true
    
    // Training states
    enum class ListeningState {
        READY,      // Ready to start
        PLAYING,    // Audio playing
        PAUSED,     // Audio paused
        WAITING,    // Waiting for reveal after audio
        REVEALED    // Sequence revealed
    }
    
    private var currentState = ListeningState.READY

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListenBinding.inflate(inflater, container, false)
        val root: View = binding.root

        initializeComponents()
        setupUI()
        updateUIState()

        return root
    }
    
    override fun onResume() {
        super.onResume()
        // Reload settings when returning to fragment
        settings = TrainingSettings.load(requireContext())
        // Update the TTS delay from settings
        autoRevealDelayMs = settings.ttsDelayMs.toLong()
        updateDisplay()
        
        // Register screen state receiver
        screenStateReceiver = ScreenStateReceiver(this)
        screenStateReceiver.register(requireContext())
        
        // If audio was playing when we paused, update UI to show stopped state
        if (wasPlayingWhenPaused) {
            wasPlayingWhenPaused = false
            currentState = ListeningState.READY
            updateUIState()
        }
        
        // Resume audio when app comes back to foreground
        if (isPaused && wasPlayingWhenPaused) {
            resumePlayback()
        }
        
        // Restart the headphone keep-alive tone
        morseGenerator.startHeadphoneKeepAlive()
    }
    
    override fun onPause() {
        super.onPause()
        
        // Unregister screen state receiver
        if (::screenStateReceiver.isInitialized) {
            screenStateReceiver.unregister(requireContext())
        }
        
        // Only stop audio if app is minimized, not if screen is just turned off
        if (isAudioPlaying || morseGenerator.isPlaying()) {
            wasPlayingWhenPaused = true
            
            // If using foreground service and app is not in foreground, let it handle playback
            if (!AudioForegroundService.isRunning() || AppState.isAppInForeground) {
                morseGenerator.stop()
                isAudioPlaying = false
                isPaused = false
                currentState = ListeningState.READY
            }
        }
        
        // Pause audio when app goes to background
        if (isAudioPlaying && !isPaused) {
            pausePlayback()
        }
        
        // Stop noise if session is active
        if (isNoiseRunning) {
            stopContinuousNoise()
        }
        
        // Stop the headphone keep-alive tone when in background to save resources
        morseGenerator.stopHeadphoneKeepAlive()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Stop and clean up audio resources
        morseGenerator.stop()
        morseGenerator.stopHeadphoneKeepAlive() // Stop the headphone keep-alive tone
        morseGenerator.release()  // Clean up ToneGenerator resources
        hideSequenceDelayProgress() // Clean up delay handler
        
        // Shutdown TextToSpeech
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        
        // Ensure session is stopped
        isSessionActive = false
        isNoiseRunning = false
        
        // Stop foreground service if it's running
        if (AudioForegroundService.isRunning()) {
            val intent = Intent(requireContext(), AudioForegroundService::class.java)
            intent.action = AudioForegroundService.ACTION_STOP
            requireContext().startService(intent)
        }
        
        _binding = null
    }

    private fun initializeComponents() {
        // Initialize with dummy progress tracker just for sequence generation
        val progressTracker = ProgressTracker(requireContext())
        sequenceGenerator = SequenceGenerator(progressTracker)
        morseGenerator = MorseCodeGenerator(requireContext())
        settings = TrainingSettings.load(requireContext())
        // Set the TTS delay from settings
        autoRevealDelayMs = settings.ttsDelayMs.toLong()
        textToSpeech = TextToSpeech(requireContext(), this)
        
        // Start the headphone keep-alive tone to prevent disconnections
        morseGenerator.startHeadphoneKeepAlive()
    }

    private fun setupUI() {
        updateDisplay()
        
        // Main controls
        binding.buttonStart.setOnClickListener {
            if (isSessionActive) {
                // If session is active, replay current sequence
                playCurrentSequence()
            } else {
                // Start a new session
                startSession()
            }
        }
        
        binding.buttonPause.setOnClickListener {
            pausePlayback()
        }
        
        binding.buttonStop.setOnClickListener {
            stopSession()
        }
        
        // Sequence controls
        binding.buttonReplay.setOnClickListener {
            playCurrentSequence()
        }
        
        binding.buttonReveal.setOnClickListener {
            revealSequence()
        }
        
        binding.buttonNext.setOnClickListener {
            startNewSequence()
        }
        
        // Auto-reveal switch
        binding.switchAutoReveal.setOnCheckedChangeListener { _, isChecked ->
            isAutoRevealEnabled = isChecked
            binding.textAutoRevealStatus.text = if (isChecked) 
                "Auto-reveal ON" 
            else 
                "Auto-reveal OFF"
            
            // Update instruction text based on auto-reveal state
            binding.textInstructions.text = if (isChecked) {
                "Continuous listening mode. Random sequences will play automatically."
            } else {
                "Listen to sequences and memorize them. After each sequence, the characters will be revealed."
            }
            
            // If currently in waiting state and auto-reveal is turned on, reveal immediately
            if (isChecked && currentState == ListeningState.WAITING) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (currentState == ListeningState.WAITING) {
                        revealSequence()
                    }
                }, autoRevealDelayMs)
            }
        }
        
        // Set initial state of auto-reveal switch
        binding.switchAutoReveal.isChecked = isAutoRevealEnabled
        binding.textAutoRevealStatus.text = if (isAutoRevealEnabled) "Auto-reveal ON" else "Auto-reveal OFF"
        binding.textInstructions.text = if (isAutoRevealEnabled) {
            "Continuous listening mode. Random sequences will play automatically."
        } else {
            "Listen to sequences and memorize them. After each sequence, the characters will be revealed."
        }
    }

    private fun updateUIState() {
        when (currentState) {
            ListeningState.READY -> {
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonPause.visibility = View.GONE
                binding.buttonStop.visibility = View.GONE
                binding.buttonReplay.visibility = View.GONE
                binding.buttonReveal.visibility = View.GONE
                binding.buttonNext.visibility = View.GONE
                
                if (isSessionActive) {
                    binding.buttonStart.text = "▶ REPLAY"
                    binding.buttonStop.visibility = View.VISIBLE
                } else {
                    binding.buttonStart.text = "▶ START"
                }
                
                binding.textStatus.text = if (isSessionActive) "Session active - ready for next sequence" else getString(R.string.ready_to_listen)
            }
            ListeningState.PLAYING -> {
                binding.buttonStart.visibility = View.GONE
                binding.buttonPause.visibility = View.VISIBLE
                binding.buttonStop.visibility = View.VISIBLE
                binding.buttonReplay.visibility = View.GONE
                binding.buttonReveal.visibility = View.GONE
                binding.buttonNext.visibility = View.GONE
                binding.textStatus.text = getString(R.string.sequence_playing)
            }
            ListeningState.PAUSED -> {
                binding.buttonStart.text = "▶ RESUME"
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonPause.visibility = View.GONE
                binding.buttonStop.visibility = View.VISIBLE
                binding.buttonReplay.visibility = View.GONE
                binding.buttonReveal.visibility = View.GONE
                binding.buttonNext.visibility = View.GONE
                binding.textStatus.text = "Paused - Press Resume to continue"
            }
            ListeningState.WAITING -> {
                binding.buttonStart.visibility = View.GONE
                binding.buttonPause.visibility = View.GONE
                binding.buttonStop.visibility = View.VISIBLE
                binding.buttonReplay.visibility = View.VISIBLE
                binding.buttonReveal.visibility = View.VISIBLE
                binding.buttonNext.visibility = View.GONE
                binding.textStatus.text = "Sequence finished - Reveal or Replay"
            }
            ListeningState.REVEALED -> {
                binding.buttonStart.visibility = View.GONE
                binding.buttonPause.visibility = View.GONE
                binding.buttonStop.visibility = View.VISIBLE
                binding.buttonReplay.visibility = View.VISIBLE
                binding.buttonReveal.visibility = View.GONE
                binding.buttonNext.visibility = View.VISIBLE
                binding.textStatus.text = getString(R.string.sequence_revealed, currentSequence)
            }
        }
    }

    private fun updateDisplay() {
        val currentLevel = settings.kochLevel
        
        // Update level and WPM display
        binding.textLevel.text = "Level $currentLevel"
        binding.textWpm.text = "${settings.speedWpm} WPM"
        
        // Update sequence count
        binding.textSequenceCount.text = getString(R.string.sequence_count, sequenceCount)
        
        // Hide the progress bar and related text since we're not tracking progress
        binding.progressLevel.visibility = View.GONE
        binding.textNextLevel.visibility = View.GONE
    }

    private fun startSession() {
        // Reset session state
        isSessionActive = true
        sequenceCount = 0
        updateDisplay()
        
        // Start continuous background noise if enabled
        if (settings.filterRingingEnabled && settings.backgroundNoiseLevel > 0) {
            startContinuousNoise()
        }
        
        // Start foreground service for background audio playback
        startAudioForegroundService()
        
        // Start first sequence
        startNewSequence()
    }
    
    /**
     * Generate a new random sequence from the current level
     */
    private fun startNewSequence() {
        // Hide progress bar
        hideSequenceDelayProgress()
        
        // Stop any current audio playback completely
        stopAudioCompletely()
        
        // Generate new sequence from current level characters
        currentSequence = sequenceGenerator.generateSequence(
            settings.kochLevel,
            settings.groupSizeMin,
            settings.groupSizeMax,
            settings.lettersOnlyMode
        )
        
        // Reset state
        resetState()
        
        // Small delay to ensure audio system is ready
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isDetached && _binding != null && currentState == ListeningState.READY) {
                playCurrentSequence()
            }
        }, 100)
    }
    
    /**
     * Reset the state for a new sequence
     */
    private fun resetState() {
        isSequenceRevealed = false
        isAudioPlaying = false
        isPaused = false
        wasPlayingWhenPaused = false
        binding.textCurrentSequence.text = "?"
        currentState = ListeningState.READY
    }
    
    /**
     * Play the current sequence
     */
    private fun playCurrentSequence() {
        // Prevent starting playback if already playing or in wrong state
        if (isAudioPlaying || currentState == ListeningState.PLAYING) {
            android.util.Log.w("ListenFragment", "Cannot start playback: isAudioPlaying=$isAudioPlaying, currentState=$currentState")
            return
        }
        
        // Stop any current playback first (defensive)
        stopAudioCompletely()
        
        // Update state
        currentState = ListeningState.PLAYING
        isAudioPlaying = true
        isPaused = false
        updateUIState()
        
        // Make sure foreground service is running when screen is off
        if (!AppState.isAppInForeground && !AudioForegroundService.isRunning()) {
            startAudioForegroundService()
        }
        
        // Play the sequence without starting noise (noise is handled at session level)
        morseGenerator.playSequence(
            currentSequence,
            settings,
            startNoise = false  // Don't start noise for each sequence
        ) {
            // On playback complete
            Handler(Looper.getMainLooper()).post {
                if (!isDetached && _binding != null && isAudioPlaying) {
                    isAudioPlaying = false
                    wasPlayingWhenPaused = false
                    currentState = ListeningState.WAITING
                    updateUIState()
                    
                    // Auto-reveal if enabled
                    if (isAutoRevealEnabled) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (currentState == ListeningState.WAITING) {
                                revealSequence()
                                
                                // Auto-advance to next sequence after reveal and speech
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (currentState == ListeningState.REVEALED && isSessionActive && isAutoRevealEnabled) {
                                        // Generate and play a new random sequence from the same level
                                        startNewSequence()
                                    }
                                }, calculateSpeechDuration(currentSequence) + 1000) // Wait for speech to finish plus a bit more
                            }
                        }, autoRevealDelayMs)
                    }
                }
            }
        }
    }
    
    /**
     * Calculate approximate duration of speech in milliseconds
     */
    private fun calculateSpeechDuration(text: String): Long {
        // Rough estimate: 200ms per character plus 500ms between characters
        return text.length * 700L
    }

    private fun pausePlayback() {
        if (isAudioPlaying) {
            morseGenerator.pause()
            isAudioPlaying = false
            isPaused = true
            wasPlayingWhenPaused = true
            currentState = ListeningState.PAUSED
            updateUIState()
            
            // Also pause the foreground service if it's running
            if (AudioForegroundService.isRunning()) {
                pauseAudioForegroundService()
            }
        }
    }

    private fun resumePlayback() {
        if (isPaused && wasPlayingWhenPaused) {
            morseGenerator.resume()
            isAudioPlaying = true
            isPaused = false
            wasPlayingWhenPaused = false
            currentState = ListeningState.PLAYING
            updateUIState()
            
            // Also resume the foreground service if it's running
            if (AudioForegroundService.isRunning()) {
                resumeAudioForegroundService()
            }
        }
    }

    private fun revealSequence() {
        if (currentState == ListeningState.WAITING || currentState == ListeningState.REVEALED) {
            // Update UI
            binding.textCurrentSequence.text = currentSequence
            currentState = ListeningState.REVEALED
            isSequenceRevealed = true
            updateUIState()
            
            // Speak the sequence
            speakSequence()
            
            // Increment sequence count
            sequenceCount++
            updateDisplay()
            
            // Update instruction text for continuous mode when auto-reveal is enabled
            if (isAutoRevealEnabled) {
                binding.textInstructions.text = "Continuous listening mode. Random sequences will play automatically."
            }
        }
    }
    
    private fun speakSequence() {
        // Speak each character with a pause between them
        val spokenText = currentSequence.toCharArray().joinToString(" ")
        
        // Set the TTS speech rate and volume based on settings
        textToSpeech.setSpeechRate(settings.ttsSpeechRate)
        
        // Create params bundle for volume control
        val params = Bundle()
        // Use the volume setting directly - it can now go up to 200%
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, settings.ttsVolumeLevel)
        
        textToSpeech.speak(spokenText, TextToSpeech.QUEUE_FLUSH, params, "seq_id")
    }

    private fun stopAudioCompletely() {
        // Stop audio and reset all audio-related state
        morseGenerator.stopSequence() // Only stop the sequence, not the continuous noise
        isAudioPlaying = false
        isPaused = false
        wasPlayingWhenPaused = false
        
        // Give audio system time to fully stop
        try {
            Thread.sleep(50) // Short delay to ensure audio stops
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
    
    /**
     * Stop the current listening session
     */
    private fun stopSession() {
        // Stop any ongoing sequence
        stopAudioCompletely()
        
        // Stop continuous background noise
        stopContinuousNoise()
        
        // Stop foreground service
        stopAudioForegroundService()
        
        // Reset session state
        isSessionActive = false
        currentState = ListeningState.READY
        
        // Update UI
        updateUIState()
        binding.textStatus.text = "Listening stopped"
        
        // Clear status message after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isDetached && _binding != null && currentState == ListeningState.READY) {
                binding.textStatus.text = getString(R.string.ready_to_listen)
            }
        }, 1000)
    }
    
    /**
     * Start continuous background noise for the session
     */
    private fun startContinuousNoise() {
        if (!isNoiseRunning && settings.filterRingingEnabled && settings.backgroundNoiseLevel > 0) {
            morseGenerator.startContinuousNoise()
            isNoiseRunning = true
            android.util.Log.d("ListenFragment", "Started continuous background noise for session")
        }
    }
    
    /**
     * Stop continuous background noise
     */
    private fun stopContinuousNoise() {
        if (isNoiseRunning) {
            morseGenerator.stopContinuousNoise()
            isNoiseRunning = false
            android.util.Log.d("ListenFragment", "Stopped continuous background noise")
        }
    }
    
    /**
     * Show the sequence delay progress bar and start updating it
     */
    private fun showSequenceDelayProgress(delayMs: Long) {
        android.util.Log.d("ListenFragment", "showSequenceDelayProgress called with ${delayMs}ms")
        if (_binding == null) return
        
        totalDelayTime = delayMs
        delayStartTime = System.currentTimeMillis()
        
        // Show the progress bar overlay
        binding.layoutSequenceDelayOverlay.visibility = View.VISIBLE
        binding.progressSequenceDelay.progress = 0
        binding.textSequenceDelayTime.text = String.format("%.1fs", delayMs / 1000.0)
        
        binding.textSequenceDelayStatus.text = "Next sequence in..."
        binding.textSequenceDelayTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_blue))
        
        // Fade in animation
        binding.layoutSequenceDelayOverlay.alpha = 0f
        binding.layoutSequenceDelayOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        
        // Start updating progress
        updateSequenceDelayProgress()
    }
    
    /**
     * Hide the sequence delay progress bar
     */
    private fun hideSequenceDelayProgress() {
        if (_binding == null) return
        
        // Fade out animation
        binding.layoutSequenceDelayOverlay.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                if (_binding != null) {
                    binding.layoutSequenceDelayOverlay.visibility = View.GONE
                }
            }
            .start()
        
        // Cancel any running progress updates
        delayHandler?.removeCallbacks(delayRunnable ?: return)
        delayHandler = null
        delayRunnable = null
    }
    
    /**
     * Update the sequence delay progress bar
     */
    private fun updateSequenceDelayProgress() {
        if (_binding == null || totalDelayTime <= 0) return
        
        delayHandler = Handler(Looper.getMainLooper())
        delayRunnable = Runnable {
            if (_binding != null && binding.layoutSequenceDelayOverlay.visibility == View.VISIBLE) {
                val elapsed = System.currentTimeMillis() - delayStartTime
                val progress = (elapsed.toFloat() / totalDelayTime * 100).toInt().coerceIn(0, 100)
                val remaining = ((totalDelayTime - elapsed) / 1000.0).coerceAtLeast(0.0)
                
                binding.progressSequenceDelay.progress = progress
                binding.textSequenceDelayTime.text = String.format("%.1fs", remaining)
                
                // Continue updating if not finished
                if (progress < 100 && binding.layoutSequenceDelayOverlay.visibility == View.VISIBLE) {
                    delayHandler?.postDelayed(delayRunnable!!, 50) // Update every 50ms
                }
            }
        }
        
        delayHandler?.post(delayRunnable!!)
    }
    
    /**
     * Schedule the next sequence with optional delay.
     */
    private fun scheduleNextSequence(delayMs: Long) {
        android.util.Log.d("ListenFragment", "scheduleNextSequence called with delay: ${delayMs}ms")
        
        if (delayMs == 0L) {
            android.util.Log.d("ListenFragment", "Zero delay - starting immediately")
            // No delay - start immediately
            if (!isDetached && _binding != null && currentState == ListeningState.REVEALED) {
                startNewSequence()
            }
        } else {
            android.util.Log.d("ListenFragment", "Showing progress bar for ${delayMs}ms delay")
            // Show progress bar and schedule next sequence with delay
            showSequenceDelayProgress(delayMs)
            
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isDetached && _binding != null && currentState == ListeningState.REVEALED) {
                    hideSequenceDelayProgress()
                    startNewSequence()
                }
            }, delayMs)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.US)
            
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                android.util.Log.e("ListenFragment", "Language not supported")
            } else {
                // Set speech pitch (keep normal)
                textToSpeech.setPitch(1.0f)
            }
        } else {
            android.util.Log.e("ListenFragment", "TextToSpeech initialization failed")
        }
    }

    // ScreenStateCallback implementation
    override fun onScreenOn() {
        // Screen turned on, no need to do anything special
    }
    
    override fun onScreenOff() {
        // Screen turned off but we want to keep playing
        // If we're playing audio, make sure the foreground service is running
        if (isAudioPlaying && !AudioForegroundService.isRunning() && AppState.isAppInForeground) {
            startAudioForegroundService()
        }
    }

    /**
     * Start the audio foreground service
     */
    private fun startAudioForegroundService() {
        val intent = Intent(requireContext(), AudioForegroundService::class.java)
        intent.action = AudioForegroundService.ACTION_START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intent)
        } else {
            requireContext().startService(intent)
        }
    }
    
    /**
     * Stop the audio foreground service
     */
    private fun stopAudioForegroundService() {
        val intent = Intent(requireContext(), AudioForegroundService::class.java)
        intent.action = AudioForegroundService.ACTION_STOP
        requireContext().startService(intent)
    }
    
    /**
     * Pause the audio foreground service
     */
    private fun pauseAudioForegroundService() {
        val intent = Intent(requireContext(), AudioForegroundService::class.java)
        intent.action = AudioForegroundService.ACTION_PAUSE
        requireContext().startService(intent)
    }
    
    /**
     * Resume the audio foreground service
     */
    private fun resumeAudioForegroundService() {
        val intent = Intent(requireContext(), AudioForegroundService::class.java)
        intent.action = AudioForegroundService.ACTION_RESUME
        requireContext().startService(intent)
    }
} 