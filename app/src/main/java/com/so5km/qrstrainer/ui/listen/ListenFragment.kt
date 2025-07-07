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
import com.so5km.qrstrainer.training.SequenceGenerator
import com.so5km.qrstrainer.audio.AudioManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
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
    private var isAutoRevealEnabled = true
    private var isSpeakEnabled = false
    private var sequenceCount = 0
    
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
            onRevealClick = { revealSequence() }
            onNextClick = { nextSequence() }
            onReplayClick = { replaySequence() }
        }
        
        // Setup switches
        binding.switchAutoReveal.apply {
            isChecked = isAutoRevealEnabled
            setOnCheckedChangeListener { _, isChecked ->
                isAutoRevealEnabled = isChecked
            }
        }
        
        binding.switchSpeak.apply {
            isChecked = isSpeakEnabled
            setOnCheckedChangeListener { _, isChecked ->
                isSpeakEnabled = isChecked
            }
        }
        
        // Initial state
        updateUIForState(ListeningState.READY)
        updateProgress()
    }
    
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            storeViewModel.state.collect { appState ->
                val listenState = appState.listenState
                updateUIForListenState(listenState.state, listenState.isRevealed)
                
                if (appState.audioState.isPlaying) {
                    // Audio is playing
                    binding.progressDelay.visibility = View.GONE
                } else {
                    // Audio stopped, start auto-reveal countdown if enabled
                    if (isAutoRevealEnabled && listenState.state == ListeningState.WAITING) {
                        startAutoRevealCountdown()
                    }
                }
            }
        }
    }
    
    private fun startListening() {
        val settings = storeViewModel.settings.value
        currentSequence = sequenceGenerator.generateSequence(
            settings.sequenceLength,
            progressTracker.getCurrentLevel()
        )
        
        // Dispatch action to update state
        storeViewModel.dispatch(AppAction.StartListening(currentSequence))
        
        // Update UI
        binding.textSequence.text = "🎵 Listen to the sequence..."
        
        // Play the sequence
        lifecycleScope.launch {
            try {
                audioManager.playSequence(currentSequence, settings)
                // After audio finishes, switch to waiting state
                delay(500)
                updateUIForState(ListeningState.WAITING)
            } catch (e: Exception) {
                updateUIForState(ListeningState.READY)
                binding.textSequence.text = "Audio error. Try again."
            }
        }
    }
    
    private fun revealSequence() {
        storeViewModel.dispatch(AppAction.RevealSequence)
        
        binding.textSequence.text = "Sequence: $currentSequence"
        
        // Speak the sequence if enabled
        if (isSpeakEnabled) {
            speakSequence(currentSequence)
        }
        
        updateUIForState(ListeningState.REVEALED)
    }
    
    private fun nextSequence() {
        storeViewModel.dispatch(AppAction.NextSequence)
        sequenceCount++
        updateProgress()
        
        // Auto-start next sequence after short delay
        lifecycleScope.launch {
            delay(500)
            startListening()
        }
    }
    
    private fun replaySequence() {
        if (currentSequence.isNotEmpty()) {
            val settings = storeViewModel.settings.value
            binding.textSequence.text = "🎵 Listen to the sequence..."
            
            lifecycleScope.launch {
                try {
                    audioManager.playSequence(currentSequence, settings)
                } catch (e: Exception) {
                    binding.textSequence.text = "Audio error. Try again."
                }
            }
        }
    }
    
    private fun updateUIForListenState(state: ListeningState, isRevealed: Boolean) {
        binding.listenControls.updateState(state, isRevealed)
        updateUIForState(state)
    }
    
    private fun updateUIForState(state: ListeningState) {
        when (state) {
            ListeningState.READY -> {
                binding.textSequence.text = "Ready to listen..."
                binding.progressDelay.visibility = View.GONE
            }
            ListeningState.PLAYING -> {
                binding.textSequence.text = "🎵 Listen to the sequence..."
                binding.progressDelay.visibility = View.GONE
            }
            ListeningState.WAITING -> {
                binding.textSequence.text = "What did you hear?"
                // Auto-reveal countdown will start if enabled
            }
            ListeningState.REVEALED -> {
                binding.textSequence.text = "Sequence: $currentSequence"
                binding.progressDelay.visibility = View.GONE
            }
            ListeningState.PAUSED -> {
                binding.progressDelay.visibility = View.GONE
            }
        }
    }
    
    private fun startAutoRevealCountdown() {
        if (!isAutoRevealEnabled) return
        
        binding.progressDelay.visibility = View.VISIBLE
        binding.progressDelay.max = 100
        
        lifecycleScope.launch {
            val delayMs = 3000L // 3 second delay
            val steps = 30
            val stepDelay = delayMs / steps
            
            for (i in steps downTo 0) {
                binding.progressDelay.progress = (i * 100) / steps
                delay(stepDelay)
                
                // Check if state changed (user manually revealed or moved on)
                val currentState = storeViewModel.state.value.listenState.state
                if (currentState != ListeningState.WAITING) {
                    break
                }
            }
            
            // Auto-reveal if still in waiting state
            val currentState = storeViewModel.state.value.listenState.state
            if (currentState == ListeningState.WAITING) {
                revealSequence()
            }
            
            binding.progressDelay.visibility = View.GONE
        }
    }
    
    private fun updateProgress() {
        val level = progressTracker.getCurrentLevel()
        val streak = progressTracker.getCurrentStreak()
        
        binding.textLevel.text = "Level: $level"
        // Now actually using the streak variable instead of sequenceCount
        binding.textStreak.text = "Streak: $streak • Sessions: $sequenceCount"
    }
    
    private fun setupAnimations() {
        // Entrance animations
        val views = listOf(
            binding.textTitle,
            binding.textSequence,
            binding.listenControls,
            binding.textLevel,
            binding.textStreak
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
                // Language not supported, disable speak feature
                binding.switchSpeak.isEnabled = false
                binding.switchSpeak.alpha = 0.5f
            }
        }
    }
    
    private fun speakSequence(sequence: String) {
        if (::textToSpeech.isInitialized && textToSpeech.isSpeaking.not()) {
            // Speak each character with pauses
            val spokenText = sequence.toCharArray().joinToString(", ")
            textToSpeech.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        audioManager.release()
        _binding = null
    }
}
