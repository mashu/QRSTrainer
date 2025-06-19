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
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null
    
    // Previous answer tracking
    private var previousSequence: String = ""
    private var previousUserInput: String = ""
    private var previousWasCorrect: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrainerBinding.inflate(inflater, container, false)
        val root: View = binding.root

        initializeComponents()
        setupUI()
        startNewSequence()

        return root
    }
    
    override fun onResume() {
        super.onResume()
        // Reload settings when returning to trainer (e.g., from settings screen)
        settings = TrainingSettings.load(requireContext())
        updateProgressDisplay()
        createKeyboard()  // Update keyboard in case level changed
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
        
        binding.buttonPlayAgain.setOnClickListener {
            // Stop any current playback and replay current sequence
            playCurrentSequence()
        }
        
        binding.buttonNext.setOnClickListener {
            // Stop any current playback and start new sequence
            startNewSequence()
        }
    }

    private fun updateProgressDisplay() {
        val currentLevel = settings.kochLevel
        val requiredCorrect = settings.requiredCorrectToAdvance
        
        // Update level and WPM display
        binding.textLevel.text = getString(R.string.level_label, currentLevel)
        binding.textWpm.text = getString(R.string.wpm_label, settings.speedWpm)
        
        // Update score display
        val sessionCorrect = progressTracker.sessionCorrect
        val sessionTotal = progressTracker.sessionTotal
        binding.textScore.text = getString(R.string.score_label, sessionCorrect, sessionTotal)
        
        // Update progress bar for level advancement
        val characters = MorseCode.getCharactersForLevel(currentLevel)
        val minCorrectCount = characters.minOfOrNull { char ->
            progressTracker.getCharacterStats(char).correctCount
        } ?: 0
        
        binding.progressLevel.max = requiredCorrect
        binding.progressLevel.progress = minCorrectCount
        
        val remaining = maxOf(0, requiredCorrect - minCorrectCount)
        binding.textNextLevel.text = getString(R.string.next_level_label, remaining)
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
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(4, 4, 4, 4)
                }
                
                if (char in availableChars) {
                    // Available character
                    setOnClickListener { onCharacterPressed(char) }
                    alpha = 1.0f
                    isEnabled = true
                } else {
                    // Unavailable character (greyed out)
                    alpha = 0.3f
                    isEnabled = false
                    setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                }
            }
            
            binding.keyboardGrid.addView(button)
        }
    }

    private fun onCharacterPressed(char: Char) {
        // Allow input during audio playback and while waiting for answer
        if (!isWaitingForAnswer && !isAudioPlaying) return
        
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
        binding.textAnswerInput.text = ""
        binding.textCurrentSequence.text = "?"
        binding.textStatus.text = getString(R.string.listening_prompt)
    }

    private fun playCurrentSequence() {
        // Stop any current playback first
        morseGenerator.stop()
        
        // Update UI state
        binding.textStatus.text = getString(R.string.playing_status)
        isAudioPlaying = true
        isWaitingForAnswer = true  // Allow input immediately when playback starts
        updateControlButtonStates()
        
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
                    // Keep isWaitingForAnswer = true so user can still input after audio ends
                    binding.textStatus.text = getString(R.string.listening_prompt)
                    updateControlButtonStates()
                    // Don't restart timeout here - it already started when playback began
                }
            }
        }
    }

    private fun updateControlButtonStates() {
        // Only disable control buttons during audio playback, keep keyboard enabled
        val buttonsEnabled = !isAudioPlaying
        
        binding.buttonPlayAgain.isEnabled = buttonsEnabled
        binding.buttonNext.isEnabled = buttonsEnabled
        
        // Update button text to indicate state
        if (buttonsEnabled) {
            binding.buttonPlayAgain.text = getString(R.string.play_again)
            binding.buttonNext.text = getString(R.string.next_sequence)
        } else {
            binding.buttonPlayAgain.text = getString(R.string.playing_button)
            binding.buttonNext.text = getString(R.string.playing_button)
        }
        
        // Keyboard stays enabled - users can type during and after audio playback
        // No need to disable keyboard buttons
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
        isWaitingForAnswer = false
        isAudioPlaying = false  // Make sure we're not in playing state
        
        // Stop any ongoing audio since user has submitted answer
        morseGenerator.stop()
        
        updateControlButtonStates()
        
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
        
        // Auto-advance after a delay
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isDetached && _binding != null) {
                startNewSequence()
            }
        }, 2000)
    }

    private fun handleTimeout() {
        isWaitingForAnswer = false
        isAudioPlaying = false
        
        // Stop any ongoing audio
        morseGenerator.stop()
        
        updateControlButtonStates()
        
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