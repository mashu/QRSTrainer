package com.so5km.qrstrainer.ui.trainer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null

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
            playCurrentSequence()
        }
        
        binding.buttonNext.setOnClickListener {
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
        if (!isWaitingForAnswer) return
        
        userInput += char
        binding.textAnswerInput.text = userInput
        
        // Check if we have enough characters
        if (userInput.length >= currentSequence.length) {
            checkAnswer()
        }
    }

    private fun startNewSequence() {
        // Cancel any existing timeout
        cancelTimeout()
        
        // Generate new sequence
        currentSequence = sequenceGenerator.generateSequence(
            settings.kochLevel,
            settings.groupSizeMin,
            settings.groupSizeMax
        )
        
        // Reset input
        userInput = ""
        binding.textAnswerInput.text = ""
        binding.textCurrentSequence.text = "?"
        binding.textStatus.text = getString(R.string.listening_prompt)
        
        // Play the sequence
        playCurrentSequence()
    }

    private fun playCurrentSequence() {
        binding.textStatus.text = "Playing..."
        isWaitingForAnswer = false
        
        morseGenerator.playSequence(
            currentSequence,
            settings.speedWpm,
            settings.repeatCount
        ) {
            // On playback complete
            Handler(Looper.getMainLooper()).post {
                isWaitingForAnswer = true
                binding.textStatus.text = getString(R.string.listening_prompt)
                startAnswerTimeout()
            }
        }
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
        
        val isCorrect = userInput.equals(currentSequence, ignoreCase = true)
        
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
            startNewSequence()
        }, 2000)
    }

    private fun handleTimeout() {
        isWaitingForAnswer = false
        
        // Record incorrect for unanswered characters
        currentSequence.forEach { char ->
            progressTracker.recordIncorrect(char)
        }
        
        binding.textStatus.text = getString(R.string.timeout_answer, currentSequence)
        binding.textCurrentSequence.text = currentSequence
        
        updateProgressDisplay()
        
        // Auto-advance after a delay
        Handler(Looper.getMainLooper()).postDelayed({
            startNewSequence()
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