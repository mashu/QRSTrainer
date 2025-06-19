package com.so5km.qrstrainer.ui.trainer

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.audio.MorseCodeGenerator
import com.so5km.qrstrainer.data.MorseCode
import com.so5km.qrstrainer.data.ProgressTracker
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.databinding.FragmentTrainerBinding
import com.so5km.qrstrainer.training.SequenceGenerator

class TrainerFragment : Fragment() {

    private var _binding: FragmentTrainerBinding? = null
    private val binding get() = _binding!!

    private lateinit var progressTracker: ProgressTracker
    private lateinit var sequenceGenerator: SequenceGenerator
    private lateinit var morseGenerator: MorseCodeGenerator
    private lateinit var settings: TrainingSettings
    
    private var currentSequence: String = ""
    private var userInput: String = ""
    private var isWaitingForAnswer = false
    private var isAudioPlaying = false
    private var isPaused = false
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null
    
    // Previous answer tracking
    private var previousSequence: String = ""
    private var previousUserInput: String = ""
    private var previousWasCorrect: Boolean = false
    
    // Lifecycle state tracking
    private var wasPlayingWhenPaused = false

    // Training states
    enum class TrainingState {
        READY,      // Ready to start
        PLAYING,    // Audio playing
        PAUSED,     // Audio paused
        WAITING,    // Waiting for answer after audio
        FINISHED    // Answer submitted or timeout
    }
    
    private var currentState = TrainingState.READY

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrainerBinding.inflate(inflater, container, false)
        val root: View = binding.root

        initializeComponents()
        setupUI()
        updateUIState()

        return root
    }
    
    override fun onResume() {
        super.onResume()
        // Reload settings when returning to trainer (e.g., from settings screen)
        settings = TrainingSettings.load(requireContext())
        updateProgressDisplay()
        createKeyboard()  // Update keyboard in case level changed
        
        // If audio was playing when we paused, update UI to show stopped state
        if (wasPlayingWhenPaused) {
            wasPlayingWhenPaused = false
            currentState = TrainingState.READY
            updateUIState()
        }
        
        // Resume audio when app comes back to foreground
        if (isPaused && wasPlayingWhenPaused) {
            resumePlayback()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Stop audio playback when app goes to background
        if (isAudioPlaying || morseGenerator.isPlaying()) {
            wasPlayingWhenPaused = true
            morseGenerator.stop()
            isAudioPlaying = false
            isPaused = false
            currentState = TrainingState.READY
        }
        
        // Pause audio when app goes to background
        if (isAudioPlaying && !isPaused) {
            pausePlayback()
        }
    }

    private fun initializeComponents() {
        progressTracker = ProgressTracker(requireContext())
        sequenceGenerator = SequenceGenerator(progressTracker)
        morseGenerator = MorseCodeGenerator(requireContext())
        settings = TrainingSettings.load(requireContext())
    }

    private fun setupUI() {
        updateProgressDisplay()
        createKeyboard()
        
        // New control system
        binding.buttonStart.setOnClickListener {
            startNewSequence()
        }
        
        binding.buttonPause.setOnClickListener {
            pausePlayback()
        }
        
        binding.buttonStop.setOnClickListener {
            stopTraining()
        }
        
        // Legacy controls (hidden by default)
        binding.buttonPlayAgain.setOnClickListener {
            playCurrentSequence()
        }
        
        binding.buttonNext.setOnClickListener {
            startNewSequence()
        }
    }

    private fun updateUIState() {
        when (currentState) {
            TrainingState.READY -> {
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonPause.visibility = View.GONE
                binding.buttonStop.visibility = View.GONE
                binding.textStatus.text = "Ready to start training"
                enableKeyboard(false)
            }
            TrainingState.PLAYING -> {
                binding.buttonStart.visibility = View.GONE
                binding.buttonPause.visibility = View.VISIBLE
                binding.buttonStop.visibility = View.VISIBLE
                binding.textStatus.text = "Playing sequence..."
                enableKeyboard(true)
            }
            TrainingState.PAUSED -> {
                binding.buttonStart.text = "▶ RESUME"
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonPause.visibility = View.GONE
                binding.buttonStop.visibility = View.VISIBLE
                binding.textStatus.text = "Paused - Press Resume to continue"
                enableKeyboard(true)
            }
            TrainingState.WAITING -> {
                binding.buttonStart.text = "▶ REPLAY"
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonPause.visibility = View.GONE
                binding.buttonStop.visibility = View.VISIBLE
                binding.textStatus.text = getString(R.string.listening_prompt)
                enableKeyboard(true)
            }
            TrainingState.FINISHED -> {
                binding.buttonStart.text = "▶ START"
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonPause.visibility = View.GONE
                binding.buttonStop.visibility = View.GONE
                enableKeyboard(false)
            }
        }
    }

    private fun enableKeyboard(enabled: Boolean) {
        for (i in 0 until binding.keyboardGrid.childCount) {
            val button = binding.keyboardGrid.getChildAt(i) as? Button
            button?.isEnabled = enabled
            button?.alpha = if (enabled) 1.0f else 0.6f
            
            // Update text color based on enabled state
            val textColor = if (enabled) {
                ContextCompat.getColor(requireContext(), R.color.keyboard_text_available)
            } else {
                ContextCompat.getColor(requireContext(), R.color.keyboard_text_disabled)
            }
            button?.setTextColor(textColor)
        }
    }

    private fun updateProgressDisplay() {
        val currentLevel = settings.kochLevel
        val requiredCorrect = settings.requiredCorrectToAdvance
        
        // Update level and WPM display
        binding.textLevel.text = "Level $currentLevel"
        binding.textWpm.text = "${settings.speedWpm} WPM"
        
        // Update score display
        val sessionCorrect = progressTracker.sessionCorrect
        val sessionTotal = progressTracker.sessionTotal
        binding.textScore.text = "Score: $sessionCorrect/$sessionTotal"
        
        // Update progress bar for level advancement
        val characters = MorseCode.getCharactersForLevel(currentLevel)
        val minCorrectCount = characters.minOfOrNull { char ->
            progressTracker.getCharacterStats(char).correctCount
        } ?: 0
        
        binding.progressLevel.max = requiredCorrect
        binding.progressLevel.progress = minCorrectCount
        
        val remaining = maxOf(0, requiredCorrect - minCorrectCount)
        binding.textNextLevel.text = if (remaining > 0) "$remaining more correct to advance" else "Ready to advance!"
    }

    private fun createKeyboard() {
        val allChars = MorseCode.getCharactersForLevel(settings.kochLevel)
        // Filter to only include A-Z letters for beginner-friendly training
        val availableChars = allChars.filter { it.isLetter() }.toTypedArray()
        
        // Debug: Log what characters we're trying to create
        android.util.Log.d("TrainerFragment", "Creating keyboard for level ${settings.kochLevel}")
        android.util.Log.d("TrainerFragment", "Available chars: ${availableChars.joinToString()}")
        
        binding.keyboardGrid.removeAllViews()
        
        // Calculate optimal layout based on number of characters and screen size
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val numChars = availableChars.size
        
        // Calculate optimal column count and button dimensions
        val keyboardLayout = calculateOptimalLayout(numChars, screenWidth, screenHeight, displayMetrics.density)
        
        // Update grid layout column count
        binding.keyboardGrid.columnCount = keyboardLayout.columnCount
        
        // Create buttons only for characters available at current Koch level
        availableChars.forEach { char ->
            val button = Button(requireContext()).apply {
                // Debug: Set text explicitly and log it
                val buttonText = char.toString()
                text = buttonText
                android.util.Log.d("TrainerFragment", "Creating button with text: '$buttonText' for char: '$char'")
                
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = keyboardLayout.buttonHeight
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(keyboardLayout.margin, keyboardLayout.margin, keyboardLayout.margin, keyboardLayout.margin)
                }
                
                // Apply responsive text size based on button size
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, keyboardLayout.textSize)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                
                // Set colors using keyboard-specific colors
                background = ContextCompat.getDrawable(requireContext(), R.drawable.button_keyboard_selector)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.keyboard_text_available))
                
                // Ensure proper text centering and responsive padding
                setAllCaps(false)
                includeFontPadding = false
                setPadding(keyboardLayout.padding, keyboardLayout.padding, keyboardLayout.padding, keyboardLayout.padding)
                gravity = android.view.Gravity.CENTER  // Center text in button
                
                setOnClickListener { onCharacterPressed(char) }
                alpha = 1.0f
                isEnabled = true
                elevation = 6f
                
                // Force text to be visible
                visibility = android.view.View.VISIBLE
            }
            
            binding.keyboardGrid.addView(button)
        }
        
        // Initially disable keyboard until training starts
        enableKeyboard(false)
    }
    
    private data class KeyboardLayoutParams(
        val columnCount: Int,
        val buttonHeight: Int,
        val textSize: Float,
        val padding: Int,
        val margin: Int
    )
    
    private fun calculateOptimalLayout(numChars: Int, screenWidth: Int, screenHeight: Int, density: Float): KeyboardLayoutParams {
        // Available height for keyboard (much more conservative - 35% of screen)
        val availableHeight = (screenHeight * 0.35).toInt()
        
        // Calculate optimal column count - use more columns to save vertical space
        val optimalColumns = when {
            numChars <= 12 -> 6  // Early levels: 6 columns
            numChars <= 18 -> 7  // Mid levels: 7 columns  
            else -> 8            // Max levels (26 letters): 8 columns for maximum compactness
        }
        
        // Calculate number of rows needed
        val numRows = kotlin.math.ceil(numChars.toDouble() / optimalColumns).toInt()
        
        // Ultra-compact buttons - minimal margins
        val marginDp = 2  // Very small margins
        val marginPx = (marginDp * density).toInt()
        val totalMarginHeight = marginPx * 2 * numRows
        val availableButtonHeight = availableHeight - totalMarginHeight
        
        // Calculate button height - make them minimal, just enough for letters
        val minButtonHeight = (28 * density).toInt() // Minimum 28dp - very compact
        val maxButtonHeight = (36 * density).toInt() // Maximum 36dp - much smaller
        val fittedButtonHeight = availableButtonHeight / numRows
        
        val buttonHeightPx = maxOf(minButtonHeight, minOf(maxButtonHeight, fittedButtonHeight))
        
        // Calculate text size - make it fill most of the button
        val buttonHeightDp = buttonHeightPx / density
        val textSizeDp = maxOf(12f, minOf(16f, buttonHeightDp * 0.5f)) // 50% of button height
        
        // Minimal padding - just enough to prevent text from touching edges
        val paddingPx = maxOf(2, (buttonHeightPx * 0.05).toInt()) // 5% of button height, minimum 2px
        
        android.util.Log.d("TrainerFragment", 
            "Ultra-compact layout: chars=$numChars, cols=$optimalColumns, rows=$numRows, " +
            "buttonH=${buttonHeightDp}dp, textSize=${textSizeDp}dp, padding=${paddingPx}px")
        
        return KeyboardLayoutParams(
            columnCount = optimalColumns,
            buttonHeight = buttonHeightPx,
            textSize = textSizeDp,
            padding = paddingPx,
            margin = marginPx
        )
    }

    private fun onCharacterPressed(char: Char) {
        // Allow input only when waiting for answer or during playback
        if (currentState != TrainingState.WAITING && currentState != TrainingState.PLAYING && currentState != TrainingState.PAUSED) return
        
        userInput += char
        updateAnswerDisplay()
        
        // Check immediately if this character is wrong
        val currentIndex = userInput.length - 1
        if (currentIndex < currentSequence.length) {
            val isCorrectChar = userInput[currentIndex].equals(currentSequence[currentIndex], ignoreCase = true)
            
            if (!isCorrectChar) {
                // Wrong character entered - immediately check answer (which will be marked as incorrect)
                checkAnswer()
                return
            }
        }
        
        // Check if we have entered the complete correct sequence
        if (userInput.length >= currentSequence.length) {
            checkAnswer()
        }
    }

    private fun updateAnswerDisplay() {
        val spannable = SpannableString(userInput)
        
        // Color each character based on correctness
        for (i in userInput.indices) {
            val color = if (i < currentSequence.length && userInput[i].equals(currentSequence[i], ignoreCase = true)) {
                Color.parseColor("#4CAF50") // Green for correct
            } else {
                Color.parseColor("#F44336") // Red for incorrect
            }
            spannable.setSpan(
                ForegroundColorSpan(color),
                i, i + 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        binding.textAnswerInput.text = spannable
    }

    private fun updatePreviousAnswerDisplay() {
        if (previousSequence.isNotEmpty()) {
            val displayText = "Previous: $previousSequence → $previousUserInput"
            val spannable = SpannableString(displayText)
            
            // Color the arrow and user input part
            val arrowIndex = displayText.indexOf("→")
            if (arrowIndex != -1) {
                val userInputStart = arrowIndex + 2
                val color = if (previousWasCorrect) {
                    Color.parseColor("#4CAF50") // Green for correct
                } else {
                    Color.parseColor("#F44336") // Red for incorrect
                }
                spannable.setSpan(
                    ForegroundColorSpan(color),
                    userInputStart, displayText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            binding.textPreviousAnswer.text = spannable
        } else {
            binding.textPreviousAnswer.text = ""
        }
    }

    private fun startNewSequence() {
        // Cancel any existing timeouts and scheduled sequences
        cancelTimeout()
        timeoutHandler?.removeCallbacksAndMessages(null)
        
        // Stop any current audio playback completely
        stopAudioCompletely()
        
        // Generate new sequence
        currentSequence = sequenceGenerator.generateSequence(
            settings.kochLevel,
            settings.groupSizeMin,
            settings.groupSizeMax
        )
        
        // Reset input and UI state
        resetInputState()
        
        // Show previous answer briefly during new sequence
        updatePreviousAnswerDisplay()
        
        // Small delay to ensure audio system is ready
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isDetached && _binding != null && currentState == TrainingState.READY) {
                playCurrentSequence()
            }
        }, 100)
    }

    private fun resetInputState() {
        userInput = ""
        isWaitingForAnswer = false
        isAudioPlaying = false
        isPaused = false
        wasPlayingWhenPaused = false
        binding.textAnswerInput.text = ""
        binding.textCurrentSequence.text = "?"
        currentState = TrainingState.READY
    }

    private fun playCurrentSequence() {
        // Prevent starting playback if already playing or in wrong state
        if (isAudioPlaying || currentState != TrainingState.READY) {
            android.util.Log.w("TrainerFragment", "Cannot start playback: isAudioPlaying=$isAudioPlaying, currentState=$currentState")
            return
        }
        
        // Stop any current playback first (defensive)
        stopAudioCompletely()
        
        // Update state
        currentState = TrainingState.PLAYING
        isAudioPlaying = true
        isWaitingForAnswer = true
        isPaused = false
        updateUIState()
        
        // Start answer timeout immediately when playback begins
        startAnswerTimeout()
        
        morseGenerator.playSequence(
            currentSequence,
            settings  // Pass the full settings object
        ) {
            // On playback complete
            Handler(Looper.getMainLooper()).post {
                if (!isDetached && _binding != null && isAudioPlaying) {
                    isAudioPlaying = false
                    wasPlayingWhenPaused = false
                    currentState = TrainingState.WAITING
                    updateUIState()
                }
            }
        }
    }

    private fun pausePlayback() {
        if (isAudioPlaying) {
            morseGenerator.pause()
            isAudioPlaying = false
            isPaused = true
            wasPlayingWhenPaused = true
            currentState = TrainingState.PAUSED
            updateUIState()
        }
    }

    private fun resumePlayback() {
        if (isPaused && wasPlayingWhenPaused) {
            morseGenerator.resume()
            isAudioPlaying = true
            isPaused = false
            wasPlayingWhenPaused = false
            currentState = TrainingState.PLAYING
            updateUIState()
        }
    }

    private fun stopTraining() {
        // Cancel all timeouts and scheduled operations
        cancelTimeout()
        timeoutHandler?.removeCallbacksAndMessages(null)
        
        // Stop any ongoing audio operations completely
        stopAudioCompletely()
        
        // Reset all state variables
        currentState = TrainingState.READY
        isWaitingForAnswer = false
        
        // Clear user input
        userInput = ""
        binding.textAnswerInput.text = ""
        binding.textCurrentSequence.text = "?"
        
        // Update UI to reflect stopped state
        updateUIState()
        
        // Brief status message
        binding.textStatus.text = "Training stopped"
        
        // Clear status message after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isDetached && _binding != null && currentState == TrainingState.READY) {
                binding.textStatus.text = "Ready to start training"
            }
        }, 1000)
    }

    private fun startAnswerTimeout() {
        cancelTimeout()
        
        timeoutHandler = Handler(Looper.getMainLooper())
        timeoutRunnable = Runnable {
            if (isWaitingForAnswer) {
                handleTimeout()
            }
        }
        
        timeoutHandler?.postDelayed(timeoutRunnable!!, (settings.answerTimeoutSeconds * 1000).toLong())
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { timeoutHandler?.removeCallbacks(it) }
        timeoutHandler = null
        timeoutRunnable = null
    }

    private fun checkAnswer() {
        // Prevent multiple simultaneous answer checks
        if (currentState == TrainingState.FINISHED) return
        
        cancelTimeout()
        currentState = TrainingState.FINISHED
        isWaitingForAnswer = false
        
        // Stop any ongoing audio immediately and wait for it to fully stop
        stopAudioCompletely()
        
        val isCorrect = userInput.equals(currentSequence, ignoreCase = true)
        
        // Store previous answer for next round
        previousSequence = currentSequence
        previousUserInput = userInput
        previousWasCorrect = isCorrect
        
        if (isCorrect) {
            // Record correct answers for each character
            currentSequence.forEach { char ->
                progressTracker.recordCorrect(char)
            }
            
            binding.textStatus.text = getString(R.string.correct_answer)
            binding.textCurrentSequence.text = currentSequence
            
            // Check for level advancement
            val levelAdvanced = checkLevelAdvancement()
            
            // Update progress display
            updateProgressDisplay()
            updateUIState()
            
            // Use different delay if level advanced to give user time to see the advancement
            val baseDelay = settings.sequenceDelayMs.toLong()
            val delay = if (levelAdvanced) maxOf(baseDelay, 2000L) else baseDelay // Minimum 2s for level advancement
            scheduleNextSequence(delay)
            
        } else {
            // Record incorrect answers for each character
            currentSequence.forEach { char ->
                progressTracker.recordIncorrect(char)
            }
            
            binding.textStatus.text = getString(R.string.incorrect_answer, currentSequence)
            binding.textCurrentSequence.text = currentSequence
            
            updateProgressDisplay()
            updateUIState()
            
            scheduleNextSequence(settings.sequenceDelayMs.toLong())
        }
    }

    private fun handleTimeout() {
        // Prevent multiple simultaneous timeout handling
        if (currentState == TrainingState.FINISHED) return
        
        currentState = TrainingState.FINISHED
        isWaitingForAnswer = false
        
        // Stop any ongoing audio completely
        stopAudioCompletely()
        
        // Store previous answer for next round
        previousSequence = currentSequence
        previousUserInput = userInput.ifEmpty { "(no answer)" }
        previousWasCorrect = false
        
        // Record incorrect for unanswered characters
        currentSequence.forEach { char ->
            progressTracker.recordIncorrect(char)
        }
        
        binding.textStatus.text = getString(R.string.timeout_answer, currentSequence)
        binding.textCurrentSequence.text = currentSequence
        
        updateProgressDisplay()
        updateUIState()
        
        // Schedule next sequence (timeout gets longer delay)
        val timeoutDelay = maxOf(settings.sequenceDelayMs.toLong(), 2000L) // Minimum 2s for timeout
        scheduleNextSequence(timeoutDelay)
    }

    private fun checkLevelAdvancement(): Boolean {
        if (!settings.isLevelLocked && 
            progressTracker.canAdvanceLevel(settings.kochLevel, settings.requiredCorrectToAdvance)) {
            
            val newLevel = minOf(settings.kochLevel + 1, MorseCode.getMaxLevel())
            if (newLevel > settings.kochLevel) {
                // Advance to next level
                settings = settings.copy(kochLevel = newLevel)
                settings.save(requireContext())
                
                // Update keyboard for new level
                createKeyboard()
                
                binding.textStatus.text = "Level Up! Now at level $newLevel"
                return true
            }
        }
        return false
    }
    
    private fun stopAudioCompletely() {
        // Stop audio and reset all audio-related state
        morseGenerator.stop()
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
    
    private fun scheduleNextSequence(delayMs: Long) {
        // Cancel any existing scheduled sequence
        timeoutHandler?.removeCallbacksAndMessages(null)
        
        if (delayMs == 0L) {
            // No delay - start immediately
            if (!isDetached && _binding != null && currentState == TrainingState.FINISHED) {
                startNewSequence()
            }
        } else {
            // Schedule next sequence with delay
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isDetached && _binding != null && currentState == TrainingState.FINISHED) {
                    startNewSequence()
                }
            }, delayMs)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        morseGenerator.stop()
        morseGenerator.release()  // Clean up ToneGenerator resources
        cancelTimeout()
        _binding = null
    }
} 