package com.so5km.qrstrainer.ui.trainer

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
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
        
        initializeComponents()
        setupUI()
        observeState()
        setupAnimations()
        updateProgressDisplay()
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
        val currentLevel = progressTracker.getCurrentLevel()
        updateMorseKeyboardForLevel(currentLevel)
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
                updateMorseKeyboardForLevel(settings.currentLevel)
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
    }
    
    private fun clearInput() {
        userInput = ""
        storeViewModel.dispatch(AppAction.UpdateUserInput(userInput))
        updateInputDisplay()
    }
    
    private fun submitAnswer() {
        if (userInput.isEmpty()) {
            showMessage("Please enter your answer first")
            return
        }
        
        val responseTime = System.currentTimeMillis() - startTime
        val isCorrect = userInput.uppercase() == currentSequence.uppercase()
        
        currentSequence.toCharArray().forEach { char ->
            val userChar = if (userInput.length > currentSequence.indexOf(char)) {
                userInput[currentSequence.indexOf(char)]
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
            if (isCorrect) {
                startTraining()
            } else {
                updateUIForState(TrainingState.READY)
            }
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
        when (state) {
            TrainingState.READY -> {
                binding.sequenceDisplay.text = "Ready to train!\nPress START to begin."
                binding.buttonStart.isEnabled = true
                binding.buttonStop.isEnabled = false
                binding.buttonReplay.isEnabled = false
                binding.morseKeyboard.alpha = 0.5f
                setKeyboardEnabled(false)
            }
            TrainingState.PLAYING -> {
                binding.sequenceDisplay.text = "🎵 Listen to the sequence..."
                binding.buttonStart.isEnabled = false
                binding.buttonStop.isEnabled = true
                binding.buttonReplay.isEnabled = false
                binding.morseKeyboard.alpha = 0.5f
                setKeyboardEnabled(false)
                startSequenceAnimation()
            }
            TrainingState.WAITING -> {
                binding.sequenceDisplay.text = "Type what you heard:\n$userInput"
                binding.buttonStart.isEnabled = false
                binding.buttonStop.isEnabled = true
                binding.buttonReplay.isEnabled = true
                binding.morseKeyboard.alpha = 1.0f
                setKeyboardEnabled(true)
                animateToInputMode()
            }
            TrainingState.FINISHED -> {
                binding.buttonStart.isEnabled = true
                binding.buttonStop.isEnabled = false
                binding.buttonReplay.isEnabled = true
                binding.morseKeyboard.alpha = 0.5f
                setKeyboardEnabled(false)
            }
            TrainingState.PAUSED -> {
                binding.sequenceDisplay.text = "Training paused"
                binding.buttonStart.isEnabled = true
                binding.buttonStop.isEnabled = false
                binding.buttonReplay.isEnabled = true
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
            binding.sequenceDisplay.text = "Type what you heard:\n$userInput"
        }
    }
    
    private fun updateProgressDisplay() {
        val level = progressTracker.getCurrentLevel()
        val progress = progressTracker.getCurrentLevelProgress()
        val streak = progressTracker.getCurrentStreak()
        
        binding.progressIndicator.text = "Level $level - Progress: ${(progress * 100).toInt()}% - Streak: $streak"
    }
    
    private fun setupAnimations() {
        val views = listOf(
            binding.progressIndicator,
            binding.sequenceDisplay,
            binding.controlPanel,
            binding.morseKeyboard
        )
        
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 100f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .setStartDelay((index * 100).toLong())
                .start()
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
        val levelChars = progressTracker.getCharactersForLevel(level)
        
        binding.morseKeyboard.removeAllViews()
        
        levelChars.forEach { char ->
            val button = android.widget.Button(requireContext()).apply {
                text = char.toString()
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = resources.getDimensionPixelSize(R.dimen.morse_key_size)
                    columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    setMargins(8, 8, 8, 8)
                }
                setBackgroundResource(R.drawable.morse_key_background)
                setOnClickListener { onCharacterSelected(char) }
            }
            binding.morseKeyboard.addView(button)
        }
        
        addControlButtons()
    }
    
    private fun addControlButtons() {
        val submitButton = android.widget.Button(requireContext()).apply {
            text = "SUBMIT"
            layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = resources.getDimensionPixelSize(R.dimen.morse_key_size)
                columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 2f)
                setMargins(8, 8, 8, 8)
            }
            setBackgroundResource(R.drawable.morse_key_background)
            setOnClickListener { submitAnswer() }
        }
        binding.morseKeyboard.addView(submitButton)
        
        val clearButton = android.widget.Button(requireContext()).apply {
            text = "CLEAR"
            layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = 0
                height = resources.getDimensionPixelSize(R.dimen.morse_key_size)
                columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 2f)
                setMargins(8, 8, 8, 8)
            }
            setBackgroundResource(R.drawable.morse_key_background)
            setOnClickListener { clearInput() }
        }
        binding.morseKeyboard.addView(clearButton)
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
