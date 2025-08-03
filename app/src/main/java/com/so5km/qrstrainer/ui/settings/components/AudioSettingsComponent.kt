package com.so5km.qrstrainer.ui.settings.components

import android.view.View
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.databinding.FragmentSettingsBinding
import com.so5km.qrstrainer.ui.components.settings.WaveformVisualizationView

/**
 * Component that handles audio-related settings
 */
class AudioSettingsComponent(
    private val binding: FragmentSettingsBinding,
    private val onSettingsUpdate: ((TrainingSettings) -> TrainingSettings) -> Unit
) {
    
    fun setup() {
        setupAudioSliders()
    }
    
    private fun setupAudioSliders() {
        // Character Speed (WPM) Slider
        binding.sliderWpm.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val wpm = value.toInt()
                binding.textWpmValue.text = binding.root.context.getString(R.string.value_wpm, wpm)
                
                onSettingsUpdate { it.copy(wpm = wpm) }
                
                // Ensure effective WPM doesn't exceed character WPM
                if (binding.sliderEffectiveWpm.value > value) {
                    binding.sliderEffectiveWpm.value = value
                    binding.textEffectiveWpmValue.text = binding.root.context.getString(R.string.value_wpm, value.toInt())
                    onSettingsUpdate { it.copy(effectiveWpm = value.toInt()) }
                }
            }
        }
        
        // Effective Speed (Farnsworth) Slider
        binding.sliderEffectiveWpm.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val effectiveWpm = value.toInt()
                binding.textEffectiveWpmValue.text = binding.root.context.getString(R.string.value_wpm, effectiveWpm)
                
                onSettingsUpdate { it.copy(effectiveWpm = effectiveWpm) }
                
                // Ensure effective WPM doesn't exceed character WPM
                if (value > binding.sliderWpm.value) {
                    binding.sliderWpm.value = value
                    binding.textWpmValue.text = binding.root.context.getString(R.string.value_wpm, value.toInt())
                    onSettingsUpdate { it.copy(wpm = value.toInt()) }
                }
            }
        }
        
        // Frequency Slider
        binding.sliderFrequency.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val frequency = value.toInt()
                binding.textFrequencyValue.text = binding.root.context.getString(R.string.value_hz, frequency)
                onSettingsUpdate { it.copy(frequency = frequency) }
                
                // Update visualizations
                binding.filterResponseView.setCenterFrequency(value)
                binding.waveformVisualization.setFrequency(value)
                
                // Update spectrum if visible
                if (binding.spectrumVisualizationView.visibility == View.VISIBLE) {
                    // Spectrum update would be handled by parent
                }
            }
        }
        
        // Volume Slider
        binding.sliderVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val volume = value / 100f
                binding.textVolumeValue.text = binding.root.context.getString(R.string.value_percent, value.toInt())
                onSettingsUpdate { it.copy(volume = volume) }
            }
        }
        
        // Rise Time Slider
        binding.sliderRiseTime.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.textRiseTimeValue.text = binding.root.context.getString(R.string.value_ms, value.toInt())
                onSettingsUpdate { it.copy(riseTimeMs = value.toDouble()) }
                
                // Update waveform visualization
                binding.waveformVisualization.setRiseTime(value)
            }
        }
        
        // Initialize waveform visualization
        binding.waveformVisualization.apply {
            setFrequency(binding.sliderFrequency.value)
            setRiseTime(binding.sliderRiseTime.value)
        }
    }
    
    fun updateUI(settings: TrainingSettings) {
        binding.apply {
            // Audio settings
            sliderWpm.value = settings.wpm.toFloat()
            sliderEffectiveWpm.value = settings.effectiveWpm.toFloat()
            sliderFrequency.value = settings.frequency.toFloat()
            sliderVolume.value = (settings.volume * 100)
            sliderRiseTime.value = settings.riseTimeMs.toFloat()
            
            textWpmValue.text = root.context.getString(R.string.value_wpm, settings.wpm)
            textEffectiveWpmValue.text = root.context.getString(R.string.value_wpm, settings.effectiveWpm)
            textFrequencyValue.text = root.context.getString(R.string.value_hz, settings.frequency)
            textVolumeValue.text = root.context.getString(R.string.value_percent, (settings.volume * 100).toInt())
            textRiseTimeValue.text = root.context.getString(R.string.value_ms, settings.riseTimeMs.toInt())
            
            // Update visualizations with current settings
            waveformVisualization.apply {
                setFrequency(settings.frequency.toFloat())
                setRiseTime(settings.riseTimeMs.toFloat())
            }
            
            filterResponseView.apply {
                setCenterFrequency(settings.frequency.toFloat())
            }
        }
    }
} 