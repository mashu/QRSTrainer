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
    }

    private fun initializeComponents() {
        progressTracker = ProgressTracker(requireContext())
        sequenceGenerator = SequenceGenerator(progressTracker)
        morseGenerator = MorseCodeGenerator()
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
        val availableChars = MorseCode.getCharactersForLevel(settings.kochLevel)
        val allKochChars = MorseCode.KOCH_SEQUENCE
        
        binding.keyboardGrid.removeAllViews()
        
        // Create buttons for all Koch characters
        allKochChars.forEach { char ->
            val button = Button(requireContext()).apply {
                text = char.toString()
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = 80  // More compact height
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(3, 3, 3, 3)
                }
                
                // Apply modern styling
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                
                if (char in availableChars) {
                    // Available character - modern blue styling
                    background = ContextCompat.getDrawable(requireContext(), R.drawable.button_keyboard_selector)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.keyboard_text_available))
                    setOnClickListener { onCharacterPressed(char) }
                    alpha = 1.0f
                    isEnabled = true
                    elevation = 4f
                } else {
                    // Unavailable character - disabled styling
                    background = ContextCompat.getDrawable(requireContext(), R.drawable.button_keyboard_selector)
                    setTextColor(ContextCompat.getColor(requireContext(), R.color.keyboard_text_disabled))
                    alpha = 0.6f
                    isEnabled = false
                    elevation = 0f
                }
            }
            
            binding.keyboardGrid.addView(button)
        }
        
        // Initially disable keyboard until training starts
        enableKeyboard(false)
    }

    private fun onCharacterPressed(char: Char) {
        // Allow input only when waiting for answer or during playback
        if (currentState != TrainingState.WAITING && currentState != TrainingState.PLAYING && currentState != TrainingState.PAUSED) return
        
        userInput += char
        updateAnswerDisplay()
        
        // Check if we have enough characters
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
        // Cancel any existing timeout
        cancelTimeout()
        
        // Stop any current audio playback
        morseGenerator.stop()
        wasPlayingWhenPaused = false
        
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
        
        // Play the sequence
        playCurrentSequence()
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
        // Stop any current playback first
        morseGenerator.stop()
        wasPlayingWhenPaused = false
        
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
            settings.speedWpm,
            settings.repeatCount,
            settings.repeatSpacingMs
        ) {
            // On playback complete
            Handler(Looper.getMainLooper()).post {
                if (!isDetached && _binding != null) {
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
            morseGenerator.stop()
            isAudioPlaying = false
            isPaused = true
            currentState = TrainingState.PAUSED
            updateUIState()
        }
    }

    private fun stopTraining() {
        // Stop any ongoing operations
        morseGenerator.stop()
        cancelTimeout()
        
        // Reset to ready state
        currentState = TrainingState.READY
        isAudioPlaying = false
        isPaused = false
        isWaitingForAnswer = false
        wasPlayingWhenPaused = false
        
        updateUIState()
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
        cancelTimeout()
        currentState = TrainingState.FINISHED
        isWaitingForAnswer = false
        isAudioPlaying = false
        wasPlayingWhenPaused = false
        
        // Stop any ongoing audio since user has submitted answer
        morseGenerator.stop()
        
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
            checkLevelAdvancement()
            
        } else {
            // Record incorrect answers for each character
            currentSequence.forEach { char ->
                progressTracker.recordIncorrect(char)
            }
            
            binding.textStatus.text = getString(R.string.incorrect_answer, currentSequence)
            binding.textCurrentSequence.text = currentSequence
        }
        
        updateProgressDisplay()
        updateUIState()
        
        // Auto-advance after a delay
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isDetached && _binding != null) {
                startNewSequence()
            }
        }, 2000)
    }

    private fun handleTimeout() {
        currentState = TrainingState.FINISHED
        isWaitingForAnswer = false
        isAudioPlaying = false
        wasPlayingWhenPaused = false
        
        // Stop any ongoing audio
        morseGenerator.stop()
        
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
        
        // Auto-advance after a delay
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isDetached && _binding != null) {
                startNewSequence()
            }
        }, 3000)
    }

    private fun checkLevelAdvancement() {
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
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        morseGenerator.stop()
        cancelTimeout()
        _binding = null
    }
} 