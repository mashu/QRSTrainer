package com.so5km.qrstrainer.ui.trainer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
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
// Removed extension imports to avoid resolution issues; using local helper instead

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
    
    // Audio coordination state
    private var shouldAdvanceAfterAudio = false
    private var pendingAdvanceAction: (() -> Unit)? = null
    
    // Character attempt history for UI feedback
    private val characterHistory = mutableListOf<CharacterAttempt>()
    
    data class CharacterAttempt(
        val userInput: Char,
        val correctChar: Char,
        val wasCorrect: Boolean
    )
    
    companion object {
        private const val TAG = "TrainerFragment"
        private const val MAX_HISTORY_SIZE = 20
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

    private fun showMessage(message: String) {
        view?.let { rootView ->
            Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show()
        }
    }
    
    private fun setupMorseKeyboard() {
        binding.morseKeyboard.setOnCharacterClickListener { char ->
            val currentState = storeViewModel.trainingState.value.state
            // If trainer is not active, play the character's morse code
            if (currentState == TrainingState.READY || currentState == TrainingState.FINISHED) {
                playCharacterPreview(char)
            } else {
                onCharacterSelected(char)
            }
        }
        
        // Initialize keyboard with characters that match what sequence generator uses
        val settings = storeViewModel.settings.value
        val availableChars = SequenceGenerator.getAvailableCharacters(settings, progressTracker)
        
        Log.d(TAG, "Initializing keyboard with ${availableChars.size} characters: $availableChars")
        
        binding.morseKeyboard.setAvailableCharacters(availableChars.toSet())
        keyboardLevel = settings.currentLevel // Track what level keyboard was built for
    }
    
    /**
     * Play the morse code for a single character when trainer is not active.
     * This allows users to preview/learn character sounds.
     */
    private fun playCharacterPreview(character: Char) {
        // Don't play if audio is already playing
        if (storeViewModel.audioState.value.isPlaying) {
            Log.d(TAG, "Audio already playing, skipping character preview")
            return
        }
        
        val settings = storeViewModel.settings.value
        Log.d(TAG, "Playing character preview: $character")
        
        lifecycleScope.launch {
            try {
                // Set up a simple completion listener for preview playback
                val previewCompletionListener = object : com.so5km.qrstrainer.audio.AudioCompletionListener {
                    override fun onSequenceCompleted() {
                        Log.d(TAG, "Character preview completed: $character")
                        storeViewModel.dispatch(AppAction.SetAudioPlaying(false))
                    }
                    
                    override fun onPlaybackStopped() {
                        Log.d(TAG, "Character preview stopped")
                        storeViewModel.dispatch(AppAction.SetAudioPlaying(false))
                    }
                    
                    override fun onPlaybackError(error: Exception) {
                        Log.e(TAG, "Character preview error", error)
                        storeViewModel.dispatch(AppAction.SetAudioPlaying(false))
                    }
                }
                
                audioManager.setAudioCompletionListener(previewCompletionListener)
                audioManager.playSequence(character.toString(), settings)
            } catch (e: Exception) {
                Log.e(TAG, "Error playing character preview", e)
            }
        }
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
        
        Log.d(TAG, "=== SEQUENCE GENERATION DEBUG ===")
        Log.d(TAG, "Settings: minGroupSize=${settings.minGroupSize}, maxGroupSize=${settings.maxGroupSize}")
        Log.d(TAG, "Settings: adaptiveGroupSize=${settings.adaptiveGroupSize}, adaptiveSpeed=${settings.adaptiveSpeed}")
        Log.d(TAG, "Settings: sequenceLength=${settings.sequenceLength}, currentLevel=${settings.currentLevel}")
        
        currentSequence = sequenceGenerator.generateGroupSequence(settings)
        
        Log.d(TAG, "Starting training with sequence: '$currentSequence' (length: ${currentSequence.length})")
        Log.d(TAG, "=== END SEQUENCE GENERATION DEBUG ===")
        Log.d(TAG, "Settings: sequenceLength=${settings.sequenceLength}, currentLevel=${settings.currentLevel}")
        
        // Clear input only when starting a new sequence
        userInput = ""
        startTime = System.currentTimeMillis()
        
        // Reset keyboard state only when starting a new sequence
        binding.morseKeyboard.resetState()
        
        // Reset audio coordination state
        shouldAdvanceAfterAudio = false
        pendingAdvanceAction = null
        
        storeViewModel.dispatch(AppAction.StartTraining(currentSequence))
        
        // Start continuous noise if enabled
        if (settings.noiseEnabled) {
            audioManager.startContinuousNoise(settings)
        }
        
        lifecycleScope.launch {
            try {
                updateUIForState(TrainingState.PLAYING)
                
                // Set up simple audio completion listener that just dispatches actions
                val audioCompletionListener = object : com.so5km.qrstrainer.audio.AudioCompletionListener {
                    override fun onSequenceCompleted() {
                        Log.d(TAG, "Audio sequence completed - dispatching AudioSequenceCompleted action")
                        storeViewModel.dispatch(AppAction.AudioSequenceCompleted)
                        
                        // If there's a pending advance action (user typed quickly), execute it now
                        if (shouldAdvanceAfterAudio && pendingAdvanceAction != null) {
                            Log.d(TAG, "Executing pending advance action after audio completion")
                            pendingAdvanceAction?.invoke()
                            shouldAdvanceAfterAudio = false
                            pendingAdvanceAction = null
                        }
                    }
                    
                    override fun onPlaybackStopped() {
                        Log.d(TAG, "Audio playback stopped - setting audio not playing")
                        storeViewModel.dispatch(AppAction.SetAudioPlaying(false))
                    }
                    
                    override fun onPlaybackError(error: Exception) {
                        Log.e(TAG, "Audio playback error", error)
                        storeViewModel.dispatch(AppAction.SetAudioPlaying(false))
                        
                        // Show error message needs main thread - dispatch to lifecycle scope
                        lifecycleScope.launch {
                            showMessage("Audio playback error: ${error.message}")
                            stopTraining()
                        }
                    }
                }
                
                audioManager.setAudioCompletionListener(audioCompletionListener)
                audioManager.playSequence(currentSequence, settings)
                
                // State transition to WAITING will be handled by AudioSequenceCompleted action
                
            } catch (e: Exception) {
                showMessage("Audio playback error: ${e.message}")
                stopTraining()
            }
        }
    }
    
    private fun stopTraining() {
        // CRITICAL: Clear audio completion listener FIRST to prevent callback interference
        audioManager.setAudioCompletionListener(null)
        
        // Cancel any ongoing audio playback
        audioManager.stopPlayback()
        audioManager.stopContinuousNoise() // Stop background noise
        
        // Clear audio coordination state
        shouldAdvanceAfterAudio = false
        pendingAdvanceAction = null
        
        storeViewModel.dispatch(AppAction.StopTraining)
        // Reset keyboard state only when manually stopping
        binding.morseKeyboard.resetState()
        // Don't reset keyboardLevel here - let it persist across training sessions
        updateUIForState(TrainingState.READY)
        updateProgressDisplay()
    }
    
    private fun replaySequence() {
        if (currentSequence.isNotEmpty()) {
            // Check if audio is already playing
            if (storeViewModel.audioState.value.isPlaying) {
                showMessage("Audio is already playing. Please wait for it to finish.")
                return
            }
            
            val settings = storeViewModel.settings.value
            lifecycleScope.launch {
                try {
                    // Set up completion listener for replay
                    val replayCompletionListener = object : com.so5km.qrstrainer.audio.AudioCompletionListener {
                        override fun onSequenceCompleted() {
                            Log.d(TAG, "Replay sequence completed")
                            storeViewModel.dispatch(AppAction.SetAudioPlaying(false))
                        }
                        
                        override fun onPlaybackStopped() {
                            Log.d(TAG, "Replay playback stopped")
                            storeViewModel.dispatch(AppAction.SetAudioPlaying(false))
                        }
                        
                        override fun onPlaybackError(error: Exception) {
                            Log.e(TAG, "Replay playback error", error)
                            storeViewModel.dispatch(AppAction.SetAudioPlaying(false))
                            showMessage("Replay error: ${error.message}")
                        }
                    }
                    
                    audioManager.setAudioCompletionListener(replayCompletionListener)
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
        
        val settings = storeViewModel.settings.value
        
        // Count only actual morse characters (not spaces) for validation
        val morseCharCount = currentSequence.count { it != ' ' }
        val morseCharsOnly = currentSequence.filter { it != ' ' }
        Log.d(TAG, "Morse character count in sequence: $morseCharCount (from '$currentSequence')")
        
        // Don't allow typing more characters than the morse character count
        if (userInput.length >= morseCharCount) {
            Log.d(TAG, "Rejecting character: already at morse character limit ($morseCharCount)")
            return
        }
        
        // Check for fail-on-first-incorrect BEFORE adding the character
        if (settings.failOnFirstIncorrect && userInput.length < morseCharsOnly.length) {
            val expectedChar = morseCharsOnly[userInput.length].uppercaseChar()
            val inputChar = character.uppercaseChar()
            
            if (inputChar != expectedChar) {
                Log.d(TAG, "Fail on first incorrect: expected '$expectedChar', got '$inputChar' - failing immediately")
                
                // Add the incorrect character first so user can see what they typed wrong
                userInput += character
                storeViewModel.dispatch(AppAction.UpdateUserInput(userInput))
                updateSequenceDisplayWithInput()
                
                // Fail immediately after a short delay to show the mistake
                lifecycleScope.launch {
                    delay(200) // Short delay to let user see the wrong character
                    submitAnswer() // This will mark the sequence as failed
                }
                Log.d(TAG, "=== CHARACTER SELECTED END (FAILED ON FIRST INCORRECT) ===")
                return
            }
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
        
        // If fail-on-first-incorrect is disabled, still auto-submit immediately upon first
        // mismatch so we don't wait for additional characters unnecessarily
        if (!settings.failOnFirstIncorrect) {
            val indexJustTyped = userInput.length - 1
            if (indexJustTyped in morseCharsOnly.indices) {
                val expectedChar = morseCharsOnly[indexJustTyped].uppercaseChar()
                val typedChar = userInput[indexJustTyped].uppercaseChar()
                if (typedChar != expectedChar) {
                    Log.d(TAG, "First mismatch detected at index $indexJustTyped. Auto-submitting.")
                    lifecycleScope.launch {
                        delay(150)
                        submitAnswer()
                    }
                    return
                }
            }
        }

        // Auto-submit when we have enough characters for the actual morse characters
        Log.d(TAG, "Checking auto-submit: userInput.length (${userInput.length}) >= morseCharCount ($morseCharCount)")
        if (userInput.length >= morseCharCount) {
            Log.d(TAG, "Auto-submitting: userInput.length (${userInput.length}) >= morseCharCount ($morseCharCount)")
            
            // Provide feedback if audio is still playing
            if (storeViewModel.audioState.value.isPlaying) {
                // Show temporary message that we're waiting for audio to complete
                binding.sequenceDisplay.text = "🎵 Waiting for audio to complete..."
                Log.d(TAG, "Audio still playing during auto-submit - user will see feedback")
            }
            
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
        val morseCharsOnly = currentSequence.filter { it != ' ' }

        if (userInput.isEmpty()) {
            binding.sequenceDisplay.text = "_\n?"
            return
        }

        val expectedPart = morseCharsOnly.take(userInput.length)

        val typedUpper = userInput.uppercase()
        val expectedUpper = expectedPart.uppercase()

        val builder = SpannableStringBuilder()

        // Top row: what the user typed
        builder.append(typedUpper)
        builder.append("\n")
        val secondLineStart = builder.length

        // Bottom row: expected characters (same length as typed)
        builder.append(expectedUpper)

        // Resolve theme error color for mismatches
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(
            com.google.android.material.R.attr.colorError,
            typedValue,
            true
        )
        val errorColor = typedValue.data

        // Color mismatched characters in both rows
        for (i in typedUpper.indices) {
            if (typedUpper[i] != expectedUpper[i]) {
                // Top row span
                builder.setSpan(
                    ForegroundColorSpan(errorColor),
                    i,
                    i + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                // Bottom row span
                val bottomStart = secondLineStart + i
                builder.setSpan(
                    ForegroundColorSpan(errorColor),
                    bottomStart,
                    bottomStart + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        binding.sequenceDisplay.text = builder
    }
    
    // Real-time alignment is now shown in the main sequence display
    

    
    private fun submitAnswer() {
        if (userInput.isEmpty()) {
            showMessage("Please enter your answer first")
            return
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        val settings = storeViewModel.settings.value
        
        // Compare against morse characters only (exclude spaces)
        val morseCharsOnly = currentSequence.filter { it != ' ' }
        if (morseCharsOnly.isEmpty()) {
            Log.e(TAG, "submitAnswer: morseCharsOnly is empty; aborting to avoid crash")
            showMessage("No characters to validate. Please try again.")
            updateUIForState(TrainingState.READY)
            return
        }
        val isCorrect = userInput.uppercase() == morseCharsOnly.uppercase()
        
        Log.d(TAG, "Answer validation: userInput='$userInput', morseCharsOnly='$morseCharsOnly', isCorrect=$isCorrect")
        
        // Record progress for each morse character (excluding spaces) via AppStore
        val denom = maxOf(1, morseCharsOnly.length)
        morseCharsOnly.forEachIndexed { index, char ->
            val userChar = if (userInput.length > index) {
                userInput[index]
            } else null
            
            val charCorrect = userChar?.uppercaseChar() == char.uppercaseChar()
            storeViewModel.dispatch(AppAction.RecordCharacterAttempt(char, charCorrect, responseTime / denom))
        }
        
        // CRITICAL: Also record the overall sequence result for proper streak tracking
        // This ensures that if any character is wrong, the streak resets properly
        storeViewModel.dispatch(AppAction.RecordSequenceAttempt(isCorrect, responseTime))
        
        // Level changes are now handled automatically by the AppStore and settings observer
        
        storeViewModel.dispatch(AppAction.SubmitAnswer(userInput))
        
        // Record character attempts for progress tracking (history moved to progress tab)
        if (!isCorrect) {
            morseCharsOnly.forEachIndexed { index, correctChar ->
                val userChar = if (userInput.length > index) userInput[index] else '?'
                val charCorrect = userChar.uppercaseChar() == correctChar.uppercaseChar()
                
                characterHistory.add(CharacterAttempt(userChar, correctChar, charCorrect))
                
                // Keep only recent history
                if (characterHistory.size > MAX_HISTORY_SIZE) {
                    characterHistory.removeAt(0)
                }
            }
        }
        
        // Keep the final alignment visible for a moment after submission
        // (Real-time alignment is already shown during typing)
        
        // Show completion feedback with progress bar
        if (isCorrect) {
            showCorrectAnswerAnimation()
            showProgressCountdown(true)
        } else {
            showIncorrectAnswerAnimation()
            showProgressCountdown(false)
        }
        
        updateProgressDisplay()
        
        // IMPORTANT: Keep UI in WAITING state during sequence delay, not FINISHED
        // This ensures stop button stays visible and keyboard remains enabled during countdown
        updateUIForState(TrainingState.WAITING)
        
        // Automatically continue to next sequence after delay, but coordinate with audio
        val advanceAction: () -> Unit = {
            lifecycleScope.launch {
                delay(settings.sequenceDelayMs)
                startTraining()
            }
        }
        
        if (storeViewModel.audioState.value.isPlaying) {
            // Audio still playing - defer advancement until audio completes
            Log.d(TAG, "Audio still playing - deferring sequence advancement")
            shouldAdvanceAfterAudio = true
            pendingAdvanceAction = advanceAction
        } else {
            // Audio finished - advance immediately
            Log.d(TAG, "Audio completed - advancing to next sequence")
            advanceAction()
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
                // Show user input if they have any, otherwise show placeholder
                if (userInput.isNotEmpty()) {
                    updateSequenceDisplayWithInput()
                } else {
                    binding.sequenceDisplay.text = "Ready to start"
                }
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
                // Always show dual-row input display, even during audio playback
                updateSequenceDisplayWithInput()
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
                // Don't change sequence display text here - let it show the completion message
                binding.buttonStart.isEnabled = false
                binding.buttonStart.visibility = View.GONE
                binding.buttonStop.isEnabled = true
                binding.buttonStop.visibility = View.VISIBLE
                binding.buttonReplay.isEnabled = true
                binding.buttonReplay.visibility = View.VISIBLE
                binding.morseKeyboard.alpha = 1.0f
                setKeyboardEnabled(true)
                // DON'T clear userInput here - preserve what user typed during playback
                // Show current input or placeholder only if not showing completion feedback
                if (!binding.sequenceDisplay.text.toString().contains("✅") && 
                    !binding.sequenceDisplay.text.toString().contains("❌")) {
                    updateSequenceDisplayWithInput()
                }
                // Do NOT reset keyboard state here - preserve selection during input
            }
            TrainingState.FINISHED -> {
                Log.d(TAG, "FINISHED state: disabling keyboard")
                binding.sequenceDisplay.text = "Training stopped"
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
        val appState = storeViewModel.state.value
        val level = appState.settings.currentLevel
        val streak = appState.progressState.currentStreak
        
        // Calculate progress percentage based on streak and level requirements
        val requiredForNext = appState.settings.correctAnswersToLevelUp
        val progress = if (requiredForNext > 0 && streak > 0) {
            minOf(1.0f, streak.toFloat() / requiredForNext)
        } else {
            0.0f
        }
        val progressPercent = (progress * 100).toInt()
        
        // Show actual streak (can be negative) and progress percentage
        val streakText = if (streak >= 0) "Streak: $streak" else "Streak: $streak"
        
        binding.progressIndicator.text = "Level $level - Progress: ${progressPercent}% - $streakText"
        
        // Update the actual progress bar
        binding.progressBar.progress = progressPercent
    }
    
    private fun setupAnimations() {
        Log.d(TAG, "Setting up animations")
        
        // Debug progress bar initial state
        binding.sequenceProgressBar.apply {
            Log.d(TAG, "Initial progress bar state: visibility=$visibility, alpha=$alpha, progress=$progress")
            Log.d(TAG, "Progress bar dimensions: width=$width, height=$height")
            Log.d(TAG, "Progress bar position: x=$x, y=$y")

        }
        
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
    

    
    // showMessage now available as extension function

    private fun showProgressCountdown(isCorrect: Boolean) {
        val settings = storeViewModel.settings.value
        val duration = settings.sequenceDelayMs
        
        Log.d(TAG, "showProgressCountdown: isCorrect=$isCorrect, duration=${duration}ms")
        
        // Show progress bar for any delay > 100ms (to make it visible)
        if (duration <= 100) {
            Log.d(TAG, "Duration too short, skipping progress bar")
            return
        }
        
        // Use high-contrast colors for better visibility
        val indicatorColor = if (isCorrect) {
            Color.parseColor("#4CAF50") // Bright green
        } else {
            Color.parseColor("#F44336") // Bright red
        }
        
        val trackColor = Color.parseColor("#E0E0E0") // Light gray
        
        Log.d(TAG, "Setting progress bar colors: indicator=0x${Integer.toHexString(indicatorColor)}, track=0x${Integer.toHexString(trackColor)}")
        
        // Configure the integrated progress bar
        binding.sequenceProgressBar.apply {
            Log.d(TAG, "Progress bar before config: visibility=$visibility, alpha=$alpha, progress=$progress")
            
            setIndicatorColor(indicatorColor)
            setTrackColor(trackColor)
            progress = 100
            visibility = View.VISIBLE
            
            Log.d(TAG, "Progress bar after config: visibility=$visibility, alpha=$alpha, progress=$progress")
            
            // Force immediate visibility for testing
            alpha = 1f
            Log.d(TAG, "Progress bar forced visible: alpha=$alpha")
        }
        
        // Animate the progress countdown
        val animator = ValueAnimator.ofInt(100, 0)
        animator.duration = duration
        animator.interpolator = LinearInterpolator()
        
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Int
            binding.sequenceProgressBar.progress = progress
            if (progress % 20 == 0) { // Log every 20% for debugging
                Log.d(TAG, "Progress update: $progress%")
            }
        }
        
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                Log.d(TAG, "Progress countdown animation STARTED")
            }
            
            override fun onAnimationEnd(animation: Animator) {
                Log.d(TAG, "Progress countdown animation ENDED, hiding progress bar")
                // Hide with fade out animation
                binding.sequenceProgressBar.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        Log.d(TAG, "Progress bar fade-out completed")
                    }
                    .start()
            }
        })
        
        Log.d(TAG, "Starting progress countdown animation...")
        animator.start()
    }
    
    // Character history display moved to progress tracking tab
    
    override fun onDestroyView() {
        super.onDestroyView()
        audioManager.release()
        _binding = null
    }
}
