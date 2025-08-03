package com.so5km.qrstrainer.ui.settings.components

import android.view.View
import com.so5km.qrstrainer.R
import com.so5km.qrstrainer.data.TrainingSettings
import com.so5km.qrstrainer.databinding.FragmentSettingsBinding
import com.so5km.qrstrainer.audio.NoiseGenerator

/**
 * Component that handles noise, filtering, and spectrum visualization settings
 */
class NoiseSettingsComponent(
    private val binding: FragmentSettingsBinding,
    private val onSettingsUpdate: ((TrainingSettings) -> TrainingSettings) -> Unit,
    private val onSpectrumUpdate: () -> Unit = {}
) {
    
    fun setup() {
        setupNoiseControls()
    }
    
    private fun setupNoiseControls() {
        // Noise Settings
        binding.switchNoise.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutNoiseControls.visibility = if (isChecked) View.VISIBLE else View.GONE
            onSettingsUpdate { it.copy(noiseEnabled = isChecked) }
            
            // Update spectrum if visible
            if (binding.spectrumVisualizationView.visibility == View.VISIBLE) {
                onSpectrumUpdate()
            }
        }
        
        // QRM switch
        binding.switchQrm.setOnCheckedChangeListener { _, isChecked ->
            onSettingsUpdate { it.copy(qrmEnabled = isChecked) }
        }
        
        binding.sliderNoiseVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val noiseVolume = value / 100f
                binding.textNoiseVolumeValue.text = binding.root.context.getString(R.string.value_percent, value.toInt())
                onSettingsUpdate { it.copy(noiseVolume = noiseVolume) }
            }
        }
        
        binding.sliderNoiseBandwidth.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val bandwidth = value.toInt()
                binding.textNoiseBandwidthValue.text = binding.root.context.getString(R.string.value_hz, bandwidth)
                onSettingsUpdate { it.copy(noiseBandwidthHz = bandwidth.toFloat()) }
                
                // Update filter visualization
                binding.filterResponseView.setBandwidth(value)
                
                // Update spectrum if visible
                if (binding.spectrumVisualizationView.visibility == View.VISIBLE) {
                    onSpectrumUpdate()
                }
            }
        }
        
        // Filter type radio buttons
        binding.radioGroupFilterType.setOnCheckedChangeListener { _, checkedId ->
            val filterType = when (checkedId) {
                R.id.radio_butterworth -> "butterworth"
                R.id.radio_chebyshev -> "chebyshev"
                R.id.radio_elliptic -> "elliptic"
                else -> "butterworth"
            }
            onSettingsUpdate { it.copy(filterType = filterType) }
            binding.filterResponseView.setFilterType(filterType)
        }
        
        // Filter order slider
        binding.sliderFilterOrder.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val order = value.toInt()
                binding.textFilterOrderValue.text = order.toString()
                onSettingsUpdate { it.copy(filterOrder = order) }
                binding.filterResponseView.setFilterOrder(order)
                
                // Update spectrum if visible
                if (binding.spectrumVisualizationView.visibility == View.VISIBLE) {
                    onSpectrumUpdate()
                }
            }
        }
        
        // Initialize filter visualization
        binding.filterResponseView.apply {
            setCenterFrequency(binding.sliderFrequency.value)
            setBandwidth(binding.sliderNoiseBandwidth.value)
            setFilterType("butterworth")
            setFilterOrder(4)
            setShowRinging(true)
        }
    }
    
    fun updateUI(settings: TrainingSettings) {
        binding.apply {
            // Noise settings
            switchNoise.isChecked = settings.noiseEnabled
            layoutNoiseControls.visibility = if (settings.noiseEnabled) View.VISIBLE else View.GONE
            
            if (settings.noiseEnabled) {
                sliderNoiseVolume.value = (settings.noiseVolume * 100)
                sliderNoiseBandwidth.value = settings.noiseBandwidthHz
                textNoiseVolumeValue.text = root.context.getString(R.string.value_percent, (settings.noiseVolume * 100).toInt())
                textNoiseBandwidthValue.text = root.context.getString(R.string.value_hz, settings.noiseBandwidthHz.toInt())
                
                // Set QRM switch
                switchQrm.isChecked = settings.qrmEnabled
                
                // Set filter type radio button
                when (settings.filterType) {
                    "butterworth" -> radioButterworth.isChecked = true
                    "chebyshev" -> radioChebyshev.isChecked = true
                    "elliptic" -> radioElliptic.isChecked = true
                }
                
                // Set filter order
                sliderFilterOrder.value = settings.filterOrder.toFloat()
                textFilterOrderValue.text = settings.filterOrder.toString()
            }
            
            // Update filter visualization
            filterResponseView.apply {
                setCenterFrequency(settings.frequency.toFloat())
                setBandwidth(settings.noiseBandwidthHz)
                setFilterType(settings.filterType)
                setFilterOrder(settings.filterOrder)
            }
        }
    }
    
    fun generateTestSpectrum(settings: TrainingSettings, noiseGenerator: NoiseGenerator): Pair<FloatArray, FloatArray> {
        return noiseGenerator.generateTestSpectrum(
            settings.frequency.toFloat(),
            settings.noiseBandwidthHz,
            settings.filterOrder,
            settings.noiseEnabled
        )
    }
} 