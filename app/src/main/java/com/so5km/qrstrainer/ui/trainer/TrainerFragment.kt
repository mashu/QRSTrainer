package com.so5km.qrstrainer.ui.trainer

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
        // Initial keyboard setup will be handled by the currentLevel observer
        // This ensures the keyboard is always in sync with the actual current level
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
            }
        }
        
        // Observe level changes and update keyboard accordingly
        viewLifecycleOwner.lifecycleScope.launch {
            progressTracker.currentLevel.collect { level ->
                Log.d(TAG, "Level changed to: $level")
                updateMorseKeyboardForLevel(level)
                updateProgressDisplay()
            }
        }
    }
    
    private fun startTraining() {
        val settings = storeViewModel.settings.value
        currentSequence = sequenceGenerator.generateSequence(
            settings.sequenceLength,
            progressTracker.getCurrentLevel()
        )
        
        userInput = ""
        startTime = System.currentTimeMillis()
        
        storeViewModel.dispatch(AppAction.StartTraining(currentSequence))
        
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
        storeViewModel.dispatch(AppAction.StopTraining)
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
        
        // Check if this character is incorrect and stop audio if so
        val currentIndex = userInput.length - 1
        if (currentIndex < currentSequence.length) {
            val expectedChar = currentSequence[currentIndex]
            if (char.uppercaseChar() != expectedChar.uppercaseChar()) {
                // Stop audio playback on first mismatch
                audioManager.stopPlayback()
                showMessage("❌ Incorrect character entered - audio stopped")
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
    
    private fun submitAnswer() {
        if (userInput.isEmpty()) {
            showMessage("Please enter your answer first")
            return
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        val isCorrect = userInput.uppercase() == currentSequence.uppercase()
        
        // Record progress for each character
        currentSequence.forEachIndexed { index, char ->
            val userChar = if (userInput.length > index) {
                userInput[index]
            } else null
            
            val charCorrect = userChar?.uppercaseChar() == char.uppercaseChar()
            progressTracker.recordAttempt(char, charCorrect, responseTime / currentSequence.length)
        }
        
        storeViewModel.dispatch(AppAction.SubmitAnswer(userInput))
        
        if (isCorrect) {
            showCorrectAnswerAnimation()
            showMessage("✅ Correct! Well done!")
        } else {
            showIncorrectAnswerAnimation()
            showMessage("❌ Incorrect. The answer was: $currentSequence")
        }
        
        updateProgressDisplay()
        
        lifecycleScope.launch {
            delay(2000)
            // Always start a new training session after answering
            startTraining()
        }
    }
    
    private fun updateUIForTrainingState(state: TrainingStateData) {
        updateUIForState(state.state)
        
        if (state.previousWasCorrect && state.previousSequence.isNotEmpty()) {
            showMessage("✅ Correct!")
        } else if (!state.previousWasCorrect && state.previousSequence.isNotEmpty()) {
            showMessage("❌ Try again!")
        }
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
                animateToInputMode()
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
        for (i in 0 until binding.morseKeyboard.childCount) {
            binding.morseKeyboard.getChildAt(i).isEnabled = enabled
        }
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
        val level = progressTracker.getCurrentLevel()
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
    
    private fun animateToInputMode() {
        binding.morseKeyboard.let { keyboard ->
            keyboard.translationY = 300f
            keyboard.animate()
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }
    
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
            val pulse = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.2f, 1f).apply {
                duration = 500
                repeatCount = ValueAnimator.INFINITE
            }
            val pulseY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.2f, 1f).apply {
                duration = 500
                repeatCount = ValueAnimator.INFINITE
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
        Log.d(TAG, "Updating morse keyboard for level: $level")
        val levelChars = progressTracker.getCharactersForLevel(level)
        Log.d(TAG, "Level chars: $levelChars")
        
        binding.morseKeyboard.removeAllViews()
        
        levelChars.forEach { char ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = char.toString()
                textSize = 16f
                isCheckable = false
                isClickable = true
                isFocusable = true
                
                // Apply Material 3 styling
                setChipBackgroundColorResource(R.color.md_theme_light_surface)
                setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.md_theme_light_onSurface))
                chipStrokeWidth = resources.getDimensionPixelSize(R.dimen.chip_stroke_width).toFloat()
                chipStrokeColor = androidx.core.content.ContextCompat.getColorStateList(context, R.color.md_theme_light_outline)
                // chipCornerRadius removed - now handled by style
                
                setOnClickListener { onCharacterSelected(char) }
            }
            binding.morseKeyboard.addView(chip)
        }
        
        addControlButtons()
    }
    
    private fun addControlButtons() {
        // No control buttons needed - CLEAR button removed as it's redundant
        // with real-time validation, early termination, and existing REPLAY/STOP buttons
    }
    
    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        audioManager.release()
        _binding = null
    }
}
