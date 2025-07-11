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
import com.so5km.qrstrainer.AppState
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.audio.MorseCodeGenerator
import com.so5km.qrstrainer.audio.SharedAudioState
import com.so5km.qrstrainer.data.MorseCode
import com.so5km.qrstrainer.data.ProgressTracker
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.databinding.FragmentTrainerBinding
import com.so5km.qrstrainer.training.CharacterTimingCalculator
import com.so5km.qrstrainer.training.SequenceGenerator
import com.so5km.qrstrainer.ui.BaseAudioFragment
import com.so5km.qrstrainer.ui.settings.SettingsViewModel

class TrainerFragment : BaseAudioFragment() {

    private var _binding: FragmentTrainerBinding? = null
    private val binding get() = _binding!!

    private lateinit var progressTracker: ProgressTracker
    private lateinit var sequenceGenerator: SequenceGenerator
    override lateinit var morseGenerator: MorseCodeGenerator
    private var settings: TrainingSettings? = null
    private val characterTimingCalculator = CharacterTimingCalculator()
    
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
    
    // Sequence delay progress tracking
    private var delayHandler: Handler? = null
    private var delayRunnable: Runnable? = null
    private var delayStartTime: Long = 0
    private var totalDelayTime: Long = 0
    
    // Lifecycle state tracking
    override var wasPlayingWhenPaused = false
    
    // Session state tracking
    override var isSessionActive = false
    private var isNoiseRunning = false
    
    // Response time tracking
    private var sequenceStartTime: Long = 0
    private var characterTimings: List<CharacterTimingCalculator.CharacterTiming> = emptyList()
    private var characterResponseTimes = mutableMapOf<Char, Long>()
    
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
        settings = AppState.getSettings(requireContext())
        android.util.Log.d("TrainerFragment", "Settings loaded - sequence delay: ${settings?.sequenceDelayMs}ms")
        updateProgressDisplay()
        createKeyboard()  // Update keyboard in case level changed
        
        // Observe SharedAudioState
        observeSharedAudioState()
        
        // Observe AppState
        observeAppState()
        
        // If audio was playing when we paused, update UI to show stopped state
        if (wasPlayingWhenPaused) {
            wasPlayingWhenPaused = false
            currentState = TrainingState.READY
            updateUIState()
        }
    }
    
    private fun observeSharedAudioState() {
        SharedAudioState.isPlaying.observe(viewLifecycleOwner) { isPlaying ->
            if (!isPlaying && currentState == TrainingState.PLAYING) {
                // Audio finished playing
                currentState = TrainingState.WAITING
                updateUIState()
                
                // Start answer timeout
                startAnswerTimeout()
            }
        }
    }
    
    private fun observeAppState() {
        AppState.isAudioPlaying.observe(viewLifecycleOwner) { isPlaying ->
            isAudioPlaying = isPlaying
            if (!isPlaying && currentState == TrainingState.PLAYING) {
                currentState = TrainingState.READY
                updateUIState()
            }
        }
        
        AppState.isNoiseRunning.observe(viewLifecycleOwner) { isRunning ->
            isNoiseRunning = isRunning
        }
        
        // Observe settings changes
        settingsViewModel.settings.observe(viewLifecycleOwner) { updatedSettings ->
            settings = updatedSettings
            updateProgressDisplay()
        }
    }

    private fun initializeComponents() {
        progressTracker = ProgressTracker(requireContext())
        sequenceGenerator = SequenceGenerator(progressTracker)
        morseGenerator = MorseCodeGenerator(requireContext())
        settings = AppState.getSettings(requireContext())
        
        // Initialize handlers
        timeoutHandler = Handler(Looper.getMainLooper())
        delayHandler = Handler(Looper.getMainLooper())
    }

    private fun setupUI() {
        updateProgressDisplay()
        createKeyboard()
        
        // New control system
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
            currentState = TrainingState.PAUSED
            updateUIState()
        }
        
        binding.buttonStop.setOnClickListener {
            stopSession()
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
                
                if (isSessionActive) {
                    binding.buttonStart.text = "▶ REPLAY"
                    binding.buttonStop.visibility = View.VISIBLE
                } else {
                    binding.buttonStart.text = "▶ START"
                }
                
                binding.textStatus.text = if (isSessionActive) "Session active - ready for next sequence" else "Ready to start training"
                enableKeyboard(isSessionActive)
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
                binding.buttonStart.text = if (isSessionActive) "▶ NEXT" else "▶ START"
                binding.buttonStart.visibility = View.VISIBLE
                binding.buttonPause.visibility = View.GONE
                binding.buttonStop.visibility = if (isSessionActive) View.VISIBLE else View.GONE
                enableKeyboard(isSessionActive)
            }
        }
    }

    private fun enableKeyboard(enabled: Boolean) {
        for (i in 0 until binding.keyboardGrid.childCount) {
            val button = binding.keyboardGrid.getChildAt(i) as? Button
            button?.isEnabled = true // Always enabled for clicking
            button?.isActivated = enabled // Use activated state to track session status
            button?.alpha = 1.0f // Always fully visible
            
            // Update text color based on session state
            val textColor = if (enabled) {
                ContextCompat.getColor(requireContext(), R.color.keyboard_text_available)
            } else {
                ContextCompat.getColor(requireContext(), R.color.keyboard_text_inactive)
            }
            button?.setTextColor(textColor)
        }
    }

    private fun updateProgressDisplay() {
        val currentLevel = settings?.kochLevel ?: 1
        val requiredCorrect = settings?.requiredCorrectToAdvance ?: 3
        
        // Update level and WPM display
        binding.textLevel.text = "Level $currentLevel"
        binding.textWpm.text = "${settings?.speedWpm ?: 20} WPM"
        
        // Update score display
        val sessionCorrect = progressTracker.sessionCorrect
        val sessionTotal = progressTracker.sessionTotal
        binding.textScore.text = "Score: $sessionCorrect/$sessionTotal"
        
        // Update progress bar for level advancement
        val characters = MorseCode.getCharactersForLevel(currentLevel, settings?.lettersOnlyMode ?: false)
        val minCorrectCount = characters.minOfOrNull { char ->
            progressTracker.getCharacterStats(char).correctCount
        } ?: 0
        
        binding.progressLevel.max = requiredCorrect
        binding.progressLevel.progress = minCorrectCount
        
        val remaining = maxOf(0, requiredCorrect - minCorrectCount)
        binding.textNextLevel.text = if (remaining > 0) "$remaining more correct to advance" else "Ready to advance!"
    }

    private fun createKeyboard() {
        val allChars = MorseCode.getCharactersForLevel(settings?.kochLevel ?: 1, settings?.lettersOnlyMode ?: false)
        // Filter is no longer needed since we're using the lettersOnlyMode setting
        val availableChars = allChars
        
        // Debug: Log what characters we're trying to create
        android.util.Log.d("TrainerFragment", "Creating keyboard for level ${settings?.kochLevel}")
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
        // Handle differently based on session state
        if (currentState == TrainingState.WAITING || currentState == TrainingState.PLAYING || currentState == TrainingState.PAUSED) {
            // During active session - add to input
            userInput += char
            updateAnswerDisplay()
            
            // Calculate response time for the current character
            val currentIndex = userInput.length - 1
            if (currentIndex < currentSequence.length) {
                val currentChar = currentSequence[currentIndex]
                val responseTimeMs = System.currentTimeMillis() - sequenceStartTime
                
                // Find the timing for this character
                val charIndex = characterTimings.indexOfFirst { it.char == currentChar }
                if (charIndex >= 0) {
                    val charResponseTime = characterTimingCalculator.calculateCharacterResponseTime(
                        characterTimings, charIndex, responseTimeMs
                    )
                    // Store the response time for this character
                    characterResponseTimes[currentChar] = charResponseTime
                    android.util.Log.d("TrainerFragment", 
                        "Character response time: '$currentChar' - ${charResponseTime}ms")
                }
                
                // Check if this character is wrong
                val isCorrectChar = char.equals(currentChar, ignoreCase = true)
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
        } else {
            // Outside of active session - just play the character sound
            morseGenerator.playSingleCharacter(char, settings ?: TrainingSettings())
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
        delayHandler?.removeCallbacksAndMessages(null)
        
        // Hide progress bar
        hideSequenceDelayProgress()
        
        // Stop any current audio playback completely
        stopAudioCompletely()
        
        // Generate new sequence
        currentSequence = sequenceGenerator.generateSequence(
            settings?.kochLevel ?: 1,
            settings?.groupSizeMin ?: 1,
            settings?.groupSizeMax ?: 1,
            settings?.lettersOnlyMode ?: false
        )
        
        android.util.Log.d("TrainerFragment", "Starting new sequence: '$currentSequence'")
        
        // Reset input and UI state
        resetInputState()
        
        // Show previous answer briefly during new sequence
        updatePreviousAnswerDisplay()
        
        // Update the current sequence display with a question mark
        binding.textCurrentSequence.text = "?"
        
        // Small delay to ensure audio system is ready
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isDetached && _binding != null) {
                android.util.Log.d("TrainerFragment", "Playing sequence after delay: '$currentSequence'")
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
        characterResponseTimes.clear()
        
        binding.textAnswerInput.text = ""
        binding.textStatus.text = if (isSessionActive) "Ready for next sequence" else "Ready to start training"
        
        // Ensure we're in the READY state before playing a new sequence
        currentState = TrainingState.READY
        
        // Update UI to reflect the current state
        updateUIState()
        
        android.util.Log.d("TrainerFragment", "Input state reset, ready for new sequence")
    }

    private fun playCurrentSequence() {
        // Prevent starting playback if already playing
        if (isAudioPlaying) {
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
        
        // Calculate character timings for response time tracking
        characterTimings = characterTimingCalculator.calculateCharacterTimings(currentSequence, settings ?: TrainingSettings())
        characterResponseTimes.clear()
        
        // Record the start time for response time tracking
        sequenceStartTime = System.currentTimeMillis()
        
        android.util.Log.d("TrainerFragment", "Playing sequence: '$currentSequence'")
        
        // Play the sequence without starting noise (noise is handled at session level)
        morseGenerator.playSequence(
            currentSequence,
            settings ?: TrainingSettings(),
            startNoise = false  // Don't start noise for each sequence
        ) {
            // On playback complete
            Handler(Looper.getMainLooper()).post {
                if (!isDetached && _binding != null && isAudioPlaying) {
                    isAudioPlaying = false
                    wasPlayingWhenPaused = false
                    currentState = TrainingState.WAITING
                    updateUIState()
                    
                    // Start answer timeout ONLY after playback completes
                    startAnswerTimeout()
                    
                    android.util.Log.d("TrainerFragment", "Sequence playback complete, waiting for answer")
                }
            }
        }
    }

    override fun pausePlayback() {
        super.pausePlayback()
        currentState = TrainingState.PAUSED
        updateUIState()
    }
    
    override fun resumePlayback() {
        super.resumePlayback()
        currentState = TrainingState.PLAYING
        updateUIState()
    }

    private fun stopTraining() {
        // Cancel all timeouts and scheduled operations
        cancelTimeout()
        timeoutHandler?.removeCallbacksAndMessages(null)
        
        // Stop any ongoing audio operations completely
        stopAudioCompletely()
        
        // Hide progress bar
        hideSequenceDelayProgress()
        
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
                binding.textStatus.text = if (isSessionActive) "Session active - ready for next sequence" else "Ready to start training"
            }
        }, 1000)
    }

    private fun startAnswerTimeout() {
        // Cancel any existing timeout
        timeoutRunnable?.let { timeoutHandler?.removeCallbacks(it) }
        
        // Create new timeout
        timeoutRunnable = Runnable {
            if (isWaitingForAnswer) {
                // Time's up - mark as incorrect
                handleTimeout()
            }
        }
        
        // Start timeout timer
        val timeoutSeconds = settings?.answerTimeoutSeconds ?: 5
        timeoutHandler?.postDelayed(timeoutRunnable!!, (timeoutSeconds * 1000).toLong())
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
            // Record correct answers and response time for each character
            for (i in currentSequence.indices) {
                val char = currentSequence[i]
                progressTracker.recordCorrect(char)
                
                // Record response time if we have it
                val responseTime = characterResponseTimes[char]
                if (responseTime != null) {
                    progressTracker.recordResponseTime(char, responseTime)
                    android.util.Log.d("TrainerFragment", "Recording response time for '$char': ${responseTime}ms")
                }
            }
            
            // Check for level advancement
            val levelAdvanced = checkLevelAdvancement()
            
            // Update progress display
            updateProgressDisplay()
            updateUIState()
            
            // Calculate delay: use separate settings for level changes vs normal sequences
            val baseDelay = settings?.sequenceDelayMs?.toLong() ?: 0L
            val delay = if (levelAdvanced) {
                // Use level change delay setting
                settings?.levelChangeDelayMs?.toLong() ?: 0L
            } else {
                // Use user's configured sequence delay (including 0 for instant)
                baseDelay
            }
            
            // Show delay info in status
            if (levelAdvanced) {
                binding.textStatus.text = "Level Up! Now at level ${settings?.kochLevel}"
                if (delay > 0) {
                    binding.textStatus.text = "${binding.textStatus.text} (${delay/1000.0}s delay)"
                }
            } else {
                binding.textStatus.text = getString(R.string.correct_answer)
                if (delay == 0L) {
                    binding.textStatus.text = "${binding.textStatus.text} (instant next)"
                } else {
                    binding.textStatus.text = "${binding.textStatus.text} (${delay/1000.0}s delay)"
                }
            }
            
            android.util.Log.d("TrainerFragment", "Correct answer - baseDelay: ${baseDelay}ms, levelAdvanced: $levelAdvanced, finalDelay: ${delay}ms")
            scheduleNextSequence(levelAdvanced)
            
        } else {
            // Record incorrect answers for each character
            currentSequence.forEach { char ->
                progressTracker.recordIncorrect(char)
            }
            
            // Check for level drop
            val levelDropped = checkLevelDrop()
            
            // Update progress display
            updateProgressDisplay()
            updateUIState()
            
            // Calculate delay: use separate settings for level changes vs normal sequences
            val baseDelay = settings?.sequenceDelayMs?.toLong() ?: 0L
            val delay = if (levelDropped) {
                // Use level change delay setting
                settings?.levelChangeDelayMs?.toLong() ?: 0L
            } else {
                // Use user's configured sequence delay (including 0 for instant)
                baseDelay
            }
            
            // Show delay info in status
            if (levelDropped) {
                binding.textStatus.text = "Level Down! Now at level ${settings?.kochLevel}"
                if (delay > 0) {
                    binding.textStatus.text = "${binding.textStatus.text} (${delay/1000.0}s delay)"
                }
            } else {
                binding.textStatus.text = getString(R.string.incorrect_answer, currentSequence)
                if (delay == 0L) {
                    binding.textStatus.text = "${binding.textStatus.text} (instant next)"
                } else {
                    binding.textStatus.text = "${binding.textStatus.text} (${delay/1000.0}s delay)"
                }
            }
            
            android.util.Log.d("TrainerFragment", "Incorrect answer - baseDelay: ${baseDelay}ms, levelDropped: $levelDropped, finalDelay: ${delay}ms")
            scheduleNextSequence(levelDropped)
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
        
        // Check for level drop
        val levelDropped = checkLevelDrop()
        
        binding.textStatus.text = getString(R.string.timeout_answer, currentSequence)
        binding.textCurrentSequence.text = currentSequence
        
        updateProgressDisplay()
        updateUIState()
        
        // Calculate delay: use separate settings for level changes vs normal sequences
        val baseDelay = settings?.sequenceDelayMs?.toLong() ?: 0L
        val delay = if (levelDropped) {
            // Use level change delay setting
            settings?.levelChangeDelayMs?.toLong() ?: 0L
        } else {
            // For timeout without level change, use max of base delay or 2s
            maxOf(baseDelay, 2000L)
        }
        
        android.util.Log.d("TrainerFragment", "Timeout - baseDelay: ${baseDelay}ms, levelDropped: $levelDropped, finalDelay: ${delay}ms")
        scheduleNextSequence(levelDropped)
    }

    private fun checkLevelAdvancement(): Boolean {
        if (!(settings?.isLevelLocked ?: false) && 
            progressTracker.canAdvanceLevel(settings?.kochLevel ?: 1, settings?.requiredCorrectToAdvance ?: 3)) {
            
            val currentLevel = settings?.kochLevel ?: 1
            val newLevel = minOf(currentLevel + 1, MorseCode.getMaxLevel())
            if (newLevel > currentLevel) {
                // Advance to next level
                settings = settings?.copy(kochLevel = newLevel)
                settings?.let { 
                    AppState.updateSettings(requireContext(), it)
                    android.util.Log.d("TrainerFragment", "Level advanced to $newLevel, updating settings")
                }
                
                // Reset level mistakes counter when advancing
                progressTracker.resetLevelMistakes()
                
                // Update keyboard for new level
                createKeyboard()
                
                binding.textStatus.text = "Level Up! Now at level $newLevel"
                
                // Force update progress display
                updateProgressDisplay()
                
                return true
            }
        }
        return false
    }
    
    private fun checkLevelDrop(): Boolean {
        if (!(settings?.isLevelLocked ?: false) && 
            progressTracker.shouldDropLevel(settings?.mistakesToDropLevel ?: 0)) {
            
            val currentLevel = settings?.kochLevel ?: 1
            val newLevel = maxOf(currentLevel - 1, 1) // Don't drop below level 1
            if (newLevel < currentLevel) {
                // Drop to previous level
                settings = settings?.copy(kochLevel = newLevel)
                settings?.let { AppState.updateSettings(requireContext(), it) }
                
                // Reset level mistakes counter when dropping
                progressTracker.resetLevelMistakes()
                
                // Update keyboard for new level
                createKeyboard()
                
                binding.textStatus.text = "Level Down! Now at level $newLevel"
                return true
            }
        }
        return false
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
     * Schedule the next sequence with optional delay.
     * If delay is 0, starts immediately (no progress bar).
     * If delay > 0, shows a progress bar during the delay period.
     */
    private fun scheduleNextSequence(levelChanged: Boolean = false) {
        // Cancel any existing delay
        delayRunnable?.let { delayHandler?.removeCallbacks(it) }
        
        // Create new delay handler if null
        if (delayHandler == null) {
            delayHandler = Handler(Looper.getMainLooper())
        }
        
        // Create new delay
        delayRunnable = Runnable {
            // Generate and play new sequence
            startNewSequence()
            
            // Reset delay tracking
            delayStartTime = 0
            totalDelayTime = 0
        }
        
        // Calculate delay: use separate settings for level changes vs normal sequences
        val baseDelay = settings?.sequenceDelayMs?.toLong() ?: 0L
        val delay = if (levelChanged) {
            // Use level change delay setting
            settings?.levelChangeDelayMs?.toLong() ?: 0L
        } else {
            // Use user's configured sequence delay (including 0 for instant)
            baseDelay
        }
        
        android.util.Log.d("TrainerFragment", "Scheduling next sequence with delay: ${delay}ms, levelChanged: $levelChanged")
        
        // Start delay timer
        if (delay > 0) {
            delayStartTime = System.currentTimeMillis()
            totalDelayTime = delay
            
            // Ensure we have a valid handler before posting
            if (delayHandler != null && delayRunnable != null) {
                android.util.Log.d("TrainerFragment", "Posting delay runnable with ${delay}ms delay")
                delayHandler?.postDelayed(delayRunnable!!, delay)
            } else {
                android.util.Log.e("TrainerFragment", "Cannot schedule next sequence: delayHandler or delayRunnable is null")
                // Fall back to immediate execution
                delayRunnable?.run()
            }
        } else {
            // No delay - start immediately
            android.util.Log.d("TrainerFragment", "No delay, starting next sequence immediately")
            delayRunnable?.run()
        }
    }
    
    /**
     * Show the sequence delay progress bar and start updating it
     */
    private fun showSequenceDelayProgress(delayMs: Long, isLevelChange: Boolean) {
        android.util.Log.d("TrainerFragment", "showSequenceDelayProgress called with ${delayMs}ms")
        if (_binding == null) return
        
        totalDelayTime = delayMs
        delayStartTime = System.currentTimeMillis()
        
        // Show the progress bar overlay
        binding.layoutSequenceDelayOverlay.visibility = View.VISIBLE
        binding.progressSequenceDelay.progress = 0
        binding.textSequenceDelayTime.text = String.format("%.1fs", delayMs / 1000.0)
        
        // Set different colors and text for level changes
        if (isLevelChange) {
            binding.textSequenceDelayStatus.text = "Level change delay..."
            // Use orange color for level changes
            binding.textSequenceDelayTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_orange))
        } else {
            binding.textSequenceDelayStatus.text = "Next sequence in..."
            // Use default blue color for normal delays
            binding.textSequenceDelayTime.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_blue))
        }
        
        // Fade in animation
        binding.layoutSequenceDelayOverlay.alpha = 0f
        binding.layoutSequenceDelayOverlay.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        
        android.util.Log.d("TrainerFragment", "Progress bar shown, starting updates")
        
        // Start updating progress
        updateSequenceDelayProgress(isLevelChange)
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
    private fun updateSequenceDelayProgress(isLevelChange: Boolean) {
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
     * Start a new training session
     */
    private fun startSession() {
        isSessionActive = true
        
        // Start continuous background noise if enabled
        if (settings?.filterRingingEnabled == true && (settings?.backgroundNoiseLevel ?: 0f) > 0f) {
            startContinuousNoise()
        }
        
        // Start a new sequence
        startNewSequence()
    }
    
    /**
     * Stop the current training session
     */
    private fun stopSession() {
        // Stop any ongoing sequence
        stopTraining()
        
        // Stop continuous background noise
        stopContinuousNoise()
        
        // Reset session state
        isSessionActive = false
        
        // Update UI
        updateUIState()
    }
    
    /**
     * Start continuous background noise for the session
     */
    override fun startContinuousNoise() {
        super.startContinuousNoise()
        isNoiseRunning = true
        android.util.Log.d("TrainerFragment", "Started continuous background noise for session")
    }
    
    /**
     * Stop continuous background noise
     */
    override fun stopContinuousNoise() {
        super.stopContinuousNoise()
        isNoiseRunning = false
        android.util.Log.d("TrainerFragment", "Stopped continuous background noise")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        morseGenerator.stop()
        morseGenerator.release()  // Clean up ToneGenerator resources
        cancelTimeout()
        hideSequenceDelayProgress() // Clean up delay handler
        
        // Ensure session is stopped
        isSessionActive = false
        isNoiseRunning = false
        
        _binding = null
    }
} 