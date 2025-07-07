package com.so5km.qrstrainer.ui.trainer

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
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
import com.so5km.qrstrainer.state.TrainingState
import com.so5km.qrstrainer.state.TrainingStateData
import kotlinx.coroutines.launch

class TrainerFragment : Fragment() {
    
    private var _binding: FragmentTrainerBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var storeViewModel: StoreViewModel
    
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
        
        storeViewModel = ViewModelProvider(requireActivity())[StoreViewModel::class.java]
        
        setupUI()
        observeState()
        setupAnimations()
    }
    
    private fun setupUI() {
        // Setup basic button listeners
        binding.buttonStart.setOnClickListener {
            // Start training logic
            showMessage("Training started!")
        }
        
        binding.buttonStop.setOnClickListener {
            // Stop training logic
            showMessage("Training stopped!")
        }
        
        binding.buttonReplay.setOnClickListener {
            // Replay sequence logic
            showMessage("Replaying sequence...")
        }
        
        // Update sequence display
        binding.sequenceDisplay.text = "Ready to train!\nPress START to begin."
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
    }
    
    private fun updateUIForTrainingState(state: TrainingStateData) {
        when (state.state) {
            TrainingState.READY -> {
                binding.sequenceDisplay.text = "Ready to train!\nPress START to begin."
                binding.buttonStart.isEnabled = true
                binding.buttonStop.isEnabled = false
            }
            TrainingState.PLAYING -> {
                binding.sequenceDisplay.text = "🎵 Listen to the sequence..."
                binding.buttonStart.isEnabled = false
                binding.buttonStop.isEnabled = true
                startSequenceAnimation()
            }
            TrainingState.WAITING -> {
                binding.sequenceDisplay.text = "Type what you heard:"
                animateToInputMode()
            }
            TrainingState.FINISHED -> {
                if (state.previousWasCorrect) {
                    binding.sequenceDisplay.text = "✅ Correct! Well done!"
                    showCorrectAnswerAnimation()
                } else {
                    binding.sequenceDisplay.text = "❌ Incorrect. Try again!"
                    showIncorrectAnswerAnimation()
                }
            }
            TrainingState.PAUSED -> {
                binding.sequenceDisplay.text = "Training paused"
            }
        }
    }
    
    private fun setupAnimations() {
        // Entrance animations
        val views = listOf(
            binding.progressIndicator,
            binding.sequenceDisplay,
            binding.controlPanel,
            binding.morseKeyboard
        )
        
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.animate()
                .alpha(1f)
                .setDuration(300)
                .setStartDelay((index * 100).toLong())
                .start()
        }
    }
    
    private fun startSequenceAnimation() {
        // Pulse animation during playback
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
        // Slide keyboard up
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
        // Green flash and scale
        binding.sequenceDisplay.let { view ->
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.1f, 1f)
            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.1f, 1f)
            
            AnimatorSet().apply {
                playTogether(scaleX, scaleY)
                duration = 300
            }.start()
        }
        
        showMessage("✅ Correct!")
    }
    
    private fun showIncorrectAnswerAnimation() {
        // Shake animation
        binding.sequenceDisplay.let { view ->
            val shake = ObjectAnimator.ofFloat(
                view, "translationX", 0f, -20f, 20f, -20f, 20f, 0f
            ).apply {
                duration = 400
            }
            shake.start()
        }
        
        showMessage("❌ Try again!")
    }
    
    private fun startAudioVisualization() {
        // Show audio visualization
        binding.audioVisualization.visibility = View.VISIBLE
    }
    
    private fun stopAudioVisualization() {
        // Hide audio visualization  
        binding.audioVisualization.visibility = View.GONE
    }
    
    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
