package com.so5km.qrstrainer.ui.trainer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.databinding.FragmentTrainerBinding
import com.so5km.qrstrainer.state.StoreViewModel
import com.so5km.qrstrainer.state.AppAction
import com.so5km.qrstrainer.state.TrainingState
import com.so5km.qrstrainer.state.TrainingStateData
import com.so5km.qrstrainer.data.ProgressTracker
import com.so5km.qrstrainer.training.SequenceGenerator
import com.so5km.qrstrainer.audio.AudioManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class TrainerFragment : Fragment() {
    
    private var _binding: FragmentTrainerBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var storeViewModel: StoreViewModel
    private lateinit var progressTracker: ProgressTracker
    private lateinit var sequenceGenerator: SequenceGenerator
    private lateinit var audioManager: AudioManager
    
    private var currentSequence = ""
    private var userInput = ""
    private var startTime: Long = 0
    private var keyboardLevel = -1 // Track the level the keyboard was built for
    
    companion object {
        private const val TAG = "TrainerFragment"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrainerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Log.d(TAG, "TrainerFragment onViewCreated called")
        
        // Ensure all views are visible first (fix for blank display issue)
        ensureViewsVisible()
        
        initializeComponents()
        setupUI()
        observeState()
        updateProgressDisplay()
        
        // Set initial state properly
        updateUIForState(TrainingState.READY)
        
        // Apply animations after everything is set up and visible
        setupAnimations()
        
        Log.d(TAG, "TrainerFragment initialization complete")
    }
    
    private fun ensureViewsVisible() {
        Log.d(TAG, "Ensuring all views are visible")
        val views = listOf(
            binding.progressIndicator,
            binding.sequenceDisplay,
            binding.controlPanel,
            binding.morseKeyboard
        )
        
        views.forEach { view ->
            view.alpha = 1f
            view.translationY = 0f
            view.visibility = View.VISIBLE
        }
    }
    
    private fun initializeComponents() {
        storeViewModel = ViewModelProvider(requireActivity())[StoreViewModel::class.java]
        progressTracker = ProgressTracker(requireContext())
        sequenceGenerator = SequenceGenerator(progressTracker)
        audioManager = AudioManager(requireContext())
    }
    
    private fun setupUI() {
        binding.buttonStart.setOnClickListener { startTraining() }
        binding.buttonStop.setOnClickListener { stopTraining() }
        binding.buttonReplay.setOnClickListener { replaySequence() }
        
        setupMorseKeyboard()
        updateUIForState(TrainingState.READY)
    }
    
    private fun setupMorseKeyboard() {
        binding.morseKeyboard.setOnCharacterClickListener { char ->
            onCharacterSelected(char)
        }
        
        // Initialize keyboard with current level characters from AppStore
        val currentLevel = storeViewModel.settings.value.currentLevel
        
        Log.d(TAG, "Initializing keyboard with level $currentLevel")
        
        val levelChars = progressTracker.getCharactersForLevel(currentLevel)
        binding.morseKeyboard.setAvailableCharacters(levelChars.toSet())
        keyboardLevel = currentLevel // Track what level keyboard was built for
    }
    
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            storeViewModel.trainingState.collect { trainingState ->
                updateUIForTrainingState(trainingState)
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            storeViewModel.audioState.collect { audioState ->
                if (audioState.isPlaying) {
                    startAudioVisualization()
                } else {
                    stopAudioVisualization()
                }
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            storeViewModel.settings.collect { settings ->
                // Update progress tracker with new settings
                progressTracker.updateSettings(settings)
                
                // Update keyboard when level changes
                if (settings.currentLevel != keyboardLevel) {
                    Log.d(TAG, "Level changed from $keyboardLevel to ${settings.currentLevel} - updating keyboard")
                    updateMorseKeyboardForLevel(settings.currentLevel)
                    keyboardLevel = settings.currentLevel
                }
                
                updateProgressDisplay()
            }
        }
    }
    
    private fun startTraining() {
        val settings = storeViewModel.settings.value
        currentSequence = sequenceGenerator.generateSequence(
            settings.sequenceLength,
            settings.currentLevel
        )
        
        userInput = ""
        startTime = System.currentTimeMillis()
        
        // Reset keyboard state only when starting a new sequence
        binding.morseKeyboard.resetState()
        
        storeViewModel.dispatch(AppAction.StartTraining(currentSequence))
        
        // Start continuous noise if enabled
        if (settings.noiseEnabled) {
            audioManager.startContinuousNoise(settings)
        }
        
        lifecycleScope.launch {
            try {
                updateUIForState(TrainingState.PLAYING)
                audioManager.playSequence(currentSequence, settings)
                delay(500)
                storeViewModel.dispatch(AppAction.UpdateUserInput(""))
                updateUIForState(TrainingState.WAITING)
            } catch (e: Exception) {
                showMessage("Audio playback error: ${e.message}")
                stopTraining()
            }
        }
    }
    
    private fun stopTraining() {
        audioManager.stopPlayback()
        audioManager.stopContinuousNoise() // Stop background noise
        storeViewModel.dispatch(AppAction.StopTraining)
        // Reset keyboard state only when manually stopping
        binding.morseKeyboard.resetState()
        // Don't reset keyboardLevel here - let it persist across training sessions
        updateUIForState(TrainingState.READY)
        updateProgressDisplay()
    }
    
    private fun replaySequence() {
        if (currentSequence.isNotEmpty()) {
            val settings = storeViewModel.settings.value
            lifecycleScope.launch {
                try {
                    audioManager.playSequence(currentSequence, settings)
                } catch (e: Exception) {
                    showMessage("Audio playback error: ${e.message}")
                }
            }
        }
    }
    
    private fun onCharacterSelected(char: Char) {
        userInput += char
        storeViewModel.dispatch(AppAction.UpdateUserInput(userInput))
        updateInputDisplay()
        
        // Check if this character is incorrect
        val currentIndex = userInput.length - 1
        if (currentIndex < currentSequence.length) {
            val expectedChar = currentSequence[currentIndex]
            if (char.uppercaseChar() != expectedChar.uppercaseChar()) {
                // Show incorrect answer on keyboard
                binding.morseKeyboard.showIncorrectAnswer(char, expectedChar)
                
                // First incorrect character - fail immediately
                audioManager.stopPlayback()
                
                // Wait a moment to show the red character, then fail the sequence
                lifecycleScope.launch {
                    delay(500) // Let user see the red character
                    failSequenceImmediately()
                }
                return
            } else {
                // Show correct answer on keyboard
                binding.morseKeyboard.showCorrectAnswer(char)
            }
        }
        
        // Auto-submit when user input matches the expected sequence length
        if (userInput.length >= currentSequence.length) {
            lifecycleScope.launch {
                delay(500) // Small delay for user to see the typed character
                submitAnswer()
            }
        }
    }
    
    private fun failSequenceImmediately() {
        val responseTime = System.currentTimeMillis() - startTime
        val settings = storeViewModel.settings.value
        
        // Record failure for all characters in the sequence
        currentSequence.forEachIndexed { index, char ->
            val userChar = if (userInput.length > index) userInput[index] else null
            val charCorrect = userChar?.uppercaseChar() == char.uppercaseChar()
            progressTracker.recordAttempt(char, charCorrect, responseTime / currentSequence.length)
        }
        
        // Level changes are now handled automatically by the AppStore and settings observer
        
        storeViewModel.dispatch(AppAction.SubmitAnswer(userInput))
        
        // Show failure animation and message with progress bar
        showIncorrectAnswerAnimation()
        showProgressMessage("❌ Incorrect! The answer was: $currentSequence", false)
        
        updateProgressDisplay()
        
        // Start new sequence after user-configured delay
        lifecycleScope.launch {
            delay(settings.sequenceDelayMs)
            startTraining()
        }
    }
    
    private fun submitAnswer() {
        if (userInput.isEmpty()) {
            showMessage("Please enter your answer first")
            return
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        val settings = storeViewModel.settings.value
        val isCorrect = userInput.uppercase() == currentSequence.uppercase()
        
        // Record progress for each character
        currentSequence.forEachIndexed { index, char ->
            val userChar = if (userInput.length > index) {
                userInput[index]
            } else null
            
            val charCorrect = userChar?.uppercaseChar() == char.uppercaseChar()
            progressTracker.recordAttempt(char, charCorrect, responseTime / currentSequence.length)
        }
        
        // Level changes are now handled automatically by the AppStore and settings observer
        
        storeViewModel.dispatch(AppAction.SubmitAnswer(userInput))
        
        // Show completion feedback with progress bar
        if (isCorrect) {
            showCorrectAnswerAnimation()
            showProgressMessage("✅ Correct! Well done!", true)
        } else {
            showIncorrectAnswerAnimation()
            showProgressMessage("❌ Incorrect! The answer was: $currentSequence", false)
        }
        
        updateProgressDisplay()
        
        lifecycleScope.launch {
            delay(settings.sequenceDelayMs)
            startTraining()
        }
    }
    
    private fun updateUIForTrainingState(state: TrainingStateData) {
        updateUIForState(state.state)
        
        // Remove the old state-based messages since we now handle them in sequence completion
    }
    
    private fun updateUIForState(state: TrainingState) {
        Log.d(TAG, "Updating UI for state: $state")
        when (state) {
            TrainingState.READY -> {
                binding.sequenceDisplay.text = "Ready to train"
                binding.buttonStart.isEnabled = true
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonStop.isEnabled = false
                binding.buttonStop.visibility = View.GONE
                binding.buttonReplay.isEnabled = false
                binding.buttonReplay.visibility = View.GONE
                binding.morseKeyboard.alpha = 0.5f
                setKeyboardEnabled(false)
                userInput = ""
                // Don't reset keyboard state here - let explicit actions handle it
            }
            TrainingState.PLAYING -> {
                binding.sequenceDisplay.text = "Listen..."
                binding.buttonStart.isEnabled = false
                binding.buttonStart.visibility = View.GONE
                binding.buttonStop.isEnabled = true
                binding.buttonStop.visibility = View.VISIBLE
                binding.buttonReplay.isEnabled = false
                binding.buttonReplay.visibility = View.GONE
                binding.morseKeyboard.alpha = 1.0f
                setKeyboardEnabled(true)
                startSequenceAnimation()
            }
            TrainingState.WAITING -> {
                binding.sequenceDisplay.text = "Type what you heard:\n$userInput"
                binding.buttonStart.isEnabled = false
                binding.buttonStart.visibility = View.GONE
                binding.buttonStop.isEnabled = true
                binding.buttonStop.visibility = View.VISIBLE
                binding.buttonReplay.isEnabled = true
                binding.buttonReplay.visibility = View.VISIBLE
                binding.morseKeyboard.alpha = 1.0f
                setKeyboardEnabled(true)
                // Remove the animateToInputMode() call that causes keyboard to disappear/reappear
                // Do NOT reset keyboard state here - preserve selection during input
            }
            TrainingState.FINISHED -> {
                binding.buttonStart.isEnabled = true
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonStop.isEnabled = false
                binding.buttonStop.visibility = View.GONE
                binding.buttonReplay.isEnabled = true
                binding.buttonReplay.visibility = View.VISIBLE
                binding.morseKeyboard.alpha = 0.5f
                setKeyboardEnabled(false)
            }
            TrainingState.PAUSED -> {
                binding.sequenceDisplay.text = "Training paused"
                binding.buttonStart.isEnabled = true
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonStop.isEnabled = false
                binding.buttonStop.visibility = View.GONE
                binding.buttonReplay.isEnabled = true
                binding.buttonReplay.visibility = View.VISIBLE
            }
        }
    }
    
    private fun setKeyboardEnabled(enabled: Boolean) {
        binding.morseKeyboard.isEnabled = enabled
    }
    
    private fun updateInputDisplay() {
        if (userInput.isEmpty()) {
            binding.sequenceDisplay.text = "Type what you heard:"
        } else {
            // Create the full text with header
            val headerText = "Type what you heard:\n"
            val fullText = headerText + userInput
            val spannableString = SpannableString(fullText)
            
            // Apply colors to the user input part only (after the header)
            val inputStartIndex = headerText.length
            userInput.forEachIndexed { index, userChar ->
                if (index < currentSequence.length) {
                    val expectedChar = currentSequence[index]
                    val isCorrect = userChar.uppercaseChar() == expectedChar.uppercaseChar()
                    val color = if (isCorrect) {
                        ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary)
                    } else {
                        ContextCompat.getColor(requireContext(), R.color.md_theme_light_error)
                    }
                    
                    spannableString.setSpan(
                        ForegroundColorSpan(color),
                        inputStartIndex + index,
                        inputStartIndex + index + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            binding.sequenceDisplay.text = spannableString
        }
    }
    
    private fun updateProgressDisplay() {
        val level = storeViewModel.settings.value.currentLevel
        val progress = progressTracker.getCurrentLevelProgress()
        val streak = maxOf(0, progressTracker.getCurrentStreak()) // Show only positive streaks
        
        binding.progressIndicator.text = "Level $level - Progress: ${(progress * 100).toInt()}% - Streak: $streak"
    }
    
    private fun setupAnimations() {
        Log.d(TAG, "Setting up animations")
        val views = listOf(
            binding.progressIndicator,
            binding.sequenceDisplay,
            binding.controlPanel,
            binding.morseKeyboard
        )
        
        // Only animate if views are actually visible and ready
        if (isAdded && view != null) {
            views.forEachIndexed { index, view ->
                // Start from current position (already visible) and add subtle animation
                val originalY = view.translationY
                
                view.translationY = originalY + 50f // Slight movement
                view.animate()
                    .translationY(originalY)
                    .setDuration(200)
                    .setStartDelay((index * 50).toLong())
                    .withEndAction {
                        // Ensure view is fully visible after animation
                        view.alpha = 1f
                        view.translationY = 0f
                        Log.d(TAG, "Animation completed for view: ${view.javaClass.simpleName}")
                    }
                    .start()
            }
        } else {
            Log.w(TAG, "Skipping animations - fragment not ready")
        }
    }
    
    private fun startSequenceAnimation() {
        binding.sequenceDisplay.let { view ->
            val pulseAnimator = ObjectAnimator.ofFloat(
                view, "alpha", 1f, 0.7f, 1f
            ).apply {
                duration = 1000
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            pulseAnimator.start()
        }
    }
    
    // Removed animateToInputMode() method that was causing keyboard to disappear/reappear
    
    private fun showCorrectAnswerAnimation() {
        binding.sequenceDisplay.let { view ->
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.1f, 1f)
            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.1f, 1f)
            
            AnimatorSet().apply {
                playTogether(scaleX, scaleY)
                duration = 300
            }.start()
        }
    }
    

    
    private fun showIncorrectAnswerAnimation() {
        binding.sequenceDisplay.let { view ->
            val shake = ObjectAnimator.ofFloat(
                view, "translationX", 0f, -20f, 20f, -20f, 20f, 0f
            ).apply {
                duration = 400
            }
            shake.start()
        }
    }
    
    private fun startAudioVisualization() {
        binding.audioVisualization.visibility = View.VISIBLE
        
        binding.audioVisualization.let { view ->
            val pulse = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.1f, 1f).apply {
                duration = 600
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            val pulseY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.1f, 1f).apply {
                duration = 600
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            
            AnimatorSet().apply {
                playTogether(pulse, pulseY)
            }.start()
        }
    }
    
    private fun stopAudioVisualization() {
        binding.audioVisualization.visibility = View.GONE
        binding.audioVisualization.clearAnimation()
    }
    
    private fun updateMorseKeyboardForLevel(level: Int) {
        val levelChars = progressTracker.getCharactersForLevel(level)
        val charactersSet = levelChars.toSet()
        
        // Update keyboard for new level (only called when level actually changes)
        binding.morseKeyboard.setAvailableCharacters(charactersSet)
        binding.morseKeyboard.resetState()
    }
    

    
    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun showProgressMessage(message: String, isCorrect: Boolean) {
        val settings = storeViewModel.settings.value
        
        // Create a custom snackbar with progress bar
        val snackbar = com.google.android.material.snackbar.Snackbar.make(
            binding.root, 
            message, 
            com.google.android.material.snackbar.Snackbar.LENGTH_INDEFINITE
        )
        
        // Customize the snackbar appearance
        val snackbarView = snackbar.view
        val textView = snackbarView.findViewById<android.widget.TextView>(com.google.android.material.R.id.snackbar_text)
        
        // Set colors based on correctness
        val backgroundColor = if (isCorrect) {
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary)
        } else {
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.md_theme_light_error)
        }
        
        val textColor = if (isCorrect) {
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.md_theme_light_onPrimary)
        } else {
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.md_theme_light_onError)
        }
        
        snackbarView.setBackgroundColor(backgroundColor)
        textView.setTextColor(textColor)
        textView.textSize = 16f
        
        // Create progress bar
        val progressBar = com.google.android.material.progressindicator.LinearProgressIndicator(requireContext())
        progressBar.isIndeterminate = false
        progressBar.max = 100
        progressBar.progress = 100
        
        // Set progress bar colors
        progressBar.setIndicatorColor(textColor)
        progressBar.trackColor = backgroundColor
        
        // Add progress bar to snackbar
        val layout = snackbarView as com.google.android.material.snackbar.Snackbar.SnackbarLayout
        val progressParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            8 // 8dp height
        )
        layout.addView(progressBar, progressParams)
        
        snackbar.show()
        
        // Use actual sequence delay setting for progress bar countdown
        val duration = settings.sequenceDelayMs
        
        // Handle zero delay case
        if (duration <= 0) {
            snackbar.dismiss()
            return
        }
        
        val animator = android.animation.ValueAnimator.ofInt(100, 0)
        animator.duration = duration
        animator.interpolator = android.view.animation.LinearInterpolator()
        
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Int
            progressBar.progress = progress
        }
        
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                snackbar.dismiss()
            }
        })
        
        animator.start()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        audioManager.release()
        _binding = null
    }
}
