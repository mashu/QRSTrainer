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
        // Don't start new audio while TTS is speaking - wait for it to finish
        if (isTtsSpeaking) {
            android.util.Log.d("ListenFragment", "TTS is speaking - waiting for completion before starting audio")
            
            // Show user feedback that we're waiting for TTS
            binding.textSequence.text = "🗣️ Waiting for speech to finish..."
            
            // Wait for TTS to complete, then start listening
            lifecycleScope.launch {
                var waitTime = 0L
                val maxWaitTime = 15000L // 15 second timeout
                
                while (isTtsSpeaking && waitTime < maxWaitTime) {
                    delay(200)
                    waitTime += 200
                }
                
                if (isTtsSpeaking) {
                    android.util.Log.w("ListenFragment", "TTS timeout - proceeding with audio")
                    isTtsSpeaking = false
                }
                
                // Now start listening after TTS completed
                startListeningInternal()
            }
            return
        }
        
        startListeningInternal()
    }
    
    private fun startListeningInternal() {
        try {
            val settings = storeViewModel.settings.value
            // Create listen-specific settings for sequence generation
            val listenSettings = settings.copy(
                wpm = settings.listenWpm,
                effectiveWpm = settings.listenEffectiveWpm,
                minGroupSize = settings.listenMinGroupSize,
                maxGroupSize = settings.listenMaxGroupSize,
                sequenceLength = settings.listenSequenceLength,
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
            
            // Play the sequence
            audioPlaybackJob?.cancel() // Cancel any ongoing playback
            audioPlaybackJob = lifecycleScope.launch {
                try {
                    android.util.Log.d("ListenFragment", "About to play sequence")
                    audioManager.playSequence(currentSequence, listenSettings)
                    android.util.Log.d("ListenFragment", "Sequence playback completed")
                    
                    // After audio finishes, switch to waiting state
                    delay(settings.listenSequenceDelayMs)
                    android.util.Log.d("ListenFragment", "Setting state to WAITING")
                    storeViewModel.dispatch(AppAction.SetListeningWaiting)
                    updateUIForState(ListeningState.WAITING)
                    
                    // Start auto-reveal countdown if enabled
                    val currentSettings = storeViewModel.settings.value
                    if (currentSettings.autoRevealEnabled) {
                        android.util.Log.d("ListenFragment", "Starting auto-reveal countdown")
                        startAutoRevealCountdown()
                    } else {
                        android.util.Log.d("ListenFragment", "Auto-reveal disabled, showing reveal button")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ListenFragment", "Error playing sequence", e)
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
            
            // Cancel any ongoing audio playback
            audioPlaybackJob?.cancel()
            audioPlaybackJob = null
            
            // Cancel any ongoing TTS coordination
            ttsJob?.cancel()
            ttsJob = null
            
            // Stop audio manager and TTS
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
            android.util.Log.d("ListenFragment", "Revealing sequence: '$currentSequence'")
            
            // Cancel any previous TTS job
            ttsJob?.cancel()
            ttsJob = null
            
            storeViewModel.dispatch(AppAction.RevealSequence)
            audioManager.stopContinuousNoise() // Stop background noise when revealing
            
            binding.textSequence.text = "$currentSequence"
            updateUIForState(ListeningState.REVEALED)
            
            // Handle TTS and auto-advance coordination
            val settings = storeViewModel.settings.value
            android.util.Log.d("ListenFragment", "Checking TTS settings - speakInListen: ${settings.ttsSpeakInListenMode}, delay: ${settings.ttsDelayMs}ms")
            
            // Handle TTS if enabled
            if (settings.ttsSpeakInListenMode) {
                android.util.Log.d("ListenFragment", "TTS enabled - will speak sequence '$currentSequence' after ${settings.ttsDelayMs}ms delay")
                
                ttsJob = lifecycleScope.launch {
                    delay(settings.ttsDelayMs)
                    
                    // Check if we're still in revealed state
                    val currentState = storeViewModel.state.value.listenState.state
                    if (currentState == ListeningState.REVEALED) {
                        android.util.Log.d("ListenFragment", "Starting TTS speech for sequence: '$currentSequence'")
                        speakSequence(currentSequence, settings)
                        // Auto-advance will be triggered by TTS completion callback
                    }
                }
            } else {
                android.util.Log.d("ListenFragment", "TTS disabled - proceeding with auto-advance")
                scheduleAutoAdvance()
            }
        } catch (e: Exception) {
            android.util.Log.e("ListenFragment", "Error revealing sequence", e)
            binding.textSequence.text = "Error revealing sequence: ${e.message}"
        }
    }
    
    private fun nextSequence() {
        // Allow next sequence even during TTS - user wants to move on
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
        // Don't replay while TTS is speaking - wait for it to finish
        if (isTtsSpeaking) {
            android.util.Log.d("ListenFragment", "TTS is speaking - waiting for completion before replaying")
            
            // Show user feedback
            binding.textSequence.text = "🗣️ Waiting for speech to finish..."
            
            // Wait for TTS to complete, then replay
            lifecycleScope.launch {
                var waitTime = 0L
                val maxWaitTime = 15000L // 15 second timeout
                
                while (isTtsSpeaking && waitTime < maxWaitTime) {
                    delay(200)
                    waitTime += 200
                }
                
                if (isTtsSpeaking) {
                    android.util.Log.w("ListenFragment", "TTS timeout - proceeding with replay")
                    isTtsSpeaking = false
                }
                
                // Now replay after TTS completed
                replaySequenceInternal()
            }
            return
        }
        
        replaySequenceInternal()
    }
    
    private fun replaySequenceInternal() {
        if (currentSequence.isNotEmpty()) {
            try {
                val settings = storeViewModel.settings.value
                // Create listen-specific settings for replay
                val listenSettings = settings.copy(
                    wpm = settings.listenWpm,
                    effectiveWpm = settings.listenEffectiveWpm,
                    minGroupSize = settings.listenMinGroupSize,
                    maxGroupSize = settings.listenMaxGroupSize,
                    sequenceLength = settings.listenSequenceLength,
                    numberOfRepeats = settings.listenNumberOfRepeats,
                    groupDelayMs = settings.listenGroupDelayMs,
                    repeatDelayMs = settings.listenRepeatDelayMs
                )
                
                android.util.Log.d("ListenFragment", "Replaying sequence: '$currentSequence'")
                
                binding.textSequence.text = "🎵 Replaying..."
                updateUIForState(ListeningState.PLAYING)
                
                audioPlaybackJob?.cancel() // Cancel any ongoing playback
                audioPlaybackJob = lifecycleScope.launch {
                    try {
                        audioManager.playSequence(currentSequence, listenSettings)
                        android.util.Log.d("ListenFragment", "Replay completed")
                        
                        // After replay, go back to previous state
                        delay(settings.listenSequenceDelayMs)
                        val currentState = storeViewModel.state.value.listenState
                        if (currentState.isRevealed) {
                            updateUIForState(ListeningState.REVEALED)
                            binding.textSequence.text = "$currentSequence"
                        } else {
                            updateUIForState(ListeningState.WAITING)
                            binding.textSequence.text = "What did you hear?"
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ListenFragment", "Error replaying sequence", e)
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
    
    private fun startAutoRevealCountdown() {
        val settings = storeViewModel.settings.value
        if (!settings.autoRevealEnabled) {
            android.util.Log.d("ListenFragment", "Auto-reveal disabled, not starting countdown")
            return
        }
        
        // Cancel any existing countdown and TTS coordination
        autoRevealJob?.cancel()
        ttsJob?.cancel()
        ttsJob = null
        
        android.util.Log.d("ListenFragment", "Starting auto-reveal countdown")
        binding.progressDelay.alpha = 1.0f
        binding.progressDelay.max = 100
        
        autoRevealJob = lifecycleScope.launch {
            try {
                val delayMs = settings.autoRevealDelayMs
                val steps = 60  // More steps for smoother animation
                val stepDelay = delayMs / steps
                
                for (i in steps downTo 0) {
                    binding.progressDelay.progress = (i * 100) / steps
                    delay(stepDelay)
                    
                    // Check if state changed (user manually revealed or moved on)
                    val currentState = storeViewModel.state.value.listenState.state
                    if (currentState != ListeningState.WAITING) {
                        android.util.Log.d("ListenFragment", "Auto-reveal cancelled - state changed to $currentState")
                        break
                    }
                }
                
                // Auto-reveal if still in waiting state
                val currentState = storeViewModel.state.value.listenState.state
                if (currentState == ListeningState.WAITING) {
                    android.util.Log.d("ListenFragment", "Auto-reveal countdown completed - revealing sequence")
                    revealSequence()
                } else {
                    android.util.Log.d("ListenFragment", "Auto-reveal skipped - final state is $currentState")
                }
            } catch (e: Exception) {
                android.util.Log.e("ListenFragment", "Error in auto-reveal countdown", e)
            } finally {
                binding.progressDelay.alpha = 0.0f
            }
        }
    }
    
    private fun updateProgress() {
        val level = storeViewModel.settings.value.currentLevel
        
        binding.textLevel.text = "Level: $level"
    }
    
    private fun scheduleAutoAdvance() {
        val settings = storeViewModel.settings.value
        if (settings.autoRevealEnabled && settings.postRevealDelayMs > 0) {
            android.util.Log.d("ListenFragment", "Scheduling auto-advance after ${settings.postRevealDelayMs}ms")
            
            lifecycleScope.launch {
                delay(settings.postRevealDelayMs)
                
                // Check if we're still in revealed state (user didn't manually advance)
                val currentState = storeViewModel.state.value.listenState.state
                if (currentState == ListeningState.REVEALED) {
                    android.util.Log.d("ListenFragment", "Auto-advancing to next sequence")
                    nextSequence()
                }
            }
        }
    }
    
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
                    
                    // TTS is complete - now schedule auto-advance
                    android.util.Log.d("ListenFragment", "TTS complete - scheduling auto-advance")
                    scheduleAutoAdvance()
                }
                
                override fun onError(utteranceId: String?) {
                    android.util.Log.e("ListenFragment", "TTS error occurred for utterance: $utteranceId")
                    isTtsSpeaking = false
                    
                    // Update UI to remove TTS indicator
                    lifecycleScope.launch {
                        val currentState = storeViewModel.state.value.listenState.state
                        if (currentState == ListeningState.REVEALED) {
                            binding.textSequence.text = "$currentSequence"
                        }
                    }
                    
                    // TTS failed - still schedule auto-advance to avoid getting stuck
                    android.util.Log.d("ListenFragment", "TTS error - scheduling auto-advance anyway")
                    scheduleAutoAdvance()
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
                    
                    // Only schedule auto-advance if not interrupted by user action
                    if (!interrupted) {
                        android.util.Log.d("ListenFragment", "TTS stopped naturally - scheduling auto-advance")
                        scheduleAutoAdvance()
                    } else {
                        android.util.Log.d("ListenFragment", "TTS stopped by user - not scheduling auto-advance")
                    }
                }
            })
            
            // Speak each character with pauses - use modern Bundle API
            val spokenText = sequence.toCharArray().joinToString(", ")
            android.util.Log.d("ListenFragment", "About to speak TTS text: '$spokenText'")
            
            val params = android.os.Bundle()
            val utteranceId = "listen_sequence_${System.currentTimeMillis()}"
            
            val result = textToSpeech.speak(spokenText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            
            android.util.Log.d("ListenFragment", "TTS speak() call result: $result")
            if (result == TextToSpeech.ERROR) {
                android.util.Log.e("ListenFragment", "TTS speak() returned ERROR")
                isTtsSpeaking = false
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
        
        // Clean up TTS
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
            isTtsSpeaking = false
        }
        
        // Clean up audio manager
        audioManager.release()
        
        _binding = null
    }
}
