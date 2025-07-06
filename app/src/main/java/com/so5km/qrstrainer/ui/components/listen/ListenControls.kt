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
        binding.buttonReveal.setOnClickListener { onRevealClick?.invoke() }
        binding.buttonNext.setOnClickListener { onNextClick?.invoke() }
        binding.buttonReplay.setOnClickListener { onReplayClick?.invoke() }
    }
    
    fun updateState(state: ListeningState, isSequenceRevealed: Boolean) {
        when (state) {
            ListeningState.READY -> {
                binding.buttonStart.visibility = VISIBLE
                binding.buttonReveal.visibility = GONE
                binding.buttonNext.visibility = GONE
                binding.buttonReplay.visibility = GONE
            }
            ListeningState.PLAYING -> {
                binding.buttonStart.visibility = GONE
                binding.buttonReveal.visibility = GONE
                binding.buttonNext.visibility = GONE
                binding.buttonReplay.visibility = GONE
            }
            ListeningState.WAITING -> {
                binding.buttonStart.visibility = GONE
                binding.buttonReveal.visibility = if (!isSequenceRevealed) VISIBLE else GONE
                binding.buttonNext.visibility = if (isSequenceRevealed) VISIBLE else GONE
                binding.buttonReplay.visibility = VISIBLE
            }
            ListeningState.REVEALED -> {
                binding.buttonStart.visibility = GONE
                binding.buttonReveal.visibility = GONE
                binding.buttonNext.visibility = VISIBLE
                binding.buttonReplay.visibility = VISIBLE
            }
            ListeningState.PAUSED -> {
                // Handle pause state if needed
            }
        }
    }
}
