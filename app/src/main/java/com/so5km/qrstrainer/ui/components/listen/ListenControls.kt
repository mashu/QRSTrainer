package com.so5km.qrstrainer.ui.components.listen

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import com.so5km.qrstrainer.databinding.ComponentListenControlsBinding
import com.so5km.qrstrainer.state.ListeningState

class ListenControls @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    
    private val binding: ComponentListenControlsBinding
    
    var onStartClick: (() -> Unit)? = null
    var onStopClick: (() -> Unit)? = null
    var onRevealClick: (() -> Unit)? = null
    var onNextClick: (() -> Unit)? = null
    var onReplayClick: (() -> Unit)? = null
    
    init {
        binding = ComponentListenControlsBinding.inflate(
            LayoutInflater.from(context), this, true
        )
        setupListeners()
    }
    
    private fun setupListeners() {
        binding.buttonStart.setOnClickListener { onStartClick?.invoke() }
        binding.buttonStop.setOnClickListener { onStopClick?.invoke() }
        binding.buttonReveal.setOnClickListener { onRevealClick?.invoke() }
        binding.buttonNext.setOnClickListener { onNextClick?.invoke() }
        binding.buttonReplay.setOnClickListener { onReplayClick?.invoke() }
    }
    
    fun updateState(state: ListeningState, isSequenceRevealed: Boolean) {
        when (state) {
            ListeningState.READY -> {
                // Show only start button, others disabled and dimmed
                binding.buttonStart.isEnabled = true
                binding.buttonStart.alpha = 1.0f
                
                binding.buttonStop.isEnabled = false
                binding.buttonStop.alpha = 0.3f
                
                binding.buttonReveal.isEnabled = false
                binding.buttonReveal.alpha = 0.3f
                
                binding.buttonNext.isEnabled = false
                binding.buttonNext.alpha = 0.3f
                
                binding.buttonReplay.isEnabled = false
                binding.buttonReplay.alpha = 0.3f
            }
            ListeningState.PLAYING -> {
                // Only stop button active
                binding.buttonStart.isEnabled = false
                binding.buttonStart.alpha = 0.3f
                
                binding.buttonStop.isEnabled = true
                binding.buttonStop.alpha = 1.0f
                
                binding.buttonReveal.isEnabled = false
                binding.buttonReveal.alpha = 0.3f
                
                binding.buttonNext.isEnabled = false
                binding.buttonNext.alpha = 0.3f
                
                binding.buttonReplay.isEnabled = false
                binding.buttonReplay.alpha = 0.3f
            }
            ListeningState.WAITING -> {
                // Stop, reveal (if not revealed), and replay active
                binding.buttonStart.isEnabled = false
                binding.buttonStart.alpha = 0.3f
                
                binding.buttonStop.isEnabled = true
                binding.buttonStop.alpha = 1.0f
                
                binding.buttonReveal.isEnabled = !isSequenceRevealed
                binding.buttonReveal.alpha = if (!isSequenceRevealed) 1.0f else 0.3f
                
                binding.buttonNext.isEnabled = isSequenceRevealed
                binding.buttonNext.alpha = if (isSequenceRevealed) 1.0f else 0.3f
                
                binding.buttonReplay.isEnabled = true
                binding.buttonReplay.alpha = 1.0f
            }
            ListeningState.REVEALED -> {
                // Stop, next, and replay active
                binding.buttonStart.isEnabled = false
                binding.buttonStart.alpha = 0.3f
                
                binding.buttonStop.isEnabled = true
                binding.buttonStop.alpha = 1.0f
                
                binding.buttonReveal.isEnabled = false
                binding.buttonReveal.alpha = 0.3f
                
                binding.buttonNext.isEnabled = true
                binding.buttonNext.alpha = 1.0f
                
                binding.buttonReplay.isEnabled = true
                binding.buttonReplay.alpha = 1.0f
            }
            ListeningState.PAUSED -> {
                // Only stop button active during pause
                binding.buttonStart.isEnabled = false
                binding.buttonStart.alpha = 0.3f
                
                binding.buttonStop.isEnabled = true
                binding.buttonStop.alpha = 1.0f
                
                binding.buttonReveal.isEnabled = false
                binding.buttonReveal.alpha = 0.3f
                
                binding.buttonNext.isEnabled = false
                binding.buttonNext.alpha = 0.3f
                
                binding.buttonReplay.isEnabled = false
                binding.buttonReplay.alpha = 0.3f
            }
        }
    }
}
