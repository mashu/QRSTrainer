package com.so5km.qrstrainer.ui.listen

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.so5km.qrstrainer.databinding.FragmentListenBinding
import com.so5km.qrstrainer.state.StoreViewModel
import com.so5km.qrstrainer.state.AppAction
import com.so5km.qrstrainer.state.ListeningState
import com.so5km.qrstrainer.data.ProgressTracker
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.training.SequenceGenerator
import com.so5km.qrstrainer.audio.AudioManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import java.util.Locale

class ListenFragment : Fragment(), TextToSpeech.OnInitListener {
    
    private var _binding: FragmentListenBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var storeViewModel: StoreViewModel
    private lateinit var progressTracker: ProgressTracker
    private lateinit var sequenceGenerator: SequenceGenerator
    private lateinit var audioManager: AudioManager
    private lateinit var textToSpeech: TextToSpeech
    
    private var currentSequence = ""
    private var sequenceCount = 0
    private var audioPlaybackJob: Job? = null
    private var autoRevealJob: Job? = null
    private var ttsJob: Job? = null
    private var isTtsSpeaking = false // Tracks TTS state to coordinate with audio playback
    private lateinit var sequenceCoordinator: ListenSequenceCoordinator
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListenBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeComponents()
        setupUI()
        observeState()
        setupAnimations()
    }
    
    private fun initializeComponents() {
        storeViewModel = ViewModelProvider(requireActivity())[StoreViewModel::class.java]
        progressTracker = ProgressTracker(requireContext())
        sequenceGenerator = SequenceGenerator(progressTracker)
        audioManager = AudioManager(requireContext())
        textToSpeech = TextToSpeech(requireContext(), this)
        
        // Initialize sequence coordinator with proper event callbacks
        sequenceCoordinator = ListenSequenceCoordinator(
            onStartNextSequence = { nextSequence() },
            onStartTTS = { sequence, settings ->
                // Start TTS directly without revealing again
                speakSequence(sequence, settings)
            }
        )
    }
    
    private fun setupUI() {
        // Setup control buttons
        binding.listenControls.apply {
            onStartClick = { startListening() }
            onStopClick = { stopListening() }
            onRevealClick = { revealSequence() }
            onNextClick = { nextSequence() }
            onReplayClick = { replaySequence() }
        }
        

        
        // Initial state
        updateUIForState(ListeningState.READY)
        binding.listenControls.updateState(ListeningState.READY, false)
        updateProgress()
    }
    
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            storeViewModel.state.collect { appState ->
                val listenState = appState.listenState
                updateUIForListenState(listenState.state, listenState.isRevealed)
            }
        }
        

    }
    
    private fun startListening() {
        // Stop any ongoing coordination - user wants to start new sequence
        sequenceCoordinator.cancel()
        
        // Stop TTS if speaking
        if (isTtsSpeaking && ::textToSpeech.isInitialized) {
            android.util.Log.d("ListenFragment", "Stopping TTS for new sequence")
            textToSpeech.stop()
            isTtsSpeaking = false
        }
        
        // Start the coordinator for this session
        sequenceCoordinator.start()
        
        startListeningInternal()
    }
    
    private fun startListeningInternal() {
        try {
            val settings = storeViewModel.settings.value
            // Create listen-specific settings for sequence generation
            val listenSettings = settings.copy(
                currentLevel = settings.listenCurrentLevel,  // Use listen-specific level!
                wpm = settings.listenWpm,
                effectiveWpm = settings.listenEffectiveWpm,
                minGroupSize = settings.listenMinGroupSize,
                maxGroupSize = settings.listenMaxGroupSize,
                sequenceLength = settings.listenSequenceLength,
                minSequenceLength = settings.listenMinSequenceLength,
                maxSequenceLength = settings.listenMaxSequenceLength,
                numberOfRepeats = settings.listenNumberOfRepeats,
                groupDelayMs = settings.listenGroupDelayMs,
                repeatDelayMs = settings.listenRepeatDelayMs
            )
            
            currentSequence = sequenceGenerator.generateGroupSequence(listenSettings)
            
            android.util.Log.d("ListenFragment", "Starting listen mode with sequence: '$currentSequence'")
            android.util.Log.d("ListenFragment", "Settings - WPM: ${settings.wpm}, Frequency: ${settings.frequency}, Volume: ${settings.volume}")
            
            // Dispatch action to update state
            storeViewModel.dispatch(AppAction.StartListening(currentSequence))
            
            // Start continuous noise if enabled
            if (settings.noiseEnabled) {
                android.util.Log.d("ListenFragment", "Starting continuous noise")
                audioManager.startContinuousNoise(settings)
            }
            
            // Update UI
            binding.textSequence.text = "🎵 Listening..."
            updateUIForState(ListeningState.PLAYING)
            
            // Set up audio completion callback for listen mode
            val audioCompletionListener = object : com.so5km.qrstrainer.audio.AudioCompletionListener {
                override fun onSequenceCompleted() {
                    android.util.Log.d("ListenFragment", "Audio sequence completed - revealing and starting coordination")
                    
                    // After audio finishes, just reveal the sequence text (no TTS logic)
                    storeViewModel.dispatch(AppAction.RevealSequence)
                    audioManager.stopContinuousNoise()
                    binding.textSequence.text = currentSequence
                    updateUIForState(ListeningState.REVEALED)
                    
                    // Now let coordinator handle ALL timing and TTS coordination
                    sequenceCoordinator.onSequencePlaybackComplete(currentSequence, settings)
                }
                
                override fun onPlaybackStopped() {
                    android.util.Log.d("ListenFragment", "Audio playback stopped")
                    updateUIForState(ListeningState.READY)
                }
                
                override fun onPlaybackError(error: Exception) {
                    android.util.Log.e("ListenFragment", "Audio playback error", error)
                    updateUIForState(ListeningState.READY)
                    binding.textSequence.text = "Audio error: ${error.message}"
                }
            }
            
            // Start audio playback with completion callback
            audioPlaybackJob?.cancel() // Cancel any ongoing playback
            audioPlaybackJob = lifecycleScope.launch {
                try {
                    android.util.Log.d("ListenFragment", "About to play sequence")
                    
                    // Set up the completion listener before starting audio
                    audioManager.setAudioCompletionListener(audioCompletionListener)
                    
                    // Start audio playback (non-blocking)
                    audioManager.playSequence(currentSequence, listenSettings)
                    
                    android.util.Log.d("ListenFragment", "Audio playback started - waiting for completion callback")
                } catch (e: Exception) {
                    android.util.Log.e("ListenFragment", "Error starting audio playback", e)
                    updateUIForState(ListeningState.READY)
                    binding.textSequence.text = "Audio error: ${e.message}"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ListenFragment", "Error starting listen mode", e)
            binding.textSequence.text = "Error starting listen mode: ${e.message}"
        }
    }
    
    private fun stopListening() {
        try {
            android.util.Log.d("ListenFragment", "Stopping listen mode")
            
            // CRITICAL: Clear completion listener FIRST to prevent callback interference
            audioManager.setAudioCompletionListener(null)
            
            // Cancel any ongoing audio playback
            audioPlaybackJob?.cancel()
            audioPlaybackJob = null
            
            // Stop sequence coordinator
            sequenceCoordinator.stop()
            
            // Cancel any ongoing TTS coordination
            ttsJob?.cancel()
            ttsJob = null
            
            // Stop audio manager and TTS (now safe from callbacks)
            audioManager.stopPlayback()
            audioManager.stopContinuousNoise()
            
            if (::textToSpeech.isInitialized) {
                textToSpeech.stop()
                isTtsSpeaking = false
            }
            
            // Reset state
            storeViewModel.dispatch(AppAction.NextSequence)
            updateUIForState(ListeningState.READY)
            binding.textSequence.text = "Ready"
            
            android.util.Log.d("ListenFragment", "Listen mode stopped successfully")
        } catch (e: Exception) {
            android.util.Log.e("ListenFragment", "Error stopping listen mode", e)
            updateUIForState(ListeningState.READY)
            binding.textSequence.text = "Ready"
        }
    }
    
    private fun revealSequence() {
        try {
            android.util.Log.d("ListenFragment", "Manual reveal requested")
            
            // Clear completion listener to prevent callback interference
            audioManager.setAudioCompletionListener(null)
            
            // Cancel coordinator since user manually revealed
            sequenceCoordinator.cancel()
            
            // Cancel any TTS
            if (isTtsSpeaking && ::textToSpeech.isInitialized) {
                textToSpeech.stop()
                isTtsSpeaking = false
            }
            
            // Just reveal the sequence - no automatic TTS or progression
            storeViewModel.dispatch(AppAction.RevealSequence)
            audioManager.stopContinuousNoise()
            binding.textSequence.text = currentSequence
            updateUIForState(ListeningState.REVEALED)
            
            android.util.Log.d("ListenFragment", "Sequence manually revealed - no auto-progression")
        } catch (e: Exception) {
            android.util.Log.e("ListenFragment", "Error revealing sequence", e)
            binding.textSequence.text = "Error revealing sequence: ${e.message}"
        }
    }
    
    private fun nextSequence() {
        // Clear completion listener to prevent callback interference
        audioManager.setAudioCompletionListener(null)
        
        // Cancel any ongoing coordination - user wants to advance manually
        sequenceCoordinator.cancel()
        
        // Stop TTS if speaking
        if (isTtsSpeaking && ::textToSpeech.isInitialized) {
            android.util.Log.d("ListenFragment", "User requested next sequence - stopping TTS")
            textToSpeech.stop()
            isTtsSpeaking = false
        }
        
        try {
            android.util.Log.d("ListenFragment", "Moving to next sequence")
            
            storeViewModel.dispatch(AppAction.NextSequence)
            audioManager.stopContinuousNoise() // Stop background noise when moving to next
            sequenceCount++
            
            // Check for auto-leveling in listen mode
            val currentSettings = storeViewModel.settings.value
            if (currentSettings.listenSequencesToLevelUp > 0 && 
                sequenceCount % currentSettings.listenSequencesToLevelUp == 0) {
                
                val newLevel = minOf(currentSettings.listenCurrentLevel + 1, 40) // Max level 40
                android.util.Log.d("ListenFragment", "Auto-leveling: sequenceCount=$sequenceCount, threshold=${currentSettings.listenSequencesToLevelUp}, advancing from level ${currentSettings.listenCurrentLevel} to $newLevel")
                
                if (newLevel > currentSettings.listenCurrentLevel) {
                    val updatedSettings = currentSettings.copy(listenCurrentLevel = newLevel)
                    storeViewModel.dispatch(AppAction.UpdateSettings(updatedSettings))
                }
            }
            
            updateProgress()
            
            // Auto-start next sequence after configurable delay
            val settings = storeViewModel.settings.value
            lifecycleScope.launch {
                delay(settings.listenNextDelayMs)
                android.util.Log.d("ListenFragment", "Auto-starting next sequence")
                startListening()
            }
        } catch (e: Exception) {
            android.util.Log.e("ListenFragment", "Error moving to next sequence", e)
            updateUIForState(ListeningState.READY)
            binding.textSequence.text = "Ready"
        }
    }
    
    private fun replaySequence() {
        // Clear completion listener to prevent callback interference
        audioManager.setAudioCompletionListener(null)
        
        // Cancel any ongoing coordination - user wants to replay
        sequenceCoordinator.cancel()
        
        // Stop TTS if speaking
        if (isTtsSpeaking && ::textToSpeech.isInitialized) {
            android.util.Log.d("ListenFragment", "TTS is speaking - stopping TTS for immediate replay")
            textToSpeech.stop()
            isTtsSpeaking = false
        }
        
        replaySequenceInternal()
    }
    
    private fun replaySequenceInternal() {
        if (currentSequence.isNotEmpty()) {
            try {
                val settings = storeViewModel.settings.value
                // Create listen-specific settings for replay
                val listenSettings = settings.copy(
                    currentLevel = settings.listenCurrentLevel,  // Use listen-specific level!
                    wpm = settings.listenWpm,
                    effectiveWpm = settings.listenEffectiveWpm,
                    minGroupSize = settings.listenMinGroupSize,
                    maxGroupSize = settings.listenMaxGroupSize,
                    sequenceLength = settings.listenSequenceLength,
                    minSequenceLength = settings.listenMinSequenceLength,
                    maxSequenceLength = settings.listenMaxSequenceLength,
                    numberOfRepeats = settings.listenNumberOfRepeats,
                    groupDelayMs = settings.listenGroupDelayMs,
                    repeatDelayMs = settings.listenRepeatDelayMs
                )
                
                android.util.Log.d("ListenFragment", "Replaying sequence: '$currentSequence'")
                
                binding.textSequence.text = "🎵 Replaying..."
                updateUIForState(ListeningState.PLAYING)
                
                // Set up audio completion callback for replay
                val replayCompletionListener = object : com.so5km.qrstrainer.audio.AudioCompletionListener {
                    override fun onSequenceCompleted() {
                        android.util.Log.d("ListenFragment", "Replay completed")
                        
                        // After replay, go back to previous state (no TTS for replays)
                        val currentState = storeViewModel.state.value.listenState
                        if (currentState.isRevealed) {
                            updateUIForState(ListeningState.REVEALED)
                            binding.textSequence.text = currentSequence
                        } else {
                            updateUIForState(ListeningState.WAITING)
                            binding.textSequence.text = "What did you hear?"
                        }
                    }
                    
                    override fun onPlaybackStopped() {
                        android.util.Log.d("ListenFragment", "Replay stopped")
                        updateUIForState(ListeningState.WAITING)
                    }
                    
                    override fun onPlaybackError(error: Exception) {
                        android.util.Log.e("ListenFragment", "Replay error", error)
                        binding.textSequence.text = "Replay error: ${error.message}"
                        updateUIForState(ListeningState.WAITING)
                    }
                }
                
                audioPlaybackJob?.cancel() // Cancel any ongoing playback
                audioPlaybackJob = lifecycleScope.launch {
                    try {
                        // Set up completion listener for replay
                        audioManager.setAudioCompletionListener(replayCompletionListener)
                        
                        // Start replay (non-blocking)
                        audioManager.playSequence(currentSequence, listenSettings)
                        android.util.Log.d("ListenFragment", "Replay started - waiting for completion")
                    } catch (e: Exception) {
                        android.util.Log.e("ListenFragment", "Error starting replay", e)
                        binding.textSequence.text = "Replay error: ${e.message}"
                        updateUIForState(ListeningState.WAITING)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ListenFragment", "Error starting replay", e)
                binding.textSequence.text = "Error starting replay: ${e.message}"
            }
        } else {
            android.util.Log.w("ListenFragment", "Cannot replay - no current sequence")
            binding.textSequence.text = "No sequence to replay"
        }
    }
    
    private fun updateUIForListenState(state: ListeningState, isRevealed: Boolean) {
        binding.listenControls.updateState(state, isRevealed)
        updateUIForState(state)
    }
    
    private fun updateUIForState(state: ListeningState) {
        when (state) {
            ListeningState.READY -> {
                binding.textSequence.text = "Ready"
                binding.progressDelay.alpha = 0.0f
            }
            ListeningState.PLAYING -> {
                binding.textSequence.text = "🎵 Listening..."
                binding.progressDelay.alpha = 0.0f
            }
            ListeningState.WAITING -> {
                binding.textSequence.text = "What did you hear?"
                // Auto-reveal countdown will start if enabled
            }
            ListeningState.REVEALED -> {
                // Show different text if TTS is speaking
                if (isTtsSpeaking) {
                    binding.textSequence.text = "🗣️ $currentSequence"
                } else {
                    binding.textSequence.text = "$currentSequence"
                }
                binding.progressDelay.alpha = 0.0f
            }
            ListeningState.PAUSED -> {
                binding.progressDelay.alpha = 0.0f
            }
        }
    }
    
    // Auto-reveal countdown removed - coordinator handles all timing
    
    private fun updateProgress() {
        val level = storeViewModel.settings.value.listenCurrentLevel
        
        binding.textLevel.text = "Listen Level: $level"
    }
    
    // scheduleAutoAdvance is now handled by ListenSequenceCoordinator
    
    private fun setupAnimations() {
        // Entrance animations
        val views = listOf(
            binding.textTitle,
            binding.textSequence,
            binding.listenControls,
            binding.textLevel
        )
        
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay((index * 100).toLong())
                .start()
        }
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Language not supported
                android.util.Log.w("ListenFragment", "TTS language not supported")
            } else {
                android.util.Log.d("ListenFragment", "TTS initialized successfully")
            }
        } else {
            android.util.Log.e("ListenFragment", "TTS initialization failed with status: $status")
        }
    }
    
    private fun speakSequence(sequence: String, settings: TrainingSettings) {
        try {
            android.util.Log.d("ListenFragment", "speakSequence called with sequence: '$sequence'")
            
            if (!::textToSpeech.isInitialized) {
                android.util.Log.e("ListenFragment", "TTS not initialized - cannot speak")
                // Notify coordinator that TTS failed so sequence can continue
                sequenceCoordinator.onTTSComplete(settings)
                return
            }
            
            android.util.Log.d("ListenFragment", "TTS is initialized, checking if already speaking...")
            if (textToSpeech.isSpeaking) {
                android.util.Log.w("ListenFragment", "TTS already speaking, stopping previous speech")
                textToSpeech.stop()
            }
            
            // Configure TTS parameters
            android.util.Log.d("ListenFragment", "Configuring TTS - rate: ${settings.ttsSpeechRate}, pitch: ${settings.ttsPitch}, volume: ${settings.ttsVolume}")
            textToSpeech.setSpeechRate(settings.ttsSpeechRate)
            textToSpeech.setPitch(settings.ttsPitch)
            
            // Set up TTS completion listener using modern API
            textToSpeech.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    android.util.Log.d("ListenFragment", "TTS started speaking for utterance: $utteranceId")
                    isTtsSpeaking = true
                    
                    // Update UI to show TTS is speaking
                    lifecycleScope.launch {
                        val currentState = storeViewModel.state.value.listenState.state
                        if (currentState == ListeningState.REVEALED) {
                            binding.textSequence.text = "🗣️ $currentSequence"
                        }
                    }
                }
                
                override fun onDone(utteranceId: String?) {
                    android.util.Log.d("ListenFragment", "TTS finished speaking for utterance: $utteranceId")
                    isTtsSpeaking = false
                    
                    // Update UI to remove TTS indicator
                    lifecycleScope.launch {
                        val currentState = storeViewModel.state.value.listenState.state
                        if (currentState == ListeningState.REVEALED) {
                            binding.textSequence.text = "$currentSequence"
                        }
                    }
                    
                    // Notify coordinator that TTS is complete
                    sequenceCoordinator.onTTSComplete(settings)
                }
                
                override fun onError(utteranceId: String?) {
                    // Deprecated method - delegate to modern method
                    onError(utteranceId, -1)
                }
                
                override fun onError(utteranceId: String?, errorCode: Int) {
                    android.util.Log.e("ListenFragment", "TTS error occurred for utterance: $utteranceId, errorCode: $errorCode")
                    isTtsSpeaking = false
                    
                    // Update UI to remove TTS indicator
                    lifecycleScope.launch {
                        val currentState = storeViewModel.state.value.listenState.state
                        if (currentState == ListeningState.REVEALED) {
                            binding.textSequence.text = "$currentSequence"
                        }
                    }
                    
                    // TTS failed - notify coordinator to continue sequence
                    android.util.Log.d("ListenFragment", "TTS error - notifying coordinator to continue")
                    sequenceCoordinator.onTTSComplete(settings)
                }
                
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    android.util.Log.d("ListenFragment", "TTS stopped for utterance: $utteranceId, interrupted: $interrupted")
                    isTtsSpeaking = false
                    
                    // Update UI to remove TTS indicator
                    lifecycleScope.launch {
                        val currentState = storeViewModel.state.value.listenState.state
                        if (currentState == ListeningState.REVEALED) {
                            binding.textSequence.text = "$currentSequence"
                        }
                    }
                    
                    // Only notify coordinator if TTS stopped naturally (not interrupted by user)
                    if (!interrupted) {
                        android.util.Log.d("ListenFragment", "TTS stopped naturally - notifying coordinator")
                        sequenceCoordinator.onTTSComplete(settings)
                    } else {
                        android.util.Log.d("ListenFragment", "TTS stopped by user - not continuing sequence")
                    }
                }
            })
            
            // Speak the sequence as individual characters with spaces
            val spokenText = sequence.toCharArray().joinToString(" ")
            android.util.Log.d("ListenFragment", "About to speak TTS text: '$spokenText'")
            
            val params = android.os.Bundle()
            val utteranceId = "listen_sequence_${System.currentTimeMillis()}"
            
            val result = textToSpeech.speak(spokenText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            
            android.util.Log.d("ListenFragment", "TTS speak() call result: $result")
            if (result == TextToSpeech.ERROR) {
                android.util.Log.e("ListenFragment", "TTS speak() returned ERROR")
                isTtsSpeaking = false
                // Notify coordinator that TTS failed so sequence can continue
                sequenceCoordinator.onTTSComplete(settings)
            } else {
                android.util.Log.d("ListenFragment", "TTS speak() called successfully, should be speaking now")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ListenFragment", "Exception in speakSequence", e)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        
        // Cancel any ongoing audio playback
        audioPlaybackJob?.cancel()
        audioPlaybackJob = null
        
        // Cancel any ongoing auto-reveal countdown
        autoRevealJob?.cancel()
        autoRevealJob = null
        
        // Cancel any ongoing TTS job
        ttsJob?.cancel()
        ttsJob = null
        
        // Stop sequence coordinator
        sequenceCoordinator.stop()
        
        // Clean up TTS
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
            isTtsSpeaking = false
        }
        
        // Clean up audio manager
        audioManager.setAudioCompletionListener(null) // Remove listener to prevent leaks
        audioManager.release()
        
        _binding = null
    }
}
