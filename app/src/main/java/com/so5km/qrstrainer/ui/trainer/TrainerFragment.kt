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
        
        // Initialize keyboard with characters that match what sequence generator uses
        val settings = storeViewModel.settings.value
        val availableChars = SequenceGenerator.getAvailableCharacters(settings, progressTracker)
        
        Log.d(TAG, "Initializing keyboard with ${availableChars.size} characters: $availableChars")
        
        binding.morseKeyboard.setAvailableCharacters(availableChars.toSet())
        keyboardLevel = settings.currentLevel // Track what level keyboard was built for
    }
    
    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            storeViewModel.trainingState.collect { trainingState ->
                Log.d(TAG, "=== TRAINING STATE CHANGED ===")
                Log.d(TAG, "New state: ${trainingState.state}, userInput: '${trainingState.userInput}', currentSequence: '${trainingState.currentSequence}'")
                updateUIForTrainingState(trainingState)
                Log.d(TAG, "=== TRAINING STATE CHANGE COMPLETE ===")
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
                
                // Update keyboard when level or character settings change
                val newAvailableChars = SequenceGenerator.getAvailableCharacters(settings, progressTracker)
                if (settings.currentLevel != keyboardLevel || !binding.morseKeyboard.hasCharacters(newAvailableChars.toSet())) {
                    Log.d(TAG, "Settings changed - updating keyboard with ${newAvailableChars.size} characters")
                    binding.morseKeyboard.setAvailableCharacters(newAvailableChars.toSet())
                    keyboardLevel = settings.currentLevel
                }
                
                updateProgressDisplay()
            }
        }
    }
    
    private fun startTraining() {
        val settings = storeViewModel.settings.value
        currentSequence = sequenceGenerator.generateGroupSequence(settings)
        
        Log.d(TAG, "Starting training with sequence: '$currentSequence' (length: ${currentSequence.length})")
        Log.d(TAG, "Settings: sequenceLength=${settings.sequenceLength}, currentLevel=${settings.currentLevel}")
        
        // Clear input only when starting a new sequence
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
                delay(100) // Reduced delay for faster keyboard activation
                // DON'T clear user input here - preserve what user typed during playback
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
    
    private fun onCharacterSelected(character: Char) {
        Log.d(TAG, "=== CHARACTER SELECTED START ===")
        Log.d(TAG, "Character selected: $character, current userInput: '$userInput', currentSequence: '$currentSequence'")
        
        // Count only actual morse characters (not spaces) for validation
        val morseCharCount = currentSequence.count { it != ' ' }
        Log.d(TAG, "Morse character count in sequence: $morseCharCount (from '$currentSequence')")
        
        // Don't allow typing more characters than the morse character count
        if (userInput.length >= morseCharCount) {
            Log.d(TAG, "Rejecting character: already at morse character limit ($morseCharCount)")
            return
        }
        
        // Add character to user input
        userInput += character
        Log.d(TAG, "Updated userInput: '$userInput'")
        
        Log.d(TAG, "About to dispatch UpdateUserInput action...")
        storeViewModel.dispatch(AppAction.UpdateUserInput(userInput))
        Log.d(TAG, "UpdateUserInput action dispatched")
        
        Log.d(TAG, "About to call updateSequenceDisplayWithInput...")
        updateSequenceDisplayWithInput()
        Log.d(TAG, "updateSequenceDisplayWithInput completed")
        
        // Auto-submit when we have enough characters for the actual morse characters
        Log.d(TAG, "Checking auto-submit: userInput.length (${userInput.length}) >= morseCharCount ($morseCharCount)")
        if (userInput.length >= morseCharCount) {
            Log.d(TAG, "Auto-submitting: userInput.length (${userInput.length}) >= morseCharCount ($morseCharCount)")
            lifecycleScope.launch {
                delay(100) // Small delay to let user see the typed character
                submitAnswer()
            }
        } else {
            Log.d(TAG, "Not auto-submitting: ${userInput.length} < $morseCharCount")
        }
        
        Log.d(TAG, "=== CHARACTER SELECTED END ===")
    }
    
    private fun updateSequenceDisplayWithInput() {
        if (userInput.isEmpty()) {
            binding.sequenceDisplay.text = "_"
            return
        }
        
        // Show the user input with color coding
        val spannableString = SpannableString(userInput)
        
        // Apply colors based on comparison with morse characters only (exclude spaces)
        val morseCharsOnly = currentSequence.filter { it != ' ' }
        if (morseCharsOnly.isNotEmpty()) {
            userInput.forEachIndexed { index, userChar ->
                if (index < morseCharsOnly.length) {
                    val expectedChar = morseCharsOnly[index]
                    val isCorrect = userChar.uppercaseChar() == expectedChar.uppercaseChar()
                    val color = if (isCorrect) {
                        ContextCompat.getColor(requireContext(), R.color.md_theme_light_primary)
                    } else {
                        ContextCompat.getColor(requireContext(), R.color.md_theme_light_error)
                    }
                    
                    spannableString.setSpan(
                        ForegroundColorSpan(color),
                        index,
                        index + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
        
        binding.sequenceDisplay.text = spannableString
    }
    

    
    private fun submitAnswer() {
        if (userInput.isEmpty()) {
            showMessage("Please enter your answer first")
            return
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        val settings = storeViewModel.settings.value
        
        // Compare against morse characters only (exclude spaces)
        val morseCharsOnly = currentSequence.filter { it != ' ' }
        val isCorrect = userInput.uppercase() == morseCharsOnly.uppercase()
        
        Log.d(TAG, "Answer validation: userInput='$userInput', morseCharsOnly='$morseCharsOnly', isCorrect=$isCorrect")
        
        // Record progress for each morse character (excluding spaces)
        morseCharsOnly.forEachIndexed { index, char ->
            val userChar = if (userInput.length > index) {
                userInput[index]
            } else null
            
            val charCorrect = userChar?.uppercaseChar() == char.uppercaseChar()
            progressTracker.recordAttempt(char, charCorrect, responseTime / morseCharsOnly.length)
        }
        
        // CRITICAL: Also record the overall sequence result for proper streak tracking
        // This ensures that if any character is wrong, the streak resets properly
        progressTracker.recordSequenceAttempt(isCorrect, responseTime)
        
        // Level changes are now handled automatically by the AppStore and settings observer
        
        storeViewModel.dispatch(AppAction.SubmitAnswer(userInput))
        
        // Show completion feedback with progress bar
        if (isCorrect) {
            showCorrectAnswerAnimation()
            showProgressMessage("✅ Correct! Well done!", true)
        } else {
            showIncorrectAnswerAnimation()
            showProgressMessage("❌ Incorrect! The answer was: $morseCharsOnly", false)
        }
        
        updateProgressDisplay()
        
        // Automatically continue to next sequence after delay
        lifecycleScope.launch {
            delay(settings.sequenceDelayMs)
            startTraining()
        }
    }
    
    private fun updateUIForTrainingState(state: TrainingStateData) {
        Log.d(TAG, "updateUIForTrainingState called with state: ${state.state}, userInput: '${state.userInput}', currentSequence: '${state.currentSequence}'")
        updateUIForState(state.state)
        
        // Remove the old state-based messages since we now handle them in sequence completion
    }
    
    private fun updateUIForState(state: TrainingState) {
        Log.d(TAG, "Updating UI for state: $state")
        when (state) {
            TrainingState.READY -> {
                Log.d(TAG, "READY state: disabling keyboard")
                binding.sequenceDisplay.text = ""
                binding.buttonStart.isEnabled = true
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonStop.isEnabled = false
                binding.buttonStop.visibility = View.GONE
                binding.buttonReplay.isEnabled = false
                binding.buttonReplay.visibility = View.GONE
                binding.morseKeyboard.alpha = 0.5f
                setKeyboardEnabled(false)
                userInput = ""
                binding.morseKeyboard.resetState()
            }
            TrainingState.PLAYING -> {
                Log.d(TAG, "PLAYING state: enabling keyboard for concurrent input")
                binding.sequenceDisplay.text = "🎵 Listen..."
                binding.buttonStart.isEnabled = false
                binding.buttonStart.visibility = View.GONE
                binding.buttonStop.isEnabled = true
                binding.buttonStop.visibility = View.VISIBLE
                binding.buttonReplay.isEnabled = false
                binding.buttonReplay.visibility = View.GONE
                binding.morseKeyboard.alpha = 1.0f
                setKeyboardEnabled(true)
                // Don't clear userInput or reset keyboard state during playback
                // Users should be able to type while listening
                startSequenceAnimation()
            }
            TrainingState.WAITING -> {
                Log.d(TAG, "WAITING state: enabling keyboard")
                binding.buttonStart.isEnabled = false
                binding.buttonStart.visibility = View.GONE
                binding.buttonStop.isEnabled = true
                binding.buttonStop.visibility = View.VISIBLE
                binding.buttonReplay.isEnabled = true
                binding.buttonReplay.visibility = View.VISIBLE
                binding.morseKeyboard.alpha = 1.0f
                setKeyboardEnabled(true)
                // DON'T clear userInput here - preserve what user typed during playback
                // Show current input or placeholder
                updateSequenceDisplayWithInput()
                // Do NOT reset keyboard state here - preserve selection during input
            }
            TrainingState.FINISHED -> {
                Log.d(TAG, "FINISHED state: disabling keyboard")
                binding.sequenceDisplay.text = "Sequence complete!"
                binding.buttonStart.isEnabled = true
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonStop.isEnabled = false
                binding.buttonStop.visibility = View.GONE
                binding.buttonReplay.isEnabled = true
                binding.buttonReplay.visibility = View.VISIBLE
                binding.morseKeyboard.alpha = 0.5f
                setKeyboardEnabled(false)
                userInput = ""
            }
            TrainingState.PAUSED -> {
                Log.d(TAG, "PAUSED state: enabling keyboard")
                binding.sequenceDisplay.text = "Paused"
                binding.buttonStart.isEnabled = true
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonStop.isEnabled = false
                binding.buttonStop.visibility = View.GONE
                binding.buttonReplay.isEnabled = true
                binding.buttonReplay.visibility = View.VISIBLE
                setKeyboardEnabled(true)
            }
        }
    }
    
    private fun setKeyboardEnabled(enabled: Boolean) {
        binding.morseKeyboard.isEnabled = enabled
    }
    

    
    private fun updateProgressDisplay() {
        val level = storeViewModel.settings.value.currentLevel
        val progress = progressTracker.getCurrentLevelProgress()
        val streak = progressTracker.getCurrentStreak()
        
        // Show actual streak (can be negative) and progress percentage
        val streakText = if (streak >= 0) "Streak: $streak" else "Streak: $streak"
        val progressPercent = (progress * 100).toInt()
        
        binding.progressIndicator.text = "Level $level - Progress: ${progressPercent}% - $streakText"
        
        // Update the actual progress bar
        binding.progressBar.progress = progressPercent
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
