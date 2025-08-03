package com.so5km.qrstrainer.ui.settings.components

import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.databinding.FragmentSettingsBinding

/**
 * Component that handles group and sequence length settings
 */
class GroupSettingsComponent(
    private val binding: FragmentSettingsBinding,
    private val onSettingsUpdate: ((TrainingSettings) -> TrainingSettings) -> Unit
) {
    
    fun setup() {
        setupGroupSliders()
    }
    
    private fun setupGroupSliders() {
        // Min Group Size
        binding.sliderMinGroupSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val minSize = value.toInt()
                binding.textMinGroupSizeValue.text = binding.root.context.getString(R.string.value_chars, minSize)
                
                onSettingsUpdate { it.copy(minGroupSize = minSize) }
                
                // Ensure max group size is at least min size
                if (binding.sliderMaxGroupSize.value < value) {
                    binding.sliderMaxGroupSize.value = value
                    binding.textMaxGroupSizeValue.text = binding.root.context.getString(R.string.value_chars, value.toInt())
                    onSettingsUpdate { it.copy(maxGroupSize = value.toInt()) }
                }
            }
        }
        
        // Max Group Size
        binding.sliderMaxGroupSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val maxSize = value.toInt()
                binding.textMaxGroupSizeValue.text = binding.root.context.getString(R.string.value_chars, maxSize)
                
                onSettingsUpdate { it.copy(maxGroupSize = maxSize) }
                
                // Ensure min group size doesn't exceed max size
                if (binding.sliderMinGroupSize.value > value) {
                    binding.sliderMinGroupSize.value = value
                    binding.textMinGroupSizeValue.text = binding.root.context.getString(R.string.value_chars, value.toInt())
                    onSettingsUpdate { it.copy(minGroupSize = value.toInt()) }
                }
            }
        }
        
        // Min Sequence Length
        binding.sliderMinSequenceLength.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.textMinSequenceLengthValue.text = value.toInt().toString()
                // Ensure max is not less than min
                if (binding.sliderMaxSequenceLength.value < value) {
                    binding.sliderMaxSequenceLength.value = value
                    binding.textMaxSequenceLengthValue.text = value.toInt().toString()
                    onSettingsUpdate { it.copy(minSequenceLength = value.toInt(), maxSequenceLength = value.toInt()) }
                } else {
                    onSettingsUpdate { it.copy(minSequenceLength = value.toInt()) }
                }
            }
        }
        
        // Max Sequence Length
        binding.sliderMaxSequenceLength.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.textMaxSequenceLengthValue.text = value.toInt().toString()
                // Ensure min is not greater than max
                if (binding.sliderMinSequenceLength.value > value) {
                    binding.sliderMinSequenceLength.value = value
                    binding.textMinSequenceLengthValue.text = value.toInt().toString()
                    onSettingsUpdate { it.copy(minSequenceLength = value.toInt(), maxSequenceLength = value.toInt()) }
                } else {
                    onSettingsUpdate { it.copy(maxSequenceLength = value.toInt()) }
                }
            }
        }
        
        // Keep old single sequence length slider for backward compatibility (hidden)
        binding.sliderSequenceLength.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val length = value.toInt()
                binding.textSequenceLengthValue.text = "$length groups"
                onSettingsUpdate { it.copy(sequenceLength = length) }
            }
        }
    }
    
    fun updateUI(settings: TrainingSettings) {
        binding.apply {
            // Group settings
            sliderMinGroupSize.value = settings.minGroupSize.toFloat()
            sliderMaxGroupSize.value = settings.maxGroupSize.toFloat()
            sliderSequenceLength.value = settings.sequenceLength.toFloat()
            sliderMinSequenceLength.value = settings.minSequenceLength.toFloat()
            sliderMaxSequenceLength.value = settings.maxSequenceLength.toFloat()
            
            textMinGroupSizeValue.text = root.context.getString(R.string.value_chars, settings.minGroupSize)
            textMaxGroupSizeValue.text = root.context.getString(R.string.value_chars, settings.maxGroupSize)
            textSequenceLengthValue.text = "${settings.sequenceLength} groups"
            textMinSequenceLengthValue.text = settings.minSequenceLength.toString()
            textMaxSequenceLengthValue.text = settings.maxSequenceLength.toString()
        }
    }
} 